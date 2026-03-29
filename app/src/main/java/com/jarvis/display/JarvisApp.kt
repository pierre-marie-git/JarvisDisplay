package com.jarvis.display

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.jarvis.display.inbox.InboxServer

class JarvisApp : Application() {

    companion object {
        const val WATCHDOG_CHANNEL_ID = "watchdog_channel"
        const val INBOX_PORT = 8767
        lateinit var instance: JarvisApp
            private set
    }

    private var inboxServer: InboxServer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    fun startInboxServer(onMatrices: (List<Matrix>) -> Unit) {
        if (inboxServer != null) return
        inboxServer = InboxServer(INBOX_PORT, onMatrices)
        inboxServer?.start()
        Log.i("JarvisApp", "InboxServer started on port $INBOX_PORT")
    }

    fun stopInboxServer() {
        inboxServer?.stop()
        inboxServer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WATCHDOG_CHANNEL_ID,
                "Watchdog Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JarvisDisplay running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
