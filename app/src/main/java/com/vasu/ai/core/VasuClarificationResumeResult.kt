package com.vasu.ai.core

data class VasuClarificationResumeResult(
    val resolved: Boolean,
    val requiresFreshUi: Boolean,
    val referenceType: VasuReferenceType,
    val reason: String
)
