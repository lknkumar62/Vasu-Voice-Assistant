package com.vasu.assistant.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pure Kotlin parser that pulls incremental text deltas out of raw SSE payloads.
 * Shared by GeminiProvider and ClaudeProvider; no I/O and no Android framework
 * types, so it unit-tests on the JVM.
 *
 * Accepted input shapes per segment:
 *  - `data: {json}` SSE field lines (the prefix is stripped)
 *  - bare `{json}` objects (already-stripped payloads)
 *  - JSON arrays of chunk objects
 *  - CR/LF-joined batches, so one string carrying several `data:` lines yields
 *    every delta it contains
 *  - `[DONE]` sentinels, `event:`/`id:`/`retry:` fields, comments (`:...`),
 *    and blanks are ignored
 *
 * Gemini chunks read `candidates[0].content.parts[].text` only; later candidates
 * are ignored. Claude chunks read
 * `{"type":"content_block_delta","delta":{"type":"text_delta","text":...}}`.
 * Malformed JSON is skipped, never thrown.
 */
object StreamTextParser {

    fun parseChunk(line: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in line.split('\r', '\n')) {
            val segment = stripPrefix(raw) ?: continue
            if (segment.isEmpty() || segment == "[DONE]") continue
            if (!segment.startsWith('{') && !segment.startsWith('[')) continue
            try {
                extract(JsonParser.parseString(segment), out)
            } catch (e: Exception) {
                // Malformed JSON / wrong shape: skip this segment, never crash.
            }
        }
        return out
    }

    /** Returns the payload part of one SSE segment, or null when it carries no data. */
    private fun stripPrefix(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith(':')) return null              // SSE comment / heartbeat
        val field = s.substringBefore(':', "")
        if (field == "event" || field == "id" || field == "retry") return null
        return if (s.startsWith("data:")) s.substring("data:".length).trim() else s
    }

    private fun extract(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonObject -> extractFromObject(element, out)
            is JsonArray -> for (i in 0 until element.size()) {
                val child = element[i]
                if (child is JsonObject) extractFromObject(child, out)
            }
            else -> Unit
        }
    }

    private fun extractFromObject(obj: JsonObject, out: MutableList<String>) {
        extractGemini(obj, out)
        extractClaude(obj, out)
    }

    /** Gemini streamGenerateContent chunk: only candidate index 0 is meaningful. */
    private fun extractGemini(obj: JsonObject, out: MutableList<String>) {
        val candidates = obj.get("candidates")?.let { if (it.isJsonArray) it.asJsonArray else null }
            ?: return
        if (candidates.size() == 0) return
        val first = candidates[0]
        if (first !is JsonObject) return
        val parts = first.get("content")?.let { c ->
            if (c.isJsonObject) c.asJsonObject.get("parts")?.let { p ->
                if (p.isJsonArray) p.asJsonArray else null
            } else null
        } ?: return
        for (i in 0 until parts.size()) {
            val part = parts[i]
            if (part is JsonObject) part.optText("text")?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
    }

    /** Anthropic SSE chunk: content_block_delta frames carry the streaming text. */
    private fun extractClaude(obj: JsonObject, out: MutableList<String>) {
        if (obj.optText("type") != "content_block_delta") return
        val delta = obj.get("delta")
        if (delta is JsonObject) {
            delta.optText("text")?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
    }

    private fun JsonObject.optText(key: String): String? {
        val v = get(key) ?: return null
        return if (v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else null
    }
}
