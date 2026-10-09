# FREE_APIS.md — VASU Android: every external network call and what it costs

Rule: the app works out of the box on free/$0 services. Anything billable
requires a user-supplied key and is never called without one.

Default provider: **Gemini** (`AiProviderConfig.GEMINI`, `AIOrchestrator` tries
Gemini first, Claude only if Gemini has no key). Default model chain is all
free-tier Flash models. No code path calls Claude (or any paid API) without a
stored user key (`ClaudeProvider.isConfigured = hasClaudeKey()`).

| Service | Endpoint | Cost tier | Key source (`SecureKeyStore`) | Offline / quota fallback |
|---|---|---|---|---|
| Gemini chat (default) | `https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent`, `:streamGenerateContent?alt=sse`, `GET /models` | Free tier (user's Google AI Studio quota) | `gemini_api_key` — user-entered in Settings; dev-only `GEMINI_API_KEY` env/prop | Model chain `gemini-2.0-flash` → `2.5-flash` → `2.0-flash-001` → `1.5-flash` → `flash-latest` (`allowModelFallback` user-toggle, default on); quota/offline → script-aware spoken error, on-device commands keep working |
| Claude chat (opt-in only) | `https://api.anthropic.com/v1/messages` (or user-overridden `claudeBaseUrl`, e.g. OmniRoute) | **Paid** — spends the user's own Anthropic/route credit | `claude_api_key` — user-entered in Settings; dev-only `CLAUDE_API_KEY` / `ANTHROPIC_API_KEY` / `OMNIROUTE_API_KEY` env/prop | Reached only when Gemini has no key AND Claude key present; `allowFallback=false` so no silent model switch; failure → spoken error, no auto-fallback to Gemini mid-turn |
| Gemini TTS (online voice) | Same host, `generateContent` with `responseModalities=AUDIO`, voice `Kore` | Same key/quota as Gemini chat (free tier) | Same Gemini key | Chain `gemini-3.1-flash-tts-preview` → `2.5-flash-preview-tts` → `2.0-flash`; `VoiceRouter` falls back to LocalTtsEngine → Android system TTS (opt-in) |
| Gemini Live realtime | `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`, model `gemini-2.0-flash-exp` | Same key/quota as Gemini chat (free tier) | Same Gemini key (passed per-session) | Falls back to normal cloud/local turn path |
| ML Kit on-device (barcode, object, OCR incl. Devanagari) | None (on-device) | $0 / always-available | None | N/A — works offline |
| TFLite on-device (wake word `hello_vasu.tflite`, guardian `silero_vad` / `ecapa6s`) | None (bundled assets) | $0 / always-available | None | Missing asset → feature inert, app unchanged |
| Android STT (`SpeechRecognizer`, `EXTRA_PREFER_OFFLINE`) | OS service (Google recognition service when online) | $0 to app (OS-provided) | None | On-device recognizer when offline; error → typed `SttErrorKind` + retry |
| Android / Local TTS (`TextToSpeech`, `HindiSpeechService`, `LocalTtsEngine`) | None (on-device) | $0 / always-available | None | N/A — final fallback for all voice |
| Telemetry | `https://telemetry.vasu.app/events` (batched via WorkManager) | $0 to user (first-party) | None | `TelemetryManager.setEnabled(false)` opts out; upload failure retries, never blocks UX |
| User-initiated intents only (no API spend) | OAuth authorize URLs (Google/GitHub/Discord/Spotify/Twitter), `google.com/search`, `wa.me` share links | $0 | None (user's own logged-in sessions) | Opened only on explicit user action |

Notes:
- `app/build.gradle.kts` contains no billed SDKs (OkHttp, ML Kit, TFLite,
  Hilt, `security-crypto`, `work-runtime` only). No OpenAI/ElevenLabs/Azure SDK.
- Keys in `.env.example` for Tavily/Brave/Groq/DeepSeek/xAI belong to the web
  companion (`src/`, `server.ts`); the Android app does not call them.
- Keys are stored in `EncryptedSharedPreferences` (Keystore) and sent via
  `x-goog-api-key` / `x-api-key` headers, never URLs; keys are never logged.
