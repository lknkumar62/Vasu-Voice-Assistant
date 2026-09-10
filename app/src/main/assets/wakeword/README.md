# Wake Word Models

TensorFlow Lite models for detecting "Hello Vasu". **The `hello_vasu.tflite` model is now bundled in this
repository and active.** VASU loads this model in `WakeWordModel` for continuous 16 kHz wake word detection.

## Model Specifications

`WakeWordModel` loads `wakeword/hello_vasu.tflite` and runs inference:

| | |
|---|---|
| Asset path | `wakeword/hello_vasu.tflite` |
| Input | `float32[1, 98, 40]` — 98 mel frames x 40 mel bands, flat native byte order |
| Output | `float32[1, 1]` — probability score in `0.0..1.0` |
| Fires when | score >= `0.7` |

Features come from `MelSpectrogram` (16 kHz mono, 512-point FFT, 40 mel bands).

## Why an openWakeWord file will not just drop in

openWakeWord (https://github.com/dscripka/openWakeWord) is the recommended source
for a custom "Hello Vasu" model, but it is a **three-stage** pipeline:

```
16 kHz audio -> melspectrogram.tflite -> embedding_model.tflite -> hello_vasu.tflite
```

Its wake word model takes speech *embeddings*, not the raw `[98][40]` mel frames
this build writes. Copying an openWakeWord model to `hello_vasu.tflite` therefore
fails at inference, and VASU will say so ("the model rejected its input") rather
than pretending to listen.

Using openWakeWord needs all three files here plus an embedding stage in
`WakeWordDetector`. That work is not done yet.

## Adding a model

1. Train or obtain a model matching the input contract in the table above.
2. Save it as `app/src/main/assets/wakeword/hello_vasu.tflite`.
3. Rebuild. Enable the wake word in Settings and read the status row - it names the
   exact failure if the model cannot be loaded or its shape does not match.

## Do not ship a stub model

A stub that returns a constant is worse than no model: the app reports "listening"
and never wakes, which is indistinguishable from a broken microphone. With no file
present, the status says exactly what is missing.

## Optional models

`embedding_model.tflite` and `melspectrogram.tflite` are openWakeWord's shared
preprocessors, and `embedding_model.tflite` is also what speaker verification
(Voice Guard) would need. Neither is loaded by the current code.
