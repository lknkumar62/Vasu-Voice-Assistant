package com.vasu.ai.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.vasu.ai.accessibility.VasuAccessibilityService
import com.vasu.ai.notification.VasuNotificationListener
import java.util.concurrent.Executors

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
    private val dynamicReplanner = VasuDynamicReplanner()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun handleAsync(command: String, callback: (Result) -> Unit) {
        worker.execute {
            val result = handle(command)
            main.post { callback(result) }
        }
    }

    fun handle(command: String): Result {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return Result(false, "Command samajh nahi aaya.")

        if (isOnline() && !keyStore.read().isNullOrBlank()) {
            var lastExecution: VasuExecutionEngine.ExecutionResult? = null
            var lastReply = ""
            var geminiProducedResult = false

            for (stepIndex in 0 until MAX_GEMINI_STEPS) {
                val context = if (lastExecution != null &&
                    lastExecution.steps.any { !it.success }
                ) {
                    val recovery = dynamicReplanner.capture(
                        originalCommand = trimmed,
                        execution = lastExecution
                    )
                    buildString {
                        append(screenContext(lastExecution))
                        append("\n\n")
                        append(recovery.asPromptContext())
                    }
                } else {
                    screenContext(lastExecution)
                }

                val plan = api.plan(trimmed, context) ?: break
                geminiProducedResult = true
                lastReply = plan.reply.ifBlank { lastReply }

                // Completion is accepted only after Gemini observes a fresh post-action context.
                // A plan that contains an action is never allowed to finish the task immediately.
                if (plan.steps.isEmpty()) {
                    if (plan.done) {
                        return Result(true, lastReply.ifBlank { "Ho gaya Boss." }, lastExecution, true)
                    }
                    return Result(false, lastReply.ifBlank { "Boss, completion verify nahi hua." }, lastExecution, true)
                }

                val validation = validator.validate(listOf(plan.steps.first()), trimmed)
                if (validation.rejectedCount > 0 || validation.actions.isEmpty()) {
                    return Result(false, "Boss, Gemini ne koi safe Android action nahi diya.", lastExecution, true)
                }

                val action = validation.actions.first()

                if (stepIndex > 0 && isSensitiveSideEffect(action)) {
                    return Result(
                        false,
                        "Boss, sensitive action ko automatic recovery ke liye repeat nahi kiya gaya.",
                        lastExecution,
                        true
                    )
                }

                val execution = executionEngine.execute(listOf(action))
                lastExecution = execution

                if (execution.success) {
                    Thread.sleep(UI_SETTLE_DELAY_MS)
                    continue
                }

                println(
                    "VASU_DYNAMIC_REPLAN " +
                        "step=$stepIndex " +
                        "failedAction=${execution.failedAction}"
                )

                Thread.sleep(RECOVERY_SETTLE_DELAY_MS)
            }

            if (geminiProducedResult) {
                return Result(
                    false,
                    lastReply.ifBlank { "Boss, task partially execute hua, lekin completion verify nahi hua." },
                    lastExecution,
                    true
                )
            }
        }
        return localFallback(trimmed)
    }

    fun saveGeminiApiKey(apiKey: String) = keyStore.save(apiKey.trim())
    fun clearGeminiApiKey() = keyStore.clear()

    private fun localFallback(command: String): Result {
        val actions = VasuCommandPlanner().plan(command) { appResolver.resolve(it) }
        if (actions.isNullOrEmpty()) {
            return if (isOnline()) Result(false, "Boss, Gemini API response nahi de saka ya valid action nahi mila.")
            else Result(false, "Internet nahi hai Boss, lekin main offline-capable phone commands kar sakta hoon.")
        }
        val execution = executionEngine.execute(actions)
        return if (execution.success) Result(true, "Ho gaya Boss.", execution, false)
        else Result(false, "Boss, command execute nahi ho paya. Required permission ya app restriction check kijiye.", execution, false)
    }

    private fun isSensitiveSideEffect(action: VasuAction): Boolean = when (action) {
        is VasuAction.CallContact,
        is VasuAction.SendSms -> true
        else -> false
    }

    private fun isOnline(): Boolean {
        val active = connectivity?.activeNetwork ?: return false
        val capabilities = connectivity?.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun screenContext(lastExecution: VasuExecutionEngine.ExecutionResult?): String = buildString {
        val service = VasuAccessibilityService.instance
        if (service == null) append("accessibility_service=unavailable\n")
        else {
            append("foreground_package=").append(service.foregroundPackage() ?: "unknown").append('\n')
            append("visible_screen=\n").append(service.describeScreen(120))
        }
        val notifications = VasuNotificationListener.recent(10)
        if (notifications.isNotEmpty()) {
            append("recent_notifications=\n")
            notifications.forEach { append("- [").append(it.packageName).append("] ").append(it.title).append(": ").append(it.text).append('\n') }
        }
        lastExecution?.let {
            append("previous_step_success=").append(it.success).append('\n')
            append("previous_completed_count=").append(it.completedCount).append('\n')
            append("previous_recovered_count=").append(it.recoveredCount).append('\n')
            append("previous_total_attempts=").append(it.totalAttempts).append('\n')
            it.steps.lastOrNull()?.let { step ->
                append("previous_action=").append(step.action).append('\n')
                append("previous_action_attempts=").append(step.attempts).append('\n')
                append("previous_action_recovered=").append(step.recovered).append('\n')
            }
        }
    }

    private companion object {
        const val MAX_GEMINI_STEPS = 8
        const val UI_SETTLE_DELAY_MS = 350L
        const val RECOVERY_SETTLE_DELAY_MS = 200L
    }
}
