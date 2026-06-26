# AI Agent Suite

A sophisticated, triple-hardened, offline-first Android application designed for high-fidelity multi-agent orchestration, dynamic task automation, secure local knowledge-base indexing (RAG), and dual-mode LLM execution. Built entirely in modern Kotlin, Jetpack Compose, and Material Design 3, the suite is powered by a real SQLite database (via Room) and safeguarded by hardware-backed cryptographic key containers.

---

## 📱 Architecture & Tech Stack

The AI Agent Suite is engineered using modern Android development principles to guarantee low-latency performance, complete database integrity, and state-of-the-art security.

*   **UI Framework**: Jetpack Compose with edge-to-edge system bar integrations, custom glassmorphism components, and a custom **Space Slate Dark Theme**.
*   **State Management**: Model-View-ViewModel (MVVM) architecture with asynchronous unidirected data flow powered by Kotlin Coroutines, `StateFlow`, and `collectAsStateWithLifecycle`.
*   **Local Persistence (Real Database)**: Room SQLite ORM managing complete relational schemas for `ChatMessageEntity` (full history), `TaskEntity` (active schedules), and `MemoryEntity` (long-term facts used in local RAG pipelines).
*   **Secure Storage**: Android Jetpack Security (`EncryptedSharedPreferences`) backed by the Android Keystore system. Keys are encrypted using AES-256-SIV for preference keys and AES-256-GCM for preference values.
*   **Network Client**: OkHttp with Kotlinx Serialization and Moshi API adapter schemas for strict JSON type safety.
*   **Native Device Features**: Automated system telemetry (live CPU, memory, and battery tracking), native Text-to-Speech (TTS) voice engines, and Speech-to-Text (STT) intent-based dictation.

---

## 🛡️ Security & Hardening Features

Security is fundamental to the AI Agent Suite. It implements enterprise-grade measures to protect user data:

1.  **Triple-Hardened Local Authentication**:
    *   No empty strings or weak email credentials bypass the entry screen.
    *   Real-time email format validation using native Android pattern matching.
    *   Saves active session states inside persistent storage so users remain securely logged in across app restarts.
2.  **Hardware-Backed API Key Vault**:
    *   API keys for Google Gemini, OpenAI, and Anthropic Claude are never stored in plain text or standard `SharedPreferences`.
    *   An Android Keystore `MasterKey` is instantiated on first boot to encrypt inputs utilizing hardware security modules (TEE/StrongBox) when available.
    *   Integrates UI-level security protections including custom password visual transformations (toggleable visibility masks) preventing shoulder surfing.
3.  **Local Loopback Protection**:
    *   All queries to local Ollama servers execute on trusted loopback networks (e.g., standard `http://10.0.2.2` for the Android emulator or customized local network endpoints) preventing data leakage to external clouds.

---

## 🤖 The Six Specialized AI Agents

The suite exposes six distinct agent workspaces, each configured with specific system instructions, conversational traits, and hardware scopes.

### 🎙️ 1. Personal Assistant
*   **Specialization**: Calendar operations, scheduled reminders, notification triage, and daily weather briefings.
*   **Feature-Set**: Configured with full on-device Text-to-Speech (TTS) readouts and native Voice Dictation support.

### ⚡ 2. Automation Agent
*   **Specialization**: Routine scripting, smart power-saving triggers, silent/DND rules, and hardware state automation.
*   **Feature-Set**: Interacts with the local system status card to advise on optimization profiles.

### 💻 3. Coding Agent
*   **Specialization**: Architectural reviews, syntax debugging, API generation, and SQL querying.
*   **Feature-Set**: Tailored to respond in cleanly formatted Markdown with structural code blocks.

### 📈 4. Business Agent
*   **Specialization**: PDF contract structures, invoicing templates, marketing copy, and local budget tracking.
*   **Feature-Set**: Designed for structured mathematical outputs, financial metrics, and corporate layouts.

### 🔍 5. Research Agent
*   **Specialization**: Dynamic context summaries, product comparison matrix creation, and document indexing.
*   **Feature-Set**: Directly tied to the local SQLite knowledge-base for fast RAG lookups.

### 🛡️ 6. Security Agent
*   **Specialization**: Permission audits, password complexity checks, phishing scam alerts, and local file scanner utilities.
*   **Feature-Set**: Evaluates device telemetry and database records to suggest hardening checklists.

---

## ⚙️ LLM Engine Pipelines & Configurations

The app has a unified network dispatcher supporting **four powerful LLM pipelines**. If any API request fails due to lack of network, invalid keys, or host timeouts, the system gracefully falls back to a high-fidelity local simulation tailored to the active agent's personality.

### 1. Google Gemini 3.5 Flash (Default)
*   **Live Mode**: Triggered when a Gemini key is saved or configured via build variables. Communicates with `https://generativelanguage.googleapis.com` via structured JSON schemas.
*   **System Instructions**: Integrates native system instructions into the model config payload.
*   **Local RAG Context**: Automatically queries the SQLite Room memory table, pulls active context, and appends long-term memories to the request as system background context.

### 2. OpenAI ChatGPT (GPT-4o-mini)
*   **Live Mode**: Triggered when an OpenAI API key is entered in Settings. Sends fully constructed chat histories to `https://api.openai.com/v1/chat/completions`.
*   **Payload Format**: Implements the standardized role-based JSON structure (`system`, `user`, `assistant`).

### 3. Anthropic Claude (Claude 3.5 Sonnet)
*   **Live Mode**: Communicates with `https://api.anthropic.com/v1/messages` using an authorized Anthropic credential.
*   **Custom Headers**: Passes `x-api-key` and strictly sets `anthropic-version` to `2023-06-01` to comply with Anthropic's message APIs.

### 4. Local Ollama LLM (Offline Llama-3-8B)
*   **Live Mode**: Connects directly to local offline engines on the host machine or home network.
*   **Configurable Host**: Supports fully custom endpoints (e.g., `http://192.168.1.50:11434` or default emulator loopback `http://10.0.2.2:11434`).
*   **Payload Format**: Interacts with Ollama's `/api/chat` route with `stream` set to `false`.

---

## 📥 Installation & Setup Guide

### Prerequisites
*   Android Studio Ladybug (2024.2.1) or higher.
*   Android SDK Platform 34 (Android 14) or higher.
*   Java Development Kit (JDK) 17.

### Step 1: Clone and Import
1.  Clone the repository to your workspace:
    ```bash
    git clone <repository-url> ai-agent-suite
    ```
2.  Open Android Studio, select **Open**, and navigate to the project root directory.
3.  Let the Gradle sync complete successfully.

### Step 2: Configure Environment Variables (Optional)
If you wish to hardcode developer-level credentials, add them to your secure build configurations using the Secrets Gradle Plugin:
1.  Create a file named `.env` at the project root directory:
    ```env
    GEMINI_API_KEY=your_default_gemini_api_key_here
    ```
2.  The plugin will compile this key safely into `BuildConfig.GEMINI_API_KEY` at build time, keeping it out of source control.

### Step 3: Run & Compile
1.  Connect an Android device with USB Debugging enabled, or boot an Android Virtual Device (AVD).
2.  Select the `app` run configuration in the top toolbar.
3.  Click the **Run** button (Green Play Icon) or compile directly from your command line:
    ```bash
    gradle assembleDebug
    ```

---

## 🕹️ User Operation Guide (Start to Finish)

Follow these steps to operate the AI Agent Suite:

### Phase 1: Authentication Gate
1.  Upon launching the app, you will see the secure **AI Agent Suite Authenticator** screen.
2.  Input your **Email Address** and your **Full Name**.
    *   *The login is real!* It verifies formatting and creates a secure session state.
3.  Click the **AUTHENTICATE & ENTER** button. This initializes the application dashboard, and your credentials are stored securely in local preferences.

### Phase 2: Orchestrating Agents (The Home Screen)
1.  The home screen features live system telemetry tracking **CPU Usage**, **Memory Allocation**, and **Battery Health** in real time.
2.  Below the telemetry, select one of the **Six Specialized Agents** to open its private chat interface.
3.  **Sending Prompts**: Type your instruction in the input bar and click **Send**.
4.  **Voice Dictation**: Click the **Microphone** icon next to the send button. This opens the native Android Speech Recognizer. Speak your prompt aloud; the app converts it to text and inserts it directly into your chat bar.
5.  **Text-To-Speech (TTS)**: When an agent replies, click the **Speaker** icon on the message bubble to have the native Android voice engine read the response aloud.

### Phase 3: Task Automation Board
1.  Tap the **Tasks** icon in the bottom navigation bar.
2.  This lists all active automation routines and scripts currently registered in your SQLite database.
3.  **Add a Task**: Click the Floating Action Button (FAB) in the bottom corner. Input the task name (e.g., "Schedule silent mode on driving state") and select a priority (High, Medium, Low).
4.  **Interactive States**: Tap the checkbox on any task to mark it as complete. Completed tasks are instantly updated in the database. Swipe or tap the delete trash can to clean up completed routines.

### Phase 4: Long-Term Memory (RAG Indexing)
1.  Tap the **Memory** icon in the bottom navigation bar.
2.  This displays a list of structured facts, system logs, and personalized context stored in the SQLite Room database.
3.  **Ingest Fact**: Type a new detail into the input field (e.g., "Arnold's work calendar ends at 5 PM daily") and click the **Save Memory** button.
4.  This memory is instantly stored locally. When using **Google Gemini 3.5 Flash**, these memories are searched and injected into the system prompt context, allowing the model to know personal facts offline.

### Phase 5: Encrypted Settings Hub
1.  Tap the **Settings** icon in the bottom navigation bar.
2.  **View Profile**: Displays your active authenticated session info, with a prominent **Log Out** button to securely clear current preferences and return to the login screen.
3.  **Select active AI LLM Model**: Tap your preferred reasoning pipeline (Gemini, ChatGPT, Claude, or Ollama). This directs all chat messages to that specific pipeline.
4.  **API Key Manager**:
    *   Paste your active production keys into the Gemini, OpenAI, Claude, or Ollama fields.
    *   Click the **Eye** icon to securely reveal/hide keys.
    *   Click the **SAVE & SECURE CREDENTIALS** button. Keys are encrypted via AES-256 and safely written to device storage.

---

## 🛠️ Developer Verification & Unit Testing

The app comes integrated with local unit testing environments for rapid integration diagnostics.

### Running Local JVM Unit Tests
Run standard Kotlin/Compose unit tests using Gradle to verify database integrations and view-model states:
```bash
gradle :app:testDebugUnitTest
```

### Advanced Telemetry Sandbox (Developer Settings)
To verify sandbox outputs and permission directories:
1.  Open the **Settings** tab.
2.  Scroll to the **System Sandbox Telemetry** block.
3.  Review active system paths, sandbox directory listings, and environment variables safely inside the secured visual container.
