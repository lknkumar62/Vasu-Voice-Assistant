package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.ocr.OcrBlock
import com.vasu.assistant.core.ocr.OcrBox
import com.vasu.assistant.core.ocr.OcrDocument
import com.vasu.assistant.core.ocr.OcrScript
import com.vasu.assistant.core.ocr.OcrText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OcrManager - on-device text recognition for images and screenshots.
 *
 * The Latin and Devanagari ML Kit recognizers both run over the same image and
 * [OcrText] merges their blocks, so mixed Hindi/English screens keep every
 * readable line. ML Kit exposes no per-character confidence, so results carry
 * text, lines and bounding boxes only - never invented scores.
 */
@Singleton
class OcrManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    }
    private val devanagariRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    /**
     * Reads all text in [imageUri]. Suspends while ML Kit runs; decoding and
     * recognition happen off the caller's thread.
     */
    suspend fun extractText(imageUri: Uri): ActionResult = withContext(Dispatchers.IO) {
        val imageResult = loadVisionImage(context, imageUri)
        val image = imageResult.getOrNull()
            ?: return@withContext visionImageError(ACTION, imageResult.exceptionOrNull())

        val latinResult = awaitVisionTask(latinRecognizer.process(image))
        val devanagariResult = awaitVisionTask(devanagariRecognizer.process(image))
        val latinText = latinResult.getOrNull()
        val devanagariText = devanagariResult.getOrNull()

        if (latinText == null && devanagariText == null) {
            val failure = latinResult.exceptionOrNull() ?: devanagariResult.exceptionOrNull()
            Log.e(TAG, "Text recognition failed", failure)
            return@withContext ActionResult.error(
                ACTION,
                "Text recognition failed",
                failure?.message ?: "ML Kit could not process the image"
            )
        }

        val document = OcrText.buildDocument(
            latin = latinText?.let { blocksOf(it, OcrScript.LATIN) }.orEmpty(),
            devanagari = devanagariText?.let { blocksOf(it, OcrScript.DEVANAGARI) }.orEmpty()
        )
        if (document.fullText.isBlank()) {
            return@withContext ActionResult.error(
                ACTION, "No text found", "No readable text in this image"
            )
        }
        Log.i(TAG, "OCR blocks=${document.blocks.size} scripts=${document.scripts.size}")
        ActionResult.success(ACTION, OcrText.formatSpokenText(document.fullText), documentData(document))
    }

    suspend fun extractTextFromImageFile(filePath: String): ActionResult =
        extractText(Uri.parse("file://$filePath"))

    private fun blocksOf(text: Text, source: OcrScript): List<OcrBlock> =
        text.textBlocks.mapNotNull { block ->
            val body = block.text.trim()
            if (body.isEmpty()) return@mapNotNull null
            val rect = block.boundingBox
            OcrBlock(
                text = body,
                lines = block.lines.map { it.text.trim() }.filter { it.isNotBlank() },
                box = if (rect == null) OcrBox.ZERO else OcrBox(rect.left, rect.top, rect.right, rect.bottom),
                script = OcrText.scriptOf(body),
                source = source
            )
        }

    private fun documentData(document: OcrDocument): Map<String, Any> = mapOf(
        "text" to document.fullText,
        "blockCount" to document.blocks.size,
        "scripts" to document.scripts.map { it.name.lowercase() },
        "blocks" to document.blocks.map { block ->
            mapOf(
                "text" to block.text,
                "left" to block.box.left,
                "top" to block.box.top,
                "right" to block.box.right,
                "bottom" to block.box.bottom,
                "script" to block.script.name.lowercase()
            )
        }
    )

    companion object {
        private const val TAG = "OcrManager"
        private const val ACTION = "ocr_extract"
    }
}
