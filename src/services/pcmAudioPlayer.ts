/**
 * PCM Audio Player — Streaming chunk-based playback engine
 * Replicates Maya's C2940lP0.java (AudioTrack + ConcurrentLinkedQueue)
 *
 * Receives 24kHz PCM chunks from Gemini Live WebSocket,
 * queues them, and plays gaplessly through Web Audio API.
 */

export type PlayerState = 'IDLE' | 'PLAYING' | 'FLUSHING';

const SAMPLE_RATE = 24000;
const JITTER_BUFFER_MS = 50;

class PcmAudioPlayer {
  private ctx: AudioContext | null = null;
  private queue: Uint8Array[] = [];
  private totalQueuedBytes = 0;
  private nextStartTime = 0;
  private activeSources: AudioBufferSourceNode[] = [];
  private state: PlayerState = 'IDLE';
  private drainInterval: ReturnType<typeof setInterval> | null = null;
  private drainCallback: (() => void) | null = null;
  private volume = 1.0;

  constructor() {}

  /**
   * Initialize AudioContext @ 24kHz (like Maya's AudioTrack config)
   */
  init(): void {
    if (this.ctx && this.ctx.state !== 'closed') return;
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    this.ctx = new AudioCtx({ sampleRate: SAMPLE_RATE });
    if (this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
  }

  /**
   * Enqueue a PCM chunk for playback
   * Mirrors Maya's C2940lP0.a(byte[]): C.add(bArr); D.addAndGet(bArr.length)
   */
  enqueueChunk(pcmBytes: Uint8Array): void {
    if (pcmBytes.length === 0) return;
    this.queue.push(pcmBytes);
    this.totalQueuedBytes += pcmBytes.length;

    // Auto-start draining if not already
    if (this.state === 'IDLE') {
      this.state = 'PLAYING';
      this.startDraining();
    }
  }

  /**
   * Stop playback and flush all queued audio
   * Mirrors Maya's C2940lP0.b(): clear queue, pause/flush/restart AudioTrack
   */
  flush(): void {
    this.queue = [];
    this.totalQueuedBytes = 0;
    this.nextStartTime = 0;

    for (const src of this.activeSources) {
      try { src.stop(); src.disconnect(); } catch (_) {}
    }
    this.activeSources = [];

    this.state = 'IDLE';
    this.stopDraining();
  }

  /**
   * Stop and clean up all resources
   * Mirrors Maya's C2940lP0.j(): stop, release, restore audio mode
   */
  stop(): void {
    this.flush();
    if (this.ctx && this.ctx.state !== 'closed') {
      this.ctx.close().catch(() => {});
    }
    this.ctx = null;
  }

  /**
   * Set volume (0.0 to 1.0)
   */
  setVolume(v: number): void {
    this.volume = Math.max(0, Math.min(1, v));
  }

  /**
   * Register callback for when queue is fully drained
   */
  onDrained(cb: () => void): void {
    this.drainCallback = cb;
  }

  getState(): PlayerState { return this.state; }
  getQueueSize(): number { return this.totalQueuedBytes; }
  isActive(): boolean { return this.state === 'PLAYING' || this.queue.length > 0; }

  // ─── PRIVATE ───────────────────────────────────────

  /**
   * Drain queue and schedule gapless playback
   * Mirrors Maya's C2662jP0 (playback worker coroutine)
   */
  private startDraining(): void {
    this.stopDraining();
    this.drainInterval = setInterval(() => {
      this.drainNextChunk();
    }, 20); // Check every 20ms for low-latency scheduling
  }

  private stopDraining(): void {
    if (this.drainInterval) {
      clearInterval(this.drainInterval);
      this.drainInterval = null;
    }
  }

  private drainNextChunk(): void {
    if (!this.ctx || this.ctx.state === 'closed') return;
    if (this.queue.length === 0) {
      // Check if all active sources finished
      if (this.activeSources.length === 0) {
        this.state = 'IDLE';
        this.stopDraining();
        this.drainCallback?.();
      }
      return;
    }

    const chunk = this.queue.shift()!;
    this.totalQueuedBytes -= chunk.length;
    this.playChunk(chunk);
  }

  /**
   * Play a single PCM chunk through Web Audio API
   * Converts 16-bit PCM → Float32 → AudioBuffer → BufferSource
   * Schedules gaplessly using nextStartTime
   */
  private playChunk(pcmBytes: Uint8Array): void {
    if (!this.ctx) return;

    try {
      // Convert PCM 16-bit LE to Float32 [-1, 1]
      const numSamples = pcmBytes.length / 2;
      const float32 = new Float32Array(numSamples);
      const dv = new DataView(pcmBytes.buffer, pcmBytes.byteOffset, pcmBytes.byteLength);
      for (let i = 0; i < numSamples; i++) {
        float32[i] = dv.getInt16(i * 2, true) / 32768.0;
      }

      const buf = this.ctx.createBuffer(1, numSamples, SAMPLE_RATE);
      buf.getChannelData(0).set(float32);

      const src = this.ctx.createBufferSource();
      src.buffer = buf;
      src.connect(this.ctx.destination);
      this.activeSources.push(src);

      // Gapless scheduling with jitter buffer
      const now = this.ctx.currentTime;
      if (this.nextStartTime < now) {
        this.nextStartTime = now + JITTER_BUFFER_MS / 1000;
      }
      src.start(this.nextStartTime);
      this.nextStartTime += buf.duration;

      src.onended = () => {
        const idx = this.activeSources.indexOf(src);
        if (idx !== -1) this.activeSources.splice(idx, 1);
      };
    } catch (e) {
      console.warn('[PcmAudioPlayer] playChunk error:', e);
    }
  }
}

export const pcmAudioPlayer = new PcmAudioPlayer();
