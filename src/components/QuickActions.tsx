import React from 'react';
import {
  MessageSquare,
  Mic,
  ShieldAlert,
  Settings,
  Target,
  Bot,
  Brain,
  Wrench,
  Smartphone,
} from 'lucide-react';

export type ScreenTab =
  | 'HOME'
  | 'CHAT'
  | 'VOICE'
  | 'GUARDIAN'
  | 'SETTINGS'
  | 'MISSIONS'
  | 'AUTO'
  | 'MEMORY'
  | 'TOOLS'
  | 'PERMISSIONS'
  | 'ANDROID_PROJECT';

interface QuickActionsProps {
  activeTab: ScreenTab;
  onSelectTab: (tab: ScreenTab) => void;
}

export const QuickActions: React.FC<QuickActionsProps> = ({ activeTab, onSelectTab }) => {
  const actions: { tab: ScreenTab; label: string; icon: React.ReactNode; badge?: string }[] = [
    { tab: 'VOICE', label: 'Voice Mode', icon: <Mic className="w-5 h-5 text-cyan-400" />, badge: 'Kore' },
    { tab: 'CHAT', label: 'Chat', icon: <MessageSquare className="w-5 h-5" /> },
    { tab: 'TOOLS', label: 'Tools', icon: <Wrench className="w-5 h-5" /> },
    { tab: 'MEMORY', label: 'Memory', icon: <Brain className="w-5 h-5" /> },
    { tab: 'MISSIONS', label: 'Missions', icon: <Target className="w-5 h-5" /> },
    { tab: 'AUTO', label: 'Screen Auto', icon: <Bot className="w-5 h-5" /> },
    { tab: 'GUARDIAN', label: 'Guardian', icon: <ShieldAlert className="w-5 h-5" /> },
    { tab: 'SETTINGS', label: 'Settings', icon: <Settings className="w-5 h-5" /> },
  ];

  return (
    <div className="w-full max-w-xl mx-auto px-4 mt-2">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-semibold tracking-wider uppercase text-slate-400 font-mono">
          Assistant Modules
        </span>
        <button
          id="btn-nav-android-repo"
          onClick={() => onSelectTab('ANDROID_PROJECT')}
          className="text-xs text-cyan-400 hover:text-cyan-300 font-mono flex items-center gap-1 hover:underline cursor-pointer"
        >
          <span>Android Source (APK CI) &rarr;</span>
        </button>
      </div>

      <div className="grid grid-cols-4 gap-2.5">
        {actions.map((act) => {
          const isSelected = activeTab === act.tab;
          return (
            <button
              id={`quick-nav-${act.tab.toLowerCase()}`}
              key={act.tab}
              type="button"
              onClick={() => onSelectTab(act.tab)}
              className={`flex flex-col items-center justify-center p-2.5 rounded-xl border transition-all duration-200 cursor-pointer ${
                isSelected
                  ? 'bg-cyan-500/20 border-cyan-500/50 text-cyan-300 shadow-lg shadow-cyan-950/50'
                  : 'bg-slate-900/60 border-slate-800/80 text-slate-400 hover:bg-slate-850 hover:text-slate-200 hover:border-slate-700'
              }`}
            >
              <div className={`mb-1 transition-transform ${isSelected ? 'scale-110 text-cyan-400' : ''}`}>
                {act.icon}
              </div>
              <span className="text-[11px] font-medium tracking-tight whitespace-nowrap">
                {act.label}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
};
