import React from 'react';
import { ShieldAlert, ShieldCheck, Octagon, Lock, Eye, AlertOctagon, CheckCircle2 } from 'lucide-react';
import { ToolRisk } from '../types';

interface GuardianViewProps {
  isGuardianActive: boolean;
  onToggleGuardian: () => void;
  requireConfirmationForHighRisk: boolean;
  onToggleConfirmation: () => void;
  onEmergencyStop: () => void;
  executionLogs: { tool: string; risk: ToolRisk; timestamp: number; success: boolean }[];
}

export const GuardianView: React.FC<GuardianViewProps> = ({
  isGuardianActive,
  onToggleGuardian,
  requireConfirmationForHighRisk,
  onToggleConfirmation,
  onEmergencyStop,
  executionLogs,
}) => {
  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3">
      {/* Header */}
      <div className="py-2 border-b border-slate-800 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldAlert className="w-5 h-5 text-cyan-400" />
          <div>
            <h2 className="font-mono text-sm font-semibold text-slate-100">
              Guardian Security & Safety Engine
            </h2>
            <p className="text-[11px] text-slate-400">Tool validation, risk assessment & kill switches</p>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto space-y-3 pt-3 pr-1">
        {/* Emergency Stop Panel */}
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-900/80 flex items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-1.5 text-rose-400 font-bold text-sm">
              <Octagon className="w-5 h-5" />
              <span>EMERGENCY STOP (KILL SWITCH)</span>
            </div>
            <p className="text-xs text-slate-300 mt-1">
              Instantly terminates all active speech, TTS, screen automation, background missions, and tool execution.
            </p>
          </div>
          <button
            id="btn-emergency-stop"
            onClick={onEmergencyStop}
            className="bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs px-4 py-3 rounded-xl shadow-lg shadow-rose-900/50 flex items-center gap-1.5 shrink-0 transition-transform active:scale-95 cursor-pointer uppercase font-mono"
          >
            <AlertOctagon className="w-4 h-4" />
            <span>HALT ALL</span>
          </button>
        </div>

        {/* Security Controls */}
        <div className="p-3 bg-slate-900/80 border border-slate-800 rounded-xl space-y-3">
          <span className="text-xs font-mono font-semibold text-cyan-300 uppercase tracking-wider block">
            Guardian Protection Policies
          </span>

          <div className="flex items-center justify-between py-1 border-b border-slate-800/60">
            <div>
              <div className="text-xs font-semibold text-slate-200">Active Risk Validation</div>
              <div className="text-[11px] text-slate-400">Verifies tool parameters and permissions before execution</div>
            </div>
            <input
              type="checkbox"
              checked={isGuardianActive}
              onChange={onToggleGuardian}
              className="w-4 h-4 accent-cyan-500 cursor-pointer"
            />
          </div>

          <div className="flex items-center justify-between py-1">
            <div>
              <div className="text-xs font-semibold text-slate-200">User Confirmation for HIGH / CRITICAL Risk</div>
              <div className="text-[11px] text-slate-400">Requires explicit spoken or tactile consent before destructive actions (e.g. Delete File, Phone Call)</div>
            </div>
            <input
              type="checkbox"
              checked={requireConfirmationForHighRisk}
              onChange={onToggleConfirmation}
              className="w-4 h-4 accent-cyan-500 cursor-pointer"
            />
          </div>
        </div>

        {/* Risk Tiers Reference */}
        <div className="p-3 bg-slate-900/80 border border-slate-800 rounded-xl space-y-2 text-xs">
          <span className="text-xs font-mono font-semibold text-slate-300 block">Risk Tiers Classification</span>
          <div className="grid grid-cols-2 gap-2 text-[11px] font-mono">
            <div className="p-2 rounded bg-emerald-950/40 border border-emerald-900/60 text-emerald-300">
              <span className="font-bold">LOW:</span> Battery, Torch, Volume, Storage, Screen Read
            </div>
            <div className="p-2 rounded bg-yellow-950/40 border border-yellow-900/60 text-yellow-300">
              <span className="font-bold">MEDIUM:</span> Phone Call, SMS, WhatsApp, Open Apps, Type Text
            </div>
            <div className="p-2 rounded bg-amber-950/40 border border-amber-900/60 text-amber-300">
              <span className="font-bold">HIGH:</span> Delete File, Screen Click, Run Mission
            </div>
            <div className="p-2 rounded bg-rose-950/40 border border-rose-900/60 text-rose-300">
              <span className="font-bold">CRITICAL:</span> Security, Account Changes, System Settings
            </div>
          </div>
        </div>

        {/* Recent Audit Logs */}
        <div className="p-3 bg-slate-900/80 border border-slate-800 rounded-xl space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono font-semibold text-slate-300">Tool Execution Audit Log</span>
            <span className="text-[10px] text-slate-500 font-mono">Real-time</span>
          </div>

          {executionLogs.length === 0 ? (
            <p className="text-xs text-slate-500 italic py-2">No tools executed in current session yet.</p>
          ) : (
            <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1 text-[11px] font-mono">
              {executionLogs.slice(-6).reverse().map((log, i) => (
                <div key={i} className="flex items-center justify-between p-1.5 bg-slate-950 rounded border border-slate-850">
                  <span className="text-cyan-300">{log.tool}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-slate-400">{log.risk}</span>
                    <span className={log.success ? 'text-emerald-400' : 'text-rose-400'}>
                      {log.success ? 'OK' : 'FAILED'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
