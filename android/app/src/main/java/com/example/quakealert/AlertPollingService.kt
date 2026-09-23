package com.example.quakealert

import android.app.*
import android.content.Intent
import android.os.*
import org.json.JSONObject
import kotlin.concurrent.thread

class AlertPollingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val poll = object : Runnable {
        override fun run() {
            thread { fetchState() }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(42, Notification.Builder(this, "quake_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Quake Alert is monitoring")
            .setContentText("Monitoring the configured earthquake server")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build())
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    private fun fetchState() {
        try {
            val base = prefs.getString("server_url", "https://127.0.0.1")!!.trimEnd('/')
            val connection = Network.open("$base/api/state")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            handleState(data)
        } catch (_: Exception) {
            // The next poll retries; a transient server/network failure must not stop monitoring.
        }
    }

    private fun handleState(data: JSONObject) {
        val alert = data.optJSONObject("alert")
        val quake = data.optJSONObject("quake")
        val id = alert?.optInt("id", 0) ?: 0
        val previousId = prefs.getInt("service_alert_id", 0)
        if (quake != null && id > previousId) {
            prefs.edit().putInt("service_alert_id", id).apply()
            postEarthquake(id, quake, data.optJSONObject("user"))
        }

        val custom = data.optJSONObject("notification")
        val customId = custom?.optInt("id", 0) ?: 0
        if (custom != null && customId > prefs.getInt("service_notification_id", 0)) {
            prefs.edit().putInt("service_notification_id", customId).apply()
            postInfo(custom.optString("title", "Quake Alert"), custom.optString("body"))
        }
    }

    private fun postEarthquake(id: Int, quake: JSONObject, user: JSONObject?) {
        val intent = Intent(this, AlertActivity::class.java)
            .putExtra("quake", quake.toString())
            .putExtra("user_lat", user?.optDouble("lat", Double.NaN) ?: Double.NaN)
            .putExtra("user_lon", user?.optDouble("lon", Double.NaN) ?: Double.NaN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val major = quake.optBoolean("major", false)
        val pending = PendingIntent.getActivity(
            this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, "quake_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (major) "Earthquake warning" else "Earthquake detected")
            .setContentText("Magnitude %.1f · %.0f km away".format(quake.optDouble("mag"), quake.optDouble("dist")))
            .setPriority(if (major) Notification.PRIORITY_MAX else Notification.PRIORITY_DEFAULT)
            .setCategory(if (major) Notification.CATEGORY_ALARM else Notification.CATEGORY_EVENT)
            .setAutoCancel(!major)
            .setContentIntent(pending)
        if (major) {
            builder.setFullScreenIntent(pending, true)
            builder.setChannelId("quake_major")
        }
        getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    private fun postInfo(title: String, body: String) {
        getSystemService(NotificationManager::class.java).notify(
            1000 + prefs.getInt("service_notification_id", 0),
            Notification.Builder(this, "quake_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body)
                .setPriority(Notification.PRIORITY_DEFAULT).setAutoCancel(true).build()
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("quake_alerts", "Earthquake alerts", NotificationManager.IMPORTANCE_HIGH)
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("quake_major", "Major earthquake warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                }
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
