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

export type AIProviderType = 'gemini' | 'openrouter' | 'groq' | 'deepseek' | 'xai' | 'custom';

export type SearchProviderType = 'tavily' | 'brave' | 'gemini_grounding';

export type ErrorClass =
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'INVALID_API_KEY'
  | 'QUOTA_EXCEEDED'
  | 'RATE_LIMITED'
  | 'MODEL_NOT_FOUND'
  | 'AUTH_ERROR'
  | 'SERVER_ERROR'
  | 'UNSUPPORTED_FEATURE'
  | 'UNKNOWN_ERROR';

export type ProviderTier = 'FREE' | 'PROMOTIONAL' | 'PAID' | 'UNKNOWN';

export type ProviderStatus = 'ONLINE' | 'OFFLINE' | 'QUOTA' | 'RATE_LIMITED' | 'AUTH_ERROR' | 'UNCONFIGURED';

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

export interface ProviderConfig {
  type: AIProviderType;
  name: string;
  apiKey: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  priority: number;
  tier: ProviderTier;
  lastSuccessfulRequest?: number;
  lastError?: string;
  lastErrorTime?: number;
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  avgLatencyMs: number;
}

export interface SearchProviderConfig {
  type: SearchProviderType;
  name: string;
  apiKey: string;
  enabled: boolean;
  priority: number;
  tier: ProviderTier;
  lastSuccessfulRequest?: number;
  lastError?: string;
}

export interface AIProviderResponse {
  replyText: string;
  source: AIProviderType | 'local_jarvis' | 'offline';
  modelUsed?: string;
  latencyMs: number;
  tokensUsed?: number;
  searchResults?: SearchResult[];
}

export interface AIProviderError {
  errorClass: ErrorClass;
  message: string;
  provider: AIProviderType;
  retryable: boolean;
  retryAfterMs?: number;
}

export interface ChatParams {
  message: string;
  history?: Array<{ role: 'user' | 'model'; parts: Array<{ text: string }> }>;
  smartMode?: string;
  language?: string;
  memoryContext?: Array<{ category: string; fact: string }>;
  apiKey?: string;
  searchResults?: SearchResult[];
}

export interface SearchResult {
  title: string;
  url: string;
  snippet: string;
  source: SearchProviderType;
  score?: number;
}

export interface SearchParams {
  query: string;
  maxResults?: number;
  freshness?: 'day' | 'week' | 'month' | 'year';
  apiKey?: string;
}

export interface SearchResponse {
  results: SearchResult[];
  source: SearchProviderType;
  latencyMs: number;
}

export interface ProviderHealth {
  provider: AIProviderType | SearchProviderType;
  status: ProviderStatus;
  latencyMs?: number;
  model?: string;
  lastError?: string;
  lastChecked: number;
}

export interface CostProtectionConfig {
  maxRequestsPerDay: number;
  maxTokensPerRequest: number;
  maxSearchRequestsPerDay: number;
  maxProviderRetries: number;
  allowPaidProvider: boolean;
  allowFreeOnlyMode: boolean;
}

export interface VasuSettings {
  geminiApiKey: string;
  geminiModel: string;
  openrouterApiKey: string;
  openrouterModel: string;
  groqApiKey: string;
  groqModel: string;
  deepseekApiKey: string;
  deepseekModel: string;
  xaiApiKey: string;
  xaiModel: string;
  customOpenaiBaseUrl: string;
  customOpenaiApiKey: string;
  customOpenaiModel: string;
  tavilyApiKey: string;
  braveSearchApiKey: string;
  providerPriority: AIProviderType[];
  searchProviderPriority: SearchProviderType[];
  freeOnlyMode: boolean;
  costProtection: CostProtectionConfig;
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
  autoSpeak: boolean;
  torchActive: boolean;
  volumeLevel: number;
  ttsVolume: number;
}

export interface ApiTestResult {
  provider: string;
  status: ProviderStatus;
  latencyMs?: number;
  model?: string;
  lastError?: string;
  timestamp: number;
}
