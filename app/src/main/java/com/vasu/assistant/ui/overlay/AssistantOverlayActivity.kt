package com.vasu.assistant.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssistantState {
    LISTENING,
    THINKING,
    RESPONDING,
    IDLE
}

@AndroidEntryPoint
class AssistantOverlayActivity : ComponentActivity() {

    @Inject lateinit var sttManager: STTManager
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var aiOrchestrator: AIOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS & STT
        ttsManager.initialize()

        setContent {
            VasuTheme {
                AssistantOverlayContent(
                    onDismiss = { finishAndClose() },
                    onCommandSubmit = { cmd -> processCommand(cmd) },
                    sttManager = sttManager,
                    ttsManager = ttsManager,
                    aiOrchestrator = aiOrchestrator
                )
            }
        }

        // Auto start listening on launch
        lifecycleScope.launch {
            delay(300)
            sttManager.startListening()
        }
    }

    private fun processCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        lifecycleScope.launch {
            val response = aiOrchestrator.processInput(trimmed)
            ttsManager.speak(response)
        }
    }

    private fun finishAndClose() {
        sttManager.stopListening()
        ttsManager.stop()
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        sttManager.stopListening()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, AssistantOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun AssistantOverlayContent(
    onDismiss: () -> Unit,
    onCommandSubmit: (String) -> Unit,
    sttManager: STTManager,
    ttsManager: TTSManager,
    aiOrchestrator: AIOrchestrator
) {
    val sttState by sttManager.state.collectAsState()
    val ttsState by ttsManager.state.collectAsState()
    val partialText by sttManager.partialResults.collectAsState(initial = "")
    val lastResponse by aiOrchestrator.lastResponse.collectAsState()
    val isAiProcessing by aiOrchestrator.isProcessing.collectAsState()

    var textQuery by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }
    var displayedTranscript by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Determine current assistant state
    val assistantState = when {
        isAiProcessing -> AssistantState.THINKING
        ttsState == TTSState.SPEAKING -> AssistantState.RESPONDING
        sttState == STTState.LISTENING -> AssistantState.LISTENING
        else -> AssistantState.IDLE
    }

    // Auto-listen to speech results
    LaunchedEffect(Unit) {
        sttManager.results.collect { result ->
            if (result.isFinal && result.text.isNotBlank()) {
                displayedTranscript = result.text
                onCommandSubmit(result.text)
            }
        }
    }

    // Auto dismiss 2.5s after TTS finishes speaking
    LaunchedEffect(ttsState, lastResponse) {
        if (ttsState == TTSState.READY && !lastResponse.isNullOrBlank() && !isAiProcessing) {
            delay(2800)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Assistant Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Intercept click
                )
                .shadow(24.dp, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = VasuDarkCard.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Handle & Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(VasuCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("V", color = VasuDarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VASU Assistant",
                            color = VasuTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { showTextInput = !showTextInput },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showTextInput) Icons.Default.Mic else Icons.Default.Keyboard,
                                contentDescription = "Toggle Input",
                                tint = VasuTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = VasuTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Spoken Transcript / Query Area
                val speechDisplay = partialText.ifBlank { displayedTranscript }
                if (speechDisplay.isNotBlank()) {
                    Text(
                        text = "\"$speechDisplay\"",
                        color = VasuTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // AI Response Area
                if (!lastResponse.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkBg.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = lastResponse ?: "",
                            color = VasuCyanLight,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Interactive Text Input Mode
                AnimatedVisibility(visible = showTextInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textQuery,
                            onValueChange = { textQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask VASU anything...", color = VasuTextMuted, fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textQuery.isNotBlank()) {
                                        displayedTranscript = textQuery
                                        onCommandSubmit(textQuery)
                                        textQuery = ""
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VasuCyan,
                                unfocusedBorderColor = VasuTextMuted,
                                focusedTextColor = VasuTextPrimary,
                                unfocusedTextColor = VasuTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (textQuery.isNotBlank()) {
                                    displayedTranscript = textQuery
                                    onCommandSubmit(textQuery)
                                    textQuery = ""
                                    keyboardController?.hide()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = VasuCyan)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = VasuDarkBg)
                        }
                    }
                }

                if (!showTextInput) {
                    // Assistant Glowing Orb & Waveform Animation
                    AssistantVisualizer(
                        state = assistantState,
                        onMicClick = {
                            if (assistantState == AssistantState.LISTENING) {
                                sttManager.stopListening()
                            } else {
                                sttManager.startListening()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Assistant State Text
                Text(
                    text = when (assistantState) {
                        AssistantState.LISTENING -> "Listening to your voice..."
                        AssistantState.THINKING -> "Thinking..."
                        AssistantState.RESPONDING -> "Speaking..."
                        AssistantState.IDLE -> "Tap mic or say command"
                    },
                    color = when (assistantState) {
                        AssistantState.LISTENING -> VasuCyan
                        AssistantState.THINKING -> VasuWarning
                        AssistantState.RESPONDING -> VasuPurpleLight
                        AssistantState.IDLE -> VasuTextMuted
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AssistantVisualizer(
    state: AssistantState,
    onMicClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val primaryColor = when (state) {
        AssistantState.LISTENING -> VasuCyan
        AssistantState.THINKING -> VasuWarning
        AssistantState.RESPONDING -> VasuPurple
        AssistantState.IDLE -> VasuTextSecondary
    }

    Box(
        modifier = Modifier
            .size(90.dp)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Glowing Ring
        if (state != AssistantState.IDLE) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (state == AssistantState.LISTENING) scale else 1.0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Inner Orb
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(primaryColor, VasuCyanDark)
                    )
                )
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    AssistantState.LISTENING -> Icons.Default.GraphicEq
                    AssistantState.THINKING -> Icons.Default.Autorenew
                    AssistantState.RESPONDING -> Icons.Default.VolumeUp
                    AssistantState.IDLE -> Icons.Default.Mic
                },
                contentDescription = "Voice State",
                tint = VasuDarkBg,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
