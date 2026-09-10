import React, { useState, useRef, useEffect } from 'react';
import { ChatMessage, AssistantState } from '../types';
import {
  Send,
  Volume2,
  VolumeX,
  Copy,
  Check,
  Trash2,
  Terminal,
  User,
  Bot,
  AlertCircle,
  Mic,
  Radio,
  Sparkles,
  Zap,
} from 'lucide-react';
import { audioEngine } from '../services/audioEngine';

interface ChatViewProps {
  messages: ChatMessage[];
  onSendMessage: (text: string) => void;
  onClearChat: () => void;
  isLoading: boolean;
  liveVoiceTranscript?: string;
  isListening?: boolean;
  audioLevel?: number;
  assistantState?: AssistantState;
  onToggleMic?: () => void;
  isContinuousListening?: boolean;
}

export const ChatView: React.FC<ChatViewProps> = ({
  messages,
  onSendMessage,
  onClearChat,
  isLoading,
  liveVoiceTranscript = '',
  isListening = false,
  audioLevel = 0,
  assistantState = 'IDLE',
  onToggleMic,
  isContinuousListening = false,
}) => {
  const [inputText, setInputText] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const samplePrompts = [
    'hello vasu',
    'kya chal raha hai?',
    'torch on kar do',
    'battery kitni hai?',
    'time kya hua hai?',
    'kuch fun sunao na',
  ];

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading, liveVoiceTranscript, isListening]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || isLoading) return;
    onSendMessage(inputText);
    setInputText('');
  };

  const [playingMsgId, setPlayingMsgId] = useState<string | null>(null);

  useEffect(() => {
    const unsub = audioEngine.onStateChange((st) => {
      if (st !== 'SPEAKING') {
        setPlayingMsgId(null);
      }
    });
    return unsub;
  }, []);

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleSpeak = (msgId: string, text: string) => {
    if (playingMsgId === msgId || (playingMsgId && audioEngine.isSpeaking())) {
      audioEngine.stop();
      setPlayingMsgId(null);
      return;
    }
    setPlayingMsgId(msgId);
    audioEngine.speakAssistantResponse(text, {
      source: 'replay',
      onStart: () => setPlayingMsgId(msgId),
      onEnd: () => setPlayingMsgId(null),
    });
  };

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-w-2xl mx-auto w-full px-3">
      {/* Top clean status bar */}
      <div className="flex items-center justify-between py-1.5 px-1 border-b border-slate-800/60 mb-2">
        <div className="flex items-center gap-2">
          {isListening ? (
            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-cyan-950/80 border border-cyan-500/50 text-xs font-mono text-cyan-300">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
              <span className="font-semibold">लगातार सुन रही हूँ (Always Listening)</span>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 text-xs text-slate-400 font-mono">
              <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
              <span>VASU AI Assistant (Hinglish)</span>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          {onToggleMic && (
            <button
              onClick={onToggleMic}
              className={`text-xs px-2.5 py-1 rounded-full border flex items-center gap-1 transition-all cursor-pointer ${
                isListening
                  ? 'bg-rose-950/80 border-rose-500/60 text-rose-300 hover:bg-rose-900'
                  : 'bg-slate-900 border-slate-800 text-slate-300 hover:text-cyan-300'
              }`}
            >
              <Mic className={`w-3 h-3 ${isListening ? 'text-rose-400 animate-pulse' : 'text-slate-400'}`} />
              <span>{isListening ? 'Pause Voice' : 'Auto-Listen'}</span>
            </button>
          )}

          <button
            id="btn-clear-chat"
            onClick={onClearChat}
            className="text-xs text-slate-500 hover:text-rose-400 flex items-center gap-1 transition-colors px-2 py-1 rounded hover:bg-slate-900 cursor-pointer"
            title="Clear conversation"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Clear</span>
          </button>
        </div>
      </div>

      {/* Messages List */}
      <div className="flex-1 overflow-y-auto space-y-3.5 pr-1 pb-2">
        {messages.length === 0 && !isListening && !liveVoiceTranscript ? (
          <div className="text-center py-16 px-4">
            <p className="text-sm font-medium text-slate-300">VASU aapke saath baat karne ke liye taiyaar hai.</p>
            <p className="text-xs text-slate-500 mt-1 max-w-xs mx-auto">
              Neeche mic icon tap karke ya text likh kar baat shuru kijiye.
            </p>
            <div className="flex flex-wrap gap-2 justify-center mt-5">
              {samplePrompts.slice(0, 4).map((prompt, idx) => (
                <button
                  key={idx}
                  onClick={() => onSendMessage(prompt)}
                  className="text-xs bg-slate-900/90 border border-slate-800 hover:border-cyan-500/50 text-cyan-300 px-3 py-1.5 rounded-full transition-colors cursor-pointer"
                >
                  "{prompt}"
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg) => {
            const isUser = msg.sender === 'user';
            const isSystem = msg.sender === 'system';

            if (isSystem) {
              return (
                <div key={msg.id} className="flex justify-center my-2">
                  <div className="bg-slate-900/90 border border-slate-800/90 text-slate-300 text-xs px-3.5 py-1.5 rounded-xl flex items-center gap-2 shadow-sm">
                    {msg.badge ? (
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800/80">
                        {msg.badge}
                      </span>
                    ) : (
                      <AlertCircle className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                    )}
                    <span className="font-mono text-[11px]">{msg.text}</span>
                  </div>
                </div>
              );
            }

            return (
              <div
                key={msg.id}
                className={`flex w-full ${isUser ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`rounded-2xl transition-all ${
                    isUser
                      ? 'max-w-[85%] bg-[#1e293b] text-slate-100 rounded-tr-sm px-4 py-2.5 text-[15px] leading-relaxed shadow-sm'
                      : 'max-w-[88%] bg-[#151b28] border border-slate-800/80 text-slate-100 rounded-tl-sm p-4 text-[15px] leading-relaxed shadow-md'
                  }`}
                >
                  {/* Tool Call Card if attached */}
                  {msg.toolCall && (
                    <div className="mb-2.5 p-2 rounded-lg bg-slate-950/80 border border-slate-800 text-xs font-mono">
                      <div className="flex items-center justify-between text-cyan-300 mb-1">
                        <span className="flex items-center gap-1 font-semibold">
                          <Terminal className="w-3 h-3 text-cyan-400" />
                          <span>Tool: {msg.toolCall.tool}</span>
                        </span>
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] uppercase font-bold ${
                            msg.toolCall.status === 'success'
                              ? 'bg-emerald-950 text-emerald-300 border border-emerald-800/60'
                              : msg.toolCall.status === 'executing'
                              ? 'bg-cyan-950 text-cyan-300 border border-cyan-800/60 animate-pulse'
                              : 'bg-rose-950 text-rose-300 border border-rose-800/60'
                          }`}
                        >
                          {msg.toolCall.status}
                        </span>
                      </div>
                      {msg.toolCall.result && (
                        <div className="text-slate-400 text-[11px] truncate">
                          {msg.toolCall.result}
                        </div>
                      )}
                    </div>
                  )}

                  <p className="whitespace-pre-wrap">{msg.text}</p>

                  {/* Clean Copy Button matching photo */}
                  {!isUser && (
                    <div className="flex items-center gap-3 mt-3 pt-2 border-t border-slate-800/40 text-slate-400">
                      <button
                        title="Copy text"
                        onClick={() => handleCopy(msg.id, msg.text)}
                        className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                      >
                        {copiedId === msg.id ? (
                          <>
                            <Check className="w-3.5 h-3.5 text-emerald-400" />
                            <span className="text-emerald-400 font-medium">Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>Copy</span>
                          </>
                        )}
                      </button>

                      <button
                        title={playingMsgId === msg.id ? "Stop Speech" : "Replay Voice"}
                        onClick={() => handleSpeak(msg.id, msg.text)}
                        className={`inline-flex items-center gap-1 text-xs transition-colors cursor-pointer ${
                          playingMsgId === msg.id
                            ? 'text-cyan-400 font-semibold animate-pulse'
                            : 'text-slate-400 hover:text-cyan-300'
                        }`}
                      >
                        {playingMsgId === msg.id ? (
                          <>
                            <VolumeX className="w-3.5 h-3.5" />
                            <span>Stop</span>
                          </>
                        ) : (
                          <>
                            <Volume2 className="w-3.5 h-3.5" />
                            <span>Play</span>
                          </>
                        )}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}

        {/* Live Voice Transcription Bubble */}
        {(isListening || liveVoiceTranscript) && (
          <div className="flex justify-end my-2">
            <div className="max-w-[85%] bg-[#1e293b] border border-cyan-500/60 rounded-2xl rounded-tr-sm p-3.5 text-sm text-cyan-200 shadow-lg shadow-cyan-950/40">
              <div className="flex items-center justify-between gap-2 mb-1 text-cyan-300 text-[11px] font-mono">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
                  <Mic className="w-3.5 h-3.5 text-cyan-300" />
                  <span>सुन रही हूँ (Listening...)</span>
                </div>
                {/* Audio Level Waveform */}
                <div className="flex items-center gap-0.5 h-3 px-1">
                  {[0.3, 0.6, 1.0, 0.7, 0.4].map((scale, idx) => (
                    <span
                      key={idx}
                      className="w-1 bg-cyan-400 rounded-full transition-all duration-75"
                      style={{
                        height: `${Math.max(3, Math.min(14, (audioLevel || 0.18) * scale * 18))}px`,
                      }}
                    />
                  ))}
                </div>
              </div>
              <p className="font-sans font-medium text-white">
                {liveVoiceTranscript ? (
                  <span>"{liveVoiceTranscript}"</span>
                ) : (
                  <span className="text-slate-400 italic">बोलिए, मैं लगातार सुन रही हूँ...</span>
                )}
              </p>
            </div>
          </div>
        )}

        {isLoading && !isListening && (
          <div className="flex justify-start items-center">
            <div className="bg-[#151b28] border border-slate-800 rounded-2xl rounded-tl-sm p-3 text-xs text-cyan-400 font-mono flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
              <span>VASU soch rahi hai aur reply taiyaar kar rahi hai...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggested quick chips */}
      <div className="py-2 flex gap-1.5 overflow-x-auto no-scrollbar shrink-0">
        {samplePrompts.map((prompt, i) => (
          <button
            key={i}
            onClick={() => onSendMessage(prompt)}
            className="text-[11px] bg-slate-900 border border-slate-800/90 hover:border-cyan-500/50 text-slate-300 px-2.5 py-1 rounded-full whitespace-nowrap transition-colors cursor-pointer"
          >
            {prompt}
          </button>
        ))}
      </div>

      {/* Input bar */}
      <form onSubmit={handleSubmit} className="flex gap-2 pb-2 shrink-0 items-center">
        {/* Continuous Voice Toggle Button */}
        {onToggleMic && (
          <button
            id="chat-btn-voice-toggle"
            type="button"
            onClick={onToggleMic}
            className={`p-2.5 rounded-xl border flex items-center justify-center transition-all cursor-pointer ${
              isListening
                ? 'bg-cyan-500 hover:bg-cyan-400 text-slate-950 border-cyan-400 shadow-lg shadow-cyan-500/50 scale-105'
                : assistantState === 'SPEAKING'
                ? 'bg-indigo-600 hover:bg-indigo-500 text-white border-indigo-400 animate-pulse'
                : 'bg-slate-900 hover:bg-slate-850 text-cyan-400 border-slate-800 hover:border-cyan-500/50'
            }`}
            title={isListening ? 'Stop continuous listening' : 'Start continuous listening'}
          >
            <Mic className="w-4 h-4" />
          </button>
        )}

        <input
          id="chat-input-field"
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="VASU se baat kijiye ya command boliye..."
          className="flex-1 bg-[#151b28] border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-cyan-500/40 focus:border-cyan-500/60"
        />
        <button
          id="btn-send-message"
          type="submit"
          disabled={!inputText.trim() || isLoading}
          className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-slate-950 font-semibold px-4 py-2.5 rounded-xl flex items-center justify-center transition-colors cursor-pointer"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};
