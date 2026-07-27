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

package com.tom.rv2ide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.tom.rv2ide.R
import com.tom.rv2ide.projects.IProjectManager
import com.tom.rv2ide.viewmodel.SigningViewModel
import java.io.File

/**
 * Bottom sheet dialog for managing signing configurations.
 * Allows creating new keystores or importing existing ones.
 *
 * @author AndroidIDE
 */
class SigningConfigBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: SigningViewModel

    private lateinit var tvCurrentConfig: TextView
    private lateinit var btnCreateNew: Button
    private lateinit var btnImportExisting: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SigningViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_signing_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentConfig = view.findViewById(R.id.tv_current_config)
        btnCreateNew = view.findViewById(R.id.btn_create_new)
        btnImportExisting = view.findViewById(R.id.btn_import_existing)
        btnCancel = view.findViewById(R.id.btn_cancel)

        btnCreateNew.setOnClickListener {
            dismiss()
            showCreateKeystoreDialog()
        }

        btnImportExisting.setOnClickListener {
            dismiss()
            showImportKeystoreDialog()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun showCreateKeystoreDialog() {
        try {
            val dialog = CreateKeystoreDialog { keystoreFile, storePass, keyAlias, keyPass ->
                injectConfig(keystoreFile, storePass, keyAlias, keyPass)
            }
            dialog.show(
                (requireActivity() as androidx.fragment.app.FragmentActivity).supportFragmentManager,
                "create_keystore"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImportKeystoreDialog() {
        // TODO: Implement file picker for importing existing keystore
        Toast.makeText(context, "Import keystore - coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun getProjectDir(): File? {
        return try {
            val path = IProjectManager.getInstance().projectDirPath
            if (path.isNullOrEmpty()) null else File(path)
        } catch (e: Exception) {
            null
        }
    }

    private fun injectConfig(keystoreFile: File, storePass: String, keyAlias: String, keyPass: String) {
        try {
            val projectDir = getProjectDir()
            val buildFile = findBuildFile(projectDir)
            if (buildFile == null) {
                Toast.makeText(context, "Could not find build file", Toast.LENGTH_SHORT).show()
                return
            }

            val relativePath = buildFile.parentFile?.toURI()?.relativize(keystoreFile.toURI())?.path
                ?: keystoreFile.absolutePath

            val success = viewModel.injectSigningConfig(buildFile, relativePath, storePass, keyAlias, keyPass)
            if (success) {
                Toast.makeText(context, getString(R.string.signing_config_injected), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to inject signing config", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findBuildFile(projectDir: File?): File? {
        if (projectDir == null) return null
        val ktsFile = File(projectDir, "build.gradle.kts")
        if (ktsFile.exists()) return ktsFile
        val gradleFile = File(projectDir, "build.gradle")
        if (gradleFile.exists()) return gradleFile
        // Try app subdirectory
        val appKts = File(projectDir, "app/build.gradle.kts")
        if (appKts.exists()) return appKts
        val appGradle = File(projectDir, "app/build.gradle")
        if (appGradle.exists()) return appGradle
        return null
    }
}

/**
 * Bottom sheet dialog for creating a new keystore.
 */
class CreateKeystoreDialog(
    private val onCreated: (keystoreFile: File, storePassword: String, keyAlias: String, keyPassword: String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var viewModel: SigningViewModel

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SigningViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_keystore, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        view.findViewById<Button>(R.id.btn_cancel)?.setOnClickListener {
            dismiss()
        }

        view.findViewById<Button>(R.id.btn_create)?.setOnClickListener {
            createKeystore()
        }
    }

    private fun createKeystore() {
        val name = keystoreName.text.toString().trim()
        val alias = keystoreAlias.text.toString().trim()
        val storePass = keystorePassword.text.toString()
        val keyPass = keyPassword.text.toString()
        val validityDays = validity.text.toString().toIntOrNull() ?: 36500

        if (name.isEmpty() || alias.isEmpty() || storePass.isEmpty() || keyPass.isEmpty()) {
            Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Build DName string
        val cn = dnameCn.text.toString().trim()
        val o = dnameO.text.toString().trim()
        val ou = dnameOu.text.toString().trim()
        val l = dnameL.text.toString().trim()
        val st = dnameSt.text.toString().trim()
        val c = dnameC.text.toString().trim()

        if (cn.isEmpty()) {
            Toast.makeText(context, "Common Name (CN) is required", Toast.LENGTH_SHORT).show()
            return
        }

        val dnameParts = mutableListOf<String>()
        dnameParts.add("CN=$cn")
        if (ou.isNotEmpty()) dnameParts.add("OU=$ou")
        if (o.isNotEmpty()) dnameParts.add("O=$o")
        if (l.isNotEmpty()) dnameParts.add("L=$l")
        if (st.isNotEmpty()) dnameParts.add("ST=$st")
        if (c.isNotEmpty()) dnameParts.add("C=$c")
        val dname = dnameParts.joinToString(", ")

        try {
            var projectDir: File? = null
            try {
                val path = IProjectManager.getInstance().projectDirPath
                if (!path.isNullOrEmpty()) {
                    projectDir = File(path)
                }
            } catch (_: Exception) {}
            
            if (projectDir == null) {
                Toast.makeText(context, "Could not determine project directory", Toast.LENGTH_SHORT).show()
                return
            }

            val result = viewModel.createKeystore(
                projectDir = projectDir,
                keystoreName = name,
                alias = alias,
                keyPassword = keyPass,
                storePassword = storePass,
                validityDays = validityDays,
                dname = dname
            )

            result.onSuccess { keystoreFile ->
                Toast.makeText(context, getString(R.string.signing_create_success), Toast.LENGTH_SHORT).show()
                dismiss()
                onCreated(keystoreFile, storePass, alias, keyPass)
            }.onFailure { error ->
                Toast.makeText(context, getString(R.string.signing_error, error.message), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.signing_error, e.message), Toast.LENGTH_LONG).show()
        }
    }
}
