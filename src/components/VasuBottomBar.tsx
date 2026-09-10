import React from 'react';
import { Home, MessageSquare, Wrench, Settings } from 'lucide-react';
import { ScreenTab } from './QuickActions';
import { AssistantState } from '../types';

interface VasuBottomBarProps {
  activeTab: ScreenTab;
  onSelectTab: (tab: ScreenTab) => void;
  assistantState: AssistantState;
  audioLevel?: number;
  onToggleListen: () => void;
}

export const VasuBottomBar: React.FC<VasuBottomBarProps> = ({
  activeTab,
  onSelectTab,
  assistantState,
  audioLevel = 0,
  onToggleListen,
}) => {
  const isListening = assistantState === 'LISTENING';
  const isThinking = assistantState === 'THINKING' || assistantState === 'EXECUTING';

  return (
    <nav
      id="vasu-bottom-bar"
      aria-label="VASU Navigation"
      className="fixed bottom-0 left-0 right-0 z-50 w-full max-w-lg mx-auto px-4 pb-3 pt-1 select-none pointer-events-auto"
    >
      <div className="relative flex items-center justify-between px-3 py-1.5 rounded-3xl bg-slate-950/95 backdrop-blur-xl border border-slate-800 shadow-2xl shadow-black/90">
        {/* 1. Home Tab */}
        <button
          id="btn-nav-home"
          type="button"
          onClick={() => onSelectTab('HOME')}
          className={`flex-1 flex flex-col items-center justify-center py-1.5 px-2 rounded-2xl transition-all duration-200 cursor-pointer ${
            activeTab === 'HOME'
              ? 'text-cyan-400 font-semibold bg-cyan-950/40'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
          }`}
        >
          <Home className={`w-5 h-5 mb-0.5 transition-transform ${activeTab === 'HOME' ? 'scale-110' : ''}`} />
          <span className="text-[11px] tracking-tight">Home</span>
        </button>

        {/* 2. Chat Tab */}
        <button
          id="btn-nav-chat"
          type="button"
          onClick={() => onSelectTab('CHAT')}
          className={`flex-1 flex flex-col items-center justify-center py-1.5 px-2 rounded-2xl transition-all duration-200 cursor-pointer ${
            activeTab === 'CHAT'
              ? 'text-cyan-400 font-semibold bg-cyan-950/40'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
          }`}
        >
          <MessageSquare className={`w-5 h-5 mb-0.5 transition-transform ${activeTab === 'CHAT' ? 'scale-110' : ''}`} />
          <span className="text-[11px] tracking-tight">Chat</span>
        </button>

        {/* 3. Middle: Floating Glowing VASU Orb */}
        <div className="relative -top-4 px-2 flex flex-col items-center shrink-0">
          <button
            id="btn-nav-small-orb"
            type="button"
            onClick={onToggleListen}
            className="group relative cursor-pointer focus:outline-none"
            title="Talk to VASU"
          >
            {/* Outer pulsating aura */}
            <div
              className={`absolute -inset-2.5 rounded-full bg-gradient-to-r from-cyan-500 via-sky-400 to-blue-600 blur-md transition-all duration-300 ${
                isListening
                  ? 'opacity-100 animate-ping'
                  : 'opacity-60 group-hover:opacity-100'
              }`}
            />

            {/* Glowing Orb container */}
            <div
              className={`relative w-13 h-13 rounded-full p-[2px] bg-gradient-to-tr from-cyan-400 via-sky-300 to-blue-500 shadow-lg shadow-cyan-500/60 flex items-center justify-center transition-transform duration-200 group-hover:scale-105 group-active:scale-95 ${
                isListening ? 'scale-110' : ''
              }`}
            >
              <div className="w-full h-full rounded-full bg-slate-950 flex items-center justify-center overflow-hidden relative">
                {/* Internal core animation */}
                <div
                  className={`w-7 h-7 rounded-full bg-gradient-to-br from-cyan-400 via-blue-500 to-indigo-600 blur-[2px] transition-all duration-150 ${
                    isListening
                      ? 'scale-125 opacity-100 animate-pulse'
                      : 'opacity-80 group-hover:opacity-100'
                  }`}
                  style={{
                    transform: isListening ? `scale(${1 + Math.min(0.5, (audioLevel || 0.2) * 1.5)})` : undefined,
                  }}
                />
                <div className="absolute w-3 h-3 rounded-full bg-white shadow-sm shadow-cyan-200" />
                {isThinking && (
                  <div className="absolute inset-0 border-2 border-dashed border-cyan-300 rounded-full animate-spin" />
                )}
              </div>
            </div>
          </button>
          <span className="text-[9px] font-mono tracking-widest text-cyan-400 font-bold uppercase mt-0.5">
            VASU
          </span>
        </div>

        {/* 4. Tools Tab */}
        <button
          id="btn-nav-tools"
          type="button"
          onClick={() => onSelectTab('TOOLS')}
          className={`flex-1 flex flex-col items-center justify-center py-1.5 px-2 rounded-2xl transition-all duration-200 cursor-pointer ${
            activeTab === 'TOOLS'
              ? 'text-cyan-400 font-semibold bg-cyan-950/40'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
          }`}
        >
          <Wrench className={`w-5 h-5 mb-0.5 transition-transform ${activeTab === 'TOOLS' ? 'scale-110' : ''}`} />
          <span className="text-[11px] tracking-tight">Tools</span>
        </button>

        {/* 5. Setting Tab */}
        <button
          id="btn-nav-setting"
          type="button"
          onClick={() => onSelectTab('SETTINGS')}
          className={`flex-1 flex flex-col items-center justify-center py-1.5 px-2 rounded-2xl transition-all duration-200 cursor-pointer ${
            activeTab === 'SETTINGS'
              ? 'text-cyan-400 font-semibold bg-cyan-950/40'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
          }`}
        >
          <Settings className={`w-5 h-5 mb-0.5 transition-transform ${activeTab === 'SETTINGS' ? 'scale-110' : ''}`} />
          <span className="text-[11px] tracking-tight">Setting</span>
        </button>
      </div>
    </nav>
  );
};
