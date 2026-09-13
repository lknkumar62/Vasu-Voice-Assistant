package com.vasu.assistant.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.wakeword.WakeWordState
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

    val gridItems = listOf(
        HomeGridItem(
            icon = Icons.Default.Chat,
            label = "Chat",
            action = "chat",
            color = VasuCyan,
            description = "Talk to VASU"
        ),
        HomeGridItem(
            icon = Icons.Default.Mic,
            label = "Voice",
            action = "voice",
            color = VasuPurple,
            description = "Voice commands"
        ),
        HomeGridItem(
            icon = Icons.Default.Security,
            label = "Guardian",
            action = "guardian",
            color = VasuGreen,
            description = "Voice unlock"
        ),
        HomeGridItem(
            icon = Icons.Default.Phonelink,
            label = "Permissions",
            action = "permissions",
            color = VasuWarning,
            description = "Access control"
        ),
        HomeGridItem(
            icon = Icons.Default.Speed,
            label = "Auto",
            action = "automation",
            color = VasuGreen,
            description = "Macros & Missions"
        ),
        HomeGridItem(
            icon = Icons.Default.Memory,
            label = "Memory",
            action = "memory",
            color = VasuPurple,
            description = "Remember things"
        ),
        HomeGridItem(
            icon = Icons.Default.Build,
            label = "Tools",
            action = "tools",
            color = VasuCyan,
            description = "Available actions"
        ),
        HomeGridItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            action = "settings",
            color = VasuTextSecondary,
            description = "Configure VASU"
        )
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
            Spacer(modifier = Modifier.height(20.dp))

            // HUD Elements: Last Message
            if (uiState.lastMessage.isNotBlank()) {
                VasuCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.lastMessage,
                        color = VasuTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // HUD Elements: Wake Word Status
            VasuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleWakeWord() },
                containerColor = if (uiState.isWakeWordActive) VasuGreen.copy(alpha = 0.12f) else VasuDarkCard,
                borderColor = if (uiState.isWakeWordActive) VasuGreen else VasuCyan,
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

            Spacer(modifier = Modifier.height(32.dp))

            // ORB CENTER - Primary Visual Focus
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        animateColorAsState(
                            when {
                                uiState.isListening -> VasuCyan.copy(alpha = 0.2f)
                                uiState.isSpeaking -> VasuPurple.copy(alpha = 0.2f)
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
                    modifier = Modifier.size(80.dp),
                    tint = when {
                        uiState.isListening -> VasuCyan
                        uiState.isSpeaking -> VasuPurple
                        uiState.isThinking -> VasuWarning
                        uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen
                        else -> VasuTextSecondary
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Text
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
                    uiState.isListening -> VasuCyan
                    uiState.isThinking -> VasuWarning
                    uiState.isSpeaking -> VasuPurple
                    uiState.wakeWordState == WakeWordState.LISTENING -> VasuGreen
                    uiState.wakeWordState == WakeWordState.DETECTED -> VasuGreen
                    uiState.wakeWordState == WakeWordState.MODEL_NOT_AVAILABLE -> VasuError
                    else -> VasuTextMuted
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Professional Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(gridItems) { item ->
                    HomeGridButton(
                        item = item,
                        onClick = {
                            when (item.action) {
                                "chat" -> onNavigateToChat()
                                "voice" -> onNavigateToVoice()
                                "guardian" -> onNavigateToGuardian()
                                "permissions" -> onNavigateToPermissions()
                                "automation" -> onNavigateToAutomation()
                                "memory" -> onNavigateToMemory()
                                "tools" -> onNavigateToTools()
                                "settings" -> onNavigateToSettings()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeGridButton(
    item: HomeGridItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VasuCard(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(item.color.copy(alpha = 0.12f)).border(1.dp, item.color.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(24.dp),
                    tint = item.color
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.label,
                color = VasuTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    color = VasuTextSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
