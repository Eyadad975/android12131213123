package com.example.quakealert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var location: TextView
    private var userLat = 30.0444
    private var userLon = 31.2357
    private var lastAlert = 0
    private val poll = object : Runnable {
        override fun run() {
            thread { fetchState() }
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(state: Bundle?) {
        applyTheme(); super.onCreate(state)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24); setBackgroundColor(Color.rgb(12,16,22)) }
        val title=TextView(this).apply { text="QUAKE ALERT"; textSize=30f; letterSpacing=.12f; setTextColor(Color.WHITE); setTypeface(null,1) }
        val subtitle=TextView(this).apply { text="EARTHQUAKE MONITOR"; textSize=12f; letterSpacing=.18f; setTextColor(Color.rgb(150,160,175)); setPadding(0,6,0,28) }
        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24); background=cardBackground() }
        status=TextView(this).apply { text="Connecting…"; textSize=18f; setTextColor(Color.WHITE); setPadding(0,0,0,20) }
        location=TextView(this).apply { text="Server location\nWaiting for server"; textSize=16f; setTextColor(Color.rgb(205,212,222)); setPadding(0,10,0,0) }
        card.addView(status); card.addView(location)
        val settings=Button(this).apply { text="SERVER SETTINGS"; setTextColor(Color.WHITE); setOnClickListener { startActivity(Intent(this@MainActivity,SettingsActivity::class.java)) } }
        root.addView(title); root.addView(subtitle); root.addView(card, LinearLayout.LayoutParams(-1, -2))
        root.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { topMargin=28 }); setContentView(root)
        createNotificationChannel()
        val needed=mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 10)
    }
    override fun onResume() { super.onResume(); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }
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
            location.text="Server location\n%.5f°, %.5f°".format(userLat, userLon)
        }
        val alert=data.optJSONObject("alert"); val q=data.optJSONObject("quake")
        val id=alert?.optInt("id",0) ?: 0
        if (q!=null && q.optBoolean("major",false) && id>lastAlert) {
            lastAlert=id
            startActivity(Intent(this,AlertActivity::class.java)
                .putExtra("quake",q.toString())
                .putExtra("user_lat",user?.optDouble("lat",Double.NaN) ?: Double.NaN)
                .putExtra("user_lon",user?.optDouble("lon",Double.NaN) ?: Double.NaN))
        }
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
