@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.retro.iptv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSystemSettings
import android.speech.RecognizerIntent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.app.PictureInPictureParams
import android.util.Rational
import android.view.KeyEvent
import android.util.Xml
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import java.net.URL
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections
import com.retro.iptv.remote.RemoteProtocol
import com.retro.iptv.remote.TvRemoteServer
import com.retro.iptv.remote.RemoteCommandBridge
import com.retro.iptv.remote.TvRemoteBackgroundService

// --- YOUR PERSONAL GITHUB PLAYLIST URL ---
const val DEFAULT_PLAYLIST_URL = "https://raw.githubusercontent.com/Kosimosi/Android-IPTV/main/playlist.m3u"
const val IPTV_INDIA_EPG_URL = "https://iptv-epg.org/files/epg-in.xml"

// --- COLOR PALETTE (Modern Oak) ---
val OakDarkBackground = Color(0xFF0B0C0E)
val OakSurface = Color(0xFF15171C)
val OakSurfaceElevated = Color(0xFF1D2027)
val FocusRed = Color(0xFFE11D48)
val ChannelCardFocusedFill = Color(0xFF241419)
val ChannelCardIdleStroke = Color(0xFF242832)
val ChannelCardFocusGlow = Color(0x66E11D48)
val RetroGlass = Color(0xD912141A)
val RetroGlassBorder = Color.White.copy(alpha = 0.12f)
val ParchmentWhite = Color.White
val WarmAshMuted = Color(0xFFA3A3A3)

enum class ChannelStatus { UNKNOWN, ONLINE, OFFLINE }

data class Channel(
    val title: String,
    val url: String,
    val group: String = "General",
    val logo: String? = null,
    val status: ChannelStatus = ChannelStatus.UNKNOWN,
    val region: String = "Unknown Region",
    val epgId: String? = null,
    val language: String = "Bangla",
    val country: String = "Bangladesh",
    val backupUrls: List<String> = emptyList(),
    val linkLabels: List<String> = emptyList()
) {
    val allUrls: List<String>
        get() = listOf(url) + backupUrls

    fun labelForStream(index: Int): String {
        val total = allUrls.size
        val raw = linkLabels.getOrNull(index)?.trim().orEmpty()
        return when {
            raw.isEmpty() -> "Link ${index + 1}"
            total > 1 && linkLabels.count { it.trim().equals(raw, ignoreCase = true) } > 1 ->
                "Link ${index + 1} ($raw)"
            else -> raw
        }
    }
}

data class Programme(val channelId: String, val title: String, val startTimeMs: Long, val stopTimeMs: Long)

@Composable
fun ProgrammeInfoCard(current: Programme?, upcoming: Programme?) {
    if (current == null && upcoming == null) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(10.dp)).background(OakSurface.copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("PROGRAMME GUIDE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        current?.let { Text("NOW  ${formatProgrammeTime(it.startTimeMs)}–${formatProgrammeTime(it.stopTimeMs)}  •  ${it.title}",
            color = ParchmentWhite, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        upcoming?.let { Text("NEXT  ${formatProgrammeTime(it.startTimeMs)}–${formatProgrammeTime(it.stopTimeMs)}  •  ${it.title}",
            color = WarmAshMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

private fun formatProgrammeTime(timeMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))

// --- RELIABLE LOGO LOADER (Follows Redirects & Decodes PNG/JPG) ---
@Composable
fun RemoteChannelLogo(url: String?, channelTitle: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val cacheDir = File(context.cacheDir, "channel_logos").apply { mkdirs() }
                    val cacheFile = File(cacheDir, "${url.hashCode().toUInt().toString(16)}.img")
                    if (cacheFile.exists() && cacheFile.length() in 1..(2L * 1024 * 1024)) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(cacheFile.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                        val cached = BitmapFactory.decodeFile(cacheFile.absolutePath, options)
                        if (cached != null) {
                            bitmap = cached.asImageBitmap()
                            return@withContext
                        }
                    }
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
                                // Never buffer an unbounded logo response. Large source images are
                                // rejected before decoding and accepted images are downsampled.
                                val maxLogoBytes = 2 * 1024 * 1024
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0
                                var count: Int
                                while (stream.read(buffer).also { count = it } >= 0) {
                                    total += count
                                    if (total > maxLogoBytes) break
                                    output.write(buffer, 0, count)
                                }
                                if (total <= maxLogoBytes) {
                                    val bytes = output.toByteArray()
                                    runCatching { cacheFile.writeBytes(bytes) }
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                    var sample = 1
                                    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                                    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                                    finalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                                }
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
                color = Color.White,
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
        // Match the launcher icon: red TV shell, white screen and red play mark.
        drawLine(Color(0xFFE62117), Offset(41f*u, 35f*u), Offset(31f*u, 22f*u), 2.8f*u)
        drawLine(Color(0xFFE62117), Offset(59f*u, 35f*u), Offset(69f*u, 22f*u), 2.8f*u)
        drawRoundRect(Color(0xFFE62117), Offset(21f*u, 35f*u), androidx.compose.ui.geometry.Size(58f*u, 43f*u), androidx.compose.ui.geometry.CornerRadius(7f*u))
        drawRoundRect(Color.White, Offset(27f*u, 41f*u), androidx.compose.ui.geometry.Size(36f*u, 31f*u), androidx.compose.ui.geometry.CornerRadius(3.5f*u))
        val play = Path().apply {
            moveTo(37f*u, 48f*u)
            lineTo(53f*u, 56f*u)
            lineTo(37f*u, 64f*u)
            close()
        }
        drawPath(play, Color(0xFFE62117))
        drawCircle(Color.White, 3.2f*u, Offset(68.5f*u, 48.5f*u))
        drawCircle(Color.White, 3.2f*u, Offset(68.5f*u, 61.5f*u))
    }
}

@Composable
private fun M3uFilterChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.clip(CircleShape)
            .background(when { selected -> FocusRed; focused -> FocusRed.copy(alpha = 0.32f); else -> Color(0xCC17191F) })
            .border(1.dp, if (focused || selected) FocusRed else Color.White.copy(alpha = 0.14f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .focusable()
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
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
            selected -> FocusRed
            focused -> FocusRed.copy(alpha = 0.78f)
            else -> Color.White.copy(alpha = 0.055f)
        },
        animationSpec = tween(180),
        label = "navPillColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused || selected) FocusRed.copy(alpha = if (selected) 0.95f else 0.75f) else Color.White.copy(alpha = 0.10f),
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
        if (focused) FocusRed.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.045f),
        tween(180),
        label = "actionPillColor"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (focused) FocusRed else Color.White.copy(alpha = 0.12f), CircleShape)
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
                Text("TV", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (compact) 17.sp else 20.sp, letterSpacing = 1.sp)
            }
        }
        val tabs: @Composable () -> Unit = {
            Row(
                modifier = if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("HOME" to "Home", "FAVOURITES" to "Favourites", "ALL_CHANNELS" to "All Channels").forEach { (tab, label) ->
                    val selected = currentTab == tab
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
                modifier = Modifier.fillMaxWidth().focusGroup().padding(horizontal = 14.dp, vertical = 10.dp),
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
    onFullscreen: () -> Unit
) {
    val liveTransition = rememberInfiniteTransition(label = "livePulse")
    val liveAlpha by liveTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotPulse"
    )
    val heroClockMs by produceState(initialValue = System.currentTimeMillis(), key1 = "heroClock") {
        while (true) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }
    val heroTimeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val heroDateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(7.dp).graphicsLayer { alpha = liveAlpha; scaleX = 0.85f + liveAlpha * 0.25f; scaleY = 0.85f + liveAlpha * 0.25f }.clip(CircleShape).background(FocusRed))
                Text("LIVE", color = FocusRed, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Row(
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(heroTimeFormat.format(Date(heroClockMs)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Box(Modifier.width(1.dp).height(10.dp).background(Color.White.copy(alpha = 0.25f)))
                Text(heroDateFormat.format(Date(heroClockMs)), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            if (resolution != null) {
                Box(
                    modifier = Modifier.clip(CircleShape).background(OakSurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(resolution, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(OakSurfaceElevated).border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable { onToggleFavorite() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (isFavorite) "★ Saved" else "☆ Favourite", color = ParchmentWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Box(Modifier.clip(CircleShape).background(Color.White).clickable { onFullscreen() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("⛶ Watch", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

// --- RESPONSIVE POSTER-STYLE CHANNEL CARD ---
@Composable
fun ChannelCard(
    channel: Channel,
    isSelected: Boolean,
    channelNumber: Int? = null,
    onSelect: () -> Unit,
    onUserInteract: () -> Unit,
    canRequestFocus: () -> Boolean = { true },
    modifier: Modifier = Modifier.width(88.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    var confirmPressed by remember { mutableStateOf(false) }
    var rapidNavigation by remember { mutableStateOf(false) }
    LaunchedEffect(rapidNavigation) {
        if (rapidNavigation) {
            delay(180)
            rapidNavigation = false
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val focusStyle = rememberRetroTvFocusStyle(isFocused, isSelected, rapidNavigation)
    val focusRequester = remember(channel.url) { FocusRequester() }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .retroTvFocusLayer(isFocused, focusStyle.scale)
            .focusRequester(focusRequester)
            .retroTvHoverRequestsFocus(focusRequester, canRequestFocus)
            .then(rememberRetroTvDpadRepeatThrottle { rapidNavigation = true })
            .clip(shape)
            .background(focusStyle.fill)
            .border(focusStyle.strokeWidth, focusStyle.stroke, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onUserInteract()
            }
            // Handle TV OK explicitly on the focused card. Consume key-down so the
            // clickable modifier cannot also dispatch a second selection on key-up.
            .onPreviewKeyEvent { keyEvent ->
                val isConfirm = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                if (isFocused && isConfirm) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (!confirmPressed) {
                                confirmPressed = true
                                onSelect()
                                onUserInteract()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            confirmPressed = false
                            true
                        }
                        else -> true
                    }
                } else {
                    false
                }
            }
            .focusable()
            .clickable {
                onSelect()
                onUserInteract()
            }
            .padding(horizontal = 7.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RemoteChannelLogo(
                    url = channel.logo,
                    channelTitle = channel.title,
                    modifier = Modifier.fillMaxWidth(0.76f).fillMaxHeight(0.76f)
                        .clip(RoundedCornerShape(6.dp))
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)
                        .size(7.dp).clip(CircleShape).background(
                            when (channel.status) {
                                ChannelStatus.ONLINE -> Color(0xFF4CAF50)
                                ChannelStatus.OFFLINE -> Color(0xFFE53935)
                                ChannelStatus.UNKNOWN -> Color(0xFF9E9E9E)
                            }
                        )
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = channel.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
            )
            val quality = channel.linkLabels.firstOrNull { it.contains("4K", true) || it.contains("FHD", true) || it.contains("HD", true) }
                ?: when {
                    channel.url.contains("2160", true) -> "4K"
                    channel.url.contains("1080", true) -> "FHD"
                    else -> "HD"
                }
            Text(quality.uppercase(Locale.ROOT), color = Color.White.copy(alpha = 0.68f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (channelNumber != null && channelNumber > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = channelNumber.toString().padStart(3, '0'),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun LiveNowEventCard(
    title: String,
    channel: Channel,
    badge: String = "LIVE NOW",
    channelNumber: Int? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.widthIn(min = 260.dp).width(260.dp)
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = RoundedCornerShape(10.dp),
                clip = false,
                ambientColor = ChannelCardFocusGlow,
                spotColor = ChannelCardFocusGlow
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) ChannelCardFocusedFill else OakSurface)
            .border(1.5.dp, if (focused) FocusRed else RetroTvFocusKit.CricketCardStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }.focusable()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(FocusRed))
                Text(badge, color = FocusRed, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(channel.title, color = RetroTvFocusKit.SecondaryText, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (channelNumber != null && channelNumber > 0) {
                    Text(
                        text = "${channelNumber.toString().padStart(3, '0')} • HD",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(FocusRed.copy(alpha = 0.88f))
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        RemoteChannelLogo(channel.logo, channel.title, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun NewsTickerBar(headlines: List<String>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (headlines.isEmpty()) return
    var focused by remember { mutableStateOf(false) }
    val tickerText = remember(headlines) { headlines.joinToString("     •     ") }
    Row(
        modifier = modifier.fillMaxWidth().height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xE6101116))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .onFocusChanged { focused = it.isFocused }.focusable(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.fillMaxHeight().background(Color(0xFFB5121B)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            factory = { context ->
                android.widget.TextView(context).apply {
                    text = tickerText
                    setTextColor(android.graphics.Color.rgb(0xF5, 0xF5, 0xF5))
                    textSize = 11f
                    setSingleLine(true)
                    setHorizontallyScrolling(true)
                    ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    isSelected = true
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((8 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt(), 0)
                    setHorizontalFadingEdgeEnabled(true)
                    setFadingEdgeLength((36 * resources.displayMetrics.density).toInt())
                }
            },
            update = { view ->
                if (view.text.toString() != tickerText) view.text = tickerText
                view.isSelected = true
            }
        )
    }
}

// --- LANDSCAPE GLASS CHANNEL SEARCH DRAWER ---
@Composable
fun SearchModal(
    channels: List<Channel>,
    favoriteUrls: List<String>,
    recentUrls: List<String>,
    epgPrograms: List<Programme>,
    voiceQuery: String,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val searchListState = rememberLazyListState()
    val searchScope = rememberCoroutineScope()
    var rapidNavigation by remember { mutableStateOf(false) }
    val isTv = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    LaunchedEffect(rapidNavigation) {
        if (rapidNavigation) {
            delay(180)
            rapidNavigation = false
        }
    }
    LaunchedEffect(Unit) {
        delay(if (isTv) 160L else 80L)
        runCatching { searchFocusRequester.requestFocus() }
    }
    LaunchedEffect(voiceQuery) {
        query = voiceQuery
    }

    val now = System.currentTimeMillis()
    val channelByUrl = remember(channels) { channels.associateBy { it.url } }
    val channelIndexByUrl = remember(channels) { channels.withIndex().associate { it.value.url to it.index } }
    val favoriteUrlSet = remember(favoriteUrls) { favoriteUrls.toHashSet() }
    val programmesByChannel = remember(epgPrograms) {
        epgPrograms.groupBy { it.channelId.lowercase(Locale.ROOT) }
    }
    val visibleChannels = remember(query, selectedFilter, channels, favoriteUrlSet, recentUrls, channelByUrl, channelIndexByUrl) {
        val recent = recentUrls.mapNotNull { channelByUrl[it] }
        val favorites = channels.filter { it.url in favoriteUrlSet }
        val base = when (selectedFilter) {
            "Favorites" -> favorites
            "Recent" -> recent
            "News", "Sports", "Movies" -> channels.filter { it.group.contains(selectedFilter, true) || it.title.contains(selectedFilter, true) }
            else -> (recent + favorites + channels).distinctBy { it.url }
        }
        if (query.isBlank()) base
        else {
            val number = query.trim().toIntOrNull()
            base.filterIndexed { _, channel ->
                val index = channelIndexByUrl[channel.url] ?: -1
                val numberMatch = number != null && index + 1 == number
                numberMatch || channel.title.contains(query.trim(), ignoreCase = true)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val drawerWidth = (maxWidth * 0.40f).coerceIn(280.dp, 560.dp)
        val searchEdgePadding = (maxHeight / 2 - 32.dp).coerceAtLeast(24.dp)
        // The video remains visible under a light scrim; tapping outside dismisses the drawer.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)).clickable { onClose() })
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .imePadding()
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xD112141A))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .clickable(enabled = false) { }
                .padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
                .graphicsLayer { clip = false }
                .then(rememberRetroTvDpadRepeatThrottle { rapidNavigation = true }),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF292D36))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernSearchIcon(Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search channel name or number...", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(FocusRed),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        singleLine = true
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable {
                            if (query.isEmpty()) {
                                searchFocusRequester.requestFocus()
                                keyboardController?.show()
                            } else query = ""
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (query.isEmpty()) "🎙" else "×", color = Color.White, fontSize = if (query.isEmpty()) 16.sp else 23.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(listOf("All", "Favorites", "Recent", "News", "Sports", "Movies")) { filter ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (selectedFilter == filter || focused) FocusRed else Color.White.copy(alpha = 0.07f))
                            .border(1.dp, if (selectedFilter == filter || focused) FocusRed else Color.White.copy(alpha = 0.14f), CircleShape)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { selectedFilter = filter }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(filter, color = Color.White, fontSize = 11.sp, fontWeight = if (selectedFilter == filter || focused) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (query.isNotBlank()) "SEARCH RESULTS (${visibleChannels.size})" else when (selectedFilter) {
                    "Favorites" -> "FAVOURITES (${visibleChannels.size})"
                    "Recent" -> "RECENTLY WATCHED (${visibleChannels.size})"
                    "News", "Sports", "Movies" -> "${selectedFilter.uppercase()} CHANNELS (${visibleChannels.size})"
                    else -> "RECENT & FAVOURITES (${visibleChannels.size})"
                },
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 7.dp)
            )
            if (visibleChannels.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No channels in this section yet" else "No matching channels found", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().focusGroup()
                        .graphicsLayer { clip = false }
                        .then(rememberRetroTvDpadRepeatThrottle { rapidNavigation = true }),
                    contentPadding = PaddingValues(vertical = searchEdgePadding),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(visibleChannels, key = { _, channel -> channel.url }) { channelPosition, channel ->
                        val channelIndex = channelIndexByUrl[channel.url] ?: -1
                        val channelProgrammes = channel.epgId
                            ?.lowercase(Locale.ROOT)
                            ?.let { programmesByChannel[it] }
                            .orEmpty()
                        val currentProgram = channelProgrammes.firstOrNull {
                            it.startTimeMs <= now && it.stopTimeMs > now
                        }
                        val upcomingProgram = channelProgrammes.firstOrNull { it.startTimeMs > now }
                        val streamBadge = channel.linkLabels.firstOrNull { label ->
                            val upper = label.uppercase()
                            upper.contains("FHD") || upper.contains("HD") || upper.contains("4K") || upper.contains("1080") || upper.contains("720")
                        }?.uppercase()
                        var focused by remember(channel.url) { mutableStateOf(false) }
                        var confirmPressed by remember(channel.url) { mutableStateOf(false) }
                        val searchCardFocus = rememberRetroTvFocusStyle(focused, rapidNavigation = rapidNavigation)
                        val searchCardRequester = remember(channel.url) { FocusRequester() }
                        Row(
                            Modifier.fillMaxWidth()
                                .retroTvFocusLayer(focused, searchCardFocus.scale)
                                .focusRequester(searchCardRequester)
                                .retroTvHoverRequestsFocus(searchCardRequester) { !searchListState.isScrollInProgress }
                                .clip(RoundedCornerShape(11.dp))
                                .background(searchCardFocus.fill)
                                .border(searchCardFocus.strokeWidth, searchCardFocus.stroke, RoundedCornerShape(11.dp))
                                .retroTvCenterOnFocus(searchListState, channelPosition, searchScope, { focused = it }, rapidNavigation = rapidNavigation)
                                .onPreviewKeyEvent { keyEvent ->
                                    val isConfirm = keyEvent.key == Key.DirectionCenter ||
                                        keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                                    if (!focused || !isConfirm) {
                                        false
                                    } else {
                                        when (keyEvent.type) {
                                            KeyEventType.KeyDown -> {
                                                if (!confirmPressed) {
                                                    confirmPressed = true
                                                    onSelectChannel(channel)
                                                }
                                                true
                                            }
                                            KeyEventType.KeyUp -> {
                                                confirmPressed = false
                                                true
                                            }
                                            else -> true
                                        }
                                    }
                                }
                                .focusable()
                                .clickable { onSelectChannel(channel) }
                                .padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp)) {
                                RemoteChannelLogo(channel.logo, channel.title, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                                Box(Modifier.align(Alignment.BottomEnd).size(9.dp).clip(CircleShape)
                                    .background(when (channel.status) { ChannelStatus.ONLINE -> Color(0xFF20C66B); ChannelStatus.OFFLINE -> Color(0xFFE53935); else -> Color(0xFF8A8A8A) })
                                    .border(1.dp, Color(0xD112141A), CircleShape))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text((channelIndex + 1).toString().padStart(3, '0'), color = FocusRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    Text(channel.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    currentProgram?.let { "${it.title} • Live" }
                                        ?: upcomingProgram?.let { "Next: ${it.title}" }
                                        ?: listOfNotNull(
                                            channel.group.takeIf { it.isNotBlank() && !it.equals("General", true) && !it.equals("Unknown", true) },
                                            channel.country.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }?.let { "Live Stream • $it" }
                                        ).joinToString(" • ").ifBlank { "Live Stream" },
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (streamBadge != null) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                    Text(streamBadge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOption(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier.then(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(when { selected -> FocusRed.copy(alpha = 0.30f); focused -> FocusRed.copy(alpha = 0.16f); else -> RetroGlass })
                .border(1.dp, if (focused || selected) FocusRed else RetroGlassBorder, RoundedCornerShape(8.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled = enabled)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = fontSize, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (focused) FocusRed.copy(alpha = 0.15f) else RetroGlass)
            .border(1.dp, if (focused) FocusRed else RetroGlassBorder, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle(!checked) }.focusable().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(if (checked) "ON" else "OFF", color = if (checked) FocusRed else WarmAshMuted, fontWeight = FontWeight.Bold)
    }
}

// --- SETTINGS MODAL ---
@Composable
fun SettingsModal(
    remoteIsTv: Boolean,
    remoteStatus: String,
    remoteAddress: String,
    remotePairCode: String,
    onOpenMobileRemote: () -> Unit,
    customM3uUrl: String,
    isWatchdogEnabled: Boolean,
    autoFailoverBackupLinks: Boolean,
    hideUnavailableChannels: Boolean,
    isPipEnabled: Boolean,
    selectedQuality: String,
    onSelectQuality: (String) -> Unit,
    updateStatus: String,
    isCheckingForUpdates: Boolean,
    isUpdateDownloaded: Boolean,
    onCheckForUpdates: () -> Unit,
    onLoadCustomM3u: (String) -> Unit,
    onResetM3u: () -> Unit,
    onToggleWatchdog: (Boolean) -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onToggleHideUnavailable: (Boolean) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onClearHistoryAndCache: () -> Unit,
    tickerMode: String,
    onTickerModeChange: (String) -> Unit,
    onClose: () -> Unit
) {
    var urlInput by remember(customM3uUrl) { mutableStateOf(customM3uUrl) }
    var urlFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (context.packageManager.hasSystemFeature("android.software.leanback")) {
            delay(120)
            runCatching { settingsFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RetroGlass)
                .border(1.dp, RetroGlassBorder, RoundedCornerShape(14.dp))
                .padding(24.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SETTINGS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    SettingsOption("✕", onClick = onClose, fontSize = 14.sp)
                }

                Text("PLAYLIST SOURCE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroGlass)
                        .border(1.dp, if (urlFocused) FocusRed else RetroGlassBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (urlInput.isEmpty()) {
                        Text("Enter M3U link...", color = WarmAshMuted, fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().focusRequester(settingsFocusRequester).onFocusChanged { urlFocused = it.isFocused }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsOption("Apply", selected = true, onClick = { onLoadCustomM3u(urlInput); onClose() }, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    SettingsOption("Reset Default", onClick = { onResetM3u(); onClose() }, modifier = Modifier.weight(1f), fontSize = 12.sp)
                }

                SettingToggleRow("15s Watchdog Auto-skip", isWatchdogEnabled, onToggleWatchdog)

                SettingToggleRow("Auto-Failover Backup Links", autoFailoverBackupLinks, onToggleAutoFailover)
                SettingToggleRow("Hide Unavailable Channels", hideUnavailableChannels, onToggleHideUnavailable)
                SettingToggleRow("Picture-in-Picture (PiP)", isPipEnabled, onTogglePip)

                Text("LIVE NEWS TICKER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OFF", "HOME ONLY", "ALWAYS ON").forEach { mode ->
                        SettingsOption(mode, selected = tickerMode == mode, onClick = { onTickerModeChange(mode) }, modifier = Modifier.weight(1f), fontSize = 9.sp)
                    }
                }

                Text("STREAM QUALITY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Auto", "1080p", "720p", "480p").forEach { quality ->
                        SettingsOption(quality, selected = selectedQuality == quality, onClick = { onSelectQuality(quality) }, modifier = Modifier.weight(1f), fontSize = 11.sp)
                    }
                }

                SettingsOption("Clear Watch History & Cache", onClick = onClearHistoryAndCache, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp)

                Text("MOBILE REMOTE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (remoteIsTv) {
                    Text("Connect your phone to the same Wi-Fi, then enter this address and PIN in its Smart Remote screen.",
                        color = WarmAshMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    Text("TV address: $remoteAddress", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Pairing PIN: $remotePairCode", color = FocusRed, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Server: $remoteStatus", color = WarmAshMuted, fontSize = 10.sp)
                } else {
                    SettingsOption("Open Smart Remote", selected = true, onClick = onOpenMobileRemote,
                        modifier = Modifier.fillMaxWidth(), fontSize = 12.sp)
                    Text("Connect this phone and your RETROTV device to the same Wi-Fi network.",
                        color = WarmAshMuted, fontSize = 11.sp)
                }

                Text("APP UPDATE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                SettingsOption(
                    when {
                        isCheckingForUpdates -> "Checking / downloading…"
                        isUpdateDownloaded -> "Install downloaded update"
                        else -> "Check for updates"
                    },
                    selected = true,
                    enabled = !isCheckingForUpdates,
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
                Text(updateStatus, color = WarmAshMuted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
fun UpdateNoticeDialog(
    version: String,
    releaseNotes: String,
    selectedAction: Int,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(16.dp))
                .background(OakSurface).border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("RETROTV UPDATE AVAILABLE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("Version $version is ready. Here's what's changed:", color = Color.White, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)).background(OakSurfaceElevated).padding(12.dp)) {
                Text(
                    releaseNotes.ifBlank { "This release contains improvements and fixes. See the GitHub release page for details." },
                    color = ParchmentWhite, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selectedAction == 0) FocusRed else Color.White)
                    .border(if (selectedAction == 0) 2.dp else 0.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onInstall).focusable().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Install update", color = if (selectedAction == 0) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selectedAction == 1) FocusRed else OakSurfaceElevated)
                    .border(if (selectedAction == 1) 2.dp else 0.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss).focusable().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Later", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun OverlayIconAction(
    icon: String,
    selected: Boolean,
    drawableId: Int? = null,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier.size(if (selected) 42.dp else 38.dp)
            .graphicsLayer { scaleX = if (selected) 1.04f else 1f; scaleY = if (selected) 1.04f else 1f }
            .clip(CircleShape)
            .background(if (selected) FocusRed.copy(alpha = 0.92f) else Color.Transparent)
            // Playback toolbar navigation is handled centrally by MainActivity's D-pad
            // state machine; prevent Compose focus from competing with that selection.
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (drawableId != null) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                modifier = Modifier.size(19.dp).graphicsLayer { alpha = contentAlpha },
                colorFilter = ColorFilter.tint(Color.White)
            )
        } else {
            Text(icon, color = Color.White.copy(alpha = contentAlpha), fontSize = 20.sp, fontWeight = FontWeight.Normal)
        }
        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.Black.copy(alpha = 0.78f) else FocusRed)
                    .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, lineHeight = 9.sp)
            }
        }
    }
}

// --- MAIN ACTIVITY CONTROLLER ---
class MainActivity : ComponentActivity() {

    private val isTvDevice: Boolean by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    private val remotePairCode: String by lazy {
        val prefs = getSharedPreferences(TvRemoteBackgroundService.PREFS, MODE_PRIVATE)
        prefs.getString(TvRemoteBackgroundService.KEY_PIN, null) ?: (java.security.SecureRandom().nextInt(900_000) + 100_000)
            .toString().also { prefs.edit().putString(TvRemoteBackgroundService.KEY_PIN, it).apply() }
    }
    private val remoteServerStatus = mutableStateOf("Not running")
    private val remoteServerAddress = mutableStateOf("Unavailable")

    private lateinit var audioManager: AudioManager
    private lateinit var playerController: PlayerController
    private val player: ExoPlayer get() = playerController.player

    private val isUiVisible = mutableStateOf(true)
    private val isPlaybackOverlayVisible = mutableStateOf(false)
    private val isQualityPickerVisible = mutableStateOf(false)
    private val isLinkPickerVisible = mutableStateOf(false)
    private val isEpgOverlayVisible = mutableStateOf(false)
    private val selectedPlaybackAction = mutableIntStateOf(0)
    private val playbackControlsLocked = mutableStateOf(false)
    private val scalingModeIndex = mutableIntStateOf(0)
    private val scalingModes = listOf("Fit", "Fill", "Zoom")
    private var playbackPlayerView: PlayerView? = null
    private val qualityPickerIndex = mutableIntStateOf(0)
    private val qualityOptions = listOf("Auto", "1080p", "720p", "480p")
    private val linkPickerIndex = mutableIntStateOf(0)
    private val activeStreamIndex = mutableIntStateOf(0)
    private val triedStreamIndices = mutableSetOf<Int>()
    private val isStartupVisible = mutableStateOf(true)
    private val isSearchOpen = mutableStateOf(false)
    private val searchVoiceQuery = mutableStateOf("")
    private val isSettingsOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)
    private val updateStatus = mutableStateOf("Check for a newer RETROTV version.")
    private val isCheckingForUpdates = mutableStateOf(false)
    private val isUpdateDownloaded = mutableStateOf(false)
    private val updateNotice = mutableStateOf<UpdateNotice?>(null)
    private val selectedUpdateAction = mutableIntStateOf(0)
    private var downloadedUpdateApk: File? = null

    private val currentTab = mutableStateOf("HOME")
    private val selectedGroupChip = mutableStateOf("All")
    private val currentResolution = mutableStateOf<String?>(null)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val isChannelInfoVisible = mutableStateOf(false)
    private val playbackRetryMessage = mutableStateOf<String?>(null)
    private val playlistStatus = mutableStateOf("Loading playlists…")
    private val recentChannelUrls = mutableStateListOf<String>()
    private val epgPrograms = mutableStateListOf<Programme>()
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var isHostForeground = false
    private var healthCheckJob: Job? = null
    private var backgroundNetworkResumeJob: Job? = null
    private var healthCheckPending = false
    private var liveContentRefreshPending = false

    private companion object {
        const val MAX_PLAYLIST_BYTES = 12L * 1024L * 1024L
        const val MAX_PLAYLIST_CHARS = 12 * 1024 * 1024
        const val MAX_PLAYLIST_CHANNELS = 20_000
        const val MAX_PLAYLIST_LINES = 500_000
        const val MAX_PLAYLIST_LINE_CHARS = 16 * 1024
        // Playback toolbar action indices (must stay in sync with the toolbar layout).
        const val ACTION_FAVOURITE = 0
        const val ACTION_SEARCH = 1
        const val ACTION_QUALITY = 2
        const val ACTION_CHANGE_LINK = 3
        const val ACTION_EPG = 4
        const val ACTION_SETTINGS = 5
        const val ACTION_SCALING = 6
        const val ACTION_LOCK = 7
        const val ACTION_MENU = 8
        const val ACTION_NUMBER_PAD = 9
        const val PLAYBACK_ACTION_COUNT = 10
        const val WATCHDOG_TIMEOUT_MS = 15_000L
        const val REQUEST_VOICE_SEARCH = 7412
    }

    private val customM3uUrl = mutableStateOf("")
    private val isWatchdogEnabled = mutableStateOf(true)
    private val autoFailoverBackupLinks = mutableStateOf(true)
    private val hideUnavailableChannels = mutableStateOf(false)
    private val isPipEnabled = mutableStateOf(false)
    private val liveNewsTickerMode = mutableStateOf("HOME ONLY")
    private val tickerHeadlines = mutableStateListOf<String>()
    private val liveContentCards = mutableStateListOf<LiveContentCard>()
    private var liveContentJob: Job? = null
    private val selectedQuality = mutableStateOf("Auto")
    private var currentStreamUrl = ""
    private val favoriteUrls = mutableStateListOf<String>()
    private val channels = mutableStateListOf<Channel>()

    private var autoHideJob: Job? = null
    private var watchdogJob: Job? = null
    private var channelInfoJob: Job? = null
    private val typedChannelNumber = mutableStateOf("")
    private var channelNumberJob: Job? = null
    private val isChannelNumberPadVisible = mutableStateOf(false)
    private val channelNumberPadFocus = mutableIntStateOf(0)
    private val channelNumberOriginalIndex = mutableIntStateOf(0)
    private val channelNumberPreviewIndex = mutableIntStateOf(-1)

    // Local fallback (first 10 channels from playlist.m3u), used only until the live playlist loads
    private val fallbackChannels = listOf(
        Channel(
            title = "Somoy TV",
            url = "https://ireentv.pages.dev/Somoy_TV_Server_6.m3u8",
            group = "News",
            logo = "https://dl.dropbox.com/s/leielj83em5kg7h/somoy_news.png",
            backupUrls = listOf(
                "http://116.204.149.16/somoytv/index.m3u8",
                "https://tvsen6.aynaott.com/4XcqdovJzbbC9WdJA9gk/index.m3u8",
                "https://ireentv.pages.dev/Somoy_TV_Server_3.m3u8"
            ),
            linkLabels = listOf("HD", "HD", "Link 3", "Link 5")
        ),
        Channel(
            title = "Jamuna TV",
            url = "https://tvsen6.aynaott.com/KGdZEdA7qQ43dmPkgk1j/index.m3u8",
            group = "News",
            logo = "https://raw.githubusercontent.com/time2shine/IPTV-logos/main/logos/bd_jamuna_tv.png",
            backupUrls = listOf(
                "https://tvsen6.aynaott.com/KGdZEdA7qQ43dmPkgk1j/index.m3u8?e=1784102531&u=ee5437a7-c16b-4700-b317-a41b77d5cba9&token=d17e86aa2e6745043e84e0547d4fb69a",
                "https://playztv-apps.pages.dev/jamuna-tv/index.m3u8"
            ),
            linkLabels = listOf("Link 1", "Link 2", "Link 3")
        ),
        Channel(
            title = "Ekattor TV",
            url = "https://tvsen6.aynaott.com/EWDrV5QskgarZEUBb3pU/index.m3u8",
            group = "News",
            logo = "https://raw.githubusercontent.com/time2shine/IPTV-logos/main/logos/bd_ekattor_tv.png",
            backupUrls = listOf(
                "https://ireentv.pages.dev/Ekattor_TV_Server_1.m3u8",
                "https://tvsen6.aynaott.com/EWDrV5QskgarZEUBb3pU/index.m3u8?e=1784102532&u=ee5437a7-c16b-4700-b317-a41b77d5cba9&token=3b9afdda2bd5332e8f49ed7dfa321a06"
            ),
            linkLabels = listOf("HD", "HD", "HD")
        ),
        Channel(
            title = "DBC News",
            url = "https://tvsen6.aynaott.com/pF66Tkz0qFwP2aMMqHyt/index.m3u8",
            group = "News",
            logo = "https://i.imgur.com/Qbt6q4z.png",
            backupUrls = listOf(
                "https://iptv-proxy.ahmed-bd-org.workers.dev/dbc-news/index.m3u8",
                "https://iptvlive.ahmed-bd-org.workers.dev/dbc-news-hd-1/index.m3u8",
                "https://tvsen6.aynaott.com/pF66Tkz0qFwP2aMMqHyt/index.m3u8?e=1784102535&u=ee5437a7-c16b-4700-b317-a41b77d5cba9&token=d1848b7fd84cef719a89b61f0f5704ee",
                "https://playztv-apps.pages.dev/dbc-news/index.m3u8"
            ),
            linkLabels = listOf("Link 1", "Link 2", "Link 3", "Link 4", "Link 5")
        ),
        Channel(
            title = "Channel 24",
            url = "https://tvsen6.aynaott.com/YrVDF8gujkdh/index.m3u8",
            group = "News",
            logo = "https://raw.githubusercontent.com/time2shine/IPTV-logos/main/logos/bd_channel_24.png",
            backupUrls = listOf(
                "https://ireentv.pages.dev/Channel_24_Backup_3.m3u8",
                "https://live-vestinn.cdnvideo.ru/vestinn/nn24-khl/playlist.m3u8"
            ),
            linkLabels = listOf("Link 1", "Link 2", "Link 3")
        ),
        Channel(
            title = "RTV",
            url = "http://116.204.149.16/rtvhd/index.m3u8",
            group = "Bangladesh",
            logo = "https://i.imgur.com/yu8ugqt.png",
            backupUrls = listOf(
                "https://j6nfugukcl.gpcdn.net/tile_6/master.m3u8",
                "https://tvsen5.aynaott.com/RtvHD/index.m3u8",
                "https://tvsen5.aynaott.com/RtvHD/index.m3u8?e=1784102560&u=ee5437a7-c16b-4700-b317-a41b77d5cba9&token=341d28b0504fc6b2cb3fd78fafa0a854",
                "https://app24.jagobd.com.bd/c3VydmVyX8RpbEU9Mi8xNy8yMFDEEHGcfRgzQ6NTAgdEoaeFzbF92YWxIZTO0U0ezN1IzMyfvcEdsEfeDeKiNkVN3PTOmdFseWRtaW51aiPhnPTI2/rtv-sg.stream/index.m3u8"
            ),
            linkLabels = listOf("Link 1", "Link 2", "Link 3", "Link 4", "Link 6")
        ),
        Channel(
            title = "Maasranga TV",
            url = "https://mtv.sunplex.live/MAASRANGA/index.m3u8",
            group = "Bangladesh",
            logo = "https://i.imgur.com/uVZJMed.png",
            backupUrls = listOf(
                "https://tvsen5.aynaott.com/maasrangatv/index.m3u8",
                "http://116.204.149.16/maasranga/index.m3u8",
                "https://ireentv.pages.dev/Maasranga_TV_Server_1.m3u8",
                "https://ireentv.pages.dev/Maasranga_TV_Backup_3.m3u8",
                "https://tvsen5.aynaott.com/maasrangatv/index.m3u8?e=1784102562&u=ee5437a7-c16b-4700-b317-a41b77d5cba9&token=2044540ee613d44b1175f2885b2ab1ee"
            ),
            linkLabels = listOf("HD", "HD", "HD", "HD", "HD", "HD")
        ),
        Channel(
            title = "News 24",
            url = "https://tvsen6.aynaott.com/cdgr3tw6WoG7JyRnLbi0/index.m3u8",
            group = "News",
            backupUrls = listOf(
                "https://iptvlive.ahmed-bd-org.workers.dev/news-24-hd-1/index.m3u8",
                "https://ireentv.pages.dev/News_24_Backup_1.m3u8",
                "https://tvsen6.aynaott.com/cdgr3tw6WoG7JyRnLbi0/index.m3u8?e=1784102534&u=ee5437a7-c16b-4700-b317-a41b77d5cba9",
                "https://iptvlive.ahmed-bd-org.workers.dev/news-24-1/index.m3u8",
                "https://ireentv.pages.dev/News_24_HD_Server_1.m3u8"
            ),
            linkLabels = listOf("HD", "HD", "HD", "HD", "HD", "HD")
        ),
        Channel(
            title = "Independent TV",
            url = "https://ireentv.pages.dev/Independent_TV_Server_1.m3u8",
            group = "News",
            logo = "https://raw.githubusercontent.com/time2shine/IPTV-logos/main/logos/bd_independent_television.png",
            backupUrls = listOf(
                "https://tvsen6.aynaott.com/Wef2Vj67njeYGDqQNFB9/index.m3u8"
            ),
            linkLabels = listOf("Link 1", "Link 2")
        ),
        Channel(
            title = "Ekhon TV",
            url = "https://tvsen6.aynaott.com/fbgZV3X17hwWcyfZ4pdb/index.m3u8",
            group = "News",
            logo = "https://i.imgur.com/tRrDh6z.png",
            backupUrls = listOf(
                "https://ireentv.pages.dev/Ekhon_TV_Server_1.m3u8",
                "https://tvsen6.aynaott.com/fbgZV3X17hwWcyfZ4pdb/tracks-v1a1/mono.ts.m3u8"
            ),
            linkLabels = listOf("HD", "HD", "HD")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadSettingsFromStorage()
        // Never trust an APK left in cache across launches: it may belong to an older release.
        File(cacheDir, "updates").listFiles()?.forEach { cached ->
            if (cached.isFile && cached.extension.equals("apk", ignoreCase = true)) cached.delete()
        }
        downloadedUpdateApk = null
        isUpdateDownloaded.value = false
        // Always query GitHub on startup before downloading or offering an update.
        lifecycleScope.launch {
            delay(5000)
            checkForUpdates()
        }

        playerController = PlayerController(this)
        player.apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering.value = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            retryJob?.cancel()
                            retryAttempt = 0
                            playbackRetryMessage.value = null
                            watchdogJob?.cancel()
                            // The link that works now is the only one in the new failover cycle.
                            triedStreamIndices.clear()
                            triedStreamIndices.add(activeStreamIndex.value)
                            lifecycleScope.launch {
                                delay(650)
                                isStartupVisible.value = false
                            }
                            val idx = currentChannelIndex.value
                            if (idx in channels.indices) {
                                channels[idx] = channels[idx].copy(status = ChannelStatus.ONLINE)
                            }
                            scheduleBackgroundNetworkWorkAfterReady()
                            broadcastRemoteState()
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
                            broadcastRemoteState()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (!isHostForeground) return
                        isBuffering.value = false
                        retryJob?.cancel()
                        retryAttempt += 1
                        val maxRetries = 2
                        if (retryAttempt <= maxRetries) {
                            val attempt = retryAttempt
                            playbackRetryMessage.value = "Stream failed — retry $attempt/$maxRetries…"
                            retryJob = lifecycleScope.launch {
                                delay(attempt * 1_000L)
                                if (currentChannelIndex.value in channels.indices) {
                                    playerController.setStream(currentStreamUrl)
                                    player.prepare()
                                }
                            }
                        } else {
                            tryNextBackupOrAdvanceChannel("Stream failed")
                        }
                    }
            })
        }

        applyStreamQuality(showToast = false)
        loadPlaylists()
        resetIdleTimer()

        setContent {
            var epgClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000)
                    epgClockMs = System.currentTimeMillis()
                }
            }
            LaunchedEffect(Unit) {
                // Never leave the branded launch cover blocking a slow or unavailable stream.
                delay(1500)
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
            LaunchedEffect(isPlaybackOverlayVisible.value) {
                if (isPlaybackOverlayVisible.value) {
                    selectedPlaybackAction.value = ACTION_FAVOURITE
                } else {
                    isQualityPickerVisible.value = false
                    isLinkPickerVisible.value = false
                    isEpgOverlayVisible.value = false
                }
            }
            LaunchedEffect(
                isPlaybackOverlayVisible.value, isUiVisible.value, isSearchOpen.value,
                isSettingsOpen.value, selectedPlaybackAction.value,
                isQualityPickerVisible.value, isLinkPickerVisible.value, isEpgOverlayVisible.value
            ) {
                // Keep the playback toolbar and its open panels available while the user
                // is navigating them with a TV remote.
                if (isPlaybackOverlayVisible.value && !isUiVisible.value &&
                    !isSearchOpen.value && !isSettingsOpen.value &&
                    !isQualityPickerVisible.value && !isLinkPickerVisible.value && !isEpgOverlayVisible.value
                ) {
                    delay(6500)
                    isPlaybackOverlayVisible.value = false
                }
            }

            BackHandler(enabled = true) {
                when {
                    isLinkPickerVisible.value -> isLinkPickerVisible.value = false
                    isSearchOpen.value -> {
                        isSearchOpen.value = false
                        searchVoiceQuery.value = ""
                    }
                    isSettingsOpen.value -> isSettingsOpen.value = false
                    isUiVisible.value -> isUiVisible.value = false
                    else -> {
                        isUiVisible.value = true
                        resetIdleTimer()
                    }
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
                        .clickable { if (!isUiVisible.value) isPlaybackOverlayVisible.value = true }
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
                                playbackPlayerView = this
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize().pointerInput(channels.size, currentChannelIndex.value, playbackControlsLocked.value) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, amount -> dragDistance += amount },
                                onDragEnd = {
                                    if (!playbackControlsLocked.value && abs(dragDistance) > 85f) {
                                        switchChannel(if (dragDistance < 0) 1 else -1)
                                        isChannelInfoVisible.value = true
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        }
                    )

                    AnimatedVisibility(
                        visible = isPlaybackOverlayVisible.value && !isUiVisible.value && !isSearchOpen.value && !isSettingsOpen.value,
                        modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(220))
                    ) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.42f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
                        val infoChannel = channels.getOrNull(currentChannelIndex.value)
                        val schedule = epgPrograms.filter { it.channelId == infoChannel?.epgId }.sortedBy { it.startTimeMs }
                        val currentProgramme = schedule.firstOrNull { it.startTimeMs <= epgClockMs && it.stopTimeMs > epgClockMs }
                        val nextProgramme = schedule.firstOrNull { it.startTimeMs > epgClockMs }
                        Box(Modifier.fillMaxSize()) {
                            val channelLinks = infoChannel?.allUrls.orEmpty()
                            val hasAlternativeLinks = channelLinks.size > 1
                            val activeLinkSubtitle = if (hasAlternativeLinks) {
                                val safeIdx = activeStreamIndex.value.coerceIn(0, channelLinks.lastIndex)
                                "${infoChannel?.labelForStream(safeIdx)} (${safeIdx + 1}/${channelLinks.size})"
                            } else null
                            if (!isUiVisible.value && !isSearchOpen.value && !isSettingsOpen.value) {
                            Row(Modifier.align(Alignment.TopStart).fillMaxWidth(0.68f).padding(start = 34.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    RemoteChannelLogo(infoChannel?.logo, infoChannel?.title ?: "TV", Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                                    Text(
                                        text = (currentChannelIndex.value + 1).toString().padStart(3, '0'),
                                        color = FocusRed,
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp,
                                        maxLines = 1
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(infoChannel?.title ?: "Live TV", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val fallbackQuality = infoChannel?.linkLabels?.firstOrNull {
                                        it.contains("4K", true) || it.contains("FHD", true) || it.contains("HD", true)
                                    } ?: "HD"
                                    val groupTitle = infoChannel?.group?.trim()?.takeIf {
                                        it.isNotBlank() && !it.equals("General", true) && !it.equals("Unknown", true) && !it.equals("Undefined", true)
                                    } ?: "Live"
                                    val channelMeta = listOf(groupTitle, currentResolution.value ?: fallbackQuality, activeLinkSubtitle)
                                        .filterNotNull().distinct().joinToString("  •  ")
                                    Text(channelMeta, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            }
                            val playbackLiveTransition = rememberInfiniteTransition(label = "playbackLivePulse")
                            val playbackLiveAlpha by playbackLiveTransition.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(650, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "playbackLiveDot"
                            )
                            val playbackClockMs by produceState(initialValue = System.currentTimeMillis(), key1 = "playbackClock") {
                                while (true) {
                                    delay(1000L)
                                    value = System.currentTimeMillis()
                                }
                            }
                            val playbackTimeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
                            val playbackDateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
                            Column(
                                Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 28.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                        .padding(horizontal = 15.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(Modifier.size(8.dp).graphicsLayer {
                                        alpha = playbackLiveAlpha
                                        scaleX = 0.85f + playbackLiveAlpha * 0.25f
                                        scaleY = 0.85f + playbackLiveAlpha * 0.25f
                                    }.clip(CircleShape).background(FocusRed))
                                    Text("LIVE", color = FocusRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        playbackTimeFormat.format(Date(playbackClockMs)),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        playbackDateFormat.format(Date(playbackClockMs)),
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 22.dp)
                                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.22f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OverlayIconAction("", selectedPlaybackAction.value == ACTION_FAVOURITE, R.drawable.ic_favourite) { infoChannel?.let { toggleFavorite(it.url) } }
                                OverlayIconAction("", selectedPlaybackAction.value == ACTION_SEARCH, R.drawable.ic_search) { isSearchOpen.value = true }
                                OverlayIconAction("ᴴᴰ", selectedPlaybackAction.value == ACTION_QUALITY) {
                                    qualityPickerIndex.value = qualityOptions.indexOf(selectedQuality.value).coerceAtLeast(0)
                                    isQualityPickerVisible.value = !isQualityPickerVisible.value
                                    isLinkPickerVisible.value = false
                                    isEpgOverlayVisible.value = false
                                }
                                OverlayIconAction(
                                    icon = "",
                                    selected = selectedPlaybackAction.value == ACTION_CHANGE_LINK,
                                    drawableId = R.drawable.ic_change_link,
                                    enabled = hasAlternativeLinks,
                                    badge = if (hasAlternativeLinks) "${activeStreamIndex.value.coerceIn(0, channelLinks.lastIndex) + 1}/${channelLinks.size}" else null
                                ) {
                                    openOrCycleChannelLink()
                                }
                                OverlayIconAction("▤", selectedPlaybackAction.value == ACTION_EPG) {
                                    isEpgOverlayVisible.value = !isEpgOverlayVisible.value
                                    isQualityPickerVisible.value = false
                                    isLinkPickerVisible.value = false
                                }
                                OverlayIconAction("⚙", selectedPlaybackAction.value == ACTION_SETTINGS) { isSettingsOpen.value = true }
                                OverlayIconAction("⛶", selectedPlaybackAction.value == ACTION_SCALING) { cycleVideoScaling() }
                                OverlayIconAction(if (playbackControlsLocked.value) "🔒" else "▣", selectedPlaybackAction.value == ACTION_LOCK) { playbackControlsLocked.value = !playbackControlsLocked.value }
                                OverlayIconAction("▦", selectedPlaybackAction.value == ACTION_MENU) { openChannelMenuFromPlayback() }
                                OverlayIconAction("123", selectedPlaybackAction.value == ACTION_NUMBER_PAD) { openChannelNumberPad() }
                            }
                            if (isChannelNumberPadVisible.value) {
                                Column(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.94f))
                                        .border(1.dp, FocusRed.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Text("TUNE CHANNEL", color = FocusRed, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                                    Text(
                                        typedChannelNumber.value.ifBlank { "— — —" },
                                        color = Color.White,
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    val previewChannel = channels.getOrNull(channelNumberPreviewIndex.intValue)
                                        .takeIf { channelNumberPreviewIndex.intValue >= 0 && typedChannelNumber.value.toIntOrNull() == channelNumberPreviewIndex.intValue + 1 }
                                    val hasLongerMatch = typedChannelNumber.value.isNotBlank() &&
                                        !typedChannelNumber.value.startsWith("0") &&
                                        channels.indices.any { index ->
                                            val candidate = (index + 1).toString()
                                            candidate.length > typedChannelNumber.value.length && candidate.startsWith(typedChannelNumber.value)
                                        }
                                    Text(
                                        previewChannel?.title ?: if (hasLongerMatch) "Keep typing to choose a longer number" else "Enter a channel number",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val keypadRows = listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("CLEAR", "0", "OK")
                                    )
                                    keypadRows.forEachIndexed { rowIndex, row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEachIndexed { colIndex, keyLabel ->
                                                val focusIndex = rowIndex * 3 + colIndex
                                                val focused = channelNumberPadFocus.intValue == focusIndex
                                                Box(
                                                    modifier = Modifier
                                                        .width(if (keyLabel == "CLEAR") 82.dp else 58.dp)
                                                        .height(42.dp)
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .background(if (focused) FocusRed else Color.White.copy(alpha = 0.10f))
                                                        .border(if (focused) 1.dp else 0.dp, if (focused) Color.White.copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(9.dp))
                                                        .clickable {
                                                            channelNumberPadFocus.intValue = focusIndex
                                                            when (focusIndex) {
                                                                9 -> clearChannelNumberEntry()
                                                                11 -> confirmChannelNumberEntry()
                                                                10 -> appendChannelNumberDigit(0)
                                                                else -> appendChannelNumberDigit(focusIndex + 1)
                                                            }
                                                        }
                                                        .focusable(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(keyLabel, color = Color.White, fontSize = if (keyLabel.length > 1) 10.sp else 17.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    Text("Preview updates after a short pause • OK confirms • Back cancels", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp)
                                }
                            }
                            if (isQualityPickerVisible.value) {
                                // Keep the quality choices centred beneath the playback toolbar
                                // on both narrow mobile screens and wide Android TV layouts.
                                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.9f)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    qualityOptions.forEachIndexed { index, quality ->
                                        Box(Modifier.clip(RoundedCornerShape(7.dp))
                                            .background(if (qualityPickerIndex.value == index) FocusRed else Color.Transparent)
                                            .clickable {
                                                qualityPickerIndex.value = index
                                                selectedQuality.value = quality
                                                getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putString("stream_quality", quality).apply()
                                                applyStreamQuality()
                                                isQualityPickerVisible.value = false
                                            }
                                            .focusable()
                                            .padding(horizontal = 13.dp, vertical = 9.dp)) {
                                            Text(quality, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (isLinkPickerVisible.value && hasAlternativeLinks && infoChannel != null) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.9f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .horizontalScroll(rememberScrollState())
                                        .padding(7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    channelLinks.forEachIndexed { index, _ ->
                                        val isFocusedOption = linkPickerIndex.value == index
                                        val isPlayingOption = activeStreamIndex.value == index
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    when {
                                                        isFocusedOption -> FocusRed
                                                        isPlayingOption -> Color.White.copy(alpha = 0.16f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (isPlayingOption && !isFocusedOption) 1.dp else 0.dp,
                                                    color = if (isPlayingOption && !isFocusedOption) Color.White.copy(alpha = 0.45f) else Color.Transparent,
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                                .clickable {
                                                    linkPickerIndex.value = index
                                                    selectChannelStream(index, showToast = true)
                                                    isLinkPickerVisible.value = false
                                                }
                                                .focusable()
                                                .padding(horizontal = 13.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                text = infoChannel.labelForStream(index),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isFocusedOption || isPlayingOption) FontWeight.Bold else FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                            if (isEpgOverlayVisible.value) {
                                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.72f).padding(bottom = 92.dp)) {
                                    if (schedule.isNotEmpty()) {
                                        ProgrammeInfoCard(currentProgramme, nextProgramme)
                                    } else {
                                        Column(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                .background(Color.Black.copy(alpha = 0.92f))
                                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                                .padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text("PROGRAM GUIDE", color = FocusRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                            Text("No programme data available for this channel.", color = Color.White, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (typedChannelNumber.value.isNotEmpty() && !isUiVisible.value && !isChannelNumberPadVisible.value) {
                        Box(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.82f))
                                .border(1.dp, FocusRed.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "CH  ${typedChannelNumber.value}",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    // Foreground OTT Interface
                    AnimatedVisibility(
                        visible = isUiVisible.value,
                        enter = fadeIn(tween(350)),
                        exit = fadeOut(tween(350))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xB30A0B0E))
                        ) {
                            val activeChannel = channels.getOrNull(currentChannelIndex.value)
                            val isFav = favoriteUrls.contains(activeChannel?.url.orEmpty())

                            val priorityGroups = listOf("Bangla", "Sports", "News", "Movies", "Kids", "Music")
                            val groupNames = remember(channels.toList()) {
                                channels.map { it.group.trim().ifBlank { "General" } }
                                    .filter { it.isNotBlank() && !it.equals("Undefined", true) }
                                    .distinct()
                                    .sortedWith(compareBy<String>(
                                        { group -> priorityGroups.indexOfFirst { priority -> group.contains(priority, true) }.let { if (it < 0) Int.MAX_VALUE else it } },
                                        { it.lowercase(Locale.ROOT) }
                                    ))
                            }
                            val filteredGroups = remember(currentTab.value, selectedGroupChip.value, channels.toList(), favoriteUrls.toList()) {
                                when (currentTab.value) {
                                    "HOME" -> emptyMap()
                                    "FAVOURITES" -> mapOf("Favourites" to channels.filter { favoriteUrls.contains(it.url) && (!hideUnavailableChannels.value || it.status != ChannelStatus.OFFLINE) })
                                    "ALL_CHANNELS" -> {
                                        val channelSource = if (hideUnavailableChannels.value) channels.filter { it.status != ChannelStatus.OFFLINE } else channels.toList()
                                        val matching = if (selectedGroupChip.value == "All") channelSource else channelSource.filter {
                                            it.group.contains(selectedGroupChip.value, true)
                                        }
                                        mapOf(selectedGroupChip.value.uppercase(Locale.ROOT) to matching)
                                    }
                                    else -> emptyMap()
                                }
                            }

                            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                            val availableWidth = (screenWidth - 48.dp).coerceAtLeast(200.dp)
                            val columns = when {
                                screenWidth >= 1100.dp -> 10
                                screenWidth >= 850.dp -> 8
                                screenWidth >= 620.dp -> 6
                                screenWidth >= 420.dp -> 5
                                else -> 3
                            }
                            val gap = 4.dp
                            val tileWidth = (availableWidth - gap * (columns - 1)) / columns

                            Column(modifier = Modifier.fillMaxSize()) {
                                OttTopHeader(
                                    currentTab = currentTab.value,
                                    initialFocusRequester = initialMenuFocusRequester,
                                    onSelectTab = {
                                        currentTab.value = it
                                        if (it == "ALL_CHANNELS") selectedGroupChip.value = "All"
                                        resetIdleTimer()
                                    },
                                    onOpenSearch = {
                                        searchVoiceQuery.value = ""
                                        isSearchOpen.value = true
                                        resetIdleTimer()
                                    },
                                    onOpenSettings = {
                                        isSettingsOpen.value = true
                                        resetIdleTimer()
                                    }
                                )

                                if (currentTab.value == "ALL_CHANNELS") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 24.dp, vertical = 4.dp).focusGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("All", "Bangla", "Sports", "News", "Movies", "Kids").forEach { group ->
                                            M3uFilterChip(
                                                label = group,
                                                selected = selectedGroupChip.value.equals(group, true),
                                                onSelect = { selectedGroupChip.value = group; resetIdleTimer() }
                                            )
                                        }
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    if (currentTab.value == "HOME") {
                                    val liveNowHome = liveContentCards.toList().distinctBy { it.id }.take(8)
                                    if (liveNowHome.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Text("LIVE NOW", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp))
                                                LazyRow(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                                                    items(liveNowHome, key = { it.id }) { item ->
                                                        LiveNowEventCard(item.title, item.channel, item.badge, channels.indexOfFirst { it.url == item.channel.url }.takeIf { it >= 0 }?.plus(1)) {
                                                            val idx = channels.indexOfFirst { it.url == item.channel.url }
                                                            if (idx >= 0) { currentChannelIndex.value = idx; playChannel(idx); isUiVisible.value = false }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (liveNewsTickerMode.value in listOf("HOME ONLY", "OVERLAYS ONLY", "ALWAYS ON") && tickerHeadlines.isNotEmpty()) {
                                        item { NewsTickerBar(tickerHeadlines, Modifier.padding(horizontal = 24.dp), onClick = { playNewsChannelFromTicker() }) }
                                    }

                                    val recentlyWatched = recentChannelUrls.mapNotNull { url -> channels.firstOrNull { it.url == url } }
                                    item {
                                        if (recentlyWatched.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("RECENTLY WATCHED", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp))
                                                LazyRow(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                                                    items(recentlyWatched, key = { it.url }) { ch ->
                                                        ChannelCard(channel = ch, isSelected = ch.url == activeChannel?.url,
                                                            channelNumber = channels.indexOfFirst { it.url == ch.url }.takeIf { it >= 0 }?.plus(1),
                                                            onSelect = { val idx = channels.indexOfFirst { it.url == ch.url }; if (idx >= 0) { currentChannelIndex.value = idx; playChannel(idx); isUiVisible.value = false; isSearchOpen.value = false; isSettingsOpen.value = false } },
                                                            onUserInteract = { resetIdleTimer() },
                                                            canRequestFocus = { !listState.isScrollInProgress })
                                                    }
                                                }
                                            }
                                        } else {
                                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text("RECENTLY WATCHED", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                                Text("Your recently watched channels will appear here.", color = WarmAshMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    }

                                    filteredGroups.forEach { (groupName, groupChannels) ->
                                        if (groupChannels.isNotEmpty()) {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = groupName.uppercase(),
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 1.sp
                                                    )
                                                    if (currentTab.value == "HOME") {
                                                        Row(
                                                            modifier = Modifier.clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.07f))
                                                                .clickable {
                                                                    selectedGroupChip.value = groupName
                                                                    currentTab.value = "ALL_CHANNELS"
                                                                    resetIdleTimer()
                                                                }
                                                                .focusable()
                                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("See All  ›", color = FocusRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                            // Each visible row is composed lazily by the parent LazyColumn.
                                            groupChannels.sortedBy { it.status == ChannelStatus.OFFLINE }
                                                .chunked(columns).forEach { rowChannels ->
                                                    item {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                                                .focusGroup().graphicsLayer { clip = false },
                                                            horizontalArrangement = Arrangement.spacedBy(gap)
                                                        ) {
                                                            rowChannels.forEach { ch ->
                                                                ChannelCard(
                                                                    channel = ch,
                                                                    isSelected = ch.url == activeChannel?.url,
                                                                    channelNumber = channels.indexOfFirst { it.url == ch.url }.takeIf { it >= 0 }?.plus(1),
                                                                    modifier = Modifier.width(tileWidth),
                                                                    onSelect = {
                                                                        val idx = channels.indexOf(ch)
                                                                        if (idx >= 0) {
                                                                            currentChannelIndex.value = idx
                                                                            playChannel(idx)
                                                                            isUiVisible.value = false
                                                                            isSearchOpen.value = false
                                                                            isSettingsOpen.value = false
                                                                        }
                                                                    },
                                                                    onUserInteract = { resetIdleTimer() },
                                                                    canRequestFocus = { !listState.isScrollInProgress }
                                                                )
                                                            }
                                                            repeat(columns - rowChannels.size) { Spacer(Modifier.width(tileWidth)) }
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }



                    if (liveNewsTickerMode.value == "ALWAYS ON" && !isUiVisible.value &&
                        !isSearchOpen.value && !isSettingsOpen.value && tickerHeadlines.isNotEmpty()
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            NewsTickerBar(tickerHeadlines, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), onClick = { playNewsChannelFromTicker() })
                        }
                    }

                    if (isSearchOpen.value) {
                        SearchModal(
                            channels = channels,
                            favoriteUrls = favoriteUrls.toList(),
                            recentUrls = recentChannelUrls.toList(),
                            epgPrograms = epgPrograms.toList(),
                            voiceQuery = searchVoiceQuery.value,
                            onSelectChannel = { ch ->
                                val idx = channels.indexOf(ch)
                                if (idx >= 0) {
                                    currentChannelIndex.value = idx
                                    playChannel(idx)
                                    isUiVisible.value = false
                                    isSearchOpen.value = false
                                    isSettingsOpen.value = false
                                }
                            },
                            onClose = {
                                isSearchOpen.value = false
                                searchVoiceQuery.value = ""
                            }
                        )
                    }

                    if (isSettingsOpen.value) {
                        SettingsModal(
                            customM3uUrl = customM3uUrl.value,
                            isWatchdogEnabled = isWatchdogEnabled.value,
                            autoFailoverBackupLinks = autoFailoverBackupLinks.value,
                            hideUnavailableChannels = hideUnavailableChannels.value,
                            isPipEnabled = isPipEnabled.value,
                            selectedQuality = selectedQuality.value,
                            onSelectQuality = { quality ->
                                selectedQuality.value = quality
                                getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putString("stream_quality", quality).apply()
                                applyStreamQuality()
                            },
                            updateStatus = updateStatus.value,
                            isCheckingForUpdates = isCheckingForUpdates.value,
                            isUpdateDownloaded = isUpdateDownloaded.value,
                            onCheckForUpdates = { checkForUpdates() },
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
                            onToggleWatchdog = { isWatchdogEnabled.value = it; getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putBoolean("watchdog_enabled", it).apply() },
                            onToggleAutoFailover = { autoFailoverBackupLinks.value = it; getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putBoolean("auto_failover_backup_links", it).apply() },
                            onToggleHideUnavailable = { hideUnavailableChannels.value = it; getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putBoolean("hide_unavailable_channels", it).apply() },
                            onTogglePip = { isPipEnabled.value = it; getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putBoolean("pip_enabled", it).apply() },
                            onClearHistoryAndCache = {
                                recentChannelUrls.clear()
                                getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().remove("recent_channel_urls").apply()
                                File(cacheDir, "channel_logos").deleteRecursively()
                                Toast.makeText(applicationContext, "Watch history and logo cache cleared", Toast.LENGTH_SHORT).show()
                            },
                            tickerMode = liveNewsTickerMode.value,
                            onTickerModeChange = {
                                liveNewsTickerMode.value = it
                                getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit().putString("live_news_ticker_mode", it).apply()
                            },
                            remoteIsTv = isTvDevice,
                            remoteStatus = remoteServerStatus.value,
                            remoteAddress = remoteServerAddress.value,
                            remotePairCode = remotePairCode,
                            onOpenMobileRemote = { startActivity(Intent(this@MainActivity, RemoteActivity::class.java)) },
                            onClose = { isSettingsOpen.value = false }
                        )
                    }

                    updateNotice.value?.let { notice ->
                        UpdateNoticeDialog(
                            version = notice.version,
                            releaseNotes = notice.releaseNotes,
                            selectedAction = selectedUpdateAction.value,
                            onInstall = {
                                updateNotice.value = null
                                notice.apkFile?.let { launchUpdateInstaller(it) }
                            },
                            onDismiss = { updateNotice.value = null }
                        )
                    }

                    if (isBuffering.value && !isUiVisible.value && !isStartupVisible.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    playbackRetryMessage.value?.let { message ->
                        Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                            Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.82f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
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
                            RetroTvMark(Modifier.size(150.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
                        }
                    }
                }
            }
        }
    }

    private data class UpdateCheckResult(
        val latestTag: String,
        val apkFile: File?,
        val updateAvailable: Boolean,
        val releaseNotes: String
    )

    private data class UpdateNotice(val version: String, val releaseNotes: String, val apkFile: File?)

    private fun checkForUpdates() {
        downloadedUpdateApk?.delete()
        downloadedUpdateApk = null
        isUpdateDownloaded.value = false
        if (isCheckingForUpdates.value) return

        isCheckingForUpdates.value = true
        updateStatus.value = "Checking GitHub for the latest release…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val releaseConnection = (URL("https://api.github.com/repos/Kosimosi/Android-IPTV/releases/latest")
                        .openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 15000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "RETROTV-Android")
                    }
                    val releaseJson = try {
                        if (releaseConnection.responseCode !in 200..299) {
                            throw IOException("GitHub returned HTTP ${releaseConnection.responseCode}")
                        }
                        JSONObject(releaseConnection.inputStream.bufferedReader().use { it.readText() })
                    } finally {
                        releaseConnection.disconnect()
                    }

                    val latestTag = releaseJson.optString("tag_name").trim()
                    if (latestTag.isBlank()) throw IOException("The latest release has no version tag.")
                    val assets = releaseJson.optJSONArray("assets") ?: throw IOException("No APK was published in the latest release.")
                    val apkAsset = (0 until assets.length())
                        .mapNotNull { assets.optJSONObject(it) }
                        .firstOrNull {
                            it.optString("name").endsWith(".apk", ignoreCase = true) &&
                                it.optString("browser_download_url").startsWith("https://")
                        } ?: throw IOException("No downloadable APK was found in the latest release.")

                    val installedVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                    if (compareVersions(latestTag, installedVersion) <= 0) {
                        return@withContext UpdateCheckResult(latestTag, null, false, releaseJson.optString("body"))
                    }

                    val apkName = apkAsset.optString("name").replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val updateDir = File(cacheDir, "updates").apply { mkdirs() }
                    val safeTag = latestTag.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val apkFile = File(updateDir, "$safeTag-$apkName")
                    apkFile.delete()
                    val downloadConnection = (URL(apkAsset.optString("browser_download_url"))
                        .openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 60000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "RETROTV-Android")
                    }
                    try {
                        if (downloadConnection.responseCode !in 200..299) {
                            throw IOException("APK download failed (HTTP ${downloadConnection.responseCode}).")
                        }
                        downloadConnection.inputStream.use { input ->
                            apkFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    } finally {
                        downloadConnection.disconnect()
                    }
                    if (!apkFile.exists() || apkFile.length() == 0L) {
                        apkFile.delete()
                        throw IOException("The downloaded APK is empty.")
                    }
                    val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?: run {
                            apkFile.delete()
                            throw IOException("The downloaded file is not a valid Android APK.")
                        }
                    val archiveVersion = archiveInfo.versionName ?: "0"
                    if (archiveInfo.packageName != packageName) {
                        apkFile.delete()
                        throw IOException("The downloaded APK is for a different application.")
                    }
                    if (compareVersions(archiveVersion, installedVersion) <= 0 ||
                        compareVersions(latestTag, archiveVersion) != 0) {
                        apkFile.delete()
                        throw IOException("GitHub's APK version ($archiveVersion) does not match the newer release ($latestTag). Update blocked to prevent installing an older build.")
                    }
                    UpdateCheckResult(latestTag, apkFile, true, releaseJson.optString("body"))
                }

                if (!result.updateAvailable || result.apkFile == null) {
                    updateStatus.value = "You're using the latest version (${result.latestTag})."
                    Toast.makeText(this@MainActivity, "RETROTV is up to date", Toast.LENGTH_SHORT).show()
                } else {
                    downloadedUpdateApk = result.apkFile
                    isUpdateDownloaded.value = true
                    updateStatus.value = "Version ${result.latestTag} downloaded. Review the changes and choose Install update."
                    selectedUpdateAction.value = 0
                    updateNotice.value = UpdateNotice(result.latestTag.removePrefix("v"), result.releaseNotes, result.apkFile)
                    Toast.makeText(this@MainActivity, "RETROTV update ${result.latestTag} is ready", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                updateStatus.value = "Update check failed: ${error.message ?: "network error"}"
                Toast.makeText(this@MainActivity, "Could not check for updates", Toast.LENGTH_SHORT).show()
            } finally {
                isCheckingForUpdates.value = false
            }
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        val left = Regex("\\d+").findAll(first).mapNotNull { it.value.toIntOrNull() }.toList()
        val right = Regex("\\d+").findAll(second).mapNotNull { it.value.toIntOrNull() }.toList()
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun launchUpdateInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            downloadedUpdateApk = null
            isUpdateDownloaded.value = false
            updateStatus.value = "The downloaded update is missing. Check again."
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            updateStatus.value = "APK downloaded. Allow RETROTV to install apps in Android settings, then tap Install downloaded update."
            try {
                startActivity(
                    Intent(
                        AndroidSystemSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Enable permission to install unknown apps for RETROTV", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (error: Exception) {
            updateStatus.value = "Unable to open installer: ${error.message ?: "unknown error"}"
            Toast.makeText(this, "Could not open the Android installer", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyStreamQuality(showToast: Boolean = true) {
        val builder = player.trackSelectionParameters.buildUpon()
        when (selectedQuality.value) {
            "1080p" -> builder.setMaxVideoSize(1920, 1080)
            "720p" -> builder.setMaxVideoSize(1280, 720)
            "480p" -> builder.setMaxVideoSize(854, 480)
            else -> builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        player.trackSelectionParameters = builder.build()
        if (showToast) Toast.makeText(this, "Stream quality: ${selectedQuality.value}", Toast.LENGTH_SHORT).show()
    }

    private fun resetIdleTimer() {
        autoHideJob?.cancel()
        // Main channel browser stays open until BACK is pressed.
        autoHideJob = null
    }

    private fun loadSettingsFromStorage() {
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        val cachedHeadlines = runCatching {
            val array = org.json.JSONArray(prefs.getString("live_content_headlines", "[]") ?: "[]")
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        tickerHeadlines.clear()
        tickerHeadlines.addAll(cachedHeadlines)
        val savedFavs = prefs.getStringSet("fav_urls", emptySet()) ?: emptySet()
        favoriteUrls.clear()
        favoriteUrls.addAll(savedFavs)
        customM3uUrl.value = prefs.getString("custom_m3u_url", "") ?: ""
        selectedQuality.value = prefs.getString("stream_quality", "Auto") ?: "Auto"
        liveNewsTickerMode.value = prefs.getString("live_news_ticker_mode", "HOME ONLY") ?: "HOME ONLY"
        isWatchdogEnabled.value = prefs.getBoolean("watchdog_enabled", true)
        autoFailoverBackupLinks.value = prefs.getBoolean("auto_failover_backup_links", true)
        hideUnavailableChannels.value = prefs.getBoolean("hide_unavailable_channels", false)
        isPipEnabled.value = prefs.getBoolean("pip_enabled", false)
        recentChannelUrls.clear()
        recentChannelUrls.addAll(prefs.getString("recent_channel_urls", "")?.split("\\n")?.filter { it.isNotBlank() }?.take(8) ?: emptyList())
    }

    private suspend fun refreshLiveContent() {
        val channelSnapshot = channels.toList()
        val snapshot = runCatching {
            withContext(Dispatchers.IO) { LiveContentRepository.load(channelSnapshot) }
        }.getOrNull() ?: return
        if (snapshot.headlines.isNotEmpty()) {
            tickerHeadlines.clear()
            tickerHeadlines.addAll(snapshot.headlines)
            val cached = org.json.JSONArray()
            snapshot.headlines.forEach(cached::put)
            getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit()
                .putString("live_content_headlines", cached.toString()).apply()
        }
        liveContentCards.clear()
        liveContentCards.addAll(snapshot.cards)
    }

    private fun playNewsChannelFromTicker() {
        val aliases = listOf("Somoy", "Jamuna", "Channel 24", "Ekattor", "Independent", "DBC", "ATN News", "News 24")
        val sourceText = tickerHeadlines.joinToString(" ")
        val alias = aliases.firstOrNull { sourceText.contains(it, ignoreCase = true) }
        val channel = if (alias != null) {
            channels.firstOrNull { it.title.contains(alias, ignoreCase = true) && it.status != ChannelStatus.OFFLINE }
        } else null
        val target = channel ?: channels.firstOrNull {
            it.group.contains("news", ignoreCase = true) && it.status != ChannelStatus.OFFLINE
        } ?: return
        val index = channels.indexOfFirst { it.url == target.url }
        if (index >= 0) {
            currentChannelIndex.value = index
            playChannel(index)
            isUiVisible.value = false
            isSearchOpen.value = false
        }
    }

    private fun saveSettingsToStorage() {
        val prefs = getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_m3u_url", customM3uUrl.value).apply()
    }

    private fun recordRecentlyWatched(channel: Channel) {
        recentChannelUrls.remove(channel.url)
        recentChannelUrls.add(0, channel.url)
        while (recentChannelUrls.size > 8) recentChannelUrls.removeAt(recentChannelUrls.lastIndex)
        getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit()
            .putString("recent_channel_urls", recentChannelUrls.joinToString("\\n")).apply()
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
            val cachedText = runCatching {
                if (cacheFile.exists() && cacheFile.length() <= MAX_PLAYLIST_BYTES) cacheFile.readText() else ""
            }.getOrDefault("")
            val cachedList = parseM3u(cachedText).distinctBy { it.url }
            val initialList = cachedList.ifEmpty { fallbackChannels }

            withContext(Dispatchers.Main) {
                channels.clear()
                channels.addAll(initialList)
                playlistStatus.value = if (cachedList.isNotEmpty()) "Cached channels ready • refreshing playlists…" else "Loading channels • using local fallback while refreshing…"
                if (currentChannelIndex.value !in channels.indices) currentChannelIndex.value = 0
                if (channels.isNotEmpty()) playChannel(currentChannelIndex.value)
            }

            val primaryUrl = if (customM3uUrl.value.isNotBlank()) customM3uUrl.value else DEFAULT_PLAYLIST_URL
            val sources = listOf(primaryUrl).distinct()
            val playlistSemaphore = Semaphore(3)
            val fetched = sources.map { url ->
                async { url to playlistSemaphore.withPermit { fetchUrl(url) } }
            }.awaitAll()
            val successfulTexts = fetched.mapNotNull { it.second }
            var combinedChars = 0
            val boundedTexts = mutableListOf<String>()
            var rejectedOversizeSources = 0
            for (text in successfulTexts) {
                val extra = text.length + if (boundedTexts.isEmpty()) 0 else 1
                if (combinedChars + extra > MAX_PLAYLIST_CHARS) {
                    rejectedOversizeSources++
                    continue
                }
                boundedTexts.add(text)
                combinedChars += extra
            }
            val failedCount = fetched.count { it.second == null } + rejectedOversizeSources
            val combinedText = boundedTexts.joinToString("\n")
            val freshList = if (combinedText.isNotBlank()) parseM3u(combinedText).distinctBy { it.url } else emptyList()

            withContext(Dispatchers.Main) {
                if (freshList.isNotEmpty()) {
                    // Keep every successful source even if one provider is temporarily unavailable.
                    runCatching { cacheFile.writeText(combinedText) }
                    val currentUrl = channels.getOrNull(currentChannelIndex.value)?.url
                    channels.clear()
                    channels.addAll(freshList)
                    val sameIndex = channels.indexOfFirst { it.url == currentUrl }
                    currentChannelIndex.value = if (sameIndex >= 0) sameIndex else 0
                    if (sameIndex < 0 && channels.isNotEmpty()) playChannel(0)
                    broadcastRemoteState()
                    playlistStatus.value = if (failedCount == 0) {
                        "Playlists updated • ${channels.size} channels"
                    } else {
                        "Partial refresh • ${channels.size} channels • $failedCount source(s) unavailable"
                    }
                } else if (cachedList.isNotEmpty()) {
                    playlistStatus.value = if (failedCount > 0) {
                        "Refresh incomplete: $failedCount source(s) unavailable • keeping cached channels"
                    } else {
                        "No usable channels returned • keeping cached channels"
                    }
                } else {
                    playlistStatus.value = "Playlist refresh failed • showing local fallback channels"
                }
            }
            checkPlaylistHealth()
        }

        // EPG downloads independently, so a slow guide cannot delay channel playback.
        lifecycleScope.launch(Dispatchers.IO) {
            val xml = fetchUrl(IPTV_INDIA_EPG_URL)
            val parsed = if (!xml.isNullOrBlank()) runCatching { parseXmlTv(xml) }.getOrDefault(emptyList()) else emptyList()
            withContext(Dispatchers.Main) {
                epgPrograms.clear()
                epgPrograms.addAll(parsed)
            }
        }
    }

    private fun checkPlaylistHealth() {
        // This callback can be reached after playlist work running on Dispatchers.IO.
        // Keep health scheduling state and all ExoPlayer reads on the player application thread.
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            healthCheckPending = true
            scheduleBackgroundNetworkWorkAfterReady()
        }
    }

    /** Defer all non-playback network work until the current stream has been stable for 3 seconds. */
    private fun scheduleBackgroundNetworkWorkAfterReady() {
        // ExoPlayer is created on main; even playbackState reads must stay on that thread.
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            if (!isHostForeground || player.playbackState != Player.STATE_READY) return@launch
            backgroundNetworkResumeJob?.cancel()
            backgroundNetworkResumeJob = lifecycleScope.launch {
                delay(3_000L)
                if (!isHostForeground || player.playbackState != Player.STATE_READY) return@launch
                if (healthCheckPending && healthCheckJob?.isActive != true) runPlaylistHealthCheck()
                if (liveContentRefreshPending && liveContentJob?.isActive != true) startLiveContentPolling()
            }
        }
    }

    private fun runPlaylistHealthCheck() {
        healthCheckPending = false
        healthCheckJob?.cancel()
        healthCheckJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sample = withContext(Dispatchers.Main) {
                    val allChannels = channels.toList()
                    val priorityNames = listOf(
                        "Somoy", "Jamuna", "Channel 24", "Ekattor", "Independent", "DBC", "ATN News",
                        "T Sports", "GTV", "Gazi TV", "Nagorik TV", "Star Sports", "Sony Sports Ten",
                        "BTV", "Channel i"
                    )
                    (allChannels.take(48) + allChannels.filter { channel ->
                        priorityNames.any { channel.title.contains(it, ignoreCase = true) }
                    }).distinctBy { it.url }.take(128)
                }
                val healthSemaphore = Semaphore(2)
                val results = sample.map { channel ->
                    async {
                        val online = healthSemaphore.withPermit {
                            (listOf(channel.url) + channel.backupUrls).take(2).any { testStream(it) }
                        }
                        channel.url to if (online) ChannelStatus.ONLINE else ChannelStatus.OFFLINE
                    }
                }.awaitAll().toMap()
                withContext(Dispatchers.Main) {
                    channels.indices.forEach { index ->
                        results[channels[index].url]?.let { status ->
                            channels[index] = channels[index].copy(status = status)
                        }
                    }
                    val offlineCount = results.values.count { it == ChannelStatus.OFFLINE }
                    playlistStatus.value += " • health checked (${results.size - offlineCount} online, $offlineCount unavailable)"
                }
            } finally {
                healthCheckJob = null
            }
        }
    }

    private fun startLiveContentPolling() {
        if (!isHostForeground || liveContentJob?.isActive == true) return
        liveContentRefreshPending = false
        liveContentJob = lifecycleScope.launch {
            refreshLiveContent()
            while (isHostForeground) {
                delay(150_000L)
                if (isHostForeground) refreshLiveContent()
            }
        }
    }

    /** Stop health/RSS requests as soon as a zap begins; they resume only after STATE_READY + 3s. */
    private fun pauseBackgroundNetworkForPlaybackStart() {
        backgroundNetworkResumeJob?.cancel()
        backgroundNetworkResumeJob = null
        if (healthCheckJob?.isActive == true) healthCheckPending = true
        healthCheckJob?.cancel()
        healthCheckJob = null
        if (liveContentJob?.isActive == true) liveContentRefreshPending = true
        liveContentJob?.cancel()
        liveContentJob = null
    }

    private fun testStream(streamUrl: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("User-Agent", "RETROTV-Android")
                instanceFollowRedirects = true
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseXmlTv(xml: String): List<Programme> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val programmes = mutableListOf<Programme>()
        var event = parser.eventType
        var channelId = ""
        var title = ""
        var start = 0L
        var stop = 0L
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channelId = parser.getAttributeValue(null, "channel").orEmpty()
                        start = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                        stop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                        title = ""
                    }
                    "title" -> if (channelId.isNotBlank()) title = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && channelId.isNotBlank() && title.isNotBlank() && start > 0L && stop > start) {
                    programmes.add(Programme(channelId, title, start, stop))
                    channelId = ""
                }
            }
            event = parser.next()
        }
        return programmes
    }

    private fun parseXmlTvTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US)
                formatter.isLenient = false
                if (!pattern.contains("Z")) formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return formatter.parse(value.trim())?.time ?: 0L
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun fetchUrl(urlString: String): String? {
        // Bound remote responses and total read time; older Android TV devices have a small heap.
        val maxBytes = if (urlString == IPTV_INDIA_EPG_URL) 8L * 1024 * 1024 else 12L * 1024 * 1024
        var connection: HttpURLConnection? = null
        val deadlineMs = SystemClock.elapsedRealtime() + 30_000L
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) return null
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) return null

            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        if (SystemClock.elapsedRealtime() > deadlineMs) return null
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) return null
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private val attributeRegex = Regex("([A-Za-z0-9_\\-]+)=\"([^\"]*)\"")

    private fun parseM3u(content: String): List<Channel> {
        if (content.length > MAX_PLAYLIST_CHARS) return emptyList()
        val list = mutableListOf<Channel>()
        var currentTitle = ""
        var currentGroup = "General"
        var currentLogo: String? = null
        var currentRegion = "Unknown Region"
        var currentLanguage = "Unknown"
        var currentCountry = "Unknown"
        var currentEpgId: String? = null
        // Alternative links for the current entry: x-backup -> 1, x-backup2 -> 2, ...
        var currentBackups: List<Pair<Int, String>> = emptyList()
        // x-link-labels, index 0 = primary URL, index N = x-backup N.
        var currentLabels: List<String> = emptyList()
        var lineCount = 0
        for (line in content.lineSequence()) {
            if (++lineCount > MAX_PLAYLIST_LINES || list.size >= MAX_PLAYLIST_CHANNELS) break
            if (line.length > MAX_PLAYLIST_LINE_CHARS) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                currentTitle = trimmed.substringAfterLast(",").trim()
                val groupMatch = """group-title="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "General"

                val logoMatch = """tvg-logo="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1)?.trim()
                val epgMatch = """tvg-id="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentEpgId = epgMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                // Attributes live before the final comma; the channel name follows it.
                val attributes = attributeRegex.findAll(trimmed.substringBeforeLast(","))
                    .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
                currentBackups = attributes.mapNotNull { (key, value) ->
                    val order = when {
                        key == "x-backup" -> 1
                        key.startsWith("x-backup") -> key.removePrefix("x-backup").toIntOrNull()
                        else -> null
                    } ?: return@mapNotNull null
                    val url = value.trim()
                    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return@mapNotNull null
                    order to url
                }.sortedBy { it.first }
                currentLabels = attributes["x-link-labels"]?.split(',')?.map { it.trim() }.orEmpty()
                val countryMatch = """tvg-country="([^"]*)"""" .trim().toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentCountry = countryMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
                val languageMatch = """tvg-language="([^"]*)"""" .trim().toRegex(RegexOption.IGNORE_CASE).find(trimmed)
                currentLanguage = languageMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
                val lowerMeta = (currentLanguage + " " + currentCountry + " " + currentTitle + " " + currentGroup).lowercase(Locale.ROOT)
                currentLanguage = when {
                    lowerMeta.contains("urdu") || currentCountry.equals("Pakistan", true) || currentCountry.equals("PK", true) -> "Urdu"
                    lowerMeta.contains("hindi") || currentLanguage.split(',', ';', '/', ' ').any { it.equals("hin", true) } -> "Hindi"
                    lowerMeta.contains("bangla") || lowerMeta.contains("bengali") ||
                        currentLanguage.split(',', ';', '/', ' ').any { it.equals("ben", true) } ||
                        currentCountry.equals("Bangladesh", true) || currentCountry.equals("BD", true) ||
                        listOf("jamuna", "dbc", "ekattor", "ntv", "deepto", "maasranga", "somoy", "channel i", "gazi tv", "banglavision").any { currentTitle.contains(it, true) } -> "Bangla"
                    else -> "Unknown"
                }
                currentRegion = if (currentLanguage == "Urdu") "Urdu / Pakistan" else currentLanguage
                if (currentCountry == "Unknown") currentCountry = when (currentLanguage) {
                    "Hindi" -> "India"
                    "Urdu" -> "Pakistan"
                    "Bangla" -> "Bangladesh"
                    else -> "Unknown"
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                if (currentTitle.isNotEmpty()) {
                    list.add(
                        Channel(
                            title = currentTitle,
                            url = trimmed,
                            group = currentGroup,
                            logo = currentLogo,
                            region = currentRegion,
                            epgId = currentEpgId,
                            language = currentLanguage,
                            country = currentCountry,
                            backupUrls = currentBackups.map { it.second },
                            linkLabels = listOf(currentLabels.getOrNull(0).orEmpty()) +
                                currentBackups.map { currentLabels.getOrNull(it.first).orEmpty() }
                        )
                    )
                    currentBackups = emptyList()
                    currentLabels = emptyList()
                    currentTitle = ""
                    currentGroup = "General"
                    currentLogo = null
                    currentRegion = "Unknown Region"
                    currentLanguage = "Unknown"
                    currentCountry = "Unknown"
                    currentEpgId = null
                }
            }
        }
        // Entries sharing a channel name are alternative links of one channel. Keep the
        // first URL as primary, then every other URL in order, dropping duplicates.
        return list.groupBy { it.title.trim().lowercase(Locale.ROOT) }.values.map { variants ->
            val primary = variants.first()
            val seen = HashSet<String>()
            val urls = mutableListOf<String>()
            val labels = mutableListOf<String>()
            for (variant in variants) {
                variant.allUrls.forEachIndexed { index, link ->
                    if (seen.add(link)) {
                        urls.add(link)
                        labels.add(variant.linkLabels.getOrNull(index).orEmpty())
                    }
                }
            }
            primary.copy(backupUrls = urls.drop(1), linkLabels = labels)
        }
    }

    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        pauseBackgroundNetworkForPlaybackStart()
        isBuffering.value = true
        currentResolution.value = null
        isChannelInfoVisible.value = true
        recordRecentlyWatched(channel)
        retryJob?.cancel()
        retryAttempt = 0
        triedStreamIndices.clear()
        isLinkPickerVisible.value = false
        playbackRetryMessage.value = null
        channelInfoJob?.cancel()
        // Keep the channel banner visible for five seconds after the menu fades away.
        val infoDuration = if (isUiVisible.value) 10_000L else 5_000L
        channelInfoJob = lifecycleScope.launch {
            delay(infoDuration)
            isChannelInfoVisible.value = false
        }

        // Always start a channel on its primary link.
        loadLink(channel, 0)
        broadcastRemoteState()
    }

    // Loads one of the channel's links into the player. Does not cancel retry jobs; callers own that.
    private fun loadLink(channel: Channel, linkIndex: Int) {
        val url = channel.allUrls.getOrNull(linkIndex) ?: return
        activeStreamIndex.value = linkIndex
        triedStreamIndices.add(linkIndex)
        currentStreamUrl = url
        retryAttempt = 0
        playerController.setStream(url)
        player.prepare()
        // Selecting a channel or link is an explicit user action; start playback after a foreground pause.
        playerController.play()
        armWatchdog()
    }

    private fun armWatchdog() {
        watchdogJob?.cancel()
        if (!isWatchdogEnabled.value) return
        watchdogJob = lifecycleScope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isBuffering.value) tryNextBackupOrAdvanceChannel("Stream timed out")
        }
    }

    // Failover: try the next untried link of the current channel; when every link has failed,
    // mark the channel offline and move on to the next channel.
    private fun tryNextBackupOrAdvanceChannel(reasonPrefix: String) {
        val idx = currentChannelIndex.value
        val channel = channels.getOrNull(idx) ?: return
        val total = channel.allUrls.size
        val current = activeStreamIndex.value.coerceIn(0, total - 1)
        triedStreamIndices.add(current)
        val nextIndex = if (autoFailoverBackupLinks.value) (1 until total).asSequence()
            .map { (current + it) % total }
            .firstOrNull { it !in triedStreamIndices } else null

        retryJob?.cancel()
        if (nextIndex != null) {
            playbackRetryMessage.value = "$reasonPrefix — trying ${channel.labelForStream(nextIndex)} (${triedStreamIndices.size + 1}/$total)…"
            retryJob = lifecycleScope.launch {
                delay(350)
                if (currentChannelIndex.value == idx) loadLink(channel, nextIndex)
            }
        } else {
            channels[idx] = channel.copy(status = ChannelStatus.OFFLINE)
            playbackRetryMessage.value = "All links unavailable — switching to next channel…"
            retryJob = lifecycleScope.launch {
                delay(1200)
                if (currentChannelIndex.value == idx) switchChannel(1)
            }
        }
    }

    // Manual link choice from the playback toolbar or picker.
    private fun selectChannelStream(index: Int, showToast: Boolean = false) {
        val channel = channels.getOrNull(currentChannelIndex.value) ?: return
        if (index !in channel.allUrls.indices) return
        retryJob?.cancel()
        playbackRetryMessage.value = null
        isBuffering.value = true
        loadLink(channel, index)
        if (showToast) {
            Toast.makeText(this, "Playing ${channel.labelForStream(index)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOrCycleChannelLink() {
        val channel = channels.getOrNull(currentChannelIndex.value)
        if (channel == null || channel.allUrls.size <= 1) {
            Toast.makeText(this, "No alternative links for this channel", Toast.LENGTH_SHORT).show()
            return
        }
        val opening = !isLinkPickerVisible.value
        isLinkPickerVisible.value = opening
        if (opening) {
            linkPickerIndex.value = activeStreamIndex.value.coerceIn(0, channel.allUrls.lastIndex)
            isQualityPickerVisible.value = false
            isEpgOverlayVisible.value = false
        }
    }

    private fun cycleVideoScaling() {
        scalingModeIndex.value = (scalingModeIndex.value + 1) % scalingModes.size
        playbackPlayerView?.resizeMode = when (scalingModes[scalingModeIndex.value]) {
            "Fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "Zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun openChannelMenuFromPlayback() {
        isPlaybackOverlayVisible.value = false
        isUiVisible.value = true
        resetIdleTimer()
    }

    private fun openChannelNumberPad() {
        channelNumberJob?.cancel()
        typedChannelNumber.value = ""
        channelNumberOriginalIndex.intValue = currentChannelIndex.value
        channelNumberPreviewIndex.intValue = -1
        channelNumberPadFocus.intValue = 0
        isQualityPickerVisible.value = false
        isLinkPickerVisible.value = false
        isEpgOverlayVisible.value = false
        isChannelNumberPadVisible.value = true
    }

    private fun appendChannelNumberDigit(digit: Int) {
        if (digit !in 0..9 || channels.isEmpty()) return
        val maxDigits = channels.size.toString().length.coerceAtLeast(1)
        if (typedChannelNumber.value.length >= maxDigits) return
        typedChannelNumber.value += digit.toString()
        channelNumberJob?.cancel()
        channelNumberJob = lifecycleScope.launch {
            delay(900L)
            if (!isChannelNumberPadVisible.value) return@launch
            val requestedNumber = typedChannelNumber.value.toIntOrNull() ?: return@launch
            val targetIndex = requestedNumber - 1
            val hasLongerMatch = !typedChannelNumber.value.startsWith("0") &&
                channels.indices.any { index ->
                    val candidate = (index + 1).toString()
                    candidate.length > typedChannelNumber.value.length && candidate.startsWith(typedChannelNumber.value)
                }
            // Do not preview "1" while it could still become 10, 15, etc.
            // The user can always press OK to explicitly choose the exact number.
            if (targetIndex in channels.indices && !hasLongerMatch) {
                channelNumberPreviewIndex.intValue = targetIndex
                currentChannelIndex.value = targetIndex
                playChannel(targetIndex)
            }
        }
    }

    private fun clearChannelNumberEntry() {
        channelNumberJob?.cancel()
        typedChannelNumber.value = ""
        channelNumberPreviewIndex.intValue = -1
        val original = channelNumberOriginalIndex.intValue
        if (currentChannelIndex.value != original) {
            currentChannelIndex.value = original
            playChannel(original)
        }
    }

    private fun confirmChannelNumberEntry() {
        channelNumberJob?.cancel()
        val requestedNumber = typedChannelNumber.value.toIntOrNull()
        val targetIndex = (requestedNumber ?: 0) - 1
        if (targetIndex in channels.indices) {
            if (currentChannelIndex.value != targetIndex) {
                currentChannelIndex.value = targetIndex
                playChannel(targetIndex)
            }
            isChannelNumberPadVisible.value = false
            typedChannelNumber.value = ""
            channelNumberPreviewIndex.intValue = -1
        } else {
            Toast.makeText(this, if (typedChannelNumber.value.isBlank()) "Enter a channel number" else "Channel $requestedNumber not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelChannelNumberEntry() {
        channelNumberJob?.cancel()
        val original = channelNumberOriginalIndex.intValue
        if (channelNumberPreviewIndex.intValue >= 0 && original in channels.indices && currentChannelIndex.value != original) {
            currentChannelIndex.value = original
            playChannel(original)
        }
        isChannelNumberPadVisible.value = false
        typedChannelNumber.value = ""
        channelNumberPreviewIndex.intValue = -1
    }

    private fun activatePlaybackAction(index: Int) {
        when (index) {
            ACTION_FAVOURITE -> channels.getOrNull(currentChannelIndex.value)?.let { toggleFavorite(it.url) }
            ACTION_SEARCH -> isSearchOpen.value = true
            ACTION_QUALITY -> {
                qualityPickerIndex.value = qualityOptions.indexOf(selectedQuality.value).coerceAtLeast(0)
                isQualityPickerVisible.value = !isQualityPickerVisible.value
                isLinkPickerVisible.value = false
                isEpgOverlayVisible.value = false
            }
            ACTION_CHANGE_LINK -> openOrCycleChannelLink()
            ACTION_EPG -> {
                isEpgOverlayVisible.value = !isEpgOverlayVisible.value
                isQualityPickerVisible.value = false
                isLinkPickerVisible.value = false
            }
            ACTION_SETTINGS -> isSettingsOpen.value = true
            ACTION_SCALING -> cycleVideoScaling()
            ACTION_LOCK -> playbackControlsLocked.value = !playbackControlsLocked.value
            ACTION_MENU -> openChannelMenuFromPlayback()
            ACTION_NUMBER_PAD -> openChannelNumberPad()
        }
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        val newIndex = (currentChannelIndex.value + delta + channels.size) % channels.size
        currentChannelIndex.value = newIndex
        playChannel(newIndex)
    }

    // Intercept playback confirmation before Compose clickable/focus handlers.
    // This makes the Activity the sole owner of OK/Select while the video controls are open.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isConfirmKey = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT

        // The search drawer owns OK/Select while it is open. Do not let the
        // playback toolbar's Activity-level handler steal channel selection.
        if (isSearchOpen.value && isConfirmKey) {
            return super.dispatchKeyEvent(event)
        }

        // The update dialog has its own D-pad selection state. Intercept OK here
        // so a focused Compose clickable cannot require a long press or double-activate.
        if (updateNotice.value != null && isConfirmKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        val notice = updateNotice.value
                        if (selectedUpdateAction.value == 0) {
                            updateNotice.value = null
                            notice?.apkFile?.let { launchUpdateInstaller(it) }
                        } else {
                            updateNotice.value = null
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> return true
            }
        }

        if (!isUiVisible.value && updateNotice.value == null && isConfirmKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // Ignore long-press repeats, but consume them so Compose cannot
                    // interpret the held centre button as a second click.
                    if (event.repeatCount == 0) {
                        if (playbackControlsLocked.value) {
                            playbackControlsLocked.value = false
                            isPlaybackOverlayVisible.value = true
                            selectedPlaybackAction.value = ACTION_LOCK
                        } else if (!isPlaybackOverlayVisible.value) {
                            isPlaybackOverlayVisible.value = true
                            selectedPlaybackAction.value = ACTION_FAVOURITE
                        } else if (isLinkPickerVisible.value) {
                            selectChannelStream(linkPickerIndex.value, showToast = true)
                            isLinkPickerVisible.value = false
                        } else if (isQualityPickerVisible.value) {
                            selectedQuality.value = qualityOptions[qualityPickerIndex.value]
                            getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit()
                                .putString("stream_quality", selectedQuality.value).apply()
                            applyStreamQuality()
                            isQualityPickerVisible.value = false
                        } else if (isChannelNumberPadVisible.value) {
                            val focus = channelNumberPadFocus.intValue
                            when (focus) {
                                9 -> clearChannelNumberEntry()
                                11 -> confirmChannelNumberEntry()
                                10 -> appendChannelNumberDigit(0)
                                else -> appendChannelNumberDigit(focus + 1)
                            }
                        } else if (isEpgOverlayVisible.value) {
                            isEpgOverlayVisible.value = false
                        } else {
                            activatePlaybackAction(selectedPlaybackAction.value)
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Menu hidden: Left/Right zap channels, Up/Down/OK/Menu open the menu.
    // Menu visible: all D-pad keys go to the menu so you can move around in it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // OK/Enter is an activation, not a repeatable navigation key. Some TV remotes
        // emit repeated ACTION_DOWN events while the centre button is held; ignore them
        // to prevent repeated toggles and flickering overlays.
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && (event?.repeatCount ?: 0) > 0
        ) return true

        // Let Android handle hardware volume keys normally.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event)
        }
        // Route remote input to the custom update prompt rather than touch-only actions.
        if (updateNotice.value != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> updateNotice.value = null
                KeyEvent.KEYCODE_DPAD_LEFT -> selectedUpdateAction.value = 0
                KeyEvent.KEYCODE_DPAD_RIGHT -> selectedUpdateAction.value = 1
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val notice = updateNotice.value
                    if (selectedUpdateAction.value == 0) {
                        updateNotice.value = null
                        notice?.apkFile?.let { launchUpdateInstaller(it) }
                    } else {
                        updateNotice.value = null
                    }
                }
            }
            return true
        }

        // Physical digit keys use the same preview/confirm flow as the on-screen keypad.
        if (!isUiVisible.value && !isSearchOpen.value && !isSettingsOpen.value) {
            val digit = when {
                keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
                keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
                else -> -1
            }
            if (digit >= 0) {
                if ((event?.repeatCount ?: 0) > 0) return true
                if (!isChannelNumberPadVisible.value) openChannelNumberPad()
                appendChannelNumberDigit(digit)
                return true
            }
        }

        if (isChannelNumberPadVisible.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> cancelChannelNumberEntry()
                KeyEvent.KEYCODE_DPAD_LEFT -> channelNumberPadFocus.intValue = if (channelNumberPadFocus.intValue % 3 == 0) channelNumberPadFocus.intValue else channelNumberPadFocus.intValue - 1
                KeyEvent.KEYCODE_DPAD_RIGHT -> channelNumberPadFocus.intValue = if (channelNumberPadFocus.intValue % 3 == 2) channelNumberPadFocus.intValue else channelNumberPadFocus.intValue + 1
                KeyEvent.KEYCODE_DPAD_UP -> channelNumberPadFocus.intValue = (channelNumberPadFocus.intValue - 3).coerceAtLeast(0)
                KeyEvent.KEYCODE_DPAD_DOWN -> channelNumberPadFocus.intValue = (channelNumberPadFocus.intValue + 3).coerceAtMost(11)
                else -> return true
            }
            return true
        }

        // Modal overlays take Back priority even when the main browser is hidden
        // (for example, Search opened from the playback toolbar).
        // Search is modal: route D-pad input to Compose, never to playback controls.
        if (isSearchOpen.value && keyCode in setOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER
            )
        ) return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_BACK && isSearchOpen.value) {
            isSearchOpen.value = false
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && isSettingsOpen.value) {
            isSettingsOpen.value = false
            return true
        }

        if (!isUiVisible.value) {
            if (playbackControlsLocked.value) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    playbackControlsLocked.value = false
                    isPlaybackOverlayVisible.value = true
                    selectedPlaybackAction.value = ACTION_LOCK
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                when {
                    isLinkPickerVisible.value -> isLinkPickerVisible.value = false
                    isQualityPickerVisible.value -> isQualityPickerVisible.value = false
                    isEpgOverlayVisible.value -> isEpgOverlayVisible.value = false
                    isPlaybackOverlayVisible.value -> isPlaybackOverlayVisible.value = false
                    else -> isCrtExiting.value = true
                }
                return true
            }

            // Playing video: Left/Right change channels. Down opens the playback
            // toolbar. Up always opens the channel browser/menu.
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_MENU) {
                openChannelMenuFromPlayback()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                val linkCount = channels.getOrNull(currentChannelIndex.value)?.allUrls?.size ?: 1
                when {
                    isLinkPickerVisible.value ->
                        linkPickerIndex.value = (linkPickerIndex.value + delta + linkCount) % linkCount
                    isQualityPickerVisible.value ->
                        qualityPickerIndex.value = (qualityPickerIndex.value + delta + qualityOptions.size) % qualityOptions.size
                    isEpgOverlayVisible.value -> Unit // Guide navigation must never zap the playing channel.
                    isPlaybackOverlayVisible.value ->
                        selectedPlaybackAction.value = (selectedPlaybackAction.value + delta + PLAYBACK_ACTION_COUNT) % PLAYBACK_ACTION_COUNT
                    else -> switchChannel(delta)
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!isPlaybackOverlayVisible.value) {
                    isPlaybackOverlayVisible.value = true
                    selectedPlaybackAction.value = ACTION_FAVOURITE
                } else if (!isQualityPickerVisible.value && !isLinkPickerVisible.value && !isEpgOverlayVisible.value) {
                    selectedPlaybackAction.value = ACTION_MENU
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!isPlaybackOverlayVisible.value) {
                    isPlaybackOverlayVisible.value = true
                } else if (isLinkPickerVisible.value) {
                    selectChannelStream(linkPickerIndex.value, showToast = true)
                    isLinkPickerVisible.value = false
                } else if (isQualityPickerVisible.value) {
                    selectedQuality.value = qualityOptions[qualityPickerIndex.value]
                    getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit()
                        .putString("stream_quality", selectedQuality.value).apply()
                    applyStreamQuality()
                    isQualityPickerVisible.value = false
                } else if (isEpgOverlayVisible.value) {
                    isEpgOverlayVisible.value = false
                } else {
                    activatePlaybackAction(selectedPlaybackAction.value)
                }
                return true
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                when {
                    isSearchOpen.value -> {
                        isSearchOpen.value = false
                        searchVoiceQuery.value = ""
                    }
                    isSettingsOpen.value -> isSettingsOpen.value = false
                    else -> isUiVisible.value = false
                }
                return true
            }
            resetIdleTimer()
        }

        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Android API; retained for broad TV device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE_SEARCH && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) searchVoiceQuery.value = spoken
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipEnabled.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isUiVisible.value &&
            !isSearchOpen.value && !isSettingsOpen.value && packageManager.hasSystemFeature("android.software.picture_in_picture")
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    private fun startTvRemoteServer() {
        if (!isTvDevice) return
        RemoteCommandBridge.attach(::handleMobileRemoteCommand, ::buildRemoteStateJson)
        val ip = findLocalIpv4Address()
        remoteServerAddress.value = if (ip != null) "$ip:${TvRemoteServer.DEFAULT_PORT}" else "IP unavailable • port ${TvRemoteServer.DEFAULT_PORT}"
        remoteServerStatus.value = getSharedPreferences(TvRemoteBackgroundService.PREFS, MODE_PRIVATE)
            .getString(TvRemoteBackgroundService.KEY_STATUS, "Starting") ?: "Starting"
        val serviceIntent = Intent(this, TvRemoteBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
    }

    private fun findLocalIpv4Address(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { network -> runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) }
            .sortedBy { network ->
                when {
                    network.name.startsWith("wlan", true) -> 0
                    network.name.startsWith("eth", true) -> 1
                    else -> 2
                }
            }
            .flatMap { network -> Collections.list(network.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    /** Called only on the main thread; stream URLs are deliberately never sent to the phone. */
    private fun buildRemoteStateJson(): String {
        val channelArray = org.json.JSONArray()
        channels.take(2_000).forEachIndexed { index, channel ->
            channelArray.put(JSONObject().apply {
                put("number", index + 1)
                put("title", channel.title)
                put("group", channel.group)
                put("logo", channel.logo ?: "")
                put("status", channel.status.name)
            })
        }
        return JSONObject().apply {
            put("type", RemoteProtocol.EVT_STATE)
            put("currentChannel", currentChannelIndex.value)
            put("resolution", currentResolution.value ?: "")
            put("playing", player.isPlaying)
            put("channelCount", channels.size)
            put("channels", channelArray)
        }.toString()
    }

    private fun broadcastRemoteState() {
        if (isTvDevice) TvRemoteBackgroundService.broadcastState(buildRemoteStateJson())
    }

    private fun handleMobileRemoteCommand(command: String, value: String) {
        when (command) {
            RemoteProtocol.CMD_DPAD -> {
                if (value == "HOME") {
                    moveTaskToBack(true)
                    return
                }
                val keyCode = when (value) {
                    "UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "OK" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "BACK" -> KeyEvent.KEYCODE_BACK
                    else -> return
                }
                val now = SystemClock.uptimeMillis()
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            }
            RemoteProtocol.CMD_NUMBER -> {
                val digit = value.toIntOrNull() ?: return
                if (!isChannelNumberPadVisible.value) openChannelNumberPad()
                appendChannelNumberDigit(digit)
            }
            RemoteProtocol.CMD_CHANNEL_ID -> {
                val index = (value.toIntOrNull() ?: return) - 1
                if (index !in channels.indices) return
                currentChannelIndex.value = index
                playChannel(index)
                isUiVisible.value = false
                isSearchOpen.value = false
                isSettingsOpen.value = false
                isChannelNumberPadVisible.value = false
            }
            RemoteProtocol.CMD_SEARCH -> {
                searchVoiceQuery.value = value
                isSearchOpen.value = true
                isUiVisible.value = false
            }
            RemoteProtocol.CMD_MEDIA -> when (value) {
                "VOL_UP" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                "VOL_DOWN" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                "MUTE" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                "TICKER_TOGGLE" -> {
                    liveNewsTickerMode.value = if (liveNewsTickerMode.value == "OFF") "HOME ONLY" else "OFF"
                    getSharedPreferences("retro_iptv_prefs", Context.MODE_PRIVATE).edit()
                        .putString("live_news_ticker_mode", liveNewsTickerMode.value).apply()
                }
            }
            RemoteProtocol.CMD_SYNC_REQ -> broadcastRemoteState()
        }
    }

    override fun onStart() {
        super.onStart()
        isHostForeground = true
        liveContentRefreshPending = true
        if (isTvDevice) startTvRemoteServer()
        if (player.playbackState == Player.STATE_READY) scheduleBackgroundNetworkWorkAfterReady()
    }

    override fun onStop() {
        RemoteCommandBridge.detach()
        // Home, app switching, and screen-off must never leave a live stream playing.
        isHostForeground = false
        backgroundNetworkResumeJob?.cancel()
        backgroundNetworkResumeJob = null
        if (healthCheckJob?.isActive == true) healthCheckPending = true
        healthCheckJob?.cancel()
        healthCheckJob = null
        if (liveContentJob?.isActive == true) liveContentRefreshPending = true
        liveContentJob?.cancel()
        liveContentJob = null
        retryJob?.cancel()
        watchdogJob?.cancel()
        playerController.pauseForBackground()
        super.onStop()
    }

    override fun onDestroy() {
        RemoteCommandBridge.detach()
        super.onDestroy()
        autoHideJob?.cancel()
        watchdogJob?.cancel()
        channelInfoJob?.cancel()
        retryJob?.cancel()
        liveContentJob?.cancel()
        healthCheckJob?.cancel()
        backgroundNetworkResumeJob?.cancel()
        playerController.release()
    }
}