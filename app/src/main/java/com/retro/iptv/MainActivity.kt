@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.retro.iptv

import android.content.Context
import android.content.Intent
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
    modifier: Modifier = Modifier.width(88.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    var confirmPressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val fill by animateColorAsState(
        targetValue = when {
            isFocused -> ChannelCardFocusedFill
            isSelected -> OakSurfaceElevated
            else -> OakSurface
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "channelCardFill"
    )
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "channelCardFocusScale"
    )
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                translationZ = if (isFocused) 12.dp.toPx() else 0f
                shadowElevation = if (isFocused) 12.dp.toPx() else 0f
                clip = false
            }
            .clip(shape)
            .background(fill)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusRed else ChannelCardIdleStroke,
                shape = shape
            )
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
        Modifier.widthIn(min = 260.dp).width(260.dp).clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xE51A1115) else Color(0xD914161D))
            .border(1.dp, if (focused) FocusRed.copy(alpha = 0.85f) else Color(0x55E50914), RoundedCornerShape(10.dp))
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
                Text(channel.title, color = WarmAshMuted, fontSize = 9.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 11f
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    isSelected = true
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((8 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt(), 0)
                    setHorizontalFadingEdgeEnabled(true)
                    setFadingEdgeLength((20 * resources.displayMetrics.density).toInt())
                }
            },
            update = { view ->
                if (view.text.toString() != tickerText) view.text = tickerText
                view.isSelected = !focused
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
    val isTv = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    LaunchedEffect(Unit) {
        delay(if (isTv) 160L else 80L)
        runCatching { searchFocusRequester.requestFocus() }
    }
    LaunchedEffect(voiceQuery) {
        if (voiceQuery.isNotBlank()) query = voiceQuery
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
                .padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
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
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(visibleChannels, key = { _, channel -> channel.url }) { _, channel ->
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
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .background(if (focused) ChannelCardFocusedFill else Color(0xFF15171C))
                                .border(1.dp, if (focused) FocusRed else ChannelCardIdleStroke, RoundedCornerShape(11.dp))
                                .onFocusChanged { focused = it.isFocused }
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
                    isSearchOpen.value -> isSearchOpen.value = false
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
                                    modifier = Modifier.fillMaxSize(),
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
                                                            onUserInteract = { resetIdleTimer() })
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
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).focusGroup(),
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
                                                                    onUserInteract = { resetIdleTimer() }
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
                            onClose = { isSearchOpen.value = false }
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