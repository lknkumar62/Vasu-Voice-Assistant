package com.vasu.assistant.core.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Internal hidden error-log. Captures every notable failure this app
 * would otherwise swallow (uncaught crashes, command failures, voice
 * errors, mission errors) into a single app-private file.
 *
 * Deliberately invisible to the end user: no UI surface reads the file,
 * and it lives in the app's private files dir which is blocked to
 * external apps unless specifically shared.
 *
 * Never log secret contents (passwords, OTP values, tokens).
 */
object ErrorLog {

    @Volatile
    private var logFile: File? = null

    private const val MAX_BYTES = 256 * 1024
    private const val TRIM_TO = MAX_BYTES / 2

    fun init(context: Context) {
        logFile = File(context.filesDir, "vasu_errors.log")
    }

    fun log(kind: String, message: String, t: Throwable? = null) {
        val target = logFile ?: return
        val line = formatLine(kind, message, t)
        try {
            target.appendText(line)
            if (target.length() > MAX_BYTES) {
                val bytes = target.readBytes()
                target.writeBytes(bytes.takeLast(TRIM_TO).toByteArray())
            }
        } catch (_: Throwable) {
            // Logging must never crash the app.
        }
    }

    fun formatLine(kind: String, message: String, t: Throwable?): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val base = "$ts [$kind] $message"
        return if (t == null) {
            "$base\n"
        } else {
            val stack = t.stackTrace.take(3).joinToString(" | ") { "${it.className}:${it.lineNumber}" }
            "$base | ${t.javaClass.simpleName}: ${t.message ?: ""} | $stack\n"
        }
    }

    /**
     * Read the tail of the internal log for diagnostics. Debug builds only
     * surface this; release builds never call it from UI.
     */
    fun readTail(maxChars: Int = 8000): String {
        return try {
            val target = logFile ?: return "(log not initialized)"
            if (!target.exists()) return "(empty — no errors recorded yet)"
            val text = target.readText()
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        } catch (_: Throwable) {
            "(unreadable)"
        }
    }

    fun clear() {
        try {
            logFile?.writeText("")
        } catch (_: Throwable) {
        }
    }
}
