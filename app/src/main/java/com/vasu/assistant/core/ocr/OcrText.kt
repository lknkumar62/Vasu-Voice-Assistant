package com.vasu.assistant.core.ocr

/**
 * Axis-aligned box in image pixels, independent of android.graphics.Rect so the
 * merge/formatting logic stays testable on the JVM.
 */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()

    fun intersectionArea(other: OcrBox): Long {
        val overlapWidth = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0)
        val overlapHeight = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0)
        return overlapWidth.toLong() * overlapHeight.toLong()
    }

    fun intersectionOverUnion(other: OcrBox): Float {
        val intersection = intersectionArea(other)
        if (intersection == 0L) return 0f
        val union = area + other.area - intersection
        if (union <= 0L) return 0f
        return intersection.toFloat() / union.toFloat()
    }

    companion object {
        val ZERO = OcrBox(0, 0, 0, 0)
    }
}

enum class OcrScript { LATIN, DEVANAGARI }

/**
 * One recognized text region. [script] is derived from the text itself while
 * [source] records which recognizer produced the block.
 */
data class OcrBlock(
    val text: String,
    val lines: List<String>,
    val box: OcrBox,
    val script: OcrScript,
    val source: OcrScript
)

data class OcrDocument(
    val fullText: String,
    val blocks: List<OcrBlock>,
    val scripts: Set<OcrScript>
)

/**
 * Pure text post-processing for OCR: script detection, cross-recognizer block
 * merging, reading order, and the spoken summary. No ML Kit or Android types.
 */
object OcrText {

    const val MAX_SPOKEN_CHARS = 1200
    const val DEFAULT_OVERLAP_THRESHOLD = 0.5f

    fun containsDevanagari(text: String): Boolean = text.any { it in DEVANAGARI_RANGE }

    fun containsLatinLetters(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    fun scriptOf(text: String): OcrScript =
        if (containsDevanagari(text)) OcrScript.DEVANAGARI else OcrScript.LATIN

    /**
     * Fuses the Latin and Devanagari recognizer outputs. A Devanagari block owns
     * its region: Latin blocks overlapping it are dropped (the Latin model cannot
     * read Devanagari and may emit noise there). Identical text coming from both
     * recognizers is kept once; identical text repeated by the same recognizer
     * (two equal labels on a screen) is kept as-is.
     */
    fun merge(
        latin: List<OcrBlock>,
        devanagari: List<OcrBlock>,
        overlapThreshold: Float = DEFAULT_OVERLAP_THRESHOLD
    ): List<OcrBlock> {
        val devanagariBlocks = devanagari.filter { it.script == OcrScript.DEVANAGARI }
        val otherDevanagari = devanagari.filterNot { it.script == OcrScript.DEVANAGARI }
        val latinSurvivors = latin.filter { latinBlock ->
            devanagariBlocks.none { it.box.intersectionOverUnion(latinBlock.box) >= overlapThreshold }
        }

        val merged = readingOrder(latinSurvivors + otherDevanagari + devanagariBlocks)
        val keeperSource = HashMap<String, OcrScript>()
        val result = ArrayList<OcrBlock>(merged.size)
        for (block in merged) {
            val key = block.text.trim()
            val keeper = keeperSource[key]
            if (keeper == null || keeper == block.source) {
                if (keeper == null) keeperSource[key] = block.source
                result.add(block)
            }
        }
        return result
    }

    fun readingOrder(blocks: List<OcrBlock>): List<OcrBlock> =
        blocks.sortedWith(compareBy({ it.box.top }, { it.box.left }))

    fun buildDocument(latin: List<OcrBlock>, devanagari: List<OcrBlock>): OcrDocument {
        val blocks = merge(latin, devanagari)
        return OcrDocument(
            fullText = blocks.joinToString(separator = "\n") { it.text },
            blocks = blocks,
            scripts = blocks.map { it.script }.toSet()
        )
    }

    /**
     * Text handed to the TTS/LLM turn. Long documents are cut at [maxChars] with
     * the true total so nothing is silently dropped (full text stays in data).
     */
    fun formatSpokenText(fullText: String, maxChars: Int = MAX_SPOKEN_CHARS): String {
        val trimmed = fullText.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) + "... (${trimmed.length} characters extracted)"
    }

    private val DEVANAGARI_RANGE = '\u0900'..'\u097F'
}
