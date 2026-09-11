package com.vasu.assistant.core.termux

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxManager @Inject constructor() {

    private val pendingCommands = ConcurrentHashMap<Int, CompletableDeferred<CommandResult>>()
    private var nextExecId = 1

    data class CommandResult(
        val output: String,
        val error: String,
        val exitCode: Int,
        val success: Boolean = exitCode == 0
    )

    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = 30000
    ): CommandResult = withContext(Dispatchers.IO) {
        val execId = nextExecId++
        val deferred = CompletableDeferred<CommandResult>()
        pendingCommands[execId] = deferred

        Log.d(TAG, "Executing command #$execId: ${command.take(50)}...")

        // Try Termux first, fallback to direct execution
        if (isTermuxAvailable()) {
            sendToTermux(execId, command)
        } else {
            executeDirectly(execId, command)
        }

        try {
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    deferred.await()
                }
            }
        } catch (e: Exception) {
            pendingCommands.remove(execId)
            CommandResult(
                output = "",
                error = "Command timed out or failed: ${e.message}",
                exitCode = -1
            )
        }
    }

    private fun sendToTermux(execId: Int, command: String) {
        try {
            val intent = Intent(ACTION_EXECUTE).apply {
                putExtra(TermuxResultReceiver.EXTRA_EXEC_ID, execId)
                putExtra(EXTRA_COMMAND, command)
                setPackage(TERMUX_PACKAGE)
            }
            appContext?.sendBroadcast(intent)
            Log.d(TAG, "Sent command to Termux: #$execId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to Termux: ${e.message}")
            executeDirectly(execId, command)
        }
    }

    private fun executeDirectly(execId: Int, command: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()

            val result = CommandResult(output.trim(), error.trim(), exitCode)
            pendingCommands.remove(execId)
            pendingCommands[execId]?.complete(result)
            Log.d(TAG, "Command #$execId completed: exit=$exitCode")
        } catch (e: Exception) {
            val result = CommandResult("", e.message ?: "Unknown error", -1)
            pendingCommands.remove(execId)
            pendingCommands[execId]?.complete(result)
        }
    }

    fun onCommandResult(execId: Int, output: String, error: String, exitCode: Int) {
        val result = CommandResult(output, error, exitCode)
        pendingCommands.remove(execId)?.complete(result)
        Log.d(TAG, "Result received for #$execId: exit=$exitCode")
    }

    private fun isTermuxAvailable(): Boolean {
        return try {
            appContext?.packageManager?.getPackageInfo(TERMUX_PACKAGE, 0) != null
        } catch (e: Exception) {
            false
        }
    }

    fun installTermux(context: Context): String {
        return "Install Termux from F-Droid: https://f-droid.org/en/packages/com.termux/"
    }

    companion object {
        private const val TAG = "TermuxManager"
        private const val TERMUX_PACKAGE = "com.termux"
        const val ACTION_EXECUTE = "com.termux.EXECUTE_COMMAND"
        const val EXTRA_COMMAND = "command"

        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
        }

        // Allow calling from TermuxResultReceiver
        private val pendingCommandsStatic = ConcurrentHashMap<Int, CompletableDeferred<CommandResult>>()
    }
}
