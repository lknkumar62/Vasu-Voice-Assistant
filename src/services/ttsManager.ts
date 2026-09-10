/**
 * TTS Manager — Centralized text-to-speech controller for VASU Voice Assistant.
 *
 * Responsibilities:
 * 1. Text normalization (strip markdown, code, URLs, UI artifacts before speaking)
 * 2. Auto-speak toggle (speak all responses automatically or only on explicit request)
 * 3. Provider abstraction (Gemini Kore → browser TTS → silent fallback)
 * 4. State tracking (speaking, current text, interruption)
 *
 * Delegates actual audio playback to audioEngine — does NOT duplicate audio logic.
 */

import { audioEngine, AudioEngineState } from './audioEngine';

export type TTSState = 'IDLE' | 'SPEAKING' | 'PAUSED';

export type TTSProvider = 'gemini_kore' | 'browser_native' | 'preloaded_clip';

interface TTSManagerOptions {
  autoSpeak: boolean;
  speed: number;
  pitch: number;
  volume: number;
  apiKey: string;
}

class TTSManager {
  private state: TTSState = 'IDLE';
  private currentText: string = '';
  private listeners: Set<(state: TTSState) => void> = new Set();
  private stateUnsub: (() => void) | null = null;
  private lastSpokenId: string = '';

  constructor() {
    this.stateUnsub = audioEngine.onStateChange((st: AudioEngineState) => {
      const newTTSState: TTSState = st === 'SPEAKING' ? 'SPEAKING' : 'IDLE';
      if (newTTSState !== this.state) {
        this.state = newTTSState;
        this.notifyListeners();
      }
    });
  }

  /**
   * Normalize text for natural speech output.
   * Strips markdown, code blocks, URLs, UI-only formatting, tool internals.
   * Converts bullet lists to spoken form.
   */
  normalizeForSpeech(text: string): string {
    if (!text) return '';

    let t = text;

    // 1. Strip prompt injection markers
    t = t.replace(/^:\s*STRICTLY\b.*$/gim, '');
    t = t.replace(/^CRITICAL SCRIPT.*$/gim, '');
    t = t.replace(/^MUST FOLLOW STRICTLY.*$/gim, '');
    t = t.replace(/^:\s*STRICTLY/gi, '');
    t = t.replace(/^:\s*/, '');

    // 2. Strip code blocks (fenced)
    t = t.replace(/```[\s\S]*?```/g, ' ');

    // 3. Strip inline code
    t = t.replace(/`([^`]+)`/g, '$1');

    // 4. Strip markdown bold/italic
    t = t.replace(/\*\*\*(.+?)\*\*\*/g, '$1');
    t = t.replace(/\*\*(.+?)\*\*/g, '$1');
    t = t.replace(/\*(.+?)\*/g, '$1');
    t = t.replace(/___(.+?)___/g, '$1');
    t = t.replace(/__(.+?)__/g, '$1');
    t = t.replace(/_(.+?)_/g, '$1');

    // 5. Strip headers
    t = t.replace(/^#{1,6}\s+/gm, '');

    // 6. Strip horizontal rules
    t = t.replace(/^[-*_]{3,}\s*$/gm, '');

    // 7. Strip blockquotes
    t = t.replace(/^>\s+/gm, '');

    // 8. Strip images, strip link URLs but keep text
    t = t.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1');
    t = t.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1');

    // 9. Remove URLs entirely (don't speak raw URLs)
    t = t.replace(/https?:\/\/[^\s]+/g, '');

    // 10. Convert bullet lists to spoken form
    t = t.replace(/^[\s]*[-*+]\s+/gm, '');
    t = t.replace(/^[\s]*\d+\.\s+/gm, '');

    // 11. Strip internal JSON/HTML artifacts
    t = t.replace(/\{[^}]*\}/g, '');
    t = t.replace(/<[^>]+>/g, '');

    // 12. Strip tool/internal markers
    t = t.replace(/\b(TOOL ACTION|VOICE ACTION|GEMINI AI|AI SERVER|JARVIS|LOCAL JARVIS|WAKE WORD|WAKE GREETING|MEMORY SAVED|ALL PERMISSIONS GRANTED|NOTIFICATIONS|MISSION AUTO|MIC PERMISSION|OFFLINE MODE|EMERGENCY STOP TRIGGERED)\b/gi, '');

    // 13. Collapse multiple spaces and trim
    t = t.replace(/\s{2,}/g, ' ').trim();

    // 14. If nothing meaningful remains, return empty
    if (t.replace(/[.,!?;:\s]/g, '').length < 2) {
      return '';
    }

    return t;
  }

  /**
   * Generate a stable ID for a message to prevent duplicate TTS playback.
   */
  generateMessageId(text: string, timestamp: number): string {
    return `tts_${timestamp}_${text.length}`;
  }

  /**
   * Speak text with full normalization, auto-speak check, and state management.
   * Returns a Promise that resolves when speech completes.
   */
  async speak(
    text: string,
    options: TTSManagerOptions & {
      source?: 'voice' | 'typed' | 'replay' | 'tool' | 'wake' | 'system';
      messageId?: string;
      force?: boolean;
    }
  ): Promise<void> {
    const normalized = this.normalizeForSpeech(text);
    if (!normalized) return;

    // Skip if auto-speak is off and not forced/replay
    if (!options.autoSpeak && !options.force && options.source !== 'replay') {
      return;
    }

    // Prevent duplicate TTS for same message
    if (options.messageId && options.messageId === this.lastSpokenId) {
      return;
    }
    if (options.messageId) {
      this.lastSpokenId = options.messageId;
    }

    // Stop any current speech (interruption)
    this.stop();

    return new Promise<void>((resolve) => {
      audioEngine.speakAssistantResponse(normalized, {
        source: options.source,
        speed: options.speed,
        pitch: options.pitch,
        volume: options.volume,
        apiKey: options.apiKey || undefined,
        onEnd: () => resolve(),
      }).catch(() => resolve());
    });
  }

  /**
   * Force speak text regardless of auto-speak setting.
   * Used for explicit user requests like "replay" or "read this out loud".
   */
  async forceSpeak(
    text: string,
    options: TTSManagerOptions & {
      source?: 'voice' | 'typed' | 'replay' | 'tool' | 'wake' | 'system';
    }
  ): Promise<void> {
    return this.speak(text, { ...options, force: true });
  }

  /**
   * Immediately stop any current speech.
   */
  stop(): void {
    this.currentText = '';
    audioEngine.stop();
  }

  /**
   * Check if TTS is currently speaking.
   */
  isSpeaking(): boolean {
    return audioEngine.isSpeaking();
  }

  /**
   * Get current TTS state.
   */
  getState(): TTSState {
    return this.state;
  }

  /**
   * Get the text currently being spoken (or last spoken).
   */
  getCurrentText(): string {
    return this.currentText;
  }

  /**
   * Subscribe to TTS state changes.
   */
  onStateChange(listener: (state: TTSState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      try {
        listener(this.state);
      } catch (err) {
        console.warn('[TTSManager] Listener error:', err);
      }
    }
  }

  /**
   * Cleanup on unmount.
   */
  dispose(): void {
    this.stateUnsub?.();
    this.listeners.clear();
  }
}

export const ttsManager = new TTSManager();
