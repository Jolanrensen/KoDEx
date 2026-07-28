package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.FileWriter
import org.junit.Test

/**
 * Verifies that a source set from another Gradle module can be used as a contextual source set via
 * [nl.jolanrensen.kodex.gradle.KodexSourceSetTaskBuilder.contextualSourceSet], carrying the producer's
 * output cache and sources over the project boundary.
 */
class TestCrossModuleContextual {

    private val projectDirectory = File("build/crossModule")

    @Language("kts")
    private val settingsFile = """
        pluginManagement {
            repositories {
                mavenLocal()
                gradlePluginPortal()
                mavenCentral()
            }
        }
        rootProject.name = "crossModule"
        include(":producer", ":consumer")
    """.trimIndent()

    @Language("properties")
    private val propertiesFile = """
        org.gradle.jvmargs=-Xmx8g
        tosAccepted = true
    """.trimIndent()

    @Language("kts")
    private fun buildFile(contextualBlock: String) = """
        import nl.jolanrensen.kodex.gradle.*
        import nl.jolanrensen.kodex.defaultProcessors.*

        plugins {
            kotlin("jvm") version "2.2.10"
            id("nl.jolanrensen.kodex") version "$VERSION"
        }

        repositories { mavenLocal(); mavenCentral() }

        kodex {
            preprocess(kotlin.sourceSets.main) {
                processors = listOf(INCLUDE_DOC_PROCESSOR, REMOVE_ESCAPE_CHARS_PROCESSOR)
                generateJar = false
                generateSourcesJar = false
                $contextualBlock
            }
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
        }
        java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }
    """.trimIndent()

    @Language("kt")
    private val producerContent = """
        package com.example.producer

        /** ! */
        interface A

        /**
         * Hello World{@include [A]}
         */
        fun helloWorld() {}
    """.trimIndent()

    @Language("kt")
    private val consumerContent = """
        package com.example.consumer

        /**
         * @include [com.example.producer.helloWorld]
         */
        fun helloWorld2() {}
    """.trimIndent()

    @Language("kt")
    private val expectedConsumerOutput = """
        package com.example.consumer

        /**
         * Hello World!
         */
        fun helloWorld2() {}
    """.trimIndent()

    @Test
    fun `contextual sourceSet from another module`() {
        projectDirectory.deleteRecursively()
        projectDirectory.mkdirs()

        File(projectDirectory, "settings.gradle.kts").write(settingsFile)
        File(projectDirectory, "gradle.properties").write(propertiesFile)

        // producer module: just preprocesses its own main
        File(projectDirectory, "producer/build.gradle.kts").write(buildFile(contextualBlock = ""))
        File(projectDirectory, "producer/src/main/kotlin/com/example/producer/Producer.kt")
            .write(producerContent)

        // consumer module: references the producer's main source set as contextual
        File(projectDirectory, "consumer/build.gradle.kts")
            .write(buildFile(contextualBlock = """contextualSourceSet(":producer", "main")"""))
        File(projectDirectory, "consumer/src/main/kotlin/com/example/consumer/Consumer.kt")
            .write(consumerContent)

        GradleRunner.create()
            .forwardOutput()
            .withArguments(":consumer:preprocessMainKodex", "--stacktrace")
            .withProjectDir(projectDirectory)
            .withDebug(true)
            .build()

        val output = File(
            projectDirectory,
            "consumer/build/kodex/mainKodex/src/main/kotlin/com/example/consumer/Consumer.kt",
        ).readText()

        output shouldBe expectedConsumerOutput
    }

    private fun File.write(string: String) {
        parentFile.mkdirs()
        FileWriter(this).use { it.write(string) }
    }
}
