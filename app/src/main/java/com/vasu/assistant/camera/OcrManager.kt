package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractText(imageUri: Uri): ActionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            var result: ActionResult = ActionResult.error("ocr", "Processing...", "Pending")
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    result = ActionResult.success(
                        "ocr",
                        "Text extracted (${visionText.textBlocks.size} blocks)",
                        mapOf("text" to visionText.text, "blocks" to visionText.textBlocks.size)
                    )
                }
                .addOnFailureListener { e ->
                    result = ActionResult.error("ocr", "OCR failed", e.message ?: "Unknown")
                }
            Thread.sleep(3000)
            result
        } catch (e: Exception) {
            ActionResult.error("ocr", "OCR failed", e.message ?: "Unknown")
        }
    }

    fun extractTextFromImageFile(filePath: String): ActionResult {
        return try {
            val uri = Uri.parse("file://$filePath")
            extractText(uri)
        } catch (e: Exception) {
            ActionResult.error("ocr", "Failed to read file", e.message ?: "Unknown")
        }
    }
}
