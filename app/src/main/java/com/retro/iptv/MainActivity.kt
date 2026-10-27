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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import androidx.media3.exoplayer.DefaultRenderersFactory
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
val SidebarOakGlass = Color(0xF5140F0C)

enum class ChannelStatus { UNKNOWN, ONLINE, OFFLINE }

data class Channel(
    val title: String,
    val url: String,
    val country: String = "BD",
    val status: ChannelStatus = ChannelStatus.UNKNOWN
)

@Composable
fun AppLaunchSplashScreen(
    isReady: Boolean,
    onAnimationEnd: () -> Unit
) {
    val alphaAnim = remember { Animatable(1f) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(isReady) {
        if (isReady) {
            delay(300)
            alphaAnim.animateTo(0f, tween(500))
            onAnimationEnd()
        } else {
            delay(5000)
            alphaAnim.animateTo(0f, tween(500))
            onAnimationEnd()
        }
    }

    if (alphaAnim.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alphaAnim.value }
                .background(OakDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(OakSurfaceElevated)
                        .border(2.dp, AmberDial, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📺", fontSize = 48.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "RETRO IPTV",
                    color = ParchmentWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "CONNECTING STREAMS...",
                    color = AmberDial,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(OakSurface)
            ) {
                val shimmerX by infiniteTransition.animateFloat(
                    initialValue = -300f,
                    targetValue = 1200f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmer"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(180.dp)
                        .graphicsLayer { translationX = shimmerX }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, AmberDial, Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

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

@Composable
fun ChannelStatusDot(status: ChannelStatus) {
    val dotColor = when (status) {
        ChannelStatus.ONLINE -> Color(0xFF4CAF50)
        ChannelStatus.OFFLINE -> Color(0xFFE53935)
        ChannelStatus.UNKNOWN -> Color(0xFF555555)
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
}

@Composable
fun ResolutionBadge(visible: Boolean, resolution: String?) {
    AnimatedVisibility(
        visible = visible && resolution != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 32.dp, end = 28.dp),
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
                    Text("RES", color = OakDarkBackground, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(resolution.orEmpty(), color = ParchmentWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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

// --- 3. CHANNEL SIDEBAR ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelSidebar(
    isOpen: Boolean,
    channels: List<Channel>,
    currentIndex: Int,
    favoriteUrls: List<String>,
    onToggleFavorite: (String) -> Unit,
    onClose: () -> Unit,
    onChannelSelected: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val ghostSuggestion = remember(searchQuery, channels) {
        if (searchQuery.isBlank()) null
        else {
            val found = channels.firstOrNull { it.title.startsWith(searchQuery, ignoreCase = true) }
            if (found != null && found.title.length > searchQuery.length) {
                found.title
            } else null
        }
    }

    val filteredList = remember(searchQuery, channels, showOnlyFavorites, favoriteUrls.toList()) {
        var base = if (showOnlyFavorites) channels.filter { favoriteUrls.contains(it.url) } else channels
        if (searchQuery.isNotBlank()) {
            base = base.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        base
    }

    LaunchedEffect(isOpen) {
        if (isOpen && channels.isNotEmpty()) {
            val target = filteredList.indexOfFirst { it.url == channels.getOrNull(currentIndex)?.url }
            if (target >= 0) listState.scrollToItem(maxOf(0, target - 2))
        }
    }

    if (isOpen) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onClose() }
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CHANNELS", color = AmberDial, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)

                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(OakSurface).padding(2.dp)
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (ghostSuggestion != null) {
                        val ghostText = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.Transparent)) {
                                append(searchQuery)
                            }
                            withStyle(SpanStyle(color = WarmAshMuted.copy(alpha = 0.45f))) {
                                append(ghostSuggestion.drop(searchQuery.length))
                            }
                        }
                        Text(text = ghostText, fontSize = 14.sp)
                    }

                    if (searchQuery.isEmpty()) {
                        Text("🔍 Search channel...", color = WarmAshMuted, fontSize = 14.sp)
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = ParchmentWhite, fontSize = 14.sp),
                        cursorBrush = SolidColor(AmberDial),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            if (isFav) {
                                Text("★", color = AmberDial, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 4. SETTINGS PANEL (CUSTOM M3U + REGIONS + FAVORITES FIX) ---
@Composable
fun SettingsSidebar(
    isOpen: Boolean,
    includeBD: Boolean,
    includeIN: Boolean,
    customM3uUrl: String,
    isWatchdogEnabled: Boolean,
    favoriteChannels: List<Channel>,
    onToggleBD: (Boolean) -> Unit,
    onToggleIN: (Boolean) -> Unit,
    onLoadCustomM3u: (String) -> Unit,
    onResetDefaultM3u: () -> Unit,
    onToggleWatchdog: (Boolean) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onExportFavorites: () -> Unit,
    onClose: () -> Unit
) {
    var m3uInputText by remember(customM3uUrl) { mutableStateOf(customM3uUrl) }

    if (isOpen) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onClose() }
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(SidebarOakGlass)
                    .clickable(enabled = false) {}
                    .padding(24.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SETTINGS", color = AmberDial, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("✕", color = WarmAshMuted, fontSize = 18.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
                        }
                    }

                    // --- CUSTOM M3U URL FIELD ---
                    item {
                        Text("PLAYLIST SOURCE (M3U)", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OakSurfaceElevated)
                                .border(1.dp, AmberDial.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (m3uInputText.isEmpty()) {
                                Text("Paste M3U URL here...", color = WarmAshMuted, fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = m3uInputText,
                                onValueChange = { m3uInputText = it },
                                textStyle = TextStyle(color = ParchmentWhite, fontSize = 12.sp),
                                cursorBrush = SolidColor(AmberDial),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmberDial)
                                    .clickable { onLoadCustomM3u(m3uInputText) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Load M3U", color = OakDarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OakSurface)
                                    .clickable {
                                        m3uInputText = ""
                                        onResetDefaultM3u()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Reset Default", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // --- REGION CHECKBOXES ---
                    item {
                        Text("CHANNEL REGIONS", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = includeBD,
                                onCheckedChange = { onToggleBD(it) },
                                colors = CheckboxDefaults.colors(checkedColor = AmberDial, checkmarkColor = OakDarkBackground)
                            )
                            Text("🇧🇩 Bangladesh Channels", color = ParchmentWhite, fontSize = 14.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = includeIN,
                                onCheckedChange = { onToggleIN(it) },
                                colors = CheckboxDefaults.colors(checkedColor = AmberDial, checkmarkColor = OakDarkBackground)
                            )
                            Text("🇮🇳 India Channels", color = ParchmentWhite, fontSize = 14.sp)
                        }
                    }

                    // --- PLAYBACK WATCHDOG ---
                    item {
                        Text("PLAYBACK WATCHDOG", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OakSurface)
                                .clickable { onToggleWatchdog(!isWatchdogEnabled) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-skip after 15s if stream fails", color = ParchmentWhite, fontSize = 13.sp)
                            Text(if (isWatchdogEnabled) "ON" else "OFF", color = if (isWatchdogEnabled) AmberDial else WarmAshMuted, fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- FAVORITES BACKUP / EXPORT ---
                    item {
                        Text("FAVORITES BACKUP", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OakSurfaceElevated)
                                .border(1.dp, AmberDial.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onExportFavorites() }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📋", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Copy Favorites Code", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Copies favorite URLs to clipboard to save", color = WarmAshMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // --- FAVORITES MANAGER ---
                    item {
                        Text("FAVORITES MANAGER (${favoriteChannels.size})", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (favoriteChannels.isEmpty()) {
                        item {
                            Text("No favorites yet. Long-press any channel in the list to add.", color = WarmAshMuted, fontSize = 12.sp)
                        }
                    }

                    itemsIndexed(favoriteChannels) { _, favChannel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(OakSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(favChannel.title, color = ParchmentWhite, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text("🗑", color = Color.Red, fontSize = 14.sp, modifier = Modifier.clickable { onRemoveFavorite(favChannel.url) }.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- 5. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var player: ExoPlayer

    private val isSidebarOpen = mutableStateOf(false)
    private val isSettingsOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)
    private val isOsdVisible = mutableStateOf(false)
    private val isResolutionVisible = mutableStateOf(false)
    private val isControlsVisible = mutableStateOf(true)
    private val isPlayerReady = mutableStateOf(false)

    private val includeBD = mutableStateOf(true)
    private val includeIN = mutableStateOf(true)
    private val customM3uUrl = mutableStateOf("")
    private val isWatchdogEnabled = mutableStateOf(true)

    private val currentResolution = mutableStateOf<String?>(null)
    private val osdNotice = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val errorMessage = mutableStateOf<String?>(null)

    private val favoriteUrls = mutableStateListOf<String>()
    private val allChannels = mutableStateListOf<Channel>()
    private val activeChannels = mutableStateListOf<Channel>()

    private var watchdogJob: Job? = null
    private var healthCheckJob: Job? = null
    private var failedSkipCount = 0
    private var autoHideJob: Job? = null

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

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isPlayerReady.value = true
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering.value = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            isPlayerReady.value = true
                            watchdogJob?.cancel()
                            failedSkipCount = 0
                            errorMessage.value = null
                            val idx = currentChannelIndex.value
                            if (idx in activeChannels.indices) {
                                activeChannels[idx] = activeChannels[idx].copy(status = ChannelStatus.ONLINE)
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
                                videoSize.height >= 480 -> "480p SD"
                                else -> "${videoSize.height}p"
                            }
                            triggerResolutionOsd()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        watchdogJob?.cancel()
                        val idx = currentChannelIndex.value
                        if (idx in activeChannels.indices) {
                            activeChannels[idx] = activeChannels[idx].copy(status = ChannelStatus.OFFLINE)
                        }
                        if (isWatchdogEnabled.value && failedSkipCount < activeChannels.size) {
                            failedSkipCount++
                            triggerOsdNotice("Stream offline, hopping to next...")
                            switchChannel(delta = 1)
                        }
                    }
                })
            }

        loadPlaylists()
        startAutoHideTimer()

        setContent {
            BackHandler(enabled = isSidebarOpen.value || isSettingsOpen.value || !isCrtExiting.value) {
                if (isSidebarOpen.value) {
                    isSidebarOpen.value = false
                } else if (isSettingsOpen.value) {
                    isSettingsOpen.value = false
                } else {
                    isCrtExiting.value = true
                }
            }

            CrtTvExitEffect(
                isExiting = isCrtExiting.value,
                onAnimationComplete = { finish() }
            ) {
                var dragTotalX by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable {
                            isControlsVisible.value = true
                            startAutoHideTimer()
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

                    AnimatedVisibility(
                        visible = isControlsVisible.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, start = 20.dp, end = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SidebarOakGlass)
                                    .border(1.dp, AmberDial, CircleShape)
                                    .clickable {
                                        isSidebarOpen.value = true
                                        startAutoHideTimer()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("☰ CHANNELS", color = AmberDial, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SidebarOakGlass)
                                    .border(1.dp, AmberDial, CircleShape)
                                    .clickable {
                                        isSettingsOpen.value = true
                                        startAutoHideTimer()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("⚙ SETTINGS", color = AmberDial, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (isBuffering.value && isPlayerReady.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberDial)
                        }
                    }

                    ChannelOsdBanner(
                        visible = isOsdVisible.value,
                        channel = activeChannels.getOrNull(currentChannelIndex.value),
                        channelIndex = currentChannelIndex.value,
                        notice = osdNotice.value
                    )

                    ResolutionBadge(
                        visible = isResolutionVisible.value,
                        resolution = currentResolution.value
                    )

                    ChannelSidebar(
                        isOpen = isSidebarOpen.value,
                        channels = activeChannels,
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

                    SettingsSidebar(
                        isOpen = isSettingsOpen.value,
                        includeBD = includeBD.value,
                        includeIN = includeIN.value,
                        customM3uUrl = customM3uUrl.value,
                        isWatchdogEnabled = isWatchdogEnabled.value,
                        favoriteChannels = allChannels.filter { favoriteUrls.contains(it.url) },
                        onToggleBD = {
                            if (!it && !includeIN.value) {
                                Toast.makeText(this@MainActivity, "Select at least one region!", Toast.LENGTH_SHORT).show()
                            } else {
                                includeBD.value = it
                                applyRegionFilter()
                            }
                        },
                        onToggleIN = {
                            if (!it && !includeBD.value) {
                                Toast.makeText(this@MainActivity, "Select at least one region!", Toast.LENGTH_SHORT).show()
                            } else {
                                includeIN.value = it
                                applyRegionFilter()
                            }
                        },
                        onLoadCustomM3u = { url ->
                            if (url.isNotBlank()) {
                                customM3uUrl.value = url
                                saveSettingsToStorage()
                                loadPlaylists()
                                isSettingsOpen.value = false
                            }
                        },
                        onResetDefaultM3u = {
                            customM3uUrl.value = ""
                            saveSettingsToStorage()
                            loadPlaylists()
                            Toast.makeText(this@MainActivity, "Reset to default playlists", Toast.LENGTH_SHORT).show()
                        },
                        onToggleWatchdog = { isWatchdogEnabled.value = it },
                        onRemoveFavorite = { url -> toggleFavorite(url) },
                        onExportFavorites = {
                            val code = favoriteUrls.joinToString(",")
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Favorites", code))
                            Toast.makeText(this@MainActivity, "Favorites copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        onClose = { isSettingsOpen.value = false }
                    )

                    AppLaunchSplashScreen(
                        isReady = isPlayerReady.value,
                        onAnimationEnd = { }
                    )
                }
            }
        }
    }

    // --- CRASH-FREE REGION FILTER ---
    private fun applyRegionFilter() {
        healthCheckJob?.cancel() // Stop health check immediately to prevent ConcurrentModificationException

        val currentUrl = activeChannels.getOrNull(currentChannelIndex.value)?.url
        val filtered = allChannels.filter { channel ->
            (includeBD.value && channel.country == "BD") ||
            (includeIN.value && channel.country == "IN") ||
            channel.country == "CUSTOM"
        }

        activeChannels.clear()
        activeChannels.addAll(filtered)

        if (activeChannels.isNotEmpty()) {
            val sameChannelIdx = activeChannels.indexOfFirst { it.url == currentUrl }
            if (sameChannelIdx >= 0) {
                currentChannelIndex.value = sameChannelIdx
            } else {
                currentChannelIndex.value = 0
                playChannel(0)
            }
            startBackgroundHealthCheck()
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
        prefs.edit()
            .putString("custom_m3u_url", customM3uUrl.value)
            .apply()
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

    private fun startAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = lifecycleScope.launch {
            delay(4000)
            if (!isSidebarOpen.value && !isSettingsOpen.value) {
                isControlsVisible.value = false
            }
        }
    }

    private fun triggerResolutionOsd() {
        isResolutionVisible.value = true
        window.decorView.postDelayed({ isResolutionVisible.value = false }, 3500)
    }

    private fun loadPlaylists() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Channel>()

            if (customM3uUrl.value.isNotBlank()) {
                try {
                    val customText = URL(customM3uUrl.value).readText()
                    list.addAll(parseM3u(customText, "CUSTOM"))
                } catch (e: Exception) { e.printStackTrace() }
            } else {
                try {
                    val bdText = URL("https://iptv-org.github.io/iptv/countries/bd.m3u").readText()
                    list.addAll(parseM3u(bdText, "BD"))
                } catch (e: Exception) { e.printStackTrace() }

                try {
                    val inText = URL("https://iptv-org.github.io/iptv/countries/in.m3u").readText()
                    list.addAll(parseM3u(inText, "IN"))
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (list.isEmpty()) {
                list.add(Channel("Apple Test Stream (HD)", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8", "BD"))
                list.add(Channel("Big Buck Bunny (HD)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "IN"))
            }

            withContext(Dispatchers.Main) {
                allChannels.clear()
                allChannels.addAll(list)
                applyRegionFilter()
            }
        }
    }

    private fun parseM3u(content: String, country: String): List<Channel> {
        val list = mutableListOf<Channel>()
        var currentTitle = ""
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                currentTitle = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                if (currentTitle.isNotEmpty()) {
                    list.add(Channel(title = currentTitle, url = trimmed, country = country))
                    currentTitle = ""
                }
            }
        }
        return list
    }

    private fun playChannel(index: Int) {
        val channel = activeChannels.getOrNull(index) ?: return
        isBuffering.value = true
        errorMessage.value = null
        osdNotice.value = null
        currentResolution.value = null

        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()

        if (isWatchdogEnabled.value) {
            startWatchdog(index)
        }
    }

    private fun startWatchdog(targetIndex: Int) {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            delay(15_000)
            if (isBuffering.value || errorMessage.value != null) {
                if (targetIndex in activeChannels.indices) {
                    activeChannels[targetIndex] = activeChannels[targetIndex].copy(status = ChannelStatus.OFFLINE)
                }
                if (failedSkipCount < activeChannels.size) {
                    failedSkipCount++
                    triggerOsdNotice("Stream offline, skipping to next...")
                    switchChannel(delta = 1)
                }
            }
        }
    }

    // --- CRASH-FREE THREAD-SAFE BACKGROUND VERIFIER ---
    private fun startBackgroundHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = activeChannels.toList() // Safe immutable copy
            val semaphore = Semaphore(3)

            snapshot.forEachIndexed { _, channel ->
                semaphore.acquire()
                launch {
                    try {
                        val isAlive = testStreamUrl(channel.url)
                        withContext(Dispatchers.Main) {
                            val idx = activeChannels.indexOfFirst { it.url == channel.url }
                            if (idx >= 0) {
                                activeChannels[idx] = activeChannels[idx].copy(
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

        if (isSettingsOpen.value) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                isSettingsOpen.value = false
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
            KeyEvent.KEYCODE_MENU -> {
                isSettingsOpen.value = true
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
        if (activeChannels.isEmpty()) return
        val newIndex = (currentChannelIndex.value + delta + activeChannels.size) % activeChannels.size
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
        healthCheckJob?.cancel()
        autoHideJob?.cancel()
        player.release()
    }
}
