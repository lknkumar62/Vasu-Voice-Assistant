/**
 * GeminiLiveSession - Manages WebSocket connection to Gemini Live API.
 *
 * Lifecycle:
 * CONNECTING -> OPEN -> SESSION CONFIGURED -> READY -> STREAMING
 *
 * Enforces:
 * - Never calls websocket.send() before the socket is OPEN.
 * - Every send operation verifies the real OPEN state.
 * - Handles onopen, onmessage, onerror, onclose cleanly.
 * - No unhandled Promise rejection is allowed.
 */

export type LiveSessionState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'OPEN'
  | 'SESSION_CONFIGURED'
  | 'READY'
  | 'STREAMING'
  | 'ERROR';

export interface GeminiLiveSessionCallbacks {
  onStateChange: (state: LiveSessionState) => void;
  onAudioChunk: (pcmBytes: Uint8Array) => void;
  onTranscript: (text: string) => void;
  onInterrupted: () => void;
  onTurnComplete?: () => void;
  onError: (errorMsg: string) => void;
}

export class GeminiLiveSession {
  private ws: WebSocket | null = null;
  private state: LiveSessionState = 'DISCONNECTED';
  private callbacks: GeminiLiveSessionCallbacks;

  public static readonly TARGET_VOICE = 'Erinome';
  public static readonly LIVE_MODEL = 'models/gemini-2.0-flash-exp';

  private readyResolver: ((success: boolean) => void) | null = null;

  constructor(callbacks: GeminiLiveSessionCallbacks) {
    this.callbacks = callbacks;
  }

  public getState(): LiveSessionState {
    return this.state;
  }

  private setState(newState: LiveSessionState): void {
    this.state = newState;
    this.callbacks.onStateChange(newState);
  }

  /**
   * Connect to Gemini Live WebSocket.
   * Can connect directly via API key, or via the local server proxy (/api/live-ws).
   * Resolves only after WebSocket is OPEN, setup is sent, and setupComplete is received (READY).
   */
  public connect(apiKey?: string, systemPrompt?: string): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      try {
        if (this.ws && this.ws.readyState === WebSocket.OPEN && this.state === 'READY') {
          console.log('[VASU] Gemini session already OPEN and READY');
          resolve(true);
          return;
        }

        this.disconnect();

        this.readyResolver = resolve;
        this.setState('CONNECTING');
        console.log('[VASU] Gemini session connecting');

        // Determine URL: direct or proxy
        let url: string;
        if (apiKey && apiKey.trim()) {
          url = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${encodeURIComponent(apiKey.trim())}`;
        } else {
          // Connect to backend server proxy
          const loc = window.location;
          const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
          url = `${proto}//${loc.host}/api/live-ws`;
        }

        const socket = new WebSocket(url);
        this.ws = socket;

        socket.onopen = () => {
          this.setState('OPEN');
          console.log('[VASU] Gemini session OPEN');

          // Send setup configuration immediately once OPEN
          this.sendSetup(systemPrompt);
        };

        socket.onmessage = (event) => {
          this.handleMessage(event.data);
        };

        socket.onerror = (event) => {
          console.error('[VASU] Gemini session WebSocket error:', event);
          this.setState('ERROR');
          this.callbacks.onError('WebSocket connection error');
          if (this.readyResolver) {
            this.readyResolver(false);
            this.readyResolver = null;
          }
        };

        socket.onclose = (event) => {
          console.log(`[VASU] Gemini session closed (code=${event.code}, wasClean=${event.wasClean})`);
          this.setState('DISCONNECTED');
          if (this.readyResolver) {
            this.readyResolver(false);
            this.readyResolver = null;
          }
        };
      } catch (err: any) {
        console.error('[VASU] Exception during WebSocket connection setup:', err?.message || err);
        this.setState('ERROR');
        this.callbacks.onError(err?.message || 'Connection failed');
        if (this.readyResolver) {
          this.readyResolver(false);
          this.readyResolver = null;
        }
      }
    });
  }

  /**
   * Send initial session configuration to Gemini Live.
   */
  private sendSetup(customSystemInstruction?: string): void {
    if (!this.isOpen()) {
      console.warn('[VASU] Cannot send setup: socket is not OPEN');
      return;
    }

    const defaultInstruction =
      'You are VASU, a natural real-time voice assistant.\n' +
      'Speak naturally and conversationally.\n' +
      "Respond in the user's language.\n" +
      'If the user speaks Hindi, respond in Hindi.\n' +
      'If the user speaks Hinglish, respond naturally in Hinglish.\n' +
      'Keep simple answers concise.\n' +
      "Do not unnecessarily repeat the user's words.\n" +
      'Do not mention internal APIs, models, WebSockets, or implementation details.\n\n' +
      'तुम VASU हो, एंड्रॉइड के लिए एक कुशल, स्नेही और बुद्धिमान AI वॉइस असिस्टेंट।\n' +
      'सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की भाषा में उत्तर दो।\n' +
      'उत्तर संक्षिप्त, स्पष्ट एवं स्वाभाविक रखो जिसे सीधे बोला जा सके।';

    const systemText = (customSystemInstruction && customSystemInstruction.trim()) || defaultInstruction;

    const setupMessage = {
      setup: {
        model: GeminiLiveSession.LIVE_MODEL,
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: GeminiLiveSession.TARGET_VOICE,
              },
            },
          },
        },
        systemInstruction: {
          parts: [{ text: systemText }],
        },
      },
    };

    const sent = this.sendRaw(JSON.stringify(setupMessage));
    if (sent) {
      this.setState('SESSION_CONFIGURED');
      console.log('[VASU] Gemini session configured');
    }
  }

  private handleMessage(data: string | ArrayBuffer): void {
    try {
      if (typeof data !== 'string') {
        return;
      }

      const message = JSON.parse(data);

      if (message._vasu_proxy === 'UPSTREAM_OPEN') {
        // Backend proxy upstream connection ready
        return;
      }

      // 1. Setup complete -> READY
      if (message.setupComplete) {
        this.setState('READY');
        console.log('[VASU] Gemini session setupComplete: READY');
        if (this.readyResolver) {
          this.readyResolver(true);
          this.readyResolver = null;
        }
        return;
      }

      // 2. Server content
      if (message.serverContent) {
        const { modelTurn, turnComplete, interrupted } = message.serverContent;

        if (interrupted) {
          console.log('[VASU] Gemini interruption received');
          this.callbacks.onInterrupted();
        }

        if (modelTurn && Array.isArray(modelTurn.parts)) {
          for (const part of modelTurn.parts) {
            // Text transcription
            if (part.text) {
              this.callbacks.onTranscript(part.text);
            }

            // Native 24 kHz 16-bit PCM Audio chunk
            if (part.inlineData && part.inlineData.data) {
              const rawData = part.inlineData.data;
              const binary = atob(rawData);
              const bytes = new Uint8Array(binary.length);
              for (let i = 0; i < binary.length; i++) {
                bytes[i] = binary.charCodeAt(i);
              }
              console.log('[VASU] Audio response received');
              console.log(`[VASU] Audio bytes received: ${bytes.byteLength}`);
              this.callbacks.onAudioChunk(bytes);
            }
          }
        }

        if (turnComplete) {
          this.callbacks.onTurnComplete?.();
        }
      }
    } catch (err: any) {
      console.error('[VASU] Failed to parse Gemini Live message:', err?.message || err);
    }
  }

  /**
   * Send text turn (e.g. for TEXT-ONLY test).
   */
  public sendText(text: string): boolean {
    if (!this.isOpen()) {
      console.warn('[VASU] Cannot send text: socket is not OPEN');
      return false;
    }

    const clientContent = {
      clientContent: {
        turns: [
          {
            role: 'user',
            parts: [{ text: text }],
          },
        ],
        turnComplete: true,
      },
    };

    return this.sendRaw(JSON.stringify(clientContent));
  }

  /**
   * Send real-time microphone 16 kHz 16-bit PCM audio chunk.
   */
  public sendAudioChunk(base64PcmChunk: string): boolean {
    if (!this.isOpen()) {
      return false;
    }

    const realtimeInput = {
      realtimeInput: {
        mediaChunks: [
          {
            mimeType: 'audio/pcm;rate=16000',
            data: base64PcmChunk,
          },
        ],
      },
    };

    const sent = this.sendRaw(JSON.stringify(realtimeInput));
    if (sent) {
      this.setState('STREAMING');
      console.log('[VASU] Audio chunk sent');
    }
    return sent;
  }

  /**
   * Low-level send with strict OPEN verification.
   */
  private sendRaw(data: string): boolean {
    if (!this.isOpen()) {
      console.warn('[VASU] sendRaw dropped message: WebSocket is not in OPEN state');
      return false;
    }
    try {
      this.ws!.send(data);
      return true;
    } catch (err: any) {
      console.error('[VASU] Error sending WebSocket data:', err?.message || err);
      return false;
    }
  }

  public isOpen(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  public disconnect(): void {
    if (this.ws) {
      try {
        this.ws.close(1000, 'Normal disconnect');
      } catch {
        // Ignored
      }
      this.ws = null;
    }
    this.setState('DISCONNECTED');
  }
}
