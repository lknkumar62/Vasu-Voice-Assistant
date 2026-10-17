package com.vasu.assistant

import com.vasu.assistant.core.offline.VoskCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-8 Pass B: the catalogue must stay internally consistent and the zip
 * unpacker's path mapper must reject every traversal shape before any IO.
 */
class VoskCatalogTest {

    @Test
    fun `language mapping picks en for english tags and hi otherwise`() {
        assertEquals("en-us-small", VoskCatalog.modelForLanguage("en-US").id)
        assertEquals("en-us-small", VoskCatalog.modelForLanguage("EN-GB").id)
        assertEquals("en-us-small", VoskCatalog.modelForLanguage(" en-in ").id)
        assertEquals("hi-small", VoskCatalog.modelForLanguage("hi-IN").id)
        assertEquals("hi-small", VoskCatalog.modelForLanguage("").id)
        assertEquals("hi-small", VoskCatalog.modelForLanguage("mr-IN").id)
    }

    @Test
    fun `catalogue is well-formed`() {
        assertTrue(VoskCatalog.MODELS.size >= 2)
        assertEquals(VoskCatalog.MODELS.size, VoskCatalog.MODELS.map { it.id }.toSet().size)
        for (spec in VoskCatalog.MODELS) {
            assertTrue(spec.url.startsWith("https://"))
            assertTrue(spec.url.endsWith(".zip"))
            assertTrue(spec.sizeMb > 0)
            assertTrue(spec.zipRoot.isNotBlank())
        }
    }

    @Test
    fun `relative entry path strips the model root`() {
        val root = "vosk-model-small-en-us-0.15"
        assertEquals("am/final.mdl", VoskCatalog.relativeEntryPath("$root/am/final.mdl", root))
        assertEquals("conf/mfcc.conf", VoskCatalog.relativeEntryPath("$root/conf/mfcc.conf", root))
        assertNull(VoskCatalog.relativeEntryPath(root, root))              // bare root dir
        assertNull(VoskCatalog.relativeEntryPath("other-root/am/x", root)) // sibling folder
    }

    @Test
    fun `relative entry path rejects traversal and absolute shapes`() {
        val root = "vosk-model-small-en-us-0.15"
        assertNull(VoskCatalog.relativeEntryPath("$root/../evil.txt", root))
        assertNull(VoskCatalog.relativeEntryPath("$root/./am", root))
        assertNull(VoskCatalog.relativeEntryPath("/etc/passwd", root))
        assertNull(VoskCatalog.relativeEntryPath("..\\windows\\evil", root))
        assertEquals("am/final.mdl", VoskCatalog.relativeEntryPath("$root\\am\\final.mdl", root))
    }
}
