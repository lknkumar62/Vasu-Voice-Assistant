/**
 * VoiceManager — Single Authoritative Voice Engine for VASU Assistant
 * 
 * Central coordinator for:
 * - Selected voice ('Kore' primary)
 * - Voice modes ('GEMINI_LIVE' | 'TTS' | 'OFFLINE')
 * - Authoritative state machine:
 *     OFF -> STARTING -> LISTENING -> ACTIVATING -> ACTIVE -> SPEAKING -> STOPPING -> ERROR
 * - Native audio pipeline coordination (Gemini Live 24kHz PCM, Web Audio decoding, speaker playback)
 * - Wake word listening lifecycle & background foreground service coordination
 * - Fast intent routing & direct device control execution
 * - Microphone/speaker isolation to eliminate self-triggering reverberation
 */

import { audioEngine } from './audioEngine';
import { GeminiLiveVoiceService } from './geminiLiveVoiceService';
import { wakeWordEngine, WakeWordStatus } from './wakeWordEngine';
import { speechRecognizer } from './speechRecognizer';
import { LocalCommandEngine, LocalCommandMatch } from './localCommandEngine';
import { executeToolCall } from './toolRegistry';
import { geminiLiveWebSocket } from './geminiLiveWebSocket';
import { pcmAudioPlayer } from './pcmAudioPlayer';
import { microphoneStreamer } from './microphoneStreamer';

export type VasuVoiceState =
  | 'OFF'
  | 'STARTING'
  | 'LISTENING'
  | 'ACTIVATING'
  | 'ACTIVE'
  | 'SPEAKING'
  | 'STOPPING'
  | 'ERROR';

export type VoiceMode = 'GEMINI_LIVE' | 'TTS' | 'OFFLINE';

export interface VoiceManagerState {
  state: VasuVoiceState;
  selectedVoice: string;
  voiceMode: VoiceMode;
  isWakeWordActive: boolean;
  isListening: boolean;
  isSpeaking: boolean;
  isLiveConnected: boolean;
  errorMessage: string | null;
  audioLevel: number;
}

export type VoiceStateListener = (state: VoiceManagerState) => void;

export class VoiceManager {
  private static instance: VoiceManager;

  private state: VasuVoiceState = 'OFF';
  private selectedVoice: string = 'Kore';
  private voiceMode: VoiceMode = 'GEMINI_LIVE';
  private errorMessage: string | null = null;
  private audioLevel: number = 0;
  private apiKey: string = '';

  private listeners: Set<VoiceStateListener> = new Set();
  private liveService: GeminiLiveVoiceService;

  private constructor() {
    this.liveService = GeminiLiveVoiceService.getInstance();
    this.setupInternalListeners();
  }

  public static getInstance(): VoiceManager {
    if (!VoiceManager.instance) {
      VoiceManager.instance = new VoiceManager();
    }
    return VoiceManager.instance;
  }

  public setApiKey(key: string) {
    this.apiKey = key.trim();
    this.liveService.setApiKey(this.apiKey);
  }

  public getApiKey(): string {
    return this.apiKey || audioEngine.getApiKey();
  }

  public subscribe(listener: VoiceStateListener): () => void {
    this.listeners.add(listener);
    listener(this.getSnapshot());
    return () => this.listeners.delete(listener);
  }

  public getSnapshot(): VoiceManagerState {
    return {
      state: this.state,
      selectedVoice: this.selectedVoice,
      voiceMode: this.voiceMode,
      isWakeWordActive: wakeWordEngine.getStatus() === 'LISTENING',
      isListening: this.state === 'LISTENING' || this.state === 'ACTIVE' || microphoneStreamer.isStreaming(),
      isSpeaking: this.state === 'SPEAKING' || audioEngine.isSpeaking() || this.liveService.getIsSpeaking() || pcmAudioPlayer.isActive(),
      isLiveConnected: geminiLiveWebSocket.isConnected() || this.liveService.getIsConnected(),
      errorMessage: this.errorMessage,
      audioLevel: this.audioLevel,
    };
  }

  private notify() {
    const snapshot = this.getSnapshot();
    for (const listener of this.listeners) {
      try {
        listener(snapshot);
      } catch (err) {
        console.error('[VoiceManager] Listener callback error:', err);
      }
    }
  }

  private setState(newState: VasuVoiceState, error: string | null = null) {
    if (this.state === newState && this.errorMessage === error) return;
    this.state = newState;
    if (error !== null) this.errorMessage = error;
    if (newState !== 'ERROR' && error === null) this.errorMessage = null;
    this.notify();
  }

  private setupInternalListeners() {
    // 1. Listen for Live WebSocket state transitions
    this.liveService.setCallbacks({
      onStateChange: (liveState) => {
        if (liveState === 'SPEAKING' || liveState === 'speaking') {
          this.setState('SPEAKING');
        } else if (liveState === 'LISTENING') {
          if (this.state !== 'SPEAKING') {
            this.setState('ACTIVE');
          }
        } else if (liveState === 'CONNECTED' && this.state === 'SPEAKING') {
          // Return to listening if wake word was active
          if (wakeWordEngine.getStatus() === 'LISTENING') {
            this.setState('LISTENING');
          } else {
            this.setState('OFF');
          }
        } else if (liveState === 'ERROR') {
          this.setState('ERROR', 'Gemini Live Session encountered an issue');
        }
      },
      onError: (err) => {
        this.setState('ERROR', err);
      },
    });

    // 2. Listen for Wake Word Engine events
    wakeWordEngine.setCallbacks(
      (event) => {
        console.info('[VoiceManager] Wake word detected:', event);
        this.setState('ACTIVATING');
        setTimeout(() => {
          this.setState('ACTIVE');
        }, 150);
      },
      (level) => {
        this.audioLevel = level;
        this.notify();
      }
    );
  }

  /**
   * Complete End-to-End Test of Kore Voice
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
  public async testKoreVoice(apiKey?: string): Promise<{ success: boolean; message?: string; error?: string }> {
    const effectiveKey = (apiKey || this.getApiKey()).trim();
    if (!effectiveKey) {
      const err = 'GEMINI_API_ERROR: API Key is not configured. Please add your key in Settings.';
      this.setState('ERROR', err);
      return { success: false, error: err };
    }

    // Ensure voice is Kore
    this.selectedVoice = 'Kore';
    this.setState('STARTING');

    try {
      // 1. Unlock Web Audio Context
      audioEngine.unlock();

      // 2. Call liveService test
      this.setState('SPEAKING');
      const result = await this.liveService.testKoreVoice(effectiveKey);

      if (result.success) {
        // Return to listening or off state
        const targetState = wakeWordEngine.getStatus() === 'LISTENING' ? 'LISTENING' : 'OFF';
        this.setState(targetState);
        return {
          success: true,
          message: 'Kore 24kHz audio played successfully through device speaker.',
        };
      }

      // Fallback via AudioEngine speakAssistantResponse
      console.warn('[VoiceManager] Live test failed, falling back to AudioEngine Kore synthesis:', result.error);
      await audioEngine.speakAssistantResponse(
        'नमस्ते! मैं कोर हूँ। वासु असिस्टेंट बिल्कुल सक्रिय और तैयार है।',
        {
          forceKoreVoice: true,
          apiKey: effectiveKey,
          onStart: () => this.setState('SPEAKING'),
          onEnd: () => {
            const targetState = wakeWordEngine.getStatus() === 'LISTENING' ? 'LISTENING' : 'OFF';
            this.setState(targetState);
          },
        }
      );

      return {
        success: true,
        message: 'Kore voice synthesized and played via speech fallback.',
      };
    } catch (e: any) {
      console.error('[VoiceManager] testKoreVoice exception:', e);
      const err = e?.message || 'AUDIO_PLAYBACK_ERROR: Failed to play Kore voice audio.';
      this.setState('ERROR', err);
      return { success: false, error: err };
    }
  }

  /**
   * Speak an assistant response — Maya-style pipeline:
   * 1. If Gemini Live WebSocket connected → send text for streaming TTS
   * 2. Otherwise → fallback to audioEngine TTS chain
   */
  public async speak(text: string, options?: { apiKey?: string; onStart?: () => void; onEnd?: () => void }): Promise<void> {
    this.setState('SPEAKING');
    wakeWordEngine.pause();

    // Try Maya-style WebSocket TTS first (streaming PCM)
    if (geminiLiveWebSocket.isConnected()) {
      try {
        pcmAudioPlayer.init();
        geminiLiveWebSocket.sendText(text);
        console.log('[VoiceManager] Sent text via Gemini Live WebSocket TTS');
        // Wait for audio to drain
        await new Promise<void>((resolve) => {
          const timeout = setTimeout(resolve, 15000);
          pcmAudioPlayer.onDrained(() => {
            clearTimeout(timeout);
            resolve();
          });
        });
        options?.onEnd?.();
        setTimeout(() => {
          if (wakeWordEngine.getStatus() === 'PAUSED' || wakeWordEngine.getStatus() === 'LISTENING') {
            wakeWordEngine.resume();
            this.setState('LISTENING');
          } else {
            this.setState('OFF');
          }
        }, 350);
        return;
      } catch (e) {
        console.warn('[VoiceManager] WebSocket TTS failed, falling back:', e);
      }
    }

    // Fallback to audioEngine TTS chain
    try {
      await audioEngine.speakAssistantResponse(text, {
        apiKey: options?.apiKey || this.getApiKey(),
        forceKoreVoice: this.selectedVoice === 'Kore',
        onStart: () => {
          this.setState('SPEAKING');
          options?.onStart?.();
        },
        onEnd: () => {
          options?.onEnd?.();
          setTimeout(() => {
            if (wakeWordEngine.getStatus() === 'PAUSED' || wakeWordEngine.getStatus() === 'LISTENING') {
              wakeWordEngine.resume();
              this.setState('LISTENING');
            } else {
              this.setState('OFF');
            }
          }, 350);
        },
      });
    } catch (err: any) {
      console.error('[VoiceManager] speak error:', err);
      this.setState('ERROR', err?.message || 'Speech output failed');
    }
  }

  /**
   * Fast command parsing and direct native action execution
   */
  public async executeCommand(transcript: string): Promise<{ handled: boolean; response: string; toolId?: string }> {
    const match = LocalCommandEngine.parse(transcript, { allowConversationalMatches: true });
    if (!match.matched) {
      return { handled: false, response: '' };
    }

    let spokenResponse = match.spokenResponse || '';

    // Execute device control tool if matched
    if (match.toolId) {
      try {
        const toolResult = await executeToolCall(match.toolId, match.args || {});
        if (toolResult && toolResult.result) {
          // If tool produced specific feedback and no spoken response, use it
          if (!spokenResponse) {
            spokenResponse = toolResult.result;
          }
        }
      } catch (toolErr) {
        console.warn('[VoiceManager] Tool execution error:', toolErr);
        spokenResponse = 'Command execute karne mein samasya aayi.';
      }
    }

    if (spokenResponse) {
      await this.speak(spokenResponse);
    }

    return {
      handled: true,
      response: spokenResponse,
      toolId: match.toolId,
    };
  }

  /**
   * Start wake word background listener ("Hello VASU")
   */
  public async startWakeWord(): Promise<boolean> {
    this.setState('STARTING');
    try {
      const status = await wakeWordEngine.start();
      if (status === 'LISTENING') {
        this.setState('LISTENING');
        return true;
      } else if (status === 'PERMISSION_REQUIRED') {
        this.setState('ERROR', 'MIC_PERMISSION_ERROR: Microphone permission is required');
        return false;
      } else {
        this.setState('ERROR', 'WAKEWORD_MODEL_ERROR: Speech recognizer not available');
        return false;
      }
    } catch (e: any) {
      this.setState('ERROR', e?.message || 'Failed to start wake word listener');
      return false;
    }
  }

  /**
   * Stop wake word listening
   */
  public stopWakeWord(): void {
    this.setState('STOPPING');
    wakeWordEngine.stop();
    this.setState('OFF');
  }

  /**
   * Toggle wake word on/off
   */
  public async toggleWakeWord(): Promise<boolean> {
    if (this.state === 'LISTENING' || wakeWordEngine.getStatus() === 'LISTENING') {
      this.stopWakeWord();
      return false;
    } else {
      return this.startWakeWord();
    }
  }

  /**
   * Interrupt ongoing speech or listening — Maya-style: flush AudioTrack + clear queue
   */
  public interrupt(): void {
    try { pcmAudioPlayer.flush(); } catch (_) {}
    this.liveService.interrupt();
    audioEngine.stop();
    this.setState(wakeWordEngine.getStatus() === 'LISTENING' ? 'LISTENING' : 'OFF');
  }

  public getSelectedVoice(): string {
    return this.selectedVoice;
  }

  public setSelectedVoice(voice: string) {
    this.selectedVoice = voice;
    this.notify();
  }
}

export const voiceManager = VoiceManager.getInstance();
