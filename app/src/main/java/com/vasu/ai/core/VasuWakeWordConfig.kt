package com.vasu.ai.core

data class VasuWakeWordConfig(
    val enabled: Boolean = false,
    val wakePhrase: String = "hello vasu",
    val keywordAssetPath: String = "keywords/hello_vasu.ppn",
    val sensitivity: Float = 0.55f,
    val detectionCooldownMs: Long = 1_500L,
    val commandTimeoutMs: Long = 10_000L,
    val recoveryDelayMs: Long = 1_000L,
    val maxRecoveryAttempts: Int = 3
)
