package com.vasu.assistant

import com.vasu.assistant.core.security.EnrollmentState
import com.vasu.assistant.core.security.RoleManager
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.core.security.VoiceEnrollmentManager
import com.vasu.assistant.core.security.VoiceStore
import com.vasu.assistant.core.security.VoiceStoreSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Enrollment state-machine contract: 3 samples of 1-5 s with acceptable SNR
 * must flow Recording → SampleRecorded(1..2) → Processing → Completed.
 * (Regression: SampleRecorded was previously rejected, capping enrollment at
 * a single sample and making 3-sample enrollment impossible.)
 */
class VoiceEnrollmentManagerTest {

    private class FakeVoiceStore : VoiceStore {
        var snapshot: VoiceStoreSnapshot = VoiceStoreSnapshot()
        override fun load(): VoiceStoreSnapshot = snapshot
        override fun save(snapshot: VoiceStoreSnapshot) {
            this.snapshot = snapshot
        }
    }

    private val store = FakeVoiceStore()
    private val roleManager = RoleManager(store)
    private val manager = VoiceEnrollmentManager(roleManager)

    /** 2 s @ 16 kHz: loud leading quarter, near-silent tail → SNR passes. */
    private fun validSample(): FloatArray {
        val n = 16000 * 2
        return FloatArray(n) { i ->
            if (i < n / 4) {
                (0.5f * sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toFloat()
            } else {
                0.0005f
            }
        }
    }

    @Test
    fun threeValidSamples_flowThroughToCompleted() {
        manager.startEnrollment()
        assertEquals(EnrollmentState.Recording, manager.state.value)

        manager.recordSample(validSample())
        assertEquals(EnrollmentState.SampleRecorded(1), manager.state.value)

        manager.recordSample(validSample())
        assertEquals(EnrollmentState.SampleRecorded(2), manager.state.value)

        manager.recordSample(validSample())
        assertTrue(manager.state.value is EnrollmentState.Processing)

        manager.completeEnrollment("Priya", UserRole.BOSS)

        val state = manager.state.value
        assertTrue(state is EnrollmentState.Completed)
        assertEquals("Priya", (state as EnrollmentState.Completed).voice.name)

        val voices = roleManager.listVoices()
        assertEquals(1, voices.size)
        assertEquals(UserRole.BOSS, voices[0].role)
        assertEquals(128, voices[0].embedding.size)

        // The enroller becomes the active speaker at completion.
        assertEquals("Priya", roleManager.currentSpeaker.value?.name)
        assertEquals(UserRole.BOSS, roleManager.currentSpeaker.value?.role)
    }

    @Test
    fun secondSample_acceptedWhileStateIsSampleRecorded() {
        manager.startEnrollment()
        manager.recordSample(validSample())
        assertTrue(manager.state.value is EnrollmentState.SampleRecorded)

        manager.recordSample(validSample())

        assertEquals(EnrollmentState.SampleRecorded(2), manager.state.value)
    }

    @Test
    fun shortSample_rejectedWithError() {
        manager.startEnrollment()
        manager.recordSample(FloatArray(8000)) // 0.5 s

        val state = manager.state.value
        assertTrue(state is EnrollmentState.Error)
    }

    @Test
    fun tooLongSample_rejectedWithError() {
        manager.startEnrollment()
        manager.recordSample(FloatArray(16000 * 6)) // 6 s

        assertTrue(manager.state.value is EnrollmentState.Error)
    }

    @Test
    fun noisySample_rejectedWithError() {
        manager.startEnrollment()
        // Uniform loud signal throughout: leading and trailing quarters have
        // equal energy → SNR ≈ 0 dB → rejected.
        val n = 16000 * 2
        val noisy = FloatArray(n) { i ->
            (0.5f * sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toFloat()
        }
        manager.recordSample(noisy)

        assertTrue(manager.state.value is EnrollmentState.Error)
    }

    @Test
    fun completeEnrollment_beforeEnoughSamples_fails() {
        manager.startEnrollment()
        manager.recordSample(validSample())

        manager.completeEnrollment("Too early", UserRole.BOSS)

        assertTrue(manager.state.value is EnrollmentState.Error)
        assertTrue(roleManager.listVoices().isEmpty())
    }

    @Test
    fun cancel_resetsFlow_andClearsSamples() {
        manager.startEnrollment()
        manager.recordSample(validSample())
        manager.cancelEnrollment()

        assertEquals(EnrollmentState.Idle, manager.state.value)

        // Fresh flow starts from zero samples again.
        manager.startEnrollment()
        manager.recordSample(validSample())
        assertEquals(EnrollmentState.SampleRecorded(1), manager.state.value)
        assertNotNull(roleManager.listVoices())
        assertTrue(roleManager.listVoices().isEmpty())
    }

    @Test
    fun enrollment_survivesRestart() {
        manager.startEnrollment()
        manager.recordSample(validSample())
        manager.recordSample(validSample())
        manager.recordSample(validSample())
        manager.completeEnrollment("Boss", UserRole.BOSS)

        val restored = RoleManager(store).listVoices()

        assertEquals(1, restored.size)
        assertEquals("Boss", restored[0].name)
        assertEquals(UserRole.BOSS, restored[0].role)
    }
}
