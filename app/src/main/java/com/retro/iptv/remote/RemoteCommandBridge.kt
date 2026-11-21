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
                "HOME" -> RetroTvAccessibilityService.performGlobal(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                "BACK" -> RetroTvAccessibilityService.performGlobal(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            RemoteProtocol.CMD_SEARCH -> RetroTvAccessibilityService.setFocusedText(value)
            RemoteProtocol.CMD_MEDIA -> if (value == "POWER") {
                RetroTvAccessibilityService.performGlobal(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }
}