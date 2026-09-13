package com.vasu.assistant.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top badges row (model + voice) — parity with web 259-278 ──
            ModelVoiceBadgesRow(
                model = uiState.geminiModel.ifBlank { "gemini-3.1-flash-live-preview" },
                voice = uiState.geminiVoice.ifBlank { "Kore" }
            )

            // ── Main status display card — parity with web 281-305 ──
            StatusDisplayCard(
                mode = uiState.mode,
                isListening = uiState.isListening,
                onReconnect = viewModel::reconnect
            )

            // ── Error banner — parity with web 308-337 ──
            if (uiState.errorMessage != null) {
                ErrorBannerCard(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::reconnect,
                    onDismiss = viewModel::clearError,
                    onOpenSettings = onNavigateToSettings
                )
            }

            // Waveform visualization — KEEP Maya native bars (not glow)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VasuDarkCard.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VasuTextMuted.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WaveformVisualizer(
                        isListening = uiState.isListening,
                        rmsLevel = uiState.rmsLevel
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status Message
                    Text(
                        text = uiState.statusMessage,
                        color = when (uiState.mode) {
                            VoiceUiMode.LISTENING -> VasuCyan
                            VoiceUiMode.SPEAKING -> VasuPurple
                            VoiceUiMode.THINKING, VoiceUiMode.PROCESSING -> VasuWarning
                            VoiceUiMode.CONNECTING, VoiceUiMode.CONNECTED -> VasuCyan
                            VoiceUiMode.PERMISSION_REQUIRED, VoiceUiMode.MIC_UNAVAILABLE, VoiceUiMode.ERROR -> VasuError
                            VoiceUiMode.OFFLINE_MODE, VoiceUiMode.GEMINI_UNAVAILABLE, VoiceUiMode.DISCONNECTED, VoiceUiMode.IDLE -> VasuTextSecondary
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Voice Engine Badge — Hindi label kept (Vasu theme)
                    Text(
                        text = "वॉयस: ${uiState.activeVoiceSource.displayName}",
                        color = VasuCyanLight.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // Transcript card
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

            // Mic Button — keep Vasu primary mic
            VoiceMicButton(
                isListening = uiState.isListening,
                isSpeaking = uiState.isSpeaking,
                onClick = { viewModel.toggleListening() }
            )

            // ── Primary Action Buttons — parity with web 422-487 ──
            // Test Kore Voice (Text Test) — full width primary
            Button(
                onClick = { viewModel.testKoreVoice("Namaste Vasu, ek chhota sa greeting bolo.") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VasuCyan,
                    contentColor = VasuDarkBg
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Kore Voice (Text Test)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            // Grid: Live Mic + Hardware Test — parity with web btn-toggle-live-mic / btn-test-speaker-hardware
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Mic Streaming toggle
                OutlinedButton(
                    onClick = { viewModel.toggleListening() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uiState.isListening) VasuCyan.copy(alpha = 0.15f) else VasuDarkCard,
                        contentColor = if (uiState.isListening) VasuCyan else VasuTextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (uiState.isListening) VasuCyan.copy(alpha = 0.6f) else VasuTextMuted.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = VasuCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isListening) "Stop Listening" else "Live Mic",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Hardware Speaker Test
                OutlinedButton(
                    onClick = { viewModel.testSpeakerHardware() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = VasuDarkCard,
                        contentColor = VasuTextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VasuTextMuted.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = VasuSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hardware Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Wake word toggle card — parity with web 489-532 ──
            WakeWordToggleCard(
                isActive = uiState.isWakeWordActive,
                wakeState = uiState.wakeWordState,
                reason = uiState.wakeWordReason,
                onToggle = viewModel::toggleWakeWord
            )

            // ── Technical Diagnostics — parity with web 534-552 ──
            DiagnosticsCard(
                model = uiState.geminiModel.ifBlank { "gemini-3.1-flash-live-preview" },
                voice = uiState.geminiVoice.ifBlank { "Kore" }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelVoiceBadgesRow(
    model: String,
    voice: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model badge — web 270-273
        Surface(
            shape = RoundedCornerShape(50),
            color = VasuDarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, VasuTextMuted.copy(alpha = 0.4f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = VasuCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = model,
                    color = VasuCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        // Voice badge — web 274-277
        Surface(
            shape = RoundedCornerShape(50),
            color = VasuDarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, VasuSuccess.copy(alpha = 0.4f)),
            modifier = Modifier.weight(0.55f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = VasuSuccess,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Voice: $voice",
                    color = VasuSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatusDisplayCard(
    mode: VoiceUiMode,
    isListening: Boolean,
    onReconnect: () -> Unit
) {
    // Map mode to web getStatusBadge() colors/labels — web 206-252
    val (dotColor, label, showReconnect) = when (mode) {
        VoiceUiMode.SPEAKING -> Triple(VasuError, "SPEAKING (Kore 24kHz)", false)
        VoiceUiMode.THINKING, VoiceUiMode.PROCESSING -> Triple(VasuWarning, "THINKING...", false)
        VoiceUiMode.LISTENING -> Triple(VasuCyan, "LISTENING (16kHz PCM)", false)
        VoiceUiMode.CONNECTED -> Triple(VasuSuccess, "CONNECTED & READY", false)
        VoiceUiMode.CONNECTING -> Triple(VasuInfo, "CONNECTING TO LIVE...", false)
        VoiceUiMode.ERROR, VoiceUiMode.PERMISSION_REQUIRED, VoiceUiMode.MIC_UNAVAILABLE -> Triple(VasuError, "ERROR ENCOUNTERED", true)
        VoiceUiMode.DISCONNECTED -> Triple(VasuTextMuted, "DISCONNECTED", true)
        else -> Triple(VasuTextMuted, "DISCONNECTED", true)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuTextMuted.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = VasuTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            if (showReconnect) {
                OutlinedButton(
                    onClick = onReconnect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reconnect", fontSize = 11.sp)
                }
            } else {
                Text(
                    text = "24 kHz PCM Stereo Out",
                    color = VasuTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorBannerCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A0F0F).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuError.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = VasuError,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Connection Notice:",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        color = VasuTextPrimary.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = VasuTextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = VasuError.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text("Configure API Key in Settings", color = Color(0xFFFF8A80), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuError.copy(alpha = 0.9f), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Retry", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WakeWordToggleCard(
    isActive: Boolean,
    wakeState: com.vasu.assistant.core.wakeword.WakeWordState,
    reason: String?,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) VasuSuccess.copy(alpha = 0.12f) else VasuDarkCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) VasuSuccess.copy(alpha = 0.3f) else VasuTextMuted.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isActive) VasuSuccess.copy(alpha = 0.2f) else VasuDarkElevated
                        )
                        .border(
                            1.dp,
                            if (isActive) VasuSuccess.copy(alpha = 0.4f) else VasuTextMuted.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                        contentDescription = null,
                        tint = if (isActive) VasuSuccess else VasuTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wake Word: \"Hello Vasu\"",
                        color = VasuTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isActive && wakeState.name == "LISTENING" -> "Continuously active in background"
                            isActive && wakeState.name == "DETECTED" -> "Wake word heard!"
                            isActive -> "Listening for wake word"
                            else -> "Tap switch to enable hands-free voice wake"
                        },
                        color = VasuTextSecondary,
                        fontSize = 11.sp
                    )
                    if (reason != null && !isActive) {
                        Text(text = reason, color = VasuTextMuted, fontSize = 10.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VasuDarkBg,
                    checkedTrackColor = VasuSuccess,
                    uncheckedThumbColor = VasuTextMuted,
                    uncheckedTrackColor = VasuDarkElevated,
                    checkedBorderColor = VasuSuccess,
                    uncheckedBorderColor = VasuTextMuted.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    model: String,
    voice: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkBg.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuTextMuted.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DiagnosticsRow(label = "Gemini Model:", value = model, valueColor = VasuCyan, isBold = true)
            DiagnosticsRow(label = "Prebuilt Voice:", value = voice, valueColor = VasuSuccess, isBold = true)
            DiagnosticsRow(label = "Input Modality:", value = "16,000 Hz Mono PCM", valueColor = VasuTextSecondary)
            DiagnosticsRow(label = "Output Audio:", value = "24,000 Hz Little-Endian PCM", valueColor = VasuTextSecondary)
        }
    }
}

@Composable
private fun DiagnosticsRow(
    label: String,
    value: String,
    valueColor: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = VasuTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
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
            .height(84.dp),
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
