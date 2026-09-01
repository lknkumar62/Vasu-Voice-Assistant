package com.vasu.assistant.core.tts

/**
 * Markdown is written to be read, not heard. Left alone the engine pronounces
 * asterisks and backticks and spells a fenced code block out character by
 * character, so any formatted reply comes out as noise.
 */
private val FENCED_CODE = Regex("```[\\s\\S]*?```")
private val IMAGE = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")
private val LINK = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
private val INLINE_CODE = Regex("`([^`]*)`")
private val RULE = Regex("(?m)^\\s{0,3}([-*_])\\s*(\\1\\s*){2,}$")
private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
private val BLOCKQUOTE = Regex("(?m)^\\s{0,3}>\\s?")
private val BULLET = Regex("(?m)^\\s{0,3}[-*+]\\s+")
private val NUMBERED = Regex("(?m)^\\s{0,3}(\\d+)[.)]\\s+")
private val EMPHASIS = Regex("(\\*{1,3}|_{2,3})(.+?)\\1")
private val BLANK_LINES = Regex("\\n{2,}")
private val REPEATED_SPACES = Regex("[ \\t]{2,}")

/**
 * Rewrites a reply so it can be spoken as written.
 *
 * A fenced code block is dropped rather than read aloud — VASU is a voice, and a
 * shell snippet spelled out is worse than silence. Everything else keeps its
 * words and loses only the punctuation that carries no sound. Single underscores
 * are left alone so snake_case names are not mangled.
 */
fun toSpeakableText(raw: String): String {
    if (raw.isBlank()) return ""

    var text = raw.replace(FENCED_CODE, " ")
    text = IMAGE.replace(text) { it.groupValues[1] }
    text = LINK.replace(text) { it.groupValues[1] }
    text = INLINE_CODE.replace(text) { it.groupValues[1] }
    text = text.replace(RULE, " ")
    text = text.replace(HEADING, "")
    text = text.replace(BLOCKQUOTE, "")
    text = text.replace(BULLET, "")
    text = NUMBERED.replace(text) { "${it.groupValues[1]}. " }
    text = EMPHASIS.replace(text) { it.groupValues[2] }
    text = text.replace(BLANK_LINES, ". ")
    text = text.replace('\n', ' ')
    text = text.replace(REPEATED_SPACES, " ")
    return text.trim()
}
