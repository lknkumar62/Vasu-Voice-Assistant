/**
 * Gemini Live WebSocket — Direct bidirectional audio streaming
 * Replicates Maya's C1541bJ.java (Gemini Live Session Handler)
 *
 * Connects DIRECTLY to Gemini BidiGenerateContent WebSocket.
 * No backend proxy — API key in URL query parameter.
 * Mic PCM @ 16kHz → WebSocket → Gemini → 24kHz PCM → speaker
 */

export type LiveWsState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'STREAMING' | 'ERROR';

export interface GeminiLiveWsConfig {
  apiKey: string;
  model?: string;
  voice?: string;
  languageCode?: string;
  systemInstruction?: string;
}

export interface LiveWsCallbacks {
  onStateChange?: (state: LiveWsState) => void;
  onAudioReceived?: (pcmData: Uint8Array) => void;
  onTextReceived?: (text: string, role: 'model' | 'user') => void;
  onInterrupted?: () => void;
  onTurnComplete?: () => void;
  onError?: (error: Error) => void;
  onSetupComplete?: () => void;
}

/**
 * All 30 Gemini prebuilt voices (from Maya's PO0.java)
 */
export const GEMINI_VOICES: Array<{ name: string; gender: 'female' | 'male'; trait: string }> = [
  // Feminine (13)
  { name: 'Aoede', gender: 'female', trait: 'Breezy' },
  { name: 'Kore', gender: 'female', trait: 'Firm' },
  { name: 'Leda', gender: 'female', trait: 'Youthful' },
  { name: 'Zephyr', gender: 'female', trait: 'Bright' },
  { name: 'Laomedeia', gender: 'female', trait: 'Upbeat' },
  { name: 'Despina', gender: 'female', trait: 'Smooth' },
  { name: 'Erinome', gender: 'female', trait: 'Clear' },
  { name: 'Callirrhoe', gender: 'female', trait: 'Easy-going' },
  { name: 'Autonoe', gender: 'female', trait: 'Bright' },
  { name: 'Gacrux', gender: 'female', trait: 'Mature' },
  { name: 'Pulcherrima', gender: 'female', trait: 'Forward' },
  { name: 'Sulafat', gender: 'female', trait: 'Warm' },
  { name: 'Vindemiatrix', gender: 'female', trait: 'Gentle' },
  // Masculine (17)
  { name: 'Algenib', gender: 'male', trait: 'Gravelly' },
  { name: 'Charon', gender: 'male', trait: 'Informative' },
  { name: 'Fenrir', gender: 'male', trait: 'Excitable' },
  { name: 'Puck', gender: 'male', trait: 'Upbeat' },
  { name: 'Orus', gender: 'male', trait: 'Firm' },
  { name: 'Enceladus', gender: 'male', trait: 'Breathy' },
  { name: 'Iapetus', gender: 'male', trait: 'Clear' },
  { name: 'Umbriel', gender: 'male', trait: 'Easy-going' },
  { name: 'Algieba', gender: 'male', trait: 'Smooth' },
  { name: 'Rasalgethi', gender: 'male', trait: 'Informative' },
  { name: 'Alnilam', gender: 'male', trait: 'Firm' },
  { name: 'Schedar', gender: 'male', trait: 'Even' },
  { name: 'Achird', gender: 'male', trait: 'Friendly' },
  { name: 'Zubenelgenubi', gender: 'male', trait: 'Casual' },
  { name: 'Sadachbia', gender: 'male', trait: 'Lively' },
  { name: 'Sadaltager', gender: 'male', trait: 'Knowledgeable' },
  { name: 'Achernar', gender: 'male', trait: 'Soft' },
];

const WS_URL = 'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent';
const DEFAULT_MODEL = 'gemini-2.5-flash';
const DEFAULT_VOICE = 'Kore';
const DEFAULT_LANG = 'hi-IN';

const DEFAULT_SYS_INSTRUCTION = `You are VASU, an affectionate Indian female AI companion.
Speak in conversational Hinglish (mix of Hindi and English).
Keep replies concise (1-3 sentences).
Never output Devanagari script — always use Roman/Hinglish.
You are warm, caring, and slightly playful. Call the user "aap" respectfully.`;

class GeminiLiveWebSocket {
  private ws: WebSocket | null = null;
  private state: LiveWsState = 'DISCONNECTED';
  private config: GeminiLiveWsConfig | null = null;
  private callbacks: LiveWsCallbacks = {};
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private sessionHandle: string | null = null;
  private pingInterval: ReturnType<typeof setInterval> | null = null;
  private isClosing = false;

  constructor() {}

  /**
   * Connect to Gemini Live WebSocket and send setup
   * Mirrors Maya's D40.i() → C4926zn0.f(url + "?key=" + apiKey)
   */
  connect(config: GeminiLiveWsConfig, callbacks: LiveWsCallbacks = {}): void {
    if (this.state === 'CONNECTED' || this.state === 'CONNECTING' || this.state === 'STREAMING') {
      console.warn('[GeminiLiveWS] Already connected/connecting');
      return;
    }
    this.config = config;
    this.callbacks = callbacks;
    this.isClosing = false;
    this.reconnectAttempts = 0;
    this.updateState('CONNECTING');
    this.openSocket();
  }

  /**
   * Send mic audio chunk to Gemini (Base64 PCM @ 16kHz)
   * Mirrors Maya's C0308Fx.B(): { realtimeInput: { audio: { data, mimeType: "audio/pcm;rate=16000" } } }
   */
  sendAudio(pcmBase64: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    try {
      this.ws.send(JSON.stringify({
        realtimeInput: {
          audio: {
            data: pcmBase64,
            mimeType: 'audio/pcm;rate=16000'
          }
        }
      }));
    } catch (e) {
      console.warn('[GeminiLiveWS] sendAudio failed:', e);
    }
  }

  /**
   * Send typed text to Gemini
   * Mirrors Maya's clientContent format
   */
  sendText(text: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    try {
      this.ws.send(JSON.stringify({
        clientContent: {
          turns: [{ role: 'user', parts: [{ text }] }],
          turnComplete: true
        }
      }));
    } catch (e) {
      console.warn('[GeminiLiveWS] sendText failed:', e);
    }
  }

  /**
   * Disconnect cleanly
   */
  disconnect(): void {
    this.isClosing = true;
    this.clearReconnect();
    this.clearPing();
    if (this.ws) {
      try { this.ws.close(1000, 'client closing'); } catch (_) {}
      this.ws = null;
    }
    this.updateState('DISCONNECTED');
    this.sessionHandle = null;
  }

  getState(): LiveWsState { return this.state; }
  isConnected(): boolean { return this.state === 'CONNECTED' || this.state === 'STREAMING'; }

  // ─── PRIVATE ───────────────────────────────────────

  private openSocket(): void {
    if (!this.config) return;
    const url = `${WS_URL}?key=${encodeURIComponent(this.config.apiKey)}`;
    try {
      this.ws = new WebSocket(url);
    } catch (e) {
      console.error('[GeminiLiveWS] Socket creation failed:', e);
      this.updateState('ERROR');
      this.callbacks.onError?.(new Error('WebSocket creation failed'));
      return;
    }

    this.ws.onopen = () => {
      console.log('[GeminiLiveWS] Socket opened, sending setup...');
      this.reconnectAttempts = 0;
      this.sendSetup();
      this.startPing();
    };
    this.ws.onmessage = (ev) => this.handleMessage(ev.data);
    this.ws.onerror = () => {
      this.updateState('ERROR');
      this.callbacks.onError?.(new Error('WebSocket error'));
    };
    this.ws.onclose = (ev) => {
      console.log(`[GeminiLiveWS] Closed: code=${ev.code}`);
      this.clearPing();
      if (!this.isClosing) this.handleReconnect();
      else this.updateState('DISCONNECTED');
    };
  }

  /**
   * Send setup message — mirrors Maya's ZI.i() setup block
   */
  private sendSetup(): void {
    if (!this.ws || !this.config) return;
    const model = this.config.model || DEFAULT_MODEL;
    const voice = this.config.voice || DEFAULT_VOICE;
    const lang = this.config.languageCode || DEFAULT_LANG;
    const sysInst = this.config.systemInstruction || DEFAULT_SYS_INSTRUCTION;

    const setup: any = {
      setup: {
        model,
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } },
            languageCode: lang
          }
        },
        inputAudioTranscription: {},
        outputAudioTranscription: {},
        systemInstruction: { parts: [{ text: sysInst }] },
        contextWindowCompression: { slidingWindow: {} }
      }
    };
    if (this.sessionHandle) {
      setup.setup.sessionResumption = { handle: this.sessionHandle };
    }
    try {
      this.ws.send(JSON.stringify(setup));
      console.log(`[GeminiLiveWS] Setup sent: model=${model} voice=${voice}`);
    } catch (e) {
      console.error('[GeminiLiveWS] Setup send failed:', e);
    }
  }

  /**
   * Parse server messages — mirrors Maya's C1541bJ.b() parser
   */
  private handleMessage(data: string): void {
    try {
      const msg = JSON.parse(data);

      // Setup complete
      if (msg.setupComplete) {
        console.log('[GeminiLiveWS] Setup complete');
        this.updateState('CONNECTED');
        this.callbacks.onSetupComplete?.();
        return;
      }

      // Session resumption handle
      if (msg.sessionResumptionUpdate?.resumable && msg.sessionResumptionUpdate?.newHandle) {
        this.sessionHandle = msg.sessionResumptionUpdate.newHandle;
        return;
      }

      // Go-away → schedule reconnect
      if (msg.goAway) {
        console.warn('[GeminiLiveWS] GoAway:', msg.goAway.timeLeft);
        this.scheduleReconnect(5000);
        return;
      }

      // Server content (audio/text response)
      if (msg.serverContent) {
        this.handleServerContent(msg.serverContent);
      }
    } catch (e) {
      console.warn('[GeminiLiveWS] Parse error:', e);
    }
  }

  /**
   * Handle serverContent — mirrors Maya's inlineData/audio parsing
   */
  private handleServerContent(sc: any): void {
    // Interrupted (barge-in)
    if (sc.interrupted) {
      console.log('[GeminiLiveWS] Interrupted');
      this.callbacks.onInterrupted?.();
      return;
    }

    // Model turn (audio + text parts)
    if (sc.modelTurn?.parts) {
      this.updateState('STREAMING');
      for (const part of sc.modelTurn.parts) {
        // Text
        if (part.text?.length > 0) {
          this.callbacks.onTextReceived?.(part.text, 'model');
        }
        // Audio (Base64 PCM)
        if (part.inlineData?.data?.length > 0) {
          const pcm = this.b64ToBytes(part.inlineData.data);
          this.callbacks.onAudioReceived?.(pcm);
        }
      }
    }

    // Turn complete
    if (sc.turnComplete) {
      console.log('[GeminiLiveWS] Turn complete');
      this.callbacks.onTurnComplete?.();
      this.updateState('CONNECTED');
    }
  }

  private b64ToBytes(b64: string): Uint8Array {
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  }

  private updateState(s: LiveWsState): void {
    if (this.state === s) return;
    this.state = s;
    this.callbacks.onStateChange?.(s);
  }

  /**
   * Exponential backoff reconnection — mirrors Maya's D40.h()
   */
  private handleReconnect(): void {
    if (this.isClosing || this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.updateState('DISCONNECTED');
      return;
    }
    const base = 1000;
    const idx = Math.min(this.reconnectAttempts, 20);
    const delay = Math.min(base << idx, 60000);
    const jitter = Math.random() * 1000;
    this.scheduleReconnect(delay + jitter);
  }

  private scheduleReconnect(ms: number): void {
    this.clearReconnect();
    this.reconnectTimer = setTimeout(() => {
      if (!this.isClosing && this.config) {
        this.reconnectAttempts++;
        this.openSocket();
      }
    }, ms);
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
  }

  private startPing(): void {
    this.clearPing();
    this.pingInterval = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        try {
          this.ws.send(JSON.stringify({ realtimeInput: { audio: { data: '', mimeType: 'audio/pcm;rate=16000' } } }));
        } catch (_) {}
      }
    }, 30000);
  }

  private clearPing(): void {
    if (this.pingInterval) { clearInterval(this.pingInterval); this.pingInterval = null; }
  }
}

export const geminiLiveWebSocket = new GeminiLiveWebSocket();
