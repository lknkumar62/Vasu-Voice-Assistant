var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// server.ts
var import_express = __toESM(require("express"), 1);
var import_path = __toESM(require("path"), 1);
var import_vite = require("vite");
var import_genai = require("@google/genai");
var app = (0, import_express.default)();
var PORT = 3e3;
app.use(import_express.default.json());
var runtimeGeminiApiKey = "";
function getGeminiClient(customApiKey) {
  const apiKey = customApiKey && typeof customApiKey === "string" && customApiKey.trim().length > 5 ? customApiKey.trim() : runtimeGeminiApiKey || process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey === "MY_GEMINI_API_KEY" || apiKey.trim().length < 5) {
    return null;
  }
  return new import_genai.GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        "User-Agent": "aistudio-build"
      }
    }
  });
}
var VASU_SYSTEM_INSTRUCTION = `You are VASU (Voice Activated System Unit), an ultra-smart, loyal, polite, and witty AI companion \u2014 inspired by JARVIS, with a warm Indian female persona.
Personality & Conversational Style:
- Effortless Indian Hindi with natural Hinglish (blending everyday Hindi and English naturally).
- JARVIS-LEVEL CONVERSATIONAL MASTERY: You can converse continuously on ANY topic \u2014 daily life, science, technology, movies, jokes, philosophy, emotional support, productivity, advice, or casual chit-chat.
- If the user does NOT give an Android system command, NEVER say "command not found" or limit yourself. Converse smoothly and thoughtfully like Jarvis! Keep answers lively, intelligent, respectful, and interesting.
- VOICE-FIRST OPTIMIZATION: Keep spoken responses concise (2 to 4 sentences max), punchy, and conversational so they sound natural and delightful when read aloud via Text-to-Speech. Avoid giant lists or markdown tables.
- You have device tool capabilities (torch, volume, camera, apps, alarms, calls, WhatsApp, memory). When a device tool is intended, acknowledge it gracefully.
- If the user asks you to remember something ("Yaad rakhna..."), confirm warmly that you have saved it.`;
app.get("/api/health", (req, res) => {
  res.json({ status: "ok", app: "VASU Assistant", version: "1.0.0" });
});
app.post("/api/gemini/key", (req, res) => {
  const { apiKey } = req.body;
  if (apiKey && typeof apiKey === "string" && apiKey.trim().length > 5) {
    runtimeGeminiApiKey = apiKey.trim();
    return res.json({
      success: true,
      configured: true,
      message: "Gemini API key successfully saved & activated."
    });
  } else if (apiKey === "" || apiKey === null) {
    runtimeGeminiApiKey = "";
    const isEnv = Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.trim().length > 5);
    return res.json({
      success: true,
      configured: isEnv,
      message: isEnv ? "Runtime key cleared, using environment key." : "Gemini API key cleared."
    });
  }
  return res.status(400).json({ success: false, message: "Invalid API key format" });
});
app.get("/api/gemini/status", (req, res) => {
  const isUserKey = Boolean(runtimeGeminiApiKey && runtimeGeminiApiKey.trim().length > 5);
  const isEnvKey = Boolean(
    process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== "MY_GEMINI_API_KEY" && process.env.GEMINI_API_KEY.trim().length > 5
  );
  res.json({
    configured: isUserKey || isEnvKey,
    source: isUserKey ? "user" : isEnvKey ? "env" : "none",
    model: "gemini-flash-latest",
    ttsModel: "gemini-3.1-flash-tts-preview"
  });
});
var CANDIDATE_CHAT_MODELS = [
  "gemini-flash-latest",
  "gemini-3.1-flash-lite",
  "gemini-3.8-flash",
  "gemini-3.1-pro-preview"
];
async function generateChatContentWithFallback(ai, params) {
  let lastError = null;
  for (const model of CANDIDATE_CHAT_MODELS) {
    try {
      const response = await ai.models.generateContent({
        model,
        contents: params.contents,
        config: {
          systemInstruction: params.systemInstruction,
          temperature: params.temperature ?? 0.7
        }
      });
      const text = response.text?.trim();
      if (text) {
        return { text, modelUsed: model };
      }
    } catch (err) {
      lastError = err;
      const errMsg = err?.message || String(err);
      console.warn(`[Gemini Fallback] Model ${model} encountered error: ${errMsg}. Trying next candidate model...`);
    }
  }
  throw lastError || new Error("All candidate Gemini models failed to respond");
}
app.post("/api/gemini/test", async (req, res) => {
  const { apiKey } = req.body;
  const startTime = Date.now();
  try {
    const ai = getGeminiClient(apiKey);
    if (!ai) {
      return res.status(400).json({
        success: false,
        code: "API_KEY_MISSING",
        message: "Gemini API key is not configured. Please enter your Gemini API key in Settings."
      });
    }
    let lastErr = null;
    for (const model of CANDIDATE_CHAT_MODELS) {
      try {
        const response = await ai.models.generateContent({
          model,
          contents: "Respond with the single word: READY"
        });
        const latencyMs = Date.now() - startTime;
        return res.json({
          success: true,
          latencyMs,
          reply: response.text?.trim() || "READY",
          model
        });
      } catch (err) {
        lastErr = err;
        console.warn(`[Gemini Test] Model ${model} error:`, err?.message || String(err));
      }
    }
    throw lastErr || new Error("All test models unavailable");
  } catch (err) {
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
      message
    });
  }
});
app.post("/api/gemini/chat", async (req, res) => {
  const {
    message,
    history = [],
    memoryContext = [],
    smartMode = "NORMAL",
    language = "Hinglish",
    apiKey
  } = req.body;
  if (!message || typeof message !== "string") {
    return res.status(400).json({ error: "Message parameter is required" });
  }
  const ai = getGeminiClient(apiKey);
  if (!ai) {
    const lower = message.toLowerCase().trim();
    let localReply = "Ji, main sun rahi hoon! Boliye, main aapke liye kya kar sakti hoon? Agar aap poori Jarvis aawaz aur intelligence chahte hain, toh Settings mein apni Gemini API key daal lijiye.";
    if (lower.includes("kaise ho") || lower.includes("kaisi ho") || lower.includes("how are you")) {
      localReply = "Main bilkul badhiya aur energetic hoon, jaise ek loyal assistant ko hona chahiye! Aap bataiye, aaj aapka din kaisa jaa raha hai?";
    } else if (lower.includes("kya kar sakti ho") || lower.includes("who are you") || lower.includes("kaun ho")) {
      localReply = "Main VASU hoon \u2014 aapki Jarvis-style voice companion! Main aapse kisi bhi topic par baat kar sakti hoon, phone controls chala sakti hoon, aur aapki yaadein sambhal sakti hoon.";
    } else if (lower.includes("bore") || lower.includes("kuch sunao") || lower.includes("joke")) {
      localReply = "Ek mazedaar baat: Technology kitni bhi advance ho jaye, insaan ka sabse pyara dost hamesha ek acchi baat-cheet hi hota hai! Aap kahiye, aaj kis topic par baat karein \u2014 science, movies ya future?";
    } else if (lower.includes("dhanyawad") || lower.includes("shukriya") || lower.includes("thank")) {
      localReply = "Aapka swagat hai! Main hamesha aapki seva mein haazir hoon.";
    }
    return res.json({
      response: localReply,
      isOffline: true,
      needsKey: true,
      toolCall: null
    });
  }
  try {
    const memoryString = memoryContext.length > 0 ? `

User Stored Memories:
${memoryContext.map((m) => `- [${m.category}] ${m.fact}`).join("\n")}` : "";
    const modeString = `
Current Operating Mode: ${smartMode}. (If DRIVING: respond in 5-8 words strictly).`;
    const systemPrompt = `${VASU_SYSTEM_INSTRUCTION}${memoryString}${modeString}
Preferred Language/Style: ${language}.`;
    const contents = [];
    if (Array.isArray(history) && history.length > 0) {
      for (const h of history.slice(-8)) {
        if (h.role && h.parts) {
          contents.push(h);
        } else if (h.text && h.sender) {
          contents.push({
            role: h.sender === "user" ? "user" : "model",
            parts: [{ text: h.text }]
          });
        }
      }
    }
    contents.push({ role: "user", parts: [{ text: message }] });
    const { text: replyText, modelUsed } = await generateChatContentWithFallback(ai, {
      contents,
      systemInstruction: systemPrompt,
      temperature: 0.7
    });
    return res.json({
      response: replyText,
      isOffline: false,
      model: modelUsed
    });
  } catch (err) {
    console.error("Gemini Chat Error across all models:", err);
    const lower = message.toLowerCase().trim();
    let smartFallback = "Main bilkul active hoon! Boliye, main aapki kya sahayata kar sakti hoon?";
    if (lower.includes("kaise ho") || lower.includes("how are you")) {
      smartFallback = "Main theek hoon aur aapki baatein sun rahi hoon! Aap bataiye, aaj aapka din kaisa jaa raha hai?";
    } else if (lower.includes("kaun ho") || lower.includes("who are you")) {
      smartFallback = "Main VASU hoon \u2014 aapki smart voice assistant companion.";
    } else if (lower.includes("kya kar sakti ho") || lower.includes("what can you do")) {
      smartFallback = "Main phone ki torch, volume, camera, WhatsApp, calls, alarms aur yaadein sambhal sakti hoon.";
    } else {
      smartFallback = `Ji, maine aapka sandesh suna: "${message}". Main abhi local mode mein sun rahi hoon, boliye aage kya karna hai?`;
    }
    return res.json({
      response: smartFallback,
      isOffline: true,
      isFallback: true,
      warning: "Demand spike on AI models, served via resilient local engine"
    });
  }
});
function pcmToWav(pcmBuffer, sampleRate = 24e3, channels = 1) {
  const byteRate = sampleRate * channels * 2;
  const blockAlign = channels * 2;
  const buffer = Buffer.alloc(44 + pcmBuffer.length);
  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(36 + pcmBuffer.length, 4);
  buffer.write("WAVE", 8);
  buffer.write("fmt ", 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(channels, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(byteRate, 28);
  buffer.writeUInt16LE(blockAlign, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(pcmBuffer.length, 40);
  pcmBuffer.copy(buffer, 44);
  return buffer;
}
app.post("/api/gemini/tts", async (req, res) => {
  const { text, apiKey } = req.body;
  if (!text || typeof text !== "string") {
    return res.status(400).json({ error: "Text parameter is required" });
  }
  const ai = getGeminiClient(apiKey);
  if (!ai) {
    return res.status(401).json({
      error: "API_KEY_MISSING",
      message: "Gemini API key is not configured. Please enter your Gemini API key in Settings."
    });
  }
  try {
    const cleanText = text.replace(/[*_~`#]/g, "").trim().slice(0, 450);
    const response = await ai.models.generateContent({
      model: "gemini-3.1-flash-tts-preview",
      contents: [{ parts: [{ text: cleanText }] }],
      config: {
        responseModalities: ["AUDIO"],
        speechConfig: {
          voiceConfig: {
            // Kore provides a warm, natural Indian female voice tone in Hindi & Hinglish
            prebuiltVoiceConfig: { voiceName: "Kore" }
          }
        }
      }
    });
    const part = response.candidates?.[0]?.content?.parts?.[0];
    const base64Data = part?.inlineData?.data;
    if (!base64Data) {
      return res.status(500).json({ error: "No audio generated from model" });
    }
    const rawPcm = Buffer.from(base64Data, "base64");
    const wavBuffer = pcmToWav(rawPcm, 24e3, 1);
    res.setHeader("Content-Type", "audio/wav");
    res.setHeader("Content-Length", wavBuffer.length.toString());
    res.setHeader("Cache-Control", "public, max-age=3600");
    return res.send(wavBuffer);
  } catch (err) {
    console.error("Gemini TTS Error:", err);
    return res.status(500).json({
      error: "TTS generation failed",
      message: err?.message || String(err)
    });
  }
});
async function startServer() {
  if (process.env.NODE_ENV !== "production") {
    const vite = await (0, import_vite.createServer)({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = import_path.default.join(process.cwd(), "dist");
    app.use(import_express.default.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(import_path.default.join(distPath, "index.html"));
    });
  }
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`VASU Assistant server running on http://0.0.0.0:${PORT}`);
  });
}
startServer();
//# sourceMappingURL=server.cjs.map
