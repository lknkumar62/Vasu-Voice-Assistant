/**
 * WakeWordEngine provides continuous microphone-based wake phrase detection
 * for "Hello VASU" / "Hey VASU" / "Namaste VASU" / "VASU" on Android and web browsers.
 *
 * Designed for Android Chromium stability:
 * - Does NOT lock active getUserMedia streams in the background, preventing Android HAL contention
 * - Seamlessly pauses and yields the mic whenever active speech recognition or speech output begins
 * - Comprehensive phonetic & multilingual matchers for Hindi, Hinglish, and English
 */

import { audioEngine } from './audioEngine';
import { speechRecognizer } from './speechRecognizer';

export type WakeWordStatus =
  | 'DISABLED'
  | 'LISTENING'
  | 'PAUSED'
  | 'PERMISSION_REQUIRED'
  | 'MODEL_ERROR';

export interface WakeWordEvent {
  transcript: string;
  command?: string;
  wakeWord?: string;
}

class WakeWordEngine {
  private status: WakeWordStatus = 'DISABLED';
  private recognition: any = null;
  private onWakeWordDetected: ((event: WakeWordEvent) => void) | null = null;
  private onAudioLevelUpdate: ((level: number) => void) | null = null;
  private isListeningForWake = false;
  private wakePhrase: string = 'Hello VASU';
  private lastTriggerTime = 0;
  private restartTimer: any = null;
  private idlePulseTimer: any = null;
  private errorCount: number = 0;
  private maxBackoffMs: number = 10000;

  public setCallbacks(
    onWake: (event: WakeWordEvent) => void,
    onAudioLevel: (level: number) => void
  ) {
    this.onWakeWordDetected = onWake;
    this.onAudioLevelUpdate = onAudioLevel;
  }

  public setConfig(phrase: string) {
    this.wakePhrase = phrase;
  }

  public getStatus(): WakeWordStatus {
    return this.status;
  }

  /**
   * Manually trigger wake word simulation (for testing or UI quick button)
   */
  public simulateWakeWord(command: string = '') {
    this.lastTriggerTime = Date.now();
    this.onWakeWordDetected?.({
      transcript: command ? `hello vasu ${command}` : 'hello vasu',
      command: command.trim(),
      wakeWord: 'Hello VASU',
    });
  }

  /**
   * Start wake word listening service
   */
  public async start(): Promise<WakeWordStatus> {
    if (this.isListeningForWake && this.status === 'LISTENING') return this.status;

    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      this.status = 'MODEL_ERROR';
      return this.status;
    }

    this.isListeningForWake = true;
    this.status = 'LISTENING';

    // Start background gentle pulse
    this.startIdlePulse();

    // Start recognition
    this.startPhraseRecognition();

    return this.status;
  }

  private startIdlePulse() {
    if (this.idlePulseTimer) clearInterval(this.idlePulseTimer);
    this.idlePulseTimer = setInterval(() => {
      if (this.status === 'LISTENING') {
        const pulse = 0.05 + Math.random() * 0.08;
        this.onAudioLevelUpdate?.(pulse);
      }
    }, 400);
  }

  private startPhraseRecognition() {
    if (!this.isListeningForWake || this.status === 'PAUSED' || this.status === 'DISABLED') return;
    if (audioEngine.getIsSpeaking() || speechRecognizer.getIsListening()) {
      return;
    }

    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) return;

    // Teardown any old instance
    if (this.recognition) {
      try {
        this.recognition.onstart = null;
        this.recognition.onresult = null;
        this.recognition.onerror = null;
        this.recognition.onend = null;
        this.recognition.abort();
      } catch (_) {}
      this.recognition = null;
    }

    try {
      this.recognition = new SpeechRecognition();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.maxAlternatives = 1;
      this.recognition.lang = 'hi-IN'; // recognizes both Hindi and English/Hinglish on Android

      // Match patterns: "Hello VASU", "Hey VASU", "Namaste VASU", "Ok VASU", "Suno VASU", "VASU"
      const WAKE_PATTERN = /(?:hello|hey|hi|helo|halo|namaste|ok|okay|suno|boliye|oye)?\s*(?:vasu|basu|wasu|vashu|vasoo|vaasu|baasu|bhashu|vasa|vaso)\b/i;
      const HINDI_WAKE_PATTERN = /(?:हेलो|हे|नमस्ते|सुनो|बोलिए|ओए)?\s*(?:वासु|बासु|वासू|भासु|वाशू)/;

      this.recognition.onresult = (event: any) => {
        const now = Date.now();
        // 8 second cooldown after trigger to prevent re-detection during conversation
        if (now - this.lastTriggerTime < 8000) return;

        for (let i = event.resultIndex; i < event.results.length; ++i) {
          // Only accept FINAL results to prevent false triggers from interim/garbage results
          if (!event.results[i].isFinal) continue;

          const rawTranscript = (event.results[i][0].transcript || '').trim();
          const confidence = event.results[i][0].confidence || 0;
          const transcript = rawTranscript.toLowerCase();

          // Require minimum confidence to avoid false positives
          if (confidence < 0.3 && rawTranscript.length < 5) continue;

          const hasMatch = WAKE_PATTERN.test(transcript) || HINDI_WAKE_PATTERN.test(rawTranscript);

          if (hasMatch) {
            this.lastTriggerTime = now;
            this.errorCount = 0; // Reset backoff on successful detection
            this.pause(); // Immediately pause to avoid conflicts with active speech

            // Extract any command spoken right after wake phrase
            const command = rawTranscript
              .replace(WAKE_PATTERN, '')
              .replace(HINDI_WAKE_PATTERN, '')
              .replace(/^[,.\s]+/, '')
              .trim();

            console.log("[WakeWordEngine] Wake word heard, pausing engine:", { rawTranscript, command, confidence });

            this.onWakeWordDetected?.({
              transcript: rawTranscript,
              command,
              wakeWord: 'Hello VASU',
            });
            break;
          }
        }
      };

      this.recognition.onerror = (e: any) => {
        const err = e.error || '';
        if (err === 'not-allowed') {
          this.status = 'PERMISSION_REQUIRED';
        } else if (err === 'network') {
          // Network error: apply exponential backoff to avoid rapid restart loop
          this.errorCount++;
          const backoffMs = Math.min(1000 * Math.pow(1.5, this.errorCount - 1), this.maxBackoffMs);
          console.log("[WakeWordEngine] Network error, backing off", backoffMs, "ms (attempt", this.errorCount, ")");
          clearTimeout(this.restartTimer);
          if (this.isListeningForWake && this.status === 'LISTENING') {
            this.restartTimer = setTimeout(() => {
              if (this.isListeningForWake && this.status === 'LISTENING') {
                this.startPhraseRecognition();
              }
            }, backoffMs);
          }
          return; // Don't trigger onend, we handled restart ourselves
        } else if (err !== 'no-speech' && err !== 'aborted') {
          console.log("[WakeWordEngine] Recognition event:", err);
        }
      };

      this.recognition.onend = () => {
        // Reset error count on successful end (no error = recognition worked)
        this.errorCount = 0;
        // Safe continuous restart
        clearTimeout(this.restartTimer);
        if (this.isListeningForWake && this.status === 'LISTENING') {
          this.restartTimer = setTimeout(() => {
            if (this.isListeningForWake && this.status === 'LISTENING') {
              this.startPhraseRecognition();
            }
          }, 350);
        }
      };

      this.recognition.start();
    } catch (e) {
      console.warn("[WakeWordEngine] Start error:", e);
    }
  }

  /**
   * Pause wake word engine immediately to free audio device for active speech recognizer
   */
  public pause() {
    this.status = 'PAUSED';
    clearTimeout(this.restartTimer);
    if (this.recognition) {
      try {
        this.recognition.onstart = null;
        this.recognition.onresult = null;
        this.recognition.onerror = null;
        this.recognition.onend = null;
        this.recognition.abort();
      } catch (_) {}
      this.recognition = null;
    }
  }

  /**
   * Resume wake word engine after active speech recognizer or speech ends
   */
  public resume() {
    if (this.isListeningForWake) {
      this.status = 'LISTENING';
      clearTimeout(this.restartTimer);
      // 2 second delay after resume to avoid re-triggering from residual audio
      this.restartTimer = setTimeout(() => {
        if (this.isListeningForWake && this.status === 'LISTENING') {
          if (!audioEngine.isSpeaking() && !audioEngine.isInSettleDelay() && !speechRecognizer.getIsListening()) {
            this.startPhraseRecognition();
          }
        }
      }, 2000);
    }
  }

  public stop() {
    this.isListeningForWake = false;
    this.status = 'DISABLED';
    clearTimeout(this.restartTimer);
    if (this.idlePulseTimer) {
      clearInterval(this.idlePulseTimer);
      this.idlePulseTimer = null;
    }

    if (this.recognition) {
      try {
        this.recognition.onstart = null;
        this.recognition.onresult = null;
        this.recognition.onerror = null;
        this.recognition.onend = null;
        this.recognition.abort();
      } catch (_) {}
      this.recognition = null;
    }
  }
}

export const wakeWordEngine = new WakeWordEngine();
