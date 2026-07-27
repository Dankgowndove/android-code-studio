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
package com.tom.rv2ide.viewmodel

import androidx.lifecycle.ViewModel
import com.tom.rv2ide.utils.GradleConfigWriter
import com.tom.rv2ide.utils.KeystoreManager
import java.io.File

/**
 * ViewModel for signing configuration operations.
 *
 * @author AndroidIDE
 */
class SigningViewModel : ViewModel() {

    /**
     * Creates a new JKS keystore file.
     *
     * @param projectDir    The project root directory.
     * @param keystoreName  Desired keystore filename.
     * @param alias         Key alias.
     * @param keyPassword   Key password.
     * @param storePassword Keystore password.
     * @param validityDays  Certificate validity in days.
     * @param dname         Distinguished name string.
     * @return Result containing the keystore File on success.
     */
    fun createKeystore(
        projectDir: File,
        keystoreName: String,
        alias: String,
        keyPassword: String,
        storePassword: String,
        validityDays: Int,
        dname: String,
    ): Result<File> {
        val keystoreDir = File(projectDir, "keystore")
        return KeystoreManager.createKeystore(
            keystoreDir = keystoreDir,
            keystoreName = keystoreName,
            alias = alias,
            keyPassword = keyPassword,
            storePassword = storePassword,
            validityDays = validityDays,
            dname = dname
        )
    }

    /**
     * Injects signing configuration into the build file.
     *
     * @param buildFile     The build.gradle or build.gradle.kts file.
     * @param keystorePath  Path to the keystore file (relative or absolute).
     * @param storePassword Keystore password.
     * @param keyAlias      Key alias.
     * @param keyPassword   Key password.
     * @return true if the config was injected successfully.
     */
    fun injectSigningConfig(
        buildFile: File,
        keystorePath: String,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ): Boolean {
        return GradleConfigWriter.injectSigningConfig(
            buildFile = buildFile,
            config = GradleConfigWriter.SigningConfig(
                keystorePath = keystorePath,
                storePassword = storePassword,
                keyAlias = keyAlias,
                keyPassword = keyPassword
            )
        )
    }
}
