package com.vasu.assistant.core.commands

import android.content.Context
import com.vasu.assistant.calls.CallManager
import com.vasu.assistant.camera.CameraManager
import com.vasu.assistant.core.device.DeviceControlManager
import com.vasu.assistant.core.messaging.MessagingManager
import com.vasu.assistant.core.file.FileManager
import com.vasu.assistant.maps.VasuLocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callManager: CallManager,
    private val messagingManager: MessagingManager,
    private val deviceControlManager: DeviceControlManager,
    private val fileManager: FileManager,
    private val locationManager: VasuLocationManager,
    private val cameraManager: CameraManager
) {
    fun parseCommand(text: String): ParsedCommand {
        val lowerText = text.lowercase()

        // Call intents
        if (lowerText.contains("call") || lowerText.contains("phone")) {
            val contactName = extractContact(text)
            return ParsedCommand.Call(contactName)
        }

        // Message intents
        if (lowerText.contains("message") || lowerText.contains("sms") || lowerText.contains("whatsapp")) {
            val contactName = extractContact(text)
            val message = extractMessage(text)
            return ParsedCommand.Message(contactName, message)
        }

        // Device intents
        if (lowerText.contains("torch") || lowerText.contains("flashlight")) {
            return ParsedCommand.DeviceControl("torch", "toggle")
        }

        if (lowerText.contains("volume")) {
            return ParsedCommand.DeviceControl("volume", extractVolumeLevel(text))
        }

        // Location intents
        if (lowerText.contains("location") || lowerText.contains("map")) {
            return ParsedCommand.Location(extractLocation(text))
        }

        // File intents
        if (lowerText.contains("file") || lowerText.contains("folder")) {
            return ParsedCommand.File(extractFilePath(text))
        }

        // Camera intents
        if (lowerText.contains("camera") || lowerText.contains("photo") || lowerText.contains("picture")) {
            return ParsedCommand.Camera("photo")
        }

        return ParsedCommand.Unknown(text)
    }

    private fun extractContact(text: String): String {
        return text.substringAfter("call").substringAfter("message").trim().split(" ").first()
    }

    private fun extractMessage(text: String): String {
        val messagePart = text.substringAfter("message").substringAfter("say").trim()
        return messagePart.substringBefore("to").trim()
    }

    private fun extractVolumeLevel(text: String): String {
        return when {
            text.contains("max") || text.contains("full") -> "15"
            text.contains("min") || text.contains("zero") || text.contains("mute") -> "0"
            text.contains("half") || text.contains("medium") -> "7"
            else -> "10"
        }
    }

    private fun extractLocation(text: String): String {
        return text.substringAfter("location").substringAfter("map").trim()
    }

    private fun extractFilePath(text: String): String {
        return text.substringAfter("file").substringAfter("folder").trim()
    }
}

sealed class ParsedCommand {
    data class Call(val contactName: String) : ParsedCommand()
    data class Message(val contactName: String, val message: String) : ParsedCommand()
    data class DeviceControl(val device: String, val action: String) : ParsedCommand()
    data class Location(val query: String) : ParsedCommand()
    data class File(val path: String) : ParsedCommand()
    data class Camera(val type: String) : ParsedCommand()
    data class Unknown(val text: String) : ParsedCommand()
}
