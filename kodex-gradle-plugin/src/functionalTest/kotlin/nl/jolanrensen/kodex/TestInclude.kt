@file:Suppress("FunctionName")

package nl.jolanrensen.kodex

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestInclude : DocProcessorFunctionalTest(name = "include") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Test
    fun `Include with and without package kotlin`() {
        @Language("kt")
        val content = """
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
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             * Hello World! 
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
    fun `Include with and without package java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}

                /**
                 * @include {@link Test#helloWorld}
                 * @include {@link com.example.plugin.Test#helloWorld}
                 */
                public void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * Hello World!
                 * Hello World!
                 */
                public void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Include no package`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Transitive include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** @include [helloWorld] */
            fun helloWorld2() {}

            /** @include [helloWorld2] */
            fun helloWorld3() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** Hello World! */
            fun helloWorld2() {}
            
            /** Hello World! */
            fun helloWorld3() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Expanding link kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** 
              * Hello World! 
              * [Some aliased link][helloWorld2] 
              * [helloWorld\]
              * [helloWorld]
              */
            fun helloWorld() {}
            
            /** @include [helloWorld] */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World! 
             * [Some aliased link][helloWorld2] 
             * [helloWorld]
             * [helloWorld]
             */
            fun helloWorld() {}
            
            /** Hello World! 
             * [Some aliased link][com.example.plugin.helloWorld2] 
             * [helloWorld]
             * [helloWorld][com.example.plugin.helloWorld] */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Transitive include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** @include {@link Test#helloWorld} */
                public void helloWorld2() {}
            
                /** @include {@link Test#helloWorld2} */
                public void helloWorld3() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** Hello World! */
                public void helloWorld2() {}
            
                /** Hello World! */
                public void helloWorld3() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Inline include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** @include [helloWorld] {@include [helloWorld]} */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** Hello World! Hello World! */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Inline include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** @include {@link Test#helloWorld} {@include {@link Test#helloWorld}} */
                public void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** Hello World! Hello World! */
                public void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Function overload include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             * @include [com.example.plugin.helloWorld]  
             */
            fun helloWorld(a: Int) {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             * Hello World! 
             */
            fun helloWorld(a: Int) {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Function overload include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * @include {@link Test#helloWorld}
                 * @include {@link com.example.plugin.Test#helloWorld}  
                 */
                public void helloWorld(int a) {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * Hello World!
                 * Hello World! 
                 */
                public void helloWorld(int a) {}
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Self reference include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [helloWorld]
             */
            fun helloWorld(a: Int) {}
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
    fun `Self reference include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * @include {@link Test#helloWorld}
                 */
                public void helloWorld(int a) {}
            }
        """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
                language = FileLanguage.JAVA,
            )
        }
    }

    @Test
    fun `Unavailable reference include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [nothing]
             */
            fun helloWorld(a: Int) {}
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
    fun `Unavailable reference include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * @include {@link Test#nothing}
                 */
                public void helloWorld(int a) {}
            }
        """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
                language = FileLanguage.JAVA,
            )
        }
    }

    @Test
    fun `Multiple files kotlin`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @include [helloWorld] */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Test2.kt",
                    content = otherFile,
                ),
            ),
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Multiple files java`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            }
        """.trimIndent()

        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** @include {@link Test#helloWorld} */
                public void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** Hello World! */
                public void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            fileName = "Test2",
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/Test.java",
                    content = otherFile,
                ),
            ),
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Multiple files with import kotlin`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            object Test2 {
                /**
                 * Hello World!
                 */
                fun helloWorld() {}
            }
        """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            import com.example.plugin.Test2.*
            
            /** @include [helloWorld] */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            import com.example.plugin.Test2.*
            
            /** Hello World! */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Test2.kt",
                    content = otherFile,
                ),
            ),
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Multiple files with import java`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
                public static void helloWorld3() {}
            }
        """.trimIndent()

        @Language("java")
        val content = """
            package com.example.plugin;
            
            import com.example.plugin.Test.*;
            import com.example.plugin.Test.helloWorld;
            import static com.example.plugin.Test.helloWorld3;
            
            public class Test2 {
            
                /** @include helloWorld */
                public void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            import com.example.plugin.Test.*;
            import com.example.plugin.Test.helloWorld;
            import static com.example.plugin.Test.helloWorld3;
            
            public class Test2 {
            
                /** Hello World! */
                public void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            fileName = "Test2",
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/Test.java",
                    content = otherFile,
                ),
            ),
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Multiple files kotlin to java`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** @include {@link TestKt#helloWorld} */
                public void helloWorld2() {}
            }
        """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** Hello World! */
                public void helloWorld2() {}
            }
        """.trimIndent()

        processContent(
            content = content,
            fileName = "Test2",
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Test.kt",
                    content = otherFile,
                ),
            ),
            processors = processors,
            language = FileLanguage.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Multiple files java to kotlin`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            }
        """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @include [Test.helloWorld] */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/Test.java",
                    content = otherFile,
                ),
            ),
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type inheritance interface 1`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            interface TestInterface {
                /**
                 * Hello World!
                 */
                fun helloWorld()
            }
            
            interface Test1 : TestInterface
            
            /**
             * @include [helloWorld]
             */
            interface Test2 : Test1
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            interface TestInterface {
                /**
                 * Hello World!
                 */
                fun helloWorld()
            }
            
            interface Test1 : TestInterface
            
            /**
             * Hello World!
             */
            interface Test2 : Test1
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type inheritance interface 2`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            interface TestInterface {
                /**
                 * Hello World!
                 */
                fun helloWorld()
            }
            
            interface Test1 : TestInterface
            
            /**
             * @include [com.example.plugin.Test1.helloWorld]
             */
            interface Test2
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            interface TestInterface {
                /**
                 * Hello World!
                 */
                fun helloWorld()
            }
            
            interface Test1 : TestInterface
            
            /**
             * Hello World!
             */
            interface Test2
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type inheritance interface 3`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            interface a {
            
                /**
                 * Hello World
                 */
                fun test()
            
                interface b : a {
            
                    interface c : b {
            
                        interface d : c {
            
                            /**
                             * @include [com.example.plugin.a.b.c.d.test]
                             */
                            fun hello()
                        }
                    }
                }
            }
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            interface a {
            
                /**
                 * Hello World
                 */
                fun test()
            
                interface b : a {
            
                    interface c : b {
            
                        interface d : c {
            
                            /**
                             * Hello World
                             */
                            fun hello()
                        }
                    }
                }
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type inheritance extensions`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            interface TestInterface
            interface Test1 : TestInterface
            
            /**
             * Hello World!
             */
            fun TestInterface.helloWorld()
            
            /**
             * @include [Test1.helloWorld]
             */
            interface Test2
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            interface TestInterface
            interface Test1 : TestInterface
            
            /**
             * Hello World!
             */
            fun TestInterface.helloWorld()
            
            /**
             * Hello World!
             */
            interface Test2
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type alias reading`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            typealias HelloWorld = String
            
            /**
             * @include [HelloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            /**
             * Hello World!
             */
            typealias HelloWorld = String
            
            /**
             * Hello World!
             */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `nested type alias reading`() {
        @Language("kt")
        val content = """
            interface A { 
                /**
                 * Hello World!
                 */
                typealias HelloWorld = String 
            }
            
            /**
             * @include [A.HelloWorld]
             */
            fun helloWorld2() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            interface A { 
                /**
                 * Hello World!
                 */
                typealias HelloWorld = String 
            }
            
            /**
             * Hello World!
             */
            fun helloWorld2() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `type alias writing`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             */
            typealias HelloWorld2 = String
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             */
            typealias HelloWorld2 = String
        """.trimIndent()

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `nested type alias writing`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            interface A {
                /**
                 * @include [helloWorld]
                 */
                typealias HelloWorld2 = String
            }
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            interface A {
                /**
                 * Hello World!
                 */
                typealias HelloWorld2 = String
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }
}
