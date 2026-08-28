package com.vasu.assistant.core.wakeword

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class MelSpectrogram(
    private val sampleRate: Int = 16000,
    private val fftSize: Int = 512,
    private val numMelBands: Int = 40,
    private val lowerFreq: Float = 0f,
    private val upperFreq: Float = sampleRate / 2f
) {

    fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }

    fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }

    fun extractForWakeWord(audioData: FloatArray): FloatArray {
        val spectrogram = computeMelSpectrogram(audioData)
        return spectrogram.flatMap { it.toList() }.toFloatArray()
    }

    fun computeMelSpectrogram(audioData: FloatArray): Array<FloatArray> {
        val numFrames = (audioData.size - fftSize) / (fftSize / 2) + 1
        val melSpectrogram = Array(numFrames.coerceAtLeast(1)) { FloatArray(numMelBands) }

        val filterbank = createMelFilterbank()

        for (frame in 0 until numFrames.coerceAtLeast(1)) {
            val start = frame * (fftSize / 2)
            val windowedFrame = FloatArray(fftSize)

            for (i in 0 until fftSize) {
                val idx = start + i
                if (idx < audioData.size) {
                    windowedFrame[i] = audioData[idx] * hanningWindow(i, fftSize)
                }
            }

            val spectrum = computeFFT(windowedFrame)

            for (mel in 0 until numMelBands) {
                var melEnergy = 0f
                for (k in spectrum.indices) {
                    melEnergy += spectrum[k] * filterbank[mel][k]
                }
                melSpectrogram[frame][mel] = ln(melEnergy.coerceAtLeast(1e-10f))
            }
        }

        return melSpectrogram
    }

    private fun hanningWindow(index: Int, size: Int): Float {
        return (0.5f * (1f - kotlin.math.cos(2.0 * Math.PI * index / (size - 1)))).toFloat()
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
            (hzPoints[i] / (sampleRate / 2.0f) * (numFFTBins - 1)).toInt().coerceIn(0, numFFTBins - 1).toFloat()
        }

        for (m in 0 until numMelBands) {
            val start = binPoints[m].toInt()
            val center = binPoints[m + 1].toInt()
            val end = binPoints[m + 2].toInt()

            for (k in start until center) {
                if (k < numFFTBins) {
                    filterbank[m][k] = (k.toFloat() - binPoints[m]) / (binPoints[m + 1] - binPoints[m])
                }
            }

            for (k in center until end) {
                if (k < numFFTBins) {
                    filterbank[m][k] = (binPoints[m + 2] - k.toFloat()) / (binPoints[m + 2] - binPoints[m + 1])
                }
            }
        }

        return filterbank
    }

    private fun computeFFT(signal: FloatArray): FloatArray {
        val n = signal.size
        val spectrum = FloatArray(n / 2 + 1)

        for (k in 0 until n / 2 + 1) {
            var real = 0f
            var imag = 0f
            for (t in 0 until n) {
                val angle = (2.0 * Math.PI * k * t / n).toFloat()
                real += signal[t] * kotlin.math.cos(angle)
                imag -= signal[t] * kotlin.math.sin(angle)
            }
            spectrum[k] = sqrt(real * real + imag * imag) / n
        }

        return spectrum
    }
}
