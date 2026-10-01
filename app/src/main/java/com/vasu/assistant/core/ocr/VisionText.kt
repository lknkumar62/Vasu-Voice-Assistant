package com.vasu.assistant.core.ocr

import kotlin.math.roundToInt

/**
 * A classifier label and the confidence ML Kit actually returned for it.
 */
data class LabelScore(val label: String, val confidence: Float)

/**
 * Detected object with pixel bounds, in a form the JVM tests can build.
 */
data class DetectedItem(
    val labels: List<LabelScore>,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Pure formatting for vision results: object descriptions and QR summaries.
 * Confidence values are the ones reported by ML Kit, rounded only for speech.
 */
object VisionText {

    const val MAX_DESCRIBED_OBJECTS = 4

    /** Returns null when nothing was detected so callers can fail honestly. */
    fun describeItems(
        items: List<DetectedItem>,
        maxObjects: Int = MAX_DESCRIBED_OBJECTS
    ): String? {
        if (items.isEmpty()) return null
        val limit = maxObjects.coerceAtLeast(1)
        val shown = items.take(limit).map { item ->
            val top = item.labels.maxByOrNull { it.confidence }
            if (top == null) "an unclassified object" else "${top.label} (${percent(top.confidence)}%)"
        }
        val head = if (items.size > shown.size) {
            "Detected ${items.size} objects: "
        } else {
            "Detected: "
        }
        return head + shown.joinToString(", ")
    }

    fun percent(confidence: Float): Int =
        (confidence.coerceIn(0f, 1f) * 100f).roundToInt()

    /** Null when no code carried a readable value. */
    fun qrSummary(values: List<String>): String? {
        val readable = values.map { it.trim() }.filter { it.isNotEmpty() }
        val first = readable.firstOrNull() ?: return null
        val extra = readable.size - 1
        return when {
            extra <= 0 -> first
            extra == 1 -> "$first (and 1 more QR code)"
            else -> "$first (and $extra more QR codes)"
        }
    }
}
