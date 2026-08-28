package com.vasu.assistant.ui.privacy

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vasu.assistant.accessibility.VasuAccessibilityService
import com.vasu.assistant.notifications.NotificationListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen() {
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var locationGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var notifEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy & Security", style = MaterialTheme.typography.headlineMedium)
        Text("VASU permissions and data controls", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        PermissionItem("Microphone", Icons.Default.Mic, micGranted, "Required for voice commands")
        PermissionItem("Camera", Icons.Default.CameraAlt, cameraGranted, "Required for photo/OCR")
        PermissionItem("Location", Icons.Default.LocationOn, locationGranted, "Required for maps and navigation")
        PermissionItem("Notification Access", Icons.Default.Notifications, notifEnabled, "Required to read notifications")
        PermissionItem("Accessibility Service", Icons.Default.Accessibility, accessibilityEnabled, "Required for automation")

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Text("Data Controls", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VASU stores all data locally on your device.", style = MaterialTheme.typography.bodySmall)
                Text("• Conversations are stored in local Room database", style = MaterialTheme.typography.bodySmall)
                Text("• Speaker embeddings encrypted with Android Keystore", style = MaterialTheme.typography.bodySmall)
                Text("• No data is sent to external servers without your AI provider config", style = MaterialTheme.typography.bodySmall)
                Text("• No OTPs, passwords, or financial data are auto-collected", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Actions", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { openAccessibilitySettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open Accessibility Settings") }

        OutlinedButton(
            onClick = { openNotificationSettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open Notification Access Settings") }
    }
}

@Composable
private fun PermissionItem(name: String, icon: ImageVector, granted: Boolean, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = name, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (granted) "✅ Granted" else "❌ Denied",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun hasPermission(context: Context, perm: String): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(ComponentName(context, NotificationListener::class.java).flattenToString())
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return flat.contains(ComponentName(context, VasuAccessibilityService::class.java).flattenToString())
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}
