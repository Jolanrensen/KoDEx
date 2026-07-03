@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestComment : DocProcessorFunctionalTest(name = "comment") {

    private val processors = listOf(
        ::COMMENT_DOC_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Block comments`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             * @comment Hi this is a comment
             * This still
             * @test Not this
             * @comment Hi this too
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             * @test Not this
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Inline tags`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!{@comment Hi this is a comment {
             *   This still}
             * {@comment Hi this too {@comment Some comment inside another}}
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             *
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `All is comment`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @comment Hello World!
             * 
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }
}
