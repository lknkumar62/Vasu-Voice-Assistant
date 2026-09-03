package com.vasu.assistant.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.core.ai.ToolDefinition
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit = {},
    onToolSelect: (String) -> Unit = {},
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedToolForDetails by remember { mutableStateOf<ToolDefinition?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Registered AI Tools (${uiState.totalCount})",
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
                placeholder = { Text("Search 39+ tools...", color = VasuTextMuted) },
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

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolsViewModel.CATEGORIES.forEach { (catKey, catLabel) ->
                    val isSelected = uiState.selectedCategory == catKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(catKey) },
                        label = { Text(catLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VasuCyan,
                            selectedLabelColor = VasuDarkBg,
                            containerColor = VasuDarkCard,
                            labelColor = VasuTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) VasuCyan else VasuDarkSurface,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Tools List
            if (uiState.filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tools found matching '${uiState.searchQuery}'",
                        color = VasuTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.filteredTools, key = { it.name }) { tool ->
                        ToolCardItem(
                            tool = tool,
                            onTest = { viewModel.executeTest(tool.name) },
                            onClick = { selectedToolForDetails = tool }
                        )
                    }
                }
            }
        }
    }

    // Test Result Dialog
    uiState.testResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearTestResult,
            containerColor = VasuDarkCard,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.success) VasuGreen else VasuError
                    )
                    Text(
                        text = if (result.success) "Test Passed" else "Test Notice",
                        color = if (result.success) VasuGreen else VasuError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Action: ${result.action}",
                        color = VasuCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = result.message,
                        color = VasuTextPrimary
                    )
                    if (result.error != null) {
                        Text(
                            text = "Error detail: ${result.error}",
                            color = VasuError,
                            fontSize = 12.sp
                        )
                    }
                    if (result.data != null && result.data.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Result Data:",
                            color = VasuTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VasuDarkSurface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = result.data.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                                modifier = Modifier.padding(8.dp),
                                color = VasuTextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::clearTestResult,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan)
                ) {
                    Text("Close", color = VasuDarkBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Tool Detail Dialog
    selectedToolForDetails?.let { tool ->
        AlertDialog(
            onDismissRequest = { selectedToolForDetails = null },
            containerColor = VasuDarkCard,
            title = {
                Text(
                    text = tool.name,
                    color = VasuCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = tool.description, color = VasuTextPrimary)

                    HorizontalDivider(color = VasuDarkSurface)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Risk Level:", color = VasuTextSecondary, fontSize = 13.sp)
                        RiskBadge(riskLevel = tool.riskLevel)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Required Role:", color = VasuTextSecondary, fontSize = 13.sp)
                        Text(
                            text = tool.requiredRole.displayName,
                            color = VasuTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (tool.parameters.isNotEmpty()) {
                        Text(
                            text = "Parameters (${tool.parameters.size}):",
                            color = VasuTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        tool.parameters.forEach { param ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = VasuDarkSurface),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = param.name + if (param.required) " *" else "",
                                            color = VasuCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = param.type,
                                            color = VasuTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = param.description,
                                        color = VasuTextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = tool.name
                        selectedToolForDetails = null
                        viewModel.executeTest(name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan)
                ) {
                    Text("Test Tool", color = VasuDarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedToolForDetails = null }) {
                    Text("Close", color = VasuTextSecondary)
                }
            }
        )
    }
}

@Composable
fun ToolCardItem(
    tool: ToolDefinition,
    onTest: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = getToolIcon(tool.name),
                        contentDescription = null,
                        tint = VasuCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = VasuCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                RiskBadge(riskLevel = tool.riskLevel)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = VasuTextPrimary,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tool.parameters.size} params • Role: ${tool.requiredRole.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VasuTextMuted,
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VasuTextSecondary)
                    ) {
                        Text("Info", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onTest,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VasuCyan,
                            contentColor = VasuDarkBg
                        )
                    ) {
                        Text("TEST", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RiskBadge(riskLevel: RiskLevel) {
    val (bgColor, textColor, label) = when (riskLevel) {
        RiskLevel.LOW -> Triple(VasuGreen.copy(alpha = 0.15f), VasuGreen, "LOW")
        RiskLevel.MEDIUM -> Triple(VasuWarning.copy(alpha = 0.15f), VasuWarning, "MED")
        RiskLevel.HIGH -> Triple(VasuError.copy(alpha = 0.15f), VasuError, "HIGH")
        RiskLevel.CRITICAL -> Triple(VasuError.copy(alpha = 0.25f), VasuError, "CRIT")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getToolIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        name.contains("torch") -> Icons.Default.FlashlightOn
        name.contains("volume") -> Icons.Default.VolumeUp
        name.contains("battery") -> Icons.Default.BatteryFull
        name.contains("alarm") -> Icons.Default.Alarm
        name.contains("timer") -> Icons.Default.Timer
        name.contains("wifi") -> Icons.Default.Wifi
        name.contains("call") -> Icons.Default.Phone
        name.contains("sms") || name.contains("whatsapp") -> Icons.Default.Message
        name.contains("file") || name.contains("browse") || name.contains("storage") -> Icons.Default.Folder
        name.contains("photo") || name.contains("camera") -> Icons.Default.CameraAlt
        name.contains("video") || name.contains("record") -> Icons.Default.Videocam
        name.contains("location") || name.contains("parking") || name.contains("traffic") -> Icons.Default.LocationOn
        name.contains("screen") || name.contains("click") || name.contains("type") -> Icons.Default.TouchApp
        name.contains("notification") -> Icons.Default.Notifications
        name.contains("mode") -> Icons.Default.Tune
        name.contains("weather") -> Icons.Default.Cloud
        name.contains("search") -> Icons.Default.Search
        name.contains("calculate") || name.contains("convert") -> Icons.Default.Calculate
        else -> Icons.Default.Extension
    }
}

