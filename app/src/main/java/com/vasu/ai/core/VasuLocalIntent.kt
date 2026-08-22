package com.vasu.ai.core

sealed class VasuLocalIntent {
    data class OpenApp(val appName: String) : VasuLocalIntent()
    data object GoBack : VasuLocalIntent()
    data object GoHome : VasuLocalIntent()
    data object OpenRecents : VasuLocalIntent()
    data object ScrollUp : VasuLocalIntent()
    data object ScrollDown : VasuLocalIntent()
    data object SwipeLeft : VasuLocalIntent()
    data object SwipeRight : VasuLocalIntent()
    data object Stop : VasuLocalIntent()
    data object Unknown : VasuLocalIntent()
}
