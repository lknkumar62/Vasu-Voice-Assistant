package com.vasu.assistant

import com.vasu.assistant.core.ai.ToolDefinition
import com.vasu.assistant.core.ai.ToolParameter
import com.vasu.assistant.core.ai.toGeminiSchemaType
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The previous version of this test built a local list of tool-name strings and
 * asserted `list.size >= 35` plus `assertNotNull` on string literals. It touched no
 * production code and could not fail. These exercise the real types instead.
 */
class ToolRouterTest {

    @Test
    fun `tool definition derives required role from risk level`() {
        val tool = ToolDefinition(
            name = "delete_file",
            description = "Delete a file",
            parameters = listOf(ToolParameter("path", "string", "Absolute path")),
            riskLevel = RiskLevel.HIGH
        )

        assertEquals(UserRole.BOSS, tool.requiredRole)
    }

    @Test
    fun `risk levels escalate the privilege they demand`() {
        assertTrue(
            "MEDIUM must demand more than LOW",
            RiskLevel.MEDIUM.requiredRole.priority > RiskLevel.LOW.requiredRole.priority
        )
        assertTrue(
            "HIGH must demand more than MEDIUM",
            RiskLevel.HIGH.requiredRole.priority > RiskLevel.MEDIUM.requiredRole.priority
        )
        assertTrue(
            "CRITICAL must demand at least as much as HIGH",
            RiskLevel.CRITICAL.requiredRole.priority >= RiskLevel.HIGH.requiredRole.priority
        )
    }

    @Test
    fun `guest cannot satisfy a high risk tool`() {
        // delete_file and friends must fail closed for an unverified speaker.
        assertTrue(UserRole.GUEST.priority < RiskLevel.HIGH.requiredRole.priority)
        assertTrue(UserRole.UNKNOWN.priority < RiskLevel.LOW.requiredRole.priority)
        assertTrue(UserRole.BLOCKED.priority < RiskLevel.LOW.requiredRole.priority)
    }

    @Test
    fun `optional parameters are not marked required`() {
        val optional = ToolParameter("label", "string", "Field label", required = false)
        val mandatory = ToolParameter("text", "string", "Text to type")

        assertTrue(mandatory.required)
        assertFalse(optional.required)
    }

    @Test
    fun `parameter types map onto gemini schema types`() {
        assertEquals("STRING", toGeminiSchemaType("string"))
        assertEquals("INTEGER", toGeminiSchemaType("int"))
        assertEquals("INTEGER", toGeminiSchemaType("Integer"))
        assertEquals("NUMBER", toGeminiSchemaType("float"))
        assertEquals("BOOLEAN", toGeminiSchemaType("boolean"))
        assertEquals("ARRAY", toGeminiSchemaType("list"))
        assertEquals("OBJECT", toGeminiSchemaType("map"))
    }

    @Test
    fun `unknown parameter types fall back to string rather than breaking the request`() {
        // Gemini rejects an unrecognised schema type outright, which would fail the
        // whole turn, so an unmapped type must degrade to STRING.
        assertEquals("STRING", toGeminiSchemaType("uri"))
        assertEquals("STRING", toGeminiSchemaType(""))
    }
}
