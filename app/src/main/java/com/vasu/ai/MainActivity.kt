package com.vasu.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
    }

    private fun refresh() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "VASU AI\nPermission Center"
            textSize = 28f
        })

        root.addView(TextView(this).apply {
            text = "VASU only uses capabilities you explicitly grant. Some Android restrictions cannot be bypassed."
            textSize = 16f
        })

        addPermissionButton(
            root,
            "Microphone / Notifications",
            "Voice input and visible notification feedback.",
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        )

        addPermissionButton(
            root,
            "Calls / Contacts / SMS",
            "Calling contacts and sending SMS when you ask VASU to do so.",
            arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS)
        )

        root.addView(Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "Overlay / Draw over other apps"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        root.addView(Button(this).apply {
            text = "Open App Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })

        root.addView(TextView(this).apply {
            text = "Status\n${statusText()}"
            textSize = 15f
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(-1, -1))
        })
    }

    private fun addPermissionButton(
        root: LinearLayout,
        title: String,
        reason: String,
        permissions: Array<String>
    ) {
        root.addView(TextView(this).apply {
            text = "$title\n$reason"
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "Grant / Review"
            setOnClickListener { permissionLauncher.launch(permissions) }
        })
    }

    private fun statusText(): String {
        val permissions = listOf(
            Manifest.permission.RECORD_AUDIO to "Microphone",
            Manifest.permission.POST_NOTIFICATIONS to "Notifications",
            Manifest.permission.CALL_PHONE to "Phone",
            Manifest.permission.READ_CONTACTS to "Contacts",
            Manifest.permission.SEND_SMS to "SMS"
        )
        return permissions.joinToString("\n") { (permission, label) ->
            "$label: ${if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "not granted"}"
        }
    }
}
