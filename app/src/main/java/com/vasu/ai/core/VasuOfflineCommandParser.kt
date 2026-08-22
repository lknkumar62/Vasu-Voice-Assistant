package com.vasu.ai.core

class VasuOfflineCommandParser(
    private val normalizer: VasuCommandNormalizer = VasuCommandNormalizer(),
    private val segmenter: VasuCommandSegmenter = VasuCommandSegmenter(),
    private val intentEngine: VasuLocalIntentEngine = VasuLocalIntentEngine()
) {
    data class ParsedCommand(
        val original: String,
        val normalized: String,
        val intent: VasuLocalIntent
    )

    data class ParseResult(
        val commands: List<ParsedCommand>,
        val fullyOfflineSupported: Boolean
    )

    fun parse(input: String): ParseResult {
        val normalized = normalizer.normalizeHindiHinglish(input)
        if (normalized.isBlank()) return ParseResult(emptyList(), false)

        val segments = segmenter.split(normalized)
        val parsed = segments.map { segment ->
            ParsedCommand(
                original = segment,
                normalized = normalizer.normalizeHindiHinglish(segment),
                intent = intentEngine.parse(segment)
            )
        }

        val supported = parsed.isNotEmpty() && parsed.all { it.intent != VasuLocalIntent.Unknown }
        println(
            "VASU_OFFLINE_PARSE " +
                "input=$input segments=${parsed.size} offline=$supported"
        )
        return ParseResult(parsed, supported)
    }
}
