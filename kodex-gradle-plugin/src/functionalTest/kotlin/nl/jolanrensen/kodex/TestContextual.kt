package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestContextual : DocProcessorFunctionalTest(name = "contextual") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `contextual sourceSet`() {
        @Language("kt")
        val testContent = """
            package com.example.plugin.tests
            
            /** ! */
            interface A
            
            /**
             * Hello World{@include [A]}
             */
            fun helloWorld() {}
        """.trimIndent()

        // added by including the `test` SourceSet
        val testFile = AdditionalFile(
            relativePath = "src/test/kotlin/com/example/plugin/tests/Test2.kt",
            content = testContent,
        )

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [com.example.plugin.tests.helloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            contextualSourceSets = listOf("kotlin.sourceSets.test.get()"),
            additionals = listOf(testFile),
        ) shouldBe expectedOutput
    }
}
