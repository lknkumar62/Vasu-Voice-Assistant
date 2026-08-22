package com.vasu.ai.core

import android.content.Context
import java.io.IOException

class VasuWakeWordModelValidator(context: Context) {
    private val appContext = context.applicationContext

    fun validate(assetPath: String): VasuWakeWordReadiness {
        return try {
            appContext.assets.open(assetPath).use { input ->
                val available = input.available()
                if (available <= 0) {
                    VasuWakeWordReadiness.MODEL_INVALID
                } else {
                    VasuWakeWordReadiness.READY
                }
            }
        } catch (_: IOException) {
            VasuWakeWordReadiness.MODEL_MISSING
        } catch (_: SecurityException) {
            VasuWakeWordReadiness.MODEL_INVALID
        } catch (_: Throwable) {
            VasuWakeWordReadiness.MODEL_INVALID
        }
    }
}
