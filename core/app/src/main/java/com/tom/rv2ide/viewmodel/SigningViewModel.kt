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

    /**
     * Checks if a build file already has signing configuration.
     */
    fun hasSigningConfig(buildFile: File): Boolean {
        return GradleConfigWriter.hasSigningConfig(buildFile)
    }

    /**
     * Removes signing configuration from the build file.
     */
    fun removeSigningConfig(buildFile: File): Boolean {
        return GradleConfigWriter.removeSigningConfig(buildFile)
    }
}
