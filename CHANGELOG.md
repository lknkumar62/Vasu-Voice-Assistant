# CHANGELOG — VASU Voice Assistant

## 2026-10-02

- Hidden internal ErrorLog for every swallowed failure (`c0b6d67`).
- Chat-open crash paths hardened, clean home layout with animated action buttons (`3b0ba5d`).
- Token-level streaming via AIProviderStream, TTS rate audit, AgentRunner runtime (`209db25`).
- Multi-step tool loop, provider streaming, hardened mission engine, security audit (`3c9f468`).
- Corrected FEATURE_CHECKLIST claims, added 2026-10-02 status and remaining-gap list (`9b8d8fb`).
- Real OCR/Vision via ML Kit, sentence-level streaming TTS (`8c416fb`).

## 2026-09-30

- Persisted voice guardian enrollments, functional guardian UI, enrolled-voice wake verification (`3a7d5ba`).

## 2026-09-29

- Root native Gradle project build, not legacy Capacitor shell (`73a6e1d`).
- Real tool execution, VASU crimson theme, conversation history, lint clean (`19cf55d`).

## 2026-09-14 — 2026-09-13

- Maya 4.18.5 parity batches: UI unification, voice reimplementation, theme hard-reset (`2e964b7`, `384c252`, `3c673f4`, `7c91e79`, `f72989a`, `dab78b5`, `1bba6c7`, `ebfaeb6`, `b1af694`, `4950035`, `67db890`, `6d81d14`, `712685c`).
- Voice fallback and listening fixes: instant local TTS, continuous listening, language mirroring (`74189e7`, `c7a135a`, `741ea1c`).

## Tests

- 17 `*Test.kt` files present under `app/src/test` (verified locally).
- Unit-test totals: see CI (RELEASE_NOTES_2026-10-02 cites run `36939538469` green; expected ~114 pending gate confirmation).
