package com.vasu.assistant

import com.vasu.assistant.core.tts.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun `english multi sentence incremental feeding emits first sentence early`() {
        val splitter = SentenceSplitter()
        val first = splitter.feed("Hello there. How are")
        assertEquals(listOf("Hello there."), first)
        val second = splitter.feed(" you? I am fine.")
        assertEquals(listOf("How are you?"), second)
        val tail = splitter.flush()
        assertEquals("I am fine.", tail)
    }

    @Test
    fun `hindi danda splitting`() {
        val splitter = SentenceSplitter()
        val first = splitter.feed("नमस्ते। आप कैसे हैं? मैं ठीक हूँ।")
        assertEquals(listOf("नमस्ते।", "आप कैसे हैं?"), first)
        val second = splitter.feed(" अगला वाक्य॥ पूरा हुआ।")
        assertEquals(listOf("मैं ठीक हूँ।", "अगला वाक्य॥"), second)
        assertEquals("पूरा हुआ।", splitter.flush())
    }

    @Test
    fun `hindi double danda splits`() {
        val out = SentenceSplitter.splitFull("पहला भाग॥ दूसरा भाग।")
        assertEquals(listOf("पहला भाग॥", "दूसरा भाग।"), out)
    }

    @Test
    fun `abbreviations do not split`() {
        val out = SentenceSplitter.splitFull("Mr. Sharma met Dr. Singh. They left vs. others.")
        assertEquals(listOf("Mr. Sharma met Dr. Singh.", "They left vs. others."), out)
    }

    @Test
    fun `e_g and i_e do not split`() {
        val out = SentenceSplitter.splitFull("Use tools, e.g. hammer, or i.e. nothing else. Done.")
        assertEquals(listOf("Use tools, e.g. hammer, or i.e. nothing else.", "Done."), out)
    }

    @Test
    fun `decimals versions and times do not split`() {
        val out = SentenceSplitter.splitFull("Pi is 3.14 and version v1.2.3 ships at 5.30 pm. Next.")
        assertEquals(listOf("Pi is 3.14 and version v1.2.3 ships at 5.30 pm.", "Next."), out)
    }

    @Test
    fun `urls emails do not split`() {
        val out = SentenceSplitter.splitFull("Visit https://example.com/page. Email me at user@test.com. Bye.")
        assertEquals(
            listOf("Visit https://example.com/page.", "Email me at user@test.com.", "Bye."),
            out
        )
    }

    @Test
    fun `initials do not split`() {
        val out = SentenceSplitter.splitFull("Signed by A. B. Kumar. Thanks.")
        assertEquals(listOf("Signed by A. B. Kumar.", "Thanks."), out)
    }

    @Test
    fun `flush emits trailing partial`() {
        val splitter = SentenceSplitter()
        splitter.feed("Complete sentence. Trailing")
        assertEquals("Trailing", splitter.flush())
        assertEquals(null, splitter.flush())
    }

    @Test
    fun `empty and whitespace input`() {
        val splitter = SentenceSplitter()
        assertEquals(emptyList<String>(), splitter.feed(""))
        assertEquals(emptyList<String>(), splitter.feed("   \n  "))
        assertEquals(null, splitter.flush())
        assertEquals(emptyList<String>(), SentenceSplitter.splitFull("   "))
    }

    @Test
    fun `terminator with closing quote splits`() {
        val out = SentenceSplitter.splitFull("She said \"Hello.\" Then she left.")
        assertEquals(listOf("She said \"Hello.\"", "Then she left."), out)
    }

    @Test
    fun `newline paragraph break splits`() {
        val out = SentenceSplitter.splitFull("First paragraph\nSecond paragraph")
        assertEquals(listOf("First paragraph", "Second paragraph"), out)
    }

    @Test
    fun `reassembly modulo whitespace`() {
        val original = "Namaste. Aap kaise hain? Main theek hoon. https://example.com dekhiye. Dr. Sharma milenge."
        val parts = SentenceSplitter.splitFull(original)
        val rejoined = parts.joinToString(" ")
        assertEquals(original, rejoined)
    }

    @Test
    fun `etc does not split`() {
        val out = SentenceSplitter.splitFull("He bought apples, etc. Then he left.")
        // Conservative: etc. protects its dot, so the split happens at the next confident boundary
        assertEquals(listOf("He bought apples, etc. Then he left."), out)
    }
}
