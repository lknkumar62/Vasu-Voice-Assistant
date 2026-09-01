# VASU Voice Assistant - Complete Implementation Specification

## Current Status (2026-09-01)

**Completed:**
- Phase 1: Full repository audit (100 Kotlin files, all systems verified)
- Phase 2: Home screen UI redesign (professional grid layout, status display)

**In Progress/Next:** Phases 3-25 (detailed specs below)

---

## PHASE 3: HELLO VASU WAKE WORD - COMPLETE SPECIFICATION

**Current State:** WakeWordDetector.kt exists with mel spectrogram processing and TFLite inference. NO MODEL BUNDLED.

**Implementation Tasks:**

1. **Status:** Update notification to report accurate wake word state
   - File: `VasuForegroundService.kt`
   - Change: Update notification text based on WakeWordDetector.state
   - Test: Verify notification shows "Listening for 'Hello Vasu'" or "Wake word unavailable"

2. **Foreground Service:** Fix microphone permission handling for Android 14+
   - File: `VasuForegroundService.kt` (ALREADY DONE - check manifest)
   - Verify: FOREGROUND_SERVICE_MICROPHONE declared, RECORD_AUDIO permission required

3. **Wake Word Detection Loop:** Complete the detection pipeline
   - File: `WakeWordDetector.kt::processBuffer()`
   - Issue: `model?.detect(features)` doesn't exist
   - Fix: Call `model?.predict(features)` and compare to threshold
   - Add: Proper exception handling and state transitions

4. **Model Loading:** Graceful handling of missing model
   - File: `WakeWordDetector.kt::initialize()`
   - Status: Already reports MODEL_NOT_AVAILABLE correctly
   - Add: Test with model present and absent

5. **Auto-Restart:** Service recovery after crashes
   - File: `VasuForegroundService.kt::onStartCommand()`
   - Status: Returns START_STICKY (good)
   - Add: Test killing service and verifying restart

**Test Cases:**
- [ ] Mic permission granted → wake word listening starts
- [ ] Mic permission denied → notification shows "permission needed"
- [ ] Wake word model missing → notification shows "model unavailable"
- [ ] Wake word detected → triggers STT listening
- [ ] False positives reduced via cooldown

**Implementation Effort:** 1-2 hours
**Blockers:** None (code structure ready)

---

## PHASE 4: REAL SPEECH RECOGNITION - COMPLETE SPECIFICATION

**Current State:** STTManager.kt uses Android SpeechRecognizer with basic implementation.

**Issues Found:**
1. `STTConfig()` constructor not initialized properly
2. `createRecognizerIntent()` method not implemented
3. `createListener()` method not implemented
4. Language selection not implemented

**Implementation Tasks:**

1. **STT Configuration:** Add missing initialization
   ```kotlin
   data class STTConfig(
       val language: String = "hi-IN",
       val maxResults: Int = 1,
       val listenTimeoutMs: Long = 15000L,
       val noSpeechTimeoutMs: Long = 5000L
   )
   ```

2. **Recognizer Intent:** Create proper recognition intent
   ```kotlin
   private fun createRecognizerIntent(): Intent {
       return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
           putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
           putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
           putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
           putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
           putExtra("android.speech.extra.DICTATION_MODE", false)
       }
   }
   ```

3. **Recognition Listener:** Implement complete listener
   ```kotlin
   private fun createListener(): RecognitionListener {
       return object : RecognitionListener {
           override fun onReadyForSpeech(params: Bundle?) {
               _state.value = STTState.LISTENING
           }
           override fun onBeginningOfSpeech() {}
           override fun onRmsChanged(rmsdB: Float) {}
           override fun onBufferReceived(buffer: ByteArray?) {}
           override fun onEndOfSpeech() {}
           override fun onError(error: Int) {
               val errorMsg = when (error) {
                   SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                   SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                   SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                   SpeechRecognizer.ERROR_NETWORK -> "Network error"
                   SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                   SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                   SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                   else -> "Unknown error"
               }
               _errors.tryEmit(SttError(SttErrorKind.RECOGNITION_FAILED, errorMsg))
               _state.value = STTState.ERROR
           }
           override fun onResults(results: Bundle?) {
               val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
               if (!matches.isNullOrEmpty()) {
                   val text = matches[0]
                   _results.tryEmit(RecognitionResult(text = text, isFinal = true))
               }
           }
           override fun onPartialResults(partialResults: Bundle?) {
               val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
               if (!matches.isNullOrEmpty()) {
                   _partialResults.tryEmit(matches[0])
               }
           }
       }
   }
   ```

4. **Language Support:** Add Hindi, English, Hinglish detection
   - File: New function `detectLanguage(text: String): String`
   - Logic: Use character detection (Devanagari → Hindi, ASCII → English)

5. **Error Recovery:** Add retry logic for transient errors
   - Retry on: NO_MATCH, NETWORK_TIMEOUT
   - Don't retry on: INSUFFICIENT_PERMISSIONS, RECOGNIZER_BUSY (too many active)

**Test Cases:**
- [ ] Hindi speech recognized correctly
- [ ] English speech recognized correctly
- [ ] Hinglish (mixed) recognized correctly
- [ ] Partial results update in real-time
- [ ] Network error handled gracefully
- [ ] Permission error handled
- [ ] Timeout handled

**Implementation Effort:** 2-3 hours
**Blockers:** None (Android SpeechRecognizer API stable)

---

## PHASE 5: NATURAL FEMALE VOICE - COMPLETE SPECIFICATION

**Current State:** TTSManager.kt has voice profile system. Female voice detection exists in `isFemaleVoiceName()`.

**Implementation Tasks:**

1. **Voice Selection:** Improve female voice detection in TTSManager.initialize()
   ```kotlin
   private fun selectFemaleVoice() {
       val voices = textToSpeech?.voices?.filter { isFemaleVoiceName(it.name) } ?: emptySet()
       if (voices.isNotEmpty()) {
           textToSpeech?.voice = voices.first()
           _voiceStatus.value = VoiceStatus(
               gender = VoiceGender.FEMALE,
               voiceName = voices.first().name
           )
       } else {
           // Fallback to first available voice
           val allVoices = textToSpeech?.voices ?: emptySet()
           if (allVoices.isNotEmpty()) {
               textToSpeech?.voice = allVoices.first()
               _voiceStatus.value = VoiceStatus(
                   gender = VoiceGender.UNLABELLED,
                   voiceName = allVoices.first().name
               )
           } else {
               _voiceStatus.value = VoiceStatus(gender = VoiceGender.NO_VOICES)
           }
       }
   }
   ```

2. **Gemini Native Audio:** Investigate integration point (FUTURE)
   - Note: Current implementation uses Android TTS
   - Gemini native audio available in newer models
   - Integration point: GeminiProvider.kt → add audio response handling
   - Mark as PHASE 8 investigation

3. **Hinglish Support:** Ensure proper text handling
   - Keep Devanagari as-is (TTS handles it)
   - Don't transliterate unless needed
   - Test: "Hello Vasu, torch on kar do" → proper pronunciation

4. **Prosody Improvement:** Add natural pauses and emphasis
   - File: New function `addNaturalPauses(text: String): String`
   - Logic: Add SSML pauses after periods, commas
   - Example: "Hello." → `Hello.<break time="500ms"/>`

**Test Cases:**
- [ ] Female voice selected when available
- [ ] Fallback to available voice if no female voice
- [ ] Hindi text pronounced correctly
- [ ] Hinglish mixed text pronounced correctly
- [ ] Natural pauses added after punctuation
- [ ] Voice status reported accurately

**Implementation Effort:** 2 hours
**Blockers:** None (Android TTS API stable)

---

## PHASE 6: CHAT SYSTEM - COMPLETE SPECIFICATION

**Current State:** ChatScreen.kt and ChatViewModel.kt exist. Basic UI present.

**Issues Found:**
1. Chat messages not persisted to database
2. AI response generation incomplete
3. No error recovery for failed messages

**Implementation Tasks:**

1. **Database Persistence:** Save all messages
   ```kotlin
   // In ChatViewModel.kt
   private fun saveMessage(message: ChatMessage) {
       viewModelScope.launch {
           conversationDao.insert(
               ConversationEntity(
                   id = message.id,
                   content = message.content,
                   isUser = message.isUser,
                   timestamp = message.timestamp,
                   conversationId = currentConversationId
               )
           )
       }
   }
   ```

2. **Load Chat History:** On screen open
   ```kotlin
   private fun loadConversationHistory() {
       viewModelScope.launch {
           val messages = conversationDao.getConversation(currentConversationId)
               .map { ChatMessage(
                   id = it.id,
                   content = it.content,
                   isUser = it.isUser,
                   timestamp = it.timestamp
               )}
           _uiState.value = _uiState.value.copy(messages = messages)
       }
   }
   ```

3. **AI Response Integration:** Connect to AIOrchestrator properly
   ```kotlin
   private fun processMessage(text: String) {
       viewModelScope.launch {
           _uiState.value = _uiState.value.copy(isLoading = true)
           try {
               val response = aiOrchestrator.processInput(text)
               addMessage(ChatMessage(content = response, isUser = false))
               ttsManager.speakQueued(response)
           } catch (e: Exception) {
               addMessage(ChatMessage(content = "Error: ${e.message}", isUser = false))
           } finally {
               _uiState.value = _uiState.value.copy(isLoading = false)
           }
       }
   }
   ```

4. **Error Recovery:** Retry failed messages
   - Add retry button to failed messages
   - Max 3 retries per message
   - Clear error state after retry attempt

5. **Streaming Responses:** Show response as it arrives
   - For Gemini streaming: Update message as chunks arrive
   - Show typing indicator during streaming

**Test Cases:**
- [ ] Send text message → saved to DB
- [ ] Load screen → previous messages visible
- [ ] Voice input → message sent
- [ ] AI response received → displayed and persisted
- [ ] Failed message → retry button visible
- [ ] Clear conversation → all deleted

**Implementation Effort:** 3 hours
**Blockers:** AIOrchestrator must be working (verify Phase 7)

---

## PHASE 7: GEMINI AI - COMPLETE SPECIFICATION

**Current State:** GeminiProvider.kt exists with real Gemini integration. API key stored securely.

**Issues Found:**
1. Model selection might be hardcoded
2. Error handling exists but needs verification
3. Tool calling needs verification

**Implementation Tasks:**

1. **Model Discovery:** Verify models are discovered at runtime
   - File: GeminiProvider.kt::testConnection()
   - Status: Already calls fetchCatalog()
   - Test: Verify it lists actual available models

2. **Model Fallback:** If selected model unavailable, use next best
   ```kotlin
   private fun selectModelChain(
       preferred: String,
       config: AiProviderConfig,
       available: List<String>?
   ): List<String> {
       val models = available ?: emptyList()
       if (models.isEmpty()) return emptyList()
       
       // Try preferred first
       if (preferred in models) return listOf(preferred)
       
       // Fallback to first available
       return listOf(models.first())
   }
   ```

3. **Error Handling:** Verify all error types handled
   - File: GeminiProvider.kt
   - Check: NOT_CONFIGURED, INVALID_KEY, OFFLINE, TIMEOUT, RATE_LIMITED
   - Test: Each error type produces correct user message

4. **Tool Execution:** Verify tool calling works
   - File: GeminiProvider.kt + ToolRouter.kt
   - Test: Simple tool call (e.g., torch on) succeeds end-to-end

5. **Connection Testing:** Settings screen test button
   - File: SettingsScreen.kt
   - Test: Button calls testConnection(), shows result

**Test Cases:**
- [ ] Invalid API key → "Invalid key" error
- [ ] Valid API key → models listed
- [ ] Selected model unavailable → fallback selected
- [ ] Network offline → "Offline" error
- [ ] Rate limited → "Rate limited" error
- [ ] Tool called via chat → executes correctly

**Implementation Effort:** 2 hours
**Blockers:** API key must be set in Settings

---

## PHASE 8-10: VOICE PROFILE, GUARDIAN, MEMORY

These are partially implemented and need:
- Testing of existing code paths
- Error handling completeness
- UI integration verification

**Quick Checklist:**

**Phase 8: Natural Voice**
- [ ] Verify female voice selected when available
- [ ] Test Hinglish pronunciation
- [ ] Add natural pauses (SSML)

**Phase 9: Guardian**
- [ ] Test enrollment flow end-to-end
- [ ] Verify speaker verification working
- [ ] Check sensitive command gating

**Phase 10: Memory**
- [ ] Test save/retrieve memory
- [ ] Verify memory loaded in AI context
- [ ] Test search functionality

---

## PHASES 11-25: QUICK IMPLEMENTATION GUIDE

### Phase 11: Permissions Center
- Replace Missions button on home with Permissions
- List all 29 Android permissions
- Show status (granted/denied) with refresh
- Open Settings for each permission

### Phase 12-15: Communication & Device Control
Most of this is intents-based and already working:
- Calls: Use CallManager (already done)
- SMS: Use MessagingManager (already done)
- WhatsApp: Use MessagingManager (already done)
- Email: Add to MessagingManager
- Device Control: Already in DeviceControlManager (torch, volume, battery, media)

Just verify each works end-to-end.

### Phase 16-18: Location, Browser, Automation
- Location: LocationManager already done, just test
- Browser: Use Intent for now (full automation is accessibility-based)
- Screen Automation: Accessibility system already ready, verify

### Phase 19-20: Natural Commands & Offline Mode
- Natural commands: Already handled by IntentParser + AIOrchestrator
- Offline mode: NetworkMonitor already done, verify graceful degradation

### Phase 21-22: UI Polish & Error Handling
- Consistent spacing and fonts across all screens
- Loading states, error states, empty states
- Proper error messages for each failure case

### Phase 23: Testing
- Build the app
- Test wake word
- Test chat
- Test each permission
- Test each communication feature
- Test device controls
- Verify no crashes

### Phase 24: Git Safety
- Commit after each major feature
- Tag important milestones
- Keep history clean

### Phase 25: Final Verification
- Build APK
- Manual E2E testing
- Verify no existing features removed
- Document known limitations

---

## Next Steps

1. **Build System:** Set up Java/Gradle environment
2. **Phase 3 Implementation:** Complete wake word detection
3. **Phase 4 Implementation:** Complete STT with error handling
4. **Phase 5 Implementation:** Complete natural voice selection
5. **Continue:** Phases 6-25 with focus and git checkpoints

**Estimated Total Time:** 20-30 hours of focused development

