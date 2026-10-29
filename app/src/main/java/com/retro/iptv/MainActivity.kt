@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.retro.iptv

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
const val IPTV_HINDI_PLAYLIST_URL = "https://iptv-org.github.io/iptv/languages/hin.m3u"
const val IPTV_BENGALI_PLAYLIST_URL = "https://iptv-org.github.io/iptv/languages/ben.m3u"

// --- COLOR PALETTE (Modern Oak) ---
val OakDarkBackground = Color(0xFF140F0C)
val OakSurface = Color(0xFF261A13)
val OakSurfaceElevated = Color(0xFF38271D)
val AmberDial = Color(0xFFE07A38)
val ParchmentWhite = Color(0xFFFAF4ED)
val WarmAshMuted = Color(0xFFA8988B)

enum class ChannelStatus { UNKNOWN, ONLINE, OFFLINE }

data class Channel(
    val title: String,
    val url: String,
    val group: String = "General",
    val logo: String? = null,
    val status: ChannelStatus = ChannelStatus.UNKNOWN,
    val region: String = "Unknown Region"
)

// --- RELIABLE LOGO LOADER (Follows Redirects & Decodes PNG/JPG) ---
@Composable
fun RemoteChannelLogo(url: String?, channelTitle: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    var currentUrl = url
                    var redirects = 0
                    var finalBitmap: ImageBitmap? = null

                    while (redirects < 5 && currentUrl != null) {
                        val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 6000
                            readTimeout = 6000
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            instanceFollowRedirects = true
                        }

                        val status = connection.responseCode
                        if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                            status == HttpURLConnection.HTTP_MOVED_TEMP ||
                            status == 307 || status == 308) {
                            currentUrl = connection.getHeaderField("Location")
                            redirects++
                            connection.disconnect()
                            continue
                        }

                        if (status in 200..299) {
                            connection.inputStream.use { stream ->
                                val bytes = stream.readBytes()
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                finalBitmap = bmp?.asImageBitmap()
                            }
                        }
                        connection.disconnect()
                        break
                    }
                    bitmap = finalBitmap
                } catch (e: Exception) {
                    bitmap = null
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = channelTitle,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(OakSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelTitle.take(3).uppercase(),
                color = AmberDial,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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

@Composable
fun ModernSearchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        drawCircle(Color.White, radius = 6.5f * unit, center = Offset(9f * unit, 9f * unit), style = Stroke(width = 2f * unit))
        drawLine(Color.White, start = Offset(13.8f * unit, 13.8f * unit), end = Offset(21f * unit, 21f * unit), strokeWidth = 2.2f * unit)
    }
}

@Composable
fun ModernSettingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val center = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 8) {
            val angle = Math.PI * i / 4.0
            val inner = 7.5f * unit
            val outer = 10f * unit
            drawLine(
                Color.White,
                start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                strokeWidth = 2.8f * unit
            )
        }
        drawCircle(Color.White, radius = 7f * unit, center = center, style = Stroke(width = 2f * unit))
        drawCircle(Color.White, radius = 2.5f * unit, center = center, style = Stroke(width = 2f * unit))
    }
}

// --- RETROTV BRAND + GLASS PILL NAVIGATION ---
@Composable
fun RetroTvMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 100f
        val cx = size.width / 2f
        drawLine(Color(0xFFE53935), Offset(cx - 13f*u, 28f*u), Offset(cx - 24f*u, 13f*u), 3f*u)
        drawLine(Color(0xFFE53935), Offset(cx + 13f*u, 28f*u), Offset(cx + 24f*u, 13f*u), 3f*u)
        drawRoundRect(Color(0xFFE53935), Offset(10f*u, 28f*u), androidx.compose.ui.geometry.Size(80f*u, 56f*u), androidx.compose.ui.geometry.CornerRadius(11f*u))
        drawRoundRect(Color.White, Offset(17f*u, 35f*u), androidx.compose.ui.geometry.Size(51f*u, 42f*u), androidx.compose.ui.geometry.CornerRadius(6f*u))
        val play = Path().apply {
            moveTo(37f*u, 44f*u)
            lineTo(54f*u, 56f*u)
            lineTo(37f*u, 68f*u)
            close()
        }
        drawPath(play, Color(0xFFE53935))
        drawCircle(Color.White, 4f*u, Offset(78f*u, 45f*u))
        drawCircle(Color.White, 4f*u, Offset(78f*u, 59f*u))
    }
}

@Composable
private fun RetroTopTab(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val pillColor by animateColorAsState(
        targetValue = when {
            selected -> AmberDial
            focused -> AmberDial.copy(alpha = 0.30f)
            else -> Color.White.copy(alpha = 0.055f)
        },
        animationSpec = tween(180),
        label = "navPillColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused || selected) AmberDial.copy(alpha = if (selected) 0.95f else 0.75f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(180),
        label = "navPillBorder"
    )
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .background(pillColor)
            .border(1.dp, borderColor, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun RetroTopAction(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        if (focused) AmberDial.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.045f),
        tween(180),
        label = "actionPillColor"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (focused) AmberDial else Color.White.copy(alpha = 0.12f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
fun OttTopHeader(
    currentTab: String,
    initialFocusRequester: FocusRequester,
    onSelectTab: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 600.dp
        val brand: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroTvMark(Modifier.size(if (compact) 30.dp else 36.dp))
                Text("RETRO", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (compact) 17.sp else 20.sp, letterSpacing = 1.5.sp)
                Text("TV", color = AmberDial, fontWeight = FontWeight.Black, fontSize = if (compact) 17.sp else 20.sp, letterSpacing = 1.sp)
            }
        }
        val tabs: @Composable () -> Unit = {
            Row(
                modifier = if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("HOME" to "Home", "FAVOURITES" to "Favourites", "REGION" to "Region", "TOPIC" to "Topic").forEach { (tab, label) ->
                    val selected = currentTab == tab || (tab == "TOPIC" && currentTab == "HOME")
                    RetroTopTab(
                        label = label,
                        selected = selected,
                        focusRequester = if (tab == "HOME") initialFocusRequester else null,
                        onSelect = { onSelectTab(tab) }
                    )
                }
            }
        }
        val actions: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroTopAction(onOpenSearch) { ModernSearchIcon(Modifier.fillMaxSize()) }
                RetroTopAction(onOpenSettings) { ModernSettingsIcon(Modifier.fillMaxSize()) }
            }
        }
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    brand()
                    actions()
                }
                tabs()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup().padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                brand()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { tabs() }
                actions()
            }
        }
    }
}

// --- HERO SECTION WITH LOGO ---
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
            .padding(horizontal = 36.dp, vertical = 12.dp)
    ) {
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel?.logo != null) {
                RemoteChannelLogo(
                    url = channel.logo,
                    channelTitle = channel.title,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column {
                Text(
                    text = channel?.title ?: "Select a Channel",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = channel?.group ?: "Live TV",
                    color = WarmAshMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberDial)
                    .clickable { onWatchFullscreen() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("▶ Fullscreen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    text = if (isFavorite) "★ Favorited" else "☆ Add Favourite",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// --- 16:9 CHANNEL CARD WITH LOGO ---
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
            .width(200.dp)
            .height(120.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteChannelLogo(
                    url = channel.logo,
                    channelTitle = channel.title,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                )

                if (channel.status == ChannelStatus.ONLINE) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                }
            }

            Text(
                text = channel.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }
    }
}

// --- SEARCH MODAL ---
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
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 480.dp)
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
                    Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
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
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(AmberDial),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(results) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectChannel(ch)
                                    onClose()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RemoteChannelLogo(url = ch.logo, channelTitle = ch.title, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = ch.title, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- SETTINGS MODAL ---
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
            .background(Color.Black.copy(alpha = 0.85f))
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
                    Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
                }

                Text("PLAYLIST SOURCE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakSurfaceElevated)
                        .border(1.dp, AmberDial.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (urlInput.isEmpty()) {
                        Text("Enter M3U link...", color = WarmAshMuted, fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
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
                        Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        Text("Reset Default", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    Text("15s Watchdog Auto-skip", color = Color.White, fontSize = 12.sp)
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
    private val isStartupVisible = mutableStateOf(true)
    private val isSearchOpen = mutableStateOf(false)
    private val isSettingsOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)

    private val currentTab = mutableStateOf("HOME")
    private val currentResolution = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val isChannelInfoVisible = mutableStateOf(false)

    private val customM3uUrl = mutableStateOf("")
    private val isWatchdogEnabled = mutableStateOf(true)
    private val favoriteUrls = mutableStateListOf<String>()
    private val channels = mutableStateListOf<Channel>()

    private var autoHideJob: Job? = null
    private var watchdogJob: Job? = null
    private var channelInfoJob: Job? = null

    // Verified 7-Channel Local Fallback
    private val fallbackChannels = listOf(
        Channel("Jamuna TV", "https://playztv-apps.pages.dev/jamuna-tv/index.m3u8", "BD News", "https://i.imgur.com/6xLp0Mf.png"),
        Channel("DBC News", "https://playztv-apps.pages.dev/dbc-news/index.m3u8", "BD News", "https://upload.wikimedia.org/wikipedia/commons/9/9f/DBC_News_logo.png"),
        Channel("Ekattor TV", "https://playztv-apps.pages.dev/ekattor-tv/index.m3u8", "BD News", "https://i.imgur.com/8neLbDd.jpg"),
        Channel("NTV", "https://playztv-apps.pages.dev/ntv/index.m3u8", "BD Entertainment", "https://upload.wikimedia.org/wikipedia/en/thumb/e/ef/NTV_%28Bangladesh%29_logo.svg/320px-NTV_%28Bangladesh%29_logo.svg.png"),
        Channel("Deepto TV", "https://playztv-apps.pages.dev/deepto-tv/index.m3u8", "BD Entertainment", "https://i.imgur.com/0sBqVhc.png"),
        Channel("Maasranga TV", "https://playztv-apps.pages.dev/maasranga-tv/index.m3u8", "BD Entertainment", "https://upload.wikimedia.org/wikipedia/en/thumb/2/27/Maasranga_Television_logo.svg/320px-Maasranga_Television_logo.svg.png"),
        Channel("DD Sports HD", "https://mumbai-edge.smartplaytv.in/DDSportsHD/index.m3u8", "Sports", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/DD_Sports_logo_2023.svg/320px-DD_Sports_logo_2023.svg.png")
    )

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
                            lifecycleScope.launch {
                                delay(650)
                                isStartupVisible.value = false
                            }
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
            LaunchedEffect(Unit) {
                // Never leave the branded launch cover blocking a slow or unavailable stream.
                delay(3200)
                isStartupVisible.value = false
            }
            val listState = rememberLazyListState()
            val initialMenuFocusRequester = remember { FocusRequester() }

            LaunchedEffect(isUiVisible.value) {
                if (isUiVisible.value) {
                    delay(180)
                    runCatching { initialMenuFocusRequester.requestFocus() }
                }
            }

            val scrollOffset by remember {
                derivedStateOf {
                    listState.firstVisibleItemScrollOffset + (listState.firstVisibleItemIndex * 350)
                }
            }
            val dynamicDarkAlpha by remember {
                derivedStateOf {
                    (0.35f + (scrollOffset / 600f).coerceIn(0f, 0.60f))
                }
            }

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

                    // Foreground OTT Interface
                    AnimatedVisibility(
                        visible = isUiVisible.value,
                        enter = fadeIn(tween(350)),
                        exit = fadeOut(tween(350))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dynamicDarkAlpha))
                        ) {
                            val activeChannel = channels.getOrNull(currentChannelIndex.value)
                            val isFav = favoriteUrls.contains(activeChannel?.url.orEmpty())

                            val filteredGroups = remember(currentTab.value, channels.toList(), favoriteUrls.toList()) {
                                when (currentTab.value) {
                                    "FAVOURITES" -> mapOf("Favourites" to channels.filter { favoriteUrls.contains(it.url) })
                                    "REGION" -> channels.groupBy { it.region.ifBlank { "Unknown Region" } }.toSortedMap()
                                    else -> channels.groupBy { it.group.ifBlank { "General" } }.toSortedMap()
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                OttTopHeader(
                                    currentTab = currentTab.value,
                                    initialFocusRequester = initialMenuFocusRequester,
                                    onSelectTab = {
                                        currentTab.value = it
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

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
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
                                                        modifier = Modifier.focusGroup(),
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

                    AnimatedVisibility(
                        visible = isChannelInfoVisible.value && !isUiVisible.value,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 28.dp),
                        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 },
                        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 3 }
                    ) {
                        val infoChannel = channels.getOrNull(currentChannelIndex.value)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .border(1.dp, AmberDial.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (infoChannel?.logo != null) {
                                RemoteChannelLogo(infoChannel.logo, infoChannel.title, Modifier.size(42.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(infoChannel?.title ?: "Live TV", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = listOfNotNull(infoChannel?.group, currentResolution.value).joinToString("  •  "),
                                    color = WarmAshMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

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

                    if (isBuffering.value && !isUiVisible.value && !isStartupVisible.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberDial)
                        }
                    }

                    AnimatedVisibility(
                        visible = isStartupVisible.value,
                        modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(260))
                    ) {
                        val transition = rememberInfiniteTransition(label = "retroSplash")
                        val pulse by transition.animateFloat(
                            initialValue = 0.92f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "splashPulse"
                        )
                        Column(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            RetroTvMark(Modifier.size(92.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("RETRO", color = Color(0xFF202020), fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                Text("TV", color = Color(0xFFE53935), fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.height(18.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFFE53935),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resetIdleTimer() {
        autoHideJob?.cancel()
        autoHideJob = lifecycleScope.launch {
            delay(5000)
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
            Toast.makeText(this, "Removed from Favourites", Toast.LENGTH_SHORT).show()
        } else {
            favoriteUrls.add(url)
            Toast.makeText(this, "Added to Favourites ★", Toast.LENGTH_SHORT).show()
        }
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("fav_urls", favoriteUrls.toSet()).apply()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(filesDir, "cached_playlist.m3u")

            val initialList = if (cacheFile.exists()) {
                val parsed = parseM3u(cacheFile.readText())
                if (parsed.isNotEmpty()) parsed else fallbackChannels
            } else {
                fallbackChannels
            }

            withContext(Dispatchers.Main) {
                channels.clear()
                channels.addAll(initialList.distinctBy { it.url })
                if (currentChannelIndex.value !in channels.indices) currentChannelIndex.value = 0
                playChannel(currentChannelIndex.value)
            }

            // Keep the current/personal playlist and add the IPTV-Org catalogue.
            val primaryUrl = if (customM3uUrl.value.isNotBlank()) customM3uUrl.value else DEFAULT_PLAYLIST_URL
            val playlistTexts = listOf(primaryUrl, IPTV_HINDI_PLAYLIST_URL, IPTV_BENGALI_PLAYLIST_URL)
                .distinct()
                .mapNotNull { url -> fetchUrl(url) }

            if (playlistTexts.isNotEmpty()) {
                val combinedText = playlistTexts.joinToString("\n")
                cacheFile.writeText(combinedText)
                val freshList = parseM3u(combinedText).distinctBy { it.url }
                withContext(Dispatchers.Main) {
                    if (freshList.isNotEmpty()) {
                        val currentPlayingUrl = channels.getOrNull(currentChannelIndex.value)?.url
                        channels.clear()
                        channels.addAll(freshList)
                        val sameIdx = channels.indexOfFirst { it.url == currentPlayingUrl }
                        if (sameIdx >= 0) {
                            currentChannelIndex.value = sameIdx
                        } else {
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
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
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
        var currentLogo: String? = null
        var currentRegion = "Unknown Region"
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                currentTitle = trimmed.substringAfterLast(",").trim()
                val groupMatch = """group-title="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "General"

                val logoMatch = """tvg-logo="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1)?.trim()
                val countryMatch = """tvg-country="([^"]*)"""" .trim().toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentRegion = countryMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Region"
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                if (currentTitle.isNotEmpty()) {
                    list.add(
                        Channel(
                            title = currentTitle,
                            url = trimmed,
                            group = currentGroup,
                            logo = currentLogo,
                            region = currentRegion
                        )
                    )
                    currentTitle = ""
                    currentGroup = "General"
                    currentLogo = null
                    currentRegion = "Unknown Region"
                }
            }
        }
        return list
    }

    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        isBuffering.value = true
        currentResolution.value = null
        isChannelInfoVisible.value = true
        channelInfoJob?.cancel()
        // Keep the channel banner visible for five seconds after the menu fades away.
        val infoDuration = if (isUiVisible.value) 10_000L else 5_000L
        channelInfoJob = lifecycleScope.launch {
            delay(infoDuration)
            isChannelInfoVisible.value = false
        }

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

    // Menu hidden: Left/Right zap channels, Up/Down/OK/Menu open the menu.
    // Menu visible: all D-pad keys go to the menu so you can move around in it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Let Android handle hardware volume keys normally.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event)
        }
        if (!isUiVisible.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    switchChannel(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    switchChannel(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MENU -> {
                    isUiVisible.value = true
                    resetIdleTimer()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    isCrtExiting.value = true
                    return true
                }
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                isUiVisible.value = false
                return true
            }
            resetIdleTimer()
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoHideJob?.cancel()
        watchdogJob?.cancel()
        channelInfoJob?.cancel()
        player.release()
    }
}
