package com.vasu.assistant.core.orchestrator

import android.content.Context
import com.vasu.assistant.core.commands.IntentParser
import com.vasu.assistant.core.commands.ParsedCommand
import com.vasu.assistant.core.error.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intentParser: IntentParser
) {
    fun route(userInput: String): ActionResult<String> {
        return try {
            val command = intentParser.parseCommand(userInput)
            when (command) {
                is ParsedCommand.Call -> ActionResult.Success("Routing to call: ${command.contactName}")
                is ParsedCommand.Message -> ActionResult.Success("Routing to message: ${command.contactName}")
                is ParsedCommand.DeviceControl -> ActionResult.Success("Routing device control: ${command.device}")
                is ParsedCommand.Location -> ActionResult.Success("Routing to location: ${command.query}")
                is ParsedCommand.File -> ActionResult.Success("Routing to file: ${command.path}")
                is ParsedCommand.Camera -> ActionResult.Success("Routing to camera: ${command.type}")
                is ParsedCommand.Unknown -> ActionResult.Error("Could not understand command: $userInput")
            }
        } catch (e: Exception) {
            ActionResult.Error("Error routing command: ${e.message}")
        }
    }
}
