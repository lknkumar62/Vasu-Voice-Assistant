/**
 * GeminiLiveVoiceService — Orchestrator for Maya-style bidirectional voice
 * Uses: geminiLiveWebSocket (direct WS) + pcmAudioPlayer (chunked playback) + microphoneStreamer (mic capture)
 *
 * Pipeline: Mic → 16kHz PCM → Base64 → WebSocket → Gemini → 24kHz PCM → Speaker
 */

import { geminiLiveWebSocket, LiveWsState } from './geminiLiveWebSocket';
import { pcmAudioPlayer } from './pcmAudioPlayer';
import { microphoneStreamer } from './microphoneStreamer';

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

  private apiKey = '';
  private currentState: LiveConnectionState = 'DISCONNECTED';
  private selectedVoice: string = 'Kore';
  private callbacks: GeminiLiveCallbacks = {};
  private isSpeaking = false;
  private isListening = false;

  private constructor() {
    // Don't connect here — connect() is called explicitly with an API key
  }

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

  public setSelectedVoice(voice: string) {
    this.selectedVoice = voice || 'Kore';
  }

  public getSelectedVoice(): string {
    return this.selectedVoice;
  }

  private setState(s: LiveConnectionState) {
    if (this.currentState === s) return;
    this.currentState = s;
    this.callbacks.onStateChange?.(s);
  }

  public getState(): LiveConnectionState { return this.currentState; }
  public getIsConnected(): boolean { return geminiLiveWebSocket.isConnected(); }
  public getIsSpeaking(): boolean { return this.isSpeaking; }
  public getIsListening(): boolean { return this.isListening; }

  /**
   * Connect to Gemini Live WebSocket (direct, no proxy)
   */
  public async connect(apiKey?: string): Promise<boolean> {
    const key = (apiKey || this.apiKey).trim();
    if (!key) {
      this.callbacks.onError?.('API key missing');
      return false;
    }
    this.apiKey = key;
    this.setState('CONNECTING');
    pcmAudioPlayer.init();

    return new Promise<boolean>((resolve) => {
      let settled = false;
      const settle = (val: boolean) => {
        if (!settled) { settled = true; resolve(val); }
      };

      geminiLiveWebSocket.connect(
        {
          apiKey: key,
          voice: this.selectedVoice,
          model: 'gemini-2.5-flash',
          languageCode: 'hi-IN'
        },
        {
          onStateChange: (s) => {
            if (s === 'CONNECTED') {
              this.setState('CONNECTED');
              settle(true);
            } else if (s === 'ERROR') {
              this.setState('ERROR');
              settle(false);
            }
          },
          onAudioReceived: (pcm) => this.handleIncomingAudio(pcm),
          onTextReceived: (text, role) => this.callbacks.onTranscript?.(text, role === 'user'),
          onInterrupted: () => { this.interrupt(); this.callbacks.onInterrupted?.(); },
          onTurnComplete: () => { this.isSpeaking = false; this.setState('CONNECTED'); },
          onError: (err) => { this.callbacks.onError?.(err.message); settle(false); }
        }
      );

      // Timeout
      setTimeout(() => {
        if (!settled) {
          this.callbacks.onError?.('Connection timeout');
          settle(false);
        }
      }, 8000);
    });
  }

  /**
   * Send text turn to Gemini (typed input)
   */
  public sendTextTurn(text: string): boolean {
    if (!geminiLiveWebSocket.isConnected()) return false;
    this.setState('THINKING');
    geminiLiveWebSocket.sendText(text);
    return true;
  }

  /**
   * Start microphone streaming
   */
  public async startMicrophone(): Promise<boolean> {
    if (this.isListening) return true;
    const ok = await microphoneStreamer.start({
      onChunk: (b64) => {
        if (geminiLiveWebSocket.isConnected()) {
          geminiLiveWebSocket.sendAudio(b64);
        }
      },
      onStateChange: (s) => {
        if (s === 'STREAMING') {
          this.isListening = true;
          this.setState('LISTENING');
        }
      },
      onError: (err) => {
        this.callbacks.onError?.(err.message);
      }
    });
    return ok;
  }

  /**
   * Stop microphone capture
   */
  public stopMicrophone(): void {
    this.isListening = false;
    microphoneStreamer.stop();
  }

  /**
   * Interrupt ongoing playback and notification
   * Mirrors Maya's C2940lP0.b(): pause/flush/restart AudioTrack
   */
  public interrupt(): void {
    this.isSpeaking = false;
    pcmAudioPlayer.flush();
    geminiLiveWebSocket.disconnect();
    this.setState(this.isListening ? 'LISTENING' : 'CONNECTED');
  }

  /**
   * Disconnect everything
   */
  public disconnect(): void {
    this.stopMicrophone();
    pcmAudioPlayer.stop();
    geminiLiveWebSocket.disconnect();
    this.isSpeaking = false;
    this.isListening = false;
    this.setState('DISCONNECTED');
  }

  /**
   * Handle incoming PCM audio from Gemini
   * Mirrors Maya's C4456wN.j() → C2940lP0.a(byte[]) queue pattern
   */
  private handleIncomingAudio(pcmData: Uint8Array): void {
    this.isSpeaking = true;
    this.setState('SPEAKING');
    this.callbacks.onAudioChunk?.(pcmData);
    pcmAudioPlayer.enqueueChunk(pcmData);
  }

  /**
   * Map WebSocket state to service state
   */
  private handleWsStateChange(wsState: LiveWsState): void {
    switch (wsState) {
      case 'CONNECTING': this.setState('CONNECTING'); break;
      case 'CONNECTED': this.setState('CONNECTED'); break;
      case 'STREAMING': this.setState('SPEAKING'); break;
      case 'ERROR': this.setState('ERROR'); break;
      case 'DISCONNECTED': this.setState('DISCONNECTED'); break;
    }
  }

  /**
   * Test Kore voice end-to-end
   */
  public async testKoreVoice(apiKey?: string): Promise<{ success: boolean; error?: string }> {
    const effectiveKey = (apiKey || this.apiKey).trim();
    if (!effectiveKey) {
      return { success: false, error: 'API key missing' };
    }

    pcmAudioPlayer.init();

    const connected = await this.connect(effectiveKey);
    if (!connected) {
      return { success: false, error: 'Failed to connect to Gemini Live' };
    }

    return new Promise((resolve) => {
      let settled = false;
      const finish = (r: { success: boolean; error?: string }) => {
        if (!settled) { settled = true; resolve(r); }
      };

      const timeout = setTimeout(() => finish({ success: false, error: 'Timeout waiting for audio' }), 10000);

      const origOnAudio = this.callbacks.onAudioChunk;
      this.callbacks.onAudioChunk = (pcm) => {
        origOnAudio?.(pcm);
        if (!settled) {
          clearTimeout(timeout);
          finish({ success: true });
        }
      };

      this.sendTextTurn('Namaste! Ek chhota sa test bol dijiye.');
    });
  }
}

export const geminiLiveVoiceService = GeminiLiveVoiceService.getInstance();
