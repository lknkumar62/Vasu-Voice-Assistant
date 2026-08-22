package com.vasu.ai.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException

class VasuPorcupineWakeWordDetector(
    context: Context,
    private val config: VasuWakeWordConfig = VasuWakeWordConfig(),
    private val accessKeyProvider: () -> String? = {
        VasuWakeWordKeyStore(context.applicationContext).getAccessKey()
    },
    private val onDetected: () -> Unit
) : VasuWakeWordDetector {

    private val appContext = context.applicationContext
    private val readinessChecker = VasuWakeWordReadinessChecker(appContext, config)

    @Volatile
    private var running = false

    @Volatile
    private var released = false

    private var engine: Porcupine? = null
    private var pendingFrame = ShortArray(0)
    private var frameOffset = 0
    private var lastDetectionMs = 0L

    @Synchronized
    override fun start(): Boolean {
        if (released) return false

        when (val readiness = readinessChecker.check()) {
            VasuWakeWordReadiness.DISABLED -> return false
            VasuWakeWordReadiness.ACCESS_KEY_MISSING,
            VasuWakeWordReadiness.MODEL_MISSING,
            VasuWakeWordReadiness.MODEL_INVALID,
            VasuWakeWordReadiness.CONFIGURATION_ERROR -> return false
            VasuWakeWordReadiness.READY -> Unit
        }

        if (running) return true

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            println("VASU_WAKEWORD_ENGINE_ERROR reason=permission_missing")
            return false
        }

        val accessKey = accessKeyProvider()?.trim().orEmpty()
        if (accessKey.isBlank()) {
            println("VASU_WAKEWORD_ACCESS_KEY_MISSING")
            return false
        }

        println("VASU_WAKEWORD_ENGINE_STARTING")

        return try {
            val created = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(config.keywordAssetPath)
                .setSensitivity(config.sensitivity.coerceIn(0f, 1f))
                .build(appContext)

            engine = created
            pendingFrame = ShortArray(created.frameLength)
            frameOffset = 0
            lastDetectionMs = 0L
            running = true
            println("VASU_WAKEWORD_ENGINE_RUNNING")
            true
        } catch (error: PorcupineException) {
            engine = null
            running = false
            println("VASU_WAKEWORD_ENGINE_ERROR reason=porcupine_init_failed")
            false
        } catch (_: Throwable) {
            engine = null
            running = false
            println("VASU_WAKEWORD_ENGINE_ERROR reason=engine_init_failed")
            false
        }
    }

    override fun processAudio(pcm: ShortArray, length: Int): Boolean {
        if (!running || released || engine == null || length <= 0) return false

        val safeLength = length.coerceIn(0, pcm.size)
        var cursor = 0
        var detected = false

        while (cursor < safeLength && running && !released) {
            val frameLength = pendingFrame.size
            if (frameLength <= 0) return false

            val copyCount = minOf(frameLength - frameOffset, safeLength - cursor)
            System.arraycopy(pcm, cursor, pendingFrame, frameOffset, copyCount)
            frameOffset += copyCount
            cursor += copyCount

            if (frameOffset == frameLength) {
                val keywordIndex = try {
                    engine?.process(pendingFrame) ?: -1
                } catch (_: Throwable) {
                    println("VASU_WAKEWORD_ENGINE_ERROR reason=process_failed")
                    running = false
                    -1
                }
                frameOffset = 0

                if (keywordIndex >= 0) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastDetectionMs >= config.detectionCooldownMs) {
                        lastDetectionMs = now
                        detected = true
                        println("VASU_WAKEWORD_DETECTED")
                        onDetected()
                    }
                }
            }
        }

        return detected
    }

    @Synchronized
    override fun stop() {
        if (!running) return
        println("VASU_WAKEWORD_ENGINE_STOPPING")
        running = false
        frameOffset = 0
        println("VASU_WAKEWORD_ENGINE_STOPPED")
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        stop()
        runCatching { engine?.delete() }
        engine = null
        pendingFrame = ShortArray(0)
        frameOffset = 0
    }

    override fun isRunning(): Boolean = running && !released
}
