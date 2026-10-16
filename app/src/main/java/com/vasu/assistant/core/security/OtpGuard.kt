package com.vasu.assistant.core.security

/**
 * OTP/payment protection policy (FIX-4, spec section 4).
 *
 * While enabled (Privacy settings, default on), a turn that carries OTP or
 * payment context may not trigger communication or notification-reading
 * tools, regardless of the autonomous-execution setting. Fail-safe by
 * direction: a false positive only skips a risky action the user can repeat
 * once the sensitive content is out of the message.
 *
 * Pure Kotlin, no android.* imports, so it stays unit-testable on the JVM.
 */
object OtpGuard {

    private val OTP_CONTEXT = Regex(
        """(?i)(?<![\p{L}\p{M}\p{Nd}])(otp|o\.t\.p|one[ -]?time[ -]?password|verification code|verify code|authentication code|auth code|ओटीपी|सत्यापन कोड|वन टाइम पासवर्ड)(?![\p{L}\p{M}\p{Nd}])"""
    )

    private val PAYMENT_CONTEXT = Regex(
        """(?i)(?<![\p{L}\p{M}\p{Nd}])(cvv|upi|atm pin|mpin|m-?pin|card number|debit card|credit card|net ?banking|payment|paytm|phonepe|gpay|google pay|bank transfer|भुगतान|बैंक|कार्ड|पिन|एटीएम|यूपीआई)(?![\p{L}\p{M}\p{Nd}])"""
    )

    /** A standalone 6-digit number is treated as a verification code. */
    private val SIX_DIGIT_CODE = Regex(
        """(?<![\p{Nd}])(?<![\p{Nd}][\s-])\d{6}(?![\p{Nd}])(?![\s-]?[\p{Nd}])"""
    )

    /** Tools that must not run on a turn carrying OTP/payment context. */
    private val GUARDED_TOOLS = setOf(
        "make_call", "send_message", "open_whatsapp", "read_notifications"
    )

    fun isSensitiveTurn(input: String): Boolean =
        OTP_CONTEXT.containsMatchIn(input) ||
            PAYMENT_CONTEXT.containsMatchIn(input) ||
            SIX_DIGIT_CODE.containsMatchIn(input)

    fun isGuardedTool(toolName: String): Boolean = toolName in GUARDED_TOOLS
}
