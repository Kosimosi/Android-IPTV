package com.retro.iptv

import android.os.Bundle
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
import com.retro.iptv.remote.MobileRemoteClient
import com.retro.iptv.remote.RemoteProtocol
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
    private val tvName = mutableStateOf("")
    private val pairingPin = mutableStateOf("")
    private val manualAddress = mutableStateOf("")
    private val currentChannel = mutableIntStateOf(-1)
    private val resolution = mutableStateOf("")
    private val channelItems = mutableStateListOf<RemoteChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var selectedTab by androidx.compose.runtime.remember { mutableStateOf("PAD") }
    var searchText by androidx.compose.runtime.remember { mutableStateOf("") }
    var searchStarted by androidx.compose.runtime.remember { mutableStateOf(false) }

    LaunchedEffect(searchText, connected, searchStarted) {
        if (connected && searchStarted) {
            delay(250)
            onSend(RemoteProtocol.CMD_SEARCH, searchText)
        }
    }

    Column(
        Modifier.fillMaxSize().background(OakDarkBackground).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("RETROTV", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(if (connected) "Connected • ${tvName.ifBlank { "RETROTV" }}" else connectionStatus,
                    color = if (connected) Color(0xFF45D483) else WarmAshMuted, fontSize = 11.sp)
            }
            RemoteActionButton("BACK", enabled = true, modifier = Modifier.width(74.dp)) { onBack() }
        }

        Card(colors = CardDefaults.cardColors(containerColor = OakSurface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PAIR WITH YOUR TV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pairingPin,
                        onValueChange = onPinChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("6-digit TV PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = WarmAshMuted,
                            cursorColor = FocusRed,
                            focusedBorderColor = FocusRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedLabelColor = FocusRed,
                            unfocusedLabelColor = WarmAshMuted
                        )
                    )
                    RemoteActionButton("FIND TV", enabled = true, modifier = Modifier.width(100.dp)) { onDiscover() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = onAddressChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("TV IP:port (fallback)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = WarmAshMuted,
                            cursorColor = FocusRed,
                            focusedBorderColor = FocusRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedLabelColor = FocusRed,
                            unfocusedLabelColor = WarmAshMuted
                        )
                    )
                    RemoteActionButton("CONNECT", enabled = true, modifier = Modifier.width(100.dp)) { onManualConnect() }
                }
                if (connected) {
                    Text("Playing: ${channels.getOrNull(currentChannel)?.title ?: "Live TV"} ${if (resolution.isNotBlank()) "• $resolution" else ""}",
                        color = Color.White, fontSize = 11.sp)
                    RemoteActionButton("DISCONNECT", enabled = true, modifier = Modifier.fillMaxWidth()) { onDisconnect() }
                }
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it; searchStarted = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            label = { Text("Search channels on TV") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = WarmAshMuted,
                            cursorColor = FocusRed,
                            focusedBorderColor = FocusRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                            focusedLabelColor = FocusRed,
                            unfocusedLabelColor = WarmAshMuted
                        )
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("PAD" to "D-PAD", "NUM" to "123 PAD", "CHANNELS" to "CHANNELS").forEach { (key, label) ->
                RemoteActionButton(label, selected = selectedTab == key, enabled = connected, modifier = Modifier.weight(1f)) { selectedTab = key }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                "PAD" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteActionButton("▲", selected = true, enabled = connected, modifier = Modifier.size(76.dp)) { onSend(RemoteProtocol.CMD_DPAD, "UP") }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteActionButton("◀", enabled = connected, modifier = Modifier.size(76.dp)) { onSend(RemoteProtocol.CMD_DPAD, "LEFT") }
                        RemoteActionButton("OK", selected = true, enabled = connected, modifier = Modifier.size(84.dp)) { onSend(RemoteProtocol.CMD_DPAD, "OK") }
                        RemoteActionButton("▶", enabled = connected, modifier = Modifier.size(76.dp)) { onSend(RemoteProtocol.CMD_DPAD, "RIGHT") }
                    }
                    Spacer(Modifier.height(8.dp))
                    RemoteActionButton("▼", enabled = connected, modifier = Modifier.size(76.dp)) { onSend(RemoteProtocol.CMD_DPAD, "DOWN") }
                }
                "NUM" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    (1..3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                            (0..2).forEach { col ->
                                val digit = (row - 1) * 3 + col + 1
                                RemoteActionButton(digit.toString(), enabled = connected, modifier = Modifier.size(68.dp)) {
                                    onSend(RemoteProtocol.CMD_NUMBER, digit.toString())
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        RemoteActionButton("⌫", enabled = connected, modifier = Modifier.size(68.dp)) { onSend(RemoteProtocol.CMD_DPAD, "BACK") }
                        RemoteActionButton("0", enabled = connected, modifier = Modifier.size(68.dp)) { onSend(RemoteProtocol.CMD_NUMBER, "0") }
                        RemoteActionButton("↵", enabled = connected, modifier = Modifier.size(68.dp)) { onSend(RemoteProtocol.CMD_DPAD, "OK") }
                    }
                }
                else -> {
                    if (channels.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (connected) "Waiting for channel list…" else "Connect to a RETROTV first", color = WarmAshMuted)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(channels, key = { it.number }) { channel ->
                                Row(
                                    Modifier.fillMaxWidth().background(if (channel.number - 1 == currentChannel) ChannelCardFocusedFill else OakSurface, RoundedCornerShape(9.dp))
                                        .clickable(enabled = connected) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onSend(RemoteProtocol.CMD_CHANNEL_ID, channel.number.toString())
                                        }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(channel.number.toString().padStart(3, '0'), color = FocusRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(channel.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(channel.group, color = WarmAshMuted, fontSize = 10.sp)
                                    }
                                    if (channel.number - 1 == currentChannel) Text("PLAYING", color = FocusRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RemoteActionButton("BACK", enabled = connected, modifier = Modifier.weight(1f)) { onSend(RemoteProtocol.CMD_DPAD, "BACK") }
            RemoteActionButton("VOL −", enabled = connected, modifier = Modifier.weight(1f)) { onSend(RemoteProtocol.CMD_MEDIA, "VOL_DOWN") }
            RemoteActionButton("VOL +", enabled = connected, modifier = Modifier.weight(1f)) { onSend(RemoteProtocol.CMD_MEDIA, "VOL_UP") }
            RemoteActionButton("MUTE", enabled = connected, modifier = Modifier.weight(1f)) { onSend(RemoteProtocol.CMD_MEDIA, "MUTE") }
        }
        RemoteActionButton("NEWS TICKER", enabled = connected, modifier = Modifier.fillMaxWidth()) {
            onSend(RemoteProtocol.CMD_MEDIA, "TICKER_TOGGLE")
        }
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
