@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestTaskConfiguration : DocProcessorFunctionalTest(name = "taskConfiguration") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR,
    ).map { it.name }

    @Test
    fun `task configuration from build script overrides kodex extension`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             * @comment Hi this is a comment
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kts")
        val extraBuildScript = """
            tasks.named<RunKodexTask>("preprocessMainKodex") {
                processors.set(listOf(COMMENT_DOC_PROCESSOR))
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            extraBuildScript = extraBuildScript,
        ) shouldBe expectedOutput
    }
}
