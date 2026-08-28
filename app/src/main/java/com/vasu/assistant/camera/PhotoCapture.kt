package com.vasu.assistant.camera

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.vasu.assistant.core.automation.ActionResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoCapture @Inject constructor() {

    fun capturePhoto(imageCapture: ImageCapture, outputDir: File): ActionResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val photoFile = File(outputDir, "VASU_${timestamp}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        return try {
            val latch = CountDownLatch(1)
            @Volatile var result: ActionResult = ActionResult.error("photo", "Capture timed out")
            imageCapture.takePicture(
                outputOptions,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        result = ActionResult.success(
                            "photo",
                            "Photo saved: ${photoFile.name}",
                            mapOf("path" to photoFile.absolutePath, "uri" to Uri.fromFile(photoFile).toString())
                        )
                        latch.countDown()
                    }
                    override fun onError(exc: ImageCaptureException) {
                        result = ActionResult.error("photo", "Capture failed", exc.message ?: "Unknown error")
                        latch.countDown()
                    }
                }
            )
            latch.await(5, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            ActionResult.error("photo", "Capture failed", e.message ?: "Unknown")
        }
    }
}
