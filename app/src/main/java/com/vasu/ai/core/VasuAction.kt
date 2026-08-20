package com.vasu.ai.core

/** A small, stable contract for all executable VASU actions. */
sealed interface VasuAction {
    data class OpenApp(val packageName: String) : VasuAction
    data class ClickText(val text: String) : VasuAction
    data class TypeText(val text: String) : VasuAction
    data class Scroll(val direction: Direction) : VasuAction
    data object Back : VasuAction
    data object Home : VasuAction
    data object Recents : VasuAction

    enum class Direction { UP, DOWN, LEFT, RIGHT }
}
