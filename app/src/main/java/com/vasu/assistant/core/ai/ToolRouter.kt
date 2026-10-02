package com.vasu.assistant.core.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.vasu.assistant.accessibility.VasuAccessibilityService
import com.vasu.assistant.camera.OcrManager
import com.vasu.assistant.camera.VisionProcessor
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.MissionEngine
import com.vasu.assistant.core.browser.BrowserManager
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.RoleManager
import com.vasu.assistant.devices.DeviceControlManager
import com.vasu.assistant.devices.MediaManager
import com.vasu.assistant.devices.TorchManager
import com.vasu.assistant.devices.VolumeManager
import com.vasu.assistant.messaging.ContactManager
import com.vasu.assistant.messaging.MessagingManager
import com.vasu.assistant.notifications.NotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolRouter - real tool execution with risk gating.
 *
 * Every tool in [getAvailableTools] either dispatches to a device/OS subsystem
 * or fails with an explicit error. There are no fake successes: an unhandled
 * tool name, a missing parameter, a disconnected accessibility service, or an
 * insufficient speaker role all return ActionResult.error.
 *
 * Risk gate: the tool's RiskLevel.requiredRole is checked against
 * [RoleManager.hasPermission] before any side effect happens (fail closed).
 * Note: RoleManager treats "Guardian disabled" as trust-the-physical-user, so
 * the gate denies only while Voice Guardian is enabled (spec section 4 flow).
 */
@Singleton
class ToolRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torchManager: TorchManager,
    private val volumeManager: VolumeManager,
    private val mediaManager: MediaManager,
    private val deviceControlManager: DeviceControlManager,
    private val browserManager: BrowserManager,
    private val contactManager: ContactManager,
    private val messagingManager: MessagingManager,
    private val ocrManager: OcrManager,
    private val visionProcessor: VisionProcessor,
    private val roleManager: RoleManager,
    private val missionEngine: MissionEngine
) {

    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            // DEVICE - Vasu smart tools
            ToolDefinition("turn_on_torch", "Turn on the device flashlight", riskLevel = RiskLevel.LOW),
            ToolDefinition("turn_off_torch", "Turn off the device flashlight", riskLevel = RiskLevel.LOW),
            ToolDefinition("volume_up", "Increase device media volume by 15%", riskLevel = RiskLevel.LOW),
            ToolDefinition("volume_down", "Decrease device media volume by 15%", riskLevel = RiskLevel.LOW),
            ToolDefinition(
                "set_volume", "Set device volume to a specific percentage (0-100)",
                parameters = listOf(ToolParameter("level", "int", "Volume percentage 0-100")),
                riskLevel = RiskLevel.LOW
            ),
            ToolDefinition("battery_info", "Check battery level and charging status", riskLevel = RiskLevel.LOW),
            ToolDefinition("device_info", "Get hardware, OS version, RAM, and display specs", riskLevel = RiskLevel.LOW),
            ToolDefinition("storage_info", "Check storage usage and available space", riskLevel = RiskLevel.LOW),
            // COMMUNICATION
            ToolDefinition(
                "open_whatsapp", "Open WhatsApp",
                parameters = listOf(
                    ToolParameter("contact", "string", "Contact name to open chat with", required = false),
                    ToolParameter("message", "string", "Message to prefill", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "make_call", "Make a phone call",
                parameters = listOf(ToolParameter("contact", "string", "Contact name or phone number")),
                riskLevel = RiskLevel.HIGH
            ),
            ToolDefinition(
                "send_message", "Send SMS or WhatsApp message",
                parameters = listOf(
                    ToolParameter("contact", "string", "Contact name"),
                    ToolParameter("message", "string", "Message body"),
                    ToolParameter("channel", "string", "Channel: sms or whatsapp (default sms)", required = false)
                ),
                riskLevel = RiskLevel.HIGH
            ),
            ToolDefinition(
                "lookup_contact", "Lookup contact by name",
                parameters = listOf(ToolParameter("name", "string", "Contact name to look up")),
                riskLevel = RiskLevel.MEDIUM
            ),
            // MEDIA / VISION
            ToolDefinition("media_play_pause", "Play or pause media", riskLevel = RiskLevel.LOW),
            ToolDefinition("media_next", "Play next track", riskLevel = RiskLevel.LOW),
            ToolDefinition("take_photo", "Take a photo", riskLevel = RiskLevel.MEDIUM),
            // ACCESSIBILITY & UTILITIES
            ToolDefinition(
                "search_web", "Search the web",
                parameters = listOf(ToolParameter("query", "string", "Search text")),
                riskLevel = RiskLevel.LOW
            ),
            ToolDefinition("read_screen", "Read current screen content", riskLevel = RiskLevel.MEDIUM),
            ToolDefinition(
                "click", "Click on screen element",
                parameters = listOf(ToolParameter("text", "string", "Visible text of the element to click")),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "type_text", "Type text into input field",
                parameters = listOf(
                    ToolParameter("label", "string", "Field label or hint", required = false),
                    ToolParameter("text", "string", "Text to type")
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition("press_back", "Press back button", riskLevel = RiskLevel.LOW),
            ToolDefinition("press_home", "Press home button", riskLevel = RiskLevel.LOW),
            ToolDefinition("scroll_down", "Scroll down", riskLevel = RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up", riskLevel = RiskLevel.LOW),
            ToolDefinition(
                "open_app", "Open an app by name",
                parameters = listOf(ToolParameter("app", "string", "App display name or package name")),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "ocr_extract", "Extract text from screen using OCR",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute image file path", required = false),
                    ToolParameter("uri", "string", "content:// image uri", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "describe_image", "Detect and describe objects in a photo or screenshot",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute image file path", required = false),
                    ToolParameter("uri", "string", "content:// image uri", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "scan_qr", "Read the QR code in a photo and return its contents",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute image file path", required = false),
                    ToolParameter("uri", "string", "content:// image uri", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            // FILES
            ToolDefinition(
                "browse_files", "Browse files in a folder",
                parameters = listOf(ToolParameter("path", "string", "Absolute folder path", required = false)),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "search_files", "Search for a file",
                parameters = listOf(
                    ToolParameter("query", "string", "File name fragment to search"),
                    ToolParameter("path", "string", "Folder to search under", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "delete_file", "Delete a file",
                parameters = listOf(ToolParameter("path", "string", "Absolute path of the file to delete")),
                riskLevel = RiskLevel.HIGH
            ),
            // ALARMS & MISSIONS
            ToolDefinition(
                "create_alarm", "Create an alarm",
                parameters = listOf(
                    ToolParameter("time", "string", "Alarm time as HH:MM"),
                    ToolParameter("label", "string", "Alarm label", required = false)
                ),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition(
                "run_mission", "Run a saved mission/automation",
                parameters = listOf(ToolParameter("mission", "string", "Mission id or name")),
                riskLevel = RiskLevel.MEDIUM
            ),
            ToolDefinition("read_notifications", "Read notifications", riskLevel = RiskLevel.MEDIUM)
        )
    }

    fun findTool(name: String): ToolDefinition? = getAvailableTools().find { it.name == name }

    /**
     * Execute [name] for real. Risk gate runs before any side effect; unknown
     * tools and missing arguments fail with an explicit error instead of
     * pretending to succeed.
     */
    suspend fun executeTool(name: String, params: Map<String, Any>): ActionResult {
        val tool = findTool(name)
            ?: return ActionResult.error(name, "Unknown tool", "No tool named '$name'").also { com.vasu.assistant.core.logging.ErrorLog.log("COMMAND", "unknown tool: $name") }

        if (!roleManager.hasPermission(tool.requiredRole)) {
            com.vasu.assistant.core.logging.ErrorLog.log("COMMAND", "denied: $name needs ${tool.requiredRole} (risk=${tool.riskLevel})")
            return ActionResult.error(
                name,
                "Permission denied",
                "${tool.riskLevel.displayName}-risk action requires ${tool.requiredRole.displayName} voice verification"
            )
        }

        // Log keys only — values may carry messages, contacts, or file paths.
        Log.i(TAG, "Executing tool: $name with params: ${params.keys}")
        return try {
            withContext(Dispatchers.IO) {
                // A hung tool (dead accessibility node, stuck OEM service) must
                // not wedge the caller's tool-call loop; time out instead.
                withTimeoutOrNull(TOOL_TIMEOUT_MS) { dispatch(name, params) }
                    ?: ActionResult.error(
                        name,
                        "Tool timed out",
                        "'$name' did not finish within ${TOOL_TIMEOUT_MS / 1000}s"
                    ).also { com.vasu.assistant.core.logging.ErrorLog.log("COMMAND", "timeout: $name") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            com.vasu.assistant.core.logging.ErrorLog.log("COMMAND", "tool $name failed: ${e.message}", e)
            ActionResult.error(name, "Tool '$name' failed", e.message ?: "Unknown error")
        }
    }

    private suspend fun dispatch(name: String, params: Map<String, Any>): ActionResult = when (name) {
        "turn_on_torch" -> torchManager.turnOn()
        "turn_off_torch" -> torchManager.turnOff()
        "volume_up" -> volumeManager.volumeUp()
        "volume_down" -> volumeManager.volumeDown()
        "set_volume" -> volumeManager.setVolume(
            paramInt(params, listOf("level", "percentage"), default = 50).coerceIn(0, 100)
        )
        "battery_info" -> batteryInfo()
        "device_info" -> ActionResult.success(
            "device_info", "Device info retrieved", deviceControlManager.getDeviceInfo()
        )
        "storage_info" -> storageInfo()
        "open_whatsapp" -> {
            val contact = paramStr(params, listOf("contact", "name"))
            val message = paramStr(params, listOf("message"))
            when {
                contact.isEmpty() && message.isNotEmpty() ->
                    ActionResult.error("open_whatsapp", "No contact given", "Provide the 'contact' for the message")
                contact.isNotEmpty() -> messagingManager.openWhatsApp(contact, message)
                else -> openAppByPackage("com.whatsapp", "WhatsApp", "open_whatsapp")
            }
        }
        "make_call" -> {
            val target = paramStr(params, listOf("contact", "number", "phone"))
            if (target.isEmpty()) {
                ActionResult.error("make_call", "No contact given", "Provide a contact name or phone number")
            } else {
                messagingManager.makeCall(target)
            }
        }
        "send_message" -> {
            val contact = paramStr(params, listOf("contact", "name", "to"))
            val message = paramStr(params, listOf("message", "text", "body"))
            when {
                contact.isEmpty() || message.isEmpty() ->
                    ActionResult.error("send_message", "Missing contact or message", "Provide both 'contact' and 'message'")
                paramStr(params, listOf("channel")).equals("whatsapp", ignoreCase = true) ->
                    messagingManager.openWhatsApp(contact, message)
                else -> messagingManager.sendSms(contact, message)
            }
        }
        "lookup_contact" -> lookupContact(params)
        "media_play_pause" -> mediaManager.playPause()
        "media_next" -> mediaManager.next()
        "take_photo" -> deviceControlManager.openCamera()
        "search_web" -> {
            val query = paramStr(params, listOf("query", "q", "question"))
            if (query.isEmpty()) {
                ActionResult.error("search_web", "No query given", "Provide a 'query' to search")
            } else {
                browserManager.search(query)
                ActionResult.success("search_web", "Searching the web for \"$query\"", mapOf("query" to query))
            }
        }
        "read_screen" -> withAccessibility("read_screen") { it.readScreen() }
        "click" -> {
            val text = paramStr(params, listOf("text", "label"))
            if (text.isEmpty()) {
                ActionResult.error("click", "No element text given", "Provide the 'text' of the element to click")
            } else {
                withAccessibility("click") { it.clickElement(text) }
            }
        }
        "type_text" -> {
            val text = paramStr(params, listOf("text"))
            if (text.isEmpty()) {
                ActionResult.error("type_text", "No text given", "Provide the 'text' to type")
            } else {
                withAccessibility("type_text") { it.typeText(paramStr(params, listOf("label")), text) }
            }
        }
        "press_back" -> withAccessibility("press_back") { it.pressBack() }
        "press_home" -> withAccessibility("press_home") { it.pressHome() }
        "scroll_down" -> withAccessibility("scroll_down") { it.scrollDown() }
        "scroll_up" -> withAccessibility("scroll_up") { it.scrollUp() }
        "open_app" -> openApp(params)
        "ocr_extract" -> ocrExtract(params)
        "describe_image" -> describeImage(params)
        "scan_qr" -> scanQr(params)
        "browse_files" -> browseFiles(params)
        "search_files" -> searchFiles(params)
        "delete_file" -> deleteFile(params)
        "create_alarm" -> createAlarm(params)
        "run_mission" -> missionEngine.executeMission(paramStr(params, listOf("mission", "id", "name")))
        "read_notifications" -> readNotifications()
        else -> ActionResult.error(name, "Tool not wired", "No handler for '$name'")
    }

    // ---------------------------------------------------------------- helpers

    private fun paramStr(params: Map<String, Any>, keys: List<String>): String =
        keys.firstNotNullOfOrNull { key -> params[key]?.toString()?.takeIf { it.isNotBlank() } } ?: ""

    private fun paramInt(params: Map<String, Any>, keys: List<String>, default: Int): Int =
        keys.firstNotNullOfOrNull { key -> params[key]?.toString()?.trim()?.toDoubleOrNull()?.toInt() } ?: default

    private fun batteryInfo(): ActionResult {
        val info = deviceControlManager.getBatteryInfo()
        val level = info["level"] as? Int ?: -1
        val charging = info["isCharging"] as? Boolean ?: false
        return if (level >= 0) {
            ActionResult.success(
                "battery_info",
                "Battery at $level%${if (charging) ", charging" else ""}",
                info
            )
        } else {
            ActionResult.error("battery_info", "Battery unavailable", "Could not read the battery level")
        }
    }

    private fun storageInfo(): ActionResult {
        val dir = Environment.getExternalStorageDirectory()
        val stat = StatFs(dir.absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val usedPercent = if (total > 0) ((total - free) * 100 / total).toInt() else 0
        return ActionResult.success(
            "storage_info",
            "Storage $usedPercent% used, ${free / (1024 * 1024)} MB free",
            mapOf(
                "totalBytes" to total,
                "freeBytes" to free,
                "usedPercent" to usedPercent
            )
        )
    }

    private fun lookupContact(params: Map<String, Any>): ActionResult {
        val name = paramStr(params, listOf("name", "contact", "query"))
        if (name.isEmpty()) {
            return ActionResult.error("lookup_contact", "No name given", "Provide a contact 'name'")
        }
        val contact = contactManager.findBestMatch(name)
            ?: return ActionResult.error("lookup_contact", "Contact not found", "No contact matches '$name'")
        return ActionResult.success(
            "lookup_contact",
            "Found ${contact.name}",
            mapOf("name" to contact.name, "number" to contact.phoneNumber)
        )
    }

    private fun withAccessibility(action: String, block: (VasuAccessibilityService) -> ActionResult): ActionResult {
        val service = VasuAccessibilityService.instance.value
            ?: return ActionResult.error(
                action, "Accessibility service not running",
                "Enable VASU Accessibility in Android Settings"
            )
        return block(service)
    }

    private fun openApp(params: Map<String, Any>): ActionResult {
        val query = paramStr(params, listOf("app", "package", "name"))
        if (query.isEmpty()) {
            return ActionResult.error("open_app", "No app given", "Provide an app name or package")
        }
        val pkg = if (query.contains('.')) query else resolvePackageByLabel(query)
            ?: return ActionResult.error("open_app", "App not found", "No app matches '$query'")

        val service = VasuAccessibilityService.instance.value
        if (service != null) {
            val result = service.openApp(pkg)
            if (result.success) return result
        }
        return openAppByPackage(pkg, pkg)
    }

    private fun openAppByPackage(pkg: String, display: String, action: String = "open_app"): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return ActionResult.error(action, "App not launchable", "$display is not launchable")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult.success(action, "Opened $display", mapOf("package" to pkg))
        } catch (e: Exception) {
            ActionResult.error(action, "Failed to open $display", e.message ?: "Unknown error")
        }
    }

    private fun resolvePackageByLabel(label: String): String? {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        apps.firstOrNull { it.loadLabel(pm).toString().equals(label, ignoreCase = true) }
            ?.let { return it.activityInfo.packageName }
        return apps.firstOrNull { it.loadLabel(pm).toString().contains(label, ignoreCase = true) }
            ?.activityInfo?.packageName
    }

    private fun imageTarget(params: Map<String, Any>): Uri? {
        val uri = paramStr(params, listOf("uri"))
        if (uri.isNotEmpty()) return Uri.parse(uri)
        val path = paramStr(params, listOf("path", "file"))
        if (path.isNotEmpty()) return Uri.parse("file://$path")
        return null
    }

    private suspend fun ocrExtract(params: Map<String, Any>): ActionResult {
        val target = imageTarget(params)
            ?: return ActionResult.error("ocr_extract", "No image given", "Provide 'path' or 'uri'")
        return ocrManager.extractText(target)
    }

    private suspend fun describeImage(params: Map<String, Any>): ActionResult {
        val target = imageTarget(params)
            ?: return ActionResult.error("describe_image", "No image given", "Provide 'path' or 'uri'")
        return visionProcessor.analyzeImage(target)
    }

    private suspend fun scanQr(params: Map<String, Any>): ActionResult {
        val target = imageTarget(params)
            ?: return ActionResult.error("scan_qr", "No image given", "Provide 'path' or 'uri'")
        return visionProcessor.scanQrCode(target)
    }

    private fun browseFiles(params: Map<String, Any>): ActionResult {
        val path = paramStr(params, listOf("path")).ifEmpty {
            Environment.getExternalStorageDirectory().absolutePath
        }
        val dir = File(path)
        if (!dir.isDirectory || !dir.canRead()) {
            return ActionResult.error("browse_files", "Folder not readable", "'$path' is not a readable folder")
        }
        val children = dir.listFiles()
            ?.map { if (it.isDirectory) "${it.name}/" else it.name }
            ?.sorted()
            ?.take(100)
            ?: emptyList()
        return ActionResult.success(
            "browse_files",
            "Found ${children.size} items in ${dir.name}",
            mapOf("path" to path, "files" to children)
        )
    }

    private fun searchFiles(params: Map<String, Any>): ActionResult {
        val query = paramStr(params, listOf("query", "name"))
        if (query.isEmpty()) {
            return ActionResult.error("search_files", "No query given", "Provide a 'query' to search")
        }
        val rootPath = paramStr(params, listOf("path")).ifEmpty {
            Environment.getExternalStorageDirectory().absolutePath
        }
        val root = File(rootPath)
        if (!root.isDirectory || !root.canRead()) {
            return ActionResult.error("search_files", "Folder not readable", "'$rootPath' is not a readable folder")
        }

        val matches = ArrayList<String>()
        val deadline = System.currentTimeMillis() + SEARCH_TIME_BUDGET_MS
        fun walk(dir: File, depth: Int) {
            if (depth > MAX_SEARCH_DEPTH || matches.size >= MAX_SEARCH_RESULTS) return
            if (System.currentTimeMillis() > deadline) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (matches.size >= MAX_SEARCH_RESULTS || System.currentTimeMillis() > deadline) return
                if (child.isDirectory) {
                    walk(child, depth + 1)
                } else if (child.name.contains(query, ignoreCase = true)) {
                    matches.add(child.absolutePath)
                }
            }
        }
        walk(root, 0)

        return if (matches.isEmpty()) {
            ActionResult.error("search_files", "No matches", "No file matching '$query' under $rootPath")
        } else {
            ActionResult.success(
                "search_files",
                "Found ${matches.size} file(s) matching '$query'",
                mapOf("query" to query, "files" to matches)
            )
        }
    }

    private fun deleteFile(params: Map<String, Any>): ActionResult {
        val path = paramStr(params, listOf("path", "file"))
        if (path.isEmpty()) {
            return ActionResult.error("delete_file", "No path given", "Provide the 'path' of the file to delete")
        }
        val file = File(path)
        return when {
            !file.exists() -> ActionResult.error("delete_file", "File not found", "'$path' does not exist")
            file.isDirectory ->
                ActionResult.error("delete_file", "Refusing folder delete", "Only single files can be deleted")
            !file.canWrite() ->
                ActionResult.error("delete_file", "Permission denied", "Cannot write to '$path'")
            else -> {
                val deleted = file.delete()
                if (deleted) {
                    ActionResult.success("delete_file", "Deleted ${file.name}", mapOf("path" to path))
                } else {
                    ActionResult.error("delete_file", "Delete failed", "Could not delete '$path'")
                }
            }
        }
    }

    private fun createAlarm(params: Map<String, Any>): ActionResult {
        val time = paramStr(params, listOf("time", "alarm_time"))
        if (time.isEmpty()) {
            return ActionResult.error("create_alarm", "No time given", "Provide 'time' as HH:MM")
        }
        val label = paramStr(params, listOf("label", "title")).ifEmpty { "VASU Alarm" }
        return deviceControlManager.createAlarm(time, label)
    }

    private fun readNotifications(): ActionResult {
        val listener = NotificationListener.instance
            ?: return ActionResult.error(
                "read_notifications", "Notification access not granted",
                "Enable notification access for VASU in Android Settings"
            )
        val items = listener.getActiveParsedNotifications().take(10).map {
            mapOf(
                "app" to it.appName,
                "title" to it.title,
                "text" to (it.bigText ?: it.text),
                "time" to it.formattedTime
            )
        }
        return if (items.isEmpty()) {
            ActionResult.success("read_notifications", "No active notifications", mapOf("notifications" to items))
        } else {
            ActionResult.success(
                "read_notifications",
                "Found ${items.size} notification(s)",
                mapOf("notifications" to items)
            )
        }
    }

    companion object {
        private const val TAG = "ToolRouter"
        private const val MAX_SEARCH_DEPTH = 4
        private const val MAX_SEARCH_RESULTS = 50
        private const val SEARCH_TIME_BUDGET_MS = 1500L

        /** Max wall time for a single tool dispatch before it is abandoned. */
        private const val TOOL_TIMEOUT_MS = 30_000L
    }
}
