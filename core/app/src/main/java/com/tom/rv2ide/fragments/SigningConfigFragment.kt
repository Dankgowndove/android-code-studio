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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.projects.IProjectManager
import com.tom.rv2ide.viewmodel.SigningViewModel
import java.io.File

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[SigningViewModel::class.java]

        optionsView = view.findViewById(R.id.options_view)
        formView = view.findViewById(R.id.form_view)

        // Options buttons
        view.findViewById<Button>(R.id.btn_create_new).setOnClickListener {
            showForm()
        }

        view.findViewById<Button>(R.id.btn_import_existing).setOnClickListener {
            showImportKeystoreDialog()
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
            createKeystore()
        }

        view.findViewById<Button>(R.id.btn_form_cancel).setOnClickListener {
            showOptions()
        }
    }

    private fun showOptions() {
        optionsView.visibility = View.VISIBLE
        formView.visibility = View.GONE
    }

    private fun showForm() {
        optionsView.visibility = View.GONE
        formView.visibility = View.VISIBLE
    }

    private fun showImportKeystoreDialog() {
        // TODO: Implement file picker for importing existing keystore
        Toast.makeText(context, R.string.import_keystore_todo, Toast.LENGTH_SHORT).show()
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

        val dnameParts = mutableListOf<String>()
        dnameParts.add("CN=$cn")
        val o = dnameO.text.toString().trim()
        val ou = dnameOu.text.toString().trim()
        val l = dnameL.text.toString().trim()
        val st = dnameSt.text.toString().trim()
        val c = dnameC.text.toString().trim()
        if (ou.isNotEmpty()) dnameParts.add("OU=$ou")
        if (o.isNotEmpty()) dnameParts.add("O=$o")
        if (l.isNotEmpty()) dnameParts.add("L=$l")
        if (st.isNotEmpty()) dnameParts.add("ST=$st")
        if (c.isNotEmpty()) dnameParts.add("C=$c")
        val dname = dnameParts.joinToString(", ")

        val projectDir = getProjectDir()
        if (projectDir == null) {
            Toast.makeText(context, R.string.signing_no_project, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.createKeystore(
            projectDir = projectDir,
            keystoreName = name,
            alias = alias,
            keyPassword = keyPass,
            storePassword = storePass,
            validityDays = validityDays,
            dname = dname
        ).onSuccess { keystoreFile ->
            Toast.makeText(context, getString(R.string.signing_create_success), Toast.LENGTH_SHORT).show()
            injectConfig(keystoreFile, storePass, alias, keyPass)
        }.onFailure { error ->
            Toast.makeText(context, getString(R.string.signing_error, error.message), Toast.LENGTH_LONG).show()
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

        val success = viewModel.injectSigningConfig(buildFile, relativePath, storePass, keyAlias, keyPass)
        if (success) {
            Toast.makeText(context, getString(R.string.signing_config_injected), Toast.LENGTH_LONG).show()
            showOptions()
        } else {
            Toast.makeText(context, R.string.signing_inject_failed, Toast.LENGTH_SHORT).show()
        }
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
        val candidates = listOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle"),
            File(projectDir, "build.gradle.kts"),
            File(projectDir, "build.gradle")
        )
        return candidates.firstOrNull { it.exists() }
    }
}
