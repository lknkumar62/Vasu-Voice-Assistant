import React, { useState } from 'react';
import { REGISTERED_TOOLS, ToolExecutionResult } from '../services/toolRegistry';
import { ToolItem, ToolRisk } from '../types';
import {
  Play,
  CheckCircle2,
  AlertTriangle,
  Search,
  Zap,
  Terminal,
  RefreshCw,
  Phone,
  MessageCircle,
  Camera,
  Flashlight,
  Music,
  Video,
  FileText,
  Wifi,
  Bluetooth,
  Map,
  QrCode,
  FolderOpen,
  Cloud,
  Eye,
  Brain,
  Languages,
  Mic,
  Menu,
  ChevronRight,
  Briefcase,
  Gauge,
  Settings,
  Shield,
  Bot,
} from 'lucide-react';

interface ToolsViewProps {
  onExecuteTool: (toolId: string) => Promise<ToolExecutionResult>;
}

const Calendar = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" x2="16" y1="2" y2="6"/><line x1="8" x2="8" y1="2" y2="6"/><line x1="3" x2="21" y1="10" y2="10"/>
  </svg>
);

const Calculator = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect width="16" height="20" x="4" y="2" rx="2"/><line x1="8" x2="16" y1="6" y2="6"/><line x1="16" x2="16" y1="14" y2="18"/><line x1="8" x2="8.01" y1="10" y2="10"/><line x1="12" x2="12.01" y1="10" y2="10"/><line x1="16" x2="16.01" y1="10" y2="10"/><line x1="8" x2="8.01" y1="14" y2="14"/><line x1="12" x2="12.01" y1="14" y2="14"/><line x1="8" x2="8.01" y1="18" y2="18"/><line x1="12" x2="12.01" y1="18" y2="18"/>
  </svg>
);

const User = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
  </svg>
);

const TOOL_SECTIONS = [
  {
    title: 'PRODUCTIVITY',
    tools: [
      { icon: FileText, label: 'Notes', desc: 'Quick notes & lists', toolId: 'browse_files' },
      { icon: Mic, label: 'Reminders', desc: 'Set reminders', toolId: 'create_alarm' },
      { icon: Calendar, label: 'Calendar', desc: 'View schedule', toolId: 'create_alarm' },
      { icon: Calculator, label: 'Calculator', desc: 'Quick calculations', toolId: 'read_screen' },
    ],
  },
  {
    title: 'MEDIA',
    tools: [
      { icon: Camera, label: 'Camera', desc: 'Capture photos', toolId: 'take_photo' },
      { icon: Eye, label: 'Gallery', desc: 'View photos', toolId: 'browse_files' },
      { icon: Music, label: 'Music', desc: 'Play music', toolId: 'media_play_pause' },
      { icon: Video, label: 'Video Player', desc: 'Play videos', toolId: 'media_play_pause' },
    ],
  },
  {
    title: 'COMMUNICATION',
    tools: [
      { icon: Phone, label: 'Call', desc: 'Make phone calls', toolId: 'make_call' },
      { icon: MessageCircle, label: 'WhatsApp', desc: 'Send messages', toolId: 'open_whatsapp' },
      { icon: MessageCircle, label: 'Messages', desc: 'Send SMS', toolId: 'send_message' },
      { icon: User, label: 'Contacts', desc: 'Find contacts', toolId: 'lookup_contact' },
    ],
  },
  {
    title: 'SYSTEM',
    tools: [
      { icon: Flashlight, label: 'Torch', desc: 'Flashlight control', toolId: 'turn_on_torch' },
      { icon: Bluetooth, label: 'Bluetooth', desc: 'Device connections', toolId: 'device_info' },
      { icon: Wifi, label: 'Wi-Fi', desc: 'Network settings', toolId: 'device_info' },
      { icon: Gauge, label: 'Device Control', desc: 'System controls', toolId: 'device_info' },
    ],
  },
  {
    title: 'AI TOOLS',
    tools: [
      { icon: Search, label: 'AI Search', desc: 'Web intelligence', toolId: 'search_web' },
      { icon: Eye, label: 'AI Vision', desc: 'Image analysis', toolId: 'ocr_extract' },
      { icon: FileText, label: 'Document AI', desc: 'Document analysis', toolId: 'read_screen' },
      { icon: Languages, label: 'Translate', desc: 'Language translation', toolId: 'read_screen' },
    ],
  },
  {
    title: 'UTILITIES',
    tools: [
      { icon: Map, label: 'Maps', desc: 'Navigation & location', toolId: 'search_web' },
      { icon: QrCode, label: 'QR Scanner', desc: 'Scan QR codes', toolId: 'ocr_extract' },
      { icon: FolderOpen, label: 'File Manager', desc: 'Browse files', toolId: 'browse_files' },
      { icon: Cloud, label: 'Cloud Sync', desc: 'Sync data', toolId: 'storage_info' },
    ],
  },
];

export const ToolsView: React.FC<ToolsViewProps> = ({ onExecuteTool }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [executingToolId, setExecutingToolId] = useState<string | null>(null);
  const [lastResults, setLastResults] = useState<Record<string, ToolExecutionResult>>({});

  const categories = ['ALL', 'PRODUCTIVITY', 'MEDIA', 'COMMUNICATION', 'SYSTEM', 'AI TOOLS', 'UTILITIES'];

  const filteredTools = REGISTERED_TOOLS.filter((tool) => {
    const matchesSearch =
      tool.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tool.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      tool.title.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesSearch;
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
      case 'CRITICAL': return 'bg-rose-500/10 text-rose-300 border-rose-500/20';
      case 'HIGH': return 'bg-amber-500/10 text-amber-300 border-amber-500/20';
      case 'MEDIUM': return 'bg-yellow-500/10 text-yellow-300 border-yellow-500/20';
      default: return 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20';
    }
  };

  const sectionsToShow = selectedCategory === 'ALL'
    ? TOOL_SECTIONS
    : TOOL_SECTIONS.filter(s => s.title === selectedCategory);

  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full bg-[#01060D]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3">
        <button className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
          <Menu className="w-4 h-4" />
        </button>
        <div className="text-center">
          <h1 className="text-[#F4F8FF] font-bold text-base">VASU</h1>
        </div>
        <div className="w-9" />
      </div>

      {/* Title */}
      <div className="px-4 pb-2">
        <h2 className="text-[#F4F8FF] text-lg font-bold">Tools</h2>
        <p className="text-[#7895B8] text-xs">All the tools you need, in one place</p>
      </div>

      {/* Search */}
      <div className="px-4 mb-3">
        <div className="relative">
          <Search className="w-4 h-4 text-[#7895B8] absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search tools..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-[#061827] border border-[#008CFF]/10 rounded-xl pl-9 pr-3 py-2.5 text-sm text-[#F4F8FF] placeholder-[#7895B8]/60 focus:outline-none focus:border-[#008CFF]/40 transition-all"
          />
        </div>
      </div>

      {/* Category Tabs */}
      <div className="px-4 mb-3 flex gap-1.5 overflow-x-auto no-scrollbar pb-1">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`text-[11px] font-mono px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap cursor-pointer ${
              selectedCategory === cat
                ? 'bg-[#008CFF]/20 text-[#008CFF] border border-[#008CFF]/30 font-semibold'
                : 'bg-[#061827] text-[#7895B8] border border-[#008CFF]/10 hover:text-[#F4F8FF]'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Hero Card */}
      <div className="mx-4 mb-4 p-4 bg-[#061827] border border-[#008CFF]/10 rounded-2xl relative overflow-hidden">
        <div className="absolute right-4 top-1/2 -translate-y-1/2 opacity-10">
          <Briefcase className="w-20 h-20 text-[#008CFF]" />
        </div>
        <p className="text-[10px] text-[#008CFF] font-mono uppercase tracking-wider mb-1">Smart Tools</p>
        <p className="text-[#F4F8FF] font-bold text-sm">for a Smarter Tomorrow</p>
        <p className="text-[#7895B8] text-xs mt-1 max-w-[70%]">More than just an assistant — VASU gives you powerful tools to make your life easier.</p>
      </div>

      {/* Tool Sections */}
      <div className="flex-1 overflow-y-auto px-4 space-y-4 pb-4">
        {sectionsToShow.map((section) => (
          <div key={section.title}>
            <div className="flex items-center justify-between mb-2">
              <p className="text-[10px] text-[#7895B8] font-mono uppercase tracking-wider">{section.title}</p>
              <button className="text-[10px] text-[#008CFF] hover:text-[#00C8FF] cursor-pointer">See All →</button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {section.tools.map((tool) => (
                <button
                  key={tool.label}
                  onClick={() => {
                    const registered = REGISTERED_TOOLS.find(t => t.id === tool.toolId);
                    if (registered) handleTestTool(registered);
                  }}
                  className="p-3 bg-[#061827] border border-[#008CFF]/10 rounded-xl hover:border-[#008CFF]/30 transition-all cursor-pointer text-left group"
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="w-8 h-8 rounded-lg bg-[#008CFF]/10 border border-[#008CFF]/20 flex items-center justify-center text-[#008CFF] group-hover:scale-105 transition-transform">
                      <tool.icon className="w-4 h-4" />
                    </div>
                    <ChevronRight className="w-4 h-4 text-[#7895B8] group-hover:text-[#008CFF] transition-colors" />
                  </div>
                  <p className="text-[11px] text-[#F4F8FF] font-medium">{tool.label}</p>
                  <p className="text-[10px] text-[#7895B8]">{tool.desc}</p>
                </button>
              ))}
            </div>
          </div>
        ))}

        {/* All Registered Tools */}
        <div>
          <p className="text-[10px] text-[#7895B8] font-mono uppercase tracking-wider mb-2">ALL REGISTERED TOOLS ({filteredTools.length})</p>
          <div className="space-y-2">
            {filteredTools.map((tool) => {
              const isExecuting = executingToolId === tool.id;
              const result = lastResults[tool.id];
              return (
                <div key={tool.id} className="p-3 bg-[#061827] border border-[#008CFF]/10 rounded-xl">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 flex-wrap mb-1">
                        <span className="font-mono text-xs font-bold text-[#008CFF]">{tool.name}</span>
                        <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded border uppercase font-semibold ${getRiskBadge(tool.risk)}`}>
                          {tool.risk}
                        </span>
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#01060D] text-[#7895B8] border border-[#008CFF]/10">
                          {tool.category}
                        </span>
                      </div>
                      <p className="text-xs text-[#7895B8] leading-snug">{tool.description}</p>
                    </div>
                    <button
                      disabled={isExecuting}
                      onClick={() => handleTestTool(tool)}
                      className="bg-[#008CFF]/10 hover:bg-[#008CFF]/20 border border-[#008CFF]/20 text-[#008CFF] px-3 py-1.5 rounded-lg text-xs font-mono font-medium flex items-center gap-1.5 transition-colors cursor-pointer shrink-0 disabled:opacity-50"
                    >
                      {isExecuting ? (
                        <><RefreshCw className="w-3 h-3 animate-spin" /><span>...</span></>
                      ) : (
                        <><Play className="w-3 h-3 fill-current" /><span>TEST</span></>
                      )}
                    </button>
                  </div>
                  {result && (
                    <div className={`mt-2 p-2 rounded-lg text-xs font-mono border ${
                      result.success
                        ? 'bg-emerald-500/5 border-emerald-500/20 text-emerald-300'
                        : 'bg-rose-500/5 border-rose-500/20 text-rose-300'
                    }`}>
                      <div className="flex items-center gap-1.5 font-semibold mb-0.5">
                        {result.success ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertTriangle className="w-3.5 h-3.5" />}
                        <span>Result:</span>
                      </div>
                      <p className="text-[11px] whitespace-pre-wrap">{result.result}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
