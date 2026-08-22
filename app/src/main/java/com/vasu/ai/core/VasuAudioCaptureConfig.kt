package com.vasu.ai.core

data class VasuAudioCaptureConfig(
    val sampleRateHz: Int = 16_000,
    val channelConfig: Int = android.media.AudioFormat.CHANNEL_IN_MONO,
    val encoding: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    val bufferMultiplier: Int = 2,
    val maxBufferBytes: Int = 64 * 1024
) {
    init {
        require(sampleRateHz > 0)
        require(bufferMultiplier >= 1)
        require(maxBufferBytes > 0)
    }
}
