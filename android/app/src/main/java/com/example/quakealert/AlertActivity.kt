package com.example.quakealert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.*
import android.view.*
import android.widget.*
import org.json.JSONObject

class AlertActivity : Activity() {
    private var player: MediaPlayer?=null
    private var vibrator: Vibrator?=null
    private var cameraId: String?=null
    private var torchOn=false
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private var seconds=10
    private var countdownTask: Runnable?=null
    private val vibrate=object:Runnable { override fun run(){ vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,700,300),0)); handler.postDelayed(this,1500) } }
    private val torchFlash=object:Runnable { override fun run() {
        cameraId?.let { torchOn=!torchOn; runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(it,torchOn) } }
        handler.postDelayed(this,500)
    } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val q=JSONObject(intent.getStringExtra("quake") ?: "{}")
        val userLat=intent.getDoubleExtra("user_lat",Double.NaN)
        val userLon=intent.getDoubleExtra("user_lon",Double.NaN)
        seconds=q.optInt("countdown", 10).coerceIn(0, 99)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(26,20,26,18); background=GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(122,0,18),Color.rgb(207,25,48))) }
        root.addView(TextView(this).apply { text="EARTHQUAKE WARNING"; textSize=27f; letterSpacing=.06f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setTypeface(null,1) })
        countdown=TextView(this).apply { text="$seconds"; textSize=86f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setTypeface(null,1); setPadding(0,20,0,0) }
        root.addView(countdown)
        root.addView(TextView(this).apply { text="SECONDS TO IMPACT"; textSize=13f; letterSpacing=.15f; setTextColor(Color.rgb(255,220,220)); gravity=Gravity.CENTER })
        val userText=if(userLat.isFinite() && userLon.isFinite()) "\nUser: %.5f, %.5f".format(userLat,userLon) else ""
        root.addView(TextView(this).apply { text="Magnitude %.1f  |  %.0f km away\nDepth %.0f km\nEpicenter %.5f°, %.5f°%s".format(q.optDouble("mag"),q.optDouble("dist"),q.optDouble("depth"),q.optDouble("lat"),q.optDouble("lon"),userText); textSize=17f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,22,0,22) })
        root.addView(TextView(this).apply { text="DROP, COVER, HOLD ON"; textSize=27f; setTypeface(null,1); setTextColor(Color.WHITE); gravity=Gravity.CENTER })
        root.addView(AlertMapView(this), LinearLayout.LayoutParams(-1, 190).apply { topMargin=8 })
        val dismiss=Button(this).apply { text="Dismiss"; setTextColor(Color.WHITE); background=GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(1,Color.argb(190,255,255,255)); cornerRadius=8f }; setOnClickListener { finish() } }
        root.addView(dismiss,LinearLayout.LayoutParams(-1,-2).apply { topMargin=50 }); setContentView(root)
        countdownTask=object : Runnable {
            override fun run() {
                if (seconds > 0) { seconds--; countdown.text="$seconds"; handler.postDelayed(this, 1000) }
            }
        }
        handler.post(countdownTask!!)
        startWarning(q)
    }
    private class AlertMapView(context: Context): View(context) {
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val w=width.toFloat(); val h=height.toFloat()
            paint.style=android.graphics.Paint.Style.FILL; paint.color=Color.argb(45,0,0,0); c.drawRoundRect(0f,0f,w,h,10f,10f,paint)
            paint.color=Color.argb(70,255,255,255); paint.strokeWidth=1f
            for (x in 0..width step 36) c.drawLine(x.toFloat(),0f,x.toFloat(),h,paint)
            for (y in 0..height step 36) c.drawLine(0f,y.toFloat(),w,y.toFloat(),paint)
            paint.style=android.graphics.Paint.Style.STROKE; paint.color=Color.WHITE; paint.strokeWidth=2f
            val cx=w*.52f; val cy=h*.5f
            c.drawCircle(cx,cy,30f,paint); c.drawCircle(cx,cy,65f,paint)
            paint.style=android.graphics.Paint.Style.FILL; paint.color=Color.WHITE; c.drawCircle(cx,cy,7f,paint)
        }
    }
    private fun startWarning(q: JSONObject) {
        player=MediaPlayer().also {
            try {
                val downloaded=java.io.File(filesDir,"warning.wav")
                if(downloaded.isFile) it.setDataSource(downloaded.absolutePath) else it.setDataSource(this,android.net.Uri.parse("android.resource://$packageName/${R.raw.warning}"))
                it.isLooping=true
                it.prepare()
                it.start()
            } catch(error: Exception) {
                runCatching { it.release() }
                player=MediaPlayer.create(this,R.raw.warning).also { fallback -> fallback.isLooping=true; fallback.start() }
            }
        }
        vibrator=getSystemService(VIBRATOR_SERVICE) as Vibrator; handler.post(vibrate)
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            val cm=getSystemService(CAMERA_SERVICE) as CameraManager
            cameraId=cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
            cameraId?.let { runCatching { cm.setTorchMode(it,true) } }
            handler.post(torchFlash)
        }
        val nm=getSystemService(NotificationManager::class.java)
        nm.notify(7,Notification.Builder(this,"quake_alerts").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Earthquake warning").setContentText("Magnitude %.1f near your location".format(q.optDouble("mag"))).setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_ALARM).build())
    }
    override fun onDestroy() {
        handler.removeCallbacks(vibrate); handler.removeCallbacks(torchFlash); countdownTask?.let(handler::removeCallbacks); vibrator?.cancel(); player?.let { runCatching { it.stop(); it.release() } }
        cameraId?.let { runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(it,false) } }
        getSystemService(NotificationManager::class.java).cancel(7); super.onDestroy()
    }
}
