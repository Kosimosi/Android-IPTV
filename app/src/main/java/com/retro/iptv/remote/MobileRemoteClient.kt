package com.retro.iptv.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.SSLParameters

class MobileRemoteClient(
    context: Context,
    private val onConnectionStateChanged: (Boolean, String?) -> Unit,
    private val onStateReceived: (String) -> Unit,
    private val onConnectionDetails: (host: String, port: Int, pin: String) -> Unit = { _, _, _ -> }
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var wsClient: WebSocketClient? = null
    @Volatile private var paired = false
    private var pairingCode = ""
    private var selectedServiceName: String? = null

    fun startDiscovery(pin: String) {
        stopDiscovery()
        val previous = wsClient
        wsClient = null
        paired = false
        runCatching { previous?.close() }
        pairingCode = pin.filter(Char::isDigit).take(6)
        if (pairingCode.length != 6) {
            mainHandler.post { onConnectionStateChanged(false, "Enter the 6-digit TV pairing PIN") }
            return
        }
        runCatching {
            multicastLock = wifiManager.createMulticastLock("retrotv-remote-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                mainHandler.post { onConnectionStateChanged(false, "Searching for RETROTV on this Wi-Fi…") }
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains(RemoteProtocol.SERVICE_TYPE.removeSuffix("."))) return
                if (wsClient != null) return
                runCatching {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host ?: return
                            stopDiscovery()
                            connectToTv(host.hostAddress, resolved.port, resolved.serviceName, pairingCode)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                if (service.serviceName == selectedServiceName && wsClient?.isOpen != true) {
                    mainHandler.post { onConnectionStateChanged(false, "TV is no longer visible") }
                }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mainHandler.post { onConnectionStateChanged(false, "Wi-Fi discovery failed ($errorCode). Use IP / PIN.") }
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(RemoteProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            mainHandler.post { onConnectionStateChanged(false, "Discovery unavailable. Use IP / PIN.") }
        }
    }

    fun connectManual(host: String, port: Int, pin: String) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        val cleanPin = pin.filter(Char::isDigit).take(6)
        if (cleanHost.isBlank() || port !in 1..65535 || cleanPin.length != 6) {
            mainHandler.post { onConnectionStateChanged(false, "Enter a valid TV IP, port and 6-digit PIN") }
            return
        }
        stopDiscovery()
        paired = false
        val previous = wsClient
        wsClient = null
        runCatching { previous?.close() }
        pairingCode = cleanPin
        connectToTv(cleanHost, port, "RETROTV", cleanPin)
    }

    fun sendCommand(command: String, value: String = "") {
        val client = wsClient
        if (paired && client?.isOpen == true) {
            runCatching { client.send(RemoteProtocol.createCommand(command, value)) }
        }
    }

    fun stop() {
        stopDiscovery()
        paired = false
        val client = wsClient
        wsClient = null
        runCatching { client?.close() }
        mainHandler.post { onConnectionStateChanged(false, "Disconnected") }
    }

    private fun connectToTv(host: String, port: Int, serviceName: String, pin: String) {
        if (wsClient?.isOpen == true) return
        val uri = try {
            val uriHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            URI("wss://$uriHost:$port")
        } catch (_: Exception) {
            mainHandler.post { onConnectionStateChanged(false, "Invalid TV address") }
            return
        }
        selectedServiceName = serviceName
        val old = wsClient
        wsClient = null
        runCatching { old?.close() }
        paired = false
        val tls = RemoteTls.clientContext(appContext)
        val client = object : WebSocketClient(uri) {
            // The TV uses a self-signed certificate whose identity is verified by our
            // persisted SHA-256 pin, not by matching a changing LAN IP to a DNS name.
            override fun onSetSSLParameters(sslParameters: SSLParameters) {
                sslParameters.endpointIdentificationAlgorithm = null
            }

            override fun onOpen(handshakedata: ServerHandshake?) {
                runCatching { send(RemoteProtocol.createCommand(RemoteProtocol.CMD_PAIR, pin)) }
            }
            override fun onMessage(message: String) {
                val type = runCatching { JSONObject(message).optString("type") }.getOrDefault("")
                when (type) {
                    RemoteProtocol.EVT_PAIRED -> {
                        try {
                            tls.persistFirstPair(appContext)
                            paired = true
                            mainHandler.post {
                                onConnectionDetails(host, port, pin)
                                onConnectionStateChanged(true, serviceName)
                            }
                        } catch (_: Exception) {
                            paired = false
                            close(1008, "Could not pin RETROTV certificate")
                            mainHandler.post { onConnectionStateChanged(false, "Certificate pinning failed. Pair again.") }
                        }
                    }
                    RemoteProtocol.EVT_STATE -> mainHandler.post { onStateReceived(message) }
                }
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                if (wsClient === this) {
                    paired = false
                    mainHandler.post {
                        if (wsClient === this) onConnectionStateChanged(false, "Disconnected from RETROTV")
                    }
                }
            }
            override fun onError(ex: Exception?) {
                if (wsClient === this) {
                    paired = false
                    mainHandler.post {
                        if (wsClient === this) onConnectionStateChanged(false, "Secure connection failed. Check TV PIN, Wi-Fi, or certificate pin.")
                    }
                }
            }
        }
        client.setSocketFactory(tls.context.socketFactory)
        wsClient = client
        mainHandler.post { onConnectionStateChanged(false, "Connecting securely to $serviceName…") }
        runCatching { client.connect() }.onFailure {
            wsClient = null
            mainHandler.post { onConnectionStateChanged(false, "Connection failed. Check IP and PIN.") }
        }
    }

    private fun stopDiscovery() {
        discoveryListener?.let { listener -> runCatching { nsdManager.stopServiceDiscovery(listener) } }
        discoveryListener = null
        runCatching { multicastLock?.let { if (it.isHeld) it.release() } }
        multicastLock = null
    }
}
