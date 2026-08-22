package com.vasu.ai.core

import java.util.Locale

class VasuClarificationInputGuard {
    fun looksLikeClarificationAnswer(input: String): Boolean {
        val text = input.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return false

        return text in setOf(
            "pehla", "pehla wala", "पहला", "पहला वाला",
            "dusra", "doosra", "dusra wala", "doosra wala", "दूसरा", "दूसरा वाला",
            "pichla", "pichla wala", "पिछला", "पिछला वाला",
            "ye", "ye wala", "yeh", "yeh wala", "ये", "ये वाला",
            "wo", "woh", "wo wala", "woh wala", "वो", "वो वाला",
            "cancel", "cancel karo", "rehne do", "रहने दो"
        )
    }
}
