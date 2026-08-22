package com.vasu.ai.core

data class VasuConversationReference(
    val type: VasuReferenceType,
    val originalText: String,
    val confidence: Float,
    val requiresFreshUiEvidence: Boolean
)
