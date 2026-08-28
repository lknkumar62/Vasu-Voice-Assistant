package com.vasu.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = VasuCyan) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VasuTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
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

            SettingsSection(title = "AI Provider") {
                SettingsItem(icon = Icons.Default.SmartToy, title = "AI Model", subtitle = "OpenAI GPT-4o (Online)")
                SettingsItem(icon = Icons.Default.Cloud, title = "Offline Mode", subtitle = "Local command engine")
            }

            SettingsSection(title = "Voice") {
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Wake Word",
                    subtitle = "\"Hello Vasu\"",
                    enabled = wakeWordEnabled,
                    onToggle = { wakeWordEnabled = it }
                )
                SettingsItem(icon = Icons.Default.Mic, title = "Voice Profile", subtitle = "Default Hindi + English")
                SettingsItem(icon = Icons.Default.VolumeUp, title = "TTS Settings", subtitle = "Speech rate, pitch, volume")
            }

            SettingsSection(title = "Security") {
                SettingsToggleItem(
                    icon = Icons.Default.Security,
                    title = "Voice Guard",
                    subtitle = "Speaker verification",
                    enabled = voiceGuardEnabled,
                    onToggle = { voiceGuardEnabled = it }
                )
            }

            SettingsSection(title = "Notifications") {
                SettingsToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Access",
                    subtitle = "Read and manage notifications",
                    enabled = notificationsEnabled,
                    onToggle = { notificationsEnabled = it }
                )
            }

            SettingsSection(title = "Appearance") {
                SettingsToggleItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Always dark",
                    enabled = darkTheme,
                    onToggle = { darkTheme = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            color = VasuCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = title, tint = VasuTextSecondary)
            Column {
                Text(title, color = VasuTextPrimary, fontSize = 16.sp)
                Text(subtitle, color = VasuTextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = VasuTextSecondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VasuTextPrimary, fontSize = 16.sp)
                Text(subtitle, color = VasuTextMuted, fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
