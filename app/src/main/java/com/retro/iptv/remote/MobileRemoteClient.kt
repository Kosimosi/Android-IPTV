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
import java.net.InetAddress
import java.net.URI

class MobileRemoteClient(
    context: Context,
    private val onConnectionStateChanged: (Boolean, String?) -> Unit,
    private val onStateReceived: (String) -> Unit
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
                            connectToTv(host, resolved.port, resolved.serviceName, pairingCode)
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
        pairingCode = cleanPin
        connectToTv(runCatching { InetAddress.getByName(cleanHost) }.getOrNull() ?: run {
            mainHandler.post { onConnectionStateChanged(false, "Invalid TV address") }
            return
        }, port, "RETROTV", cleanPin)
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

    private fun connectToTv(host: InetAddress, port: Int, serviceName: String, pin: String) {
        connectToTv(host.hostAddress, port, serviceName, pin)
    }

    private fun connectToTv(host: String, port: Int, serviceName: String, pin: String) {
        if (wsClient?.isOpen == true) return
        selectedServiceName = serviceName
        val old = wsClient
        wsClient = null
        runCatching { old?.close() }
        paired = false
        val client = object : WebSocketClient(URI("ws://$host:$port")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                runCatching { send(RemoteProtocol.createCommand(RemoteProtocol.CMD_PAIR, pin)) }
            }
            override fun onMessage(message: String) {
                val type = runCatching { JSONObject(message).optString("type") }.getOrDefault("")
                when (type) {
                    RemoteProtocol.EVT_PAIRED -> {
                        paired = true
                        mainHandler.post { onConnectionStateChanged(true, serviceName) }
                    }
                    RemoteProtocol.EVT_STATE -> mainHandler.post { onStateReceived(message) }
                }
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                paired = false
                mainHandler.post { onConnectionStateChanged(false, "Disconnected from RETROTV") }
            }
            override fun onError(ex: Exception?) {
                paired = false
                mainHandler.post { onConnectionStateChanged(false, "Could not connect. Check TV PIN and Wi-Fi.") }
            }
        }
        wsClient = client
        mainHandler.post { onConnectionStateChanged(false, "Connecting to $serviceName…") }
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
