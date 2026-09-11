/**
 * VASU API Key Manager
 * Centralized API key storage and management
 */

import {
  AIProviderType,
  SearchProviderType,
  ProviderConfig,
  ProviderTier,
  ApiTestResult,
} from '../types';
import { aiProviderManager } from './aiProviderManager';
import { webSearchManager } from './webSearchManager';

export interface ApiKeyEntry {
  id: string;
  provider: AIProviderType | SearchProviderType;
  providerType: 'ai' | 'search';
  name: string;
  key: string;
  model?: string;
  baseUrl?: string;
  enabled: boolean;
  tier: ProviderTier;
  priority: number;
  lastTested?: number;
  testResult?: ApiTestResult;
}

export class ApiKeyManager {
  private keys: Map<string, ApiKeyEntry> = new Map();

  constructor() {
    this.loadFromStorage();
    this.syncFromProviders();
  }

  private loadFromStorage() {
    try {
      const saved = localStorage.getItem('vasu_api_keys');
      if (saved) {
        const entries = JSON.parse(saved);
        for (const entry of entries) {
          this.keys.set(entry.id, entry);
        }
      }
    } catch (_) {}
  }

  private saveToStorage() {
    try {
      const entries = Array.from(this.keys.values());
      localStorage.setItem('vasu_api_keys', JSON.stringify(entries));
    } catch (_) {}
  }

  private syncFromProviders() {
    const aiProviders: AIProviderType[] = ['gemini', 'openrouter', 'groq', 'deepseek', 'xai', 'custom'];
    for (const type of aiProviders) {
      const config = aiProviderManager.getConfig(type);
      if (config) {
        const id = `ai_${type}`;
        if (!this.keys.has(id)) {
          this.keys.set(id, {
            id,
            provider: type,
            providerType: 'ai',
            name: config.name,
            key: config.apiKey,
            model: config.model,
            baseUrl: config.baseUrl,
            enabled: config.enabled,
            tier: config.tier,
            priority: config.priority,
          });
        } else {
          const entry = this.keys.get(id)!;
          entry.key = config.apiKey;
          entry.model = config.model;
          entry.enabled = config.enabled;
        }
      }
    }

    const searchProviders: SearchProviderType[] = ['tavily', 'brave'];
    for (const type of searchProviders) {
      const id = `search_${type}`;
      const key = webSearchManager.getApiKey(type);
      if (!this.keys.has(id)) {
        this.keys.set(id, {
          id,
          provider: type,
          providerType: 'search',
          name: type.charAt(0).toUpperCase() + type.slice(1),
          key,
          enabled: key.length > 5,
          tier: 'FREE',
          priority: type === 'tavily' ? 1 : 2,
        });
      } else {
        const entry = this.keys.get(id)!;
        entry.key = key;
        entry.enabled = key.length > 5;
      }
    }

    this.saveToStorage();
  }

  setKey(id: string, key: string) {
    let entry = this.keys.get(id);
    if (!entry) entry = this.keys.get(`ai_${id}`);
    if (!entry) {
      const isAI = ['gemini', 'openrouter', 'groq', 'deepseek', 'xai', 'custom'].includes(id);
      const entryId = isAI ? `ai_${id}` : `search_${id}`;
      entry = {
        id: entryId,
        provider: id as AIProviderType,
        providerType: isAI ? 'ai' : 'search',
        name: id.charAt(0).toUpperCase() + id.slice(1),
        key,
        enabled: key.length > 5,
        tier: 'FREE',
        priority: isAI ? 1 : 2,
      };
      this.keys.set(entryId, entry);
    }
    entry.key = key;
    entry.enabled = key.length > 5;

    if (entry.providerType === 'ai') {
      aiProviderManager.configure(entry.provider as AIProviderType, {
        apiKey: key,
        enabled: key.length > 5,
      });
    }

    this.saveToStorage();
  }

  setModel(id: string, model: string) {
    const entry = this.keys.get(id);
    if (!entry) return;
    entry.model = model;

    if (entry.providerType === 'ai') {
      aiProviderManager.configure(entry.provider as AIProviderType, { model });
    }

    this.saveToStorage();
  }

  setBaseUrl(id: string, baseUrl: string) {
    const entry = this.keys.get(id);
    if (!entry) return;
    entry.baseUrl = baseUrl;

    if (entry.providerType === 'ai') {
      aiProviderManager.configure(entry.provider as AIProviderType, { baseUrl });
    }

    this.saveToStorage();
  }

  setEnabled(id: string, enabled: boolean) {
    const entry = this.keys.get(id);
    if (!entry) return;
    entry.enabled = enabled;

    if (entry.providerType === 'ai') {
      aiProviderManager.configure(entry.provider as AIProviderType, { enabled });
    }

    this.saveToStorage();
  }

  setPriority(id: string, priority: number) {
    const entry = this.keys.get(id);
    if (!entry) return;
    entry.priority = priority;

    if (entry.providerType === 'ai') {
      aiProviderManager.configure(entry.provider as AIProviderType, { priority });
    }

    this.saveToStorage();
  }

  getEntry(id: string): ApiKeyEntry | undefined {
    return this.keys.get(id);
  }

  getAllEntries(): ApiKeyEntry[] {
    return Array.from(this.keys.values()).sort((a, b) => a.priority - b.priority);
  }

  getAIEntries(): ApiKeyEntry[] {
    return this.getAllEntries().filter(e => e.providerType === 'ai');
  }

  getSearchEntries(): ApiKeyEntry[] {
    return this.getAllEntries().filter(e => e.providerType === 'search');
  }

  maskKey(key: string): string {
    if (!key || key.length < 8) return '****';
    return key.slice(0, 4) + '****' + key.slice(-4);
  }

  async testKey(id: string): Promise<ApiTestResult> {
    const entry = this.keys.get(id);
    if (!entry) {
      return { provider: id, status: 'UNCONFIGURED', timestamp: Date.now() };
    }

    const startTime = Date.now();
    try {
      if (entry.providerType === 'ai') {
        const health = await aiProviderManager.testConnection(entry.provider as AIProviderType);
        const result: ApiTestResult = {
          provider: entry.name,
          status: health.status,
          latencyMs: health.latencyMs,
          model: health.model,
          lastError: health.lastError,
          timestamp: Date.now(),
        };
        entry.lastTested = Date.now();
        entry.testResult = result;
        this.saveToStorage();
        return result;
      } else {
        const testResult = await webSearchManager.testConnection(entry.provider as SearchProviderType);
        const result: ApiTestResult = {
          provider: entry.name,
          status: testResult.success ? 'ONLINE' : 'OFFLINE',
          latencyMs: testResult.latencyMs,
          lastError: testResult.error,
          timestamp: Date.now(),
        };
        entry.lastTested = Date.now();
        entry.testResult = result;
        this.saveToStorage();
        return result;
      }
    } catch (err: any) {
      const result: ApiTestResult = {
        provider: entry.name,
        status: 'OFFLINE',
        lastError: err.message,
        timestamp: Date.now(),
      };
      entry.lastTested = Date.now();
      entry.testResult = result;
      this.saveToStorage();
      return result;
    }
  }

  async testAllKeys(): Promise<ApiTestResult[]> {
    const results: ApiTestResult[] = [];
    for (const entry of this.keys.values()) {
      if (entry.key && entry.key.length > 5) {
        results.push(await this.testKey(entry.id));
      }
    }
    return results;
  }
}

export const apiKeyManager = new ApiKeyManager();
