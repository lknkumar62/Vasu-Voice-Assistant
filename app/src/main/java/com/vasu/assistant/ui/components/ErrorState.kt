package com.vasu.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasu.assistant.ui.theme.VasuCrimson
import com.vasu.assistant.ui.theme.VasuError
import com.vasu.assistant.ui.theme.VasuTextPrimary
import com.vasu.assistant.ui.theme.VasuTextSecondary

/**
 * ErrorState is a centered placeholder for failure/empty-error content with a retry action.
 * The retry button is shown only when both [retryLabel] and [onRetry] are provided.
 */
@Composable
fun ErrorState(
    icon: ImageVector,
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VasuError,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = VasuTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (retryLabel != null && onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VasuCrimson,
                    contentColor = VasuTextPrimary
                )
            ) {
                Text(text = retryLabel)
            }
        }
    }
}
