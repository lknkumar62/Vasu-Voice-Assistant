package com.vasu.ai.core

import java.util.Locale

class VasuClarificationResolver {
    fun resolve(
        request: VasuClarificationRequest,
        answer: String
    ): VasuClarificationAnswer {
        val text = answer.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return unmatched(answer)

        return when {
            isFirst(text) -> matched(VasuReferenceType.FIRST_RESULT, text, 0.98f)
            isSecond(text) -> matched(VasuReferenceType.SECOND_RESULT, text, 0.98f)
            isPrevious(text) -> matched(VasuReferenceType.PREVIOUS_RESULT, text, 0.95f)
            isThis(text) -> matched(VasuReferenceType.THIS_ITEM, text, 0.90f)
            isThat(text) -> matched(VasuReferenceType.THAT_ITEM, text, 0.90f)
            isCancel(text) -> VasuClarificationAnswer(
                matched = false,
                referenceType = VasuReferenceType.UNKNOWN,
                confidence = 1f,
                normalizedAnswer = "cancel",
                requiresFreshUiEvidence = false
            )
            else -> unmatched(answer)
        }
    }

    private fun isFirst(text: String): Boolean = text in setOf(
        "first", "first one", "first wala", "pehla", "pehla wala", "pehle wala", "पहला", "पहला वाला"
    )

    private fun isSecond(text: String): Boolean = text in setOf(
        "second", "second one", "second wala", "dusra", "doosra", "dusra wala", "doosra wala", "दूसरा", "दूसरा वाला"
    )

    private fun isPrevious(text: String): Boolean = text in setOf(
        "previous", "previous one", "previous wala", "pichla", "pichla wala", "पिछला", "पिछला वाला"
    )

    private fun isThis(text: String): Boolean = text in setOf(
        "this", "this one", "this wala", "ye", "ye wala", "yeh", "yeh wala", "ये", "ये वाला"
    )

    private fun isThat(text: String): Boolean = text in setOf(
        "that", "that one", "that wala", "woh", "wo", "woh wala", "wo wala", "वो", "वो वाला"
    )

    private fun isCancel(text: String): Boolean = text in setOf(
        "cancel", "cancel karo", "rehne do", "chhodo", "leave it", "नहीं रहने दो", "रहने दो"
    )

    private fun matched(
        type: VasuReferenceType,
        text: String,
        confidence: Float
    ): VasuClarificationAnswer = VasuClarificationAnswer(
        matched = true,
        referenceType = type,
        confidence = confidence,
        normalizedAnswer = text,
        requiresFreshUiEvidence = true
    )

    private fun unmatched(answer: String): VasuClarificationAnswer = VasuClarificationAnswer(
        matched = false,
        referenceType = VasuReferenceType.UNKNOWN,
        confidence = 0f,
        normalizedAnswer = answer.trim(),
        requiresFreshUiEvidence = true
    )
}
