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

    private val conversationStore = VasuConversationContextStore()
    private val conversationStateMachine = VasuConversationStateMachine(conversationStore)
    private val followUpResolver = VasuFollowUpResolver(conversationStore)
    private val referenceResolver = VasuReferenceResolver(conversationStore)
    private val contextMapper = VasuGeminiConversationContextMapper()
    private val contextPromptBuilder = VasuGeminiContextPromptBuilder()

    fun handleAsync(command: String, callback: (Result) -> Unit) {
        worker.execute {
            val result = handle(command)
            main.post { callback(result) }
        }
    }

    fun handle(command: String): Result {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return Result(false, "Command samajh nahi aaya.")

        val referenceResult = referenceResolver.resolve(trimmed)
        val resolution = followUpResolver.resolve(trimmed)
        val effectiveCommand = resolution.resolvedCommand
        val boundedContext = contextMapper.map(resolution.context)
        val contextPrompt = contextPromptBuilder.build(
            command = effectiveCommand,
            context = boundedContext,
            isFollowUp = resolution.isFollowUp,
            reference = referenceResult.reference
        )

        println(
            "VASU_GEMINI_REFERENCE " +
                "type=${referenceResult.reference.type} " +
                "freshUi=${referenceResult.reference.requiresFreshUiEvidence}"
        )

        conversationStateMachine.startProcessing()
        conversationStore.updateUserCommand(trimmed)
        conversationStore.addTurn(
            VasuConversationTurn(trimmed, timestampMs = System.currentTimeMillis())
        )

        if (isOnline() && !keyStore.read().isNullOrBlank()) {
            var lastExecution: VasuExecutionEngine.ExecutionResult? = null
            var lastReply = ""
            var geminiProducedResult = false
            conversationStateMachine.startExecuting()

            for (stepIndex in 0 until MAX_GEMINI_STEPS) {
                val screen = screenContext(lastExecution)
                val context = if (lastExecution != null && lastExecution.steps.any { !it.success }) {
                    val recovery = dynamicReplanner.capture(effectiveCommand, lastExecution)
                    buildString {
                        append(screen)
                        append("\n\n")
                        append(recovery.asPromptContext())
                    }
                } else {
                    buildString {
                        append(screen)
                        if (resolution.isFollowUp || referenceResult.reference.type != VasuReferenceType.NONE) {
                            append("\n\nCONVERSATION CONTEXT:\n")
                            append(contextPrompt)
                        }
                    }
                }

                val plan = api.plan(effectiveCommand, context) ?: break
                geminiProducedResult = true
                lastReply = plan.reply.ifBlank { lastReply }

                if (plan.steps.isEmpty()) {
                    if (plan.done) {
                        val reply = lastReply.ifBlank { "Ho gaya Boss." }
                        conversationStore.updateAssistantResponse(reply)
                        conversationStore.addTurn(VasuConversationTurn(trimmed, reply, System.currentTimeMillis(), true))
                        conversationStateMachine.complete()
                        return Result(true, reply, lastExecution, true)
                    }
                    conversationStateMachine.fail()
                    return finalizeFailure(trimmed, lastReply.ifBlank { "Boss, completion verify nahi hua." }, lastExecution, true)
                }

                val validation = validator.validate(listOf(plan.steps.first()), effectiveCommand)
                if (validation.rejectedCount > 0 || validation.actions.isEmpty()) {
                    conversationStateMachine.fail()
                    return finalizeFailure(trimmed, "Boss, Gemini ne koi safe Android action nahi diya.", lastExecution, true)
                }

                val action = validation.actions.first()
                if (referenceResult.reference.requiresFreshUiEvidence) {
                    println("VASU_REFERENCE_EXECUTION_GUARD reference=${referenceResult.reference.type} action=${action::class.simpleName}")
                }

                if (stepIndex > 0 && isSensitiveSideEffect(action)) {
                    conversationStateMachine.fail()
                    return finalizeFailure(trimmed, "Boss, sensitive action ko automatic recovery ke liye repeat nahi kiya gaya.", lastExecution, true)
                }

                val execution = executionEngine.execute(listOf(action))
                lastExecution = execution

                if (execution.success) {
                    conversationStore.updateLastAction(action::class.simpleName, true)
                    updateActiveAppFromCurrentScreen(context)
                    Thread.sleep(UI_SETTLE_DELAY_MS)
                    continue
                }

                conversationStore.updateLastAction(action::class.simpleName, false)
                println("VASU_DYNAMIC_REPLAN step=$stepIndex failedAction=${execution.failedAction}")
                Thread.sleep(RECOVERY_SETTLE_DELAY_MS)
            }

            if (geminiProducedResult) {
                conversationStateMachine.waitForFollowUp()
                return finalizeFailure(trimmed, lastReply.ifBlank { "Boss, task partially execute hua, lekin completion verify nahi hua." }, lastExecution, true)
            }
        }

        conversationStateMachine.startExecuting()
        val result = localFallback(effectiveCommand)
        if (result.execution?.success == true) {
            result.execution.steps.lastOrNull()?.let { step ->
                conversationStore.updateLastAction(step.action::class.simpleName, true)
                updateActiveAppFromCurrentScreen(context)
            }
            conversationStateMachine.complete()
            conversationStore.updateAssistantResponse(result.reply)
            conversationStore.addTurn(VasuConversationTurn(trimmed, result.reply, System.currentTimeMillis(), true))
        } else {
            conversationStateMachine.fail()
        }
        return result
    }

    fun saveGeminiApiKey(apiKey: String) = keyStore.save(apiKey.trim())
    fun clearGeminiApiKey() = keyStore.clear()

    private fun finalizeFailure(originalCommand: String, reply: String, execution: VasuExecutionEngine.ExecutionResult?, usedGemini: Boolean): Result {
        conversationStore.updateAssistantResponse(reply)
        conversationStore.addTurn(VasuConversationTurn(originalCommand, reply, System.currentTimeMillis(), false))
        return Result(false, reply, execution, usedGemini)
    }

    private fun updateActiveAppFromCurrentScreen(context: Context) {
        val service = VasuAccessibilityService.instance ?: return
        val packageName = service.foregroundPackage() ?: return
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrNull()
        conversationStore.updateActiveApp(label, packageName)
    }

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
        is VasuAction.CallContact, is VasuAction.SendSms -> true
        else -> false
    }

    private fun isOnline(): Boolean {
        val active = connectivity?.activeNetwork ?: return false
        val capabilities = connectivity?.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
