package com.vasu.assistant.core.memory

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

/**
 * VaultExtractor - pure structured-knowledge extraction helpers ported from
 * Jarvis vault/extractor.ts (prompt builder + response parser + store guards).
 *
 * Pure Kotlin: no android.* imports, no LLM calls in this batch. LLM wiring
 * (build prompt -> call model -> parse -> persist graph) lands later; this
 * file only builds the prompt, parses model JSON, and exposes guards shared
 * by the storage hook in [MemoryManager].
 */

data class ExtractedEntity(
    val name: String,
    val type: String,
    val properties: Map<String, String> = emptyMap()
)

data class ExtractedFact(
    val subject: String,
    val predicate: String,
    val obj: String,
    val confidence: Float,
    val userQuote: String? = null
)

data class ExtractedRelationship(
    val from: String,
    val to: String,
    val type: String
)

data class ExtractedCommitment(
    val what: String,
    val whenDue: String? = null,
    val priority: String? = null
)

data class ExtractionResult(
    val entities: List<ExtractedEntity> = emptyList(),
    val facts: List<ExtractedFact> = emptyList(),
    val relationships: List<ExtractedRelationship> = emptyList(),
    val commitments: List<ExtractedCommitment> = emptyList()
)

object VaultExtractor {

    val VALID_ENTITY_TYPES = setOf("person", "project", "tool", "place", "concept", "event")

    const val MAX_INPUT_CHARS = 2000

    private val FACT_BEARING_KEYWORDS = listOf("my", "mera", "i am", "i like", "birthday", "remember")

    /**
     * Cheap pre-LLM gate: true when the input looks like it may carry a
     * storable fact. Used to skip extraction (and its LLM cost) for chit-chat.
     */
    fun looksFactBearing(input: String): Boolean {
        val lower = input.lowercase()
        return FACT_BEARING_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Build the fixed extraction prompt. Inputs are truncated to
     * [MAX_INPUT_CHARS] each to bound token cost.
     */
    fun buildExtractionPrompt(userMsg: String, assistantMsg: String): String {
        val user = userMsg.take(MAX_INPUT_CHARS)
        val assistant = assistantMsg.take(MAX_INPUT_CHARS)
        return """You are an expert at extracting structured information from conversations. Analyze the following conversation and extract entities, facts, relationships, and commitments.

USER MESSAGE:
$user

ASSISTANT RESPONSE:
$assistant

Extract the following information and return ONLY valid JSON (no markdown, no explanation):

{
  "entities": [
    {
      "name": "Entity name",
      "type": "person|project|tool|place|concept|event",
      "properties": {}
    }
  ],
  "facts": [
    {
      "subject": "Entity name",
      "predicate": "property_name",
      "object": "value",
      "confidence": 0.0-1.0,
      "user_quote": "Exact supporting quote from USER MESSAGE, or omit"
    }
  ],
  "relationships": [
    {
      "from": "Entity A name",
      "to": "Entity B name",
      "type": "relationship_type"
    }
  ],
  "commitments": [
    {
      "what": "Description of commitment",
      "when_due": "ISO date string (optional)",
      "priority": "low|normal|high|critical (optional)"
    }
  ]
}

GUIDELINES:
- Extract only concrete, verifiable information
- For entities: identify people, projects, tools, places, concepts, events
- For facts: extract attributes about entities (e.g., "birthday_is", "works_at", "location_is")
- For relationships: extract connections between entities (e.g., "sister_of", "manages", "part_of")
- For commitments: extract any promises, tasks, or reminders mentioned
- Use snake_case for predicates and relationship types
- Set confidence lower (0.5-0.8) for implied or uncertain information
- The assistant response is context only, never evidence that the user confirmed a fact.
- Only quote exact text from USER MESSAGE. Do not invent a source, scope or date.
- location, alias, email and works_at can have multiple valid values. Do not infer exclusivity.
- A correction is still an extracted claim. Do not claim verification or supersede stored facts.
- If no information to extract, return empty arrays
- Respond with ONLY the JSON object, no other text"""
    }

    /**
     * Parse model output into an [ExtractionResult]. Never throws: any parse
     * failure yields an empty result. Without the user message, quotes cannot
     * be verified, so they are kept as reported; prefer the two-arg overload.
     */
    fun parseExtractionResponse(raw: String): ExtractionResult =
        parseExtractionResponse(raw, null)

    /**
     * Parse model output, keeping a fact's user_quote only when it is exact
     * text from [userMsg] (verbatim containment check). Pass null [userMsg]
     * when the user text is unavailable.
     */
    fun parseExtractionResponse(raw: String, userMsg: String?): ExtractionResult {
        return try {
            var cleaned = raw.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring("```json".length).trimStart()
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length - 3).trimEnd()
                }
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring("```".length).trimStart()
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length - 3).trimEnd()
                }
            }
            val root = JsonParser.parseString(cleaned)
            if (!root.isJsonObject) return emptyResult()
            val obj = root.asJsonObject
            ExtractionResult(
                entities = optArray(obj, "entities").mapNotNull { parseEntity(it) },
                facts = optArray(obj, "facts").mapNotNull { parseFact(it, userMsg) },
                relationships = optArray(obj, "relationships").mapNotNull { parseRelationship(it) },
                commitments = optArray(obj, "commitments").mapNotNull { parseCommitment(it) }
            )
        } catch (_: Exception) {
            emptyResult()
        }
    }

    /**
     * Stable dedup reference for an extraction episode: hex SHA-256 of the
     * concatenated user + assistant messages.
     */
    fun sourceRef(userMsg: String, assistantMsg: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((userMsg + assistantMsg).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun emptyResult() = ExtractionResult()

    private fun optArray(obj: JsonObject, name: String): List<JsonElement> {
        return try {
            val el = obj.get(name) ?: return emptyList()
            if (!el.isJsonArray) return emptyList()
            el.asJsonArray.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JsonObject.str(name: String): String? {
        return try {
            val el = get(name) ?: return null
            if (!el.isJsonPrimitive) return null
            val prim = el.asJsonPrimitive
            when {
                prim.isString -> prim.asString
                prim.isNumber -> prim.asNumber.toString()
                prim.isBoolean -> prim.asBoolean.toString()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEntity(el: JsonElement): ExtractedEntity? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val name = obj.str("name")?.trim().orEmpty()
        val type = obj.str("type")?.trim().orEmpty()
        if (name.isBlank() || type !in VALID_ENTITY_TYPES) return null
        val props = mutableMapOf<String, String>()
        try {
            val raw = obj.get("properties")
            if (raw != null && raw.isJsonObject) {
                for ((key, value) in raw.asJsonObject.entrySet()) {
                    val rendered = when {
                        !value.isJsonPrimitive -> value.toString()
                        value.asJsonPrimitive.isString -> value.asString
                        value.asJsonPrimitive.isNumber -> value.asJsonPrimitive.asNumber.toString()
                        value.asJsonPrimitive.isBoolean -> value.asJsonPrimitive.asBoolean.toString()
                        else -> value.toString()
                    }
                    props[key] = rendered
                }
            }
        } catch (_: Exception) {
            // Keep entity, drop unparseable properties.
        }
        return ExtractedEntity(name, type, props)
    }

    private fun parseFact(el: JsonElement, userMsg: String?): ExtractedFact? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val subject = obj.str("subject")?.trim().orEmpty()
        val predicate = obj.str("predicate")?.trim().orEmpty()
        val factObj = obj.str("object")?.trim().orEmpty()
        if (subject.isBlank() || predicate.isBlank() || factObj.isBlank()) return null
        val confidence = try {
            val raw = obj.get("confidence")
            if (raw == null || !raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber) {
                0.5f
            } else {
                val value = raw.asFloat
                if (value.isFinite() && value in 0f..1f) value else 0.5f
            }
        } catch (_: Exception) {
            0.5f
        }
        val rawQuote = obj.str("user_quote")?.trim().orEmpty()
        val quote: String? = when {
            rawQuote.isBlank() -> null
            userMsg == null -> rawQuote
            userMsg.contains(rawQuote) -> rawQuote
            else -> null
        }
        return ExtractedFact(subject, predicate, factObj, confidence, quote)
    }

    private fun parseRelationship(el: JsonElement): ExtractedRelationship? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val from = obj.str("from")?.trim().orEmpty()
        val to = obj.str("to")?.trim().orEmpty()
        val type = obj.str("type")?.trim().orEmpty()
        if (from.isBlank() || to.isBlank() || type.isBlank()) return null
        return ExtractedRelationship(from, to, type)
    }

    private fun parseCommitment(el: JsonElement): ExtractedCommitment? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val what = obj.str("what")?.trim().orEmpty()
        if (what.isBlank()) return null
        return ExtractedCommitment(
            what = what,
            whenDue = obj.str("when_due")?.trim()?.ifBlank { null },
            priority = obj.str("priority")?.trim()?.ifBlank { null }
        )
    }
}
