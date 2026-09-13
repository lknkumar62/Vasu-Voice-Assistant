import { ToolItem, ToolRisk } from '../types';

export interface ToolExecutionResult {
  success: boolean;
  result: string;
  displayData?: any;
  risk: ToolRisk;
}

// In-memory simulated storage for files, notifications, and media
const mockFileSystem = [
  { name: 'DCIM/Camera/IMG_20260901_0921.jpg', size: '3.4 MB', type: 'image' },
  { name: 'Documents/Resume_Rahul_Kumar.pdf', size: '420 KB', type: 'document' },
  { name: 'Downloads/vasu-assistant-guide.pdf', size: '1.2 MB', type: 'document' },
  { name: 'Music/Arijit_Singh_Playlist.mp3', size: '8.1 MB', type: 'audio' },
  { name: 'WhatsApp/Media/Voice_Note_01.opus', size: '150 KB', type: 'audio' },
];

let isTorchOn = false;
let currentVolume = 70;
let isMediaPlaying = false;
let currentTrack = "Arijit Singh - Kesariya (Remix)";

export const REGISTERED_TOOLS: ToolItem[] = [
  // SYSTEM & DEVICE
  {
    id: 'turn_on_torch',
    name: 'turn_on_torch',
    title: 'Turn On Torch',
    description: 'Turn on the device flashlight',
    category: 'SYSTEM',
    risk: 'LOW',
    requiredPermission: 'Camera / Flashlight',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'turn_off_torch',
    name: 'turn_off_torch',
    title: 'Turn Off Torch',
    description: 'Turn off the device flashlight',
    category: 'SYSTEM',
    risk: 'LOW',
    requiredPermission: 'Camera / Flashlight',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'volume_up',
    name: 'volume_up',
    title: 'Volume Up',
    description: 'Increase device media volume by 15%',
    category: 'SYSTEM',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'volume_down',
    name: 'volume_down',
    title: 'Volume Down',
    description: 'Decrease device media volume by 15%',
    category: 'SYSTEM',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'set_volume',
    name: 'set_volume',
    title: 'Set Volume',
    description: 'Set device volume to a specific percentage (0 - 100)',
    category: 'SYSTEM',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [{ name: 'level', type: 'number', description: 'Volume level 0-100', required: true }],
  },
  {
    id: 'battery_info',
    name: 'battery_info',
    title: 'Battery Info',
    description: 'Check battery level and charging status',
    category: 'DEVICE',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'device_info',
    name: 'device_info',
    title: 'Device Info',
    description: 'Get hardware, OS version, RAM, and display specs',
    category: 'DEVICE',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'storage_info',
    name: 'storage_info',
    title: 'Storage Info',
    description: 'Get internal storage free space and breakdown',
    category: 'FILES',
    risk: 'LOW',
    requiredPermission: 'Storage Access',
    isAvailable: true,
    parameters: [],
  },

  // COMMUNICATION & WHATSAPP
  {
    id: 'open_whatsapp',
    name: 'open_whatsapp',
    title: 'Open WhatsApp',
    description: 'Open WhatsApp with an optional recipient or message draft',
    category: 'COMMUNICATION',
    risk: 'MEDIUM',
    requiredPermission: 'App Launch',
    isAvailable: true,
    parameters: [
      { name: 'recipient', type: 'string', description: 'Contact name or phone number', required: false },
      { name: 'message', type: 'string', description: 'Message draft', required: false },
    ],
  },
  {
    id: 'make_call',
    name: 'make_call',
    title: 'Make Phone Call',
    description: 'Initiate a phone call via Android Dialer intent',
    category: 'COMMUNICATION',
    risk: 'MEDIUM',
    requiredPermission: 'Phone / Call Intent',
    isAvailable: true,
    parameters: [{ name: 'phoneNumber', type: 'string', description: 'Phone number to call', required: true }],
  },
  {
    id: 'send_message',
    name: 'send_message',
    title: 'Send SMS',
    description: 'Draft and prepare SMS to a contact',
    category: 'COMMUNICATION',
    risk: 'MEDIUM',
    requiredPermission: 'SMS Intent',
    isAvailable: true,
    parameters: [
      { name: 'phoneNumber', type: 'string', description: 'Phone number', required: true },
      { name: 'message', type: 'string', description: 'Text message body', required: true },
    ],
  },
  {
    id: 'lookup_contact',
    name: 'lookup_contact',
    title: 'Lookup Contact',
    description: 'Find contact phone number or email by name',
    category: 'COMMUNICATION',
    risk: 'LOW',
    requiredPermission: 'Contacts',
    isAvailable: true,
    parameters: [{ name: 'name', type: 'string', description: 'Contact name', required: true }],
  },

  // MEDIA & WEB
  {
    id: 'media_play_pause',
    name: 'media_play_pause',
    title: 'Media Play / Pause',
    description: 'Toggle playback of currently playing media',
    category: 'MEDIA',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'media_next',
    name: 'media_next',
    title: 'Next Track',
    description: 'Skip to next music track',
    category: 'MEDIA',
    risk: 'LOW',
    requiredPermission: 'None',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'search_web',
    name: 'search_web',
    title: 'Search Web',
    description: 'Perform web search for information or queries',
    category: 'DEVICE',
    risk: 'LOW',
    requiredPermission: 'Internet',
    isAvailable: true,
    parameters: [{ name: 'query', type: 'string', description: 'Search keywords', required: true }],
  },

  // ACCESSIBILITY & SCREEN AUTOMATION
  {
    id: 'read_screen',
    name: 'read_screen',
    title: 'Read Screen Content',
    description: 'Accessibility Service inspects visible nodes and text on display',
    category: 'SCREEN',
    risk: 'LOW',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'click',
    name: 'click',
    title: 'Click UI Element',
    description: 'Simulate user touch click on button or element matching label or text',
    category: 'SCREEN',
    risk: 'HIGH',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [{ name: 'targetText', type: 'string', description: 'Text or description of element', required: true }],
  },
  {
    id: 'type_text',
    name: 'type_text',
    title: 'Type Text',
    description: 'Input text into currently active or targeted text field',
    category: 'SCREEN',
    risk: 'MEDIUM',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [{ name: 'text', type: 'string', description: 'Text to input', required: true }],
  },
  {
    id: 'press_back',
    name: 'press_back',
    title: 'Press Back Button',
    description: 'Navigate back using system accessibility action',
    category: 'NAVIGATION',
    risk: 'LOW',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'press_home',
    name: 'press_home',
    title: 'Press Home Button',
    description: 'Navigate to home launcher screen',
    category: 'NAVIGATION',
    risk: 'LOW',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'scroll_down',
    name: 'scroll_down',
    title: 'Scroll Down',
    description: 'Perform downward scroll gesture on active view',
    category: 'SCREEN',
    risk: 'LOW',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'scroll_up',
    name: 'scroll_up',
    title: 'Scroll Up',
    description: 'Perform upward scroll gesture on active view',
    category: 'SCREEN',
    risk: 'LOW',
    requiredPermission: 'Accessibility Service',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'open_app',
    name: 'open_app',
    title: 'Open Application',
    description: 'Launch an installed application by name or package',
    category: 'NAVIGATION',
    risk: 'MEDIUM',
    requiredPermission: 'Query All Packages',
    isAvailable: true,
    parameters: [{ name: 'appName', type: 'string', description: 'Application name e.g. YouTube, Camera, WhatsApp', required: true }],
  },

  // CAMERA & VISION
  {
    id: 'take_photo',
    name: 'take_photo',
    title: 'Take Photo',
    description: 'Open camera viewfinder and capture image',
    category: 'CAMERA',
    risk: 'LOW',
    requiredPermission: 'Camera',
    isAvailable: true,
    parameters: [],
  },
  {
    id: 'ocr_extract',
    name: 'ocr_extract',
    title: 'Extract Text (OCR)',
    description: 'Recognize text from camera view or image on screen',
    category: 'CAMERA',
    risk: 'LOW',
    requiredPermission: 'Camera',
    isAvailable: true,
    parameters: [],
  },

  // FILES
  {
    id: 'browse_files',
    name: 'browse_files',
    title: 'Browse Files',
    description: 'List files in device internal storage',
    category: 'FILES',
    risk: 'LOW',
    requiredPermission: 'Storage Access',
    isAvailable: true,
    parameters: [{ name: 'folder', type: 'string', description: 'Folder path e.g. Downloads, DCIM', required: false }],
  },
  {
    id: 'search_files',
    name: 'search_files',
    title: 'Search Files',
    description: 'Search files matching query in local storage',
    category: 'FILES',
    risk: 'LOW',
    requiredPermission: 'Storage Access',
    isAvailable: true,
    parameters: [{ name: 'query', type: 'string', description: 'File name keyword', required: true }],
  },
  {
    id: 'delete_file',
    name: 'delete_file',
    title: 'Delete File',
    description: 'Permanently remove a file from device storage',
    category: 'FILES',
    risk: 'HIGH',
    requiredPermission: 'Manage External Storage',
    isAvailable: true,
    parameters: [{ name: 'filePath', type: 'string', description: 'Path to the file', required: true }],
  },

  // ALARMS & MISSIONS
  {
    id: 'create_alarm',
    name: 'create_alarm',
    title: 'Create Alarm',
    description: 'Set device alarm or reminder for a specific time',
    category: 'ALARMS',
    risk: 'LOW',
    requiredPermission: 'Set Alarm / Schedule Exact Alarm',
    isAvailable: true,
    parameters: [
      { name: 'time', type: 'string', description: 'Time e.g. 7:00 AM', required: true },
      { name: 'label', type: 'string', description: 'Alarm label', required: false },
    ],
  },
  {
    id: 'run_mission',
    name: 'run_mission',
    title: 'Run Mission',
    description: 'Trigger an automated background routine or mission',
    category: 'MISSIONS',
    risk: 'HIGH',
    requiredPermission: 'Foreground Service / WorkManager',
    isAvailable: true,
    parameters: [{ name: 'missionId', type: 'string', description: 'ID of mission to run', required: true }],
  },

  // NOTIFICATIONS
  {
    id: 'read_notifications',
    name: 'read_notifications',
    title: 'Read Notifications',
    description: 'Read recent notifications from WhatsApp, Messages, Gmail',
    category: 'DEVICE',
    risk: 'LOW',
    requiredPermission: 'Notification Listener Service',
    isAvailable: true,
    parameters: [],
  },
];

export class ToolExecutor {
  public static async execute(
    toolId: string,
    args: Record<string, any> = {},
    context?: { setTorchState?: (on: boolean) => void }
  ): Promise<ToolExecutionResult> {
    const tool = REGISTERED_TOOLS.find((t) => t.id === toolId);
    if (!tool) {
      return {
        success: false,
        result: `Unknown tool: ${toolId}`,
        risk: 'LOW',
      };
    }

    try {
      switch (toolId) {
        case 'turn_on_torch': {
          isTorchOn = true;
          context?.setTorchState?.(true);
          // Try to access camera torch track if permitted
          try {
            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
              const stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: 'environment' },
              });
              const track = stream.getVideoTracks()[0];
              const capabilities = (track as any).getCapabilities?.() || {};
              if (capabilities.torch) {
                await (track as any).applyConstraints({ advanced: [{ torch: true }] });
              }
            }
          } catch (_) {}
          return {
            success: true,
            result: 'Flashlight (Torch) has been turned on successfully.',
            displayData: { torch: true },
            risk: tool.risk,
          };
        }

        case 'turn_off_torch': {
          isTorchOn = false;
          context?.setTorchState?.(false);
          return {
            success: true,
            result: 'Flashlight (Torch) has been turned off.',
            displayData: { torch: false },
            risk: tool.risk,
          };
        }

        case 'volume_up': {
          currentVolume = Math.min(100, currentVolume + 15);
          return {
            success: true,
            result: `Volume has been increased. Current volume is now ${currentVolume}%.`,
            displayData: { volume: currentVolume },
            risk: tool.risk,
          };
        }

        case 'volume_down': {
          currentVolume = Math.max(0, currentVolume - 15);
          return {
            success: true,
            result: `Volume has been decreased. Current volume is now ${currentVolume}%.`,
            displayData: { volume: currentVolume },
            risk: tool.risk,
          };
        }

        case 'set_volume': {
          const level = Number(args.level ?? 50);
          currentVolume = Math.max(0, Math.min(100, level));
          return {
            success: true,
            result: `Volume has been set to ${currentVolume}%.`,
            displayData: { volume: currentVolume },
            risk: tool.risk,
          };
        }

        case 'battery_info': {
          let level = 84;
          let charging = false;
          if ('getBattery' in navigator) {
            try {
              const battery: any = await (navigator as any).getBattery();
              level = Math.round(battery.level * 100);
              charging = battery.charging;
            } catch (_) {}
          }
          return {
            success: true,
            result: `Battery is currently at ${level}% ${charging ? '(Charging)' : '(Not charging)'}.`,
            displayData: { level, charging },
            risk: tool.risk,
          };
        }

        case 'device_info': {
          const platform = navigator.platform || 'Android 14 (API 34)';
          const cores = navigator.hardwareConcurrency || 8;
          return {
            success: true,
            result: `Device: Android 14 • ${cores} Core CPU • VASU AI Assistant v1.0.0 Online`,
            displayData: { platform, cores, memory: '8 GB RAM', screen: `${window.innerWidth}x${window.innerHeight}` },
            risk: tool.risk,
          };
        }

        case 'storage_info': {
          return {
            success: true,
            result: 'Internal Storage: 78.4 GB used of 128 GB (49.6 GB Free, 38.7% available)',
            displayData: { total: '128 GB', used: '78.4 GB', free: '49.6 GB' },
            risk: tool.risk,
          };
        }

        case 'open_whatsapp': {
          const recipient = args.recipient || 'Rahul';
          const msg = args.message ? encodeURIComponent(args.message) : '';
          const url = `https://api.whatsapp.com/send?text=${msg}`;
          window.open(url, '_blank');
          return {
            success: true,
            result: `WhatsApp has been opened ${recipient ? `(for ${recipient})` : ''}.`,
            displayData: { recipient, message: args.message },
            risk: tool.risk,
          };
        }

        case 'make_call': {
          const phone = args.phoneNumber || '+91 98765 43210';
          window.open(`tel:${phone}`, '_self');
          return {
            success: true,
            result: `Calling ${phone} now...`,
            displayData: { phone },
            risk: tool.risk,
          };
        }

        case 'send_message': {
          const phone = args.phoneNumber || '+91 98765 43210';
          const body = encodeURIComponent(args.message || 'Hello from VASU');
          window.open(`sms:${phone}?body=${body}`, '_self');
          return {
            success: true,
            result: `SMS ready to send to ${phone}: "${args.message || ''}"`,
            displayData: { phone, message: args.message },
            risk: tool.risk,
          };
        }

        case 'media_play_pause': {
          isMediaPlaying = !isMediaPlaying;
          return {
            success: true,
            result: isMediaPlaying
              ? `Now playing: ${currentTrack}`
              : 'Media has been paused.',
            displayData: { isPlaying: isMediaPlaying, track: currentTrack },
            risk: tool.risk,
          };
        }

        case 'media_next': {
          currentTrack = "A.R. Rahman - Kun Faya Kun (Live)";
          isMediaPlaying = true;
          return {
            success: true,
            result: `Now playing next track: ${currentTrack}`,
            displayData: { isPlaying: true, track: currentTrack },
            risk: tool.risk,
          };
        }

        case 'read_screen': {
          // Accessibility service reads visible UI nodes from DOM
          const headings = Array.from(document.querySelectorAll('h1, h2, h3, button, input'))
            .slice(0, 10)
            .map((el) => {
              const text = el.textContent?.trim() || (el as HTMLInputElement).placeholder || '';
              const tag = el.tagName.toLowerCase();
              return `[${tag}] ${text}`;
            })
            .filter((s) => s.length > 3);

          const summary = headings.length > 0 ? headings.join(' • ') : 'VASU Assistant Home Screen visible with microphone controls.';
          return {
            success: true,
            result: `Here is what is on the screen: ${summary.slice(0, 200)}...`,
            displayData: { nodes: headings },
            risk: tool.risk,
          };
        }

        case 'open_app': {
          const appName = args.appName || 'YouTube';
          let appUrl = 'https://google.com';
          if (appName.toLowerCase().includes('youtube')) appUrl = 'https://youtube.com';
          else if (appName.toLowerCase().includes('camera')) appUrl = '#camera';
          else if (appName.toLowerCase().includes('instagram')) appUrl = 'https://instagram.com';
          else if (appName.toLowerCase().includes('google')) appUrl = 'https://google.com';
          
          if (appUrl.startsWith('http')) {
            window.open(appUrl, '_blank');
          }
          return {
            success: true,
            result: `${appName} has been opened successfully.`,
            displayData: { app: appName },
            risk: tool.risk,
          };
        }

        case 'create_alarm': {
          const time = args.time || '07:00 AM';
          const label = args.label || 'VASU Alarm';
          return {
            success: true,
            result: `Alarm has been set for tomorrow at ${time} (${label}).`,
            displayData: { time, label },
            risk: tool.risk,
          };
        }

        case 'browse_files': {
          const folder = args.folder || 'Downloads';
          const files = mockFileSystem.filter((f) => f.name.includes(folder));
          return {
            success: true,
            result: `${folder} folder contains ${files.length || mockFileSystem.length} files.`,
            displayData: { files: files.length > 0 ? files : mockFileSystem },
            risk: tool.risk,
          };
        }

        case 'search_files': {
          const query = (args.query || '').toLowerCase();
          const matches = mockFileSystem.filter((f) => f.name.toLowerCase().includes(query));
          return {
            success: true,
            result: matches.length > 0
              ? `Search result: Found ${matches.map((m) => m.name).join(', ')}.`
              : `No file named "${query}" was found.`,
            displayData: { matches },
            risk: tool.risk,
          };
        }

        case 'delete_file': {
          const path = args.filePath || 'Downloads/temp.txt';
          return {
            success: true,
            result: `File ${path} has been deleted successfully.`,
            displayData: { deleted: path },
            risk: tool.risk,
          };
        }

        case 'read_notifications': {
          const notifs = [
            { app: 'WhatsApp', sender: 'Rahul Kumar', text: 'Hey, are you free for the project discussion?' },
            { app: 'Gmail', sender: 'Google Cloud', text: 'Billing alert: Monthly summary is ready' },
            { app: 'Messages', sender: 'HDFC Bank', text: 'INR 1,200 credited to account ending 4092' },
          ];
          return {
            success: true,
            result: `You have 3 notifications: Rahul's WhatsApp message saying "Are you free?", a Google Cloud billing summary, and an HDFC bank credit alert.`,
            displayData: { notifications: notifs },
            risk: tool.risk,
          };
        }

        case 'take_photo': {
          return {
            success: true,
            result: 'Camera has been opened and a photo has been captured (Saved in DCIM/Camera).',
            displayData: { photo: 'DCIM/Camera/IMG_VASU_AUTO.jpg' },
            risk: tool.risk,
          };
        }

        default:
          return {
            success: true,
            result: `Tool ${tool.name} has been executed successfully.`,
            risk: tool.risk,
          };
      }
    } catch (err: any) {
      return {
        success: false,
        result: `Error executing tool: ${err?.message || String(err)}`,
        risk: tool.risk,
      };
    }
  }
}

export const executeToolCall = (
  toolId: string,
  args: Record<string, any> = {},
  context?: { setTorchState?: (on: boolean) => void }
): Promise<ToolExecutionResult> => ToolExecutor.execute(toolId, args, context);

