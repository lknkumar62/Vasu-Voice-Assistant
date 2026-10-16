package com.vasu.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vasu.assistant.ui.theme.VasuIdle
import com.vasu.assistant.ui.theme.VasuListening
import com.vasu.assistant.ui.theme.VasuSpeaking
import com.vasu.assistant.ui.theme.VasuSuccess
import com.vasu.assistant.ui.theme.VasuThinking

/**
 * FIX-7: animated "neon orb" — breathing glow, two counter-rotating arcs and an
 * expanding pulse ring while listening. When [animationsEnabled] is false the orb
 * renders a single static frame (same colors, zero motion).
 */
@Composable
fun NeonOrb(
    state: OrbState,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
    orbSize: Dp = 168.dp
) {
    val targetColor = when (state) {
        OrbState.LISTENING -> VasuListening
        OrbState.SPEAKING -> VasuSpeaking
        OrbState.THINKING -> VasuThinking
        OrbState.AWAKE -> VasuSuccess
        OrbState.IDLE -> VasuIdle
    }
    val color = if (animationsEnabled) {
        animateColorAsState(targetColor, label = "orb_color").value
    } else {
        targetColor
    }
    val motion = rememberOrbMotion(animationsEnabled)

    Canvas(modifier = modifier.size(orbSize).scale(motion.breath)) {
        val w = size.minDimension
        val c = center
        val r = w / 2f

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        // Core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.10f)),
                center = c,
                radius = r * 0.62f
            ),
            radius = r * 0.62f,
            center = c
        )
        // Counter-rotating arcs
        val insetA = w * 0.07f
        drawArc(
            color = color,
            startAngle = motion.spin,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(insetA, insetA),
            size = Size(w - insetA * 2f, w - insetA * 2f),
            style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
        )
        val insetB = w * 0.17f
        drawArc(
            color = color.copy(alpha = 0.5f),
            startAngle = motion.spinReverse,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(insetB, insetB),
            size = Size(w - insetB * 2f, w - insetB * 2f),
            style = Stroke(width = w * 0.013f, cap = StrokeCap.Round)
        )
        // Pulse ring while listening / awake
        if (state == OrbState.LISTENING || state == OrbState.AWAKE) {
            drawCircle(
                color = color.copy(alpha = (1f - motion.pulse) * 0.45f),
                radius = r * (0.8f + 0.35f * motion.pulse),
                center = c,
                style = Stroke(width = w * 0.008f)
            )
        }
    }
}

private data class OrbMotion(
    val breath: Float = 1f,
    val spin: Float = 0f,
    val spinReverse: Float = 180f,
    val pulse: Float = 0f
)

@Composable
private fun rememberOrbMotion(animationsEnabled: Boolean): OrbMotion {
    if (!animationsEnabled) return OrbMotion()
    val transition = rememberInfiniteTransition(label = "vasu_orb")
    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_breath"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "orb_spin"
    )
    val spinReverse by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "orb_spin_reverse"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "orb_pulse"
    )
    return OrbMotion(breath, spin, spinReverse, pulse)
}
