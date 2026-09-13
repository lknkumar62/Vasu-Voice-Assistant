package com.vasu.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
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

                StatusRow(
                    icon = when (state.customVoiceStatus) {
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_MODEL,
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> Icons.Default.CheckCircle
                        else -> Icons.Default.Info
                    },
                    label = when (state.customVoiceStatus) {
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_MODEL -> "Vasu Voice Model Active"
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> "Vasu Voices Loaded (43)"
                        com.vasu.assistant.core.tts.VoiceModelStatus.FALLBACK_SYSTEM_TTS -> "Vasu Voice Assets"
                        com.vasu.assistant.core.tts.VoiceModelStatus.ERROR -> "Custom Voice Error"
                    },
                    detail = when (state.customVoiceStatus) {
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_MODEL -> "Vasu neural voice active"
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> "43 Vasu voices ready: friday/warm/venom personas"
                        com.vasu.assistant.core.tts.VoiceModelStatus.FALLBACK_SYSTEM_TTS -> "Vasu voices not found — using Gemini Kore"
                        com.vasu.assistant.core.tts.VoiceModelStatus.ERROR -> "Failed loading Vasu voice assets."
                    },
                    color = when (state.customVoiceStatus) {
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_MODEL,
                        com.vasu.assistant.core.tts.VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> VasuSuccess
                        else -> VasuCyan
                    }
                )
            }

            // Vasu Voice Selection — 43 voices (Maya parity, Vasu branding) — Hyper Professional Material3
            SettingsSection(title = "VASU VOICE (43)") {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VasuCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Selected Vasu Voice", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Same engine as Maya — 43 studio voices", color = VasuTextSecondary, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(VasuCyan.copy(alpha = 0.15f)).border(1.dp, VasuCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("43", color = VasuCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    var vasuVoiceExpanded by remember { mutableStateOf(false) }
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
                    val selectedSuffix = state.geminiTtsVoice.ifBlank { "Kore" }
                    val selectedFull = VasuSettings.MAYA_VOICES.firstOrNull { it.substringAfter("_") == selectedSuffix } ?: "maya_Kore"
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { vasuVoiceExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.25f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(VasuCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(selectedFull, color = VasuTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                val trait = voiceTraits[selectedSuffix] ?: "Studio"
                                val persona = if (selectedFull.startsWith("friday")) "Friday — sweet" else if (selectedFull.startsWith("venom")) "Venom — deep" else "Warm — warm"
                                Text("$trait • $persona", color = VasuTextMuted, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = vasuVoiceExpanded,
                            onDismissRequest = { vasuVoiceExpanded = false },
                            modifier = Modifier.background(VasuDarkElevated, RoundedCornerShape(16.dp)).border(1.dp, VasuCyan.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
                            ) {
                                DropdownGroupHeader(title = "FRIDAY — SWEET", count = 13, color = VasuCyan)
                                for (v in VasuSettings.MAYA_VOICES.filter { it.startsWith("friday") }) {
                                    val suffix = v.substringAfter("_")
                                    val isSelected = suffix == selectedSuffix
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isSelected) VasuCyan.copy(alpha = 0.20f) else VasuTextMuted.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                                    Text(suffix.take(1), color = if (isSelected) VasuCyan else VasuTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(v, color = if (isSelected) VasuCyan else VasuTextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                    Text(voiceTraits[suffix] ?: "Warm", color = VasuTextMuted, fontSize = 10.sp)
                                                }
                                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = { viewModel.setGeminiTtsVoice(suffix); vasuVoiceExpanded = false },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                HorizontalDivider(color = VasuCyan.copy(alpha = 0.10f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                DropdownGroupHeader(title = "WARM — MAYA (WARM)", count = 13, color = VasuSuccess)
                                for (v in VasuSettings.MAYA_VOICES.filter { it.startsWith("maya") }) {
                                    val suffix = v.substringAfter("_")
                                    val isSelected = suffix == selectedSuffix
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isSelected) VasuSuccess.copy(alpha = 0.20f) else VasuTextMuted.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                                    Text(suffix.take(1), color = if (isSelected) VasuSuccess else VasuTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(v, color = if (isSelected) VasuSuccess else VasuTextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                    Text(voiceTraits[suffix] ?: "Warm", color = VasuTextMuted, fontSize = 10.sp)
                                                }
                                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = VasuSuccess, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = { viewModel.setGeminiTtsVoice(suffix); vasuVoiceExpanded = false },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                HorizontalDivider(color = VasuCyan.copy(alpha = 0.10f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                DropdownGroupHeader(title = "VENOM — DEEP", count = 17, color = VasuPurple)
                                for (v in VasuSettings.MAYA_VOICES.filter { it.startsWith("venom") }) {
                                    val suffix = v.substringAfter("_")
                                    val isSelected = suffix == selectedSuffix
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isSelected) VasuPurple.copy(alpha = 0.20f) else VasuTextMuted.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                                    Text(suffix.take(1), color = if (isSelected) VasuPurple else VasuTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(v, color = if (isSelected) VasuPurple else VasuTextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                    Text(voiceTraits[suffix] ?: "Deep", color = VasuTextMuted, fontSize = 10.sp)
                                                }
                                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = VasuPurple, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = { viewModel.setGeminiTtsVoice(suffix); vasuVoiceExpanded = false },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VasuDarkElevated).border(1.dp, VasuCyan.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(16.dp))
                        Text(
                            "43 VASU voices — Friday (sweet, 13) · Warm (warm, 13 — maya_Kore etc.) · Venom (deep, 17). Same engine as Maya, same voice model names (maya_Kore, friday_Aoede…). Home & Chat logic unchanged — all features identical, only orb differs.",
                            color = VasuTextSecondary, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                }
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

            SettingsSection(title = "SECURITY & AUTOMATION") {
                SettingsToggleItem(
                    icon = Icons.Default.Bolt,
                    title = "Auto-Allow Actions",
                    subtitle = if (state.autoAllowEnabled) "Autonomous execution enabled (no confirmation prompts)" else "Ask confirmation for high-risk actions",
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

            // Live Chat / WebSocket API Key
            SettingsSection(title = "LIVE CHAT") {
                var chatServerInput by remember { mutableStateOf(state.chatServerUrl) }
                var chatKeyInput by remember { mutableStateOf("") }
                var showChatKey by remember { mutableStateOf(false) }

                SettingsCard {
                    Text("WebSocket Server URL", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatServerInput,
                        onValueChange = { chatServerInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("wss://chat.vasu.app/ws", color = VasuTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(8.dp))

                    Text("API Key (optional)", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatKeyInput,
                        onValueChange = { chatKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Server API key (if required)", color = VasuTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showChatKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveChatConfig(chatServerInput, chatKeyInput) },
                        enabled = chatServerInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg)
                    ) { Text("Save") }
                }
            }

            // Advanced / Connectors API Keys (moved from CONNECTORS to match Maya sections)
            SettingsSection(title = "ADVANCED") {
                var googleClientId by remember { mutableStateOf(state.googleClientId) }
                var githubClientId by remember { mutableStateOf(state.githubClientId) }
                var githubClientSecret by remember { mutableStateOf("") }
                var showGithubSecret by remember { mutableStateOf(false) }
                var discordClientId by remember { mutableStateOf(state.discordClientId) }

                SettingsCard {
                    Text("Google Client ID", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = googleClientId,
                        onValueChange = { googleClientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("xxx.apps.googleusercontent.com", color = VasuTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("GitHub Client ID", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = githubClientId,
                        onValueChange = { githubClientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Iv1.xxxxxxxx", color = VasuTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("GitHub Client Secret", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = githubClientSecret,
                        onValueChange = { githubClientSecret = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("xxxxxxxxxxxxxxxx", color = VasuTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showGithubSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        trailingIcon = {
                            IconButton(onClick = { showGithubSecret = !showGithubSecret }) {
                                Icon(if (showGithubSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = VasuTextSecondary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Discord Client ID", color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discordClientId,
                        onValueChange = { discordClientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("1234567890", color = VasuTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan, unfocusedBorderColor = VasuTextMuted,
                            focusedTextColor = VasuTextPrimary, unfocusedTextColor = VasuTextPrimary, cursorColor = VasuCyan
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveOAuthConfig(googleClientId, githubClientId, githubClientSecret, discordClientId) },
                        colors = ButtonDefaults.buttonColors(containerColor = VasuCyan, contentColor = VasuDarkBg)
                    ) { Text("Save") }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DropdownGroupHeader(title: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("$count", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            Text(label, color = VasuTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(VasuCyan.copy(alpha = 0.12f)).border(1.dp, VasuCyan.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(String.format("%.2f", value), color = VasuCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VasuCyan,
                activeTrackColor = VasuCyan,
                inactiveTrackColor = VasuTextMuted.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.12f))
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                Text(label, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                detail?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(it, color = VasuTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
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
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VasuCyan.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = VasuCyan, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VasuTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = VasuTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VasuTextMuted, modifier = Modifier.size(18.dp))
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
