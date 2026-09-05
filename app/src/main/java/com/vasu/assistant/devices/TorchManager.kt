package com.vasu.assistant.devices

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorchManager - Native CameraManager flashlight controller for VASU Assistant.
 * Controls device flash instantly with < 50ms latency.
 */
@Singleton
class TorchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "TorchManager"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn: Boolean = false

    private fun getCameraId(): String? {
        val manager = cameraManager ?: return null
        return try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            manager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(tag, "Error querying camera IDs", e)
            null
        }
    }

    fun turnOn(): ActionResult {
        val cameraId = getCameraId() ?: return ActionResult.error("torch", "No flashlight hardware detected on this device", "HARDWARE_NOT_FOUND")
        return try {
            cameraManager?.setTorchMode(cameraId, true)
            isTorchOn = true
            ActionResult.success("torch_on", "Torch switched on")
        } catch (e: CameraAccessException) {
            Log.e(tag, "CameraAccessException turning torch on", e)
            ActionResult.error("torch", "Failed to access camera flashlight", e.message ?: "CameraAccessException")
        } catch (e: Exception) {
            Log.e(tag, "Exception turning torch on", e)
            ActionResult.error("torch", "Failed to switch torch on", e.message ?: "UnknownError")
        }
    }

    fun turnOff(): ActionResult {
        val cameraId = getCameraId() ?: return ActionResult.error("torch", "No flashlight hardware detected on this device", "HARDWARE_NOT_FOUND")
        return try {
            cameraManager?.setTorchMode(cameraId, false)
            isTorchOn = false
            ActionResult.success("torch_off", "Torch switched off")
        } catch (e: Exception) {
            Log.e(tag, "Exception turning torch off", e)
            ActionResult.error("torch", "Failed to switch torch off", e.message ?: "UnknownError")
        }
    }

    fun toggle(): ActionResult = if (isTorchOn) turnOff() else turnOn()
    fun isOn(): Boolean = isTorchOn
    fun isEnabled(): Boolean = isTorchOn
}

