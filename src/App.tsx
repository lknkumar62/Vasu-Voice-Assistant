import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  AssistantState,
  VasuSettings,
  ChatMessage,
  MemoryItem,
  MissionItem,
  PermissionItem,
  ToolRisk,
} from './types';
import { VasuOrb } from './components/VasuOrb';
import { MicButton } from './components/MicButton';
import { QuickActions, ScreenTab } from './components/QuickActions';
import { VasuBottomBar } from './components/VasuBottomBar';
import { ChatView } from './components/ChatView';
import { ToolsView } from './components/ToolsView';
import { MemoryView } from './components/MemoryView';
import { MissionsView } from './components/MissionsView';
import { PermissionsView } from './components/PermissionsView';
import { GuardianView } from './components/GuardianView';
import { SettingsView } from './components/SettingsView';
import { HomeView } from './components/HomeView';
import { VoiceView } from './components/VoiceView';
import { AndroidProjectModal } from './components/AndroidProjectModal';

import { audioEngine } from './services/audioEngine';
import { ttsManager } from './services/ttsManager';
import { wakeWordEngine, WakeWordStatus, WakeWordEvent } from './services/wakeWordEngine';
import { speechRecognizer, SpeechError } from './services/speechRecognizer';
import { ToolExecutor, ToolExecutionResult } from './services/toolRegistry';
import { LocalCommandEngine } from './services/localCommandEngine';
import { GeminiClient } from './services/geminiClient';
import { apiKeyManager } from './services/apiKeyManager';
import { aiProviderManager } from './services/aiProviderManager';

import {
  ShieldAlert,
  Zap,
  Volume2,
  Settings,
  Flashlight,
  Wifi,
  WifiOff,
  AlertTriangle,
  RotateCcw,
  Bot,
  Home,
  Octagon,
  Mic,
} from 'lucide-react';

export default function App() {
  // 1. Assistant State Machine
  const [assistantState, setAssistantState] = useState<AssistantState>('IDLE');
  const [activeTab, setActiveTab] = useState<ScreenTab>('HOME');
  const [audioLevel, setAudioLevel] = useState<number>(0);
  const [activeToolName, setActiveToolName] = useState<string | undefined>(undefined);
  const [isTorchActive, setIsTorchActive] = useState<boolean>(false);
  const [showAndroidModal, setShowAndroidModal] = useState<boolean>(false);
  const [isOnline, setIsOnline] = useState<boolean>(true);
  const [wakeStatus, setWakeStatus] = useState<WakeWordStatus>('DISABLED');
  const wakeStatusRef = useRef<WakeWordStatus>('DISABLED');
  wakeStatusRef.current = wakeStatus;

  // Audio Context unlock on first user interaction & early greeting preload
  useEffect(() => {
    const unlockAudio = () => {
      audioEngine.unlock();
      audioEngine.preloadGreetingAudio();
    };
    window.addEventListener('click', unlockAudio, { once: true });
    window.addEventListener('touchstart', unlockAudio, { once: true });
    audioEngine.preloadGreetingAudio();
    return () => {
      window.removeEventListener('click', unlockAudio);
      window.removeEventListener('touchstart', unlockAudio);
    };
  }, []);

  // 2. Settings & Preferences
  const [settings, setSettings] = useState<VasuSettings>(() => {
    const saved = localStorage.getItem('vasu_settings');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (_) {}
    }
    return {
      geminiApiKey: '',
      geminiModel: 'gemini-3.6-flash',
      openrouterApiKey: '',
      openrouterModel: 'openrouter/free',
      groqApiKey: '',
      groqModel: 'llama-3.1-8b-instant',
      deepseekApiKey: '',
      deepseekModel: 'deepseek-chat',
      xaiApiKey: '',
      xaiModel: 'grok-2-mini',
      customOpenaiBaseUrl: '',
      customOpenaiApiKey: '',
      customOpenaiModel: '',
      tavilyApiKey: '',
      braveSearchApiKey: '',
      providerPriority: ['gemini', 'openrouter', 'groq', 'deepseek', 'xai', 'custom'],
      searchProviderPriority: ['tavily', 'brave', 'gemini_grounding'],
      freeOnlyMode: false,
      costProtection: {
        maxRequestsPerDay: 500,
        maxTokensPerRequest: 2000,
        maxSearchRequestsPerDay: 100,
        maxProviderRetries: 3,
        allowPaidProvider: true,
        allowFreeOnlyMode: false,
      },
      wakeWordEnabled: true,
      wakePhrase: 'Hello VASU',
      wakeSensitivity: 'MEDIUM',
      backgroundListening: true,
      ttsVoice: 'Kore',
      ttsSpeed: 1.0,
      ttsPitch: 1.05,
      language: 'Hinglish',
      smartMode: 'NORMAL',
      guardianActive: true,
      requireConfirmationForHighRisk: true,
      followUpMode: true,
      autoSpeak: true,
      torchActive: false,
      volumeLevel: 70,
      ttsVolume: 1.0,
    };
  });

  // 3. Conversation Messages (seeded matching user screenshot in Hinglish)
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    try {
      const saved = localStorage.getItem('vasu_messages');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch (_) {}
    return [
      {
        id: 'msg_user_1',
        sender: 'user',
        text: 'hello vasu',
        timestamp: Date.now() - 40000,
      },
      {
        id: 'msg_vasu_1',
        sender: 'vasu',
        text: 'Hello ji! Phirse Vasu? Lagta hai Vasu tumhare khayalon me kuch zyada hi chhayi hui hai 😉 Main toh aapki pyaari Vasu hoon na! Bolo, kya chal raha hai aaj? Koi kaam waam hai ya chill mode?',
        timestamp: Date.now() - 35000,
      },
    ];
  });

  // Persist messages to localStorage
  useEffect(() => {
    if (messages.length > 0) {
      localStorage.setItem('vasu_messages', JSON.stringify(messages.slice(-100)));
    }
  }, [messages]);

  // Continuous conversational listening states
  const [isContinuousListening, setIsContinuousListening] = useState<boolean>(false);
  const isContinuousListeningRef = useRef<boolean>(false);
  isContinuousListeningRef.current = isContinuousListening;
  const assistantStateRef = useRef<AssistantState>('IDLE');
  assistantStateRef.current = assistantState;

  // Live voice input for real-time transcription in Chat
  const [liveVoiceTranscript, setLiveVoiceTranscript] = useState('');
  // Processing lock to prevent duplicate command processing
  const processingRef = useRef<boolean>(false);
  const processMessageIdRef = useRef<string>('');

  // 4. Persistent Memory
  const [memories, setMemories] = useState<MemoryItem[]>(() => {
    const saved = localStorage.getItem('vasu_memories');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (_) {}
    }
    return [
      {
        id: 'mem_1',
        category: 'USER_PREFERENCE',
        fact: 'User prefers Hinglish and Indian Hindi conversation',
        createdAt: Date.now() - 3600000,
      },
      {
        id: 'mem_2',
        category: 'DEVICE_PREFERENCE',
        fact: 'Morning routine set for 07:00 AM with alarm briefing',
        createdAt: Date.now() - 7200000,
      },
    ];
  });

  // 5. Automated Missions
  const [missions, setMissions] = useState<MissionItem[]>([
    {
      id: 'mis_1',
      title: 'Morning Routine & Briefing',
      triggerType: 'TIME',
      triggerValue: '07:00 AM',
      action: 'Good morning bolna, weather aur daily tasks summary padhna',
      isEnabled: true,
      lastRun: Date.now() - 86400000,
    },
    {
      id: 'mis_2',
      title: 'Low Battery Guardian Alert',
      triggerType: 'BATTERY',
      triggerValue: '< 20%',
      action: 'Battery saver activate karna aur user ko alert dena',
      isEnabled: true,
    },
    {
      id: 'mis_3',
      title: 'Night Sleep Mode',
      triggerType: 'EVENT',
      triggerValue: '11:00 PM',
      action: 'Mute sounds, reduce brightness aur DND switch on karna',
      isEnabled: false,
    },
  ]);

  // 6. Permissions State with Local Persistence
  const [permissions, setPermissions] = useState<PermissionItem[]>(() => {
    const saved = localStorage.getItem('vasu_permissions');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (_) {}
    }
    return [
      { id: 'microphone', name: 'Microphone', description: 'Voice input and real-time audio capture', status: 'GRANTED', category: 'RUNTIME' },
      { id: 'accessibility', name: 'Accessibility Service', description: 'Inspect UI nodes, read screen text, and click elements', status: 'GRANTED', category: 'SPECIAL' },
      { id: 'notifications', name: 'Notification Access', description: 'Read and summarize incoming alerts from WhatsApp, Messages', status: 'GRANTED', category: 'SPECIAL' },
      { id: 'camera', name: 'Camera & Flashlight', description: 'Torch control and camera photo capture', status: 'GRANTED', category: 'HARDWARE' },
      { id: 'files', name: 'Storage & Files', description: 'Browse downloads, search documents, storage metrics', status: 'GRANTED', category: 'RUNTIME' },
      { id: 'phone', name: 'Phone & Dialer', description: 'Initiate voice calls to contacts', status: 'GRANTED', category: 'RUNTIME' },
      { id: 'contacts', name: 'Contacts', description: 'Find phone numbers and names', status: 'GRANTED', category: 'RUNTIME' },
      { id: 'alarms', name: 'Alarms & Timers', description: 'Schedule exact alarms and reminder routines', status: 'GRANTED', category: 'SPECIAL' },
      { id: 'foreground_service', name: 'Foreground Service', description: 'Background wake-word listening with notification', status: 'GRANTED', category: 'SPECIAL' },
      { id: 'bluetooth', name: 'Bluetooth', description: 'Device connection and audio routing', status: 'GRANTED', category: 'HARDWARE' },
    ];
  });

  // Persist permissions changes
  useEffect(() => {
    localStorage.setItem('vasu_permissions', JSON.stringify(permissions));
  }, [permissions]);

  // 7. Security Audit Log
  const [executionLogs, setExecutionLogs] = useState<{ tool: string; risk: ToolRisk; timestamp: number; success: boolean }[]>([]);

  // Sync settings to storage & audio engine
  useEffect(() => {
    localStorage.setItem('vasu_settings', JSON.stringify(settings));
    audioEngine.setApiKey(settings.geminiApiKey || '');
    // Always keep aiProviderManager in sync with settings
    if (settings.geminiApiKey) {
      try {
        aiProviderManager.configure('gemini', { apiKey: settings.geminiApiKey, enabled: true });
      } catch (_) {}
    }
  }, [settings]);

  // Sync apiKey to server on boot if already stored
  useEffect(() => {
    console.log('[App] Boot sync running, geminiApiKey:', settings.geminiApiKey ? settings.geminiApiKey.substring(0,8) + '...' : 'EMPTY');
    if (settings.geminiApiKey) {
      audioEngine.setApiKey(settings.geminiApiKey);
      // Direct configure aiProviderManager (in case apiKeyManager fails silently)
      try {
        aiProviderManager.configure('gemini', { apiKey: settings.geminiApiKey, enabled: true });
        console.log('[App] Direct aiProviderManager.configure done');
      } catch (e) {
        console.error('[App] aiProviderManager.configure failed:', e);
      }
      // Also try via apiKeyManager
      try {
        apiKeyManager.setKey('gemini', settings.geminiApiKey);
        console.log('[App] apiKeyManager.setKey completed');
      } catch (e) {
        console.error('[App] apiKeyManager.setKey failed:', e);
      }
      fetch('/api/gemini/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey: settings.geminiApiKey }),
      }).catch(() => {});
    }
  }, []);

  // Sync memories to storage
  useEffect(() => {
    localStorage.setItem('vasu_memories', JSON.stringify(memories));
  }, [memories]);

  // Online / Offline monitor
  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => {
      setIsOnline(false);
      setAssistantState('OFFLINE');
    };
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Emergency Stop Handler
  const handleEmergencyStop = useCallback(() => {
    audioEngine.stop();
    speechRecognizer.cancel();
    wakeWordEngine.pause();
    setAssistantState('IDLE');
    setActiveToolName(undefined);
    audioEngine.playErrorChime();

    setMessages((prev) => [
      ...prev,
      {
        id: `stop_${Date.now()}`,
        sender: 'system',
        text: 'EMERGENCY STOP TRIGGERED: All active speech, tools, and background tasks halted.',
        timestamp: Date.now(),
      },
    ]);
  }, []);

  // Central Tool Executor
  const handleExecuteTool = useCallback(
    async (toolId: string, args: Record<string, any> = {}, logToChat: boolean = false): Promise<ToolExecutionResult> => {
      setActiveToolName(toolId);
      setAssistantState('EXECUTING');

      const res = await ToolExecutor.execute(toolId, args, {
        setTorchState: (on: boolean) => setIsTorchActive(on),
      });

      // Log execution for Guardian
      setExecutionLogs((prev) => [
        ...prev,
        {
          tool: toolId,
          risk: res.risk,
          timestamp: Date.now(),
          success: res.success,
        },
      ]);

      if (logToChat) {
        setMessages((prev) => [
          ...prev,
          {
            id: `tool_${Date.now()}`,
            sender: 'vasu',
            text: res.result,
            timestamp: Date.now(),
            badge: 'TOOL ACTION',
            toolCall: {
              tool: toolId,
              status: res.success ? 'success' : 'error',
              result: res.result,
            },
          },
        ]);
      }

      if (res.success) {
        audioEngine.playSuccessChime();
      }

      return res;
    },
    []
  );

  // Ref for continuous Jarvis conversation loop
  const startListeningRef = useRef<() => void>(() => {});

  // Centralized Assistant Speech Pipeline:
  // Guarantees all assistant responses (typed, voice, tools, replay) speak consistently,
  // clean prompt leakage, enforce settle delay, and prevent mic feedback loops.
  const speakAssistantResponse = useCallback(
    async (
      text: string,
      options: {
        source?: 'voice' | 'typed' | 'replay' | 'tool' | 'wake';
        force?: boolean;
      } = {}
    ) => {
      const { source = 'typed', force } = options;
      speechRecognizer.stop();
      wakeWordEngine.pause();
      setAssistantState('SPEAKING');

      // Play TTS — with max 12s timeout so it never blocks forever
      try {
        await Promise.race([
          ttsManager.speak(text, {
            autoSpeak: settings.autoSpeak,
            speed: settings.ttsSpeed,
            pitch: settings.ttsPitch,
            volume: settings.ttsVolume,
            apiKey: settings.geminiApiKey || '',
            source,
            force,
          }),
          new Promise<void>((resolve) => setTimeout(() => resolve(), 12000))
        ]);
      } catch (_) {}

      // After TTS (or timeout), restart mic for continuous conversation
      setAssistantState('IDLE');
      if (source === 'voice' || source === 'wake') {
        if (settings.followUpMode || isContinuousListeningRef.current) {
          setTimeout(() => {
            if (assistantStateRef.current === 'IDLE') {
              startListeningRef.current?.();
            }
          }, 300);
        } else if (settings.wakeWordEnabled && wakeStatusRef.current !== 'DISABLED') {
          wakeWordEngine.resume();
        }
      } else {
        if (settings.wakeWordEnabled && wakeStatusRef.current !== 'DISABLED') {
          wakeWordEngine.resume();
        }
      }
    },
    [settings.ttsSpeed, settings.ttsPitch, settings.ttsVolume, settings.autoSpeak, settings.geminiApiKey, settings.followUpMode, settings.wakeWordEnabled]
  );

  // Process Natural Language Command (supports text or voice input)
  const processUserCommand = useCallback(
    async (text: string, isVoice = false) => {
      if (!text.trim()) return;

      // Deduplication: prevent same text processed twice within 800ms
      const dedupeKey = `${text.trim().toLowerCase()}_${isVoice ? 'v' : 't'}`;
      if (processingRef.current && processMessageIdRef.current === dedupeKey) {
        return;
      }
      processingRef.current = true;
      processMessageIdRef.current = dedupeKey;

      try {

      // Append user message with voice indicator
      const userMsgId = `user_${Date.now()}`;
      setMessages((prev) => [
        ...prev,
        {
          id: userMsgId,
          sender: 'user',
          text,
          timestamp: Date.now(),
          isVoice,
          badge: isVoice ? 'VOICE COMMAND' : undefined,
        },
      ]);

      setAssistantState('THINKING');

      // 1. Fast path: Check Deterministic Local Command Engine
      // If user has Gemini API key configured, delegate general conversation to Gemini
      // and only match hardware device tools, camera, alarms, calls, and memory storage
      const hasGeminiKey = Boolean(settings.geminiApiKey);
      const localMatch = LocalCommandEngine.parse(text, {
        allowConversationalMatches: !hasGeminiKey,
      });

      if (localMatch.matched) {
        // Handle memory storage command
        if (localMatch.isMemoryCommand && localMatch.memoryFact) {
          const newMem: MemoryItem = {
            id: `mem_${Date.now()}`,
            category: 'USER_PREFERENCE',
            fact: localMatch.memoryFact,
            createdAt: Date.now(),
          };
          setMemories((prev) => [newMem, ...prev]);

          const reply = localMatch.spokenResponse || 'Ji, maine yaad rakh liya hai.';
          setMessages((prev) => [
            ...prev,
            {
              id: `vasu_${Date.now()}`,
              sender: 'vasu',
              text: reply,
              timestamp: Date.now(),
              badge: 'MEMORY SAVED',
            },
          ]);

          await speakAssistantResponse(reply, { source: isVoice ? 'voice' : 'typed' });
          return;
        }

        // Handle tool execution command
        if (localMatch.toolId) {
          const toolId = localMatch.toolId;
          const toolArgs = localMatch.args || {};

          // Execute tool
          const execResult = await handleExecuteTool(toolId, toolArgs);

          // Prepare spoken reply
          const spokenReply = localMatch.spokenResponse || execResult.result;

          setMessages((prev) => [
            ...prev,
            {
              id: `vasu_${Date.now()}`,
              sender: 'vasu',
              text: spokenReply,
              timestamp: Date.now(),
              badge: isVoice ? 'VOICE ACTION' : 'TOOL ACTION',
              toolCall: {
                tool: toolId,
                status: execResult.success ? 'success' : 'error',
                result: execResult.result,
              },
            },
          ]);

          setActiveToolName(undefined);
          await speakAssistantResponse(spokenReply, { source: isVoice ? 'voice' : 'typed' });
          return;
        }

        // Handle general conversational offline responses (e.g. greetings, identity, status)
        if (localMatch.spokenResponse) {
          const reply = localMatch.spokenResponse;
          setMessages((prev) => [
            ...prev,
            {
              id: `vasu_${Date.now()}`,
              sender: 'vasu',
              text: reply,
              timestamp: Date.now(),
              badge: 'LOCAL JARVIS',
            },
          ]);

          await speakAssistantResponse(reply, { source: isVoice ? 'voice' : 'typed' });
          return;
        }
      }

      // 2. AI Brain via Gemini Client (Direct Gemini REST + Server fallback + Offline Jarvis)
      const aiMsgId = `vasu_${Date.now()}_ai`;
      try {
        const historyContext = messages.slice(-8).map((m) => ({
          role: (m.sender === 'user' ? 'user' : 'model') as 'user' | 'model',
          parts: [{ text: m.text }],
        }));

        const chatResult = await GeminiClient.chat({
          message: text,
          history: historyContext,
          memoryContext: memories.slice(0, 5),
          smartMode: settings.smartMode,
          language: settings.language,
          apiKey: settings.geminiApiKey || undefined,
        });

        const replyText = chatResult.replyText;

        setMessages((prev) => [
          ...prev,
          {
            id: aiMsgId,
            sender: 'vasu',
            text: replyText,
            timestamp: Date.now(),
            badge: chatResult.source === 'gemini_direct' ? 'GEMINI AI' : (chatResult.source === 'gemini_server' ? 'AI SERVER' : 'JARVIS'),
          },
        ]);

        await speakAssistantResponse(replyText, { source: isVoice ? 'voice' : 'typed' });
      } catch (err: any) {
        console.warn("Gemini chat error:", err);
        const fallbackText = "Maaf kijiye, connect hone mein thodi takleef hui. Offline commands chalu hain.";

        setMessages((prev) => [
          ...prev,
          {
            id: aiMsgId,
            sender: 'vasu',
            text: fallbackText,
            timestamp: Date.now(),
            badge: 'OFFLINE MODE',
          },
        ]);

        await speakAssistantResponse(fallbackText, { source: isVoice ? 'voice' : 'typed' });
      }
      } finally {
        processingRef.current = false;
      }
    },
    [memories, settings, messages, handleExecuteTool, speakAssistantResponse]
  );

  // Start Microphone Listening Session
  const handleStartListening = useCallback(async () => {
    // If thinking or audio engine is settling, block listening
    if (
      assistantStateRef.current === 'THINKING' ||
      audioEngine.isInSettleDelay()
    ) {
      console.log("[handleStartListening] Blocked because assistant is thinking or settling");
      return;
    }

    // If TTS is playing, STOP it so user can speak
    if (assistantStateRef.current === 'SPEAKING' || audioEngine.isSpeaking()) {
      console.log("[handleStartListening] Stopping TTS to allow user speech");
      audioEngine.stop();
      ttsManager.stop();
    }

    audioEngine.unlock();
    wakeWordEngine.pause();
    // Brief settle time
    await new Promise((r) => setTimeout(r, 120));
    setAssistantState('LISTENING');

    await speechRecognizer.startListening(
      {
        onPartialResult: (partial) => {
          setLiveVoiceTranscript(partial);
        },
        onFinalResult: (finalText) => {
          setLiveVoiceTranscript('');
          speechRecognizer.stop();
          setAssistantState('THINKING');
          processUserCommand(finalText, true);
        },
        onAudioLevel: (lvl) => {
          setAudioLevel(lvl);
        },
        onError: (err: SpeechError) => {
          console.warn("STT Error:", err);
          setLiveVoiceTranscript('');
          speechRecognizer.cancel();
          if (err.type === 'MIC_PERMISSION') {
            audioEngine.playErrorChime();
            setMessages((prev) => [
              ...prev,
              {
                id: `stt_err_${Date.now()}`,
                sender: 'system',
                text: 'Microphone permission denied. Settings me allow kijiye.',
                timestamp: Date.now(),
                badge: 'MIC PERMISSION',
              },
            ]);
            setAssistantState('ERROR');
            setTimeout(() => {
              setAssistantState('IDLE');
              wakeWordEngine.resume();
            }, 2000);
          } else {
            setAssistantState('IDLE');
            wakeWordEngine.resume();
          }
        },
        onEnd: () => {
          setLiveVoiceTranscript('');
          setAssistantState((curr) => {
            if (curr === 'LISTENING') {
              wakeWordEngine.resume();
              return 'IDLE';
            }
            return curr;
          });
        },
        onSilenceTimeout: () => {
          setLiveVoiceTranscript('');
          setAssistantState('IDLE');
          wakeWordEngine.resume();
        },
      },
      settings.language,
      true
    );
  }, [settings.language, processUserCommand]);

  // Keep ref up to date
  useEffect(() => {
    startListeningRef.current = handleStartListening;
  }, [handleStartListening]);

  // Toggle Microphone Listening Session
  const handleToggleListen = useCallback(() => {
    audioEngine.unlock();

    // If speaking, interrupt immediately!
    if (assistantState === 'SPEAKING') {
      ttsManager.stop();
      setAssistantState('IDLE');
      wakeWordEngine.resume();
      return;
    }

    // If in error or stuck, reset cleanly to IDLE!
    if (assistantState === 'ERROR') {
      setIsContinuousListening(false);
      isContinuousListeningRef.current = false;
      speechRecognizer.stop();
      ttsManager.stop();
      setAssistantState('IDLE');
      wakeWordEngine.resume();
      return;
    }

    // If already listening, stop continuous mode
    if (assistantState === 'LISTENING') {
      setIsContinuousListening(false);
      isContinuousListeningRef.current = false;
      speechRecognizer.stop();
      setAssistantState('IDLE');
      wakeWordEngine.resume();
      return;
    }

    // Start continuous listening
    setIsContinuousListening(true);
    isContinuousListeningRef.current = true;
    handleStartListening();
  }, [assistantState, handleStartListening]);

  // Stable Wake Word trigger handler
  const handleWakeWordTriggerRef = useRef<(event: WakeWordEvent) => void>(() => {});

  handleWakeWordTriggerRef.current = async (event: WakeWordEvent) => {
    audioEngine.unlock();
    console.log("Wake word triggered:", event);

    if (event.command && event.command.trim().length > 1) {
      // Command was spoken in the same breath (e.g. "Hello VASU torch on karo")
      setMessages((prev) => [
        ...prev,
        {
          id: `wake_${Date.now()}`,
          sender: 'system',
          text: `🎙️ Background Wake Word: "${event.wakeWord || 'Hello VASU'}" detected with command`,
          timestamp: Date.now(),
          badge: 'WAKE WORD',
        },
      ]);
      await audioEngine.playWakeChime();
      wakeWordEngine.pause();
      await processUserCommand(event.command.trim(), true);
    } else {
      // User said "Hello VASU" / "Hey VASU"
      wakeWordEngine.pause();
      setAssistantState('SPEAKING');

      setMessages((prev) => [
        ...prev,
        {
          id: `wake_${Date.now()}`,
          sender: 'system',
          text: `🎙️ Background Wake Word Heard: "${event.wakeWord || 'Hello VASU'}"`,
          timestamp: Date.now(),
          badge: 'WAKE WORD',
        },
      ]);

      const greeting = settings.language === 'English'
        ? "Yes! I'm listening, go ahead."
        : "Hello ji! Boliye, main sun rahi hoon.";

      setMessages((prev) => [
        ...prev,
        {
          id: `vasu_${Date.now()}`,
          sender: 'vasu',
          text: greeting,
          timestamp: Date.now(),
          badge: 'WAKE GREETING',
        },
      ]);

      // 1. First play the crisp wake chime and wait for it to finish (320ms)
      await audioEngine.playWakeChime();

      // 2. Speak the quick greeting with zero-latency local speech
      await audioEngine.speakWakeGreeting(greeting, {
        speed: settings.ttsSpeed,
        pitch: settings.ttsPitch,
        language: settings.language,
      });

      // 3. Start listening after greeting settles
      setTimeout(() => {
        if (assistantStateRef.current === 'IDLE' || assistantStateRef.current === 'SPEAKING') {
          setAssistantState('IDLE');
          startListeningRef.current?.();
        }
      }, 1500);
    }
  };

  // Explicit voice activation (unlocks AudioContext + asks for mic permission + starts wakeWordEngine)
  const handleActivateVoice = useCallback(async () => {
    audioEngine.unlock();
    try {
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach((t) => t.stop());
      }
      setPermissions((prev) =>
        prev.map((p) => (p.id === 'microphone' ? { ...p, status: 'GRANTED' } : p))
      );
      const st = await wakeWordEngine.start();
      setWakeStatus(st);
      audioEngine.playSuccessChime();
    } catch (err) {
      console.warn("Could not activate microphone:", err);
      setPermissions((prev) =>
        prev.map((p) => (p.id === 'microphone' ? { ...p, status: 'DENIED' } : p))
      );
      setWakeStatus('PERMISSION_REQUIRED');
    }
  }, []);

  // Enable All Permissions 1-Tap Handler
  const handleEnableAllPermissions = useCallback(async () => {
    audioEngine.unlock();
    try {
      // 1. Request microphone
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        try {
          const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
          stream.getTracks().forEach((t) => t.stop());
        } catch (_) {}
      }

      // 2. Request browser notifications
      if ('Notification' in window && Notification.permission !== 'granted') {
        try {
          await Notification.requestPermission();
        } catch (_) {}
      }

      // 3. Mark all permissions as GRANTED
      setPermissions((prev) => prev.map((p) => ({ ...p, status: 'GRANTED' })));

      // 4. Start wake word engine
      const st = await wakeWordEngine.start();
      setWakeStatus(st);

      // 5. Auditory & visual feedback
      audioEngine.playSuccessChime();
      const reply = "सभी अनुमतियां सफलतापूर्वक सक्षम कर दी गई हैं। वासु आपके हर आदेश के लिए पूरी तरह से तैयार है।";
      setMessages((prev) => [
        ...prev,
        {
          id: `perm_all_${Date.now()}`,
          sender: 'vasu',
          text: reply,
          timestamp: Date.now(),
          badge: 'ALL PERMISSIONS GRANTED',
        },
      ]);
      await ttsManager.forceSpeak(reply, {
        autoSpeak: true,
        speed: settings.ttsSpeed,
        pitch: settings.ttsPitch,
        volume: settings.ttsVolume,
        apiKey: settings.geminiApiKey || '',
        source: 'system',
      });
    } catch (err) {
      console.warn("Error enabling all permissions:", err);
    }
  }, [settings.ttsSpeed, settings.ttsPitch, settings.ttsVolume, settings.geminiApiKey]);

  // Dedicated Read Notifications handler
  const handleReadNotifications = useCallback(async () => {
    audioEngine.unlock();
    const result = await handleExecuteTool('read_notifications', {});
    setMessages((prev) => [
      ...prev,
      {
        id: `notif_${Date.now()}`,
        sender: 'vasu',
        text: result.result,
        timestamp: Date.now(),
        badge: 'NOTIFICATIONS',
        toolCall: {
          tool: 'read_notifications',
          status: result.success ? 'success' : 'error',
          result: result.result,
        },
      },
    ]);
    setAssistantState('SPEAKING');
    wakeWordEngine.pause();
    await ttsManager.forceSpeak(result.result, {
      autoSpeak: true,
      speed: settings.ttsSpeed,
      pitch: settings.ttsPitch,
      volume: settings.ttsVolume,
      apiKey: settings.geminiApiKey || '',
      source: 'tool',
    });
    wakeWordEngine.resume();
    setAssistantState('IDLE');
  }, [handleExecuteTool, settings.ttsSpeed, settings.ttsPitch, settings.ttsVolume, settings.geminiApiKey]);

  // Setup Wake Word Engine callbacks (stable ref avoids re-initializing on state updates)
  useEffect(() => {
    wakeWordEngine.setCallbacks(
      (event) => handleWakeWordTriggerRef.current(event),
      (level) => setAudioLevel(level)
    );

    let isMounted = true;
    if (settings.wakeWordEnabled) {
      // Delay wake word start to avoid detecting app startup audio/greeting
      setTimeout(() => {
        if (isMounted) {
          wakeWordEngine.start().then((st) => {
            if (isMounted) {
              setWakeStatus(st);
            }
          });
        }
      }, 3000);
    } else {
      wakeWordEngine.stop();
      setWakeStatus('DISABLED');
    }

    return () => {
      isMounted = false;
      wakeWordEngine.stop();
    };
  }, [settings.wakeWordEnabled]);

  // Simulate Wake Word manually (for testing or single tap)
  const handleSimulateWakeWord = useCallback((command: string = '') => {
    audioEngine.unlock();
    wakeWordEngine.simulateWakeWord(command);
  }, []);

  // Open Android Settings Intent helper
  const handleOpenAndroidSettings = useCallback((actionKey: string) => {
    try {
      if (actionKey === 'APPLICATION_DETAILS_SETTINGS' || actionKey.includes('package')) {
        window.location.href = 'intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;package=com.vasu.assistant;end';
        return;
      }
      window.location.href = `intent:#Intent;action=android.settings.${actionKey};end`;
    } catch (err) {
      console.warn("Intent redirect error:", err);
    }
  }, []);

  // Individual Permission toggle (ON / OFF switch)
  const handleTogglePermission = useCallback(async (id: string, enable: boolean) => {
    audioEngine.unlock();

    if (enable) {
      if (id === 'microphone') {
        await handleActivateVoice();
        return;
      } else if (id === 'camera') {
        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
          try {
            const stream = await navigator.mediaDevices.getUserMedia({ video: true });
            stream.getTracks().forEach((t) => t.stop());
          } catch (_) {}
        }
      } else if (id === 'notifications') {
        if ('Notification' in window) {
          try {
            await Notification.requestPermission();
          } catch (_) {}
        }
        handleOpenAndroidSettings('ACTION_NOTIFICATION_LISTENER_SETTINGS');
      } else if (id === 'accessibility') {
        handleOpenAndroidSettings('ACCESSIBILITY_SETTINGS');
      } else if (id === 'files') {
        handleOpenAndroidSettings('MANAGE_ALL_FILES_ACCESS_PERMISSION');
      } else if (id === 'alarms') {
        handleOpenAndroidSettings('REQUEST_SCHEDULE_EXACT_ALARM');
      } else if (id === 'foreground_service') {
        handleOpenAndroidSettings('REQUEST_IGNORE_BATTERY_OPTIMIZATIONS');
      } else if (id === 'bluetooth') {
        handleOpenAndroidSettings('BLUETOOTH_SETTINGS');
      } else if (id === 'phone' || id === 'contacts') {
        handleOpenAndroidSettings('APPLICATION_DETAILS_SETTINGS');
      }

      setPermissions((prev) =>
        prev.map((p) => (p.id === id ? { ...p, status: 'GRANTED' } : p))
      );
      audioEngine.playSuccessChime();
    } else {
      if (id === 'microphone') {
        wakeWordEngine.stop();
        setWakeStatus('DISABLED');
      }

      setPermissions((prev) =>
        prev.map((p) => (p.id === id ? { ...p, status: 'DENIED' } : p))
      );
    }
  }, [handleActivateVoice, handleOpenAndroidSettings]);

  // Individual Permission request trigger (legacy adapter)
  const handleRequestPermission = async (id: string) => {
    await handleTogglePermission(id, true);
  };

  // Audio replay function for HomeView or ChatView
  const handleReplayAudio = useCallback((text: string) => {
    audioEngine.unlock();
    if (assistantStateRef.current === 'SPEAKING') {
      ttsManager.stop();
      setAssistantState('IDLE');
      if (settings.wakeWordEnabled && wakeStatusRef.current !== 'DISABLED') {
        wakeWordEngine.resume();
      }
      return;
    }
    ttsManager.forceSpeak(text, {
      autoSpeak: true,
      speed: settings.ttsSpeed,
      pitch: settings.ttsPitch,
      volume: settings.ttsVolume,
      apiKey: settings.geminiApiKey || '',
      source: 'replay',
    }).then(() => {
      setAssistantState('IDLE');
      if (settings.wakeWordEnabled && wakeStatusRef.current !== 'DISABLED') {
        wakeWordEngine.resume();
      }
    });
  }, [settings.ttsSpeed, settings.ttsPitch, settings.ttsVolume, settings.geminiApiKey, settings.wakeWordEnabled]);

  const latestVasuMsg = [...messages].reverse().find((m) => m.sender === 'vasu');
  const latestUserMsg = [...messages].reverse().find((m) => m.sender === 'user');

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-cyan-500/30 selection:text-white relative overflow-x-hidden">
      {/* Torch Active Glow Flash */}
      {isTorchActive && (
        <div className="fixed inset-0 pointer-events-none bg-cyan-100/10 z-40 border-8 border-cyan-400/40 animate-pulse" />
      )}

      {/* Top Header — hidden on HOME tab (HomeView has its own header) */}
      {activeTab !== 'HOME' && (
      <header className="sticky top-0 z-30 bg-slate-950/80 backdrop-blur-md border-b border-slate-900 px-4 py-3 flex items-center justify-between">
        {/* Brand & State */}
        <div
          onClick={() => setActiveTab('HOME')}
          className="flex items-center gap-2 cursor-pointer select-none"
        >
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-600 to-teal-400 flex items-center justify-center text-slate-950 font-bold font-mono text-sm shadow-md shadow-cyan-900/40">
            V
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-mono text-base font-bold tracking-tight text-white flex items-center gap-1.5">
                <span>VASU</span>
                <span className="text-cyan-400 font-sans font-normal text-xs">Assistant</span>
              </h1>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-cyan-950 text-cyan-300 border border-cyan-800">
                v1.0
              </span>
            </div>
            <span className="text-[10px] text-slate-400 font-mono block -mt-0.5">
              Android AI Voice System
            </span>
          </div>
        </div>

        {/* Status Indicators & Shortcuts */}
        <div className="flex items-center gap-2">
          {/* Torch Indicator */}
          {isTorchActive && (
            <button
              onClick={() => handleExecuteTool('turn_off_torch')}
              className="px-2 py-1 rounded-full bg-yellow-500/20 text-yellow-300 border border-yellow-500/40 text-[11px] font-mono flex items-center gap-1 animate-pulse"
              title="Torch is ON - click to turn off"
            >
              <Flashlight className="w-3.5 h-3.5 fill-current" />
              <span className="hidden sm:inline">Torch ON</span>
            </button>
          )}

          {/* Smart Mode Pill */}
          <div className="px-2 py-1 rounded-full bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-300">
            {settings.smartMode}
          </div>

          {/* Online/Offline Badge */}
          <div
            className={`px-2 py-1 rounded-full text-[11px] font-mono flex items-center gap-1 border ${
              isOnline
                ? 'bg-emerald-950/60 text-emerald-300 border-emerald-800'
                : 'bg-amber-950/60 text-amber-300 border-amber-800'
            }`}
          >
            {isOnline ? <Wifi className="w-3 h-3 text-emerald-400" /> : <WifiOff className="w-3 h-3 text-amber-400" />}
            <span className="hidden sm:inline">{isOnline ? 'Online' : 'Offline'}</span>
          </div>

          {/* Emergency Stop Button */}
          <button
            id="header-btn-emergency"
            onClick={handleEmergencyStop}
            title="Guardian Emergency Stop"
            className="p-1.5 rounded-lg bg-rose-950/60 hover:bg-rose-900 border border-rose-800/80 text-rose-300 transition-colors cursor-pointer"
          >
            <Octagon className="w-4 h-4 text-rose-400" />
          </button>

          {/* Voice Mode button */}
          <button
            id="header-btn-voice"
            onClick={() => setActiveTab(activeTab === 'VOICE' ? 'HOME' : 'VOICE')}
            title="Live Voice Mode (Kore)"
            className={`p-1.5 rounded-lg border transition-colors cursor-pointer ${
              activeTab === 'VOICE'
                ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50'
                : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200'
            }`}
          >
            <Mic className="w-4 h-4" />
          </button>

          {/* Settings button */}
          <button
            id="header-btn-settings"
            onClick={() => setActiveTab(activeTab === 'SETTINGS' ? 'HOME' : 'SETTINGS')}
            className={`p-1.5 rounded-lg border transition-colors cursor-pointer ${
              activeTab === 'SETTINGS'
                ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50'
                : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200'
            }`}
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </header>
      )}

      {/* Main Dynamic View Area */}
      <main className="flex-1 flex flex-col items-center justify-between pb-2 w-full">
        {activeTab === 'HOME' && (
          <HomeView
            assistantState={assistantState}
            audioLevel={audioLevel}
            activeToolName={activeToolName}
            messages={messages}
            liveVoiceTranscript={liveVoiceTranscript}
            isContinuousListening={isContinuousListening}
            isTorchActive={isTorchActive}
            onToggleListen={handleToggleListen}
            onSendMessage={(msg) => processUserCommand(msg, false)}
            onReplayAudio={handleReplayAudio}
            onNavigateToChat={() => setActiveTab('CHAT')}
            onToggleTorch={() => handleExecuteTool(isTorchActive ? 'turn_off_torch' : 'turn_on_torch', {}, true)}
            onSelectTab={setActiveTab}
          />
        )}

        {activeTab === 'VOICE' && (
          <VoiceView
            apiKey={settings.geminiApiKey}
            isWakeWordActive={settings.wakeWordEnabled}
            onToggleWakeWord={() => setSettings((s) => ({ ...s, wakeWordEnabled: !s.wakeWordEnabled }))}
            onBack={() => setActiveTab('HOME')}
            onOpenSettings={() => setActiveTab('SETTINGS')}
          />
        )}

        {activeTab === 'CHAT' && (
          <ChatView
            messages={messages}
            onSendMessage={(msg) => processUserCommand(msg, false)}
            onClearChat={() => { setMessages([]); localStorage.removeItem('vasu_messages'); }}
            isLoading={assistantState === 'THINKING' || assistantState === 'EXECUTING'}
            liveVoiceTranscript={liveVoiceTranscript}
            isListening={assistantState === 'LISTENING'}
            audioLevel={audioLevel}
            assistantState={assistantState}
            onToggleMic={handleToggleListen}
            isContinuousListening={isContinuousListening}
          />
        )}

        {(activeTab === 'TOOLS' ||
          activeTab === 'MEMORY' ||
          activeTab === 'MISSIONS' ||
          activeTab === 'AUTO' ||
          activeTab === 'GUARDIAN') && (
          <div className="w-full max-w-2xl mx-auto px-3 pt-2 pb-1 flex items-center justify-between">
            <button
              onClick={() => setActiveTab('SETTINGS')}
              className="text-xs font-mono text-cyan-400 hover:text-cyan-300 flex items-center gap-1 cursor-pointer bg-slate-900/80 hover:bg-slate-850 px-2.5 py-1 rounded-lg border border-slate-800 transition-colors"
            >
              <span>&larr; Back to Settings</span>
            </button>
            <span className="text-[10px] font-mono text-slate-500 uppercase">
              Setting &bull; {activeTab}
            </span>
          </div>
        )}

        {activeTab === 'TOOLS' && (
          <ToolsView onExecuteTool={(toolId) => handleExecuteTool(toolId, {}, true)} />
        )}

        {activeTab === 'MEMORY' && (
          <MemoryView
            memories={memories}
            onAddMemory={(cat, fact) => {
              setMemories((prev) => [
                { id: `mem_${Date.now()}`, category: cat, fact, createdAt: Date.now() },
                ...prev,
              ]);
            }}
            onDeleteMemory={(id) => setMemories((prev) => prev.filter((m) => m.id !== id))}
            onClearMemory={() => setMemories([])}
          />
        )}

        {activeTab === 'MISSIONS' && (
          <MissionsView
            missions={missions}
            onToggleMission={(id) =>
              setMissions((prev) =>
                prev.map((m) => (m.id === id ? { ...m, isEnabled: !m.isEnabled } : m))
              )
            }
            onRunMission={async (id) => {
              const mission = missions.find((m) => m.id === id);
              if (mission) {
                setAssistantState('EXECUTING');
                setMessages((prev) => [
                  ...prev,
                  {
                    id: `mis_${Date.now()}`,
                    sender: 'system',
                    text: `⚡ Background Routine Triggered: "${mission.title}" (${mission.action})`,
                    timestamp: Date.now(),
                    badge: 'MISSION AUTO',
                  },
                ]);
                await handleExecuteTool('run_mission', { missionId: id }, true);
                setMissions((prev) =>
                  prev.map((m) => (m.id === id ? { ...m, lastRun: Date.now() } : m))
                );
                setAssistantState('IDLE');
              }
            }}
            onCreateMission={(newMission) =>
              setMissions((prev) => [...prev, { ...newMission, id: `mis_${Date.now()}` }])
            }
          />
        )}

        {activeTab === 'AUTO' && (
          <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3 py-2 space-y-3">
            <div className="border-b border-slate-800 pb-2">
              <h2 className="font-mono text-sm font-semibold text-slate-100 flex items-center gap-2">
                <Bot className="w-5 h-5 text-cyan-400" />
                <span>Accessibility Screen Automation</span>
              </h2>
              <p className="text-xs text-slate-400">
                Inspect UI nodes, click buttons, scroll views, and type text without fixed coordinates.
              </p>
            </div>

            <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 space-y-3">
              <span className="text-xs font-mono font-semibold text-cyan-300 block">
                Live Screen Node Inspector
              </span>
              <p className="text-xs text-slate-300">
                Active Service: <span className="font-mono text-emerald-400">VasuAccessibilityService</span>
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => handleExecuteTool('read_screen')}
                  className="bg-cyan-500 text-slate-950 px-3 py-2 rounded-lg text-xs font-bold font-mono hover:bg-cyan-400 cursor-pointer"
                >
                  Read Visible Screen
                </button>
                <button
                  onClick={() => handleExecuteTool('press_home')}
                  className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-3 py-2 rounded-lg text-xs font-mono cursor-pointer"
                >
                  Home Navigation
                </button>
                <button
                  onClick={() => handleExecuteTool('press_back')}
                  className="bg-slate-800 hover:bg-slate-700 text-slate-200 px-3 py-2 rounded-lg text-xs font-mono cursor-pointer"
                >
                  Back Action
                </button>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'GUARDIAN' && (
          <GuardianView
            isGuardianActive={settings.guardianActive}
            onToggleGuardian={() =>
              setSettings((s) => ({ ...s, guardianActive: !s.guardianActive }))
            }
            requireConfirmationForHighRisk={settings.requireConfirmationForHighRisk}
            onToggleConfirmation={() =>
              setSettings((s) => ({
                ...s,
                requireConfirmationForHighRisk: !s.requireConfirmationForHighRisk,
              }))
            }
            onEmergencyStop={handleEmergencyStop}
            executionLogs={executionLogs}
          />
        )}

        {activeTab === 'PERMISSIONS' && (
          <PermissionsView
            permissions={permissions}
            onRequestPermission={handleRequestPermission}
            onTogglePermission={handleTogglePermission}
            onOpenSettings={handleOpenAndroidSettings}
            onEnableAllPermissions={handleEnableAllPermissions}
            onReadNotifications={handleReadNotifications}
          />
        )}

        {activeTab === 'SETTINGS' && (
          <SettingsView
            settings={settings}
            onUpdateSettings={(newS) => setSettings((s) => ({ ...s, ...newS }))}
            onClearMemory={() => setMemories([])}
            onClearChat={() => { setMessages([]); localStorage.removeItem('vasu_messages'); }}
            onSelectTab={setActiveTab}
          />
        )}
      </main>

      {/* 5-Item Navigation Bar (Home, Permission, Small Orb, Chat, Setting) */}
      <VasuBottomBar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        assistantState={assistantState}
        audioLevel={audioLevel}
        onToggleListen={handleToggleListen}
      />

      {/* Android Project Explorer Modal */}
      <AndroidProjectModal
        isOpen={showAndroidModal || activeTab === 'ANDROID_PROJECT'}
        onClose={() => {
          setShowAndroidModal(false);
          if (activeTab === 'ANDROID_PROJECT') setActiveTab('HOME');
        }}
      />
    </div>
  );
}
