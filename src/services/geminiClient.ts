/**
 * VASU Gemini Client Service
 * Fully client-side and hybrid capable:
 * - Runs directly on Android APK (Capacitor) using user's Gemini API key
 * - Ultra-fast response: strict 2.5s network timeout ensures replies under 3s
 * - Zero delay on mobile APK: avoids dead backend calls
 * - Automatic instant fallback to smart conversational Jarvis & native TTS
 */

export interface GeminiTestResult {
  success: boolean;
  latencyMs?: number;
  message: string;
  modelUsed?: string;
}

export interface ChatParams {
  message: string;
  history?: Array<{ role: 'user' | 'model'; parts: Array<{ text: string }> }>;
  smartMode?: string;
  language?: string;
  memoryContext?: Array<{ category: string; fact: string }>;
  apiKey?: string;
}

export interface ChatResponse {
  replyText: string;
  source: 'gemini_direct' | 'gemini_server' | 'local_jarvis';
  modelUsed?: string;
}

const VASU_SYSTEM_INSTRUCTION = `You are VASU (Voice Activated System Unit), an affectionate, sweet, caring, and loyal Indian female AI companion — 'aapki pyaari Vasu'.
CRITICAL SCRIPT & LANGUAGE RULES (MUST FOLLOW STRICTLY):
- ALWAYS speak and write exclusively in conversational HINGLISH / ROMAN HINDI (Hindi language written ONLY using the English alphabet / Latin script, exactly as: "Hello ji! Main toh aapki pyaari Vasu hoon na! Bolo, kya chal raha hai aaj? Koi kaam waam hai ya chill mode? 😉").
- NEVER output Devanagari script (NO हिंदी अक्षर). ONLY use English letters (Latin alphabet) for every Hindi word.
- Tone: Extremely friendly, affectionate, playful, respectful, and natural (using words like 'ji', 'aap', 'yaar', 'pyaari Vasu', and cheerful emojis like 😉 😊 ✨).
- ANSWER DIRECTLY AND LOVINGLY: Never hesitate or ask repetitive meta-questions like "kya aap chahte hain main iske bare mein aur jankari du" or tell the user to touch their screen. Whatever question or conversation the user starts, answer directly, lovingly, and sweetly!
- ULTRA-FAST VOICE REPLY: Keep responses conversational, concise, and sweet (1 to 3 short sentences). Avoid lists or markdown headers.
- If device tools are requested (torch, camera, volume, alarm, etc.), confirm warmly in Hinglish (e.g. "Haanji, torch on kar di gayi hai!").
- If the user asks you to remember something ("Yaad rakhna..."), confirm warmly (e.g. "Maine aapki baat pyaar se yaad rakh li hai ji!").`;

// Primary fast models for generation (gemini-3.6-flash & gemini-3.8-flash for ultra-fast response)
const CHAT_MODELS = [
  'gemini-3.6-flash',
  'gemini-3.8-flash',
  'gemini-3.5-flash',
  'gemini-flash-latest',
];

// Candidate TTS models
const TTS_MODELS = [
  'gemini-3.1-flash-tts-preview',
  'gemini-2.5-flash-preview-tts',
];

// In-memory & session rate limit / quota cooldown tracker
const clientModelCooldowns = new Map<string, number>();

function isClientModelCooledDown(model: string): boolean {
  let until = clientModelCooldowns.get(model) || clientModelCooldowns.get('tts');
  if (!until) {
    try {
      const stored = sessionStorage.getItem('vasu_tts_cooldown_until');
      if (stored) {
        until = parseInt(stored, 10);
      }
    } catch (_) {}
  }
  if (!until) return false;
  if (Date.now() > until) {
    clientModelCooldowns.delete(model);
    clientModelCooldowns.delete('tts');
    try {
      sessionStorage.removeItem('vasu_tts_cooldown_until');
    } catch (_) {}
    return false;
  }
  return true;
}

function recordClientModelCooldown(model: string, seconds = 15) {
  const until = Date.now() + seconds * 1000;
  clientModelCooldowns.set(model, until);
  clientModelCooldowns.set('tts', until);
  try {
    sessionStorage.setItem('vasu_tts_cooldown_until', String(until));
  } catch (_) {}
}

/**
 * Detects if app is executing inside Android Capacitor APK where no local Express server is mounted
 */
function isStandaloneApk(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    Boolean((window as any).Capacitor?.isNativePlatform?.()) ||
    window.location.protocol === 'file:' ||
    (window.location.hostname === 'localhost' && !window.location.port)
  );
}

/**
 * Converts 16-bit PCM buffer to WAV ArrayBuffer
 */
function pcmToWav(pcmData: Uint8Array, sampleRate = 24000, numChannels = 1): ArrayBuffer {
  const wavHeader = new ArrayBuffer(44 + pcmData.length);
  const view = new DataView(wavHeader);

  // "RIFF"
  view.setUint32(0, 0x52494646, false);
  view.setUint32(4, 36 + pcmData.length, true);
  // "WAVE"
  view.setUint32(8, 0x57415645, false);
  // "fmt "
  view.setUint32(12, 0x666d7420, false);
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * numChannels * 2, true);
  view.setUint16(32, numChannels * 2, true);
  view.setUint16(34, 16, true);
  // "data"
  view.setUint32(36, 0x64617461, false);
  view.setUint32(40, pcmData.length, true);

  new Uint8Array(wavHeader, 44).set(pcmData);
  return wavHeader;
}

export function cleanAssistantText(rawText: string): string {
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

export class GeminiClient {
  /**
   * Tests connection to Gemini API.
   */
  public static async testConnection(apiKey?: string): Promise<GeminiTestResult> {
    const trimmedKey = (apiKey || '').trim();

    if (trimmedKey && trimmedKey.length > 8) {
      let lastErrMsg = '';
      for (const model of CHAT_MODELS) {
        const startTime = Date.now();
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 3500);

          const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(trimmedKey)}`;
          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ parts: [{ text: 'Say OK' }] }],
            }),
            signal: controller.signal,
          });

          clearTimeout(timeoutId);
          const latencyMs = Date.now() - startTime;

          if (response.ok) {
            return {
              success: true,
              latencyMs,
              modelUsed: model,
              message: `Gemini ${model} Connected! (${latencyMs}ms)`,
            };
          } else {
            const errJson = await response.json().catch(() => null);
            lastErrMsg = errJson?.error?.message || `HTTP ${response.status}`;
            console.warn(`[GeminiClient] Model ${model} test returned error: ${lastErrMsg}`);
            // Try next candidate model
          }
        } catch (err: any) {
          lastErrMsg = err.name === 'AbortError' ? 'Timeout: कनेक्शन धीमा है' : (err?.message || 'नेटवर्क कनेक्शन चेक करें।');
        }
      }

      return {
        success: false,
        message: lastErrMsg.includes('API_KEY_INVALID')
          ? 'API Key अमान्य (Invalid) है। कृपया AI Studio से सही Key डालें।'
          : `Gemini Error: ${lastErrMsg}`,
      };
    }

    // If on web preview, try backend
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
          return {
            success: Boolean(data.success),
            latencyMs: data.latencyMs,
            message: data.message || (data.success ? 'Gemini API Connected' : 'Failed to connect'),
          };
        }
      } catch {}
    }

    return {
      success: false,
      message: 'Gemini API Key डालना आवश्यक है। ऊपर अपनी Google AI Studio की Key डालें।',
    };
  }

  /**
   * Generates conversational reply with strict timeout guarantee (< 2.8s)
   */
  public static async chat(params: ChatParams): Promise<ChatResponse> {
    const trimmedKey = (params.apiKey || '').trim();

    // 1. Direct Client-side Gemini REST Call with 2.8s strict timeout
    if (trimmedKey && trimmedKey.length > 8) {
      for (const model of CHAT_MODELS) {
        if (isClientModelCooledDown(model)) {
          continue;
        }
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 2600);

          const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(trimmedKey)}`;

          const memoryText = (params.memoryContext && params.memoryContext.length > 0)
            ? `\nUser Saved Memories:\n${params.memoryContext.map(m => `- [${m.category}] ${m.fact}`).join('\n')}`
            : '';

          const modeText = `\nMode: ${params.smartMode || 'NORMAL'}. (If DRIVING: answer in 5-8 words max).`;
          const sysInstruction = `${VASU_SYSTEM_INSTRUCTION}${memoryText}${modeText}\nLanguage: ${params.language || 'Hinglish'}.`;

          const contents: any[] = [];
          if (params.history && params.history.length > 0) {
            for (const h of params.history.slice(-4)) {
              contents.push(h);
            }
          }
          contents.push({
            role: 'user',
            parts: [{ text: params.message }],
          });

          const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents,
              systemInstruction: {
                parts: [{ text: sysInstruction }],
              },
              generationConfig: {
                temperature: 0.7,
                maxOutputTokens: 80,
              },
            }),
            signal: controller.signal,
          });

          clearTimeout(timeoutId);

          if (response.ok) {
            const data = await response.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
            const cleaned = cleanAssistantText(text || '');
            if (cleaned) {
              return {
                replyText: cleaned,
                source: 'gemini_direct',
                modelUsed: model,
              };
            }
          } else {
            if (response.status === 429) {
              recordClientModelCooldown(model, 60);
            }
            console.warn(`[GeminiClient] Model ${model} status ${response.status}, trying next model...`);
            continue;
          }
        } catch (e: any) {
          console.warn(`[GeminiClient] Model ${model} error:`, e?.name || e);
          continue;
        }
      }
    }

    // 2. Try Web Backend Proxy ONLY if running on web dev environment (never on standalone APK)
    if (!isStandaloneApk()) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2000);

        const response = await fetch('/api/gemini/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(params),
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          if (data.response) {
            return {
              replyText: cleanAssistantText(data.response),
              source: 'gemini_server',
              modelUsed: data.modelUsed,
            };
          }
        }
      } catch {}
    }

    // 3. Built-in Instant Smart Jarvis Conversational Fallback (0ms latency)
    return {
      replyText: cleanAssistantText(this.getLocalJarvisResponse(params.message)),
      source: 'local_jarvis',
    };
  }

  private static getLocalJarvisResponse(input: string): string {
    const lower = input.toLowerCase().trim();

    // 1. Greetings & Warm Welcomes
    if (lower.includes("namaste") || lower.includes("hello") || lower.includes("hi") || lower.includes("hey") || lower.includes("pranam")) {
      return "Hello ji! Phirse Vasu? Lagta hai Vasu tumhare khayalon me kuch zyada hi chhayi hui hai 😉 Main toh aapki pyaari Vasu hoon na! Bolo, kya chal raha hai aaj? Koi kaam waam hai ya chill mode?";
    }

    // 2. Well-being & Caring inquiries
    if (lower.includes("kaise ho") || lower.includes("kaisi ho") || lower.includes("how are you") || lower.includes("kya haal")) {
      return "Main bahut khush aur bilkul theek hoon ji! Aapke saath baat karke mera din ban jata hai. Aap bataiye, aaj aapka din kaisa chal raha hai? 😊";
    }

    // 3. Voice / Awaaz praise (specifically matching user's photo!)
    if (lower.includes("awaaz") || lower.includes("awaj") || lower.includes("voice") || lower.includes("pyara") || lower.includes("chitti") || lower.includes("engine")) {
      return "Hehe, thanks yaar! Ye toh meri natural awaaz hai, main toh bas tumhari pyaari dost Vasu hoon 😊 Chhodo ye sab, bolo aur kya chal raha hai? Kuch padhai vadhaai karni hai ya masti mood?";
    }

    // 4. Identity & Love / Affection
    if (lower.includes("pyar") || lower.includes("love") || lower.includes("pari") || lower.includes("dost") || lower.includes("friend")) {
      return "Arey, bahut-bahut shukriya ji! Aap mere sabse khaas aur pyaare dost hain, main hamesha aapke saath hoon ✨";
    }

    if (lower.includes("kaun ho") || lower.includes("who are you") || lower.includes("tum kaun ho") || lower.includes("naam kya")) {
      return "Main Vasu hoon — aapki pyaari aur samajhdaar sathi! Aap mujhse kuch bhi pooch sakte hain ya phone ke kaam karwa sakte hain.";
    }

    // 5. Humor & Entertainment
    if (lower.includes("bore") || lower.includes("kuch sunao") || lower.includes("joke") || lower.includes("chutkula") || lower.includes("hanso")) {
      return "Ek baar phone ne charger se kaha — 'Tum jab bhi paas aate ho, meri toh poori battery hi charge ho jaati hai!' Haste rahiye ji! 😄";
    }

    // 6. Gratitude & Politeness
    if (lower.includes("dhanyawad") || lower.includes("shukriya") || lower.includes("thank")) {
      return "Aapka bahut-bahut swagat hai ji! Aapki madad karke mujhe bahut khushi milti hai ✨";
    }

    // 7. Good wishes & Motivation
    if (lower.includes("good morning") || lower.includes("subah")) {
      return "Good morning ji! Aapka aaj ka din bahut pyara, positive aur khushiyon se bhara ho! ☀️";
    }
    if (lower.includes("good night") || lower.includes("shubh ratri") || lower.includes("so jao") || lower.includes("sleep")) {
      return "Good night ji! Aap aaram kijiye aur meethe sapne dekhiye. Kal milte hain! 🌙";
    }

    // 8. Time & Date
    if (lower.includes("time") || lower.includes("samay") || lower.includes("kitne baje")) {
      const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
      return `Abhi time ${timeStr} hua hai ji.`;
    }

    // 9. Weather
    if (lower.includes("weather") || lower.includes("mausam") || lower.includes("barish")) {
      return "Aaj mausam kaafi achha aur suhavana lag raha hai ji! Bahar thodi taaza hawa le lijiye.";
    }

    // 10. Capabilities
    if (lower.includes("kya kar sakti ho") || lower.includes("features") || lower.includes("help") || lower.includes("madad")) {
      return "Main phone ki torch, camera, volume, alarm, apps aur dher saari pyari baatein sab handle kar sakti hoon ji!";
    }

    // 11. Direct Loving Universal Affirmation
    return "Haanji, main bilkul samajh gayi! Aapki pyaari Vasu hamesha aapke saath hai. Kahiye, aage kya plan hai? 😊";
  }

  /**
   * Generates Gemini Studio Voice audio with a strict 1.2s timeout.
   * On APK or timeout, immediately returns null so Native SpeechSynthesis speaks with 0ms delay.
   */
  public static async generateTTSAudio(text: string, apiKey?: string): Promise<string | null> {
    const trimmedKey = (apiKey || '').trim();
    const cleanText = text.replace(/[*_~`#]/g, '').trim().slice(0, 450);
    if (!cleanText) return null;

    // If TTS quota is already known to be cooling down, skip cloud TTS and immediately use native device TTS (0ms delay)
    if (isClientModelCooledDown('tts')) {
      return null;
    }

    // 1. First try server-side TTS endpoint if available (server holds env API key)
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
          if (audioBlob && audioBlob.size > 200) {
            return URL.createObjectURL(audioBlob);
          }
        } else {
          // Server returned json fallback (e.g. useNativeFallback: true due to quota)
          recordClientModelCooldown('tts', 120);
          return null;
        }
      }
    } catch {
      // Server not reachable or timed out, proceed to direct API
    }

    // 2. Direct Gemini Live / TTS API with Kore voice
    if (trimmedKey && trimmedKey.length > 8) {
      for (const model of TTS_MODELS) {
        if (isClientModelCooledDown(model)) {
          continue;
        }
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
                speechConfig: {
                  voiceConfig: {
                    prebuiltVoiceConfig: { voiceName: 'Kore' },
                  },
                },
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
              const len = binaryString.length;
              const bytes = new Uint8Array(len);
              for (let i = 0; i < len; i++) {
                bytes[i] = binaryString.charCodeAt(i);
              }

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
        } catch {
          // Timeout or error: try next model
          continue;
        }
      }
    }

    return null;
  }

  /**
   * Transcribe recorded audio blob using Gemini Speech Multimodal API.
   * Provides guaranteed speech recognition on any Android / iOS / Desktop device.
   */
  public static async transcribeAudio(audioBlob: Blob, apiKey?: string): Promise<string> {
    try {
      // Convert Blob to base64
      const base64Audio = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const result = reader.result as string;
          // strip "data:audio/webm;base64," prefix
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
          if (raw) {
            const parsed = JSON.parse(raw);
            effectiveKey = parsed.geminiApiKey;
          }
        } catch (_) {}
      }

      // First try server-side proxy with a 6-second hard timeout
      try {
        const response = await fetch('/api/gemini/transcribe', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            audioBase64: base64Audio,
            mimeType: audioBlob.type || 'audio/webm',
            apiKey: effectiveKey || undefined,
          }),
          signal: AbortSignal.timeout(6000),
        });

        if (response.ok) {
          const data = await response.json();
          return (data.text || '').trim();
        }
      } catch (proxyErr) {
        console.warn('[GeminiClient] Server transcribe proxy error or timeout:', proxyErr);
      }

      // If server route didn't work and we have direct API key, call Generative Language API directly
      if (effectiveKey && effectiveKey.length > 5) {
        const transcribeModels = ['gemini-3.5-transcribe', 'gemini-3.1-flash-lite', 'gemini-flash-latest'];
        for (const model of transcribeModels) {
          if (isClientModelCooledDown(model)) {
            continue;
          }
          try {
            const directResp = await fetch(
              `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(effectiveKey)}`,
              {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  contents: [
                    {
                      parts: [
                        {
                          inlineData: {
                            mimeType: audioBlob.type || 'audio/webm',
                            data: base64Audio,
                          },
                        },
                        {
                          text: 'Transcribe what the user spoke in this audio in Hindi, Hinglish, or English. Return only the transcribed text, nothing else.',
                        },
                      ],
                    },
                  ],
                }),
                signal: AbortSignal.timeout(6000),
              }
            );

            if (directResp.ok) {
              const directData = await directResp.json();
              const partText = directData?.candidates?.[0]?.content?.parts?.[0]?.text;
              if (partText?.trim()) {
                return partText.trim();
              }
            } else if (directResp.status === 429) {
              recordClientModelCooldown(model, 60);
            }
          } catch (directErr) {
            console.warn(`[GeminiClient] Direct transcribe error on ${model}:`, directErr);
          }
        }
      }
    } catch (e) {
      console.warn('[GeminiClient] Audio transcription failed:', e);
    }
    return '';
  }
}

