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

package com.tom.rv2ide.artificial.agents

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class Agents(ctx: Context) {

  private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
  private val AGENT_KEY = "ai_agent_model_name"
  private val PROVIDER_KEY = "ai_provider_name"
  
  private val openai_models = arrayOf(
    // GPT-5.6 family (current 2026-07)
    "gpt-5.6-sol",
    "gpt-5.6-terra",
    "gpt-5.6-luna",

    // GPT-5.5 (2026-04)
    "gpt-5.5",
    "gpt-5.5-pro",
    "gpt-5.5-pro-2026-04-23",

    // GPT-5.4 family
    "gpt-5.4",
    "gpt-5.4-mini",

    // GPT-5.3 Codex
    "gpt-5.3-codex",

    // GPT-5.2 Codex (recommended for API key workflows)
    "gpt-5.2-codex",

    // GPT-5 family
    "gpt-5",
    "gpt-5-mini",
    "gpt-5-nano",
    "gpt-5-pro",
    "gpt-5-codex",

    // GPT-4.1 family
    "gpt-4.1",
    "gpt-4.1-mini",
    "gpt-4.1-nano",

    // GPT-4o
    "gpt-4o",
    "gpt-4o-mini",

    // o-series reasoning models
    "o1",
    "o3",
    "o3-mini",
    "o4-mini",

    // Image generation
    "gpt-image-2"
  )
  
  private val claude_models = arrayOf(
    // Claude 5 family (2026)
    "claude-fable-5",
    "claude-opus-5",
    "claude-sonnet-5",
    "claude-haiku-4.5",

    // Claude 4 family (still active)
    "claude-opus-4.8",
    "claude-opus-4.7",
    "claude-sonnet-4.6",
    "claude-sonnet-4.5"
  )
  
  private val gemini_models = arrayOf(
    // Gemini 3.x family (2026)
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",

    // Gemini 2.5 family (deprecated Oct 2026)
    "gemini-2.5-pro",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite"
  )
  
  private val deepseek_models = arrayOf(
    // DeepSeek V4 (current 2026)
    "deepseek-v4-flash",
    "deepseek-v4-pro",
    "deepseek-v4-flash-vision-exp"  // 多模态视觉模型 (2026-08-21)
  )
  
  private val grok_models = arrayOf(
    // Grok 4.x family (2026)
    "grok-4.6",
    "grok-4.5",
    "grok-4.3",
    "grok-build-0.1"
  )
  
  private val localllm_models = arrayOf(
    "local-model"
  )
  
  val ai_agents = openai_models + claude_models + gemini_models + deepseek_models + grok_models + localllm_models
  
  fun getModelsForProvider(providerId: String): Array<String> {
    // Check custom providers first
    val customModels = com.tom.rv2ide.artificial.agents.custom.CustomProviderManager.getModelsForProvider(providerId)
    if (customModels != null) return customModels
    return when(providerId) {
      "openai" -> openai_models
      "gemini" -> gemini_models
      "claude" -> claude_models
      "deepseek" -> deepseek_models
      "grok" -> grok_models
      "localllm" -> localllm_models
      else -> gemini_models
    }
  }
  
  fun getProviderForModel(modelName: String): String? {
    // Check custom providers first
    val customProvider = com.tom.rv2ide.artificial.agents.custom.CustomProviderManager.getProviderForModel(modelName)
    if (customProvider != null) return customProvider
    return when {
      modelName in openai_models -> "openai"
      modelName in gemini_models -> "gemini"
      modelName in claude_models -> "claude"
      modelName in deepseek_models -> "deepseek"
      modelName in grok_models -> "grok"
      modelName in localllm_models -> "localllm"
      else -> null
    }
  }
  
  fun setAgent(name: String) {
      val provider = when {
          name in openai_models -> "openai"
          name in gemini_models -> "gemini"
          name in claude_models -> "claude"
          name in deepseek_models -> "deepseek"
          name in grok_models -> "grok"
          name in localllm_models -> "localllm"
          else -> sp.getString(PROVIDER_KEY, "gemini") ?: "gemini"
      }
      
      sp.edit().putString(PROVIDER_KEY, provider).apply()
      sp.edit().putString(AGENT_KEY, name).apply()
  }
  
  fun getAgent(): String {
    val savedModel = sp.getString(AGENT_KEY, null)
    if (savedModel != null) return savedModel
    
    return when (getProvider()) {
      "openai" -> "gpt-5.6-terra"
      "gemini" -> "gemini-3.7-flash"
      "claude" -> "claude-sonnet-5"
      "deepseek" -> "deepseek-v4-flash"
      "grok" -> "grok-4.6"
      else -> "gemini-3.7-flash"
    }
  }
  
  fun setProvider(provider: String) {
    sp.edit().putString(PROVIDER_KEY, provider).apply()
  }
  
  fun getProvider(): String {
    return sp.getString(PROVIDER_KEY, "gemini") ?: "gemini"
  }
  
  fun isValidModelForProvider(modelName: String, providerId: String): Boolean {
    return modelName in getModelsForProvider(providerId)
  }
}
