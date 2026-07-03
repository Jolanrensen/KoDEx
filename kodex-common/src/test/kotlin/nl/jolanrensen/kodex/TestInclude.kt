package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor
import org.intellij.lang.annotations.Language

class TestInclude : DocProcessorTest("include") {

    private val processors = listOf(
        IncludeDocProcessor(),
    )

    // TODO
    fun `Include with and without package kotlin`() =
        runBlocking {
            @Language("kt")
            val file = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             * @include [com.example.plugin.helloWorld]  
             */
            fun helloWorld2() {}
            """.trimIndent()

            @Language("kt")
            val documentationHelloWorld = """
            /**
             * Hello World!
             */
            """.trimIndent()

            @Language("kt")
            val documentableSourceNoDocHelloWorld = """
                fun helloWorld() {}
            """.trimIndent()

            val helloWorld = createDocumentableWrapper(
                documentation = documentationHelloWorld,
                documentableSourceNoDoc = documentableSourceNoDocHelloWorld,
                fullyQualifiedPath = "com.example.plugin.helloWorld",
                docFileTextRange = file.textRangeOf(documentationHelloWorld),
                fileTextRange = TODO(),
            )

            @Language("kt")
            val documentationHelloWorld2 = """
            /**
             * @include [helloWorld]
             * @include [com.example.plugin.helloWorld]  
             */
            """.trimIndent()

            @Language("kt")
            val documentableSourceNoDocHelloWorld2 = """
            fun helloWorld2() {}
            """.trimIndent()

            val helloWorld2 = createDocumentableWrapper(
                documentation = documentationHelloWorld2,
                documentableSourceNoDoc = documentableSourceNoDocHelloWorld2,
                fullyQualifiedPath = "com.example.plugin.helloWorld2",
                docFileTextRange = file.textRangeOf(documentationHelloWorld2),
                fileTextRange = TODO(),
            )

            @Language("kt")
            val expectedOutput = """
            /**
             * Hello World!
             * Hello World! 
             */
            """.trimIndent()

            processContent(
                documentableWrapper = helloWorld2,
                processors = processors,
                additionals = listOf(
                    AdditionalDocumentable(helloWorld),
                ),
            ) shouldBe expectedOutput
        }
}
