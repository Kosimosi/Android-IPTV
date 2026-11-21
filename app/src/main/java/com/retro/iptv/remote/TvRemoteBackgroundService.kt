package com.retro.iptv.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.retro.iptv.MainActivity
import com.retro.iptv.R
import java.security.SecureRandom

class TvRemoteBackgroundService : Service() {
    private var server: TvRemoteServer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REMOTE_ENABLED, true)) {
            stopSelf()
            return
        }
        val savedPin = prefs.getString(KEY_PIN, null)
        val pin = if (savedPin != null && savedPin.length == 6 && savedPin.all { it.isDigit() }) {
            savedPin
        } else {
            (SecureRandom().nextInt(900_000) + 100_000).toString().also {
                prefs.edit().putString(KEY_PIN, it).apply()
            }
        }
        startServer(pin)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startServer(pin: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        server = TvRemoteServer(
            context = applicationContext,
            pairingCode = pin,
            stateProvider = RemoteCommandBridge::state,
            onCommandReceived = { command, value ->
                if (command == RemoteProtocol.CMD_LAUNCH_RETROTV && value in RemoteProtocol.launchTargets) {
                    if (value == RemoteProtocol.TARGET_YOUTUBE) {
                        packageManager.getLaunchIntentForPackage("com.google.android.youtube.tv")?.let { launch ->
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(launch) }
                        }
                    } else if (RemoteCommandBridge.hasForegroundHandler()) {
                        RemoteCommandBridge.dispatch(applicationContext, command, value)
                    } else {
                        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            launch.putExtra(EXTRA_LAUNCH_TARGET, value)
                            runCatching { startActivity(launch) }
                        }
                    }
                } else {
                    RemoteCommandBridge.dispatch(applicationContext, command, value)
                }
            },
            onStatusChanged = { status -> prefs.edit().putString(KEY_STATUS, status).apply() }
        ).also { it.start() }
    }

    private fun restartServer(newPin: String) {
        val previous = server
        server = null
        Thread {
            previous?.stopAndWait()
            synchronized(this) {
                if (instance === this && getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_REMOTE_ENABLED, true)) startServer(newPin)
            }
        }.apply { name = "RetroTV-PIN-Rotation"; isDaemon = true; start() }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "RetroTV Remote", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RetroTV remote is available")
            .setContentText("Phone remote server • port 8899")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile private var instance: TvRemoteBackgroundService? = null
        fun broadcastState(state: String) { instance?.server?.broadcastState(state) }
        const val PREFS = "retro_remote_service"
        const val KEY_PIN = "pairing_pin"
        const val KEY_STATUS = "server_status"
        const val KEY_REMOTE_ENABLED = "remote_enabled"
        const val EXTRA_LAUNCH_TARGET = "launch_target"

        fun regeneratePin(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            val pin = (SecureRandom().nextInt(900_000) + 100_000).toString()
            prefs.edit().putString(KEY_PIN, pin).apply()
            instance?.restartServer(pin)
            return pin
        }

        private const val CHANNEL_ID = "retrotv_remote_service"
        private const val NOTIFICATION_ID = 8899
    }
}