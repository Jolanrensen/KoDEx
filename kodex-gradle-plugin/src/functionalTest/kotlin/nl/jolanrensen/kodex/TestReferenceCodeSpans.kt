package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REFERENCE_CODE_SPAN_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestReferenceCodeSpans : DocProcessorFunctionalTest("reference-code-spans") {

    private val processors = listOf(
        ::REFERENCE_CODE_SPAN_DOC_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Code spans are added`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            /**
             * Hello [helloWorld], 
             * [this is a][reference], [`this too`][helloWorld]
             * @this [shouldBeLeftAlone]
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expected = """
            package com.example.plugin
            /**
             * Hello [`helloWorld`][helloWorld], 
             * [`this is a`][reference], [`this too`][helloWorld]
             * @this [shouldBeLeftAlone]
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expected
    }
}
