package com.vasu.assistant

import com.vasu.assistant.core.ai.AiErrorKind
import com.vasu.assistant.core.stt.SttErrorKind
import com.vasu.assistant.core.wakeword.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the failure taxonomy. The bug these cover: every recognition failure used
 * to arrive as an opaque string, so "no speech" and "the recogniser was misused"
 * were indistinguishable to the UI, and ERROR_CLIENT surfaced as "Client error".
 */
class ErrorTaxonomyTest {

    @Test
    fun `no speech is distinct from a recognition fault`() {
        assertNotEquals(SttErrorKind.NO_SPEECH, SttErrorKind.RECOGNITION_ERROR)
    }

    @Test
    fun `retryable stt failures do not demand user action`() {
        SttErrorKind.values().filter { it.canRetry }.forEach {
            assertFalse(
                "$it is retryable, so it must not also require user action",
                it.needsUserAction
            )
        }
    }

    @Test
    fun `permission and service failures require user action and are not silently retried`() {
        listOf(
            SttErrorKind.MIC_PERMISSION_DENIED,
            SttErrorKind.SERVICE_UNAVAILABLE,
            SttErrorKind.LANGUAGE_UNAVAILABLE
        ).forEach {
            assertTrue("$it must require user action", it.needsUserAction)
            assertFalse("$it must not be auto-retried", it.canRetry)
        }
    }

    @Test
    fun `every stt error kind is classified exactly one way or neither`() {
        // UNKNOWN and AUDIO_ERROR are deliberately neither: we do not know enough
        // to promise a retry helps, nor what the user should change.
        SttErrorKind.values().forEach {
            assertFalse(
                "$it cannot be both retryable and blocked on the user",
                it.canRetry && it.needsUserAction
            )
        }
    }

    @Test
    fun `a missing api key is never treated as a transient failure`() {
        // Retrying a request with no key configured just burns time and would let
        // the UI show a spinner instead of pointing the user at Settings.
        assertFalse(AiErrorKind.NOT_CONFIGURED.isTransient)
        assertFalse(AiErrorKind.INVALID_KEY.isTransient)
        assertFalse(AiErrorKind.PERMISSION_DENIED.isTransient)
        assertFalse(AiErrorKind.QUOTA_EXCEEDED.isTransient)
    }

    @Test
    fun `network and server failures are transient`() {
        assertTrue(AiErrorKind.OFFLINE.isTransient)
        assertTrue(AiErrorKind.TIMEOUT.isTransient)
        assertTrue(AiErrorKind.RATE_LIMITED.isTransient)
        assertTrue(AiErrorKind.SERVER_ERROR.isTransient)
    }

    @Test
    fun `safety blocks are not retried`() {
        // Resending an identical blocked prompt gets an identical block.
        assertFalse(AiErrorKind.BLOCKED_BY_SAFETY.isTransient)
    }

    /**
     * The wake word model used to fail as a bare `false`, so the app could only say
     * "unavailable". Each failure needs its own explanation because the fixes
     * differ: bundle the asset, replace a corrupt file, or match the input shape.
     */
    @Test
    fun `every wake word failure explains itself distinctly`() {
        val failures = ModelStatus.values().filter { it != ModelStatus.READY }
        val details = failures.map { it.detail }

        failures.forEach {
            assertTrue("$it must carry an explanation", it.detail.isNotBlank())
        }
        assertEquals(
            "each wake word failure must read differently",
            details.size,
            details.distinct().size
        )
    }

    @Test
    fun `only READY reports the wake word model as usable`() {
        assertNotEquals(ModelStatus.ASSET_MISSING, ModelStatus.LOAD_FAILED)
        ModelStatus.values().filter { it != ModelStatus.READY }.forEach {
            assertFalse(
                "$it must not read as a working wake word",
                it.detail.contains("loaded", ignoreCase = true) &&
                    !it.detail.contains("not", ignoreCase = true)
            )
        }
    }
}
