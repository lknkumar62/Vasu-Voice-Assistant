# Wake Word Models

This directory contains TensorFlow Lite models for wake word detection.

## Required Models

1. **hello_vasu.tflite** - Main wake word detection model
   - Input: Mel spectrogram features (98 frames x 40 mel bands)
   - Output: Detection score (0.0 to 1.0)
   - Threshold: 0.7

2. **embedding_model.tflite** - Speaker embedding model (optional)
   - For speaker verification
   - Used in Phase 4: Voice Guardian

3. **melspectrogram.tflite** - Mel spectrogram extraction model (optional)
   - For on-device feature extraction
   - Alternative to CPU-based extraction

## How to Get Models

### Option 1: Use Pre-trained Model
Download from: https://github.com/tensorflow/tensorflow/tree/master/tensorflow/lite/examples/speech_recognition

### Option 2: Train Your Own
Use TensorFlow to train a wake word model:
1. Collect audio samples of "Hello Vasu"
2. Extract mel spectrogram features
3. Train a CNN or RNN model
4. Convert to TFLite format

### Option 3: Use Placeholder Model
For development/testing, create a simple model that always returns 0.5.

## Model Placement
Place models in this directory:
```
app/src/main/assets/wakeword/
├── hello_vasu.tflite
├── embedding_model.tflite (optional)
└── melspectrogram.tflite (optional)
```

## Note
The app will work without models but wake word detection will be disabled.
Voice input will still work via manual mic button.
