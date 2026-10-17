# VASU AI — Test Checklist (phone-based)

## Phase 1
1.1 **Project setup**: build runs (`./gradlew assembleDebug`) on a clean checkout; CI workflow posts APK.
1.2 **Theme**: app opens dark, colors match #020408 bg / #0A1628 panel / #00D4FF neon blue; change accent → UI updates (if in scope); font weights render crisp (no italics).
1.3 **Permission center**: each permission card shows granted/denied; denied-permission button opens app-settings; new chat permission request re-asks on resume; no crash with denial.
1.4 **Home/orb**: orb animates (breathing); taps start/stop listening with haptics; animations-off toggle halts animation.
1.5 **STT**: partial text updates while speaking; stops on silence; offline + online switch.
1.6 **TTS**: male/female voices switch, speed/pitch live effect; speak interrupt on speak-as-user/Stop.
1.8 **Chat**: messages persist after kill; long-press menu works; export produces text file.
1.9 **Settings**: all toggles persist; API key test-connection button gives success/fail.

## Phase 2
2.1 **Parser**: EN commands parse: flashlight on/off, volume up/down/mute, clock, battery; Hinglish commands match. Unknown → safe conversational reply.
2.2 **Executor**: flashlight toggle physically works (device screen/torch), volume actually changes, time/battery speak correctly.
2.3–2.4 Vosk: model manager download/update progress; offline dictation produces text; parity with English phrases.

## Phase 3
3.1 App launcher list shows installed apps with proper labels (WHERE USER HAS GRANT PERMISSION) to open WhatsApp/YouTube.
3.2 Calling someone by name shows disambiguation when multiple matches; ACTION_DIAL (no direct call without permission).
3.4 Command library: Try-it triggers same execution.

## Phase 4
4.1 Background service notification persists; mic permission permanent (Start on boot after reboot).
4.2 Battery-optimization page opens and guides; survives screen lock ≥2 min.
4.3 Doze-exemption granted → service starts automatically on reboot.

## Phase 5
5.1 Porcupine needs AccessKey; missing key → setup guidance, no crash.
5.2 "Hey VASU"/"OK VASU" detection works within 5s of spoken phrase.
5.3 Wake detected → starts STT → TTS of answer at least once.

## Phase 6
6.1–6.4 Accessibility: tapping "Read screen" lists current screen text; "click on <text>" opens an element; "go home" works.
6.5 Risk: high-risk action asked confirmation (voice); emergency stop halts TTS+listening+automation within 500ms.

## Phase 7
7.1 Overlay appears over launcher; draggable, snaps to edge; tap 2 times to execute of quick voice panel.
7.3 Mini panel closes gracefully, no window leak.

## Phase 8
8.1–8.2 AI: a message produces a one-sentence reply (no markdown); multiple provider endpoints work with the user key.
8.3 Streaming shows text appearing word-by-word.
8.4 Tool calls execute and confirmed via chat card.
8.5 Memory screen lists/edits/deletes facts; master toggle off → no injection.
8.6 Test-connection returns success/error from Settings.

## Phase 9
9.1 Auto routing: airplane-like mode testes offline path.
9.2 Fast-cancellation latency check: a user speaks → VASU responds <3s with good signal.
9.3 Error handling: 4 errors simulated → app shows & retries, no crash.
9.4 Release build: signed APK (release variant) installs & launches.

## FIX-1..4 verification (2026-10-16)
FIX-1 — wake word:
- "Hello Vasu" fires repeatedly (5+ times in a row), each time followed by a command; mic never wedges after a response.
FIX-2 — boot / foreground service:
- Wake word ON + reboot phone (Android 11+): a "Tap to resume listening" notification appears; tapping it resumes listening. Android 13+: allow VASU notifications first; if denied, wake word stays off until the app is opened once (known limitation).
- Wake word OFF + reboot: no notification, no service.
FIX-3 — streaming prompt / memory / tools:
- "yaad rakhna mera meeting time 5 baje hai" → "Yaad rakh liya."; then "mera meeting time kya hai?" → reply mentions 5 baje.
- Devanagari: "याद रखो मेरा नाम राहुल है" → stored; "भूल जाओ ..." → "Bhool gaya."; repeating forget → "kuch yaad nahi tha".
- "mummy ko call karo" (autonomous execution ON) → real dialer/call happens (no fake "done" without an action).
FIX-4 — HIGH-risk confirmation / OTP guard / emergency stop:
- Settings → turn OFF autonomous execution → "mummy ko call karo" → VASU asks "Confirm karo — kya main call karna kar doon? Haan ya nahi bolo."; "haan" → executes; "nahi" → "Theek hai, cancel kar diya."; any other sentence → cancelled and treated as a new request.
- Devanagari/English variants ask in the same script; a new command like "Rahul ko call karo" while a confirmation is pending must NOT count as "yes".
- OTP guard: "OTP 654321 hai, Rahul ko message bhej do" → safety refusal (Privacy → OTP protection ON by default); turning it OFF in Privacy settings allows it.
- Emergency stop (stop listening / service shutdown) while a mission runs → mission aborts at the next step with "stopped by user", TTS stops.

Chat / diagnostics (2026-10-16):
- Chat: send 4-5 messages quickly (two within the same second) → no crash, all messages appear in order.
- Diagnostics: Settings → ADVANCED → "Open Diagnostics" (debug APK only) → internal error-log card visible and copyable for sharing.

FIX-5 — chat sessions / export / long-press / streaming (2026-10-16):
- **Upgrade/migration test (most important)**: install this APK on top of the previously installed OLD build (do NOT uninstall first) → app opens normally, old chat messages are still there (History sheet shows them as "Chat" entries), stored memories still answer. Any crash at open = tell me immediately.
- Word-stream: send a message → the reply bubble appears and grows sentence by sentence while VASU speaks.
- History icon (top bar) → earlier chats with titles (first message); tap = open; Edit = rename; Delete = remove that chat.
- New chat icon → fresh session; after the first message it appears in History as its own entry.
- Long-press any message → Copy / Speak / Share / Delete that single message.
- Share icon (top bar) → Android share sheet with the whole chat as text.
- Clear Conversation button now clears only the CURRENT chat (not everything).

FIX-12 — live voice / hands-free loop (2026-10-16):
- **Jarvis loop (most important)**: keep the app closed (or screen off) → say "Hello Vasu" → after the chime/notification, say a command ("aaj mausam kaisa hai?" / "time kya hai") → VASU must HEAR it and ANSWER out loud. Repeat 4-5 commands in one flow.
- **No double voice**: each reply must be heard exactly ONCE (the double-speaking bug is fixed — earlier it sometimes spoke twice).
- **Offline fallback**: turn airplane mode ON → "Hello Vasu" → "time kya hai" → reply still spoken (local voice). If nothing is heard, the notification returns to "Hello Vasu" within ~15s — try again.
- **After 5+ back-to-back wake commands**: wake word must still work, phone must not freeze or get unusually hot.
- If live voice still fails, open Settings → ADVANCED → "Open Diagnostics" (debug APK) → copy the internal log and send it to me.

FIX-6 — settings wiring (2026-10-16):
- **Language**: Settings → VOICE & SOUND → Language → switch to English (US) → say "Hello Vasu" → "what is the time" → VASU should understand English now (STT follows this setting; it used to stay Hindi forever). Switch back to Hindi and repeat in Hindi.
- **Voice gender**: Settings → Voice Gender → tap **Jarvis (Male)** → Test Voice (or a hands-free command) → the voice must be male; tap **Nova (Female)** → female again. Picking a specific voice in the big list below should also move the highlighted gender chip.
- **Live pickers**: Settings → "Live Voice" and "Live Model" dropdowns — change them, then run a hands-free command: the next reply should use the chosen voice. (If the chosen model is not enabled for your key, VASU silently retries the other one — an error there is not a bug, but tell me the log if voice stops working after switching models.)
- **Voice Guard**: toggle Voice Guard ON → close Settings → reopen → it must still be ON (it now lives in the guardian store, not a dead flag). If you ever open the Guardian screen, the state must match.

FIX-7 — visual identity (2026-10-16):
- **Orb**: Home tab → the assistant orb should now breathe, show two slowly rotating arcs, and pulse a ring while listening / when the wake word is heard. Colors: crimson = listening, cyan = speaking, yellow = thinking.
- **Animations off**: Settings → APPEARANCE → Animations OFF → back to Home: the orb is a static frame and the Voice tab's dot/waveform/mic halo stop moving; switch ON again to restore motion.
- **Fonts**: titles and labels should now use the new sci-fi fonts (no default AOSP look). If any text looks broken/cut off, send a screenshot along with the Diagnostics log.

FIX-8 Pass A — offline routes / command library / contacts (2026-10-16):
- **Offline routes (try with airplane mode ON)**: "open whatsapp" / "whatsapp kholo" → WhatsApp opens; "go back" / "wapas chalo" → previous screen; "go home" → home screen; "scroll down" / "neeche scroll karo" → screen scrolls; "click login" style → clicks that visible text (accessibility service on).
- **Call**: "call mom" / "mummy ko call karo" → dialer opens pre-filled (you still press the final call button). Numbers too: "call 9876543210".
- **Multi-match contacts**: if 2+ contacts share a name, "call Rahul" no longer guesses — VASU answers "Multiple contacts match …" with the names and asks for the full name (same for SMS/WhatsApp).
- **Command Library**: Tools tab → "Voice Command Library" card → full list (Device / Screen & navigation / Apps / Calls). An automated test keeps this list and the real routing in sync.
- **Compound guard**: "torch on and send sms" / "go back aur photo dikhao" → no half execution (the LLM handles the whole sentence).

FIX-8 Pass B — offline voice / Vosk (2026-10-16):
- **Download a model**: Settings → OFFLINE VOICE → "Hindi — small" → tap Download → progress bar chale → "Downloaded — offline ready" aaye (~42 MB, WiFi pe kar lena; beech me app band na karo). English model alag se download hota hai.
- **Offline recognition (asli test)**: toggle "Offline voice (Vosk)" ON karo → airplane mode ON rakho → "Hello Vasu" → bol "time kya hai" → jawab aana chahiye (bina internet!). English me test: Settings → Language → English (US) + English model downloaded.
- **Toggle OFF**: purana behaviour (phone ka speech service) turant wapas — agar offline voice me kuch atke, toggle OFF karke normal voice check karo aur Diagnostics log bhejo.
- **Delete**: model ke saamne Delete → "Download" button wapas aa jaye; dobara download bhi chale.
- **Pehli command thodi slow** lag sakti hai (model load hota hai ~1-2s) — ye expected hai; uske baad ki commands normal.
