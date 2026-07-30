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
 * Injects signing configuration into build.gradle or build.gradle.kts.
 *
 * Uses bracket-counting (not fragile regex) to reliably locate
 * the `android {}` → `buildTypes {}` → `release {}` nesting.
 *
 * Supports two injection modes:
 * - Direct: credentials written inline in the build file.
 * - Properties: credentials stored in keystore.properties, build file only references variables.
 */
object GradleConfigWriter {

    data class SigningConfig(
        val keystorePath: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
        val configName: String = "release"
    )

    fun hasSigningConfig(buildFile: File): Boolean {
        if (!buildFile.exists()) return false
        val content = buildFile.readText()
        return content.contains("signingConfigs") || content.contains("signingConfig")
    }

    fun removeSigningConfig(buildFile: File): Boolean {
        if (!buildFile.exists()) return false
        return try {
            val content = buildFile.readText()
            val pattern = Regex(
                """(?m)^\s*signingConfigs\s*\{.*?(?=^\s*(?:\w+\s*\{|$))""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            var newContent = pattern.replace(content, "")
            val signRefPattern = Regex("""(?m)^\s*signingConfig\s*[= ]\s*signingConfigs[^}\n]*\n?""")
            newContent = signRefPattern.replace(newContent, "")
            val propsPattern = Regex(
                """(?m)^\s*val\s+keystoreProperties\b.*?(?=^\s*(?:\w+\s*\{|$))""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            newContent = propsPattern.replace(newContent, "")
            if (newContent != content) {
                buildFile.writeText(newContent)
                true
            } else false
        } catch (_: Exception) { false }
    }

    fun injectSigningConfig(
        buildFile: File,
        config: SigningConfig,
        usePropertiesFile: Boolean = false
    ): Boolean {
        val content = buildFile.readText()
        val isKts = buildFile.name.endsWith(".kts")

        // Already configured?
        if (content.contains("signingConfigs") && (
            content.contains("create(\"${config.configName}\")") ||
            content.contains("\"${config.configName}\"")
        )) return true

        val newContent = buildInjection(content, config, isKts, usePropertiesFile)
        if (newContent == content) return false

        return try {
            val backupFile = File(buildFile.parentFile, "${buildFile.name}.bak")
            if (!backupFile.exists()) buildFile.copyTo(backupFile, overwrite = true)
            buildFile.writeText(newContent)
            true
        } catch (_: Exception) { false }
    }

    fun createKeystoreProperties(
        projectDir: File,
        keystorePath: String,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ): File {
        val propsFile = File(projectDir, "keystore.properties")
        propsFile.writeText(buildString {
            appendLine("storeFile=$keystorePath")
            appendLine("storePassword=$storePassword")
            appendLine("keyAlias=$keyAlias")
            appendLine("keyPassword=$keyPassword")
        })
        addToGitignore(projectDir, "keystore.properties")
        return propsFile
    }

    private fun addToGitignore(projectDir: File, entry: String) {
        val gitignore = File(projectDir, ".gitignore")
        if (!gitignore.exists()) return
        val content = gitignore.readText()
        if (!content.contains(entry)) {
            gitignore.appendText("\n$entry\n")
        }
    }

    // ====================================================================
    //  Bracket-counting engine
    // ====================================================================

    /** Finds matching closing brace for the opening brace at [openPos]. Returns index after '}'. */
    private fun findClosingBrace(text: String, openPos: Int): Int {
        var depth = 0
        var i = openPos
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i + 1 }
            }
            i++
        }
        return -1
    }

    /** Returns [start, end) range of a top-level block named [name], or null. */
    private fun findBlockRange(content: String, name: String): IntRange? {
        val pattern = Regex("""(?m)^\s*${Regex.escape(name)}\s*\{""")
        val match = pattern.find(content) ?: return null
        val open = content.indexOf('{', match.range.first)
        if (open < 0) return null
        val end = findClosingBrace(content, open)
        return if (end > 0) open until end else null
    }

    /** Returns [start, end) range of a nested block named [name] inside [parent]. */
    private fun findNestedBlockRange(parent: String, name: String): IntRange? {
        for (pat in listOf(
            Regex("""(?m)^\s*${Regex.escape(name)}\s*\{"""),
            Regex("""(?m)^\s*getByName\s*\(\s*"${Regex.escape(name)}"\s*\)\s*\{"""),
            Regex("""(?m)^\s*create\s*\(\s*"${Regex.escape(name)}"\s*\)\s*\{""")
        )) {
            val match = pat.find(parent) ?: continue
            val open = parent.indexOf('{', match.range.first)
            if (open < 0) continue
            val end = findClosingBrace(parent, open)
            if (end > 0) return open until end
        }
        return null
    }

    // ====================================================================
    //  Build injection content
    // ====================================================================

    private fun buildSigningBlock(config: SigningConfig, isKts: Boolean, useProps: Boolean): String {
        if (useProps) {
            return if (isKts) {
                """
                |    val keystorePropertiesFile = rootProject.file("keystore.properties")
                |    val keystoreProperties = java.util.Properties()
                |    if (keystorePropertiesFile.exists()) {
                |        keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
                |    }
                |
                |    signingConfigs {
                |        create("${config.configName}") {
                |            storeFile = file(keystoreProperties["storeFile"] as String)
                |            storePassword = keystoreProperties["storePassword"] as String
                |            keyAlias = keystoreProperties["keyAlias"] as String
                |            keyPassword = keystoreProperties["keyPassword"] as String
                |        }
                |    }
                """.trimMargin()
            } else {
                """
                |    def keystorePropertiesFile = rootProject.file("keystore.properties")
                |    def keystoreProperties = new Properties()
                |    if (keystorePropertiesFile.exists()) {
                |        keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
                |    }
                |
                |    signingConfigs {
                |        ${config.configName} {
                |            storeFile file(keystoreProperties['storeFile'])
                |            storePassword keystoreProperties['storePassword']
                |            keyAlias keystoreProperties['keyAlias']
                |            keyPassword keystoreProperties['keyPassword']
                |        }
                |    }
                """.trimMargin()
            }
        }
        // Direct mode
        return if (isKts) {
            """
            |    signingConfigs {
            |        create("${config.configName}") {
            |            storeFile = file("${config.keystorePath}")
            |            storePassword = "${config.storePassword}"
            |            keyAlias = "${config.keyAlias}"
            |            keyPassword = "${config.keyPassword}"
            |        }
            |    }
            """.trimMargin()
        } else {
            """
            |    signingConfigs {
            |        ${config.configName} {
            |            storeFile file('${config.keystorePath}')
            |            storePassword '${config.storePassword}'
            |            keyAlias '${config.keyAlias}'
            |            keyPassword '${config.keyPassword}'
            |        }
            |    }
            """.trimMargin()
        }
    }

    private fun buildReleaseAssign(config: SigningConfig, isKts: Boolean): String {
        return if (isKts) {
            """            signingConfig = signingConfigs.getByName("${config.configName}")"""
        } else {
            """            signingConfig signingConfigs.${config.configName}"""
        }
    }

    private fun buildFallbackBuildTypes(releaseAssign: String, isKts: Boolean): String {
        return if (isKts) {
            """
            |    buildTypes {
            |        getByName("release") {
            |            isMinifyEnabled = false
            |$releaseAssign
            |        }
            |    }
            """.trimMargin()
        } else {
            """
            |    buildTypes {
            |        release {
            |            minifyEnabled false
            |$releaseAssign
            |        }
            |    }
            """.trimMargin()
        }
    }

    private fun buildFallbackAndroid(signingBlock: String, releaseAssign: String, isKts: Boolean): String {
        val bt = buildFallbackBuildTypes(releaseAssign, isKts)
        return "android {\n$signingBlock\n\n$bt\n}"
    }

    // ====================================================================
    //  Main injection logic
    // ====================================================================

    private fun buildInjection(
        content: String,
        config: SigningConfig,
        isKts: Boolean,
        useProps: Boolean
    ): String {
        val signingBlock = buildSigningBlock(config, isKts, useProps)
        val releaseAssign = buildReleaseAssign(config, isKts)

        // Find android { } block
        val androidRange = findBlockRange(content, "android")
        if (androidRange == null) {
            // No android block — append a full one
            return content.trimEnd() + "\n\n" +
                buildFallbackAndroid(signingBlock, releaseAssign, isKts) + "\n"
        }

        // Extract android block inner content
        val androidOpen = content.indexOf('{', androidRange.first)
        val innerStart = androidOpen + 1
        val innerEnd = androidRange.last - 1 // closing brace position
        val androidInner = content.substring(innerStart, innerEnd)

        // Find buildTypes inside android
        val btRange = findNestedBlockRange(androidInner, "buildTypes")

        if (btRange == null) {
            // No buildTypes — create one before closing of android
            val fullBt = buildFallbackBuildTypes(releaseAssign, isKts)
            val newInner = (signingBlock + "\n\n" + fullBt + "\n" + androidInner.trimEnd()).trimEnd()
            return content.substring(0, innerStart) + "\n" +
                newInner + "\n" +
                content.substring(innerEnd)
        }

        // We have buildTypes. Insert signingConfigs before it.
        val btAbsStart = innerStart + btRange.first
        val result = StringBuilder()
        result.append(content.substring(0, btAbsStart))
        result.append(signingBlock)
        result.append("\n\n")
        result.append(content.substring(btAbsStart))

        // Now inject signingConfig into release block inside buildTypes.
        // Re-calculate positions after the insertion.
        val shift = signingBlock.length + 2 // +2 for "\n\n"
        val shiftedBtAbsStart = btAbsStart + shift
        // Find the shifted buildTypes braces
        val shiftedBtOpen = result.indexOf("{", shiftedBtAbsStart)
        if (shiftedBtOpen < 0) return result.toString()
        val shiftedBtClose = findClosingBrace(result.toString(), shiftedBtOpen)
        if (shiftedBtClose < 0) return result.toString()
        val shiftedBtInner = result.substring(shiftedBtOpen + 1, shiftedBtClose - 1)

        // Find release block inside buildTypes
        val relRange = findNestedBlockRange(shiftedBtInner, "release")
        if (relRange != null) {
            val relInner = shiftedBtInner.substring(relRange.first, relRange.last)
            if (!relInner.contains("signingConfig")) {
                val relOpenInBt = shiftedBtInner.indexOf('{', relRange.first)
                if (relOpenInBt >= 0) {
                    val insertAbs = shiftedBtOpen + 1 + relOpenInBt + 1
                    result.insert(insertAbs, "\n" + releaseAssign)
                }
            }
        }

        return result.toString()
    }
}
