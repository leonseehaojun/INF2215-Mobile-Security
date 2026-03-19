package com.example.inf2215

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotCapture(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val backgroundHandler = Handler(Looper.getMainLooper())

    private val width: Int
    private val height: Int
    private val density: Int

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
    }

    fun setMediaProjection(projection: MediaProjection?) {
        this.mediaProjection = projection
        setupImageReader()
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, backgroundHandler
        )
    }

    fun captureScreenshot(): File? {
        if (imageReader == null || mediaProjection == null) return null
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            saveImage(image)
        } finally {
            image.close()
        }
    }

    private fun saveImage(image: android.media.Image): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "screenshot_$timestamp.png"
        val dir = File(context.filesDir, "screenshots")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)

        // Convert Image to raw bytes (simplified – in practice convert to PNG)
        val planes = image.planes
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return try {
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun cleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}