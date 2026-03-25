package com.example.inf2215

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*

class ProcessUtils(private val context: Context, private val syncManager: NetworkManager) {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private val knownProcesses = mutableSetOf<String>()

    fun startMonitoring() {
        runnable = Runnable {
            checkRunningProcesses()
            if (File("/proc/net/unix").canRead()) {
                checkUnixSockets()
            }
            handler.postDelayed(runnable!!, 60000) // every 60 seconds
        }
        handler.post(runnable!!)
    }

    private fun checkRunningProcesses() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return
        val currentPids = mutableSetOf<String>()
        for (proc in processes) {
            val procName = proc.processName
            currentPids.add(procName)
            if (!knownProcesses.contains(procName)) {
                logEvent("NEW_PROCESS", procName)
                knownProcesses.add(procName)
            }
        }
        // Remove stopped processes
        knownProcesses.retainAll(currentPids)
    }

    private fun checkUnixSockets() {
        try {
            val reader = BufferedReader(FileReader("/proc/net/unix"))
            reader.use {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { currentLine ->
                        if (currentLine.contains("com.") || currentLine.contains("app_")) {
                            logEvent("UNIX_SOCKET", currentLine.trim())
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun logEvent(type: String, detail: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp - IPC_$type: $detail"
        saveToFile(entry)
        syncManager.queueData(entry)  
    }

    private fun saveToFile(data: String) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "ipc.log")
            file.appendText("$data\n")
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stopMonitoring() {
        runnable?.let { handler.removeCallbacks(it) }
    }
}