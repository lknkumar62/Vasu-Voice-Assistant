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
     * Gemini is preferred for every online request. Local planning remains the
     * safety/offline fallback so the phone still performs basic commands without internet.
     */
    fun handle(command: String): Result {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return Result(false, "Command samajh nahi aaya.")

        if (isOnline()) {
            val context = screenContext()
            val plan = api.plan(trimmed, context)
            if (plan != null) {
                val validation = validator.validate(plan.steps)
                if (validation.rejectedCount == 0 && validation.actions.isNotEmpty()) {
                    val execution = executionEngine.execute(validation.actions)
                    return if (execution.success) {
                        Result(true, plan.reply.ifBlank { "Ho gaya Boss." }, execution, true)
                    } else {
                        Result(true, "Boss, action ka step complete nahi hua. Android/app restriction ho sakti hai.", execution, true)
                    }
                }
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
                Result(false, "Boss, Gemini ne is request ke liye valid Android action nahi diya.")
            } else {
                Result(false, "Internet nahi hai Boss, lekin main sirf offline-capable phone commands kar sakta hoon.")
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

    private fun screenContext(): String {
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
        }.distinct().take(80)

        return buildString {
            append("foreground_package=").append(packageName).append('\n')
            append("visible_text=\n")
            texts.forEach { append("- ").append(it).append('\n') }
        }
    }
}
