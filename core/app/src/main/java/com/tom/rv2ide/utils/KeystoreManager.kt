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
     * Tries to find the keytool executable in common locations.
     */
    private fun findKeytool(): String? {
        val candidates = listOf(
            "keytool",
            "/usr/bin/keytool",
            "${Environment.PREFIX}/bin/keytool",
            "${Environment.PREFIX}/opt/openjdk/bin/keytool"
        )
        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists() && file.canExecute()) return candidate
        }
        // Try java home
        try {
            val javaHome = System.getProperty("java.home")
            if (javaHome != null) {
                val kt = File(javaHome, "bin/keytool")
                if (kt.exists() && kt.canExecute()) return kt.absolutePath
            }
        } catch (_: Exception) {}
        // Fall back to just "keytool" - it might be on PATH
        return "keytool"
    }

    /**
     * Creates a new JKS keystore with a self-signed key pair using the keytool utility.
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
                return Result.failure(IllegalStateException(
                    "Keystore file already exists: ${keystoreFile.absolutePath}"
                ))
            }

            val keytool = findKeytool() ?: return Result.failure(
                RuntimeException("keytool not found. Please ensure JDK is installed.")
            )

            val cmd = arrayOf(
                keytool, "-genkeypair", "-v",
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
                val stdOut = process.inputStream.bufferedReader().readText()
                val msg = if (error.isNotBlank()) error else stdOut
                Result.failure(RuntimeException("keytool failed (exit $exitCode): $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies that a keystore file can be opened with the given credentials.
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
