package com.example.inf2215

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class EngagementHelper(private val context: Context, private val syncManager: NetworkManager) {

    private val tag = "EngagementHelper"
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null

    var currentForegroundApp = ""
        private set

    private var isTracking = false

    fun startTracking() {
        if (!hasPermission()) {
            // Permission already requested in MainActivity; just log
            return
        }

        checkRunnable = Runnable {
            updateForegroundApp()
            handler.postDelayed(checkRunnable!!, 60000) // every 60 seconds
        }
        handler.post(checkRunnable!!)
        isTracking = true
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateForegroundApp() {
        try {
            val calendar = Calendar.getInstance()
            // FIX - calculate start first, then end:
            calendar.add(Calendar.MINUTE, -1)
            val startFixed = calendar.timeInMillis
            val endFixed = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startFixed, endFixed
            )
            if (stats.isNullOrEmpty()) return

            val sortedMap = TreeMap<Long, String>()
            for (us in stats) {
                sortedMap[us.lastTimeUsed] = us.packageName
            }

            val topPackage = sortedMap[sortedMap.lastKey()]
            if (topPackage != null && topPackage != currentForegroundApp) {
                currentForegroundApp = topPackage
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "$timestamp - APP_USAGE: $currentForegroundApp"
                saveToFile(entry)
                syncManager.queueData(entry)  
            }
        } catch (e: SecurityException) {
            // Permission revoked
        }
    }

    private fun saveToFile(data: String) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "app_usage.log")
            FileOutputStream(file, true).bufferedWriter().use {
                it.write(data)
                it.newLine()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stopTracking() {
        checkRunnable?.let { handler.removeCallbacks(it) }  // safe call instead of !!
        isTracking = false
    }
}