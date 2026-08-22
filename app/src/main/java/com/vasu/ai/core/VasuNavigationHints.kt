package com.vasu.ai.core

/** Lightweight app-specific navigation hints. Hints never force an action. */
object VasuNavigationHints {
    private val hints = mapOf(
        "com.google.android.youtube" to listOf("search", "home", "shorts"),
        "com.android.chrome" to listOf("search", "address", "tab"),
        "com.google.android.apps.maps" to listOf("search", "directions"),
        "com.whatsapp" to listOf("search", "chats")
    )

    fun hintsFor(packageName: String?): List<String> =
        if (packageName.isNullOrBlank()) emptyList() else hints[packageName].orEmpty()
}
