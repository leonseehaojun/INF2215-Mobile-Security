package com.example.inf2215

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*

class RunningService : Service() {

    // Global State
    companion object {
        var isServiceRunning = mutableStateOf(false)
        var pathPoints = mutableStateOf<List<LatLng>>(emptyList())
        var distanceKm = mutableStateOf(0.0)
        var seconds = mutableStateOf(0L)

        // Actions
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define Location Logic
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    val currentList = pathPoints.value

                    if (currentList.isNotEmpty()) {
                        val lastPoint = currentList.last()
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            lastPoint.latitude, lastPoint.longitude,
                            newLatLng.latitude, newLatLng.longitude,
                            results
                        )
                        val distMeters = results[0]

                        // Filter small movements (>1m)
                        if (distMeters > 1.0) {
                            distanceKm.value += (distMeters / 1000.0)
                            pathPoints.value = currentList + newLatLng
                        }
                    } else {
                        pathPoints.value = listOf(newLatLng)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (isServiceRunning.value) return
        isServiceRunning.value = true

        // Start Foreground Notification
        startForegroundServiceNotification()

        // Start Timer
        serviceScope.launch {
            while (isServiceRunning.value) {
                delay(1000)
                seconds.value += 1
                updateNotification(formatTime(seconds.value), String.format("%.2f km", distanceKm.value))
            }
        }

        // Start Location Updates
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(3f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun pause() {
        isServiceRunning.value = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun stop() {
        pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "running_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Running Tracker", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Run Tracker Active")
            .setContentText("Tracking your run...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun updateNotification(time: String, dist: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "running_channel")
            .setContentTitle("Run Active")
            .setContentText("$time • $dist")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        manager.notify(1, notification)
    }

    // Helper to format time inside service
    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}