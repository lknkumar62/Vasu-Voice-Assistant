package com.vasu.assistant

import com.vasu.assistant.core.security.OtpGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FIX-4 OTP/payment guard: which turns are sensitive and which
 * tools the guard blocks. Pure JVM, no Android.
 */
class OtpGuardTest {

    @Test
    fun otpContextIsSensitive() {
        assertTrue(OtpGuard.isSensitiveTurn("OTP bhej do"))
        assertTrue(OtpGuard.isSensitiveTurn("one time password kya hai"))
        assertTrue(OtpGuard.isSensitiveTurn("मेरा ओटीपी बताओ"))
        assertTrue(OtpGuard.isSensitiveTurn("123456"))
        assertTrue(OtpGuard.isSensitiveTurn("code 654321 hai"))
    }

    @Test
    fun paymentContextIsSensitive() {
        assertTrue(OtpGuard.isSensitiveTurn("cvv batao"))
        assertTrue(OtpGuard.isSensitiveTurn("upi pin yaad karo"))
        assertTrue(OtpGuard.isSensitiveTurn("card number bhej do"))
        assertTrue(OtpGuard.isSensitiveTurn("paytm se payment karo"))
    }

    @Test
    fun normalTurnsAreNotSensitive() {
        assertFalse(OtpGuard.isSensitiveTurn("kal ki meeting ka time kya hai"))
        assertFalse(OtpGuard.isSensitiveTurn("Rahul ko message bhej do ki main aa raha hoon"))
        assertFalse(OtpGuard.isSensitiveTurn("phone number 9876543210 notes me hai"))
        assertFalse(OtpGuard.isSensitiveTurn("phone number 987654 3210 notes me hai"))
    }

    @Test
    fun guardedToolsCoverCommunication() {
        assertTrue(OtpGuard.isGuardedTool("send_message"))
        assertTrue(OtpGuard.isGuardedTool("make_call"))
        assertTrue(OtpGuard.isGuardedTool("open_whatsapp"))
        assertTrue(OtpGuard.isGuardedTool("read_notifications"))
        assertFalse(OtpGuard.isGuardedTool("turn_on_torch"))
        assertFalse(OtpGuard.isGuardedTool("create_alarm"))
    }
}
