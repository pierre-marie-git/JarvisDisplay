package com.jarvis.display

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val NOTIFICATION_ID = 1001
        private var lastMainActivityTime = 0L
        private const val MAX_IDLE_MS = 10_000L // 10 seconds

        fun startIfNotRunning(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun noteMainActivityRunning() {
            lastMainActivityTime = SystemClock.elapsedRealtime()
        }
    }

    private var watchdogThread: Thread? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Watchdog service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        running = true
        startWatchdog()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    JarvisApp.WATCHDOG_CHANNEL_ID,
                    "Watchdog Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps JarvisDisplay running"
                    setShowBadge(false)
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create channel: ${e.message}")
            }
        }
    }

    private fun createNotification(): Notification {
        // Ensure channel exists (idempotent)
        try {
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Channel setup failed: ${e.message}")
        }

        val pendingIntent = try {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } catch (e: Exception) {
            Log.e(TAG, "PendingIntent failed: ${e.message}")
            null
        }

        val b = NotificationCompat.Builder(this, JarvisApp.WATCHDOG_CHANNEL_ID)
            .setContentTitle("JarvisDisplay")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        pendingIntent?.let { b.setContentIntent(it) }

        return b.build()
    }

    private fun startWatchdog() {
        watchdogThread?.interrupt()
        watchdogThread = Thread {
            while (running) {
                try {
                    Thread.sleep(5_000L)
                    val elapsed = SystemClock.elapsedRealtime() - lastMainActivityTime
                    if (lastMainActivityTime > 0 && elapsed > MAX_IDLE_MS) {
                        Log.w(TAG, "MainActivity unresponsive for ${elapsed}ms, restarting...")
                        restartMainActivity()
                        lastMainActivityTime = SystemClock.elapsedRealtime()
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
    }

    private fun restartMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        running = false
        watchdogThread?.interrupt()
        super.onDestroy()
    }
}
