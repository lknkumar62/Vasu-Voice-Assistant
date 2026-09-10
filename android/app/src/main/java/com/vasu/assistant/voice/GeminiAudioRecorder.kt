package com.vasu.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.vasu.assistant.config.GeminiVoiceConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Real-time microphone audio capture for Gemini Live.
 * Captures 16 kHz 16-bit mono PCM in small chunks for low-latency streaming.
 */
class GeminiAudioRecorder {

    private val tag = "GeminiAudioRecorder"

    private val sampleRate = GeminiVoiceConfig.INPUT_SAMPLE_RATE // 16000 Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null

    // Chunk size: 512 samples = 1024 bytes (32 ms of audio for smooth real-time streaming)
    private val chunkSize = 1024

    interface AudioChunkListener {
        fun onAudioChunk(pcmData: ByteArray)
        fun onAudioLevel(level: Float) // 0.0 to 1.0
        fun onError(message: String)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(listener: AudioChunkListener) {
        if (isRecording.get()) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(chunkSize * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_RECOGNITION fails on device
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener.onError("AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordThread = Thread {
                val buffer = ByteArray(chunkSize)
                while (isRecording.get()) {
                    val readBytes = audioRecord?.read(buffer, 0, chunkSize) ?: -1
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        listener.onAudioChunk(chunk)

                        // Calculate RMS level for visualizer
                        val level = calculateRms(chunk, readBytes)
                        listener.onAudioLevel(level)
                    } else if (readBytes < 0) {
                        Log.w(tag, "AudioRecord read returned error code: $readBytes")
                    }
                }
            }.apply {
                name = "VasuAudioRecordThread"
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(tag, "Recording started at 16kHz PCM mono")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord", e)
            listener.onError(e.message ?: "Recording error")
            stopRecording()
        }
    }

    private fun calculateRms(pcmData: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        for (i in 0 until length step 2) {
            val sample = (pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += (shortSample * shortSample)
        }
        val rms = sqrt(sum / sampleCount)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(tag, "Error stopping AudioRecord", e)
        }

        recordThread?.interrupt()
        recordThread = null
        Log.d(tag, "Recording stopped")
    }

    fun isRecording(): Boolean = isRecording.get()
}
