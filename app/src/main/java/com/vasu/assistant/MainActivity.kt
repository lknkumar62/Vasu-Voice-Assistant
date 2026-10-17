package com.vasu.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vasu.assistant.core.navigation.VasuNavGraph
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.ui.overlay.FloatingOrbService
import com.vasu.assistant.ui.theme.VasuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: VasuSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resumeWakeWordIfRequested(intent)
        resumeFloatingOrbIfEnabled()

        enableEdgeToEdge()
        setContent {
            VasuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VasuNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resumeWakeWordIfRequested(intent)
    }

    /**
     * Tapped a "tap to resume listening" notification (boot on Android 11+ /
     * call ended): mic-FGS from the background is disallowed, foreground is
     * fine. Runs from onCreate and onNewIntent because the activity is
     * singleTop - a live task delivers the tap to onNewIntent.
     */
    private fun resumeWakeWordIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(VasuForegroundService.EXTRA_RESUME_WAKE_WORD, false) == true &&
            settings.wakeWordEnabled.value
        ) {
            VasuForegroundService.start(this)
        }
    }

    /** FIX-9: restore the floating orb when the app is opened and the toggle is on. */
    private fun resumeFloatingOrbIfEnabled() {
        if (settings.floatingOrbEnabled.value) {
            FloatingOrbService.start(this)
        }
    }
}
