package com.retro.iptv

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.common.MediaItem

/**
 * Owns the Media3 player instance and its host-activity lifecycle operations.
 *
 * MainActivity remains responsible for app-specific playback policy and UI callbacks;
 * this class centralizes player construction, foreground pause, explicit play, and release.
 */
internal class PlayerController(context: Context) {
    val player: ExoPlayer
    private lateinit var hlsMediaSourceFactory: HlsMediaSource.Factory

    init {
        val appContext = context.applicationContext
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(4_000)
            .setReadTimeoutMs(8_000)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(httpDataSourceFactory)
        hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 25_000, 800, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext)
            .setInitialBitrateEstimate(1_500_000L)
            .build()

        player = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    /** Replace the current stream while retaining this single activity-scoped player instance. */
    fun setStream(url: String) {
        val item = MediaItem.fromUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) {
            player.setMediaSource(hlsMediaSourceFactory.createMediaSource(item))
        } else {
            player.setMediaItem(item)
        }
    }

    /** Pause immediately when the activity leaves the foreground. Never auto-resume. */
    fun pauseForBackground() {
        if (player.isPlaying || player.playWhenReady) player.pause()
    }

    /** Called only after an explicit user action such as selecting a channel. */
    fun play() {
        player.play()
    }

    fun release() {
        player.release()
    }
}
