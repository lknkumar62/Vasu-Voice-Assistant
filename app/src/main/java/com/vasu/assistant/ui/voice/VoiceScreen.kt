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
import com.vasu.assistant.ui.components.GlassCard
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
                        color = VasuCrimson,
                        fontSize = 18.sp
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Top Badges Row
            ModelVoiceBadgesRow(
                model = uiState.geminiModel.ifBlank { "gemini-3.1-flash-live-preview" },
                voice = uiState.geminiVoice.ifBlank { "Kore" }
            )

            // 2. Status Hub
            StatusDisplayCard(
                mode = uiState.mode,
                isListening = uiState.isListening,
                isSpeaking = uiState.isSpeaking,
                activeSpeaker = if (uiState.isSpeaking) uiState.geminiVoice.ifBlank { "Kore" } else null,
                onReconnect = viewModel::reconnect
            )

            // 3. Error Banner
            if (uiState.errorMessage != null) {
                ErrorBannerCard(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::reconnect,
                    onDismiss = viewModel::clearError,
                    onOpenSettings = onNavigateToSettings
                )
            }

            // 4. Waveform Visualizer Card
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WaveformVisualizer(
                        isListening = uiState.isListening,
                        rmsLevel = uiState.rmsLevel,
                        isSpeaking = uiState.isSpeaking
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MicLevelMeter(
                        rmsLevel = uiState.rmsLevel,
                        isListening = uiState.isListening,
                        isSpeaking = uiState.isSpeaking
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    VoiceStatePills(mode = uiState.mode)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = uiState.statusMessage,
                        color = when (uiState.mode) {
                            VoiceUiMode.LISTENING -> VasuListening
                            VoiceUiMode.SPEAKING -> VasuSpeaking
                            VoiceUiMode.THINKING, VoiceUiMode.PROCESSING -> VasuThinking
                            VoiceUiMode.CONNECTING, VoiceUiMode.CONNECTED -> VasuListening
                            VoiceUiMode.PERMISSION_REQUIRED, VoiceUiMode.MIC_UNAVAILABLE, VoiceUiMode.ERROR -> VasuError
                            else -> VasuTextSecondary
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 5. Transcript Area (Glass Card)
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.transcript.isNotEmpty()) {
                        Text(
                            text = "You",
                            color = VasuListening,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.transcript,
                            color = VasuCrimson,
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

                    if (uiState.lastResponse.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = VasuCrimson.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Vasu",
                            color = VasuSpeaking,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

            // 6. Voice Control (Mic Button)
            VoiceMicButton(
                isListening = uiState.isListening,
                isSpeaking = uiState.isSpeaking,
                rmsLevel = uiState.rmsLevel,
                onClick = { viewModel.toggleListening() }
            )

            // 7. Primary Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.testKoreVoice() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCrimson, contentColor = VasuDarkBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Kore Voice", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.testSpeakerHardware() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuTextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VasuCrimson.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp), tint = VasuSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hardware Test", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 8. Wake Word Toggle
            WakeWordToggleCard(
                isActive = uiState.isWakeWordActive,
                wakeState = uiState.wakeWordState,
                reason = uiState.wakeWordReason,
                onToggle = viewModel::toggleWakeWord
            )

            // 9. Technical Diagnostics
            DiagnosticsCard(
                model = uiState.geminiModel.ifBlank { "gemini-3.1-flash-live-preview" },
                voice = uiState.geminiVoice.ifBlank { "Kore" }
            )

            Spacer(modifier = Modifier.height(24.dp))
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VasuDarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, VasuCrimson.copy(alpha = 0.18f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(VasuCrimson.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = VasuCrimson, modifier = Modifier.size(10.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = model, color = VasuCrimson, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        // Voice Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VasuDarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, VasuSuccess.copy(alpha = 0.25f)),
            modifier = Modifier.weight(0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(VasuSuccess.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = VasuSuccess, modifier = Modifier.size(10.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Voice: $voice", color = VasuSuccess, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatusDisplayCard(
    mode: VoiceUiMode,
    isListening: Boolean,
    onReconnect: () -> Unit,
    isSpeaking: Boolean = false,
    activeSpeaker: String? = null
) {
    val (dotColor, label, showReconnect) = when (mode) {
        VoiceUiMode.SPEAKING -> Triple(VasuSpeaking, "SPEAKING (${activeSpeaker ?: "Kore"})", false)
        VoiceUiMode.THINKING, VoiceUiMode.PROCESSING -> Triple(VasuThinking, "THINKING...", false)
        VoiceUiMode.LISTENING -> Triple(VasuListening, "LISTENING (You)", false)
        VoiceUiMode.CONNECTED -> Triple(VasuSuccess, "CONNECTED & READY", false)
        VoiceUiMode.CONNECTING -> Triple(VasuInfo, "CONNECTING...", false)
        VoiceUiMode.OFFLINE_MODE, VoiceUiMode.GEMINI_UNAVAILABLE -> Triple(VasuWarning, "OFFLINE MODE", true)
        VoiceUiMode.ERROR, VoiceUiMode.PERMISSION_REQUIRED, VoiceUiMode.MIC_UNAVAILABLE -> Triple(VasuError, "SYSTEM ERROR", true)
        VoiceUiMode.DISCONNECTED -> Triple(VasuTextMuted, "DISCONNECTED", true)
        else -> Triple(VasuIdle, "IDLE", true)
    }
    // Gentle pulse for the live states (listening / speaking) only.
    val infiniteTransition = rememberInfiniteTransition(label = "status_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking || mode == VoiceUiMode.SPEAKING || mode == VoiceUiMode.LISTENING) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor.copy(alpha = dotAlpha)))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = label, color = dotColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            if (showReconnect) {
                OutlinedButton(
                    onClick = onReconnect,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuCrimson),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VasuCrimson.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reconnect", fontSize = 10.sp)
                }
            } else {
                Text(text = "24 kHz PCM Stereo", color = VasuTextMuted, fontSize = 10.sp)
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = VasuError.copy(alpha = 0.6f),
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VasuError, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Connection Notice:", color = VasuError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message, color = VasuTextPrimary.copy(alpha = 0.95f), fontSize = 12.sp, lineHeight = 16.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = VasuTextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenSettings) {
                    Text("Settings", color = VasuTextSecondary, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuError.copy(alpha = 0.9f), contentColor = VasuTextPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Retry", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MicLevelMeter(
    rmsLevel: Float,
    isListening: Boolean,
    isSpeaking: Boolean
) {
    // Live RMS mic meter while recording; idle placeholder otherwise.
    // rmsLevel is 0..1 from the existing STT flow — UI-only rendering.
    val level = rmsLevel.coerceIn(0f, 1f)
    val meterColor = when {
        isListening -> VasuListening
        isSpeaking -> VasuSpeaking
        else -> VasuTextMuted
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = level,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = meterColor,
            trackColor = VasuDarkElevated
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                isListening -> "Mic level — live"
                isSpeaking -> "Vasu is speaking"
                else -> "Mic idle"
            },
            color = VasuTextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun VoiceStatePills(mode: VoiceUiMode) {
    // Clear IDLE vs LISTENING vs SPEAKING indicator with theme colors.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatePill(label = "IDLE", active = mode == VoiceUiMode.IDLE || mode == VoiceUiMode.CONNECTED || mode == VoiceUiMode.DISCONNECTED, activeColor = VasuTextMuted)
        StatePill(label = "LISTENING", active = mode == VoiceUiMode.LISTENING, activeColor = VasuListening)
        StatePill(label = "SPEAKING", active = mode == VoiceUiMode.SPEAKING, activeColor = VasuSpeaking)
    }
}

@Composable
private fun StatePill(label: String, active: Boolean, activeColor: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (active) activeColor.copy(alpha = 0.15f) else VasuDarkElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) activeColor.copy(alpha = 0.5f) else VasuTextMuted.copy(alpha = 0.2f)
        )
    ) {
        Text(
            text = label,
            color = if (active) activeColor else VasuTextMuted,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WakeWordToggleCard(
    isActive: Boolean,
    wakeState: com.vasu.assistant.core.wakeword.WakeWordState,
    reason: String?,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isActive) VasuSuccess.copy(alpha = 0.05f) else VasuDarkCard,
        borderColor = if (isActive) VasuSuccess.copy(alpha = 0.2f) else VasuCrimson.copy(alpha = 0.12f),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) VasuSuccess.copy(alpha = 0.15f) else VasuDarkElevated)
                        .border(1.dp, if (isActive) VasuSuccess.copy(alpha = 0.3f) else VasuTextMuted.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
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
                    Text("Wake Word: \"Hello Vasu\"", color = VasuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            isActive && wakeState.name == "LISTENING" -> "Active in background"
                            isActive && wakeState.name == "DETECTED" -> "Wake word heard!"
                            isActive -> "Listening for wake word"
                            else -> "Enable hands-free voice wake"
                        },
                        color = VasuTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Switch(
                checked = isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VasuDarkBg,
                    checkedTrackColor = VasuSuccess,
                    uncheckedThumbColor = VasuTextMuted,
                    uncheckedTrackColor = VasuDarkElevated
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
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticsRow(label = "Gemini Model:", value = model, valueColor = VasuCrimson, isBold = true)
            DiagnosticsRow(label = "Prebuilt Voice:", value = voice, valueColor = VasuSuccess, isBold = true)
            DiagnosticsRow(label = "Input Modality:", value = "16,000 Hz Mono PCM", valueColor = VasuTextSecondary)
            DiagnosticsRow(label = "Output Audio:", value = "24,000 Hz Little-Endian PCM", valueColor = VasuTextSecondary)
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String, valueColor: Color, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = VasuTextSecondary, fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun WaveformVisualizer(
    isListening: Boolean,
    rmsLevel: Float = 0f,
    isSpeaking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val active = isListening || isSpeaking
    val barColor = when {
        isListening -> VasuListening
        isSpeaking -> VasuSpeaking
        else -> VasuTextMuted.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(25) { index ->
            val delay = index * 80
            val baseHeight = if (active) (15f + (index % 5) * 10f) else 6f
            // Live RMS drives bar amplitude while recording; gentle idle
            // motion while speaking so SPEAKING reads distinctly.
            val rmsModulator = when {
                isListening -> (rmsLevel.coerceIn(0f, 1f) * 60f)
                isSpeaking -> 12f
                else -> 0f
            }
            
            val height by infiniteTransition.animateFloat(
                initialValue = baseHeight,
                targetValue = baseHeight + rmsModulator,
                animationSpec = infiniteRepeatable(
                    animation = tween(600 + delay, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.coerceIn(6f, 70f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun VoiceMicButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    onClick: () -> Unit,
    rmsLevel: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val buttonColor = when {
        isListening -> VasuListening
        isSpeaking -> VasuSpeaking
        else -> VasuCrimson
    }
    val micLabel = when {
        isListening -> "Listening — tap to stop"
        isSpeaking -> "Speaking — tap to interrupt"
        else -> "Tap mic to speak"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (isListening || isSpeaking) {
                // RMS-reactive halo while recording: louder input widens the glow.
                val haloAlpha = (0.15f + rmsLevel.coerceIn(0f, 1f) * 0.25f)
                    .let { if (isSpeaking) 0.2f else it }
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = haloAlpha))
                )
            }

            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.size(80.dp),
                containerColor = buttonColor,
                contentColor = VasuDarkBg,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = micLabel,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = micLabel,
            color = when {
                isListening -> VasuListening
                isSpeaking -> VasuSpeaking
                else -> VasuTextSecondary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
