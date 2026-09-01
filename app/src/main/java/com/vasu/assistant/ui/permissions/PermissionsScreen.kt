package com.vasu.assistant.ui.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vasu.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Auto refresh permissions whenever user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Permissions Center",
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
        ) {
            // Summary Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Permission Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = VasuTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "Granted: ${uiState.grantedCount} / ${uiState.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.grantedCount == uiState.totalCount) VasuGreen else VasuCyan
                    )

                    val progress = if (uiState.totalCount > 0) uiState.grantedCount.toFloat() / uiState.totalCount else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .padding(top = 10.dp),
                        color = if (progress >= 1f) VasuGreen else VasuCyan,
                        trackColor = VasuDarkSurface
                    )
                }
            }

            // Permissions List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.items) { item ->
                    PermissionRowCard(
                        item = item,
                        onClick = { viewModel.openPermissionSettings(context, item) }
                    )
                }
            }

            // Refresh Button
            Button(
                onClick = { viewModel.refreshPermissions(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VasuCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp),
                    tint = VasuDarkBg
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh All Permissions", color = VasuDarkBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionRowCard(
    item: PermissionStatusItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isGranted) VasuGreen.copy(alpha = 0.12f) else VasuDarkCard
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (item.isGranted) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when (item.id) {
                    "mic" -> Icons.Default.Mic
                    "overlay" -> Icons.Default.Layers
                    "accessibility" -> Icons.Default.SettingsAccessibility
                    "notif_listener", "post_notif" -> Icons.Default.Notifications
                    "call" -> Icons.Default.Phone
                    "contacts" -> Icons.Default.Contacts
                    "sms" -> Icons.Default.Message
                    "cam" -> Icons.Default.CameraAlt
                    "loc" -> Icons.Default.LocationOn
                    "battery" -> Icons.Default.BatteryChargingFull
                    else -> Icons.Default.Security
                },
                contentDescription = item.title,
                modifier = Modifier.size(28.dp),
                tint = if (item.isGranted) VasuGreen else VasuCyan
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VasuTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VasuTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Action / Status Badge
            if (item.isGranted) {
                Surface(
                    color = VasuGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "✓ Enabled",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = VasuGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = VasuCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Grant", color = VasuDarkBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
