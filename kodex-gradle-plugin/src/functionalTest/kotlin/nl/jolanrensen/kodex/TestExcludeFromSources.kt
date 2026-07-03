package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestExcludeFromSources : DocProcessorFunctionalTest(name = "excl") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Simple interface exclusion test`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
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

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun `Simple interface exclusion test one line`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources interface HelloWorld
            @ExcludeFromSources interface HelloWorld2
            
            /**
             * {@include [HelloWorld]}!
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

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun `Nested exclusion test`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources
            interface HelloWorld {
            
                /**
                 * {@include [HelloWorld]}!
                 */
                fun helloWorld() {}
            }
            
            /**
             * {@include [HelloWorld.helloWorld]}!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            
            
            /**
             * Hello World!!
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun `class`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources
            class HelloWorld
            
            /**
             * {@include [HelloWorld]}!
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

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun `annotation class`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources
            annotation class HelloWorld
            
            /**
             * {@include [HelloWorld]}!
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

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun `property`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            
            class HelloWorld {
                /**
                 * Hello World
                 */
                @ExcludeFromSources
                val helloWorld = "Hello World"
            }
            
            /**
             * {@include [HelloWorld.helloWorld]}!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            
            class HelloWorld {
                
            }
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }

    @Test
    fun file() {
        @Language("kt")
        val content = """
            @file:ExcludeFromSources            
            
            package com.example.plugin
            
            
            class HelloWorld {
                /**
                 * Hello World
                 */
                val helloWorld = "Hello World"
            }
            
            /**
             * {@include [HelloWorld.helloWorld]}!
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe null
    }

    @Test
    fun `file 2`() {
        @Language("kt")
        val content = """
            @file:[ExcludeFromSources ]            
            
            package com.example.plugin
            
            
            class HelloWorld {
                /**
                 * Hello World
                 */
                val helloWorld = "Hello World"
            }
            
            /**
             * {@include [HelloWorld.helloWorld]}!
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe null
    }
}
