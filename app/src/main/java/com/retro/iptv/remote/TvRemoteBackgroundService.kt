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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pin = prefs.getString(KEY_PIN, null)?.takeIf { it.matches(Regex("\\d{6}")) }
            ?: (SecureRandom().nextInt(900_000) + 100_000).toString().also {
                prefs.edit().putString(KEY_PIN, it).apply()
            }
        server = TvRemoteServer(
            context = applicationContext,
            pairingCode = pin,
            stateProvider = RemoteCommandBridge::state,
            onCommandReceived = { command, value ->
                RemoteCommandBridge.dispatch(applicationContext, command, value)
            },
            onStatusChanged = { status ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATUS, status).apply()
            }
        ).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
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
        return builder.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("RetroTV remote is available")
            .setContentText("Phone remote server • port 8899")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PREFS = "retro_remote_service"
        const val KEY_PIN = "pairing_pin"
        const val KEY_STATUS = "server_status"
        private const val CHANNEL_ID = "retrotv_remote_service"
        private const val NOTIFICATION_ID = 8899
    }
}