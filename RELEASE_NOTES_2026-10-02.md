# RELEASE NOTES — 2026-10-02

## Headline changes
- Multi-step tool loop in `AIOrchestrator`: up to 3 tool rounds per user turn (`ToolCallLoopPlanner.MAX_TOOL_ROUNDS = 3`), tool results fed back as text turns.
- Chunked streaming now available: `AIProviderStream.generateStream` for Gemini and Claude via `StreamTextParser` (additive; default providers remain single-shot).
- Mission/macro engine hardened: per-step timeout, 60s mission budget, `MAX_STEPS = 50`, cancellation-safe (`withTimeoutOrNull`), precondition fail-fast.
- Security audit shipped: `SECURITY_AUDIT_2026-10-02.md`; High/Medium logging leaks patched (notification text, accessibility click text, live-chat payload, STT transcript now log metadata/lengths only).
- Feature checklist corrected: previously optimistic "zero stubs / build compiles" claims replaced with verified code state in `FEATURE_CHECKLIST.md`.

## Unit tests
EXPECTED PENDING CI: ~114 total unit tests (85 pre-existing from earlier 10-02 batch + 29 new from this parallel batch — W1 +9, W2 +11, W3 +9). Exact count is pending the coordinator's gate run; do not quote a hard number until CI confirms.

## CI
- Run `36939538469`: green (`assembleDebug` + unit tests + `lintDebug` 0 errors).
- Security audit findings and disposition in `SECURITY_AUDIT_2026-10-02.md`.

## Migration / notes
- `MissionEngine.createMission` / `MacroEngine.createMacro` now return `ActionResult` instead of the previous type — source-compatible at call sites using the result, but **binary-incompatible** (recompile any prebuilt consumers).
- No data migration required; `EncryptedVoiceStore` storage-mode marker keeps biometric enrollments readable across restarts.

## Known limitations
- Providers are single-shot by default; streaming via `AIProviderStream.generateStream` is additive and not yet adopted at every call site.
- No dedicated agent process: tool loop is capped at 3 rounds; missions bounded by 60s budget and MAX_STEPS=50.
- Guardian is fail-closed by design: speaker session resets on restart, tools deny until next wake-word verification.
- VASU ships one bundled wake-word owner cohort; multi-owner "family" wake via TFLite path is not wired.
