@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.retro.iptv

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.retro.iptv.remote.IrRemoteManager
import com.retro.iptv.remote.IrdbFetcher
import com.retro.iptv.remote.IrdbEntry
import com.retro.iptv.remote.IrdbCode
import com.retro.iptv.remote.DeviceCategory
import com.retro.iptv.remote.IrProfile
import com.retro.iptv.remote.BluetoothHidRemoteManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retro.iptv.remote.MobileRemoteClient
import com.retro.iptv.remote.RemoteProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class RemoteChannel(
    val number: Int,
    val title: String,
    val group: String,
    val logo: String,
    val status: String
)

class RemoteActivity : ComponentActivity() {
    private lateinit var remoteClient: MobileRemoteClient
    private lateinit var irRemoteManager: IrRemoteManager
    private lateinit var btHidManager: BluetoothHidRemoteManager
    private val btHidConnected = mutableStateOf(false)
    private val btHidStatus = mutableStateOf("Bluetooth HID not connected")
    private val requestBtPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) enableBluetoothHid()
        else btHidStatus.value = "Bluetooth permissions are required for HID remote"
    }
    private val discoverableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            btHidManager.initialize()
            btHidManager.connectFirstPairedHost()
            btHidStatus.value = "Phone discoverable · select RETROTV Remote in TV Bluetooth settings"
        } else {
            btHidStatus.value = "Bluetooth pairing was cancelled"
        }
    }
    private val connectionStatus = mutableStateOf("Enter the TV pairing PIN, then find your TV.")
    private val connected = mutableStateOf(false)
    private val hasIrEmitter = mutableStateOf(false)
    private val tvName = mutableStateOf("")
    private val pairingPin = mutableStateOf("")
    private val manualAddress = mutableStateOf("")
    private val isRemoteSetupComplete = mutableStateOf(false)
    private val inRetroTv = mutableStateOf(true)
    private val currentChannel = mutableIntStateOf(-1)
    private val resolution = mutableStateOf("")
    private val channelItems = mutableStateListOf<RemoteChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        irRemoteManager = IrRemoteManager(this)
        hasIrEmitter.value = irRemoteManager.hasIrBlaster()
        btHidManager = BluetoothHidRemoteManager(this) { active, status ->
            runOnUiThread {
                btHidConnected.value = active
                btHidStatus.value = status
            }
        }
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            btHidManager.initialize()
            btHidManager.connectFirstPairedHost()
        }
        val setupPrefs = getSharedPreferences(REMOTE_SETUP_PREFS, MODE_PRIVATE)
        isRemoteSetupComplete.value = setupPrefs.getBoolean(KEY_SETUP_COMPLETE, false)
        pairingPin.value = setupPrefs.getString(KEY_SAVED_TV_PIN, "").orEmpty()
        val savedIp = setupPrefs.getString(KEY_SAVED_TV_IP, "").orEmpty()
        val savedPort = setupPrefs.getInt(KEY_SAVED_TV_PORT, 8899)
        manualAddress.value = savedIp.takeIf { it.isNotBlank() }?.let { host ->
            if (host.contains(":")) "[$host]:$savedPort" else "$host:$savedPort"
        }.orEmpty()
        remoteClient = MobileRemoteClient(
            context = this,
            onConnectionStateChanged = { isConnected, name ->
                connected.value = isConnected
                connectionStatus.value = name ?: if (isConnected) "Connected" else connectionStatus.value
                if (isConnected) tvName.value = name.orEmpty()
            },
            onStateReceived = ::applyRemoteState,
            onConnectionDetails = { host, port, pin ->
                setupPrefs.edit()
                    .putBoolean(KEY_SETUP_COMPLETE, true)
                    .putString(KEY_SAVED_TV_IP, host)
                    .putInt(KEY_SAVED_TV_PORT, port)
                    .putString(KEY_SAVED_TV_PIN, pin)
                    .putString(KEY_SAVED_TV_BRAND, irRemoteManager.selectedProfile.name)
                    .apply()
                pairingPin.value = pin
                manualAddress.value = if (host.contains(":")) "[$host]:$port" else "$host:$port"
                isRemoteSetupComplete.value = true
            }
        )
        setContent {
            RemoteControlScreen(
                connectionStatus = connectionStatus.value,
                connected = connected.value,
                hasIrEmitter = hasIrEmitter.value,
                btHidConnected = btHidConnected.value,
                btHidStatus = btHidStatus.value,
                onEnableBluetooth = { requestBluetoothHidPermissions() },
                onHidAction = { action -> sendHidAction(action) },
                onTypeText = { text -> if (btHidManager.isConnected()) btHidManager.typeText(text) },
                setupComplete = isRemoteSetupComplete.value,
                inRetroTv = inRetroTv.value,
                tvName = tvName.value,
                pairingPin = pairingPin.value,
                onPinChange = { pairingPin.value = it.filter(Char::isDigit).take(6) },
                manualAddress = manualAddress.value,
                onAddressChange = { manualAddress.value = it },
                currentChannel = currentChannel.intValue,
                resolution = resolution.value,
                channels = channelItems,
                onDiscover = { remoteClient.startDiscovery(pairingPin.value) },
                onDiscoveryTimeout = { remoteClient.cancelDiscovery() },
                onManualConnect = { connectManual() },
                onSend = { command, value -> remoteClient.sendCommand(command, value) },
                onDisconnect = { remoteClient.stop() },
                onBack = { finish() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (isRemoteSetupComplete.value && !connected.value) {
            val prefs = getSharedPreferences(REMOTE_SETUP_PREFS, MODE_PRIVATE)
            val host = prefs.getString(KEY_SAVED_TV_IP, "").orEmpty()
            val port = prefs.getInt(KEY_SAVED_TV_PORT, 8899)
            val pin = prefs.getString(KEY_SAVED_TV_PIN, "").orEmpty()
            if (host.isNotBlank() && pin.length == 6) remoteClient.connectManual(host, port, pin)
        }
    }

    override fun onDestroy() {
        if (::btHidManager.isInitialized) btHidManager.releaseResources()
        super.onDestroy()
    }

    private fun requestBluetoothHidPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                requestBtPermissions.launch(missing.toTypedArray())
                return
            }
        }
        enableBluetoothHid()
    }

    private fun enableBluetoothHid() {
        runCatching { discoverableLauncher.launch(btHidManager.requestDiscoverableIntent()) }
            .onFailure { btHidStatus.value = "Bluetooth HID profile or discoverability is unavailable on this phone" }
    }

    private fun sendHidAction(action: String): Boolean {
        if (!btHidManager.isConnected()) return false
        return when (action) {
            "UP" -> btHidManager.sendConsumerUsage(0x0042)
            "DOWN" -> btHidManager.sendConsumerUsage(0x0043)
            "LEFT" -> btHidManager.sendConsumerUsage(0x0044)
            "RIGHT" -> btHidManager.sendConsumerUsage(0x0045)
            "OK" -> btHidManager.sendConsumerUsage(0x0041)
            "BACK" -> btHidManager.sendConsumerUsage(0x0224)
            "HOME" -> btHidManager.sendConsumerUsage(0x0223)
            "VOL_UP" -> btHidManager.sendConsumerUsage(0x00E9)
            "VOL_DOWN" -> btHidManager.sendConsumerUsage(0x00EA)
            "POWER" -> btHidManager.sendConsumerUsage(0x0030)
            "MUTE" -> btHidManager.sendConsumerUsage(0x00E2)
            "ENTER" -> btHidManager.sendEnter()
            "BACKSPACE" -> btHidManager.sendBackspace()
            else -> false
        }
    }

    override fun onStop() {
        remoteClient.stop()
        super.onStop()
    }

    private fun connectManual() {
        val address = manualAddress.value.trim()
        val host = address.substringBeforeLast(':', address).trim()
        val port = address.substringAfterLast(':', "8899").toIntOrNull() ?: 8899
        remoteClient.connectManual(host, port, pairingPin.value)
    }

    private fun applyRemoteState(raw: String) {
        runCatching {
            val json = JSONObject(raw)
            inRetroTv.value = json.optBoolean("inRetroTv", true)
            currentChannel.intValue = json.optInt("currentChannel", -1)
            resolution.value = json.optString("resolution").takeIf { it.isNotBlank() }.orEmpty()
            val array = json.optJSONArray("channels") ?: return
            val updated = ArrayList<RemoteChannel>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                updated += RemoteChannel(
                    number = item.optInt("number", index + 1),
                    title = item.optString("title"),
                    group = item.optString("group"),
                    logo = item.optString("logo"),
                    status = item.optString("status")
                )
            }
            channelItems.clear()
            channelItems.addAll(updated)
        }
    }

    companion object {
        private const val REMOTE_SETUP_PREFS = "retro_mobile_remote_setup"
        private const val KEY_SETUP_COMPLETE = "isRemoteSetupComplete"
        private const val KEY_SAVED_TV_IP = "savedTvIp"
        private const val KEY_SAVED_TV_PIN = "savedTvPin"
        private const val KEY_SAVED_TV_BRAND = "savedTvBrand"
        private const val KEY_SAVED_TV_PORT = "savedTvPort"
    }
}

@Composable
private fun RemoteControlScreen(
    connectionStatus: String,
    connected: Boolean,
    hasIrEmitter: Boolean,
    setupComplete: Boolean,
    inRetroTv: Boolean,
    tvName: String,
    pairingPin: String,
    onPinChange: (String) -> Unit,
    manualAddress: String,
    onAddressChange: (String) -> Unit,
    currentChannel: Int,
    resolution: String,
    channels: List<RemoteChannel>,
    onDiscover: () -> Unit,
    onDiscoveryTimeout: () -> Unit,
    onManualConnect: () -> Unit,
    onSend: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    btHidConnected: Boolean,
    btHidStatus: String,
    onEnableBluetooth: () -> Unit,
    onHidAction: (String) -> Boolean,
    onTypeText: (String) -> Unit
) {
    val view = LocalView.current
    var mode by androidx.compose.runtime.remember { mutableStateOf("PAD") }
    var isPairingExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    val context = LocalContext.current
    val irRemoteManager = androidx.compose.runtime.remember { IrRemoteManager(context) }
    val irBlasterDetected = irRemoteManager.hasIrBlaster()
    val setupPrefs = androidx.compose.runtime.remember {
        context.getSharedPreferences("retro_mobile_remote_setup", android.content.Context.MODE_PRIVATE)
    }
    var selectedCategory by androidx.compose.runtime.remember { mutableStateOf(irRemoteManager.selectedCategory) }
    var selectedProfile by androidx.compose.runtime.remember { mutableStateOf(irRemoteManager.selectedProfile) }
    var setupStep by androidx.compose.runtime.remember { mutableIntStateOf(if (irBlasterDetected) 1 else 2) }
    var manualConnectVisible by androidx.compose.runtime.remember { mutableStateOf(false) }
    var scanStartedPin by androidx.compose.runtime.remember { mutableStateOf("") }
    var irFeedback by androidx.compose.runtime.remember { mutableStateOf("") }
    var irTestFeedback by androidx.compose.runtime.remember { mutableStateOf("") }
    val irdbFetcher = androidx.compose.runtime.remember { IrdbFetcher(context) }
    val coroutineScope = rememberCoroutineScope()
    var irdbEntries by androidx.compose.runtime.remember { mutableStateOf<List<IrdbEntry>>(emptyList()) }
    var irdbCodes by androidx.compose.runtime.remember { mutableStateOf<List<IrdbCode>>(emptyList()) }
    var irdbPickerOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var irdbSearch by androidx.compose.runtime.remember { mutableStateOf("") }
    var irdbLoading by androidx.compose.runtime.remember { mutableStateOf(false) }
    var irdbStatus by androidx.compose.runtime.remember { mutableStateOf("") }
    var selectedIrdbLabel by androidx.compose.runtime.remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        selectedCategory = irRemoteManager.selectedCategory
        selectedProfile = irRemoteManager.selectedProfile
    }
    androidx.compose.runtime.LaunchedEffect(pairingPin, setupStep, setupComplete, connected) {
        if (!setupComplete && setupStep == 2 && pairingPin.length == 6 && !connected && scanStartedPin != pairingPin) {
            scanStartedPin = pairingPin
            manualConnectVisible = false
            onDiscover()
            delay(4000)
            if (!connected) {
                onDiscoveryTimeout()
                manualConnectVisible = true
            }
        }
    }
    var keyboardOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var searchText by androidx.compose.runtime.remember { mutableStateOf("") }
    var searchStarted by androidx.compose.runtime.remember { mutableStateOf(false) }
    var retroActionsOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    val dpadEnabled = btHidConnected || (connected && inRetroTv) || irBlasterDetected

    fun sendIrAction(action: String): Boolean {
        if (!irBlasterDetected || btHidConnected) return false
        val databaseCode = IrdbFetcher.findCode(irdbCodes, action)
        if (databaseCode != null && irRemoteManager.transmitIrdbCode(databaseCode)) {
            irdbStatus = "IRDB · " + selectedIrdbLabel + " · " + action + " sent"
            return true
        }
        val sent = irRemoteManager.sendKey(action, selectedProfile)
        if (sent) irdbStatus = "Built-in " + selectedProfile.label + " IR · " + action + " sent"
        else if (databaseCode != null) irdbStatus = "IRDB protocol " + databaseCode.protocol + " is not supported yet"
        else if (irdbCodes.isNotEmpty()) irdbStatus = "No " + action + " code in selected IRDB model"
        return sent
    }

    fun sendDpad(action: String) {
        if (btHidConnected) onHidAction(action)
        else if (connected) onSend(RemoteProtocol.CMD_DPAD, action)
        else sendIrAction(action)
    }
    fun sendTransport(hidAction: String, command: String, value: String) {
        if (btHidConnected) onHidAction(hidAction)
        else if (connected) onSend(command, value)
        else sendIrAction(hidAction)
    }

    LaunchedEffect(searchText, connected, searchStarted) {
        if (connected && searchStarted && !btHidConnected) {
            delay(250)
            onSend(RemoteProtocol.CMD_SEARCH, searchText)
        }
    }

    @Composable
    fun circle(label: String, size: androidx.compose.ui.unit.Dp = 62.dp, active: Boolean = false, action: () -> Unit) {
        RemoteActionButton(label, selected = active, enabled = connected, modifier = Modifier.size(size), onClick = action)
    }

    Column(
        Modifier.fillMaxSize().background(OakDarkBackground).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (!setupComplete) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("RETRO TV REMOTE SETUP", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("STEP $setupStep OF 2", color = FocusRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (setupStep == 1 && irBlasterDetected) {
                    Text("IR Blaster Detected", color = Color(0xFF45D483), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Choose device type and brand, then test the power signal.", color = WarmAshMuted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().background(OakDarkBackground, RoundedCornerShape(10.dp)).padding(4.dp)) {
                        DeviceCategory.values().forEach { option ->
                            Box(
                                Modifier.weight(1f).background(
                                    if (selectedCategory == option) FocusRed else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ).clickable {
                                    selectedCategory = option
                                    irRemoteManager.selectedCategory = option
                                    selectedProfile = irRemoteManager.profilesFor(option).first()
                                    irRemoteManager.selectedProfile = selectedProfile
                                }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(option.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        items(irRemoteManager.profilesFor(selectedCategory)) { brand ->
                            FilterChip(
                                selected = selectedProfile == brand,
                                onClick = {
                                    selectedProfile = brand
                                    selectedCategory = brand.category
                                    irRemoteManager.selectedProfile = brand
                                },
                                label = { Text(brand.label, fontSize = 11.sp) }
                            )
                        }
                    }
                    RemoteActionButton("TEST POWER", modifier = Modifier.fillMaxWidth(), enabled = true) {
                        irTestFeedback = if (sendIrAction("POWER")) {
                            "IR signal sent. Check whether your TV responds."
                        } else {
                            "IR transmission failed. Check the phone's IR hardware."
                        }
                    }
                    if (irTestFeedback.isNotBlank()) Text(irTestFeedback, color = WarmAshMuted, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteActionButton("SKIP", modifier = Modifier.weight(1f), enabled = true) { setupStep = 2 }
                        RemoteActionButton("IT WORKED → NEXT", modifier = Modifier.weight(2f), enabled = true) {
                            irRemoteManager.selectedProfile = selectedProfile
                            setupStep = 2
                        }
                    }
                } else {
                    Text("Connect your TV", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Keep your phone and TV on the same Wi-Fi. Open Retro TV on the television.",
                        color = WarmAshMuted, fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = pairingPin,
                        onValueChange = onPinChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6-digit PIN shown in Retro TV settings") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = FocusRed, focusedBorderColor = FocusRed,
                            unfocusedBorderColor = Color.White.copy(alpha = .25f),
                            focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted
                        )
                    )
                    Text(
                        if (connected) "Pairing successful. Saving your TV…" else if (pairingPin.length == 6 && !manualConnectVisible)
                            "Searching for RETROTV over Wi-Fi for 4 seconds…"
                        else "Enter the TV's 6-digit pairing PIN to start automatic discovery.",
                        color = if (connected) Color(0xFF45D483) else WarmAshMuted, fontSize = 11.sp
                    )
                    if (manualConnectVisible && !connected) {
                        Text("Automatic discovery timed out. Enter the TV address and PIN manually.", color = Color(0xFFFFC46B), fontSize = 11.sp)
                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = onAddressChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("TV IP:port (example 192.168.1.20:8899)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = FocusRed, focusedBorderColor = FocusRed,
                                unfocusedBorderColor = Color.White.copy(alpha = .25f),
                                focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted
                            )
                        )
                        RemoteActionButton("CONNECT WITH IP + PIN", modifier = Modifier.fillMaxWidth(), enabled = pairingPin.length == 6) {
                            onManualConnect()
                        }
                    }
                    RemoteActionButton("BLUETOOTH PAIR TV", modifier = Modifier.fillMaxWidth(), enabled = true, onClick = onEnableBluetooth)
                    Text(connectionStatus, color = WarmAshMuted, fontSize = 10.sp)
                }
            }
        } else {
        // Compact status bar: pairing details stay out of the way once connected.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                Box(Modifier.size(40.dp).background(FocusRed, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("▣", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("RETRO TV", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        if (connected) "● Connected · ${tvName.ifBlank { "RETROTV" }}" else connectionStatus,
                        color = if (connected) Color(0xFF45D483) else WarmAshMuted,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
            RemoteIconButton(Icons.Rounded.PowerSettingsNew, "Power", tint = Color(0xFFE11D48), enabled = true) {
                if (btHidConnected) {
                    onHidAction("POWER")
                } else if (connected) {
                    onSend(RemoteProtocol.CMD_MEDIA, "WAKE_SLEEP")
                } else {
                    irFeedback = if (sendIrAction("POWER")) "IR power signal sent · check TV response" else "IR transmission failed"
                }
            }
            if (hasIrEmitter) {
                RemoteIconButton(Icons.Rounded.Menu, "TV source/input", tint = Color(0xFFE5E5EA), enabled = true) {
                    irFeedback = if (irRemoteManager.sendSourceInput(selectedProfile)) "IR source command sent · check TV response" else "IR transmission failed"
                }
            }
            RemoteIconButton(Icons.Rounded.Tune, "Pairing settings", tint = Color(0xFFE5E5EA), enabled = true) {
                isPairingExpanded = !isPairingExpanded
            }
        }

        AnimatedVisibility(visible = isPairingExpanded) {
            Card(colors = CardDefaults.cardColors(containerColor = OakSurface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION & IR SETTINGS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("CLOSE", color = FocusRed, fontSize = 10.sp, modifier = Modifier.clickable { isPairingExpanded = false })
                    }
                    OutlinedTextField(
                        value = pairingPin, onValueChange = onPinChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text("6-digit TV pairing PIN") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = FocusRed, focusedBorderColor = FocusRed, unfocusedBorderColor = Color.White.copy(alpha = .25f),
                            focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteActionButton("AUTO DISCOVER", modifier = Modifier.weight(1f), enabled = true) {
                            onDiscover()
                        }
                        RemoteActionButton("DISCONNECT", modifier = Modifier.weight(1f), enabled = connected) { onDisconnect() }
                    }
                    OutlinedTextField(
                        value = manualAddress, onValueChange = onAddressChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manual TV IP:port (fallback)") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = FocusRed, focusedBorderColor = FocusRed, unfocusedBorderColor = Color.White.copy(alpha = .25f),
                            focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted)
                    )
                    RemoteActionButton("CONNECT WITH IP + PIN", enabled = true, modifier = Modifier.fillMaxWidth()) { onManualConnect() }
                    RemoteActionButton("BLUETOOTH PAIR TV", enabled = true, modifier = Modifier.fillMaxWidth(), onClick = onEnableBluetooth)
                    Text(if (btHidConnected) "Bluetooth HID connected" else btHidStatus, color = if (btHidConnected) Color(0xFF45D483) else WarmAshMuted, fontSize = 10.sp)
                    Text(
                        if (hasIrEmitter) "IR BLASTER AVAILABLE · SELECT TV BRAND" else "NO IR BLASTER ON THIS PHONE · WI-FI REMOTE ACTIVE",
                        color = if (hasIrEmitter) Color(0xFF45D483) else WarmAshMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (hasIrEmitter) {
                        Row(Modifier.fillMaxWidth().background(OakDarkBackground, RoundedCornerShape(10.dp)).padding(4.dp)) {
                            DeviceCategory.values().forEach { option ->
                                Box(
                                    Modifier.weight(1f).background(
                                        if (selectedCategory == option) FocusRed else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    ).clickable {
                                        selectedCategory = option
                                        irRemoteManager.selectedCategory = option
                                        selectedProfile = irRemoteManager.profilesFor(option).first()
                                        irRemoteManager.selectedProfile = selectedProfile
                                    }.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(option.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(irRemoteManager.profilesFor(selectedCategory)) { profile ->
                                FilterChip(
                                    selected = selectedProfile == profile,
                                    onClick = { selectedProfile = profile; irRemoteManager.selectedProfile = profile },
                                    label = { Text(profile.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        RemoteActionButton(
                            if (irdbLoading && irdbEntries.isEmpty()) "LOADING IRDB…" else "MORE BRANDS (IRDB)",
                            modifier = Modifier.fillMaxWidth(), enabled = !irdbLoading
                        ) {
                            irdbLoading = true
                            coroutineScope.launch {
                                runCatching { if (irdbEntries.isEmpty()) irdbFetcher.fetchIndex() else irdbEntries }
                                    .onSuccess { irdbEntries = it; irdbPickerOpen = true; irdbSearch = "" }
                                    .onFailure { irdbStatus = "IRDB index unavailable: ${it.message}" }
                                irdbLoading = false
                            }
                        }
                        if (selectedIrdbLabel.isNotBlank()) Text("IRDB DEVICE · $selectedIrdbLabel", color = Color(0xFF45D483), fontSize = 10.sp)
                        if (irdbStatus.isNotBlank()) Text(irdbStatus, color = WarmAshMuted, fontSize = 10.sp)
                        if (irFeedback.isNotBlank()) Text(irFeedback, color = WarmAshMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        if (irdbPickerOpen) {
            Dialog(onDismissRequest = { irdbPickerOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OakSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("IRDB BRANDS & MODELS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        OutlinedTextField(
                            value = irdbSearch, onValueChange = { irdbSearch = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Search brand, device type or model") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = FocusRed, focusedBorderColor = FocusRed,
                                unfocusedBorderColor = Color.White.copy(alpha = .25f),
                                focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted
                            )
                        )
                        Text("${irdbEntries.size} IRDB device profiles · select a model CSV", color = WarmAshMuted, fontSize = 10.sp)
                        LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(
                                irdbEntries.filter {
                                    "${it.brand} ${it.deviceType} ${it.model}".contains(irdbSearch, ignoreCase = true)
                                }.take(500),
                                key = { it.filePath }
                            ) { entry ->
                                Text(
                                    entry.displayName,
                                    color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        irdbPickerOpen = false
                                        irdbLoading = true
                                        selectedIrdbLabel = entry.displayName
                                        coroutineScope.launch {
                                            runCatching { irdbFetcher.loadCodes(entry) }
                                                .onSuccess {
                                                    irdbCodes = it
                                                    irdbStatus = "Loaded ${it.size} IR codes from ${entry.filePath}"
                                                }
                                                .onFailure { irdbStatus = "Could not load IRDB CSV: ${it.message}" }
                                            irdbLoading = false
                                        }
                                    }.padding(vertical = 9.dp, horizontal = 6.dp)
                                )
                            }
                        }
                        RemoteActionButton("CLOSE", modifier = Modifier.fillMaxWidth(), enabled = true) {
                            irdbPickerOpen = false
                        }
                    }
                }
            }
        }

        if (connected) {
            Text(
                "NOW PLAYING  ·  ${channels.getOrNull(currentChannel)?.title ?: "Live TV"}${if (resolution.isNotBlank()) "  ·  $resolution" else ""}",
                color = WarmAshMuted, fontSize = 10.sp, maxLines = 1
            )
        }

        // Mode switch controls the centre area without moving the rest of the remote.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            listOf("PAD" to "D-PAD", "NUM" to "123", "LIVE" to "LIVE CHANNELS").forEach { (key, label) ->
                Box(
                    Modifier.padding(horizontal = 3.dp).background(if (mode == key) FocusRed else OakSurfaceElevated, CircleShape)
                        .clickable { mode = key }.padding(horizontal = 15.dp, vertical = 8.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (mode) {
                "PAD" -> Box(
                    Modifier.size(240.dp).background(Color(0xFF16161D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    RemoteIconButton(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter).padding(top = 8.dp), tint = if (dpadEnabled) Color(0xFFF4F4F6) else WarmAshMuted.copy(alpha = .38f), enabled = dpadEnabled) { sendDpad("UP") }
                    RemoteIconButton(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), tint = if (dpadEnabled) Color(0xFFF4F4F6) else WarmAshMuted.copy(alpha = .38f), enabled = dpadEnabled) { sendDpad("DOWN") }
                    RemoteIconButton(Icons.Rounded.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart).padding(start = 8.dp), tint = if (dpadEnabled) Color(0xFFF4F4F6) else WarmAshMuted.copy(alpha = .38f), enabled = dpadEnabled) { sendDpad("LEFT") }
                    RemoteIconButton(Icons.Rounded.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), tint = if (dpadEnabled) Color(0xFFF4F4F6) else WarmAshMuted.copy(alpha = .38f), enabled = dpadEnabled) { sendDpad("RIGHT") }
                    Box(Modifier.size(76.dp).background(Color(0xFFE11D48), CircleShape).clickable(enabled = connected || btHidConnected || irBlasterDetected) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        sendDpad("OK")
                    }, contentAlignment = Alignment.Center) {
                        Text("OK", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                }
                "NUM" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            (0..2).forEach { col ->
                                val digit = when {
                                    row < 3 -> row * 3 + col + 1
                                    col == 0 -> -1
                                    col == 1 -> 0
                                    else -> -2
                                }
                                RemoteActionButton(if (digit == -1) "⌫" else if (digit == -2) "↵" else digit.toString(),
                                    enabled = connected, modifier = Modifier.size(62.dp)) {
                                    when (digit) {
                                        -1 -> sendTransport("BACK", RemoteProtocol.CMD_DPAD, "BACK")
                                        -2 -> sendDpad("OK")
                                        else -> onSend(RemoteProtocol.CMD_NUMBER, digit.toString())
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (channels.isEmpty()) {
                        Text(if (connected) "Waiting for live channel list…" else "Connect to RETRO TV", color = WarmAshMuted)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(channels, key = { it.number }) { channel ->
                                Row(
                                    Modifier.fillMaxWidth().background(
                                        if (channel.number - 1 == currentChannel) ChannelCardFocusedFill else OakSurface,
                                        RoundedCornerShape(10.dp)
                                    ).clickable(enabled = connected) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onSend(RemoteProtocol.CMD_CHANNEL_ID, channel.number.toString())
                                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(channel.number.toString().padStart(3, '0'), color = FocusRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(channel.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(channel.group, color = WarmAshMuted, fontSize = 10.sp)
                                    }
                                    if (channel.number - 1 == currentChannel) Text("LIVE", color = FocusRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (keyboardOpen) {
            OutlinedTextField(
                value = searchText, onValueChange = {
                    if (btHidConnected) {
                        val common = searchText.zip(it).takeWhile { pair -> pair.first == pair.second }.size
                        val removed = "\b".repeat(searchText.length - common)
                        val added = if (it.length > common) it.substring(common) else ""
                        if (removed.isNotEmpty() || added.isNotEmpty()) onTypeText(removed + added)
                    }
                    searchText = it
                    searchStarted = true
                },
                modifier = Modifier.fillMaxWidth(), enabled = connected || btHidConnected, singleLine = true,
                label = { Text("Instant channel search") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    cursorColor = FocusRed, focusedBorderColor = FocusRed, unfocusedBorderColor = Color.White.copy(alpha = .25f),
                    focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted)
            )
        }

        // Ergonomic transport row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RemoteIconTextButton(Icons.Rounded.ArrowBack, "BACK", Modifier.weight(1f), connected || btHidConnected || irBlasterDetected) { sendTransport("BACK", RemoteProtocol.CMD_DPAD, "BACK") }
            Spacer(Modifier.width(8.dp))
            RemoteIconTextButton(Icons.Rounded.Home, "HOME", Modifier.weight(1.35f).height(54.dp), connected || btHidConnected || irBlasterDetected) { sendTransport("HOME", RemoteProtocol.CMD_DPAD, "HOME") }
            Spacer(Modifier.width(8.dp))
            RemoteIconTextButton(Icons.Rounded.Menu, "MENU", Modifier.weight(1f), connected) { mode = "LIVE" }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteIconTextButton(Icons.Rounded.VolumeOff, "MUTE", Modifier.weight(.9f), connected || btHidConnected || irBlasterDetected) { sendTransport("MUTE", RemoteProtocol.CMD_MEDIA, "MUTE") }
            RemoteIconTextButton(Icons.Rounded.VolumeDown, "VOL −", Modifier.weight(1.1f), connected || btHidConnected || irBlasterDetected) { sendTransport("VOL_DOWN", RemoteProtocol.CMD_MEDIA, "VOL_DOWN") }
            RemoteIconTextButton(Icons.Rounded.VolumeUp, "VOL +", Modifier.weight(1.1f), connected || btHidConnected || irBlasterDetected) { sendTransport("VOL_UP", RemoteProtocol.CMD_MEDIA, "VOL_UP") }
            RemoteIconTextButton(Icons.Rounded.Keyboard, "KEYS", Modifier.weight(.9f), connected || btHidConnected) { keyboardOpen = !keyboardOpen }
        }

        // Branded launch pill and secondary shortcuts.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteLaunchPill("RETRO TV", Modifier.weight(1.25f), selected = true,
                onClick = { onSend(RemoteProtocol.CMD_LAUNCH_RETROTV, RemoteProtocol.TARGET_HOME) },
                onLongClick = { retroActionsOpen = true })
            RemoteLaunchPill("YouTube", Modifier.weight(1f), onClick = {
                onSend(RemoteProtocol.CMD_LAUNCH_RETROTV, RemoteProtocol.TARGET_YOUTUBE)
            })
            RemoteLaunchPill("SCORE", Modifier.weight(.8f), onClick = {
                onSend(RemoteProtocol.CMD_LAUNCH_RETROTV, RemoteProtocol.TARGET_SCORE_CARD)
            })
        }
        if (retroActionsOpen) {
            ModalBottomSheet(onDismissRequest = { retroActionsOpen = false }, containerColor = OakSurface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RETRO TV SHORTCUTS", color = Color.White, fontWeight = FontWeight.Black)
                    listOf(
                        "SCORE CARD" to RemoteProtocol.TARGET_SCORE_CARD,
                        "NEWS TICKER" to RemoteProtocol.TARGET_NEWS_TICKER,
                        "FM RADIO" to RemoteProtocol.TARGET_FM_RADIO
                    ).forEach { (label, target) ->
                        RemoteActionButton(label, modifier = Modifier.fillMaxWidth(), enabled = true) {
                            onSend(RemoteProtocol.CMD_LAUNCH_RETROTV, target)
                            retroActionsOpen = false
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        Text(
            if (!inRetroTv) "External app • Tap RETRO TV to return"
            else "RETRO TV  ·  ${if (connected) "SECURELY PAIRED ON LOCAL WI-FI" else "WAITING FOR TV"}",
            color = if (!inRetroTv) Color(0xFFFFC46B) else WarmAshMuted,
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().clickable { retroActionsOpen = !retroActionsOpen },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        }
    }
}

@Composable
private fun RemoteIconButton(
    image: ImageVector,
    description: String,
    modifier: Modifier = Modifier.size(42.dp),
    tint: Color = Color(0xFFF4F4F6),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.background(OakSurfaceElevated, CircleShape)) {
        Icon(imageVector = image, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RemoteIconTextButton(
    image: ImageVector,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OakSurfaceElevated, contentColor = Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(image, contentDescription = label, tint = Color(0xFFF4F4F6), modifier = Modifier.size(22.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RemoteLaunchPill(
    label: String,
    modifier: Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(modifier.height(52.dp).background(if (selected) Color(0xFFE11D48) else OakSurfaceElevated, RoundedCornerShape(12.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RemoteActionButton(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.height(46.dp),
    onClick: () -> Unit
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) FocusRed else OakSurfaceElevated,
            contentColor = Color.White,
            disabledContainerColor = OakSurface.copy(alpha = 0.45f),
            disabledContentColor = WarmAshMuted
        )
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
