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
    fun `imports of excluded documentables are removed`() {
        @Language("kt")
        val docs = """
            package com.example.plugin.docs
            
            import com.example.plugin.ExcludeFromSources
            
            /**
             * Hello World
             */
            @ExcludeFromSources
            interface HelloWorldDoc
            
            /** Kept */
            interface KeptDoc
        """.trimIndent()

        @Language("kt")
        val excludedFile = """
            @file:ExcludeFromSources
            
            package com.example.plugin.docs2
            
            import com.example.plugin.ExcludeFromSources
            
            /** Excluded */
            interface ExcludedFileDoc
        """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            import com.example.plugin.docs.HelloWorldDoc
            import com.example.plugin.docs.KeptDoc
            import com.example.plugin.docs2.ExcludedFileDoc
            
            /**
             * {@include [HelloWorldDoc]}!
             * [ExcludedFileDoc]
             * [KeptDoc]
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            import com.example.plugin.docs.KeptDoc
            
            /**
             * Hello World!
             * [ExcludedFileDoc]
             * [KeptDoc]
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
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/docs/Docs.kt",
                    content = docs,
                ),
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/docs2/Docs2.kt",
                    content = excludedFile,
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
