package com.vasu.assistant.core.wakeword

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MelSpectrogram - Extracts Mel-frequency spectrogram features from audio.
 *
 * Used for wake word detection by converting raw audio into
 * a format suitable for neural network inference.
 */
class MelSpectrogram(
    private val sampleRate: Int = 16000,
    private val fftSize: Int = 512,
    private val hopSize: Int = 160,
    private val numMelBands: Int = 40,
    private val lowerFreq: Float = 0f,
    private val upperFreq: Float = sampleRate / 2f
) {
    // Mel filterbank
    private val melFilterbank: Array<FloatArray>
    private val window: FloatArray

    init {
        melFilterbank = createMelFilterbank()
        window = createHammingWindow()
    }

    /**
     * Extract Mel spectrogram from audio samples
     * @param audioSamples Audio samples in float format (-1.0 to 1.0)
     * @return 2D array of Mel spectrogram [numFrames][numMelBands]
     */
    fun extract(audioSamples: FloatArray): Array<FloatArray> {
        // Apply windowing and compute frames
        val frames = extractFrames(audioSamples)
        val spectrogram = mutableListOf<FloatArray>()

        for (frame in frames) {
            // Apply FFT
            val fftResult = fft(frame)

            // Compute power spectrum
            val powerSpectrum = FloatArray(fftSize / 2 + 1) { i ->
                val real = fftResult[i * 2]
                val imag = fftResult[i * 2 + 1]
                (real * real + imag * imag) / fftSize
            }

            // Apply Mel filterbank
            val melSpectrum = applyMelFilterbank(powerSpectrum)

            // Convert to log scale (dB)
            val logMelSpectrum = FloatArray(melSpectrum.size) { i ->
                10f * log10(melSpectrum[i] + 1e-10f)
            }

            spectrogram.add(logMelSpectrum)
        }

        return spectrogram.toTypedArray()
    }

    /**
     * Extract features for wake word detection (fixed-size output)
     * @param audioSamples Audio samples
     * @param numFrames Number of output frames (pad or truncate)
     * @return Fixed-size feature array
     */
    fun extractForWakeWord(
        audioSamples: FloatArray,
        numFrames: Int = 98  // Default for 1 second at 16kHz with hop=160
    ): Array<FloatArray> {
        val spectrogram = extract(audioSamples)

        return Array(numFrames) { frameIndex ->
            if (frameIndex < spectrogram.size) {
                spectrogram[frameIndex]
            } else {
                FloatArray(numMelBands) { 0f }  // Pad with zeros
            }
        }
    }

    private fun extractFrames(audioSamples: FloatArray): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0

        while (start + fftSize <= audioSamples.size) {
            val frame = FloatArray(fftSize)
            for (i in 0 until fftSize) {
                frame[i] = audioSamples[start + i] * window[i]
            }
            frames.add(frame)
            start += hopSize
        }

        return frames
    }

    private fun createHammingWindow(): FloatArray {
        return FloatArray(fftSize) { n ->
            (0.54f - 0.46f * cos(2.0 * PI * n / (fftSize - 1))).toFloat()
        }
    }

    private fun fft(signal: FloatArray): FloatArray {
        val n = signal.size
        if (n == 0) return floatArrayOf()

        // Pad to power of 2
        var m = 1
        while (m < n) m *= 2

        val real = FloatArray(m)
        val imag = FloatArray(m)
        signal.copyInto(real)

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until m - 1) {
            if (i < j) {
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal
            }
            var k = m / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // FFT butterfly
        var len = 2
        while (len <= m) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()

            var i = 0
            while (i < m) {
                var curReal = 1.0f
                var curImag = 0.0f

                for (k in 0 until halfLen) {
                    val tReal = curReal * real[i + k + halfLen] - curImag * imag[i + k + halfLen]
                    val tImag = curReal * imag[i + k + halfLen] + curImag * real[i + k + halfLen]

                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] += tReal
                    imag[i + k] += tImag

                    val newReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = newReal
                }
                i += len
            }
            len *= 2
        }

        // Interleave real and imaginary
        val result = FloatArray(m * 2)
        for (i in 0 until m) {
            result[i * 2] = real[i]
            result[i * 2 + 1] = imag[i]
        }

        return result
    }

    private fun createMelFilterbank(): Array<FloatArray> {
        val numFFTBins = fftSize / 2 + 1
        val filterbank = Array(numMelBands) { FloatArray(numFFTBins) }

        val melLow = hzToMel(lowerFreq)
        val melHigh = hzToMel(upperFreq)
        val melPoints = FloatArray(numMelBands + 2) { i ->
            melLow + i * (melHigh - melLow) / (numMelBands + 1)
        }

        val hzPoints = FloatArray(melPoints.size) { i ->
            melToHz(melPoints[i])
        }

        val binPoints = FloatArray(hzPoints.size) { i ->
            (hzPoints[i] / (sampleRate / 2.0f) * (numFFTBins - 1)).toInt().coerceIn(0, numFFTBins - 1)
        }

        for (m in 0 until numMelBands) {
            val start = binPoints[m].toInt()
            val center = binPoints[m + 1].toInt()
            val end = binPoints[m + 2].toInt()

            for (k in start until center) {
                if (k < numFFTBins) {
                    filterbank[m][k] = (k - binPoints[m]) / (binPoints[m + 1] - binPoints[m])
                }
            }

            for (k in center until end) {
                if (k < numFFTBins) {
                    filterbank[m][k] = (binPoints[m + 2] - k) / (binPoints[m + 2] - binPoints[m + 1])
                }
            }
        }

        return filterbank
    }

    private fun applyMelFilterbank(powerSpectrum: FloatArray): FloatArray {
        return FloatArray(numMelBands) { m ->
            var sum = 0f
            for (k in powerSpectrum.indices) {
                sum += melFilterbank[m][k] * powerSpectrum[k]
            }
            sum
        }
    }

    companion object {
        fun hzToMel(hz: Float): Float {
            return 2595f * log10(1f + hz / 700f)
        }

        fun melToHz(mel: Float): Float {
            return 700f * (10f.pow(mel / 2595f) - 1f)
        }
    }
}
