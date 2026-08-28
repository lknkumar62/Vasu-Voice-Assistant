package com.vasu.assistant.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    val permissions = listOf(
        Triple("Microphone", Manifest.permission.RECORD_AUDIO, "Voice commands"),
        Triple("Camera", Manifest.permission.CAMERA, "Photo & OCR"),
        Triple("Location", Manifest.permission.ACCESS_FINE_LOCATION, "Maps & navigation"),
        Triple("Post Notifications", Manifest.permission.POST_NOTIFICATIONS, "VASU notifications")
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineMedium)
        Text("Manage Android permissions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        permissions.forEach { (name, perm, reason) ->
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (granted) "✅" else "❌",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Role Permissions (Voice Guardian)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val roles = listOf(
            "BOSS" to "Full access to all features",
            "FAMILY" to "Normal assistant + restricted admin",
            "FRIEND" to "Info only: music, search, weather",
            "GUEST" to "Conversation only",
            "BLOCKED" to "All commands denied"
        )

        roles.forEach { (role, desc) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(role, style = MaterialTheme.typography.bodyLarge)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open App Settings") }
    }
}
