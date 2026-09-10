import express from "express";
import path from "path";
import http from "http";
import { WebSocketServer, WebSocket } from "ws";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI, Modality } from "@google/genai";

const app = express();
const PORT = 3000;

// Allow up to 50MB payloads for base64 audio transcription & image analysis
app.use(express.json({ limit: "50mb" }));
app.use(express.urlencoded({ limit: "50mb", extended: true }));

// Lazy-initialized Gemini AI client with runtime key support
let runtimeGeminiApiKey = "";

function getGeminiClient(customApiKey?: string) {
  const apiKey = (customApiKey && typeof customApiKey === "string" && customApiKey.trim().length > 5)
    ? customApiKey.trim()
    : (runtimeGeminiApiKey || process.env.GEMINI_API_KEY);

  if (!apiKey || apiKey === "MY_GEMINI_API_KEY" || apiKey.trim().length < 5) {
    return null;
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        "User-Agent": "aistudio-build",
      },
    },
  });
}

// VASU Assistant System Persona
const VASU_SYSTEM_INSTRUCTION = `You are VASU, a natural real-time voice assistant.
Speak naturally and conversationally.
Respond in the user's language.
If the user speaks Hindi, respond in Hindi.
If the user speaks Hinglish, respond naturally in Hinglish.
Keep simple commands concise.
Do not unnecessarily repeat the user's words.
Do not mention internal APIs, models, tools, or implementation details.
When a device-control action is requested, use the existing VASU device-control/tool architecture.`;

// Health check
app.get("/api/health", (req, res) => {
  res.json({ status: "ok", app: "VASU Assistant", version: "1.0.0" });
});

// Web Search - Tavily
app.post("/api/search/tavily", async (req, res) => {
  const { query, maxResults = 5, apiKey } = req.body;
  const key = apiKey || process.env.TAVILY_API_KEY;
  if (!key) {
    return res.status(400).json({ error: "Tavily API key not configured" });
  }
  if (!query) {
    return res.status(400).json({ error: "Query parameter is required" });
  }
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    const response = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        api_key: key,
        query,
        max_results: maxResults,
        search_depth: "basic",
      }),
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    if (!response.ok) {
      throw new Error(`Tavily HTTP ${response.status}`);
    }
    const data = await response.json();
    const results = (data.results || []).map((r: any) => ({
      title: r.title || "",
      url: r.url || "",
      snippet: r.content || "",
      source: "tavily",
      score: r.score || 0,
    }));
    return res.json({ results, source: "tavily" });
  } catch (err: any) {
    return res.status(500).json({ error: err.message || "Search failed" });
  }
});

// Web Search - Brave
app.post("/api/search/brave", async (req, res) => {
  const { query, maxResults = 5, apiKey } = req.body;
  const key = apiKey || process.env.BRAVE_SEARCH_API_KEY;
  if (!key) {
    return res.status(400).json({ error: "Brave Search API key not configured" });
  }
  if (!query) {
    return res.status(400).json({ error: "Query parameter is required" });
  }
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    const url = new URL("https://api.search.brave.com/res/v1/web");
    url.searchParams.set("q", query);
    url.searchParams.set("count", String(maxResults));
    const response = await fetch(url.toString(), {
      method: "GET",
      headers: {
        Accept: "application/json",
        "X-Subscription-Token": key,
      },
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    if (!response.ok) {
      throw new Error(`Brave HTTP ${response.status}`);
    }
    const data = await response.json();
    const results = (data.web?.results || []).map((r: any) => ({
      title: r.title || "",
      url: r.url || "",
      snippet: r.description || "",
      source: "brave",
    }));
    return res.json({ results, source: "brave" });
  } catch (err: any) {
    return res.status(500).json({ error: err.message || "Search failed" });
  }
});

// Multi-provider chat proxy
app.post("/api/chat", async (req, res) => {
  const { message, provider, apiKey, model, history = [], language = "Hinglish" } = req.body;
  if (!message) {
    return res.status(400).json({ error: "Message is required" });
  }

  const systemInstruction = `You are VASU, a friendly Indian AI assistant. Respond in ${language}. Keep responses concise and natural.`;

  try {
    if (provider === "openrouter" || provider === "groq" || provider === "deepseek" || provider === "xai" || provider === "custom") {
      const baseUrls: Record<string, string> = {
        openrouter: "https://openrouter.ai/api/v1",
        groq: "https://api.groq.com/openai/v1",
        deepseek: "https://api.deepseek.com/v1",
        xai: "https://api.x.ai/v1",
        custom: req.body.baseUrl || "",
      };
      const baseUrl = baseUrls[provider];
      if (!baseUrl || !apiKey) {
        return res.status(400).json({ error: `${provider} not configured` });
      }
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 8000);
      const messages = [
        { role: "system", content: systemInstruction },
        ...history.slice(-4).map((h: any) => ({
          role: h.role === "model" ? "assistant" : "user",
          content: h.parts?.map((p: any) => p.text).join(" ") || "",
        })),
        { role: "user", content: message },
      ];
      const response = await fetch(`${baseUrl}/chat/completions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify({ model, messages, temperature: 0.7, max_tokens: 150 }),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      return res.json({ response: data.choices?.[0]?.message?.content || "", provider, model });
    }

    // Default: Gemini
    const geminiKey = apiKey || runtimeGeminiApiKey || process.env.GEMINI_API_KEY;
    if (!geminiKey) {
      return res.status(400).json({ error: "No API key configured" });
    }
    const ai = new GoogleGenAI({ apiKey: geminiKey });
    const contents: any[] = [];
    if (history) {
      for (const h of history.slice(-4)) {
        contents.push(h);
      }
    }
    contents.push({ role: "user", parts: [{ text: message }] });
    const response = await ai.models.generateContent({
      model: model || "gemini-2.5-flash",
      contents,
      config: { systemInstruction, temperature: 0.7, maxOutputTokens: 150 },
    });
    return res.json({ response: response.text || "", provider: "gemini", model });
  } catch (err: any) {
    return res.status(500).json({ error: err.message || "Chat failed" });
  }
});

// API Provider status check
app.post("/api/providers/test", async (req, res) => {
  const { provider, apiKey, baseUrl } = req.body;
  const startTime = Date.now();
  try {
    if (provider === "gemini") {
      const key = apiKey || runtimeGeminiApiKey || process.env.GEMINI_API_KEY;
      if (!key) return res.json({ status: "UNCONFIGURED", provider });
      const ai = new GoogleGenAI({ apiKey: key });
      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: "Say OK",
        config: { maxOutputTokens: 5 },
      });
      return res.json({ status: "ONLINE", provider, latencyMs: Date.now() - startTime, model: "gemini-2.5-flash" });
    }
    if (["openrouter", "groq", "deepseek", "xai", "custom"].includes(provider)) {
      const baseUrls: Record<string, string> = {
        openrouter: "https://openrouter.ai/api/v1",
        groq: "https://api.groq.com/openai/v1",
        deepseek: "https://api.deepseek.com/v1",
        xai: "https://api.x.ai/v1",
        custom: baseUrl || "",
      };
      if (!apiKey) return res.json({ status: "UNCONFIGURED", provider });
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);
      const response = await fetch(`${baseUrls[provider]}/models`, {
        headers: { Authorization: `Bearer ${apiKey}` },
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      return res.json({ status: response.ok ? "ONLINE" : "OFFLINE", provider, latencyMs: Date.now() - startTime });
    }
    return res.json({ status: "UNKNOWN", provider });
  } catch (err: any) {
    return res.json({ status: "OFFLINE", provider, error: err.message, latencyMs: Date.now() - startTime });
  }
});

// Update or set runtime Gemini API key
app.post("/api/gemini/key", (req, res) => {
  const { apiKey } = req.body;
  if (apiKey && typeof apiKey === "string" && apiKey.trim().length > 5) {
    runtimeGeminiApiKey = apiKey.trim();
    return res.json({
      success: true,
      configured: true,
      message: "Gemini API key successfully saved & activated.",
    });
  } else if (apiKey === "" || apiKey === null) {
    runtimeGeminiApiKey = "";
    const isEnv = Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.trim().length > 5);
    return res.json({
      success: true,
      configured: isEnv,
      message: isEnv ? "Runtime key cleared, using environment key." : "Gemini API key cleared.",
    });
  }
  return res.status(400).json({ success: false, message: "Invalid API key format" });
});

// Gemini configuration status
app.get("/api/gemini/status", (req, res) => {
  const isUserKey = Boolean(runtimeGeminiApiKey && runtimeGeminiApiKey.trim().length > 5);
  const isEnvKey = Boolean(
    process.env.GEMINI_API_KEY &&
    process.env.GEMINI_API_KEY !== "MY_GEMINI_API_KEY" &&
    process.env.GEMINI_API_KEY.trim().length > 5
  );
  res.json({
    configured: isUserKey || isEnvKey,
    source: isUserKey ? "user" : (isEnvKey ? "env" : "none"),
    model: "gemini-2.5-flash",
    ttsModel: "gemini-2.0-flash-exp",
    voiceName: "Kore",
  });
});

// Model rate limit and quota cooldown tracker
const modelCooldownMap = new Map<string, number>();

function isModelCoolingDown(model: string): boolean {
  const until = modelCooldownMap.get(model);
  if (!until) return false;
  if (Date.now() > until) {
    modelCooldownMap.delete(model);
    return false;
  }
  return true;
}

function markModelRateLimited(model: string, retryDelaySec = 60) {
  const delay = Math.max(30, retryDelaySec) * 1000;
  modelCooldownMap.set(model, Date.now() + delay);
  console.info(`[ModelRateLimit] Pausing requests to ${model} for ${Math.round(delay / 1000)}s due to quota limits.`);
}

// Candidate models for chat & fallback (current active models as of 2026)
const CANDIDATE_CHAT_MODELS = [
  "gemini-2.5-flash",
  "gemini-2.0-flash",
  "gemini-1.5-flash",
];

async function generateChatContentWithFallback(
  ai: GoogleGenAI,
  params: {
    contents: any[];
    systemInstruction?: string;
    temperature?: number;
  }
): Promise<{ text: string; modelUsed: string }> {
  let lastError: any = null;

  for (const model of CANDIDATE_CHAT_MODELS) {
    if (isModelCoolingDown(model)) {
      continue;
    }
    try {
      const response = await ai.models.generateContent({
        model,
        contents: params.contents,
        config: {
          systemInstruction: params.systemInstruction,
          temperature: params.temperature ?? 0.65,
          maxOutputTokens: 80,
        },
      });

      const text = response.text?.trim();
      if (text) {
        return { text, modelUsed: model };
      }
    } catch (err: any) {
      lastError = err;
      const errMsg = err?.message || String(err);
      if (errMsg.includes("429") || errMsg.includes("RESOURCE_EXHAUSTED") || errMsg.includes("quota")) {
        markModelRateLimited(model, 60);
      }
      console.warn(`[Gemini Fallback] Model ${model} encountered error: ${errMsg}. Trying next candidate model...`);
    }
  }

  throw lastError || new Error("All candidate Gemini models failed to respond");
}

// Gemini Connection Test
app.post("/api/gemini/test", async (req, res) => {
  const { apiKey } = req.body;
  const startTime = Date.now();
  try {
    const ai = getGeminiClient(apiKey);
    if (!ai) {
      return res.status(400).json({
        success: false,
        code: "API_KEY_MISSING",
        message: "Gemini API key is not configured. Please enter your Gemini API key in Settings.",
      });
    }

    let lastErr: any = null;
    for (const model of CANDIDATE_CHAT_MODELS) {
      try {
        const response = await ai.models.generateContent({
          model,
          contents: "Respond with the single word: READY",
        });

        const latencyMs = Date.now() - startTime;
        return res.json({
          success: true,
          latencyMs,
          reply: response.text?.trim() || "READY",
          model,
        });
      } catch (err: any) {
        lastErr = err;
        console.warn(`[Gemini Test] Model ${model} error:`, err?.message || String(err));
      }
    }

    throw lastErr || new Error("All test models unavailable");
  } catch (err: any) {
    const message = err?.message || String(err);
    let code = "GEMINI_ERROR";
    if (message.includes("API key not valid") || message.includes("INVALID_ARGUMENT")) {
      code = "INVALID_API_KEY";
    } else if (message.includes("quota") || message.includes("429") || message.includes("ResourceExhausted")) {
      code = "RATE_LIMIT";
    } else if (message.includes("503") || message.includes("high demand") || message.includes("UNAVAILABLE")) {
      code = "HIGH_DEMAND";
    } else if (message.includes("ENOTFOUND") || message.includes("fetch failed") || message.includes("Network")) {
      code = "NETWORK_ERROR";
    }
    return res.status(500).json({
      success: false,
      code,
      message,
    });
  }
});

// Gemini Chat & Continuous Intent Engine
app.post("/api/gemini/chat", async (req, res) => {
  const {
    message,
    history = [],
    memoryContext = [],
    smartMode = "NORMAL",
    language = "Hinglish",
    apiKey,
  } = req.body;

  if (!message || typeof message !== "string") {
    return res.status(400).json({ error: "Message parameter is required" });
  }

  const ai = getGeminiClient(apiKey);

  // If Gemini API Key is not configured, reply with local fallback
  if (!ai) {
    return res.json({
      response: "Ji, main Vasu hoon! Gemini API key configure nahi hai. Settings mein jaake API key daaliye. Tab tak device commands kaam karte rahenge.",
      isOffline: true,
      needsKey: false,
      toolCall: null,
    });
  }

  try {
    const memoryString = memoryContext.length > 0 
      ? `\n\nUser Stored Memories:\n${memoryContext.map((m: any) => `- [${m.category}] ${m.fact}`).join("\n")}`
      : "";

    const modeString = `\nCurrent Operating Mode: ${smartMode}. (If DRIVING: respond in 5-8 words strictly).`;
    const systemPrompt = `${VASU_SYSTEM_INSTRUCTION}${memoryString}${modeString}\nPreferred Language/Style: ${language}.`;

    // Multi-turn conversation contents for continuous Jarvis conversation
    const contents: any[] = [];
    if (Array.isArray(history) && history.length > 0) {
      for (const h of history.slice(-8)) {
        if (h.role && h.parts) {
          contents.push(h);
        } else if (h.text && h.sender) {
          contents.push({
            role: h.sender === "user" ? "user" : "model",
            parts: [{ text: h.text }],
          });
        }
      }
    }
    contents.push({ role: "user", parts: [{ text: message }] });

    // Send chat request using multi-model fallback cascade
    const { text: replyText, modelUsed } = await generateChatContentWithFallback(ai, {
      contents,
      systemInstruction: systemPrompt,
      temperature: 0.7,
    });

    return res.json({
      response: replyText,
      isOffline: false,
      model: modelUsed,
    });
  } catch (err: any) {
    console.error("Gemini Chat Error across all models:", err);

    return res.json({
      response: "Maaf kijiye, AI models abhi busy hain. Thodi der baad try karein ya Settings mein API key check karein.",
      isOffline: true,
      isFallback: true,
      warning: "AI service temporarily unavailable",
    });
  }
});

// Helper to convert 16-bit mono PCM to standard playable WAV buffer
function pcmToWav(pcmBuffer: Buffer, sampleRate = 24000, channels = 1): Buffer {
  const byteRate = sampleRate * channels * 2;
  const blockAlign = channels * 2;
  const buffer = Buffer.alloc(44 + pcmBuffer.length);

  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(36 + pcmBuffer.length, 4);
  buffer.write("WAVE", 8);
  buffer.write("fmt ", 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20); // PCM
  buffer.writeUInt16LE(channels, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(byteRate, 28);
  buffer.writeUInt16LE(blockAlign, 32);
  buffer.writeUInt16LE(16, 34); // 16-bit
  buffer.write("data", 36);
  buffer.writeUInt32LE(pcmBuffer.length, 40);

  pcmBuffer.copy(buffer, 44);
  return buffer;
}

// Gemini Text-to-Speech Engine (Studio Quality Indian Voice)
app.post("/api/gemini/tts", async (req, res) => {
  const { text, apiKey } = req.body;
  if (!text || typeof text !== "string") {
    return res.status(400).json({ error: "Text parameter is required" });
  }

  const ai = getGeminiClient(apiKey);
  if (!ai) {
    return res.status(401).json({
      error: "API_KEY_MISSING",
      message: "Gemini API key is not configured. Please enter your Gemini API key in Settings.",
    });
  }

  // If TTS quota is already in cooldown, return immediately with zero delay
  if (isModelCoolingDown("gemini-3.1-flash-tts-preview") || isModelCoolingDown("tts")) {
    return res.json({ useNativeFallback: true, message: "Use local speech fallback (cloud TTS cooling down)" });
  }

  try {
    const cleanText = text.replace(/[*_~`#]/g, "").trim().slice(0, 450);
    const candidateModels = [
      "gemini-3.1-flash-tts-preview",
      "gemini-2.5-flash-preview-tts",
      "gemini-2.0-flash-exp",
    ];

    let base64Data: string | undefined;

    for (const model of candidateModels) {
      if (isModelCoolingDown(model)) {
        continue;
      }
      try {
        const response = await ai.models.generateContent({
          model,
          contents: [{ parts: [{ text: cleanText }] }],
          config: {
            responseModalities: ["AUDIO"],
            speechConfig: {
              voiceConfig: {
                // Kore provides natural conversational female voice for Hindi, Hinglish & English
                prebuiltVoiceConfig: { voiceName: "Kore" },
              },
            },
          },
        });

        const part = response.candidates?.[0]?.content?.parts?.[0];
        if (part?.inlineData?.data) {
          base64Data = part.inlineData.data;
          break;
        }
      } catch (modelErr: any) {
        const errMsg = modelErr?.message || String(modelErr);
        if (errMsg.includes("429") || errMsg.includes("RESOURCE_EXHAUSTED") || errMsg.includes("quota")) {
          // Free tier TTS quota is exhausted or rate limited; cool down TTS models for 15s then retry
          markModelRateLimited("gemini-3.1-flash-tts-preview", 15);
          markModelRateLimited("tts", 15);
          console.info(`[VoiceEngine] Gemini TTS quota limit reached, seamlessly routing to native speech synthesis.`);
        } else {
          console.info(`[VoiceEngine] TTS transient issue on ${model}, routing to native speech: ${errMsg.slice(0, 100)}`);
        }
      }
    }

    if (!base64Data) {
      return res.json({ useNativeFallback: true, message: "Use local speech fallback" });
    }

    const rawPcm = Buffer.from(base64Data, "base64");
    const wavBuffer = pcmToWav(rawPcm, 24000, 1);

    res.setHeader("Content-Type", "audio/wav");
    res.setHeader("Content-Length", wavBuffer.length.toString());
    res.setHeader("Cache-Control", "public, max-age=3600");
    return res.send(wavBuffer);
  } catch (err: any) {
    console.error("Gemini TTS Error:", err);
    return res.json({ useNativeFallback: true, error: err?.message || String(err) });
  }
});

// Gemini Multimodal Audio Transcription Endpoint (Guaranteed Speech-to-Text)
app.post("/api/gemini/transcribe", async (req, res) => {
  const { audioBase64, mimeType = "audio/webm", apiKey } = req.body;
  if (!audioBase64 || typeof audioBase64 !== "string") {
    return res.status(400).json({ error: "audioBase64 is required" });
  }

  const ai = getGeminiClient(apiKey);
  if (!ai) {
    return res.status(401).json({
      error: "API_KEY_MISSING",
      message: "Gemini API key is not configured for cloud audio transcription.",
    });
  }

  try {
    const audioPart = {
      inlineData: {
        mimeType: mimeType || "audio/webm",
        data: audioBase64,
      },
    };

    let text = "";
    const transcribeModels = [
      "gemini-3.5-transcribe",
      "gemini-3.1-flash-lite",
      "gemini-flash-latest",
    ];

    for (const model of transcribeModels) {
      if (isModelCoolingDown(model)) {
        continue;
      }
      try {
        const response = await ai.models.generateContent({
          model,
          contents: {
            parts: [
              audioPart,
              {
                text: "You are an accurate audio speech-to-text transcriber. Listen carefully to the voice in this audio and transcribe what was spoken in Hindi, Hinglish, or English. Return ONLY the transcribed text. Do not add quotes, commentary, or punctuation labels. If there is no speech or only background silence, return an empty string.",
              },
            ],
          },
        });
        text = response.text?.trim() || "";
        if (text) {
          break;
        }
      } catch (modelErr: any) {
        const errMsg = modelErr?.message || String(modelErr);
        if (errMsg.includes("429") || errMsg.includes("RESOURCE_EXHAUSTED") || errMsg.includes("quota")) {
          markModelRateLimited(model, 60);
        }
        console.warn(`Transcribe failed on ${model}:`, errMsg);
      }
    }

    return res.json({ text });
  } catch (err: any) {
    console.error("Gemini Transcription Error:", err);
    return res.status(500).json({
      error: "Transcription failed",
      message: err?.message || String(err),
    });
  }
});

// Global Error Handler for JSON parsing & payload limits
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  if (err?.type === "entity.too.large") {
    return res.status(413).json({
      error: "PAYLOAD_TOO_LARGE",
      message: "Audio payload exceeds allowed size.",
    });
  }
  if (err) {
    console.error("Server Error:", err);
    return res.status(500).json({
      error: "SERVER_ERROR",
      message: err.message || "Internal server error",
    });
  }
  next();
});

// Production and Vite Dev Middleware
async function startServer() {
  const httpServer = http.createServer(app);

  // Gemini Live Real-time Audio WebSocket Server (noServer mode to prevent upgrade conflicts with Vite HMR)
  const wss = new WebSocketServer({ noServer: true });

  wss.on("error", (err) => {
    console.warn("[Gemini Live WSS Error]:", err);
  });

  wss.on("connection", async (clientWs: WebSocket) => {
    let session: any = null;
    let isClosed = false;

    const safeClientSend = (payload: any) => {
      if (!isClosed && clientWs.readyState === WebSocket.OPEN) {
        try {
          clientWs.send(typeof payload === "string" ? payload : JSON.stringify(payload));
        } catch (sendErr) {
          console.warn("[WS client send error]:", sendErr);
        }
      }
    };

    clientWs.on("error", (err) => {
      console.warn("[Client WS error]:", err?.message || err);
    });

    clientWs.on("message", async (rawData: any) => {
      try {
        const msg = JSON.parse(rawData.toString());

        if (msg.type === "init") {
          // If a previous session exists for this connection, close it first
          if (session) {
            try {
              session.close?.();
            } catch (_) {}
            session = null;
          }

          const clientAi = getGeminiClient(msg.apiKey);
          if (!clientAi) {
            safeClientSend({ type: "error", message: "Gemini API key is not configured" });
            return;
          }

          const candidateModels = [
            "gemini-2.5-flash-native-audio-latest",
            "gemini-2.5-flash-native-audio-preview-09-2025",
            "gemini-3.5-transcribe-live",
          ];

          let connectedModel: string | null = null;
          let lastErrorMsg = "Live session failed";

          for (const modelCandidate of candidateModels) {
            try {
              session = await clientAi.live.connect({
                model: modelCandidate,
                config: {
                  responseModalities: [Modality.AUDIO],
                  speechConfig: {
                    voiceConfig: {
                      prebuiltVoiceConfig: { voiceName: msg.voice || "Kore" },
                    },
                  },
                  systemInstruction: { parts: [{ text: VASU_SYSTEM_INSTRUCTION }] },
                  outputAudioTranscription: {},
                  inputAudioTranscription: {},
                },
                callbacks: {
                  onmessage: (serverMessage: any) => {
                    if (isClosed) return;

                    // Stream 24kHz PCM audio chunks to client
                    const parts = serverMessage.serverContent?.modelTurn?.parts;
                    if (parts && Array.isArray(parts)) {
                      for (const part of parts) {
                        if (part.inlineData?.data) {
                          safeClientSend({ type: "audio", data: part.inlineData.data });
                        }
                        if (part.text) {
                          safeClientSend({ type: "transcript", text: part.text });
                        }
                      }
                    }

                    // Handle model turn complete
                    if (serverMessage.serverContent?.turnComplete) {
                      safeClientSend({ type: "turnComplete" });
                    }

                    // Handle user interruption
                    if (serverMessage.serverContent?.interrupted) {
                      safeClientSend({ type: "interrupted" });
                    }
                  },
                  onerror: (err: any) => {
                    console.warn(`[Gemini Live Session Error on ${modelCandidate}]:`, err?.message || err);
                    if (!isClosed) {
                      safeClientSend({ type: "error", message: err?.message || String(err) });
                    }
                  },
                  onclose: () => {
                    if (!isClosed) {
                      safeClientSend({ type: "closed" });
                    }
                  },
                },
              });

              connectedModel = modelCandidate;
              break;
            } catch (liveErr: any) {
              lastErrorMsg = liveErr?.message || String(liveErr);
              console.warn(`[Gemini Live connect on ${modelCandidate} failed]:`, lastErrorMsg);
            }
          }

          if (connectedModel) {
            safeClientSend({ type: "ready", voice: "Kore", model: connectedModel });
          } else {
            console.error("Gemini Live connection failed on all models:", lastErrorMsg);
            safeClientSend({ type: "error", message: lastErrorMsg });
          }
        } else if (msg.type === "text" && session && msg.text) {
          // Send text prompt to Gemini Live session to request spoken audio
          try {
            session.sendClientContent({
              turns: [{ role: "user", parts: [{ text: msg.text }] }],
              turnComplete: true,
            });
          } catch (textErr: any) {
            console.warn("[Gemini Live send text error]:", textErr);
            safeClientSend({ type: "error", message: "Failed to send text to Live session: " + (textErr?.message || textErr) });
          }
        } else if (msg.type === "audio" && session && msg.data) {
          // Stream 16kHz PCM chunk to Gemini Live
          try {
            session.sendRealtimeInput({
              audio: { data: msg.data, mimeType: "audio/pcm;rate=16000" },
            });
          } catch (audioErr) {
            console.warn("[Gemini Live send audio error]:", audioErr);
          }
        } else if (msg.type === "interrupt") {
          // Client user started speaking: cancel model speech immediately
          if (session) {
            try {
              session.sendRealtimeInput({
                audio: { data: "", mimeType: "audio/pcm;rate=16000" },
              });
            } catch (_) {}
          }
        }
      } catch (err: any) {
        console.error("WS message processing error:", err);
      }
    });

    clientWs.on("close", () => {
      isClosed = true;
      try {
        session?.close?.();
      } catch (_) {}
      session = null;
    });
  });

  // Dedicated HTTP Upgrade Routing:
  // Route /api/live directly to Gemini Live WebSocketServer
  // Forward all other upgrades to Vite dev server HMR without aborting
  httpServer.on("upgrade", (req, socket, head) => {
    try {
      const url = new URL(req.url || "", `http://${req.headers.host || "localhost"}`);
      if (url.pathname === "/api/live") {
        wss.handleUpgrade(req, socket, head, (ws) => {
          wss.emit("connection", ws, req);
        });
        return;
      }
      // If not /api/live and in production, terminate unrecognized upgrade
      if (process.env.NODE_ENV === "production") {
        socket.destroy();
      }
      // In dev mode, leave socket open for Vite's HMR listener to handle
    } catch (err) {
      console.warn("[Upgrade Routing Error]:", err);
      try {
        socket.destroy();
      } catch (_) {}
    }
  });

  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: {
          server: httpServer,
        },
      },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  httpServer.listen(PORT, "0.0.0.0", () => {
    console.log(`VASU Assistant server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
