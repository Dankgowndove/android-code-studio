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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages custom AI providers: CRUD operations and persistence via SharedPreferences.
 * Each custom provider is stored as a JSON object in a JSON array under the key "custom_ai_providers".
 */
object CustomProviderManager {

    private const val PREFS_KEY = "custom_ai_providers"
    private const val ID_PREFIX = "custom_"

    private val providers = mutableMapOf<String, CustomProviderConfig>()

    fun getAll(): List<CustomProviderConfig> = providers.values.toList()

    fun getModelsForProvider(providerId: String): Array<String>? {
        val config = providers[providerId] ?: return null
        return config.models.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    }

    fun getProviderForModel(modelName: String): String? {
        return providers.entries.firstOrNull { (_, config) ->
            config.models.split(",").map { it.trim() }.contains(modelName)
        }?.key
    }

    fun getProviderName(providerId: String): String? {
        return providers[providerId]?.name
    }

    fun add(config: CustomProviderConfig): CustomProviderConfig {
        val id = if (config.id.isNotEmpty()) config.id else generateId()
        val finalConfig = config.copy(id = id)
        providers[id] = finalConfig
        CustomProvider.registerWithConfig(finalConfig)
        return finalConfig
    }

    fun remove(id: String) {
        providers.remove(id)
    }

    fun save(config: CustomProviderConfig) {
        providers[config.id] = config
        CustomProvider.registerWithConfig(config)
    }

    fun loadAndRegister(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val json = sp.getString(PREFS_KEY, null) ?: return

        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val config = CustomProviderConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    baseUrl = obj.getString("baseUrl"),
                    apiKey = obj.getString("apiKey"),
                    protocol = obj.getString("protocol"),
                    models = obj.getString("models")
                )
                providers[config.id] = config
                CustomProvider.registerWithConfig(config)
            }
        } catch (_: Exception) { }
    }

    fun persist(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val arr = JSONArray()
        for ((_, config) in providers) {
            arr.put(JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("baseUrl", config.baseUrl)
                put("apiKey", config.apiKey)
                put("protocol", config.protocol)
                put("models", config.models)
            })
        }
        sp.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    private fun generateId(): String {
        return ID_PREFIX + System.currentTimeMillis().toString(36)
    }
}

data class CustomProviderConfig(
    val id: String = "",
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val protocol: String,  // "openai" or "claude"
    val models: String     // comma-separated
)
