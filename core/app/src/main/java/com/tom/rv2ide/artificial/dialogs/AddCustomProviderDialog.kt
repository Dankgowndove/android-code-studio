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

package com.tom.rv2ide.artificial.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.artificial.agents.custom.CustomProviderConfig
import com.tom.rv2ide.artificial.agents.custom.CustomProviderManager
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Dialog for adding a custom AI provider.
 * Users can configure a provider with OpenAI-compatible or Claude-compatible protocol.
 */
class AddCustomProviderDialog : DialogFragment() {

    var onProviderAdded: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_custom_provider, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<TextInputEditText>(R.id.provider_name)
        val etBaseUrl = view.findViewById<TextInputEditText>(R.id.base_url)
        val etApiKey = view.findViewById<TextInputEditText>(R.id.api_key)
        val etModels = view.findViewById<TextInputEditText>(R.id.models)
        val rgProtocol = view.findViewById<android.widget.RadioGroup>(R.id.rg_protocol)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener { dismiss() }

        view.findViewById<View>(R.id.btn_test_connection).setOnClickListener {
            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            val protocol = if (rgProtocol.checkedRadioButtonId == R.id.rb_claude) "claude" else "openai"

            if (baseUrl.isEmpty()) {
                Toast.makeText(context, R.string.ai_custom_fill_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            testConnection(baseUrl, apiKey, protocol)
        }

        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val name = etName.text.toString().trim()
            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            val models = etModels.text.toString().trim()

            if (name.isEmpty() || baseUrl.isEmpty() || models.isEmpty()) {
                Toast.makeText(context, R.string.ai_custom_fill_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val protocol = if (rgProtocol.checkedRadioButtonId == R.id.rb_claude) "claude" else "openai"

            val config = CustomProviderConfig(
                name = name,
                baseUrl = baseUrl,
                apiKey = apiKey,
                protocol = protocol,
                models = models
            )

            val savedConfig = CustomProviderManager.add(config)
            CustomProviderManager.persist(requireContext())

            Toast.makeText(context, getString(R.string.ai_custom_added, name), Toast.LENGTH_SHORT).show()
            onProviderAdded?.invoke()
            dismiss()
        }
    }

    private fun testConnection(baseUrl: String, apiKey: String, protocol: String) {
        val btnTest = view?.findViewById<View>(R.id.btn_test_connection)
        btnTest?.isEnabled = false
        btnTest?.alpha = 0.5f

        val originalText = btnTest?.let { view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_test_connection) }?.text ?: "Testing..."

        android.os.AsyncTask.execute {
            try {
                // Build a minimal request to test connectivity
                val url = when (protocol) {
                    "claude" -> {
                        val base = baseUrl.trimEnd('/')
                        if (base.endsWith("/messages")) base
                        else if (base.endsWith("/v1")) "$base/messages"
                        else "$base/v1/messages"
                    }
                    else -> {
                        val base = baseUrl.trimEnd('/')
                        if (base.endsWith("/chat/completions")) base
                        else if (base.endsWith("/v1")) "$base/chat/completions"
                        else "$base/v1/chat/completions"
                    }
                }

                val json = org.json.JSONObject().apply {
                    put("model", "test")
                    put("max_tokens", 1)
                }

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .apply {
                        when (protocol) {
                            "claude" -> {
                                addHeader("x-api-key", apiKey)
                                addHeader("anthropic-version", "2023-06-01")
                                addHeader("Content-Type", "application/json")
                                json.put("messages", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply {
                                        put("role", "user")
                                        put("content", "hi")
                                    })
                                })
                            }
                            else -> {
                                addHeader("Authorization", "Bearer $apiKey")
                                addHeader("Content-Type", "application/json")
                                json.put("messages", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply {
                                        put("role", "user")
                                        put("content", "hi")
                                    })
                                })
                            }
                        }
                    }
                    .post(json.toString().toRequestBody(okhttp3.MediaType.parse("application/json")!!))
                    .build()

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()

                activity?.runOnUiThread {
                    btnTest?.isEnabled = true
                    btnTest?.alpha = 1.0f

                    if (response.isSuccessful) {
                        Toast.makeText(context, R.string.ai_custom_test_success, Toast.LENGTH_LONG).show()
                    } else {
                        val errorBody = response.body()?.string() ?: "HTTP ${response.code()}"
                        val msg = if (response.code() == 401 || response.code() == 403) {
                            "Auth error (${response.code()}) - check API key"
                        } else if (response.code() == 404) {
                            "URL not found - check base URL"
                        } else {
                            "HTTP ${response.code()}: $errorBody".take(100)
                        }
                        Toast.makeText(context, getString(R.string.ai_custom_test_failed, msg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    btnTest?.isEnabled = true
                    btnTest?.alpha = 1.0f

                    val msg = when {
                        e is java.net.UnknownHostException -> "Cannot resolve host"
                        e is java.net.ConnectException -> "Connection refused"
                        e is java.net.SocketTimeoutException -> "Connection timeout"
                        e is java.net.SocketException -> "Socket error: ${e.localizedMessage}"
                        else -> e.localizedMessage ?: "Unknown error"
                    }
                    Toast.makeText(context, getString(R.string.ai_custom_test_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val TAG = "AddCustomProviderDialog"
    }
}
