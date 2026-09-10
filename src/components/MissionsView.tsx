import React, { useState } from 'react';
import { MissionItem } from '../types';
import { Target, Clock, Battery, Moon, Plus, Play, CheckCircle2, ToggleLeft, ToggleRight } from 'lucide-react';

interface MissionsViewProps {
  missions: MissionItem[];
  onToggleMission: (id: string) => void;
  onRunMission: (id: string) => void;
  onCreateMission: (mission: Omit<MissionItem, 'id'>) => void;
}

export const MissionsView: React.FC<MissionsViewProps> = ({
  missions,
  onToggleMission,
  onRunMission,
  onCreateMission,
}) => {
  const [isCreating, setIsCreating] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newTriggerType, setNewTriggerType] = useState<MissionItem['triggerType']>('TIME');
  const [newTriggerValue, setNewTriggerValue] = useState('07:00 AM');
  const [newAction, setNewAction] = useState('Good morning briefing aur calendar check');

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    onCreateMission({
      title: newTitle.trim(),
      triggerType: newTriggerType,
      triggerValue: newTriggerValue,
      action: newAction,
      isEnabled: true,
    });
    setNewTitle('');
    setIsCreating(false);
  };

  const getTriggerIcon = (type: MissionItem['triggerType']) => {
    switch (type) {
      case 'TIME':
      case 'RECURRING':
        return <Clock className="w-4 h-4 text-cyan-400" />;
      case 'BATTERY':
        return <Battery className="w-4 h-4 text-amber-400" />;
      case 'EVENT':
      default:
        return <Moon className="w-4 h-4 text-purple-400" />;
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3">
      {/* Header */}
      <div className="py-2 border-b border-slate-800 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Target className="w-5 h-5 text-cyan-400" />
          <div>
            <h2 className="font-mono text-sm font-semibold text-slate-100">
              Automated Missions ({missions.length})
            </h2>
            <p className="text-[11px] text-slate-400">Scheduled routines & background triggers</p>
          </div>
        </div>
        <button
          onClick={() => setIsCreating(!isCreating)}
          className="bg-cyan-500/15 hover:bg-cyan-500/25 border border-cyan-500/30 text-cyan-300 text-xs px-2.5 py-1.5 rounded-lg flex items-center gap-1 font-mono transition-colors cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>New Mission</span>
        </button>
      </div>

      {/* Creation form */}
      {isCreating && (
        <form onSubmit={handleCreate} className="my-2 p-3 bg-slate-900 border border-cyan-500/30 rounded-xl space-y-2">
          <span className="text-xs font-mono font-semibold text-cyan-300">Create Android Mission</span>
          <input
            type="text"
            placeholder="Mission Title e.g., Morning Briefing"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded p-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
          />
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-0.5">Trigger Type</label>
              <select
                value={newTriggerType}
                onChange={(e) => setNewTriggerType(e.target.value as any)}
                className="w-full bg-slate-950 border border-slate-800 rounded p-1.5 text-xs text-slate-300 font-mono"
              >
                <option value="TIME">Specific Time (Daily)</option>
                <option value="BATTERY">Battery Level</option>
                <option value="EVENT">System Event / Sleep</option>
              </select>
            </div>
            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-0.5">Trigger Condition</label>
              <input
                type="text"
                value={newTriggerValue}
                onChange={(e) => setNewTriggerValue(e.target.value)}
                placeholder="07:00 AM or <20%"
                className="w-full bg-slate-950 border border-slate-800 rounded p-1.5 text-xs text-slate-200 font-mono"
              >
              </input>
            </div>
          </div>
          <div>
            <label className="text-[10px] font-mono text-slate-400 block mb-0.5">Action to Execute</label>
            <input
              type="text"
              value={newAction}
              onChange={(e) => setNewAction(e.target.value)}
              placeholder="e.g. Good morning bolna aur weather update dena"
              className="w-full bg-slate-950 border border-slate-800 rounded p-1.5 text-xs text-slate-200"
            />
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={() => setIsCreating(false)}
              className="text-xs text-slate-400 hover:text-slate-200 px-2 py-1"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="bg-cyan-500 text-slate-950 px-3 py-1 rounded text-xs font-semibold hover:bg-cyan-400 cursor-pointer"
            >
              Save Mission
            </button>
          </div>
        </form>
      )}

      {/* Missions List */}
      <div className="flex-1 overflow-y-auto space-y-2.5 pt-3 pr-1">
        {missions.map((mission) => (
          <div
            key={mission.id}
            className={`p-3 rounded-xl border transition-all ${
              mission.isEnabled
                ? 'bg-slate-900/90 border-slate-800'
                : 'bg-slate-950/60 border-slate-900 opacity-60'
            }`}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-start gap-2.5">
                <div className="p-2 rounded-lg bg-slate-950 border border-slate-800">
                  {getTriggerIcon(mission.triggerType)}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-sm text-slate-200 font-sans">
                      {mission.title}
                    </span>
                    <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-cyan-950/80 text-cyan-300 border border-cyan-800">
                      {mission.triggerValue}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5 leading-snug">{mission.action}</p>
                </div>
              </div>

              {/* Controls */}
              <div className="flex items-center gap-2 shrink-0">
                <button
                  title="Run now"
                  onClick={() => onRunMission(mission.id)}
                  className="p-1.5 rounded-lg bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-300 text-xs flex items-center gap-1 font-mono transition-colors"
                >
                  <Play className="w-3 h-3 fill-current" />
                  <span className="hidden sm:inline">Run</span>
                </button>
                <button
                  onClick={() => onToggleMission(mission.id)}
                  className="text-slate-400 hover:text-cyan-300 transition-colors"
                >
                  {mission.isEnabled ? (
                    <ToggleRight className="w-6 h-6 text-cyan-400" />
                  ) : (
                    <ToggleLeft className="w-6 h-6 text-slate-600" />
                  )}
                </button>
              </div>
            </div>

            {mission.lastRun && (
              <div className="mt-2 pt-2 border-t border-slate-800/60 flex items-center gap-1.5 text-[10px] text-slate-500 font-mono">
                <CheckCircle2 className="w-3 h-3 text-emerald-400" />
                <span>Last executed: {new Date(mission.lastRun).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
