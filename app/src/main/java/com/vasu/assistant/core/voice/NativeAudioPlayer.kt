package com.vasu.assistant.core.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NativeAudioPlayer - Ultra-low latency streaming PCM audio player.
 *
 * Configured precisely for Gemini Live native audio output:
 * - 16-bit Linear PCM
 * - 24,000 Hz (24 kHz)
 * - Mono
 * - Little-endian
 *
 * Features:
 * - Progressive streaming: audio chunks are fed to AudioTrack immediately upon receipt.
 * - Instant interruption: audio can be halted and flushed in < 10ms when user speaks.
 * - Safe error handling: handles audio track state transitions without throwing uncaught exceptions.
 */
@Singleton
class NativeAudioPlayer @Inject constructor() {

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackMutex = Mutex()

    sealed interface PlaybackCommand {
        class Chunk(val pcm: ByteArray) : PlaybackCommand
        class EndOfTurn(val onFinished: (() -> Unit)? = null) : PlaybackCommand
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var audioQueue = Channel<PlaybackCommand>(Channel.UNLIMITED)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var hasLoggedPlaybackStart = false

    companion object {
        private const val TAG = "NativeAudioPlayer"
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private fun getMinBufferSize(): Int {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        return if (minBuf <= 0) SAMPLE_RATE * 2 else minBuf
    }

    private fun ensureAudioTrack(): AudioTrack {
        val current = audioTrack
        if (current != null && current.state == AudioTrack.STATE_INITIALIZED) {
            return current
        }

        val bufferSize = getMinBufferSize() * 2

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }

        audioTrack = track
        return track
    }

    /**
     * Feed an audio chunk (16-bit PCM, 24kHz) for progressive streaming playback.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (pcmChunk.isEmpty()) return

        if (!_isPlaying.value) {
            _isPlaying.value = true
            if (!hasLoggedPlaybackStart) {
                Log.d(TAG, "[VASU] Audio playback started")
                hasLoggedPlaybackStart = true
            }
            startPlaybackLoop()
        }

        audioQueue.trySend(PlaybackCommand.Chunk(pcmChunk))
    }

    /**
     * Explicit end-of-response signal: marks that Gemini has finished sending audio chunks.
     * Allows remaining queued PCM chunks and hardware buffer to drain and play out completely,
     * then stops AudioTrack, resets isPlaying to false, and invokes onFinished callback.
     */
    fun markEndOfResponse(onFinished: (() -> Unit)? = null) {
        if (_isPlaying.value) {
            audioQueue.trySend(PlaybackCommand.EndOfTurn(onFinished))
        } else {
            onFinished?.invoke()
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = playerScope.launch {
            var totalFramesWritten = 0L
            try {
                val track = ensureAudioTrack()
                val startHeadPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                while (isActive) {
                    val cmd = audioQueue.receiveCatching().getOrNull() ?: break
                    when (cmd) {
                        is PlaybackCommand.Chunk -> {
                            if (cmd.pcm.isNotEmpty() && isActive) {
                                val written = track.write(cmd.pcm, 0, cmd.pcm.size)
                                if (written > 0) {
                                    totalFramesWritten += written / 2
                                }
                            }
                        }
                        is PlaybackCommand.EndOfTurn -> {
                            // All chunks for this turn have been written to AudioTrack.
                            // In MODE_STREAM, track.stop() allows already-buffered audio to finish playing.
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.stop()
                            }
                            // Wait for the hardware playback buffer to drain with safety timeout relative to startHeadPosition
                            val targetHeadPosition = startHeadPosition + totalFramesWritten
                            val drainStartMs = System.currentTimeMillis()
                            val remainingFrames = targetHeadPosition - (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                            val maxDrainWaitMs = if (remainingFrames > 0) {
                                (remainingFrames * 1000L / SAMPLE_RATE) + 500L
                            } else {
                                300L
                            }
                            while (isActive && (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < targetHeadPosition) {
                                if (track.playState == AudioTrack.PLAYSTATE_STOPPED) break
                                if (System.currentTimeMillis() - drainStartMs > maxDrainWaitMs) {
                                    Log.d(TAG, "Hardware playback buffer drain timeout reached")
                                    break
                                }
                                kotlinx.coroutines.delay(20)
                            }
                            cmd.onFinished?.invoke()
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation on user interruption
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack error during playback", e)
            } finally {
                _isPlaying.value = false
                hasLoggedPlaybackStart = false
                Log.d(TAG, "[VASU] Audio playback completed")
            }
        }
    }

    /**
     * Immediately halts assistant audio playback and flushes pending audio chunks.
     * Required for real-time user interruption (< 10ms latency).
     */
    fun stopAndFlush() {
        playbackJob?.cancel()
        playbackJob = null

        // Recreate the queue to discard remaining chunks
        audioQueue.close()
        audioQueue = Channel(Channel.UNLIMITED)

        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        } finally {
            _isPlaying.value = false
            hasLoggedPlaybackStart = false
        }
    }

    /**
     * Release AudioTrack resources.
     */
    fun release() {
        stopAndFlush()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }
}
