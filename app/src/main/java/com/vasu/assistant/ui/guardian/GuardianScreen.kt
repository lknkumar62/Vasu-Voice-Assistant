package com.vasu.assistant.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.security.GuardianState
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: GuardianViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEnrollmentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Guardian",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Guardian Status
            item {
                GuardianStatusCard(
                    isEnabled = uiState.isGuardianEnabled,
                    state = uiState.guardianState,
                    currentSpeaker = uiState.currentSpeaker?.name,
                    onToggle = viewModel::toggleGuardian
                )
            }

            // Enrolled Voices
            item {
                Text(
                    text = "Enrolled Voices",
                    color = VasuCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.enrolledVoices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No voices enrolled yet.\nEnroll your voice to enable Guardian.",
                            modifier = Modifier.padding(20.dp),
                            color = VasuTextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                items(uiState.enrolledVoices) { voice ->
                    VoiceCard(
                        name = voice.name,
                        role = voice.role,
                        verificationCount = voice.verificationCount,
                        onRemove = { viewModel.removeVoice(voice.id) }
                    )
                }
            }

            // Enroll Button
            item {
                Button(
                    onClick = { showEnrollmentDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VasuCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enroll New Voice",
                        color = VasuDarkBg,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Message
            if (uiState.message.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = VasuGreen.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = uiState.message,
                            modifier = Modifier.padding(16.dp),
                            color = VasuGreen
                        )
                    }
                }
            }
        }
    }

    // Enrollment Dialog
    if (showEnrollmentDialog) {
        EnrollmentDialog(
            name = uiState.enrollmentName,
            role = uiState.enrollmentRole,
            isEnrolling = uiState.isEnrolling,
            enrollmentState = uiState.enrollmentState,
            onNameChange = viewModel::updateEnrollmentName,
            onRoleChange = viewModel::updateEnrollmentRole,
            onStartEnrollment = viewModel::startEnrollment,
            onCompleteEnrollment = viewModel::completeEnrollment,
            onDismiss = {
                showEnrollmentDialog = false
                viewModel.cancelEnrollment()
            }
        )
    }
}

@Composable
fun GuardianStatusCard(
    isEnabled: Boolean,
    state: GuardianState,
    currentSpeaker: String?,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) VasuGreen.copy(alpha = 0.1f) else VasuDarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Voice Guardian",
                        color = VasuTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled) "Active" else "Disabled",
                        color = if (isEnabled) VasuGreen else VasuTextMuted,
                        fontSize = 14.sp
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VasuDarkBg,
                        checkedTrackColor = VasuGreen,
                        uncheckedThumbColor = VasuTextMuted,
                        uncheckedTrackColor = VasuDarkSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Speaker
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VasuDarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = VasuTextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Current Speaker",
                        color = VasuTextMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = currentSpeaker ?: "No speaker verified",
                        color = VasuTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceCard(
    name: String,
    role: UserRole,
    verificationCount: Int,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VasuDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().toString(),
                    color = VasuCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = VasuTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = role.displayName,
                    color = when (role) {
                        UserRole.BOSS -> VasuCyan
                        UserRole.FAMILY -> VasuGreen
                        UserRole.FRIEND -> VasuPurple
                        UserRole.GUEST -> VasuTextMuted
                        else -> VasuTextMuted
                    },
                    fontSize = 12.sp
                )
                Text(
                    text = "Verified $verificationCount times",
                    color = VasuTextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = VasuError
                )
            }
        }
    }
}

@Composable
fun EnrollmentDialog(
    name: String,
    role: UserRole,
    isEnrolling: Boolean,
    enrollmentState: com.vasu.assistant.core.security.EnrollmentState,
    onNameChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onStartEnrollment: () -> Unit,
    onCompleteEnrollment: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VasuDarkCard,
        title = {
            Text(
                text = "Enroll Voice",
                color = VasuTextPrimary
            )
        },
        text = {
            Column {
                if (!isEnrolling) {
                    // Name input
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text("Name", color = VasuTextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VasuTextPrimary,
                            unfocusedTextColor = VasuTextPrimary,
                            focusedBorderColor = VasuCyan,
                            unfocusedBorderColor = VasuTextMuted
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Role selection
                    Text(
                        text = "Select Role:",
                        color = VasuTextMuted,
                        fontSize = 12.sp
                    )

                    UserRole.entries.filter { it != UserRole.UNKNOWN && it != UserRole.BLOCKED }.forEach { roleOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = role == roleOption,
                                onClick = { onRoleChange(roleOption) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VasuCyan,
                                    unselectedColor = VasuTextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${roleOption.displayName} - ${
                                    when (roleOption) {
                                        UserRole.BOSS -> "Full access"
                                        UserRole.FAMILY -> "Normal use"
                                        UserRole.FRIEND -> "Info only"
                                        UserRole.GUEST -> "Chat only"
                                        else -> ""
                                    }
                                }",
                                color = VasuTextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Enrollment in progress
                    when (enrollmentState) {
                        is com.vasu.assistant.core.security.EnrollmentState.Recording -> {
                            Text(
                                text = "Recording voice samples...",
                                color = VasuCyan
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = VasuCyan
                            )
                        }
                        is com.vasu.assistant.core.security.EnrollmentState.SampleRecorded -> {
                            Text(
                                text = "Sample ${enrollmentState.sampleCount}/3 recorded",
                                color = VasuGreen
                            )
                        }
                        is com.vasu.assistant.core.security.EnrollmentState.Processing -> {
                            Text(
                                text = "Processing voice...",
                                color = VasuWarning
                            )
                        }
                        else -> {
                            Text(
                                text = "Speak clearly for 3 short phrases",
                                color = VasuTextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isEnrolling) {
                Button(
                    onClick = onStartEnrollment,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan)
                ) {
                    Text("Start Recording", color = VasuDarkBg)
                }
            } else {
                Button(
                    onClick = onCompleteEnrollment,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuGreen),
                    enabled = enrollmentState is com.vasu.assistant.core.security.EnrollmentState.SampleRecorded ||
                            enrollmentState is com.vasu.assistant.core.security.EnrollmentState.Processing
                ) {
                    Text("Complete", color = VasuDarkBg)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VasuTextSecondary)
            }
        }
    )
}
