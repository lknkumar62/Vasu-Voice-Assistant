package com.vasu.ai.memory

class VasuMemorySafetyPolicy {

    private val blockedKeys = setOf(
        "password",
        "passcode",
        "pin",
        "otp",
        "cvv",
        "card number",
        "credit card",
        "debit card",
        "api key",
        "access key",
        "secret",
        "token"
    )

    fun isAllowedKey(key: String): Boolean {
        val normalized = key.trim().lowercase()

        if (normalized.isBlank()) {
            return false
        }

        return blockedKeys.none { blocked ->
            normalized == blocked ||
                normalized.contains("$blocked ")
        }
    }
}
