package com.vasu.assistant.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NativeAudioPlayer - High-performance PCM audio player for Gemini Live voice output.
 *
 * Plays 24 kHz, 16-bit Mono PCM audio chunks directly through AudioTrack with
 * zero transcoding latency, precise hardware buffer draining, and immediate interruption support.
 */
@Singleton
class NativeAudioPlayer internal constructor(
    private val audioManager: AudioManager?
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    )

    constructor() : this(null as AudioManager?)

    companion object {
        private const val TAG = "NativeAudioPlayer"
        const val SAMPLE_RATE = 24000 // Gemini Live native output rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isInterrupted = AtomicBoolean(false)
    private val isDraining = AtomicBoolean(false)
    private val lock = Object()

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var totalFramesWritten: Long = 0
    private var audioFocusRequest: AudioFocusRequest? = null
    private var activeOnEndCallback: (() -> Unit)? = null

    @Synchronized
    private fun initAudioTrack(): AudioTrack? {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing previous AudioTrack: ${e.message}")
        }

        return try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = if (minBufferSize > 0) maxOf(minBufferSize * 4, 16384) else 16384

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            totalFramesWritten = 0
            track
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
            null
        }
    }

    /**
     * Enqueue a PCM chunk received from Gemini Live.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (pcmChunk.isEmpty()) return
        isInterrupted.set(false)
        audioQueue.add(pcmChunk)
        startPlaybackLoopIfNeeded()
    }

    /**
     * Signals that the model has finished sending audio chunks for this turn.
     * The player will smoothly drain the audio buffer before switching isPlaying to false.
     * Invokes optional onFinished callback once hardware audio draining completes.
     */
    fun markEndOfResponse(onFinished: (() -> Unit)? = null) {
        activeOnEndCallback = onFinished
        isDraining.set(true)
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    @Synchronized
    private fun startPlaybackLoopIfNeeded() {
        if (playbackThread != null && playbackThread!!.isAlive) {
            synchronized(lock) {
                lock.notifyAll()
            }
            return
        }

        isInterrupted.set(false)
        isDraining.set(false)
        _isPlaying.value = true

        playbackThread = Thread {
            requestAudioFocus()
            try {
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initAudioTrack()
                }

                audioTrack?.let { track ->
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                }

                while (!isInterrupted.get()) {
                    val chunk = audioQueue.poll()
                    if (chunk != null && chunk.isNotEmpty()) {
                        _isPlaying.value = true
                        val written = audioTrack?.write(chunk, 0, chunk.size) ?: 0
                        if (written > 0) {
                            totalFramesWritten += (written / 2) // 16-bit mono = 2 bytes/frame
                        }
                    } else {
                        // Queue empty, wait or drain
                        if (isDraining.get() && audioQueue.isEmpty()) {
                            drainHardwareAudio()
                            break
                        }
                        synchronized(lock) {
                            lock.wait(50)
                        }
                        if (audioQueue.isEmpty() && isDraining.get()) {
                            drainHardwareAudio()
                            break
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Audio playback thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio playback loop: ${e.message}", e)
            } finally {
                _isPlaying.value = false
                isDraining.set(false)
                abandonAudioFocus()

                val callback = activeOnEndCallback
                activeOnEndCallback = null
                try {
                    callback?.invoke()
                } catch (cbErr: Exception) {
                    Log.w(TAG, "Error in onEnd callback: ${cbErr.message}", cbErr)
                }
            }
        }.apply {
            name = "VasuGeminiAudioPlayerThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun drainHardwareAudio() {
        val track = audioTrack ?: return
        val targetFrames = totalFramesWritten
        var attempts = 0
        while (attempts < 60 && !isInterrupted.get()) {
            val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head >= targetFrames) break
            Thread.sleep(25)
            attempts++
        }
    }

    /**
     * Stop speech playback immediately and discard buffered audio.
     */
    fun stopAndFlush() {
        isInterrupted.set(true)
        isDraining.set(false)
        audioQueue.clear()

        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        }

        playbackThread?.interrupt()
        playbackThread = null
        _isPlaying.value = false
        abandonAudioFocus()

        val callback = activeOnEndCallback
        activeOnEndCallback = null
        try {
            callback?.invoke()
        } catch (cbErr: Exception) {
            Log.w(TAG, "Error in onEnd callback on interrupt: ${cbErr.message}")
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            stopAndFlush()
                        }
                    }
                    .build()
                audioFocusRequest = req
                manager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }

    /**
     * Release AudioTrack resources.
     */
    fun release() {
        stopAndFlush()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
