@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.TODO_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestTodo : DocProcessorFunctionalTest(name = "todo") {

    private val processors = listOf(
        ::TODO_DOC_PROCESSOR.name,
    )

    @Test
    fun `Simple todo kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** TODO */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Simple todo java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            class HelloWorld {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /** TODO */
            class HelloWorld {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Simple todo with function java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            class HelloWorld {
            
                void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /** TODO */
            class HelloWorld {
            
                /** TODO */
                void helloWorld() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Todo with other docs kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            fun helloWorld() {}
            
            /**
             * This already has docs
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** TODO */
            fun helloWorld() {}
            
            /**
             * This already has docs
             */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Todo with other docs java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            class HelloWorld {
            
                void helloWorld() {}
                
                /**
                 * This already has docs
                 */
                void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /** TODO */
            class HelloWorld {
            
                /** TODO */
                void helloWorld() {}
                
                /**
                 * This already has docs
                 */
                void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            fileName = "HelloWorld",
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }
}
