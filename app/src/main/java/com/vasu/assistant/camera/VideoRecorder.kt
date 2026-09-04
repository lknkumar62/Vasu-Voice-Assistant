package com.vasu.assistant.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingState {
    IDLE,
    RECORDING,
    STOPPING,
    ERROR
}

/**
 * Authoritative Video Recorder with strict lifecycle state machine.
 *
 * Guarantees:
 * - Single authoritative recording state: IDLE -> RECORDING -> STOPPING -> IDLE.
 * - Prevents duplicate startRecording() calls.
 * - Allows stopRecording() to cleanly stop an active recording.
 * - Always resets state to IDLE on stop or failure so repeated start/stop cycles work cleanly.
 */
@Singleton
class VideoRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var recorder: MediaRecorder? = null
    private var state = RecordingState.IDLE
    private var currentFile: File? = null

    fun startRecording(outputDir: File): ActionResult {
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (!hasCam || !hasMic) {
            return ActionResult.error(
                "video",
                "Camera and microphone permissions required to record video",
                "PERMISSION_REQUIRED"
            )
        }

        synchronized(lock) {
            if (state == RecordingState.RECORDING) {
                return ActionResult.error(
                    "video",
                    "Recording already in progress",
                    "RECORDING_ALREADY_ACTIVE",
                    mapOf("state" to "RECORDING", "file" to (currentFile?.name ?: ""))
                )
            }
            if (state == RecordingState.STOPPING) {
                return ActionResult.error(
                    "video",
                    "Recorder is finishing previous recording. Please wait.",
                    "RECORDING_STOPPING"
                )
            }
            state = RecordingState.RECORDING
        }

        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val file = File(outputDir, "VASU_VID_${timestamp}.mp4")
            currentFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = rec

            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.DEFAULT)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1280, 720)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(4_000_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            ActionResult.success("video", "Recording started: ${file.name}", mapOf("path" to file.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed to start", e)
            synchronized(lock) {
                state = RecordingState.IDLE
                currentFile = null
                try {
                    recorder?.release()
                } catch (ignored: Exception) {}
                recorder = null
            }
            ActionResult.error("video", "Recording failed to start: ${e.message}", "INITIALIZATION_FAILED")
        }
    }

    fun stopRecording(): ActionResult {
        synchronized(lock) {
            if (state != RecordingState.RECORDING) {
                return ActionResult.error("video", "No active recording in progress", "NOT_RECORDING")
            }
            state = RecordingState.STOPPING
        }

        return try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaRecorder", e)
                }
                try {
                    release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing MediaRecorder", e)
                }
            }
            recorder = null
            val file = currentFile
            currentFile = null

            if (file != null && file.exists() && file.length() > 0) {
                ActionResult.success("video", "Recording saved: ${file.name}", mapOf("path" to file.absolutePath, "size" to file.length()))
            } else {
                ActionResult.error("video", "Recording stopped but file is missing or empty", "FILE_EMPTY")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
            ActionResult.error("video", "Stop recording failed: ${e.message}", "STOP_FAILED")
        } finally {
            synchronized(lock) {
                state = RecordingState.IDLE
                recorder = null
            }
        }
    }

    fun isRecording(): Boolean = synchronized(lock) { state == RecordingState.RECORDING }

    fun getRecordingState(): RecordingState = synchronized(lock) { state }

    companion object {
        private const val TAG = "VideoRecorder"
    }
}
