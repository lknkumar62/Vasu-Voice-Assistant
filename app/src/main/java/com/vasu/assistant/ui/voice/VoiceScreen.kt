package com.vasu.assistant.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Mode",
                        fontWeight = FontWeight.Bold,
                        color = VasuCyan
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VasuTextSecondary
                        )
                    }
                },
                actions = {
                    // Stop button if speaking
                    if (uiState.isSpeaking) {
                        IconButton(onClick = viewModel::stopSpeaking) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Speaking",
                                tint = VasuError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VasuDarkBg
                )
            )
        },
        containerColor = VasuDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Waveform visualization
            WaveformVisualizer(
                isListening = uiState.isListening,
                rmsLevel = uiState.rmsLevel
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status
            Text(
                text = when {
                    uiState.isListening -> "सुन रही हूँ..."
                    uiState.isSpeaking -> "बोल रही हूँ..."
                    uiState.sttState == STTState.PROCESSING -> "सोच रही हूँ..."
                    else -> "बोलने के लिए माइक दबाएं"
                },
                color = when {
                    uiState.isListening -> VasuCyan
                    uiState.isSpeaking -> VasuPurple
                    uiState.sttState == STTState.PROCESSING -> VasuWarning
                    else -> VasuTextSecondary
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transcript
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = VasuDarkCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Partial transcript (real-time)
                    if (uiState.transcript.isNotEmpty()) {
                        Text(
                            text = uiState.transcript,
                            color = VasuCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "आपकी आवाज़ यहाँ दिखाई देगी...",
                            color = VasuTextMuted,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Last response
                    if (uiState.lastResponse.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = VasuDarkCard)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.lastResponse,
                            color = VasuTextPrimary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Mic Button
            VoiceMicButton(
                isListening = uiState.isListening,
                isSpeaking = uiState.isSpeaking,
                onClick = { viewModel.toggleListening() }
            )
        }
    }
}

@Composable
fun WaveformVisualizer(
    isListening: Boolean,
    rmsLevel: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(20) { index ->
            val delay = index * 100
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = if (isListening) (20f + (index % 5) * 15f) else 8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600 + delay, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isListening -> VasuCyan
                            else -> VasuTextMuted.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

@Composable
fun VoiceMicButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val buttonColor = when {
        isListening -> VasuError
        isSpeaking -> VasuPurple
        else -> VasuCyan
    }

    Box(contentAlignment = Alignment.Center) {
        if (isListening || isSpeaking) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.15f))
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(88.dp),
            containerColor = buttonColor,
            contentColor = VasuDarkBg,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start Listening",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
