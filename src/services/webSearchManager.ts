/**
 * VASU Web Search Manager
 * Multi-provider web search with intelligent fallback
 */

import {
  SearchProviderType,
  SearchParams,
  SearchResponse,
  SearchResult,
} from '../types';

const SEARCH_PROVIDER_CONFIGS: Record<SearchProviderType, { baseUrl: string; tier: string }> = {
  tavily: { baseUrl: 'https://api.tavily.com', tier: 'FREE' },
  brave: { baseUrl: 'https://api.search.brave.com/res/v1/web', tier: 'FREE' },
  gemini_grounding: { baseUrl: 'https://generativelanguage.googleapis.com/v1beta', tier: 'FREE' },
};

export class WebSearchManager {
  private providerPriority: SearchProviderType[] = ['tavily', 'brave', 'gemini_grounding'];
  private cooldowns: Map<string, number> = new Map();
  private searchCounts: Map<string, { day: string; count: number }> = new Map();

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage() {
    try {
      const saved = localStorage.getItem('vasu_search_config');
      if (saved) {
        const config = JSON.parse(saved);
        if (config.priority) this.providerPriority = config.priority;
      }
    } catch (_) {}
  }

  private saveToStorage() {
    try {
      localStorage.setItem('vasu_search_config', JSON.stringify({
        priority: this.providerPriority,
      }));
    } catch (_) {}
  }

  setPriority(priority: SearchProviderType[]) {
    this.providerPriority = priority;
    this.saveToStorage();
  }

  getApiKey(type: SearchProviderType): string {
    try {
      const settings = JSON.parse(localStorage.getItem('vasu_settings') || '{}');
      switch (type) {
        case 'tavily': return settings.tavilyApiKey || '';
        case 'brave': return settings.braveSearchApiKey || '';
        default: return '';
      }
    } catch (_) { return ''; }
  }

  isConfigured(type: SearchProviderType): boolean {
    if (type === 'gemini_grounding') {
      try {
        const settings = JSON.parse(localStorage.getItem('vasu_settings') || '{}');
        return Boolean(settings.geminiApiKey && settings.geminiApiKey.length > 5);
      } catch (_) { return false; }
    }
    return this.getApiKey(type).length > 5;
  }

  getEnabledProviders(): SearchProviderType[] {
    return this.providerPriority.filter(type => this.isConfigured(type));
  }

  isCooledDown(type: SearchProviderType): boolean {
    const until = this.cooldowns.get(type);
    if (!until) return false;
    if (Date.now() > until) {
      this.cooldowns.delete(type);
      return false;
    }
    return true;
  }

  private setCooldown(type: SearchProviderType, seconds: number = 30) {
    this.cooldowns.set(type, Date.now() + seconds * 1000);
  }

  async search(params: SearchParams): Promise<SearchResponse> {
    const enabledProviders = this.getEnabledProviders();
    if (enabledProviders.length === 0) {
      return { results: [], source: 'tavily', latencyMs: 0 };
    }

    for (const provider of enabledProviders) {
      if (this.isCooledDown(provider)) continue;
      try {
        const results = await this.searchWithProvider(provider, params);
        return { results, source: provider, latencyMs: Date.now() };
      } catch (err: any) {
        console.warn(`[WebSearch] ${provider} failed:`, err.message);
        if (err.message?.includes('429') || err.message?.includes('quota')) {
          this.setCooldown(provider, 60);
        }
        continue;
      }
    }

    return { results: [], source: 'tavily', latencyMs: 0 };
  }

  private async searchWithProvider(type: SearchProviderType, params: SearchParams): Promise<SearchResult[]> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    try {
      switch (type) {
        case 'tavily':
          return await this.searchTavily(params, controller.signal);
        case 'brave':
          return await this.searchBrave(params, controller.signal);
        case 'gemini_grounding':
          return await this.searchGeminiGrounding(params, controller.signal);
        default:
          return [];
      }
    } finally {
      clearTimeout(timeoutId);
    }
  }

  private async searchTavily(params: SearchParams, signal: AbortSignal): Promise<SearchResult[]> {
    const apiKey = this.getApiKey('tavily');
    if (!apiKey) throw new Error('Tavily API key not configured');

    const response = await fetch(`${SEARCH_PROVIDER_CONFIGS.tavily.baseUrl}/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        api_key: apiKey,
        query: params.query,
        max_results: params.maxResults || 5,
        search_depth: 'basic',
        include_answer: false,
      }),
      signal,
    });

    if (!response.ok) {
      throw new Error(`Tavily HTTP ${response.status}`);
    }

    const data = await response.json();
    return (data.results || []).map((r: any) => ({
      title: r.title || '',
      url: r.url || '',
      snippet: r.content || '',
      source: 'tavily' as SearchProviderType,
      score: r.score || 0,
    }));
  }

  private async searchBrave(params: SearchParams, signal: AbortSignal): Promise<SearchResult[]> {
    const apiKey = this.getApiKey('brave');
    if (!apiKey) throw new Error('Brave Search API key not configured');

    const url = new URL(`${SEARCH_PROVIDER_CONFIGS.brave.baseUrl}`);
    url.searchParams.set('q', params.query);
    url.searchParams.set('count', String(params.maxResults || 5));
    if (params.freshness) {
      url.searchParams.set('freshness', params.freshness);
    }

    const response = await fetch(url.toString(), {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        'Accept-Encoding': 'gzip',
        'X-Subscription-Token': apiKey,
      },
      signal,
    });

    if (!response.ok) {
      throw new Error(`Brave HTTP ${response.status}`);
    }

    const data = await response.json();
    return (data.web?.results || []).map((r: any) => ({
      title: r.title || '',
      url: r.url || '',
      snippet: r.description || '',
      source: 'brave' as SearchProviderType,
      score: r.age ? 0.5 : 0,
    }));
  }

  private async searchGeminiGrounding(params: SearchParams, signal: AbortSignal): Promise<SearchResult[]> {
    try {
      const settings = JSON.parse(localStorage.getItem('vasu_settings') || '{}');
      const apiKey = settings.geminiApiKey;
      if (!apiKey) throw new Error('Gemini API key not configured');

      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=${encodeURIComponent(apiKey)}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [{ parts: [{ text: `Search the web for: ${params.query}. Provide brief, accurate results.` }] }],
            tools: [{ googleSearch: {} }],
          }),
          signal,
        }
      );

      if (!response.ok) throw new Error(`Gemini HTTP ${response.status}`);

      const data = await response.json();
      const groundingMeta = data?.candidates?.[0]?.groundingMetadata;
      const chunks = groundingMeta?.groundingChunks || [];
      return chunks.slice(0, params.maxResults || 5).map((chunk: any) => ({
        title: chunk.web?.title || 'Web Result',
        url: chunk.web?.uri || '',
        snippet: '',
        source: 'gemini_grounding' as SearchProviderType,
      }));
    } catch (err) {
      throw err;
    }
  }

  async testConnection(type: SearchProviderType): Promise<{ success: boolean; latencyMs: number; error?: string }> {
    const startTime = Date.now();
    try {
      const results = await this.searchWithProvider(type, { query: 'test search vasu assistant', maxResults: 1 });
      return { success: true, latencyMs: Date.now() - startTime };
    } catch (err: any) {
      return { success: false, latencyMs: Date.now() - startTime, error: err.message };
    }
  }

  maskApiKey(key: string): string {
    if (!key || key.length < 8) return '****';
    return key.slice(0, 4) + '****' + key.slice(-4);
  }
}

export const webSearchManager = new WebSearchManager();
