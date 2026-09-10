package com.vasu.assistant.ui.automation

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
fun AutomationScreen(viewModel: AutomationViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Automation", style = MaterialTheme.typography.headlineMedium)
        Text("Missions, macros and smart modes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        // Smart Modes Section
        Text("Smart Modes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.smartModes) { mode ->
                val isActive = mode == uiState.activeMode
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isActive) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            when (mode) {
                                "DRIVING" -> Icons.Default.DirectionsCar
                                "SLEEP" -> Icons.Default.Bedtime
                                "WORK" -> Icons.Default.Work
                                "GAMING" -> Icons.Default.SportsEsports
                                else -> Icons.Default.PhoneAndroid
                            },
                            contentDescription = mode
                        )
                        Text(mode, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (isActive) {
                            Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = { viewModel.setSmartMode(mode) }) { Text("Activate") }
                        }
                    }
                }
            }
        }
    }
}
