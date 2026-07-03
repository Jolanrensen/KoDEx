@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestIncludeFile : DocProcessorFunctionalTest(name = "includeFile") {

    private val processors = listOf(
        ::INCLUDE_FILE_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Simple include file kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * ```json
             * {@includeFile (./someFile.json)}
             * ```
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * ```json
             * { a: 1, b: 2 }
             * ```
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/someFile.json",
                    content = "{ a: 1, b: 2 }",
                ),
            ),
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `File does not exist`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @includeFile (../../someFile.txt)
             */
            fun helloWorld() {}
        """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
            )
        }
    }

    @Test
    fun `File is folder`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @includeFile (./folder)
             */
            fun helloWorld() {}
        """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                additionals = listOf(
                    AdditionalDirectory("src/main/kotlin/com/example/plugin/folder"),
                ),
                processors = processors,
            )
        }
    }

    @Test
    fun `Include itself block comment kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @includeFile (./test.kt)
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * package com.example.plugin
             *
             * /**
             *  * @includeFile (./test.kt)
             *  */
             * fun helloWorld() {}
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            fileName = "test",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Include itself block comment java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * @includeFile (./HelloWorld.java)
             */
            class HelloWorld {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /**
             * package com.example.plugin;
             *
             * /**
             *  * &#64;includeFile (./HelloWorld.java)
             *  &#42;&#47;
             * class HelloWorld {}
             */
            class HelloWorld {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Include itself inline comment kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * ```kotlin
             * {@includeFile (./test.kt)}
             * ```
             */
            fun helloWorld() {}
        """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                fileName = "test",
                processors = processors,
            )
        }
    }

    @Test
    fun `Include itself inline comment java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * ```java
             * {@includeFile (./HelloWorld.java)}
             * ```
             */
            class HelloWorld {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /**
             * ```java
             * package com.example.plugin;
             *
             * /**
             *  * ```java
             *  * {&#64;includeFile (./HelloWorld.java)}
             *  * ```
             *  &#42;&#47;
             * class HelloWorld {}
             * ```
             */
            class HelloWorld {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
            processors = processors,
        ) shouldBe expectedOutput
    }
}
