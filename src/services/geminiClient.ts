/**
 * VASU Gemini Client Service - Updated with Multi-Provider Support
 * Maintains backward compatibility while using the new AI Provider Manager
 */

import { aiProviderManager } from './aiProviderManager';
import { webSearchManager } from './webSearchManager';
import { toPhoneticHinglish } from './audioEngine';
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
  // Convert any Devanagari to phonetic Hinglish (Gemini sometimes ignores the no-Devanagari instruction)
  cleaned = toPhoneticHinglish(cleaned);
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
  'gemini-3.6-flash',
  'gemini-flash-latest',
  'gemini-3-flash-preview',
];

const TTS_MODELS = [
  'gemini-2.5-flash-preview-tts',
  'gemini-2.0-flash-live-001',
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
  // Only cool down TTS if the TTS model itself is rate-limited, not chat models
  if (model === 'tts' || model.includes('tts')) {
    clientModelCooldowns.set('tts', until);
    try { sessionStorage.setItem('vasu_tts_cooldown_until', String(until)); } catch (_) {}
  }
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
    if (trimmedKey && trimmedKey.length > 5) {
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
    console.log('[GeminiClient] chat called, apiKey from params:', params.apiKey ? params.apiKey.substring(0,8) + '...' : 'EMPTY');
    // Try multi-provider system first
    const providerResponse = await aiProviderManager.chat(params);
    console.log('[GeminiClient] aiProviderManager response source:', providerResponse.source);
    if (providerResponse.source !== 'local_jarvis') {
      return {
        replyText: cleanAssistantText(providerResponse.replyText),
        source: 'ai_provider',
        modelUsed: providerResponse.modelUsed,
      };
    }

    console.warn('[GeminiClient] aiProviderManager returned local_jarvis, trying direct Gemini fallback');

    // Fallback to direct Gemini if configured
    const trimmedKey = (params.apiKey || '').trim();
    console.log('[GeminiClient] trimmedKey length:', trimmedKey.length, 'present:', !!trimmedKey);
    if (trimmedKey && trimmedKey.length > 5) {
      const sysInstruction = `You are VASU, an affectionate Indian female AI companion. Speak in conversational Hinglish. Keep replies concise (1-3 sentences). Never output Devanagari script.`;
      for (const model of CHAT_MODELS) {
        if (isClientModelCooledDown(model)) continue;
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 4000);
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
              systemInstruction: { parts: [{ text: sysInstruction }] },
              generationConfig: { temperature: 0.7, maxOutputTokens: 250 },
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
            console.warn(`[GeminiClient] ${model} returned ${response.status}`);
            if (response.status === 429) recordClientModelCooldown(model, 10);
          }
        } catch (e) {
          console.warn(`[GeminiClient] ${model} failed:`, e);
          continue;
        }
      }
    }

    // Local Jarvis fallback — both provider system and direct Gemini failed
    console.error('[GeminiClient] ALL AI providers failed — showing offline message. apiKey present:', !!trimmedKey, 'length:', trimmedKey.length);
    return {
      replyText: cleanAssistantText(
        'Ji, main Vasu hoon! Offline mode mein limited commands available hain. ' +
        'AI features ke liye please Gemini ya OpenRouter API key configure kijiye Settings mein. ' +
        'Tab tak torch, camera, alarm jaise device commands kaam karte rahenge!'
      ),
      source: 'local_jarvis',
    };
  }

  private static getLocalJarvisResponse(input: string): string {
    return '';
  }

  public static async generateTTSAudio(text: string, apiKey?: string): Promise<string | null> {
    const trimmedKey = (apiKey || '').trim();
    const cleanText = text.replace(/[*_~`#]/g, '').trim().slice(0, 450);
    if (!cleanText) {
      console.warn('[GeminiClient] TTS: empty text after cleaning');
      return null;
    }
    if (isClientModelCooledDown('tts')) {
      console.warn('[GeminiClient] TTS in cooldown, skipping');
      return null;
    }

    console.log(`[GeminiClient] generateTTSAudio called, key present: ${!!trimmedKey}, key length: ${trimmedKey.length}, text length: ${cleanText.length}`);

    // Direct Gemini TTS (works in standalone APK + web)
    if (trimmedKey && trimmedKey.length > 5) {
      for (const model of TTS_MODELS) {
        if (isClientModelCooledDown(model)) {
          console.log(`[GeminiClient] TTS model ${model} in cooldown, skipping`);
          continue;
        }
        try {
          console.log(`[GeminiClient] Trying TTS with model: ${model}, key prefix: ${trimmedKey.substring(0, 10)}...`);
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 5000);
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
          console.log(`[GeminiClient] TTS ${model} response status: ${response.status}`);
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
              const audioUrl = URL.createObjectURL(blob);
              console.log(`[GeminiClient] TTS audio generated successfully, blob URL: ${audioUrl.substring(0, 50)}...`);
              return audioUrl;
            } else {
              console.warn(`[GeminiClient] TTS ${model} returned OK but no audio data. Response structure:`, JSON.stringify(data).substring(0, 200));
            }
          } else {
            const errorText = await response.text().catch(() => 'Could not read error body');
            console.warn(`[GeminiClient] TTS ${model} returned ${response.status}: ${errorText.substring(0, 200)}`);
            if (response.status === 429) {
              recordClientModelCooldown('tts', 5);
              recordClientModelCooldown(model, 5);
              // Don't break — try next model
              continue;
            }
          }
        } catch (e: any) {
          const errMsg = e?.name === 'AbortError' ? 'Timeout after 5s' : e?.message || String(e);
          console.warn(`[GeminiClient] TTS ${model} failed: ${errMsg}`);
          continue;
        }
      }
    } else {
      console.warn(`[GeminiClient] No valid API key for TTS. Key present: ${!!trimmedKey}, length: ${trimmedKey.length}`);
    }
    console.warn('[GeminiClient] All TTS models exhausted, returning null');
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

      // Server transcribe (non-standalone only)
      if (!isStandaloneApk()) {
        try {
          const response = await fetch('/api/gemini/transcribe', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ audioBase64: base64Audio, mimeType: audioBlob.type || 'audio/webm', apiKey: effectiveKey || undefined }),
            signal: AbortSignal.timeout(8000),
          });
          if (response.ok) {
            const data = await response.json();
            return (data.text || '').trim();
          }
        } catch (e) {
          console.warn('[GeminiClient] Server transcribe failed, trying direct:', e);
        }
      }

      // Direct Gemini transcribe
      if (effectiveKey && effectiveKey.length > 5) {
        const transcribeModels = ['gemini-3.6-flash', 'gemini-flash-latest'];
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
                signal: AbortSignal.timeout(10000),
              }
            );
            if (directResp.ok) {
              const directData = await directResp.json();
              const partText = directData?.candidates?.[0]?.content?.parts?.[0]?.text;
              if (partText?.trim()) return partText.trim();
            } else if (directResp.status === 429) {
              recordClientModelCooldown(model, 60);
            }
          } catch (e) {
            console.warn(`[GeminiClient] Direct transcribe ${model} failed:`, e);
          }
        }
      }
    } catch (e) {
      console.error('[GeminiClient] transcribeAudio error:', e);
    }
    return '';
  }
}

export { cleanAssistantText };
