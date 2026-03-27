package com.jarvis.display

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : Activity(), FlipPresenter.FlipView {

    private lateinit var flipBoard: FlipBoardView
    private lateinit var accentBar: View
    private lateinit var muteButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var tapZone: View

    private lateinit var presenter: FlipPresenter
    private lateinit var apiService: ApiService
    private lateinit var cacheManager: CacheManager

    private var adminTapCount = 0
    private var lastAdminTapTime = 0L
    private val ADMIN_TAP_DELAY = 500L

    private val ACCENT_COLORS = intArrayOf(
        Color.parseColor("#FF3333"),
        Color.parseColor("#3333FF"),
        Color.parseColor("#33FF33"),
        Color.parseColor("#FFFF33"),
        Color.parseColor("#FF33FF"),
        Color.parseColor("#33FFFF"),
        Color.parseColor("#FF8833"),
        Color.parseColor("#8833FF")
    )

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                if (isNetworkAvailable(this@MainActivity)) {
                    presenter.refreshNow()
                }
            }
        }
    }

    private val watchdogHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: android.os.Message) {
            WatchdogService.noteMainActivityRunning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen kiosk mode
        hideSystemUI()

        setContentView(R.layout.activity_main)

        cacheManager = CacheManager(this)
        apiService = ApiService()
        apiService.setBaseUrl("http://${cacheManager.getServerIp()}")

        initViews()
        setupAdminTapZone()
        registerNetworkReceiver()
        startWatchdogHeartbeat()

        presenter = FlipPresenter(this, apiService, cacheManager)

        // Start watchdog service
        WatchdogService.startIfNotRunning(this)

        presenter.start()
    }

    @SuppressLint("WrongConstant")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }

    private fun initViews() {
        flipBoard = findViewById(R.id.flip_board)
        accentBar = findViewById(R.id.accent_bar)
        muteButton = findViewById(R.id.mute_button)
        statusText = findViewById(R.id.status_text)
        tapZone = findViewById(R.id.admin_tap_zone)

        updateAccentBar(Color.parseColor("#33FF33"))

        muteButton.setOnClickListener {
            presenter.toggleMute()
        }
        // Set mute state directly from cache (presenter not yet available)
        muteButton.setImageDrawable(ContextCompat.getDrawable(this,
            if (cacheManager.isMuted()) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off))

        flipBoard.onAllAnimationsComplete = {
            watchdogHandler.sendEmptyMessage(0)
        }
    }

    private fun setupAdminTapZone() {
        // Bottom-right corner: 5 taps to reveal admin
        tapZone.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastAdminTapTime > ADMIN_TAP_DELAY * 3) {
                adminTapCount = 0
            }
            lastAdminTapTime = now
            adminTapCount++
            if (adminTapCount >= 5) {
                adminTapCount = 0
                openAdminActivity()
            }
        }
    }

    private fun openAdminActivity() {
        val intent = Intent(this, AdminActivity::class.java)
        startActivity(intent)
    }

    private fun registerNetworkReceiver() {
        NetworkWatcher.onNetworkAvailable = {
            Handler(Looper.getMainLooper()).post {
                presenter.refreshNow()
            }
        }
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, filter)
    }

    private fun startWatchdogHeartbeat() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                WatchdogService.noteMainActivityRunning()
                handler.postDelayed(this, 5_000)
            }
        }
        handler.post(runnable)
    }

    override fun displayMessages(messages: List<Message>, onMessageChanged: (Boolean) -> Unit) {
        flipBoard.displayMessages(messages) { changed ->
            if (changed) {
                randomizeAccentColor()
            }
            onMessageChanged(changed)
        }
    }

    override fun showDefaultMessage() {
        flipBoard.showDefaultMessage()
        randomizeAccentColor()
    }

    override fun updateLastRefreshTime(time: String) {
        statusText.text = "Last refresh: $time"
    }

    override fun updateAccentBar(color: Int) {
        accentBar.setBackgroundColor(color)
    }

    override fun setMuted(muted: Boolean) {
        flipBoard.setMuted(muted)
        updateMuteButton()
    }

    private fun updateMuteButton() {
        val iconRes = if (presenter.isMuted()) {
            android.R.drawable.ic_lock_silent_mode
        } else {
            android.R.drawable.ic_lock_silent_mode_off
        }
        muteButton.setImageDrawable(ContextCompat.getDrawable(this, iconRes))
    }

    private fun randomizeAccentColor() {
        val color = ACCENT_COLORS[Random().nextInt(ACCENT_COLORS.size)]
        updateAccentBar(color)
    }

    override fun onResume() {
        super.onResume()
        WatchdogService.noteMainActivityRunning()
        hideSystemUI()
        updateMuteButton()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        presenter.stop()
        try {
            unregisterReceiver(networkReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        super.onDestroy()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
