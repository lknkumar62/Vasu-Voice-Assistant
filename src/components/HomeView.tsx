import React, { useState } from 'react';
import { AssistantState, ChatMessage } from '../types';
import { VasuOrb } from './VasuOrb';
import {
  Mic,
  MicOff,
  Send,
  Volume2,
  Copy,
  Check,
  Sparkles,
  ArrowRight,
  Flashlight,
  Clock,
  Battery,
  ShieldCheck,
  Bot,
  User,
  Zap,
  Phone,
  MessageCircle,
  Camera,
  Eye,
  Monitor,
  Cpu,
  Wrench,
  Lightbulb,
} from 'lucide-react';

interface HomeViewProps {
  assistantState: AssistantState;
  audioLevel?: number;
  activeToolName?: string;
  messages: ChatMessage[];
  liveVoiceTranscript?: string;
  isContinuousListening: boolean;
  isTorchActive: boolean;
  onToggleListen: () => void;
  onSendMessage: (text: string) => void;
  onReplayAudio: (text: string) => void;
  onNavigateToChat: () => void;
  onToggleTorch: () => void;
  onSelectTab?: (tab: any) => void;
}

export const HomeView: React.FC<HomeViewProps> = ({
  assistantState,
  audioLevel = 0,
  activeToolName,
  messages,
  liveVoiceTranscript = '',
  isContinuousListening,
  isTorchActive,
  onToggleListen,
  onSendMessage,
  onReplayAudio,
  onNavigateToChat,
  onToggleTorch,
  onSelectTab,
}) => {
  const [inputText, setInputText] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Find the latest user message and latest VASU response
  const latestUserMsg = [...messages].reverse().find((m) => m.sender === 'user');
  const latestVasuMsg = [...messages].reverse().find((m) => m.sender === 'vasu');

  const isListening = assistantState === 'LISTENING';
  const isThinking = assistantState === 'THINKING' || assistantState === 'EXECUTING';

  const handleSend = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const clean = inputText.trim();
    if (!clean) return;
    setInputText('');
    onSendMessage(clean);
  };

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1800);
  };

  return (
    <div className="w-full max-w-lg mx-auto flex flex-col items-center px-4 py-2 space-y-4 pb-24 select-none animate-in fade-in duration-300">
      {/* 1. Header Greeting (From Screenshot 2) */}
      <div className="w-full text-center space-y-0.5 pt-1">
        <h1 className="text-xl font-bold text-slate-100 tracking-tight flex items-center justify-center gap-1.5">
          <span>Hello, Lawkush</span>
          <span className="text-lg">👋</span>
        </h1>
        <p className="text-xs text-slate-400 font-sans">
          I'm VASU. How can I assist you today?
        </p>
      </div>

      {/* 2. Center: Soundwave Orb */}
      <div className="relative my-2 flex items-center justify-center">
        <VasuOrb
          state={assistantState}
          audioLevel={audioLevel}
          activeToolName={activeToolName}
          onClick={onToggleListen}
          size="hero"
        />
      </div>

      {/* 3. Live Speech Transcript or Recent Dialogue */}
      {(liveVoiceTranscript || latestVasuMsg) && (
        <div className="w-full bg-slate-900/80 border border-slate-800 rounded-2xl p-3 shadow-md backdrop-blur-sm space-y-1 animate-in fade-in duration-200">
          <div className="flex items-center justify-between text-[10px] font-mono text-cyan-400">
            <span className="flex items-center gap-1">
              <Sparkles className="w-3 h-3" />
              {isListening ? 'Listening...' : 'VASU Response'}
            </span>
            {latestVasuMsg && (
              <button
                onClick={() => onReplayAudio(latestVasuMsg.text)}
                className="text-slate-400 hover:text-cyan-300 cursor-pointer flex items-center gap-1"
              >
                <Volume2 className="w-3 h-3" /> Replay
              </button>
            )}
          </div>
          <p className="text-xs text-slate-200 leading-relaxed font-sans">
            {liveVoiceTranscript || latestVasuMsg?.text}
          </p>
        </div>
      )}

      {/* 4. Two Quick Action Tiles (Voice Mode & Visual Lens) */}
      <div className="w-full grid grid-cols-2 gap-2.5">
        <button
          type="button"
          onClick={onToggleListen}
          className={`flex items-center gap-2.5 p-3 rounded-2xl border transition-all cursor-pointer text-left ${
            isListening
              ? 'bg-cyan-950/80 border-cyan-500 shadow-lg shadow-cyan-500/20'
              : 'bg-slate-900/80 hover:bg-slate-850 border-slate-800 hover:border-cyan-500/40'
          }`}
        >
          <div className="w-9 h-9 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
            <Mic className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs font-bold text-slate-100">Voice Mode</div>
            <div className="text-[10px] text-slate-400">Talk to VASU</div>
          </div>
        </button>

        <button
          type="button"
          onClick={() => onSendMessage('कैमरा खोलो')}
          className="flex items-center gap-2.5 p-3 rounded-2xl bg-slate-900/80 hover:bg-slate-850 border border-slate-800 hover:border-cyan-500/40 transition-all cursor-pointer text-left"
        >
          <div className="w-9 h-9 rounded-xl bg-indigo-500/15 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shrink-0">
            <Eye className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs font-bold text-slate-100">Visual Lens</div>
            <div className="text-[10px] text-slate-400">See & Analyze</div>
          </div>
        </button>
      </div>

      {/* 5. Four Circular Quick Action Buttons (Call, WhatsApp, Camera, Torch) */}
      <div className="w-full bg-slate-900/60 border border-slate-800/80 rounded-2xl p-3 flex items-center justify-around">
        {/* Call */}
        <button
          type="button"
          onClick={() => onSendMessage('Call dialer kholo')}
          className="flex flex-col items-center gap-1 cursor-pointer group"
        >
          <div className="w-11 h-11 rounded-full bg-slate-950 border border-slate-700/80 group-hover:border-emerald-500 flex items-center justify-center text-emerald-400 group-hover:scale-105 transition-all shadow-md shadow-emerald-950/40">
            <Phone className="w-5 h-5" />
          </div>
          <span className="text-[10px] font-medium text-slate-300 group-hover:text-emerald-300">Call</span>
        </button>

        {/* WhatsApp */}
        <button
          type="button"
          onClick={() => onSendMessage('WhatsApp kholo')}
          className="flex flex-col items-center gap-1 cursor-pointer group"
        >
          <div className="w-11 h-11 rounded-full bg-slate-950 border border-slate-700/80 group-hover:border-green-500 flex items-center justify-center text-green-400 group-hover:scale-105 transition-all shadow-md shadow-green-950/40">
            <MessageCircle className="w-5 h-5" />
          </div>
          <span className="text-[10px] font-medium text-slate-300 group-hover:text-green-300">WhatsApp</span>
        </button>

        {/* Camera */}
        <button
          type="button"
          onClick={() => onSendMessage('Camera kholo')}
          className="flex flex-col items-center gap-1 cursor-pointer group"
        >
          <div className="w-11 h-11 rounded-full bg-slate-950 border border-slate-700/80 group-hover:border-cyan-500 flex items-center justify-center text-cyan-400 group-hover:scale-105 transition-all shadow-md shadow-cyan-950/40">
            <Camera className="w-5 h-5" />
          </div>
          <span className="text-[10px] font-medium text-slate-300 group-hover:text-cyan-300">Camera</span>
        </button>

        {/* Torch */}
        <button
          type="button"
          onClick={onToggleTorch}
          className="flex flex-col items-center gap-1 cursor-pointer group"
        >
          <div className={`w-11 h-11 rounded-full border flex items-center justify-center group-hover:scale-105 transition-all shadow-md ${
            isTorchActive
              ? 'bg-amber-500/20 border-amber-400 text-amber-300 shadow-amber-500/30'
              : 'bg-slate-950 border-slate-700/80 text-amber-400 group-hover:border-amber-400'
          }`}>
            <Flashlight className="w-5 h-5" />
          </div>
          <span className={`text-[10px] font-medium ${isTorchActive ? 'text-amber-300' : 'text-slate-300 group-hover:text-amber-300'}`}>
            Torch {isTorchActive ? 'ON' : ''}
          </span>
        </button>
      </div>

      {/* 6. Four Action Grid Cards */}
      <div className="w-full grid grid-cols-2 gap-2.5">
        {/* JARVIS Mode */}
        <button
          type="button"
          onClick={() => onSendMessage('Start continuous chat with me')}
          className="p-3 rounded-2xl bg-slate-900/80 hover:bg-slate-850 border border-slate-800 hover:border-cyan-500/40 text-left transition-all cursor-pointer group"
        >
          <div className="w-8 h-8 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 mb-2 group-hover:scale-105 transition-transform">
            <Bot className="w-4 h-4" />
          </div>
          <div className="text-xs font-bold text-slate-100 group-hover:text-cyan-300">JARVIS Mode</div>
          <div className="text-[10px] text-slate-400">PC Assistant</div>
        </button>

        {/* Device Control */}
        <button
          type="button"
          onClick={() => onSelectTab?.('TOOLS')}
          className="p-3 rounded-2xl bg-slate-900/80 hover:bg-slate-850 border border-slate-800 hover:border-emerald-500/40 text-left transition-all cursor-pointer group"
        >
          <div className="w-8 h-8 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 mb-2 group-hover:scale-105 transition-transform">
            <Cpu className="w-4 h-4" />
          </div>
          <div className="text-xs font-bold text-slate-100 group-hover:text-emerald-300">Device Control</div>
          <div className="text-[10px] text-slate-400">System Actions</div>
        </button>

        {/* AI Vision */}
        <button
          type="button"
          onClick={() => onSendMessage('कैमरा खोलो और बताओ क्या दिख रहा है')}
          className="p-3 rounded-2xl bg-slate-900/80 hover:bg-slate-850 border border-slate-800 hover:border-purple-500/40 text-left transition-all cursor-pointer group"
        >
          <div className="w-8 h-8 rounded-xl bg-purple-500/15 border border-purple-500/30 flex items-center justify-center text-purple-400 mb-2 group-hover:scale-105 transition-transform">
            <Eye className="w-4 h-4" />
          </div>
          <div className="text-xs font-bold text-slate-100 group-hover:text-purple-300">AI Vision</div>
          <div className="text-[10px] text-slate-400">See & Analyze</div>
        </button>

        {/* Smart Tools */}
        <button
          type="button"
          onClick={() => onSelectTab?.('TOOLS')}
          className="p-3 rounded-2xl bg-slate-900/80 hover:bg-slate-850 border border-slate-800 hover:border-amber-500/40 text-left transition-all cursor-pointer group"
        >
          <div className="w-8 h-8 rounded-xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-amber-400 mb-2 group-hover:scale-105 transition-transform">
            <Wrench className="w-4 h-4" />
          </div>
          <div className="text-xs font-bold text-slate-100 group-hover:text-amber-300">Smart Tools</div>
          <div className="text-[10px] text-slate-400">Multiple Utilities</div>
        </button>
      </div>

      {/* 7. Today's Insight Card (From Screenshot 2) */}
      <div className="w-full p-3 bg-gradient-to-r from-slate-900 via-cyan-950/20 to-slate-900 border border-cyan-500/20 rounded-2xl space-y-1">
        <div className="flex items-center gap-1.5 text-[10px] font-mono text-cyan-400 font-bold uppercase tracking-wider">
          <Lightbulb className="w-3.5 h-3.5 text-amber-400" />
          <span>Today's Insight</span>
        </div>
        <p className="text-xs text-slate-300 font-sans italic">
          "A smarter tomorrow begins with what you do today."
        </p>
      </div>

      {/* 8. Bottom Chat Input */}
      <form onSubmit={handleSend} className="w-full flex items-center gap-2 pt-1">
        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="Ask VASU anything..."
          className="flex-1 bg-slate-900/90 border border-slate-800 focus:border-cyan-500/60 rounded-2xl px-3.5 py-2.5 text-xs text-slate-100 placeholder-slate-500 outline-none transition-all shadow-inner"
        />
        <button
          type="submit"
          disabled={!inputText.trim()}
          className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 text-slate-950 p-2.5 rounded-2xl cursor-pointer transition-all shadow-md shadow-cyan-500/20 active:scale-95 shrink-0"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};
