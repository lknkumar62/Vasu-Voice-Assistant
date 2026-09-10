import React, { useState } from 'react';
import { REGISTERED_TOOLS, ToolExecutor, ToolExecutionResult } from '../services/toolRegistry';
import { ToolItem, ToolRisk } from '../types';
import {
  Play,
  CheckCircle2,
  AlertTriangle,
  Search,
  Zap,
  Sliders,
  Terminal,
  RefreshCw,
} from 'lucide-react';

interface ToolsViewProps {
  onExecuteTool: (toolId: string) => Promise<ToolExecutionResult>;
}

export const ToolsView: React.FC<ToolsViewProps> = ({ onExecuteTool }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [executingToolId, setExecutingToolId] = useState<string | null>(null);
  const [lastResults, setLastResults] = useState<Record<string, ToolExecutionResult>>({});

  const categories = ['ALL', 'SYSTEM', 'DEVICE', 'SCREEN', 'FILES', 'COMMUNICATION', 'MEDIA', 'ALARMS'];

  const filteredTools = REGISTERED_TOOLS.filter((tool) => {
    const matchesCategory = selectedCategory === 'ALL' || tool.category === selectedCategory;
    const matchesSearch =
      tool.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tool.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tool.title.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const handleTestTool = async (tool: ToolItem) => {
    setExecutingToolId(tool.id);
    try {
      const res = await onExecuteTool(tool.id);
      setLastResults((prev) => ({ ...prev, [tool.id]: res }));
    } finally {
      setExecutingToolId(null);
    }
  };

  const getRiskBadge = (risk: ToolRisk) => {
    switch (risk) {
      case 'CRITICAL':
        return 'bg-rose-950/80 text-rose-300 border-rose-800';
      case 'HIGH':
        return 'bg-amber-950/80 text-amber-300 border-amber-800';
      case 'MEDIUM':
        return 'bg-yellow-950/80 text-yellow-300 border-yellow-800';
      case 'LOW':
      default:
        return 'bg-emerald-950/80 text-emerald-300 border-emerald-800';
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3">
      {/* Header & Controls */}
      <div className="py-2 border-b border-slate-800 space-y-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Terminal className="w-5 h-5 text-cyan-400" />
            <h2 className="font-mono text-sm font-semibold text-slate-100">
              Tool Registry ({REGISTERED_TOOLS.length} Tools)
            </h2>
          </div>
          <span className="text-xs text-slate-400 font-mono">Centralized Hub</span>
        </div>

        {/* Search */}
        <div className="relative">
          <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search tools by name, description, risk..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          />
        </div>

        {/* Category Pills */}
        <div className="flex gap-1.5 overflow-x-auto no-scrollbar pb-1">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`text-[11px] font-mono px-2.5 py-1 rounded-lg transition-colors whitespace-nowrap cursor-pointer ${
                selectedCategory === cat
                  ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 font-semibold'
                  : 'bg-slate-900 text-slate-400 border border-slate-800 hover:text-slate-200'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Tool List */}
      <div className="flex-1 overflow-y-auto space-y-2.5 pt-3 pr-1">
        {filteredTools.map((tool) => {
          const isExecuting = executingToolId === tool.id;
          const result = lastResults[tool.id];

          return (
            <div
              key={tool.id}
              className="bg-slate-900/80 border border-slate-800/90 rounded-xl p-3 hover:border-slate-700 transition-all"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <span className="font-mono text-xs font-bold text-cyan-300">
                      {tool.name}
                    </span>
                    <span
                      className={`text-[10px] font-mono px-1.5 py-0.5 rounded border uppercase font-semibold ${getRiskBadge(
                        tool.risk
                      )}`}
                    >
                      {tool.risk} Risk
                    </span>
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                      {tool.category}
                    </span>
                  </div>

                  <p className="text-xs text-slate-300 leading-snug">{tool.description}</p>

                  <div className="text-[11px] text-slate-500 mt-1 flex items-center gap-1">
                    <span>Perm:</span>
                    <span className="text-slate-400 font-mono">{tool.requiredPermission}</span>
                  </div>
                </div>

                {/* TEST Button */}
                <button
                  id={`btn-test-tool-${tool.id}`}
                  disabled={isExecuting}
                  onClick={() => handleTestTool(tool)}
                  className="bg-cyan-500/10 hover:bg-cyan-500/25 border border-cyan-500/30 text-cyan-300 px-3 py-1.5 rounded-lg text-xs font-mono font-medium flex items-center gap-1.5 transition-colors cursor-pointer shrink-0 disabled:opacity-50"
                >
                  {isExecuting ? (
                    <>
                      <RefreshCw className="w-3 h-3 animate-spin text-cyan-400" />
                      <span>Testing...</span>
                    </>
                  ) : (
                    <>
                      <Play className="w-3 h-3 fill-current" />
                      <span>TEST</span>
                    </>
                  )}
                </button>
              </div>

              {/* Execution Result preview */}
              {result && (
                <div
                  className={`mt-2.5 p-2 rounded-lg text-xs font-mono border ${
                    result.success
                      ? 'bg-emerald-950/40 border-emerald-900 text-emerald-300'
                      : 'bg-rose-950/40 border-rose-900 text-rose-300'
                  }`}
                >
                  <div className="flex items-center gap-1.5 font-semibold mb-0.5">
                    {result.success ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                    ) : (
                      <AlertTriangle className="w-3.5 h-3.5 text-rose-400" />
                    )}
                    <span>Result:</span>
                  </div>
                  <p className="text-[11px] text-slate-300 whitespace-pre-wrap">{result.result}</p>
                  {result.displayData && (
                    <pre className="mt-1 text-[10px] text-slate-400 bg-slate-950 p-1.5 rounded overflow-x-auto">
                      {JSON.stringify(result.displayData, null, 2)}
                    </pre>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
