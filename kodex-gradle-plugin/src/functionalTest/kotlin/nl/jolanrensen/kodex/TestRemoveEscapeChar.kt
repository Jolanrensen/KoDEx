package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestRemoveEscapeChar : DocProcessorFunctionalTest("escape-char") {

    private val processors = listOf(
        ::ARG_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Escaped arg`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            /**
             * Simplistic JSON path implementation.
             * Supports just keys (in bracket notation), double quotes, arrays, and wildcards.
             *
             * Examples:
             * `\${'$'}["store"]["book"][*]["author"]`
             *
             * `\${'$'}[1]` will match `\${'$'}[*]`
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expected = """
            package com.example.plugin
            /**
             * Simplistic JSON path implementation.
             * Supports just keys (in bracket notation), double quotes, arrays, and wildcards.
             *
             * Examples:
             * `${'$'}["store"]["book"][*]["author"]`
             *
             * `${'$'}[1]` will match `${'$'}[*]`
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
