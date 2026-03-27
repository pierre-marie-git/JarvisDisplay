package com.jarvis.display

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.app.Activity

class AdminActivity : Activity() {

    private lateinit var cacheManager: CacheManager
    private lateinit var apiService: ApiService
    private lateinit var serverIpInput: EditText
    private lateinit var statusText: TextView
    private lateinit var lastErrorText: TextView
    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        // Keep screen on while admin is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cacheManager = CacheManager(this)
        apiService = ApiService()
        apiService.setBaseUrl("http://${cacheManager.getServerIp()}")

        setupViews()
        updateStatus()
    }

    private fun setupViews() {
        serverIpInput = findViewById(R.id.server_ip_input)
        statusText = findViewById(R.id.status_text)
        lastErrorText = findViewById(R.id.last_error_text)
        versionText = findViewById(R.id.version_text)
        val saveBtn = findViewById<Button>(R.id.save_button)
        val refreshBtn = findViewById<Button>(R.id.refresh_button)

        serverIpInput.setText(cacheManager.getServerIp())

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "Version: ${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            versionText.text = "Version: 1.0"
        }

        saveBtn.setOnClickListener {
            val ip = serverIpInput.text.toString().trim()
            if (ip.isNotEmpty()) {
                cacheManager.setServerIp(ip)
                apiService.setBaseUrl("http://$ip")
                cacheManager.setLastError(null)
                updateStatus()
            }
        }

        refreshBtn.setOnClickListener {
            apiService.fetchStatus(object : ApiService.ApiCallback<StatusInfo> {
                override fun onSuccess(data: StatusInfo) {
                    cacheManager.setLastError(null)
                    runOnUiThread {
                        statusText.text = "Server: OK (${data.status})"
                        lastErrorText.text = "Last error: None"
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        statusText.text = "Server: UNREACHABLE"
                        cacheManager.setLastError(error)
                    }
                }
            })
        }
    }

    private fun updateStatus() {
        val lastError = cacheManager.getLastError()
        if (lastError != null) {
            lastErrorText.text = "Last error: $lastError"
        } else {
            lastErrorText.text = "Last error: None"
        }
        statusText.text = "Server IP: ${cacheManager.getServerIp()}"
    }

    override fun onResume() {
        super.onResume()
        WatchdogService.noteMainActivityRunning()
    }
}
