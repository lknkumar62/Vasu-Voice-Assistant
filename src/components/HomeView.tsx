import React, { useState } from 'react';
import { AssistantState, ChatMessage } from '../types';
import { VasuOrb } from './VasuOrb';
import {
  Mic,
  Send,
  Volume2,
  Sparkles,
  Flashlight,
  Phone,
  MessageCircle,
  Camera,
  Youtube,
  MapPin,
  Settings,
  MoreHorizontal,
  Battery,
  Wifi,
  Shield,
  Zap,
  Lightbulb,
  Menu,
  User,
  Bot,
  Eye,
  Cpu,
  Wrench,
  Search,
  Paperclip,
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

  const quickActions = [
    { icon: Phone, label: 'Call', color: 'emerald', cmd: 'Call dialer kholo' },
    { icon: MessageCircle, label: 'WhatsApp', color: 'green', cmd: 'WhatsApp kholo' },
    { icon: Camera, label: 'Camera', color: 'cyan', cmd: 'Camera kholo' },
    { icon: Flashlight, label: 'Torch', color: 'amber', cmd: '', isTorch: true },
    { icon: Youtube, label: 'YouTube', color: 'red', cmd: 'YouTube kholo' },
    { icon: MapPin, label: 'Maps', color: 'blue', cmd: 'Google Maps kholo' },
    { icon: Settings, label: 'Settings', color: 'slate', cmd: '', isSettings: true },
    { icon: MoreHorizontal, label: 'More', color: 'purple', cmd: 'Aur tools dikhao' },
  ];

  const colorMap: Record<string, { bg: string; border: string; text: string; hover: string }> = {
    emerald: { bg: 'bg-emerald-500/10', border: 'border-emerald-500/30', text: 'text-emerald-400', hover: 'hover:border-emerald-400' },
    green: { bg: 'bg-green-500/10', border: 'border-green-500/30', text: 'text-green-400', hover: 'hover:border-green-400' },
    cyan: { bg: 'bg-[#008CFF]/10', border: 'border-[#008CFF]/30', text: 'text-[#008CFF]', hover: 'hover:border-[#00C8FF]' },
    amber: { bg: 'bg-amber-500/10', border: 'border-amber-500/30', text: 'text-amber-400', hover: 'hover:border-amber-400' },
    red: { bg: 'bg-red-500/10', border: 'border-red-500/30', text: 'text-red-400', hover: 'hover:border-red-400' },
    blue: { bg: 'bg-blue-500/10', border: 'border-blue-500/30', text: 'text-blue-400', hover: 'hover:border-blue-400' },
    slate: { bg: 'bg-slate-500/10', border: 'border-slate-500/30', text: 'text-slate-400', hover: 'hover:border-slate-300' },
    purple: { bg: 'bg-purple-500/10', border: 'border-purple-500/30', text: 'text-purple-400', hover: 'hover:border-purple-400' },
  };

  return (
    <div className="w-full max-w-lg mx-auto flex flex-col min-h-full bg-[#01060D] select-none">
      {/* Top Header */}
      <div className="flex items-center justify-between px-4 py-3">
        <button className="w-10 h-10 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
          <Menu className="w-5 h-5" />
        </button>
        <div className="text-center">
          <h1 className="text-[#F4F8FF] font-bold text-lg tracking-tight">VASU</h1>
          <p className="text-[10px] text-[#7895B8] -mt-0.5">YOUR AI COMPANION</p>
        </div>
        <button className="w-10 h-10 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
          <User className="w-5 h-5" />
        </button>
      </div>

      {/* Greeting */}
      <div className="text-center px-4 py-2">
        <h2 className="text-[#F4F8FF] text-xl font-bold">Hello, User</h2>
        <p className="text-[#7895B8] text-sm mt-0.5">I'm VASU</p>
        <p className="text-[#7895B8] text-xs">How can I assist you today?</p>
      </div>

      {/* Main Orb Section */}
      <div className="relative flex items-center justify-center py-4">
        {/* Animated rings */}
        <div className="absolute w-48 h-48 rounded-full border border-[#008CFF]/10 animate-pulse" />
        <div className="absolute w-56 h-56 rounded-full border border-[#008CFF]/5" />
        
        <VasuOrb
          state={assistantState}
          audioLevel={audioLevel}
          activeToolName={activeToolName}
          onClick={onToggleListen}
          size="hero"
        />

        {/* Status Labels */}
        <div className="absolute left-2 top-1/2 -translate-y-1/2 flex flex-col gap-3">
          {['LISTENS', 'UNDERSTANDS', 'ACTS', 'ALWAYS WITH YOU'].map((label, i) => (
            <div key={label} className="text-[8px] text-[#7895B8] font-mono tracking-wider opacity-60">
              {label}
            </div>
          ))}
        </div>
        <div className="absolute right-2 top-1/2 -translate-y-1/2 flex flex-col gap-3 items-end">
          {['Control', 'Create', 'Explore', 'With VASU'].map((label) => (
            <div key={label} className="text-[8px] text-[#7895B8] font-mono tracking-wider opacity-60">
              {label}
            </div>
          ))}
        </div>
      </div>

      {/* Live Transcript / Response */}
      {(liveVoiceTranscript || latestVasuMsg) && (
        <div className="mx-4 mb-3 bg-[#061827] border border-[#008CFF]/20 rounded-2xl p-3 backdrop-blur-sm">
          <div className="flex items-center justify-between text-[10px] font-mono text-[#008CFF] mb-1">
            <span className="flex items-center gap-1">
              <Sparkles className="w-3 h-3" />
              {isListening ? 'Listening...' : 'VASU Response'}
            </span>
            {latestVasuMsg && (
              <button
                onClick={() => onReplayAudio(latestVasuMsg.text)}
                className="text-[#7895B8] hover:text-[#00C8FF] cursor-pointer flex items-center gap-1"
              >
                <Volume2 className="w-3 h-3" /> Replay
              </button>
            )}
          </div>
          <p className="text-xs text-[#F4F8FF] leading-relaxed">
            {liveVoiceTranscript || latestVasuMsg?.text}
          </p>
        </div>
      )}

      {/* Input Bar */}
      <form onSubmit={handleSend} className="mx-4 mb-4 flex items-center gap-2">
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
        <button type="button" className="w-10 h-10 rounded-xl bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer shrink-0">
          <Sparkles className="w-4 h-4" />
        </button>
        <button
          type="submit"
          disabled={!inputText.trim()}
          className="w-10 h-10 bg-[#008CFF] hover:bg-[#00C8FF] disabled:opacity-40 text-[#01060D] rounded-xl cursor-pointer transition-all flex items-center justify-center shrink-0"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>

      {/* Quick Action Grid */}
      <div className="px-4 mb-4">
        <div className="grid grid-cols-4 gap-2">
          {quickActions.map((action) => {
            const colors = colorMap[action.color];
            const isActive = action.isTorch && isTorchActive;
            return (
              <button
                key={action.label}
                type="button"
                onClick={() => {
                  if (action.isSettings) {
                    onSelectTab?.('SETTINGS');
                  } else if (action.isTorch) {
                    onToggleTorch();
                  } else {
                    onSendMessage(action.cmd);
                  }
                }}
                className={`flex flex-col items-center gap-1.5 p-3 rounded-xl bg-[#061827] border ${isActive ? 'border-amber-400/60 bg-amber-500/10' : `border-[#008CFF]/10 ${colors.hover}`} transition-all cursor-pointer group`}
              >
                <div className={`w-9 h-9 rounded-lg ${isActive ? 'bg-amber-500/20 text-amber-300' : `${colors.bg} ${colors.text}`} flex items-center justify-center border ${isActive ? 'border-amber-500/30' : colors.border} group-hover:scale-105 transition-transform`}>
                  <action.icon className="w-4 h-4" />
                </div>
                <span className={`text-[10px] font-medium ${isActive ? 'text-amber-300' : 'text-[#7895B8] group-hover:text-[#F4F8FF]'}`}>
                  {action.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Today's Insight */}
      <div className="mx-4 mb-4 p-4 bg-[#061827] border border-[#008CFF]/10 rounded-2xl">
        <div className="flex items-center gap-1.5 text-[10px] font-mono text-[#008CFF] font-bold uppercase tracking-wider mb-1.5">
          <Lightbulb className="w-3.5 h-3.5 text-amber-400" />
          <span>Today's Insight</span>
        </div>
        <p className="text-xs text-[#7895B8] italic">
          "A smarter tomorrow begins with what you do today."
        </p>
      </div>
    </div>
  );
};
