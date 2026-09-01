package com.vasu.assistant.ui.tools

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor() : ViewModel() {
    val tools = listOf(
        ToolItem("Call", "Make phone calls", "call"),
        ToolItem("Message", "Send SMS and WhatsApp", "message"),
        ToolItem("Device Control", "Control torch, volume, media", "device"),
        ToolItem("Location", "Find location and maps", "location"),
        ToolItem("Files", "Browse and manage files", "files"),
        ToolItem("Camera", "Take photos and videos", "camera"),
        ToolItem("Settings", "Configure app settings", "settings"),
        ToolItem("Missions", "Create automated missions", "missions")
    )
}

data class ToolItem(
    val name: String,
    val description: String,
    val id: String
)
