# TTS Pipeline 24 kHz Audit

| Engine / Player | Claimed rate | Actual rate handed to decoder/player | Verdict |
|---|---|---|---|
| GeminiTtsEngine (core/tts/GeminiTtsEngine.kt) | 24 kHz raw PCM (per Gemini Live/TTS docs) | Parses `rate=` from MIME (`extractSampleRate`, default 24000) and passes the same value to `AudioFormat.Builder.setSampleRate` (l.406) and buffer sizing (l.376) | OK |
| GeminiTtsEngine container path (WAV/MP3 via MediaPlayer) | 24 kHz default | `pcmToWav` header + cached `sampleRate` built from MIME rate (l.689, l.717ff) | OK |
| NativeAudioPlayer (core/voice/NativeAudioPlayer.kt) | 24 kHz | Hardcoded `SAMPLE_RATE = 24000` (l.37); ignores the `rate=` in each inlineData MIME (GeminiLiveSession.kt l.201-207 drops the MIME entirely) | OK today (Gemini Live contract is 24 kHz), but brittle if API rate changes — noted, outside tts/** |
| NativeMicrophoneRecorder (core/voice) | 16 kHz | `SAMPLE_RATE = 16000` (l.46), upload MIME `audio/pcm;rate=16000` (GeminiLiveSession.kt l.272) | OK |
| LocalTtsEngine / HindiSpeechService | Android TTS (system rate) | Android `TextToSpeech` (no raw PCM rate control) | OK (no PCM claim) |
| AndroidFallbackTtsEngine | Android TTS | Android `TextToSpeech` | OK (no PCM claim) |
| CustomVoiceEngine / LocalTtsEngine custom samples | none claimed | MediaPlayer on asset WAV/MP3/OGG (hardware-negotiated rate) | OK |
| VoiceEnrollmentManager (core/security) | — | Duration math `bytes / 16000.0 * 1000` (l.68) — off by ~2x; should divide by bytes/sec (16000*2 for 16-bit mono) | Mismatch (outside tts/**, noted) |

## Checks performed in GeminiTtsEngine
- One `AudioTrack` (MODE_STREAM), `ENCODING_PCM_16BIT`, `CHANNEL_OUT_MONO`, rate = parsed MIME rate (GeminiTtsEngine.kt:403-408).
- PCM write loop chunks are 4096-byte aligned → 16-bit frame aligned (l.442).
- Cached audio reuses the stored `sampleRate` from synthesis time (l.176, l.153).
- No path labels audio 24 kHz while feeding AudioTrack a different rate.

## Changes made
- Added truth logs in `GeminiTtsEngine.playPcmViaAudioTrack` and `playContainerViaMediaPlayer`:
  `AUDIO_TRUTH ttsId=... rate=<n>Hz encoding=... channels=... path=...` (l.438, l.549).
- `playContainerViaMediaPlayer` now takes the parsed `sampleRate` so the log is truthful (private fun; no public API change).

## Verdict
TTS pipeline sample-rate handling is consistent: Gemini PCM is played at the MIME-declared rate through a matching AudioFormat; Gemini Live native audio is fixed 24 kHz in/out on both mic (16 kHz up) and speaker (24 kHz down) paths. One defensive log added; no rate fixes required. Cross-check note: VoiceEnrollmentManager duration math appears off by 2x (outside owned files).
