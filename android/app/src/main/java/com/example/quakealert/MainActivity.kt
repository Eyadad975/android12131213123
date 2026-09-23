package com.example.quakealert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.app.PendingIntent
import android.view.View
import android.widget.*
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var location: TextView
    private lateinit var mapView: WebView
    private var userLat = 30.0444
    private var userLon = 31.2357
    private var lastAlert = 0
    private var lastNotification = 0
    private var loadedMapUrl = ""
    private var polling = false
    private val poll = object : Runnable {
        override fun run() {
            thread { fetchState() }
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(state: Bundle?) {
        applyTheme(); super.onCreate(state)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,28,20,20); setBackgroundColor(Color.rgb(13,17,23)) }
        val title=TextView(this).apply { text="QUAKE ALERT"; textSize=30f; letterSpacing=.12f; setTextColor(Color.WHITE); setTypeface(null,1) }
        val subtitle=TextView(this).apply { text="EARTHQUAKE MONITOR"; textSize=12f; letterSpacing=.18f; setTextColor(Color.rgb(150,160,175)); setPadding(0,6,0,28) }
        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18); background=cardBackground() }
        status=TextView(this).apply { text="Waiting for earthquake alerts"; textSize=15f; setTextColor(Color.rgb(230,237,243)); setPadding(0,0,0,14) }
        location=TextView(this).apply { text="USER LOCATION\nWaiting for server"; textSize=14f; letterSpacing=.04f; setTextColor(Color.rgb(190,200,212)); setPadding(0,10,0,0) }
        mapView=WebView(this).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            webViewClient=object: WebViewClient() {
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    val host=android.net.Uri.parse(prefs.getString("server_url","https://127.0.0.1")).host
                    val url=error.url ?: ""
                    if(host != null && url.contains(host)) handler.proceed() else handler.cancel()
                }
            }
        }
        card.addView(mapView, LinearLayout.LayoutParams(-1, 380))
        card.addView(status); card.addView(location)
        val settings=Button(this).apply { text="SERVER SETTINGS"; setTextColor(Color.WHITE); setOnClickListener { startActivity(Intent(this@MainActivity,SettingsActivity::class.java)) } }
        root.addView(title); root.addView(subtitle); root.addView(card, LinearLayout.LayoutParams(-1, -2))
        root.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { topMargin=28 }); setContentView(root)
        loadMap()
        createNotificationChannel()
        val serviceIntent=Intent(this, AlertPollingService::class.java)
        if(Build.VERSION.SDK_INT>=26) startForegroundService(serviceIntent) else startService(serviceIntent)
        requestFullScreenIntentPermission()
        val needed=mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 10)
    }
    override fun onResume() {
        super.onResume()
        val configured=prefs.getString("server_url","https://127.0.0.1")!!.trimEnd('/')
        if(configured != loadedMapUrl) loadMap()
        if (!polling) { polling=true; handler.post(poll) }
    }
    // Keep monitoring while the launcher or another app is visible so the
    // full-screen notification can wake the warning activity.
    override fun onPause() { super.onPause() }
    private fun fetchState() {
        try {
            val base=prefs.getString("server_url","https://127.0.0.1")!!.trimEnd('/')
            val c=Network.open("$base/api/state")
            c.connectTimeout=5000; c.readTimeout=5000
            val data=JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect(); runOnUiThread { render(data) }
        } catch (_: Exception) { runOnUiThread { status.text="Cannot connect to Quake Alert server" } }
    }
    private fun render(data: JSONObject) {
        status.text="Connected · waiting for earthquake alerts"
        val user=data.optJSONObject("user")
        if (user!=null) {
            userLat=user.optDouble("lat",userLat)
            userLon=user.optDouble("lon",userLon)
            location.text="USER LOCATION\n%.5f°, %.5f°".format(userLat, userLon)
        }
        val alert=data.optJSONObject("alert"); val q=data.optJSONObject("quake")
        mapView.evaluateJavascript("setUser($userLat,$userLon);", null)
        val id=alert?.optInt("id",0) ?: 0
        if (q!=null && id>lastAlert) {
            lastAlert=id
            mapView.evaluateJavascript("showQuake(${q});", null)
        }
    }
    private fun isInForeground(): Boolean =
        !isFinishing && !isDestroyed && hasWindowFocus()

    private fun requestFullScreenIntentPermission() {
        if(Build.VERSION.SDK_INT >= 34) {
            val manager=getSystemService(NotificationManager::class.java)
            if(!manager.canUseFullScreenIntent()) {
                startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                    data=android.net.Uri.parse("package:$packageName")
                })
            }
        }
    }
    private fun loadMap() {
        val base=prefs.getString("server_url","https://127.0.0.1")!!.trimEnd('/')
        loadedMapUrl=base
        mapView.loadUrl("$base/map")
    }
    private fun showNotification(title: String, body: String) {
        getSystemService(NotificationManager::class.java).notify(
            1000 + lastNotification,
            Notification.Builder(this,"quake_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body)
                .setPriority(Notification.PRIORITY_DEFAULT).setAutoCancel(true).build()
        )
    }
    private fun cardBackground() = GradientDrawable().apply {
        setColor(Color.rgb(27,34,44)); cornerRadius=24f
        setStroke(1, Color.rgb(54,65,80))
    }
    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("quake_alerts","Earthquake alerts",NotificationManager.IMPORTANCE_HIGH).apply { description="Major earthquake warnings" })
    }
    private fun applyTheme() {
        if(prefs.getBoolean("dark",true)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar)
    }
}
