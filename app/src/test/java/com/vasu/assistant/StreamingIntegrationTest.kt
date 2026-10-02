package com.vasu.assistant

import com.vasu.assistant.core.tts.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingIntegrationTest {

    @Test
    fun `first sentence is emitted from first chunk without waiting for rest of stream`() {
        val splitter = SentenceSplitter()
        val emitted = mutableListOf<String>()

        // Chunk 1 of N: boundary not yet confident (terminator alone) -> nothing emitted.
        emitted.addAll(splitter.feed("Hello world."))
        assertTrue(emitted.isEmpty())

        // Chunk 2: both boundaries become confident and pop out as the stream
        // continues, even though the stream has more bytes coming.
        emitted.addAll(splitter.feed(" How are you doing? I am"))
        assertEquals(listOf("Hello world.", "How are you doing?"), emitted)

        emitted.addAll(splitter.feed(" fine, thanks."))
        assertEquals(listOf("Hello world.", "How are you doing?"), emitted)

        splitter.flush()?.let { emitted.add(it) }
        assertEquals(listOf("Hello world.", "How are you doing?", "I am fine, thanks."), emitted)
    }

    @Test
    fun `flush emits the trailing tail`() {
        val splitter = SentenceSplitter()
        assertTrue(splitter.feed("Dhamma santi hai").isEmpty())
        val tail = splitter.flush()
        assertEquals("Dhamma santi hai", tail)
        assertEquals(null, splitter.flush())
    }

    @Test
    fun `chunked feeding preserves full text when joined`() {
        val chunks = listOf("Aap", " theek ", "hain. Main bhi ", "theek hoon. Dhanyavaad")
        val splitter = SentenceSplitter()
        val sentences = mutableListOf<String>()
        for (chunk in chunks) sentences.addAll(splitter.feed(chunk))
        splitter.flush()?.let { sentences.add(it) }

        assertEquals(listOf("Aap theek hain.", "Main bhi theek hoon.", "Dhanyavaad"), sentences)
        // Joined sentence output covers the full response text (whitespace-normalized).
        val reconstructed = sentences.joinToString(" ")
        val originalNormalized = chunks.joinToString("").trim().replace(Regex("\\s+"), " ")
        assertEquals(originalNormalized, reconstructed)
    }
}
