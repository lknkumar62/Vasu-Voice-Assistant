package com.vasu.ai.core

class VasuCommandSegmenter {
    companion object {
        private val separators = Regex("\\s+(?:phir|fir|then|and then|aur phir)\\s+|[;]+")
    }

    fun split(command: String): List<String> = command
        .split(separators)
        .map(String::trim)
        .filter(String::isNotBlank)
}
