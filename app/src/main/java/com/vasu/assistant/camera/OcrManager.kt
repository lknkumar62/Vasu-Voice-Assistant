package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun extractText(imageUri: Uri): ActionResult {
        return ActionResult.error("ocr", "OCR temporarily disabled", "Library missing in build env")
    }

    fun extractTextFromImageFile(filePath: String): ActionResult {
        return extractText(Uri.parse("file://$filePath"))
    }
}
