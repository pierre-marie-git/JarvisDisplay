package com.jarvis.display

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
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

class MainActivity : Activity(), FlipPresenter.FlipView {

    private lateinit var flipBoard: FlipBoardView
    private lateinit var matrixQueue: MatrixQueue
    private lateinit var muteButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var tapZone: View
    private lateinit var accentBar: View

    private var adminTapCount = 0
    private var lastAdminTapTime = 0L
    private val ADMIN_TAP_DELAY = 2000L
    private val ADMIN_TAP_THRESHOLD = 5

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        initViews()
        setupAdminTapZone()

        // Create queue and start with test data
        matrixQueue = MatrixQueue(flipBoard) {
            runOnUiThread { updateStatus() }
        }
        matrixQueue.startWithTestData()

        // Start InboxServer to receive matrices from backend
        (application as JarvisApp).startInboxServer { matrices ->
            if (matrices.isNotEmpty()) {
                runOnUiThread {
                    matrixQueue.loadAndPlay(matrices)
                }
            }
        }
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
            // Toggle mute (not implemented yet in MVP)
        }

        statusText.text = "last refresh --:--:--"

        flipBoard.onAnimationComplete = {
            runOnUiThread { updateStatus() }
        }
    }

    private fun updateStatus() {
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        statusText.text = "last refresh ${now.format(java.util.Date())}"
    }

    private fun setupAdminTapZone() {
        tapZone.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastAdminTapTime > ADMIN_TAP_DELAY * 3) {
                adminTapCount = 0
            }
            lastAdminTapTime = now
            adminTapCount++
            if (adminTapCount >= ADMIN_TAP_THRESHOLD) {
                adminTapCount = 0
                openAdminActivity()
            }
        }
    }

    private fun openAdminActivity() {
        startActivity(android.content.Intent(this, AdminActivity::class.java))
    }

    override fun showDefaultMatrix() {
        flipBoard.showDefaultMatrix()
    }

    private fun randomizeAccentColor() {
        val colors = intArrayOf(
            Color.parseColor("#FF5722"), Color.parseColor("#E91E63"),
            Color.parseColor("#9C27B0"), Color.parseColor("#673AB7"),
            Color.parseColor("#3F51B5"), Color.parseColor("#2196F3"),
            Color.parseColor("#009688"), Color.parseColor("#4CAF50"),
            Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B")
        )
        updateAccentBar(colors[kotlin.random.Random.nextInt(colors.size)])
    }

    private fun updateAccentBar(color: Int) {
        if (::accentBar.isInitialized) {
            accentBar.setBackgroundColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::matrixQueue.isInitialized) {
            matrixQueue.stop()
        }
        (application as JarvisApp).stopInboxServer()
    }
}
