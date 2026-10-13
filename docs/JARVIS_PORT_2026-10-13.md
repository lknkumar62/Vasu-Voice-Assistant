# Jarvis → Vasu Port — 2026-10-13

Source: https://github.com/vierisid/jarvis.git (cloned /root/jarvis, 97M/3320 files).
Storage: `/storage/emulated/0/Open code backup/jarvis-raw/` + `jarvis-20261010.tar.gz` (69M).

## Ported (pure Kotlin, no new deps)

### 1. Personality engine — `core/personality/` (Jarvis: src/personality/*)
- `PersonalityModel.kt` — LearnedPreferences (verbosity/formality/humor/emoji/format),
  PersonalityModel (core traits, learnedKeys, messageCount, trust, channelOverrides).
- `PersonalityLearner.kt` — keyword signal extraction (no LLM), clamp 0..10,
  learnedKeys only for explicit feedback, trust = min(10, 3 + count/10).
- `PersonalityAdapter.kt` — channel defaults (+ new `voice` preset 4/3/3/lists),
  forChannel precedence (override > learned > preset), personalityToPrompt block.
- `PersonalityManager.kt` — SharedPreferences `vasu_personality` via org.json,
  fail-open, no message logging.
- Wired: `AIOrchestrator` observes each turn + appends block via new optional
  `PromptManager.buildPrompt(includePersonality, personalityBlock)` params
  (existing callers unchanged).

### 2. Tool-relevance filter — `core/ai/ToolRelevanceFilter.kt` (Jarvis: tool-relevance/*)
- 10 trigger groups over the real 33 ToolRouter tools, EN+HI keywords,
  whole-word script-aware regex, 8000-char window, URL/domain/path detectors.
- `decide()` fails OPEN to full list on weak signal (never empty).
- Hooked into both `generate` lambdas (gemini/claude); try/catch fallback + kept/total log.

### 3. Vault extractor (pure part) — `core/memory/VaultExtractor.kt` (Jarvis: src/vault/extractor.ts)
- ExtractionResult data classes, buildExtractionPrompt (2000-char caps, JSON-only),
  parseExtractionResponse via Gson (JVM-safe), entity allowlist, confidence
  fallback 0.5, blank drops, quote gate overload, SHA-256 sourceRef.
- Hooked into `MemoryManager.processInput` via fact-bearing gate (no LLM call yet;
  `storeVaultFacts` ready for the reply path when model routing lands).

### 4. Awareness skeleton — `core/awareness/` (Jarvis: awareness/*, roles/*, daemon/*)
- `SuggestionEngine.kt` — Kind priority + Jarvis rate limits (15s–600s),
  50-hash FIFO dedup, pure evaluate(nowMs), KDoc mobile-trigger mapping.
- `AwarenessRoles.kt` — ACTIVITY_OBSERVER (authority 2), validate(),
  goal fuzzy matcher (threshold 0.15, min 2 terms).
- Standalone by design; wiring (WorkManager, ErrorLog hook, UsageStats) is coordinator follow-up.

## Tests (all JVM, JUnit4)
PersonalityLearner (13), PersonalityAdapter (7), ToolRelevanceFilter (12),
VaultExtractor (13), SuggestionEngine (14).

## Not ported (deliberate)
- Bun/SQLite/socket.io engine runtime, sidecar RPC, cron/webhook HTTP servers,
  cloud-vision struggle scoring, LLM-coupled extractor storage, telemetry
  endpoint (Vasu has its own TelemetryManager).
