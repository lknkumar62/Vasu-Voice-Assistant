export type AssistantState =
  | 'IDLE'
  | 'LISTENING'
  | 'THINKING'
  | 'EXECUTING'
  | 'SPEAKING'
  | 'OFFLINE'
  | 'ERROR';

export type VoiceMode = 'PUSH_TO_TALK' | 'WAKE_WORD' | 'CONTINUOUS';

export type SmartMode = 'NORMAL' | 'DRIVING' | 'SLEEP' | 'WORK' | 'GAMING' | 'CUSTOM';

export type ToolRisk = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface ToolParameter {
  name: string;
  type: string;
  description: string;
  required: boolean;
}

export interface ToolItem {
  id: string;
  name: string;
  title: string;
  description: string;
  category: 'DEVICE' | 'SYSTEM' | 'FILES' | 'CAMERA' | 'COMMUNICATION' | 'MEDIA' | 'SCREEN' | 'NAVIGATION' | 'ALARMS' | 'MISSIONS';
  risk: ToolRisk;
  requiredPermission: string;
  isAvailable: boolean;
  parameters: ToolParameter[];
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'vasu' | 'system';
  text: string;
  timestamp: number;
  badge?: string;
  isVoice?: boolean;
  toolCall?: {
    tool: string;
    status: 'pending' | 'executing' | 'success' | 'error';
    result?: string;
  };
}

export interface MemoryItem {
  id: string;
  category: 'USER_PREFERENCE' | 'IMPORTANT_FACT' | 'TASK_CONTEXT' | 'DEVICE_PREFERENCE' | 'CONVERSATION_CONTEXT';
  fact: string;
  createdAt: number;
}

export interface MissionItem {
  id: string;
  title: string;
  triggerType: 'TIME' | 'BATTERY' | 'EVENT' | 'RECURRING';
  triggerValue: string;
  action: string;
  isEnabled: boolean;
  lastRun?: number;
}

export interface PermissionItem {
  id: string;
  name: string;
  description: string;
  status: 'GRANTED' | 'DENIED' | 'REQUIRED' | 'UNSUPPORTED';
  category: 'RUNTIME' | 'SPECIAL' | 'HARDWARE';
}

export interface VasuSettings {
  geminiApiKey: string;
  geminiModel: string;
  wakeWordEnabled: boolean;
  wakePhrase: 'Hello VASU' | 'Hey VASU' | 'Custom';
  wakeSensitivity: 'LOW' | 'MEDIUM' | 'HIGH';
  backgroundListening: boolean;
  ttsVoice: string;
  ttsSpeed: number;
  ttsPitch: number;
  language: 'Hindi' | 'Hinglish' | 'English';
  smartMode: SmartMode;
  guardianActive: boolean;
  requireConfirmationForHighRisk: boolean;
  followUpMode: boolean;
  torchActive: boolean;
  volumeLevel: number;
}
