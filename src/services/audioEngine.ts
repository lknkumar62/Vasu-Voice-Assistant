/**
 * Browser-compatible AudioEngine using Web Audio API.
 *
 * Input format: 16-bit PCM / 16 kHz / mono / little-endian
 * Output format: 16-bit PCM / 24 kHz / mono / little-endian
 *
 * DO NOT put Android-only classes (AudioRecord, AudioTrack, ForegroundService) in this file.
 */

export class BrowserAudioPlayer {
  private audioCtx: AudioContext | null = null;
  private nextPlayTime = 0;
  private isPlayingAudio = false;
  private hasLoggedPlaybackStart = false;
  private activeSourceNodes: AudioBufferSourceNode[] = [];
  private onPlaybackEndedCallback?: () => void;
  private endTimeoutId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Lazy initialize to comply with browser autoplay policies
  }

  public setOnPlaybackEnded(callback: () => void): void {
    this.onPlaybackEndedCallback = callback;
  }

  private ensureContext(): AudioContext {
    if (!this.audioCtx || this.audioCtx.state === 'closed') {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      // Default hardware rate is safer across all devices; AudioBuffer resamples accurately
      this.audioCtx = new AudioCtxClass();
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
    return this.audioCtx;
  }

  /**
   * Enqueue a 24 kHz 16-bit PCM audio chunk for immediate progressive playback.
   */
  public enqueueAudioChunk(pcm24kBytes: Uint8Array): void {
    if (!pcm24kBytes || pcm24kBytes.length === 0) return;

    if (this.endTimeoutId) {
      clearTimeout(this.endTimeoutId);
      this.endTimeoutId = null;
    }

    const ctx = this.ensureContext();

    if (!this.hasLoggedPlaybackStart) {
      console.log('[VASU] Audio playback started');
      this.hasLoggedPlaybackStart = true;
    }
    this.isPlayingAudio = true;

    // Convert 16-bit PCM to Float32Array
    const numSamples = Math.floor(pcm24kBytes.byteLength / 2);
    const dataView = new DataView(pcm24kBytes.buffer, pcm24kBytes.byteOffset, pcm24kBytes.byteLength);
    const float32Data = new Float32Array(numSamples);

    for (let i = 0; i < numSamples; i++) {
      const int16 = dataView.getInt16(i * 2, true); // little-endian
      float32Data[i] = int16 < 0 ? int16 / 32768 : int16 / 32767;
    }

    const audioBuffer = ctx.createBuffer(1, numSamples, 24000);
    audioBuffer.getChannelData(0).set(float32Data);

    const source = ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(ctx.destination);

    const now = ctx.currentTime;
    // Schedule seamlessly directly after previous chunk or right now
    const startTime = Math.max(now, this.nextPlayTime);
    source.start(startTime);
    this.nextPlayTime = startTime + audioBuffer.duration;

    this.activeSourceNodes.push(source);

    source.onended = () => {
      const index = this.activeSourceNodes.indexOf(source);
      if (index !== -1) {
        this.activeSourceNodes.splice(index, 1);
      }
      if (this.activeSourceNodes.length === 0) {
        if (this.endTimeoutId) {
          clearTimeout(this.endTimeoutId);
        }
        const remainingMs = Math.max(0, (this.nextPlayTime - ctx.currentTime) * 1000);
        this.endTimeoutId = setTimeout(() => {
          if (this.activeSourceNodes.length === 0) {
            this.isPlayingAudio = false;
            this.hasLoggedPlaybackStart = false;
            console.log('[VASU] Audio playback completed');
            this.onPlaybackEndedCallback?.();
          }
        }, remainingMs + 30);
      }
    };
  }

  /**
   * Immediately halts assistant audio playback and flushes queued audio.
   * Required for interruption.
   */
  public stopAndFlush(): void {
    if (this.endTimeoutId) {
      clearTimeout(this.endTimeoutId);
      this.endTimeoutId = null;
    }
    for (const source of this.activeSourceNodes) {
      try {
        source.stop();
        source.disconnect();
      } catch {
        // Ignored if already stopped
      }
    }
    this.activeSourceNodes = [];
    if (this.audioCtx) {
      this.nextPlayTime = this.audioCtx.currentTime;
    }
    this.isPlayingAudio = false;
    this.hasLoggedPlaybackStart = false;
  }

  public isPlaying(): boolean {
    return this.isPlayingAudio;
  }
}

export class BrowserMicrophoneRecorder {
  private audioStream: MediaStream | null = null;
  private audioCtx: AudioContext | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private isRecordingAudio = false;

  /**
   * Start microphone capture and stream 16-bit PCM / 16 kHz / mono chunks.
   */
  public async startStreaming(onChunk: (base64Chunk: string) => void): Promise<boolean> {
    if (this.isRecordingAudio) return true;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

      this.audioStream = stream;
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioCtxClass();
      const inputSampleRate = this.audioCtx.sampleRate;
      console.log(`[VASU] microphone input rate: ${inputSampleRate} Hz`);
      console.log('[VASU] Gemini PCM output rate: 16000 Hz');

      this.sourceNode = this.audioCtx.createMediaStreamSource(stream);
      // Process in ~100-150ms buffers depending on input sample rate
      const bufferSize = inputSampleRate >= 44100 ? 4096 : 2048;
      this.processorNode = this.audioCtx.createScriptProcessor(bufferSize, 1, 1);

      this.processorNode.onaudioprocess = (event) => {
        if (!this.isRecordingAudio) return;
        const rawInput = event.inputBuffer.getChannelData(0);
        // Resample from physical input rate to exactly 16 kHz
        const resampled = this.resampleTo16k(rawInput, inputSampleRate);
        const pcm16 = new Int16Array(resampled.length);

        for (let i = 0; i < resampled.length; i++) {
          const s = Math.max(-1, Math.min(1, resampled[i]));
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
        }

        // Convert Int16Array to little-endian bytes and base64
        const uint8 = new Uint8Array(pcm16.buffer, pcm16.byteOffset, pcm16.byteLength);
        let binary = '';
        for (let i = 0; i < uint8.byteLength; i++) {
          binary += String.fromCharCode(uint8[i]);
        }
        const base64 = btoa(binary);
        onChunk(base64);
      };

      this.sourceNode.connect(this.processorNode);
      this.processorNode.connect(this.audioCtx.destination);

      this.isRecordingAudio = true;
      console.log('[VASU] Microphone started');
      return true;
    } catch (err: any) {
      console.error('[VASU] Failed to start microphone:', err?.message || err);
      return false;
    }
  }

  /**
   * Resamples raw audio buffer from native input rate to target 16 kHz mono.
   */
  private resampleTo16k(inputData: Float32Array, inputRate: number): Float32Array {
    if (inputRate === 16000) {
      return inputData;
    }
    const ratio = inputRate / 16000;
    const targetLength = Math.max(1, Math.round(inputData.length / ratio));
    const output = new Float32Array(targetLength);

    for (let i = 0; i < targetLength; i++) {
      const startInput = Math.floor(i * ratio);
      const endInput = Math.min(inputData.length, Math.floor((i + 1) * ratio));
      let sum = 0;
      let count = 0;
      for (let j = startInput; j < endInput; j++) {
        sum += inputData[j];
        count++;
      }
      output[i] = count > 0 ? sum / count : inputData[Math.min(startInput, inputData.length - 1)];
    }
    return output;
  }

  public stopStreaming(): void {
    this.isRecordingAudio = false;

    if (this.processorNode) {
      this.processorNode.disconnect();
      this.processorNode = null;
    }
    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }
    if (this.audioStream) {
      this.audioStream.getTracks().forEach((t) => t.stop());
      this.audioStream = null;
    }
    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      this.audioCtx.close().catch(() => {});
      this.audioCtx = null;
    }
  }

  public isRecording(): boolean {
    return this.isRecordingAudio;
  }
}
