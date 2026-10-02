package com.vasu.assistant.core.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionEngineTest {

    private class FakeStepExecutor : StepExecutor {
        var handler: suspend (MissionStep) -> ActionResult = {
            ActionResult.success(it.action, "ok")
        }
        val invoked = mutableListOf<String>()

        override suspend fun executeStep(step: MissionStep): ActionResult {
            invoked += step.action
            return handler(step)
        }
    }

    private fun engineWith(executor: FakeStepExecutor) = MissionEngine(executor)

    @Test
    fun `timeout aborts mission`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = {
            delay(5_000)
            ActionResult.success(it.action, "ok")
        }
        val engine = engineWith(fake)
        val mission = Mission(name = "m", steps = listOf(MissionStep("sleep")))
        val result = engine.executeMission(mission, missionTimeoutMs = 60_000, stepTimeoutMs = 100)
        assertFalse(result.success)
        assertTrue(result.message.contains("timeout"))
        assertEquals(0, (result.data?.get("stepsCompleted") as? Number)?.toInt())
    }

    @Test
    fun `step timeout respects per-step timeoutMs override`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = {
            delay(5_000)
            ActionResult.success(it.action, "ok")
        }
        val engine = engineWith(fake)
        val step = MissionStep("sleep", timeoutMs = 150)
        val result = engine.executeMission(Mission(name = "m", steps = listOf(step)), stepTimeoutMs = 60_000)
        assertFalse(result.success)
        assertTrue(result.message.contains("timeout after 150ms"))
    }

    @Test
    fun `cancel-safe sequencing rethrows when scope cancelled`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = {
            delay(5_000)
            ActionResult.success(it.action, "ok")
        }
        val engine = engineWith(fake)
        try {
            kotlinx.coroutines.withTimeout(5_000) {
                var thrown: CancellationException? = null
                val deferred = async {
                    try {
                        engine.executeMission(Mission(name = "m", steps = listOf(MissionStep("sleep"))))
                    } catch (e: CancellationException) {
                        thrown = e
                        throw e
                    }
                }
                delay(100)
                deferred.cancel()
                try { deferred.await() } catch (e: CancellationException) {}
                assertTrue("expected CancellationException to propagate from executeMission", thrown != null)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    @Test
    fun `cancellation from step executor yields cancelled failure`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = { throw CancellationException("inner cancel") }
        val engine = engineWith(fake)
        val result = engine.executeMission(Mission(name = "m", steps = listOf(MissionStep("x"))))
        assertFalse(result.success)
        assertEquals("CANCELLED", result.error)
        assertTrue(result.message.contains("cancelled"))
    }

    @Test
    fun `continueOnError lets mission proceed past failed step`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = { step ->
            when (step.action) {
                "bad" -> ActionResult.error("bad", "boom", "STEP_FAILED")
                else -> ActionResult.success(step.action, "ok")
            }
        }
        val engine = engineWith(fake)
        val mission = Mission(
            name = "m",
            steps = listOf(
                MissionStep("bad", continueOnError = true),
                MissionStep("good")
            )
        )
        val result = engine.executeMission(mission)
        assertTrue(result.success)
        assertEquals(listOf("bad", "good"), fake.invoked)
        assertEquals(2, (result.data?.get("stepsCompleted") as? Number)?.toInt())
    }

    @Test
    fun `max-steps rejection`() = runBlocking {
        val fake = FakeStepExecutor()
        val engine = engineWith(fake)
        val steps = (1..51).map { MissionStep("s$it") }
        val created = engine.createMission("big", steps)
        assertFalse(created.success)
        assertTrue(created.message.contains("MAX_STEPS"))
        assertTrue(engine.missions.value.isEmpty())
        val direct = engine.executeMission(Mission(name = "big", steps = steps))
        assertFalse(direct.success)
        assertTrue(direct.message.contains("MAX_STEPS"))
    }

    @Test
    fun `budget timeout surfaces as failure message`() = runBlocking {
        val fake = FakeStepExecutor()
        fake.handler = {
            delay(200)
            ActionResult.success(it.action, "ok")
        }
        val engine = engineWith(fake)
        val steps = (1..10).map { MissionStep("s$it") }
        val result = engine.executeMission(
            Mission(name = "m", steps = steps),
            missionTimeoutMs = 350
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("timed out"))
        val completed = (result.data?.get("stepsCompleted") as? Number)?.toInt() ?: -1
        assertTrue(completed in 0 until 10)
    }

    @Test
    fun `precondition aborts early with clear message`() = runBlocking {
        val fake = FakeStepExecutor()
        val engine = engineWith(fake)
        engine.permissionChecker = { perm -> perm == "granted" }
        val mission = Mission(
            name = "m",
            steps = listOf(
                MissionStep("needs", requiresPermission = "denied"),
                MissionStep("never")
            )
        )
        val result = engine.executeMission(mission)
        assertFalse(result.success)
        assertTrue(result.message.contains("missing permission: denied"))
        assertTrue(fake.invoked.isEmpty())
    }

    @Test
    fun `success path records structured steps`() = runBlocking {
        val fake = FakeStepExecutor()
        val engine = engineWith(fake)
        val mission = Mission(name = "m", steps = listOf(MissionStep("a"), MissionStep("b")))
        val result = engine.executeMission(mission)
        assertTrue(result.success)
        assertEquals(2, (result.data?.get("stepsCompleted") as? Number)?.toInt())
        assertEquals(2, (result.data?.get("stepsTotal") as? Number)?.toInt())
    }
}
