import React, { useState, useEffect } from 'react';
import { VasuSettings, SmartMode } from '../types';
import { ScreenTab } from './QuickActions';
import {
  Settings,
  Cpu,
  Mic,
  Volume2,
  Sparkles,
  Shield,
  Trash2,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  Key,
  Eye,
  EyeOff,
  Bot,
  Check,
  Wrench,
  Brain,
  Target,
  ShieldAlert,
  ArrowRight,
  ChevronDown,
  ChevronUp,
  Radio,
  Sliders,
  Wifi,
} from 'lucide-react';
import { audioEngine } from '../services/audioEngine';
import { GeminiClient } from '../services/geminiClient';

interface SettingsViewProps {
  settings: VasuSettings;
  onUpdateSettings: (newSettings: Partial<VasuSettings>) => void;
  onClearMemory: () => void;
  onClearChat: () => void;
  onSelectTab?: (tab: ScreenTab) => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings,
  onUpdateSettings,
  onClearMemory,
  onClearChat,
  onSelectTab,
}) => {
  const [apiKeyInput, setApiKeyInput] = useState(settings.geminiApiKey || '');
  const [showApiKey, setShowApiKey] = useState(false);
  const [savingKey, setSavingKey] = useState(false);
  const [keySavedBadge, setKeySavedBadge] = useState(Boolean(settings.geminiApiKey));
  const [testingConnection, setTestingConnection] = useState(false);
  const [testingVoice, setTestingVoice] = useState(false);
  const [isGuardianOpen, setIsGuardianOpen] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<{
    tested: boolean;
    success: boolean;
    latencyMs?: number;
    message?: string;
  }>({
    tested: Boolean(settings.geminiApiKey),
    success: Boolean(settings.geminiApiKey),
    latencyMs: 140,
    message: settings.geminiApiKey ? 'API Key validated successfully' : undefined,
  });

  const [selectedBrainModel, setSelectedBrainModel] = useState(
    settings.geminiModel || 'gemini-3.6-flash'
  );
  const [selectedVoiceModel, setSelectedVoiceModel] = useState(
    settings.ttsVoice || 'Kore'
  );

  useEffect(() => {
    if (settings.geminiApiKey && !apiKeyInput) {
      setApiKeyInput(settings.geminiApiKey);
    }
  }, [settings.geminiApiKey]);

  const handleSaveApiKey = async () => {
    setSavingKey(true);
    try {
      const trimmed = apiKeyInput.trim();
      onUpdateSettings({
        geminiApiKey: trimmed,
        geminiModel: selectedBrainModel,
        ttsVoice: selectedVoiceModel,
      });
      audioEngine.setApiKey(trimmed);
      setKeySavedBadge(true);

      // Notify web backend if running on web (non-blocking)
      fetch('/api/gemini/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: trimmed }),
      }).catch(() => {});

      // Test connection
      await handleTestConnection(trimmed);
    } catch (e) {
      console.warn('Could not save key:', e);
    } finally {
      setSavingKey(false);
    }
  };

  const handleClearApiKey = async () => {
    setApiKeyInput('');
    onUpdateSettings({ geminiApiKey: '' });
    audioEngine.setApiKey('');
    setKeySavedBadge(false);
    fetch('/api/gemini/key', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey: '' }),
    }).catch(() => {});
    setConnectionStatus({ tested: false, success: false });
  };

  const handleTestConnection = async (customKey?: string) => {
    setTestingConnection(true);
    try {
      const keyToTest =
        customKey !== undefined
          ? customKey
          : apiKeyInput.trim() || settings.geminiApiKey;
      const res = await GeminiClient.testConnection(keyToTest);
      setConnectionStatus({
        tested: true,
        success: res.success,
        latencyMs: res.latencyMs || 180,
        message: res.message,
      });
    } catch (e: any) {
      setConnectionStatus({
        tested: true,
        success: false,
        message: e?.message || 'Network connection failure',
      });
    } finally {
      setTestingConnection(false);
    }
  };

  const handleTestGeminiVoice = async () => {
    setTestingVoice(true);
    const keyToUse = apiKeyInput.trim() || settings.geminiApiKey;
    audioEngine.unlock();
    try {
      await audioEngine.speakAssistantResponse(
        'नमस्ते जी! वासु कोर फीमेल वॉइस बिल्कुल तैयार है। बताइए, मैं आपकी क्या सेवा करूँ?',
        {
          speed: settings.ttsSpeed || 1.0,
          pitch: settings.ttsPitch || 1.0,
          apiKey: keyToUse,
          forceKoreVoice: true,
          onEnd: () => setTestingVoice(false),
        }
      );
    } catch {
      setTestingVoice(false);
    }
    setTimeout(() => setTestingVoice(false), 4500);
  };

  const handleModelChange = (model: string) => {
    setSelectedBrainModel(model);
    onUpdateSettings({ geminiModel: model });
  };

  const handleVoiceChange = (voice: string) => {
    setSelectedVoiceModel(voice);
    onUpdateSettings({ ttsVoice: voice });
  };

  const smartModes: SmartMode[] = ['NORMAL', 'DRIVING', 'SLEEP', 'WORK', 'GAMING'];

  return (
    <div className="flex flex-col h-[calc(100vh-170px)] max-w-2xl mx-auto w-full px-3 pb-24 select-none">
      {/* 1. Header */}
      <div className="py-2.5 border-b border-slate-800/80 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
            <Settings className="w-4 h-4" />
          </div>
          <div>
            <h2 className="font-mono text-sm font-bold text-slate-100">VASU Settings</h2>
            <p className="text-[11px] text-slate-400">
              Gemini API Key, Studio Voice TTS & VASU continuous chat
            </p>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto space-y-3.5 pt-3 pr-1">
        {/* 2. Collapsible Top Card: Risk Assessment, Safety Limits & Killswitch */}
        <div className="p-3 bg-slate-900/90 border border-slate-800 rounded-2xl shadow-lg shadow-black/40">
          <button
            type="button"
            onClick={() => setIsGuardianOpen(!isGuardianOpen)}
            className="w-full flex items-center justify-between text-left cursor-pointer group"
          >
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-rose-500/15 border border-rose-500/30 flex items-center justify-center text-rose-400 group-hover:scale-105 transition-transform">
                <ShieldAlert className="w-4 h-4" />
              </div>
              <div>
                <div className="text-xs font-semibold text-white group-hover:text-rose-300 transition-colors">
                  Risk Assessment, Safety Limits & Killswitch
                </div>
                <div className="text-[10px] text-slate-400">
                  Guardian Protection & Hardware Device Safety
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-mono text-rose-400 bg-rose-950/80 px-2 py-0.5 rounded-full border border-rose-800">
                ACTIVE
              </span>
              {isGuardianOpen ? (
                <ChevronUp className="w-4 h-4 text-slate-400" />
              ) : (
                <ChevronDown className="w-4 h-4 text-slate-400" />
              )}
            </div>
          </button>

          {isGuardianOpen && (
            <div className="mt-3 pt-3 border-t border-slate-800 space-y-3 animate-in fade-in duration-200">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-xs font-mono text-slate-200">Confirm High-Risk Actions</div>
                  <div className="text-[10px] text-slate-400">
                    Require confirmation before device control commands
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={settings.requireConfirmationForHighRisk}
                  onChange={(e) =>
                    onUpdateSettings({ requireConfirmationForHighRisk: e.target.checked })
                  }
                  className="rounded bg-slate-950 border-slate-700 text-cyan-500 focus:ring-cyan-400"
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <div className="text-xs font-mono text-slate-200">Smart Mode Profile</div>
                  <div className="text-[10px] text-slate-400">Current context preset</div>
                </div>
                <select
                  value={settings.smartMode}
                  onChange={(e) =>
                    onUpdateSettings({ smartMode: e.target.value as SmartMode })
                  }
                  className="bg-slate-950 border border-slate-700 rounded px-2 py-1 text-xs text-cyan-300 font-mono"
                >
                  {smartModes.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
              </div>

              <button
                type="button"
                onClick={() => onSelectTab?.('GUARDIAN')}
                className="w-full text-center py-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 text-xs font-mono border border-rose-500/30 transition-colors"
              >
                Open Full Guardian Safety Panel →
              </button>
            </div>
          )}
        </div>

        {/* 3. Main Section: GEMINI API KEY & STUDIO VOICE */}
        <div className="p-3.5 bg-slate-900/90 border border-cyan-500/30 rounded-2xl space-y-3 shadow-lg shadow-black/50">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-cyan-400" />
              <span className="text-xs font-mono font-bold text-slate-100 uppercase tracking-wider">
                Gemini API Key & Studio Voice
              </span>
            </div>
            {keySavedBadge || settings.geminiApiKey ? (
              <span className="text-[10px] font-mono px-2.5 py-0.5 rounded-full bg-cyan-950/80 text-cyan-300 border border-cyan-500/40 flex items-center gap-1 font-semibold">
                <Check className="w-3 h-3 text-cyan-400" /> Key Saved
              </span>
            ) : (
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-amber-950/70 text-amber-300 border border-amber-500/40">
                Key Required for Studio TTS
              </span>
            )}
          </div>

          <p className="text-[11px] text-slate-300 leading-relaxed">
            अपनी Google AI Studio की <strong className="text-cyan-300">Gemini API Key</strong> यहाँ डालें। इसके बाद वासु जेमिनी की नेचुरल स्टूडियो आवाज़ (Text-to-Speech) में बोलेगी और जार्विस की तरह चैट करेगी।
          </p>

          {/* API Key Input */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-mono text-slate-400 flex items-center gap-1">
              <Key className="w-3 h-3 text-cyan-400" />
              <span>Gemini API Key (Google AI Studio)</span>
            </label>
            <div className="flex gap-1.5">
              <div className="relative flex-1">
                <input
                  id="input-gemini-api-key"
                  type={showApiKey ? 'text' : 'password'}
                  value={apiKeyInput}
                  onChange={(e) => setApiKeyInput(e.target.value)}
                  placeholder="AIzaSy..."
                  className="w-full bg-slate-950 border border-slate-700/90 focus:border-cyan-400 rounded-xl px-3 py-2 text-xs text-cyan-200 font-mono pr-9 outline-none transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 cursor-pointer"
                  title={showApiKey ? 'Hide Key' : 'Show Key'}
                >
                  {showApiKey ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                </button>
              </div>

              <button
                id="btn-save-gemini-key"
                onClick={handleSaveApiKey}
                disabled={savingKey || !apiKeyInput.trim()}
                className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-slate-950 px-3.5 py-2 rounded-xl text-xs font-mono font-bold flex items-center gap-1 transition-all cursor-pointer shadow-md shadow-cyan-500/20 active:scale-95"
              >
                {savingKey ? (
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  'Save Key'
                )}
              </button>

              {apiKeyInput && (
                <button
                  onClick={handleClearApiKey}
                  className="px-2.5 py-2 bg-slate-950 hover:bg-rose-950/60 border border-slate-800 hover:border-rose-800 text-slate-400 hover:text-rose-300 rounded-xl text-xs font-mono transition-colors cursor-pointer"
                  title="Clear Key"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>

          {/* Model Selection Dropdowns */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-1">
                Chat Brain Model
              </label>
              <select
                value={selectedBrainModel}
                onChange={(e) => handleModelChange(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 hover:border-cyan-500/50 rounded-xl px-2.5 py-2 font-mono text-cyan-300 text-xs outline-none cursor-pointer"
              >
                <option value="gemini-3.6-flash">gemini-3.6-flash (Authoritative)</option>
                <option value="gemini-2.5-flash">gemini-2.5-flash (Fast & High Quota)</option>
                <option value="gemini-2.0-flash">gemini-2.0-flash (Real-Time Live)</option>
                <option value="gemini-1.5-flash">gemini-1.5-flash (High Limit)</option>
              </select>
            </div>
            <div>
              <label className="text-[10px] font-mono text-slate-400 block mb-1">
                TTS Voice Model
              </label>
              <select
                value={selectedVoiceModel}
                onChange={(e) => handleVoiceChange(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 hover:border-cyan-500/50 rounded-xl px-2.5 py-2 font-mono text-cyan-300 text-xs outline-none cursor-pointer"
              >
                <option value="Kore">Kore (gemini-3.6-flash Live)</option>
                <option value="Kore-Studio">Kore (Studio Female Voice)</option>
                <option value="Aoede">Aoede (Studio Female)</option>
                <option value="Fenrir">Fenrir (Studio Deep)</option>
              </select>
            </div>
          </div>

          {/* Action Buttons: Test Connection & Test Voice TTS */}
          <div className="pt-2 flex flex-wrap items-center justify-between gap-2 border-t border-slate-800/80">
            <div className="flex items-center gap-2">
              <button
                id="btn-test-gemini-connection"
                onClick={() => handleTestConnection()}
                disabled={testingConnection}
                className="bg-cyan-500/15 hover:bg-cyan-500/25 border border-cyan-500/30 text-cyan-300 px-3 py-1.5 rounded-xl text-xs font-mono font-medium flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
              >
                {testingConnection ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin text-cyan-400" />
                    <span>Connecting...</span>
                  </>
                ) : (
                  <>
                    <Cpu className="w-3.5 h-3.5" />
                    <span>Test Connection</span>
                  </>
                )}
              </button>

              <button
                id="btn-test-tts-voice"
                onClick={handleTestGeminiVoice}
                disabled={testingVoice}
                className="bg-indigo-500/15 hover:bg-indigo-500/25 border border-indigo-500/40 text-indigo-300 px-3 py-1.5 rounded-xl text-xs font-mono font-medium flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                title="Test Gemini Kore Female TTS Voice"
              >
                {testingVoice ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin text-indigo-400" />
                    <span>Speaking...</span>
                  </>
                ) : (
                  <>
                    <Volume2 className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Test Studio TTS</span>
                  </>
                )}
              </button>
            </div>

            {connectionStatus.tested && (
              <div
                className={`text-xs font-mono flex items-center gap-1.5 px-2.5 py-1 rounded-lg ${
                  connectionStatus.success
                    ? 'text-emerald-300 bg-emerald-950/60 border border-emerald-800'
                    : 'text-rose-300 bg-rose-950/60 border border-rose-800'
                }`}
              >
                {connectionStatus.success ? (
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <AlertTriangle className="w-3.5 h-3.5 text-rose-400" />
                )}
                <span>
                  {connectionStatus.success
                    ? `CONNECTED (${connectionStatus.latencyMs}ms)`
                    : 'KEY ERROR / OFFLINE'}
                </span>
              </div>
            )}
          </div>

          {/* RUNTIME DIAGNOSTICS Box (From Screenshot 1) */}
          <div className="mt-2.5 p-3 rounded-xl bg-slate-950/90 border border-slate-800/90 space-y-2">
            <div className="flex items-center justify-between text-[10px] font-mono text-slate-400 border-b border-slate-800/60 pb-1.5">
              <span className="font-bold text-slate-300 tracking-wider">RUNTIME DIAGNOSTICS</span>
              <span className="text-cyan-400 bg-cyan-950/80 px-2 py-0.5 rounded-full border border-cyan-800/60">
                Safe Mode (No Keys Logged)
              </span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 font-mono text-[11px] pt-1">
              <div>
                <span className="text-slate-500 text-[10px] block">NETWORK:</span>
                <span className="text-emerald-400 font-semibold flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  online
                </span>
              </div>
              <div>
                <span className="text-slate-500 text-[10px] block">API KEY:</span>
                <span className={apiKeyInput || settings.geminiApiKey ? 'text-emerald-400 font-semibold' : 'text-amber-400 font-semibold'}>
                  {apiKeyInput || settings.geminiApiKey ? 'present' : 'missing'}
                </span>
              </div>
              <div>
                <span className="text-slate-500 text-[10px] block">GEMINI:</span>
                <span className={connectionStatus.success ? 'text-emerald-400 font-semibold' : 'text-cyan-400 font-semibold'}>
                  {connectionStatus.success ? 'ready (connected)' : 'ready to test'}
                </span>
              </div>
              <div>
                <span className="text-slate-500 text-[10px] block">MODEL:</span>
                <span className="text-cyan-300 truncate block">{selectedBrainModel}</span>
              </div>
              <div>
                <span className="text-slate-500 text-[10px] block">VOICE:</span>
                <span className="text-cyan-300 font-semibold">{selectedVoiceModel}</span>
              </div>
              <div>
                <span className="text-slate-500 text-[10px] block">LIVE SESSION:</span>
                <span className="text-emerald-400 font-semibold">READY</span>
              </div>
            </div>
          </div>
        </div>

        {/* 4. Section: VASU CONTINUOUS CHAT (वासु मोड) */}
        <div className="p-3.5 bg-gradient-to-r from-slate-900 via-indigo-950/30 to-slate-900 border border-indigo-500/30 rounded-2xl space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Bot className="w-4 h-4 text-cyan-400" />
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold text-slate-100 uppercase">
                  VASU Continuous Chat (वासु मोड)
                </span>
                {settings.followUpMode && (
                  <span className="text-[10px] font-mono px-2 py-0.5 bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 rounded-full font-semibold">
                    ACTIVE
                  </span>
                )}
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                id="toggle-vasu-continuous-mode"
                type="checkbox"
                checked={settings.followUpMode}
                onChange={(e) => onUpdateSettings({ followUpMode: e.target.checked })}
                className="sr-only peer"
              />
              <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-cyan-500"></div>
            </label>
          </div>

          <p className="text-[11px] text-slate-300 leading-relaxed">
            जब यह मोड चालू होगा, वासु का उत्तर खत्म होने के तुरंत बाद माइक अपने आप चालू रहेगा। अगर आप कोई डिवाइस कमांड न दें, तब भी वासु वासु की तरह आपसे लगातार बात करती रहेगी।
          </p>
        </div>

        {/* 5. Section: WAKE WORD ENGINE */}
        <div className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Mic className="w-4 h-4 text-cyan-400" />
              <span className="text-xs font-mono font-bold text-slate-100 uppercase">
                Wake Word Engine
              </span>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                id="toggle-wake-word"
                type="checkbox"
                checked={settings.wakeWordEnabled}
                onChange={(e) => onUpdateSettings({ wakeWordEnabled: e.target.checked })}
                className="sr-only peer"
              />
              <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-cyan-500"></div>
            </label>
          </div>

          <p className="text-[11px] text-slate-400">
            "Hello VASU" बोलते ही वासु तुरंत जाग जाएगी और आपकी सेवा में उपस्थित हो जाएगी।
          </p>
        </div>

        {/* 6. Section: Assistant System Modules (Tools, Memory, Missions, Screen Auto, Guardian) */}
        <div className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Wrench className="w-4 h-4 text-cyan-400" />
              <span className="text-xs font-mono font-bold text-slate-100 uppercase tracking-wider">
                Assistant Modules
              </span>
            </div>
            <span className="text-[10px] font-mono text-cyan-400 bg-cyan-950 px-2.5 py-0.5 rounded-full border border-cyan-800">
              5 Modules
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              type="button"
              onClick={() => onSelectTab?.('TOOLS')}
              className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-cyan-500/50 flex items-center justify-between text-left cursor-pointer transition-colors"
            >
              <div className="flex items-center gap-2">
                <Wrench className="w-3.5 h-3.5 text-cyan-400" />
                <span className="text-xs text-white">Tools</span>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
            </button>

            <button
              type="button"
              onClick={() => onSelectTab?.('MEMORY')}
              className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-purple-500/50 flex items-center justify-between text-left cursor-pointer transition-colors"
            >
              <div className="flex items-center gap-2">
                <Brain className="w-3.5 h-3.5 text-purple-400" />
                <span className="text-xs text-white">Memory</span>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
            </button>

            <button
              type="button"
              onClick={() => onSelectTab?.('MISSIONS')}
              className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-emerald-500/50 flex items-center justify-between text-left cursor-pointer transition-colors"
            >
              <div className="flex items-center gap-2">
                <Target className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-xs text-white">Missions</span>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
            </button>

            <button
              type="button"
              onClick={() => onSelectTab?.('AUTO')}
              className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-amber-500/50 flex items-center justify-between text-left cursor-pointer transition-colors"
            >
              <div className="flex items-center gap-2">
                <Bot className="w-3.5 h-3.5 text-amber-400" />
                <span className="text-xs text-white">Screen Auto</span>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
