package com.example.quakealert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var schedule: TextView
    private lateinit var location: TextView
    private lateinit var secondsInput: EditText
    private lateinit var testWarning: CheckBox
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
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        val title=TextView(this).apply { text="QUAKE ALERT"; textSize=28f; setTextColor(Color.rgb(215,38,61)); setTypeface(null,1) }
        status=TextView(this).apply { text="Connecting…"; textSize=18f; setPadding(0,24,0,20) }
        schedule=TextView(this).apply { textSize=16f; setTextColor(Color.rgb(180,120,20)); visibility=TextView.GONE }
        location=TextView(this).apply { text="Location: waiting for server"; textSize=16f }
        secondsInput=EditText(this).apply {
            hint="Seconds until test"; setText("10")
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
        }
        testWarning=CheckBox(this).apply { text="Test warning (major alert)"; isChecked=true }
        val scheduleButton=Button(this).apply {
            text="Schedule test"
            setOnClickListener { scheduleTest() }
        }
        val settings=Button(this).apply { text="Server settings"; setOnClickListener { startActivity(Intent(this@MainActivity,SettingsActivity::class.java)) } }
        root.addView(title); root.addView(status); root.addView(schedule); root.addView(location)
        root.addView(secondsInput); root.addView(testWarning); root.addView(scheduleButton)
        root.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { topMargin=24 }); setContentView(root)
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
            val c=URL("$base/api/state").openConnection() as HttpURLConnection
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
            location.text="Location: $userLat, $userLon"
        }
        val s=data.optJSONObject("schedule")
        val at=s?.optDouble("at",0.0) ?: 0.0
        val warns=s?.optBoolean("warn",false) ?: false
        schedule.visibility=if(at>0 && warns) TextView.VISIBLE else TextView.GONE
        if(at>0 && warns) schedule.text="Scheduled system test: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date((at*1000).toLong()))}"
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
    private fun scheduleTest() {
        val seconds=secondsInput.text.toString().toLongOrNull()
        if (seconds==null || seconds !in 1..86400) {
            secondsInput.error="Enter 1–86400 seconds"
            return
        }
        val base=prefs.getString("server_url","https://127.0.0.1")!!.trimEnd('/')
        val payload=JSONObject().apply {
            put("seconds",seconds)
            put("warn",testWarning.isChecked)
            put("lat",userLat)
            put("lon",userLon)
            put("mag",5.0)
            put("depth",10.0)
        }.toString()
        status.text="Scheduling test…"
        thread {
            try {
                val c=URL("$base/api/schedule-test").openConnection() as HttpURLConnection
                c.requestMethod="POST"; c.doOutput=true; c.connectTimeout=5000; c.readTimeout=5000
                c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val stream=if(c.responseCode in 200..299) c.inputStream else c.errorStream
                val response=stream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${c.responseCode}"
                c.disconnect()
                runOnUiThread { status.text="Schedule response: $response" }
            } catch (error: Exception) {
                runOnUiThread { status.text="Schedule failed: ${error.message ?: "connection error"}" }
            }
        }
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
