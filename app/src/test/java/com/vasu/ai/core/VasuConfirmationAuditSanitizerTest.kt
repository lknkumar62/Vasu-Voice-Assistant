package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VasuConfirmationAuditSanitizerTest {

    @Test
    fun trimsAndBoundsReason() {
        val result =
            VasuConfirmationAuditSanitizer.sanitizeReason(
                "  ${"x".repeat(300)}  "
            )

        assertEquals(200, result.length)
    }

    @Test
    fun blankRequestIdBecomesNull() {
        assertNull(
            VasuConfirmationAuditSanitizer.sanitizeRequestId("   ")
        )
    }

    @Test
    fun requestIdIsBounded() {
        val result =
            VasuConfirmationAuditSanitizer.sanitizeRequestId(
                "x".repeat(150)
            )

        assertEquals(100, result?.length)
    }

    @Test
    fun actionNameIsTrimmed() {
        assertEquals(
            "SendSms",
            VasuConfirmationAuditSanitizer.sanitizeActionName(
                "  SendSms  "
            )
        )
    }
}
