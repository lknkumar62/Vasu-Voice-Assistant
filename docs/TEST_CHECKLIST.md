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
