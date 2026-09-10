package com.vasu.assistant.core.tts

import java.text.Normalizer

/**
 * Normalizes text so it can be naturally spoken by the local Speech/TTS engine.
 *
 * Requirements:
 * - Preserves Devanagari Unicode and combining characters (matras, halant, nuktas).
 * - Strips markdown code blocks, backticks, formatting tags.
 * - Strips URLs and UI-only artifacts.
 * - Filters emojis and symbols to prevent TTS engines from spelling codepoints.
 * - Preserves Hindi and standard punctuation for natural pauses.
 */
private val FENCED_CODE = Regex("```[\\s\\S]*?```")
private val URL_PATTERN = Regex("(?:https?://|www\\.)\\S+")
private val IMAGE = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")
private val LINK = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
private val INLINE_CODE = Regex("`([^`]*)`")
private val RULE = Regex("(?m)^\\s{0,3}([-*_])\\s*(\\1\\s*){2,}$")
private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
private val BLOCKQUOTE = Regex("(?m)^\\s{0,3}>\\s?")
private val BULLET = Regex("(?m)^\\s{0,3}[-*+]\\s+")
private val NUMBERED = Regex("(?m)^\\s{0,3}(\\d+)[.)]\\s+")
private val EMPHASIS = Regex("(\\*{1,3}|_{2,3})(.+?)\\1")
private val UI_LABELS = Regex("(?i)^(?:VASU|Assistant|User|Speaker|Role|Copy):\\s*")
private val BLANK_LINES = Regex("\\n{2,}")
private val REPEATED_SPACES = Regex("[ \\t]{2,}")

/**
 * Matches Unicode Emojis, Dingbats, Miscellaneous Symbols, Supplemental Symbols.
 * Devanagari Unicode range (U+0900 to U+097F) is strictly preserved.
 */
private val EMOJI_AND_SYMBOLS = Regex(
    "[\\p{So}\\p{Sk}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE00}-\\x{FE0F}]"
)

/**
 * Rewrites a canonical reply into clean speakable text for the local TTS engine.
 */
fun toSpeakableText(raw: String): String {
    if (raw.isBlank()) return ""

    // 1. Unicode NFC Normalization to keep Devanagari conjuncts and matras solid
    var text = Normalizer.normalize(raw, Normalizer.Form.NFC)

    // 2. Remove fenced code snippets entirely (code is noise when read out)
    text = text.replace(FENCED_CODE, " ")

    // 3. Remove URLs
    text = text.replace(URL_PATTERN, " ")

    // 4. Extract text from markdown links and images
    text = IMAGE.replace(text) { it.groupValues[1] }
    text = LINK.replace(text) { it.groupValues[1] }
    text = INLINE_CODE.replace(text) { it.groupValues[1] }

    // 5. Strip markdown structure
    text = text.replace(RULE, " ")
    text = text.replace(HEADING, "")
    text = text.replace(BLOCKQUOTE, "")
    text = text.replace(BULLET, "")
    text = NUMBERED.replace(text) { "${it.groupValues[1]}. " }
    text = EMPHASIS.replace(text) { it.groupValues[2] }

    // 6. Remove UI prefixes
    text = text.replace(UI_LABELS, "")

    // 7. Remove emojis and non-speech symbols
    text = text.replace(EMOJI_AND_SYMBOLS, " ")

    // 8. Normalize spacing and pauses
    text = text.replace(BLANK_LINES, ". ")
    text = text.replace('\n', ' ')
    text = text.replace(REPEATED_SPACES, " ")

    return text.trim()
}
