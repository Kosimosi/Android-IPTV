package com.retro.iptv.remote

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.util.Log

/** Phone-side Bluetooth HID Device: the Android TV/box is the HID host. */
class BluetoothHidRemoteManager(
    private val context: Context,
    private val onConnectionChanged: (Boolean, String) -> Unit = { _, _ -> }
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var connectedDevice: BluetoothDevice? = null
    @Volatile private var registered = false
    private val typingExecutor = Executors.newSingleThreadExecutor()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registeredNow: Boolean) {
            registered = registeredNow
            if (!registeredNow) {
                connectedDevice = null
                onConnectionChanged(false, "Bluetooth HID unavailable")
            } else if (pluggedDevice != null) {
                connectedDevice = pluggedDevice
                onConnectionChanged(true, pluggedDevice.name ?: "Bluetooth host")
            } else {
                // The profile proxy is ready now; retry any host paired before this session.
                connectFirstPairedHost()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    onConnectionChanged(true, device.name ?: "Bluetooth host")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) connectedDevice = null
                    onConnectionChanged(false, "Bluetooth disconnected")
                }
            }
        }
    }

    fun initialize(): Boolean {
        if (!hasConnectPermission()) return false
        val bt = adapter ?: return false
        if (!bt.isEnabled) return false
        return runCatching {
            bt.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        }.getOrDefault(false)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as? BluetoothHidDevice ?: return
            hidDevice = hid
            if (!hasConnectPermission()) return
            runCatching {
                hid.registerApp(
                    BluetoothHidDeviceAppSdpSettings(
                        "RETROTV Remote",
                        "Universal TV and Android TV remote",
                        "RETROTV",
                        BluetoothHidDevice.SUBCLASS1_COMBO,
                        HID_REPORT_DESCRIPTOR
                    ),
                    null, null, Executor { command -> Handler(Looper.getMainLooper()).post(command) }, callback
                )
            }.onFailure { Log.e(TAG, "HID registration failed", it) }
        }

        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            registered = false
            connectedDevice = null
            onConnectionChanged(false, "Bluetooth service unavailable")
        }
    }

    fun isConnected(): Boolean = registered && hidDevice != null && connectedDevice != null

    /** Connect to a TV/box that has already paired with this phone. */
    fun connectPairedHost(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission()) return false
        return runCatching { hidDevice?.connect(device) == true }.getOrDefault(false)
    }

    fun connectFirstPairedHost(): Boolean {
        if (!hasConnectPermission()) return false
        val devices = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
        return devices.any(::connectPairedHost)
    }

    /** Start Android's standard discoverability prompt for one-time pairing from TV settings. */
    fun requestDiscoverableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }

    fun sendConsumerUsage(usage: Int): Boolean {
        val device = connectedDevice ?: return false
        val hid = hidDevice ?: return false
        if (!isConnected() || !hasConnectPermission()) return false
        val report = byteArrayOf((usage and 0xFF).toByte(), ((usage ushr 8) and 0xFF).toByte())
        val sent = runCatching { hid.sendReport(device, REPORT_CONSUMER, report) }.getOrDefault(false)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { hid.sendReport(device, REPORT_CONSUMER, byteArrayOf(0, 0)) }
        }, 45)
        return sent
    }

    fun sendKey(keyCode: Int, shift: Boolean = false): Boolean {
        val device = connectedDevice ?: return false
        val hid = hidDevice ?: return false
        if (!isConnected() || !hasConnectPermission()) return false
        val report = ByteArray(8)
        report[0] = if (shift) 0x02 else 0
        report[2] = keyCode.toByte()
        val sent = runCatching { hid.sendReport(device, REPORT_KEYBOARD, report) }.getOrDefault(false)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { hid.sendReport(device, REPORT_KEYBOARD, ByteArray(8)) }
        }, 35)
        return sent
    }

    fun sendBack(): Boolean = sendConsumerUsage(0x0224)
    fun sendHome(): Boolean = sendConsumerUsage(0x0223)
    fun sendVolumeUp(): Boolean = sendConsumerUsage(0x00E9)
    fun sendVolumeDown(): Boolean = sendConsumerUsage(0x00EA)
    fun sendMute(): Boolean = sendConsumerUsage(0x00E2)
    fun sendEnter(): Boolean = sendKey(0x28)
    fun sendBackspace(): Boolean = sendKey(0x2A)

    fun typeText(text: String): Boolean {
        if (!isConnected()) return false
        return runCatching {
            typingExecutor.execute {
                text.forEach { char ->
                    val key = asciiToHid(char) ?: return@forEach
                    sendKey(key.first, key.second)
                    Thread.sleep(45)
                }
            }
            true
        }.getOrDefault(false)
    }

    fun releaseResources() {
        typingExecutor.shutdownNow()
        if (!hasConnectPermission()) return
        runCatching { hidDevice?.unregisterApp() }
        runCatching { hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        hidDevice = null
        connectedDevice = null
        registered = false
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun asciiToHid(char: Char): Pair<Int, Boolean>? {
        if (char in 'a'..'z') return (0x04 + char.code - 'a'.code) to false
        if (char in 'A'..'Z') return (0x04 + char.code - 'A'.code) to true
        if (char in '1'..'9') return (0x1E + char.code - '1'.code) to false
        if (char == '0') return 0x27 to false
        return when (char) {
            ' ' -> 0x2C to false
            '\n', '\r' -> 0x28 to false
            '\b' -> 0x2A to false
            '-' -> 0x2D to false
            '_' -> 0x2D to true
            '=' -> 0x2E to false
            '+' -> 0x2E to true
            '[' -> 0x2F to false
            '{' -> 0x2F to true
            ']' -> 0x30 to false
            '}' -> 0x30 to true
            '\\' -> 0x31 to false
            '|' -> 0x31 to true
            ';' -> 0x33 to false
            ':' -> 0x33 to true
            '\'' -> 0x34 to false
            '"' -> 0x34 to true
            '`' -> 0x35 to false
            '~' -> 0x35 to true
            ',' -> 0x36 to false
            '<' -> 0x36 to true
            '.' -> 0x37 to false
            '>' -> 0x37 to true
            '/' -> 0x38 to false
            '?' -> 0x38 to true
            '!' -> 0x1E to true
            '@' -> 0x1F to true
            '#' -> 0x20 to true
            '$' -> 0x21 to true
            '%' -> 0x22 to true
            '^' -> 0x23 to true
            '&' -> 0x24 to true
            '*' -> 0x25 to true
            '(' -> 0x26 to true
            ')' -> 0x27 to true
            else -> null
        }
    }

    companion object {
        private const val TAG = "BluetoothHidRemote"
        private const val REPORT_CONSUMER = 1
        private const val REPORT_KEYBOARD = 2

        // Report 1: 16-bit Consumer Control. Report 2: standard 8-byte boot-style keyboard.
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x03, 0x19, 0x00, 0x2A,
            0xFF.toByte(), 0x03, 0x75, 0x10, 0x95.toByte(), 0x01, 0x81.toByte(), 0x00, 0xC0.toByte(),
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02, 0x95.toByte(), 0x01,
            0x75, 0x08, 0x81.toByte(), 0x01, 0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00,
            0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte()
        )
    }
}
