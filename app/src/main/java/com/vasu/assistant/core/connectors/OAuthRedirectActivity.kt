package com.vasu.assistant.core.connectors

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.vasu.assistant.MainActivity

class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRedirect(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val data: Uri? = intent?.data

        if (data == null) {
            Log.w(TAG, "No data in redirect")
            finish()
            return
        }

        // Check for error
        val error = data.getQueryParameter("error")
        if (!error.isNullOrEmpty()) {
            Log.w(TAG, "OAuth error: $error")
            showToast("Connect cancel ho gaya: $error")
            finish()
            return
        }

        // Extract authorization code
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state") ?: ""

        if (code.isNullOrEmpty()) {
            Log.w(TAG, "No authorization code")
            showToast("Connect adhoora reh gaya — dobara try karo")
            finish()
            return
        }

        // Extract provider from last path segment
        val pathSegments = data.pathSegments
        val provider = pathSegments?.lastOrNull() ?: ""

        // Extract any extra params
        val pickedFileIds = data.getQueryParameter("picked_file_ids")

        Log.d(TAG, "OAuth success: provider=$provider, code=${code.take(10)}..., state=$state")

        // Save token to connector manager
        val extras = mutableMapOf<String, String>()
        if (!pickedFileIds.isNullOrEmpty()) {
            extras["picked_file_ids"] = pickedFileIds
        }

        OAuthManager.onAuthCodeReceived(provider, code, state, extras)

        showToast("Connected successfully!")
        navigateToMain()
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "OAuthRedirect"
    }
}
