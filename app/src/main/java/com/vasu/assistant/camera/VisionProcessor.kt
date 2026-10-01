package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.ocr.DetectedItem
import com.vasu.assistant.core.ocr.LabelScore
import com.vasu.assistant.core.ocr.VisionText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VisionProcessor - on-device object detection and QR reading for photos and
 * screenshots. Detected objects are returned with their real ML Kit labels,
 * confidences and pixel bounds; a silent image fails with an explicit error.
 */
@Singleton
class VisionProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val objectDetector: ObjectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
    }

    private val qrScanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    /** Describes the objects detected in [imageUri]. Suspends while ML Kit runs. */
    suspend fun analyzeImage(imageUri: Uri): ActionResult = withContext(Dispatchers.IO) {
        val imageResult = loadVisionImage(context, imageUri)
        val image = imageResult.getOrNull()
            ?: return@withContext visionImageError(ACTION_VISION, imageResult.exceptionOrNull())

        val detection = awaitVisionTask(objectDetector.process(image))
        val objects = detection.getOrNull()
            ?: return@withContext ActionResult.error(
                ACTION_VISION,
                "Object detection failed",
                detection.exceptionOrNull()?.message ?: "ML Kit could not process the image"
            )
        if (objects.isEmpty()) {
            return@withContext ActionResult.error(
                ACTION_VISION, "Nothing detected", "No recognizable objects in this image"
            )
        }

        val items = objects.map { detected ->
            DetectedItem(
                labels = detected.labels
                    .map { LabelScore(it.text, it.confidence) }
                    .sortedByDescending { it.confidence },
                left = detected.boundingBox.left,
                top = detected.boundingBox.top,
                right = detected.boundingBox.right,
                bottom = detected.boundingBox.bottom
            )
        }
        val description = VisionText.describeItems(items)
            ?: "Detected ${items.size} objects"
        Log.i(TAG, "Detected ${items.size} object(s)")
        ActionResult.success(ACTION_VISION, description, visionData(items))
    }

    /** Reads the QR code in [imageUri] and returns its raw payload. */
    suspend fun scanQrCode(imageUri: Uri): ActionResult = withContext(Dispatchers.IO) {
        val imageResult = loadVisionImage(context, imageUri)
        val image = imageResult.getOrNull()
            ?: return@withContext visionImageError(ACTION_QR, imageResult.exceptionOrNull())

        val scan = awaitVisionTask(qrScanner.process(image))
        val barcodes = scan.getOrNull()
            ?: return@withContext ActionResult.error(
                ACTION_QR,
                "QR scan failed",
                scan.exceptionOrNull()?.message ?: "ML Kit could not process the image"
            )

        val values = barcodes.mapNotNull { it.rawValue?.trim() }.filter { it.isNotEmpty() }
        val summary = VisionText.qrSummary(values)
            ?: return@withContext ActionResult.error(
                ACTION_QR, "No QR code found", "No readable QR code in this image"
            )
        ActionResult.success(
            ACTION_QR, summary,
            mapOf("value" to values.first(), "count" to values.size, "values" to values)
        )
    }

    private fun visionData(items: List<DetectedItem>): Map<String, Any> = mapOf(
        "objectCount" to items.size,
        "objects" to items.map { item ->
            mapOf(
                "labels" to item.labels.map {
                    mapOf("label" to it.label, "confidence" to it.confidence)
                },
                "left" to item.left,
                "top" to item.top,
                "right" to item.right,
                "bottom" to item.bottom
            )
        }
    )

    companion object {
        private const val TAG = "VisionProcessor"
        private const val ACTION_VISION = "describe_image"
        private const val ACTION_QR = "scan_qr"
    }
}
