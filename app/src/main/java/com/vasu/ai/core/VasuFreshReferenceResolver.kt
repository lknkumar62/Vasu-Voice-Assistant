package com.vasu.ai.core

class VasuFreshReferenceResolver {

    fun requiresFreshResolution(
        referenceType: VasuReferenceType
    ): Boolean {
        return when (referenceType) {
            VasuReferenceType.FIRST_RESULT,
            VasuReferenceType.SECOND_RESULT,
            VasuReferenceType.PREVIOUS_RESULT,
            VasuReferenceType.THIS_ITEM,
            VasuReferenceType.THAT_ITEM -> true
            else -> false
        }
    }
}
