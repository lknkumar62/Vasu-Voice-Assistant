# VASU AI — Progress

## Status Legend: TODO | IN PROGRESS | DONE | BLOCKED

### Phase 1 — Voice Foundation
- [ ] 1.1 Project setup + Gradle + CI workflow :: TODO
- [ ] 1.2 Theme + typography + GlassCard :: TODO
- [ ] 1.3 Permission Center :: TODO
- [ ] 1.4 Home + animated orb :: TODO
- [ ] 1.5 STT manager :: TODO
- [ ] 1.6 TTS manager :: TODO
- [ ] 1.7 Assistant state machine + HomeViewModel :: TODO
- [ ] 1.8 Chat + Room :: TODO
- [ ] 1.9 Settings shell + DataStore :: TODO

### Phase 2 — Offline Commands
- [ ] 2.1 Command parser + risk classifier :: TODO
- [ ] 2.2 Executor (flashlight, volume, time, battery) :: TODO
- [ ] 2.3 Vosk model manager :: TODO
- [ ] 2.4 Vosk offline STT integration :: TODO

### Phase 3 — App Control
- [ ] 3.1 App launcher mapping :: TODO
- [ ] 3.2 Contact lookup + calling :: TODO
- [ ] 3.3 Hinglish normalizer :: TODO
- [ ] 3.4 Command library :: TODO

### Phase 4 — Service / Persistence
- [ ] 4.1 Foreground mic service :: TODO
- [ ] 4.2 Notification controls (optional) :: TODO
- [ ] 4.3 Boot receiver + battery optimization :: TODO

### Phase 5 — Wake Word
- [ ] 5.1 Porcupine integration :: TODO
- [ ] 5.2 Wake setup screen :: TODO
- [ ] 5.3 Service + wake → STT handoff :: TODO

### Phase 6 — Automation
- [ ] 6.1 Accessibility service :: TODO
- [ ] 6.2 Node finder + actions :: TODO
- [ ] 6.3 Step planner (multi-step) :: TODO
- [ ] 6.4 WhatsApp flow :: TODO
- [ ] 6.5 Risk confirmation + emergency stop :: TODO

### Phase 7 — Overlay
- [ ] 7.1 Overlay service :: TODO
- [ ] 7.2 Draggable orb :: TODO
- [ ] 7.3 Mini voice panel :: TODO

### Phase 8 — AI
- [ ] 8.1 AiProvider interface :: TODO
- [ ] 8.2 OpenAI / OpenRouter / Groq / Anthropic :: TODO
- [ ] 8.3 Streaming SSE :: TODO
- [ ] 8.4 Tool calling :: TODO
- [ ] 8.5 Memory injection + screen :: TODO
- [ ] 8.6 API settings + test-connection :: TODO

### Phase 9 — Finalize
- [ ] 9.1 Hybrid router :: TODO
- [ ] 9.2 Performance / battery :: TODO
- [ ] 9.3 Error handling pass :: TODO
- [ ] 9.4 Final QA + release build :: TODO

## Known Issues
- User's phone: app crash on opening Chat (v4.18.6 installed) — awaiting hidden error-log paste from diag build (artifact of CI run `85c9132`).
- Package: keeping `com.vasu.assistant` for now (preserves installed app data + keys). Any rename needs explicit user approval.
- Spec-vs-repo gap audit DONE (`docs/GAP_AUDIT.md`); fix queue executing — FIX-1..5 done (wake word; boot/FGS; streaming prompt/tools/memory; HIGH-risk confirmation + OTP guard + emergency stop; chat sessions/export/long-press/word-stream + Room v2 migration); FIX-12 done out of order (voice-pipeline blockers: binary Live frames, current live model, background wake→command STT loop, double-TTS guard); repo-wide health audit reported (queue FIX-12..20 — remaining: mission/permission wiring, messaging honesty, UI wiring); FIX-6 (STT settings + voice/gender/model picker) next.

## Decisions Log
- 2026-10-16 Package: namespace/applicationId stay `com.vasu.assistant` (reverted an accidental flip). Why: installed-app data, chat history and stored keys survive; 150+ existing files keep compiling; user rule = no package rename without approval.
- 2026-10-16 Fonts: bundling real .ttf files; using Orbitron + Exo 2 + Rajdhani/Raleway (PIL/DPI safe; warn if any OSS family requires weights > 400 — will verify while bundling).
- 2026-10-16 Persistence split: Chat/Memory → Room; settings toggle → DataStore; keys → EncryptedSharedPreferences.
- 2026-10-16 FIX-1 wake word: `ROLLING_WINDOW_SAMPLES` derived from `WakeWordModel.INPUT_SIZE` (25,344 = 98 frames; old 25,088 gave 97 → gate never passed). Detection now sets state IDLE + `shouldBeListening`, and `resumeAfterSpeechRecognition()` re-arms capture — detector survives beyond the first wake. Interpreter closed before reload. Regression test added.
- 2026-10-16 Error handling: reuse existing hidden `com.vasu.assistant.core.logging.ErrorLog` (capped 256 KB, app-private, no UI reads it) instead of a duplicate logger.
- 2026-10-16 FIX-2 boot/FGS: pre-Android-11 boots start the mic service directly; 11+ gets a "tap to resume" notification (boot mic-FGS disallowed; 14+ throws). Unknown service actions never start the mic pipeline; failed foreground starts degrade to DATA_SYNC, and `stopSelf()` is the last resort instead of a RemoteServiceException.
- 2026-10-16 FIX-3 streaming: tool-relevant turns (keyword filter on the current input only) route to the non-streaming tool loop for real execution; the streaming prompt excludes the tool section entirely; explicit remember/forget commands short-circuit before every local matcher; auto-learning from generic turns stays off (UserMemory name heuristics too loose). Residual trade-off: broad keywords ("time") route casual chat into the slower tool path; the tool-restraint prompt line prevents unwanted calls.
- 2026-10-16 FIX-4 safety: HIGH-risk tools ask a script-aware Haan/Nahi confirmation whenever autonomous execution is off (it stays ON by default, preserving current behavior); OTP/payment context blocks communication tools regardless of that setting (`otpProtectionEnabled`, default on, wired to the Privacy toggle); emergency stop = service shutdown + `AutomationEngine.stop()` + `MissionEngine.cancelAll()` (cooperative step-boundary checks).
- 2026-10-16 FIX-5 chat: sessions are real Room rows (one per chat, auto-titled from the first user message, latest resumes on open; Clear now clears only the current chat); word-streaming is sentence-level in sync with TTS (not token-level); Room v1→v2 rebuilds the messages table (no SQL defaults — Room validates defaults) and backfills legacy per-ViewModel ids into separate "Chat" sessions; export is plain-text share; delete-chat has no confirm (personal-app trade-off).
- 2026-10-16 FIX-12 voice: server messages arrive as **binary** WS frames — a bytes `onMessage` override was the missing piece (setup never completed); endpoint → v1beta; model → `models/gemini-3.8-live` (retry once with `models/gemini-live-2.5-flash-native-audio`); audio → `realtimeInput.audio` (`audio/pcm;rate=16000`); text → `realtimeInput.text`; payloads in a pure testable `GeminiLiveProtocol`. Background wake→command: the service captures one STT turn (12s) → `AIOrchestrator` → TTS, then waits for the speech queue to drain (≤60s cap) before re-arming the wake word; a live-session failure reroutes the wake to this loop instead of dropping the command. `VoiceRouter` fallback hops are once-per-call — engines signalling failure both via `onError` and a false return no longer double-speak.
