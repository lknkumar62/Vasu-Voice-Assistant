package com.vasu.assistant.core.connectors

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthManager @Inject constructor() {

    private val _connectedAccounts = MutableStateFlow<Map<String, ConnectedAccount>>(emptyMap())
    val connectedAccounts: StateFlow<Map<String, ConnectedAccount>> = _connectedAccounts.asStateFlow()

    data class ConnectedAccount(
        val provider: String,
        val displayName: String,
        val email: String? = null,
        val avatarUrl: String? = null,
        val connectedAt: Long = System.currentTimeMillis(),
        val scopes: List<String> = emptyList()
    )

    fun connect(
        context: Context,
        provider: String,
        clientId: String,
        scopes: List<String> = emptyList(),
        redirectUri: String = "${OAUTH_SCHEME}://${provider}/callback"
    ): String {
        val authUrl = when (provider) {
            "google" -> buildGoogleAuthUrl(clientId, scopes, redirectUri)
            "github" -> buildGitHubAuthUrl(clientId, scopes, redirectUri)
            "discord" -> buildDiscordAuthUrl(clientId, scopes, redirectUri)
            else -> buildGenericAuthUrl(provider, clientId, scopes, redirectUri)
        }
        Log.d(TAG, "Connecting to $provider: $authUrl")
        return authUrl
    }

    private fun buildGoogleAuthUrl(clientId: String, scopes: List<String>, redirectUri: String): String {
        val scopeStr = scopes.joinToString(" ")
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=$scopeStr" +
            "&access_type=offline"
    }

    private fun buildGitHubAuthUrl(clientId: String, scopes: List<String>, redirectUri: String): String {
        val scopeStr = scopes.joinToString(" ")
        return "https://github.com/login/oauth/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&scope=$scopeStr"
    }

    private fun buildDiscordAuthUrl(clientId: String, scopes: List<String>, redirectUri: String): String {
        val scopeStr = scopes.joinToString("+")
        return "https://discord.com/api/oauth2/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=$scopeStr"
    }

    private fun buildGenericAuthUrl(provider: String, clientId: String, scopes: List<String>, redirectUri: String): String {
        return "${AUTH_BASE_URLS[provider]}/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=code" +
            "&scope=${scopes.joinToString(" ")}"
    }

    fun disconnect(context: Context, provider: String) {
        val prefs = getPrefs(context)
        prefs.edit().remove("token_$provider").apply()
        prefs.edit().remove("account_$provider").apply()
        _connectedAccounts.value = _connectedAccounts.value.toMutableMap().apply {
            remove(provider)
        }
        Log.d(TAG, "Disconnected: $provider")
    }

    fun isConnected(context: Context, provider: String): Boolean {
        return getPrefs(context).getString("token_$provider", null) != null
    }

    fun getAccount(context: Context, provider: String): ConnectedAccount? {
        return try {
            val json = getPrefs(context).getString("account_$provider, null")
            if (json == null) null
            else {
                // Simple parsing
                val parts = json.split("|")
                ConnectedAccount(
                    provider = provider,
                    displayName = parts.getOrElse(1) { provider },
                    email = parts.getOrElse(2) { null },
                    avatarUrl = parts.getOrElse(3) { null }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getProviders(): List<String> = listOf("google", "github", "discord", "spotify", "twitter")

    companion object {
        private const val TAG = "OAuthManager"
        private const val OAUTH_SCHEME = "vasu"
        private const val PREFS_NAME = "vasu_oauth"

        private val AUTH_BASE_URLS = mapOf(
            "google" to "https://accounts.google.com/o/oauth2/v2",
            "github" to "https://github.com/login/oauth",
            "discord" to "https://discord.com/api/oauth2",
            "spotify" to "https://accounts.spotify.com/authorize",
            "twitter" to "https://twitter.com/i/oauth2"
        )

        private var instance: OAuthManager? = null

        fun getInstance(): OAuthManager {
            return instance ?: OAuthManager().also { instance = it }
        }

        fun onAuthCodeReceived(provider: String, code: String, state: String, extras: Map<String, String>) {
            Log.d(TAG, "Auth code received for $provider: code=${code.take(10)}...")
            // Here you would exchange the code for a token via your backend
            // For now, store the code
            instance?.let { mgr ->
                // Token exchange would happen here via API call
                Log.d(TAG, "Token exchange needed for $provider")
            }
        }

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
