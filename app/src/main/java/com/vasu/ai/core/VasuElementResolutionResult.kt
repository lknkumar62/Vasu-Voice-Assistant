package com.vasu.ai.core

data class VasuElementResolutionResult<T>(
    val selected: T?,
    val ambiguous: Boolean,
    val candidateCount: Int,
    val bestScore: Int,
    val secondBestScore: Int
)
