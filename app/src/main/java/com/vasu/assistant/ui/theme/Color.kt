package com.vasu.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// VASU ORIGINAL PALETTE — deep black + crimson/electric accent, glassmorphism
val VasuObsidian = Color(0xFF000000) // Pure Black Background
val VasuCrimson = Color(0xFFE11D48) // Crimson accent — primary brand
val VasuElectric = Color(0xFF00E5FF) // Electric accent — secondary
val VasuNeonGreen = Color(0xFF39FF14) // Neon Green — success
val VasuGlassWhite = Color(0x1AFFFFFF) // Glassmorphism overlay

val VasuSuccess = VasuNeonGreen
val VasuGreen = VasuNeonGreen
val VasuDarkBg = VasuObsidian
val VasuDarkCard = Color(0xFF0A0A0A) // Slight grey for depth
val VasuDarkSurface = Color(0xFF050505)
val VasuDarkElevated = Color(0xFF121212)

val VasuTextPrimary = Color(0xFFFFFFFF)
val VasuTextSecondary = Color(0xFFB0B0B0)
val VasuTextMuted = Color(0xFF666666)

val VasuError = Color(0xFFFF0040)
val VasuWarning = Color(0xFFFFD700)
val VasuInfo = VasuElectric

// Voice Activity
val VasuListening = VasuCrimson
val VasuSpeaking = VasuElectric
val VasuThinking = Color(0xFFFFFF00)
val VasuIdle = Color(0xFF333333)

// Material 3 Mapping
val DarkPrimary = VasuCrimson
val DarkOnPrimary = Color(0xFF000000)
val DarkPrimaryContainer = Color(0xFF3A0010)
val DarkOnPrimaryContainer = VasuCrimson

val DarkSecondary = VasuElectric
val DarkOnSecondary = Color(0xFF000000)
val DarkSecondaryContainer = Color(0xFF003040)
val DarkOnSecondaryContainer = VasuElectric

val DarkBackground = VasuObsidian
val DarkOnBackground = VasuTextPrimary
val DarkSurface = VasuObsidian
val DarkOnSurface = VasuTextPrimary
val DarkSurfaceVariant = VasuObsidian
val DarkOnSurfaceVariant = VasuTextSecondary

val DarkError = VasuError
val DarkOnError = Color(0xFFFFFFFF)
val DarkErrorContainer = Color(0xFF660000)
val DarkOnErrorContainer = Color(0xFFFFBABA)
