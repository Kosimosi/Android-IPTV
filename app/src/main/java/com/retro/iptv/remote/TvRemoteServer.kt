package com.retro.iptv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TvRemoteServer(
    context: Context,
    private val pairingCode: String,
    private val stateProvider: () -> String,
    private val onCommandReceived: (String, String) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val authenticated = ConcurrentHashMap.newKeySet<WebSocket>()
    private val failedAttempts = ConcurrentHashMap<String, Int>()
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var wsServer: RetroWsServer? = null

    fun start(port: Int = DEFAULT_PORT) {
        if (wsServer != null) return
        val server = RetroWsServer(InetSocketAddress("0.0.0.0", port))
        server.isReuseAddr = true
        server.connectionLostTimeout = 25
        server.maxFrameSize = MAX_MESSAGE_CHARS
        wsServer = server
        try {
            server.start()
            registerNsdService(port)
            onStatusChanged("Starting • port $port")
        } catch (error: Exception) {
            wsServer = null
            onStatusChanged("Unavailable: ${error.message ?: "server error"}")
        }
    }

    fun broadcastState(stateJson: String) {
        authenticated.toList().forEach { socket ->
            runCatching { if (socket.isOpen) socket.send(stateJson) }
        }
    }

    fun stop() {
        registrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        registrationListener = null
        authenticated.clear()
        val server = wsServer
        wsServer = null
        if (server != null) {
            Thread {
                runCatching { server.stop(700) }
            }.apply { name = "RetroTV-Remote-Stop"; isDaemon = true; start() }
        }
        onStatusChanged("Stopped")
    }

    private fun registerNsdService(port: Int) {
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager
        val info = NsdServiceInfo().apply {
            serviceName = RemoteProtocol.SERVICE_NAME
            serviceType = RemoteProtocol.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.post { onStatusChanged("Ready • ${serviceInfo.serviceName}") }
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                mainHandler.post { onStatusChanged("Discovery unavailable (error $errorCode); use IP / PIN") }
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                mainHandler.post { onStatusChanged("Discovery stop failed ($errorCode)") }
            }
        }
        registrationListener = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { onStatusChanged("Discovery unavailable; use IP / PIN") }
    }

    private inner class RetroWsServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            if (connections.size > MAX_CLIENTS) {
                conn.close(1008, "Too many remote clients")
                return
            }
            mainHandler.post { onStatusChanged("Client connected • pairing required") }
        }

        override fun onMessage(conn: WebSocket, message: String) {
            if (message.length > MAX_MESSAGE_CHARS) {
                conn.close(1009, "Message too large")
                return
            }
            val json = runCatching { JSONObject(message) }.getOrNull() ?: return
            val command = json.optString("cmd").take(32)
            val value = json.optString("value").take(512)

            if (command == RemoteProtocol.CMD_PAIR) {
                val address = conn.remoteSocketAddress?.address?.hostAddress ?: "unknown"
                if (failedAttempts[address] ?: 0 >= MAX_PAIR_ATTEMPTS) {
                    conn.close(1008, "Pairing attempts exceeded")
                    return
                }
                if (!constantTimeEquals(value, pairingCode)) {
                    failedAttempts.merge(address, 1, Int::plus)
                    conn.close(1008, "Incorrect pairing code")
                    return
                }
                authenticated.add(conn)
                failedAttempts.remove(address)
                runCatching { conn.send(JSONObject().put("type", RemoteProtocol.EVT_PAIRED).toString()) }
                mainHandler.post { runCatching { if (conn.isOpen) conn.send(stateProvider()) } }
                mainHandler.post { onStatusChanged("Paired • ${authenticated.size} remote(s)") }
                return
            }

            if (!authenticated.contains(conn)) {
                conn.close(1008, "Pair first")
                return
            }

            when (command) {
                RemoteProtocol.CMD_DPAD -> if (value in ALLOWED_DPAD) {
                    mainHandler.post { onCommandReceived(command, value) }
                }
                RemoteProtocol.CMD_NUMBER -> if (value.length == 1 && value[0].isDigit()) {
                    mainHandler.post { onCommandReceived(command, value) }
                }
                RemoteProtocol.CMD_CHANNEL_ID -> if (value.toIntOrNull()?.let { it in 1..20_000 } == true) {
                    mainHandler.post { onCommandReceived(command, value) }
                }
                RemoteProtocol.CMD_SEARCH -> mainHandler.post { onCommandReceived(command, value) }
                RemoteProtocol.CMD_MEDIA -> if (value in ALLOWED_MEDIA) {
                    mainHandler.post { onCommandReceived(command, value) }
                }
                RemoteProtocol.CMD_SYNC_REQ -> mainHandler.post { runCatching { if (conn.isOpen) conn.send(stateProvider()) } }
            }
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            authenticated.remove(conn)
            mainHandler.post {
                onStatusChanged(if (authenticated.isEmpty()) "Ready • waiting for phone" else "Paired • ${authenticated.size} remote(s)")
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            mainHandler.post { onStatusChanged("Remote connection error") }
        }

        override fun onStart() {
            mainHandler.post { onStatusChanged("Ready • port $DEFAULT_PORT") }
        }
    }

    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(first.toByteArray(Charsets.UTF_8), second.toByteArray(Charsets.UTF_8))

    companion object {
        const val DEFAULT_PORT = 8899
        private const val MAX_CLIENTS = 3
        private const val MAX_MESSAGE_CHARS = 16 * 1024
        private const val MAX_PAIR_ATTEMPTS = 5
        private val ALLOWED_DPAD = setOf("UP", "DOWN", "LEFT", "RIGHT", "OK", "BACK", "HOME")
        private val ALLOWED_MEDIA = setOf("VOL_UP", "VOL_DOWN", "MUTE", "TICKER_TOGGLE")
    }
}
