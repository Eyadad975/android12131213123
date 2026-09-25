package com.example.quakealert

import android.app.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.http.SslError
import android.os.*
import android.view.Gravity
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var mapView: WebView
    private var loadedMapUrl = ""
    private var permissionPromptOpen = false
    private val permissionHandler = Handler(Looper.getMainLooper())
    private val permissionPrompt = object : Runnable {
        override fun run() {
            if (!permissionPromptOpen) requestFullScreenIntentPermission()
            permissionHandler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(state: Bundle?) {
        applyTheme()
        super.onCreate(state)
        mapView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    val host = android.net.Uri.parse(
                        prefs.getString("server_url", "https://127.0.0.1")
                    ).host
                    if (host != null && (error.url ?: "").contains(host)) handler.proceed()
                    else handler.cancel()
                }
            }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(13, 17, 23))
            addView(mapView, FrameLayout.LayoutParams(-1, -1))
        }
        var cornerTaps = 0
        fun addCornerHitArea(gravity: Int) {
            root.addView(View(this).apply {
                isClickable = true
                setOnClickListener {
                    cornerTaps++
                    if (cornerTaps >= 15) {
                        cornerTaps = 0
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                }
            }, FrameLayout.LayoutParams(dp(140), dp(140)).apply {
                this.gravity = gravity
            })
        }
        addCornerHitArea(Gravity.TOP or Gravity.START)
        addCornerHitArea(Gravity.TOP or Gravity.END)
        addCornerHitArea(Gravity.BOTTOM or Gravity.START)
        addCornerHitArea(Gravity.BOTTOM or Gravity.END)
        setContentView(root)
        loadMap()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        } else {
            startMonitoring()
        }
        permissionHandler.post(permissionPrompt)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 20) startMonitoring()
    }

    private fun startMonitoring() {
        val serviceIntent = Intent(this, AlertPollingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    override fun onResume() {
        super.onResume()
        permissionPromptOpen = false
        val configured = prefs.getString("server_url", "https://127.0.0.1")!!.trimEnd('/')
        if (configured != loadedMapUrl) loadMap()
    }

    private fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = getSystemService(NotificationManager::class.java)
            if (!manager.canUseFullScreenIntent()) {
                permissionPromptOpen = true
                startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun loadMap() {
        val base = prefs.getString("server_url", "https://127.0.0.1")!!.trimEnd('/')
        loadedMapUrl = base
        mapView.loadUrl("$base/map")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    "quake_alerts", "Earthquake alerts", NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun applyTheme() {
        if (prefs.getBoolean("dark", true)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar)
    }

    override fun onDestroy() {
        permissionHandler.removeCallbacks(permissionPrompt)
        mapView.destroy()
        super.onDestroy()
    }
}
