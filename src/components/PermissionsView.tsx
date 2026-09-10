import React, { useState } from 'react';
import { PermissionItem } from '../types';
import {
  ShieldCheck,
  Mic,
  Camera,
  Users,
  Phone,
  Bell,
  Eye,
  Folder,
  Bluetooth,
  AlarmClock,
  Layers,
  CheckCircle2,
  ExternalLink,
  Volume2,
  Check,
  Sparkles,
  Settings,
  ToggleLeft,
  ToggleRight,
  HelpCircle,
} from 'lucide-react';

interface PermissionsViewProps {
  permissions: PermissionItem[];
  onRequestPermission: (id: string) => Promise<void>;
  onTogglePermission?: (id: string, enable: boolean) => Promise<void>;
  onOpenSettings?: (id: string) => void;
  onEnableAllPermissions?: () => Promise<void>;
  onReadNotifications?: () => Promise<void>;
}

export const PERMISSION_SETTINGS_INFO: Record<string, { action: string; label: string; hint: string }> = {
  microphone: {
    action: 'APPLICATION_DETAILS_SETTINGS',
    label: 'App Info & Mic',
    hint: 'Android Settings > Apps > VASU Assistant > Permissions > Microphone',
  },
  accessibility: {
    action: 'ACCESSIBILITY_SETTINGS',
    label: 'Accessibility Service',
    hint: 'Android Settings > Accessibility > VASU Assistant > Turn ON',
  },
  notifications: {
    action: 'ACTION_NOTIFICATION_LISTENER_SETTINGS',
    label: 'Notification Listener',
    hint: 'Android Settings > Special App Access > Notification Access > Allow VASU',
  },
  camera: {
    action: 'APPLICATION_DETAILS_SETTINGS',
    label: 'Camera Permission',
    hint: 'Android Settings > Apps > VASU Assistant > Permissions > Camera',
  },
  files: {
    action: 'MANAGE_ALL_FILES_ACCESS_PERMISSION',
    label: 'All Files Access',
    hint: 'Android Settings > Special App Access > All Files Access > Allow VASU',
  },
  phone: {
    action: 'APPLICATION_DETAILS_SETTINGS',
    label: 'Phone Permissions',
    hint: 'Android Settings > Apps > VASU Assistant > Permissions > Phone & Calls',
  },
  contacts: {
    action: 'APPLICATION_DETAILS_SETTINGS',
    label: 'Contacts Access',
    hint: 'Android Settings > Apps > VASU Assistant > Permissions > Contacts',
  },
  alarms: {
    action: 'REQUEST_SCHEDULE_EXACT_ALARM',
    label: 'Exact Alarms',
    hint: 'Android Settings > Special Access > Alarms & Reminders > Allow VASU',
  },
  foreground_service: {
    action: 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    label: 'Battery Unrestricted',
    hint: 'Android Settings > Battery > Battery Optimization > Don\'t Optimize',
  },
  bluetooth: {
    action: 'BLUETOOTH_SETTINGS',
    label: 'Bluetooth Settings',
    hint: 'Android Settings > Bluetooth / Connected Devices',
  },
};

export const PermissionsView: React.FC<PermissionsViewProps> = ({
  permissions,
  onRequestPermission,
  onTogglePermission,
  onOpenSettings,
  onEnableAllPermissions,
  onReadNotifications,
}) => {
  const [isEnablingAll, setIsEnablingAll] = useState(false);
  const [isReadingNotifs, setIsReadingNotifs] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [selectedHint, setSelectedHint] = useState<string | null>(null);

  const grantedCount = permissions.filter((p) => p.status === 'GRANTED').length;
  const allGranted = grantedCount === permissions.length;

  const handleOpenAndroidSettings = (actionKey: string) => {
    if (onOpenSettings) {
      onOpenSettings(actionKey);
      return;
    }

    try {
      if (actionKey === 'APPLICATION_DETAILS_SETTINGS' || actionKey.includes('package')) {
        window.location.href = `intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;package=com.vasu.assistant;end`;
        return;
      }
      window.location.href = `intent:#Intent;action=android.settings.${actionKey};end`;
    } catch (_) {
      const info = PERMISSION_SETTINGS_INFO[actionKey];
      alert(`Android Settings Redirect:\n${info ? info.hint : 'Please open Android Settings > Apps > Special App Access'}`);
    }
  };

  const handleToggle = async (perm: PermissionItem) => {
    setTogglingId(perm.id);
    const willEnable = perm.status !== 'GRANTED';
    try {
      if (onTogglePermission) {
        await onTogglePermission(perm.id, willEnable);
      } else {
        if (willEnable) {
          await onRequestPermission(perm.id);
        }
      }
    } finally {
      setTogglingId(null);
    }
  };

  const handleEnableAll = async () => {
    setIsEnablingAll(true);
    try {
      if (onEnableAllPermissions) {
        await onEnableAllPermissions();
      } else {
        for (const p of permissions) {
          if (p.status !== 'GRANTED') {
            await onRequestPermission(p.id);
          }
        }
      }
    } finally {
      setIsEnablingAll(false);
    }
  };

  const handleTriggerReadNotifs = async () => {
    setIsReadingNotifs(true);
    try {
      if (onReadNotifications) {
        await onReadNotifications();
      }
    } finally {
      setIsReadingNotifs(false);
    }
  };

  const getPermIcon = (id: string) => {
    switch (id) {
      case 'microphone':
        return <Mic className="w-4 h-4 text-cyan-400" />;
      case 'camera':
        return <Camera className="w-4 h-4 text-teal-400" />;
      case 'contacts':
        return <Users className="w-4 h-4 text-indigo-400" />;
      case 'phone':
        return <Phone className="w-4 h-4 text-emerald-400" />;
      case 'notifications':
        return <Bell className="w-4 h-4 text-amber-400" />;
      case 'accessibility':
        return <Eye className="w-4 h-4 text-cyan-300" />;
      case 'files':
        return <Folder className="w-4 h-4 text-blue-400" />;
      case 'bluetooth':
        return <Bluetooth className="w-4 h-4 text-sky-400" />;
      case 'alarms':
        return <AlarmClock className="w-4 h-4 text-orange-400" />;
      case 'foreground_service':
      default:
        return <Layers className="w-4 h-4 text-purple-400" />;
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-210px)] max-w-2xl mx-auto w-full px-3">
      {/* Header */}
      <div className="py-2.5 border-b border-slate-800 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-5 h-5 text-cyan-400 shrink-0" />
          <div>
            <h2 className="font-mono text-sm font-semibold text-slate-100 flex items-center gap-2">
              <span>Permission & Automation Hub</span>
              <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono border ${
                allGranted
                  ? 'bg-emerald-950 text-emerald-300 border-emerald-800'
                  : 'bg-cyan-950 text-cyan-300 border-cyan-800'
              }`}>
                {grantedCount}/{permissions.length} Active
              </span>
            </h2>
            <p className="text-[11px] text-slate-400">Android System, Accessibility, Voice & Notification controls</p>
          </div>
        </div>

        {/* Universal Android App Info Settings Button */}
        <button
          id="btn-universal-app-settings"
          onClick={() => handleOpenAndroidSettings('APPLICATION_DETAILS_SETTINGS')}
          className="px-2.5 py-1.5 rounded-lg text-xs bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 flex items-center gap-1 transition-colors cursor-pointer shrink-0"
          title="Open Android App Info & Permissions"
        >
          <Settings className="w-3.5 h-3.5 text-cyan-400" />
          <span className="hidden sm:inline">App Settings</span>
          <ExternalLink className="w-3 h-3 text-slate-400" />
        </button>
      </div>

      {/* Action Banners */}
      <div className="pt-3 space-y-2.5">
        {/* 1. ENABLE ALL PERMISSIONS PROMINENT BUTTON */}
        <div className="p-3 rounded-xl bg-gradient-to-r from-cyan-950/70 via-slate-900 to-indigo-950/70 border border-cyan-500/40 flex flex-col sm:flex-row items-center justify-between gap-3 shadow-lg shadow-cyan-950/30">
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <div className="p-2.5 rounded-lg bg-cyan-500/20 border border-cyan-500/40 text-cyan-400 shrink-0">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-xs font-semibold text-white flex items-center gap-1.5">
                <span>Enable All Permissions</span>
                <span className="text-[10px] text-cyan-300 bg-cyan-900/60 px-1.5 rounded font-mono">1-Tap Setup</span>
              </h3>
              <p className="text-[11px] text-slate-300">
                माइक, कैमरा, नोटिफिकेशन और ऑटोमेशन की सभी अनुमतियां एक साथ सक्षम करें
              </p>
            </div>
          </div>

          <button
            id="btn-enable-all-permissions"
            onClick={handleEnableAll}
            disabled={isEnablingAll || allGranted}
            className={`w-full sm:w-auto px-4 py-2 rounded-lg font-medium text-xs flex items-center justify-center gap-1.5 transition-all shadow-md cursor-pointer ${
              allGranted
                ? 'bg-emerald-900/60 text-emerald-200 border border-emerald-700 cursor-default'
                : 'bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold active:scale-95'
            }`}
          >
            {allGranted ? (
              <>
                <Check className="w-4 h-4 text-emerald-300" />
                <span>All Permissions Active</span>
              </>
            ) : isEnablingAll ? (
              <span>Enabling...</span>
            ) : (
              <>
                <ShieldCheck className="w-4 h-4" />
                <span>Allow All Permissions</span>
              </>
            )}
          </button>
        </div>

        {/* 2. DEDICATED NOTIFICATION READER CARD */}
        <div className="p-3 rounded-xl bg-slate-900/90 border border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <div className="p-2.5 rounded-lg bg-amber-500/20 border border-amber-500/30 text-amber-400 shrink-0">
              <Bell className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xs font-semibold text-slate-200">Notification Reader</h3>
                <span className="text-[10px] text-amber-300 bg-amber-950/80 px-1.5 py-0.2 rounded font-mono border border-amber-800/50">
                  Auto Speak
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                WhatsApp, Messages, Gmail के नोटिफिकेशन्स वासु पढ़कर सुनाएगी
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
            <button
              id="btn-open-notif-settings"
              onClick={() => handleOpenAndroidSettings('ACTION_NOTIFICATION_LISTENER_SETTINGS')}
              className="px-2.5 py-1.5 rounded-lg text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 flex items-center gap-1 transition-colors cursor-pointer"
              title="Open Android Notification Access Settings"
            >
              <Settings className="w-3.5 h-3.5 text-amber-400" />
              <span>Android Access ↗</span>
            </button>

            <button
              id="btn-read-notifications-now"
              onClick={handleTriggerReadNotifs}
              disabled={isReadingNotifs}
              className="px-3 py-1.5 rounded-lg text-xs bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 font-medium flex items-center gap-1.5 transition-colors cursor-pointer active:scale-95"
            >
              <Volume2 className="w-3.5 h-3.5 text-amber-400" />
              <span>{isReadingNotifs ? 'Reading...' : 'Read Now'}</span>
            </button>
          </div>
        </div>

        {/* 3. DEDICATED ACCESSIBILITY SERVICE CARD */}
        <div className="p-3 rounded-xl bg-slate-900/90 border border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <div className="p-2.5 rounded-lg bg-cyan-500/20 border border-cyan-500/30 text-cyan-300 shrink-0">
              <Eye className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xs font-semibold text-slate-200">Accessibility Automation Service</h3>
                <span className="text-[10px] text-cyan-300 bg-cyan-950 px-1.5 py-0.2 rounded font-mono border border-cyan-800/50">
                  Screen Nodes
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                स्क्रीन पर ऑटो-क्लिक, ऐप्स खोलना और स्क्रीन टेक्स्ट पढ़ने के लिए
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
            <button
              id="btn-open-accessibility-settings"
              onClick={() => handleOpenAndroidSettings('ACCESSIBILITY_SETTINGS')}
              className="px-3 py-1.5 rounded-lg text-xs bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 border border-cyan-500/40 font-medium flex items-center gap-1.5 transition-colors cursor-pointer active:scale-95"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              <span>Accessibility Settings ↗</span>
            </button>
          </div>
        </div>
      </div>

      {/* Helpful Hint Callout if clicked */}
      {selectedHint && (
        <div className="mt-2.5 p-2.5 rounded-lg bg-slate-900 border border-cyan-500/40 flex items-center justify-between text-xs text-cyan-200">
          <div className="flex items-center gap-2">
            <HelpCircle className="w-4 h-4 text-cyan-400 shrink-0" />
            <span>{selectedHint}</span>
          </div>
          <button
            onClick={() => setSelectedHint(null)}
            className="text-slate-400 hover:text-white text-xs px-1.5 py-0.5"
          >
            ✕
          </button>
        </div>
      )}

      {/* Permissions List with ON/OFF Toggles */}
      <div className="flex-1 overflow-y-auto space-y-2 pt-3 pr-1 mt-1">
        <div className="flex items-center justify-between px-1 mb-1">
          <h4 className="text-[11px] font-mono text-slate-400 uppercase tracking-wider">
            All Permissions (ON / OFF Controls)
          </h4>
          <span className="text-[10px] text-slate-400">
            टॉगल करें या सेटिंग्स में खोलें
          </span>
        </div>

        {permissions.map((perm) => {
          const isGranted = perm.status === 'GRANTED';
          const isToggling = togglingId === perm.id;
          const info = PERMISSION_SETTINGS_INFO[perm.id];

          return (
            <div
              key={perm.id}
              className={`p-3 rounded-xl border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 transition-all ${
                isGranted
                  ? 'bg-slate-900/90 border-slate-800 hover:border-slate-700'
                  : 'bg-slate-950/70 border-slate-800/60'
              }`}
            >
              {/* Permission Details */}
              <div className="flex items-start gap-3 flex-1 min-w-0">
                <div className={`p-2 rounded-lg border shrink-0 ${
                  isGranted ? 'bg-slate-950 border-slate-800' : 'bg-slate-900/50 border-slate-800/50 opacity-60'
                }`}>
                  {getPermIcon(perm.id)}
                </div>
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-xs text-slate-100">{perm.name}</span>
                    <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 uppercase">
                      {perm.category}
                    </span>
                    {isGranted ? (
                      <span className="flex items-center gap-1 text-[10px] font-mono px-1.5 py-0.2 rounded bg-emerald-950/80 text-emerald-300 border border-emerald-800">
                        <CheckCircle2 className="w-2.5 h-2.5 text-emerald-400" />
                        <span>ACTIVE</span>
                      </span>
                    ) : (
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800/80 text-slate-400 border border-slate-700">
                        OFF
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5 leading-snug">{perm.description}</p>
                </div>
              </div>

              {/* Action Area: ON/OFF Switch + Android Settings Redirect */}
              <div className="flex items-center gap-2 w-full sm:w-auto justify-end shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-800/60">
                {/* Dedicated Android Settings Redirect Button */}
                <button
                  id={`btn-settings-${perm.id}`}
                  onClick={() => {
                    const action = info ? info.action : 'APPLICATION_DETAILS_SETTINGS';
                    handleOpenAndroidSettings(action);
                    if (info?.hint) {
                      setSelectedHint(info.hint);
                    }
                  }}
                  className="px-2 py-1.5 rounded-lg text-xs bg-slate-800/80 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 flex items-center gap-1 transition-colors cursor-pointer"
                  title={info ? info.hint : 'Open in Android Settings'}
                >
                  <Settings className="w-3 h-3 text-slate-400" />
                  <span className="text-[11px]">Settings ↗</span>
                </button>

                {/* Interactive ON / OFF Switch */}
                <button
                  id={`btn-toggle-perm-${perm.id}`}
                  onClick={() => handleToggle(perm)}
                  disabled={isToggling}
                  className={`relative flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-mono font-bold transition-all cursor-pointer select-none ${
                    isGranted
                      ? 'bg-emerald-950 text-emerald-300 border border-emerald-700 hover:bg-emerald-900/60 shadow-sm shadow-emerald-950/50'
                      : 'bg-slate-800 text-slate-400 border border-slate-700 hover:bg-slate-700/80'
                  }`}
                  title={isGranted ? 'Click to turn OFF' : 'Click to turn ON'}
                >
                  {isGranted ? (
                    <>
                      <span>ON</span>
                      <ToggleRight className="w-5 h-5 text-emerald-400" />
                    </>
                  ) : (
                    <>
                      <span>OFF</span>
                      <ToggleLeft className="w-5 h-5 text-slate-500" />
                    </>
                  )}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
