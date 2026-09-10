package com.vasu.assistant.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import com.vasu.assistant.core.voice.GeminiVoiceState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AssistantOverlayActivity : ComponentActivity() {

    @Inject
    lateinit var geminiLiveVoiceService: GeminiLiveVoiceService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Automatically start listening session upon wake-word trigger
        geminiLiveVoiceService.startMicrophoneConversation()

        setContent {
            OverlayContent(
                voiceService = geminiLiveVoiceService,
                onDismiss = {
                    geminiLiveVoiceService.stopMicrophoneConversation()
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, AssistantOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun OverlayContent(
    voiceService: GeminiLiveVoiceService,
    onDismiss: () -> Unit
) {
    val voiceState by voiceService.voiceState.collectAsState()
    val transcript by voiceService.currentTranscript.collectAsState()
    val lastResponse by voiceService.lastResponse.collectAsState()

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = interactionSource, indication = null) {
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false) {}, // Prevent dismiss when clicking sheet
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "VASU Assistant",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Orb Indicator
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = when (voiceState) {
                                    GeminiVoiceState.SPEAKING -> listOf(Color(0xFFF43F5E), Color(0xFFBE123C))
                                    GeminiVoiceState.LISTENING -> listOf(Color(0xFF06B6D4), Color(0xFF0284C7))
                                    GeminiVoiceState.THINKING -> listOf(Color(0xFFA855F7), Color(0xFF7E22CE))
                                    else -> listOf(Color(0xFF334155), Color(0xFF1E293B))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState == GeminiVoiceState.SPEAKING) {
                            Icons.Default.VolumeUp
                        } else {
                            Icons.Default.Mic
                        },
                        contentDescription = "Voice State",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status text
                Text(
                    text = when (voiceState) {
                        GeminiVoiceState.LISTENING -> "सुन रही हूँ... (Listening)"
                        GeminiVoiceState.SPEAKING -> "कोर बोल रही है... (Kore speaking)"
                        GeminiVoiceState.THINKING -> "प्रोसेस कर रही हूँ..."
                        GeminiVoiceState.CONNECTING -> "जेमिनी लाइव से कनेक्ट हो रहा है..."
                        else -> "नमस्ते! कहिए, क्या मदद करूँ?"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Transcripts
                if (transcript.isNotEmpty() || lastResponse.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (transcript.isNotEmpty()) {
                            Text(
                                text = "You: $transcript",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp
                            )
                        }
                        if (lastResponse.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "VASU: $lastResponse",
                                color = Color(0xFF4ADE80),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
