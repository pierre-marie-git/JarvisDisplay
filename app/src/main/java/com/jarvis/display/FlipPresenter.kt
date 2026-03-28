package com.jarvis.display

import android.os.Handler
import android.os.Looper

/**
 * Fetches matrices from the API server and loads them into the MatrixQueue.
 * The MatrixQueue handles all display timing/looping — this presenter just manages data.
 */
class FlipPresenter(
    private val view: FlipView,
    private val apiService: ApiService,
    private val cacheManager: CacheManager
) {
    interface FlipView {
        fun showDefaultMatrix()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isMuted = false

    fun start() {
        // Start with test data immediately — no server needed
        view.showDefaultMatrix()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    fun toggleMute() {
        isMuted = !isMuted
        cacheManager.setMuted(isMuted)
    }
}
