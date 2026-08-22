package com.vasu.ai.core

data class VasuClarificationAnswer(
    val matched: Boolean,
    val referenceType: VasuReferenceType,
    val confidence: Float,
    val normalizedAnswer: String,
    val requiresFreshUiEvidence: Boolean
)
