package com.vasu.ai.core

/**
 * Central confirmation sensitivity check for execution-boundary callers.
 *
 * Keep this aligned with the existing security policy: phone calls and SMS
 * require explicit confirmation; other actions remain unchanged here.
 */
fun isConfirmationSensitive(action: VasuAction): Boolean = when (action) {
    is VasuAction.CallContact,
    is VasuAction.SendSms -> true

    else -> false
}
