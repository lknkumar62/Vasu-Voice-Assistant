package com.vasu.ai.core

data class VasuWakeWordConfig(
    val enabled: Boolean = false,
    val wakePhrase: String = "hello vasu",
    val commandTimeoutMs: Long = 10_000L,
    val recoveryDelayMs: Long = 1_000L,
    val maxRecoveryAttempts: Int = 3
)
