package com.vasu.assistant.devicecontrol

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log

/**
 * Android Native Device Control Manager for VASU Assistant.
 * Handles Torch, Volume, Camera, Alarms, Phone Calls, and WhatsApp intents.
 */
class DeviceControlManager(private val context: Context) {

    private val tag = "DeviceControlManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn = false

    fun toggleTorch(enable: Boolean): String {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return "Torch hardware not found"
            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            if (enable) "Torch chalu kar di gayi hai!" else "Torch band kar di gayi hai!"
        } catch (e: Exception) {
            Log.e(tag, "Torch error", e)
            "Torch activate nahi ho payi."
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Camera open kar diya gaya hai!"
        } catch (e: Exception) {
            Log.e(tag, "Camera error", e)
            "Camera kholne mein samasya aayi."
        }
    }

    fun setVolume(levelPercent: Int): String {
        return try {
            audioManager?.let { am ->
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = ((levelPercent / 100f) * max).toInt().coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                "Volume $levelPercent percent set kar diya gaya hai!"
            } ?: "Volume control uplabdh nahi hai."
        } catch (e: Exception) {
            Log.e(tag, "Volume error", e)
            "Volume badla nahi jaa saka."
        }
    }

    fun setAlarm(hour: Int, minutes: Int, message: String = "VASU Alarm"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm $hour:$minutes baje ke liye set kar diya gaya hai!"
        } catch (e: Exception) {
            Log.e(tag, "Alarm error", e)
            "Alarm set nahi ho paya."
        }
    }

    fun makeCall(phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "$phoneNumber par call dial kiya jaa raha hai."
        } catch (e: Exception) {
            Log.e(tag, "Call error", e)
            "Call dial nahi ho payi."
        }
    }

    fun openWhatsApp(message: String = ""): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "WhatsApp open kiya jaa raha hai."
        } catch (e: Exception) {
            Log.e(tag, "WhatsApp error", e)
            "WhatsApp launch nahi ho saka."
        }
    }
}
