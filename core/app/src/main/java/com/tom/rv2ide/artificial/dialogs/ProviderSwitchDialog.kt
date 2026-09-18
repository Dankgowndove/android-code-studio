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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

class ProviderSwitchDialog(private val context: Context) {
    
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val AUTO_SWITCH_KEY = "auto_switch_providers"
    
    
    fun isAutoSwitchEnabled(): Boolean {
        return sp.getBoolean(AUTO_SWITCH_KEY, false)
    }
    
    fun setAutoSwitch(enabled: Boolean) {
        sp.edit().putBoolean(AUTO_SWITCH_KEY, enabled).apply()
    }
    
    fun showProviderErrorDialog(
        currentProvider: String,
        errorMessage: String,
        availableProviders: List<Pair<String, String>>, // List of (id, name)
        onProviderSelected: (String) -> Unit,
        onEnableAutoSwitch: () -> Unit
    ) {
        if (availableProviders.isEmpty()) {
            showNoProvidersAvailableDialog(errorMessage)
            return
        }
        
        val providerNames = availableProviders.map { it.second }.toTypedArray()
        
        MaterialAlertDialogBuilder(context)
            .setTitle("⚠️ Provider Error")
            .setMessage(
                "Current Provider: $currentProvider\n\n" +
                "Error: $errorMessage\n\n" +
                "Available providers: ${availableProviders.size}\n\n" +
                "Would you like to switch to another provider?"
            )
            .setPositiveButton("Switch Manually") { dialog, _ ->
                dialog.dismiss()
                showProviderSelectionDialog(availableProviders, onProviderSelected)
            }
            .setNegativeButton("Enable Auto-Switch") { dialog, _ ->
                setAutoSwitch(true)
                onEnableAutoSwitch()
                dialog.dismiss()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showProviderSelectionDialog(
        availableProviders: List<Pair<String, String>>,
        onProviderSelected: (String) -> Unit
    ) {
        val providerNames = availableProviders.map { it.second }.toTypedArray()
        val providerIds = availableProviders.map { it.first }
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Select Provider")
            .setItems(providerNames) { dialog, which ->
                onProviderSelected(providerIds[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showNoProvidersAvailableDialog(errorMessage: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("❌ No Providers Available")
            .setMessage(
                "Error: $errorMessage\n\n" +
                "Unfortunately, there are no other providers available with valid API keys.\n\n" +
                "Please:\n" +
                "1. Check your API keys\n" +
                "2. Verify account quotas\n" +
                "3. Try again later"
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    fun showAutoSwitchNotification(
        fromProvider: String,
        toProvider: String,
        reason: String
    ): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
            .setTitle("🔄 Auto-Switched Provider")
            .setMessage(
                "Switched from: $fromProvider\n" +
                "Switched to: $toProvider\n\n" +
                "Reason: $reason\n\n" +
                "Auto-switch is enabled. You can disable it in settings."
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Disable Auto-Switch") { dialog, _ ->
                setAutoSwitch(false)
                dialog.dismiss()
            }
    }
}
