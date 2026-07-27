/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.tom.rv2ide.utils

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

/**
 * Manages Java keystore operations for Android app signing.
 * Uses the keytool command-line tool available in the Termux/JDK environment.
 *
 * @author AndroidIDE
 */
object KeystoreManager {

    /**
     * Creates a new JKS keystore with a self-signed key pair using the keytool utility.
     *
     * @param keystoreDir  Directory where the keystore file will be created.
     * @param keystoreName Name of the keystore file (e.g. "release.keystore").
     * @param alias        Key alias within the keystore.
     * @param keyPassword  Password for the private key.
     * @param storePassword Password for the keystore itself.
     * @param validityDays Number of days the certificate is valid (default 36500 ≈ 100 years).
     * @param dname        Distinguished name string (e.g. "CN=xxx, OU=xxx, O=xxx, L=xxx, ST=xxx, C=xxx").
     * @return Result containing the created keystore file on success, or the exception on failure.
     */
    fun createKeystore(
        keystoreDir: File,
        keystoreName: String,
        alias: String,
        keyPassword: String,
        storePassword: String,
        validityDays: Int = 36500,
        dname: String,
    ): Result<File> {
        return try {
            keystoreDir.mkdirs()
            val keystoreFile = File(keystoreDir, keystoreName)
            if (keystoreFile.exists()) {
                return Result.failure(IllegalStateException("Keystore file already exists: ${keystoreFile.absolutePath}"))
            }

            val cmd = arrayOf(
                "keytool", "-genkeypair", "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", validityDays.toString(),
                "-storepass", storePassword,
                "-keypass", keyPassword,
                "-dname", dname,
                "-storetype", "JKS"
            )

            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(keystoreFile)
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Result.failure(RuntimeException("keytool failed with exit code $exitCode: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies that a keystore file can be opened with the given credentials.
     *
     * @param keystoreFile  The keystore file to verify.
     * @param storePassword The keystore password.
     * @param keyAlias      The alias of the key to verify.
     * @param keyPassword   The password for the key.
     * @return true if the keystore is valid and the key can be retrieved.
     */
    fun verifyKeystore(
        keystoreFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(keystoreFile).use { input ->
                keyStore.load(input, storePassword.toCharArray())
            }
            keyStore.getKey(keyAlias, keyPassword.toCharArray()) != null
        } catch (e: Exception) {
            false
        }
    }
}
