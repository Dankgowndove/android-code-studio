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

    companion object {
        const val TAG = "AddCustomProviderDialog"
    }
}
