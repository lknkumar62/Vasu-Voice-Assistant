/**
 * VASU Gemini Client Service - Updated with Multi-Provider Support
 * Maintains backward compatibility while using the new AI Provider Manager
 */

import { aiProviderManager } from './aiProviderManager';
import { webSearchManager } from './webSearchManager';
import { AIProviderResponse, ChatParams, SearchResult } from '../types';

export interface GeminiTestResult {
  success: boolean;
  latencyMs?: number;
  message: string;
  modelUsed?: string;
}

export interface ChatResponse {
  replyText: string;
  source: 'gemini_direct' | 'gemini_server' | 'local_jarvis' | 'ai_provider';
  modelUsed?: string;
}

function cleanAssistantText(rawText: string): string {
  if (!rawText) return '';
  let cleaned = rawText
    .replace(/^:\s*STRICTLY\b.*$/gim, '')
    .replace(/^CRITICAL SCRIPT.*$/gim, '')
    .replace(/^MUST FOLLOW STRICTLY.*$/gim, '')
    .replace(/^:\s*STRICTLY/gi, '')
    .replace(/^:\s*/, '')
    .replace(/\{.*?\}/g, '')
    .replace(/[*_~`#]/g, '')
    .trim();
  return cleaned;
}

function isStandaloneApk(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    Boolean((window as any).Capacitor?.isNativePlatform?.()) ||
    window.location.protocol === 'file:' ||
    (window.location.hostname === 'localhost' && !window.location.port)
  );
}

const CHAT_MODELS = [
  'gemini-2.5-flash',
  'gemini-2.0-flash',
  'gemini-1.5-flash',
];

const TTS_MODELS = [
  'gemini-2.0-flash-exp',
  'gemini-1.5-flash',
];

const clientModelCooldowns = new Map<string, number>();

function isClientModelCooledDown(model: string): boolean {
  let until = clientModelCooldowns.get(model) || clientModelCooldowns.get('tts');
  if (!until) {
    try {
      const stored = sessionStorage.getItem('vasu_tts_cooldown_until');
      if (stored) until = parseInt(stored, 10);
    } catch (_) {}
  }
  if (!until) return false;
  if (Date.now() > until) {
    clientModelCooldowns.delete(model);
    clientModelCooldowns.delete('tts');
    try { sessionStorage.removeItem('vasu_tts_cooldown_until'); } catch (_) {}
    return false;
  }
  return true;
}

function recordClientModelCooldown(model: string, seconds = 15) {
  const until = Date.now() + seconds * 1000;
  clientModelCooldowns.set(model, until);
  clientModelCooldowns.set('tts', until);
  try { sessionStorage.setItem('vasu_tts_cooldown_until', String(until)); } catch (_) {}
}

function pcmToWav(pcmData: Uint8Array, sampleRate = 24000, numChannels = 1): ArrayBuffer {
  const wavHeader = new ArrayBuffer(44 + pcmData.length);
  const view = new DataView(wavHeader);
  view.setUint32(0, 0x52494646, false);
  view.setUint32(4, 36 + pcmData.length, true);
  view.setUint32(8, 0x57415645, false);
  view.setUint32(12, 0x666d7420, false);
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * numChannels * 2, true);
  view.setUint16(32, numChannels * 2, true);
  view.setUint16(34, 16, true);
  view.setUint32(36, 0x64617461, false);
  view.setUint32(40, pcmData.length, true);
  new Uint8Array(wavHeader, 44).set(pcmData);
  return wavHeader;
}

export class GeminiClient {
  public static async testConnection(apiKey?: string): Promise<GeminiTestResult> {
    const trimmedKey = (apiKey || '').trim();
    if (trimmedKey && trimmedKey.length > 8) {
      for (const model of CHAT_MODELS) {
        const startTime = Date.now();
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 3500);
          const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(trimmedKey)}`;
          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contents: [{ parts: [{ text: 'Say OK' }] }] }),
            signal: controller.signal,
          });
          clearTimeout(timeoutId);
          const latencyMs = Date.now() - startTime;
          if (response.ok) {
            return { success: true, latencyMs, modelUsed: model, message: `Gemini ${model} Connected! (${latencyMs}ms)` };
          }
        } catch (_) {}
      }
    }

    if (!isStandaloneApk()) {
      try {
        const res = await fetch('/api/gemini/test', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ apiKey: '' }),
        });
        const contentType = res.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await res.json();
          return { success: Boolean(data.success), latencyMs: data.latencyMs, message: data.message || 'Gemini API Connected' };
        }
      } catch {}
    }

    return { success: false, message: 'Gemini API Key डालना आवश्यक है।' };
  }

  public static async chat(params: ChatParams): Promise<ChatResponse> {
    // Try multi-provider system first
    const providerResponse = await aiProviderManager.chat(params);
    if (providerResponse.source !== 'local_jarvis') {
      return {
        replyText: cleanAssistantText(providerResponse.replyText),
        source: 'ai_provider',
        modelUsed: providerResponse.modelUsed,
      };
    }

    // Fallback to direct Gemini if configured
    const trimmedKey = (params.apiKey || '').trim();
    if (trimmedKey && trimmedKey.length > 8) {
      for (const model of CHAT_MODELS) {
        if (isClientModelCooledDown(model)) continue;
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 2600);
          const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(trimmedKey)}`;
          const contents: any[] = [];
          if (params.history) {
            for (const h of params.history.slice(-4)) {
              contents.push(h);
            }
          }
          contents.push({ role: 'user', parts: [{ text: params.message }] });
          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents,
              generationConfig: { temperature: 0.7, maxOutputTokens: 150 },
            }),
            signal: controller.signal,
          });
          clearTimeout(timeoutId);
          if (response.ok) {
            const data = await response.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
            const cleaned = cleanAssistantText(text || '');
            if (cleaned) return { replyText: cleaned, source: 'gemini_direct', modelUsed: model };
          } else {
            if (response.status === 429) recordClientModelCooldown(model, 60);
          }
        } catch (_) { continue; }
      }
    }

    // Local Jarvis fallback
    return {
      replyText: cleanAssistantText(this.getLocalJarvisResponse(params.message)),
      source: 'local_jarvis',
    };
  }

  private static getLocalJarvisResponse(input: string): string {
    const lower = input.toLowerCase().trim();
    if (lower.includes("namaste") || lower.includes("hello") || lower.includes("hi") || lower.includes("hey")) {
      return "Hello ji! Phirse Vasu? Lagta hai Vasu tumhare khayalon me kuch zyada hi chhayi hui hai 😉 Main toh aapki pyaari Vasu hoon na! Bolo, kya chal raha hai aaj?";
    }
    if (lower.includes("kaise ho") || lower.includes("how are you")) {
      return "Main bahut khush aur bilkul theek hoon ji! Aapke saath baat karke mera din ban jata hai. Aap bataiye, aaj aapka din kaisa chal raha hai? 😊";
    }
    if (lower.includes("kaun ho") || lower.includes("who are you")) {
      return "Main Vasu hoon — aapki pyaari aur samajhdaar sathi! Aap mujhse kuch bhi pooch sakte hain.";
    }
    if (lower.includes("bore") || lower.includes("joke")) {
      return "Ek baar phone ne charger se kaha — 'Tum jab bhi paas aate ho, meri toh poori battery hi charge ho jaati hai!' Haste rahiye ji! 😄";
    }
    if (lower.includes("dhanyawad") || lower.includes("thank")) {
      return "Aapka bahut-bahut swagat hai ji! Aapki madad karke mujhe bahut khushi milti hai ✨";
    }
    if (lower.includes("search") || lower.includes("khoj") || lower.includes("dhundh")) {
      return "Web search ke liye please Tavily ya Brave Search API key configure kijiye Settings mein. Main aapki poori help karungi! 🔍";
    }
    return "Haanji, main bilkul samajh gayi! Aapki pyaari Vasu hamesha aapke saath hai. Kahiye, aage kya plan hai? 😊";
  }

  public static async generateTTSAudio(text: string, apiKey?: string): Promise<string | null> {
    const trimmedKey = (apiKey || '').trim();
    const cleanText = text.replace(/[*_~`#]/g, '').trim().slice(0, 450);
    if (!cleanText) return null;
    if (isClientModelCooledDown('tts')) return null;

    try {
      const serverController = new AbortController();
      const serverTimer = setTimeout(() => serverController.abort(), 2500);
      const serverResponse = await fetch('/api/gemini/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: cleanText, apiKey: trimmedKey }),
        signal: serverController.signal,
      });
      clearTimeout(serverTimer);
      if (serverResponse.ok) {
        const contentType = serverResponse.headers.get('content-type') || '';
        if (contentType.includes('audio/') || contentType.includes('wav')) {
          const audioBlob = await serverResponse.blob();
          if (audioBlob && audioBlob.size > 200) return URL.createObjectURL(audioBlob);
        } else {
          recordClientModelCooldown('tts', 120);
          return null;
        }
      }
    } catch {}

    if (trimmedKey && trimmedKey.length > 8) {
      for (const model of TTS_MODELS) {
        if (isClientModelCooledDown(model)) continue;
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 2500);
          const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(trimmedKey)}`;
          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ parts: [{ text: cleanText }] }],
              generationConfig: {
                responseModalities: ['AUDIO'],
                speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: 'Kore' } } },
              },
            }),
            signal: controller.signal,
          });
          clearTimeout(timeoutId);
          if (response.ok) {
            const data = await response.json();
            const part = data?.candidates?.[0]?.content?.parts?.[0];
            const base64Data = part?.inlineData?.data;
            if (base64Data) {
              const binaryString = atob(base64Data);
              const bytes = new Uint8Array(binaryString.length);
              for (let i = 0; i < binaryString.length; i++) bytes[i] = binaryString.charCodeAt(i);
              const wavBuffer = pcmToWav(bytes, 24000, 1);
              const blob = new Blob([wavBuffer], { type: 'audio/wav' });
              return URL.createObjectURL(blob);
            }
          } else {
            if (response.status === 429) {
              recordClientModelCooldown('tts', 15);
              recordClientModelCooldown(model, 15);
              break;
            }
          }
        } catch { continue; }
      }
    }
    return null;
  }

  public static async transcribeAudio(audioBlob: Blob, apiKey?: string): Promise<string> {
    try {
      const base64Audio = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const result = reader.result as string;
          const base64 = result.includes(',') ? result.split(',')[1] : result;
          resolve(base64);
        };
        reader.onerror = reject;
        reader.readAsDataURL(audioBlob);
      });

      if (!base64Audio || base64Audio.length < 50) return '';

      let effectiveKey = apiKey;
      if (!effectiveKey && typeof window !== 'undefined') {
        try {
          const raw = localStorage.getItem('vasu_settings');
          if (raw) { const parsed = JSON.parse(raw); effectiveKey = parsed.geminiApiKey; }
        } catch (_) {}
      }

      try {
        const response = await fetch('/api/gemini/transcribe', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ audioBase64: base64Audio, mimeType: audioBlob.type || 'audio/webm', apiKey: effectiveKey || undefined }),
          signal: AbortSignal.timeout(6000),
        });
        if (response.ok) {
          const data = await response.json();
          return (data.text || '').trim();
        }
      } catch {}

      if (effectiveKey && effectiveKey.length > 5) {
        const transcribeModels = ['gemini-2.0-flash', 'gemini-1.5-flash'];
        for (const model of transcribeModels) {
          if (isClientModelCooledDown(model)) continue;
          try {
            const directResp = await fetch(
              `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(effectiveKey)}`,
              {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  contents: [{
                    parts: [
                      { inlineData: { mimeType: audioBlob.type || 'audio/webm', data: base64Audio } },
                      { text: 'Transcribe what the user spoke in this audio in Hindi, Hinglish, or English. Return only the transcribed text.' },
                    ],
                  }],
                }),
                signal: AbortSignal.timeout(6000),
              }
            );
            if (directResp.ok) {
              const directData = await directResp.json();
              const partText = directData?.candidates?.[0]?.content?.parts?.[0]?.text;
              if (partText?.trim()) return partText.trim();
            } else if (directResp.status === 429) {
              recordClientModelCooldown(model, 60);
            }
          } catch {}
        }
      }
    } catch {}
    return '';
  }
}

export { cleanAssistantText };
