package com.vasu.assistant.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGuardian: () -> Unit = {},
    onNavigateToMissions: () -> Unit = {},
    onNavigateToAutomation: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VASU",
                        fontWeight = FontWeight.Bold,
                        color = VasuCyan
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = VasuTextSecondary
                        )
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VasuAvatarCircle(
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    isThinking = uiState.isThinking,
                    isWakeWordActive = uiState.isWakeWordActive
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = when {
                        uiState.isListening -> "Listening..."
                        uiState.isThinking -> "Thinking..."
                        uiState.isSpeaking -> "Speaking..."
                        uiState.wakeWordState == WakeWordState.LISTENING -> "Say 'Hello Vasu'"
                        uiState.wakeWordState == WakeWordState.DETECTED -> "Wake word detected!"
                        else -> "Ready"
                    },
                    color = when {
                        uiState.isListening -> VasuCyan
                        uiState.isThinking -> VasuWarning
                        uiState.isSpeaking -> VasuPurple
                        uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen
                        uiState.wakeWordState == WakeWordState.DETECTED -> VasuGreen
                        else -> VasuTextMuted
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = uiState.lastMessage,
                        modifier = Modifier.padding(20.dp),
                        color = VasuTextPrimary,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleWakeWord() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isWakeWordActive) VasuGreen.copy(alpha = 0.1f) else VasuDarkCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.isWakeWordActive) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                                contentDescription = "Wake Word",
                                tint = if (uiState.isWakeWordActive) VasuGreen else VasuTextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Wake Word", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (uiState.isWakeWordActive) '"Hello Vasu" active' else "Tap to enable",
                                    color = VasuTextMuted, fontSize = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isWakeWordActive,
                            onCheckedChange = { viewModel.toggleWakeWord() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VasuDarkBg,
                                checkedTrackColor = VasuGreen,
                                uncheckedThumbColor = VasuTextMuted,
                                uncheckedTrackColor = VasuDarkSurface
                            )
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Chat, Voice, Guardian, Settings
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        uiState.quickActions.take(4).forEach { action ->
                            QuickActionButton(action = action, onClick = {
                                when (action.action) {
                                    "chat" -> onNavigateToChat()
                                    "voice" -> onNavigateToVoice()
                                    "guardian" -> onNavigateToGuardian()
                                    "settings" -> onNavigateToSettings()
                                }
                            })
                        }
                    }
                    // Row 2: Missions, Automation, Memory, Tools
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        uiState.quickActions.drop(4).forEach { action ->
                            QuickActionButton(action = action, onClick = {
                                when (action.action) {
                                    "missions" -> onNavigateToMissions()
                                    "automation" -> onNavigateToAutomation()
                                    "memory" -> onNavigateToMemory()
                                    "tools" -> onNavigateToTools()
                                }
                            })
                        }
                    }
                }

                MicButton(
                    isListening = uiState.isListening,
                    onClick = { viewModel.toggleListening() }
                )
            }
        }
    }
}

@Composable
fun VasuAvatarCircle(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    isWakeWordActive: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking || isWakeWordActive) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowColor by animateColorAsState(
        targetValue = when {
            isListening -> VasuCyan
            isSpeaking -> VasuPurple
            isThinking -> VasuWarning
            isWakeWordActive -> VasuGreen
            else -> VasuDarkCard
        },
        animationSpec = tween(500),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Box(
            modifier = Modifier.size(200.dp).scale(scale)
                .shadow(elevation = if (isListening || isSpeaking || isWakeWordActive) 32.dp else 8.dp, shape = CircleShape, ambientColor = glowColor, spotColor = glowColor)
                .clip(CircleShape).background(VasuDarkSurface)
        )
        Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(VasuDarkCard), contentAlignment = Alignment.Center) {
            Text(
                text = "VASU",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isListening -> VasuCyan; isSpeaking -> VasuPurple
                    isThinking -> VasuWarning; isWakeWordActive -> VasuGreen
                    else -> VasuTextSecondary
                }
            )
        }
    }
}

@Composable
fun QuickActionButton(action: QuickAction, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Card(
            modifier = Modifier.size(56.dp),
            colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = action.icon, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = action.label, color = VasuTextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun MicButton(isListening: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(modifier = Modifier.size(88.dp).scale(pulse).clip(CircleShape).background(VasuCyan.copy(alpha = 0.2f)))
        }
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            containerColor = if (isListening) VasuError else VasuCyan,
            contentColor = VasuDarkBg,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start Listening",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
