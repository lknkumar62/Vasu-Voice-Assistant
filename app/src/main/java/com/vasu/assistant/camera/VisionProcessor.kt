package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    private val barcodeScanner = BarcodeScanning.getClient()

    fun analyzeImage(imageUri: Uri): ActionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val latch = CountDownLatch(1)
            var result: ActionResult = ActionResult.error("vision", "Analysis timed out", "Timeout")
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val detected = labels.map { label ->
                        mapOf("text" to label.text, "confidence" to label.confidence, "index" to label.index)
                    }
                    result = ActionResult.success("vision", "Detected ${labels.size} labels", mapOf("labels" to detected))
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    result = ActionResult.error("vision", "Analysis failed", e.message ?: "Unknown")
                    latch.countDown()
                }
            latch.await(5, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            ActionResult.error("vision", "Analysis failed", e.message ?: "Unknown")
        }
    }

    fun scanQrCode(imageUri: Uri): ActionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val latch = CountDownLatch(1)
            var result: ActionResult = ActionResult.error("qr", "Scan timed out", "Timeout")
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        result = ActionResult.error("qr", "No QR/barcode found", "Nothing detected")
                    } else {
                        val found = barcodes.map { barcode ->
                            mapOf(
                                "format" to barcode.format,
                                "value" to (barcode.rawValue ?: "Unknown"),
                                "type" to barcode.valueType
                            )
                        }
                        result = ActionResult.success("qr", "Found ${barcodes.size} barcodes", mapOf("barcodes" to found))
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    result = ActionResult.error("qr", "Scan failed", e.message ?: "Unknown")
                    latch.countDown()
                }
            latch.await(5, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            ActionResult.error("qr", "QR scan failed", e.message ?: "Unknown")
        }
    }
}
