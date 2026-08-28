package com.vasu.assistant.camera

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null

    fun startRecording(outputDir: File): ActionResult {
        if (isRecording) return ActionResult.error("video", "Already recording")
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val file = File(outputDir, "VASU_VID_${timestamp}.mp4")
            currentFile = file

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1920, 1080)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(8_000_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            ActionResult.success("video", "Recording started: ${file.name}", mapOf("path" to file.absolutePath))
        } catch (e: Exception) {
            ActionResult.error("video", "Recording failed to start", e.message ?: "Unknown")
        }
    }

    fun stopRecording(): ActionResult {
        if (!isRecording) return ActionResult.error("video", "Not recording")
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            val file = currentFile
            currentFile = null
            if (file != null && file.exists()) {
                ActionResult.success("video", "Recording saved: ${file.name}", mapOf("path" to file.absolutePath, "size" to file.length()))
            } else {
                ActionResult.error("video", "Recording file not found")
            }
        } catch (e: Exception) {
            isRecording = false
            recorder = null
            ActionResult.error("video", "Stop recording failed", e.message ?: "Unknown")
        }
    }

    fun isRecording() = isRecording
}
