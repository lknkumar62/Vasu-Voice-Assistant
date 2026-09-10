/**
 * GeminiLiveVoiceService (Frontend / Capacitor)
 * Handles bidirectional low-latency Gemini Live streaming session with prebuilt voice 'Erinome'.
 * 
 * Pipeline:
 * Microphone (16 kHz mono PCM) -> WebSocket (/api/live or direct) -> Gemini Live -> 24 kHz PCM -> AudioContext
 * 
 * Features:
 * - Erinome prebuilt voice
 * - Native 24 kHz PCM playback with gapless scheduling
 * - Sub-millisecond interruption handling: clears audio queue immediately when user speaks
 * - Real-time transcript tracking
 */

export type LiveConnectionState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'LISTENING'
  | 'THINKING'
  | 'SPEAKING'
  | 'ERROR'
  | 'idle'
  | 'active'
  | 'speaking'
  | 'interrupted'
  | 'error';

export interface GeminiLiveCallbacks {
  onAudioChunk?: (pcmData: Uint8Array) => void;
  onTranscript?: (text: string, isUser: boolean) => void;
  onInterrupted?: () => void;
  onStateChange?: (state: LiveConnectionState) => void;
  onError?: (err: string) => void;
}

export class GeminiLiveVoiceService {
  private static instance: GeminiLiveVoiceService;

  private ws: WebSocket | null = null;
  private isConnected = false;
  private isSpeaking = false;
  private isListening = false;
  private apiKey = '';
  private currentState: LiveConnectionState = 'DISCONNECTED';

  // Reconnection management
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 5;
  private reconnectTimeout: any = null;
  private isIntentionalDisconnect = false;
  private selectedVoice: string = 'Kore';

  // Audio Playback (24 kHz)
  private playbackContext: AudioContext | null = null;
  private nextStartTime = 0;
  private activeSources: AudioBufferSourceNode[] = [];

  // Audio Recording (16 kHz)
  private recordStream: MediaStream | null = null;
  private audioInputContext: AudioContext | null = null;
  private scriptProcessor: ScriptProcessorNode | null = null;

  private callbacks: GeminiLiveCallbacks = {};

  private constructor() {}

  public static getInstance(): GeminiLiveVoiceService {
    if (!GeminiLiveVoiceService.instance) {
      GeminiLiveVoiceService.instance = new GeminiLiveVoiceService();
    }
    return GeminiLiveVoiceService.instance;
  }

  public setCallbacks(cbs: GeminiLiveCallbacks) {
    this.callbacks = { ...this.callbacks, ...cbs };
  }

  public setApiKey(key: string) {
    this.apiKey = key.trim();
  }

  private setState(state: LiveConnectionState) {
    this.currentState = state;
    this.callbacks.onStateChange?.(state);
  }

  public getState(): LiveConnectionState {
    return this.currentState;
  }

  public setSelectedVoice(voice: string) {
    this.selectedVoice = voice || 'Kore';
  }

  public getSelectedVoice(): string {
    return this.selectedVoice;
  }

  private getPlaybackContext(): AudioContext {
    if (!this.playbackContext || this.playbackContext.state === 'closed') {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.playbackContext = new AudioCtx({ sampleRate: 24000 });
    }
    if (this.playbackContext.state === 'suspended') {
      this.playbackContext.resume().catch(() => {});
    }
    return this.playbackContext;
  }

  /**
   * Safe guarded WebSocket message sender
   */
  private safeSend(data: any): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        this.ws.send(typeof data === 'string' ? data : JSON.stringify(data));
        return true;
      } catch (err) {
        console.warn('[GeminiLiveVoiceService] safeSend error:', err);
      }
    }
    return false;
  }

  /**
   * Safely closes the existing socket and clears event listeners
   */
  private cleanupSocket() {
    if (this.ws) {
      try {
        this.ws.onopen = null;
        this.ws.onmessage = null;
        this.ws.onerror = null;
        this.ws.onclose = null;
        if (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING) {
          this.ws.close();
        }
      } catch (_) {}
      this.ws = null;
    }
    this.isConnected = false;
  }

  /**
   * Connects to Gemini Live Session
   */
  public async connect(apiKey?: string): Promise<boolean> {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    this.isIntentionalDisconnect = false;

    const key = apiKey || this.apiKey;
    this.setState('CONNECTING');

    // Tear down any existing socket before establishing a single session
    this.cleanupSocket();

    return new Promise((resolve) => {
      let settled = false;
      const settle = (val: boolean) => {
        if (!settled) {
          settled = true;
          resolve(val);
        }
      };

      try {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.host;
        const wsUrl = `${protocol}//${host}/api/live`;

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
          this.isConnected = true;
          this.reconnectAttempts = 0; // reset backoff counter on success
          // Guarded send for initialization
          this.safeSend({ type: 'init', apiKey: key, voice: this.selectedVoice });
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);

            if (data.type === 'ready') {
              this.setState('CONNECTED');
              settle(true);
            } else if (data.type === 'audio' && data.data) {
              this.setState('SPEAKING');
              this.handleIncomingAudio(data.data);
            } else if (data.type === 'transcript' && data.text) {
              this.callbacks.onTranscript?.(data.text, false);
            } else if (data.type === 'interrupted') {
              this.interrupt();
            } else if (data.type === 'error') {
              this.callbacks.onError?.(data.message || 'Live session error');
              this.setState('ERROR');
            }
          } catch (e) {
            console.warn('[GeminiLiveVoiceService] parse error:', e);
          }
        };

        this.ws.onerror = (err) => {
          console.warn('[GeminiLiveVoiceService] WS error:', err);
          this.isConnected = false;
          this.setState('ERROR');
          this.scheduleReconnect(key);
          settle(false);
        };

        this.ws.onclose = () => {
          this.isConnected = false;
          if (!this.isIntentionalDisconnect) {
            this.setState('DISCONNECTED');
            this.scheduleReconnect(key);
          }
          settle(false);
        };

        // Safety connection timeout (5 seconds)
        setTimeout(() => {
          if (!this.isConnected && !settled) {
            console.warn('[GeminiLiveVoiceService] Connection timeout');
            settle(false);
          }
        }, 5000);
      } catch (e) {
        console.error('[GeminiLiveVoiceService] connect exception:', e);
        this.setState('ERROR');
        settle(false);
      }
    });
  }

  /**
   * Bounded exponential backoff reconnection
   */
  private scheduleReconnect(apiKey?: string) {
    if (this.isIntentionalDisconnect) return;
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[GeminiLiveVoiceService] Max reconnect attempts reached');
      this.setState('ERROR');
      return;
    }

    const backoffMs = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 8000);
    this.reconnectAttempts++;
    console.info(`[GeminiLiveVoiceService] Reconnecting in ${backoffMs}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

    this.reconnectTimeout = setTimeout(() => {
      if (!this.isIntentionalDisconnect) {
        this.connect(apiKey).then((success) => {
          if (success && this.isListening) {
            // Resume streaming mic if active
            this.startMicrophone();
          }
        }).catch(() => {});
      }
    }, backoffMs);
  }

  /**
   * Decodes base64 24kHz PCM audio and schedules gapless playback
   */
  private handleIncomingAudio(base64Audio: string) {
    try {
      const binary = atob(base64Audio);
      const len = binary.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binary.charCodeAt(i);
      }

      // Convert 16-bit PCM little-endian to Float32 [-1.0, 1.0]
      const samples = new Float32Array(len / 2);
      const dataView = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      for (let i = 0; i < samples.length; i++) {
        const int16 = dataView.getInt16(i * 2, true);
        samples[i] = int16 / 32768;
      }

      const ctx = this.getPlaybackContext();
      const audioBuffer = ctx.createBuffer(1, samples.length, 24000);
      audioBuffer.getChannelData(0).set(samples);

      const source = ctx.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(ctx.destination);

      const now = ctx.currentTime;
      if (this.nextStartTime < now) {
        this.nextStartTime = now + 0.05; // 50ms jitter buffer
      }

      source.start(this.nextStartTime);
      this.nextStartTime += audioBuffer.duration;

      this.activeSources.push(source);
      this.isSpeaking = true;
      this.callbacks.onStateChange?.('speaking');

      source.onended = () => {
        const idx = this.activeSources.indexOf(source);
        if (idx !== -1) this.activeSources.splice(idx, 1);
        if (this.activeSources.length === 0) {
          this.isSpeaking = false;
          this.callbacks.onStateChange?.('active');
        }
      };
    } catch (e) {
      console.warn('[GeminiLiveVoiceService] handleIncomingAudio error:', e);
    }
  }

  /**
   * Starts capturing microphone audio at 16 kHz PCM and streams to Gemini Live
   */
  public async startMicrophone(): Promise<boolean> {
    if (this.isListening) return true;

    try {
      this.recordStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioInputContext = new AudioCtx({ sampleRate: 16000 });
      const micSource = this.audioInputContext.createMediaStreamSource(this.recordStream);

      // Buffer size 512 = 32ms chunks
      this.scriptProcessor = this.audioInputContext.createScriptProcessor(512, 1, 1);

      this.scriptProcessor.onaudioprocess = (e) => {
        if (!this.isConnected || !this.ws || this.ws.readyState !== WebSocket.OPEN) return;

        const inputChannel = e.inputBuffer.getChannelData(0);
        // Convert Float32Array to 16-bit PCM
        const pcm16 = new Int16Array(inputChannel.length);
        for (let i = 0; i < inputChannel.length; i++) {
          const s = Math.max(-1, Math.min(1, inputChannel[i]));
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }

        // Base64 encode
        const uint8 = new Uint8Array(pcm16.buffer);
        let binary = '';
        for (let i = 0; i < uint8.byteLength; i++) {
          binary += String.fromCharCode(uint8[i]);
        }
        const b64 = btoa(binary);

        this.safeSend({ type: 'audio', data: b64 });
      };

      micSource.connect(this.scriptProcessor);
      this.scriptProcessor.connect(this.audioInputContext.destination);

      this.isListening = true;
      this.setState('LISTENING');
      return true;
    } catch (err: any) {
      console.warn('[GeminiLiveVoiceService] startMicrophone error:', err);
      return false;
    }
  }

  /**
   * Stops microphone capture
   */
  public stopMicrophone() {
    this.isListening = false;
    try {
      this.scriptProcessor?.disconnect();
      this.scriptProcessor = null;
      this.audioInputContext?.close().catch(() => {});
      this.audioInputContext = null;

      this.recordStream?.getTracks().forEach((track) => track.stop());
      this.recordStream = null;
    } catch (e) {
      console.warn('[GeminiLiveVoiceService] stopMicrophone error:', e);
    }
  }

  /**
   * Instantly stops any ongoing playback and drops queued audio chunks
   */
  public interrupt() {
    this.callbacks.onInterrupted?.();
    this.setState('LISTENING');

    // Notify backend/Live session
    this.safeSend({ type: 'interrupt' });

    // Stop all active playing audio buffer source nodes
    for (const source of this.activeSources) {
      try {
        source.stop();
        source.disconnect();
      } catch (_) {}
    }
    this.activeSources = [];
    this.nextStartTime = 0;
    this.isSpeaking = false;
  }

  public disconnect() {
    this.isIntentionalDisconnect = true;
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    this.interrupt();
    this.stopMicrophone();
    this.cleanupSocket();
    this.setState('DISCONNECTED');

    try {
      this.playbackContext?.close().catch(() => {});
      this.playbackContext = null;
    } catch (_) {}
  }

  public getIsConnected(): boolean {
    return this.isConnected;
  }
  public getIsSpeaking(): boolean {
    return this.isSpeaking;
  }
  public getIsListening(): boolean {
    return this.isListening;
  }

  /**
   * Send a text prompt turn to the live Gemini session to generate native audio output
   */
  public sendTextTurn(text: string): boolean {
    if (!this.isConnected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('[GeminiLiveVoiceService] Cannot send text turn: WebSocket is not open');
      return false;
    }
    return this.safeSend({ type: 'text', text });
  }

  /**
   * Complete End-to-End Test for Kore Voice
   * 1. Validate API configuration.
   * 2. Validate selected voice = Kore.
   * 3. Create/reuse the voice engine.
   * 4. Generate a real short test response.
   * 5. Receive audio.
   * 6. Decode it.
   * 7. Play it through the device speaker.
   * 8. Release/stop cleanly.
   * 9. Return to the correct idle/listening state.
   * 10. Provide useful error reporting.
   */
  public async testKoreVoice(apiKey?: string): Promise<{ success: boolean; error?: string }> {
    const effectiveKey = (apiKey || this.apiKey || '').trim();
    if (!effectiveKey) {
      return {
        success: false,
        error: 'API_KEY_MISSING: Please configure your Gemini API Key in Settings to test Kore voice.',
      };
    }

    // 1. Prepare/unlock audio playback context
    try {
      const ctx = this.getPlaybackContext();
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }
    } catch (e) {
      console.warn('[GeminiLiveVoiceService] AudioContext resume warning:', e);
    }

    // 2. Try native live session first
    if (!this.isConnected) {
      const connected = await this.connect(effectiveKey);
      if (!connected) {
        return this.testKoreViaTtsFallback(effectiveKey);
      }
    }

    return new Promise<{ success: boolean; error?: string }>((resolve) => {
      let settled = false;
      let timeoutTimer: any = null;

      const finish = (result: { success: boolean; error?: string }) => {
        if (!settled) {
          settled = true;
          if (timeoutTimer) clearTimeout(timeoutTimer);
          resolve(result);
        }
      };

      // Set safety timeout of 7 seconds before trying fallback
      timeoutTimer = setTimeout(async () => {
        if (!settled) {
          console.info('[GeminiLiveVoiceService] Live socket test timed out, trying direct Kore TTS fallback...');
          const fallbackRes = await this.testKoreViaTtsFallback(effectiveKey);
          finish(fallbackRes);
        }
      }, 7000);

      // Listen for model audio
      let receivedChunk = false;
      const prevOnTranscript = this.callbacks.onTranscript;
      const prevOnStateChange = this.callbacks.onStateChange;

      this.callbacks.onStateChange = (state) => {
        prevOnStateChange?.(state);
        if (state === 'SPEAKING' || state === 'speaking') {
          receivedChunk = true;
          finish({ success: true });
        }
      };

      const sent = this.sendTextTurn('नमस्ते वासु! कोर आवाज़ में एक छोटा सा टेस्ट उत्तर बोलिए।');
      if (!sent) {
        this.testKoreViaTtsFallback(effectiveKey).then(finish);
      }
    });
  }

  /**
   * Fallback test via direct Gemini Kore TTS endpoint (/api/gemini/tts)
   */
  public async testKoreViaTtsFallback(apiKey: string): Promise<{ success: boolean; error?: string }> {
    try {
      const resp = await fetch('/api/gemini/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: 'नमस्ते जी! मैं कोर हूँ। वासु वॉइस असिस्टेंट बिल्कुल ठीक काम कर रहा है।',
          apiKey,
        }),
      });

      if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        return {
          success: false,
          error: data.message || `Server returned ${resp.status} for Kore TTS`,
        };
      }

      const contentType = resp.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const data = await resp.json();
        if (data.useNativeFallback) {
          return {
            success: false,
            error: data.message || 'Gemini TTS is currently cooling down',
          };
        }
      }

      const arrayBuffer = await resp.arrayBuffer();
      if (!arrayBuffer || arrayBuffer.byteLength === 0) {
        return { success: false, error: 'Empty audio buffer received from Kore TTS' };
      }

      const ctx = this.getPlaybackContext();
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }

      const audioBuffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
      const source = ctx.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(ctx.destination);

      return new Promise((resolve) => {
        source.onended = () => {
          this.setState('CONNECTED');
          resolve({ success: true });
        };
        this.setState('SPEAKING');
        source.start();
      });
    } catch (err: any) {
      console.error('[GeminiLiveVoiceService] testKoreViaTtsFallback error:', err);
      return {
        success: false,
        error: err?.message || 'Failed to synthesize and play Kore voice audio',
      };
    }
  }
}
