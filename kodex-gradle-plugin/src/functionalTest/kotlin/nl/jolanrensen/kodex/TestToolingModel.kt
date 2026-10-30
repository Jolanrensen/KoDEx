package nl.jolanrensen.kodex

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.tooling.KodexGradleModel
import org.gradle.tooling.GradleConnector
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.io.File
import java.io.FileWriter

/**
 * Verifies that the KoDEx configuration of a build can be read through the Gradle Tooling API
 * as a [KodexGradleModel] (built by [nl.jolanrensen.kodex.gradle.KodexToolingModelBuilder]).
 * This is what the KoDEx IntelliJ plugin does during Gradle sync to run "connected" to the build.
 */
class TestToolingModel {

    private val projectDirectory = File("build/toolingModel")

    @Language("kts")
    private val settingsFile = """
        pluginManagement {
            repositories {
                mavenLocal()
                gradlePluginPortal()
                mavenCentral()
            }
        }
        rootProject.name = "toolingModel"
        include(":producer", ":consumer", ":plain")
    """.trimIndent()

    @Language("properties")
    private val propertiesFile = """
        org.gradle.jvmargs=-Xmx8g
        tosAccepted = true
    """.trimIndent()

    @Language("kts")
    private fun buildFile(kodexBlock: String) =
        """
        import nl.jolanrensen.kodex.gradle.*
        import nl.jolanrensen.kodex.defaultProcessors.*
        
        plugins {
            kotlin("jvm") version "2.2.10"
            id("nl.jolanrensen.kodex") version "$VERSION"
        }
        
        repositories { mavenLocal(); mavenCentral() }
        
        kodex {
            $kodexBlock
        }
        
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
        }
        java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }
        """.trimIndent()

    @Language("kts")
    private val plainBuildFile = """
        plugins {
            kotlin("jvm") version "2.2.10"
        }
        repositories { mavenLocal(); mavenCentral() }
    """.trimIndent()

    @Test
    fun `KodexGradleModel can be fetched through the tooling api`() {
        projectDirectory.deleteRecursively()
        projectDirectory.mkdirs()

        File(projectDirectory, "settings.gradle.kts").write(settingsFile)
        File(projectDirectory, "gradle.properties").write(propertiesFile)

        File(projectDirectory, "producer/build.gradle.kts").write(
            buildFile(
                """
                preprocess(kotlin.sourceSets.main) {
                    generateJar = false
                    generateSourcesJar = false
                }
                """.trimIndent(),
            ),
        )
        File(projectDirectory, "producer/src/main/kotlin/com/example/producer/Producer.kt")
            .write("package com.example.producer\n\n/** ! */\ninterface A")

        File(projectDirectory, "consumer/build.gradle.kts").write(
            buildFile(
                """
                preprocess(kotlin.sourceSets.main) {
                    processors = listOf(INCLUDE_DOC_PROCESSOR, REMOVE_ESCAPE_CHARS_PROCESSOR)
                    arguments(INCLUDE_DOC_PROCESSOR_PRE_SORT to false, "someString" to "value", "someInt" to 3)
                    processLimit = 42
                    generateJar = false
                    generateSourcesJar = false
                    contextualSourceSets(kotlin.sourceSets.test)
                    contextualSourceSet(":producer", "main")
                }
                preprocess(kotlin.sourceSets.test) {
                    generateJar = false
                    generateSourcesJar = false
                }
                """.trimIndent(),
            ),
        )
        File(projectDirectory, "consumer/src/main/kotlin/com/example/consumer/Consumer.kt")
            .write("package com.example.consumer\n\n/** @include [com.example.producer.A] */\nfun helloWorld2() {}")
        File(projectDirectory, "consumer/src/test/kotlin/com/example/consumer/ConsumerTest.kt")
            .write("package com.example.consumer\n\n/** test */\nfun test() {}")

        File(projectDirectory, "plain/build.gradle.kts").write(plainBuildFile)

        // Consumer: two preprocess configurations, with same-project and cross-module contextual source sets
        val consumerModel = fetchModel(File(projectDirectory, "consumer")).shouldNotBeNull()
        consumerModel.kodexVersion shouldBe VERSION
        consumerModel.projectPath shouldBe ":consumer"
        consumerModel.projectDir.canonicalFile shouldBe File(projectDirectory, "consumer").canonicalFile
        consumerModel.preprocessConfigurations.map { it.sourceSetName } shouldContainExactly listOf("main", "test")

        val main = consumerModel.preprocessConfigurations.first { it.sourceSetName == "main" }
        main.newSourceSetName shouldBe "mainKodex"
        main.taskName shouldBe "preprocessMainKodex"
        main.processors shouldContainExactly listOf(
            "nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor",
            "nl.jolanrensen.kodex.defaultProcessors.RemoveEscapeCharsProcessor",
        )
        main.arguments shouldBe mapOf(
            "nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor.PRE_SORT" to false,
            "someString" to "value",
            "someInt" to 3,
        )
        main.processLimit shouldBe 42
        main.sourceRoots.map { it.canonicalFile } shouldContainExactlyInAnyOrder listOf(
            File(projectDirectory, "consumer/src/main/kotlin").canonicalFile,
            File(projectDirectory, "consumer/src/main/java").canonicalFile,
        )
        main.contextualSourceSetNames shouldContainExactly listOf("test")
        main.crossModuleContextualSourceSets shouldContainExactly listOf(":producer/main")
        // the same-project test roots and the (existing) roots of :producer's main, resolved to directories
        main.contextualSourceRoots.map { it.canonicalFile } shouldContainExactlyInAnyOrder listOf(
            File(projectDirectory, "consumer/src/test/kotlin").canonicalFile,
            File(projectDirectory, "consumer/src/test/java").canonicalFile,
            File(projectDirectory, "producer/src/main/kotlin").canonicalFile,
        )
        // no custom plugins declared, and the Dokka dependencies KoDEx needs itself are not exposed
        main.pluginClasspath.shouldBeEmpty()

        val test = consumerModel.preprocessConfigurations.first { it.sourceSetName == "test" }
        test.taskName shouldBe "preprocessTestKodex"
        test.processors.size shouldBe 8 // the defaults
        test.contextualSourceRoots.shouldBeEmpty()

        // Producer: default configuration
        val producerModel = fetchModel(File(projectDirectory, "producer")).shouldNotBeNull()
        producerModel.projectPath shouldBe ":producer"
        producerModel.preprocessConfigurations.single().sourceSetName shouldBe "main"

        // Plain project without KoDEx: no model, the IDE falls back to "standalone"
        fetchModel(File(projectDirectory, "plain")).shouldBeNull()
    }

    private fun fetchModel(projectDir: File): KodexGradleModel? =
        GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .connect()
            .use { connection ->
                try {
                    connection.model(KodexGradleModel::class.java)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                        .get()
                } catch (e: org.gradle.tooling.UnknownModelException) {
                    null
                }
            }

    private fun File.write(string: String) {
        parentFile.mkdirs()
        FileWriter(this).use { it.write(string) }
    }
}
