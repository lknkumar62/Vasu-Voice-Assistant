# VASU 30-AGENT AUTONOMOUS TEAM

## IMPORTANT: How to Use This System

When the user gives a task, you MUST:
1. **SHOW ACTIVATION** — List which agents are being activated
2. **INVOKE VIA TASK TOOL** — Use the Task tool with subagent_type for each agent
3. **SHOW STATUS** — Before each agent runs, show: `[AGENT ##] name: STATUS - description`
4. **SHOW RESULTS** — After each agent runs, show: `[AGENT ##] name: COMPLETE - what was done`
5. **NEVER SKIP AGENTS** — Always run the full pipeline

## Agent Status Panel

| # | Agent | Status | Task |
|---|-------|--------|------|
| 01 | master-orchestrator | IDLE | Routes tasks to specialists |
| 02 | repository-explorer | IDLE | Searches codebase, finds files |
| 03 | architecture-analyst | IDLE | Analyzes architecture, detects conflicts |
| 04 | root-cause-bug-hunter | IDLE | Finds actual root causes |
| 05 | debugging-engineer | IDLE | Reproduces, analyzes, fixes bugs |
| 06 | frontend-engineer | IDLE | React, TypeScript, Vite, Tailwind, UI |
| 07 | backend-engineer | IDLE | API routes, server logic, auth |
| 08 | typescript-engineer | IDLE | Type errors, interfaces, strict typing |
| 09 | android-capacitor-engineer | IDLE | Android, Capacitor, permissions, APK |
| 10 | speech-stt-engineer | IDLE | Speech recognition, microphone, STT |
| 11 | tts-voice-engineer | IDLE | TTS pipeline, voice, audio playback |
| 12 | wake-word-engineer | IDLE | Wake word detection, background listening |
| 13 | ai-architect | IDLE | AI pipeline, prompt architecture |
| 14 | model-router | IDLE | Smart model selection |
| 15 | ai-api-provider-engineer | IDLE | Gemini, OpenRouter, Groq integrations |
| 16 | ollama-local-ai-engineer | IDLE | Ollama, local models |
| 17 | tool-automation-engineer | IDLE | Tool calling, function execution |
| 18 | security-engineer | IDLE | Security audit, secrets, vulnerabilities |
| 19 | performance-engineer | IDLE | CPU, RAM, latency, optimization |
| 20 | concurrency-engineer | IDLE | Race conditions, duplicate requests |
| 21 | error-recovery-engineer | IDLE | Fallback, retry, error handling |
| 22 | test-engineer | IDLE | Unit tests, integration tests |
| 23 | regression-engineer | IDLE | Verify nothing broke |
| 24 | build-ci-engineer | IDLE | Build, lint, TypeScript, CI |
| 25 | independent-code-reviewer | IDLE | Review code independently |
| 26 | ux-ui-engineer | IDLE | User flow, visual consistency |
| 27 | technical-research-engineer | IDLE | Research docs, APIs |
| 28 | documentation-engineer | IDLE | README, docs, architecture docs |
| 29 | git-release-engineer | IDLE | Git status, clean commits, secrets |
| 30 | final-verification-engineer | IDLE | Final gate — verify everything works |

## Workflow

Every task follows this pipeline:
```
DISCOVER → UNDERSTAND → PLAN → IMPLEMENT → TEST → REVIEW → REGRESSION → BUILD → VERIFY
```

## Example Output Format

```
🚀 ACTIVATING 30-AGENT TEAM FOR: [task description]

[AGENT 02] repository-explorer: RUNNING - Searching codebase...
[AGENT 02] repository-explorer: COMPLETE - Found relevant files in src/

[AGENT 03] architecture-analyst: RUNNING - Analyzing architecture...
[AGENT 03] architecture-analyst: COMPLETE - No conflicts detected

[AGENT 06] frontend-engineer: RUNNING - Implementing React component...
[AGENT 06] frontend-engineer: COMPLETE - Component implemented

[AGENT 22] test-engineer: RUNNING - Running test suite...
[AGENT 22] test-engineer: COMPLETE - All tests passed

[AGENT 25] independent-code-reviewer: RUNNING - Reviewing code...
[AGENT 25] independent-code-reviewer: COMPLETE - Code quality: GOOD

[AGENT 24] build-ci-engineer: RUNNING - Building project...
[AGENT 24] build-ci-engineer: COMPLETE - Build successful

[AGENT 30] final-verification-engineer: RUNNING - Final verification...
[AGENT 30] final-verification-engineer: COMPLETE - ALL VERIFIED ✅

✅ TASK COMPLETE - 8 agents activated, all verified
```
