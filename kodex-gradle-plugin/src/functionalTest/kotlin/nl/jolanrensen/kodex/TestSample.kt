@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestSample : DocProcessorFunctionalTest(name = "sample") {

    private val processors = listOf(
        ::SAMPLE_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Simple sample same file Kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @sample [helloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * ```kotlin
             * /**
             *  * Hello World!
             *  */
             * fun helloWorld() {}
             * ```
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
    fun `Simple sample same file Java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
            
            /**
             * @sample {@link HelloWorld}
             */
            public class HelloWorld2 {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
            
            /**
             * <pre>
             * /**
             *  * Hello World!
             *  &#42;&#47;
             * public class HelloWorld {
             *     public static void main(String[] args) {
             *         System.out.println(&quot;Hello World!&quot;);
             *     }
             * }
             * </pre>
             */
            public class HelloWorld2 {}
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
    fun `Simple sample no comments same file Kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @sampleNoComments [helloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * ```kotlin
             * fun helloWorld() {}
             * ```
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
    fun `Simple sample no comments same file Java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
            
            /**
             * @sampleNoComments {@link HelloWorld}
             */
            public class HelloWorld2 {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
            
            /**
             * <pre>
             * public class HelloWorld {
             *     public static void main(String[] args) {
             *         System.out.println(&quot;Hello World!&quot;);
             *     }
             * }
             * </pre>
             */
            public class HelloWorld2 {}
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
    fun `Sample Java from Kotlin`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
        """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @sample [HelloWorld]
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * ```java
             * /**
             *  * Hello World!
             *  */
             * public class HelloWorld {
             *     public static void main(String[] args) {
             *         System.out.println("Hello World!");
             *     }
             * }
             * ```
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/HelloWorld.java",
                    content = otherFile,
                ),
            ),
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Sample start and end Kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {
                // SampleStart
                println("Hello World!")
                // SampleEnd
            }
            
            /**
             * @sample [helloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {
                // SampleStart
                println("Hello World!")
                // SampleEnd
            }
            
            /**
             * ```kotlin
             * println("Hello World!")
             * ```
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
    fun `Sample start and end Java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    // SampleStart
                    System.out.println("Hello World!");
                    // SampleEnd
                }
            }
            
            /**
             * @sample {@link HelloWorld}
             */
            public class HelloWorld2 {}
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            /**
             * Hello World!
             */
            public class HelloWorld {
                public static void main(String[] args) {
                    // SampleStart
                    System.out.println("Hello World!");
                    // SampleEnd
                }
            }
            
            /**
             * <pre>
             * System.out.println(&quot;Hello World!&quot;);
             * </pre>
             */
            public class HelloWorld2 {}
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
