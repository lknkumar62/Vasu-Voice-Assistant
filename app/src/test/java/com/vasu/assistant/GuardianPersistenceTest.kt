package com.vasu.assistant

import com.vasu.assistant.core.security.RoleManager
import com.vasu.assistant.core.security.StoredVoice
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.core.security.VoiceStore
import com.vasu.assistant.core.security.VoiceStoreSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice Guardian persistence contract:
 * enrollments + guardian toggle must survive an app restart,
 * current speaker must NOT (session-only by design).
 */
class GuardianPersistenceTest {

    private class FakeVoiceStore : VoiceStore {
        var snapshot: VoiceStoreSnapshot = VoiceStoreSnapshot()
        override fun load(): VoiceStoreSnapshot = snapshot
        override fun save(snapshot: VoiceStoreSnapshot) {
            this.snapshot = snapshot
        }
    }

    private val store = FakeVoiceStore()

    /** A fresh RoleManager over the same store = app process restart. */
    private fun restart(): RoleManager = RoleManager(store)

    @Test
    fun freshInstall_startsWithNoVoicesAndGuardianDisabled() {
        val rm = restart()
        assertTrue(rm.listVoices().isEmpty())
        assertFalse(rm.guardianEnabled.value)
        assertNull(rm.currentSpeaker.value)
    }

    @Test
    fun enrolledVoice_survivesRestart() {
        val enrolled = restart().enrollVoice(
            "Priya", UserRole.BOSS, floatArrayOf(0.1f, -0.2f, 0.3f)
        )

        val restored = restart().getVoice(enrolled.id)

        assertNotNull(restored)
        assertEquals("Priya", restored!!.name)
        assertEquals(UserRole.BOSS, restored.role)
        assertEquals(0, restored.verificationCount)
        assertArrayEquals(
            floatArrayOf(0.1f, -0.2f, 0.3f),
            restored.embedding,
            0f
        )
    }

    @Test
    fun guardianToggle_survivesRestart() {
        restart().setGuardianEnabled(true)
        assertTrue(restart().guardianEnabled.value)

        restart().setGuardianEnabled(false)
        assertFalse(restart().guardianEnabled.value)
    }

    @Test
    fun roleUpdate_survivesRestart() {
        val rm = restart()
        val voice = rm.enrollVoice("Sam", UserRole.GUEST, floatArrayOf(1f))

        assertTrue(rm.updateVoiceRole(voice.id, UserRole.FAMILY))

        assertEquals(UserRole.FAMILY, restart().getVoice(voice.id)?.role)
    }

    @Test
    fun verificationStats_surviveRestart() {
        val rm = restart()
        val voice = rm.enrollVoice("Boss", UserRole.BOSS, floatArrayOf(1f))

        rm.recordVerification(voice.id)
        rm.recordVerification(voice.id)

        val restored = restart().getVoice(voice.id)
        assertEquals(2, restored?.verificationCount)
        assertTrue((restored?.lastVerified ?: 0L) > 0L)
    }

    @Test
    fun removedVoice_staysRemovedAfterRestart() {
        val rm = restart()
        val voice = rm.enrollVoice("Guest", UserRole.GUEST, floatArrayOf(1f))

        assertTrue(rm.removeVoice(voice.id))

        assertNull(restart().getVoice(voice.id))
        assertTrue(restart().listVoices().isEmpty())
    }

    @Test
    fun currentSpeaker_isNeverPersisted() {
        val rm = restart()
        val voice = rm.enrollVoice("Boss", UserRole.BOSS, floatArrayOf(1f))
        rm.setCurrentSpeaker(voice)

        assertNotNull(restart().getVoice(voice.id))
        assertNull(restart().currentSpeaker.value)
    }

    @Test
    fun unknownStoredRoleName_degradesToUnknown_andKeepsToggle() {
        store.snapshot = VoiceStoreSnapshot(
            voices = listOf(
                StoredVoice(
                    id = "ghost",
                    name = "Ghost",
                    roleName = "NOT_A_ROLE",
                    embedding = floatArrayOf(0.5f),
                    enrolledAt = 1L,
                    lastVerified = 0L,
                    verificationCount = 0
                )
            ),
            guardianEnabled = true
        )

        val rm = restart()

        assertEquals(UserRole.UNKNOWN, rm.getVoice("ghost")?.role)
        assertTrue(rm.guardianEnabled.value)
    }

    @Test
    fun multipleVoices_allSurviveRestart() {
        val rm = restart()
        val a = rm.enrollVoice("Boss", UserRole.BOSS, floatArrayOf(1f, 0f))
        val b = rm.enrollVoice("Kid", UserRole.GUEST, floatArrayOf(0f, 1f))

        val restored = restart().listVoices()

        assertEquals(2, restored.size)
        assertNotNull(restored.find { it.id == a.id })
        assertNotNull(restored.find { it.id == b.id })
        assertEquals("Kid", restored.find { it.id == b.id }?.name)
    }
}
