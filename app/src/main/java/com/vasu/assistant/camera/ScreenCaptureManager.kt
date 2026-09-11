package com.vasu.assistant.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCaptureManager @Inject constructor() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDpi = 400

    data class CaptureResult(
        val success: Boolean,
        val bitmap: Bitmap? = null,
        val filePath: String? = null,
        val error: String? = null
    )

    fun requestPermission(activity: Activity) {
        val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        activity.startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_SCREENSHOT
        )
    }

    fun handlePermissionResult(resultCode: Int, data: Intent?, context: Context): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Screen capture permission denied")
            return false
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDpi = metrics.densityDpi

        Log.d(TAG, "Screen capture permission granted: ${screenWidth}x${screenHeight}")
        return true
    }

    fun captureScreen(context: Context, callback: (CaptureResult) -> Unit) {
        val projection = mediaProjection
        if (projection == null) {
            callback(CaptureResult(false, error = "MediaProjection not initialized"))
            return
        }

        try {
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            )

            virtualDisplay = projection.createVirtualDisplay(
                "VASU-ScreenCapture",
                screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, handler
            )

            imageReader!!.setOnImageAvailableListener({ reader ->
                val image: Image? = reader.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    val filePath = saveBitmap(context, bitmap)
                    handler.post {
                        callback(CaptureResult(true, bitmap, filePath))
                    }
                }
            }, handler)

            // Timeout after 5 seconds
            handler.postDelayed({
                if (virtualDisplay != null) {
                    releaseCapture()
                    callback(CaptureResult(false, error = "Capture timeout"))
                }
            }, 5000)

        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}")
            callback(CaptureResult(false, error = e.message))
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): String {
        val file = File(context.filesDir, "screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun releaseCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            Log.d(TAG, "Screen capture released")
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_SCREENSHOT = 1001
    }
}
