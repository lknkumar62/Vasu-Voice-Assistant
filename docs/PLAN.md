# VASU AI — Product Plan

**Package:** `com.vasuai.assistant`
**minSdk:** 26, **targetSdk/compileSdk:** 34
**Stack:** Kotlin, Jetpack Compose + Material 3, MVVM + StateFlow, Room, DataStore, Coroutines, Hilt
**Architecture:** Clean layers — `ui`, `voice`, `ai`, `offline`, `automation`, `accessibility`, `overlay`, `services`, `wakeword`, `memory`, `settings`, `permissions`

## Product Summary
Jarvis-style voice assistant for Android. One-screen chat, live orb, background wake-word listening, hybrid (online/offline) brain, app-wide tool automation with risk gating, floating orb, local command intelligence. APIs via user keys only. Free-tier by default.

## High-Level Data Flow
```
Mic (foreground service) -> WakeWord(Porcupine) / STT(Android+Vosk) ->
  Normalizer (EN/HI/Hinglish) -> IntentParser (offline rules first)
  -> RiskChecker (role) -> Executor (app/device/automation/AI tool) ->
  Result -> TTS (Android, 24kHz apex when cloud) + Chat/card
  -> Memory store on turns
```
State machine: IDLE → WAKE → LISTENING → PROCESSING → SPEAKING → IDLE. ERROR is a sub-state with backoff.

## Tech Decisions
- DI: Hilt (already in repo, kapt).
- DB: Room 2.6.1 (Conversation + Memory).
- Networking: OkHttp 4.12 with streaming + manual SSE.
- Preferences: DataStore (Preferences) for settings; Room for chat/memory; EncryptedSharedPreferences for keys + biometrics.
- Wake word: Porcupine .ppn + AccessKey (user supplies).
- Offline STT: Vosk (small English + Hindi), downloadable via UI.
- Wake word falls back to "Vasu" phrase from SpeechRecognizer if Porcupine not configured.
- Reset/recovery: ErrorLog (hidden) never visible to user.

## Android Limits & Fallbacks
- WiFi/BT toggle direct: blocked 10+. Use Settings panel intent or Accessibility tap.
- Background mic: foreground service (microphone) + persistent notification; screen-off impossible for STT, but wake-word survives via foreground service.
- Overlay: SYSTEM_ALERT_WINDOW, user grants manually; boot entry point.
- Accessibility: manual toggle in settings + multi-step automation only after auto.
- Wake word needs user-supplied Picovoice AccessKey + .ppn keyword.
- Vosk model download on first run, show progress.
- Calling by name uses ContactsContract; never `tel:name`.

## Task List (max 5 files / task)

### Phase 1 — Shell, Voice Basics, Chat, Settings
- 1.1 Project setup: Gradle, version catalog, buildConfig, dependencies. `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/build-apk.yml`, `.gitignore`. Depends: none. Acc: `./gradlew assembleDebug` green on CI. Risks: SDK-34 only installed.
- 1.2 Theme + typography: `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`, font resources. Depends on 1.1. Acc: Orbitor/Exo/Rajdhani/Raleway bundled (real .ttf), colors per spec (#020408, #0A1628, #00D4FF, etc.), glass card exists (translucent, 0.5dp border, glow).
- 1.3 Permission Center: one glass card per permission (icon/why/status/Grant) + "denied/don't-ask → open app settings" + no-crash + "speak clear message if missing". Files: `permissions/PermissionManager.kt`, `ui/permissions/PermissionCenterScreen.kt`, `ui/components/GlassCard.kt`. Acc: each permission status (Granted/Denied/Not set) re-evaluated on resume; grant buttons route to app-setings.
- 1.4 Home + animated orb: `ui/home/HomeScreen.kt`, `ui/components/NeonOrb.kt` (Canvas, breathing, rotating arcs, state colors, pulse rings), `ui/components/Waveform.kt`. Taps with haptic: orb tap toggles listen, long-press quick actions. Acc: orb animates at 60fps, respects animations-off switch.
- 1.5 STT manager: `voice/STTManager.kt` (Android SR + Vosk dispatcher), auto-switch by network + user override, continuous listening with auto-restart, partial streaming, RMS. Acc: partial text updates ≤300ms; error codes map to speakable strings; safe with mic permission missing.
- 1.6 TTS manager: `voice/TTSManager.kt` (Android TTS, voices male-Jarvis / female "Nova", speed/pitch, interrupt on user speech/Stop), audio focus ducking. States respected. Acc: stop works within one buffer; offline fallback speaks; speakingId tracked in state.
- 1.7 Assistant state machine + ViewModel: `services/AssistantState.kt` (sealed: IDLE/WAKE/LISTENING/PROCESSING/SPEAKING/ERROR) + `ui/home/HomeViewModel.kt`. Acc: transitions validated, one state at a time, ERROR always resets to IDLE via 5s timeout.
- 1.8 Chat + Room: `database/*` entities/DAO, `ui/chat/ChatScreen.kt` (history, bubbles, long-press copy/share/speak/delete, streaming, session list, export). Acc: insert ≤50ms, offline chat history viewable, no DB on main thread.
- 1.9 Settings shell (DataStore) + screens for each section: `settings/SettingsStore.kt`, `ui/settings/*` screens. Acc: live apply; reset works.

### Phase 2
- 2.1 Norm/Intent parser (offline) + risk classifier, `offline/CommandParser.kt` (EN/HI/Hinglish).
- 2.2 Executor (flashlight/volume/settings/time/battery), `automation/DeviceExecutor.kt`.
- 2.3 Vosk model manager + download UI: `offline/ModelManager.kt`, `ui/offline/ModelScreen.kt`.
- 2.4 Vosk STT integration into STTManager.

### Phase 3
- 3.1 App launcher mapping (`automation/AppLauncher.kt`).
- 3.2 Contact lookup + calling (`ContactLookup.kt`, `CallManager.kt` using ACTION_DIAL/call permission).
- 3.3 Hinglish normalizer.
- 3.4 Command library screen (`CommandLibraryScreen.kt`).

### Phase 4
- 4.1 Foreground mic service (type microphone + notification).
- 4.2 Notification controls (read/send reply optional).
- 4.3 Boot receiver + battery-optimization flow + survive screen lock.

### Phase 5
- 5.1 Porcupine init + setup (AccessKey, .ppn upload).
- 5.2 Wake setup screen.
- 5.3 Service + wake → STT handoff.

### Phase 6
- 6.1 AccessibilityService.
- 6.2 Node finder + actions.
- 6.3 Step planner + multi-step.
- 6.4 WhatsApp flow.
- 6.5 Risk confirmation + emergency stop.

### Phase 7
- 7.1 Overlay service.
- 7.2 Draggable orb.
- 7.3 Mini voice panel.

### Phase 8
- 8.1 AiProvider interface.
- 8.2 OpenAI/OpenRouter/Groq/Anthropic clients.
- 8.3 Streaming SSE.
- 8.4 Tool calling.
- 8.5 Memory injection + screen.
- 8.6 API settings + test-connection.

### Phase 9
- 9.1 Hybrid router (auto online/offline).
- 9.2 Performance/battery.
- 9.3 Error handling pass.
- 9.4 Final QA + release build.

Plus: Gradle + CI workflow, ProGuard rules, licenses attribution.

## TODO Before Code Per Phase (manifest list)
- Phase 1: RECORD_AUDIO, POST_NOTIFICATIONS, STORAGE-scoped (none needed).
- Phase 2: CAMERA (flashlight via camera).
- Phase 3: CALL_PHONE, READ_CONTACTS, SCHEDULE_EXACT_ALARM.
- Phase 4: FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
- Phase 6: BIND_ACCESSIBILITY_SERVICE, QUERY_ALL_PACKAGES.
- Phase 7: SYSTEM_ALERT_WINDOW.

## Risk Register
- R1 Wake word requires costly Picovoice key → support falling back to always-listen.
- R2 Accessibility auto-dismissed by users → permission card prominent.
- R3 Android 14 foreground service policy strict on microphone with screen off → use ForegroundService.MICROPHONE + notification; document limitations.
- R4 Vosk model ~50MB; unreliable network → download with retry + resume + free space check.
- R5 Multi-provider rate limits → backoff + offline fallback.
- R6 Play policy on phone automation → keep phone-only, no banking apps, confirmation guardrails.
- R7 Storage size (APK + models) → models external storage, not APK.

## Where To Write Code
Root Gradle project (new module tree under `app/src/main/java/com/vasuai/assistant/`). Existing `com.vasu.assistant` (from prior VASU) remains as reference compilation; we add `com.vasuai.assistant` tree alongside. ApplicationId flips to `com.vasuai.assistant` only after the new shell compiles (so the legacy code never ships; it just sits dead during migration).

## Reference Existing Code (read, don't copy blindly)
- `com.vasu.assistant.core.ai.ToolRouter.kt` — real tool dispatch + risk gate.
- `com.vasu.assistant.core.ai/AIOrchestrator.kt` — provider routing + tool loop.
- `com.vasu.assistant.core/tts/*` — sentence streaming.
- `com.vasu.assistant/ui/permissions/*`, `ui/components/*` — component patterns.
- `com.vasu.assistant/core/logging/ErrorLog.kt` — hidden error log.
- Avoid importing `*` from `core.memory` patterns without verification.

## Coding Rules (non-negotiable)
- No fake success messages — return clear errors.
- All API keys in `SecureKeyStore` (EncryptedSharedPreferences); keys never logged, never in screenshots.
- Every network call handles timeout (15s+15s retries) + offline fallback.
- Banned permissions close to illegal: CALL_PHONE only via user confirmed (or ACTION_DIAL); RECORD_AUDIO via tappable consent; no forced RECORD_AUDIO at boot without consent.
- No secrets in Git; `.gitignore` for keystores/jars.
- Hilt scope: @Singleton for DB/prefs, HiltViewModel for screens.

## Testing
- Unit tests for parser/normalizer/tool router/sentence splitter: target 100+ tests.
- Instrumented smoke (single device): chat open, button actions, no crash.
- Test matrix: Galaxy S21/Android 14, Pixel 7/Android 13, budget phone Android 10, emulator API 26.

## Memory & Context
- Keep `MindTrace VASU_sprint` note (private notepad file `docs/MINDTRACE.md`) for open issues. URL to budget plan / timings.

## Definition Of Done (per task, from user)
- reach UI, no crash, clear error, progress + commit.
- Stop on first build failure; fix before proceeding.
- max 5 files/task.

## Next Action
After I reply "APPROVE", begin with 1.1 + 1.2 in parallel (Gradle + theme) — but per instructions, I will start ONE task at a time. Task 1.1 first. STOP and wait.
