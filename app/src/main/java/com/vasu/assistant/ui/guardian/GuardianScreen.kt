package com.vasu.assistant.ui.guardian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.security.EnrollmentState
import com.vasu.assistant.core.security.EnrolledVoice
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: GuardianViewModel = hiltViewModel()
) {
    val enabled by viewModel.guardianEnabled.collectAsState()
    val voices by viewModel.voices.collectAsState()
    val current by viewModel.currentSpeaker.collectAsState()
    val enrollState by viewModel.enrollmentState.collectAsState()
    val isRecording by viewModel.isRecordingSample.collectAsState()
    val rms by viewModel.rmsLevel.collectAsState()
    val message by viewModel.uiMessage.collectAsState()

    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var localNotice by remember { mutableStateOf<String?>(null) }
    val notice = message ?: localNotice

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) {
            viewModel.beginEnrollment()
        } else {
            localNotice = "Microphone permission is required to enroll voices."
        }
    }

    fun startEnrollmentFlow() {
        localNotice = null
        if (micGranted) {
            viewModel.beginEnrollment()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Guardian", fontWeight = FontWeight.Bold, color = VasuCrimson) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VasuTextSecondary
                        )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            notice?.let {
                Text(
                    text = it,
                    color = VasuError,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // ── Guardian master toggle ──────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Voice Guardian",
                            color = VasuTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Verify the speaker before high-risk actions.",
                            color = VasuTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = current?.let { "Active speaker: ${it.name} (${it.role.displayName})" }
                                ?: "Active speaker: unverified",
                            color = if (current != null) VasuElectric else VasuTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = viewModel::setGuardianEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VasuDarkBg,
                            checkedTrackColor = VasuCrimson,
                            uncheckedThumbColor = VasuTextSecondary,
                            uncheckedTrackColor = VasuDarkElevated
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Enrollment section ──────────────────────────────────
            Text(
                "Voice enrollment",
                color = VasuTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            when (val state = enrollState) {
                is EnrollmentState.Idle -> EnrollCtaCard(onClick = { startEnrollmentFlow() })

                is EnrollmentState.Completed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = VasuSuccess
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "\"${state.voice.name}\" enrolled as ${state.voice.role.displayName}",
                                color = VasuTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { startEnrollmentFlow() }) {
                                Text("Enroll another voice")
                            }
                        }
                    }
                }

                is EnrollmentState.Recording, is EnrollmentState.SampleRecorded -> {
                    val sampleCount = (state as? EnrollmentState.SampleRecorded)?.sampleCount ?: 0
                    RecorderCard(
                        sampleCount = sampleCount,
                        isRecording = isRecording,
                        rms = rms,
                        onStart = { localNotice = null; viewModel.startSample() },
                        onStop = { viewModel.stopAndSubmitSample() },
                        onCancel = { viewModel.cancelEnrollment() }
                    )
                }

                is EnrollmentState.Error -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = VasuError, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            OutlinedButton(onClick = { viewModel.cancelEnrollment() }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(onClick = { startEnrollmentFlow() }) {
                                Text("Try again")
                            }
                        }
                    }
                }

                is EnrollmentState.Processing -> CompleteEnrollmentForm(
                    onSave = { name, role -> viewModel.completeEnrollment(name, role) },
                    onCancel = { viewModel.cancelEnrollment() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Enrolled voices ─────────────────────────────────────
            Text(
                "Enrolled voices (${voices.size})",
                color = VasuTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            if (voices.isEmpty()) {
                Text(
                    "No voices enrolled yet.",
                    color = VasuTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                voices.forEach { voice ->
                    VoiceRow(
                        voice = voice,
                        onRoleChange = { role -> viewModel.updateRole(voice.id, role) },
                        onDelete = { viewModel.removeVoice(voice.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = VasuTextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Enrollments are stored encrypted and survive restarts.",
                    color = VasuTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EnrollCtaCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = VasuCrimson
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Add your voice for biometric verification",
                color = VasuTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onClick) {
                Text("Start enrollment")
            }
        }
    }
}

@Composable
private fun RecorderCard(
    sampleCount: Int,
    isRecording: Boolean,
    rms: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    var elapsedMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedMs = 0L
            while (elapsedMs < 5000L) {
                delay(200)
                elapsedMs += 200
            }
        } else {
            elapsedMs = 0L
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Record 3 short samples",
                color = VasuTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Speak, then pause for half a second at the end of each sample.",
                color = VasuTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (index < sampleCount) VasuSuccess else VasuDarkElevated,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = rms.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isRecording) VasuCrimson else VasuDarkElevated,
                trackColor = VasuDarkElevated
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRecording) {
                    "Recording… ${"%.1f".format(elapsedMs / 1000.0)}s / 5s"
                } else {
                    "Sample $sampleCount / 3"
                },
                color = VasuTextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledIconButton(
                onClick = { if (isRecording) onStop() else onStart() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRecording) VasuError else VasuCrimson
                ),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop sample" else "Record sample",
                    tint = VasuTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onCancel) {
                Text("Cancel enrollment", color = VasuTextMuted)
            }
        }
    }
}

@Composable
private fun CompleteEnrollmentForm(
    onSave: (String, UserRole) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.FAMILY) }
    var roleMenuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Samples captured — name this voice",
                color = VasuTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box {
                OutlinedButton(onClick = { roleMenuOpen = true }) {
                    Text("Role: ${role.displayName}", color = VasuElectric)
                }
                DropdownMenu(
                    expanded = roleMenuOpen,
                    onDismissRequest = { roleMenuOpen = false }
                ) {
                    UserRole.entries
                        .filter { it != UserRole.UNKNOWN }
                        .forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    role = option
                                    roleMenuOpen = false
                                }
                            )
                        }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onSave(name, role) },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save voice")
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: EnrolledVoice,
    onRoleChange: (UserRole) -> Unit,
    onDelete: () -> Unit
) {
    var roleMenuOpen by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    voice.name,
                    color = VasuTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Verified ${voice.verificationCount}× · " +
                        if (voice.lastVerified > 0) {
                            "last ${dateFormat.format(Date(voice.lastVerified))}"
                        } else {
                            "never verified"
                        },
                    color = VasuTextMuted,
                    fontSize = 12.sp
                )
            }

            Box {
                OutlinedButton(
                    onClick = { roleMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(voice.role.displayName, color = VasuElectric, fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = roleMenuOpen,
                    onDismissRequest = { roleMenuOpen = false }
                ) {
                    UserRole.entries
                        .filter { it != UserRole.UNKNOWN }
                        .forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    onRoleChange(option)
                                    roleMenuOpen = false
                                }
                            )
                        }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${voice.name}",
                    tint = VasuError
                )
            }
        }
    }
}
