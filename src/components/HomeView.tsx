import React, { useState, useEffect, useRef } from 'react';
import { GeminiLiveVoiceService, VoiceState } from '../services/geminiLiveVoiceService';

export const HomeView: React.FC = () => {
  const [voiceState, setVoiceState] = useState<VoiceState>('IDLE');
  const [transcript, setTranscript] = useState<string>('');
  const [response, setResponse] = useState<string>('');
  const [logs, setLogs] = useState<string[]>([]);
  const [customPrompt, setCustomPrompt] = useState<string>('Namaste Vasu, ek chhota sa greeting bolo.');

  const serviceRef = useRef<GeminiLiveVoiceService | null>(null);

  const addLog = (msg: string) => {
    setLogs((prev) => [...prev.slice(-40), `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  useEffect(() => {
    const originalLog = console.log;
    const originalWarn = console.warn;
    const originalError = console.error;

    console.log = (...args: any[]) => {
      originalLog(...args);
      const str = args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' ');
      if (str.startsWith('[VASU]')) {
        addLog(str);
      }
    };

    console.warn = (...args: any[]) => {
      originalWarn(...args);
      const str = args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' ');
      if (str.startsWith('[VASU]')) {
        addLog(`WARN: ${str}`);
      }
    };

    console.error = (...args: any[]) => {
      originalError(...args);
      const str = args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' ');
      if (str.startsWith('[VASU]')) {
        addLog(`ERROR: ${str}`);
      }
    };

    const service = new GeminiLiveVoiceService({
      onStateChange: (state: VoiceState) => {
        setVoiceState(state);
      },
      onTranscript: (t: string) => {
        setTranscript(t);
      },
      onResponse: (r: string) => {
        setResponse((prev) => (prev ? `${prev} ${r}` : r));
      },
      onError: (err: string) => {
        addLog(`Service Error: ${err}`);
      },
    });

    serviceRef.current = service;

    return () => {
      console.log = originalLog;
      console.warn = originalWarn;
      console.error = originalError;
      service.disconnect();
    };
  }, []);

  const handleTextTest = async () => {
    setResponse('');
    addLog(`Running Text-Only Test: "${customPrompt}"`);
    if (serviceRef.current) {
      await serviceRef.current.sendTextTurn(customPrompt);
    }
  };

  const handleToggleMic = async () => {
    if (!serviceRef.current) return;
    if (voiceState === 'LISTENING') {
      serviceRef.current.stopMicrophoneStreaming();
    } else {
      setResponse('');
      addLog('Starting real-time microphone conversation ("Hello Vasu")...');
      await serviceRef.current.startMicrophoneStreaming();
    }
  };

  const handleStopSpeaking = () => {
    if (serviceRef.current) {
      serviceRef.current.stopSpeaking();
    }
  };

  const getStateColor = () => {
    switch (voiceState) {
      case 'CONNECTING':
        return '#38bdf8';
      case 'CONNECTED':
        return '#06b6d4';
      case 'LISTENING':
        return '#00f2fe';
      case 'THINKING':
        return '#fbbf24';
      case 'SPEAKING':
        return '#a855f7';
      case 'ERROR':
        return '#ef4444';
      case 'DISCONNECTED':
        return '#64748b';
      case 'IDLE':
      default:
        return '#475569';
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: '#0a0d14',
      color: '#f8fafc',
      fontFamily: 'system-ui, -apple-system, sans-serif',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: '24px',
      boxSizing: 'border-box'
    }}>
      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: '24px' }}>
        <h1 style={{
          fontSize: '28px',
          fontWeight: 700,
          letterSpacing: '1px',
          margin: 0,
          background: 'linear-gradient(135deg, #00f2fe 0%, #4facfe 100%)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
        }}>
          VASU VOICE ASSISTANT
        </h1>
        <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#94a3b8' }}>
          Native Gemini Live Conversational Engine • Voice: <strong style={{ color: '#38bdf8' }}>Kore</strong>
        </p>
      </div>

      {/* Voice Orb */}
      <div style={{
        position: 'relative',
        width: '180px',
        height: '180px',
        margin: '20px 0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        <div style={{
          width: '140px',
          height: '140px',
          borderRadius: '50%',
          backgroundColor: getStateColor(),
          boxShadow: `0 0 40px ${getStateColor()}88, inset 0 0 20px #ffffff44`,
          transition: 'all 0.4s ease',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          animation: voiceState === 'SPEAKING' || voiceState === 'LISTENING' ? 'pulse 1.8s infinite' : 'none'
        }}>
          <span style={{
            fontSize: '12px',
            fontWeight: 800,
            letterSpacing: '1.5px',
            color: '#0f172a',
            textTransform: 'uppercase',
          }}>
            {voiceState}
          </span>
        </div>
      </div>

      {/* Status Badge */}
      <div style={{
        padding: '6px 16px',
        borderRadius: '20px',
        backgroundColor: '#1e293b',
        border: `1px solid ${getStateColor()}`,
        fontSize: '13px',
        color: '#e2e8f0',
        marginBottom: '20px',
      }}>
        State: <strong>{voiceState}</strong> • Format: <strong>16kHz In / 24kHz Out</strong>
      </div>

      {/* Control Actions */}
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: '12px',
        justifyContent: 'center',
        maxWidth: '560px',
        marginBottom: '24px',
      }}>
        <button
          onClick={handleToggleMic}
          style={{
            padding: '12px 24px',
            borderRadius: '12px',
            border: 'none',
            backgroundColor: voiceState === 'LISTENING' ? '#ef4444' : '#0284c7',
            color: '#ffffff',
            fontWeight: 600,
            fontSize: '14px',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
          }}
        >
          {voiceState === 'LISTENING' ? '🛑 Stop Listening' : '🎤 Live Microphone ("Hello Vasu")'}
        </button>

        <button
          onClick={handleTextTest}
          disabled={voiceState === 'CONNECTING'}
          style={{
            padding: '12px 24px',
            borderRadius: '12px',
            border: '1px solid #38bdf8',
            backgroundColor: 'rgba(56, 189, 248, 0.1)',
            color: '#38bdf8',
            fontWeight: 600,
            fontSize: '14px',
            cursor: 'pointer',
          }}
        >
          🔊 Test Kore Voice (Text Test)
        </button>

        {voiceState === 'SPEAKING' && (
          <button
            onClick={handleStopSpeaking}
            style={{
              padding: '12px 20px',
              borderRadius: '12px',
              border: '1px solid #a855f7',
              backgroundColor: 'rgba(168, 85, 247, 0.15)',
              color: '#d8b4fe',
              fontWeight: 600,
              fontSize: '14px',
              cursor: 'pointer',
            }}
          >
            ✋ Interrupt Speaking
          </button>
        )}
      </div>

      {/* Custom Prompt Input for Text Test */}
      <div style={{ width: '100%', maxWidth: '560px', marginBottom: '20px' }}>
        <label style={{ fontSize: '12px', color: '#94a3b8', display: 'block', marginBottom: '6px' }}>
          Test sentence (Hindi / Hinglish / English):
        </label>
        <input
          type="text"
          value={customPrompt}
          onChange={(e) => setCustomPrompt(e.target.value)}
          placeholder="Namaste Vasu, ek chhota sa greeting bolo."
          style={{
            width: '100%',
            padding: '10px 14px',
            borderRadius: '8px',
            border: '1px solid #334155',
            backgroundColor: '#0f172a',
            color: '#f8fafc',
            fontSize: '14px',
            boxSizing: 'border-box'
          }}
        />
      </div>

      {/* Transcript & Response Area */}
      <div style={{
        width: '100%',
        maxWidth: '560px',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        marginBottom: '20px',
      }}>
        {transcript && (
          <div style={{
            padding: '12px 16px',
            borderRadius: '10px',
            backgroundColor: '#1e293b',
            borderLeft: '4px solid #00f2fe',
          }}>
            <span style={{ fontSize: '11px', color: '#94a3b8', display: 'block' }}>USER / PROMPT:</span>
            <div style={{ fontSize: '15px', color: '#e2e8f0', marginTop: '4px' }}>{transcript}</div>
          </div>
        )}

        {response && (
          <div style={{
            padding: '12px 16px',
            borderRadius: '10px',
            backgroundColor: '#1e1b4b',
            borderLeft: '4px solid #a855f7',
          }}>
            <span style={{ fontSize: '11px', color: '#c084fc', display: 'block' }}>VASU (Kore Voice):</span>
            <div style={{ fontSize: '15px', color: '#f3e8ff', marginTop: '4px' }}>{response}</div>
          </div>
        )}
      </div>

      {/* Safe Diagnostics Box */}
      <div style={{
        width: '100%',
        maxWidth: '560px',
        backgroundColor: '#020617',
        border: '1px solid #1e293b',
        borderRadius: '10px',
        padding: '12px',
        boxSizing: 'border-box'
      }}>
        <div style={{
          fontSize: '11px',
          fontWeight: 700,
          letterSpacing: '1px',
          color: '#64748b',
          marginBottom: '8px',
          textTransform: 'uppercase',
        }}>
          Live Diagnostic Logs
        </div>
        <div style={{
          maxHeight: '120px',
          overflowY: 'auto',
          fontSize: '11px',
          fontFamily: 'monospace',
          color: '#38bdf8',
          lineHeight: '1.4',
        }}>
          {logs.length === 0 ? (
            <div style={{ color: '#475569' }}>Logs will appear here once connected...</div>
          ) : (
            logs.map((log, i) => <div key={i}>{log}</div>)
          )}
        </div>
      </div>
    </div>
  );
};
