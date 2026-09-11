package com.vasu.assistant.ui.missions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MissionsViewModel = hiltViewModel()
) {
    val missions by viewModel.missions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missions", fontWeight = FontWeight.Bold, color = VasuCyan) },
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
        if (missions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No missions yet", color = VasuTextMuted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(missions) { mission ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(mission.name, color = VasuTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("${mission.steps.size} steps", color = VasuTextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
