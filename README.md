# VASU — Voice Assistant (Android)

VASU is a bilingual (Hindi / English / Hinglish) Jarvis-class voice assistant for Android: wake word, on-device fast-path commands, Gemini/Claude tool calling, voice-print Guardian security, accessibility automation, and local memory.

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean · Hilt · Coroutines/Flow · Room · DataStore · WorkManager

- Package: `com.vasu.assistant`
- minSdk 26 · targetSdk 35 · compileSdk 34

## Build

```bash
# Requires JDK 17 + Android SDK. local.properties must contain sdk.dir=<path>.
./gradlew assembleDebug          # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint
```

> On aarch64 Linux hosts, `gradle.properties` pins `android.aapt2FromMavenOverride=/usr/bin/aapt2`
> because the SDK's x86_64 aapt2 cannot run natively.

## Architecture

```
app/src/main/java/com/vasu/assistant/
├── core/
│   ├── ai/          AIOrchestrator, GeminiProvider, ClaudeProvider, PromptManager, ToolRouter
│   ├── automation/  MissionEngine, TaskExecutor, ActionResult
│   ├── security/    RoleManager, VoiceGuardian, RiskLevel/UserRole model
│   ├── stt|tts/     STTManager, TTSManager, VoiceRouter
│   ├── wakeword/    WakeWordDetector (hello_vasu.tflite)
│   └── settings/    VasuSettings (DataStore/SharedPreferences)
├── devices/         Torch, Volume, Bluetooth, Media, DeviceControl managers
├── messaging/       ContactManager, MessagingManager (SMS/WhatsApp/calls)
├── accessibility/   VasuAccessibilityService + screen interaction
├── notifications/   NotificationListener + parser
└── ui/              Compose screens (home, chat, voice, guardian, tools, …)
```

### Command flow

1. **Fast path** — `AIOrchestrator.executeFastDeviceCommand` runs torch/volume/bluetooth/battery/time/camera commands on-device in <50 ms, script-aware confirmations (Roman/Devanagari/English).
2. **Tool calling** — everything else goes to Gemini/Claude with the full tool schema. A returned `FunctionCall` is executed for real by `ToolRouter.executeTool` (risk-gated, runs on `Dispatchers.IO`); the actual result is spoken. No placeholder successes: unknown tools, missing args, or a disconnected accessibility service return explicit errors.
3. **Risk gate** — every tool carries a `RiskLevel`; `RiskLevel.requiredRole` is checked against `RoleManager.hasPermission` before any side effect. HIGH (call / SMS / delete) demands owner voice verification while Voice Guardian is enabled; OTPs are never spoken, logged, or sent to the LLM.

## Permissions

Mic, camera, contacts, SMS, call log, phone state, notifications (listener), accessibility, overlay, external media/storage, exact alarms, location, Bluetooth. HIGH-risk actions additionally require Voice Guardian enrollment.

## Status

Phase 1 baseline: build green, unit tests green, real tool execution wired. See `AGENTS.md` for the team workflow used during development.
