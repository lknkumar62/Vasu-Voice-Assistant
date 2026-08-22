package com.vasu.ai.core

import java.util.Locale

class VasuFollowUpIntentDetector {
    fun detect(input: String): VasuConversationReference {
        val text = input.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return reference(VasuReferenceType.UNKNOWN, input, 0f, false)

        return when {
            isBareConfirmation(text) -> reference(VasuReferenceType.CONFIRMATION, input, 0.98f, false)
            startsWithContinue(text) -> reference(VasuReferenceType.CONTINUE, input, 0.95f, false)
            isFirstResult(text) -> reference(VasuReferenceType.FIRST_RESULT, input, 0.92f, true)
            isSecondResult(text) -> reference(VasuReferenceType.SECOND_RESULT, input, 0.92f, true)
            isPreviousResult(text) -> reference(VasuReferenceType.PREVIOUS_RESULT, input, 0.90f, true)
            isThisItem(text) -> reference(VasuReferenceType.THIS_ITEM, input, 0.88f, true)
            isThatItem(text) -> reference(VasuReferenceType.THAT_ITEM, input, 0.88f, true)
            else -> reference(VasuReferenceType.NONE, input, 1f, false)
        }
    }

    private fun isBareConfirmation(text: String) = text in setOf(
        "haan", "ha", "yes", "yeah", "ok", "okay", "theek hai", "ठीक है", "हां", "हाँ"
    )

    private fun startsWithContinue(text: String) =
        text == "continue" || text.startsWith("haan ") || text.startsWith("ha ") ||
            text.startsWith("phir ") || text.startsWith("fir ")

    private fun isFirstResult(text: String) = text in setOf(
        "pehla wala kholo", "pehla wala", "first one kholo", "first wala kholo", "पहला वाला खोलो"
    )

    private fun isSecondResult(text: String) = text in setOf(
        "dusra wala kholo", "doosra wala kholo", "second one kholo", "second wala kholo", "दूसरा वाला खोलो"
    )

    private fun isPreviousResult(text: String) = text in setOf(
        "previous wala kholo", "pichla wala kholo", "पिछला वाला खोलो"
    )

    private fun isThisItem(text: String) = text in setOf(
        "isko kholo", "isko open karo", "ye kholo", "ye wala kholo", "इसको खोलो", "ये वाला खोलो"
    )

    private fun isThatItem(text: String) = text in setOf(
        "usko kholo", "woh kholo", "wo wala kholo", "उसको खोलो", "वो वाला खोलो"
    )

    private fun reference(
        type: VasuReferenceType,
        text: String,
        confidence: Float,
        freshUiEvidence: Boolean
    ) = VasuConversationReference(type, text, confidence, freshUiEvidence)
}
