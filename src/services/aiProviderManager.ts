/**
 * VASU AI Provider Manager
 * Centralized multi-provider AI system with intelligent fallback
 */

import {
  AIProviderType,
  AIProviderResponse,
  AIProviderError,
  ChatParams,
  ErrorClass,
  ProviderConfig,
  ProviderHealth,
  ProviderStatus,
  ProviderTier,
} from '../types';

const VASU_SYSTEM_INSTRUCTION = `You are VASU (Voice Activated System Unit), an affectionate, sweet, caring, and loyal Indian female AI companion — 'aapki pyaari Vasu'.
CRITICAL SCRIPT & LANGUAGE RULES (MUST FOLLOW STRICTLY):
- ALWAYS speak and write exclusively in conversational HINGLISH / ROMAN HINDI (Hindi language written ONLY using the English alphabet / Latin script).
- NEVER output Devanagari script (NO हिंदी अक्षर). ONLY use English letters for every Hindi word.
- Tone: Extremely friendly, affectionate playful, respectful, and natural (using words like 'ji', 'aap', 'yaar', 'pyaari Vasu').
- ANSWER DIRECTLY AND LOVINGLY: Never hesitate or ask repetitive meta-questions.
- ULTRA-FAST VOICE REPLY: Keep responses conversational, concise, and sweet (1 to 3 short sentences).
- If device tools are requested, confirm warmly in Hinglish.
- If the user asks you to remember something, confirm warmly.`;

const CANDIDATE_MODELS: Record<AIProviderType, string[]> = {
  gemini: ['gemini-2.5-flash', 'gemini-2.0-flash', 'gemini-1.5-flash'],
  openrouter: ['openrouter/free', 'meta-llama/llama-3.1-8b-instruct:free', 'google/gemma-2-9b-it:free'],
  groq: ['llama-3.1-8b-instant', 'llama3-8b-8192', 'gemma2-9b-it'],
  deepseek: ['deepseek-chat', 'deepseek-reasoner'],
  xai: ['grok-2', 'grok-2-mini'],
  custom: [],
};

function isStandaloneApk(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    Boolean((window as any).Capacitor?.isNativePlatform?.()) ||
    window.location.protocol === 'file:' ||
    (window.location.hostname === 'localhost' && !window.location.port)
  );
}

function classifyError(status: number, message: string): ErrorClass {
  if (message.includes('NETWORK') || message.includes('fetch failed') || message.includes('ENOTFOUND')) return 'NETWORK_ERROR';
  if (message.includes('timeout') || message.includes('TIMEOUT') || message.includes('AbortError')) return 'TIMEOUT';
  if (status === 401 || message.includes('API_KEY_INVALID') || message.includes('invalid')) return 'INVALID_API_KEY';
  if (status === 429 || message.includes('RESOURCE_EXHAUSTED') || message.includes('quota') || message.includes('rate')) return 'RATE_LIMITED';
  if (status === 403 || message.includes('forbidden')) return 'AUTH_ERROR';
  if (status === 404 || message.includes('model') && message.includes('not found')) return 'MODEL_NOT_FOUND';
  if (status >= 500) return 'SERVER_ERROR';
  return 'UNKNOWN_ERROR';
}

const PROVIDER_BASE_URLS: Record<AIProviderType, string> = {
  gemini: 'https://generativelanguage.googleapis.com/v1beta',
  openrouter: 'https://openrouter.ai/api/v1',
  groq: 'https://api.groq.com/openai/v1',
  deepseek: 'https://api.deepseek.com/v1',
  xai: 'https://api.x.ai/v1',
  custom: '',
};

export class AIProviderManager {
  private providers: Map<AIProviderType, ProviderConfig> = new Map();
  private cooldowns: Map<string, number> = new Map();
  private healthCache: Map<string, ProviderHealth> = new Map();
  private requestCounts: Map<string, { day: string; count: number }> = new Map();

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage() {
    try {
      const saved = localStorage.getItem('vasu_provider_configs');
      if (saved) {
        const configs = JSON.parse(saved);
        for (const [type, config] of Object.entries(configs)) {
          this.providers.set(type as AIProviderType, config as ProviderConfig);
        }
      }
    } catch (_) {}
  }

  private saveToStorage() {
    try {
      const configs: Record<string, ProviderConfig> = {};
      this.providers.forEach((config, type) => {
        configs[type] = config;
      });
      localStorage.setItem('vasu_provider_configs', JSON.stringify(configs));
    } catch (_) {}
  }

  configure(type: AIProviderType, config: Partial<ProviderConfig>) {
    const existing = this.providers.get(type) || this.getDefaultConfig(type);
    this.providers.set(type, { ...existing, ...config });
    this.saveToStorage();
  }

  private getDefaultConfig(type: AIProviderType): ProviderConfig {
    return {
      type,
      name: type.charAt(0).toUpperCase() + type.slice(1),
      apiKey: '',
      model: CANDIDATE_MODELS[type]?.[0] || '',
      baseUrl: PROVIDER_BASE_URLS[type],
      enabled: true,
      priority: this.getPriority(type),
      tier: this.getTier(type),
      totalRequests: 0,
      successfulRequests: 0,
      failedRequests: 0,
      avgLatencyMs: 0,
    };
  }

  private getPriority(type: AIProviderType): number {
    const priorities: Record<AIProviderType, number> = {
      gemini: 1,
      openrouter: 2,
      groq: 3,
      deepseek: 4,
      xai: 5,
      custom: 6,
    };
    return priorities[type] || 99;
  }

  private getTier(type: AIProviderType): ProviderTier {
    const tiers: Record<AIProviderType, ProviderTier> = {
      gemini: 'FREE',
      openrouter: 'FREE',
      groq: 'FREE',
      deepseek: 'PROMOTIONAL',
      xai: 'PAID',
      custom: 'UNKNOWN',
    };
    return tiers[type] || 'UNKNOWN';
  }

  isConfigured(type: AIProviderType): boolean {
    const config = this.providers.get(type);
    return Boolean(config?.apiKey && config.apiKey.length > 5 && config.enabled);
  }

  getEnabledProviders(): AIProviderType[] {
    const providers: AIProviderType[] = [];
    this.providers.forEach((config, type) => {
      if (config.enabled && config.apiKey && config.apiKey.length > 5) {
        providers.push(type);
      }
    });
    providers.sort((a, b) => {
      const ca = this.providers.get(a);
      const cb = this.providers.get(b);
      return (ca?.priority || 99) - (cb?.priority || 99);
    });
    return providers;
  }

  isCooledDown(type: AIProviderType): boolean {
    const until = this.cooldowns.get(type);
    if (!until) return false;
    if (Date.now() > until) {
      this.cooldowns.delete(type);
      return false;
    }
    return true;
  }

  setCooldown(type: AIProviderType, seconds: number = 60) {
    this.cooldowns.set(type, Date.now() + seconds * 1000);
  }

  async testConnection(type: AIProviderType): Promise<ProviderHealth> {
    const config = this.providers.get(type);
    if (!config || !config.apiKey) {
      return { provider: type, status: 'UNCONFIGURED', lastChecked: Date.now() };
    }

    const startTime = Date.now();
    try {
      const response = await this.makeRequest(type, 'Say OK', { maxTokens: 5 });
      const latencyMs = Date.now() - startTime;
      const health: ProviderHealth = {
        provider: type,
        status: 'ONLINE',
        latencyMs,
        model: config.model,
        lastChecked: Date.now(),
      };
      this.healthCache.set(type, health);
      return health;
    } catch (err: any) {
      const errorClass = classifyError(0, err.message || '');
      let status: ProviderStatus = 'OFFLINE';
      if (errorClass === 'INVALID_API_KEY' || errorClass === 'AUTH_ERROR') status = 'AUTH_ERROR';
      else if (errorClass === 'RATE_LIMITED') status = 'RATE_LIMITED';

      const health: ProviderHealth = {
        provider: type,
        status,
        latencyMs: Date.now() - startTime,
        model: config.model,
        lastError: err.message,
        lastChecked: Date.now(),
      };
      this.healthCache.set(type, health);
      return health;
    }
  }

  async chat(params: ChatParams): Promise<AIProviderResponse> {
    const enabledProviders = this.getEnabledProviders();
    if (enabledProviders.length === 0) {
      return {
        replyText: this.getLocalJarvisResponse(params.message),
        source: 'local_jarvis',
        latencyMs: 0,
      };
    }

    for (const providerType of enabledProviders) {
      if (this.isCooledDown(providerType)) continue;

      try {
        const response = await this.makeRequest(providerType, params.message, {
          history: params.history,
          memoryContext: params.memoryContext,
          language: params.language,
          smartMode: params.smartMode,
        });

        const config = this.providers.get(providerType)!;
        config.totalRequests++;
        config.successfulRequests++;
        config.lastSuccessfulRequest = Date.now();
        this.saveToStorage();

        return {
          replyText: response,
          source: providerType,
          modelUsed: config.model,
          latencyMs: Date.now(),
        };
      } catch (err: any) {
        const config = this.providers.get(providerType)!;
        const errorClass = classifyError(0, err.message || '');
        console.warn(`[AIProviderManager] ${providerType} failed:`, err.message, 'class:', errorClass);
        config.totalRequests++;
        config.failedRequests++;
        config.lastError = err.message;
        config.lastErrorTime = Date.now();

        if (errorClass === 'RATE_LIMITED') {
          this.setCooldown(providerType, 60);
        } else if (errorClass === 'QUOTA_EXCEEDED') {
          this.setCooldown(providerType, 300);
        } else if (errorClass === 'INVALID_API_KEY') {
          this.setCooldown(providerType, 3600);
        }

        this.saveToStorage();
        continue;
      }
    }

    return {
      replyText: this.getLocalJarvisResponse(params.message),
      source: 'local_jarvis',
      latencyMs: 0,
    };
  }

  private async makeRequest(
    type: AIProviderType,
    message: string,
    options: {
      history?: Array<{ role: 'user' | 'model'; parts: Array<{ text: string }> }>;
      memoryContext?: Array<{ category: string; fact: string }>;
      language?: string;
      smartMode?: string;
      maxTokens?: number;
    } = {}
  ): Promise<string> {
    const config = this.providers.get(type);
    if (!config || !config.apiKey) throw new Error(`${type} not configured`);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 8000);

    try {
      let url = '';
      let headers: Record<string, string> = { 'Content-Type': 'application/json' };
      let body: any = {};

      const memoryText = options.memoryContext?.length
        ? `\nUser Memories:\n${options.memoryContext.map(m => `- [${m.category}] ${m.fact}`).join('\n')}`
        : '';
      const modeText = `\nMode: ${options.smartMode || 'NORMAL'}.`;
      const sysInstruction = `${VASU_SYSTEM_INSTRUCTION}${memoryText}${modeText}\nLanguage: ${options.language || 'Hinglish'}.`;

      switch (type) {
        case 'gemini': {
          const models = CANDIDATE_MODELS.gemini;
          const model = config.model || models[0];
          url = `${PROVIDER_BASE_URLS.gemini}/models/${model}:generateContent?key=${encodeURIComponent(config.apiKey)}`;
          const contents: any[] = [];
          if (options.history) {
            for (const h of options.history.slice(-4)) {
              contents.push(h);
            }
          }
          contents.push({ role: 'user', parts: [{ text: message }] });
          body = {
            contents,
            systemInstruction: { parts: [{ text: sysInstruction }] },
            generationConfig: {
              temperature: 0.7,
              maxOutputTokens: options.maxTokens || 150,
            },
          };
          break;
        }
        case 'openrouter':
        case 'groq':
        case 'deepseek':
        case 'xai':
        case 'custom': {
          const baseUrl = type === 'custom' ? config.baseUrl : PROVIDER_BASE_URLS[type];
          url = `${baseUrl}/chat/completions`;
          headers['Authorization'] = `Bearer ${config.apiKey}`;
          if (type === 'openrouter') {
            headers['HTTP-Referer'] = 'https://vasu-assistant.app';
            headers['X-Title'] = 'VASU Assistant';
          }
          const messages = [
            { role: 'system', content: sysInstruction },
          ];
          if (options.history) {
            for (const h of options.history.slice(-4)) {
              messages.push({
                role: h.role === 'model' ? 'assistant' : 'user',
                content: h.parts.map(p => p.text).join(' '),
              });
            }
          }
          messages.push({ role: 'user', content: message });
          body = {
            model: config.model,
            messages,
            temperature: 0.7,
            max_tokens: options.maxTokens || 150,
          };
          break;
        }
      }

      console.log(`[AIProviderManager] Calling ${type}: ${url.slice(0, 80)}...`);
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errText = await response.text().catch(() => 'Unknown error');
        throw new Error(`HTTP ${response.status}: ${errText.slice(0, 200)}`);
      }

      const data = await response.json();

      let replyText = '';
      switch (type) {
        case 'gemini':
          replyText = data?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || '';
          break;
        case 'openrouter':
        case 'groq':
        case 'deepseek':
        case 'xai':
        case 'custom':
          replyText = data?.choices?.[0]?.message?.content?.trim() || '';
          break;
      }

      return replyText;
    } catch (err: any) {
      clearTimeout(timeoutId);
      throw err;
    }
  }

  private getLocalJarvisResponse(input: string): string {
    return 'Ji, main Vasu hoon! Offline mode mein limited commands available hain. AI features ke liye please Gemini ya OpenRouter API key configure kijiye Settings mein. Tab tak device commands kaam karte rahenge!';
  }

  getConfig(type: AIProviderType): ProviderConfig | undefined {
    return this.providers.get(type);
  }

  getAllConfigs(): Map<AIProviderType, ProviderConfig> {
    return new Map(this.providers);
  }

  getHealth(type: AIProviderType): ProviderHealth | undefined {
    return this.healthCache.get(type);
  }

  maskApiKey(key: string): string {
    if (!key || key.length < 8) return '****';
    return key.slice(0, 4) + '****' + key.slice(-4);
  }
}

export const aiProviderManager = new AIProviderManager();
