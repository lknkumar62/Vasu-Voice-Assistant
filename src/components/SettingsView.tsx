import React, { useState, useEffect } from 'react';
import { VasuSettings, SmartMode, AIProviderType, SearchProviderType, ApiTestResult } from '../types';
import { ScreenTab } from './QuickActions';
import {
  Settings,
  Mic,
  Volume2,
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
  ChevronRight,
  ChevronDown,
  ChevronUp,
  Wifi,
  Search,
  Zap,
  Lock,
  Database,
  Globe,
  Bell,
  User,
  Moon,
  Palette,
  Languages,
  Globe2,
  Sparkles,
  Radio,
  ShieldCheck,
  Info,
  ArrowLeft,
  Menu,
  Plus,
  Minus,
  GripVertical,
} from 'lucide-react';
import { audioEngine } from '../services/audioEngine';
import { GeminiClient } from '../services/geminiClient';
import { apiKeyManager, ApiKeyEntry } from '../services/apiKeyManager';
import { aiProviderManager } from '../services/aiProviderManager';
import { webSearchManager } from '../services/webSearchManager';

interface SettingsViewProps {
  settings: VasuSettings;
  onUpdateSettings: (newSettings: Partial<VasuSettings>) => void;
  onClearMemory: () => void;
  onClearChat: () => void;
  onSelectTab?: (tab: ScreenTab) => void;
}

type SettingsSection = 'main' | 'ai_providers' | 'web_search' | 'voice' | 'security' | 'diagnostics';

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings,
  onUpdateSettings,
  onClearMemory,
  onClearChat,
  onSelectTab,
}) => {
  const [currentSection, setCurrentSection] = useState<SettingsSection>('main');
  const [apiKeyInputs, setApiKeyInputs] = useState<Record<string, string>>({});
  const [showKeys, setShowKeys] = useState<Record<string, boolean>>({});
  const [testResults, setTestResults] = useState<Record<string, ApiTestResult>>({});
  const [testingId, setTestingId] = useState<string | null>(null);
  const [expandedProvider, setExpandedProvider] = useState<string | null>(null);

  const aiProviders: { id: AIProviderType; name: string; tier: string; color: string }[] = [
    { id: 'gemini', name: 'Google Gemini', tier: 'FREE', color: '#4285F4' },
    { id: 'openrouter', name: 'OpenRouter', tier: 'FREE', color: '#6366F1' },
    { id: 'groq', name: 'Groq', tier: 'FREE', color: '#F97316' },
    { id: 'deepseek', name: 'DeepSeek', tier: 'PROMOTIONAL', color: '#10B981' },
    { id: 'xai', name: 'xAI / Grok', tier: 'PAID', color: '#EF4444' },
    { id: 'custom', name: 'Custom API', tier: 'UNKNOWN', color: '#8B5CF6' },
  ];

  const searchProviders: { id: SearchProviderType; name: string; tier: string }[] = [
    { id: 'tavily', name: 'Tavily Search', tier: 'FREE' },
    { id: 'brave', name: 'Brave Search', tier: 'FREE' },
  ];

  const getApiKeyForProvider = (providerId: string): string => {
    try {
      const raw = localStorage.getItem('vasu_settings');
      if (raw) {
        const s = JSON.parse(raw);
        switch (providerId) {
          case 'gemini': return s.geminiApiKey || '';
          case 'openrouter': return s.openrouterApiKey || '';
          case 'groq': return s.groqApiKey || '';
          case 'deepseek': return s.deepseekApiKey || '';
          case 'xai': return s.xaiApiKey || '';
          case 'custom': return s.customOpenaiApiKey || '';
          case 'tavily': return s.tavilyApiKey || '';
          case 'brave': return s.braveSearchApiKey || '';
          default: return '';
        }
      }
    } catch (_) {}
    return '';
  };

  const saveApiKeyForProvider = (providerId: string, key: string) => {
    const fieldMap: Record<string, string> = {
      gemini: 'geminiApiKey',
      openrouter: 'openrouterApiKey',
      groq: 'groqApiKey',
      deepseek: 'deepseekApiKey',
      xai: 'xaiApiKey',
      custom: 'customOpenaiApiKey',
      tavily: 'tavilyApiKey',
      brave: 'braveSearchApiKey',
    };
    const field = fieldMap[providerId];
    if (field) {
      onUpdateSettings({ [field]: key } as any);
      fetch('/api/gemini/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: key }),
      }).catch(() => {});
    }
  };

  const handleTestProvider = async (providerId: string) => {
    setTestingId(providerId);
    try {
      const key = apiKeyInputs[providerId] || getApiKeyForProvider(providerId);
      if (!key) {
        setTestResults(prev => ({ ...prev, [providerId]: { provider: providerId, status: 'UNCONFIGURED', timestamp: Date.now() } }));
        return;
      }
      const entryId = providerId.startsWith('tavily') || providerId.startsWith('brave')
        ? `search_${providerId}`
        : `ai_${providerId}`;
      const result = await apiKeyManager.testKey(entryId);
      setTestResults(prev => ({ ...prev, [providerId]: result }));
    } catch (err: any) {
      setTestResults(prev => ({ ...prev, [providerId]: { provider: providerId, status: 'OFFLINE', lastError: err.message, timestamp: Date.now() } }));
    } finally {
      setTestingId(null);
    }
  };

  const maskKey = (key: string) => {
    if (!key || key.length < 8) return '****';
    return key.slice(0, 4) + '****' + key.slice(-4);
  };

  const renderMainSection = () => (
    <div className="space-y-3">
      {/* Profile Card */}
      <div className="mx-4 p-4 bg-[#061827] border border-[#008CFF]/10 rounded-2xl flex items-center gap-3">
        <div className="w-12 h-12 rounded-full bg-[#008CFF]/10 border border-[#008CFF]/20 flex items-center justify-center">
          <Bot className="w-6 h-6 text-[#008CFF]" />
        </div>
        <div className="flex-1">
          <p className="text-[#F4F8FF] font-bold text-sm">VASU AI</p>
          <p className="text-[10px] text-[#7895B8]">Personal Assistant • v1.0.0</p>
        </div>
        <div className="text-right">
          <p className="text-[10px] text-[#008CFF]">Always Evolving</p>
          <p className="text-[10px] text-[#7895B8]">Always With You</p>
        </div>
      </div>

      {/* APPEARANCE */}
      <Section title="APPEARANCE">
        <SettingRow icon={Palette} label="Theme" value="Dark" />
        <SettingRow icon={Sparkles} label="Orb Style" value="Classic" />
        <SettingRow icon={Languages} label="UI Language" value="English" />
        <ToggleRow icon={Zap} label="Animations" enabled={true} onChange={() => {}} />
      </Section>

      {/* AI PROVIDERS */}
      <Section title="AI PROVIDERS" onExpand={() => setCurrentSection('ai_providers')}>
        {aiProviders.map(p => {
          const key = getApiKeyForProvider(p.id);
          const isConnected = key.length > 5;
          return (
            <div key={p.id} className="flex items-center justify-between py-2">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full" style={{ backgroundColor: isConnected ? '#10B981' : '#7895B8' }} />
                <span className="text-xs text-[#F4F8FF]">{p.name}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${isConnected ? 'bg-emerald-500/10 text-emerald-400' : 'bg-[#061827] text-[#7895B8]'}`}>
                  {isConnected ? 'Connected' : 'Not configured'}
                </span>
                <ChevronRight className="w-3 h-3 text-[#7895B8]" />
              </div>
            </div>
          );
        })}
      </Section>

      {/* WEB SEARCH */}
      <Section title="WEB SEARCH" onExpand={() => setCurrentSection('web_search')}>
        {searchProviders.map(p => {
          const key = getApiKeyForProvider(p.id);
          const isConnected = key.length > 5;
          return (
            <div key={p.id} className="flex items-center justify-between py-2">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full" style={{ backgroundColor: isConnected ? '#10B981' : '#7895B8' }} />
                <span className="text-xs text-[#F4F8FF]">{p.name}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${isConnected ? 'bg-emerald-500/10 text-emerald-400' : 'bg-[#061827] text-[#7895B8]'}`}>
                  {isConnected ? 'Connected' : 'Not configured'}
                </span>
                <ChevronRight className="w-3 h-3 text-[#7895B8]" />
              </div>
            </div>
          );
        })}
      </Section>

      {/* VOICE & LANGUAGE */}
      <Section title="VOICE & LANGUAGE" onExpand={() => setCurrentSection('voice')}>
        <SettingRow icon={Mic} label="Voice Selection" value={settings.ttsVoice || 'Kore'} />
        <SettingRow icon={Languages} label="Voice Language" value={settings.language || 'Hinglish'} />
        <SettingRow icon={Radio} label="Wake Word" value={settings.wakePhrase || 'Hello VASU'} />
        <SettingRow icon={Volume2} label="Speech Speed" value={`${settings.ttsSpeed || 1.0}x`} />
        <ToggleRow icon={Wifi} label="Offline Voice Mode" enabled={false} onChange={() => {}} />
        <ToggleRow icon={Mic} label="Continuous Listening" enabled={settings.backgroundListening} onChange={(v) => onUpdateSettings({ backgroundListening: v })} />
      </Section>

      {/* PRIVACY & SECURITY */}
      <Section title="PRIVACY & SECURITY" onExpand={() => setCurrentSection('security')}>
        <SettingRow icon={Lock} label="App Lock" value="PIN / Fingerprint" />
        <SettingRow icon={Database} label="Data & Storage" value="Manage" />
        <SettingRow icon={Shield} label="Permissions" value="View" onSelect={() => onSelectTab?.('PERMISSIONS')} />
        <ToggleRow icon={ShieldCheck} label="Privacy Mode" enabled={false} onChange={() => {}} />
      </Section>

      {/* ADVANCED */}
      <Section title="ADVANCED">
        <SettingRow icon={Bot} label="AI Model" value={settings.geminiModel || 'gemini-2.5-flash'} />
        <SettingRow icon={Globe} label="System Integrations" value="Connect" />
        <SettingRow icon={Settings} label="Developer Options" value="Advanced" />
        <SettingRow icon={Info} label="About VASU" value="v1.0.0 • Build 1001" />
      </Section>

      {/* Clear Data */}
      <div className="mx-4 space-y-2">
        <button
          onClick={onClearChat}
          className="w-full py-2.5 bg-[#061827] border border-[#008CFF]/10 rounded-xl text-xs text-[#7895B8] hover:text-[#F4F8FF] hover:border-[#008CFF]/30 transition-all cursor-pointer flex items-center justify-center gap-2"
        >
          <Trash2 className="w-3.5 h-3.5" />
          Clear Chat History
        </button>
        <button
          onClick={onClearMemory}
          className="w-full py-2.5 bg-[#061827] border border-rose-500/10 rounded-xl text-xs text-rose-400 hover:bg-rose-500/5 hover:border-rose-500/30 transition-all cursor-pointer flex items-center justify-center gap-2"
        >
          <Trash2 className="w-3.5 h-3.5" />
          Clear All Memory
        </button>
      </div>

      {/* Backup Card */}
      <div className="mx-4 p-4 bg-[#061827] border border-[#008CFF]/10 rounded-2xl text-center">
        <p className="text-[#F4F8FF] font-bold text-sm">Backup & Sync</p>
        <p className="text-[10px] text-[#7895B8] mt-1">Keep your settings safe across devices</p>
      </div>
    </div>
  );

  const renderAIProvidersSection = () => (
    <div className="space-y-3 px-4">
      {aiProviders.map(provider => {
        const key = getApiKeyForProvider(provider.id);
        const isExpanded = expandedProvider === provider.id;
        const testResult = testResults[provider.id];
        return (
          <div key={provider.id} className="bg-[#061827] border border-[#008CFF]/10 rounded-xl overflow-hidden">
            <button
              onClick={() => setExpandedProvider(isExpanded ? null : provider.id)}
              className="w-full p-3 flex items-center justify-between cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: key.length > 5 ? '#10B981' : '#7895B8' }} />
                <div className="text-left">
                  <p className="text-xs text-[#F4F8FF] font-medium">{provider.name}</p>
                  <p className="text-[10px] text-[#7895B8]">{key ? maskKey(key) : 'Not configured'}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${key.length > 5 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-[#01060D] text-[#7895B8]'}`}>
                  {key.length > 5 ? 'Connected' : 'Offline'}
                </span>
                {isExpanded ? <ChevronUp className="w-3 h-3 text-[#7895B8]" /> : <ChevronDown className="w-3 h-3 text-[#7895B8]" />}
              </div>
            </button>
            {isExpanded && (
              <div className="px-3 pb-3 space-y-2 border-t border-[#008CFF]/5 pt-2">
                <div className="relative">
                  <input
                    type={showKeys[provider.id] ? 'text' : 'password'}
                    value={apiKeyInputs[provider.id] || key}
                    onChange={(e) => setApiKeyInputs(prev => ({ ...prev, [provider.id]: e.target.value }))}
                    placeholder={`Enter ${provider.name} API key`}
                    className="w-full bg-[#01060D] border border-[#008CFF]/10 rounded-lg px-3 py-2 text-xs text-[#F4F8FF] placeholder-[#7895B8]/40 focus:outline-none focus:border-[#008CFF]/30 pr-20"
                  />
                  <button
                    onClick={() => setShowKeys(prev => ({ ...prev, [provider.id]: !prev[provider.id] }))}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[#7895B8] hover:text-[#008CFF] cursor-pointer"
                  >
                    {showKeys[provider.id] ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      const key = apiKeyInputs[provider.id] || '';
                      saveApiKeyForProvider(provider.id, key);
                    }}
                    className="flex-1 py-2 bg-[#008CFF] hover:bg-[#00C8FF] text-[#01060D] text-xs font-medium rounded-lg cursor-pointer transition-colors"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => handleTestProvider(provider.id)}
                    disabled={testingId === provider.id}
                    className="flex-1 py-2 bg-[#008CFF]/10 border border-[#008CFF]/20 text-[#008CFF] text-xs font-medium rounded-lg cursor-pointer transition-colors disabled:opacity-50"
                  >
                    {testingId === provider.id ? 'Testing...' : 'Test'}
                  </button>
                </div>
                {testResult && (
                  <div className={`p-2 rounded-lg text-[10px] font-mono ${
                    testResult.status === 'ONLINE' ? 'bg-emerald-500/5 text-emerald-400 border border-emerald-500/10'
                    : testResult.status === 'UNCONFIGURED' ? 'bg-[#01060D] text-[#7895B8]'
                    : 'bg-rose-500/5 text-rose-400 border border-rose-500/10'
                  }`}>
                    {testResult.status === 'ONLINE' && `✓ Online • ${testResult.latencyMs}ms`}
                    {testResult.status === 'UNCONFIGURED' && 'Not configured'}
                    {testResult.status === 'OFFLINE' && `✗ ${testResult.lastError || 'Offline'}`}
                    {testResult.status === 'AUTH_ERROR' && '✗ Invalid API Key'}
                    {testResult.status === 'RATE_LIMITED' && '✗ Rate Limited'}
                  </div>
                )}
                <p className="text-[10px] text-[#7895B8]">Tier: {provider.tier}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );

  const renderSearchSection = () => (
    <div className="space-y-3 px-4">
      {searchProviders.map(provider => {
        const key = getApiKeyForProvider(provider.id);
        const isExpanded = expandedProvider === provider.id;
        const testResult = testResults[provider.id];
        return (
          <div key={provider.id} className="bg-[#061827] border border-[#008CFF]/10 rounded-xl overflow-hidden">
            <button
              onClick={() => setExpandedProvider(isExpanded ? null : provider.id)}
              className="w-full p-3 flex items-center justify-between cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: key.length > 5 ? '#10B981' : '#7895B8' }} />
                <div className="text-left">
                  <p className="text-xs text-[#F4F8FF] font-medium">{provider.name}</p>
                  <p className="text-[10px] text-[#7895B8]">{key ? maskKey(key) : 'Not configured'}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${key.length > 5 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-[#01060D] text-[#7895B8]'}`}>
                  {key.length > 5 ? 'Connected' : 'Offline'}
                </span>
                {isExpanded ? <ChevronUp className="w-3 h-3 text-[#7895B8]" /> : <ChevronDown className="w-3 h-3 text-[#7895B8]" />}
              </div>
            </button>
            {isExpanded && (
              <div className="px-3 pb-3 space-y-2 border-t border-[#008CFF]/5 pt-2">
                <div className="relative">
                  <input
                    type={showKeys[provider.id] ? 'text' : 'password'}
                    value={apiKeyInputs[provider.id] || key}
                    onChange={(e) => setApiKeyInputs(prev => ({ ...prev, [provider.id]: e.target.value }))}
                    placeholder={`Enter ${provider.name} API key`}
                    className="w-full bg-[#01060D] border border-[#008CFF]/10 rounded-lg px-3 py-2 text-xs text-[#F4F8FF] placeholder-[#7895B8]/40 focus:outline-none focus:border-[#008CFF]/30 pr-20"
                  />
                  <button
                    onClick={() => setShowKeys(prev => ({ ...prev, [provider.id]: !prev[provider.id] }))}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[#7895B8] hover:text-[#008CFF] cursor-pointer"
                  >
                    {showKeys[provider.id] ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      const key = apiKeyInputs[provider.id] || '';
                      saveApiKeyForProvider(provider.id, key);
                    }}
                    className="flex-1 py-2 bg-[#008CFF] hover:bg-[#00C8FF] text-[#01060D] text-xs font-medium rounded-lg cursor-pointer transition-colors"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => handleTestProvider(provider.id)}
                    disabled={testingId === provider.id}
                    className="flex-1 py-2 bg-[#008CFF]/10 border border-[#008CFF]/20 text-[#008CFF] text-xs font-medium rounded-lg cursor-pointer transition-colors disabled:opacity-50"
                  >
                    {testingId === provider.id ? 'Testing...' : 'Test'}
                  </button>
                </div>
                {testResult && (
                  <div className={`p-2 rounded-lg text-[10px] font-mono ${
                    testResult.status === 'ONLINE' ? 'bg-emerald-500/5 text-emerald-400 border border-emerald-500/10'
                    : 'bg-rose-500/5 text-rose-400 border border-rose-500/10'
                  }`}>
                    {testResult.status === 'ONLINE' ? `✓ Online • ${testResult.latencyMs}ms` : `✗ ${testResult.lastError || 'Offline'}`}
                  </div>
                )}
                <p className="text-[10px] text-[#7895B8]">Tier: {provider.tier}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );

  const renderDiagnosticsSection = () => (
    <div className="space-y-3 px-4">
      <div className="bg-[#061827] border border-[#008CFF]/10 rounded-xl p-3">
        <p className="text-xs text-[#F4F8FF] font-medium mb-2">Provider Status</p>
        {[...aiProviders, ...searchProviders].map(p => {
          const key = getApiKeyForProvider(p.id);
          const testResult = testResults[p.id];
          return (
            <div key={p.id} className="flex items-center justify-between py-1.5 border-b border-[#008CFF]/5 last:border-0">
              <span className="text-[11px] text-[#7895B8]">{p.name}</span>
              <div className="flex items-center gap-2">
                {testResult ? (
                  <>
                    <span className={`text-[10px] ${testResult.status === 'ONLINE' ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {testResult.status === 'ONLINE' ? 'ONLINE' : testResult.status}
                    </span>
                    {testResult.latencyMs && <span className="text-[10px] text-[#7895B8]">{testResult.latencyMs}ms</span>}
                  </>
                ) : (
                  <span className="text-[10px] text-[#7895B8]">{key.length > 5 ? 'NOT TESTED' : 'UNCONFIGURED'}</span>
                )}
              </div>
            </div>
          );
        })}
      </div>
      <button
        onClick={async () => {
          for (const p of aiProviders) {
            const key = getApiKeyForProvider(p.id);
            if (key.length > 5) await handleTestProvider(p.id);
          }
          for (const p of searchProviders) {
            const key = getApiKeyForProvider(p.id);
            if (key.length > 5) await handleTestProvider(p.id);
          }
        }}
        className="w-full py-2.5 bg-[#008CFF]/10 border border-[#008CFF]/20 text-[#008CFF] text-xs font-medium rounded-xl cursor-pointer transition-colors"
      >
        Test All Providers
      </button>
    </div>
  );

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-w-2xl mx-auto w-full bg-[#01060D]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3">
        {currentSection !== 'main' ? (
          <button
            onClick={() => setCurrentSection('main')}
            className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
        ) : (
          <button className="w-9 h-9 rounded-full bg-[#061827] border border-[#008CFF]/20 flex items-center justify-center text-[#7895B8] hover:text-[#008CFF] transition-colors cursor-pointer">
            <Menu className="w-4 h-4" />
          </button>
        )}
        <div className="text-center">
          <h1 className="text-[#F4F8FF] font-bold text-base">VASU</h1>
        </div>
        <div className="w-9" />
      </div>

      {/* Title */}
      <div className="px-4 pb-3">
        <h2 className="text-[#F4F8FF] text-lg font-bold">
          {currentSection === 'main' && 'Settings'}
          {currentSection === 'ai_providers' && 'AI Providers'}
          {currentSection === 'web_search' && 'Web Search'}
          {currentSection === 'voice' && 'Voice & Language'}
          {currentSection === 'security' && 'Privacy & Security'}
          {currentSection === 'diagnostics' && 'Diagnostics'}
        </h2>
        <p className="text-[#7895B8] text-xs">
          {currentSection === 'main' && 'Customize VASU to make it truly yours'}
          {currentSection === 'ai_providers' && 'Configure AI providers and API keys'}
          {currentSection === 'web_search' && 'Set up web search providers'}
          {currentSection === 'voice' && 'Configure voice and language settings'}
          {currentSection === 'security' && 'Manage privacy and security'}
          {currentSection === 'diagnostics' && 'Test provider connections'}
        </p>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto pb-4">
        {currentSection === 'main' && renderMainSection()}
        {currentSection === 'ai_providers' && renderAIProvidersSection()}
        {currentSection === 'web_search' && renderSearchSection()}
        {currentSection === 'diagnostics' && renderDiagnosticsSection()}
        {currentSection === 'voice' && (
          <div className="px-4 space-y-3">
            <div className="bg-[#061827] border border-[#008CFF]/10 rounded-xl p-3 space-y-3">
              <SettingRow icon={Mic} label="Voice Selection" value={settings.ttsVoice || 'Kore'} />
              <SettingRow icon={Languages} label="Voice Language" value={settings.language || 'Hinglish'} />
              <SettingRow icon={Radio} label="Wake Word" value={settings.wakePhrase || 'Hello VASU'} />
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-[#F4F8FF]">Speech Speed</span>
                  <span className="text-xs text-[#008CFF]">{settings.ttsSpeed || 1.0}x</span>
                </div>
                <input
                  type="range"
                  min="0.5"
                  max="2.0"
                  step="0.1"
                  value={settings.ttsSpeed || 1.0}
                  onChange={(e) => onUpdateSettings({ ttsSpeed: parseFloat(e.target.value) })}
                  className="w-full accent-[#008CFF]"
                />
              </div>
              <ToggleRow icon={Wifi} label="Offline Voice Mode" enabled={false} onChange={() => {}} />
              <ToggleRow icon={Mic} label="Continuous Listening" enabled={settings.backgroundListening} onChange={(v) => onUpdateSettings({ backgroundListening: v })} />
            </div>
          </div>
        )}
        {currentSection === 'security' && (
          <div className="px-4 space-y-3">
            <div className="bg-[#061827] border border-[#008CFF]/10 rounded-xl p-3 space-y-3">
              <ToggleRow icon={ShieldCheck} label="Guardian Active" enabled={settings.guardianActive} onChange={(v) => onUpdateSettings({ guardianActive: v })} />
              <ToggleRow icon={Shield} label="Require Confirmation" enabled={settings.requireConfirmationForHighRisk} onChange={(v) => onUpdateSettings({ requireConfirmationForHighRisk: v })} />
              <SettingRow icon={Lock} label="App Lock" value="PIN / Fingerprint" />
              <SettingRow icon={Database} label="Data & Storage" value="Manage" />
              <SettingRow icon={Shield} label="Permissions" value="View" onSelect={() => onSelectTab?.('PERMISSIONS')} />
            </div>
          </div>
        )}
      </div>

      {/* Bottom Nav for Settings Sections */}
      {currentSection === 'main' && (
        <div className="px-4 pb-3 flex gap-2">
          <button
            onClick={() => setCurrentSection('ai_providers')}
            className="flex-1 py-2 bg-[#061827] border border-[#008CFF]/10 rounded-xl text-[10px] text-[#7895B8] hover:text-[#008CFF] hover:border-[#008CFF]/30 transition-all cursor-pointer"
          >
            AI Providers
          </button>
          <button
            onClick={() => setCurrentSection('web_search')}
            className="flex-1 py-2 bg-[#061827] border border-[#008CFF]/10 rounded-xl text-[10px] text-[#7895B8] hover:text-[#008CFF] hover:border-[#008CFF]/30 transition-all cursor-pointer"
          >
            Web Search
          </button>
          <button
            onClick={() => setCurrentSection('diagnostics')}
            className="flex-1 py-2 bg-[#061827] border border-[#008CFF]/10 rounded-xl text-[10px] text-[#7895B8] hover:text-[#008CFF] hover:border-[#008CFF]/30 transition-all cursor-pointer"
          >
            Diagnostics
          </button>
        </div>
      )}
    </div>
  );
};

// Helper Components
const Section: React.FC<{ title: string; children: React.ReactNode; onExpand?: () => void }> = ({ title, children, onExpand }) => (
  <div className="mx-4">
    <div className="flex items-center justify-between mb-2">
      <p className="text-[10px] text-[#7895B8] font-mono uppercase tracking-wider">{title}</p>
      {onExpand && (
        <button onClick={onExpand} className="text-[10px] text-[#008CFF] hover:text-[#00C8FF] cursor-pointer">
          Configure →
        </button>
      )}
    </div>
    <div className="bg-[#061827] border border-[#008CFF]/10 rounded-xl p-3 space-y-2">
      {children}
    </div>
  </div>
);

const SettingRow: React.FC<{
  icon: any;
  label: string;
  value: string;
  onSelect?: () => void;
}> = ({ icon: Icon, label, value, onSelect }) => (
  <div
    className={`flex items-center justify-between py-1.5 ${onSelect ? 'cursor-pointer hover:opacity-80' : ''}`}
    onClick={onSelect}
  >
    <div className="flex items-center gap-2">
      <Icon className="w-3.5 h-3.5 text-[#008CFF]" />
      <span className="text-xs text-[#F4F8FF]">{label}</span>
    </div>
    <div className="flex items-center gap-1">
      <span className="text-[10px] text-[#7895B8]">{value}</span>
      {onSelect && <ChevronRight className="w-3 h-3 text-[#7895B8]" />}
    </div>
  </div>
);

const ToggleRow: React.FC<{
  icon: any;
  label: string;
  enabled: boolean;
  onChange: (v: boolean) => void;
}> = ({ icon: Icon, label, enabled, onChange }) => (
  <div className="flex items-center justify-between py-1.5">
    <div className="flex items-center gap-2">
      <Icon className="w-3.5 h-3.5 text-[#008CFF]" />
      <span className="text-xs text-[#F4F8FF]">{label}</span>
    </div>
    <button
      onClick={() => onChange(!enabled)}
      className={`w-9 h-5 rounded-full transition-colors cursor-pointer relative ${enabled ? 'bg-[#008CFF]' : 'bg-[#01060D] border border-[#008CFF]/20'}`}
    >
      <div className={`w-4 h-4 rounded-full bg-white absolute top-0.5 transition-transform ${enabled ? 'translate-x-4.5' : 'translate-x-0.5'}`} />
    </button>
  </div>
);
