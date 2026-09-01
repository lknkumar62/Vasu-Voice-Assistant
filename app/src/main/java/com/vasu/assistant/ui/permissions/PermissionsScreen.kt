package com.vasu.assistant.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*

data class PermissionItem(
    val name: String,
    val permission: String,
    val description: String,
    val icon: ImageVector,
    val settingsAction: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val permissions = listOf(
        // Core Voice
        PermissionItem("Microphone", Manifest.permission.RECORD_AUDIO, "Required for voice commands and wake word detection", Icons.Default.Mic),
        PermissionItem("Post Notifications", Manifest.permission.POST_NOTIFICATIONS, "Show VASU notifications and alerts", Icons.Default.Notifications),

        // Communication
        PermissionItem("Contacts", Manifest.permission.READ_CONTACTS, "Find contacts for calls and messages", Icons.Default.Contacts),
        PermissionItem("Make Calls", Manifest.permission.CALL_PHONE, "Dial phone numbers from voice commands", Icons.Default.Phone),
        PermissionItem("Send SMS", Manifest.permission.SEND_SMS, "Send text messages from voice", Icons.Default.Message),
        PermissionItem("Read Phone State", Manifest.permission.READ_PHONE_STATE, "Detect incoming calls", Icons.Default.PhoneInTalk),

        // Media & Storage
        PermissionItem("Camera", Manifest.permission.CAMERA, "Take photos and OCR scanning", Icons.Default.CameraAlt),
        PermissionItem("Photo Library", Manifest.permission.READ_MEDIA_IMAGES, "Access photos and gallery", Icons.Default.ImageSearch),
        PermissionItem("Videos", Manifest.permission.READ_MEDIA_VIDEO, "Access video files", Icons.Default.VideoLibrary),
        PermissionItem("Audio Files", Manifest.permission.READ_MEDIA_AUDIO, "Access audio and music", Icons.Default.AudioFile),

        // Location
        PermissionItem("Location (Precise)", Manifest.permission.ACCESS_FINE_LOCATION, "GPS for maps and navigation", Icons.Default.LocationOn),
        PermissionItem("Location (Approximate)", Manifest.permission.ACCESS_COARSE_LOCATION, "Cell-based location", Icons.Default.LocationSearching),

        // System
        PermissionItem("Bluetooth", Manifest.permission.BLUETOOTH, "Connect to Bluetooth devices", Icons.Default.BluetoothAudio),
        PermissionItem("Bluetooth Connect", Manifest.permission.BLUETOOTH_CONNECT, "Connect to paired devices", Icons.Default.Bluetooth),
        PermissionItem("Schedule Alarms", Manifest.permission.SCHEDULE_EXACT_ALARM, "Set exact alarm times", Icons.Default.Alarm),
        PermissionItem("System Alert Window", Manifest.permission.SYSTEM_ALERT_WINDOW, "Display overlay windows", Icons.Default.LayersOutlined),
        PermissionItem("Query Packages", Manifest.permission.QUERY_ALL_PACKAGES, "Find and launch apps", Icons.Default.Apps),
    )

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
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Permission Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = VasuTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val grantedCount = permissions.count { perm ->
                        ContextCompat.checkSelfPermission(context, perm.permission) == PackageManager.PERMISSION_GRANTED
                    }

                    Text(
                        "Granted: $grantedCount / ${permissions.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VasuCyan
                    )

                    LinearProgressIndicator(
                        progress = { grantedCount.toFloat() / permissions.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(top = 8.dp),
                        color = VasuGreen,
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(permissions) { item ->
                    PermissionItemCard(
                        item = item,
                        context = context,
                        isGranted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED,
                        onRefresh = { viewModel.refreshPermissions(context) }
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
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp),
                    tint = VasuDarkBg
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Status", color = VasuDarkBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    item: PermissionItem,
    context: Context,
    isGranted: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isGranted) {
                    // Open system settings for this permission
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) VasuGreen.copy(alpha = 0.1f) else VasuError.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isGranted) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                modifier = Modifier.size(28.dp),
                tint = if (isGranted) VasuGreen else VasuError
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VasuTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VasuTextMuted,
                    maxLines = 2
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .background(
                        color = if (isGranted) VasuGreen else VasuError,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGranted) "✓ Granted" else "✗ Denied",
                    style = MaterialTheme.typography.labelSmall,
                    color = VasuDarkBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // Action Button
            if (!isGranted) {
                IconButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = VasuCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
