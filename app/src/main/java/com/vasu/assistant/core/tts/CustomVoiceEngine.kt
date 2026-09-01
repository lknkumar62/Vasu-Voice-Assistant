package com.vasu.assistant.core.tts

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceModelStatus(val description: String) {
    ACTIVE_CUSTOM_MODEL("Custom local VASU neural voice model active"),
    ACTIVE_CUSTOM_SAMPLES("Custom local VASU audio samples loaded"),
    FALLBACK_SYSTEM_TTS("System TTS engine active (No local model in assets/vasu_voice)"),
    ERROR("Error loading custom voice assets")
}

/**
 * CustomVoiceEngine - Manages local custom voice assets, neural voice models,
 * and high-fidelity audio samples placed in `app/src/main/assets/vasu_voice/`.
 */
@Singleton
class CustomVoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _status = MutableStateFlow(VoiceModelStatus.FALLBACK_SYSTEM_TTS)
    val status: StateFlow<VoiceModelStatus> = _status.asStateFlow()

    private val _customModelPath = MutableStateFlow<String?>(null)
    val customModelPath: StateFlow<String?> = _customModelPath.asStateFlow()

    private val customSampleMap = mutableMapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null

    init {
        detectCustomVoiceAssets()
    }

    /**
     * Inspect assets/vasu_voice and external directory for custom voice models and samples.
     */
    fun detectCustomVoiceAssets() {
        try {
            val assetList = runCatching { context.assets.list("vasu_voice") ?: emptyArray() }.getOrDefault(emptyArray())
            
            // Check for neural model files (.onnx, .tflite, .bin)
            val modelFile = assetList.firstOrNull { it.endsWith(".onnx") || it.endsWith(".tflite") || it == "model.bin" }
            
            // Check for custom audio samples in assets/vasu_voice/samples or assets/vasu_voice
            val sampleFiles = assetList.filter { it.endsWith(".wav") || it.endsWith(".mp3") || it.endsWith(".ogg") }

            // Also check app private files dir /vasu_voice/
            val internalVoiceDir = File(context.filesDir, "vasu_voice")
            val internalModel = if (internalVoiceDir.exists()) {
                internalVoiceDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") || it.name.endsWith(".tflite") }
            } else null

            if (modelFile != null || internalModel != null) {
                _customModelPath.value = internalModel?.absolutePath ?: "assets/vasu_voice/$modelFile"
                _status.value = VoiceModelStatus.ACTIVE_CUSTOM_MODEL
                Log.i(TAG, "Loaded custom voice neural model: ${_customModelPath.value}")
            } else if (sampleFiles.isNotEmpty()) {
                sampleFiles.forEach { file ->
                    val key = file.substringBeforeLast(".").lowercase().replace("_", " ").trim()
                    customSampleMap[key] = "vasu_voice/$file"
                }
                _status.value = VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES
                Log.i(TAG, "Loaded ${customSampleMap.size} custom voice audio samples.")
            } else {
                _status.value = VoiceModelStatus.FALLBACK_SYSTEM_TTS
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning for custom voice assets", e)
            _status.value = VoiceModelStatus.FALLBACK_SYSTEM_TTS
        }
    }

    /**
     * Checks if a custom phrase audio recording is available for the given text.
     */
    fun hasCustomSampleFor(text: String): Boolean {
        val normalized = normalizePhrase(text)
        return customSampleMap.containsKey(normalized)
    }

    /**
     * Plays a matched custom audio sample directly with MediaPlayer.
     */
    fun playCustomSample(text: String, onCompletion: (() -> Unit)? = null): Boolean {
        val normalized = normalizePhrase(text)
        val assetPath = customSampleMap[normalized] ?: return false

        return try {
            mediaPlayer?.release()
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setOnCompletionListener {
                    onCompletion?.invoke()
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play custom voice sample: $assetPath", e)
            false
        }
    }

    /**
     * Stop currently playing sample.
     */
    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    private fun normalizePhrase(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s\u0900-\u097F]"), "")
            .trim()
    }

    companion object {
        private const val TAG = "CustomVoiceEngine"
    }
}
