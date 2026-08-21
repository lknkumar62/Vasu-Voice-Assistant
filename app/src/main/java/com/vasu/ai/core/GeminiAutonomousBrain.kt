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
            var reply = ""
            var geminiProducedResult = false

            for (stepIndex in 0 until MAX_GEMINI_STEPS) {
                val plan = api.plan(trimmed, screenContext(lastExecution))
                if (plan == null) {
                    if (lastExecution != null) {
                        return Result(
                            true,
                            reply.ifBlank { "Boss, task execute ho gaya, lekin final verification nahi mil paya." },
                            lastExecution,
                            true
                        )
                    }
                    break
                }

                geminiProducedResult = true
                reply = plan.reply.ifBlank { reply }

                // An empty step list is Gemini's explicit signal that no more UI action is needed.
                if (plan.steps.isEmpty()) {
                    if (reply.isNotBlank()) return Result(true, reply, lastExecution, true)
                    if (lastExecution != null) {
                        return Result(true, "Boss, task complete ho gaya.", lastExecution, true)
                    }
                    break
                }

                // Execute exactly one validated action, then request a fresh screen snapshot.
                // This prevents stale multi-action plans from blindly running against a changed UI.
                val validation = validator.validate(listOf(plan.steps.first()), trimmed)
                if (validation.rejectedCount > 0 || validation.actions.isEmpty()) {
                    return Result(false, "Boss, Gemini ne koi safe Android action nahi diya.", lastExecution, true)
                }

                val execution = executionEngine.execute(validation.actions)
                lastExecution = execution
                if (!execution.success) {
                    return Result(false, "Boss, ye step execute nahi hua. Permission ya Android/app restriction ho sakti hai.", execution, true)
                }

                // Allow the target app enough time to publish a fresh accessibility hierarchy
                // before Gemini receives the next screen snapshot.
                Thread.sleep(UI_SETTLE_DELAY_MS)

                if (stepIndex == MAX_GEMINI_STEPS - 1) {
                    return Result(
                        true,
                        reply.ifBlank { "Boss, task execute hua, lekin maximum autonomous steps limit ho gayi." },
                        lastExecution,
                        true
                    )
                }
            }

            // Never run the local fallback after Gemini has already executed an action:
            // doing so could repeat calls, SMS, taps, or other side effects.
            if (geminiProducedResult && lastExecution != null) {
                return Result(
                    true,
                    reply.ifBlank { "Boss, task execute ho gaya, lekin final verification complete nahi ho paya." },
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
        }
    }

    private companion object {
        const val MAX_GEMINI_STEPS = 8
        const val UI_SETTLE_DELAY_MS = 350L
    }
}
