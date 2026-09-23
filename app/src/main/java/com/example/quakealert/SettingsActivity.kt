package com.example.quakealert

import android.app.*
import android.os.Bundle
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val p=getSharedPreferences("settings",MODE_PRIVATE)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28) }
        box.addView(TextView(this).apply { text="Server settings"; textSize=26f })
        box.addView(TextView(this).apply { text="Server URL (include https:// and port if needed)" })
        val url=EditText(this).apply { setText(p.getString("server_url","https://127.0.0.1")); inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI }
        val dark=CheckBox(this).apply { text="Use dark theme"; isChecked=p.getBoolean("dark",true) }
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
                        val connection=URL("$base/warning.wav").openConnection() as HttpURLConnection
                        connection.connectTimeout=8000
                        connection.readTimeout=15000
                        if(connection.responseCode !in 200..299) {
                            throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
                        }
                        val temporary=java.io.File(filesDir,"warning.wav.tmp")
                        connection.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                        connection.disconnect()
                        val target=java.io.File(filesDir,"warning.wav")
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
