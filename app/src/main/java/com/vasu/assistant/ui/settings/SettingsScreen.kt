package com.vasu.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.network.NetworkState
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.tts.VoiceGender
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.ui.theme.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }

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

            SettingsSection(title = "AI PROVIDER") {
                SettingsToggleItem(
                    icon = Icons.Default.SmartToy,
                    title = "Enable Gemini",
                    subtitle = if (state.geminiEnabled) "Cloud reasoning on" else "Offline commands only",
                    enabled = state.geminiEnabled,
                    onToggle = viewModel::setGeminiEnabled
                )

                if (!state.keyStoreAvailable) {
                    StatusRow(
                        icon = Icons.Default.Warning,
                        label = "Secure storage unavailable",
                        detail = "This device's keystore could not be opened, so a key cannot be saved.",
                        color = VasuError
                    )
                }

                SettingsCard {
                    Text("API key", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    state.maskedKey?.let {
                        Text("Saved: $it", color = VasuTextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste your Gemini API key", color = VasuTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key",
                                    tint = VasuTextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan,
                            unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary,
                            unfocusedTextColor = VasuTextPrimary,
                            cursorColor = VasuCyan
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveKey(keyInput)
                                keyInput = ""
                            },
                            enabled = keyInput.isNotBlank() && state.keyStoreAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VasuCyan,
                                contentColor = VasuDarkBg
                            )
                        ) { Text("Save") }

                        OutlinedButton(
                            onClick = viewModel::testConnection,
                            enabled = state.hasKey && state.connectionTest != ConnectionTest.TESTING
                        ) {
                            Text(
                                if (state.connectionTest == ConnectionTest.TESTING) "Testing..." else "Test connection",
                                color = VasuCyan
                            )
                        }
                    }

                    if (state.hasKey) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::removeKey) {
                            Text("Remove key", color = VasuError)
                        }
                    }
                }

                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Model",
                            color = VasuTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = viewModel::refreshModels,
                            enabled = state.hasKey && !state.modelsRefreshing
                        ) {
                            Text(
                                if (state.modelsRefreshing) "Checking..." else "Refresh list",
                                color = VasuCyan
                            )
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { modelMenuOpen = true }) {
                            Text(state.model, color = VasuTextPrimary)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VasuTextSecondary)
                        }
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false }
                        ) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.setModel(model)
                                        modelMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.modelMessage.ifBlank {
                            if (state.modelsDiscovered) {
                                "${state.availableModels.size} models this key can use, read from Google."
                            } else {
                                "Not checked against your key yet - these are the configured defaults."
                            }
                        },
                        color = VasuTextMuted,
                        fontSize = 12.sp
                    )
                }

                SettingsToggleItem(
                    icon = Icons.Default.SwapHoriz,
                    title = "Allow model fallback",
                    subtitle = "If your model is unavailable, try the next configured one instead of failing.",
                    enabled = state.allowModelFallback,
                    onToggle = viewModel::setAllowModelFallback
                )

                state.activeModel?.takeIf { it != state.model }?.let {
                    StatusRow(
                        icon = Icons.Default.SwapHoriz,
                        label = "Answering with $it",
                        detail = "${state.model} was unavailable, so a configured fallback is in use.",
                        color = VasuWarning
                    )
                }

                StatusRow(
                    icon = if (state.network == NetworkState.ONLINE) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = when (state.network) {
                        NetworkState.ONLINE -> "Online"
                        NetworkState.DEGRADED -> "Connected, but no working internet"
                        NetworkState.OFFLINE -> "Offline"
                    },
                    detail = if (state.cloudUsable) "Gemini can be reached."
                    else "Cloud AI is unavailable; VASU will use offline commands.",
                    color = if (state.network == NetworkState.ONLINE) VasuSuccess else VasuWarning
                )

                StatusRow(
                    icon = when (state.connectionTest) {
                        ConnectionTest.PASSED -> Icons.Default.CheckCircle
                        ConnectionTest.FAILED -> Icons.Default.Error
                        else -> Icons.Default.HelpOutline
                    },
                    label = when (state.connectionTest) {
                        ConnectionTest.NOT_TESTED -> "Connection not tested"
                        ConnectionTest.TESTING -> "Testing connection"
                        ConnectionTest.PASSED -> "Connection verified"
                        ConnectionTest.FAILED -> "Connection failed"
                    },
                    detail = state.connectionMessage.ifBlank { null },
                    color = when (state.connectionTest) {
                        ConnectionTest.PASSED -> VasuSuccess
                        ConnectionTest.FAILED -> VasuError
                        else -> VasuTextSecondary
                    }
                )

                if (state.lastSuccessfulConnection > 0L) {
                    StatusRow(
                        icon = Icons.Default.History,
                        label = "Last successful connection",
                        detail = DateFormat.getDateTimeInstance()
                            .format(Date(state.lastSuccessfulConnection)),
                        color = VasuTextSecondary
                    )
                }

                state.lastError?.let {
                    StatusRow(
                        icon = Icons.Default.BugReport,
                        label = "Last error",
                        detail = it,
                        color = VasuError
                    )
                }
            }

            SettingsSection(title = "VOICE") {
                SettingsCard {
                    Text("Language", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { languageMenuOpen = true }) {
                            Text(
                                VasuSettings.LANGUAGES.firstOrNull { it.first == state.voiceProfile.language }
                                    ?.second ?: state.voiceProfile.language,
                                color = VasuTextPrimary
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VasuTextSecondary)
                        }
                        DropdownMenu(
                            expanded = languageMenuOpen,
                            onDismissRequest = { languageMenuOpen = false }
                        ) {
                            VasuSettings.LANGUAGES.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setLanguage(tag)
                                        languageMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    if (state.installedVoices.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Installed on this device: ${state.installedVoices.joinToString()}",
                            color = VasuTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                SliderCard(
                    label = "Speech rate",
                    value = state.voiceProfile.speechRate,
                    range = VasuSettings.RATE_RANGE,
                    onChange = viewModel::setSpeechRate
                )
                SliderCard(
                    label = "Pitch",
                    value = state.voiceProfile.pitch,
                    range = VasuSettings.PITCH_RANGE,
                    onChange = viewModel::setPitch
                )
                SliderCard(
                    label = "Volume",
                    value = state.voiceProfile.volume,
                    range = 0f..1f,
                    onChange = viewModel::setVolume
                )

                SettingsCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::testVoice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VasuCyan,
                                contentColor = VasuDarkBg
                            )
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test voice")
                        }
                        OutlinedButton(onClick = viewModel::openTtsSettings) {
                            Text("Install voices", color = VasuCyan)
                        }
                    }
                }

                StatusRow(
                    icon = if (state.voiceStatus.gender == VoiceGender.FEMALE) Icons.Default.CheckCircle
                    else Icons.Default.HelpOutline,
                    label = when (state.voiceStatus.gender) {
                        VoiceGender.FEMALE -> "Female voice in use"
                        VoiceGender.UNLABELLED -> "Engine default voice"
                        VoiceGender.NO_VOICES -> "No voices reported"
                        VoiceGender.UNKNOWN -> "Voice not selected yet"
                    },
                    detail = when (state.voiceStatus.gender) {
                        VoiceGender.FEMALE -> state.voiceStatus.voiceName
                        VoiceGender.UNLABELLED ->
                            "This speech engine does not label voice gender, so VASU cannot pick a female one. Install Google or Samsung TTS for more voices, or raise the pitch above."
                        VoiceGender.NO_VOICES ->
                            "The speech engine listed no voices. Install voice data from Install voices."
                        VoiceGender.UNKNOWN -> "Press Test voice to initialise the engine."
                    },
                    color = if (state.voiceStatus.gender == VoiceGender.FEMALE) VasuSuccess else VasuTextSecondary
                )
            }

            SettingsSection(title = "WAKE WORD") {
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "\"Hello Vasu\"",
                    subtitle = "Listen in the background",
                    enabled = state.wakeWordEnabled,
                    onToggle = viewModel::setWakeWordEnabled
                )
                StatusRow(
                    icon = when (state.wakeWordState) {
                        WakeWordState.LISTENING, WakeWordState.DETECTED -> Icons.Default.CheckCircle
                        WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> Icons.Default.Error
                        WakeWordState.IDLE -> Icons.Default.HelpOutline
                    },
                    label = when (state.wakeWordState) {
                        WakeWordState.IDLE -> "Not running"
                        WakeWordState.LISTENING -> "Listening"
                        WakeWordState.DETECTED -> "Wake word heard"
                        WakeWordState.MODEL_NOT_AVAILABLE -> "Unavailable"
                        WakeWordState.ERROR -> "Error"
                    },
                    detail = state.wakeWordReason,
                    color = when (state.wakeWordState) {
                        WakeWordState.LISTENING, WakeWordState.DETECTED -> VasuSuccess
                        WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> VasuError
                        WakeWordState.IDLE -> VasuTextSecondary
                    }
                )
            }

            SettingsSection(title = "SECURITY") {
                SettingsToggleItem(
                    icon = Icons.Default.Security,
                    title = "Voice Guard",
                    subtitle = "Require speaker verification for risky tools",
                    enabled = state.voiceGuardEnabled,
                    onToggle = viewModel::setVoiceGuardEnabled
                )
            }

            SettingsSection(title = "PERMISSIONS") {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Screen control",
                    subtitle = "Grant VASU's accessibility service",
                    onClick = viewModel::openAccessibilitySettings
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification access",
                    subtitle = "Read and act on notifications",
                    onClick = viewModel::openNotificationAccessSettings
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "App permissions",
                    subtitle = "Microphone, contacts, phone, SMS",
                    onClick = viewModel::openAppSettings
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SliderCard(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(String.format("%.2f", value), color = VasuCyan, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VasuCyan,
                activeTrackColor = VasuCyan,
                inactiveTrackColor = VasuTextMuted
            )
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    detail: String?,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Column {
                Text(label, color = color, fontSize = 14.sp)
                detail?.let { Text(it, color = VasuTextMuted, fontSize = 12.sp) }
            }
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
    icon: ImageVector,
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
    icon: ImageVector,
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
            verticalAlignment = Alignment.CenterVertically
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
