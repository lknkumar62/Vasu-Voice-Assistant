import React, { useState } from 'react';
import {
  Folder,
  FileCode,
  Download,
  X,
  Copy,
  Check,
  Terminal,
  Layers,
  Sparkles,
  GitBranch,
} from 'lucide-react';

interface AndroidProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface ProjectFile {
  path: string;
  name: string;
  language: string;
  category: string;
  content: string;
}

const ANDROID_FILES: ProjectFile[] = [
  {
    path: '.github/workflows/android-debug.yml',
    name: 'android-debug.yml',
    category: 'CI/CD',
    language: 'yaml',
    content: `name: Android Debug Build & APK Generation

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    name: Build VASU Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Run Unit Tests
        run: ./gradlew test

      - name: Run Android Lint
        run: ./gradlew lintDebug

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload Debug APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: vasu-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
`,
  },
  {
    path: 'app/build.gradle.kts',
    name: 'app/build.gradle.kts',
    category: 'Build Config',
    language: 'kotlin',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vasu.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vasu.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Room Database for Persistent Memory
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager for Missions
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines & StateFlow
    implementation(libs.kotlinx.coroutines.android)

    // Security & EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // OkHttp & Serialization
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // TensorFlow Lite for Real Wake Word Spotting
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
`,
  },
  {
    path: 'app/src/main/AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    category: 'Manifest',
    language: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Audio & Microphone Permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Foreground Service for Background Wake Word -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- System & Device Controls -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.flash" android:required="false" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".VasuApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="VASU Assistant"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.VasuAssistant">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VasuAssistant">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Background Wake Word & Voice Foreground Service -->
        <service
            android:name=".service.VasuVoiceService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="microphone" />

        <!-- Accessibility Service for Screen Inspection & Automation -->
        <service
            android:name=".service.VasuAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- Notification Listener Service -->
        <service
            android:name=".service.VasuNotificationListenerService"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>
</manifest>
`,
  },
  {
    path: 'app/src/main/java/com/vasu/assistant/MainActivity.kt',
    name: 'MainActivity.kt',
    category: 'Presentation',
    language: 'kotlin',
    content: `package com.vasu.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vasu.assistant.presentation.navigation.VasuNavHost
import com.vasu.assistant.presentation.theme.VasuAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VasuAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VasuNavHost()
                }
            }
        }
    }
}
`,
  },
  {
    path: 'app/src/main/java/com/vasu/assistant/ai/AIProviderRouter.kt',
    name: 'AIProviderRouter.kt',
    category: 'AI Routing',
    language: 'kotlin',
    content: `package com.vasu.assistant.ai

import com.vasu.assistant.core.Result
import com.vasu.assistant.offline.LocalCommandEngine

class AIProviderRouter(
    private val geminiProvider: GeminiProvider,
    private val localEngine: LocalCommandEngine
) {
    suspend fun route(input: String, isNetworkAvailable: Boolean): Result<AIResponse> {
        // 1. Fast path for local deterministic commands
        val localMatch = localEngine.parse(input)
        if (localMatch.matched) {
            return Result.Success(
                AIResponse(
                    spokenReply = localMatch.spokenResponse ?: "Ji, execute kar diya hai.",
                    toolToExecute = localMatch.toolId,
                    toolArguments = localMatch.args ?: emptyMap(),
                    isOffline = true
                )
            )
        }

        // 2. Gemini Online Brain if network available & configured
        if (isNetworkAvailable && geminiProvider.isConfigured()) {
            val geminiResult = geminiProvider.chat(input)
            if (geminiResult is Result.Success) {
                return geminiResult
            }
        }

        // 3. Fallback to Local Engine response
        return Result.Success(
            AIResponse(
                spokenReply = "VASU offline mode mein hai. Aap device tools jaise torch, volume, alarm direct use kar sakte hain.",
                isOffline = true
            )
        )
    }
}

data class AIResponse(
    val spokenReply: String,
    val toolToExecute: String? = null,
    val toolArguments: Map<String, Any> = emptyMap(),
    val isOffline: Boolean = false
)
`,
  },
  {
    path: 'app/src/main/java/com/vasu/assistant/tools/ToolRegistry.kt',
    name: 'ToolRegistry.kt',
    category: 'Tools',
    language: 'kotlin',
    content: `package com.vasu.assistant.tools

import com.vasu.assistant.security.ToolRisk

object ToolRegistry {
    private val tools = mutableMapOf<String, ToolDefinition>()

    init {
        register(
            ToolDefinition(
                name = "turn_on_torch",
                description = "Turn device flashlight on",
                risk = ToolRisk.LOW,
                requiredPermission = "android.permission.CAMERA"
            )
        )
        register(
            ToolDefinition(
                name = "volume_up",
                description = "Increase media volume",
                risk = ToolRisk.LOW,
                requiredPermission = null
            )
        )
        register(
            ToolDefinition(
                name = "open_whatsapp",
                description = "Open WhatsApp chat intent",
                risk = ToolRisk.MEDIUM,
                requiredPermission = null
            )
        )
        register(
            ToolDefinition(
                name = "read_screen",
                description = "Accessibility screen reader node extraction",
                risk = ToolRisk.LOW,
                requiredPermission = "Accessibility"
            )
        )
        register(
            ToolDefinition(
                name = "delete_file",
                description = "Delete file from device",
                risk = ToolRisk.HIGH,
                requiredPermission = "android.permission.MANAGE_EXTERNAL_STORAGE"
            )
        )
    }

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
    }

    fun get(name: String): ToolDefinition? = tools[name]

    fun getAll(): List<ToolDefinition> = tools.values.toList()
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val risk: ToolRisk,
    val requiredPermission: String?
)
`,
  },
  {
    path: 'app/src/main/java/com/vasu/assistant/service/VasuAccessibilityService.kt',
    name: 'VasuAccessibilityService.kt',
    category: 'Automation',
    language: 'kotlin',
    content: `package com.vasu.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VasuAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observes UI window events safely
    }

    override fun onInterrupt() {
        // Release accessibility resources
    }

    fun inspectCurrentScreen(): List<String> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val textList = mutableListOf<String>()
        traverseNode(rootNode, textList)
        return textList
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, results: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            results.add("[\${node.className}] \$text")
        }
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), results)
        }
    }
}
`,
  },
];

export const AndroidProjectModal: React.FC<AndroidProjectModalProps> = ({ isOpen, onClose }) => {
  const [selectedFile, setSelectedFile] = useState<ProjectFile>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = () => {
    // Generate blob bundle with the selected source code
    const blob = new Blob([selectedFile.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = selectedFile.name;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
          <div className="flex items-center gap-2">
            <Layers className="w-5 h-5 text-cyan-400" />
            <div>
              <h3 className="text-sm font-mono font-bold text-slate-100 flex items-center gap-2">
                <span>VASU Assistant — Android Source Repository</span>
                <span className="text-[10px] px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800 font-normal">
                  Production Kotlin
                </span>
              </h3>
              <p className="text-[11px] text-slate-400 font-sans">
                Clean Architecture + Jetpack Compose + MVVM + Room + WorkManager + GitHub Actions CI
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 flex overflow-hidden">
          {/* File Tree Sidebar */}
          <div className="w-64 border-r border-slate-800 p-2 overflow-y-auto bg-slate-950/40 text-xs font-mono space-y-1 shrink-0">
            <span className="text-[10px] uppercase text-slate-500 font-bold px-2 py-1 block">
              Source Tree (Ready to Build)
            </span>
            {ANDROID_FILES.map((file) => {
              const isSelected = selectedFile.path === file.path;
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left px-2.5 py-1.5 rounded-lg flex items-center gap-2 transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-cyan-500/20 text-cyan-300 font-semibold border border-cyan-500/40'
                      : 'text-slate-400 hover:bg-slate-850 hover:text-slate-200'
                  }`}
                >
                  <FileCode className="w-3.5 h-3.5 shrink-0 text-cyan-400" />
                  <span className="truncate">{file.name}</span>
                </button>
              );
            })}
          </div>

          {/* Code Viewer */}
          <div className="flex-1 flex flex-col overflow-hidden bg-slate-950">
            {/* Viewer Toolbar */}
            <div className="px-4 py-2 border-b border-slate-800 flex items-center justify-between text-xs font-mono bg-slate-900/50">
              <span className="text-cyan-400">{selectedFile.path}</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleCopy}
                  className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center gap-1 transition-colors"
                >
                  {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>
                <button
                  onClick={handleDownloadZip}
                  className="px-2.5 py-1 rounded bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-semibold flex items-center gap-1 transition-colors cursor-pointer"
                >
                  <Download className="w-3 h-3" />
                  <span>Download File</span>
                </button>
              </div>
            </div>

            {/* Code Content */}
            <pre className="flex-1 p-4 text-xs font-mono text-slate-300 overflow-y-auto leading-relaxed whitespace-pre selection:bg-cyan-500/30">
              <code>{selectedFile.content}</code>
            </pre>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-3 border-t border-slate-800 bg-slate-950/80 flex items-center justify-between text-xs font-mono text-slate-400">
          <div className="flex items-center gap-2">
            <GitBranch className="w-4 h-4 text-cyan-400" />
            <span>Target Repository: vasu-assistant • Branch: main</span>
          </div>
          <span>CI: .github/workflows/android-debug.yml &rarr; app-debug.apk</span>
        </div>
      </div>
    </div>
  );
};
