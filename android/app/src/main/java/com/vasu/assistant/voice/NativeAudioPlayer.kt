package com.vasu.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.vasu.assistant.config.GeminiVoiceConfig
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Rigid state machine for Native Audio Player.
 * - IDLE: Not playing any audio; microphone can be safely opened.
 * - PLAYING: Writing active PCM chunks to AudioTrack buffer.
 * - DRAINING: All chunks written; waiting for hardware AudioTrack to drain all audio samples to speaker.
 */
enum class AudioPlayerState {
    IDLE,
    PLAYING,
    DRAINING
}

/**
 * Native PCM Audio Player using Android AudioTrack.
 * Maintains a strict state machine (IDLE, PLAYING, DRAINING) and invokes 'onEnd'
 * callbacks only after actual hardware audio draining completes.
 */
open class NativeAudioPlayer(private val context: Context) {

    private val tag = "NativeAudioPlayer"

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val playerState = AtomicReference(AudioPlayerState.IDLE)
    private val isInterrupted = AtomicBoolean(false)
    private var playbackThread: Thread? = null
    private var totalFramesWritten = 0L

    private var activeOnEndCallback: (() -> Unit)? = null
    var onPlaybackEndedListener: (() -> Unit)? = null

    private val sampleRate = GeminiVoiceConfig.OUTPUT_SAMPLE_RATE // 24000 Hz
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            totalFramesWritten = 0L
            Log.d(tag, "AudioTrack initialized: 24kHz PCM mono, buffer: $bufferSize")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrack", e)
        }
    }

    fun getState(): AudioPlayerState = playerState.get()

    fun isIdle(): Boolean = playerState.get() == AudioPlayerState.IDLE

    fun isCurrentlyPlaying(): Boolean = playerState.get() != AudioPlayerState.IDLE

    fun setOnEndCallback(callback: (() -> Unit)?) {
        this.activeOnEndCallback = callback
    }

    /**
     * Enqueues incoming PCM audio chunk from Gemini Live response.
     * Optionally takes an onEnd callback that is invoked only after audio draining.
     */
    fun enqueueAudio(pcmChunk: ByteArray, onEnd: (() -> Unit)? = null) {
        if (onEnd != null) {
            this.activeOnEndCallback = onEnd
        }
        if (isInterrupted.get()) return
        audioQueue.add(pcmChunk)
        startPlaybackLoopIfNeeded()
    }

    private synchronized fun startPlaybackLoopIfNeeded() {
        if (playbackThread != null && playbackThread!!.isAlive) return

        isInterrupted.set(false)
        playerState.set(AudioPlayerState.PLAYING)

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

                var emptyQueueCount = 0
                while (!isInterrupted.get()) {
                    val chunk = audioQueue.poll()
                    if (chunk != null && chunk.isNotEmpty()) {
                        playerState.set(AudioPlayerState.PLAYING)
                        emptyQueueCount = 0
                        val written = audioTrack?.write(chunk, 0, chunk.size) ?: 0
                        if (written > 0) {
                            totalFramesWritten += (written / 2)
                        }
                    } else {
                        // Queue temporarily empty, check if playback has drained
                        Thread.sleep(20)
                        emptyQueueCount++
                        if (audioQueue.isEmpty() && emptyQueueCount > 5) {
                            // Audio chunks have finished streaming, begin DRAINING phase
                            playerState.set(AudioPlayerState.DRAINING)
                            audioTrack?.let { track ->
                                val headPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                                if (headPosition >= totalFramesWritten) {
                                    return@let
                                }
                            }
                            if (emptyQueueCount > 20) {
                                break
                            }
                        }
                    }
                }

                // Complete hardware audio draining before transitioning back to IDLE
                if (!isInterrupted.get() && audioTrack != null) {
                    playerState.set(AudioPlayerState.DRAINING)
                    val track = audioTrack!!
                    val targetFrames = totalFramesWritten
                    var waitAttempts = 0
                    while (waitAttempts < 60 && !isInterrupted.get()) {
                        val headPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        if (headPosition >= targetFrames) {
                            Log.d(tag, "Audio draining completed: head=$headPosition, totalFrames=$targetFrames")
                            break
                        }
                        Thread.sleep(25)
                        waitAttempts++
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(tag, "Playback thread interrupted")
            } catch (e: Exception) {
                Log.e(tag, "Playback loop error", e)
            } finally {
                // Transition back to IDLE state strictly after actual audio draining
                playerState.set(AudioPlayerState.IDLE)
                abandonAudioFocus()

                // Trigger onEnd callbacks
                val callback = activeOnEndCallback
                activeOnEndCallback = null
                try {
                    callback?.invoke()
                    onPlaybackEndedListener?.invoke()
                } catch (cbErr: Exception) {
                    Log.w(tag, "Error in onEnd callback", cbErr)
                }
            }
        }.apply {
            name = "VasuNativeAudioPlaybackThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Interrupts playback instantly.
     * Clears pending queue, pauses and flushes AudioTrack buffer, and resets state to IDLE.
     */
    fun interrupt() {
        Log.d(tag, "Interruption triggered: discarding audio buffer immediately")
        isInterrupted.set(true)
        playerState.set(AudioPlayerState.IDLE)
        audioQueue.clear()

        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error flushing AudioTrack", e)
        }

        playbackThread?.interrupt()
        playbackThread = null
        abandonAudioFocus()

        val callback = activeOnEndCallback
        activeOnEndCallback = null
        try {
            callback?.invoke()
            onPlaybackEndedListener?.invoke()
        } catch (cbErr: Exception) {
            Log.w(tag, "Error in onEnd callback on interrupt", cbErr)
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            interrupt()
                        }
                    }
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to abandon audio focus", e)
        }
    }

    fun release() {
        interrupt()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(tag, "Error releasing AudioTrack", e)
        }
    }
}
