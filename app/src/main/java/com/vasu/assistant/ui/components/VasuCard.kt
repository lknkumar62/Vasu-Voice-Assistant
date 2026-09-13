package com.vasu.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vasu.assistant.ui.theme.VasuCyan
import com.vasu.assistant.ui.theme.VasuDarkCard

/**
 * VasuCard is a standardized design system wrapper to ensure visual consistency
 * across the application. It encapsulates the "hyper-professional" look.
 */
@Composable
fun VasuCard(
    modifier: Modifier = Modifier,
    containerColor: Color = VasuDarkCard,
    borderColor: Color = VasuCyan,
    borderAlpha: Float = 0.12f,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = shape,
        border = BorderStroke(1.dp, borderColor.copy(alpha = borderAlpha))
    ) {
        Box(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}
