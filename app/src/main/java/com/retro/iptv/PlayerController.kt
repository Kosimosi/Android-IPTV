package com.retro.iptv

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * Owns the Media3 player instance and its host-activity lifecycle operations.
 *
 * MainActivity remains responsible for app-specific playback policy and UI callbacks;
 * this class centralizes player construction, foreground pause, explicit play, and release.
 */
internal class PlayerController(context: Context) {
    val player: ExoPlayer

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
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
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
