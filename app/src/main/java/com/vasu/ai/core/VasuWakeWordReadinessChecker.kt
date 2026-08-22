package com.vasu.ai.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class VasuWakeWordReadinessChecker(
    context: Context,
    private val config: VasuWakeWordConfig = VasuWakeWordConfig(),
    private val keyStore: VasuWakeWordKeyStore = VasuWakeWordKeyStore(context)
) {
    private val appContext = context.applicationContext
    private val modelValidator = VasuWakeWordModelValidator(appContext)

    fun check(): VasuWakeWordReadiness {
        if (!config.enabled) {
            println("VASU_WAKEWORD_READINESS disabled")
            return VasuWakeWordReadiness.DISABLED
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            println("VASU_WAKEWORD_READINESS permission_missing")
            return VasuWakeWordReadiness.CONFIGURATION_ERROR
        }

        if (!keyStore.hasAccessKey()) {
            println("VASU_WAKEWORD_ACCESS_KEY_MISSING")
            return VasuWakeWordReadiness.ACCESS_KEY_MISSING
        }

        return when (val modelState = modelValidator.validate(config.keywordAssetPath)) {
            VasuWakeWordReadiness.READY -> {
                println("VASU_WAKEWORD_READY")
                VasuWakeWordReadiness.READY
            }
            VasuWakeWordReadiness.MODEL_MISSING -> {
                println("VASU_WAKEWORD_MODEL_MISSING")
                modelState
            }
            VasuWakeWordReadiness.MODEL_INVALID -> {
                println("VASU_WAKEWORD_MODEL_INVALID")
                modelState
            }
            else -> VasuWakeWordReadiness.CONFIGURATION_ERROR
        }
    }
}
