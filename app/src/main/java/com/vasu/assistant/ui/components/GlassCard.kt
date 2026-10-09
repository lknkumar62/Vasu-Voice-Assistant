package com.vasu.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vasu.assistant.ui.theme.VasuCrimson
import com.vasu.assistant.ui.theme.VasuGlassBorder
import com.vasu.assistant.ui.theme.VasuGlassWhite

/**
 * GlassCard is a semi-transparent glassmorphism surface layered over [com.vasu.assistant.ui.theme.VasuDarkCard].
 * Matches the [VasuCard] API shape (Modifier-first, content-slot-last).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = VasuGlassWhite,
    borderColor: Color = VasuGlassBorder,
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: Dp = 16.dp,
    accentEdge: Boolean = false,
    accentColor: Color = VasuCrimson,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (accentEdge) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accentColor)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
