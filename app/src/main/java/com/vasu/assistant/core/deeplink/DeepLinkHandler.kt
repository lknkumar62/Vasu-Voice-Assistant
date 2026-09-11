package com.vasu.assistant.core.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object DeepLinkHandler {

    private const val TAG = "DeepLinkHandler"

    sealed class DeepLink {
        data class Screen(val route: String) : DeepLink()
        data class Action(val action: String, val params: Map<String, String> = emptyMap()) : DeepLink()
        data class VoiceCommand(val command: String) : DeepLink()
        data class OpenApp(val packageName: String) : DeepLink()
        data class Share(val text: String, val url: String? = null) : DeepLink()
        object Assistant : DeepLink()
        object Unknown : DeepLink()
    }

    fun handleIntent(context: Context, intent: Intent): DeepLink {
        val deepLink = parseIntent(intent)
        processDeepLink(context, deepLink)
        return deepLink
    }

    fun parseIntent(intent: Intent): DeepLink {
        // Check for open_route extra
        val openRoute = intent.getStringExtra(EXTRA_OPEN_ROUTE)
        if (!openRoute.isNullOrEmpty()) {
            Log.d(TAG, "Deep link: open_route=$openRoute")
            return DeepLink.Screen(openRoute)
        }

        // Check for voice command
        val voiceCommand = intent.getStringExtra(EXTRA_VOICE_COMMAND)
        if (!voiceCommand.isNullOrEmpty()) {
            Log.d(TAG, "Deep link: voice_command=$voiceCommand")
            return DeepLink.VoiceCommand(voiceCommand)
        }

        // Check for URI data
        val data: Uri? = intent.data
        if (data != null) {
            return parseUri(data)
        }

        // Check for ACTION_ASSIST or VOICE_COMMAND
        when (intent.action) {
            Intent.ACTION_ASSIST,
            "android.intent.action.VOICE_COMMAND" -> {
                Log.d(TAG, "Deep link: assistant trigger")
                return DeepLink.Assistant
            }
            Intent.ACTION_VIEW -> {
                val viewData = intent.data
                if (viewData != null) {
                    return parseUri(viewData)
                }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val url = intent.getStringExtra(Intent.EXTRA_STREAM)?.toString()
                return DeepLink.Share(text, url)
            }
        }

        return DeepLink.Unknown
    }

    private fun parseUri(uri: Uri): DeepLink {
        val scheme = uri.scheme ?: ""
        val host = uri.host ?: ""
        val path = uri.path?.trimStart('/') ?: ""

        Log.d(TAG, "Parsing URI: scheme=$scheme, host=$host, path=$path")

        return when (scheme) {
            SCHEME_VASU -> {
                when (host) {
                    "screen" -> DeepLink.Screen(path)
                    "action" -> {
                        val params = mutableMapOf<String, String>()
                        uri.queryParameterNames.forEach { key ->
                            uri.getQueryParameter(key)?.let { params[key] = it }
                        }
                        DeepLink.Action(path, params)
                    }
                    "voice" -> DeepLink.VoiceCommand(path)
                    "app" -> DeepLink.OpenApp(path)
                    "assistant" -> DeepLink.Assistant
                    else -> DeepLink.Screen(path.ifEmpty { "home" })
                }
            }
            "mailto" -> {
                DeepLink.Action("compose_email", mapOf("to" to host))
            }
            "http", "https" -> {
                DeepLink.Action("open_url", mapOf("url" to uri.toString()))
            }
            else -> DeepLink.Unknown
        }
    }

    private fun processDeepLink(context: Context, deepLink: DeepLink) {
        when (deepLink) {
            is DeepLink.Screen -> {
                Log.d(TAG, "Navigate to screen: ${deepLink.route}")
                navigateToScreen(context, deepLink.route)
            }
            is DeepLink.Action -> {
                Log.d(TAG, "Execute action: ${deepLink.action}")
                executeAction(context, deepLink.action, deepLink.params)
            }
            is DeepLink.VoiceCommand -> {
                Log.d(TAG, "Voice command: ${deepLink.command}")
                processVoiceCommand(context, deepLink.command)
            }
            is DeepLink.OpenApp -> {
                Log.d(TAG, "Open app: ${deepLink.packageName}")
                openApp(context, deepLink.packageName)
            }
            is DeepLink.Share -> {
                Log.d(TAG, "Share: ${deepLink.text}")
                handleShare(context, deepLink.text, deepLink.url)
            }
            is DeepLink.Assistant -> {
                Log.d(TAG, "Launch assistant overlay")
                launchAssistant(context)
            }
            is DeepLink.Unknown -> {
                Log.w(TAG, "Unknown deep link")
            }
        }
    }

    private fun navigateToScreen(context: Context, route: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$SCHEME_VASU://screen/$route")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun executeAction(context: Context, action: String, params: Map<String, String>) {
        when (action) {
            "compose_email" -> {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${params["to"] ?: ""}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(emailIntent)
            }
            "open_url" -> {
                val url = params["url"] ?: return
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
            "call" -> {
                val number = params["number"] ?: return
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
            }
            "sms" -> {
                val number = params["number"] ?: return
                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                    putExtra("sms_body", params["message"] ?: "")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    private fun processVoiceCommand(context: Context, command: String) {
        val intent = Intent("com.vasu.assistant.VOICE_COMMAND").apply {
            putExtra("command", command)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.sendBroadcast(intent)
    }

    private fun openApp(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Open Play Store
            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playIntent)
        }
    }

    private fun handleShare(context: Context, text: String, url: String?) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, if (url != null) "$text\n$url" else text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via VASU"))
    }

    private fun launchAssistant(context: Context) {
        val intent = Intent("com.vasu.assistant.ASSIST").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    // Build deep link URIs
    fun buildUri(route: String): Uri = Uri.parse("$SCHEME_VASU://screen/$route")
    fun buildActionUri(action: String, params: Map<String, String> = emptyMap()): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME_VASU)
            .authority("action")
            .appendPath(action)
        params.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build()
    }

    const val EXTRA_OPEN_ROUTE = "open_route"
    const val EXTRA_VOICE_COMMAND = "command"
    const val SCHEME_VASU = "vasu"
}
