# Custom VASU Local Voice Directory

Place your custom VASU voice files and audio samples here. VASU automatically detects and loads files placed in this directory.

## Location in Repository:
```
app/src/main/assets/vasu_voice/
```

## Supported Formats & File Types:

### Option 1: Custom Voice Audio Samples (.wav / .mp3 / .ogg)
You can place pre-recorded or AI-generated female voice clips directly into this folder:
- `greeting.wav` (Spoken on wake-up / "Hello Vasu")
- `listening.wav` ("Haan bolo, sun rahi hoon")
- `torch_on.wav` ("Ji, flashlight on kar di")
- `torch_off.wav` ("Flashlight off kar di")
- `affirmation.wav` ("Ji jaan, bilkul kar diya")
- `error.wav` ("Sorry jaan, kuch galat ho gaya")

Any `.wav` / `.mp3` named with phrases (e.g. `namaste_main_vasu_hoon.wav` or `haan_bolo.mp3`) will be played directly with 100% custom female voice quality whenever that phrase is spoken!

### Option 2: Neural Local Voice Model (.onnx / .tflite / .bin)
- `vasu_voice.onnx` or `vasu_voice.tflite`
- `config.json` / `tokens.txt`

When present, VASU prioritizes this local neural voice model over the default system TTS.

## Priority Order:
1. Exact Custom Voice Sample (`app/src/main/assets/vasu_voice/*.wav`)
2. Local Neural Voice Model (`app/src/main/assets/vasu_voice/vasu_voice.onnx`)
3. High-Quality Android Female Hindi/Hinglish TTS (`hi-IN` female voice profile)
