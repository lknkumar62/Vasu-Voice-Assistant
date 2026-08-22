package com.vasu.ai.core

data class VasuConversationReferenceContext(
    val activeAppName: String?,
    val activeAppPackage: String?,
    val lastUserCommand: String?,
    val lastSuccessfulAction: String?,
    val referenceType: VasuReferenceType,
    val referenceConfidence: Float,
    val freshUiEvidenceRequired: Boolean
)
