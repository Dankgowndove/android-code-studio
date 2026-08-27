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

class SigningViewModel : ViewModel() {

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

    fun injectSigningConfig(
        buildFile: File,
        keystorePath: String,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
        usePropertiesFile: Boolean = false,
        projectDir: File? = null
    ): Boolean {
        // If using properties file mode, create keystore.properties first
        if (usePropertiesFile && projectDir != null) {
            GradleConfigWriter.createKeystoreProperties(
                projectDir = projectDir,
                keystorePath = keystorePath,
                storePassword = storePassword,
                keyAlias = keyAlias,
                keyPassword = keyPassword
            )
        }
        return GradleConfigWriter.injectSigningConfig(
            buildFile = buildFile,
            config = GradleConfigWriter.SigningConfig(
                keystorePath = keystorePath,
                storePassword = storePassword,
                keyAlias = keyAlias,
                keyPassword = keyPassword
            ),
            usePropertiesFile = usePropertiesFile
        )
    }

    fun hasSigningConfig(buildFile: File): Boolean {
        return GradleConfigWriter.hasSigningConfig(buildFile)
    }

    fun removeSigningConfig(buildFile: File): Boolean {
        return GradleConfigWriter.removeSigningConfig(buildFile)
    }
}
