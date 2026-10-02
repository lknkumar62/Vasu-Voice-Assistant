# DECISIONS — VASU Voice Assistant (recorded 2026-10-02)

- **Spoken persona is "VASU" everywhere** — the master prompt asked for "Nova"; decided VASU is the canonical assistant name across prompts, TTS greetings, and UI branding.
- **Biometric embeddings in EncryptedSharedPreferences, not Room** — `EncryptedVoiceStore` keeps voice prints on Keystore-backed storage with a sticky storage-mode marker and malformed-entry tolerance; a Room DB added no needed query capability for the enrollment lookup pattern.
- **Guardian fails closed on corrupt storage** — a damaged or unparsable enrollment snapshot denies tools and requires re-enrollment rather than silently downgrading to a permissive mode.
- **Streaming TTS via sentence splitting** — providers were not streaming until this batch, so `processInputStreaming` splits the completed canonical response into sentences; the new `AIProviderStream.generateStream` path will replace sentence-splitting as it is adopted per call site.
- **Root-level `SecurityTypes.HIGH` maps to BOSS role** — highest trust tier is the guardian/owner (BOSS) role; lower levels map to guest/family without access to sensitive tools.
- **Legacy `android/` Capacitor shell deprecated** — the root Gradle project (`app/`, `build.gradle.kts`) is the supported build; the Capacitor/React shell under `android/` + `src/` is kept for reference only and is not part of CI.
