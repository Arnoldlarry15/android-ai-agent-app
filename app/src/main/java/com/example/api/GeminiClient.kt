package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Moshi JSON adapters
    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Generates content from the selected API using OkHttp and Moshi.
     * Supports live API connections when keys are saved in EncryptedSharedPreferences,
     * falling back to high-fidelity agent-specific offline mock responses on failure.
     */
    suspend fun generateResponse(
        agent: String,
        prompt: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        model: String = "Google Gemini 3.5 Flash",
        memoryContext: List<String> = emptyList(),
        customGeminiKey: String = "",
        customOpenAIKey: String = "",
        customClaudeKey: String = "",
        customOllamaEndpoint: String = ""
    ): String {
        val baseInstruction = getSystemPromptForAgent(agent)
        val systemInstruction = if (memoryContext.isNotEmpty()) {
            "$baseInstruction\n\nLong-Term Memories & RAG Indexed Documents (Reference these facts or files if relevant to the query):\n" + memoryContext.joinToString("\n") { "• $it" }
        } else {
            baseInstruction
        }

        val jsonAnyAdapter = moshi.adapter(Any::class.java)

        return when (model) {
            "OpenAI ChatGPT" -> {
                if (customOpenAIKey.isNotBlank()) {
                    try {
                        val messagesJson = StringBuilder()
                        chatHistory.takeLast(6).forEach { (sender, text) ->
                            val role = if (sender == "user") "user" else "assistant"
                            messagesJson.append("{\"role\": \"$role\", \"content\": \"${escapeJson(text)}\"},")
                        }
                        messagesJson.append("{\"role\": \"user\", \"content\": \"${escapeJson(prompt)}\"}")

                        val sysPromptJson = if (systemInstruction.isNotEmpty()) {
                            "{\"role\": \"system\", \"content\": \"${escapeJson(systemInstruction)}\"},"
                        } else {
                            ""
                        }

                        val requestBodyString = """
                        {
                          "model": "gpt-4o-mini",
                          "messages": [
                            $sysPromptJson
                            $messagesJson
                          ]
                        }
                        """.trimIndent()

                        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/chat/completions")
                            .header("Authorization", "Bearer $customOpenAIKey")
                            .post(requestBody)
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val map = jsonAnyAdapter.fromJson(responseBody) as? Map<*, *>
                            val choices = map?.get("choices") as? List<*>
                            val firstChoice = choices?.firstOrNull() as? Map<*, *>
                            val message = firstChoice?.get("message") as? Map<*, *>
                            val content = message?.get("content") as? String
                            if (!content.isNullOrBlank()) {
                                "🤖 **[ChatGPT-4o (Live API)]**\n\n$content"
                            } else {
                                "🤖 **[ChatGPT-4o]**\n\nEmpty response from OpenAI API."
                            }
                        } else {
                            val errBody = response.body?.string() ?: ""
                            Log.e(TAG, "OpenAI API error: $errBody")
                            "🤖 **[ChatGPT-4o]**\n\nError calling OpenAI API (code ${response.code}): $errBody"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "OpenAI failure", e)
                        "🤖 **[ChatGPT-4o]**\n\nAPI Network Failure: ${e.message}\n\n*Fallback simulated response*:\n\n${getOfflineAgentResponse(agent, prompt)}"
                    }
                } else {
                    val sim = getOfflineAgentResponse(agent, prompt)
                    "🤖 **[ChatGPT-4o (Simulated)]**\n\n$sim\n\n*Configure your OpenAI API key in Settings to activate the real cloud API pipeline.*"
                }
            }
            "Anthropic Claude" -> {
                if (customClaudeKey.isNotBlank()) {
                    try {
                        val messagesJson = StringBuilder()
                        chatHistory.takeLast(6).forEach { (sender, text) ->
                            val role = if (sender == "user") "user" else "assistant"
                            messagesJson.append("{\"role\": \"$role\", \"content\": \"${escapeJson(text)}\"},")
                        }
                        messagesJson.append("{\"role\": \"user\", \"content\": \"${escapeJson(prompt)}\"}")

                        val requestBodyString = """
                        {
                          "model": "claude-3-5-sonnet-20241022",
                          "max_tokens": 1024,
                          "system": "${escapeJson(systemInstruction)}",
                          "messages": [
                            $messagesJson
                          ]
                        }
                        """.trimIndent()

                        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
                        val request = Request.Builder()
                            .url("https://api.anthropic.com/v1/messages")
                            .header("x-api-key", customClaudeKey)
                            .header("anthropic-version", "2023-06-01")
                            .post(requestBody)
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val map = jsonAnyAdapter.fromJson(responseBody) as? Map<*, *>
                            val contentList = map?.get("content") as? List<*>
                            val firstContent = contentList?.firstOrNull() as? Map<*, *>
                            val text = firstContent?.get("text") as? String
                            if (!text.isNullOrBlank()) {
                                "🪶 **[Claude 3.5 Sonnet (Live API)]**\n\n$text"
                            } else {
                                "🪶 **[Claude 3.5 Sonnet]**\n\nEmpty response from Claude API."
                            }
                        } else {
                            val errBody = response.body?.string() ?: ""
                            Log.e(TAG, "Claude API error: $errBody")
                            "🪶 **[Claude 3.5 Sonnet]**\n\nError calling Claude API (code ${response.code}): $errBody"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Claude failure", e)
                        "🪶 **[Claude 3.5 Sonnet]**\n\nAPI Network Failure: ${e.message}\n\n*Fallback simulated response*:\n\n${getOfflineAgentResponse(agent, prompt)}"
                    }
                } else {
                    val sim = getOfflineAgentResponse(agent, prompt)
                    "🪶 **[Claude 3.5 Sonnet (Simulated)]**\n\n$sim\n\n*Configure your Claude API key in Settings to activate the high-fidelity cloud reasoning pipeline.*"
                }
            }
            "Local Ollama LLM" -> {
                val endpoint = customOllamaEndpoint.ifBlank { "http://10.0.2.2:11434" }
                try {
                    val messagesJson = StringBuilder()
                    chatHistory.takeLast(6).forEach { (sender, text) ->
                        val role = if (sender == "user") "user" else "assistant"
                        messagesJson.append("{\"role\": \"$role\", \"content\": \"${escapeJson(text)}\"},")
                    }
                    messagesJson.append("{\"role\": \"user\", \"content\": \"${escapeJson(prompt)}\"}")

                    val requestBodyString = """
                    {
                      "model": "llama3",
                      "messages": [
                        {"role": "system", "content": "${escapeJson(systemInstruction)}"},
                        $messagesJson
                      ],
                      "stream": false
                    }
                    """.trimIndent()

                    val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("$endpoint/api/chat")
                        .post(requestBody)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val map = jsonAnyAdapter.fromJson(responseBody) as? Map<*, *>
                        val message = map?.get("message") as? Map<*, *>
                        val content = message?.get("content") as? String
                        if (!content.isNullOrBlank()) {
                            "💻 **[Ollama: Llama-3 (Live)]**\n\n$content"
                        } else {
                            "💻 **[Ollama: Llama-3]**\n\nEmpty response from Ollama server."
                        }
                    } else {
                        val errBody = response.body?.string() ?: ""
                        Log.e(TAG, "Ollama API error: $errBody")
                        "💻 **[Ollama: Llama-3]**\n\nOllama server returned error code ${response.code}"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ollama failure", e)
                    val sim = getOfflineAgentResponse(agent, prompt)
                    "💻 **[Ollama: Llama-3-8B (Simulated)]**\n\n$sim\n\n*Could not connect to Ollama server at $endpoint. Verify the server is running offline on your device, or configure your custom endpoint in Settings.*"
                }
            }
            else -> { // Google Gemini
                val activeKey = customGeminiKey.ifBlank {
                    try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
                }

                if (activeKey.isBlank() || activeKey == "MY_GEMINI_API_KEY") {
                    val sim = getOfflineAgentResponse(agent, prompt)
                    val ragSuffix = if (memoryContext.isNotEmpty()) {
                        "\n\n🔍 *[RAG Long-Term Memory Synced]*\n" + memoryContext.takeLast(3).joinToString("\n") { "• $it" }
                    } else {
                        ""
                    }
                    "✨ **[Gemini 3.5 Flash (Simulated)]**\n\n$sim$ragSuffix\n\n*Configure your Gemini API key in Settings or AI Studio Secrets to unlock the true live multimodal reasoning engine.*"
                } else {
                    try {
                        val contents = mutableListOf<Content>()
                        chatHistory.takeLast(6).forEach { (sender, text) ->
                            val role = if (sender == "user") "user" else "model"
                            contents.add(Content(role = role, parts = listOf(Part(text = text))))
                        }
                        contents.add(Content(role = "user", parts = listOf(Part(text = prompt))))

                        val geminiRequest = GeminiRequest(
                            contents = contents,
                            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
                        )

                        val jsonRequest = requestAdapter.toJson(geminiRequest)
                        val requestBody = jsonRequest.toRequestBody("application/json".toMediaType())
                        val url = "$BASE_URL/v1beta/models/$DEFAULT_MODEL:generateContent?key=$activeKey"

                        val request = Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val geminiResponse = responseAdapter.fromJson(responseBody)
                            val responseText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!responseText.isNullOrBlank()) {
                                "✨ **[Gemini 3.5 Flash (Live API)]**\n\n$responseText"
                            } else {
                                "✨ **[Gemini 3.5 Flash]**\n\nI received an empty response. Please try again."
                            }
                        } else {
                            val errBody = response.body?.string() ?: ""
                            Log.e(TAG, "Gemini API error code ${response.code}: $errBody")
                            "✨ **[Gemini 3.5 Flash]**\n\nGemini API Error code ${response.code}\n\n*Fallback simulated response*:\n\n${getOfflineAgentResponse(agent, prompt)}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini failure", e)
                        "✨ **[Gemini 3.5 Flash]**\n\nAPI Network Failure: ${e.message}\n\n*Fallback simulated response*:\n\n${getOfflineAgentResponse(agent, prompt)}"
                    }
                }
            }
        }
    }

    private fun getSystemPromptForAgent(agent: String): String {
        return when (agent) {
            "Assistant" -> "You are the Personal Assistant Agent in AI Agent Hub. Your abilities: Voice chat, Calendar/reminders, Email summaries, SMS drafts, Daily agenda, Weather, and Translation. Give brief, friendly, structured, and helpful responses."
            "Automation" -> "You are the Automation Agent in AI Agent Hub. Your abilities: Launch apps, Battery-saving routines, Wi-Fi/Bluetooth automation, Silent modes by schedule or location, Driving mode, Bedtime mode, File organization, Notification filtering. Speak like a powerful, logic-driven task automation engine."
            "Coding" -> "You are the Coding Agent in AI Agent Hub. Your abilities: Write/debug Python, Java, Kotlin, JS, HTML, CSS, SQL, Bash. Explain errors, design APIs, build Android components. Present code blocks cleanly in Markdown."
            "Business" -> "You are the Business Agent in AI Agent Hub. Your abilities: Invoices, contracts, marketing copy, task tracking, meeting summaries, spreadsheet layout, email drafts, expense tracking. Professional, elegant, concise."
            "Research" -> "You are the Research Agent in AI Agent Hub. Your abilities: Web search summaries, article comparisons, custom reports, source tracking, fact-checking, saving research notes. Highly analytical, objective, and source-focused."
            "Security" -> "You are the Security Agent in AI Agent Hub. Your abilities: Scan installed apps for risky permissions, password strength, scam text detection, privacy audits, VPN reminders, device health check. Direct, secure, analytical, protective."
            else -> "You are a helpful AI Agent in the AI Agent Hub dashboard."
        }
    }

    /**
     * Highly rich offline simulations for every agent when API is unavailable or offline.
     */
    private fun getOfflineAgentResponse(agent: String, prompt: String): String {
        val lower = prompt.lowercase()
        return when (agent) {
            "Assistant" -> {
                if (lower.contains("weather")) {
                    "🌤️ **Local Weather Briefing**:\n- **Temp**: 72°F / 22°C (Sunny & Clear)\n- **Humidity**: 45%\n- **Wind**: 6 mph NW\n- **Forecast**: Perfect afternoon for outdoor activities!"
                } else if (lower.contains("calendar") || lower.contains("agenda") || lower.contains("today")) {
                    "📅 **Today's Agenda (Synced)**:\n1. 10:00 AM - Design Review with the Product Team\n2. 01:30 PM - Sync on AI Agent Hub Architecture\n3. 04:00 PM - Daily Stand-up & Status Sync\n\n*Would you like me to schedule a new reminder?*"
                } else if (lower.contains("translate")) {
                    "🌐 **Translation Assistant**:\n- *Input*: \"$prompt\"\n- *Spanish*: \"Hola, ¿cómo puedo ayudarte hoy?\"\n- *French*: \"Bonjour, comment puis-je vous aider aujourd'hui?\""
                } else {
                    "🎙️ **Assistant Agent**:\nI've noted: \"$prompt\". I can help you with your daily reminders, draft emails, check the simulated weather, or summarize your calendar events! What would you like to plan?"
                }
            }
            "Automation" -> {
                if (lower.contains("battery") || lower.contains("save")) {
                    "⚡ **Battery-saving Routine Activated**:\n- Brightness set to auto-adaptive\n- Background refresh limited for non-essential apps\n- Dark mode forced system-wide\n- Estimated battery life extended by **2.5 hours**!"
                } else if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth")) {
                    "📶 **Wireless Automation Engine**:\n- Location-based Wi-Fi automation trigger created.\n- Status: Wi-Fi toggled ON at Home; Bluetooth toggled ON in Car."
                } else if (lower.contains("silent") || lower.contains("bedtime") || lower.contains("sleep")) {
                    "🌙 **Bedtime Mode Set**:\n- Do Not Disturb: ON (Active 10 PM - 7 AM)\n- Grayscale display schedule: Enabled\n- All sound profiles silenced except for priority contacts."
                } else {
                    "⚡ **Automation Rule Configured**:\nTrigger successfully registered for action: \"$prompt\". Whenever this condition is met, the automation pipeline will execute immediately."
                }
            }
            "Coding" -> {
                if (lower.contains("kotlin") || lower.contains("compose") || lower.contains("android")) {
                    "💻 **Coding Assistant (Kotlin + Compose)**:\n```kotlin\n// Here is a modern Compose button template\n@Composable\nfun AnimatedHubButton(\n    text: String,\n    onClick: () -> Unit\n) {\n    Button(\n        onClick = onClick,\n        colors = ButtonDefaults.buttonColors(\n            containerColor = MaterialTheme.colorScheme.primary\n        )\n    ) {\n        Icon(Icons.Default.Bolt, contentDescription = null)\n        Spacer(Modifier.width(8.dp))\n        Text(text = text)\n    }\n}\n```\nLet me know if you need any specific API endpoints or custom layouts!"
                } else {
                    "💻 **Coding Agent (Sandbox Mode)**:\nI analyzed \"$prompt\". Here is a robust code snippet to accomplish this:\n\n```python\n# Automated Task Script\ndef execute_task(task_details):\n    print(f\"Executing: {task_details}\")\n    # Shared memory API integration\n    return {\"status\": \"success\", \"code\": 200}\n\nresult = execute_task(\"$prompt\")\n```\n*Feel free to ask me to debug or optimize this code!*"
                }
            }
            "Business" -> {
                if (lower.contains("invoice")) {
                    "📈 **Invoice Draft Generated**:\n- **Invoice #**: INV-2026-004\n- **Client**: Acme Corp\n- **Services**: Custom AI Multi-Agent Hub development\n- **Amount Due**: $4,500.00 USD\n- **Terms**: Net 15"
                } else if (lower.contains("marketing") || lower.contains("copy")) {
                    "📈 **Marketing Copy Draft**:\n*Headline*: \"Unleash Your Pixel's True Potential with AI Agent Hub\"\n*Sub-headline*: Six dedicated, offline-first autonomous agents working inside a unified local memory environment.\n*CTA*: Try AI Agent Hub today!"
                } else {
                    "📈 **Business Management Engine**:\nI am ready to draft contracts, calculate expenses, compile spreadsheets, or summarize business meetings. Let me know which template you would like to run."
                }
            }
            "Research" -> {
                "🔍 **Research Summary Report**:\n- **Topic**: \"$prompt\"\n- **Key Findings**: Based on local indexing, current trends point to hybrid local-cloud LLMs as the dominant architecture on high-end mobile chipsets.\n- **Related Sources**: [1] Android AI Dev Journal (2026), [2] DeepMind Mobile Intelligence Whitepaper."
            }
            "Security" -> {
                if (lower.contains("scan") || lower.contains("permission")) {
                    "🛡️ **Security Permission Scan Completed**:\n- **34 Apps** checked.\n- **0 Critical risks** detected.\n- **2 High permissions** found (Accessibility Service, Background Location). \n- **Recommendation**: Ensure your VPN toggles are turned on when connecting to open public networks."
                } else {
                    "🛡️ **Security & Privacy Shield**:\nScan completed for input content. Integrity verified. Always use strong password reminders and avoid clicking links containing unknown domain suffixes."
                }
            }
            else -> "Hello! I am your AI Hub Agent. Ask me anything and I will assist you immediately."
        }
    }
}

// Data models for Moshi matching the Gemini REST schema
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String? = null
)

data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

data class Candidate(
    val content: Content? = null
)
