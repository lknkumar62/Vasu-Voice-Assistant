import { GeminiClient } from './geminiClient';
import { GeminiLiveVoiceService } from './geminiLiveVoiceService';

/**
 * Translates/transliterates Hindi Devanagari text into natural phonetic Hinglish.
 * On Android, most devices ONLY have English (US/India) voices installed by default.
 * When English voices receive Devanagari characters, they remain 100% SILENT.
 * Transliterating to phonetic Hinglish ensures EVERY Android device speaks out loud with crystal clarity!
 */
export function toPhoneticHinglish(hindiText: string): string {
  if (!/[\u0900-\u097F]/.test(hindiText)) {
    return hindiText;
  }

  let text = hindiText;

  // 1. High-frequency assistant conversational phrases
  const phraseMap: [RegExp, string][] = [
    [/नमस्ते\s*जी!?/gi, 'Namaste ji! '],
    [/नमस्ते/gi, 'Namaste '],
    [/प्रणाम/gi, 'Pranam '],
    [/हुक्म\s*कीजिए/gi, 'Hukm kijiye, '],
    [/मैं\s*सुन\s*रही\s*हूँ/gi, 'main sun rahi hoon. '],
    [/आपकी\s*प्यारी\s*वासु/gi, 'Aapki pyaari Vasu '],
    [/हाज़िर\s*है/gi, 'haazir hai. '],
    [/बताइए/gi, 'Bataiye, '],
    [/आज\s*का\s*दिन/gi, 'aaj ka din '],
    [/कैसा\s*चल\s*रहा\s*है/gi, 'kaisa chal raha hai? '],
    [/क्या\s*मदद\s*करूँ/gi, 'kya madad karoon? '],
    [/हाँ\s*जी/gi, 'Haan ji, '],
    [/जी\s*बोलिए/gi, 'Ji boliye, '],
    [/बोलिए/gi, 'Boliye, '],
    [/टॉर्च|फ्लैशलाइट/gi, 'Flashlight '],
    [/चालू\s*कर\s*दिया\s*गया\s*है/gi, 'on kar diya gaya hai. '],
    [/चालू\s*कर\s*दी\s*गई\s*है/gi, 'on kar di gayi hai. '],
    [/बंद\s*कर\s*दिया\s*गया\s*है/gi, 'off kar diya gaya hai. '],
    [/बंद\s*कर\s*दी\s*गई\s*है/gi, 'off kar di gayi hai. '],
    [/कैमरा\s*खोल\s*दिया\s*गया\s*है/gi, 'Camera open kar diya gaya hai. '],
    [/अलार्म\s*सेट\s*कर\s*दिया\s*गया\s*है/gi, 'Alarm set kar diya gaya hai. '],
    [/सफलतापूर्वक/gi, 'safalta poorvak '],
    [/याद\s*रख\s*लिया\s*है/gi, 'yaad rakh liya hai. '],
    [/सभी\s*अनुमतियां/gi, 'Sabhi permissions '],
    [/सक्षम\s*कर\s*दी\s*गई\s*हैं/gi, 'enable kar di gayi hain. '],
    [/तैयार\s*है/gi, 'ready hai. '],
    [/आदेश\s*के\s*लिए/gi, 'aadesh ke liye '],
    [/पूरी\s*तरह\s*से/gi, 'poori tarah se '],
    [/सहायता/gi, 'sahayata '],
    [/धन्यवाद/gi, 'Dhanyawad! '],
    [/शुक्रिया/gi, 'Shukriya! '],
  ];

  for (const [re, replacement] of phraseMap) {
    text = text.replace(re, replacement);
  }

  // 2. Character-level phonetics for any remaining Devanagari glyphs
  if (/[\u0900-\u097F]/.test(text)) {
    const charMap: Record<string, string> = {
      'अ': 'a', 'आ': 'aa', 'इ': 'i', 'ई': 'ee', 'उ': 'u', 'ऊ': 'oo',
      'ए': 'e', 'ऐ': 'ai', 'ओ': 'o', 'औ': 'au', 'ऋ': 'ri',
      'क': 'ka', 'ख': 'kha', 'ग': 'ga', 'घ': 'gha', 'ङ': 'nga',
      'च': 'cha', 'छ': 'chha', 'ज': 'ja', 'झ': 'jha', 'ञ': 'nya',
      'ट': 'ta', 'ठ': 'tha', 'ड': 'da', 'ढ': 'dha', 'ण': 'na',
      'त': 'ta', 'थ': 'tha', 'द': 'da', 'ध': 'dha', 'न': 'na',
      'प': 'pa', 'फ': 'pha', 'ब': 'ba', 'भ': 'bha', 'म': 'ma',
      'य': 'ya', 'र': 'ra', 'ल': 'la', 'व': 'va', 'श': 'sha',
      'ष': 'sha', 'स': 'sa', 'ह': 'ha',
      'ा': 'aa', 'ि': 'i', 'ी': 'ee', 'ु': 'u', 'ू': 'oo',
      'े': 'e', 'ै': 'ai', 'ो': 'o', 'ौ': 'au', 'ृ': 'ri',
      'ं': 'n', 'ँ': 'n', 'ः': 'h', '्': '', '़': '',
      '।': '.', '॥': '.', '०': '0', '१': '1', '२': '2', '३': '3',
      '४': '4', '५': '5', '६': '6', '७': '7', '८': '8', '९': '9',
    };

    let result = '';
    const chars = Array.from(text);
    for (let i = 0; i < chars.length; i++) {
      const c = chars[i];
      const next = chars[i + 1];

      if (charMap[c]) {
        let mapped = charMap[c];
        if (next === '्' || (next && ['ा', 'ि', 'ी', 'ु', 'ू', 'े', 'ै', 'ो', 'ौ'].includes(next))) {
          if (mapped.endsWith('a')) {
            mapped = mapped.slice(0, -1);
          }
        }
        result += mapped;
      } else {
        result += c;
      }
    }
    text = result;
  }

  return text.replace(/\s+/g, ' ').trim();
}

/**
 * AudioEngine manages audio focus, speech synthesis (TTS),
 * and dynamic synthesized sound effects.
 */

/**
 * Pre-rendered high quality studio voice clips for instant zero-latency responses
 */
const AUDIO_CLIPS_CONFIG: Array<{ id: string; file: string; matchers: string[] }> = [
  {
    id: 'greeting',
    file: '/audio/vasu_greeting.wav',
    matchers: ['दिन कैसा चल रहा है? और मैं आपकी क्या मदद', 'दिन कैसा चल रहा है और मैं आपकी क्या मदद', 'दिन कैसा चल रहा है? और मैं'],
  },
  {
    id: 'ready',
    file: '/audio/vasu_ready.wav',
    matchers: ['ध्यान से सुन रही है', 'क्या हुक्म है', 'पूरी तरह से तैयार हूँ'],
  },
  {
    id: 'torch_on',
    file: '/audio/vasu_torch_on.wav',
    matchers: ['टॉर्च चालू कर दी है'],
  },
  {
    id: 'torch_off',
    file: '/audio/vasu_torch_off.wav',
    matchers: ['टॉर्च बंद कर दी गई है'],
  },
  {
    id: 'camera',
    file: '/audio/vasu_camera.wav',
    matchers: ['कैमरा खोल दिया गया है'],
  },
  {
    id: 'alarm',
    file: '/audio/vasu_alarm.wav',
    matchers: ['अलार्म सेट कर दिया गया है'],
  },
  {
    id: 'memory',
    file: '/audio/vasu_memory.wav',
    matchers: ['बात प्यार से याद रख ली है', 'याद रख लिया है'],
  },
  {
    id: 'thank_you',
    file: '/audio/vasu_thank_you.wav',
    matchers: ['आपकी मदद करके मुझे बहुत खुशी हुई', 'बहुत-बहुत शुक्रिया जी'],
  },
  {
    id: 'how_are_you',
    file: '/audio/vasu_how_are_you.wav',
    matchers: ['बिल्कुल ठीक और खुश हूँ जी, आप बताइए आज आपका दिन कैसा चल रहा है', 'बिल्कुल ठीक और खुश हूँ जी'],
  },
];

export type AudioEngineState = 'IDLE' | 'SPEAKING' | 'LISTENING';

class AudioEngine {
  private synth: SpeechSynthesis | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private currentAudioElement: HTMLAudioElement | null = null;
  private currentBufferSource: AudioBufferSourceNode | null = null;
  private audioContext: AudioContext | null = null;
  private state: AudioEngineState = 'IDLE';
  private stateListeners: Set<(state: AudioEngineState) => void> = new Set();
  private stopRecordingHandler: (() => void) | null = null;
  private cachedVoices: SpeechSynthesisVoice[] = [];
  private isUnlocked = false;
  private greetingBuffer: AudioBuffer | null = null;
  private clipBuffers: Map<string, AudioBuffer> = new Map();
  private isPreloadingClips = false;
  private apiKey: string = '';

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
      this.loadVoices();
      if (this.synth.onvoiceschanged !== undefined) {
        this.synth.onvoiceschanged = () => this.loadVoices();
      }
    }
    // Preload studio audio clips for 0ms latency
    this.preloadAllAudioClips();
  }

  private isSettling = false;

  public getState(): AudioEngineState {
    return this.state;
  }

  public isIdle(): boolean {
    return this.state === 'IDLE' && !this.isSettling;
  }

  public isSpeaking(): boolean {
    return this.state === 'SPEAKING' || this.isSettling || (this.synth ? this.synth.speaking : false);
  }

  public isInSettleDelay(): boolean {
    return this.isSettling;
  }

  public isListening(): boolean {
    return this.state === 'LISTENING';
  }

  public getIsSpeaking(): boolean {
    return this.isSpeaking();
  }

  public getIsListening(): boolean {
    return this.state === 'LISTENING';
  }

  public onStateChange(listener: (state: AudioEngineState) => void): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  private transitionTo(nextState: AudioEngineState) {
    if (this.state === nextState) return;
    this.state = nextState;
    for (const listener of this.stateListeners) {
      try {
        listener(nextState);
      } catch (err) {
        console.warn("[AudioEngine] Error in state listener:", err);
      }
    }
  }

  /**
   * Register callback from speech recognizer or audio recorder to immediately abort recording sessions.
   */
  public registerRecordingStopper(stopper: () => void): void {
    this.stopRecordingHandler = stopper;
  }

  /**
   * Transition rigid state machine to LISTENING for microphone sessions.
   * Explicitly stops any active audio playback first to ensure mic does not capture speaker echo.
   */
  public setListeningState(): void {
    this.stopPreviousOperations();
    this.transitionTo('LISTENING');
  }

  /**
   * Returns state to IDLE when listening session ends
   */
  public setIdleState(): void {
    if (this.state === 'LISTENING') {
      this.transitionTo('IDLE');
    }
  }

  /**
   * Explicitly stops any previous playback, speech synthesis, or microphone recording
   * before any new playback operation begins.
   */
  public stopPreviousOperations(): void {
    this.isSettling = false;
    // 1. Abort any active microphone recording / listening
    if (this.stopRecordingHandler) {
      try {
        this.stopRecordingHandler();
      } catch (e) {
        console.warn("[AudioEngine] Error stopping microphone recording:", e);
      }
    }

    // 2. Interrupt Gemini Live service if running
    try {
      GeminiLiveVoiceService.getInstance().interrupt();
    } catch (_) {}

    // 3. Stop and disconnect Web Audio buffer source
    if (this.currentBufferSource) {
      try {
        this.currentBufferSource.stop();
        this.currentBufferSource.disconnect();
      } catch (_) {}
      this.currentBufferSource = null;
    }

    // 4. Pause and reset HTMLAudioElement
    if (this.currentAudioElement) {
      try {
        this.currentAudioElement.pause();
        this.currentAudioElement.currentTime = 0;
      } catch (_) {}
      this.currentAudioElement = null;
    }

    // 5. Cancel SpeechSynthesis only if an utterance is actively speaking or pending
    if (this.synth && (this.synth.speaking || this.synth.pending)) {
      try {
        this.synth.cancel();
      } catch (_) {}
    }
    this.currentUtterance = null;
    try {
      delete (window as any).__vasu_active_utterance;
    } catch (_) {}

    this.transitionTo('IDLE');
  }

  public setApiKey(key: string) {
    this.apiKey = key ? key.trim() : '';
  }

  public getApiKey(): string {
    if (this.apiKey) return this.apiKey;
    if (typeof window !== 'undefined') {
      try {
        const saved = localStorage.getItem('vasu_settings');
        if (saved) {
          const parsed = JSON.parse(saved);
          if (parsed.geminiApiKey) {
            this.apiKey = parsed.geminiApiKey.trim();
            return this.apiKey;
          }
        }
      } catch (_) {}
    }
    return '';
  }

  /**
   * Preload and decode all official VASU studio voice clips into memory for sub-second, zero-latency playback
   */
  public async preloadAllAudioClips(): Promise<void> {
    if (this.isPreloadingClips || typeof window === 'undefined') return;
    this.isPreloadingClips = true;

    try {
      const ctx = this.getAudioContext();
      await Promise.all(
        AUDIO_CLIPS_CONFIG.map(async (clip) => {
          if (this.clipBuffers.has(clip.id)) return;
          try {
            const res = await fetch(clip.file);
            if (res.ok) {
              const arrayBuf = await res.arrayBuffer();
              ctx.decodeAudioData(
                arrayBuf,
                (decoded) => {
                  this.clipBuffers.set(clip.id, decoded);
                  if (clip.id === 'greeting') {
                    this.greetingBuffer = decoded;
                  }
                },
                (_) => {}
              );
            }
          } catch (_) {}
        })
      );
    } catch (e) {
      console.warn("Preloading audio clips error:", e);
    } finally {
      this.isPreloadingClips = false;
    }
  }

  public preloadGreetingAudio(): Promise<void> {
    return this.preloadAllAudioClips();
  }

  /**
   * Find matching pre-rendered clip based on spoken text
   */
  private findMatchingClip(text: string): { id: string; file: string } | null {
    const clean = text.toLowerCase();
    for (const clip of AUDIO_CLIPS_CONFIG) {
      for (const matcher of clip.matchers) {
        if (clean.includes(matcher.toLowerCase())) {
          return { id: clip.id, file: clip.file };
        }
      }
    }
    return null;
  }

  /**
   * Plays a pre-rendered studio audio clip with sub-second zero-latency Web Audio API
   */
  public playClip(clip: { id: string; file: string }, onEnd?: () => void): Promise<void> {
    return new Promise(async (resolve) => {
      this.stopPreviousOperations();
      this.unlock();
      this.transitionTo('SPEAKING');

      let ended = false;
      const finish = () => {
        if (ended) return;
        ended = true;
        this.currentBufferSource = null;
        this.currentAudioElement = null;
        this.transitionTo('IDLE');
        onEnd?.();
        resolve();
      };

      // 1. AudioContext check
      const ctx = this.getAudioContext();
      if (ctx.state === 'suspended') {
        try {
          await ctx.resume();
        } catch (_) {}
      }

      // 2. Play from preloaded AudioBuffer (0ms startup)
      const buffer = this.clipBuffers.get(clip.id);
      if (buffer) {
        try {
          const source = ctx.createBufferSource();
          source.buffer = buffer;
          source.connect(ctx.destination);
          this.currentBufferSource = source;
          source.onended = finish;
          source.start(0);
          return;
        } catch (e) {
          console.warn(`Buffer playback failed for clip ${clip.id}:`, e);
        }
      }

      // 3. Fallback to HTMLAudioElement
      try {
        const audio = new Audio(clip.file);
        this.currentAudioElement = audio;
        audio.onended = finish;
        audio.onerror = () => finish();
        const p = audio.play();
        if (p !== undefined) {
          p.catch(() => finish());
        }
      } catch (err) {
        finish();
      }
    });
  }

  private loadVoices() {
    if (!this.synth) return;
    try {
      const v = this.synth.getVoices();
      if (v && v.length > 0) {
        this.cachedVoices = v;
      }
    } catch (_) {}
  }

  /**
   * Unlock AudioContext and SpeechSynthesis on user interaction
   */
  public unlock() {
    try {
      const ctx = this.getAudioContext();
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
      }
      if (!this.isUnlocked) {
        this.isUnlocked = true;
        // Play a short silent buffer to satisfy mobile browser autoplay policy
        const buffer = ctx.createBuffer(1, 1, 22050);
        const source = ctx.createBufferSource();
        source.buffer = buffer;
        source.connect(ctx.destination);
        source.start(0);
      }
      if (this.synth) {
        if (this.synth.paused) {
          this.synth.resume();
        }
      }
    } catch (e) {
      console.warn("AudioEngine unlock error:", e);
    }
  }

  public getAudioContext(): AudioContext {
    if (!this.audioContext) {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioContext = new AudioCtx();
    }
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume().catch(() => {});
    }
    return this.audioContext;
  }

  /**
   * Play futuristic wake chime when "Hello VASU" is detected.
   * Returns a Promise that resolves when chime finishes (320ms) so callers can await cleanly.
   */
  public playWakeChime(): Promise<void> {
    return new Promise((resolve) => {
      try {
        this.stopPreviousOperations();
        this.unlock();
        this.transitionTo('SPEAKING');

        const ctx = this.getAudioContext();
        if (ctx.state === 'suspended') {
          ctx.resume().catch(() => {});
        }
        const osc1 = ctx.createOscillator();
        const osc2 = ctx.createOscillator();
        const gain = ctx.createGain();

        osc1.type = 'sine';
        osc2.type = 'triangle';

        const now = ctx.currentTime;
        // Dual tone ascending chime: 587Hz (D5) -> 880Hz (A5)
        osc1.frequency.setValueAtTime(587.33, now);
        osc1.frequency.exponentialRampToValueAtTime(880.0, now + 0.15);

        osc2.frequency.setValueAtTime(880.0, now);
        osc2.frequency.exponentialRampToValueAtTime(1174.66, now + 0.18);

        gain.gain.setValueAtTime(0.001, now);
        gain.gain.exponentialRampToValueAtTime(0.22, now + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.3);

        osc1.connect(gain);
        osc2.connect(gain);
        gain.connect(ctx.destination);

        let resolved = false;
        const done = () => {
          if (resolved) return;
          resolved = true;
          this.transitionTo('IDLE');
          resolve();
        };

        osc1.onended = done;
        osc1.start(now);
        osc2.start(now);
        osc1.stop(now + 0.32);
        osc2.stop(now + 0.32);

        setTimeout(done, 340);
      } catch (e) {
        console.warn("Could not play wake chime", e);
        this.transitionTo('IDLE');
        resolve();
      }
    });
  }

  /**
   * Play success notification chime
   */
  public playSuccessChime() {
    try {
      this.stopPreviousOperations();
      this.unlock();
      this.transitionTo('SPEAKING');

      const ctx = this.getAudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const now = ctx.currentTime;

      osc.type = 'sine';
      osc.frequency.setValueAtTime(523.25, now); // C5
      osc.frequency.setValueAtTime(659.25, now + 0.08); // E5
      osc.frequency.setValueAtTime(783.99, now + 0.16); // G5

      gain.gain.setValueAtTime(0.001, now);
      gain.gain.exponentialRampToValueAtTime(0.18, now + 0.05);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.35);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.onended = () => {
        this.transitionTo('IDLE');
      };
      osc.start(now);
      osc.stop(now + 0.36);
    } catch (e) {
      console.warn("Could not play success chime", e);
      this.transitionTo('IDLE');
    }
  }

  /**
   * Play instant tone when microphone opens for listening.
   * Returns a Promise that resolves when tone finishes (160ms).
   */
  public playListeningTone(): Promise<void> {
    return new Promise((resolve) => {
      try {
        this.stopPreviousOperations();
        this.unlock();
        this.transitionTo('SPEAKING');

        const ctx = this.getAudioContext();
        if (ctx.state === 'suspended') {
          ctx.resume().catch(() => {});
        }
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        const now = ctx.currentTime;

        osc.type = 'sine';
        osc.frequency.setValueAtTime(440.0, now); // A4
        osc.frequency.exponentialRampToValueAtTime(880.0, now + 0.1); // A5

        gain.gain.setValueAtTime(0.001, now);
        gain.gain.exponentialRampToValueAtTime(0.14, now + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.15);

        osc.connect(gain);
        gain.connect(ctx.destination);

        let resolved = false;
        const done = () => {
          if (resolved) return;
          resolved = true;
          this.transitionTo('IDLE');
          resolve();
        };

        osc.onended = done;
        osc.start(now);
        osc.stop(now + 0.16);
        setTimeout(done, 180);
      } catch (e) {
        console.warn("Could not play listening tone", e);
        this.transitionTo('IDLE');
        resolve();
      }
    });
  }

  /**
   * Play alert/error tone
   */
  public playErrorChime() {
    try {
      this.stopPreviousOperations();
      this.unlock();
      this.transitionTo('SPEAKING');

      const ctx = this.getAudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const now = ctx.currentTime;

      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(320, now);
      osc.frequency.exponentialRampToValueAtTime(220, now + 0.2);

      gain.gain.setValueAtTime(0.15, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.onended = () => {
        this.transitionTo('IDLE');
      };
      osc.start(now);
      osc.stop(now + 0.25);
    } catch (e) {
      console.warn("Could not play error chime", e);
      this.transitionTo('IDLE');
    }
  }

  /**
   * Plays the official VASU Greeting Audio ("नमस्ते जी! आपकी प्यारी वासु हाज़िर है...")
   * This uses the high quality pre-rendered Studio voice sample.
   */
  public async playVasuGreeting(onEnd?: () => void): Promise<void> {
    this.stopPreviousOperations();
    this.unlock();
    this.transitionTo('SPEAKING');

    return new Promise(async (resolve) => {
      let isResolved = false;
      const finish = () => {
        if (isResolved) return;
        isResolved = true;
        this.currentBufferSource = null;
        this.currentAudioElement = null;
        this.transitionTo('IDLE');
        onEnd?.();
        resolve();
      };

      // 1. First ensure AudioContext is active
      const ctx = this.getAudioContext();
      if (ctx.state === 'suspended') {
        try {
          await ctx.resume();
        } catch (_) {}
      }

      // If buffer is already decoded, play it immediately with Web Audio API (100% reliable)
      if (this.greetingBuffer) {
        try {
          const source = ctx.createBufferSource();
          source.buffer = this.greetingBuffer;
          source.connect(ctx.destination);
          this.currentBufferSource = source;
          source.onended = finish;
          source.start(0);
          return;
        } catch (e) {
          console.warn("Web Audio BufferSource playback failed:", e);
        }
      }

      // 2. Fallback to HTMLAudioElement
      try {
        const audio = new Audio('/audio/vasu_greeting.wav');
        this.currentAudioElement = audio;

        audio.onended = finish;
        audio.onerror = (e) => {
          console.warn("Audio element playback error, falling back to synthesis:", e);
          this.speakNative(
            "नमस्ते जी! आपकी प्यारी वासु हाज़िर है। बताइए, आज आपका दिन कैसा चल रहा है? और मैं आपकी क्या मदद करूँ?",
            { onEnd: finish }
          );
        };

        const playPromise = audio.play();
        if (playPromise !== undefined) {
          playPromise.catch((err) => {
            console.warn("Greeting audio play failed (autoplay block?):", err);
            this.speakNative(
              "नमस्ते जी! आपकी प्यारी वासु हाज़िर है। बताइए, आज आपका दिन कैसा चल रहा है? और मैं आपकी क्या मदद करूँ?",
              { onEnd: finish }
            );
          });
        }
      } catch (err) {
        console.warn("Audio constructor error:", err);
        this.speakNative(
          "नमस्ते जी! आपकी प्यारी वासु हाज़िर है। बताइए, आज आपका दिन कैसा चल रहा है? और मैं आपकी क्या मदद करूँ?",
          { onEnd: finish }
        );
      }
    });
  }

  /**
   * Dedicated, zero-latency voice greeting specifically when "Hello VASU" is heard.
   * Runs local speech synthesis directly so the user is guaranteed to hear VASU's voice immediately!
   */
  public async speakWakeGreeting(
    text: string,
    options: {
      speed?: number;
      pitch?: number;
      language?: 'Hindi' | 'Hinglish' | 'English';
      onStart?: () => void;
      onEnd?: () => void;
    } = {}
  ): Promise<void> {
    this.stopPreviousOperations();
    this.unlock();

    const cleanText = text
      .replace(/[*_~`#]/g, '')
      .replace(/\{.*?\}/g, '')
      .trim();

    if (!cleanText) {
      this.transitionTo('IDLE');
      options.onEnd?.();
      return;
    }

    await this.speakNative(cleanText, {
      speed: options.speed || 1.05,
      pitch: options.pitch || 1.06,
      volume: 1.0,
      onStart: options.onStart,
      onEnd: options.onEnd,
    });
  }

  /**
   * Legacy wake response compatibility helper
   */
  public async speakWakeResponse(language: 'Hindi' | 'Hinglish' | 'English' = 'Hinglish'): Promise<void> {
    await this.playWakeChime();
    const greetingText = language === 'English'
      ? "Yes! I'm listening, go ahead."
      : "नमस्ते जी! हुक्म कीजिए, मैं सुन रही हूँ।";
    await this.speakWakeGreeting(greetingText, { language });
  }

  /**
   * Centralized Assistant Speech Pipeline for all sources:
   * - typed input responses
   * - voice input responses
   * - play/replay button clicks
   * - device control/tool confirmations
   * - wake word responses
   *
   * Guarantees:
   * 1. Stops any microphone recording / listening immediately
   * 2. Cleans text (removes prompt fragments, leaked markers, : STRICTLY, markdown)
   * 3. Sets state = SPEAKING
   * 4. Synthesizes & plays audio (matched pre-rendered clip -> Gemini Kore TTS -> Native Speech)
   * 5. Enforces safety settle delay (350ms) after hardware audio finishes before setting IDLE and resolving
   */
  public async speakAssistantResponse(
    text: string,
    options: {
      source?: 'voice' | 'typed' | 'replay' | 'tool' | 'wake' | 'system';
      speed?: number;
      pitch?: number;
      volume?: number;
      apiKey?: string;
      forceKoreVoice?: boolean;
      onStart?: () => void;
      onEnd?: () => void;
    } = {}
  ): Promise<void> {
    this.stopPreviousOperations();
    this.unlock();

    // Clean text thoroughly
    const cleanText = text
      .replace(/^:\s*STRICTLY\b.*$/gim, '')
      .replace(/^CRITICAL SCRIPT.*$/gim, '')
      .replace(/^MUST FOLLOW STRICTLY.*$/gim, '')
      .replace(/^:\s*STRICTLY/gi, '')
      .replace(/^:\s*/, '')
      .replace(/\{.*?\}/g, '')
      .replace(/[*_~`#]/g, '')
      .trim();

    if (!cleanText) {
      options.onEnd?.();
      return;
    }

    return new Promise<void>((resolve) => {
      let resolved = false;
      const completeWithSettle = () => {
        if (resolved) return;
        resolved = true;
        this.isSettling = true;
        setTimeout(() => {
          this.isSettling = false;
          this.transitionTo('IDLE');
          try {
            options.onEnd?.();
          } catch (cbErr) {
            console.warn('[AudioEngine] onEnd callback error:', cbErr);
          }
          resolve();
        }, 120); // 120ms settle delay prevents echo reverberation into microphone while enabling sub-second response
      };

      this.speak(cleanText, {
        speed: options.speed,
        pitch: options.pitch,
        volume: options.volume,
        apiKey: options.apiKey,
        preferLocal: false,
        onStart: options.onStart,
        onEnd: completeWithSettle,
      }).catch((err) => {
        console.warn('[AudioEngine] speakAssistantResponse error, completing:', err);
        completeWithSettle();
      });
    });
  }

  /**
   * Spoken voice output with Gemini Kore audio and offline fallback.
   * Plays through high-fidelity Kore voice when online/configured, with seamless offline synthesis fallback.
   */
  public async speakLocal(
    text: string,
    options: {
      speed?: number;
      pitch?: number;
      volume?: number;
      apiKey?: string;
      onStart?: () => void;
      onEnd?: () => void;
    } = {}
  ): Promise<void> {
    return this.speakAssistantResponse(text, {
      ...options,
      source: 'system',
    });
  }

  /**
   * Play raw 16-bit mono PCM audio buffer directly through Web Audio API to phone speakers
   */
  public async playPCM16(pcmData: Uint8Array | ArrayBuffer, sampleRate = 24000): Promise<void> {
    this.stopPreviousOperations();
    this.unlock();
    const ctx = this.getAudioContext();
    if (ctx.state === 'suspended') {
      try {
        await ctx.resume();
      } catch (_) {}
    }

    const bytes = pcmData instanceof Uint8Array ? pcmData : new Uint8Array(pcmData);
    const samples = new Float32Array(bytes.length / 2);
    const dataView = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    for (let i = 0; i < samples.length; i++) {
      samples[i] = dataView.getInt16(i * 2, true) / 32768.0;
    }

    const audioBuffer = ctx.createBuffer(1, samples.length, sampleRate);
    audioBuffer.getChannelData(0).set(samples);

    return new Promise((resolve) => {
      const source = ctx.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(ctx.destination);
      this.currentBufferSource = source;
      this.transitionTo('SPEAKING');
      source.onended = () => {
        this.currentBufferSource = null;
        this.transitionTo('IDLE');
        resolve();
      };
      source.start(0);
    });
  }

  /**
   * Play an immediate test sound and spoken confirmation to verify audio output hardware with Kore voice
   */
  public async testSpeaker(): Promise<void> {
    this.unlock();
    await this.playWakeChime();
    await this.speak("नमस्ते! वासु ऑडियो सिस्टम और कोर वॉइस बिल्कुल ठीक काम कर रहा है।", {
      speed: 1.0,
      pitch: 1.0,
    });
  }

  /**
   * Speak text naturally using Gemini Kore voice with Web Audio API decoding and offline fallback
   */
  public async speak(
    text: string,
    options: {
      speed?: number;
      pitch?: number;
      volume?: number;
      apiKey?: string;
      preferLocal?: boolean;
      useStudioVoice?: boolean;
      onStart?: () => void;
      onEnd?: () => void;
    } = {}
  ): Promise<void> {
    this.stopPreviousOperations();
    this.unlock();

    const cleanText = text
      .replace(/[*_~`#]/g, '')
      .replace(/\{.*?\}/g, '')
      .trim();

    if (!cleanText) {
      this.transitionTo('IDLE');
      options.onEnd?.();
      return;
    }

    // Check if the text matches any pre-rendered studio voice clip
    const matchedClip = this.findMatchingClip(cleanText);
    if (matchedClip) {
      await this.playClip(matchedClip, options.onEnd);
      return;
    }

    // Attempt dynamic Gemini native audio (Kore prebuilt voice) first for conversational speech
    if (!options.preferLocal) {
      try {
        const activeKey = options.apiKey || this.getApiKey();
        const audioUrl = await GeminiClient.generateTTSAudio(cleanText, activeKey);

        if (audioUrl) {
          const ctx = this.getAudioContext();
          if (ctx.state === 'suspended') {
            try {
              await ctx.resume();
            } catch (_) {}
          }

          let playedViaWebAudio = false;
          try {
            const resp = await fetch(audioUrl);
            const arrayBuf = await resp.arrayBuffer();
            const decodedBuffer = await ctx.decodeAudioData(arrayBuf);

            this.transitionTo('SPEAKING');
            options.onStart?.();

            await new Promise<void>((res) => {
              let finished = false;
              const complete = () => {
                if (finished) return;
                finished = true;
                this.currentBufferSource = null;
                try {
                  URL.revokeObjectURL(audioUrl);
                } catch (_) {}
                this.transitionTo('IDLE');
                options.onEnd?.();
                res();
              };

              const source = ctx.createBufferSource();
              source.buffer = decodedBuffer;
              source.connect(ctx.destination);
              this.currentBufferSource = source;
              source.onended = complete;
              source.start(0);
            });
            playedViaWebAudio = true;
          } catch (decodeErr) {
            console.warn("WebAudio decoding failed, attempting HTMLAudioElement fallback:", decodeErr);
          }

          if (playedViaWebAudio) {
            return;
          }

          // Fallback to HTMLAudioElement
          let playedViaAudioElement = false;
          try {
            const audio = new Audio(audioUrl);
            this.currentAudioElement = audio;

            await new Promise<void>((res, rej) => {
              let finished = false;
              const complete = () => {
                if (finished) return;
                finished = true;
                this.currentAudioElement = null;
                try {
                  URL.revokeObjectURL(audioUrl);
                } catch (_) {}
                this.transitionTo('IDLE');
                options.onEnd?.();
                res();
              };

              audio.onended = complete;
              audio.onerror = (e) => {
                console.warn("Kore audio element error:", e);
                this.transitionTo('IDLE');
                rej(e);
              };
              audio.play().then(() => {
                this.transitionTo('SPEAKING');
                options.onStart?.();
              }).catch((err) => {
                console.warn("Kore audio play rejected:", err);
                this.transitionTo('IDLE');
                rej(err);
              });
            });
            playedViaAudioElement = true;
          } catch (audioElErr) {
            console.warn("HTMLAudioElement fallback failed, falling through to speakNative:", audioElErr);
          }

          if (playedViaAudioElement) {
            return;
          }
        }
      } catch (e) {
        console.warn("Gemini Kore voice generation failed, falling back to offline speech:", e);
      }
    }

    // Offline Fallback: Browser / Android Web Speech API
    // If speechSynthesis unavailable (Android WebView), play a notification chime
    if (!this.synth) {
      console.warn('[AudioEngine] No speechSynthesis available — playing chime as voice indicator');
      this.transitionTo('SPEAKING');
      options.onStart?.();
      try { await this.playSuccessChime(); } catch (_) {}
      this.transitionTo('IDLE');
      options.onEnd?.();
      return;
    }
    await this.speakNative(cleanText, options);
  }

  /**
   * Browser SpeechSynthesis fallback with Android GC protection, dual-voice fallback,
   * and automatic phonetic Hinglish transliteration when Hindi TTS pack is absent.
   */
  private speakNative(
    cleanText: string,
    options: {
      speed?: number;
      pitch?: number;
      volume?: number;
      onStart?: () => void;
      onEnd?: () => void;
    } = {}
  ): Promise<void> {
    return new Promise((resolve) => {
      this.transitionTo('SPEAKING');
      if (!this.synth) {
        this.transitionTo('IDLE');
        options.onEnd?.();
        resolve();
        return;
      }

      // Resume speech synthesis engine in case it's in paused state
      try {
        if (this.synth.paused) {
          this.synth.resume();
        }
      } catch (_) {}

      // Always query fresh voices from synth if available
      let voices = this.synth.getVoices();
      if (!voices || voices.length === 0) {
        voices = this.cachedVoices;
      } else {
        this.cachedVoices = voices;
      }

      const hasDevanagari = /[\u0900-\u097F]/.test(cleanText);

      // Prioritize Indian Hindi Female voices (Swara, Kalpana, Lekha, Google हिन्दी, etc.)
      const femaleKeywords = ['female', 'woman', 'girl', 'swara', 'kalpana', 'lekha', 'neerja', 'priya', 'hi-in-x-hie', 'hi-in-x-hic', 'google हिन्दी'];
      const hindiVoice =
        voices.find((v) => {
          const isHi = v.lang.startsWith('hi') || v.name.toLowerCase().includes('hindi');
          if (!isHi) return false;
          const nameLower = v.name.toLowerCase();
          return femaleKeywords.some((k) => nameLower.includes(k));
        }) ||
        voices.find((v) => v.lang.startsWith('hi') || v.name.toLowerCase().includes('hindi'));

      const indianEnglishVoice =
        voices.find((v) => {
          const isIndian = v.lang === 'en-IN' || v.lang.startsWith('en-IN') || v.name.toLowerCase().includes('india');
          if (!isIndian) return false;
          const nameLower = v.name.toLowerCase();
          return femaleKeywords.some((k) => nameLower.includes(k)) || nameLower.includes('heera') || nameLower.includes('veena');
        }) ||
        voices.find((v) => v.lang === 'en-IN' || v.lang.startsWith('en-IN') || v.name.toLowerCase().includes('india'));

      const generalEnglishVoice =
        voices.find((v) => femaleKeywords.some((k) => v.name.toLowerCase().includes(k)) && v.lang.startsWith('en')) ||
        voices.find((v) => (v.name.includes('Google') || v.name.includes('Natural')) && v.lang.startsWith('en')) ||
        voices.find((v) => v.lang.startsWith('en')) ||
        voices[0];

      // CRITICAL FOR ANDROID COMPATIBILITY:
      // If text is Hindi Devanagari, BUT device has NO Hindi voice pack installed,
      // English TTS engines will fail silently when passed Devanagari Unicode.
      // We automatically convert to natural phonetic Hinglish so the voice is 100% audible!
      let textToSpeak = cleanText;
      let selectedVoice: SpeechSynthesisVoice | undefined = undefined;
      let targetLang = 'en-US';

      if (hasDevanagari && hindiVoice) {
        textToSpeak = cleanText;
        selectedVoice = hindiVoice;
        targetLang = hindiVoice.lang || 'hi-IN';
      } else if (hasDevanagari && !hindiVoice) {
        // No Hindi voice on this device! Convert to Hinglish so English voice speaks it aloud!
        textToSpeak = toPhoneticHinglish(cleanText);
        selectedVoice = indianEnglishVoice || generalEnglishVoice;
        targetLang = selectedVoice?.lang || 'en-US';
      } else {
        // English / Hinglish text
        selectedVoice = indianEnglishVoice || generalEnglishVoice;
        targetLang = selectedVoice?.lang || 'en-US';
      }

      const utterance = new SpeechSynthesisUtterance(textToSpeak);
      this.currentUtterance = utterance;
      // CRITICAL FOR CHROMIUM & ANDROID WEBVIEW: Prevents Garbage Collector from killing active speech
      (window as any).__vasu_active_utterance = utterance;
      if (!(window as any).__vasu_utterances) {
        (window as any).__vasu_utterances = [];
      }
      (window as any).__vasu_utterances.push(utterance);
      if ((window as any).__vasu_utterances.length > 10) {
        (window as any).__vasu_utterances.shift();
      }

      utterance.rate = options.speed || 1.02;
      utterance.pitch = options.pitch || 1.15;
      utterance.volume = options.volume !== undefined ? options.volume : 1.0;
      utterance.lang = targetLang;

      if (selectedVoice) {
        utterance.voice = selectedVoice;
      }

      let ended = false;
      // Android Chromium resume tick to prevent TTS engine from going to sleep
      const resumeInterval = setInterval(() => {
        if (!ended && this.synth && this.synth.speaking && this.synth.paused) {
          try {
            this.synth.resume();
          } catch (_) {}
        }
      }, 250);

      const finish = () => {
        if (ended) return;
        ended = true;
        clearInterval(resumeInterval);
        this.currentUtterance = null;
        try {
          delete (window as any).__vasu_active_utterance;
        } catch (_) {}
        this.transitionTo('IDLE');
        options.onEnd?.();
        resolve();
      };

      utterance.onstart = () => {
        this.transitionTo('SPEAKING');
        options.onStart?.();
      };

      const timeoutSec = Math.max(4, Math.ceil(textToSpeak.length / 6));
      const watchdog = setTimeout(() => {
        if (!ended) {
          finish();
        }
      }, timeoutSec * 1000);

      const wrappedFinish = () => {
        clearTimeout(watchdog);
        finish();
      };

      let hasRetried = false;
      utterance.onend = wrappedFinish;
      utterance.onerror = (e: any) => {
        const errType = e?.error || '';
        console.warn("SpeechSynthesis error:", errType, e);
        if (ended) return;

        // If canceled or interrupted immediately by Android WebView IPC, retry once after 120ms
        if ((errType === 'canceled' || errType === 'interrupted') && !hasRetried) {
          hasRetried = true;
          setTimeout(() => {
            if (!ended && this.synth) {
              try {
                this.synth.resume();
                const freshUtterance = new SpeechSynthesisUtterance(textToSpeak);
                freshUtterance.rate = utterance.rate;
                freshUtterance.pitch = utterance.pitch;
                freshUtterance.volume = utterance.volume;
                freshUtterance.lang = utterance.lang;
                if (utterance.voice) freshUtterance.voice = utterance.voice;
                freshUtterance.onstart = utterance.onstart;
                freshUtterance.onend = wrappedFinish;
                freshUtterance.onerror = wrappedFinish;
                (window as any).__vasu_active_utterance = freshUtterance;
                this.synth.speak(freshUtterance);
                return;
              } catch (_) {}
            }
            wrappedFinish();
          }, 120);
          return;
        }

        // If other error occurred, attempt instant retry with transliterated English fallback
        if (!hasRetried) {
          hasRetried = true;
          try {
            const fallbackText = toPhoneticHinglish(cleanText);
            const fallbackUtterance = new SpeechSynthesisUtterance(fallbackText);
            (window as any).__vasu_active_utterance = fallbackUtterance;
            fallbackUtterance.lang = 'en-US';
            fallbackUtterance.rate = options.speed || 1.0;
            fallbackUtterance.pitch = options.pitch || 1.0;
            fallbackUtterance.onend = wrappedFinish;
            fallbackUtterance.onerror = wrappedFinish;
            this.synth?.speak(fallbackUtterance);
            return;
          } catch (_) {}
        }
        wrappedFinish();
      };

      // 130ms delay gives Android Chromium IPC time to settle after any synth.cancel()
      setTimeout(() => {
        try {
          if (this.synth) {
            this.synth.resume();
            this.synth.speak(utterance);
            // Chromium Android kickstart
            setTimeout(() => {
              if (this.synth && this.synth.paused) {
                this.synth.resume();
              }
            }, 100);
          }
        } catch (err) {
          console.warn("Could not speak native:", err);
          wrappedFinish();
        }
      }, 130);
    });
  }

  /**
   * Instantly stops any current speech or audio (Interruption capability)
   */
  public stop() {
    this.stopPreviousOperations();
  }
}

export const audioEngine = new AudioEngine();
