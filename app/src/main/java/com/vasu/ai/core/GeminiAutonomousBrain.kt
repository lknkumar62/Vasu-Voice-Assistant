package com.vasu.ai.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vasu.ai.accessibility.VasuAccessibilityService

class GeminiAutonomousBrain(context: Context) {

    data class Result(
        val handled: Boolean,
        val reply: String,
        val execution: VasuExecutionEngine.ExecutionResult? = null,
        val usedGemini: Boolean = false
    )

    private val appResolver = VasuAppResolver(context)
    private val keyStore = GeminiKeyStore(context)
    private val api = GeminiApiClient(keyStore::read)
    private val validator = GeminiActionValidator(appResolver)
    private val executor = VasuActionExecutor(context)
    private val executionEngine = VasuExecutionEngine(executor)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /**
     * Online requests are planned by Gemini against the current screen. After every
     * successful UI action the planner receives fresh screen context and decides the
     * next step. Basic local planning remains the offline fallback.
     */
    fun handle(command: String): Result {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return Result(false, "Command samajh nahi aaya.")

        if (isOnline() && keyStore.read().isNullOrBlank().not()) {
            var lastExecution: VasuExecutionEngine.ExecutionResult? = null
            var reply = ""

            repeat(MAX_GEMINI_STEPS) {
                val plan = api.plan(trimmed, screenContext(lastExecution)) ?: return@repeat
                reply = plan.reply.ifBlank { reply }

                if (plan.steps.isEmpty()) {
                    if (reply.isNotBlank()) return Result(true, reply, lastExecution, true)
                    return@repeat
                }

                val validation = validator.validate(listOf(plan.steps.first()))
                if (validation.rejectedCount > 0 || validation.actions.isEmpty()) {
                    return Result(false, "Boss, Gemini ne koi safe Android action nahi diya.", lastExecution, true)
                }

                val execution = executionEngine.execute(validation.actions)
                lastExecution = execution
                if (!execution.success) {
                    return Result(true, "Boss, ye step execute nahi hua. Permission ya Android/app restriction ho sakti hai.", execution, true)
                }

                if (reply.isNotBlank() && plan.steps.size == 1) {
                    return Result(true, reply, execution, true)
                }
            }

            if (lastExecution != null) {
                return Result(true, reply.ifBlank { "Boss, task poora verify nahi ho paya." }, lastExecution, true)
            }
        }

        return localFallback(trimmed)
    }

    fun saveGeminiApiKey(apiKey: String) = keyStore.save(apiKey.trim())

    fun clearGeminiApiKey() = keyStore.clear()

    private fun localFallback(command: String): Result {
        val actions = VasuCommandPlanner().plan(command) { appResolver.resolve(it) }
        if (actions.isNullOrEmpty()) {
            return if (isOnline()) {
                Result(false, "Boss, Gemini API response nahi de saka ya valid action nahi mila.")
            } else {
                Result(false, "Internet nahi hai Boss, lekin main offline-capable phone commands kar sakta hoon.")
            }
        }
        val execution = executionEngine.execute(actions)
        return if (execution.success) {
            Result(true, "Ho gaya Boss.", execution, false)
        } else {
            Result(true, "Boss, command execute nahi ho paya. Required permission ya app restriction check kijiye.", execution, false)
        }
    }

    private fun isOnline(): Boolean {
        val active = connectivity?.activeNetwork ?: return false
        val capabilities = connectivity?.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun screenContext(lastExecution: VasuExecutionEngine.ExecutionResult?): String {
        val service = VasuAccessibilityService.instance ?: return "Accessibility service unavailable."
        val root = service.root() ?: return "No readable foreground window."
        val packageName = service.foregroundPackage() ?: "unknown"
        val texts = buildList {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
                val text = node.text?.toString()?.trim().orEmpty()
                val description = node.contentDescription?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) add(text.take(160))
                if (description.isNotBlank()) add(description.take(160))
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child)
                }
            }
            visit(root)
        }.distinct().take(100)

        return buildString {
            append("foreground_package=").append(packageName).append('\n')
            append("visible_text=\n")
            texts.forEach { append("- ").append(it).append('\n') }
            lastExecution?.let {
                append("previous_step_success=").append(it.success).append('\n')
                append("previous_completed_count=").append(it.completedCount).append('\n')
            }
        }
    }

    private companion object {
        const val MAX_GEMINI_STEPS = 8
    }
}
