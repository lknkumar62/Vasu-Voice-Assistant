package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun analyzeImage(imageUri: Uri): ActionResult {
        return ActionResult.error("vision", "Vision temporarily disabled", "Library missing in build env")
    }

    fun scanQrCode(imageUri: Uri): ActionResult {
        return ActionResult.error("qr", "QR Scan temporarily disabled", "Library missing in build env")
    }
}
