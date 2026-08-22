package com.vasu.ai.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class VasuAudioCaptureManager(
    private val context: Context,
    private val config: VasuAudioCaptureConfig = VasuAudioCaptureConfig()
) : VasuAudioCapture {

    @Volatile
    private var running = false

    @Volatile
    private var released = false

    private var recorder: AudioRecord? = null
    private var worker: ExecutorService? = null
    private var captureTask: Future<*>? = null
    private var callback: ((ShortArray, Int) -> Unit)? = null

    @Synchronized
    override fun start(): Boolean {
        if (released) return false
        if (running) return true

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            println("VASU_AUDIO_CAPTURE_PERMISSION_MISSING")
            return false
        }

        println("VASU_AUDIO_CAPTURE_STARTING")

        val minimumBuffer = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelConfig,
            config.encoding
        )

        if (minimumBuffer <= 0) {
            println("VASU_AUDIO_CAPTURE_ERROR reason=invalid_min_buffer")
            return false
        }

        val bufferBytes = (minimumBuffer * config.bufferMultiplier)
            .coerceAtLeast(minimumBuffer)
            .coerceAtMost(config.maxBufferBytes)
            .let { value -> if (value % 2 == 0) value else value - 1 }

        return try {
            val created = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRateHz,
                config.channelConfig,
                config.encoding,
                bufferBytes
            )

            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                println("VASU_AUDIO_CAPTURE_ERROR reason=audio_record_not_initialized")
                false
            } else {
                recorder = created
                running = true
                println("VASU_AUDIO_CAPTURE_RUNNING")
                true
            }
        } catch (security: SecurityException) {
            recorder = null
            println("VASU_AUDIO_CAPTURE_PERMISSION_MISSING")
            false
        } catch (error: IllegalArgumentException) {
            recorder = null
            println("VASU_AUDIO_CAPTURE_ERROR reason=invalid_audio_config")
            false
        } catch (error: Throwable) {
            recorder = null
            println("VASU_AUDIO_CAPTURE_ERROR reason=${error::class.simpleName}")
            false
        }
    }

    @Synchronized
    fun startCaptureLoop(
        onPcmFrame: (ShortArray, Int) -> Unit
    ): Boolean {
        if (!start()) return false
        callback = onPcmFrame
        val currentRecorder = recorder ?: return false
        if (captureTask?.isDone == false) return true

        val executor = worker ?: Executors.newSingleThreadExecutor().also { worker = it }
        captureTask = executor.submit {
            val buffer = ShortArray(1024)
            try {
                currentRecorder.startRecording()
                while (running && !released) {
                    val read = currentRecorder.read(
                        buffer,
                        0,
                        buffer.size,
                        AudioRecord.READ_BLOCKING
                    )

                    when {
                        read > 0 -> callback?.invoke(buffer.copyOf(read), read)
                        read == AudioRecord.ERROR_DEAD_OBJECT -> {
                            println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_DEAD_OBJECT")
                            running = false
                        }
                        read == AudioRecord.ERROR_INVALID_OPERATION -> {
                            println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_INVALID_OPERATION")
                            running = false
                        }
                        read == AudioRecord.ERROR_BAD_VALUE -> {
                            println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_BAD_VALUE")
                            running = false
                        }
                        else -> {
                            println("VASU_AUDIO_CAPTURE_ERROR reason=read_$read")
                            running = false
                        }
                    }
                }
            } catch (error: Throwable) {
                if (!released) {
                    println("VASU_AUDIO_CAPTURE_ERROR reason=${error::class.simpleName}")
                }
                running = false
            } finally {
                runCatching { currentRecorder.stop() }
            }
        }

        return true
    }

    override fun read(buffer: ShortArray): VasuAudioCaptureResult {
        if (!running || released) {
            return VasuAudioCaptureResult(false, reason = "not_running")
        }
        val currentRecorder = recorder
            ?: return VasuAudioCaptureResult(false, reason = "recorder_unavailable")

        return try {
            val read = currentRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            when {
                read > 0 -> VasuAudioCaptureResult(
                    success = true,
                    samplesRead = read,
                    bytesRead = read * 2
                )
                read == AudioRecord.ERROR_DEAD_OBJECT -> {
                    println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_DEAD_OBJECT")
                    running = false
                    VasuAudioCaptureResult(false, reason = "ERROR_DEAD_OBJECT")
                }
                read == AudioRecord.ERROR_INVALID_OPERATION -> {
                    println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_INVALID_OPERATION")
                    running = false
                    VasuAudioCaptureResult(false, reason = "ERROR_INVALID_OPERATION")
                }
                read == AudioRecord.ERROR_BAD_VALUE -> {
                    println("VASU_AUDIO_CAPTURE_ERROR reason=ERROR_BAD_VALUE")
                    running = false
                    VasuAudioCaptureResult(false, reason = "ERROR_BAD_VALUE")
                }
                else -> {
                    println("VASU_AUDIO_CAPTURE_ERROR reason=read_$read")
                    running = false
                    VasuAudioCaptureResult(false, reason = "read_$read")
                }
            }
        } catch (error: Throwable) {
            running = false
            println("VASU_AUDIO_CAPTURE_ERROR reason=${error::class.simpleName}")
            VasuAudioCaptureResult(false, reason = error::class.simpleName ?: "read_error")
        }
    }

    @Synchronized
    override fun stop() {
        if (!running && recorder == null) return
        println("VASU_AUDIO_CAPTURE_STOPPING")
        running = false
        runCatching { captureTask?.cancel(true) }
        captureTask = null
        runCatching { recorder?.stop() }
        println("VASU_AUDIO_CAPTURE_STOPPED")
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        stop()
        runCatching { recorder?.release() }
        recorder = null
        runCatching { worker?.shutdownNow() }
        worker = null
        callback = null
    }

    override fun isRunning(): Boolean = running && !released
}
