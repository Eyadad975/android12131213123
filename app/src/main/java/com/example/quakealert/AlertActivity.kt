package com.example.quakealert

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
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
    private val handler=Handler(Looper.getMainLooper())
    private val vibrate=object:Runnable { override fun run(){ vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,700,300),0)); handler.postDelayed(this,1500) } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val q=JSONObject(intent.getStringExtra("quake") ?: "{}")
        val userLat=intent.getDoubleExtra("user_lat",Double.NaN)
        val userLon=intent.getDoubleExtra("user_lon",Double.NaN)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(30,30,30,30); setBackgroundColor(Color.rgb(122,0,18)) }
        root.addView(TextView(this).apply { text="EARTHQUAKE WARNING"; textSize=30f; setTextColor(Color.WHITE); gravity=Gravity.CENTER })
        val userText=if(userLat.isFinite() && userLon.isFinite()) "\nUser: %.5f, %.5f".format(userLat,userLon) else ""
        root.addView(TextView(this).apply { text="Magnitude %.1f\n%.0f km from your location\nDepth %.0f km\nQuake: %.5f, %.5f%s".format(q.optDouble("mag"),q.optDouble("dist"),q.optDouble("depth"),q.optDouble("lat"),q.optDouble("lon"),userText); textSize=18f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,40,0,40) })
        root.addView(TextView(this).apply { text="DROP, COVER, HOLD ON"; textSize=28f; setTextColor(Color.WHITE); gravity=Gravity.CENTER })
        val dismiss=Button(this).apply { text="Dismiss"; setOnClickListener { finish() } }
        root.addView(dismiss,LinearLayout.LayoutParams(-1,-2).apply { topMargin=50 }); setContentView(root)
        startWarning(q)
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
        }
        val nm=getSystemService(NotificationManager::class.java)
        nm.notify(7,Notification.Builder(this,"quake_alerts").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Earthquake warning").setContentText("Magnitude %.1f near your location".format(q.optDouble("mag"))).setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_ALARM).build())
    }
    override fun onDestroy() {
        handler.removeCallbacks(vibrate); vibrator?.cancel(); player?.let { runCatching { it.stop(); it.release() } }
        cameraId?.let { runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(it,false) } }
        getSystemService(NotificationManager::class.java).cancel(7); super.onDestroy()
    }
}
