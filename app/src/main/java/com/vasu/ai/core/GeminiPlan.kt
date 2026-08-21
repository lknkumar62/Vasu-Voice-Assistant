package com.vasu.ai.core

/** Structured model output used by the autonomous Gemini brain. */
data class GeminiPlan(
    val reply: String,
    val steps: List<GeminiStep>,
    val done: Boolean
)

data class GeminiStep(
    val action: String,
    val target: String = "",
    val value: String = ""
)
