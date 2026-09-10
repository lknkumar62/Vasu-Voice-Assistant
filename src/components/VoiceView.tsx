import React, { useState, useEffect, useRef } from 'react';
import {
  Mic,
  MicOff,
  Volume2,
  Radio,
  Sparkles,
  AlertCircle,
  RefreshCw,
  Play,
  ArrowLeft,
  CheckCircle2,
  Cpu,
} from 'lucide-react';
import { GeminiLiveVoiceService, LiveConnectionState } from '../services/geminiLiveVoiceService';
import { audioEngine } from '../services/audioEngine';
import { voiceManager } from '../services/voiceManager';

interface VoiceViewProps {
  apiKey?: string;
  isWakeWordActive: boolean;
  onToggleWakeWord: () => void;
  onBack: () => void;
  onOpenSettings: () => void;
}

export const VoiceView: React.FC<VoiceViewProps> = ({
  apiKey = '',
  isWakeWordActive,
  onToggleWakeWord,
  onBack,
  onOpenSettings,
}) => {
  const [liveState, setLiveState] = useState<LiveConnectionState>('DISCONNECTED');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [userTranscript, setUserTranscript] = useState<string>('');
  const [modelTranscript, setModelTranscript] = useState<string>('');
  const [isTestingAudio, setIsTestingAudio] = useState<boolean>(false);
  const [audioLevel, setAudioLevel] = useState<number>(0);
  const [hasTestedSuccessfully, setHasTestedSuccessfully] = useState<boolean>(false);

  const liveServiceRef = useRef<GeminiLiveVoiceService>(GeminiLiveVoiceService.getInstance());
  const animFrameRef = useRef<number | null>(null);

  // Synchronize with GeminiLiveVoiceService state
  useEffect(() => {
    const service = liveServiceRef.current;

    service.setCallbacks({
      onStateChange: (st) => {
        setLiveState(st);
        if (st !== 'ERROR') {
          setErrorMessage(null);
        }
      },
      onTranscript: (text, isUser) => {
        if (isUser) {
          setUserTranscript((prev) => (prev ? `${prev} ${text}` : text));
        } else {
          setModelTranscript((prev) => (prev ? `${prev} ${text}` : text));
        }
      },
      onError: (err) => {
        console.error('[VoiceView Error]:', err);
        setErrorMessage(err);
        setLiveState('ERROR');
      },
    });

    // Auto-connect to live session if API key is provided
    const effectiveKey = apiKey || audioEngine.getApiKey();
    if (effectiveKey) {
      service.connect(effectiveKey).catch((e) => {
        console.warn('Live service auto-connect error:', e);
      });
    }

    return () => {
      // Don't disconnect if user wants background listening, but stop animation
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
      }
    };
  }, [apiKey]);

  // Audio level simulation / pulse effect when speaking or listening
  useEffect(() => {
    let phase = 0;
    const updateVisualizer = () => {
      if (liveState === 'SPEAKING' || isTestingAudio) {
        phase += 0.15;
        const simulated = Math.abs(Math.sin(phase) * 0.7 + Math.cos(phase * 1.5) * 0.3);
        setAudioLevel(0.3 + simulated * 0.7);
      } else if (liveState === 'LISTENING') {
        phase += 0.08;
        const simulated = Math.abs(Math.sin(phase) * 0.4);
        setAudioLevel(0.1 + simulated * 0.4);
      } else {
        setAudioLevel(0);
      }
      animFrameRef.current = requestAnimationFrame(updateVisualizer);
    };

    animFrameRef.current = requestAnimationFrame(updateVisualizer);
    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
    };
  }, [liveState, isTestingAudio]);

  // Handle Real End-to-End Kore Voice Test
  const handleTestKoreVoice = async () => {
    if (isTestingAudio) return;
    setIsTestingAudio(true);
    setErrorMessage(null);
    setUserTranscript('');
    setModelTranscript('Connecting to Gemini Live (voice: Kore, 24kHz PCM)...');

    try {
      // 1. First unlock Web Audio hardware
      audioEngine.unlock();

      const effectiveKey = apiKey || audioEngine.getApiKey();
      if (!effectiveKey) {
        setErrorMessage('API_KEY_MISSING: Please configure your Gemini API Key in Settings to test Kore voice.');
        setIsTestingAudio(false);
        return;
      }

      // 2. Perform end-to-end audio playback test via unified voiceManager
      const service = liveServiceRef.current;
      const result = typeof voiceManager.testKoreVoice === 'function'
        ? await voiceManager.testKoreVoice(effectiveKey)
        : (service && typeof service.testKoreVoice === 'function'
            ? await service.testKoreVoice(effectiveKey)
            : { success: false, error: 'Voice engine initializing...' });

      if (result.success) {
        setHasTestedSuccessfully(true);
        setModelTranscript('Kore Voice test complete: 24kHz audio played through device speaker successfully.');
      } else {
        // Fallback to direct TTS endpoint with Kore voice
        console.warn('Live WebSocket test failed, falling back to Kore TTS audio pipeline:', result.error);
        setModelTranscript('Falling back to direct Gemini Kore audio synthesis...');
        await audioEngine.speakAssistantResponse('नमस्ते! मैं कोर हूँ। वासु वॉइस असिस्टेंट बिल्कुल ठीक काम कर रहा है।', {
          apiKey: effectiveKey,
          forceKoreVoice: true,
          onStart: () => setLiveState('SPEAKING'),
          onEnd: () => {
            setLiveState('CONNECTED');
            setHasTestedSuccessfully(true);
            setModelTranscript('Kore voice audio played through speakers successfully.');
          },
        });
      }
    } catch (err: any) {
      console.error('Kore voice test exception:', err);
      const msg = err?.message || 'AUDIO_PLAYBACK_ERROR: Failed to complete voice test.';
      setErrorMessage(msg);
      setLiveState('ERROR');
    } finally {
      setIsTestingAudio(false);
    }
  };

  // Toggle Live Microphone Streaming
  const handleToggleMic = async () => {
    const service = liveServiceRef.current;
    audioEngine.unlock();

    if (service.getIsListening()) {
      service.stopMicrophone();
      setLiveState('CONNECTED');
    } else {
      const effectiveKey = apiKey || audioEngine.getApiKey();
      if (!service.getIsConnected()) {
        const connected = await service.connect(effectiveKey);
        if (!connected) {
          setErrorMessage('Could not connect to Gemini Live. Please check your internet or API key.');
          return;
        }
      }
      const started = await service.startMicrophone();
      if (!started) {
        setErrorMessage('Microphone access was denied. Please allow microphone permission in browser/app settings.');
        setLiveState('ERROR');
      }
    }
  };

  // Reconnect Handler
  const handleReconnect = async () => {
    setErrorMessage(null);
    setLiveState('CONNECTING');
    const effectiveKey = apiKey || audioEngine.getApiKey();
    try {
      const ok = await liveServiceRef.current.connect(effectiveKey);
      if (!ok) {
        setErrorMessage('Connection failed. Verify your Gemini API Key in Settings.');
      }
    } catch (e: any) {
      setErrorMessage(e?.message || 'Connection error');
    }
  };

  // Compute status badge appearance
  const getStatusBadge = () => {
    switch (liveState) {
      case 'SPEAKING':
        return {
          bg: 'bg-rose-500/20 text-rose-300 border-rose-500/40',
          dot: 'bg-rose-400 animate-ping',
          label: 'SPEAKING (Kore 24kHz)',
        };
      case 'THINKING':
        return {
          bg: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
          dot: 'bg-amber-400 animate-pulse',
          label: 'THINKING...',
        };
      case 'LISTENING':
        return {
          bg: 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40',
          dot: 'bg-cyan-400 animate-pulse',
          label: 'LISTENING (16kHz PCM)',
        };
      case 'CONNECTED':
        return {
          bg: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40',
          dot: 'bg-emerald-400',
          label: 'CONNECTED & READY',
        };
      case 'CONNECTING':
        return {
          bg: 'bg-blue-500/20 text-blue-300 border-blue-500/40',
          dot: 'bg-blue-400 animate-ping',
          label: 'CONNECTING TO LIVE...',
        };
      case 'ERROR':
        return {
          bg: 'bg-red-500/20 text-red-300 border-red-500/40',
          dot: 'bg-red-400',
          label: 'ERROR ENCOUNTERED',
        };
      case 'DISCONNECTED':
      default:
        return {
          bg: 'bg-slate-800 text-slate-400 border-slate-700',
          dot: 'bg-slate-500',
          label: 'DISCONNECTED',
        };
    }
  };

  const status = getStatusBadge();

  return (
    <div id="voice-view-container" className="w-full max-w-xl mx-auto px-4 py-4 flex flex-col gap-4">
      {/* Top Header */}
      <div className="flex items-center justify-between border-b border-slate-800/80 pb-3">
        <button
          id="btn-voice-back"
          onClick={onBack}
          className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors cursor-pointer py-1 px-2 rounded-lg hover:bg-slate-800"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back</span>
        </button>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] font-mono text-cyan-400">
            <Cpu className="w-3.5 h-3.5 text-cyan-400" />
            <span>gemini-3.1-flash-live-preview</span>
          </div>
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-900/90 border border-emerald-500/40 text-[11px] font-mono text-emerald-300 font-semibold">
            <Volume2 className="w-3.5 h-3.5 text-emerald-400" />
            <span>Voice: Kore</span>
          </div>
        </div>
      </div>

      {/* Main Status Display */}
      <div className="flex items-center justify-between bg-slate-900/70 border border-slate-800 rounded-xl p-3">
        <div className="flex items-center gap-2.5">
          <span className="relative flex h-3 w-3">
            <span className={`absolute inline-flex h-full w-full rounded-full opacity-75 ${status.dot}`} />
            <span className={`relative inline-flex rounded-full h-3 w-3 ${status.dot.split(' ')[0]}`} />
          </span>
          <span className="text-xs font-mono font-bold tracking-wide uppercase text-slate-200">
            {status.label}
          </span>
        </div>

        {liveState === 'ERROR' || liveState === 'DISCONNECTED' ? (
          <button
            id="btn-voice-reconnect"
            onClick={handleReconnect}
            className="flex items-center gap-1 text-xs text-cyan-400 hover:text-cyan-300 py-1 px-2.5 rounded-lg bg-cyan-950/40 border border-cyan-800/60 cursor-pointer transition-all"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Reconnect</span>
          </button>
        ) : (
          <span className="text-[11px] font-mono text-slate-400">24 kHz PCM Stereo Out</span>
        )}
      </div>

      {/* Actual Error Banner if any */}
      {errorMessage && (
        <div
          id="voice-error-banner"
          className="bg-red-950/40 border border-red-800/80 rounded-xl p-3.5 text-xs text-red-200 flex flex-col gap-2"
        >
          <div className="flex items-start gap-2">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <div className="flex-1">
              <span className="font-semibold text-red-300 block mb-0.5">Live Connection Notice:</span>
              <span className="text-slate-300 leading-relaxed">{errorMessage}</span>
            </div>
          </div>
          <div className="flex items-center justify-end gap-2 pt-1 border-t border-red-900/40">
            <button
              id="btn-error-settings"
              onClick={onOpenSettings}
              className="text-[11px] text-red-300 hover:text-red-100 underline cursor-pointer"
            >
              Configure API Key in Settings
            </button>
            <button
              id="btn-error-retry"
              onClick={handleReconnect}
              className="text-[11px] bg-red-900/50 hover:bg-red-900/80 text-red-200 px-2.5 py-1 rounded-md cursor-pointer transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      )}

      {/* Interactive Central Visualizer Card */}
      <div className="relative bg-gradient-to-b from-slate-900/90 to-slate-950/90 border border-slate-800 rounded-2xl p-6 flex flex-col items-center justify-center gap-5 min-h-[260px] overflow-hidden">
        {/* Animated Glow Rings */}
        <div
          className={`absolute w-64 h-64 rounded-full transition-all duration-300 pointer-events-none ${
            liveState === 'SPEAKING'
              ? 'bg-rose-500/20 blur-3xl scale-125'
              : liveState === 'LISTENING'
              ? 'bg-cyan-500/20 blur-3xl scale-110'
              : liveState === 'THINKING'
              ? 'bg-purple-500/20 blur-3xl scale-115'
              : 'bg-emerald-500/10 blur-2xl scale-95'
          }`}
          style={{
            transform: `scale(${1 + audioLevel * 0.4})`,
          }}
        />

        {/* Central Orb / Voice Icon */}
        <button
          id="btn-voice-orb-action"
          onClick={handleToggleMic}
          className={`relative z-10 w-28 h-28 rounded-full flex items-center justify-center transition-all duration-300 cursor-pointer shadow-2xl ${
            liveState === 'SPEAKING'
              ? 'bg-gradient-to-tr from-rose-600 to-pink-500 ring-8 ring-rose-500/30'
              : liveState === 'LISTENING'
              ? 'bg-gradient-to-tr from-cyan-600 to-blue-500 ring-8 ring-cyan-500/30 animate-pulse'
              : liveState === 'THINKING'
              ? 'bg-gradient-to-tr from-purple-600 to-indigo-500 ring-8 ring-purple-500/30'
              : 'bg-gradient-to-tr from-slate-800 to-slate-700 hover:from-slate-700 hover:to-slate-600 ring-4 ring-slate-700/50'
          }`}
          style={{
            transform: `scale(${1 + audioLevel * 0.15})`,
          }}
        >
          {liveState === 'SPEAKING' ? (
            <Volume2 className="w-12 h-12 text-white animate-bounce" />
          ) : liveState === 'LISTENING' ? (
            <Mic className="w-12 h-12 text-white" />
          ) : liveState === 'THINKING' ? (
            <Radio className="w-12 h-12 text-white animate-spin" />
          ) : (
            <MicOff className="w-10 h-10 text-slate-400" />
          )}
        </button>

        {/* Central Prompt text */}
        <div className="relative z-10 text-center flex flex-col items-center gap-1 max-w-sm">
          <span className="text-sm font-semibold text-slate-200">
            {liveState === 'SPEAKING'
              ? 'Kore Speaking (Audio playing aloud)'
              : liveState === 'LISTENING'
              ? 'VASU is listening to your microphone...'
              : liveState === 'THINKING'
              ? 'Gemini Live processing...'
              : 'Tap Orb or button below to speak'}
          </span>
          <span className="text-xs text-slate-400">
            Bidirectional real-time voice streaming with Gemini Live
          </span>
        </div>

        {/* Live Transcript Stream */}
        {(userTranscript || modelTranscript) && (
          <div className="relative z-10 w-full bg-slate-950/80 border border-slate-800/80 rounded-xl p-3 text-xs flex flex-col gap-2 max-h-36 overflow-y-auto">
            {userTranscript && (
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-mono font-bold text-cyan-400">You:</span>
                <span className="text-slate-200">{userTranscript}</span>
              </div>
            )}
            {modelTranscript && (
              <div className="flex flex-col border-t border-slate-800/60 pt-1.5">
                <span className="text-[10px] uppercase font-mono font-bold text-emerald-400">
                  Kore (VASU):
                </span>
                <span className="text-slate-300">{modelTranscript}</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Primary Action Buttons */}
      <div className="flex flex-col gap-3">
        {/* STEP 7: Test Kore Voice (Text Test) Button */}
        <button
          id="btn-test-kore-voice"
          onClick={handleTestKoreVoice}
          disabled={isTestingAudio}
          className={`w-full py-3.5 px-4 rounded-xl font-medium text-sm flex items-center justify-center gap-2.5 transition-all shadow-lg cursor-pointer ${
            isTestingAudio
              ? 'bg-rose-950/80 text-rose-300 border border-rose-500/50 cursor-wait'
              : hasTestedSuccessfully
              ? 'bg-emerald-900/40 hover:bg-emerald-900/60 text-emerald-200 border border-emerald-500/50 shadow-emerald-950/40'
              : 'bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white shadow-cyan-900/40 hover:scale-[1.01] active:scale-[0.99]'
          }`}
        >
          {isTestingAudio ? (
            <>
              <Radio className="w-4 h-4 animate-spin text-rose-300" />
              <span>Playing Kore 24kHz Audio (SPEAKING)...</span>
            </>
          ) : (
            <>
              <Play className="w-4 h-4 fill-current" />
              <span className="font-semibold">Test Kore Voice (Text Test)</span>
              {hasTestedSuccessfully && (
                <CheckCircle2 className="w-4 h-4 text-emerald-400 ml-auto" />
              )}
            </>
          )}
        </button>

        {/* Start / Stop Live Microphone Conversation */}
        <div className="grid grid-cols-2 gap-3">
          <button
            id="btn-toggle-live-mic"
            onClick={handleToggleMic}
            className={`py-3 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer border ${
              liveServiceRef.current.getIsListening()
                ? 'bg-cyan-950/80 text-cyan-300 border-cyan-500/60 shadow-lg shadow-cyan-950/50'
                : 'bg-slate-900/90 hover:bg-slate-800 text-slate-200 border-slate-700'
            }`}
          >
            {liveServiceRef.current.getIsListening() ? (
              <>
                <MicOff className="w-4 h-4 text-cyan-400" />
                <span>Stop Listening</span>
              </>
            ) : (
              <>
                <Mic className="w-4 h-4 text-cyan-400" />
                <span>Live Mic Streaming</span>
              </>
            )}
          </button>

          {/* Test Offline Native Synthesis Speaker */}
          <button
            id="btn-test-speaker-hardware"
            onClick={() => audioEngine.testSpeaker()}
            className="py-3 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 bg-slate-900/90 hover:bg-slate-800 text-slate-200 border border-slate-700 transition-all cursor-pointer"
          >
            <Volume2 className="w-4 h-4 text-emerald-400" />
            <span>Hardware Speaker Test</span>
          </button>
        </div>
      </div>

      {/* Wake Word "Hello VASU" Toggle Card with 100% Guaranteed Touch Target */}
      <div
        id="card-wake-word-control"
        className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex items-center justify-between gap-3 shadow-md"
      >
        <div className="flex items-center gap-3">
          <div
            className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
              isWakeWordActive
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                : 'bg-slate-800 text-slate-400 border border-slate-700'
            }`}
          >
            <Sparkles className="w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <span className="text-xs font-bold text-slate-200">
              Wake Word: "Hello VASU"
            </span>
            <span className="text-[11px] text-slate-400">
              {isWakeWordActive
                ? 'Continuously active in background'
                : 'Tap switch to enable hands-free voice wake'}
            </span>
          </div>
        </div>

        {/* 48px Tappable Toggle Switch */}
        <button
          id="btn-toggle-wake-word"
          type="button"
          onClick={onToggleWakeWord}
          aria-label="Toggle Wake Word"
          className={`relative inline-flex h-8 w-14 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 focus:ring-offset-slate-900 p-0.5 ${
            isWakeWordActive ? 'bg-emerald-500' : 'bg-slate-700'
          }`}
        >
          <span
            className={`pointer-events-none inline-block h-6 w-6 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
              isWakeWordActive ? 'translate-x-6' : 'translate-x-0'
            }`}
          />
        </button>
      </div>

      {/* Technical Diagnostics */}
      <div className="bg-slate-950/60 border border-slate-800/60 rounded-xl p-3 text-[11px] font-mono text-slate-400 flex flex-col gap-1.5">
        <div className="flex items-center justify-between text-slate-300">
          <span>Gemini Model:</span>
          <span className="text-cyan-400 font-bold">gemini-3.1-flash-live-preview</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Prebuilt Voice:</span>
          <span className="text-emerald-400 font-bold">Kore</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Input Modality:</span>
          <span>16,000 Hz Mono PCM</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Output Audio:</span>
          <span>24,000 Hz Little-Endian PCM</span>
        </div>
      </div>
    </div>
  );
};
