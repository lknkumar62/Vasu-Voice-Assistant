package com.vasu.assistant

import com.vasu.assistant.core.command.CommandRegistry
import com.vasu.assistant.core.command.LocalCommandRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-8 ("single registry"): every catalogued example must round-trip through
 * [LocalCommandRouter.match] to exactly the spec's tool with the spec's risk —
 * the Command Library can never advertise a command the matcher doesn't route.
 */
class CommandRegistryTest {

    @Test
    fun `every example routes to its registered tool`() {
        for (spec in CommandRegistry.ALL) {
            for (example in spec.examples) {
                val cmd = LocalCommandRouter.match(example)
                assertNotNull("no match for [${spec.id}] example: $example", cmd)
                assertEquals(
                    "wrong tool for [${spec.id}] example: $example",
                    spec.toolName, cmd!!.toolName
                )
            }
        }
    }

    @Test
    fun `required role matches the registered risk for every example`() {
        for (spec in CommandRegistry.ALL) {
            for (example in spec.examples) {
                val cmd = LocalCommandRouter.match(example)
                assertEquals(
                    "role mismatch for [${spec.id}] example: $example",
                    spec.risk.requiredRole, cmd!!.requiredRole
                )
            }
        }
    }

    @Test
    fun `registry and router cover exactly the same tools`() {
        val registryTools = CommandRegistry.ALL.map { it.toolName }.toSet()
        assertEquals(LocalCommandRouter.SUPPORTED_TOOLS, registryTools)
    }

    @Test
    fun `ids are unique and metadata non-blank`() {
        val ids = CommandRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CommandRegistry.ALL.all { it.category.isNotBlank() && it.title.isNotBlank() })
        assertTrue(CommandRegistry.ALL.all { it.examples.isNotEmpty() })
        assertTrue(CommandRegistry.byCategory().size >= 4)
    }
}
