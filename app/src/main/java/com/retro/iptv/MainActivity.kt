package com.retro.iptv

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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

const val GITHUB_REPO_API = "https://api.github.com/repos/Kosimosi/Android-IPTV/releases/latest"
const val APK_DOWNLOAD_URL = "https://github.com/Kosimosi/Android-IPTV/releases/latest/download/RetroIPTV.apk"

// --- COLOR PALETTE (Modern Oak) ---
val OakDarkBackground = Color(0xFF140F0C)
val OakSurface = Color(0xFF261A13)
val OakSurfaceElevated = Color(0xFF38271D)
val AmberDial = Color(0xFFE07A38)
val ParchmentWhite = Color(0xFFFAF4ED)
val WarmAshMuted = Color(0xFFA8988B)
val SidebarOakGlass = Color(0xF2140F0C)

enum class ChannelStatus { UNKNOWN, ONLINE, OFFLINE }

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
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = beamWhiteAlpha.value)))
            }
        }
    }
}

// --- LED STATUS LIGHT ---
@Composable
fun ChannelStatusDot(status: ChannelStatus) {
    val dotColor = when (status) {
        ChannelStatus.ONLINE -> Color(0xFF4CAF50)
        ChannelStatus.OFFLINE -> Color(0xFFE53935)
        ChannelStatus.UNKNOWN -> Color(0xFF555555)
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
}

// --- BOTTOM-RIGHT RESOLUTION BADGE ---
@Composable
fun ResolutionBadge(visible: Boolean, resolution: String?) {
    AnimatedVisibility(
        visible = visible && resolution != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp, end = 28.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SidebarOakGlass)
                    .border(1.dp, OakSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmberDial)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "RES",
                        color = OakDarkBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resolution.orEmpty(),
                    color = ParchmentWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- BOTTOM-LEFT CHANNEL BANNER ---
@Composable
fun ChannelOsdBanner(visible: Boolean, channel: Channel?, channelIndex: Int, notice: String?) {
    AnimatedVisibility(
        visible = visible && (channel != null || notice != null),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 32.dp, start = 24.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SidebarOakGlass)
                    .border(1.dp, OakSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = notice ?: channel?.title.orEmpty(),
                        color = ParchmentWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (notice != null) "Notice" else "Live Stream",
                        color = WarmAshMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// --- SIDEBAR CHANNEL MENU WITH SYNC, SEARCH & FAVORITES ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelSidebar(
    isOpen: Boolean,
    channels: List<Channel>,
    currentIndex: Int,
    favoriteUrls: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onClose: () -> Unit,
    onChannelSelected: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val filteredList = remember(searchQuery, channels, showOnlyFavorites, favoriteUrls) {
        var base = if (showOnlyFavorites) channels.filter { favoriteUrls.contains(it.url) } else channels
        if (searchQuery.isNotBlank()) {
            base = base.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        base
    }

    // Auto-scroll to currently playing channel when sidebar opens (Doesn't start from #1)
    LaunchedEffect(isOpen) {
        if (isOpen && channels.isNotEmpty()) {
            val targetInFiltered = filteredList.indexOfFirst { it.url == channels.getOrNull(currentIndex)?.url }
            if (targetInFiltered >= 0) {
                listState.scrollToItem(maxOf(0, targetInFiltered - 2))
            }
        }
    }

    if (isOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onClose() }
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .background(SidebarOakGlass)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header + Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "CHANNELS", color = AmberDial, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)

                    // ALL / FAVORITES Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(OakSurface)
                            .padding(2.dp)
                    ) {
                        Text(
                            text = "ALL",
                            color = if (!showOnlyFavorites) OakDarkBackground else WarmAshMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!showOnlyFavorites) AmberDial else Color.Transparent)
                                .clickable { showOnlyFavorites = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "★ FAV",
                            color = if (showOnlyFavorites) OakDarkBackground else WarmAshMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (showOnlyFavorites) AmberDial else Color.Transparent)
                                .clickable { showOnlyFavorites = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(text = "🔍 Search channel...", color = WarmAshMuted, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = ParchmentWhite, fontSize = 14.sp),
                        cursorBrush = SolidColor(AmberDial),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Channel List
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(filteredList) { _, channel ->
                        val originalIndex = channels.indexOf(channel)
                        val isSelected = originalIndex == currentIndex
                        val isFav = favoriteUrls.contains(channel.url)
                        var isFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) OakSurfaceElevated else if (isSelected) OakSurface else Color.Transparent)
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) AmberDial else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                // Click to play, Long-press to toggle favorite!
                                .combinedClickable(
                                    onClick = { onChannelSelected(originalIndex) },
                                    onLongClick = { onToggleFavorite(channel.url) }
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (originalIndex + 1).toString().padStart(2, '0'),
                                color = if (isFocused) AmberDial else WarmAshMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(30.dp)
                            )

                            ChannelStatusDot(status = channel.status)

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = channel.title,
                                color = if (channel.status == ChannelStatus.OFFLINE) ParchmentWhite.copy(alpha = 0.35f) else ParchmentWhite,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )

                            // Star icon if favorited
                            if (isFav) {
                                Text(text = "★", color = AmberDial, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var player: ExoPlayer

    private val isSidebarOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)
    private val isOsdVisible = mutableStateOf(false)
    private val isResolutionVisible = mutableStateOf(false)
    private val isMenuButtonVisible = mutableStateOf(true)

    private val currentResolution = mutableStateOf<String?>(null)
    private val osdNotice = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val errorMessage = mutableStateOf<String?>(null)

    private val favoriteUrls = mutableStateSetOf<String>()
    private val channels = mutableStateListOf<Channel>()

    private var watchdogJob: Job? = null
    private var failedSkipCount = 0
    private var menuHideJob: Job? = null

    private val playlistUrls = listOf(
        "https://iptv-org.github.io/iptv/countries/bd.m3u",
        "https://iptv-org.github.io/iptv/countries/in.m3u"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Load favorites from local device storage
        loadFavoritesFromStorage()

        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering.value = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) {
                        watchdogJob?.cancel()
                        failedSkipCount = 0
                        errorMessage.value = null
                        val idx = currentChannelIndex.value
                        if (idx in channels.indices) {
                            channels[idx] = channels[idx].copy(status = ChannelStatus.ONLINE)
                        }
                    }
                }

                // Video Resolution Listener
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        currentResolution.value = when {
                            videoSize.height >= 2160 -> "4K UHD"
                            videoSize.height >= 1440 -> "2K QHD"
                            videoSize.height >= 1080 -> "1080p FHD"
                            videoSize.height >= 720 -> "720p HD"
                            videoSize.height >= 480 -> "480p SD"
                            else -> "${videoSize.height}p"
                        }
                        triggerResolutionOsd()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    watchdogJob?.cancel()
                    val idx = currentChannelIndex.value
                    if (idx in channels.indices) {
                        channels[idx] = channels[idx].copy(status = ChannelStatus.OFFLINE)
                    }
                    if (failedSkipCount < channels.size) {
                        failedSkipCount++
                        triggerOsdNotice("Stream offline, hopping to next...")
                        switchChannel(delta = 1)
                    }
                }
            })
        }

        loadPlaylists()
        startMenuAutoHideTimer()

        setContent {
            CrtTvExitEffect(
                isExiting = isCrtExiting.value,
                onAnimationComplete = { finish() }
            ) {
                var dragTotalX by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        // Tapping screen toggles Menu button visibility & sidebar
                        .clickable {
                            isMenuButtonVisible.value = true
                            startMenuAutoHideTimer()
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragTotalX < -80f) switchChannel(delta = 1)
                                    else if (dragTotalX > 80f) switchChannel(delta = -1)
                                    dragTotalX = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTotalX += dragAmount
                                }
                            )
                        }
                ) {
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

                    // Auto-Hiding Mobile Menu Button
                    AnimatedVisibility(
                        visible = isMenuButtonVisible.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 24.dp, start = 20.dp)
                                .clip(CircleShape)
                                .background(SidebarOakGlass)
                                .border(1.dp, AmberDial, CircleShape)
                                .clickable {
                                    isSidebarOpen.value = true
                                    startMenuAutoHideTimer()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(text = "☰ MENU", color = AmberDial, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Loading Spinner
                    if (isBuffering.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberDial)
                        }
                    }

                    // Bottom-Left Channel Banner
                    ChannelOsdBanner(
                        visible = isOsdVisible.value,
                        channel = channels.getOrNull(currentChannelIndex.value),
                        channelIndex = currentChannelIndex.value,
                        notice = osdNotice.value
                    )

                    // Bottom-Right Resolution Badge (Disappears with banner)
                    ResolutionBadge(
                        visible = isResolutionVisible.value,
                        resolution = currentResolution.value
                    )

                    // Channel Sidebar
                    ChannelSidebar(
                        isOpen = isSidebarOpen.value,
                        channels = channels,
                        currentIndex = currentChannelIndex.value,
                        favoriteUrls = favoriteUrls,
                        onToggleFavorite = { url -> toggleFavorite(url) },
                        onClose = { isSidebarOpen.value = false },
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

    // --- FAVORITES STORAGE HELPERS ---
    private fun loadFavoritesFromStorage() {
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        val savedFavs = prefs.getStringSet("fav_urls", emptySet()) ?: emptySet()
        favoriteUrls.clear()
        favoriteUrls.addAll(savedFavs)
    }

    private fun toggleFavorite(url: String) {
        val updated = favoriteUrls.toMutableSet()
        if (updated.contains(url)) {
            updated.remove(url)
            Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            updated.add(url)
            Toast.makeText(this, "Added to Favorites ★", Toast.LENGTH_SHORT).show()
        }
        favoriteUrls.clear()
        favoriteUrls.addAll(updated)

        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("fav_urls", updated).apply()
    }

    // Auto-hide menu button after 4 seconds of inactivity
    private fun startMenuAutoHideTimer() {
        menuHideJob?.cancel()
        menuHideJob = lifecycleScope.launch {
            delay(4000)
            if (!isSidebarOpen.value) {
                isMenuButtonVisible.value = false
            }
        }
    }

    private fun triggerResolutionOsd() {
        isResolutionVisible.value = true
        window.decorView.postDelayed({
            isResolutionVisible.value = false
        }, 3500)
    }

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

    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        isBuffering.value = true
        errorMessage.value = null
        osdNotice.value = null
        currentResolution.value = null

        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()

        startWatchdog(index)
    }

    private fun startWatchdog(targetIndex: Int) {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            delay(15_000)
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

    private fun startBackgroundHealthCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(3)
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
        menuHideJob?.cancel()
        player.release()
    }
}