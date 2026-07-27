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

/**
 * Injects signing configuration into the project's build.gradle or build.gradle.kts file.
 *
 * @author AndroidIDE
 */
object GradleConfigWriter {

    /**
     * Represents a signing configuration to inject into the build file.
     *
     * @param keystorePath  Absolute or relative path to the keystore file.
     * @param storePassword The keystore password.
     * @param keyAlias      The alias of the signing key.
     * @param keyPassword   The password for the signing key.
     * @param configName    The name for this signing config block (default "release").
     */
    data class SigningConfig(
        val keystorePath: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
        val configName: String = "release"
    )

    /**
     * Injects signing configuration into the build file.
     *
     * @param buildFile The build.gradle or build.gradle.kts file.
     * @param config    The signing configuration to inject.
     * @return true if successful.
     */
    fun injectSigningConfig(buildFile: File, config: SigningConfig): Boolean {
        val content = buildFile.readText()
        val isKts = buildFile.name.endsWith(".kts")

        // Skip if already configured
        if (content.contains("signingConfigs") && content.contains("create(\"${config.configName}\")")) {
            return true
        }
        if (content.contains("signingConfigs") && content.contains("\"${config.configName}\"")) {
            return true
        }

        val newContent = if (isKts) {
            injectKotlinDsl(content, config)
        } else {
            injectGroovyDsl(content, config)
        }

        if (newContent == content) {
            return false
        }

        return try {
            buildFile.writeText(newContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun injectKotlinDsl(content: String, config: SigningConfig): String {
        var newContent = content

        // Add signingConfigs block if not present
        if (!newContent.contains("signingConfigs")) {
            val signingBlock = """
                |    signingConfigs {
                |        create("${config.configName}") {
                |            storeFile = file("${config.keystorePath}")
                |            storePassword = "${config.storePassword}"
                |            keyAlias = "${config.keyAlias}"
                |            keyPassword = "${config.keyPassword}"
                |        }
                |    }
            """.trimMargin()

            newContent = if (newContent.contains("buildTypes")) {
                newContent.replaceFirst("buildTypes", "$signingBlock\n\n    buildTypes")
            } else if (newContent.contains("android {")) {
                newContent.replaceFirst("(android\\s*\\{)", "$1\n$signingBlock")
            } else {
                newContent
            }
        }

        // Add signingConfig to release buildType
        if (!newContent.contains("signingConfig = signingConfigs")) {
            val releasePattern = Regex("""(getByName\("release"\)|create\("release"\)|release\s*\{)\s*\{""")
            newContent = releasePattern.replace(newContent) { match ->
                "${match.value}\n            signingConfig = signingConfigs.getByName(\"${config.configName}\")"
            }
        }

        return newContent
    }

    private fun injectGroovyDsl(content: String, config: SigningConfig): String {
        var newContent = content

        if (!newContent.contains("signingConfigs")) {
            val signingBlock = """
                |    signingConfigs {
                |        ${config.configName} {
                |            storeFile file('${config.keystorePath}')
                |            storePassword '${config.storePassword}'
                |            keyAlias '${config.keyAlias}'
                |            keyPassword '${config.keyPassword}'
                |        }
                |    }
            """.trimMargin()

            newContent = if (newContent.contains("buildTypes")) {
                newContent.replaceFirst("buildTypes", "$signingBlock\n\n    buildTypes")
            } else if (newContent.contains("android {")) {
                newContent.replaceFirst("(android\\s*\\{)", "$1\n$signingBlock")
            } else {
                newContent
            }
        }

        if (!newContent.contains("signingConfigs.${config.configName}")) {
            val releasePattern = Regex("""release\s*\{""")
            newContent = releasePattern.replace(newContent) { match ->
                "${match.value}\n            signingConfig signingConfigs.${config.configName}"
            }
        }

        return newContent
    }
}
