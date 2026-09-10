# VASU Assistant (Voice & Vision Agent)

VASU is an intelligent bilingual (Hindi / English / Hinglish) voice assistant web application inspired by Jarvis, featuring hands-free voice recognition, wake-word activation, offline-first command parsing, Gemini AI integration, and speech synthesis.

## Key Features

- **Hands-free Wake Word Detection**: Listens for `"Hello VASU"` or `"Hey VASU"`.
- **Bilingual & Hinglish Brain**: Powered by Google Gemini with multi-model fallback cascade (`gemini-flash-latest`, `gemini-3.1-flash-lite`, `gemini-3.8-flash`, etc.).
- **Natural Voice Synthesis (TTS)**: High-quality Hindi/Hinglish speech output using Gemini TTS and Web Speech synthesis fallback.
- **Hardware & Tool Controls**: Torch/flashlight toggle, volume controls, camera inspection, alarms, contact shortcuts, and local persistent memories.
- **Jarvis Continuous Conversation Mode**: Keeps the microphone listening after responses for natural conversational back-and-forth.

## Setup & Running

```bash
# Install dependencies
npm install

# Run development server
npm run dev

# Build production bundle
npm run build
```

The application runs on port 3000.
