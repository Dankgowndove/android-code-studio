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
     */
    data class SigningConfig(
        val keystorePath: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
        val configName: String = "release"
    )

    /**
     * Checks if signing configuration already exists in the build file.
     */
    fun hasSigningConfig(buildFile: File): Boolean {
        if (!buildFile.exists()) return false
        val content = buildFile.readText()
        return content.contains("signingConfigs") || content.contains("signingConfig")
    }

    /**
     * Removes signing configuration from the build file.
     */
    fun removeSigningConfig(buildFile: File): Boolean {
        if (!buildFile.exists()) return false
        return try {
            val content = buildFile.readText()
            // Remove signingConfigs block (handles both Kotlin DSL and Groovy DSL)
            val pattern = Regex(
                """(?m)^\s*signingConfigs\s*\{.*?(?=^\s*(?:\w+\s*\{|$))""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            var newContent = pattern.replace(content, "")

            // Remove signingConfig reference from buildTypes
            val signRefPattern = Regex(
                """(?m)^\s*signingConfig\s*[= ]\s*signingConfigs[^}\n]*\n?"""
            )
            newContent = signRefPattern.replace(newContent, "")

            // Remove keystore properties loading block (Kotlin DSL)
            val propsPattern = Regex(
                """(?m)^\s*val\s+keystoreProperties\b.*?(?=^\s*(?:\w+\s*\{|$))""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            newContent = propsPattern.replace(newContent, "")

            if (newContent != content) {
                buildFile.writeText(newContent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Injects signing configuration into the build file.
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
            // Backup before modifying
            val backupFile = File(buildFile.parentFile, "${buildFile.name}.bak")
            buildFile.copyTo(backupFile, overwrite = true)
            buildFile.writeText(newContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun injectKotlinDsl(content: String, config: SigningConfig): String {
        var newContent = content

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
