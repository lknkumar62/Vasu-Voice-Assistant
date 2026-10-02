package com.vasu.assistant.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.ui.components.AnimatedActionButton
import com.vasu.assistant.ui.components.VasuCard
import com.vasu.assistant.ui.theme.*

data class HomeGridItem(
    val icon: ImageVector,
    val label: String,
    val action: String,
    val color: androidx.compose.ui.graphics.Color,
    val description: String = ""
)

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

    val actionItems = listOf(
        HomeGridItem(Icons.Default.Chat, "Chat", "chat", VasuCrimson, "Talk to VASU"),
        HomeGridItem(Icons.Default.Mic, "Voice", "voice", VasuElectric, "Voice commands"),
        HomeGridItem(Icons.Default.Security, "Guardian", "guardian", VasuGreen, "Voice unlock"),
        HomeGridItem(Icons.Default.Phonelink, "Permissions", "permissions", VasuWarning, "Access control"),
        HomeGridItem(Icons.Default.Speed, "Auto", "automation", VasuGreen, "Macros & Missions"),
        HomeGridItem(Icons.Default.Memory, "Memory", "memory", VasuElectric, "Remember things"),
        HomeGridItem(Icons.Default.Build, "Tools", "tools", VasuCrimson, "Available actions"),
        HomeGridItem(Icons.Default.Flag, "Missions", "missions", VasuElectric, "Goals & routines"),
        HomeGridItem(Icons.Default.Lock, "Privacy", "privacy", VasuGreen, "Data & consent"),
        HomeGridItem(Icons.Default.Settings, "Settings", "settings", VasuTextSecondary, "Configure VASU")
    )

    Scaffold(
        containerColor = VasuDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VASU",
                    color = VasuTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                val (statusText, statusColor) = when {
                    uiState.isListening -> "Listening" to VasuCrimson
                    uiState.isThinking -> "Thinking" to VasuWarning
                    uiState.isSpeaking -> "Speaking" to VasuElectric
                    uiState.wakeWordState == WakeWordState.LISTENING -> "Listening" to VasuGreen
                    uiState.wakeWordState == WakeWordState.DETECTED -> "Awake" to VasuGreen
                    uiState.wakeWordState == WakeWordState.MODEL_NOT_AVAILABLE -> "Unavailable" to VasuError
                    else -> "Ready" to VasuTextMuted
                }
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            VasuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                animateColorAsState(
                                    when {
                                        uiState.isListening -> VasuCrimson.copy(alpha = 0.2f)
                                        uiState.isSpeaking -> VasuElectric.copy(alpha = 0.2f)
                                        uiState.isThinking -> VasuWarning.copy(alpha = 0.2f)
                                        uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen.copy(alpha = 0.1f)
                                        else -> VasuDarkCard
                                    }
                                ).value
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "VASU Orb",
                            modifier = Modifier.size(56.dp),
                            tint = when {
                                uiState.isListening -> VasuCrimson
                                uiState.isSpeaking -> VasuElectric
                                uiState.isThinking -> VasuWarning
                                uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen
                                else -> VasuTextSecondary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when {
                            uiState.isListening -> "Listening..."
                            uiState.isThinking -> "Thinking..."
                            uiState.isSpeaking -> "Speaking..."
                            uiState.wakeWordState == WakeWordState.LISTENING -> "Say \"Hello Vasu\""
                            uiState.wakeWordState == WakeWordState.DETECTED -> "Wake word detected!"
                            uiState.wakeWordState == WakeWordState.MODEL_NOT_AVAILABLE -> "Wake word unavailable"
                            else -> "Ready"
                        },
                        color = when {
                            uiState.isListening -> VasuCrimson
                            uiState.isThinking -> VasuWarning
                            uiState.isSpeaking -> VasuElectric
                            uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen
                            uiState.wakeWordState == WakeWordState.DETECTED -> VasuGreen
                            uiState.wakeWordState == WakeWordState.MODEL_NOT_AVAILABLE -> VasuError
                            else -> VasuTextMuted
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (uiState.lastMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.lastMessage,
                            color = VasuTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            VasuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleWakeWord() },
                shape = RoundedCornerShape(18.dp),
                containerColor = if (uiState.isWakeWordActive) VasuGreen.copy(alpha = 0.12f) else VasuDarkCard,
                borderColor = if (uiState.isWakeWordActive) VasuGreen else VasuCrimson,
                borderAlpha = if (uiState.isWakeWordActive) 0.25f else 0.12f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (uiState.isWakeWordActive) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                            contentDescription = "Wake Word",
                            tint = if (uiState.isWakeWordActive) VasuGreen else VasuTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Wake Word",
                                color = VasuTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (uiState.isWakeWordActive) "\"Hello Vasu\" listening" else "Tap to enable",
                                color = VasuTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isWakeWordActive,
                        onCheckedChange = { viewModel.toggleWakeWord() },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VasuDarkBg,
                            checkedTrackColor = VasuGreen,
                            uncheckedThumbColor = VasuTextMuted,
                            uncheckedTrackColor = VasuDarkSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = VasuDarkElevated
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ACTIONS",
                color = VasuTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                actionItems.forEach { item ->
                    AnimatedActionButton(
                        label = item.label,
                        description = item.description,
                        icon = item.icon,
                        onClick = {
                            when (item.action) {
                                "chat" -> onNavigateToChat()
                                "voice" -> onNavigateToVoice()
                                "guardian" -> onNavigateToGuardian()
                                "permissions" -> onNavigateToPermissions()
                                "automation" -> onNavigateToAutomation()
                                "memory" -> onNavigateToMemory()
                                "tools" -> onNavigateToTools()
                                "missions" -> onNavigateToMissions()
                                "privacy" -> onNavigateToPrivacy()
                                "settings" -> onNavigateToSettings()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "VASU · dark theme",
                    color = VasuTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }
}
