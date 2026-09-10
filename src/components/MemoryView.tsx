import React, { useState } from 'react';
import { MemoryItem } from '../types';
import { Brain, Plus, Trash2, Search, Tag, Check, Edit2 } from 'lucide-react';

interface MemoryViewProps {
  memories: MemoryItem[];
  onAddMemory: (category: MemoryItem['category'], fact: string) => void;
  onDeleteMemory: (id: string) => void;
  onClearMemory: () => void;
}

export const MemoryView: React.FC<MemoryViewProps> = ({
  memories,
  onAddMemory,
  onDeleteMemory,
  onClearMemory,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [newFact, setNewFact] = useState('');
  const [newCategory, setNewCategory] = useState<MemoryItem['category']>('USER_PREFERENCE');
  const [isAdding, setIsAdding] = useState(false);

  const categories: MemoryItem['category'][] = [
    'USER_PREFERENCE',
    'IMPORTANT_FACT',
    'TASK_CONTEXT',
    'DEVICE_PREFERENCE',
    'CONVERSATION_CONTEXT',
  ];

  const filteredMemories = memories.filter((m) =>
    m.fact.toLowerCase().includes(searchQuery.toLowerCase()) ||
    m.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleAddSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFact.trim()) return;
    onAddMemory(newCategory, newFact.trim());
    setNewFact('');
    setIsAdding(false);
  };

  const getCategoryColor = (cat: MemoryItem['category']) => {
    switch (cat) {
      case 'USER_PREFERENCE':
        return 'bg-cyan-950/80 text-cyan-300 border-cyan-800';
      case 'IMPORTANT_FACT':
        return 'bg-purple-950/80 text-purple-300 border-purple-800';
      case 'DEVICE_PREFERENCE':
        return 'bg-emerald-950/80 text-emerald-300 border-emerald-800';
      default:
        return 'bg-slate-800 text-slate-300 border-slate-700';
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3">
      {/* Header */}
      <div className="py-2 border-b border-slate-800 space-y-2">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Brain className="w-5 h-5 text-cyan-400" />
            <h2 className="font-mono text-sm font-semibold text-slate-100">
              Persistent Memory ({memories.length})
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsAdding(!isAdding)}
              className="bg-cyan-500/15 hover:bg-cyan-500/25 border border-cyan-500/30 text-cyan-300 text-xs px-2.5 py-1 rounded-lg flex items-center gap-1 font-mono transition-colors cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Memory</span>
            </button>
            {memories.length > 0 && (
              <button
                onClick={onClearMemory}
                className="text-xs text-rose-400 hover:text-rose-300 flex items-center gap-1 p-1"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>

        {/* Search */}
        <div className="relative">
          <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search memories (e.g. coffee, Hindi, favourite)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          />
        </div>
      </div>

      {/* Add New Memory Form */}
      {isAdding && (
        <form onSubmit={handleAddSubmit} className="my-2 p-3 bg-slate-900 border border-cyan-500/30 rounded-xl space-y-2">
          <span className="text-xs font-mono font-semibold text-cyan-300">Add New Fact for VASU to Remember</span>
          <textarea
            rows={2}
            value={newFact}
            onChange={(e) => setNewFact(e.target.value)}
            placeholder="e.g., Mera favourite color blue hai, ya mujhe subah 7 baje coffee peena pasand hai"
            className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
          />
          <div className="flex items-center justify-between">
            <select
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value as any)}
              className="bg-slate-950 border border-slate-800 rounded px-2 py-1 text-xs text-slate-300 font-mono focus:outline-none"
            >
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setIsAdding(false)}
                className="text-xs text-slate-400 hover:text-slate-200 px-2 py-1"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="bg-cyan-500 text-slate-950 px-3 py-1 rounded text-xs font-semibold hover:bg-cyan-400 cursor-pointer"
              >
                Save
              </button>
            </div>
          </div>
        </form>
      )}

      {/* Memories List */}
      <div className="flex-1 overflow-y-auto space-y-2 pt-2 pr-1">
        {filteredMemories.length === 0 ? (
          <div className="text-center py-12 text-slate-500 text-xs">
            <Brain className="w-10 h-10 mx-auto mb-2 opacity-30" />
            <p>No memories saved yet.</p>
            <p className="mt-1">Say: <span className="text-cyan-400 font-mono">"VASU yaad rakhna mera naam Rahul hai"</span></p>
          </div>
        ) : (
          filteredMemories.map((mem) => (
            <div
              key={mem.id}
              className="bg-slate-900/80 border border-slate-800/80 rounded-xl p-3 flex items-start justify-between gap-3 hover:border-slate-700 transition-colors"
            >
              <div className="space-y-1">
                <span
                  className={`text-[10px] font-mono px-2 py-0.5 rounded border uppercase font-medium ${getCategoryColor(
                    mem.category
                  )}`}
                >
                  {mem.category.replace('_', ' ')}
                </span>
                <p className="text-xs text-slate-200 leading-relaxed font-sans">{mem.fact}</p>
                <span className="text-[10px] text-slate-500 font-mono block">
                  Saved: {new Date(mem.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
              <button
                onClick={() => onDeleteMemory(mem.id)}
                className="text-slate-500 hover:text-rose-400 p-1 rounded transition-colors shrink-0"
                title="Delete memory"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
