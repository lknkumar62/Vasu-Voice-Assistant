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
    ACTIVE_CUSTOM_MODEL("Vasu voice model active"),
    ACTIVE_CUSTOM_SAMPLES("Vasu voices loaded (43 voices)"),
    FALLBACK_SYSTEM_TTS("System TTS fallback (no Vasu voice found)"),
    ERROR("Error loading Vasu voice assets")
}

/**
 * CustomVoiceEngine - Manages Vasu's 43 voices (Maya parity: friday/maya/venom) + Vasu local samples.
 * Loads from assets/voices (friday/maya/venom — Maya parity) and vasu_voice.
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
     * Inspect all voice assets: Vasu's 43 voices (Maya parity) + Vasu local samples.
     * Vasu voices are in assets/voices (friday/maya/venom — Maya parity), Vasu samples in vasu_voice.
     */
    fun detectCustomVoiceAssets() {
        try {
            // 1. Scan Vasu voices (43 ogg files — Maya parity: friday/maya/venom)
            val mayaVoices = runCatching { context.assets.list("voices") ?: emptyArray() }.getOrDefault(emptyArray())
                .filter { it.endsWith(".ogg") || it.endsWith(".wav") || it.endsWith(".mp3") }
            
            // 2. Scan Vasu custom samples
            val vasuList = runCatching { context.assets.list("vasu_voice") ?: emptyArray() }.getOrDefault(emptyArray())
            val modelFile = vasuList.firstOrNull { it.endsWith(".onnx") || it.endsWith(".tflite") || it == "model.bin" }
            val vasuSamples = vasuList.filter { it.endsWith(".wav") || it.endsWith(".mp3") || it.endsWith(".ogg") }

            // 3. Check internal dir
            val internalVoiceDir = File(context.filesDir, "vasu_voice")
            val internalModel = if (internalVoiceDir.exists()) {
                internalVoiceDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") || it.name.endsWith(".tflite") }
            } else null

            var loadedCount = 0

            // Load Vasu voices first — map each voice name to asset path (Maya parity)
            mayaVoices.forEach { file ->
                val key = file.substringBeforeLast(".").lowercase().replace("_", " ").trim() // e.g. maya kore -> maya kore
                customSampleMap[key] = "voices/$file"
                // Also map short names: kore, aoede etc. point to default vasu variant (Maya parity)
                val shortName = file.substringAfter("_").substringBeforeLast(".").lowercase()
                customSampleMap.putIfAbsent(shortName, "voices/$file")
                // Common greetings point to default Vasu voice
                when (key) {
                    "maya kore", "friday kore", "maya erinome" -> {
                        customSampleMap.putIfAbsent("hello", "voices/$file")
                        customSampleMap.putIfAbsent("hello vasu", "voices/$file")
                        customSampleMap.putIfAbsent("hi vasu", "voices/$file")
                        customSampleMap.putIfAbsent("greeting", "voices/$file")
                    }
                }
                loadedCount++
            }

            // Load Vasu samples
            vasuSamples.forEach { file ->
                val key = file.substringBeforeLast(".").lowercase().replace("_", " ").trim()
                customSampleMap[key] = "vasu_voice/$file"
                loadedCount++
            }

            if (modelFile != null || internalModel != null) {
                _customModelPath.value = internalModel?.absolutePath ?: "assets/vasu_voice/$modelFile"
                _status.value = VoiceModelStatus.ACTIVE_CUSTOM_MODEL
                Log.i(TAG, "Loaded Vasu neural model: ${_customModelPath.value} with $loadedCount voices")
            } else if (loadedCount > 0) {
                _status.value = VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES
                Log.i(TAG, "Loaded $loadedCount Vasu voice samples (Vasu: ${vasuSamples.size}, legacy: ${mayaVoices.size})")
            } else {
                _status.value = VoiceModelStatus.FALLBACK_SYSTEM_TTS
                Log.w(TAG, "No Vasu voice assets found — will use Gemini only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning Vasu voice assets", e)
            _status.value = VoiceModelStatus.ERROR
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
