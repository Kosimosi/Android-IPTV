package com.retro.iptv

import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Color Tokens
val OakDarkBackground = Color(0xFF140F0C)
val OakSurface = Color(0xFF261A13)
val OakSurfaceElevated = Color(0xFF38271D)
val AmberDial = Color(0xFFE07A38)
val ParchmentWhite = Color(0xFFFAF4ED)
val WarmAshMuted = Color(0xFFA8988B)
val SidebarOakGlass = Color(0xD2140F0C)

data class Channel(val title: String, val url: String)

object M3uParser {
    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var title: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) ->
                    title = line.substringAfterLast(',', "").trim().ifEmpty { null }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    if (line.startsWith("http", ignoreCase = true)) {
                        out += Channel(title ?: line, line)
                    }
                    title = null
                }
            }
        }
        return out.distinctBy { it.url }
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
    val dotAlpha = remember { Animatable(0f) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            // 1. Picture collapses into a bright horizontal line
            launch { beamWhiteAlpha.animateTo(0.85f, tween(100)) }
            scaleY.animateTo(0.004f, tween(180, easing = FastOutLinearInEasing))
            // 2. Line snaps inward to a center dot
            scaleX.animateTo(0f, tween(140, easing = FastOutLinearInEasing))
            // 3. Dot fades to black, then the app closes
            dotAlpha.snapTo(1f)
            delay(80)
            dotAlpha.animateTo(0f, tween(260))
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
        if (dotAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha.value))
            )
        }
    }
}

@Composable
fun ChannelOsdBanner(visible: Boolean, channel: Channel?, channelIndex: Int) {
    AnimatedVisibility(
        visible = visible && channel != null,
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
                        text = channel?.title.orEmpty(),
                        color = ParchmentWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Live Stream", color = WarmAshMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) OakSurfaceElevated else OakDarkBackground)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AmberDial else OakSurfaceElevated,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) AmberDial else ParchmentWhite,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun ChannelSidebar(
    isOpen: Boolean,
    channels: List<Channel>,
    currentIndex: Int,
    onChannelSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(isOpen) {
        if (isOpen) {
            listState.scrollToItem(currentIndex.coerceAtLeast(0))
            delay(120)
            runCatching { selectedFocus.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.35f) // ~35% of the screen
                .background(SidebarOakGlass)
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHANNELS",
                        color = AmberDial,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TvButton(
                        label = "SETTINGS",
                        onClick = onOpenSettings,
                        fontSize = 13.sp
                    )
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                                .onFocusChanged { isFocused = it.hasFocus }
                                .clickable { onChannelSelected(index) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                color = if (isFocused) AmberDial else WarmAshMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                text = channel.title,
                                color = ParchmentWhite,
                                fontSize = if (isFocused) 20.sp else 17.sp, // enlarged when focused
                                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    isOpen: Boolean,
    currentUrl: String,
    sourceLabel: String,
    message: String?,
    onSave: (String) -> Unit,
    onImportFile: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val urlFocus = remember { FocusRequester() }
    var text by remember(isOpen, currentUrl) { mutableStateOf(currentUrl) }
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            delay(150)
            runCatching { urlFocus.requestFocus() }
        }
    }

    AnimatedVisibility(visible = isOpen, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OakSurface)
                    .border(1.dp, OakSurfaceElevated, RoundedCornerShape(16.dp))
                    .padding(28.dp)
            ) {
                Text(
                    text = "SETTINGS",
                    color = AmberDial,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PLAYLIST URL (M3U / M3U8)  -  Source: $sourceLabel",
                    color = WarmAshMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ParchmentWhite, fontSize = 17.sp),
                    cursorBrush = SolidColor(AmberDial),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OakDarkBackground)
                        .border(
                            width = if (fieldFocused) 2.dp else 1.dp,
                            color = if (fieldFocused) AmberDial else OakSurfaceElevated,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(urlFocus)
                        .onFocusChanged { fieldFocused = it.hasFocus }
                        .onPreviewKeyEvent { e ->
                            // Remote OK opens the on-screen keyboard
                            if (e.key == Key.DirectionCenter) {
                                if (e.type == KeyEventType.KeyUp) keyboard?.show()
                                true
                            } else false
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message ?: "Press OK on the field to type, then Save & Reload",
                    color = if (message != null) AmberDial else WarmAshMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Save & Reload", { onSave(text) }, Modifier.weight(1f))
                    TvButton(
                        "Paste",
                        { clipboard.getText()?.text?.let { text = it.trim() } },
                        Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Import .m3u file", onImportFile, Modifier.weight(1f))
                    TvButton("Reset", onReset, Modifier.weight(1f))
                    TvButton("Close", onClose, Modifier.weight(1f))
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var player: ExoPlayer

    private val isSidebarOpen = mutableStateOf(false)
    private val isSettingsOpen = mutableStateOf(false)
    private val isCrtExiting = mutableStateOf(false)
    private val isOsdVisible = mutableStateOf(false)
    private val currentChannelIndex = mutableStateOf(0)
    private val isBuffering = mutableStateOf(true)
    private val errorMessage = mutableStateOf<String?>(null)
    private val settingsMessage = mutableStateOf<String?>(null)
    private val statusText = mutableStateOf<String?>("Loading playlist...")
    private val playlistUrlState = mutableStateOf(DEFAULT_PLAYLIST_URL)
    private val sourceLabel = mutableStateOf("URL")

    private val scope = MainScope()
    private val prefs by lazy { getSharedPreferences("retro_tv", Context.MODE_PRIVATE) }
    private var playlistLoaded = false

    private val playlistUrl: String
        get() = prefs.getString(KEY_PLAYLIST_URL, null) ?: DEFAULT_PLAYLIST_URL

    private val pickPlaylist =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importLocalPlaylist(uri)
        }

    // Fallback channels, used when no playlist can be loaded and nothing is cached
    private val channels = mutableStateListOf(
        Channel("Big Buck Bunny (HD)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        Channel("Apple HLS Test Stream", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8"),
        Channel("Tears of Steel (HLS)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playlistUrlState.value = playlistUrl
        sourceLabel.value = if (prefs.getBoolean(KEY_USE_LOCAL, false)) "Local file" else "URL"

        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering.value = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) {
                        errorMessage.value = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering.value = false
                    errorMessage.value = "Playback Error: ${error.errorCodeName}"
                }
            })
        }

        loadPlaylist()

        setContent {
            CrtTvExitEffect(
                isExiting = isCrtExiting.value,
                onAnimationComplete = { finish() }
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    // Video View
                    AndroidView(
                        factory = { ctx ->
                            // TextureView surface renders correctly inside Compose layers
                            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                                isFocusable = false
                                isClickable = false
                                this.player = this@MainActivity.player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Touch controls (phones/tablets): tap = sidebar, swipe = change channel
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onScreenTap() })
                            }
                            .pointerInput(Unit) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        if (total < -120f) onSwipe(1) else if (total > 120f) onSwipe(-1)
                                    },
                                    onHorizontalDrag = { _, dx -> total += dx }
                                )
                            }
                    )

                    // Loading Indicator
                    if (isBuffering.value) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AmberDial)
                                statusText.value?.let {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = it, color = WarmAshMuted, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Error Message Display
                    errorMessage.value?.let { error ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OakSurface)
                                    .padding(16.dp)
                            ) {
                                Text(text = error, color = Color.Red, fontSize = 16.sp)
                            }
                        }
                    }

                    // Channel OSD Banner
                    ChannelOsdBanner(
                        visible = isOsdVisible.value,
                        channel = channels.getOrNull(currentChannelIndex.value),
                        channelIndex = currentChannelIndex.value
                    )

                    // Transparent Oak Sidebar
                    ChannelSidebar(
                        isOpen = isSidebarOpen.value,
                        channels = channels,
                        currentIndex = currentChannelIndex.value,
                        onChannelSelected = { index ->
                            currentChannelIndex.value = index
                            playChannel(index)
                            isSidebarOpen.value = false
                            triggerOsd()
                        },
                        onOpenSettings = {
                            isSidebarOpen.value = false
                            openSettings()
                        }
                    )

                    // Settings (playlist URL / local file)
                    SettingsPanel(
                        isOpen = isSettingsOpen.value,
                        currentUrl = playlistUrlState.value,
                        sourceLabel = sourceLabel.value,
                        message = settingsMessage.value,
                        onSave = { saveUrl(it) },
                        onImportFile = { pickFile() },
                        onReset = { resetPlaylist() },
                        onClose = { isSettingsOpen.value = false }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isSettingsOpen.value) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                isSettingsOpen.value = false
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isSidebarOpen.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    isSidebarOpen.value = false
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                    isSidebarOpen.value = false
                    openSettings()
                    return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchChannel(delta = 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
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
                openSettings()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                isCrtExiting.value = true
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun onScreenTap() {
        when {
            isSettingsOpen.value -> Unit
            isSidebarOpen.value -> isSidebarOpen.value = false
            else -> isSidebarOpen.value = true
        }
    }

    private fun onSwipe(delta: Int) {
        if (!isSidebarOpen.value && !isSettingsOpen.value) switchChannel(delta)
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        val newIndex = (currentChannelIndex.value + delta + channels.size) % channels.size
        currentChannelIndex.value = newIndex
        playChannel(newIndex)
        triggerOsd()
    }

    private fun playChannel(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        prefs.edit().putString(KEY_LAST_URL, channel.url).apply()
        isBuffering.value = true
        errorMessage.value = null
        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    private fun triggerOsd() {
        isOsdVisible.value = true
        window.decorView.removeCallbacks(hideOsd)
        window.decorView.postDelayed(hideOsd, 3000)
    }

    private val hideOsd = Runnable { isOsdVisible.value = false }

    // ---- Settings -------------------------------------------------------

    private fun openSettings() {
        settingsMessage.value = null
        isSettingsOpen.value = true
    }

    private fun saveUrl(raw: String) {
        val url = raw.trim()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            settingsMessage.value = "Enter a full http:// or https:// URL"
            return
        }
        prefs.edit().putString(KEY_PLAYLIST_URL, url).putBoolean(KEY_USE_LOCAL, false).apply()
        File(filesDir, CACHE_FILE).delete()
        playlistUrlState.value = url
        sourceLabel.value = "URL"
        settingsMessage.value = "Loading..."
        loadPlaylist(fromSettings = true)
    }

    private fun resetPlaylist() {
        prefs.edit().remove(KEY_PLAYLIST_URL).putBoolean(KEY_USE_LOCAL, false).apply()
        File(filesDir, CACHE_FILE).delete()
        playlistUrlState.value = DEFAULT_PLAYLIST_URL
        sourceLabel.value = "URL"
        settingsMessage.value = "Loading default playlist..."
        loadPlaylist(fromSettings = true)
    }

    private fun pickFile() {
        try {
            pickPlaylist.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            settingsMessage.value = "No file picker on this device - use a URL instead"
        }
    }

    private fun importLocalPlaylist(uri: Uri) {
        scope.launch {
            settingsMessage.value = "Importing..."
            val ok = withContext(Dispatchers.IO) {
                try {
                    val text = contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (M3uParser.parse(text).isEmpty()) {
                        false
                    } else {
                        File(filesDir, LOCAL_FILE).writeText(text)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (!ok) {
                settingsMessage.value = "No channels found in that file"
                return@launch
            }
            prefs.edit().putBoolean(KEY_USE_LOCAL, true).apply()
            sourceLabel.value = "Local file"
            loadPlaylist(fromSettings = true)
        }
    }

    // ---- Playlist loading -----------------------------------------------

    private fun loadPlaylist(fromSettings: Boolean = false) {
        scope.launch {
            val playingUrl =
                if (player.mediaItemCount > 0) channels.getOrNull(currentChannelIndex.value)?.url else null
            val useLocal = prefs.getBoolean(KEY_USE_LOCAL, false)
            val url = playlistUrl

            val list = withContext(Dispatchers.IO) {
                if (useLocal) {
                    runCatching { M3uParser.parse(File(filesDir, LOCAL_FILE).readText()) }
                        .getOrDefault(emptyList())
                } else {
                    val cache = File(filesDir, CACHE_FILE)
                    var result = try {
                        val text = fetchPlaylist(url)
                        val parsed = M3uParser.parse(text)
                        if (parsed.isNotEmpty()) cache.writeText(text)
                        parsed
                    } catch (e: Exception) {
                        emptyList<Channel>()
                    }
                    if (result.isEmpty()) {
                        result = runCatching { M3uParser.parse(cache.readText()) }
                            .getOrDefault(emptyList())
                    }
                    result
                }
            }

            statusText.value = null
            if (list.isNotEmpty()) {
                channels.clear()
                channels.addAll(list)
                playlistLoaded = true
            }

            val target = playingUrl ?: prefs.getString(KEY_LAST_URL, null)
            val idx = channels.indexOfFirst { it.url == target }
            currentChannelIndex.value = if (idx >= 0) idx else 0

            if (playingUrl == null || idx < 0) playChannel(currentChannelIndex.value)

            if (list.isEmpty()) {
                if (fromSettings) {
                    settingsMessage.value = "Couldn't load channels from this source"
                } else {
                    errorMessage.value = if (playlistLoaded) {
                        "Playlist unavailable - kept previous channels"
                    } else {
                        "Playlist unavailable - using demo channels"
                    }
                }
            } else {
                errorMessage.value = null
                if (fromSettings) {
                    settingsMessage.value = null
                    isSettingsOpen.value = false
                    triggerOsd()
                }
            }
        }
    }

    private fun fetchPlaylist(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "RetroTV/1.2")
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---- Lifecycle --------------------------------------------------------

    override fun onStart() {
        super.onStart()
        if (player.mediaItemCount > 0) {
            if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
            player.play()
        }
    }

    override fun onStop() {
        player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.decorView.removeCallbacks(hideOsd)
        scope.cancel()
        player.release()
    }

    companion object {
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/bd.m3u"
        private const val KEY_PLAYLIST_URL = "playlist_url"
        private const val KEY_USE_LOCAL = "use_local"
        private const val KEY_LAST_URL = "last_url"
        private const val CACHE_FILE = "playlist.m3u"
        private const val LOCAL_FILE = "local.m3u"
    }
}
