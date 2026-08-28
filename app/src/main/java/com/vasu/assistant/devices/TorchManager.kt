package com.vasu.assistant.devices

import android.content.Context
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var isOn = false

    fun turnOn(): ActionResult {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ActionResult.error("torch", "No camera found", "No camera")
            cameraManager.setTorchMode(cameraId, true)
            isOn = true
            ActionResult.success("torch", "Flashlight turned on")
        } catch (e: Exception) {
            ActionResult.error("torch", "Failed to turn on flashlight", e.message ?: "Unknown")
        }
    }

    fun turnOff(): ActionResult {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ActionResult.error("torch", "No camera found", "No camera")
            cameraManager.setTorchMode(cameraId, false)
            isOn = false
            ActionResult.success("torch", "Flashlight turned off")
        } catch (e: Exception) {
            ActionResult.error("torch", "Failed to turn off flashlight", e.message ?: "Unknown")
        }
    }

    fun toggle(): ActionResult = if (isOn) turnOff() else turnOn()
    fun isOn(): Boolean = isOn
}
