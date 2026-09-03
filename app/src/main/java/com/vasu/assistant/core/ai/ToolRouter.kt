package com.vasu.assistant.core.ai

import android.net.Uri
import com.vasu.assistant.calls.CallManager
import com.vasu.assistant.camera.OcrManager
import com.vasu.assistant.camera.VasuCameraManager
import com.vasu.assistant.camera.VisionProcessor
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.AutomationEngine
import com.vasu.assistant.core.automation.AutomationStep
import com.vasu.assistant.core.automation.MacroEngine
import com.vasu.assistant.core.automation.MissionEngine
import com.vasu.assistant.core.security.PermissionGate
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.Tool
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.devices.DeviceControlManager
import com.vasu.assistant.files.FileManager
import com.vasu.assistant.maps.PlacesManager
import com.vasu.assistant.maps.VasuLocationManager
import com.vasu.assistant.messaging.MessagingManager
import com.vasu.assistant.messaging.WhatsAppAutomation
import com.vasu.assistant.notifications.NotificationActionManager
import com.vasu.assistant.notifications.NotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val riskLevel: RiskLevel,
    val requiredRole: UserRole = riskLevel.requiredRole
)

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

@Singleton
class ToolRouter @Inject constructor(
    private val permissionGate: PermissionGate,
    private val automationEngine: AutomationEngine,
    private val callManager: CallManager,
    private val messagingManager: MessagingManager,
    private val whatsappAutomation: WhatsAppAutomation,
    private val deviceControl: DeviceControlManager,
    private val fileManager: FileManager,
    private val cameraManager: VasuCameraManager,
    private val browserManager: com.vasu.assistant.core.browser.BrowserManager,
    private val locationManager: VasuLocationManager,
    private val placesManager: PlacesManager,
    private val macroEngine: MacroEngine,
    private val notificationActionManager: NotificationActionManager,
    private val visionProcessor: VisionProcessor,
    private val ocrManager: OcrManager,
    private val missionEngineProvider: Provider<MissionEngine>
) {
    private val missionEngine: MissionEngine
        get() = missionEngineProvider.get()

    private val toolRegistry = mutableMapOf<String, ToolDefinition>()

    init {
        registerDefaultTools()
    }

    fun registerTool(tool: ToolDefinition) {
        toolRegistry[tool.name] = tool
    }

    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ActionResult {
        val tool = toolRegistry[toolName]
            ?: return ActionResult.error(toolName, "Tool not found", "Unknown tool: $toolName")

        val permission = permissionGate.checkPermission(
            Tool(tool.name, tool.description, tool.riskLevel, tool.requiredRole)
        )
        if (permission is com.vasu.assistant.core.security.PermissionResult.Denied) {
            return ActionResult.error(toolName, "Permission denied", permission.reason)
        }

        return when (toolName) {
            // === APPS & AUTOMATION ===
            "open_app" -> {
                val pkg = parameters["package"] as? String ?: parameters["name"] as? String ?: ""
                automationEngine.executeSteps(listOf(AutomationStep("open_app", mapOf("package" to pkg))))
                    .let { if (it.success) ActionResult.success("open_app", "Opened $pkg") else ActionResult.error("open_app", it.message, it.message) }
            }
            "close_app" -> {
                automationEngine.executeSteps(listOf(AutomationStep("home")))
                    .let { if (it.success) ActionResult.success("close_app", "Closed application") else ActionResult.error("close_app", it.message, it.message) }
            }
            "list_apps", "search_apps" -> {
                val query = parameters["query"] as? String ?: ""
                deviceControl.listInstalledApps(query)
            }
            "click", "click_element" -> {
                val text = parameters["text"] as? String ?: ""
                automationEngine.executeSteps(listOf(AutomationStep("click", mapOf("text" to text))))
                    .let { if (it.success) ActionResult.success("click", "Clicked: $text") else ActionResult.error("click", it.message, it.message) }
            }
            "type_text" -> {
                val text = parameters["text"] as? String ?: ""
                val label = parameters["label"] as? String ?: ""
                automationEngine.executeSteps(listOf(AutomationStep("type", mapOf("label" to label, "text" to text))))
                    .let { if (it.success) ActionResult.success("type_text", "Typed: $text") else ActionResult.error("type_text", it.message, it.message) }
            }
            "read_screen" -> {
                automationEngine.executeSteps(listOf(AutomationStep("read_screen")))
                    .let { it.stepResults.firstOrNull() ?: ActionResult.error("read_screen", "Failed", "No result") }
            }
            "scroll_down" -> {
                automationEngine.executeSteps(listOf(AutomationStep("scroll_down")))
                    .let { if (it.success) ActionResult.success("scroll_down", "Scrolled down") else ActionResult.error("scroll_down", it.message, it.message) }
            }
            "scroll_up" -> {
                automationEngine.executeSteps(listOf(AutomationStep("scroll_up")))
                    .let { if (it.success) ActionResult.success("scroll_up", "Scrolled up") else ActionResult.error("scroll_up", it.message, it.message) }
            }
            "scroll_screen" -> {
                val direction = parameters["direction"] as? String ?: "down"
                val stepAction = if (direction.equals("up", ignoreCase = true)) "scroll_up" else "scroll_down"
                automationEngine.executeSteps(listOf(AutomationStep(stepAction)))
                    .let { if (it.success) ActionResult.success("scroll_screen", "Scrolled $direction") else ActionResult.error("scroll_screen", it.message, it.message) }
            }
            "press_back" -> {
                automationEngine.executeSteps(listOf(AutomationStep("back")))
                    .let { if (it.success) ActionResult.success("back", "Pressed back") else ActionResult.error("back", it.message, it.message) }
            }
            "press_home" -> {
                automationEngine.executeSteps(listOf(AutomationStep("home")))
                    .let { if (it.success) ActionResult.success("home", "Pressed home") else ActionResult.error("home", it.message, it.message) }
            }
            "take_screenshot" -> {
                ActionResult.success("take_screenshot", "Screenshot requested via system accessibility")
            }

            // === HARDWARE & DEVICE CONTROLS ===
            "turn_on_torch" -> {
                val enabled = parameters["enabled"] as? Boolean ?: true
                if (enabled) deviceControl.getTorch().turnOn() else deviceControl.getTorch().turnOff()
            }
            "turn_off_torch" -> deviceControl.getTorch().turnOff()
            "toggle_torch" -> deviceControl.getTorch().toggle()
            "set_volume" -> {
                val level = (parameters["level"] as? Number)?.toInt() ?: 50
                deviceControl.getVolume().setVolume(level)
            }
            "get_volume" -> {
                val vol = deviceControl.getVolume().getVolume()
                ActionResult.success("get_volume", "Current volume: $vol%", mapOf("volume" to vol))
            }
            "volume_up" -> deviceControl.getVolume().volumeUp()
            "volume_down" -> deviceControl.getVolume().volumeDown()
            "set_brightness" -> {
                val level = (parameters["level"] as? Number)?.toInt() ?: 50
                deviceControl.setBrightness(level)
            }
            "toggle_bluetooth" -> deviceControl.getBluetooth().toggle()
            "toggle_wifi" -> deviceControl.toggleWifi()
            "set_ringer_mode" -> {
                val mode = parameters["mode"] as? String ?: "normal"
                deviceControl.setRingerMode(mode)
            }
            "battery_info", "get_battery_info" -> {
                val info = deviceControl.getBatteryInfo()
                ActionResult.success("battery", "Battery: ${info["level"]}%, Charging: ${info["isCharging"]}", info)
            }
            "device_info" -> {
                val info = deviceControl.getDeviceInfo()
                ActionResult.success("device", "${info["brand"]} ${info["model"]}, Android ${info["android_version"]}", info)
            }

            // === MEDIA CONTROLS ===
            "play_music" -> deviceControl.getMedia().play()
            "pause_music" -> deviceControl.getMedia().pause()
            "media_play_pause" -> deviceControl.getMedia().playPause()
            "media_next", "next_track" -> deviceControl.getMedia().next()
            "media_previous", "previous_track" -> deviceControl.getMedia().previous()

            // === COMMUNICATION ===
            "make_call" -> {
                val number = parameters["number"] as? String ?: parameters["contact"] as? String ?: ""
                callManager.makeCall(number)
            }
            "send_sms", "send_message" -> {
                val contact = parameters["contact"] as? String ?: parameters["number"] as? String ?: ""
                val msg = parameters["message"] as? String ?: ""
                messagingManager.sendSms(contact, msg)
            }
            "whatsapp", "send_whatsapp_message" -> {
                val contact = parameters["contact"] as? String ?: ""
                val msg = parameters["message"] as? String ?: ""
                whatsappAutomation.sendMessage(contact, msg)
            }
            "read_notifications" -> {
                val active = NotificationListener.instance?.getActiveParsedNotifications() ?: emptyList()
                val summary = active.map { mapOf("app" to it.appName, "title" to it.title, "text" to it.text, "time" to it.formattedTime) }
                ActionResult.success("notifications", "Found ${summary.size} active notifications", mapOf("notifications" to summary))
            }
            "dismiss_notification" -> {
                val pkg = parameters["package"] as? String ?: ""
                val notifId = (parameters["id"] as? Number)?.toInt() ?: 0
                notificationActionManager.dismissNotification(pkg, notifId)
            }

            // === TIME & ALARM ===
            "create_alarm" -> {
                val time = parameters["time"] as? String ?: "07:00"
                val label = parameters["label"] as? String ?: "VASU Alarm"
                deviceControl.createAlarm(time, label)
            }
            "set_timer" -> {
                val seconds = (parameters["seconds"] as? Number)?.toInt()
                    ?: (parameters["duration"] as? Number)?.toInt()
                    ?: 60
                val label = parameters["label"] as? String ?: "VASU Timer"
                deviceControl.setTimer(seconds, label)
            }
            "get_time" -> {
                val formatted = SimpleDateFormat("hh:mm a, EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                ActionResult.success("time", "Current time is $formatted", mapOf("time" to formatted))
            }
            "get_weather" -> {
                val city = parameters["city"] as? String ?: parameters["location"] as? String ?: ""
                val query = if (city.isNotBlank()) "weather in $city" else "current weather"
                browserManager.search(query)
                ActionResult.success("weather", "Checking weather for ${if (city.isNotBlank()) city else "current location"}")
            }

            // === NAVIGATION & MAPS ===
            "get_current_location" -> locationManager.getCurrentLocation()
            "save_parking" -> locationManager.saveParkingLocation()
            "search_places" -> {
                val query = parameters["query"] as? String ?: ""
                placesManager.searchNearby(query)
            }
            "get_directions" -> {
                val dest = parameters["destination"] as? String ?: parameters["to"] as? String ?: ""
                locationManager.openNavigation(dest)
            }
            "search_web" -> {
                val query = parameters["query"] as? String ?: ""
                browserManager.search(query)
                ActionResult.success("search_web", "Searching for $query")
            }
            "open_url" -> {
                val url = parameters["url"] as? String ?: ""
                browserManager.openUrl(url)
                ActionResult.success("open_url", "Opened $url")
            }
            "open_youtube" -> {
                val query = parameters["query"] as? String ?: ""
                browserManager.openYouTube(query)
                ActionResult.success("open_youtube", "Opened YouTube for $query")
            }
            "open_maps" -> {
                val query = parameters["query"] as? String ?: ""
                browserManager.openMaps(query)
                ActionResult.success("open_maps", "Opened Maps for $query")
            }

            // === FILE MANAGEMENT ===
            "browse_files" -> fileManager.browseDirectory(parameters["path"] as? String ?: "")
            "search_files" -> fileManager.searchFiles(parameters["query"] as? String ?: "")
            "read_file" -> fileManager.readFileContent(parameters["path"] as? String ?: "")
            "rename_file" -> fileManager.renameFile(parameters["path"] as? String ?: "", parameters["newName"] as? String ?: "")
            "copy_file" -> fileManager.copyFile(parameters["source"] as? String ?: "", parameters["dest"] as? String ?: "")
            "move_file" -> fileManager.moveFile(parameters["source"] as? String ?: "", parameters["dest"] as? String ?: "")
            "delete_file" -> fileManager.deleteFile(parameters["path"] as? String ?: "")
            "share_file" -> fileManager.shareFile(parameters["path"] as? String ?: "")
            "storage_info", "storage_analyzer" -> fileManager.getStorageInfo()
            "list_images" -> fileManager.listImages(parameters["path"] as? String ?: "")

            // === CAMERA & VISION ===
            "take_photo" -> cameraManager.takePhoto()
            "start_recording", "record_video" -> cameraManager.startRecording()
            "stop_recording" -> cameraManager.stopRecording()
            "toggle_flash" -> cameraManager.toggleFlash()
            "ocr_extract", "ocr_screen" -> {
                val uriStr = parameters["image_uri"] as? String ?: ""
                if (uriStr.isNotBlank()) {
                    ocrManager.extractText(Uri.parse(uriStr))
                } else {
                    ActionResult.error("ocr", "No image URI provided for OCR", "Missing parameter")
                }
            }
            "scan_qr" -> {
                val uriStr = parameters["image_uri"] as? String ?: ""
                if (uriStr.isNotBlank()) {
                    visionProcessor.scanQrCode(Uri.parse(uriStr))
                } else {
                    ActionResult.error("scan_qr", "No image URI provided for QR scan", "Missing parameter")
                }
            }
            "analyze_image" -> {
                val uriStr = parameters["image_uri"] as? String ?: ""
                if (uriStr.isNotBlank()) {
                    visionProcessor.analyzeImage(Uri.parse(uriStr))
                } else {
                    ActionResult.error("analyze_image", "No image URI provided", "Missing parameter")
                }
            }

            // === MACROS & MISSIONS ===
            "create_macro" -> {
                val name = parameters["name"] as? String ?: "New Macro"
                val trigger = parameters["trigger"] as? String ?: ""
                val macro = macroEngine.createMacro(name, trigger, emptyList())
                ActionResult.success("create_macro", "Created macro: $name", mapOf("macroId" to macro.id))
            }
            "run_macro", "execute_macro" -> {
                val macroId = parameters["macro_id"] as? String ?: ""
                macroEngine.runMacro(macroId)
            }
            "list_macros" -> macroEngine.listMacros()
            "toggle_macro" -> {
                val macroId = parameters["macro_id"] as? String ?: ""
                macroEngine.toggleMacro(macroId)
            }
            "delete_macro" -> {
                val macroId = parameters["macro_id"] as? String ?: ""
                macroEngine.deleteMacro(macroId)
            }
            "run_mission" -> {
                val missionId = parameters["mission_id"] as? String ?: ""
                val success = missionEngine.executeMission(missionId)
                if (success) ActionResult.success("run_mission", "Mission completed successfully")
                else ActionResult.error("run_mission", "Mission execution failed", "Failed step")
            }

            else -> ActionResult.error(toolName, "Tool not implemented", "Not yet available: $toolName")
        }
    }

    fun getAvailableTools(): List<ToolDefinition> = toolRegistry.values.toList()

    private fun registerDefaultTools() {
        val tools = listOf(
            // Apps & Automation
            ToolDefinition("open_app", "Open an application", listOf(ToolParameter("package", "string", "Package or app name")), RiskLevel.LOW),
            ToolDefinition("close_app", "Close currently open application", emptyList(), RiskLevel.LOW),
            ToolDefinition("list_apps", "List installed applications", listOf(ToolParameter("query", "string", "Search filter", false)), RiskLevel.LOW),
            ToolDefinition("search_apps", "Search installed applications", listOf(ToolParameter("query", "string", "App name")), RiskLevel.LOW),
            ToolDefinition("click", "Click on a UI element", listOf(ToolParameter("text", "string", "Text to click")), RiskLevel.LOW),
            ToolDefinition("click_element", "Click on a UI element", listOf(ToolParameter("text", "string", "Text to click")), RiskLevel.LOW),
            ToolDefinition("type_text", "Type text into an input field", listOf(ToolParameter("text", "string", "Text"), ToolParameter("label", "string", "Field label", false)), RiskLevel.LOW),
            ToolDefinition("read_screen", "Read text content of current screen", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_down", "Scroll down the current screen", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up the current screen", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_screen", "Scroll the screen in given direction", listOf(ToolParameter("direction", "string", "up or down", false)), RiskLevel.LOW),
            ToolDefinition("press_back", "Press the device Back button", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_home", "Press the device Home button", emptyList(), RiskLevel.LOW),
            ToolDefinition("take_screenshot", "Capture screen content", emptyList(), RiskLevel.LOW),

            // Device & Hardware
            ToolDefinition("turn_on_torch", "Turn flashlight on", listOf(ToolParameter("enabled", "boolean", "On/off", false)), RiskLevel.LOW),
            ToolDefinition("turn_off_torch", "Turn flashlight off", emptyList(), RiskLevel.LOW),
            ToolDefinition("toggle_torch", "Toggle device flashlight", emptyList(), RiskLevel.LOW),
            ToolDefinition("set_volume", "Set device media volume percentage", listOf(ToolParameter("level", "int", "0-100")), RiskLevel.LOW),
            ToolDefinition("get_volume", "Get current device volume level", emptyList(), RiskLevel.LOW),
            ToolDefinition("volume_up", "Increase media volume", emptyList(), RiskLevel.LOW),
            ToolDefinition("volume_down", "Decrease media volume", emptyList(), RiskLevel.LOW),
            ToolDefinition("set_brightness", "Set display brightness level", listOf(ToolParameter("level", "int", "0-100")), RiskLevel.LOW),
            ToolDefinition("toggle_bluetooth", "Toggle Bluetooth radio", emptyList(), RiskLevel.LOW),
            ToolDefinition("toggle_wifi", "Open Wi-Fi settings to toggle", emptyList(), RiskLevel.LOW),
            ToolDefinition("set_ringer_mode", "Set ringer mode (normal, silent, vibrate)", listOf(ToolParameter("mode", "string", "normal/silent/vibrate")), RiskLevel.LOW),
            ToolDefinition("battery_info", "Get battery charge and charging status", emptyList(), RiskLevel.LOW),
            ToolDefinition("get_battery_info", "Get battery charge level and status", emptyList(), RiskLevel.LOW),
            ToolDefinition("device_info", "Get device manufacturer, model, and OS info", emptyList(), RiskLevel.LOW),

            // Media
            ToolDefinition("play_music", "Resume or start music playback", emptyList(), RiskLevel.LOW),
            ToolDefinition("pause_music", "Pause music playback", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_play_pause", "Toggle play/pause on active media", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_next", "Skip to next media track", emptyList(), RiskLevel.LOW),
            ToolDefinition("next_track", "Skip to next track", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_previous", "Skip to previous media track", emptyList(), RiskLevel.LOW),
            ToolDefinition("previous_track", "Skip to previous track", emptyList(), RiskLevel.LOW),

            // Communication
            ToolDefinition("make_call", "Place a phone call to a contact or number", listOf(ToolParameter("number", "string", "Contact name or phone number")), RiskLevel.MEDIUM),
            ToolDefinition("send_sms", "Send an SMS message", listOf(ToolParameter("contact", "string", "Recipient phone number"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("send_message", "Send an SMS message", listOf(ToolParameter("contact", "string", "Recipient phone number"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("whatsapp", "Send a message via WhatsApp", listOf(ToolParameter("contact", "string", "Contact name"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("send_whatsapp_message", "Send WhatsApp message to contact", listOf(ToolParameter("contact", "string", "Contact name"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("read_notifications", "Read currently active notifications", emptyList(), RiskLevel.LOW),
            ToolDefinition("dismiss_notification", "Dismiss a notification", listOf(ToolParameter("package", "string", "Package name", false), ToolParameter("id", "int", "Notification ID", false)), RiskLevel.LOW),

            // Time & Alarm
            ToolDefinition("create_alarm", "Set a device alarm clock", listOf(ToolParameter("time", "string", "HH:mm or time expression"), ToolParameter("label", "string", "Alarm description", false)), RiskLevel.LOW),
            ToolDefinition("set_timer", "Set a countdown timer in seconds", listOf(ToolParameter("seconds", "int", "Timer duration in seconds"), ToolParameter("label", "string", "Timer label", false)), RiskLevel.LOW),
            ToolDefinition("get_time", "Get current time, day, and date", emptyList(), RiskLevel.LOW),
            ToolDefinition("get_weather", "Check weather information", listOf(ToolParameter("city", "string", "City name", false)), RiskLevel.LOW),

            // Navigation & Web
            ToolDefinition("get_current_location", "Get current GPS coordinates and address", emptyList(), RiskLevel.LOW),
            ToolDefinition("save_parking", "Save current location as parking spot", emptyList(), RiskLevel.LOW),
            ToolDefinition("search_places", "Search nearby places or businesses", listOf(ToolParameter("query", "string", "Place search query")), RiskLevel.LOW),
            ToolDefinition("get_directions", "Open turn-by-turn navigation in Maps", listOf(ToolParameter("destination", "string", "Destination name or address")), RiskLevel.LOW),
            ToolDefinition("search_web", "Search the web via browser", listOf(ToolParameter("query", "string", "Search query")), RiskLevel.LOW),
            ToolDefinition("open_url", "Open a web URL in browser", listOf(ToolParameter("url", "string", "Website URL")), RiskLevel.LOW),
            ToolDefinition("open_youtube", "Search and play video on YouTube", listOf(ToolParameter("query", "string", "Search keywords")), RiskLevel.LOW),
            ToolDefinition("open_maps", "Search location on Google Maps", listOf(ToolParameter("query", "string", "Location query")), RiskLevel.LOW),

            // Files
            ToolDefinition("browse_files", "Browse files and folders in directory", listOf(ToolParameter("path", "string", "Directory path", false)), RiskLevel.LOW),
            ToolDefinition("search_files", "Search files by name in storage", listOf(ToolParameter("query", "string", "Search keyword")), RiskLevel.LOW),
            ToolDefinition("read_file", "Read text content of a file", listOf(ToolParameter("path", "string", "File path")), RiskLevel.LOW),
            ToolDefinition("rename_file", "Rename a file or folder", listOf(ToolParameter("path", "string", "File path"), ToolParameter("newName", "string", "New file name")), RiskLevel.MEDIUM),
            ToolDefinition("copy_file", "Copy a file to another folder", listOf(ToolParameter("source", "string", "Source file path"), ToolParameter("dest", "string", "Destination directory")), RiskLevel.MEDIUM),
            ToolDefinition("move_file", "Move a file to another folder", listOf(ToolParameter("source", "string", "Source file path"), ToolParameter("dest", "string", "Destination directory")), RiskLevel.MEDIUM),
            ToolDefinition("delete_file", "Delete a file from storage", listOf(ToolParameter("path", "string", "File path")), RiskLevel.HIGH),
            ToolDefinition("share_file", "Share a file via Android share sheet", listOf(ToolParameter("path", "string", "File path")), RiskLevel.LOW),
            ToolDefinition("storage_info", "Get internal/external storage usage statistics", emptyList(), RiskLevel.LOW),
            ToolDefinition("storage_analyzer", "Analyze storage breakdown", emptyList(), RiskLevel.LOW),
            ToolDefinition("list_images", "List recent photos/images from storage", listOf(ToolParameter("path", "string", "Directory path", false)), RiskLevel.LOW),

            // Camera & Vision
            ToolDefinition("take_photo", "Take a photo using camera", emptyList(), RiskLevel.MEDIUM),
            ToolDefinition("record_video", "Start camera video recording", emptyList(), RiskLevel.MEDIUM),
            ToolDefinition("start_recording", "Start video recording", emptyList(), RiskLevel.MEDIUM),
            ToolDefinition("stop_recording", "Stop video recording", emptyList(), RiskLevel.LOW),
            ToolDefinition("toggle_flash", "Toggle camera flash / torch", emptyList(), RiskLevel.LOW),
            ToolDefinition("ocr_extract", "Extract OCR text from image file", listOf(ToolParameter("image_uri", "string", "Image URI / file path")), RiskLevel.LOW),
            ToolDefinition("ocr_screen", "Perform OCR on screen image", listOf(ToolParameter("image_uri", "string", "Image URI", false)), RiskLevel.LOW),
            ToolDefinition("scan_qr", "Scan QR code or barcode from image", listOf(ToolParameter("image_uri", "string", "Image URI / file path")), RiskLevel.LOW),
            ToolDefinition("analyze_image", "Analyze image contents using ML Kit", listOf(ToolParameter("image_uri", "string", "Image URI")), RiskLevel.LOW),

            // Macros & Missions
            ToolDefinition("create_macro", "Create a new automation macro", listOf(ToolParameter("name", "string", "Macro name"), ToolParameter("trigger", "string", "Trigger voice phrase")), RiskLevel.LOW),
            ToolDefinition("run_macro", "Run an automation macro", listOf(ToolParameter("macro_id", "string", "Macro ID")), RiskLevel.LOW),
            ToolDefinition("execute_macro", "Execute a configured macro", listOf(ToolParameter("macro_id", "string", "Macro ID")), RiskLevel.LOW),
            ToolDefinition("list_macros", "List all created macros", emptyList(), RiskLevel.LOW),
            ToolDefinition("toggle_macro", "Enable or disable a macro", listOf(ToolParameter("macro_id", "string", "Macro ID")), RiskLevel.LOW),
            ToolDefinition("delete_macro", "Delete an automation macro", listOf(ToolParameter("macro_id", "string", "Macro ID")), RiskLevel.MEDIUM),
            ToolDefinition("run_mission", "Run a complex multi-step mission", listOf(ToolParameter("mission_id", "string", "Mission ID")), RiskLevel.HIGH)
        )
        tools.forEach { toolRegistry[it.name] = it }
    }
}
