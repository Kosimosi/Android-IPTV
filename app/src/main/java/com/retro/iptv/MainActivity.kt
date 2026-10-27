@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.retro.iptv

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// --- YOUR PERSONAL GITHUB PLAYLIST URL ---
const val DEFAULT_PLAYLIST_URL = "https://raw.githubusercontent.com/Kosimosi/Android-IPTV/main/playlist.m3u"

// --- COLOR PALETTE (Modern Oak / OTT Glass) ---
val OakDarkBackground = Color(0xFF140F0C)
val OakSurface = Color(0xFF261A13)
val OakSurfaceElevated = Color(0xFF38271D)
val AmberDial = Color(0xFFE07A38)
val ParchmentWhite = Color(0xFFFAF4ED)
val WarmAshMuted = Color(0xFFA8988B)
val ScrimDarkGradient = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.85f),
    0.3f to Color.Black.copy(alpha = 0.25f),
    0.6f to Color.Black.copy(alpha = 0.70f),
    1.0f to Color.Black.copy(alpha = 0.95f)
)

enum class ChannelStatus { UNKNOWN, ONLINE, OFFLINE }

data class Channel(
    val title: String,
    val url: String,
    val group: String = "General",
    val status: ChannelStatus = ChannelStatus.UNKNOWN
)

// --- RETRO CRT SHUTDOWN ANIMATION ---
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

// --- OTT TOP HEADER (Logo, Categories & Actions) ---
@Composable
fun OttTopHeader(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "RETRO",
                color = AmberDial,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "TV",
                color = ParchmentWhite,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 1.sp
            )
        }

        // Center Category Tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 24.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                Text(
                    text = cat.uppercase(),
                    color = if (isSelected) AmberDial else ParchmentWhite.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Action Icons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OakSurfaceElevated)
                    .border(1.dp, AmberDial.copy(alpha = 0.6f), CircleShape)
                    .clickable { onOpenSearch() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("🔍 Search", color = ParchmentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OakSurfaceElevated)
                    .border(1.dp, AmberDial.copy(alpha = 0.6f), CircleShape)
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("⚙ Settings", color = ParchmentWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- HERO SECTION (Active Stream Metadata) ---
@Composable
fun HeroSection(
    channel: Channel?,
    resolution: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onWatchFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 16.dp)
    ) {
        // Live & Resolution Badges
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("● LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }

            if (resolution != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(resolution, color = AmberDial, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Channel Title
        Text(
            text = channel?.title ?: "Select a Channel",
            color = ParchmentWhite,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Text(
            text = channel?.group ?: "Live Stream",
            color = WarmAshMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberDial)
                    .clickable { onWatchFullscreen() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("▶ Fullscreen", color = OakDarkBackground, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OakSurfaceElevated)
                    .border(1.dp, AmberDial.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onToggleFavorite() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isFavorite) "★ Favorited" else "☆ Add Favorite",
                    color = ParchmentWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// --- 16:9 CHANNEL CARD ---
@Composable
fun ChannelCard(
    channel: Channel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUserInteract: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(102.dp)
            .graphicsLayer {
                val scale = if (isFocused) 1.08f else 1.0f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) OakSurfaceElevated else OakSurface)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                color = if (isFocused) AmberDial else if (isSelected) AmberDial.copy(alpha = 0.7f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onUserInteract()
            }
            .focusable()
            .clickable { 
                onSelect()
                onUserInteract()
            }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Channel Number Badge & Status Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AmberDial else OakSurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TV",
                        color = if (isSelected) OakDarkBackground else WarmAshMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (channel.status == ChannelStatus.ONLINE) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                }
            }

            // Channel Title
            Text(
                text = channel.title,
                color = if (isSelected || isFocused) ParchmentWhite else ParchmentWhite.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2
            )
        }
    }
}

// --- SEARCH MODAL OVERLAY ---
@Composable
fun SearchModal(
    channels: List<Channel>,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, channels) {
        if (query.isBlank()) emptyList()
        else channels.filter { it.title.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OakSurface)
                .border(1.5.dp, AmberDial, RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SEARCH CHANNELS", color = AmberDial, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("✕", color = WarmAshMuted, fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text("Type channel name...", color = WarmAshMuted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = ParchmentWhite, fontSize = 13.sp),
                        cursorBrush = SolidColor(AmberDial),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(results) { ch ->
                        Text(
                            text = ch.title,
                            color = ParchmentWhite,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectChannel(ch)
                                    onClose()
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- SETTINGS MODAL OVERLAY ---
@Composable
fun SettingsModal(
    customM3uUrl: String,
    isWatchdogEnabled: Boolean,
    onLoadCustomM3u: (String) -> Unit,
    onResetM3u: () -> Unit,
    onToggleWatchdog: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var urlInput by remember(customM3uUrl) { mutableStateOf(customM3uUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OakSurface)
                .border(1.5.dp, AmberDial, RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SETTINGS", color = AmberDial, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("✕", color = WarmAshMuted, fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
                }

                Text("CUSTOM M3U PLAYLIST", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (urlInput.isEmpty()) {
                        Text("Paste M3U URL...", color = WarmAshMuted, fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        textStyle = TextStyle(color = ParchmentWhite, fontSize = 12.sp),
                        cursorBrush = SolidColor(AmberDial),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberDial)
                            .clickable {
                                onLoadCustomM3u(urlInput)
                                onClose()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Apply URL", color = OakDarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(OakSurfaceElevated)
                            .clickable {
                                onResetM3u()
                                onClose()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reset Default", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .clickable { onToggleWatchdog(!isWatchdogEnabled) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("15s Watchdog Auto-skip", color = ParchmentWhite, fontSize = 12.sp)
                    Text(if (isWatchdogEnabled) "ON" else "OFF", color = if (isWatchdogEnabled) AmberDial else WarmAshMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- MAIN ACTIVITY CONTROLLER ---
class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var player: ExoPlayer

    private val isUiVisible = mutableStateOf(true)
    private val isSearchOpen = mutableStateOf(false)
    private val isSettingsOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)

    private val currentResolution = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val selectedCategory = mutableStateOf("HOME")

    private val customM3uUrl = mutableStateOf("")
    private val isWatchdogEnabled = mutableStateOf(true)
    private val favoriteUrls = mutableStateListOf<String>()
    private val channels = mutableStateListOf<Channel>()

    private var autoHideJob: Job? = null
    private var watchdogJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadSettingsFromStorage()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering.value = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            watchdogJob?.cancel()
                            val idx = currentChannelIndex.value
                            if (idx in channels.indices) {
                                channels[idx] = channels[idx].copy(status = ChannelStatus.ONLINE)
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            currentResolution.value = when {
                                videoSize.height >= 2160 -> "4K UHD"
                                videoSize.height >= 1440 -> "2K QHD"
                                videoSize.height >= 1080 -> "1080p FHD"
                                videoSize.height >= 720 -> "720p HD"
                                else -> "${videoSize.height}p"
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (isWatchdogEnabled.value) {
                            switchChannel(1)
                        }
                    }
                })
            }

        loadPlaylists()
        resetIdleTimer()

        setContent {
            BackHandler(enabled = isSearchOpen.value || isSettingsOpen.value || isUiVisible.value || !isCrtExiting.value) {
                when {
                    isSearchOpen.value -> isSearchOpen.value = false
                    isSettingsOpen.value -> isSettingsOpen.value = false
                    isUiVisible.value -> isUiVisible.value = false
                    else -> isCrtExiting.value = true
                }
            }

            CrtTvExitEffect(
                isExiting = isCrtExiting.value,
                onAnimationComplete = { finish() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable {
                            isUiVisible.value = !isUiVisible.value
                            if (isUiVisible.value) resetIdleTimer()
                        }
                ) {
                    // LAYER 1: Full-Screen Live Video Playing in the Background
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

                    // LAYER 2: OTT Overlay (Auto-Hides after 6s of inactivity)
                    AnimatedVisibility(
                        visible = isUiVisible.value,
                        enter = fadeIn(tween(350)),
                        exit = fadeOut(tween(350))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ScrimDarkGradient)
                        ) {
                            val activeChannel = channels.getOrNull(currentChannelIndex.value)
                            val isFav = favoriteUrls.contains(activeChannel?.url.orEmpty())

                            // Build category shelves from M3U group titles
                            val allCategories = remember(channels) {
                                listOf("HOME") + channels.map { it.group }.distinct().filter { it.isNotBlank() } + listOf("FAVORITES")
                            }

                            val filteredGroups = remember(selectedCategory.value, channels, favoriteUrls.toList()) {
                                when (selectedCategory.value) {
                                    "HOME" -> channels.groupBy { it.group }
                                    "FAVORITES" -> mapOf("Favorites" to channels.filter { favoriteUrls.contains(it.url) })
                                    else -> mapOf(selectedCategory.value to channels.filter { it.group.equals(selectedCategory.value, ignoreCase = true) })
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Header
                                OttTopHeader(
                                    categories = allCategories,
                                    selectedCategory = selectedCategory.value,
                                    onSelectCategory = {
                                        selectedCategory.value = it
                                        resetIdleTimer()
                                    },
                                    onOpenSearch = {
                                        isSearchOpen.value = true
                                        resetIdleTimer()
                                    },
                                    onOpenSettings = {
                                        isSettingsOpen.value = true
                                        resetIdleTimer()
                                    }
                                )

                                // Main Vertical Content (Hero + Horizontal Shelves)
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    // Hero Section
                                    item {
                                        HeroSection(
                                            channel = activeChannel,
                                            resolution = currentResolution.value,
                                            isFavorite = isFav,
                                            onToggleFavorite = {
                                                activeChannel?.url?.let { toggleFavorite(it) }
                                                resetIdleTimer()
                                            },
                                            onWatchFullscreen = {
                                                isUiVisible.value = false
                                            }
                                        )
                                    }

                                    // Category Shelves
                                    filteredGroups.forEach { (groupName, groupChannels) ->
                                        if (groupChannels.isNotEmpty()) {
                                            item {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        text = groupName.uppercase(),
                                                        color = AmberDial,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 1.sp,
                                                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 6.dp)
                                                    )

                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                        contentPadding = PaddingValues(horizontal = 36.dp)
                                                    ) {
                                                        items(groupChannels) { ch ->
                                                            ChannelCard(
                                                                channel = ch,
                                                                isSelected = ch.url == activeChannel?.url,
                                                                onSelect = {
                                                                    val idx = channels.indexOf(ch)
                                                                    if (idx >= 0) {
                                                                        currentChannelIndex.value = idx
                                                                        playChannel(idx)
                                                                    }
                                                                },
                                                                onUserInteract = { resetIdleTimer() }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Modals
                    if (isSearchOpen.value) {
                        SearchModal(
                            channels = channels,
                            onSelectChannel = { ch ->
                                val idx = channels.indexOf(ch)
                                if (idx >= 0) {
                                    currentChannelIndex.value = idx
                                    playChannel(idx)
                                }
                            },
                            onClose = { isSearchOpen.value = false }
                        )
                    }

                    if (isSettingsOpen.value) {
                        SettingsModal(
                            customM3uUrl = customM3uUrl.value,
                            isWatchdogEnabled = isWatchdogEnabled.value,
                            onLoadCustomM3u = { url ->
                                customM3uUrl.value = url
                                saveSettingsToStorage()
                                loadPlaylists()
                            },
                            onResetM3u = {
                                customM3uUrl.value = ""
                                saveSettingsToStorage()
                                loadPlaylists()
                            },
                            onToggleWatchdog = { isWatchdogEnabled.value = it },
                            onClose = { isSettingsOpen.value = false }
                        )
                    }

                    if (isBuffering.value && !isUiVisible.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberDial)
                        }
                    }
                }
            }
        }
    }

    private fun resetIdleTimer() {
        autoHideJob?.cancel()
        autoHideJob = lifecycleScope.launch {
            delay(6000) // Auto-hide after 6s of inactivity
            if (!isSearchOpen.value && !isSettingsOpen.value) {
                isUiVisible.value = false
            }
        }
    }

    private fun loadSettingsFromStorage() {
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        val savedFavs = prefs.getStringSet("fav_urls", emptySet()) ?: emptySet()
        favoriteUrls.clear()
        favoriteUrls.addAll(savedFavs)
        customM3uUrl.value = prefs.getString("custom_m3u_url", "") ?: ""
    }

    private fun saveSettingsToStorage() {
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_m3u_url", customM3uUrl.value).apply()
    }

    private fun toggleFavorite(url: String) {
        if (favoriteUrls.contains(url)) {
            favoriteUrls.remove(url)
            Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            favoriteUrls.add(url)
            Toast.makeText(this, "Added to Favorites ★", Toast.LENGTH_SHORT).show()
        }
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("fav_urls", favoriteUrls.toSet()).apply()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(filesDir, "cached_playlist.m3u")

            // 1. Instant local cache load
            if (cacheFile.exists()) {
                val cached = parseM3u(cacheFile.readText())
                if (cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        channels.clear()
                        channels.addAll(cached)
                        if (currentChannelIndex.value !in channels.indices) currentChannelIndex.value = 0
                        playChannel(currentChannelIndex.value)
                    }
                }
            }

            // 2. Fetch fresh from GitHub
            val targetUrl = if (customM3uUrl.value.isNotBlank()) customM3uUrl.value else DEFAULT_PLAYLIST_URL
            val freshText = fetchUrl(targetUrl)

            if (freshText != null) {
                cacheFile.writeText(freshText)
                val freshList = parseM3u(freshText)
                withContext(Dispatchers.Main) {
                    if (freshList.isNotEmpty()) {
                        val currentPlayingUrl = channels.getOrNull(currentChannelIndex.value)?.url
                        channels.clear()
                        channels.addAll(freshList)
                        val sameIdx = channels.indexOfFirst { it.url == currentPlayingUrl }
                        if (sameIdx >= 0) {
                            currentChannelIndex.value = sameIdx
                        } else if (channels.isNotEmpty()) {
                            currentChannelIndex.value = 0
                            playChannel(0)
                        }
                    }
                }
            }
        }
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                instanceFollowRedirects = true
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseM3u(content: String): List<Channel> {
        val list = mutableListOf<Channel>()
        var currentTitle = ""
        var currentGroup = "General"
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                currentTitle = trimmed.substringAfterLast(",").trim()
                val groupMatch = """group-title="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "General"
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                if (currentTitle.isNotEmpty()) {
                    list.add(Channel(title = currentTitle, url = trimmed, group = currentGroup))
                    currentTitle = ""
                    currentGroup = "General"
                }
            }
        }
        return list
    }

    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        isBuffering.value = true
        currentResolution.value = null

        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()

        if (isWatchdogEnabled.value) {
            watchdogJob?.cancel()
            watchdogJob = lifecycleScope.launch {
                delay(15_000)
                if (isBuffering.value) {
                    switchChannel(1)
                }
            }
        }
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        val newIndex = (currentChannelIndex.value + delta + channels.size) % channels.size
        currentChannelIndex.value = newIndex
        playChannel(newIndex)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Any remote button wakes up the OTT interface
        if (!isUiVisible.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    isCrtExiting.value = true
                    return true
                }
                else -> {
                    isUiVisible.value = true
                    resetIdleTimer()
                    return true
                }
            }
        }

        resetIdleTimer()

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                switchChannel(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                switchChannel(-1)
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
            KeyEvent.KEYCODE_BACK -> {
                isUiVisible.value = false
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoHideJob?.cancel()
        watchdogJob?.cancel()
        player.release()
    }
}