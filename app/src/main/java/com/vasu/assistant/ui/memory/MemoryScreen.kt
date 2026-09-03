package com.vasu.assistant.ui.memory

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MemoryItem?>(false as? MemoryItem) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VASU Memory (${uiState.totalCount})",
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
                actions = {
                    if (uiState.memories.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = VasuError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = VasuCyan,
                contentColor = VasuDarkBg
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        },
        containerColor = VasuDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search memories...", color = VasuTextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = VasuCyan)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = VasuTextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VasuTextPrimary,
                    unfocusedTextColor = VasuTextPrimary,
                    focusedBorderColor = VasuCyan,
                    unfocusedBorderColor = VasuDarkCard,
                    focusedContainerColor = VasuDarkSurface,
                    unfocusedContainerColor = VasuDarkSurface
                )
            )

            if (uiState.filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = VasuCyan
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "No matching memories found" else "No memories stored yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = VasuTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "VASU automatically remembers important facts from conversations, or you can add them manually with + button.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VasuTextMuted
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(uiState.filteredMemories, key = { it.id }) { memory ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = VasuCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = memory.key,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = VasuCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = { editingItem = memory },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = VasuTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteMemory(memory.key) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = VasuError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = memory.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VasuTextPrimary
                                )

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Source: ${memory.source}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VasuTextMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Confidence: ${(memory.confidence * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VasuGreen,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var newKey by remember { mutableStateOf("") }
        var newValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = VasuDarkCard,
            title = { Text("Add Memory", color = VasuTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Key / Subject (e.g. user_name)", color = VasuTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VasuTextPrimary,
                            unfocusedTextColor = VasuTextPrimary,
                            focusedBorderColor = VasuCyan,
                            unfocusedBorderColor = VasuTextMuted
                        )
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Value / Fact (e.g. Rahul Sharma)", color = VasuTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VasuTextPrimary,
                            unfocusedTextColor = VasuTextPrimary,
                            focusedBorderColor = VasuCyan,
                            unfocusedBorderColor = VasuTextMuted
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKey.isNotBlank() && newValue.isNotBlank()) {
                            viewModel.addMemory(newKey, newValue)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan)
                ) {
                    Text("Save", color = VasuDarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = VasuTextSecondary)
                }
            }
        )
    }

    // Edit Memory Dialog
    editingItem?.let { item ->
        var editValue by remember(item) { mutableStateOf(item.value) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            containerColor = VasuDarkCard,
            title = { Text("Edit Memory: ${item.key}", color = VasuTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text("Value", color = VasuTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VasuTextPrimary,
                        unfocusedTextColor = VasuTextPrimary,
                        focusedBorderColor = VasuCyan,
                        unfocusedBorderColor = VasuTextMuted
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editValue.isNotBlank()) {
                            viewModel.updateMemory(item.key, editValue)
                            editingItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan)
                ) {
                    Text("Update", color = VasuDarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = VasuTextSecondary)
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            containerColor = VasuDarkCard,
            title = { Text("Clear All Memories?", color = VasuError, fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete all remembered preferences and facts. This action cannot be undone.", color = VasuTextPrimary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMemory()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VasuError)
                ) {
                    Text("Delete All", color = VasuTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = VasuTextSecondary)
                }
            }
        )
    }
}
