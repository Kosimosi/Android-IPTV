package com.retro.iptv.remote

import android.content.Context

object RemoteCommandBridge {
    @Volatile private var foregroundCommand: ((String, String) -> Unit)? = null
    @Volatile private var foregroundState: (() -> String)? = null

    fun attach(command: (String, String) -> Unit, state: () -> String) {
        foregroundCommand = command
        foregroundState = state
    }

    fun detach() {
        foregroundCommand = null
        foregroundState = null
        // Tell already-paired phones that the TV app is no longer foregrounded.
        TvRemoteBackgroundService.broadcastState(state())
    }

    fun hasForegroundHandler(): Boolean = foregroundCommand != null

    fun state(): String = runCatching { foregroundState?.invoke() }.getOrNull()
        ?: """{"type":"STATE","playing":false,"inRetroTv":false,"currentChannel":-1,"channels":[]}"""

    fun dispatch(context: Context, command: String, value: String) {
        val active = foregroundCommand
        if (active != null) {
            active(command, value)
            return
        }
        when (command) {
            RemoteProtocol.CMD_DPAD -> when (value) {
                "HOME" -> RetroTvAccessibilityService.performHome(context)
                "BACK" -> RetroTvAccessibilityService.performBack(context)
            }
            RemoteProtocol.CMD_SEARCH -> RetroTvAccessibilityService.setFocusedText(value)
            RemoteProtocol.CMD_MEDIA -> if (value == "POWER") {
                RetroTvAccessibilityService.performHome(context)
            }
        }
    }
}