package com.vasu.assistant.core.offline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * FIX-8 Pass B: thin wrapper around Vosk (`Model` + `Recognizer` + AudioRecord).
 *
 * - One session at a time; [start] only schedules the session thread, so the
 *   caller (main thread) never blocks on the ~40 MB model build. Failures after
 *   `start() == true` surface via [Listener.onError].
 * - The loaded [Model] stays cached between sessions (keyed by path) so
 *   repeated commands don't rebuild the graph. It is released with the process;
 *   a path change closes the old one.
 * - Silence watchdog: when no partial text has arrived for `silenceTimeoutMs`,
 *   the turn is finalized via `finalResult` (blank = nothing was said).
 * - `stop(flush = true)` emits the pending text as final (manual turn end);
 *   `stop(flush = false)` discards it (cancel / teardown).
 */
@Singleton
class VoskRecognizerEngine @Inject constructor() {

    interface Listener {
        fun onPartial(text: String)
        fun onRms(level: Float)
        /** Final text for the turn; blank when silence ended an empty turn. */
        fun onFinal(text: String)
        /** Session failed after [start] returned true. */
        fun onError(message: String)
    }

    private val lock = Any()
    private val modelLock = Any()
    private var sessionThread: Thread? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var cachedModel: Model? = null
    private var cachedModelPath: String? = null

    @Volatile private var running = false
    @Volatile private var flushOnStop = false
    @Volatile private var sessionEpoch = 0

    val isActive: Boolean get() = running

    /**
     * Starts a recognition session. Returns false only when a session is
     * already running; late failures arrive via [Listener.onError].
     * Caller must hold RECORD_AUDIO permission (STTManager checks first).
     */
    fun start(modelDir: File, silenceTimeoutMs: Long, listener: Listener): Boolean {
        synchronized(lock) {
            if (running) return false
            running = true
            flushOnStop = false
            val epoch = ++sessionEpoch
            sessionThread = thread(name = "VoskEngine") {
                runSession(epoch, modelDir, silenceTimeoutMs.coerceAtLeast(1500L), listener)
            }
            return true
        }
    }

    /** Ends the session (joins the worker briefly). [flush] emits pending text. */
    fun stop(flush: Boolean) {
        flushOnStop = flush
        val t: Thread?
        synchronized(lock) {
            if (!running) {
                flushOnStop = false
                return
            }
            running = false
            t = sessionThread
            try {
                audioRecord?.stop()
            } catch (_: Throwable) {
            }
        }
        try {
            t?.join(2500)
        } catch (_: InterruptedException) {
        }
        flushOnStop = false
    }

    // ---------------------------------------------------------------- session

    /** RECORD_AUDIO is guaranteed by the caller (STTManager) before start(). */
    @SuppressLint("MissingPermission")
    private fun runSession(epoch: Int, modelDir: File, silenceTimeoutMs: Long, listener: Listener) {
        var rec: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            val model = modelFor(modelDir.absolutePath)
            rec = Recognizer(model, SAMPLE_RATE.toFloat())
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) throw IllegalStateException("AudioRecord unavailable for 16 kHz PCM")
            ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, CHUNK_BYTES * 4)
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord init failed")
            }
            synchronized(lock) {
                // A stop() whose join timed out must not let this stale worker
                // clobber a newer session's fields.
                if (!running || epoch != sessionEpoch) return
                recognizer = rec
                audioRecord = ar
            }
            ar.startRecording()
            readLoop(epoch, rec, ar, silenceTimeoutMs, listener)
        } catch (t: Throwable) {
            Log.e(TAG, "Vosk session failed: ${t.message}", t)
            listener.onError(t.message ?: "Offline recognition failed")
        } finally {
            synchronized(lock) {
                if (epoch == sessionEpoch) {
                    recognizer = null
                    audioRecord = null
                    sessionThread = null
                    running = false
                }
            }
            try {
                ar?.stop()
            } catch (_: Throwable) {
            }
            try {
                ar?.release()
            } catch (_: Throwable) {
            }
            try {
                rec?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun readLoop(epoch: Int, rec: Recognizer, ar: AudioRecord, silenceTimeoutMs: Long, listener: Listener) {
        val buffer = ByteArray(CHUNK_BYTES)
        var lastSpeechAt = SystemClock.elapsedRealtime()
        var readFailed = false
        while (running && epoch == sessionEpoch) {
            val n = ar.read(buffer, 0, buffer.size)
            if (n <= 0) {
                if (n < 0) {
                    readFailed = true
                    break
                }
                continue
            }
            listener.onRms(rmsOf(buffer, n))
            if (rec.acceptWaveForm(buffer, n)) {
                val text = textOf(rec.result, "text")
                if (text.isNotBlank()) {
                    listener.onFinal(text)
                    return
                }
                lastSpeechAt = SystemClock.elapsedRealtime()
            } else {
                val partial = textOf(rec.partialResult, "partial")
                if (partial.isNotBlank()) {
                    listener.onPartial(partial)
                    lastSpeechAt = SystemClock.elapsedRealtime()
                }
            }
            if (SystemClock.elapsedRealtime() - lastSpeechAt >= silenceTimeoutMs) {
                // Blank text = nothing was said; STTManager maps that to NO_SPEECH.
                listener.onFinal(textOf(rec.finalResult, "text"))
                return
            }
        }
        if (!running) {
            // stop() ended the loop; a manual stop flushes whatever was pending.
            // Natural ends (endpoint/silence) returned early above.
            if (flushOnStop) {
                val text = textOf(rec.finalResult, "text")
                if (text.isNotBlank()) listener.onFinal(text)
            }
        } else if (readFailed) {
            // AudioRecord failed mid-turn → surface it so mic/wake are released.
            listener.onError("Audio read failed")
        }
    }

    private fun modelFor(path: String): Model {
        synchronized(modelLock) {
            val cached = cachedModel
            if (cached != null && cachedModelPath == path) return cached
            try {
                cached?.close()
            } catch (_: Throwable) {
            }
            val model = Model(path)
            cachedModel = model
            cachedModelPath = path
            return model
        }
    }

    private fun rmsOf(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = (((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()) / 32768.0
            sum += sample * sample
            i += 2
        }
        val samples = length / 2
        return if (samples > 0) sqrt(sum / samples).toFloat() else 0f
    }

    private fun textOf(json: String, key: String): String = try {
        JSONObject(json).optString(key, "").trim()
    } catch (_: Throwable) {
        ""
    }

    companion object {
        private const val TAG = "VoskEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_BYTES = 4096
    }
}
