@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.io.File

class PluginExtensionTest : DocProcessorFunctionalTest("extension") {

    private val processors = listOf(
        "\"nl.jolanrensen.extension.Extension\"",
        "REMOVE_ESCAPE_CHARS_PROCESSOR",
    )

    private val plugins = listOf(
        "nl.jolanrensen:PluginExtensionTest:$version",
    )

    @Language("kts")
    private val settingsFile = """
        rootProject.name = "PluginExtensionTest"
    """.trimIndent()

    @Language("kts")
    private val buildFile: String = """
        plugins {  
            kotlin("jvm") version "2.2.10"
            id("com.vanniktech.maven.publish") version "0.22.0"
        }

        group = "nl.jolanrensen"
        version = "$version"
        
        repositories {
            mavenLocal()
            gradlePluginPortal()
            mavenCentral()
        }
        
        dependencies {
            compileOnly("nl.jolanrensen.kodex:kodex-gradle-plugin:$version")
//            implementation("nl.jolanrensen.kodex:kodex-common:$version")
        }
    """.trimIndent()

    @Language("kt")
    private val extensionPlugin = """
        package nl.jolanrensen.extension

        import nl.jolanrensen.kodex.docContent.*
        import nl.jolanrensen.kodex.processor.*
        import nl.jolanrensen.kodex.query.*
        
        class Extension : DocProcessor() {
        
            override suspend fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
                documentablesByPath.documentablesToProcess.map { (path, v) ->
                    path to v.map {
                        it.copy(
                            docContent = it.docContent.value
                                .replace('e', 'a')
                                .replace('E', 'A')
                                .asDocContent(),
                            isModified = true,
                        )
                    }
                }.toDocumentablesByPath(documentablesByPath.loadedProcessors)
        }
    """.trimIndent()

    private val extProjectDirectory = File("build/extension-plugin")

    init {
        initializePluginProjectFiles()
        writeAdditionalPluginFiles(
            listOf(
                AdditionalFile(
                    relativePath = "settings.gradle.kts",
                    content = settingsFile,
                ),
                AdditionalFile(
                    relativePath = "build.gradle.kts",
                    content = buildFile,
                ),
                AdditionalFile(
                    relativePath = "src/main/kotlin/nl/jolanrensen/extension/Extension.kt",
                    content = extensionPlugin,
                ),
                AdditionalFile(
                    relativePath = "src/main/resources/META-INF/services/nl.jolanrensen.kodex.processor.DocProcessor",
                    content = "nl.jolanrensen.extension.Extension",
                ),
            ),
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments("clean", "publishToMavenLocal")
            .withProjectDir(extProjectDirectory)
            .withDebug(true)
            .build()
    }

    /**
     * Clears the project directory and creates the build files.
     */
    private fun initializePluginProjectFiles() {
        // Set up the test build
        extProjectDirectory.deleteRecursively()
        extProjectDirectory.mkdirs()

        File(extProjectDirectory, "settings.gradle.kts")
            .write(settingsFile)

        File(extProjectDirectory, "build.gradle.kts")
            .write(buildFile)
    }

    /**
     * Writes the [additionalFiles] to the project directory.
     */
    private fun writeAdditionalPluginFiles(additionalFiles: List<Additional>) {
        for (additional in additionalFiles) {
            with(File(extProjectDirectory, additional.relativePath)) {
                when (additional) {
                    is AdditionalDirectory -> mkdirs()
                    is AdditionalFile -> write(additional.content)
                }
            }
        }
    }

    @Test
    fun `E to A processor`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * eeeEEEE
             */
            class TestClass {
            
                /**
                 * This is a test function.
                 */
                fun testFunction() {
                    println("Hello World!")
                }
            
            }
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * aaaAAAA
             */
            class TestClass {
            
                /**
                 * This is a tast function.
                 */
                fun testFunction() {
                    println("Hello World!")
                }
            
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            plugins = plugins,
            // buildScan = true,
        ) shouldBe expectedOutput
    }
}
