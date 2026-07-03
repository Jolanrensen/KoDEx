package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import nl.jolanrensen.kodex.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestExportAsHtml : DocProcessorFunctionalTest("exportAsHtml") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR,
        ::EXPORT_AS_HTML_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Simple Export as HTML test`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!</p></body>")
    }

    @Test
    fun `Simple Export as HTML with range`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * @exportAsHtmlStart 
             * {@include [HelloWorld]}!
             * Hi this is a test
             * @exportAsHtmlEnd
             * Excluded  
             */
            @ExportAsHtml
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!\nHi this is a test</p></body>")
        outputFile.readText().shouldNotContain("Excluded")
        outputFile.readText().shouldNotContain("exportAsHtmlStart")
        outputFile.readText().shouldNotContain("exportAsHtmlEnd")
    }

    @Test
    fun `Simple Export as HTML test with theme unnamed`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml(true)
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!</p></body>")
    }

    @Test
    fun `Simple Export as HTML test with theme named`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml(theme = true)
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!</p></body>")
    }

    @Test
    fun `Simple Export as HTML test without theme unnamed`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml(false)
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldNotContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!</p></body>")
    }

    @Test
    fun `Simple Export as HTML test without theme named`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml(theme = false)
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldNotContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>Hello World!</p></body>")
    }

    @Test
    fun `Simple Export as HTML test without theme unnamed without stripping`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * [Hello World][HelloWorld]
             */
            interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            @ExportAsHtml(false, false)
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
        )

        val outputFile = outputDirectory.resolve("htmlExports/com.example.plugin.helloWorld.html")
        outputFile.exists() shouldBe true
        outputFile.readText().shouldNotContain("<style type=\"text/css\">")
        outputFile.readText().shouldContain("<body><p>[Hello World][com.example.plugin.HelloWorld]!</p></body>")
    }
}
