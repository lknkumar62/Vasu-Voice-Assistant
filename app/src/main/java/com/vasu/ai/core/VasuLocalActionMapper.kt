package com.vasu.ai.core

class VasuLocalActionMapper(
    private val appResolver: VasuAppResolver
) {
    fun map(intent: VasuLocalIntent): VasuAction? = when (intent) {
        is VasuLocalIntent.OpenApp -> {
            appResolver.resolve(intent.appName)?.let(VasuAction::OpenApp)
        }
        VasuLocalIntent.GoBack -> VasuAction.Back
        VasuLocalIntent.GoHome -> VasuAction.Home
        VasuLocalIntent.OpenRecents -> VasuAction.Recents
        VasuLocalIntent.ScrollUp -> VasuAction.Scroll(VasuAction.Direction.UP)
        VasuLocalIntent.ScrollDown -> VasuAction.Scroll(VasuAction.Direction.DOWN)
        VasuLocalIntent.SwipeLeft -> VasuAction.Swipe(VasuAction.Direction.LEFT)
        VasuLocalIntent.SwipeRight -> VasuAction.Swipe(VasuAction.Direction.RIGHT)
        VasuLocalIntent.Stop,
        VasuLocalIntent.Unknown -> null
    }
}
