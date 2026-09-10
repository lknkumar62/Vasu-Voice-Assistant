# VASU Implementation - Focused Roadmap

## Strategy: Fix + Complete + Test

Rather than rebuild from scratch, we:
1. **Fix** existing partial implementations
2. **Complete** stub features with real code
3. **Test** each feature works end-to-end
4. **Commit** incrementally with git safety

## Critical Path (Phases that unlock everything else)

### Tier 1: Voice Foundation (Phases 3-5)
- **Phase 3**: Wake Word - Make WakeWordDetector actually work (TFLite model loading/inference)
- **Phase 4**: STT - Fix SpeechRecognizer with proper error handling + Hindi support
- **Phase 5**: TTS - Ensure female voice selection works + natural pronunciation

### Tier 2: AI Integration (Phases 6-7)
- **Phase 6**: Chat - Connect UI to AIOrchestrator, add persistence
- **Phase 7**: Gemini - Verify API integration, model discovery, error handling

### Tier 3: Security & Memory (Phases 8-10)
- **Phase 8**: Natural Voice - Verify voice selection, add Gemini audio investigation
- **Phase 9**: Guardian - Test enrollment and speaker verification flows
- **Phase 10**: Memory - Verify persistence and AI context injection

### Tier 2: Communication & Device (Phases 11-13)
- Calls, SMS, WhatsApp - Already have intents, need testing
- Device control - Torch, volume, battery, media - Already have implementations

### Tier 3: Advanced Features (Phases 14-20)
- Notifications, files, photos, maps, browser, screen automation

### Tier 4: Polish (Phases 21-25)
- UI consistency, error handling, testing, final verification

## Execution: Phase by Phase with Testing

Each phase:
1. Read existing code
2. Identify gaps
3. Write missing pieces
4. Test with assertions
5. Commit with description
6. Move to next phase

Status: Ready to begin Tier 1 (Phases 3-5) immediately

