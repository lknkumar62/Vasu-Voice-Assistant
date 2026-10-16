package com.vasu.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vasu.assistant.R

// FIX-7: bundled OFL fonts under res/font — Orbitron (display), Exo 2 (titles),
// Raleway (body), Rajdhani (labels). Orbitron/Exo 2/Raleway are variable fonts,
// so they render their Regular instance and heavier weights are synthesized.
private val VasuOrbitron = FontFamily(Font(R.font.orbitron))
private val VasuExo2 = FontFamily(Font(R.font.exo2))
private val VasuRaleway = FontFamily(Font(R.font.raleway))
private val VasuRajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

val VasuTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VasuOrbitron,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VasuOrbitron,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = VasuExo2,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = VasuRaleway,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = VasuRajdhani,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)
