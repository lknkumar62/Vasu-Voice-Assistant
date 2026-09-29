package com.vasu.assistant.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.audio.AudioSessionManager
import com.vasu.assistant.core.security.EnrollmentState
import com.vasu.assistant.core.security.EnrolledVoice
import com.vasu.assistant.core.security.RoleManager
import com.vasu.assistant.core.security.UserRole
import com.vasu.assistant.core.security.VoiceEnrollmentManager
import com.vasu.assistant.core.voice.NativeMicrophoneRecorder
import com.vasu.assistant.core.wakeword.WakeWordDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Backs GuardianScreen: guardian toggle + enrolled voice management +
 * the 3-sample biometric enrollment flow fed from [NativeMicrophoneRecorder]
 * (16 kHz PCM16 mono), arbitrated through [AudioSessionManager].
 */
@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val roleManager: RoleManager,
    private val enrollmentManager: VoiceEnrollmentManager,
    private val mic: NativeMicrophoneRecorder,
    private val audioSession: AudioSessionManager,
    private val wakeWordDetector: WakeWordDetector
) : ViewModel() {

    val guardianEnabled: StateFlow<Boolean> = roleManager.guardianEnabled
    val voices: StateFlow<List<EnrolledVoice>> = roleManager.enrolledVoices
    val currentSpeaker: StateFlow<EnrolledVoice?> = roleManager.currentSpeaker
    val enrollmentState: StateFlow<EnrollmentState> = enrollmentManager.state
    val rmsLevel: StateFlow<Float> = mic.rmsLevel

    private val _isRecordingSample = MutableStateFlow(false)
    val isRecordingSample: StateFlow<Boolean> = _isRecordingSample.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val pcmBuffer = ByteArrayOutputStream()
    private val recording = AtomicBoolean(false)
    private val sessionId = AtomicInteger(0)
    private var micHeld = false
    private var wakePaused = false

    fun clearMessage() {
        _uiMessage.value = null
    }

    fun setGuardianEnabled(enabled: Boolean) {
        if (enabled && roleManager.listVoices().isEmpty()) {
            _uiMessage.value = "Enroll a voice before enabling Guardian."
            return
        }
        roleManager.setGuardianEnabled(enabled)
    }

    fun removeVoice(id: String) {
        roleManager.removeVoice(id)
    }

    fun updateRole(id: String, role: UserRole) {
        roleManager.updateVoiceRole(id, role)
    }

    fun beginEnrollment() {
        if (mic.isRecording.value) {
            _uiMessage.value = "Mic is busy with another voice session — end it first."
            return
        }
        val state = enrollmentManager.state.value
        val midFlow = state is EnrollmentState.Recording || state is EnrollmentState.SampleRecorded
        // Already mid-flow on a live mic session: nothing to do. If ownership
        // was force-released (focus loss / external app), re-acquire instead of
        // dead-ending in a state that can never record again.
        if (midFlow && micHeld) return

        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) {
                val ok = !mic.isRecording.value &&
                    audioSession.requestMic(AudioSessionManager.MicOwner.ENROLLMENT)
                if (ok) micHeld = true
                ok
            }
            if (granted) {
                if (!wakePaused) {
                    wakeWordDetector.pauseForSpeechRecognition()
                    wakePaused = true
                }
                if (!midFlow) enrollmentManager.startEnrollment()
            } else {
                _uiMessage.value = if (midFlow) {
                    "Mic was lost — cancel and start enrollment again."
                } else {
                    "Microphone unavailable right now. Try again."
                }
            }
        }.invokeOnCompletion { cause ->
            // Cancellation (screen closed mid-request) can leave ownership
            // granted after onCleared already ran — release from here too.
            if (cause != null) releaseSession()
        }
    }

    fun cancelEnrollment() {
        stopMic()
        releaseSession()
        enrollmentManager.cancelEnrollment()
    }

    fun startSample(): Boolean {
        val state = enrollmentManager.state.value
        if (state !is EnrollmentState.Recording && state !is EnrollmentState.SampleRecorded) {
            _uiMessage.value = "Start the enrollment flow first."
            return false
        }
        if (!micHeld) {
            _uiMessage.value = "Mic not ready — cancel and start enrollment again."
            return false
        }
        if (!recording.compareAndSet(false, true)) return true

        val session = sessionId.get()
        synchronized(pcmBuffer) { pcmBuffer.reset() }
        val ok = mic.startStreaming { chunk -> if (session == sessionId.get()) onChunk(chunk) }
        if (ok) {
            _isRecordingSample.value = true
        } else {
            recording.set(false)
            _uiMessage.value = "Could not start the microphone."
        }
        return ok
    }

    fun stopAndSubmitSample() {
        submitSample()
    }

    fun completeEnrollment(name: String, role: UserRole) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiMessage.value = "Enter a name for this voice."
            return
        }
        enrollmentManager.completeEnrollment(trimmed, role)
        releaseSession()
    }

    private fun onChunk(chunk: ByteArray) {
        var overflow = false
        synchronized(pcmBuffer) {
            if (!recording.get()) return
            if (pcmBuffer.size() + chunk.size > MAX_SAMPLE_BYTES) {
                overflow = true
            } else {
                pcmBuffer.write(chunk)
            }
        }
        if (overflow) submitSample()
    }

    private fun submitSample() {
        if (!recording.compareAndSet(true, false)) return
        sessionId.incrementAndGet()
        _isRecordingSample.value = false
        mic.stopStreaming()

        val bytes = synchronized(pcmBuffer) {
            val copy = pcmBuffer.toByteArray()
            pcmBuffer.reset()
            copy
        }

        val floats = toFloats(bytes)
        if (floats.size < MIN_SAMPLE_SAMPLES) {
            _uiMessage.value = "Sample too short — record for at least 1 second."
            return
        }

        val state = enrollmentManager.state.value
        if (state is EnrollmentState.Idle ||
            state is EnrollmentState.Error ||
            state is EnrollmentState.Completed
        ) {
            enrollmentManager.startEnrollment()
        }
        enrollmentManager.recordSample(floats)
    }

    private fun stopMic() {
        if (recording.compareAndSet(true, false)) {
            sessionId.incrementAndGet()
            _isRecordingSample.value = false
            mic.stopStreaming()
        }
        synchronized(pcmBuffer) { pcmBuffer.reset() }
    }

    private fun releaseMic() {
        if (micHeld) {
            audioSession.releaseMic(AudioSessionManager.MicOwner.ENROLLMENT)
            micHeld = false
        }
    }

    /** Releases mic ownership and un-pauses the wake-word detector. */
    private fun releaseSession() {
        releaseMic()
        if (wakePaused) {
            wakeWordDetector.resumeAfterSpeechRecognition()
            wakePaused = false
        }
    }

    /** 16-bit little-endian PCM → normalised floats, matching recorder format. */
    private fun toFloats(bytes: ByteArray): FloatArray {
        val count = bytes.size / 2
        return FloatArray(count) { i ->
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            ((lo or (hi shl 8)).toShort()) / 32768f
        }
    }

    override fun onCleared() {
        stopMic()
        releaseSession()
        enrollmentManager.cancelEnrollment()
        super.onCleared()
    }

    companion object {
        /** 5 s of 16 kHz PCM16 mono. */
        private const val MAX_SAMPLE_BYTES = 16000 * 2 * 5

        /** 1 s — below this VoiceEnrollmentManager rejects the sample. */
        private const val MIN_SAMPLE_SAMPLES = 16000
    }
}
