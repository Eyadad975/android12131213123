package com.example.quakealert

import android.app.*
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.net.HttpURLConnection
import kotlin.concurrent.thread

class SettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val p=getSharedPreferences("settings",MODE_PRIVATE)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24); setBackgroundColor(Color.rgb(12,16,22)) }
        box.addView(TextView(this).apply { text="Server settings"; textSize=30f; setTextColor(Color.WHITE); setTypeface(null,1) })
        box.addView(TextView(this).apply { text="Connect this device to your Quake Alert server."; textSize=15f; setTextColor(Color.rgb(170,180,195)); setPadding(0,8,0,26) })
        box.addView(TextView(this).apply { text="SERVER URL"; textSize=12f; setTextColor(Color.rgb(215,38,61)) })
        val url=EditText(this).apply { setText(p.getString("server_url","https://127.0.0.1:443")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI; setSingleLine(true); setPadding(18,0,18,0); background=GradientDrawable().apply { setColor(Color.rgb(27,34,44)); cornerRadius=14f } }
        val dark=CheckBox(this).apply { text="Use dark theme"; isChecked=true; visibility=View.GONE }
        val soundStatus=TextView(this).apply { text="The bundled warning sound is currently used." }
        val updateSound=Button(this).apply {
            text="Update warning sound from server"
            setOnClickListener {
                val base=url.text.toString().trim().trimEnd('/')
                if(!base.startsWith("http://")&&!base.startsWith("https://")) {
                    url.error="Enter a URL beginning with http:// or https://"
                    return@setOnClickListener
                }
                isEnabled=false
                soundStatus.text="Downloading warning sound..."
                thread {
                    try {
                        val connection=Network.open("$base/warning.wav")
                        connection.connectTimeout=8000
                        connection.readTimeout=15000
                        if(connection.responseCode !in 200..299) {
                            throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
                        }
                        val temporary=java.io.File(filesDir,"warning.wav.tmp")
                        connection.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                        connection.disconnect()
                        val target=java.io.File(filesDir,"warning.wav")
                        if(target.exists() && !target.delete()) throw IllegalStateException("Could not replace existing sound")
                        if(!temporary.renameTo(target)) throw IllegalStateException("Could not save sound")
                        runOnUiThread { soundStatus.text="Warning sound updated successfully."; isEnabled=true }
                    } catch(error: Exception) {
                        runOnUiThread { soundStatus.text="Sound update failed: ${error.message ?: "connection error"}"; isEnabled=true }
                    }
                }
            }
        }
        val save=Button(this).apply { text="Save"; setOnClickListener {
            val value=url.text.toString().trim().trimEnd('/')
            if(value.startsWith("http://")||value.startsWith("https://")) { p.edit().putString("server_url",value).putBoolean("dark",dark.isChecked).apply(); recreate(); finish() }
            else url.error="Enter a URL beginning with http:// or https://"
        }}
        box.addView(url); box.addView(dark); box.addView(updateSound); box.addView(soundStatus); box.addView(save); setContentView(box)
    }
}
