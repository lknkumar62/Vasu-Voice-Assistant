package com.vasu.assistant

import com.vasu.assistant.core.ai.ConfirmationGate
import com.vasu.assistant.core.ai.DetectedStyle
import com.vasu.assistant.core.security.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FIX-4 HIGH-risk confirmation gate: which tools it gates, what
 * counts as yes/no, and the script-aware question. Pure JVM, no Android.
 */
class ConfirmationGateTest {

    @Test
    fun highRiskIsGatedOnlyWhenAutoAllowIsOff() {
        assertTrue(ConfirmationGate.requiresConfirmation(RiskLevel.HIGH, autoAllow = false))
        assertFalse(ConfirmationGate.requiresConfirmation(RiskLevel.HIGH, autoAllow = true))
        assertFalse(ConfirmationGate.requiresConfirmation(RiskLevel.MEDIUM, autoAllow = false))
        assertFalse(ConfirmationGate.requiresConfirmation(RiskLevel.LOW, autoAllow = false))
    }

    @Test
    fun affirmativesAreRecognised() {
        assertTrue(ConfirmationGate.isAffirmative("haan"))
        assertTrue(ConfirmationGate.isAffirmative("Haan kar do"))
        assertTrue(ConfirmationGate.isAffirmative("yes"))
        assertTrue(ConfirmationGate.isAffirmative("ok karo"))
        assertTrue(ConfirmationGate.isAffirmative("theek hai"))
        assertTrue(ConfirmationGate.isAffirmative("हाँ"))
        assertFalse(ConfirmationGate.isAffirmative("nahi"))
        assertFalse(ConfirmationGate.isAffirmative("aaj mausam kaisa hai"))
    }

    @Test
    fun negativesAreRecognisedAndWin() {
        assertTrue(ConfirmationGate.isNegative("nahi"))
        assertTrue(ConfirmationGate.isNegative("nahi karo"))
        assertTrue(ConfirmationGate.isNegative("cancel"))
        assertTrue(ConfirmationGate.isNegative("mat karo"))
        assertTrue(ConfirmationGate.isNegative("नहीं"))
        assertFalse(ConfirmationGate.isNegative("haan karo"))
    }

    @Test
    fun questionFollowsDetectedScript() {
        assertTrue(
            ConfirmationGate.questionFor("make_call", DetectedStyle.DEVANAGARI_HINDI).contains("हाँ")
        )
        assertTrue(
            ConfirmationGate.questionFor("make_call", DetectedStyle.ENGLISH).contains("yes")
        )
        assertTrue(
            ConfirmationGate.questionFor("make_call", DetectedStyle.ROMAN_HINDI).contains("Haan")
        )
    }

    @Test
    fun commandWithKaroIsNotMistakenForConfirmation() {
        assertFalse(ConfirmationGate.isAffirmative("Rahul ko call karo"))
        assertFalse(ConfirmationGate.isAffirmative("usko message bhej do karo"))
        assertTrue(ConfirmationGate.isAffirmative("karo"))
        assertTrue(ConfirmationGate.isAffirmative("ab kar do"))
    }
}
