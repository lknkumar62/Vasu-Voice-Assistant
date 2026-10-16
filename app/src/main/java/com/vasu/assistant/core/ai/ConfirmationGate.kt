package com.vasu.assistant.core.ai

import com.vasu.assistant.core.security.RiskLevel
import java.util.Locale

/**
 * HIGH-risk confirmation policy (FIX-4, spec section 4).
 *
 * With autonomous execution on (the default), HIGH-risk tools run without a
 * prompt. When the user turns it off, a HIGH-risk tool call is parked and the
 * user is asked a script-aware yes/no question; only a clear yes on a later
 * turn executes it. A clear no cancels it; anything else drops the parked
 * call and is treated as a new request. Negative always wins over positive
 * ("nahi karo" must never execute).
 *
 * Pure Kotlin, no android.* imports, so it stays unit-testable on the JVM.
 */
object ConfirmationGate {

    /** HIGH-risk tools are gated only while autonomous execution is off. */
    fun requiresConfirmation(risk: RiskLevel, autoAllow: Boolean): Boolean =
        risk == RiskLevel.HIGH && !autoAllow

    /** Unambiguous yes answers: enough on their own. */
    private val STRONG_AFFIRMATIVE = listOf(
        "haan", "han", "haanji", "hanji", "ha", "yes", "yeah", "yep",
        "ok", "okay", "theek", "thik", "sahi", "bilkul", "sure",
        "confirm", "go ahead",
        "हाँ", "हां", "ठीक", "बिल्कुल"
    )

    /**
     * Imperative "do it" phrases: a yes only when the whole utterance is a
     * short answer (<= 3 words), so a new command that merely contains
     * "... karo" is not mistaken for a confirmation.
     */
    private val WEAK_AFFIRMATIVE = listOf(
        "kar do", "kardo", "karlo", "kar lo", "karo",
        "कर दो", "कर लो", "करो"
    )

    private val NEGATIVE = listOf(
        "nahi", "nahin", "nai", "na", "no", "nope",
        "cancel", "cancel karo", "ruko", "stop", "mat", "mat karo",
        "नहीं", "नही", "ना", "रुको", "रद्द", "मत", "मत करो"
    )

    fun isAffirmative(input: String): Boolean {
        if (containsWord(input, STRONG_AFFIRMATIVE)) return true
        val wordCount = input.trim().split(Regex("\\s+")).size
        return wordCount <= 3 && containsWord(input, WEAK_AFFIRMATIVE)
    }

    fun isNegative(input: String): Boolean = containsWord(input, NEGATIVE)

    /** Script-aware confirmation question for a parked tool call. */
    fun questionFor(toolName: String, style: DetectedStyle): String {
        val whatRoman = when (toolName) {
            "make_call" -> "call karna"
            "send_message" -> "message bhejna"
            "delete_file" -> "file delete karna"
            else -> "ye action karna"
        }
        val whatDeva = when (toolName) {
            "make_call" -> "कॉल करना"
            "send_message" -> "मैसेज भेजना"
            "delete_file" -> "फ़ाइल हटाना"
            else -> "यह काम करना"
        }
        val whatEng = when (toolName) {
            "make_call" -> "make the call"
            "send_message" -> "send the message"
            "delete_file" -> "delete the file"
            else -> "do this"
        }
        return when (style) {
            DetectedStyle.ENGLISH -> "Please confirm - should I $whatEng? Say yes or no."
            DetectedStyle.ROMAN_HINDI, DetectedStyle.HINGLISH ->
                "Confirm karo - kya main $whatRoman kar doon? Haan ya nahi bolo."
            DetectedStyle.DEVANAGARI_HINDI, DetectedStyle.OTHER ->
                "पुष्टि करें - क्या मैं $whatDeva कर दूँ? हाँ या नहीं बोलिए।"
        }
    }

    /** Script-aware whole-word containment (letters/marks/digits count as word chars). */
    private fun containsWord(input: String, words: List<String>): Boolean {
        val lower = input.trim().lowercase(Locale.ROOT)
        return words.any { word ->
            Regex("""(?<![\p{L}\p{M}\p{Nd}])${Regex.escape(word)}(?![\p{L}\p{M}\p{Nd}])""")
                .containsMatchIn(lower)
        }
    }
}
