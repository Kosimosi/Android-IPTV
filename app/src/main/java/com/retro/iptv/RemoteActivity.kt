package com.retro.iptv

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
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
import com.retro.iptv.remote.RetroTvAccessibilityService
import kotlinx.coroutines.delay
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
    private val connectionStatus = mutableStateOf("Enter the TV pairing PIN, then find your TV.")
    private val connected = mutableStateOf(false)
    private val accessibilityEnabled = mutableStateOf(false)
    private val tvName = mutableStateOf("")
    private val pairingPin = mutableStateOf("")
    private val manualAddress = mutableStateOf("")
    private val currentChannel = mutableIntStateOf(-1)
    private val resolution = mutableStateOf("")
    private val channelItems = mutableStateListOf<RemoteChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessibilityEnabled.value = RetroTvAccessibilityService.isEnabled(this)
        remoteClient = MobileRemoteClient(
            context = this,
            onConnectionStateChanged = { isConnected, name ->
                connected.value = isConnected
                connectionStatus.value = name ?: if (isConnected) "Connected" else connectionStatus.value
                if (isConnected) tvName.value = name.orEmpty()
            },
            onStateReceived = ::applyRemoteState
        )
        setContent {
            RemoteControlScreen(
                connectionStatus = connectionStatus.value,
                connected = connected.value,
                accessibilityEnabled = accessibilityEnabled.value,
                onRequestAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                tvName = tvName.value,
                pairingPin = pairingPin.value,
                onPinChange = { pairingPin.value = it.filter(Char::isDigit).take(6) },
                manualAddress = manualAddress.value,
                onAddressChange = { manualAddress.value = it },
                currentChannel = currentChannel.intValue,
                resolution = resolution.value,
                channels = channelItems,
                onDiscover = { remoteClient.startDiscovery(pairingPin.value) },
                onManualConnect = { connectManual() },
                onSend = { command, value -> remoteClient.sendCommand(command, value) },
                onDisconnect = { remoteClient.stop() },
                onBack = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled.value = RetroTvAccessibilityService.isEnabled(this)
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
}

@Composable
private fun RemoteControlScreen(
    connectionStatus: String,
    connected: Boolean,
    accessibilityEnabled: Boolean,
    onRequestAccessibility: () -> Unit,
    tvName: String,
    pairingPin: String,
    onPinChange: (String) -> Unit,
    manualAddress: String,
    onAddressChange: (String) -> Unit,
    currentChannel: Int,
    resolution: String,
    channels: List<RemoteChannel>,
    onDiscover: () -> Unit,
    onManualConnect: () -> Unit,
    onSend: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    var mode by androidx.compose.runtime.remember { mutableStateOf("PAD") }
    var settingsOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var keyboardOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var searchText by androidx.compose.runtime.remember { mutableStateOf("") }
    var searchStarted by androidx.compose.runtime.remember { mutableStateOf(false) }
    var retroActionsOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var accessibilityExplainerOpen by androidx.compose.runtime.remember { mutableStateOf(false) }

    LaunchedEffect(searchText, connected, searchStarted) {
        if (connected && searchStarted) {
            delay(250)
            onSend(RemoteProtocol.CMD_SEARCH, searchText)
        }
    }

    @Composable
    fun circle(label: String, size: androidx.compose.ui.unit.Dp = 62.dp, active: Boolean = false, action: () -> Unit) {
        RemoteActionButton(label, selected = active, enabled = connected, modifier = Modifier.size(size), onClick = action)
    }

    Column(
        Modifier.fillMaxSize().background(OakDarkBackground).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
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
            RemoteActionButton("⏻", enabled = false, modifier = Modifier.size(42.dp)) {}
            RemoteActionButton("⋮", enabled = true, modifier = Modifier.size(42.dp)) { settingsOpen = !settingsOpen }
        }

        if (settingsOpen || !connected) {
            Card(colors = CardDefaults.cardColors(containerColor = OakSurface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION & IR SETTINGS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("CLOSE", color = FocusRed, fontSize = 10.sp, modifier = Modifier.clickable { settingsOpen = false })
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
                        RemoteActionButton("AUTO DISCOVER", modifier = Modifier.weight(1f), enabled = true) { onDiscover() }
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
                    Text("IR power / brand profiles require a compatible phone IR emitter. This control is not enabled on unsupported phones.",
                        color = WarmAshMuted, fontSize = 10.sp)
                    Text(
                        if (accessibilityEnabled) "FULL TV CONTROL ACCESS ENABLED" else "FULL TV CONTROL ACCESS OPTIONAL",
                        color = if (accessibilityEnabled) Color(0xFF45D483) else WarmAshMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RemoteActionButton(
                        if (accessibilityEnabled) "ACCESSIBILITY SETTINGS" else "ENABLE FULL TV CONTROL",
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) { accessibilityExplainerOpen = true }
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
                "PAD" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    circle("⌃", 62.dp) { onSend(RemoteProtocol.CMD_DPAD, "UP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        circle("‹", 68.dp) { onSend(RemoteProtocol.CMD_DPAD, "LEFT") }
                        Box(Modifier.size(78.dp).background(FocusRed, CircleShape).clickable(enabled = connected) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSend(RemoteProtocol.CMD_DPAD, "OK")
                        }, contentAlignment = Alignment.Center) {
                            Text("OK", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        }
                        circle("›", 68.dp) { onSend(RemoteProtocol.CMD_DPAD, "RIGHT") }
                    }
                    circle("⌄", 62.dp) { onSend(RemoteProtocol.CMD_DPAD, "DOWN") }
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
                                        -1 -> onSend(RemoteProtocol.CMD_DPAD, "BACK")
                                        -2 -> onSend(RemoteProtocol.CMD_DPAD, "OK")
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
                value = searchText, onValueChange = { searchText = it; searchStarted = true },
                modifier = Modifier.fillMaxWidth(), enabled = connected, singleLine = true,
                label = { Text("Instant channel search") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    cursorColor = FocusRed, focusedBorderColor = FocusRed, unfocusedBorderColor = Color.White.copy(alpha = .25f),
                    focusedLabelColor = FocusRed, unfocusedLabelColor = WarmAshMuted)
            )
        }

        // Ergonomic transport row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RemoteActionButton("←", modifier = Modifier.weight(1f), enabled = connected) { onSend(RemoteProtocol.CMD_DPAD, "BACK") }
            Spacer(Modifier.width(8.dp))
            RemoteActionButton("⌂", modifier = Modifier.weight(1.35f).height(54.dp), enabled = true) {
                onSend(RemoteProtocol.CMD_DPAD, "HOME")
            }
            Spacer(Modifier.width(8.dp))
            RemoteActionButton("≡", modifier = Modifier.weight(1f), enabled = connected) { mode = "LIVE" }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteActionButton("🔇", modifier = Modifier.weight(.8f), enabled = connected) { onSend(RemoteProtocol.CMD_MEDIA, "MUTE") }
            RemoteActionButton("🔉  VOL −", modifier = Modifier.weight(1.2f), enabled = connected) { onSend(RemoteProtocol.CMD_MEDIA, "VOL_DOWN") }
            RemoteActionButton("VOL +  🔊", modifier = Modifier.weight(1.2f), enabled = connected) { onSend(RemoteProtocol.CMD_MEDIA, "VOL_UP") }
            RemoteActionButton("⌨", modifier = Modifier.weight(.8f), enabled = connected) { keyboardOpen = !keyboardOpen }
        }

        // Branded launch pill and secondary shortcuts.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteActionButton("RETRO TV", selected = true, enabled = true, modifier = Modifier.weight(1.6f).height(52.dp)) {
                onSend(RemoteProtocol.CMD_DPAD, "HOME")
            }
            RemoteActionButton("▶ YouTube", enabled = false, modifier = Modifier.weight(1f)) {}
            RemoteActionButton("123", enabled = true, modifier = Modifier.weight(.65f)) { mode = "NUM" }
        }
        if (retroActionsOpen) {
            Text("NEWS TICKER", color = Color.White, modifier = Modifier.fillMaxWidth().clickable(enabled = connected) {
                onSend(RemoteProtocol.CMD_MEDIA, "TICKER_TOGGLE")
                retroActionsOpen = false
            }.padding(8.dp))
            Text("SCORE CARD  ·  FM RADIO (NOT AVAILABLE YET)", color = WarmAshMuted, fontSize = 10.sp)
        }
        if (accessibilityExplainerOpen) {
            Dialog(onDismissRequest = { accessibilityExplainerOpen = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OakSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("RETROTV ACCESSIBILITY", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            "RetroTV needs Accessibility access so your phone can act as a full TV remote — navigate Android TV's home screen and type search queries.",
                            color = WarmAshMuted,
                            fontSize = 13.sp
                        )
                        RemoteActionButton("OPEN ACCESSIBILITY SETTINGS", enabled = true, modifier = Modifier.fillMaxWidth()) {
                            accessibilityExplainerOpen = false
                            onRequestAccessibility()
                        }
                        RemoteActionButton("NOT NOW", enabled = true, modifier = Modifier.fillMaxWidth()) {
                            accessibilityExplainerOpen = false
                        }
                    }
                }
            }
        }

        Text(
            "RETRO TV  ·  ${if (connected) "SECURELY PAIRED ON LOCAL WI-FI" else "WAITING FOR TV"}",
            color = WarmAshMuted, fontSize = 9.sp, modifier = Modifier.fillMaxWidth().clickable { retroActionsOpen = !retroActionsOpen },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
