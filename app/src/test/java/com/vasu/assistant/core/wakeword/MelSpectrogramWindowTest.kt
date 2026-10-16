package com.vasu.assistant.core.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the wake-word window/frame off-by-one that made
 * "Hello Vasu" physically unable to fire.
 *
 * WakeWordDetector gates inference on `melFeatures.size >= WakeWordModel.INPUT_SIZE`
 * while MelSpectrogram yields `(samples - fftSize) / hop + 1` frames. The old
 * 25,088-sample window produced only 97 frames against an inputSize of 98, so the
 * gate never opened. These tests pin the contract between the detector window and
 * the model input so the two cannot drift apart again.
 */
class MelSpectrogramWindowTest {

    @Test
    fun `rolling window formula yields exactly model input frames`() {
        // MelSpectrogram with fftSize = 512 (hop = fftSize / 2 = 256):
        // numFrames = (samples - 512) / 256 + 1
        val frames = (WakeWordDetector.ROLLING_WINDOW_SAMPLES - 512) / 256 + 1
        assertEquals(WakeWordModel.INPUT_SIZE, frames)
    }

    @Test
    fun `extractor produces at least model input frames for the detector window`() {
        val mel = MelSpectrogram(
            sampleRate = 16000,
            fftSize = 512,
            numMelBands = WakeWordModel.NUM_MEL_BANDS
        )
        val frames =
            mel.computeMelSpectrogram(FloatArray(WakeWordDetector.ROLLING_WINDOW_SAMPLES))
        assertTrue(
            "expected >= ${WakeWordModel.INPUT_SIZE} mel frames, got ${frames.size}",
            frames.size >= WakeWordModel.INPUT_SIZE
        )
    }
}
