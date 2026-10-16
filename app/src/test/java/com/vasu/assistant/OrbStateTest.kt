package com.vasu.assistant

import com.vasu.assistant.ui.components.OrbState
import com.vasu.assistant.ui.components.orbStateFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** FIX-7: the orb's state precedence must mirror the home card status text. */
class OrbStateTest {

    @Test
    fun idleWhenNothingActive() {
        assertEquals(OrbState.IDLE, orbStateFor(false, false, false, false, false))
    }

    @Test
    fun listeningBeatsEverything() {
        assertEquals(OrbState.LISTENING, orbStateFor(true, true, true, true, true))
    }

    @Test
    fun speakingBeatsThinkingAndWake() {
        assertEquals(OrbState.SPEAKING, orbStateFor(false, true, true, true, true))
    }

    @Test
    fun thinkingBeatsWake() {
        assertEquals(OrbState.THINKING, orbStateFor(false, false, true, true, true))
    }

    @Test
    fun awakeOnWakeListeningOrDetected() {
        assertEquals(OrbState.AWAKE, orbStateFor(false, false, false, true, false))
        assertEquals(OrbState.AWAKE, orbStateFor(false, false, false, false, true))
    }
}
