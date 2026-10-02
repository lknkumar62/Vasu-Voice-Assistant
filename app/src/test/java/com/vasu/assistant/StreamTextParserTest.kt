package com.vasu.assistant

import com.vasu.assistant.core.ai.StreamTextParser
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTextParserTest {

    @Test
    fun `gemini single delta`() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Namaste!"}]},"index":0}],"usageMetadata":{"promptTokenCount":8,"candidatesTokenCount":2,"totalTokenCount":10},"modelVersion":"gemini-1.5-flash"}"""
        assertEquals(listOf("Namaste!"), StreamTextParser.parseChunk(line))
    }

    @Test
    fun `gemini multi candidate ignores all but first`() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"first"}]},"index":0},{"content":{"role":"model","parts":[{"text":"second"}]},"index":1}]}"""
        assertEquals(listOf("first"), StreamTextParser.parseChunk(line))
    }

    @Test
    fun `gemini multiple parts in first candidate are concatenated as separate deltas`() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"hello"},{"text":" world"}]},"index":0}]}"""
        assertEquals(listOf("hello", " world"), StreamTextParser.parseChunk(line))
    }

    @Test
    fun `gemini chunk without text yields nothing`() {
        val line = """data: {"candidates":[{"finishReason":"STOP"}]}"""
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk(line))
    }

    @Test
    fun `claude content_block_delta`() {
        val line = """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"VASU here"}}"""
        assertEquals(listOf("VASU here"), StreamTextParser.parseChunk(line))
    }

    @Test
    fun `claude ping and event lines ignored`() {
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("event: ping"))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("""data: {"type":"ping"}"""))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("event: content_block_delta"))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("""data: {"type":"message_stop"}"""))
    }

    @Test
    fun `empty data line yields no items`() {
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk(""))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("   "))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("data:"))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("data: "))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk(":"))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("data: [DONE]"))
    }

    @Test
    fun `malformed JSON is skipped without crashing`() {
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("data: {not json"))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("""data: {"candidates": oops}"""))
        assertEquals(emptyList<String>(), StreamTextParser.parseChunk("data: \u0000\u0001garbage"))
    }

    @Test
    fun `mixed batch in one line with CRLF splits yields all deltas`() {
        val batch = "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\r\n" +
            "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\" world\"}]}}]}"
        assertEquals(listOf("Hello", " world"), StreamTextParser.parseChunk(batch))
    }

    @Test
    fun `json array payload yields all chunk deltas`() {
        val line = """data: [{"type":"content_block_delta","delta":{"text":"a"}},{"type":"content_block_delta","delta":{"text":"b"}}]"""
        assertEquals(listOf("a", "b"), StreamTextParser.parseChunk(line))
    }
}
