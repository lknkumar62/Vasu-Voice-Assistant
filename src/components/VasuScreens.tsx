import React, { useEffect, useMemo, useRef, useState } from "react";
import { AssistantState, ChatMessage, VasuSettings, ToolItem } from "../types";
import { VasuOrb } from "./VasuOrb";
import { REGISTERED_TOOLS, ToolExecutionResult } from "../services/toolRegistry";
import { audioEngine } from "../services/audioEngine";
import { ttsManager } from "../services/ttsManager";
import { apiKeyManager } from "../services/apiKeyManager";
import { ScreenTab } from "./QuickActions";

import {
  Menu, User, Search, Plus, Send, Mic, Paperclip,
  Phone, MessageCircle, Camera, Flashlight, Youtube, MapPin,
  Settings, MoreHorizontal, Sparkles, Volume2, VolumeX,
  Lightbulb, Copy, Check, Trash2, Bot, Terminal, AlertCircle, CheckCircle2,
  ChevronRight, RefreshCw, Play, FileText, Bell, Calendar,
  Calculator, Image as ImageIcon, Music, Video, Users,
  Bluetooth, Wifi, Eye, Languages, QrCode, FolderOpen, Cloud,
  Shield, ShieldCheck, Lock, Database, Cpu, Share2, Code,
  Info, Palette, Radio, Headphones, WifiOff, ArrowLeft, Home,
} from "lucide-react";

/* ================================================================
   COMMON
   ================================================================ */

const CircleButton = ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) => (
  <button type="button" onClick={onClick} className="w-11 h-11 shrink-0 rounded-full bg-[#061827] border border-[#008CFF]/35 text-[#BBD8F5] flex items-center justify-center hover:text-[#00C8FF] hover:border-[#00C8FF] hover:shadow-[0_0_22px_rgba(0,140,255,.25)] active:scale-95 transition">
    {children}
  </button>
);

const Card = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <div className={`bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20 rounded-[18px] shadow-[inset_0_1px_rgba(255,255,255,.03)] ${className}`}>
    {children}
  </div>
);

const IconBox = ({ children }: { children: React.ReactNode }) => (
  <div className="w-10 h-10 rounded-xl bg-[#008CFF]/10 border border-[#008CFF]/25 text-[#008CFF] flex items-center justify-center">
    {children}
  </div>
);

const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <div className="text-[11px] tracking-[.25em] text-[#86BDF2] font-bold uppercase mb-2 mt-5">
    {children}
  </div>
);

/* ================================================================
   HOME
   ================================================================ */

export interface HomeViewProps {
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
  assistantState, audioLevel = 0, activeToolName, messages,
  liveVoiceTranscript = "", isTorchActive,
  onToggleListen, onSendMessage, onReplayAudio,
  onToggleTorch, onSelectTab,
}) => {
  const [input, setInput] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const latestVasu = [...messages].reverse().find((m) => m.sender === "vasu");

  const send = (e?: React.FormEvent) => {
    e?.preventDefault();
    const value = input.trim();
    if (!value) return;
    setInput("");
    onSendMessage(value);
  };

  const quickActions = [
    { label: "Call", desc: "Make a call", icon: Phone, command: "Call dialer kholo" },
    { label: "WhatsApp", desc: "Send a message", icon: MessageCircle, command: "WhatsApp kholo" },
    { label: "Camera", desc: "Take a photo", icon: Camera, command: "Camera kholo" },
    { label: "Torch", desc: "Turn on light", icon: Flashlight, command: "" },
    { label: "YouTube", desc: "Play anything", icon: Youtube, command: "YouTube kholo" },
    { label: "Maps", desc: "Find a location", icon: MapPin, command: "Google Maps kholo" },
    { label: "Settings", desc: "Open settings", icon: Settings, command: "" },
    { label: "More", desc: "All features", icon: MoreHorizontal, command: "" },
  ];

  const executeQuickAction = (item: (typeof quickActions)[number]) => {
    if (item.label === "Torch") { onToggleTorch(); return; }
    if (item.label === "Settings") { onSelectTab?.("SETTINGS"); return; }
    if (item.label === "More") { onSelectTab?.("TOOLS"); return; }
    onSendMessage(item.command);
  };

  return (
    <main className="min-h-full w-full max-w-2xl mx-auto bg-[#01060D] text-[#F4F8FF] px-4 pb-24" style={{ paddingTop: 'env(safe-area-inset-top, 0px)' }}>
      <header className="grid grid-cols-[48px_1fr_48px] items-center py-3">
        <CircleButton onClick={() => setDrawerOpen(true)}><Menu size={22} /></CircleButton>
        <div className="text-center">
          <div className="text-3xl font-black tracking-[.17em] text-white drop-shadow-[0_0_12px_rgba(0,140,255,.45)]">VASU</div>
          <div className="text-[8px] tracking-[.38em] text-[#7895B8]">YOUR AI COMPANION</div>
        </div>
        <CircleButton onClick={() => onSelectTab?.("SETTINGS")}><User size={21} /></CircleButton>
      </header>

      <section className="pt-5">
        <h1 className="text-[32px] leading-tight font-bold">Hello, <span className="text-[#008CFF]">User</span></h1>
        <h2 className="text-[27px] font-bold">I'm <span className="text-[#008CFF]">VASU</span></h2>
        <p className="text-[#7895B8] text-[16px] mt-1">How can I assist you today?</p>
      </section>

      <section className="relative h-[360px] flex items-center justify-center">
        <div className="absolute w-[310px] h-[310px] rounded-full border border-[#008CFF]/10 animate-pulse" />
        <div className="absolute w-[270px] h-[270px] rounded-full border border-[#00C8FF]/15" />
        <div className="absolute w-[230px] h-[230px] rounded-full border border-[#008CFF]/10" />
        <VasuOrb state={assistantState} audioLevel={audioLevel} activeToolName={activeToolName} onClick={onToggleListen} size="hero" />
        <div className="absolute left-0 bottom-10 text-[9px] leading-6 tracking-[.18em] text-[#7895B8]">
          LISTENS<br />UNDERSTANDS<br />ACTS<br />ALWAYS WITH YOU
        </div>
        <div className="absolute right-0 bottom-10 text-right text-[15px] italic leading-7 text-[#00C8FF]">
          Control<br />Create<br />Explore<br />With VASU
        </div>
      </section>

      {(liveVoiceTranscript || latestVasu) && (
        <Card className="p-4 mb-3">
          <div className="flex items-center justify-between text-xs text-[#008CFF]">
            <span>
              <Sparkles size={14} className="inline mr-1" />
              {assistantState === "LISTENING" ? "Listening..." : "VASU Response"}
            </span>
            {latestVasu && (
              <button type="button" onClick={() => onReplayAudio(latestVasu.text)} className="text-[#7895B8] hover:text-[#00C8FF]">
                <Volume2 size={15} />
              </button>
            )}
          </div>
          <p className="text-sm mt-2 leading-relaxed whitespace-pre-wrap">{liveVoiceTranscript || latestVasu?.text}</p>
        </Card>
      )}

      <form onSubmit={send} className="flex items-center gap-2 p-2 mb-4 rounded-[18px] bg-[#061827] border border-[#008CFF]/35 shadow-[0_0_18px_rgba(0,140,255,.08)]">
        <button type="button" className="w-10 h-10 rounded-xl flex items-center justify-center text-[#7895B8]"><Paperclip size={19} /></button>
        <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ask VASU anything..." className="flex-1 min-w-0 bg-transparent outline-none text-sm text-white placeholder:text-[#7895B8]/60" />
        <button type="submit" disabled={!input.trim()} className="w-11 h-11 rounded-full bg-[#008CFF] flex items-center justify-center disabled:opacity-30 shadow-[0_0_20px_rgba(0,140,255,.45)]"><Send size={18} /></button>
      </form>

      <div className="grid grid-cols-4 gap-2">
        {quickActions.map((item) => {
          const Icon = item.icon;
          const active = item.label === "Torch" && isTorchActive;
          return (
            <button key={item.label} type="button" onClick={() => executeQuickAction(item)}
              className={`min-h-[108px] rounded-[18px] p-3 bg-gradient-to-br from-[#061827] to-[#030F1B] border ${active ? "border-amber-400/70" : "border-[#008CFF]/20"} flex flex-col items-center justify-center gap-2 active:scale-95 transition`}>
              <Icon size={27} className={item.label === "Call" ? "text-emerald-400" : item.label === "WhatsApp" ? "text-green-400" : item.label === "Torch" && active ? "text-amber-300" : "text-[#008CFF]"} />
              <span className="text-xs font-semibold">{item.label}</span>
              <span className="text-[9px] text-[#7895B8] text-center">{item.desc}</span>
            </button>
          );
        })}
      </div>

      <Card className="mt-4 p-4 flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-[#008CFF]/10 border border-[#008CFF]/30 flex items-center justify-center"><Lightbulb size={26} className="text-[#008CFF]" /></div>
        <div className="flex-1">
          <div className="text-[#008CFF] font-bold text-sm">Today's Insight</div>
          <p className="text-sm mt-1">A smarter tomorrow begins with what you do today.</p>
        </div>
        <ChevronRight size={22} className="text-[#7895B8]" />
      </Card>

      {/* Side Drawer */}
      {drawerOpen && (
        <div className="fixed inset-0 z-50 flex">
          <div className="absolute inset-0 bg-black/60" onClick={() => setDrawerOpen(false)} />
          <div className="relative w-72 max-w-[80vw] h-full bg-[#030F1B] border-r border-[#008CFF]/20 flex flex-col shadow-2xl">
            <div className="p-4 border-b border-[#008CFF]/15 flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-[#008CFF]/15 border border-[#008CFF]/50 flex items-center justify-center">
                <Bot size={20} className="text-[#008CFF]" />
              </div>
              <div>
                <div className="text-sm font-bold text-white">VASU</div>
                <div className="text-[10px] text-[#7895B8]">YOUR AI COMPANION</div>
              </div>
            </div>
            <nav className="flex-1 overflow-y-auto py-2">
              {[
                { label: "Home", icon: Home, tab: "HOME" },
                { label: "Chat", icon: MessageCircle, tab: "CHAT" },
                { label: "Voice Mode", icon: Mic, tab: "VOICE" },
                { label: "Tools", icon: Terminal, tab: "TOOLS" },
                { label: "Memory", icon: Database, tab: "MEMORY" },
                { label: "Missions", icon: Sparkles, tab: "MISSIONS" },
                { label: "Guardian", icon: Shield, tab: "GUARDIAN" },
                { label: "Permissions", icon: Lock, tab: "PERMISSIONS" },
                { label: "Settings", icon: Settings, tab: "SETTINGS" },
              ].map(({ label, icon: Icon, tab }) => (
                <button key={tab} type="button" onClick={() => { onSelectTab?.(tab as any); setDrawerOpen(false); }}
                  className="w-full flex items-center gap-3 px-4 py-3 text-sm text-[#BBD8F5] hover:bg-[#008CFF]/10 hover:text-[#00C8FF] transition">
                  <Icon size={18} />
                  <span>{label}</span>
                </button>
              ))}
            </nav>
            <div className="p-4 border-t border-[#008CFF]/15 text-[10px] text-[#7895B8]">
              VASU Assistant v1.0
            </div>
          </div>
        </div>
      )}
    </main>
  );
};

/* ================================================================
   CHAT
   ================================================================ */

export interface ChatViewProps {
  messages: ChatMessage[];
  onSendMessage: (text: string) => void;
  onClearChat: () => void;
  isLoading: boolean;
  liveVoiceTranscript?: string;
  isListening?: boolean;
  audioLevel?: number;
  assistantState?: AssistantState;
  onToggleMic?: () => void;
  isContinuousListening?: boolean;
}

export const ChatView: React.FC<ChatViewProps> = ({
  messages, onSendMessage, onClearChat, isLoading,
  liveVoiceTranscript = "", isListening = false,
  assistantState = "IDLE", onToggleMic,
}) => {
  const [input, setInput] = useState("");
  const [copied, setCopied] = useState<string | null>(null);
  const [playingMsgId, setPlayingMsgId] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const isUserNearBottomRef = useRef<boolean>(true);
  const lastMessageCountRef = useRef<number>(messages.length);

  // Intelligent scroll: only auto-scroll if user is near bottom
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const isNearBottom = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      // User is near bottom if within 150px of the bottom
      return scrollHeight - scrollTop - clientHeight < 150;
    };

    const handleScroll = () => {
      isUserNearBottomRef.current = isNearBottom();
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => container.removeEventListener('scroll', handleScroll);
  }, []);

  // Auto-scroll only when user is near bottom and new message arrives
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const newMessageAdded = messages.length > lastMessageCountRef.current;
    lastMessageCountRef.current = messages.length;

    // Only auto-scroll if user was already near bottom AND a new message was added
    if (isUserNearBottomRef.current && newMessageAdded) {
      // Use requestAnimationFrame for smooth scroll without jank
      requestAnimationFrame(() => {
        endRef.current?.scrollIntoView({ behavior: "smooth" });
      });
    }
  }, [messages]);

  useEffect(() => {
    const unsub = audioEngine.onStateChange((st) => { if (st !== "SPEAKING") setPlayingMsgId(null); });
    return unsub;
  }, []);

  const send = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;
    onSendMessage(input.trim());
    setInput("");
  };

  const copy = (id: string, text: string) => {
    navigator.clipboard?.writeText(text);
    setCopied(id);
    setTimeout(() => setCopied(null), 1500);
  };

  const speak = (msgId: string, text: string) => {
    if (playingMsgId === msgId || audioEngine.isSpeaking()) {
      audioEngine.stop();
      setPlayingMsgId(null);
      return;
    }
    setPlayingMsgId(msgId);
    audioEngine.speakAssistantResponse(text, {
      source: "replay",
      onStart: () => setPlayingMsgId(msgId),
      onEnd: () => setPlayingMsgId(null),
    });
  };

  const suggestions = [
    ["Ask a Question", "Explain quantum computing simply", Search],
    ["Generate Image", "Create a futuristic city image", ImageIcon],
    ["Write Code", "Write a Python sorting function", Code],
    ["Summarize", "Summarize this document", FileText],
    ["Control Device", "Turn on torch", Mic],
    ["Give Ideas", "Give me 5 business ideas", Lightbulb],
  ] as const;

  return (
    <main className="h-full min-h-0 w-full max-w-6xl mx-auto bg-[#01060D] text-[#F4F8FF] px-3 pb-24 flex flex-col">
      <header className="flex items-center justify-between py-4">
        <CircleButton><Menu size={20} /></CircleButton>
        <div className="text-center">
          <div className="text-2xl font-black tracking-[.18em]">VASU</div>
          <div className="text-[8px] tracking-[.3em] text-[#7895B8]">YOUR AI COMPANION</div>
        </div>
        <div className="flex gap-2">
          <CircleButton><Search size={18} /></CircleButton>
          <CircleButton><Plus size={18} /></CircleButton>
        </div>
      </header>

      <div className="pb-3">
        <h1 className="text-3xl font-bold">Chat</h1>
        <p className="text-[#7895B8]">Talk. Ask. Explore. VASU is always with you.</p>
      </div>

      <div className="grid md:grid-cols-[240px_1fr] gap-3 flex-1 min-h-0">
        <aside className="hidden md:flex flex-col bg-[#061827] border border-[#008CFF]/15 rounded-[18px] p-3 overflow-hidden">
          <div className="flex items-center gap-2 p-2 rounded-xl border border-[#008CFF]/15 mb-3">
            <Search size={16} className="text-[#7895B8]" />
            <input placeholder="Search conversations..." className="bg-transparent outline-none text-xs w-full" />
          </div>
          <button type="button" className="bg-[#008CFF] rounded-xl py-3 font-semibold mb-3">
            <Plus size={17} className="inline mr-2" />New Chat
          </button>
          {["New Chat", "Phone Control", "Study Help", "Image Generation", "YouTube Ideas", "App Development", "Travel Plan", "Document Help", "Motivation"].map((chat) => (
            <button key={chat} type="button" className="text-left p-3 rounded-lg hover:bg-[#008CFF]/10 border-b border-white/5 text-sm">
              {chat}<span className="block text-[10px] text-[#7895B8] mt-1">Conversation</span>
            </button>
          ))}
          <button type="button" onClick={onClearChat} className="mt-auto p-3 rounded-xl border border-rose-500/20 text-rose-300 text-sm">
            <Trash2 size={16} className="inline mr-2" />Clear All Chats
          </button>
        </aside>

        <section className="min-h-0 flex flex-col bg-[#030F1B] border border-[#008CFF]/20 rounded-[20px] overflow-hidden">
          <div className="p-3 border-b border-[#008CFF]/15 flex items-center gap-3">
            <div className={`w-12 h-12 rounded-full border flex items-center justify-center transition-all ${assistantState === "SPEAKING" ? "bg-[#008CFF]/20 border-[#008CFF]/50 shadow-lg shadow-[#008CFF]/20" : "bg-[#008CFF]/15 border-[#008CFF]/50"}`}>
              <Bot size={25} className={`transition-colors ${assistantState === "SPEAKING" ? "text-[#00C8FF]" : "text-[#00C8FF]"}`} />
            </div>
            <div className="flex-1">
              <div className="font-bold">VASU</div>
              <div className="text-xs text-emerald-400">● Online</div>
              <div className="text-[10px] text-[#7895B8]">
                {assistantState === "LISTENING" ? "Listening..." : assistantState === "THINKING" ? "Thinking..." : assistantState === "EXECUTING" ? "Executing..." : assistantState === "SPEAKING" ? "Speaking..." : "Always ready to help"}
              </div>
            </div>
            <button type="button" onClick={() => { if (assistantState === "SPEAKING") audioEngine.stop(); }} className="text-[#7895B8] hover:text-[#00C8FF]">
              {assistantState === "SPEAKING" ? <VolumeX size={19} /> : <Volume2 size={19} />}
            </button>
          </div>

          <div ref={scrollContainerRef} className="flex-1 overflow-y-auto p-3 space-y-3">
            {messages.length === 0 && !isListening && !liveVoiceTranscript && (
              <div className="py-6 text-center">
                <div className="w-16 h-16 mx-auto rounded-full bg-[#008CFF]/10 border border-[#008CFF]/30 flex items-center justify-center"><Bot size={31} className="text-[#008CFF]" /></div>
                <h2 className="mt-3 font-bold">Hello, User 👋</h2>
                <p className="text-xs text-[#7895B8]">I'm VASU, your AI companion.</p>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mt-5">
                  {suggestions.map(([label, command, Icon]) => (
                    <button key={label} type="button" onClick={() => onSendMessage(command)} className="text-left p-3 rounded-xl bg-[#061827] border border-[#008CFF]/15 hover:border-[#008CFF]/40">
                      <Icon size={18} className="text-[#008CFF] mb-2" />
                      <span className="text-xs">{label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((msg) => {
              const isUser = msg.sender === "user";
              const isSystem = msg.sender === "system";
              if (isSystem) {
                return (
                  <div key={msg.id} className="text-center text-[10px] text-[#7895B8]">
                    <AlertCircle size={13} className="inline mr-1" />{msg.text}
                  </div>
                );
              }
              return (
                <div key={msg.id} className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
                  <div className={`max-w-[88%] rounded-2xl p-3 ${isUser ? "bg-[#008CFF]/20 border border-[#008CFF]/30 rounded-tr-sm" : "bg-[#061827] border border-[#008CFF]/15 rounded-tl-sm"}`}>
                    {msg.toolCall && (
                      <div className="mb-2 p-2 bg-black/30 rounded-lg text-[10px]">
                        <Terminal size={12} className="inline text-[#008CFF] mr-1" />
                        Tool: {msg.toolCall.tool} · {msg.toolCall.status}
                      </div>
                    )}
                    <div className="whitespace-pre-wrap text-sm leading-relaxed">{msg.text}</div>
                    {!isUser && (
                      <div className="flex gap-4 mt-3 pt-2 border-t border-white/5">
                        <button type="button" onClick={() => copy(msg.id, msg.text)} className="text-[#7895B8] hover:text-[#00C8FF]">
                          {copied === msg.id ? <Check size={14} className="text-emerald-400" /> : <Copy size={14} />}
                        </button>
                        <button type="button" onClick={() => speak(msg.id, msg.text)} className={`text-[#7895B8] ${playingMsgId === msg.id ? "text-[#008CFF] animate-pulse" : "hover:text-[#00C8FF]"}`}>
                          {playingMsgId === msg.id ? <VolumeX size={14} /> : <Volume2 size={14} />}
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}

            {liveVoiceTranscript && (
              <div className="flex justify-end">
                <div className="max-w-[85%] bg-[#008CFF]/20 border border-[#008CFF]/30 rounded-2xl rounded-tr-sm p-3 text-sm text-[#F4F8FF]">
                  <div className="flex items-center gap-1.5 mb-1 text-[#008CFF] text-[11px] font-mono">
                    <span className="w-2 h-2 rounded-full bg-[#008CFF] animate-ping" />
                    <Mic className="w-3.5 h-3.5" /><span>Listening...</span>
                  </div>
                  <p className="font-medium">"{liveVoiceTranscript}"</p>
                </div>
              </div>
            )}

            {isLoading && !isListening && (
              <div className="flex justify-start items-center">
                <div className="bg-[#061827] border border-[#008CFF]/10 rounded-2xl rounded-tl-sm p-3 text-xs text-[#008CFF] font-mono flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#008CFF] animate-ping" />
                  <span>VASU soch rahi hai...</span>
                </div>
              </div>
            )}

            <div ref={endRef} />
          </div>

          <div className="p-2 border-t border-[#008CFF]/15">
            <div className="flex gap-2 overflow-x-auto mb-2">
              <button type="button" onClick={() => onSendMessage("Camera kholo")} className="shrink-0 flex items-center gap-1 px-3 py-2 rounded-xl bg-[#061827] border border-[#008CFF]/15 text-[10px]"><Camera size={14} />Take a photo</button>
              <button type="button" onClick={() => onSendMessage("YouTube kholo")} className="shrink-0 flex items-center gap-1 px-3 py-2 rounded-xl bg-[#061827] border border-[#008CFF]/15 text-[10px]"><Youtube size={14} />Open YouTube</button>
              <button type="button" onClick={() => onSendMessage("Torch on karo")} className="shrink-0 flex items-center gap-1 px-3 py-2 rounded-xl bg-[#061827] border border-[#008CFF]/15 text-[10px]"><Flashlight size={14} />Turn on torch</button>
              {onToggleMic && (
                <button type="button" onClick={onToggleMic} className={`shrink-0 flex items-center gap-1 px-3 py-2 rounded-xl border text-[10px] ${isListening ? "bg-[#008CFF] text-[#01060D] border-[#008CFF]" : "bg-[#061827] border-[#008CFF]/15"}`}>
                  <Mic size={14} />{isListening ? "Listening..." : "Voice mode"}
                </button>
              )}
            </div>
            <form onSubmit={send} className="flex items-center gap-2 p-2 rounded-[18px] bg-[#061827] border border-[#008CFF]/35">
              <Paperclip size={19} className="text-[#7895B8]" />
              <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ask VASU anything..." className="flex-1 min-w-0 bg-transparent outline-none text-sm" />
              {onToggleMic && (
                <button type="button" onClick={onToggleMic} className={`w-9 h-9 flex items-center justify-center ${isListening ? "text-[#008CFF]" : "text-[#7895B8]"}`}>
                  <Mic size={18} />
                </button>
              )}
              <button type="submit" disabled={!input.trim() || isLoading} className="w-10 h-10 rounded-full bg-[#008CFF] flex items-center justify-center disabled:opacity-30"><Send size={17} /></button>
            </form>
          </div>
        </section>
      </div>
    </main>
  );
};

/* ================================================================
   TOOLS
   ================================================================ */

export interface ToolsViewProps {
  onExecuteTool: (toolId: string) => Promise<ToolExecutionResult>;
}

const toolGroups: { title: string; items: [string, string, React.FC<any>, string][] }[] = [
  { title: "PRODUCTIVITY", items: [["Notes", "browse_files", FileText, "Write & save notes"], ["Reminders", "create_alarm", Bell, "Set smart reminders"], ["Calendar", "create_alarm", Calendar, "Manage your schedule"], ["Calculator", "read_screen", Calculator, "Simple & scientific"]] },
  { title: "MEDIA", items: [["Camera", "take_photo", Camera, "Take photos & videos"], ["Gallery", "browse_files", ImageIcon, "View your media files"], ["Music", "media_play_pause", Music, "Play your favorite songs"], ["Video Player", "media_play_pause", Video, "Watch videos offline"]] },
  { title: "COMMUNICATION", items: [["Call", "make_call", Phone, "Make phone calls"], ["WhatsApp", "open_whatsapp", MessageCircle, "Send messages"], ["Messages", "send_message", MessageCircle, "SMS & chat"], ["Contacts", "lookup_contact", Users, "Manage your contacts"]] },
  { title: "SYSTEM", items: [["Torch", "turn_on_torch", Flashlight, "Turn on light"], ["Bluetooth", "device_info", Bluetooth, "Manage devices"], ["Wi-Fi", "device_info", Wifi, "Connect networks"], ["Device Control", "device_info", Settings, "Control system settings"]] },
  { title: "AI TOOLS", items: [["AI Search", "search_web", Search, "Get instant answers"], ["AI Vision", "ocr_extract", Eye, "See & analyze images"], ["Document AI", "read_screen", FileText, "Summarize & extract text"], ["Translate", "read_screen", Languages, "Multiple languages"]] },
  { title: "UTILITIES", items: [["Maps", "search_web", MapPin, "Find locations"], ["QR Scanner", "ocr_extract", QrCode, "Scan & generate"], ["File Manager", "browse_files", FolderOpen, "Manage your files"], ["Cloud Sync", "storage_info", Cloud, "Backup & sync"]] },
];

export const ToolsView: React.FC<ToolsViewProps> = ({ onExecuteTool }) => {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("ALL");
  const [running, setRunning] = useState<string | null>(null);
  const [results, setResults] = useState<Record<string, ToolExecutionResult>>({});

  const categories = ["ALL", ...toolGroups.map((g) => g.title)];

  const registered = useMemo(() => REGISTERED_TOOLS.filter((tool) => `${tool.name} ${tool.title} ${tool.description}`.toLowerCase().includes(search.toLowerCase())), [search]);

  const execute = async (id: string) => {
    if (running) return;
    setRunning(id);
    try {
      const result = await onExecuteTool(id);
      setResults((old) => ({ ...old, [id]: result }));
    } finally {
      setRunning(null);
    }
  };

  return (
    <main className="min-h-full w-full max-w-6xl mx-auto bg-[#01060D] text-[#F4F8FF] px-3 pb-24">
      <header className="flex items-center justify-between py-4">
        <CircleButton><Menu size={20} /></CircleButton>
        <div className="text-center">
          <div className="text-2xl font-black tracking-[.18em]">VASU</div>
          <div className="text-[8px] tracking-[.3em] text-[#7895B8]">YOUR AI COMPANION</div>
        </div>
        <CircleButton><Search size={18} /></CircleButton>
      </header>

      <h1 className="text-3xl font-bold">Tools</h1>
      <p className="text-[#7895B8]">All the tools you need, in one place</p>

      <Card className="flex items-center gap-2 p-2 mt-4">
        <Search size={17} className="text-[#7895B8]" />
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search tools..." className="flex-1 bg-transparent outline-none text-sm" />
      </Card>

      <div className="flex gap-2 overflow-x-auto py-3">
        {categories.map((item) => (
          <button key={item} type="button" onClick={() => setCategory(item)}
            className={`shrink-0 px-4 py-2 rounded-xl text-[10px] border ${category === item ? "bg-[#008CFF]/25 text-[#00C8FF] border-[#008CFF]/60" : "bg-[#061827] text-[#7895B8] border-[#008CFF]/15"}`}>
            {item}
          </button>
        ))}
      </div>

      <Card className="p-5 mb-5 relative overflow-hidden">
        <Sparkles size={65} className="absolute right-6 top-5 text-[#008CFF] opacity-30" />
        <h2 className="text-xl font-bold">Smart Tools for a Smarter Tomorrow</h2>
        <p className="text-sm text-[#7895B8] mt-1 max-w-xl">More than just an assistant — VASU gives you powerful tools to make your life easier.</p>
      </Card>

      <div className="space-y-6">
        {toolGroups.filter((group) => category === "ALL" || category === group.title).map(({ title, items }) => (
          <section key={title}>
            <div className="flex justify-between items-center mb-2">
              <SectionTitle>{title}</SectionTitle>
              <span className="text-xs text-[#008CFF]">See All →</span>
            </div>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
              {items.filter((item) => !search || `${item[0]} ${item[3]}`.toLowerCase().includes(search.toLowerCase())).map(([label, toolId, Icon, desc]) => (
                <button key={label} type="button" disabled={!!running} onClick={() => execute(toolId)}
                  className="text-left p-4 min-h-[125px] rounded-[18px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20 hover:border-[#008CFF]/50 active:scale-[.98] transition disabled:opacity-60">
                  <div className="flex justify-between">
                    <IconBox><Icon size={22} /></IconBox>
                    <ChevronRight size={17} className="text-[#7895B8]" />
                  </div>
                  <div className="font-semibold text-sm mt-3">{label}</div>
                  <div className="text-[10px] text-[#7895B8] mt-1">{desc}</div>
                  {running === toolId && <RefreshCw size={14} className="animate-spin text-[#00C8FF] mt-2" />}
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>

      <section className="mt-8">
        <SectionTitle>ALL REGISTERED TOOLS ({registered.length})</SectionTitle>
        <div className="space-y-2">
          {registered.map((tool: ToolItem) => {
            const result = results[tool.id];
            return (
              <div key={tool.id} className="rounded-[18px] bg-[#061827] border border-[#008CFF]/15 p-3 flex items-center gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex gap-2 flex-wrap items-center">
                    <b className="text-xs text-[#00C8FF]">{tool.name}</b>
                    <span className="text-[9px] text-[#7895B8]">{tool.category}</span>
                    <span className="text-[9px] text-[#7895B8]">{tool.risk}</span>
                  </div>
                  <p className="text-[10px] text-[#7895B8] truncate mt-1">{tool.description}</p>
                  {result && (
                    <div className="text-[10px] text-emerald-400 mt-1">
                      <CheckCircle2 size={12} className="inline mr-1" />{result.success ? "Executed" : "Failed"}
                    </div>
                  )}
                </div>
                <button type="button" disabled={running === tool.id} onClick={() => execute(tool.id)}
                  className="shrink-0 px-3 py-2 rounded-xl bg-[#008CFF]/10 border border-[#008CFF]/25 text-[#008CFF] text-[10px] flex items-center gap-1">
                  {running === tool.id ? <RefreshCw size={12} className="animate-spin" /> : <Play size={12} fill="currentColor" />}
                  {running === tool.id ? "..." : "TEST"}
                </button>
              </div>
            );
          })}
        </div>
      </section>
    </main>
  );
};

/* ================================================================
   SETTINGS
   ================================================================ */

export interface SettingsViewProps {
  settings: VasuSettings;
  onUpdateSettings: (newSettings: Partial<VasuSettings>) => void;
  onClearMemory: () => void;
  onClearChat: () => void;
  onSelectTab?: (tab: ScreenTab) => void;
}

const SettingRow = ({ icon: Icon, label, value, onClick, toggle, enabled, onChange }: {
  icon: any; label: string; value?: string; onClick?: () => void;
  toggle?: boolean; enabled?: boolean; onChange?: (v: boolean) => void;
}) => (
  <button type="button" onClick={onClick || (toggle && onChange ? () => onChange(!enabled) : undefined)}
    className="w-full p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20 flex items-center gap-3 text-left active:scale-[.99] transition">
    <IconBox><Icon size={19} /></IconBox>
    <div className="flex-1 min-w-0">
      <div className="text-sm font-semibold">{label}</div>
      {value && <div className="text-[10px] text-[#7895B8] mt-1 truncate">{value}</div>}
    </div>
    {toggle ? (
      <span className={`w-11 h-6 rounded-full p-1 transition ${enabled ? "bg-[#008CFF]" : "bg-[#061827] border border-[#008CFF]/20"}`}>
        <span className={`block w-4 h-4 rounded-full bg-white transition ${enabled ? "translate-x-5" : "translate-x-0"}`} />
      </span>
    ) : (
      <ChevronRight size={17} className="text-[#7895B8]" />
    )}
  </button>
);

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings, onUpdateSettings, onClearMemory, onClearChat, onSelectTab,
}) => {
  const [page, setPage] = useState<"main" | "voice" | "security" | "advanced">("main");
  const [animations, setAnimations] = useState(true);
  const [privacy, setPrivacy] = useState(false);
  const [saveStates, setSaveStates] = useState<Record<string, 'idle' | 'saving' | 'saved' | 'error'>>({});
  const [tempKeys, setTempKeys] = useState<Record<string, string>>({});

  // Save API key handler with validation and state management
  const saveApiKey = async (key: string) => {
    const value = tempKeys[key] ?? (settings as any)[key] ?? "";
    if (!value.trim()) {
      setSaveStates(prev => ({ ...prev, [key]: 'error' }));
      setTimeout(() => setSaveStates(prev => ({ ...prev, [key]: 'idle' })), 2000);
      return;
    }

    setSaveStates(prev => ({ ...prev, [key]: 'saving' }));

    try {
      // Validate API key format based on provider
      const isValid = validateApiKey(key, value);
      if (!isValid) {
        setSaveStates(prev => ({ ...prev, [key]: 'error' }));
        setTimeout(() => setSaveStates(prev => ({ ...prev, [key]: 'idle' })), 2000);
        return;
      }

      // Save to settings (persists to localStorage via App.tsx useEffect)
      onUpdateSettings({ [key]: value });

      // Also update apiKeyManager so aiProviderManager gets configured
      const apiKeyMap: Record<string, string> = {
        geminiApiKey: 'gemini',
        openrouterApiKey: 'openrouter',
        groqApiKey: 'groq',
        deepseekApiKey: 'deepseek',
        xaiApiKey: 'xai',
        tavilyApiKey: 'tavily',
        braveSearchApiKey: 'brave',
      };
      const providerId = apiKeyMap[key];
      if (providerId) {
        try { apiKeyManager.setKey(providerId, value); } catch (_) {}
      }

      // Clear temp state
      setTempKeys(prev => { const n = { ...prev }; delete n[key]; return n; });

      // Small delay to show saving state
      await new Promise(resolve => setTimeout(resolve, 300));

      setSaveStates(prev => ({ ...prev, [key]: 'saved' }));
      setTimeout(() => setSaveStates(prev => ({ ...prev, [key]: 'idle' })), 2000);
    } catch (error) {
      setSaveStates(prev => ({ ...prev, [key]: 'error' }));
      setTimeout(() => setSaveStates(prev => ({ ...prev, [key]: 'idle' })), 2000);
    }
  };

  // Validate API key format (relaxed — just check reasonable length)
  const validateApiKey = (key: string, value: string): boolean => {
    return value.trim().length >= 8;
  };

  // Get save button text based on state
  const getSaveButtonText = (key: string) => {
    const state = saveStates[key] || 'idle';
    switch (state) {
      case 'saving':
        return 'Saving...';
      case 'saved':
        return 'Saved ✓';
      case 'error':
        return 'Save failed';
      default:
        return 'Save';
    }
  };

  // Get save button class based on state
  const getSaveButtonClass = (key: string) => {
    const state = saveStates[key] || 'idle';
    const baseClass = "px-3 py-2 text-[10px] rounded-lg cursor-pointer transition-colors";
    switch (state) {
      case 'saving':
        return `${baseClass} bg-[#008CFF]/20 text-[#7895B8] border border-[#008CFF]/20 cursor-wait`;
      case 'saved':
        return `${baseClass} bg-emerald-500/20 text-emerald-400 border border-emerald-500/30`;
      case 'error':
        return `${baseClass} bg-rose-500/20 text-rose-400 border border-rose-500/30`;
      default:
        return `${baseClass} bg-[#008CFF]/10 border border-[#008CFF]/20 text-[#008CFF] hover:bg-[#008CFF]/20`;
    }
  };

  if (page !== "main") {
    const titles: Record<string, string> = { voice: "Voice & Language", security: "Privacy & Security", advanced: "Advanced" };
    return (
      <main className="min-h-full w-full max-w-3xl mx-auto bg-[#01060D] text-[#F4F8FF] px-3 pb-24">
        <header className="flex items-center gap-3 py-4">
          <CircleButton onClick={() => setPage("main")}><ArrowLeft size={20} /></CircleButton>
          <h1 className="text-xl font-bold">{titles[page]}</h1>
        </header>

        {page === "voice" && (
          <div className="space-y-2">
            <SettingRow icon={Mic} label="Voice Selection" value={settings.ttsVoice || "Kore"} />
            <SettingRow icon={Languages} label="Voice Language" value={settings.language || "Hinglish"} />
            <SettingRow icon={Radio} label="Wake Word" value={settings.wakePhrase || "Hello VASU"} />
            <SettingRow icon={Volume2} label="Speech Speed" value={`${settings.ttsSpeed || 1}x`} />
            <SettingRow icon={Volume2} label="Auto Speak" toggle enabled={settings.autoSpeak !== false}
              onChange={(v) => onUpdateSettings({ autoSpeak: v })} />
            <div className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold">Voice Volume</span>
                <span className="text-xs text-[#008CFF]">{Math.round((settings.ttsVolume ?? 1.0) * 100)}%</span>
              </div>
              <input type="range" min="0" max="1.0" step="0.05" value={settings.ttsVolume ?? 1.0}
                onChange={(e) => onUpdateSettings({ ttsVolume: parseFloat(e.target.value) })}
                className="w-full accent-[#008CFF]" />
            </div>
            <div className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold">Pitch</span>
                <span className="text-xs text-[#008CFF]">{settings.ttsPitch || 1.05}</span>
              </div>
              <input type="range" min="0.5" max="2.0" step="0.05" value={settings.ttsPitch || 1.05}
                onChange={(e) => onUpdateSettings({ ttsPitch: parseFloat(e.target.value) })}
                className="w-full accent-[#008CFF]" />
            </div>
            <div className="flex items-center justify-between p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20">
              <span className="text-sm font-semibold">Test Voice</span>
              <button type="button" onClick={() => {
                audioEngine.unlock();
                ttsManager.forceSpeak("Namaste ji! Main Vasu hoon, aapki AI sahayak. Aaj aapka din kaisa chal raha hai?", {
                  autoSpeak: true, speed: settings.ttsSpeed, pitch: settings.ttsPitch,
                  volume: settings.ttsVolume, apiKey: settings.geminiApiKey || "", source: "replay",
                });
              }} className="px-3 py-1.5 bg-[#008CFF]/10 border border-[#008CFF]/20 text-[#008CFF] text-[11px] font-mono rounded-lg hover:bg-[#008CFF]/20 cursor-pointer">
                Play Test
              </button>
            </div>
            <SettingRow icon={WifiOff} label="Offline Voice Mode" value="Work without internet" toggle enabled />
            <SettingRow icon={Headphones} label="Continuous Listening" value="Listen in background" toggle enabled={!!settings.backgroundListening}
              onChange={(v) => onUpdateSettings({ backgroundListening: v })} />
          </div>
        )}

        {page === "security" && (
          <div className="space-y-2">
            <SettingRow icon={Lock} label="App Lock" value="PIN / Fingerprint / Face" />
            <SettingRow icon={Database} label="Data & Storage" value="Manage cache & files" />
            <SettingRow icon={Shield} label="Permissions" value="Camera, Mic, Contacts, etc."
              onClick={() => onSelectTab?.("PERMISSIONS")} />
            <SettingRow icon={ShieldCheck} label="Privacy Mode" value="Hide sensitive content" toggle enabled={privacy}
              onChange={(v) => setPrivacy(v)} />
            <button type="button" onClick={onClearChat} className="w-full p-3 rounded-xl border border-rose-500/20 text-rose-300 text-sm">
              <Trash2 size={16} className="inline mr-2" />Clear Chat History
            </button>
            <button type="button" onClick={onClearMemory} className="w-full p-3 rounded-xl border border-rose-500/20 text-rose-300 text-sm">
              <Trash2 size={16} className="inline mr-2" />Clear All Memory
            </button>
          </div>
        )}

        {page === "advanced" && (
          <div className="space-y-4">
            <div className="space-y-2">
              <SectionTitle>AI PROVIDER KEYS</SectionTitle>
              {[
                { label: "Gemini API Key", key: "geminiApiKey", placeholder: "AIza..." },
                { label: "OpenRouter API Key", key: "openrouterApiKey", placeholder: "sk-or-..." },
                { label: "Groq API Key", key: "groqApiKey", placeholder: "gsk_..." },
                { label: "DeepSeek API Key", key: "deepseekApiKey", placeholder: "sk-..." },
                { label: "xAI API Key", key: "xaiApiKey", placeholder: "xai-..." },
                { label: "Tavily Search Key", key: "tavilyApiKey", placeholder: "tvly-..." },
                { label: "Brave Search Key", key: "braveSearchApiKey", placeholder: "BSA..." },
              ].map(({ label, key, placeholder }) => (
                <div key={key} className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20">
                  <label className="text-xs font-semibold text-[#7895B8] block mb-1">{label}</label>
                  <div className="flex gap-2">
                    <input
                      type="password"
                      value={tempKeys[key] !== undefined ? tempKeys[key] : ((settings as any)[key] || "")}
                      onChange={(e) => setTempKeys(prev => ({ ...prev, [key]: e.target.value }))}
                      placeholder={placeholder}
                      className="flex-1 bg-[#01060D] border border-[#008CFF]/15 rounded-lg px-3 py-2 text-sm text-white placeholder:text-[#7895B8]/40 outline-none focus:border-[#008CFF]/50"
                    />
                    <button
                      type="button"
                      onClick={() => saveApiKey(key)}
                      disabled={saveStates[key] === 'saving'}
                      className={getSaveButtonClass(key)}
                    >
                      {getSaveButtonText(key)}
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <div className="space-y-2">
              <SectionTitle>MODEL CONFIGURATION</SectionTitle>
              <div className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20">
                <label className="text-xs font-semibold text-[#7895B8] block mb-1">Gemini Model</label>
                <select value={settings.geminiModel || "gemini-2.5-flash"}
                  onChange={(e) => onUpdateSettings({ geminiModel: e.target.value })}
                  className="w-full bg-[#01060D] border border-[#008CFF]/15 rounded-lg px-3 py-2 text-sm text-white outline-none">
                  <option value="gemini-2.5-flash">gemini-2.5-flash</option>
                  <option value="gemini-2.0-flash">gemini-2.0-flash</option>
                  <option value="gemini-1.5-flash">gemini-1.5-flash</option>
                </select>
              </div>
              <div className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20">
                <label className="text-xs font-semibold text-[#7895B8] block mb-1">Language</label>
                <select value={settings.language || "Hinglish"}
                  onChange={(e) => onUpdateSettings({ language: e.target.value as any })}
                  className="w-full bg-[#01060D] border border-[#008CFF]/15 rounded-lg px-3 py-2 text-sm text-white outline-none">
                  <option value="Hinglish">Hinglish</option>
                  <option value="Hindi">Hindi</option>
                  <option value="English">English</option>
                </select>
              </div>
              <div className="p-3 rounded-[17px] bg-gradient-to-br from-[#061827] to-[#030F1B] border border-[#008CFF]/20">
                <label className="text-xs font-semibold text-[#7895B8] block mb-1">Smart Mode</label>
                <select value={settings.smartMode || "NORMAL"}
                  onChange={(e) => onUpdateSettings({ smartMode: e.target.value as any })}
                  className="w-full bg-[#01060D] border border-[#008CFF]/15 rounded-lg px-3 py-2 text-sm text-white outline-none">
                  <option value="NORMAL">Normal</option>
                  <option value="DRIVING">Driving</option>
                  <option value="SLEEP">Sleep</option>
                </select>
              </div>
            </div>
            <SettingRow icon={Cpu} label="AI Model" value={settings.geminiModel || "VASU Core"} />
            <SettingRow icon={Share2} label="System Integrations" value="Connect your apps" />
            <SettingRow icon={Code} label="Developer Options" value="Advanced settings" />
            <SettingRow icon={Info} label="About VASU" value="Version 1.0.0" />
            <SettingRow icon={Cloud} label="Backup & Sync" value="Keep settings safe across devices" />
          </div>
        )}
      </main>
    );
  }

  return (
    <main className="min-h-full w-full max-w-3xl mx-auto bg-[#01060D] text-[#F4F8FF] px-3 pb-24">
      <header className="flex items-center justify-between py-4">
        <CircleButton><Menu size={20} /></CircleButton>
        <div className="text-center">
          <div className="text-2xl font-black tracking-[.18em]">VASU</div>
          <div className="text-[8px] tracking-[.3em] text-[#7895B8]">YOUR AI COMPANION</div>
        </div>
        <div className="w-11" />
      </header>

      <h1 className="text-3xl font-bold">Settings</h1>
      <p className="text-[#7895B8] mb-5">Customize VASU to make it truly yours</p>

      <Card className="p-5 flex items-center gap-4 mb-5">
        <div className="w-16 h-16 rounded-full bg-[#008CFF]/15 border border-[#008CFF]/50 flex items-center justify-center"><Bot size={34} className="text-[#00C8FF]" /></div>
        <div className="flex-1">
          <div className="text-xl font-bold">VASU AI</div>
          <div className="text-sm text-[#7895B8]">Personal Assistant</div>
          <div className="text-[10px] text-[#008CFF] mt-1">Version 1.0.0</div>
        </div>
        <div className="text-right text-xs text-[#7895B8]">Always Evolving<br />Always With You</div>
      </Card>

      <SectionTitle>APPEARANCE</SectionTitle>
      <div className="grid md:grid-cols-2 gap-2">
        <SettingRow icon={Palette} label="Theme" value="Dark / Light / System" />
        <SettingRow icon={Sparkles} label="Orb Style" value="Classic / Energy / Neon / Matrix" />
        <SettingRow icon={Languages} label="UI Language" value="English / हिन्दी / More" />
        <SettingRow icon={Sparkles} label="Animations" value="Toggle UI animations" toggle enabled={animations} onChange={(v) => setAnimations(v)} />
      </div>

      <SectionTitle>VOICE & LANGUAGE</SectionTitle>
      <div className="grid md:grid-cols-2 gap-2">
        <SettingRow icon={Mic} label="Voice Selection" value={settings.ttsVoice || "Kore"} onClick={() => setPage("voice")} />
        <SettingRow icon={Languages} label="Voice Language" value={settings.language || "Hinglish"} onClick={() => setPage("voice")} />
        <SettingRow icon={Radio} label="Wake Word" value={settings.wakePhrase || "Hello VASU"} onClick={() => setPage("voice")} />
        <SettingRow icon={Volume2} label="Speech Speed" value={`${settings.ttsSpeed || 1}x`} onClick={() => setPage("voice")} />
        <SettingRow icon={Volume2} label="Auto Speak" toggle enabled={settings.autoSpeak !== false}
          onChange={(v) => onUpdateSettings({ autoSpeak: v })} />
        <SettingRow icon={Headphones} label="Continuous Listening" value="Listen in background" toggle enabled={!!settings.backgroundListening}
          onChange={(v) => onUpdateSettings({ backgroundListening: v })} />
      </div>

      <SectionTitle>PRIVACY & SECURITY</SectionTitle>
      <div className="grid md:grid-cols-2 gap-2">
        <SettingRow icon={Lock} label="App Lock" value="PIN / Fingerprint / Face" onClick={() => setPage("security")} />
        <SettingRow icon={Database} label="Data & Storage" value="Manage cache & files" onClick={() => setPage("security")} />
        <SettingRow icon={Shield} label="Permissions" value="Camera, Mic, Contacts, etc." onClick={() => onSelectTab?.("PERMISSIONS")} />
        <SettingRow icon={ShieldCheck} label="Privacy Mode" value="Hide sensitive content" toggle enabled={privacy} onChange={(v) => setPrivacy(v)} />
      </div>

      <SectionTitle>ADVANCED</SectionTitle>
      <div className="grid md:grid-cols-2 gap-2">
        <SettingRow icon={Cpu} label="AI Model" value={settings.geminiModel || "VASU Core"} onClick={() => setPage("advanced")} />
        <SettingRow icon={Share2} label="System Integrations" value="Connect your apps" onClick={() => setPage("advanced")} />
        <SettingRow icon={Code} label="Developer Options" value="Advanced settings" onClick={() => setPage("advanced")} />
        <SettingRow icon={Info} label="About VASU" value="Version 1.0.0" onClick={() => setPage("advanced")} />
      </div>

      <div className="mt-3">
        <SettingRow icon={Cloud} label="Backup & Sync" value="Keep your settings safe across devices" onClick={() => setPage("advanced")} />
      </div>

      <div className="grid md:grid-cols-2 gap-2 mt-5">
        <button type="button" onClick={onClearChat} className="p-3 rounded-xl bg-[#061827] border border-[#008CFF]/15 text-sm text-rose-300 hover:bg-rose-950/30">
          <Trash2 size={16} className="inline mr-2" />Clear Chat History
        </button>
        <button type="button" onClick={onClearMemory} className="p-3 rounded-xl bg-[#061827] border border-rose-500/20 text-sm text-rose-300 hover:bg-rose-950/30">
          <Trash2 size={16} className="inline mr-2" />Clear All Memory
        </button>
      </div>
    </main>
  );
};
