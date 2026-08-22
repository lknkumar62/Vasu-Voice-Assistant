package com.vasu.ai.core

data class VasuElementAmbiguity(
    val ambiguous: Boolean,
    val candidateCount: Int,
    val bestScore: Int,
    val secondBestScore: Int,
    val scoreGap: Int
) {
    companion object {
        private const val MIN_CANDIDATES = 2
        private const val MAX_SCORE_GAP = 8

        fun evaluate(scores: List<Int>): VasuElementAmbiguity {
            val sorted = scores.sortedDescending()
            if (sorted.size < MIN_CANDIDATES) {
                return VasuElementAmbiguity(
                    ambiguous = false,
                    candidateCount = sorted.size,
                    bestScore = sorted.firstOrNull() ?: 0,
                    secondBestScore = 0,
                    scoreGap = Int.MAX_VALUE
                )
            }

            val best = sorted[0]
            val second = sorted[1]
            val gap = best - second
            return VasuElementAmbiguity(
                ambiguous = gap <= MAX_SCORE_GAP,
                candidateCount = sorted.size,
                bestScore = best,
                secondBestScore = second,
                scoreGap = gap
            )
        }
    }
}
