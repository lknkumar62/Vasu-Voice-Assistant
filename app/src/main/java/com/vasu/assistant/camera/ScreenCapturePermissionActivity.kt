package com.vasu.assistant.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class ScreenCapturePermissionActivity : Activity() {

    private lateinit var screenCaptureManager: ScreenCaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenCaptureManager = ScreenCaptureManager()

        Log.d(TAG, "Requesting screen capture permission")
        screenCaptureManager.requestPermission(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ScreenCaptureManager.REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null) {
                val granted = screenCaptureManager.handlePermissionResult(resultCode, data, this)
                if (granted) {
                    Toast.makeText(this, "Screen capture enabled!", Toast.LENGTH_SHORT).show()
                    // Store the result for later use
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_CAPTURE_ENABLED, true)
                        .apply()
                }
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "ScreenCapturePermission"
        private const val PREFS = "vasu_screen_capture"
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"

        fun launch(context: Context) {
            val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun isCaptureEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_CAPTURE_ENABLED, false)
        }
    }
}
