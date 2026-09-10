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
  Sparkles,
  Menu,
  Search,
  Plus,
  Paperclip,
  Camera,
  Image,
  Code,
  FileText,
  Calendar,
  Lightbulb,
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

  const quickChatActions = [
    { icon: Sparkles, label: 'Explain a topic', cmd: 'Explain quantum computing in simple terms' },
    { icon: Image, label: 'Generate an image', cmd: 'Create a futuristic city image' },
    { icon: Code, label: 'Write code', cmd: 'Write a Python function to sort a list' },
    { icon: FileText, label: 'Summarize a document', cmd: 'Summarize this document for me' },
    { icon: Calendar, label: 'Plan my day', cmd: 'Plan my day for tomorrow' },
    { icon: Lightbulb, label: 'Give me ideas', cmd: 'Give me 5 business ideas for 2026' },
  ];

  const deviceActions = [
    { icon: Camera, label: 'Take a photo', cmd: 'Camera kholo' },
    { icon: Mic, label: 'Open YouTube', cmd: 'YouTube kholo' },
    { icon: Mic, label: 'Turn on torch', cmd: 'Torch on karo' },
    { icon: User, label: 'Call someone', cmd: 'Call dialer kholo' },
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
      if (st !== 'SPEAKING') setPlayingMsgId(null);
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

  const showEmptyState = messages.length === 0 && !isListening && !liveVoiceTranscript;

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-w-2xl mx-auto w-full bg-[#01060D]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-[#008CFF]/10">
        <button className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
          <Menu className="w-4 h-4" />
        </button>
        <div className="text-center">
          <h1 className="text-[#F4F8FF] font-bold text-base">VASU</h1>
        </div>
        <div className="flex items-center gap-2">
          <button className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
            <Search className="w-4 h-4" />
          </button>
          <button className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
            <Plus className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Title */}
      <div className="px-4 py-3">
        <h2 className="text-[#F4F8FF] text-lg font-bold">Chat</h2>
        <p className="text-[#7895B8] text-xs">Talk. Ask. Explore. VASU is always with you.</p>
      </div>

      {/* VASU Profile Header */}
      <div className="mx-4 mb-3 p-3 bg-[#061827] border border-[#008CFF]/10 rounded-2xl flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-[#008CFF]/10 border border-[#008CFF]/30 flex items-center justify-center">
          <Bot className="w-5 h-5 text-[#008CFF]" />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-[#F4F8FF] font-bold text-sm">VASU</span>
            <span className="w-2 h-2 rounded-full bg-emerald-400" />
          </div>
          <p className="text-[10px] text-[#7895B8]">Online • Ready to help</p>
        </div>
        <button className="w-8 h-8 rounded-lg bg-[#061827] border border-[#008CFF]/10 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
          <Volume2 className="w-4 h-4" />
        </button>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 space-y-3">
        {showEmptyState ? (
          <div className="text-center py-8">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-[#008CFF]/10 border border-[#008CFF]/20 flex items-center justify-center">
              <Bot className="w-8 h-8 text-[#008CFF]" />
            </div>
            <p className="text-[#F4F8FF] font-bold">Hello, User 👋</p>
            <p className="text-[#7895B8] text-sm mt-1">I'm VASU, your AI companion.</p>
            <p className="text-[#7895B8] text-xs">How can I assist you today?</p>

            {/* Quick Chat Actions */}
            <div className="grid grid-cols-2 gap-2 mt-6 text-left">
              {quickChatActions.map((action, i) => (
                <button
                  key={i}
                  onClick={() => onSendMessage(action.cmd)}
                  className="p-3 bg-[#061827] border border-[#008CFF]/10 rounded-xl hover:border-[#008CFF]/30 transition-all cursor-pointer text-left"
                >
                  <action.icon className="w-4 h-4 text-[#008CFF] mb-1.5" />
                  <p className="text-[11px] text-[#F4F8FF] font-medium">{action.label}</p>
                </button>
              ))}
            </div>

            {/* Device Actions */}
            <div className="mt-4">
              <p className="text-[10px] text-[#7895B8] font-mono uppercase tracking-wider mb-2">Device Actions</p>
              <div className="grid grid-cols-2 gap-2 text-left">
                {deviceActions.map((action, i) => (
                  <button
                    key={i}
                    onClick={() => onSendMessage(action.cmd)}
                    className="p-2.5 bg-[#061827] border border-[#008CFF]/10 rounded-xl hover:border-[#008CFF]/30 transition-all cursor-pointer flex items-center gap-2"
                  >
                    <action.icon className="w-3.5 h-3.5 text-[#008CFF]" />
                    <span className="text-[10px] text-[#F4F8FF]">{action.label}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <>
            {/* Date Pill */}
            <div className="flex justify-center">
              <span className="text-[10px] text-[#7895B8] bg-[#061827] border border-[#008CFF]/10 px-3 py-1 rounded-full">
                Today
              </span>
            </div>

            {messages.map((msg) => {
              const isUser = msg.sender === 'user';
              const isSystem = msg.sender === 'system';

              if (isSystem) {
                return (
                  <div key={msg.id} className="flex justify-center my-2">
                    <div className="bg-[#061827] border border-[#008CFF]/10 text-[#7895B8] text-xs px-3.5 py-1.5 rounded-xl flex items-center gap-2">
                      {msg.badge ? (
                        <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-[#008CFF]/10 text-[#008CFF] border border-[#008CFF]/20">
                          {msg.badge}
                        </span>
                      ) : (
                        <AlertCircle className="w-3.5 h-3.5 text-[#008CFF] shrink-0" />
                      )}
                      <span className="font-mono text-[11px]">{msg.text}</span>
                    </div>
                  </div>
                );
              }

              return (
                <div key={msg.id} className={`flex w-full ${isUser ? 'justify-end' : 'justify-start'}`}>
                  <div className={`rounded-2xl transition-all ${
                    isUser
                      ? 'max-w-[85%] bg-[#008CFF]/20 text-[#F4F8FF] rounded-tr-sm px-4 py-2.5 text-[13px] leading-relaxed border border-[#008CFF]/10'
                      : 'max-w-[88%] bg-[#061827] border border-[#008CFF]/10 text-[#F4F8FF] rounded-tl-sm p-4 text-[13px] leading-relaxed'
                  }`}>
                    {msg.toolCall && (
                      <div className="mb-2.5 p-2 rounded-lg bg-[#01060D] border border-[#008CFF]/10 text-xs font-mono">
                        <div className="flex items-center justify-between text-[#008CFF] mb-1">
                          <span className="flex items-center gap-1 font-semibold">
                            <Terminal className="w-3 h-3" />
                            <span>Tool: {msg.toolCall.tool}</span>
                          </span>
                          <span className={`px-1.5 py-0.5 rounded text-[10px] uppercase font-bold ${
                            msg.toolCall.status === 'success'
                              ? 'bg-emerald-950 text-emerald-300 border border-emerald-800/60'
                              : msg.toolCall.status === 'executing'
                              ? 'bg-[#008CFF]/10 text-[#008CFF] border border-[#008CFF]/20 animate-pulse'
                              : 'bg-rose-950 text-rose-300 border border-rose-800/60'
                          }`}>
                            {msg.toolCall.status}
                          </span>
                        </div>
                        {msg.toolCall.result && (
                          <div className="text-[#7895B8] text-[11px] truncate">{msg.toolCall.result}</div>
                        )}
                      </div>
                    )}

                    <p className="whitespace-pre-wrap">{msg.text}</p>

                    {!isUser && (
                      <div className="flex items-center gap-3 mt-3 pt-2 border-t border-[#008CFF]/5 text-[#7895B8]">
                        <button
                          title="Copy text"
                          onClick={() => handleCopy(msg.id, msg.text)}
                          className="inline-flex items-center gap-1.5 text-xs hover:text-[#F4F8FF] transition-colors cursor-pointer"
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
                              ? 'text-[#008CFF] font-semibold animate-pulse'
                              : 'hover:text-[#00C8FF]'
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
            })}

            {/* Live Voice Transcription */}
            {(isListening || liveVoiceTranscript) && (
              <div className="flex justify-end my-2">
                <div className="max-w-[85%] bg-[#008CFF]/20 border border-[#008CFF]/30 rounded-2xl rounded-tr-sm p-3.5 text-sm text-[#F4F8FF]">
                  <div className="flex items-center justify-between gap-2 mb-1 text-[#008CFF] text-[11px] font-mono">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-[#008CFF] animate-ping" />
                      <Mic className="w-3.5 h-3.5" />
                      <span>Listening...</span>
                    </div>
                    <div className="flex items-center gap-0.5 h-3 px-1">
                      {[0.3, 0.6, 1.0, 0.7, 0.4].map((scale, idx) => (
                        <span
                          key={idx}
                          className="w-1 bg-[#008CFF] rounded-full transition-all duration-75"
                          style={{ height: `${Math.max(3, Math.min(14, (audioLevel || 0.18) * scale * 18))}px` }}
                        />
                      ))}
                    </div>
                  </div>
                  <p className="font-medium">
                    {liveVoiceTranscript ? `"${liveVoiceTranscript}"` : (
                      <span className="text-[#7895B8] italic">Boliye, main sun rahi hoon...</span>
                    )}
                  </p>
                </div>
              </div>
            )}

            {isLoading && !isListening && (
              <div className="flex justify-start items-center">
                <div className="bg-[#061827] border border-[#008CFF]/10 rounded-2xl rounded-tl-sm p-3 text-xs text-[#008CFF] font-mono flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#008CFF] animate-ping" />
                  <span>VASU soch rahi hai...</span>
                </div>
              </div>
            )}
          </>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input Bar */}
      <form onSubmit={handleSubmit} className="mx-4 mb-3 flex items-center gap-2">
        <button type="button" className="w-10 h-10 rounded-xl bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer shrink-0">
          <Paperclip className="w-4 h-4" />
        </button>
        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="Ask VASU anything..."
          className="flex-1 bg-[#061827] border border-[#008CFF]/20 focus:border-[#008CFF]/60 rounded-xl px-4 py-2.5 text-sm text-[#F4F8FF] placeholder-[#7895B8]/60 outline-none transition-all"
        />
        {onToggleMic && (
          <button
            type="button"
            onClick={onToggleMic}
            className={`w-10 h-10 rounded-xl border flex items-center justify-center transition-all cursor-pointer shrink-0 ${
              isListening
                ? 'bg-[#008CFF] text-[#01060D] border-[#008CFF] shadow-lg shadow-[#008CFF]/30'
                : 'bg-[#061827] border-[#008CFF]/20 text-[#7895B8] hover:text-[#008CFF]'
            }`}
          >
            <Mic className="w-4 h-4" />
          </button>
        )}
        <button
          type="submit"
          disabled={!inputText.trim() || isLoading}
          className="w-10 h-10 bg-[#008CFF] hover:bg-[#00C8FF] disabled:opacity-40 text-[#01060D] rounded-xl cursor-pointer transition-all flex items-center justify-center shrink-0"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};
