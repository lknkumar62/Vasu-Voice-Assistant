package com.vasu.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasu.assistant.core.network.NetworkState
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.tts.VoiceGender
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.ui.components.VasuCard
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

    // Hoisted UI state for the ADVANCED section. Initialized from persisted
    // settings so field values survive a recomposition; explicit types keep the
    // delegate inference unambiguous at the top of the composable body.
    var chatServerInput by remember { mutableStateOf<String>(state.chatServerUrl) }
    var googleClientId by remember { mutableStateOf<String>(state.googleClientId) }
    var githubClientId by remember { mutableStateOf<String>(state.githubClientId) }
    var discordClientId by remember { mutableStateOf<String>(state.discordClientId) }

    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = VasuCyan, fontSize = 18.sp) },
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
            Spacer(modifier = Modifier.height(12.dp))

            // --- AI ENGINE SECTION ---
            SettingsSection(title = "AI ENGINE") {
                SettingsToggleItem(
                    icon = Icons.Default.SmartToy,
                    title = "Enable Gemini",
                    subtitle = if (state.geminiEnabled) "Cloud reasoning active" else "Offline mode only",
                    enabled = state.geminiEnabled,
                    onToggle = viewModel::setGeminiEnabled
                )

                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("API Key", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (state.hasKey) {
                            Text("Verified", color = VasuSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter Gemini API Key", color = VasuTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = VasuTextSecondary)
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

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveKey(keyInput)
                                keyInput = ""
                            },
                            enabled = keyInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Save Key", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = viewModel::testConnection,
                            enabled = state.hasKey && state.connectionTest != ConnectionTest.TESTING,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Text(
                                if (state.connectionTest == ConnectionTest.TESTING) "Testing..." else "Test connection",
                                color = VasuCyan,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Model selection", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = viewModel::refreshModels) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", color = VasuCyan, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { modelMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuTextPrimary)
                        ) {
                            Text(state.model, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VasuTextSecondary)
                        }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false }
                            ) {
                                for (model in state.availableModels) {
                                    DropdownMenuItem(
                                        text = { Text(model, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                                        onClick = {
                                            viewModel.setModel(model)
                                            modelMenuOpen = false
                                        }
                                    )
                                }
                            }

                    }
                }

                SettingsToggleItem(
                    icon = Icons.Default.SwapHoriz,
                    title = "Model Fallback",
                    subtitle = "Use alternative model if primary is unavailable",
                    enabled = state.allowModelFallback,
                    onToggle = viewModel::setAllowModelFallback
                )

                // Network & Connection Statuses
                StatusRow(
                    icon = if (state.network == NetworkState.ONLINE) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = if (state.network == NetworkState.ONLINE) "Network Online" else "Network Offline",
                    detail = if (state.cloudUsable) "Gemini Cloud access available" else "Cloud AI unavailable; using local engine",
                    color = if (state.network == NetworkState.ONLINE) VasuSuccess else VasuWarning
                )
                StatusRow(
                    icon = when (state.connectionTest) {
                        ConnectionTest.PASSED -> Icons.Default.CheckCircle
                        ConnectionTest.FAILED -> Icons.Default.Error
                        else -> Icons.Default.HelpOutline
                    },
                    label = when (state.connectionTest) {
                        ConnectionTest.NOT_TESTED -> "Connection Not Tested"
                        ConnectionTest.TESTING -> "Testing Connection..."
                        ConnectionTest.PASSED -> "Connection Verified"
                        ConnectionTest.FAILED -> "Connection Failed"
                        else -> "Unknown Connection State"
                    },
                    detail = state.connectionMessage.ifBlank { null },
                    color = when (state.connectionTest) {
                        ConnectionTest.PASSED -> VasuSuccess
                        ConnectionTest.FAILED -> VasuError
                        else -> VasuTextSecondary
                    }
                )
            }

            // --- VOICE & SOUND SECTION ---
            SettingsSection(title = "VOICE & SOUND") {
                SettingsCard {
                    Text("Language", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { languageMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuTextPrimary)
                        ) {
                            Text(
                                VasuSettings.LANGUAGES.firstOrNull { it.first == state.voiceProfile.language }?.second ?: state.voiceProfile.language,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.weight(1f))
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
                }

                SliderCard(
                    label = "Speech Rate",
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

                VasuVoiceSelector(
                    selectedVoice = state.geminiTtsVoice,
                    onVoiceSelected = viewModel::setGeminiTtsVoice
                )

                SettingsCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = viewModel::testVoice,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test Voice", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = viewModel::openTtsSettings,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("TTS Settings", color = VasuCyan, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // --- SECURITY & AUTOMATION SECTION ---
            SettingsSection(title = "SECURITY & AUTOMATION") {
                SettingsToggleItem(
                    icon = Icons.Default.Bolt,
                    title = "Auto-Allow Actions",
                    subtitle = if (state.autoAllowEnabled) "Autonomous execution enabled" else "Requires confirmation",
                    enabled = state.autoAllowEnabled,
                    onToggle = viewModel::setAutoAllowEnabled
                )
                SettingsToggleItem(
                    icon = Icons.Default.Security,
                    title = "Voice Guard",
                    subtitle = "Require speaker verification for risky tools",
                    enabled = state.voiceGuardEnabled,
                    onToggle = viewModel::setVoiceGuardEnabled
                )
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "\"Hello Vasu\"",
                    subtitle = "Background listening active",
                    enabled = state.wakeWordEnabled,
                    onToggle = viewModel::setWakeWordEnabled
                )

                StatusRow(
                    icon = when (state.wakeWordState) {
                        WakeWordState.LISTENING, WakeWordState.DETECTED -> Icons.Default.CheckCircle
                        WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> Icons.Default.Error
                        WakeWordState.IDLE -> Icons.Default.HelpOutline
                        else -> Icons.Default.HelpOutline
                    },
                    label = when (state.wakeWordState) {
                        WakeWordState.IDLE -> "Wake Word: Idle"
                        WakeWordState.LISTENING -> "Wake Word: Listening"
                        WakeWordState.DETECTED -> "Wake Word: Detected"
                        WakeWordState.MODEL_NOT_AVAILABLE -> "Wake Word: Unavailable"
                        WakeWordState.ERROR -> "Wake Word: Error"
                        else -> "Wake Word: Unknown"
                    },
                    detail = state.wakeWordReason,
                    color = when (state.wakeWordState) {
                        WakeWordState.LISTENING, WakeWordState.DETECTED -> VasuSuccess
                        WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> VasuError
                        WakeWordState.IDLE -> VasuTextSecondary
                        else -> VasuTextSecondary
                    }
                )
            }

            // --- SYSTEM ACCESS SECTION ---
            SettingsSection(title = "SYSTEM ACCESS") {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Screen Control",
                    subtitle = "Accessibility service permissions",
                    onClick = viewModel::openAccessibilitySettings
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Access",
                    subtitle = "Read and act on incoming notifications",
                    onClick = viewModel::openNotificationAccessSettings
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "App Permissions",
                    subtitle = "Manage microphone, contacts and storage",
                    onClick = viewModel::openAppSettings
                )
            }

            // --- ADVANCED SECTION ---
            SettingsSection(title = "ADVANCED") {
                var chatKeyInput by remember { mutableStateOf("") }
                var showChatKey by remember { mutableStateOf(false) }

                SettingsCard {
                    Text("WebSocket Server URL", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatServerInput,
                        onValueChange = { chatServerInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("wss://chat.vasu.app/ws", color = VasuTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Chat API Key", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatKeyInput,
                        onValueChange = { chatKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional API Key", color = VasuTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showChatKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showChatKey = !showChatKey }) {
                                Icon(if (showChatKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = VasuTextSecondary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.saveChatConfig(chatServerInput, chatKeyInput) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Save Chat Config", fontWeight = FontWeight.Bold) }
                }

                var githubClientSecret by remember { mutableStateOf("") }
                var showGithubSecret by remember { mutableStateOf(false) }

                SettingsCard {
                    Text("OAuth Connectors", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    
                    ConnectorField("Google Client ID", googleClientId, { googleClientId = it }, "xxx.apps.googleusercontent.com")
                    Spacer(Modifier.height(12.dp))
                    ConnectorField("GitHub Client ID", githubClientId, { githubClientId = it }, "Iv1.xxxxxxxx")
                    Spacer(Modifier.height(12.dp))
                    ConnectorField(
                        label = "GitHub Client Secret", 
                        value = githubClientSecret, 
                        onValueChange = { githubClientSecret = it }, 
                        placeholder = "xxxxxxxxxxxxxxxx",
                        isPassword = true,
                        showPassword = showGithubSecret,
                        onTogglePassword = { showGithubSecret = !showGithubSecret }
                    )
                    Spacer(Modifier.height(12.dp))
                    ConnectorField("Discord Client ID", discordClientId, { discordClientId = it }, "1234567890")
                    
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.saveOAuthConfig(googleClientId, githubClientId, githubClientSecret, discordClientId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Save Connector Config", fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConnectorField(
    label: String, 
    value: String, 
    onValueChange: (String) -> Unit, 
    placeholder: String, 
    isPassword: Boolean = false, 
    showPassword: Boolean = false, 
    onTogglePassword: () -> Unit = {}
) {
    Column {
        Text(label, color = VasuTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = VasuTextMuted) },
            singleLine = true,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = onTogglePassword) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = VasuTextSecondary)
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
            )
        )
    }
}

@Composable
private fun VasuVoiceSelector(
    selectedVoice: String,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val voiceTraits = remember {
        mapOf(
            "Aoede" to "Breezy", "Autonoe" to "Bright", "Callirrhoe" to "Easy-going", "Despina" to "Smooth",
            "Erinome" to "Clear", "Gacrux" to "Mature", "Kore" to "Firm", "Laomedeia" to "Upbeat",
            "Leda" to "Youthful", "Pulcherrima" to "Forward", "Sulafat" to "Warm", "Vindemiatrix" to "Gentle", "Zephyr" to "Bright",
            "Achernar" to "Deep", "Achird" to "Brisk", "Algenib" to "Gravelly", "Algieba" to "Smooth", "Alnilam" to "Firm",
            "Charon" to "Informative", "Enceladus" to "Breathy", "Fenrir" to "Excitable", "Iapetus" to "Clear", "Orus" to "Firm",
            "Puck" to "Upbeat", "Rasalgethi" to "Gravelly", "Sadachbia" to "Even", "Sadaltager" to "Knowledgeable", "Schedar" to "Even-tempered", "Umbriel" to "Easy-going", "Zubenelgenubi" to "Casual"
        )
    }

    val selectedSuffix = selectedVoice.ifBlank { "Kore" }
    val selectedFull = VasuSettings.MAYA_VOICES.firstOrNull { it.substringAfter("_") == selectedSuffix } ?: "maya_Kore"
    val currentTrait = voiceTraits[selectedSuffix] ?: "Studio"
    val currentPersona = if (selectedFull.startsWith("friday")) "FRIDAY" else if (selectedFull.startsWith("venom")) "VENOM" else "MAYA"

    SettingsCard {
        // Header Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VasuCyan.copy(alpha = 0.08f))
                .border(1.dp, VasuCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(VasuCyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = VasuDarkBg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$currentPersona - $selectedSuffix", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Trait: $currentTrait", color = VasuTextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("43", color = VasuCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuTextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Change Vasu Voice", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VasuTextSecondary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(VasuDarkElevated, RoundedCornerShape(16.dp))
                    .border(1.dp, VasuCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    VoiceGroup("FRIDAY - SWEET", VasuCyan, VasuSettings.MAYA_VOICES.filter { it.startsWith("friday") }, selectedSuffix, voiceTraits, onVoiceSelected)
                    HorizontalDivider(color = VasuCyan.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                    VoiceGroup("WARM - MAYA", VasuSuccess, VasuSettings.MAYA_VOICES.filter { it.startsWith("maya") }, selectedSuffix, voiceTraits, onVoiceSelected)
                    HorizontalDivider(color = VasuCyan.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                    VoiceGroup("VENOM - DEEP", VasuPurple, VasuSettings.MAYA_VOICES.filter { it.startsWith("venom") }, selectedSuffix, voiceTraits, onVoiceSelected)
                }
            }
        }
    }
}

@Composable
private fun VoiceGroup(
    title: String,
    color: Color,
    voices: List<String>,
    selectedSuffix: String,
    traits: Map<String, String>,
    onVoiceSelected: (String) -> Unit
) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        voices.forEach { voice ->
            val suffix = voice.substringAfter("_")
            val isSelected = suffix == selectedSuffix
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isSelected) color else color.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(suffix, color = if (isSelected) VasuCyan else VasuTextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            Text(traits[suffix] ?: "Studio", color = VasuTextSecondary, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    onVoiceSelected(suffix)
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            )
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                String.format("%.2f", value),
                color = VasuCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VasuCyan,
                activeTrackColor = VasuCyan,
                inactiveTrackColor = VasuTextMuted.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    VasuCard {
        Column(content = content)
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    detail: String?,
    color: Color
) {
    VasuCard(
        borderColor = color,
        borderAlpha = 0.12f
    ) {
        Row(
            modifier = Modifier.padding(0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                detail?.let {
                    Text(it, color = VasuTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(VasuCyan))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = VasuCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(VasuCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = VasuTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VasuTextMuted, modifier = Modifier.size(20.dp))
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) VasuCyan.copy(alpha = 0.15f) else VasuTextMuted.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = if (enabled) VasuCyan else VasuTextSecondary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = VasuTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VasuDarkBg,
                    checkedTrackColor = VasuCyan,
                    checkedBorderColor = VasuCyan,
                    uncheckedThumbColor = VasuTextMuted,
                    uncheckedTrackColor = VasuDarkElevated,
                    uncheckedBorderColor = VasuTextMuted.copy(alpha = 0.3f)
                )
            )
        }
    }
}
