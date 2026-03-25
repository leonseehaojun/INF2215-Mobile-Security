package com.example.inf2215

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MediaHelper(private val context: Context) {

    companion object {
        private const val TAG = "MediaHelper"
        private const val DEFAULT_QUALITY = 90  // 0–100
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // Use a dedicated background thread for image processing
    private val backgroundThread = HandlerThread("ScreenshotThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private val mainHandler = Handler(Looper.getMainLooper())

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

    /**
     * Set the MediaProjection obtained from user permission
     */
    fun setMediaProjection(projection: MediaProjection?) {
        if (projection == null) {
            Log.w(TAG, "MediaProjection is null – cannot setup capture")
            return
        }
        this.mediaProjection = projection
        setupImageReader()
    }

    private fun setupImageReader() {
        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, backgroundHandler
            )
            Log.d(TAG, "VirtualDisplay created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup ImageReader / VirtualDisplay", e)
        }
    }

    /**
     * Capture current screen and save as PNG file
     *
     * @param quality Compression quality (0–100, default 90)
     * @param onResult callback with the saved file or null on failure
     */
    fun captureScreenshot(
        quality: Int = DEFAULT_QUALITY,
        onResult: (File?) -> Unit
    ) {
        if (imageReader == null || mediaProjection == null) {
            Log.w(TAG, "Cannot capture – ImageReader or MediaProjection not ready")
            mainHandler.post { onResult(null) }
            return
        }

        backgroundHandler.post {
            var image: Image? = null
            try {
                image = imageReader?.acquireLatestImage() ?: run {
                    Log.w(TAG, "No latest image available")
                    mainHandler.post { onResult(null) }
                    return@post
                }

                val bitmap = imageToBitmap(image) ?: run {
                    mainHandler.post { onResult(null) }
                    return@post
                }

                val file = saveBitmap(bitmap, quality)
                mainHandler.post { onResult(file) }

            } catch (e: Exception) {
                Log.e(TAG, "Screenshot capture failed", e)
                mainHandler.post { onResult(null) }
            } finally {
                image?.close()
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun saveBitmap(bitmap: Bitmap, quality: Int): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "screenshot_$timestamp.png"
        val dir = File(context.filesDir, "screenshots")

        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create screenshots directory")
            return null
        }

        val file = File(dir, filename)

        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
            }
            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            null
        }
    }

    /**
     * Clean up all resources
     */
    fun cleanup() {
        backgroundHandler.post {
            try {
                virtualDisplay?.release()
                imageReader?.close()
                mediaProjection?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            } finally {
                virtualDisplay = null
                imageReader = null
                mediaProjection = null
            }
        }

        // Quit the background thread safely
        backgroundThread.quitSafely()
    }
}