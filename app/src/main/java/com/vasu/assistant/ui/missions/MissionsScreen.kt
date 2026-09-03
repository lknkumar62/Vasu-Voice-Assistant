package com.vasu.assistant.ui.missions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MissionsViewModel = hiltViewModel()
) {
    val missions by viewModel.missions.collectAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Missions & Macros",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
            )
        },
        containerColor = VasuDarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createMission("Routine #${missions.size + 1}") },
                containerColor = VasuCyan
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Mission", tint = VasuDarkBg)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (missions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = "Empty",
                            modifier = Modifier.size(64.dp),
                            tint = VasuTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No missions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = VasuTextPrimary
                        )
                        Text(
                            "Create your first automated mission",
                            style = MaterialTheme.typography.bodySmall,
                            color = VasuTextMuted
                        )
                    }
                }
            } else {
                items(missions) { mission ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Mission",
                                    tint = VasuGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        mission.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VasuTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${mission.steps.size} steps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VasuTextMuted
                                    )
                                }
                                Button(
                                    onClick = { viewModel.executeMission(mission) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan),
                                    modifier = Modifier.size(40.dp, 36.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Execute",
                                        tint = VasuDarkBg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
