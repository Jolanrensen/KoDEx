@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.NO_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestNoDoc : DocProcessorFunctionalTest(name = "no-doc") {

    private val processors = listOf(
        ::NO_DOC_PROCESSOR.name,
    )

    @Test
    fun `Simple no docs kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
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

    @Test
    fun `Simple no docs java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            class HelloWorld {
            
                /**
                 * Hello World!
                 */
                void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            class HelloWorld {
            
                void helloWorld() {}
            }
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
    fun `Indented no docs kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
                    /**
                     * Hello World!
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

    @Test
    fun `Indented no docs java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
                    /**
                     * Hello World!
                     */
            class HelloWorld {
            
                        /**
                         * Hello World!
                         */
                void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            class HelloWorld {
            
                void helloWorld() {}
            }
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
    fun `No docs without start newline kotlin`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `No docs without start newline java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            /**
             * Hello World!
             */
            class HelloWorld {
                /**
                 * Hello World!
                 */
                void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            class HelloWorld {
                void helloWorld() {}
            }
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
    fun `No docs without end newline kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            /**
             * Hello World!
             */fun helloWorld() {}
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

    @Test
    fun `No docs without end newline java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            /**
             * Hello World!
             */class HelloWorld {
                /**
                 * Hello World!
                 */
                void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            class HelloWorld {
                void helloWorld() {}
            }
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
    fun `Already no docs kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe content
    }

    @Test
    fun `Already no docs java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            class HelloWorld {
            
                void helloWorld() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
            processors = processors,
        ) shouldBe content
    }
}
