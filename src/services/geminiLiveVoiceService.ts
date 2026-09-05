import { GeminiLiveSession, LiveSessionState } from './geminiLiveSession';
import { BrowserAudioPlayer, BrowserMicrophoneRecorder } from './audioEngine';

export type VoiceState =
  | 'IDLE'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'LISTENING'
  | 'THINKING'
  | 'SPEAKING'
  | 'DISCONNECTED'
  | 'ERROR';

export interface VoiceServiceListener {
  onStateChange: (state: VoiceState) => void;
  onTranscript: (text: string) => void;
  onResponse: (text: string) => void;
  onError: (errorMsg: string) => void;
}

export class GeminiLiveVoiceService {
  private session: GeminiLiveSession;
  private audioPlayer: BrowserAudioPlayer;
  private micRecorder: BrowserMicrophoneRecorder;
  private listener: VoiceServiceListener;
  private currentState: VoiceState = 'IDLE';

  public static readonly TARGET_VOICE = 'Kore';

  constructor(listener: VoiceServiceListener) {
    this.listener = listener;
    this.audioPlayer = new BrowserAudioPlayer();
    this.micRecorder = new BrowserMicrophoneRecorder();

    this.audioPlayer.setOnPlaybackEnded(() => {
      if (this.currentState === 'SPEAKING') {
        this.setState(this.micRecorder.isRecording() ? 'LISTENING' : 'CONNECTED');
      }
    });

    this.session = new GeminiLiveSession({
      onStateChange: (sessionState: LiveSessionState) => {
        this.mapSessionState(sessionState);
      },
      onAudioChunk: (pcm24kBytes: Uint8Array) => {
        this.setState('SPEAKING');
        this.audioPlayer.enqueueAudioChunk(pcm24kBytes);
      },
      onTranscript: (text: string) => {
        this.listener.onResponse(text);
      },
      onInterrupted: () => {
        this.handleInterruption();
      },
      onTurnComplete: () => {
        if (!this.audioPlayer.isPlaying()) {
          this.setState(this.micRecorder.isRecording() ? 'LISTENING' : 'CONNECTED');
        }
      },
      onError: (err: string) => {
        this.setState('ERROR');
        this.listener.onError(err);
      },
    });
  }

  private setState(newState: VoiceState): void {
    if (this.currentState !== newState) {
      this.currentState = newState;
      this.listener.onStateChange(newState);
    }
  }

  public getState(): VoiceState {
    return this.currentState;
  }

  private mapSessionState(sessionState: LiveSessionState): void {
    switch (sessionState) {
      case 'CONNECTING':
        this.setState('CONNECTING');
        break;
      case 'OPEN':
      case 'SESSION_CONFIGURED':
      case 'READY':
        if (this.micRecorder.isRecording()) {
          this.setState('LISTENING');
        } else if (this.audioPlayer.isPlaying()) {
          this.setState('SPEAKING');
        } else {
          this.setState('CONNECTED');
        }
        break;
      case 'STREAMING':
        if (this.audioPlayer.isPlaying()) {
          this.setState('SPEAKING');
        } else {
          this.setState('LISTENING');
        }
        break;
      case 'DISCONNECTED':
        this.setState('DISCONNECTED');
        break;
      case 'ERROR':
        this.setState('ERROR');
        break;
    }
  }

  /**
   * Connect to the Gemini Live session.
   */
  public async connect(apiKey?: string): Promise<boolean> {
    this.setState('CONNECTING');
    const success = await this.session.connect(apiKey);
    if (success) {
      this.setState('CONNECTED');
    } else {
      this.setState('ERROR');
    }
    return success;
  }

  /**
   * TEXT-ONLY TEST (TEST 4):
   * Sends text: "Namaste Vasu, ek chhota sa greeting bolo."
   * Connects -> configures -> sends text -> receives AUDIO -> decodes -> plays.
   */
  public async sendTextTurn(promptText: string = 'Namaste Vasu, ek chhota sa greeting bolo.'): Promise<boolean> {
    if (!this.session.isOpen() || this.session.getState() !== 'READY') {
      const connected = await this.connect();
      if (!connected) return false;
    }

    this.audioPlayer.stopAndFlush();
    this.setState('THINKING');
    this.listener.onTranscript(promptText);

    return this.session.sendText(promptText);
  }

  /**
   * Start live microphone streaming to Gemini Live session.
   */
  public async startMicrophoneStreaming(apiKey?: string): Promise<boolean> {
    if (!this.session.isOpen() || this.session.getState() !== 'READY') {
      const connected = await this.connect(apiKey);
      if (!connected) return false;
    }

    this.setState('LISTENING');

    const started = await this.micRecorder.startStreaming((base64Chunk: string) => {
      // User speech interruption detection:
      // If user speaks while assistant is speaking, halt assistant immediately
      if (this.audioPlayer.isPlaying()) {
        this.handleInterruption();
      }
      this.session.sendAudioChunk(base64Chunk);
    });

    if (!started) {
      this.setState('ERROR');
      this.listener.onError('Microphone access failed');
      return false;
    }

    return true;
  }

  public stopMicrophoneStreaming(): void {
    this.micRecorder.stopStreaming();
    if (this.currentState === 'LISTENING') {
      this.setState('CONNECTED');
    }
  }

  public stopSpeaking(): void {
    this.audioPlayer.stopAndFlush();
    if (this.currentState === 'SPEAKING') {
      this.setState(this.micRecorder.isRecording() ? 'LISTENING' : 'CONNECTED');
    }
  }

  private handleInterruption(): void {
    console.log('[VASU] Handling interruption - halting assistant playback');
    this.audioPlayer.stopAndFlush();
    if (this.micRecorder.isRecording()) {
      this.setState('LISTENING');
    }
  }

  public disconnect(): void {
    this.stopMicrophoneStreaming();
    this.stopSpeaking();
    this.session.disconnect();
    this.setState('DISCONNECTED');
  }
}
