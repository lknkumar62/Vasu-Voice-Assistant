package com.vasu.assistant

import com.vasu.assistant.core.agent.AgentRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AgentRunnerTest {

    private fun step(id: String, timeoutMs: Long? = null) = AgentRunner.StepSpec<String>(id, timeoutMs)

    @Test
    fun `success run completes all steps`() = runBlocking {
        val runner = AgentRunner()
        val seen = mutableListOf<String>()
        val result = runner.run(listOf(step("a"), step("b"))) { spec ->
            seen += spec.id
            spec.id
        }
        assertTrue(result.success)
        assertEquals(listOf("a", "b"), result.completedIds)
        assertEquals(2, result.stepsRun)
        assertEquals(listOf("a", "b"), seen)
        assertNull(result.lastErrorKind)
        assertNull(result.lastErrorMessage)
        assertNull(result.timedOutAtStepId)
    }

    @Test
    fun `step timeout fails with STEP_TIMEOUT and halts`() = runBlocking {
        val runner = AgentRunner()
        val seen = mutableListOf<String>()
        val result = runner.run(listOf(step("slow", timeoutMs = 50), step("never"))) { spec ->
            seen += spec.id
            delay(5_000)
            spec.id
        }
        assertFalse(result.success)
        assertEquals(AgentRunner.STEP_TIMEOUT, result.lastErrorKind)
        assertEquals("slow", result.timedOutAtStepId)
        assertTrue(result.completedIds.isEmpty())
        assertEquals(1, result.stepsRun)
        assertEquals(listOf("slow"), seen)
    }

    @Test
    fun `task timeout fails with TASK_TIMEOUT and reports in-flight step`() = runBlocking {
        val runner = AgentRunner(taskTimeoutMs = 300)
        val result = runner.run(listOf(step("fast"), step("slow"))) { spec ->
            if (spec.id == "fast") return@run spec.id
            delay(5_000)
            spec.id
        }
        assertFalse(result.success)
        assertEquals(AgentRunner.TASK_TIMEOUT, result.lastErrorKind)
        assertEquals("slow", result.timedOutAtStepId)
        assertEquals(listOf("fast"), result.completedIds)
    }

    @Test
    fun `step exception maps to EXCEPTION and halts`() = runBlocking {
        val runner = AgentRunner()
        val executed = mutableListOf<String>()
        val result = runner.run(listOf(step("ok"), step("boom"), step("never"))) { spec ->
            executed += spec.id
            if (spec.id == "boom") throw IllegalStateException("kaboom")
            spec.id
        }
        assertFalse(result.success)
        assertEquals(AgentRunner.EXCEPTION, result.lastErrorKind)
        assertEquals("kaboom", result.lastErrorMessage)
        assertEquals(listOf("ok"), result.completedIds)
        assertEquals(2, result.stepsRun)
        assertEquals(listOf("ok", "boom"), executed)
    }

    @Test
    fun `max steps cap rejects with TOO_MANY_STEPS`() = runBlocking {
        val runner = AgentRunner(maxStepsPerTask = 2)
        var called = false
        val result = runner.run(listOf(step("a"), step("b"), step("c"))) { spec ->
            called = true
            spec.id
        }
        assertFalse(result.success)
        assertEquals(AgentRunner.TOO_MANY_STEPS, result.lastErrorKind)
        assertEquals(0, result.stepsRun)
        assertTrue(result.completedIds.isEmpty())
        assertFalse(called)
    }

    @Test
    fun `cancellation propagates and completed ids are not over-counted`() = runBlocking {
        val runner = AgentRunner()
        val completedMirror = mutableListOf<String>()
        val threw = AtomicBoolean(false)
        val job = launch {
            try {
                runner.run(listOf(step("first"), step("second"))) { spec ->
                    if (spec.id == "first") {
                        completedMirror += spec.id
                        return@run spec.id
                    }
                    delay(5_000)
                    completedMirror += spec.id
                    spec.id
                }
            } catch (ce: CancellationException) {
                threw.set(true)
                throw ce
            }
        }
        delay(100)
        job.cancel()
        job.join()
        assertTrue(threw.get())
        assertEquals(listOf("first"), completedMirror)
    }

    @Test
    fun `empty step list succeeds`() = runBlocking {
        val runner = AgentRunner()
        val result = runner.run(emptyList<AgentRunner.StepSpec<String>>()) { spec -> spec.id }
        assertTrue(result.success)
        assertEquals(0, result.stepsRun)
        assertTrue(result.completedIds.isEmpty())
        assertNotNull(result)
    }
}
