package com.vasu.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    var wakeWordEnabled by remember { mutableStateOf(true) }
    var voiceGuardEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkTheme by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // AI Provider Section
            SettingsSection(title = "AI Provider") {
                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    title = "AI Model",
                    subtitle = "OpenAI GPT-4o (Online)",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = "Offline Mode",
                    subtitle = "Local command engine",
                    onClick = { }
                )
            }

            // Voice Section
            SettingsSection(title = "Voice") {
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Wake Word",
                    subtitle = '"Hello Vasu"',
                    enabled = wakeWordEnabled,
                    onToggle = { wakeWordEnabled = it }
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "Voice Profile",
                    subtitle = "Default Hindi + English",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.VolumeUp,
                    title = "TTS Settings",
                    subtitle = "Speech rate, pitch, volume",
                    onClick = { }
                )
            }

            // Security Section
            SettingsSection(title = "Security") {
                SettingsToggleItem(
                    icon = Icons.Default.Shield,
                    title = "Voice Guardian",
                    subtitle = "Speaker verification",
                    enabled = voiceGuardEnabled,
                    onToggle = { voiceGuardEnabled = it }
                )
                SettingsItem(
                    icon = Icons.Default.People,
                    title = "Enrolled Voices",
                    subtitle = "Manage speaker profiles",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = "Permissions",
                    subtitle = "App permissions manager",
                    onClick = { }
                )
            }

            // Features Section
            SettingsSection(title = "Features") {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility",
                    subtitle = "Automation service",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Notification listener",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera & Vision",
                    subtitle = "Photo, video, OCR",
                    onClick = { }
                )
            }

            // App Section
            SettingsSection(title = "App") {
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "Memory",
                    subtitle = "Conversation & user memory",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Computer,
                    title = "PC Connect",
                    subtitle = "Pair with your computer",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About VASU",
                    subtitle = "Version 1.0.0",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = VasuCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = VasuDarkCard
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = VasuDarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VasuTextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = VasuTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = VasuTextMuted,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VasuTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VasuTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = VasuTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = VasuTextMuted,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VasuDarkBg,
                checkedTrackColor = VasuCyan,
                uncheckedThumbColor = VasuTextMuted,
                uncheckedTrackColor = VasuDarkSurface
            )
        )
    }
}
