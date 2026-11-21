package com.retro.iptv.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo

class RetroTvAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: RetroTvAccessibilityService? = null

        fun performGlobal(context: Context, action: Int): Boolean {
            if (action != AccessibilityService.GLOBAL_ACTION_HOME && action != AccessibilityService.GLOBAL_ACTION_BACK) return false
            return runCatching { instance?.performGlobalAction(action) == true }.getOrDefault(false)
        }

        fun performHome(context: Context): Boolean = performGlobal(context, AccessibilityService.GLOBAL_ACTION_HOME)

        fun performBack(context: Context): Boolean = performGlobal(context, AccessibilityService.GLOBAL_ACTION_BACK)

        fun setFocusedText(value: String): Boolean {
            val service = instance ?: return false
            return runCatching {
                val root = service.rootInActiveWindow ?: return false
                val node = findEditable(root) ?: return false
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                // ACTION_SET_TEXT is the supported accessibility action for replacing
                // text in the currently focused editable field of another app.
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }.getOrDefault(false)
        }

        private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable && node.isFocused) return node
            for (index in 0 until node.childCount) {
                val found = node.getChild(index)?.let(::findEditable)
                if (found != null) return found
            }
            return null
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.contains(context.packageName + "/" + RetroTvAccessibilityService::class.java.name)
        }
    }
}