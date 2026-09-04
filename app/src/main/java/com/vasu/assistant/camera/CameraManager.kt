package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VasuCameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoCapture: PhotoCapture,
    private val videoRecorder: VideoRecorder,
    private val ocrManager: OcrManager,
    private val visionProcessor: VisionProcessor
) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashEnabled = false

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = false
    ): ActionResult {
        return try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()
            activeCameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(lifecycleOwner, activeCameraSelector, preview, imageCapture)

            val camName = if (useFrontCamera) "Front" else "Back"
            ActionResult.success("camera_init", "Camera initialized ($camName)")
        } catch (e: Exception) {
            ActionResult.error("camera_init", "Camera init failed", e.message ?: "Unknown")
        }
    }

    fun takePhoto(): ActionResult {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.error(
                "photo",
                "Camera permission required. Please grant camera access in Settings.",
                "CAMERA_PERMISSION_REQUIRED"
            )
        }

        var capture = imageCapture
        if (capture == null) {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val provider = cameraProviderFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
                cameraProvider = provider

                val newCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    androidx.lifecycle.ProcessLifecycleOwner.get(),
                    activeCameraSelector,
                    newCapture
                )
                imageCapture = newCapture
                capture = newCapture
            } catch (e: Exception) {
                android.util.Log.e("CameraManager", "Failed to initialize CameraX for photo", e)
                return ActionResult.error("photo", "Camera initialization failed: ${e.message}", "CAMERA_NOT_READY")
            }
        }

        return photoCapture.capturePhoto(capture, getOutputDir())
    }

    fun startRecording(): ActionResult {
        return videoRecorder.startRecording(getOutputDir())
    }

    fun stopRecording(): ActionResult {
        return videoRecorder.stopRecording()
    }

    fun toggleFlash(): ActionResult {
        flashEnabled = !flashEnabled
        imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        return ActionResult.success("flash", if (flashEnabled) "Flash ON" else "Flash OFF")
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView): ActionResult {
        val newFront = activeCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
        return initializeCamera(lifecycleOwner, previewView, newFront)
    }

    fun analyzeImage(imageUri: Uri): ActionResult = visionProcessor.analyzeImage(imageUri)
    fun scanQrCode(imageUri: Uri): ActionResult = visionProcessor.scanQrCode(imageUri)
    fun extractText(imageUri: Uri): ActionResult = ocrManager.extractText(imageUri)

    fun getPhotoGallery(): ActionResult {
        val dir = getOutputDir()
        val photos = dir.listFiles()?.filter { it.isFile }?.map { f ->
            mapOf("name" to f.name, "path" to f.absolutePath, "size" to f.length(), "date" to f.lastModified())
        }?.sortedByDescending { it["date"] as Long } ?: emptyList()
        return ActionResult.success("gallery", "Found ${photos.size} photos", mapOf("photos" to photos))
    }

    private fun getOutputDir(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Vasu")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
