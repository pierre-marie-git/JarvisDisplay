package com.jarvis.display

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.InputStream

class FlipPresenter(
    private val view: FlipView,
    private val apiService: ApiService,
    private val cacheManager: CacheManager
) {

    interface FlipView {
        fun displayMessages(messages: List<Message>, onMessageChanged: (Boolean) -> Unit)
        fun showDefaultMessage()
        fun updateLastRefreshTime(time: String)
        fun updateAccentBar(color: Int)
        fun setMuted(muted: Boolean)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var pollingRunnable: Runnable? = null
    private var isPolling = false
    private var retryCount = 0
    private val maxRetryInterval = 60_000L // 60 seconds max
    private var currentRetryInterval = 3_000L

    private var lastAudioFetchTime = 0L
    private var lastMessageId: String? = null

    init {
        // setMuted called explicitly after presenter creation
    }

    fun start() {
        // Load cached messages first
        val cached = cacheManager.loadMessages()
        if (cached.isNotEmpty()) {
            view.displayMessages(cached) { changed ->
                if (changed) {
                    playSoundIfNeeded()
                }
            }
        } else {
            view.showDefaultMessage()
        }

        startPolling()
    }

    fun stop() {
        isPolling = false
        pollingRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        pollNow()
    }

    private fun pollNow() {
        if (!isPolling) return

        apiService.fetchMessages(object : ApiService.ApiCallback<List<Message>> {
            override fun onSuccess(data: List<Message>) {
                cacheManager.setLastError(null)
                retryCount = 0
                currentRetryInterval = 3_000L

                cacheManager.saveMessages(data)
                lastMessageId = data.firstOrNull()?.id

                view.displayMessages(data) { changed ->
                    if (changed) {
                        playSoundIfNeeded()
                    }
                }

                updateRefreshTime()
                scheduleNextPoll(3_000L)
            }

            override fun onError(error: String) {
                cacheManager.setLastError(error)
                scheduleNextPoll(currentRetryInterval)
                currentRetryInterval = minOf(currentRetryInterval * 2, maxRetryInterval)
            }
        })
    }

    private fun scheduleNextPoll(interval: Long) {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = Runnable {
            pollNow()
        }
        handler.postDelayed(pollingRunnable!!, interval)
    }

    fun refreshNow() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollNow()
    }

    private fun playSoundIfNeeded() {
        if (cacheManager.isMuted()) return

        val now = System.currentTimeMillis()
        if (now - lastAudioFetchTime < 60_000L && mediaPlayer != null) {
            mediaPlayer?.start()
            return
        }
        lastAudioFetchTime = now

        apiService.fetchAudio(object : ApiService.AudioCallback {
            override fun onSuccess(stream: InputStream) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(stream as? java.io.FileDescriptor)
                        // For InputStream, we use a different approach
                    }
                    // Use a simpler approach - just create a temp file
                    val tmpFile = java.io.File.createTempFile("jarvis_audio", ".wav")
                    tmpFile.outputStream().use { fos ->
                        stream.copyTo(fos)
                    }
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tmpFile.absolutePath)
                        setOnPreparedListener { mp -> mp.start() }
                        setOnCompletionListener { mp ->
                            mp.release()
                            tmpFile.delete()
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    // Silently fail audio
                }
            }

            override fun onError(error: String) {
                // Silently fail audio
            }
        })
    }

    fun toggleMute() {
        val newMuted = !cacheManager.isMuted()
        cacheManager.setMuted(newMuted)
        view.setMuted(newMuted)
    }

    fun isMuted(): Boolean = cacheManager.isMuted()

    fun getServerIp(): String = cacheManager.getServerIp()

    fun getLastError(): String? = cacheManager.getLastError()

    fun setServerIp(ip: String) {
        cacheManager.setServerIp(ip)
        apiService.setBaseUrl("http://$ip")
        refreshNow()
    }

    private fun updateRefreshTime() {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        view.updateLastRefreshTime(sdf.format(java.util.Date()))
    }

    fun updateAccentColor(color: Int) {
        view.updateAccentBar(color)
    }
}
