package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import kotlin.test.Test

class ReadMeExampleTest : DocProcessorFunctionalTest("readMe") {

    private val processors = listOf(
        // The @include processor
        ::INCLUDE_DOC_PROCESSOR,
        // The @includeFile processor
        ::INCLUDE_FILE_DOC_PROCESSOR,
        // The @set and @get / $ processor
        ::ARG_DOC_PROCESSOR,
        // The @comment processor
        ::COMMENT_DOC_PROCESSOR,
        // The @sample and @sampleNoComments processor
        ::SAMPLE_DOC_PROCESSOR,
        ::REMOVE_ESCAPE_CHARS_PROCESSOR,
    ).map { it.name }

    @Language("java")
    private val javaContent = """
        package com.example.plugin;
        
        import kotlin.Unit;
        
        import java.io.File;
        
        public class Submitting {
        
            public boolean sample() {
                // SampleStart
                int number = 1;
                File file = new File("file.json");
                boolean result = TestKt.submit(number, file, e -> {
                    System.out.println(e.getMessage());
                    return Unit.INSTANCE;
                });
                // SampleEnd
                return result;
            }
        }
    """.trimIndent()

    @Language("json")
    private val jsonContent = """
        {
          "number": 5.0
        }
    """.trimIndent()

    @Test
    fun `README example test`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * ## Submit Number
             * Submits the given number.
             *
             * ### For example
             * {@comment Giving an example of how the function can be called, default the argument to `5.0`}
             * ```kotlin
             * MyClass().submit(${'$'}{[SubmitDocs.ExampleArg]=5.0}, File("file.json")) { println(it) }
             * ```
             *
             * ### Result
             * The number will be submitted to a JSON file like this:
             * ```json
             * {@includeFile (./submitted.json)}
             * ```
             * @get [ExtraInfoArg] {@comment Attempt to retrieve the [ExtraInfoArg] variable}
             * ${'$'}[ParamArg] {@comment Attempt to retrieve the [ParamArg] variable using shorter notation}
             * @param location The file location to submit the number to.
             * @param onException What to do when an exception occurs.
             * @return `true` if the number was submitted successfully, `false` otherwise.
             */
            @ExcludeFromSources
            private interface SubmitDocs {
            
                /* Example argument, defaults to 5.0 */
                interface ExampleArg
            
                /* Optional extra info */
                interface ExtraInfoArg
            
                /* The param part */
                interface ParamArg
            }
            
            /**
             * @include [SubmitDocs]
             * @set [SubmitDocs.ParamArg] @param [number] The [Int] to submit.
             * @set [SubmitDocs.ExampleArg] 5{@comment Overriding the default example argument}
             * @comment While You can use block tags for multiline comments, most of the time, inline tags are clearer:
             * {@set [SubmitDocs.ExtraInfoArg]
             *  ### This function can also be used from Java:
             *  {@sample [Submitting.sample]}
             * }
             */
            public fun submit(number: Int, location: File, onException: (e: Exception) -> Unit): Boolean = TODO()
            
            /** @include [SubmitDocs] {@set [SubmitDocs.ParamArg] @param [number] The [Double] to submit.} */
            public fun submit(number: Double, location: File, onException: (e: Exception) -> Unit): Boolean = TODO()
        """.trimIndent()

        @Language("kt")
        val expected = """
            package com.example.plugin
            
            
            
            /**
             * ## Submit Number
             * Submits the given number.
             *
             * ### For example
             *
             * ```kotlin
             * MyClass().submit(5, File("file.json")) { println(it) }
             * ```
             *
             * ### Result
             * The number will be submitted to a JSON file like this:
             * ```json
             * {
             *   "number": 5.0
             * }
             * ```
             * ### This function can also be used from Java:
             *  ```java
             * int number = 1;
             * File file = new File("file.json");
             * boolean result = TestKt.submit(number, file, e -> {
             *     System.out.println(e.getMessage());
             *     return Unit.INSTANCE;
             * });
             * ```
             * @param [number] The [Int] to submit. 
             * @param location The file location to submit the number to.
             * @param onException What to do when an exception occurs.
             * @return `true` if the number was submitted successfully, `false` otherwise.
             */
            public fun submit(number: Int, location: File, onException: (e: Exception) -> Unit): Boolean = TODO()
            
            /** ## Submit Number
             * Submits the given number.
             *
             * ### For example
             *
             * ```kotlin
             * MyClass().submit(5.0, File("file.json")) { println(it) }
             * ```
             *
             * ### Result
             * The number will be submitted to a JSON file like this:
             * ```json
             * {
             *   "number": 5.0
             * }
             * ```
             *
             * @param [number] The [Double] to submit. 
             * @param location The file location to submit the number to.
             * @param onException What to do when an exception occurs.
             * @return `true` if the number was submitted successfully, `false` otherwise. */
            public fun submit(number: Double, location: File, onException: (e: Exception) -> Unit): Boolean = TODO()
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Submitting.java",
                    content = javaContent,
                ),
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/submitted.json",
                    content = jsonContent,
                ),
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expected
    }
}
