package com.vasu.assistant.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NativeMicrophoneRecorder - Continuously streams 16-bit PCM / 16 kHz / mono audio
 * from the device microphone for Gemini Live realtime input.
 */
@Singleton
class NativeMicrophoneRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    companion object {
        private const val TAG = "NativeMicRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Buffer ~100ms chunks (1600 samples = 3200 bytes)
        private const val CHUNK_SIZE_BYTES = 3200
    }

    /**
     * Start capturing microphone audio and streaming PCM bytes to the chunk consumer.
     */
    fun startStreaming(onAudioChunk: (ByteArray) -> Unit): Boolean {
        if (_isRecording.value) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Microphone permission not granted")
            return false
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid minimum buffer size for AudioRecord: $minBuf")
            return false
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(minBuf * 2, CHUNK_SIZE_BYTES * 4)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate AudioRecord", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return false
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord recording", e)
            record.release()
            return false
        }

        audioRecord = record
        _isRecording.value = true
        Log.d(TAG, "[VASU] Microphone started")

        recordingJob = recordScope.launch {
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            try {
                while (isActive && _isRecording.value) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        calculateRms(chunk)
                        onAudioChunk(chunk)
                    }
                }
            } catch (e: CancellationException) {
                // Normal exit
            } catch (e: Exception) {
                Log.e(TAG, "Error in microphone recording loop", e)
            } finally {
                stopInternal()
            }
        }

        return true
    }

    private fun calculateRms(chunk: ByteArray) {
        var sum = 0.0
        val numSamples = chunk.size / 2
        for (i in 0 until numSamples) {
            val sample = (chunk[i * 2].toInt() and 0xFF) or (chunk[i * 2 + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        val rms = Math.sqrt(sum / maxOf(1, numSamples)).toFloat()
        _rmsLevel.value = (rms / 32768f).coerceIn(0f, 1f)
    }

    /**
     * Stop capturing microphone audio.
     */
    fun stopStreaming() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            _isRecording.value = false
            _rmsLevel.value = 0f
        }
    }
}
