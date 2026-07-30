/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.artificial.agents.custom

import android.content.Context
import com.tom.rv2ide.artificial.agents.AIAgent
import com.tom.rv2ide.artificial.agents.AIAgentRegistry
import com.tom.rv2ide.artificial.agents.ModificationAttempt
import com.tom.rv2ide.artificial.file.AIFileWriter
import com.tom.rv2ide.artificial.file.FileWriteResult
import com.tom.rv2ide.artificial.project.awareness.ProjectTreeResult
import com.tom.rv2ide.artificial.rules.WritingRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Custom AI provider that supports both OpenAI-compatible and Claude-compatible APIs.
 * Users can add multiple instances of this provider with different configurations.
 */
class CustomProvider(
    override val providerId: String,
    private val displayName: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val protocol: String,   // "openai" or "claude"
    private val modelList: List<String>
) : AIAgent {

    override val providerName: String get() = displayName

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val writingRules = WritingRules.Instructions()
    private var projectTreeResult: ProjectTreeResult? = null
    private var fileWriter: AIFileWriter? = null
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val modificationHistory = mutableListOf<ModificationAttempt>()
    private var currentAttemptCount = 0
    private val maxRetryAttempts = 3
    private var initialized = false

    override fun initialize(apiKey: String, context: Context) {
        fileWriter = AIFileWriter(context)
        initialized = true
    }

    override fun isInitialized(): Boolean = initialized

    override fun reinitializeWithNewModel(apiKey: String, context: Context) {
        initialize(apiKey, context)
    }

    override fun setContext(context: Context) {
        fileWriter = AIFileWriter(context)
    }

    override fun setProjectData(projectTreeResult: ProjectTreeResult) {
        this.projectTreeResult = projectTreeResult
    }

    override fun clearConversation() {
        conversationHistory.clear()
        modificationHistory.clear()
        currentAttemptCount = 0
    }

    override fun recordModification(filePath: String, oldContent: String?, newContent: String, success: Boolean) {
        modificationHistory.add(ModificationAttempt(
            timestamp = System.currentTimeMillis(),
            filePath = filePath,
            previousContent = oldContent,
            newContent = newContent,
            attemptNumber = currentAttemptCount,
            success = success
        ))
    }

    override fun undoLastModification(): Boolean {
        if (modificationHistory.isEmpty()) return false
        val lastMod = modificationHistory.lastOrNull { it.success } ?: return false
        if (lastMod.previousContent != null) {
            val result = writeFile(lastMod.filePath, lastMod.previousContent)
            if (result is FileWriteResult.Success) {
                modificationHistory.removeAt(modificationHistory.lastIndexOf(lastMod))
                return true
            }
        } else {
            try {
                File(lastMod.filePath).delete()
                modificationHistory.removeAt(modificationHistory.lastIndexOf(lastMod))
                return true
            } catch (_: Exception) { return false }
        }
        return false
    }

    override fun getModificationHistory(): List<ModificationAttempt> = modificationHistory.toList()
    override fun resetAttemptCount() { currentAttemptCount = 0 }
    override fun incrementAttemptCount() { currentAttemptCount++ }
    override fun getCurrentAttemptCount(): Int = currentAttemptCount
    override fun canRetry(): Boolean = currentAttemptCount < maxRetryAttempts

    override fun writeFile(filePath: String, content: String): FileWriteResult {
        val writer = fileWriter ?: return FileWriteResult.Error("File writer not initialized")
        return writer.writeFile(filePath, content, createBackup = true)
    }

    override suspend fun generateCode(
        prompt: String,
        context: String?,
        language: String,
        projectStructure: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = buildFullPrompt(prompt, context)
            val modelName = modelList.firstOrNull() ?: "default"

            val response = when (protocol) {
                "openai" -> callOpenAICompatibleAPI(fullPrompt, modelName)
                "claude" -> callClaudeCompatibleAPI(fullPrompt, modelName)
                else -> return@withContext Result.failure(Exception("Unknown protocol: $protocol"))
            }

            if (response.isBlank()) {
                return@withContext Result.failure(Exception("Empty response from $displayName"))
            }

            conversationHistory.add(ConversationMessage("user", prompt))
            conversationHistory.add(ConversationMessage("assistant", response))
            if (conversationHistory.size > 20) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFullPrompt(prompt: String, context: String?): String {
        return buildString {
            append("=== PROJECT STRUCTURE ===\n")
            projectTreeResult?.let {
                append(it.tree)
                append("\n\n")
            }
            if (context != null) {
                append("=== ADDITIONAL CONTEXT ===\n")
                append(context)
                append("\n\n")
            }
            append("=== USER REQUEST ===\n")
            append(prompt)
        }
    }

    private fun callOpenAICompatibleAPI(prompt: String, modelName: String): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", writingRules.useThis())
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("temperature", 0.7)
            put("stream", false)
        }

        val url = normalizeUrl(baseUrl) + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("$displayName API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: ""
        return JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun callClaudeCompatibleAPI(prompt: String, modelName: String): String {
        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 4096)
            put("system", writingRules.useThis())
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val url = normalizeUrl(baseUrl) + "/messages"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("$displayName API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: ""
        return JSONObject(responseBody)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/')
            .replace(Regex("/v1$"), "")
            .replace(Regex("/chat/completions$"), "")
            .replace(Regex("/messages$"), "")
    }

    companion object {
        fun registerWithConfig(config: CustomProviderConfig) {
            val provider = CustomProvider(
                providerId = config.id,
                displayName = config.name,
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                protocol = config.protocol,
                modelList = config.models.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            )

            AIAgentRegistry.register(config.id, object : AIAgentRegistry.AgentFactory {
                override fun create(context: Context): AIAgent {
                    provider.initialize(config.apiKey, context)
                    return provider
                }
                override fun hasValidApiKey(): Boolean = config.apiKey.isNotEmpty()
                override fun getApiKey(): String? = config.apiKey
            })
        }
    }
}

data class ConversationMessage(
    val role: String,
    val content: String
)
