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
        card.addView(MapMonitorView(this), LinearLayout.LayoutParams(-1, 260))
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
    override fun onResume() { super.onResume(); if (!polling) { polling=true; handler.post(poll) } }
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
        val id=alert?.optInt("id",0) ?: 0
        if (q!=null && id>lastAlert) {
            lastAlert=id
            val intent=Intent(this,AlertActivity::class.java)
                .putExtra("quake",q.toString())
                .putExtra("user_lat",user?.optDouble("lat",Double.NaN) ?: Double.NaN)
                .putExtra("user_lon",user?.optDouble("lon",Double.NaN) ?: Double.NaN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(this, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder=Notification.Builder(this,"quake_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if(q.optBoolean("major",false)) "Earthquake warning" else "Earthquake detected")
                .setContentText("Magnitude %.1f · %.0f km away".format(q.optDouble("mag"),q.optDouble("dist")))
                .setPriority(if(q.optBoolean("major",false)) Notification.PRIORITY_MAX else Notification.PRIORITY_DEFAULT)
                .setCategory(if(q.optBoolean("major",false)) Notification.CATEGORY_ALARM else Notification.CATEGORY_EVENT)
                .setAutoCancel(!q.optBoolean("major",false)).setContentIntent(pending)
            if(q.optBoolean("major",false)) {
                builder.setFullScreenIntent(pending,true)
                startActivity(intent)
            }
            getSystemService(NotificationManager::class.java).notify(id,builder.build())
        }
    }
    private class MapMonitorView(context: Context): View(context) {
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val w=width.toFloat(); val h=height.toFloat()
            paint.color=Color.rgb(18,25,34); c.drawRect(0f,0f,w,h,paint)
            paint.color=Color.rgb(40,52,65); paint.strokeWidth=1f
            for(x in 0..width step 42) c.drawLine(x.toFloat(),0f,x.toFloat(),h,paint)
            for(y in 0..height step 42) c.drawLine(0f,y.toFloat(),w,y.toFloat(),paint)
            paint.style=android.graphics.Paint.Style.STROKE; paint.color=Color.rgb(255,69,58); paint.strokeWidth=2f
            val cx=w*.58f; val cy=h*.48f
            c.drawCircle(cx,cy,34f,paint); c.drawCircle(cx,cy,72f,paint); c.drawCircle(cx,cy,112f,paint)
            paint.style=android.graphics.Paint.Style.FILL; paint.color=Color.rgb(255,69,58); c.drawCircle(cx,cy,8f,paint)
            paint.color=Color.rgb(47,129,247); c.drawCircle(w*.35f,h*.64f,7f,paint)
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
