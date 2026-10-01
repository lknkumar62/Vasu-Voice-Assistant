package com.vasu.assistant.core.tts

/**
 * SentenceSplitter - incremental sentence boundary detector for streaming TTS.
 *
 * Feed text chunk-by-chunk via [feed]; each completed sentence is emitted as
 * soon as its boundary is confident. Call [flush] at end-of-stream to emit any
 * trailing partial sentence. [splitFull] is the non-streaming convenience.
 *
 * Confident boundaries: English terminators (. ! ?) optionally followed by
 * ellipsis/closing quotes/brackets, Hindi danda '।' (U+0964) and double danda
 * '॥' (U+0965), U+2026 ellipsis, and newline paragraph breaks. A boundary is
 * only emitted when the terminator is followed by whitespace + a letter or
 * Devanagari character (or a newline), so abbreviations, decimals, initials,
 * URLs/emails/paths, versions and times stay intact.
 */
class SentenceSplitter {

    private val buffer = StringBuilder()

    /** Characters allowed between a terminator and the confidence lookahead. */
    private val closers = charArrayOf('"', '\'', '`', ')', ']', '}', '»', '”', '’', '›', '>', '*', '_')

    private val abbreviations = setOf(
        "mr", "mrs", "dr", "vs", "etc", "e.g", "i.e", "st", "no",
        "prof", "sr", "jr", "fig", "approx", "dept", "shri", "sh", "mo", "jan", "feb",
        "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )

    /**
     * Appends [chunk] and returns the (possibly empty) list of sentences that
     * became complete with this chunk, in order.
     */
    fun feed(chunk: String): List<String> {
        buffer.append(chunk)
        val out = mutableListOf<String>()
        while (true) {
            val boundary = findConfidentBoundary() ?: break
            val sentence = buffer.substring(0, boundary).trim()
            buffer.delete(0, boundary)
            while (buffer.isNotEmpty() && buffer[0].isWhitespace()) {
                buffer.deleteCharAt(0)
            }
            if (sentence.isNotEmpty()) out.add(sentence)
        }
        return out
    }

    /** Emits the trailing buffered text (if any) and clears the splitter. */
    fun flush(): String? {
        val tail = buffer.toString().trim()
        buffer.clear()
        return tail.ifEmpty { null }
    }

    /**
     * Finds the earliest confident boundary and returns the index one past the
     * terminator+closers (i.e. the cut point), or null if no confident
     * boundary exists yet (needs more input or none).
     */
    private fun findConfidentBoundary(): Int? {
        val s = buffer.toString()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\n' || c == '\r') {
                // Paragraph break is always a confident boundary.
                val next = i + 1
                // Emit even if the sentence so far is blank — the caller drops
                // blank sentences, and this lets leading blank lines be eaten.
                if (next > 0) return i
                i++
                continue
            }
            if (c == '.' || c == '!' || c == '?' || c == '।' || c == '॥' || c == '…') {
                if (isProtectedTerminator(s, i)) {
                    i++
                    continue
                }
                var end = i + 1
                while (end < s.length && s[end] == c) end++
                if (c == '!' || c == '?') {
                    while (end < s.length && (s[end] == '!' || s[end] == '?')) end++
                }
                while (end < s.length && s[end] in closers) end++

                if (end >= s.length) return null // wait for more input

                when {
                    s[end] == '\n' || s[end] == '\r' -> return end
                    s[end] == ' ' || s[end] == '\t' -> {
                        var k = end
                        while (k < s.length && (s[k] == ' ' || s[k] == '\t')) k++
                        if (k >= s.length) return null // whitespace at buffer end: wait
                        if (s[k] == '\n' || s[k] == '\r' || s[k].isLetter()) return end
                        // Terminator + whitespace + non-letter: not confident; skip.
                        i = end
                    }
                    else -> i = end // terminator not followed by whitespace: skip
                }
            } else {
                i++
            }
        }
        return null
    }

    /**
     * Decides whether the terminator at index [i] belongs to an abbreviation,
     * decimal, initial, URL/email/path, version or time and therefore cannot
     * end a sentence.
     */
    private fun isProtectedTerminator(s: String, i: Int): Boolean {
        val c = s[i]
        if (c == '।' || c == '॥' || c == '…') return false

        val token = tokenAt(s, i)
        // Dots/!/? INSIDE a URL/email/path token stay protected; a token's
        // trailing dot may legitimately terminate a sentence.
        var tokenStart = i
        while (tokenStart > 0 && !s[tokenStart - 1].isWhitespace()) tokenStart--
        var tokenEnd = i
        while (tokenEnd < s.length && !s[tokenEnd].isWhitespace()) tokenEnd++
        val dotIsInterior = i < tokenEnd - 1
        if (dotIsInterior &&
            (token.contains("://") || token.contains("www.") || token.contains("@") ||
                token.startsWith("/") || token.startsWith("~") || token.contains("\\"))
        ) return true

        if (c == '!' || c == '?') return false

        // c == '.'
        // Decimals / versions / times: digit '.' digit
        if (i > 0 && s[i - 1].isDigit() && i + 1 < s.length && s[i + 1].isDigit()) return true

        // Domain-like tokens: example.com, sub.example.co.in
        if (dotIsInterior) {
            val domainStripped = token.trimEnd('.', ',', '"', '\'', ')', ']', '}')
            val domainRegex = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
            if (domainRegex.matches(domainStripped)) return true
        }

        // Abbreviations: word immediately before this dot
        var w = i - 1
        while (w >= 0 && s[w].isLetterOrDigit()) w--
        val wordBefore = s.substring(w + 1, i)
        // Also handle dots inside the abbreviation itself ("e.g.", "i.e.")
        var w2 = i - 1
        while (w2 >= 0 && (s[w2].isLetterOrDigit() || s[w2] == '.')) w2--
        val wordWithDots = s.substring(w2 + 1, i).lowercase()
        if (wordBefore.lowercase() in abbreviations || wordWithDots in abbreviations) return true

        // Initials: single capital letter before the dot ("A. B.")
        if (wordBefore.length == 1 && wordBefore[0].isUpperCase()) return true

        return false
    }

    /** The whitespace-delimited token that spans index [i]. */
    private fun tokenAt(s: String, i: Int): String {
        var start = i
        while (start > 0 && !s[start - 1].isWhitespace()) start--
        var end = i
        while (end < s.length && !s[end].isWhitespace()) end++
        return s.substring(start, end)
    }

    companion object {
        /** Non-streaming helper: split a complete response into sentences. */
        fun splitFull(text: String): List<String> {
            val splitter = SentenceSplitter()
            val out = mutableListOf<String>()
            out.addAll(splitter.feed(text))
            splitter.flush()?.let { out.add(it) }
            return out
        }
    }
}
