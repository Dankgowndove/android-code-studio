/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.fragments

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.projects.IProjectManager
import com.tom.rv2ide.tasks.TaskExecutor
import com.tom.rv2ide.viewmodel.SigningViewModel
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

/**
 * Fragment for managing app signing configuration.
 * Serves as a tab in the EditorBottomSheet.
 *
 * Allows creating new keystore files using keytool,
 * importing existing keystores, and automatically
 * injecting signing config into build.gradle(.kts).
 *
 * @author AndroidIDE
 */
class SigningConfigFragment : Fragment(R.layout.fragment_signing_config) {

    private lateinit var viewModel: SigningViewModel

    private lateinit var optionsView: LinearLayout
    private lateinit var formView: LinearLayout
    private lateinit var statusView: LinearLayout

    // Status view
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDetails: TextView
    private lateinit var btnRemoveConfig: MaterialButton
    private lateinit var rgInjectMode: RadioGroup

    // Form fields
    private lateinit var keystoreName: TextInputEditText
    private lateinit var keystoreAlias: TextInputEditText
    private lateinit var keystorePassword: TextInputEditText
    private lateinit var keyPassword: TextInputEditText
    private lateinit var validity: TextInputEditText
    private lateinit var dnameCn: TextInputEditText
    private lateinit var dnameO: TextInputEditText
    private lateinit var dnameOu: TextInputEditText
    private lateinit var dnameL: TextInputEditText
    private lateinit var dnameSt: TextInputEditText
    private lateinit var dnameC: TextInputEditText

    // Form mode: create or import
    private var isImportMode = false
    private var importSourceUri: Uri? = null

    private val importKeystoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleImportUri(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[SigningViewModel::class.java]

        optionsView = view.findViewById(R.id.options_view)
        formView = view.findViewById(R.id.form_view)
        statusView = view.findViewById(R.id.status_view)

        tvStatusTitle = view.findViewById(R.id.tv_status_title)
        tvStatusDetails = view.findViewById(R.id.tv_status_details)
        btnRemoveConfig = view.findViewById(R.id.btn_remove_config)
        rgInjectMode = view.findViewById(R.id.rg_inject_mode)

        // Options buttons
        view.findViewById<Button>(R.id.btn_create_new).setOnClickListener {
            isImportMode = false
            importSourceUri = null
            formView.findViewById<TextView>(R.id.form_title).apply {
                text = getString(R.string.create_new_keystore)
            }
            setFormMode(false)
            showForm()
        }

        view.findViewById<Button>(R.id.btn_import_existing).setOnClickListener {
            openFilePicker()
        }

        btnRemoveConfig.setOnClickListener {
            removeExistingConfig()
        }

        // Form fields
        keystoreName = view.findViewById(R.id.keystore_name)
        keystoreAlias = view.findViewById(R.id.keystore_alias)
        keystorePassword = view.findViewById(R.id.keystore_password)
        keyPassword = view.findViewById(R.id.key_password)
        validity = view.findViewById(R.id.validity)
        dnameCn = view.findViewById(R.id.dname_cn)
        dnameO = view.findViewById(R.id.dname_o)
        dnameOu = view.findViewById(R.id.dname_ou)
        dnameL = view.findViewById(R.id.dname_l)
        dnameSt = view.findViewById(R.id.dname_st)
        dnameC = view.findViewById(R.id.dname_c)

        view.findViewById<Button>(R.id.btn_create).setOnClickListener {
            if (isImportMode) {
                importKeystore()
            } else {
                createKeystore()
            }
        }

        view.findViewById<Button>(R.id.btn_form_cancel).setOnClickListener {
            showOptions()
        }

        // Check existing config
        refreshStatus()
    }

    private fun refreshStatus() {
        val buildFile = findBuildFile(getProjectDir())
        val hasConfig = buildFile?.let { viewModel.hasSigningConfig(it) } ?: false

        if (hasConfig) {
            optionsView.visibility = View.GONE
            statusView.visibility = View.VISIBLE
            tvStatusTitle.text = getString(R.string.signing_config_exists)
            tvStatusDetails.text = getString(R.string.signing_config_active)
            btnRemoveConfig.isVisible = true
        } else {
            optionsView.visibility = View.VISIBLE
            statusView.visibility = View.GONE
        }
    }

    private fun removeExistingConfig() {
        val buildFile = findBuildFile(getProjectDir()) ?: return
        if (viewModel.removeSigningConfig(buildFile)) {
            Toast.makeText(context, R.string.signing_config_removed, Toast.LENGTH_SHORT).show()
            refreshStatus()
        } else {
            Toast.makeText(context, R.string.signing_remove_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFilePicker() {
        try {
            importKeystoreLauncher.launch(arrayOf(
                "application/octet-stream",
                "application/x-java-keystore",
                "*/*"
            ))
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.signing_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImportUri(uri: Uri) {
        importSourceUri = uri
        isImportMode = true

        // Try to read the keystore alias if possible
        var aliasHint = ""
        try {
            context?.contentResolver?.openInputStream(uri)?.use { input ->
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                ks.load(input, null)
                if (ks.aliases().hasMoreElements()) {
                    aliasHint = ks.aliases().nextElement()
                }
            }
        } catch (_: Exception) {
            // KeyStore is password protected, can't read aliases without password
            aliasHint = getString(R.string.keystore_unknown_alias)
        }

        formView.findViewById<TextView>(R.id.form_title).apply {
            text = getString(R.string.import_keystore)
        }

        // Pre-fill form for import mode
        keystoreName.setText(getKeystoreNameFromUri(uri))
        keystoreAlias.setText(aliasHint)
        keystorePassword.setText("")
        keyPassword.setText("")

        // Update form to import mode
        setFormMode(true)

        showForm()
    }

    /**
     * Toggles the form between "create" and "import" modes.
     * In import mode the DN / validity fields are unnecessary so they are hidden,
     * and the primary button text changes accordingly.
     */
    private fun setFormMode(importMode: Boolean) {
        listOfNotNull(
            view?.findViewById<View>(R.id.dn_section_header),
            (dnameCn.parent.parent as? View),
            (dnameO.parent.parent as? View),
            (dnameOu.parent.parent as? View),
            (dnameL.parent.parent as? View),
            (dnameSt.parent.parent as? View),
            (dnameC.parent.parent as? View),
            (validity.parent.parent as? View)
        ).forEach { it.visibility = if (importMode) View.GONE else View.VISIBLE }

        isImportMode = importMode
        view?.findViewById<Button>(R.id.btn_create)?.setText(
            if (importMode) getString(R.string.signing_btn_import)
            else getString(R.string.signing_btn_create)
        )
    }

    private fun getKeystoreNameFromUri(uri: Uri): String {
        val displayName = uri.lastPathSegment ?: "imported.keystore"
        return if (displayName.contains(".")) displayName
        else "$displayName.keystore"
    }

    private fun importKeystore() {
        val uri = importSourceUri ?: run {
            Toast.makeText(context, R.string.signing_no_file_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val alias = keystoreAlias.text.toString().trim()
        val storePass = keystorePassword.text.toString()
        val keyPass = keyPassword.text.toString()
        val targetName = keystoreName.text.toString().trim()

        if (alias.isEmpty() || storePass.isEmpty() || keyPass.isEmpty()) {
            Toast.makeText(context, R.string.signing_fill_required, Toast.LENGTH_SHORT).show()
            return
        }

        val projectDir = getProjectDir() ?: run {
            Toast.makeText(context, R.string.signing_no_project, Toast.LENGTH_SHORT).show()
            return
        }

        val keystoreDir = File(projectDir, "keystore").apply { mkdirs() }
        val targetFile = File(keystoreDir, targetName.ifEmpty { "imported.keystore" })

        TaskExecutor.executeAsyncProvideError({
            try {
                // Copy imported keystore to project directory
                context?.contentResolver?.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify the keystore is valid
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                FileInputStream(targetFile).use { ks.load(it, storePass.toCharArray()) }
                if (ks.getKey(alias, keyPass.toCharArray()) == null) {
                    throw IllegalArgumentException("Invalid alias or key password")
                }

                targetFile
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                throw e
            }
        }) { result: File?, error: Throwable? ->
            if (error != null) {
                Toast.makeText(context, getString(R.string.signing_error, error.message), Toast.LENGTH_LONG).show()
                return@executeAsyncProvideError
            }
            if (result != null) {
                Toast.makeText(context, R.string.signing_import_success, Toast.LENGTH_SHORT).show()
                injectConfig(result, storePass, alias, keyPass)
            }
        }
    }

    private fun createKeystore() {
        val name = keystoreName.text.toString().trim()
        val alias = keystoreAlias.text.toString().trim()
        val storePass = keystorePassword.text.toString()
        val keyPass = keyPassword.text.toString()
        val validityDays = validity.text.toString().toIntOrNull() ?: 36500

        if (name.isEmpty() || alias.isEmpty() || storePass.isEmpty() || keyPass.isEmpty()) {
            Toast.makeText(context, R.string.signing_fill_required, Toast.LENGTH_SHORT).show()
            return
        }

        val cn = dnameCn.text.toString().trim()
        if (cn.isEmpty()) {
            Toast.makeText(context, R.string.signing_cn_required, Toast.LENGTH_SHORT).show()
            return
        }

        val dnameParts = mutableListOf("CN=$cn")
        dnameO.text.toString().trim().let { if (it.isNotEmpty()) dnameParts.add("O=$it") }
        dnameOu.text.toString().trim().let { if (it.isNotEmpty()) dnameParts.add("OU=$it") }
        dnameL.text.toString().trim().let { if (it.isNotEmpty()) dnameParts.add("L=$it") }
        dnameSt.text.toString().trim().let { if (it.isNotEmpty()) dnameParts.add("ST=$it") }
        dnameC.text.toString().trim().let { if (it.isNotEmpty()) dnameParts.add("C=$it") }
        val dname = dnameParts.joinToString(", ")

        val projectDir = getProjectDir() ?: run {
            Toast.makeText(context, R.string.signing_no_project, Toast.LENGTH_SHORT).show()
            return
        }

        TaskExecutor.executeAsyncProvideError({
            viewModel.createKeystore(
                projectDir = projectDir,
                keystoreName = name,
                alias = alias,
                keyPassword = keyPass,
                storePassword = storePass,
                validityDays = validityDays,
                dname = dname
            )
        }) { result: Result<File>?, _: Throwable? ->
            if (result == null) {
                Toast.makeText(context, getString(R.string.signing_error, "Unknown error"), Toast.LENGTH_LONG).show()
                return@executeAsyncProvideError
            }
            result.onSuccess { keystoreFile ->
                Toast.makeText(context, R.string.signing_create_success, Toast.LENGTH_SHORT).show()
                injectConfig(keystoreFile, storePass, alias, keyPass)
            }.onFailure { error ->
                Toast.makeText(context, getString(R.string.signing_error, error.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun injectConfig(keystoreFile: File, storePass: String, keyAlias: String, keyPass: String) {
        val projectDir = getProjectDir()
        val buildFile = findBuildFile(projectDir)
        if (buildFile == null) {
            Toast.makeText(context, R.string.signing_no_build_file, Toast.LENGTH_SHORT).show()
            return
        }

        val relativePath = try {
            buildFile.parentFile?.toURI()?.relativize(keystoreFile.toURI())?.path
                ?: keystoreFile.absolutePath
        } catch (e: Exception) {
            keystoreFile.absolutePath
        }

        val useProps = rgInjectMode.checkedRadioButtonId == R.id.rb_properties
        val success = viewModel.injectSigningConfig(
            buildFile = buildFile,
            keystorePath = relativePath,
            storePassword = storePass,
            keyAlias = keyAlias,
            keyPassword = keyPass,
            usePropertiesFile = useProps,
            projectDir = projectDir
        )
        if (success) {
            Toast.makeText(context, getString(R.string.signing_config_injected), Toast.LENGTH_LONG).show()
            showOptions()
            refreshStatus()
        } else {
            Toast.makeText(context, R.string.signing_inject_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOptions() {
        optionsView.visibility = View.VISIBLE
        formView.visibility = View.GONE
        statusView.visibility = View.GONE
        refreshStatus()
    }

    private fun showForm() {
        optionsView.visibility = View.GONE
        formView.visibility = View.VISIBLE
        statusView.visibility = View.GONE
    }

    private fun getProjectDir(): File? {
        return try {
            val path = IProjectManager.getInstance().projectDirPath
            if (path.isNullOrEmpty()) null else File(path)
        } catch (e: Exception) {
            null
        }
    }

    private fun findBuildFile(projectDir: File?): File? {
        if (projectDir == null) return null
        return listOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle"),
            File(projectDir, "build.gradle.kts"),
            File(projectDir, "build.gradle")
        ).firstOrNull { it.exists() }
    }
}
