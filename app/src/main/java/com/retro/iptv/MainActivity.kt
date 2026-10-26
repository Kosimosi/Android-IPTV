package com.retro.iptv

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// --- COLOR PALETTE (Modern Oak) ---
val OakDarkBackground = Color(0xFF140F0C)
val OakSurface = Color(0xFF261A13)
val OakSurfaceElevated = Color(0xFF38271D)
val AmberDial = Color(0xFFE07A38)
val ParchmentWhite = Color(0xFFFAF4ED)
val WarmAshMuted = Color(0xFFA8988B)
val SidebarOakGlass = Color(0xD2140F0C)

// --- DATA MODELS ---
enum class ChannelStatus {
    UNKNOWN,
    ONLINE,
    OFFLINE
}

data class Channel(
    val title: String,
    val url: String,
    val status: ChannelStatus = ChannelStatus.UNKNOWN
)

// --- RETRO CRT TURN-OFF ANIMATION ---
@Composable
fun CrtTvExitEffect(
    isExiting: Boolean,
    onAnimationComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    val scaleY = remember { Animatable(1f) }
    val scaleX = remember { Animatable(1f) }
    val beamWhiteAlpha = remember { Animatable(0f) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            launch { beamWhiteAlpha.animateTo(0.85f, tween(100)) }
            scaleY.animateTo(0.004f, tween(180, easing = FastOutLinearInEasing))
            scaleX.animateTo(0f, tween(140, easing = FastOutLinearInEasing))
            onAnimationComplete()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
            }
        ) {
            content()
            if (beamWhiteAlpha.value > 0f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = beamWhiteAlpha.value))
                )
            }
        }
    }
}

// --- LED STATUS LIGHT ---
@Composable
fun ChannelStatusDot(status: ChannelStatus) {
    val dotColor = when (status) {
        ChannelStatus.ONLINE -> Color(0xFF4CAF50)   // Green LED
        ChannelStatus.OFFLINE -> Color(0xFFE53935)  // Red LED
        ChannelStatus.UNKNOWN -> Color(0xFF555555)  // Gray (pending check)
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

// --- BOTTOM INFO BANNER (OSD) ---
@Composable
fun ChannelOsdBanner(visible: Boolean, channel: Channel?, channelIndex: Int, notice: String?) {
    AnimatedVisibility(
        visible = visible && (channel != null || notice != null),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp, start = 64.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SidebarOakGlass)
                    .border(1.dp, OakSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberDial)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CH ${(channelIndex + 1).toString().padStart(2, '0')}",
                        color = OakDarkBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = notice ?: channel?.title.orEmpty(),
                        color = ParchmentWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (notice != null) "Watchdog Notice" else "Live Stream",
                        color = WarmAshMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// --- CHANNEL SIDEBAR WITH LED DOTS ---
@Composable
fun ChannelSidebar(
    isOpen: Boolean,
    channels: List<Channel>,
    currentIndex: Int,
    onChannelSelected: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(440.dp)
                .background(SidebarOakGlass)
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BANGLADESH & INDIA",
                        color = AmberDial,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${channels.size} CH",
                        color = WarmAshMuted,
                        fontSize = 12.sp
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(channels) { index, channel ->
                        var isFocused by remember { mutableStateOf(false) }
                        val isSelected = index == currentIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isFocused -> OakSurfaceElevated
                                        isSelected -> OakSurface
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) AmberDial else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { onChannelSelected(index) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                color = if (isFocused) AmberDial else WarmAshMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp)
                            )

                            // Status LED Dot
                            ChannelStatusDot(status = channel.status)

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = channel.title,
                                color = if (channel.status == ChannelStatus.OFFLINE)
                                    ParchmentWhite.copy(alpha = 0.35f)
                                else ParchmentWhite,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- MAIN ACTIVITY & LOGIC ---
class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var player: ExoPlayer

    private val isSidebarOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)
    private val isOsdVisible = mutableStateOf(false)
    private val osdNotice = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val errorMessage = mutableStateOf<String?>(null)

    private val channels = mutableStateListOf<Channel>()

    // Watchdog variables
    private var watchdogJob: Job? = null
    private var failedSkipCount = 0

    // Bangladesh + India Playlist URLs
    private val playlistUrls = listOf(
        "https://iptv-org.github.io/iptv/countries/bd.m3u",
        "https://iptv-org.github.io/iptv/countries/in.m3u"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering.value = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) {
                        // Stream is alive! Cancel watchdog & mark channel ONLINE
                        watchdogJob?.cancel()
                        failedSkipCount = 0
                        errorMessage.value = null

                        val idx = currentChannelIndex.value
                        if (idx in channels.indices) {
                            channels[idx] = channels[idx].copy(status = ChannelStatus.ONLINE)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Skip automatically on immediate error
                    watchdogJob?.cancel()
                    val idx = currentChannelIndex.value
                    if (idx in channels.indices) {
                        channels[idx] = channels[idx].copy(status = ChannelStatus.OFFLINE)
                    }

                    if (failedSkipCount < channels.size) {
                        failedSkipCount++
                        triggerOsdNotice("Stream error, hopping to next...")
                        switchChannel(delta = 1)
                    }
                }
            })
        }

        // Fetch BD + India Playlists
        loadPlaylists()

        setContent {
            CrtTvExitEffect(
                isExiting = isCrtExiting.value,
                onAnimationComplete = { finish() }
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    // Video View
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                                isFocusable = false
                                isClickable = false
                                this.player = this@MainActivity.player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Loading Spinner
                    if (isBuffering.value) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AmberDial)
                        }
                    }

                    // Bottom Channel Banner
                    ChannelOsdBanner(
                        visible = isOsdVisible.value,
                        channel = channels.getOrNull(currentChannelIndex.value),
                        channelIndex = currentChannelIndex.value,
                        notice = osdNotice.value
                    )

                    // Channel Sidebar
                    ChannelSidebar(
                        isOpen = isSidebarOpen.value,
                        channels = channels,
                        currentIndex = currentChannelIndex.value,
                        onChannelSelected = { index ->
                            currentChannelIndex.value = index
                            playChannel(index)
                            isSidebarOpen.value = false
                        }
                    )
                }
            }
        }
    }

    // --- M3U LOADER (BD + India) ---
    private fun loadPlaylists() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mergedList = mutableListOf<Channel>()
            for (url in playlistUrls) {
                try {
                    val response = URL(url).readText()
                    mergedList.addAll(parseM3u(response))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                if (mergedList.isNotEmpty()) {
                    channels.clear()
                    channels.addAll(mergedList)
                    playChannel(0)
                    startBackgroundHealthCheck()
                } else {
                    // Fallback to sample stream if download fails
                    channels.add(Channel("Apple Test Stream", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8"))
                    playChannel(0)
                }
            }
        }
    }

    private fun parseM3u(content: String): List<Channel> {
        val list = mutableListOf<Channel>()
        var currentTitle = ""
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                currentTitle = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                if (currentTitle.isNotEmpty()) {
                    list.add(Channel(title = currentTitle, url = trimmed))
                    currentTitle = ""
                }
            }
        }
        return list
    }

    // --- 15-SECOND AUTO-SKIP WATCHDOG ---
    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        isBuffering.value = true
        errorMessage.value = null
        osdNotice.value = null

        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()

        startWatchdog(index)
    }

    private fun startWatchdog(targetIndex: Int) {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            delay(15_000) // 15 seconds countdown

            if (isBuffering.value || errorMessage.value != null) {
                if (targetIndex in channels.indices) {
                    channels[targetIndex] = channels[targetIndex].copy(status = ChannelStatus.OFFLINE)
                }

                if (failedSkipCount < channels.size) {
                    failedSkipCount++
                    triggerOsdNotice("Stream offline, skipping to next...")
                    switchChannel(delta = 1)
                }
            }
        }
    }

    // --- BACKGROUND STREAM VERIFIER ---
    private fun startBackgroundHealthCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(3) // Check 3 streams at a time

            channels.forEachIndexed { index, channel ->
                if (index == currentChannelIndex.value) return@forEachIndexed

                semaphore.acquire()
                launch {
                    try {
                        val isAlive = testStreamUrl(channel.url)
                        withContext(Dispatchers.Main) {
                            if (index in channels.indices) {
                                channels[index] = channels[index].copy(
                                    status = if (isAlive) ChannelStatus.ONLINE else ChannelStatus.OFFLINE
                                )
                            }
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    private fun testStreamUrl(streamUrl: String): Boolean {
        return try {
            val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Range", "bytes=0-1024")
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }

    // --- REMOTE CONTROLS ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isSidebarOpen.value) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                isSidebarOpen.value = false
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                switchChannel(delta = 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                switchChannel(delta = -1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                isSidebarOpen.value = true
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                isCrtExiting.value = true
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        val newIndex = (currentChannelIndex.value + delta + channels.size) % channels.size
        currentChannelIndex.value = newIndex
        playChannel(newIndex)
        triggerOsdNotice(null)
    }

    private fun triggerOsdNotice(notice: String?) {
        osdNotice.value = notice
        isOsdVisible.value = true
        window.decorView.postDelayed({
            isOsdVisible.value = false
            osdNotice.value = null
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        player.release()
    }
}
