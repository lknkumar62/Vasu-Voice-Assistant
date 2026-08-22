package com.vasu.ai.core

sealed class VasuBrainDecision {
    data class Local(
        val intents: List<VasuLocalIntent>
    ) : VasuBrainDecision()

    data class Online(
        val reason: String
    ) : VasuBrainDecision()

    object NoOp : VasuBrainDecision()
}
