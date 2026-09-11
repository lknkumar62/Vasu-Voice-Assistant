package com.vasu.assistant.core.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryManager @Inject constructor() {

    private val events = mutableListOf<TelemetryEvent>()
    private var isEnabled = true
    private var sessionId = System.currentTimeMillis().toString()

    data class TelemetryEvent(
        val type: String,
        val name: String,
        val properties: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )

    fun track(type: String, name: String, properties: Map<String, Any> = emptyMap()) {
        if (!isEnabled) return

        val event = TelemetryEvent(type, name, properties)
        events.add(event)

        // Also write to trace log
        writeTraceLog(event)

        // Flush if batch size reached
        if (events.size >= BATCH_SIZE) {
            flush()
        }
    }

    fun trackScreen(screenName: String) {
        track("screen_view", screenName)
    }

    fun trackAction(action: String, target: String = "") {
        track("action", action, mapOf("target" to target))
    }

    fun trackError(error: String, source: String = "") {
        track("error", error, mapOf("source" to source))
    }

    fun trackPerformance(metric: String, value: Long, unit: String = "ms") {
        track("performance", metric, mapOf("value" to value, "unit" to unit))
    }

    fun trackFeature(feature: String, enabled: Boolean) {
        track("feature", feature, mapOf("enabled" to enabled))
    }

    fun flush() {
        if (events.isEmpty()) return

        val batch = events.toList()
        events.clear()

        // Upload to server
        uploadBatch(batch)
    }

    private fun uploadBatch(batch: List<TelemetryEvent>) {
        // In production, this would upload to your analytics server
        Log.d(TAG, "Flushing ${batch.size} telemetry events")
        batch.forEach { event ->
            Log.d(TAG, "[${event.type}] ${event.name}: ${event.properties}")
        }
    }

    private fun writeTraceLog(event: TelemetryEvent) {
        try {
            val logFile = File(appContext?.filesDir, TRACE_LOG_FILE)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val timestamp = sdf.format(Date(event.timestamp))
            val line = "$timestamp [${event.type.uppercase()}] ${event.name} ${event.properties}\n"
            logFile.appendText(line)

            // Trim if too large (max 1MB)
            if (logFile.length() > MAX_LOG_SIZE) {
                val lines = logFile.readLines().takeLast(1000)
                logFile.writeText(lines.joinToString("\n"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write trace log: ${e.message}")
        }
    }

    fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "sdk" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            "app_version" to "1.0.0"
        )
    }

    fun getSessionId(): String = sessionId
    fun setEnabled(enabled: Boolean) { isEnabled = enabled }
    fun isEnabled(): Boolean = isEnabled

    fun getTraceLog(): String {
        return try {
            val logFile = File(appContext?.filesDir, TRACE_LOG_FILE)
            if (logFile.exists()) logFile.readText() else ""
        } catch (e: Exception) { "" }
    }

    fun clearTraceLog() {
        try {
            File(appContext?.filesDir, TRACE_LOG_FILE).delete()
        } catch (e: Exception) {}
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    companion object {
        private const val TAG = "TelemetryManager"
        private const val TRACE_LOG_FILE = "agent_trace.log"
        private const val BATCH_SIZE = 20
        private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB

        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}
