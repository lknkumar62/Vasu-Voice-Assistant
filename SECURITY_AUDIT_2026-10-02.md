# SECURITY AUDIT — 2026-10-02

Agent: WORKER 4 (security-engineer). Scope: secret/OTP leakage into logs and tool results.

## Executive summary

No secrets or OTP material is logged in the files this agent owns (`MessagingManager.kt`,
`core/security/**`). `MessagingManager.kt` contains zero `Log.*` calls, and both
`SecureKeyStore` and `SecurityImpl` log metadata only (key lengths, similarity scores).
ToolRouter and AIOrchestrator log parameter/argument **keys** only — values are never dumped.
`UserMemory` explicitly strips OTP/card/password patterns before persistence.

Several leaks exist in files **outside** my owned set (reported below, not edited):
notification-text logging that can capture OTP SMS bodies, accessibility click-text logging,
live-chat payload logging, and STT result text logging.

## Files audited (grep evidence)

| File | Grep findings |
|---|---|
| `core/ai/ToolRouter.kt` | `ToolRouter.kt:218` logs `params.keys` only; `:224` logs tool name + exception. No value logging. OK |
| `core/ai/AIOrchestrator.kt` | `:71–225` logs requestId, source, style, `argKeys=${call.args.keys}`. No prompt/body dump. OK |
| `core/ai/AIClient.kt` | No `Log.*` statements at all. OK |
| `core/ai/SecureKeyStore.kt` | `:56,:62,:75` log key **length** only; `:47` warns about unencrypted fallback. No key material. OK |
| `core/security/EncryptedVoiceStore.kt` | `:59–151` log error messages, counts, malformed-entry index. No enrollment/voice data. OK |
| `core/security/SecurityImpl.kt` | `:35,:38` log speaker name + similarity score only. OK |
| `messaging/MessagingManager.kt` | Zero `Log.*`. SMS bodies read from `Telephony.Sms.BODY` (`:140,:148`) are placed in result objects; `:47` puts raw body on an external intent. No logging. OK (no fix needed) |
| `notifications/NotificationAutoReplyManager.kt` | `:80` logs `text.take(50)` of incoming notification (can contain OTP SMS); `:106` logs `$replyText`. LEAK (not owned — reported) |
| `notifications/NotificationListener.kt` | `:56–130` logs package names and detection events only. OK |
| `notifications/NotificationParser.kt` | Retains full title/text/bigText/extras in `ParsedNotification`; passed to listeners/LLM by design; not logged. Inherent-risk note |
| `accessibility/VasuAccessibilityService.kt:94` | `Log.d("View clicked: ${it.text}")` — can leak OTP typed/tapped on screen. LEAK (not owned) |
| `accessibility/ScreenReader.kt:148` | First 5 on-screen texts embedded in LLM summary by design. Inherent-risk note |
| `core/network/LiveChatWebSocket.kt:87,:136` | Logs first 100/50 chars of received/sent chat text. LEAK (not owned) |
| `core/stt/STTManager.kt:434` | `Log.i("[STT_RESULT] text=...")` logs dictated speech (OTPs spoken aloud appear). LEAK (not owned) |
| `core/memory/UserMemory.kt:16–30` | Filters CC numbers, 6-digit OTP, password/secret patterns before storing. OK |
| `ui/privacy/Privacy*.kt` | `otpProtectionEnabled` toggle exists (default true) but no functional enforcement found in filter path. Gap note |
| `AndroidManifest.xml` | `READ_SMS` (:29), `SEND_SMS` (:28), `READ_CALL_LOG` (:30), notification-listener via service, accessibility service — present; no inline comments justifying them. Gap |
| `app/build.gradle*` / sources | No hardcoded `ghp_`, `sk-`, `AIza`, or literal `apiKey="..."` secrets found. OK |

## Findings

| Severity | File:line | Issue | Fix status |
|---|---|---|---|
| High | app/.../notifications/NotificationAutoReplyManager.kt:80 | Logs first 50 chars of notification text — OTP SMS bodies land in logcat | Reported only (not owned): drop `${text.take(50)}` from line |
| High | app/.../notifications/NotificationAutoReplyManager.kt:106 | Logs auto-reply body text | Reported only: drop `$replyText` |
| High | app/.../accessibility/VasuAccessibilityService.kt:94 | Logs text of clicked views — leaks OTPs from tapped UI | Reported only: log node id/type instead of text |
| Medium | app/.../core/network/LiveChatWebSocket.kt:87,136 | Logs chat payload snippets both directions | Reported only: drop payload logging |
| Medium | app/.../core/stt/STTManager.kt:434 | Logs full STT transcript text | Reported only: log confidence/length only |
| Low | app/.../messaging/MessagingManager.kt:47 | Raw SMS body placed on external intent extra | Reported only (owned but intentional design); no change |
| Low | AndroidManifest.xml:28–33 | Dangerous permissions without justification comments | Reported only (manifest not owned) |
| Low | ui/privacy/PrivacyViewModel.kt:29 | OTP-protection toggle not wired to any enforcement path | Reported only |

No edits were required inside owned files: `MessagingManager.kt` and `core/security/**` have no
value-level logging of secrets. Minimal fixes are listed above for the owning workers.

## What looks OK

- ToolRouter logs `params.keys` only (ToolRouter.kt:218); AIOrchestrator logs `argKeys` only (:212).
- SecureKeyStore never logs key material — length and presence only (SecureKeyStore.kt:56–75).
- AIClient has no logging; providers log error kinds/messages, not request bodies.
- EncryptedSharedPreferences wrappers (`SecureKeyStore`, `EncryptedVoiceStore`) with warned
  plaintext fallback; guardian payloads not logged.
- `UserMemory` strips OTP/card/credential patterns before persistence.
- No hardcoded API keys/secrets in source or gradle files.
- NotificationListener logs package names/events only; parser does not log.

## Release checklist (verified green)

- [x] Tool params logged by key name only, never values (ToolRouter, AIOrchestrator)
- [x] API keys never appear in logs — length-only logging verified (SecureKeyStore)
- [x] AI prompt/response bodies not dumped — AIClient silent, providers log errors only
- [x] No hardcoded secrets (`ghp_`, `sk-`, `AIza`) in app sources/gradle
- [x] Sensitive patterns (OTP/card/password) filtered from long-term memory (UserMemory)
- [ ] OTP/notification-text logging in NotificationAutoReplyManager, accessibility click text,
      LiveChatWebSocket payloads, STT transcript — flagged to owning workers, must be fixed
      before release sign-off
