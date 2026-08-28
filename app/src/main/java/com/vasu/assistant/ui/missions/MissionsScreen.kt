package com.vasu.assistant.ui.missions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(viewModel: MissionsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Missions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        if (uiState.activeMission != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Running: ${uiState.activeMission}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.pauseMission() }) { Text("Pause") }
                        TextButton(onClick = { viewModel.cancelMission() }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (uiState.macros.isNotEmpty()) {
            Text("Macros", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.macros) { macro ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(macro["name"] as? String ?: "", style = MaterialTheme.typography.bodyLarge)
                            Text("Trigger: ${macro["trigger"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Runs: ${macro["runCount"]}", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { viewModel.runMacro(macro["id"] as? String ?: return@items) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                        }
                    }
                }
            }
        }
    }
}
