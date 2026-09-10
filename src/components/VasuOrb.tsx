import React, { useEffect, useRef } from 'react';
import { AssistantState } from '../types';
import { Sparkles, Terminal, WifiOff, AlertTriangle, Mic, Volume2 } from 'lucide-react';

interface VasuOrbProps {
  state: AssistantState;
  audioLevel?: number; // 0.0 to 1.0
  activeToolName?: string;
  onClick?: () => void;
  size?: 'sm' | 'md' | 'lg' | 'hero';
}

export const VasuOrb: React.FC<VasuOrbProps> = ({
  state,
  audioLevel = 0,
  activeToolName,
  onClick,
  size = 'hero',
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const audioLevelRef = useRef<number>(audioLevel);
  audioLevelRef.current = audioLevel;

  const stateRef = useRef<AssistantState>(state);
  stateRef.current = state;

  // Dynamic dimensions based on requested size
  const dimensions = {
    sm: { container: 'w-24 h-24', canvas: 96, core: 'w-16 h-16' },
    md: { container: 'w-44 h-44', canvas: 176, core: 'w-28 h-28' },
    lg: { container: 'w-60 h-60', canvas: 240, core: 'w-40 h-40' },
    hero: { container: 'w-64 h-64 sm:w-72 sm:h-72', canvas: 288, core: 'w-44 h-44 sm:w-52 sm:h-52' },
  }[size];

  // Particle & Soundwave Canvas for ambient quantum aura
  // Smooth 60fps requestAnimationFrame with internal interpolation - NO STUTTER!
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationId: number;
    let angle = 0;
    let smoothedLevel = 0;

    const render = () => {
      const curState = stateRef.current;
      const targetLevel = audioLevelRef.current || 0;
      smoothedLevel += (targetLevel - smoothedLevel) * 0.2;

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;
      const baseRadius = (canvas.width / 2) * 0.72;
      angle += curState === 'THINKING' ? 0.045 : curState === 'LISTENING' ? 0.03 : 0.015;

      // Color scheme based on state
      let colorPrimary = 'rgba(6, 182, 212, '; // Neon Cyan
      let colorSecondary = 'rgba(56, 189, 248, '; // Sky Blue
      let colorGlow = 'rgba(14, 165, 233, '; // Electric Blue

      if (curState === 'OFFLINE') {
        colorPrimary = 'rgba(245, 158, 11, ';
        colorSecondary = 'rgba(251, 191, 36, ';
        colorGlow = 'rgba(217, 119, 6, ';
      } else if (curState === 'ERROR') {
        colorPrimary = 'rgba(239, 68, 68, ';
        colorSecondary = 'rgba(248, 113, 113, ';
        colorGlow = 'rgba(220, 38, 38, ';
      } else if (curState === 'EXECUTING') {
        colorPrimary = 'rgba(16, 185, 129, ';
        colorSecondary = 'rgba(52, 211, 153, ';
        colorGlow = 'rgba(5, 150, 105, ';
      } else if (curState === 'SPEAKING') {
        colorPrimary = 'rgba(56, 189, 248, ';
        colorSecondary = 'rgba(125, 211, 252, ';
        colorGlow = 'rgba(3, 105, 161, ';
      }

      // 1. Audio reactive expanding outer energy rings
      const pulseExpand = smoothedLevel * 30;
      const ringGrad = ctx.createRadialGradient(
        centerX,
        centerY,
        baseRadius * 0.4,
        centerX,
        centerY,
        baseRadius + pulseExpand + 25
      );
      ringGrad.addColorStop(0, colorPrimary + '0.45)');
      ringGrad.addColorStop(0.6, colorSecondary + '0.15)');
      ringGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.fillStyle = ringGrad;
      ctx.beginPath();
      ctx.arc(centerX, centerY, baseRadius + pulseExpand + 20, 0, Math.PI * 2);
      ctx.fill();

      // 2. Multi-layered reactive soundwave rings
      const waveCount = curState === 'LISTENING' || curState === 'SPEAKING' ? 3 : 2;
      for (let w = 0; w < waveCount; w++) {
        const waveRadius = baseRadius * (0.8 + w * 0.12) + pulseExpand * (1 - w * 0.2);
        ctx.save();
        ctx.beginPath();
        ctx.strokeStyle = colorPrimary + (0.35 - w * 0.08) + ')';
        ctx.lineWidth = 1.5;
        ctx.setLineDash([8 + w * 4, 6 + w * 2]);
        ctx.arc(centerX, centerY, Math.max(10, waveRadius), angle * (w % 2 === 0 ? 1 : -1), angle * (w % 2 === 0 ? 1 : -1) + Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }

      // 3. Orbital Particles
      const particleCount = curState === 'THINKING' ? 24 : 16;
      for (let i = 0; i < particleCount; i++) {
        const pAngle = angle + (i * (Math.PI * 2)) / particleCount;
        const distOffset = Math.sin(angle * 2 + i) * 6 + pulseExpand * 0.5;
        const px = centerX + Math.cos(pAngle) * (baseRadius + distOffset);
        const py = centerY + Math.sin(pAngle) * (baseRadius + distOffset);

        ctx.fillStyle = i % 2 === 0 ? colorPrimary + '0.85)' : colorSecondary + '0.85)';
        ctx.beginPath();
        const pRadius = i % 3 === 0 ? 2.5 : 1.5;
        ctx.arc(px, py, pRadius, 0, Math.PI * 2);
        ctx.fill();
      }

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
    };
  }, [size]); // Only recreate when size changes!

  // Central core styling & glow
  const getCoreStyles = () => {
    switch (state) {
      case 'LISTENING':
        return 'from-cyan-400 via-sky-500 to-blue-600 shadow-cyan-400/60 scale-105';
      case 'THINKING':
        return 'from-cyan-300 via-blue-500 to-indigo-700 shadow-blue-500/70 animate-pulse';
      case 'EXECUTING':
        return 'from-emerald-400 via-teal-500 to-cyan-600 shadow-emerald-500/60';
      case 'SPEAKING':
        return 'from-sky-300 via-cyan-400 to-blue-500 shadow-cyan-400/80 scale-105';
      case 'OFFLINE':
        return 'from-amber-400 via-orange-500 to-yellow-600 shadow-amber-500/50';
      case 'ERROR':
        return 'from-rose-500 via-red-600 to-pink-700 shadow-rose-500/60';
      case 'IDLE':
      default:
        return 'from-cyan-500 via-blue-600 to-slate-900 shadow-cyan-500/30';
    }
  };

  return (
    <div
      onClick={onClick}
      className={`relative flex items-center justify-center select-none cursor-pointer group transition-transform duration-300 hover:scale-102 ${dimensions.container}`}
    >
      {/* Dynamic Soundwave & Particle Canvas */}
      <canvas
        ref={canvasRef}
        width={dimensions.canvas}
        height={dimensions.canvas}
        className="absolute inset-0 pointer-events-none z-0"
      />

      {/* Outer Rotating Cybernetic Ring */}
      <div
        className={`absolute inset-2 sm:inset-3 rounded-full border border-cyan-500/30 border-dashed pointer-events-none transition-all duration-700 ${
          state === 'THINKING' ? 'animate-[spin_4s_linear_infinite]' : 'animate-[spin_18s_linear_infinite]'
        }`}
      />

      {/* Middle Glowing Aura Ring */}
      <div
        className={`absolute inset-5 sm:inset-6 rounded-full border border-cyan-400/40 pointer-events-none transition-all duration-500 ${
          state === 'LISTENING' || state === 'SPEAKING' ? 'scale-105 border-cyan-300/60' : ''
        }`}
      />

      {/* Core Glowing Sphere */}
      <div
        className={`relative z-10 rounded-full bg-gradient-to-tr ${getCoreStyles()} shadow-2xl flex flex-col items-center justify-center p-3 transition-all duration-300 overflow-hidden ${dimensions.core}`}
      >
        {/* Core Inner Specular Gloss */}
        <div className="absolute top-1 left-2 w-1/3 h-1/3 rounded-full bg-white/40 blur-[2px] pointer-events-none" />

        {/* Ambient Dark Core Inset */}
        <div className="absolute inset-2 sm:inset-3 rounded-full bg-slate-950/80 backdrop-blur-sm flex flex-col items-center justify-center border border-cyan-500/20">
          {/* Inner Light Orb */}
          <div
            className={`w-6 h-6 sm:w-8 sm:h-8 rounded-full bg-gradient-to-br from-cyan-300 to-blue-500 shadow-lg shadow-cyan-400/50 flex items-center justify-center transition-transform duration-150 ${
              state === 'LISTENING' ? 'scale-125' : state === 'SPEAKING' ? 'scale-115' : ''
            }`}
          >
            <div className="w-2 h-2 sm:w-2.5 sm:h-2.5 rounded-full bg-white shadow-sm shadow-white" />
          </div>

          {/* Assistant State Label for Large Orb */}
          {size === 'hero' && (
            <span className="mt-2 text-[10px] font-mono tracking-widest text-cyan-300 font-bold uppercase transition-all">
              {state}
            </span>
          )}
        </div>
      </div>

      {/* Floating Tool Execution Badge */}
      {activeToolName && (
        <div className="absolute -bottom-3 z-20 px-3 py-1 rounded-full bg-emerald-950/90 border border-emerald-500/50 text-emerald-300 text-[10px] font-mono flex items-center gap-1.5 shadow-lg backdrop-blur-md animate-bounce">
          <Terminal className="w-3 h-3 text-emerald-400" />
          <span>{activeToolName}</span>
        </div>
      )}
    </div>
  );
};
