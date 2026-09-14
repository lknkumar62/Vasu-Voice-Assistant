package com.vasu.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// MAYA 4.18.5 EXACT PALETTE
val MayaObsidian = Color(0xFF000000) // Pure Black Background
val MayaElectricCyan = Color(0xFF00FFFF) // Neon Cyan Accents
val MayaElectricPurple = Color(0xFFBF00FF) // Neon Purple
val MayaNeonGreen = Color(0xFF39FF14) // Neon Green
val MayaGlassWhite = Color(0x1AFFFFFF) // Glassmorphism overlay

val VasuCyan = MayaElectricCyan
val VasuPurple = MayaElectricPurple
val VasuSuccess = MayaNeonGreen
val VasuGreen = MayaNeonGreen
val VasuDarkBg = MayaObsidian
val VasuDarkCard = Color(0xFF0A0A0A) // Slight grey for depth
val VasuDarkSurface = Color(0xFF050505)
val VasuDarkElevated = Color(0xFF121212)

val VasuTextPrimary = Color(0xFFFFFFFF)
val VasuTextSecondary = Color(0xFFB0B0B0)
val VasuTextMuted = Color(0xFF666666)

val VasuError = Color(0xFFFF0040)
val VasuWarning = Color(0xFFFFD700)
val VasuInfo = Color(0xFF00BFFF)

// Voice Activity
val VasuListening = MayaElectricCyan
val VasuSpeaking = MayaElectricPurple
val VasuThinking = Color(0xFFFFFF00)
val VasuIdle = Color(0xFF333333)

// Material 3 Mapping
val DarkPrimary = MayaElectricCyan
val DarkOnPrimary = Color(0xFF000000)
val DarkPrimaryContainer = Color(0xFF003333)
val DarkOnPrimaryContainer = MayaElectricCyan

val DarkSecondary = MayaElectricPurple
val DarkOnSecondary = Color(0xFF000000)
val DarkSecondaryContainer = Color(0xFF330033)
val DarkOnSecondaryContainer = MayaElectricPurple

val DarkBackground = MayaObsidian
val DarkOnBackground = VasuTextPrimary
val DarkSurface = MayaObsidian
val DarkOnSurface = VasuTextPrimary
val DarkSurfaceVariant = MayaObsidian
val DarkOnSurfaceVariant = VasuTextSecondary

val DarkError = VasuError
val DarkOnError = Color(0xFFFFFFFF)
val DarkErrorContainer = Color(0xFF660000)
val DarkOnErrorContainer = Color(0xFFFFBABA)
