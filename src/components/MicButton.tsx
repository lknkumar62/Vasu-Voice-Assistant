import React from 'react';
import { Mic, MicOff, Square } from 'lucide-react';
import { AssistantState } from '../types';

interface MicButtonProps {
  state: AssistantState;
  onToggleListen: () => void;
  disabled?: boolean;
}

export const MicButton: React.FC<MicButtonProps> = ({
  state,
  onToggleListen,
  disabled = false,
}) => {
  const isListening = state === 'LISTENING';
  const isBusy = state === 'THINKING' || state === 'EXECUTING';

  return (
    <div className="flex flex-col items-center justify-center my-3">
      <button
        id="vasu-mic-button"
        type="button"
        onClick={onToggleListen}
        disabled={disabled}
        aria-label={isListening ? 'Stop listening' : 'Start voice input'}
        className={`relative group w-20 h-20 rounded-full flex items-center justify-center transition-all duration-300 shadow-xl focus:outline-none focus:ring-4 focus:ring-cyan-500/50 ${
          disabled
            ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
            : isListening
            ? 'bg-gradient-to-tr from-cyan-500 to-cyan-300 text-slate-950 scale-105 shadow-cyan-500/50'
            : isBusy
            ? 'bg-gradient-to-tr from-cyan-900 to-slate-800 text-cyan-300 shadow-cyan-900/30 animate-pulse'
            : 'bg-gradient-to-tr from-cyan-600 via-cyan-500 to-teal-400 text-slate-950 hover:scale-105 active:scale-95 shadow-cyan-500/30'
        }`}
      >
        {/* Ripple rings when listening */}
        {isListening && (
          <>
            <span className="absolute inset-0 rounded-full bg-cyan-400/40 animate-ping pointer-events-none" />
            <span className="absolute -inset-2 rounded-full border-2 border-cyan-300/40 animate-pulse pointer-events-none" />
          </>
        )}

        <div className="relative z-10 flex items-center justify-center">
          {isListening ? (
            <Square className="w-8 h-8 fill-current" />
          ) : isBusy ? (
            <Mic className="w-8 h-8 animate-pulse text-cyan-300" />
          ) : (
            <Mic className="w-8 h-8" />
          )}
        </div>
      </button>

      <span className="mt-2 text-xs font-medium text-slate-400 tracking-wide font-mono">
        {isListening ? 'Tap to Stop' : isBusy ? 'Processing...' : 'Tap to Speak'}
      </span>
    </div>
  );
};
