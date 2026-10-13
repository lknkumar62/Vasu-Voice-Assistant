package com.vasu.assistant

import com.vasu.assistant.core.memory.VaultExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure VaultExtractor contract: prompt shape, tolerant parsing,
 * store guards (confidence, blank objects, quote gate, entity allowlist),
 * and stable source refs. No LLM, no Android APIs; Gson runs on JVM.
 */
class VaultExtractorTest {

    private fun factJson(
        subject: String = "Anna",
        predicate: String = "birthday_is",
        obj: String = "March 15",
        confidence: String = "1.0",
        quote: String? = null
    ): String {
        val quotePart = if (quote == null) "" else ""","user_quote":"$quote""""
        return """{"subject":"$subject","predicate":"$predicate","object":"$obj","confidence":$confidence$quotePart}"""
    }

    private fun envelope(
        entities: String = "[]",
        facts: String = "[]",
        relationships: String = "[]",
        commitments: String = "[]"
    ): String {
        return """{"entities":$entities,"facts":$facts,"relationships":$relationships,"commitments":$commitments}"""
    }

    @Test
    fun `fenced json response is stripped and parsed`() {
        val raw = "```json\n${envelope(facts = "[${factJson()}]")}\n```"
        val result = VaultExtractor.parseExtractionResponse(raw)
        assertEquals(1, result.facts.size)
        assertEquals("Anna", result.facts[0].subject)
    }

    @Test
    fun `plain fence variant is stripped and parsed`() {
        val raw = "```\n${envelope(facts = "[${factJson()}]")}\n```"
        val result = VaultExtractor.parseExtractionResponse(raw)
        assertEquals(1, result.facts.size)
    }

    @Test
    fun `missing arrays yield empty lists`() {
        val result = VaultExtractor.parseExtractionResponse("{}")
        assertTrue(result.entities.isEmpty())
        assertTrue(result.facts.isEmpty())
        assertTrue(result.relationships.isEmpty())
        assertTrue(result.commitments.isEmpty())
    }

    @Test
    fun `non-object and invalid json yield empty result`() {
        for (raw in listOf("Not JSON at all", "[1,2,3]", "", "```json\nnot json\n```")) {
            val result = VaultExtractor.parseExtractionResponse(raw)
            assertTrue("expected empty for: $raw", result.facts.isEmpty())
            assertTrue("expected empty for: $raw", result.entities.isEmpty())
        }
    }

    @Test
    fun `out-of-range and non-numeric confidence fall back to 0_5`() {
        val raw = envelope(
            facts = "[${factJson(confidence = "1.5")}," +
                "${factJson(confidence = "-0.2")}," +
                """{"subject":"Anna","predicate":"likes","object":"tea","confidence":"high"},""" +
                """{"subject":"Anna","predicate":"likes","object":"coffee"}]"""
        )
        val facts = VaultExtractor.parseExtractionResponse(raw).facts
        assertEquals(4, facts.size)
        facts.forEach { assertEquals(0.5f, it.confidence, 0.0001f) }
    }

    @Test
    fun `in-range confidence is preserved`() {
        val raw = envelope(facts = "[${factJson(confidence = "0.9")}]")
        val facts = VaultExtractor.parseExtractionResponse(raw).facts
        assertEquals(1, facts.size)
        assertEquals(0.9f, facts[0].confidence, 0.0001f)
    }

    @Test
    fun `facts with blank object are dropped`() {
        val raw = envelope(
            facts = """[{"subject":"Anna","predicate":"birthday_is","object":"   ","confidence":1.0},""" +
                """{"subject":"Anna","predicate":"works_at","confidence":1.0},""" +
                "${factJson()}]"
        )
        val facts = VaultExtractor.parseExtractionResponse(raw).facts
        assertEquals(1, facts.size)
        assertEquals("March 15", facts[0].obj)
    }

    @Test
    fun `quote kept only when it is exact user text`() {
        val userMsg = "My sister Anna's birthday is March 15"
        val good = factJson(quote = "birthday is March 15")
        val bad = factJson(predicate = "lives_in", obj = "Paris", quote = "Anna lives in Paris")
        val result = VaultExtractor.parseExtractionResponse(envelope(facts = "[$good,$bad]"), userMsg)
        assertEquals(2, result.facts.size)
        assertEquals("birthday is March 15", result.facts[0].userQuote)
        assertNull(result.facts[1].userQuote)
    }

    @Test
    fun `entity type allowlist drops unknown types`() {
        val raw = envelope(
            entities = """[{"name":"Anna","type":"person"},""" +
                """{"name":"Zog","type":"alien"},""" +
                """{"name":"","type":"person"}]"""
        )
        val entities = VaultExtractor.parseExtractionResponse(raw).entities
        assertEquals(1, entities.size)
        assertEquals("Anna", entities[0].name)
        assertEquals("person", entities[0].type)
    }

    @Test
    fun `sourceRef is stable 64-hex and input-sensitive`() {
        val first = VaultExtractor.sourceRef("hi", "hello")
        val second = VaultExtractor.sourceRef("hi", "hello")
        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(first, VaultExtractor.sourceRef("hi", "other"))
        assertNotEquals(first, VaultExtractor.sourceRef("other", "hello"))
    }

    @Test
    fun `prompt contains both messages and demands json-only`() {
        val user = "My birthday is June 1"
        val assistant = "Nice, I will remember it!"
        val prompt = VaultExtractor.buildExtractionPrompt(user, assistant)
        assertTrue(prompt.contains(user))
        assertTrue(prompt.contains(assistant))
        assertTrue(prompt.contains("ONLY"))
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("never evidence"))
    }

    @Test
    fun `prompt truncates long inputs to 2000 chars`() {
        val long = "x".repeat(5000)
        val prompt = VaultExtractor.buildExtractionPrompt(long, long)
        assertTrue(prompt.contains("x".repeat(2000)))
        assertFalse(prompt.contains("x".repeat(2001)))
    }

    @Test
    fun `fact-bearing gate matches spec keywords`() {
        assertTrue(VaultExtractor.looksFactBearing("My sister Anna"))
        assertTrue(VaultExtractor.looksFactBearing("mera naam Ravi hai"))
        assertTrue(VaultExtractor.looksFactBearing("I am an engineer"))
        assertTrue(VaultExtractor.looksFactBearing("I like filter coffee"))
        assertTrue(VaultExtractor.looksFactBearing("Her birthday is March 15"))
        assertTrue(VaultExtractor.looksFactBearing("Remember my car number"))
        assertTrue(!VaultExtractor.looksFactBearing("What is the time?"))
    }

    @Test
    fun `commitments require non-blank what`() {
        val raw = envelope(
            commitments = """[{"what":"Call mom","when_due":"2026-03-14","priority":"high"},""" +
                """{"what":"   "}]"""
        )
        val commitments = VaultExtractor.parseExtractionResponse(raw).commitments
        assertEquals(1, commitments.size)
        assertEquals("Call mom", commitments[0].what)
        assertEquals("2026-03-14", commitments[0].whenDue)
        assertEquals("high", commitments[0].priority)
    }
}
