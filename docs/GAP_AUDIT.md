# VASU — Spec vs Repo Gap Audit (2026-10-16)

4 parallel read-only audits of `app/src/main/java/com/vasu/assistant/` against the VASU AI spec.
Status: **COMPLETE** — 4/4 agents reported (A: P1, B: P2-3, C: P4-5, D: P6-9).

---

## Agent B — Phases 2-3 (command system) ✅ REPORTED

| # | Item | Status | Evidence / missing |
|---|------|--------|--------------------|
| 1 | Offline pipeline (normalizer+parser EN/HI/Hinglish, risk) | PARTIAL | `core/command/LocalCommandRouter.kt`: normalize + token match, EN/Devanagari/Roman vocab; torch/volume/battery/time/date only. "open YouTube", "WhatsApp kholo", "call mummy", "go back/home", "scroll down", "click on X" = cloud-only. No input risk classifier (static per-tool RiskLevel + RoleManager gate in `ToolRouter.executeTool`; local routes hardcode LOW) |
| 2 | Device executor | PARTIAL | Torch/volume/time/battery/alarm ✅. WiFi: `toggleWifi()` dead code, no tool = unreachable. Bluetooth: exists in `DeviceControlManager` but reachable only via ungated fast path; replies falsely say "now on/off" (only opens settings). Timer: `setTimer` exists, no tool def |
| 3 | Vosk offline STT + model manager | MISSING (Vosk) / PARTIAL | No vosk dep anywhere. Offline STT only via Android on-device recognizer (API 33+). No model manager. `VasuRecognitionService` = stub that always returns ERROR_CLIENT |
| 4 | Unknown commands never crash | EXISTS | `AIOrchestrator.processInput` safe fallbacks; no crash path found |
| 5 | App open by name | PARTIAL | `ToolRouter.resolvePackageByLabel`: exact→contains, first match; no fuzzy/did-you-mean; no launcher-map cache |
| 6 | Contacts | PARTIAL | `messaging/ContactManager.kt` search + findBestMatch (exact→startsWith→first). Multiple matches NOT surfaced — first silently used. ACTION_DIAL only; CALL_PHONE not in manifest but checked in PermissionsViewModel:126 |
| 7 | Hinglish normalizer | PARTIAL | `LanguageDetector` classifies HINGLISH; inline Roman vocab in LocalCommandRouter; `HindiResponseNormalizer` = output-side only. No dedicated input normalizer |
| 8 | Command library screen / single map | PARTIAL | `ui/tools/ToolsScreen.kt` AI Tool Matrix + Test Tool button. No example phrases, no command-library screen. 4 registries to sync (ToolRouter, LocalCommandRouter vocab, ToolsViewModel categories, HindiResponseNormalizer). `ToolsViewModel.getSafeDefaultParams` references non-existent tools (set_timer, calculate, weather, set_brightness, smart_mode) → test yields "Unknown tool" |

### Top gaps (B)
1. Vosk absent + no model manager.
2. Offline misses: open app / call / back / home / scroll / click — cloud-only.
3. No Hinglish normalizer component.
4. No command library / example phrases / single registry; Test Tool broken for several defaults.
5. Contacts: no multi-match disambiguation; fuzzy weak; CALL_PHONE undeclared (yet checked).
6. WiFi + timer unreachable by voice; Bluetooth ungated.
7. App-name matching weak (no fuzzy).

### Flagged risks (B)
1. **Risk-gate bypass**: RoleManager deny on local command falls through to `executeFastDeviceCommand` (AIOrchestrator 486-598) which runs torch/volume/bluetooth WITHOUT role check.
2. **Fake successes**: fast path ignores ActionResult failures, always speaks success (torch hw error; "Bluetooth now on/off" while only opening settings).
3. Cloud dependency for baseline commands with no API key → "add Gemini API key" even for spec-offline requests.
4. `VasuRecognitionService` stub breaks third-party STT selection.
5. Raw `contains` matching in fast path can misroute ("commute" → mute); ContactManager lacks READ_CONTACTS pre-check.

---

## Agent A — Phase 1 (voice/chat/settings/UI) ✅ REPORTED

| # | Item | Status | Evidence / missing |
|---|------|--------|--------------------|
| 1 | STT | PARTIAL | SpeechRecognizer + retry/backoff + partials/RMS + continuous auto-restart ✅. **STT language frozen to hi-IN** — `STTConfig` never initialized from settings (zero `sttManager.initialize` call sites); language picker only updates TTS profile; `hi-Latn` Hinglish never applied; offline manual override unreachable from UI |
| 2 | TTS | PARTIAL | Android TTS + speed/pitch + immediate stop ✅; ducking partial (only full AUDIOFOCUS_LOSS handled). **No male "Jarvis" voice** — `VoiceGender` enum lacks MALE; selection female-preferring. Gemini Live hardcodes `TARGET_VOICE=Kore` + `gemini-2.0-flash-exp` while UI badge shows gemini-3.1 — picker doesn't affect live conversation |
| 3 | State machine | PARTIAL | `VoiceStateManager` validated transitions + UI states/colors ✅. Missing: per-state animations (pulse only for listening/speaking); parallel 2nd FSM `VasuServiceState`; TTS→SPEAKING-from-IDLE rejected as invalid transition |
| 4 | Permission center | PARTIAL | 11 cards icon/why/status/Grant ✅, reachable from Home ✅. Missing: Denied vs Not-set distinction (boolean only); `shouldShowRequestPermissionRationale` unused; Grant only deep-links to settings (no runtime request launcher); CALL_PHONE/SEND_SMS cards probe undeclared permissions → forever "Denied"; no onboarding screen |
| 5 | Chat | PARTIAL | Room messages + write path ✅; memory screen view/edit/delete ✅. **Missing: source/type columns; sessions entity (single conversation only); long-press menu (no share/delete); export; word-by-word UI (full response appended; streaming = TTS-only); memory master toggle; memory never injected** (`includeMemory=false`; `MemoryManager.getFullContext()` zero callers; `processInput` dead) |
| 6 | Settings | PARTIAL | Sections AI/voice/security/system/advanced + test-connection ✅. **SharedPreferences, not DataStore** (dep declared, zero usages); missing Offline/Overlay/Appearance/Privacy/Service/About sections; `offlineOnly` toggle unreachable from UI |
| 7 | UI/theme | PARTIAL | Palette + GlassCard + waveform visuals ✅. **No `res/font` at all** — `Type.kt` = FontFamily.Default; no Orbitron/Exo 2/Rajdhani/Raleway. **No animated orb** — static circle+icon; `AvatarManager.orbAnimation/orbColorHex` dead code; no animations-off setting; infinite transitions unconditional |

### Top gaps (A)
1. Memory pipeline disconnected (facts stored, never injected; no master toggle).
2. Custom fonts absent — typography = system defaults.
3. STT language frozen hi-IN; settings never reach STT config.
4. No voice gender choice; Gemini Live voice/model picker mismatch.
5. Chat sessions/export/long-press/word-streaming missing.
6. Settings on SharedPreferences not DataStore; missing sections.
7. Animated orb not implemented; animations-off switch missing.

### Flagged risks (A)
1. CALL_PHONE/SEND_SMS probed but undeclared in manifest → permanently "Denied", un-grantable.
2. `MessagingManager.kt:132-154` SMS query without permission check/try-catch → SecurityException crash risk.
3. Stubs: `PlacesManager.searchNearby` logs only; memory "LLM wiring" deferred; "43 voices" = pre-recorded .ogg clips not synthesis; "Attachments coming soon" toast.
4. SecureKeyStore plaintext SharedPreferences fallback; chat API key + GitHub client secret stored plaintext (`SettingsViewModel.kt:435-458`).
5. Dead/duplicate: stale Capacitor `android/` tree (26 files, not in Gradle build); `AvatarManager` uncalled; DB v1 `exportSchema=false` no migrations (data-loss risk on entity change).

## Agent C — Phases 4-5 (service/wake word) ✅ REPORTED

| # | Item | Status | Evidence / missing |
|---|------|--------|--------------------|
| 1 | Foreground mic service | PARTIAL | Manifest: mic FGS types declared (`foregroundServiceType="microphone|camera|mediaProjection|dataSync"` L196-199). `VasuForegroundService.kt`: channel/notif, `START_STICKY` L183, mic check L311-313. Missing: no `wakeWordEnabled` check; `goForeground()` L315-323 passes type 0 when mic perm absent → API 34 runtime prereqs unmet = SecurityException crash risk |
| 2 | Notification controls | PARTIAL | Only "Stop" action (L373); no "Listen", no quick-reply |
| 3 | Boot + battery opt | PARTIAL | `BootReceiver.kt:11-23` exists but IGNORES user setting (always starts) and mic-FGS from BOOT_COMPLETED is disallowed on Android 14+ → crash risk. Battery-opt flow ✅ `PermissionsViewModel.kt:190-204,247-255`. No start-on-boot toggle anywhere. Receiver sends `"BOOT_STANDBY"` action that onStartCommand doesn't handle → falls into `beginWakeWordPipeline()` |
| 4 | Survives screen lock | PARTIAL | `PARTIAL_WAKE_LOCK` 12h acquired once (L138-152), not renewed. Overlay activity has no `setShowWhenLocked` → wake popup can't show over lockscreen |
| 5 | Emergency/service stop | EXISTS (caveats) | `ACTION_STOP` + `shutdownService()` L330-345 clean. Caveat: `stop()`/`updateStatus()` use `startService()` from background (L112,125) → `IllegalStateException` risk |
| 6 | Wake-word engine | PARTIAL — custom TFLite (`assets/wakeword/hello_vasu.tflite` 26 KB), NOT Porcupine | **FATAL OFF-BY-ONE**: `MelSpectrogram` yields 97 frames, `WakeWordModel.inputSize=98` → gate `WakeWordDetector.kt:245` never passes → inference never runs, detection can never fire, UI still shows "Listening" |
| 7 | Phrases / sensitivity | PARTIAL | Only "Hello Vasu" hardcoded; no "Hey/OK VASU"; threshold 0.7f hardcoded (`WakeWordModel.kt:47`), zero sensitivity settings |
| 8 | Picovoice AccessKey / .ppn | MISSING | No picovoice/porcupine anywhere; no wake-word secret storage |
| 9 | Wake setup screen | PARTIAL | Toggle + status only (`SettingsScreen.kt:340-370`, `VoiceScreen.kt:497-555`). No phrase picker / sensitivity slider / test button |
| 10 | Wake→STT→answer handoff | PARTIAL | Wiring exists: `onWakeWordDetected` L282-292 → overlay + `triggerActiveVoiceSession()` → GeminiLive mic conversation. Broken: (a) detection unreachable (item 6); (b) detector never re-arms after first use — `releaseMicForCommand()` L318-336 kills own job, `resumeAfterSpeechRecognition()` L362-365 only flips a flag; (c) service-side STT fallback has no collector for `sttManager.results` → backgrounded wake gets no answer |

### Top gaps (C)
1. Wake word can never fire — 97 vs 98 frame off-by-one.
2. `goForeground(withMic=false)` → type 0 + declared mic/camera/mediaProjection types → startForeground SecurityException risk (targetSdk 34).
3. BootReceiver ignores settings; mic-FGS from BOOT_COMPLETED disallowed on 14+.
4. Detector one-shot, no re-arm.
5. Background STT fallback dead-ends (no result collector outside ViewModels).
6. No sensitivity/phrases/AccessKey; model pipeline admittedly unfinished (assets README).
7. `BOOT_STANDBY`/`INCOMING_CALL`/`CAPTURE_MIC` actions unhandled → all fall into `beginWakeWordPipeline()` (mic on during calls).

### Flagged risks (C)
1. SecurityException crash via startForeground type fallback + boot mic-FGS.
2. `startService()` from background in stop()/updateStatus() → IllegalStateException.
3. Always-on Guardian gate: `voiceGuardian.verifyOwner()` fails-open-false → wake silently ignored while state shows LISTENING; toggle doesn't disable it.
4. `WakeWordModel.load()` leaks Interpreter (no close); O(n²) DFT on every chunk (CPU).
5. Exported overlay activity + duplicate mic start race (any launcher can trigger mic session).

## Agent D — Phases 6-9 (automation/overlay/AI) ✅ REPORTED

| Item | Status | Evidence / missing |
|---|---|---|
| 1. AccessibilityService | PARTIAL | Service declared, enabled-check + guidance ✅, actions click/scroll/type/back/home/read + find-by-text ✅. **Missing: wait-for-screen** (only blind delay); fake-success: `typeText` L92-123, `scrollDown/Up` L166-191 ignore `performAction` boolean |
| 2. Multi-step planner | PARTIAL | `MissionEngine.executeMission` real: per-step timeout, stop-on-failure, MAX_STEPS=50, progress counts. `AIOrchestrator.ToolCallLoopPlanner` max 3 rounds. **Missing:** no NL→steps planner ("open Instagram and play reels" impossible); no progress UI; missions in-memory only; `AutomationEngine.executeSteps`+`currentTask` = dead code |
| 3. WhatsApp flow | PARTIAL | `openWhatsApp` wa.me deep link + `?text=`; `open_whatsapp`/`send_message(channel=whatsapp)` tools. **Missing:** accessibility flow (open→search→type→send); send parked to user/OS confirm; `send_whatsapp` tool name absent |
| 4. Risk confirm / OTP guard / emergency stop | PARTIAL | `RiskLevel.HIGH` static gate for make_call/send_message/delete_file via RoleManager. **Missing: confirmation step** — `autoAllowEnabled` (default true) never read by execution; delete_file runs immediately. **OTP/payment guard: MISSING** — `otpProtectionEnabled` UI-only, not persisted/consumed; no payment/OTP detection. **Emergency stop: PARTIAL** — shutdownService stops wake/live/STT/TTS/mic; `AutomationEngine.stop()` unwired; missions/tool execution not cancelled |
| 5. Overlay | PARTIAL | SYSTEM_ALERT_WINDOW declared + permission flow ✅. **Missing:** no floating draggable orb (zero `TYPE_APPLICATION_OVERLAY`); `AssistantOverlayActivity` = full-screen translucent activity, no drag/snap |
| 6. AI providers | PARTIAL | Gemini + Claude = real OkHttp impls; `AIProviderStream` interface. **Missing:** OpenAI/OpenRouter/Groq; no unified generate(); no temperature/maxTokens/systemPrompt in config |
| 7. SSE streaming | PARTIAL | Gemini SSE + Claude stream real; StreamTextParser; sentence→TTS pipeline. **Missing:** word-by-word in UI — text set only after full response |
| 8. Tool calling | PARTIAL | Registry + validation + risk gate + JSON schema builders ✅, 30s timeout. Names differ from spec (make_call/turn_on_torch/send_message/run_mission). **Critical: streaming path sends `tools=emptyList()`** + no system prompt on Claude stream — real Chat/Voice use `processInputStreaming` → tool calling only on rarely-reached `processInput` path |
| 9. Error handling | PARTIAL | Full taxonomy 401/403/404/429/5xx/no-net/timeout/empty + script-aware messages; Gemini retry+backoff. **Missing:** Claude no retry; no offline LLM fallback; interrupt doesn't cancel in-flight LLM request |
| 10. Memory/keys/prompt/test | PARTIAL | Keys only SecureKeyStore (no hardcoded keys); test-connection ✅. **Missing: `includeMemory=false` everywhere** — `MemoryManager.getFullContext()` zero callers; system prompt dropped on Claude streaming; no "no markdown/short" rule; key store plaintext fallback + logs key fragments (L46-48, L56, L75) |
| 11. Hybrid router | PARTIAL | LocalCommandRouter local-first ✅; TTS VoiceRouter online→local→Android ✅. **Missing:** AI engine selection ignores NetworkMonitor/offlineOnly; `AIProvider.LOCAL` = canned string |
| 12. Release readiness | PARTIAL | Signing config w/ env keys; minify+shrink true; proguard 91 lines; CI = unit tests + assembleDebug + artifact. **Missing:** no release/signed CI job, no lint; blanket `-keep com.vasu.assistant.**` defeats R8; debug-fallback signing when keystore absent |

### Top gaps (D)
1. **Primary Chat/Voice streaming path sends no tools + no system prompt** — tool calling, Jarvis tone, memory never reach the model in real use.
2. No NL compound-task planner; `AutomationEngine` dead code; no progress UI.
3. No HIGH-risk confirmation; `autoAllowEnabled` dead; risk gate fail-open when Guardian off (default).
4. No payment/OTP screen guard.
5. Overlay = full-screen activity, not floating draggable orb.
6. Emergency stop doesn't halt automation/missions.
7. OpenAI/OpenRouter/Groq absent; memory injection + word-by-word UI not wired.

### Flagged risks (D)
1. SecureKeyStore plaintext SharedPreferences fallback on Keystore failure; logs key length/prefix fragments.
2. Fail-open HIGH-risk execution (make_call/send_message/delete_file) when Guardian off — confirmation skipped.
3. Fake-success accessibility: typeText/scroll claim success regardless; openApp returns true after Play-Store redirect.
4. `network_security_config.xml` cleartext global.
5. Release debug-signed when keystore absent; proguard blanket keep kills obfuscation value.

---

## Consolidated Priority Queue (merged, 2026-10-16)

| # | Fix | Why first | Files (est.) |
|---|-----|-----------|--------------|
| FIX-1 | **Wake word pipeline dead**: 97-vs-98 mel-frame off-by-one → detection never fires; detector never re-arms after first use; Interpreter leak | Headline feature 100% non-functional; provable by code + unit test | `MelSpectrogram.kt`, `WakeWordDetector.kt`, `WakeWordModel.kt` |
| FIX-2 | **Boot/FGS safety**: BootReceiver ignores setting + mic-FGS from BOOT_COMPLETED disallowed (14+) → SecurityException; `goForeground(type=0)` fallback crash; background `startService()` in stop()/updateStatus() | Crash risks on user's device; Android 14 policy | `BootReceiver.kt`, `VasuForegroundService.kt`, settings |
| FIX-3 | **AI streaming path missing tools + system prompt + memory** (`includeMemory=false` everywhere; MemoryManager dead) | Core value: tool calling + Jarvis tone + memory in real chat | `AIOrchestrator.kt`, `GeminiProvider.kt`, `ClaudeProvider.kt`, `PromptManager.kt` |
| FIX-4 | HIGH-risk confirmation (autoAllowEnabled dead, fail-open) + OTP/payment guard + emergency-stop wiring | Safety-critical per spec | `ToolRouter.kt`, `RoleManager.kt`, `AutomationEngines.kt` |
| FIX-5 | Chat completeness: sessions/rename/delete/search/export/long-press/word-streaming + Room migration + source/type | Spec Phase 1 chat requirements | `VasuDatabase.kt`, DAOs, `ChatScreen.kt`, `ChatViewModel.kt` |
| FIX-6 | STT settings wiring (frozen hi-IN) + voice gender Jarvis/Nova + Gemini Live model/voice picker | "Voice problems" cluster | `STTManager.kt`, `VoiceProfile.kt`, `GeminiLiveSession.kt` |
| FIX-7 | Fonts (`res/font` .ttf) + real animated orb + animations-off setting | Spec visual identity | `Type.kt`, new fonts, Home/overlay |
| FIX-8 | Vosk + model manager; offline routes (open app/call/back/home/scroll/click); contacts multi-match; command library + single registry | Offline spec + no-cloud UX | multiple |
| FIX-9 | Floating overlay orb (TYPE_APPLICATION_OVERLAY, drag, snap, mini panel) | Spec Phase 7 | new overlay service |
| FIX-10 | Providers OpenAI/OpenRouter/Groq + word streaming + Claude retry + interrupt cancels request | Spec Phase 8 breadth | providers |
| FIX-11 | Security/packaging: SecureKeyStore fallback, SMS crash risk, cleartext config, release CI job, proguard, manifest perm mismatch | Hardening | multiple |

(Also queued: accessibility wait-for-screen, fake-success fixes, notification "Listen" action, NL compound planner.)

### Fix progress
- **FIX-1** ✅ 2026-10-16: window derived from `WakeWordModel.INPUT_SIZE` (= 25,344 → exactly 98 frames); state → IDLE after mic release (kills zombie LISTENING); `shouldBeListening` + `resumeAfterSpeechRecognition()` re-arm; interpreter closed on reload; regression test `MelSpectrogramWindowTest`.
  - Independent review pass 1 found BLOCKER: blind re-arm could force-steal the mic from an active Gemini Live session → fixed with `requestMicIfFree()` (atomic, never steals) + bounded polling (12×500ms, every check gated).
  - Review pass 2 (verify): BLOCKER resolved; 2 more MINORs fixed — hot-mic race (intent re-check inside `startInternal` under the monitor) and non-mic retry churn (permission/model pre-checks stop polling early).
  - Review pass 3 (verify): both races closed, **no BLOCKER/MAJOR**. Open NITs (documented, non-blocking): re-arm polling stops on `INFERENCE_FAILED` status (one-off native throw; recovery = toggle); STT `onResults` re-arm can briefly hear TTS (deferred to FIX-6 voice-chain rework).
  - Gate: green ✅ — `assembleDebug` + **210/210** unit tests + `lintDebug` clean (2026-10-16).
- **FIX-2** ✅ 2026-10-16: BootReceiver gates on `wakeWordEnabled` (Hilt EntryPoint, fail-closed); API < 30 starts the service directly, API 30+ posts a "tap to resume" notification (mic-FGS from BOOT_COMPLETED is disallowed from Android 11; Android 14 throws `ForegroundServiceStartNotAllowedException`); `goForeground` never passes type 0, uses DATA_SYNC when mic=false, catches `SecurityException`/`IllegalStateException` → guarded DATA_SYNC degrade, and `stopSelf()` if even that fails; `onStartCommand` routes unknown actions safely (never starts the mic pipeline) and no longer rewrites a live notification; wake-listener `start()==false` degrades to tap-to-resume.
  - Independent review pass 1: 2 MAJORs — `singleTop` resume-extra missed by `onCreate` (fixed: shared handler in `onCreate` + `onNewIntent`); API 30–33 boot mic-FGS silently ineffective (fixed: gate moved to `< 30`). MINORs fixed: `ForegroundServiceStartNotAllowedException` catch, distinct PendingIntent request code (2 vs 0), no notification rewrite while running.
  - Review pass 2 (verify): no BLOCKER/MAJOR. Known limitation (documented): API 33+ with POST_NOTIFICATIONS denied → boot resume notification is silently dropped; wake word stays off after reboot until the app is opened once.
  - Gate: green ✅ — `assembleDebug` + **218/218** unit tests + `lintDebug` clean (2026-10-16).
- **FIX-3** ✅ 2026-10-16: streaming path now carries the real system prompt (`buildTurnContext`: personality + remembered facts; `includeTools=false` because the streaming wire format sends no tools) and routes tool-relevant turns to the non-streaming tool loop (real execution beats a hallucinated "done"); explicit remember/forget commands (Roman + Devanagari variants, one shared prefix set via `detectCommand`/`performCommand`) run before every local matcher so "remember that my meeting time is 5" is stored, not hijacked; `forgetMatching` deletes by key or value-contains; Devanagari keywords added to `ToolRelevanceFilter`; claims-guard + tool-restraint lines in the prompt; `VasuSettings`/`PrivacyViewModel` OTP toggle wired in FIX-4.
  - Independent review pass 1: 2 MAJORs — streaming prompt mentioned tools while sending none (fixed: `includeTools=false` for streaming); local matcher hijacked memory commands (fixed: memory handled first). MINORs fixed: `CancellationException` rethrown, forget no-op, Devanagari keyword gaps; NITs: "yaad rakhna" suffix, `memoryBlock` param moved to end.
  - Review pass 2 (verify): found the gate failure `Dagger/MissingBinding: ConversationMemory` (dead DI chain unmasked by the new injection) → fixed with `@Inject` constructor. MAJOR: routing false-positives offer tools to casual chat → fixed with a tool-restraint prompt line; residual (documented): misrouted chat turns lose first-sentence streaming latency by design.
  - Review pass 3 (verify fix round): all items OK, no new BLOCKER/MAJOR. **Verdict: fix round verified.**
  - Gate: green ✅ — `assembleDebug` + **218/218** unit tests + `lintDebug` clean (2026-10-16).
