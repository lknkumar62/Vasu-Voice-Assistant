package com.vasu.ai

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vasu.ai.core.GeminiAutonomousBrain
import com.vasu.ai.core.GeminiKeyStore
import com.vasu.ai.memory.VasuMemoryStore
import com.vasu.ai.notification.VasuNotificationListener
import com.vasu.ai.voice.VasuVoiceService

class MainActivity : AppCompatActivity() {

    private lateinit var autonomousBrain: GeminiAutonomousBrain
    private lateinit var memory: VasuMemoryStore
    private lateinit var statusView: TextView
    private lateinit var outputView: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autonomousBrain = GeminiAutonomousBrain(this)
        memory = VasuMemoryStore(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 36)
        }

        root.addView(TextView(this).apply {
            text = "VASU AI"
            textSize = 32f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Autonomous Android Voice Assistant"
            textSize = 16f
        })

        section(root, "AUTONOMOUS BRAIN", "Gemini plans actions from the current Android screen. Every action is validated before execution.")
        val keyInput = EditText(this).apply {
            hint = "Paste Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(keyInput)
        root.addView(button("SAVE GEMINI API KEY") {
            val key = keyInput.text.toString().trim()
            if (key.isNotBlank()) {
                autonomousBrain.saveGeminiApiKey(key)
                keyInput.setText("")
                refreshStatus()
            }
        })
        root.addView(button("REMOVE GEMINI API KEY") {
            autonomousBrain.clearGeminiApiKey()
            refreshStatus()
        })

        section(root, "VOICE ENGINE", "Foreground microphone service. Say “Hello Vasu” followed by a command.")
        root.addView(button("START VASU VOICE") {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                return@button
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                return@button
            }
            ContextCompat.startForegroundService(this, Intent(this, VasuVoiceService::class.java))
            refreshStatus()
        })
        root.addView(button("STOP VASU VOICE") {
            stopService(Intent(this, VasuVoiceService::class.java))
            refreshStatus()
        })

        section(root, "MICROPHONE / COMMUNICATION", "Only Android runtime permissions explicitly granted to VASU are used.")
        root.addView(button("GRANT / REVIEW PHONE PERMISSIONS") {
            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(permissions.toTypedArray())
        })

        section(root, "ACCESSIBILITY OPERATOR", "Required for screen reading, clicking, typing, scrolling, Back/Home/Recents and autonomous UI workflows.")
        root.addView(button("ENABLE ACCESSIBILITY SERVICE") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        section(root, "NOTIFICATION ACCESS", "Allows VASU to receive notification context after you explicitly enable Notification Access.")
        root.addView(button("ENABLE NOTIFICATION ACCESS") {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        })

        section(root, "OVERLAY / FLOATING UI", "Optional permission for a floating VASU indicator or command bubble.")
        root.addView(button("OVERLAY / DRAW OVER OTHER APPS") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        section(root, "BATTERY / BACKGROUND", "Battery optimization can interrupt long-running voice services on some phones. Android/OEM rules still apply.")
        root.addView(button("OPEN BATTERY OPTIMIZATION SETTINGS") {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })

        section(root, "DIRECT COMMAND TEST", "Use this to test the brain without speech. Example: open YouTube / flashlight on / volume badha.")
        val commandInput = EditText(this).apply {
            hint = "Type a command"
            minLines = 2
        }
        root.addView(commandInput)
        root.addView(button("RUN COMMAND") {
            val command = commandInput.text.toString().trim()
            if (command.isNotBlank()) {
                outputView.text = "Processing…"
                autonomousBrain.handleAsync(command) { result ->
                    memory.add(command, result.reply, result.handled, result.usedGemini)
                    outputView.text = "${result.reply}\n\nHandled: ${result.handled}\nGemini: ${result.usedGemini}"
                    refreshStatus()
                }
            }
        })
        outputView = TextView(this).apply {
            textSize = 15f
            setPadding(0, 12, 0, 12)
            text = "No command run yet."
        }
        root.addView(outputView)

        section(root, "MEMORY", "Recent local command history used for diagnostics and future context features.")
        root.addView(button("SHOW RECENT MEMORY") {
            val lines = memory.recent(15).reversed().map { "• ${it.command} → ${it.reply}" }
            outputView.text = if (lines.isEmpty()) "Memory empty." else lines.joinToString("\n")
        })
        root.addView(button("CLEAR MEMORY") {
            memory.clear()
            outputView.text = "Memory cleared."
        })

        section(root, "SYSTEM STATUS", "Live permission and service state.")
        statusView = TextView(this).apply { textSize = 15f }
        root.addView(statusView)
        refreshStatus()

        root.addView(button("OPEN APP SETTINGS") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(-1, -1))
        })
    }

    private fun section(root: LinearLayout, title: String, description: String) {
        root.addView(TextView(this).apply {
            text = "\n$title"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = description
            textSize = 14f
        })
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun refreshStatus() {
        if (!::statusView.isInitialized) return
        statusView.text = listOf(
            "Microphone: ${runtimeGranted(Manifest.permission.RECORD_AUDIO)}",
            "Notifications: ${if (Build.VERSION.SDK_INT >= 33) runtimeGranted(Manifest.permission.POST_NOTIFICATIONS) else "not required"}",
            "Phone: ${runtimeGranted(Manifest.permission.CALL_PHONE)}",
            "Contacts: ${runtimeGranted(Manifest.permission.READ_CONTACTS)}",
            "SMS: ${runtimeGranted(Manifest.permission.SEND_SMS)}",
            "Accessibility: ${accessibilityEnabled()}",
            "Notification Access: ${notificationAccessEnabled()}",
            "Overlay: ${Settings.canDrawOverlays(this)}",
            "Gemini API: ${if (!GeminiKeyStore(this).read().isNullOrBlank()) "configured" else "not configured"}",
            "Voice service: ${voiceServiceRunning()}"
        ).joinToString("\n")
    }

    private fun runtimeGranted(permission: String): String =
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "not granted"

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(ComponentName(this, "com.vasu.ai.accessibility.VasuAccessibilityService").flattenToString(), true) }
    }

    private fun notificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { it.equals(ComponentName(this, VasuNotificationListener::class.java).flattenToString(), true) }
    }

    private fun voiceServiceRunning(): Boolean = getSharedPreferences("vasu_runtime", MODE_PRIVATE).getBoolean("voice_running", false)
}
