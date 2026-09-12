/**
 * Microphone Streamer — Captures mic audio and streams PCM chunks to WebSocket
 * Replicates Maya's C2525iP0.java (AudioRecord read loop) + C0308Fx.B() (Base64 encoding)
 *
 * Captures at 16kHz mono PCM, encodes to Base64, emits chunks for WebSocket transmission.
 * Applies AEC/NS/AGC when available (via getUserMedia constraints).
 */

export type StreamerState = 'STOPPED' | 'STARTING' | 'STREAMING' | 'ERROR';

export interface MicChunkCallback {
  onChunk?: (base64Pcm: string) => void;
  onStateChange?: (state: StreamerState) => void;
  onError?: (error: Error) => void;
}

const SAMPLE_RATE = 16000;
const CHUNK_DURATION_MS = 100; // 100ms chunks = 1600 samples per chunk

class MicrophoneStreamer {
  private state: StreamerState = 'STOPPED';
  private stream: MediaStream | null = null;
  private audioCtx: AudioContext | null = null;
  private scriptProcessor: ScriptProcessorNode | null = null;
  private callbacks: MicChunkCallback = {};

  constructor() {}

  /**
   * Start capturing microphone and streaming PCM chunks
   * Mirrors Maya's AudioRecord(VOICE_COMMUNICATION, 16000, MONO, PCM_16BIT)
   */
  async start(callbacks: MicChunkCallback = {}): Promise<boolean> {
    if (this.state === 'STREAMING' || this.state === 'STARTING') return true;
    this.callbacks = callbacks;
    this.updateState('STARTING');

    try {
      // Request mic with echo cancellation, noise suppression, auto gain
      // Mirrors Maya's AcousticEchoCanceler + NoiseSuppressor + AutomaticGainControl
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: SAMPLE_RATE,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        }
      });

      // Create AudioContext @ 16kHz (matches Gemini input requirement)
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioCtx({ sampleRate: SAMPLE_RATE });

      const micSource = this.audioCtx.createMediaStreamSource(this.stream);

      // ScriptProcessorNode to capture chunks
      // Buffer size 1600 = 100ms at 16kHz = one chunk per callback
      const bufferSize = Math.floor(SAMPLE_RATE * CHUNK_DURATION_MS / 1000); // 1600
      this.scriptProcessor = this.audioCtx.createScriptProcessor(bufferSize, 1, 1);

      this.scriptProcessor.onaudioprocess = (e) => {
        if (this.state !== 'STREAMING') return;

        const inputChannel = e.inputBuffer.getChannelData(0);

        // Convert Float32 → Int16 PCM (like Maya's recording loop)
        const pcm16 = new Int16Array(inputChannel.length);
        for (let i = 0; i < inputChannel.length; i++) {
          const s = Math.max(-1, Math.min(1, inputChannel[i]));
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }

        // Base64 encode (like Maya's Base64.encodeToString(bArr, 2))
        const uint8 = new Uint8Array(pcm16.buffer);
        let binary = '';
        for (let i = 0; i < uint8.byteLength; i++) {
          binary += String.fromCharCode(uint8[i]);
        }
        const b64 = btoa(binary);

        // Emit chunk
        this.callbacks.onChunk?.(b64);
      };

      micSource.connect(this.scriptProcessor);
      this.scriptProcessor.connect(this.audioCtx.destination);

      this.updateState('STREAMING');
      return true;
    } catch (e: any) {
      console.error('[MicrophoneStreamer] Start error:', e);
      this.updateState('ERROR');
      this.callbacks.onError?.(e);
      return false;
    }
  }

  /**
   * Stop microphone capture
   * Mirrors Maya's C2940lP0.i(): stop AudioRecord, release effects
   */
  stop(): void {
    this.updateState('STOPPED');

    try {
      this.scriptProcessor?.disconnect();
      this.scriptProcessor = null;

      this.audioCtx?.close().catch(() => {});
      this.audioCtx = null;

      this.stream?.getTracks().forEach(t => t.stop());
      this.stream = null;
    } catch (e) {
      console.warn('[MicrophoneStreamer] Stop error:', e);
    }
  }

  getState(): StreamerState { return this.state; }
  isStreaming(): boolean { return this.state === 'STREAMING'; }

  // ─── PRIVATE ───────────────────────────────────────

  private updateState(s: StreamerState): void {
    if (this.state === s) return;
    this.state = s;
    this.callbacks.onStateChange?.(s);
  }
}

export const microphoneStreamer = new MicrophoneStreamer();
