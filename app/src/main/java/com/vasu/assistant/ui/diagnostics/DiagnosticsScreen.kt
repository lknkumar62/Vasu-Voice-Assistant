package com.vasu.assistant.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.Bold, color = VasuCrimson) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VasuTextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = VasuCrimson)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
            )
        },
        containerColor = VasuDarkBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.items.size) { index ->
                val item = uiState.items[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                DiagnosticStatus.OK -> Icons.Default.CheckCircle
                                DiagnosticStatus.WARNING -> Icons.Default.Warning
                                DiagnosticStatus.ERROR -> Icons.Default.Error
                                DiagnosticStatus.INFO -> Icons.Default.Info
                                DiagnosticStatus.UNKNOWN -> Icons.Default.HelpOutline
                            },
                            contentDescription = null,
                            tint = when (item.status) {
                                DiagnosticStatus.OK -> VasuSuccess
                                DiagnosticStatus.WARNING -> VasuWarning
                                DiagnosticStatus.ERROR -> VasuError
                                DiagnosticStatus.INFO -> VasuCrimson
                                DiagnosticStatus.UNKNOWN -> VasuTextSecondary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = VasuTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(item.details, color = VasuTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (com.vasu.assistant.BuildConfig.DEBUG) {
                item {
                    ErrorLogCard(
                        tail = viewModel.errorLogTail.collectAsState().value,
                        onCopy = { text ->
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                            Toast.makeText(context, "Error log copied", Toast.LENGTH_SHORT).show()
                        },
                        onRefresh = viewModel::refreshErrorLog,
                        onClear = viewModel::clearErrorLog
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorLogCard(
    tail: String,
    onCopy: (String) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Internal error log (debug only — invisible in release)",
                color = VasuTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(
                    tail.ifBlank { "(empty)" },
                    color = VasuTextSecondary,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCopy(tail) }) { Text("Copy", fontSize = 12.sp) }
                OutlinedButton(onClick = onRefresh) { Text("Reload", fontSize = 12.sp) }
                TextButton(onClick = onClear) { Text("Clear", fontSize = 12.sp) }
            }
        }
    }
}
