package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import nl.jolanrensen.kodex.defaultProcessors.CommentDocProcessor
import nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.DocumentablesByPathWithCache
import org.junit.jupiter.api.Test

/**
 * Mimics how the IntelliJ plugin processes docs one at a time using [DocumentablesByPathWithCache].
 */
class TestDocumentablesByPathWithCache : DocProcessorTest("cache") {

    private fun processors(): List<DocProcessor> =
        listOf(
            CommentDocProcessor(),
            IncludeDocProcessor(),
        )

    private val docs = mutableMapOf<String, DocumentableWrapper>()

    private fun setDoc(name: String, documentation: String, offset: Int) {
        val path = "com.example.plugin.$name"
        docs[path] = createDocumentableWrapper(
            documentation = documentation,
            documentableSourceNoDoc = "fun $name() {}",
            fullyQualifiedPath = path,
            docFileTextRange = offset until (offset + documentation.length),
            fileTextRange = offset until (offset + documentation.length + 20),
        )
    }

    private val cache = DocumentablesByPathWithCache(
        processLimit = 10_000,
        loadedProcessors = processors(),
        // like in IntelliJ, a new wrapper is created from the source for each query
        queryNew = { _, link ->
            docs.values
                .filter { it.fullyQualifiedPath == link || it.fullyQualifiedPath.endsWith(".$link") }
                .map { it.copy() }
                .ifEmpty { null }
        },
        logDebug = {},
    )

    // same flow as DocProcessorService.getProcessedDocumentableWrapperOrNull
    private suspend fun render(name: String): String {
        val doc = docs.getValue("com.example.plugin.$name")
        val needsRebuild = cache.updatePreProcessing(doc)
        if (!needsRebuild) return cache.getDocContentResult(doc.identifier)!!.value.normalize()

        var acc: DocumentablesByPath = cache
        for (processor in processors()) {
            acc = processor.processSafely(processLimit = 10_000, documentablesByPath = acc)
            if (processor is IncludeDocProcessor) {
                // PostIncludeDocProcessorCacheCollector
                acc.documentablesToProcess.values.flatten().forEach { cache.updatePostIncludeDocContentResult(it) }
            }
        }
        val result = acc[doc.identifier]!!.docContent.value
        cache.updatePostProcessing()
        return result.normalize()
    }

    private fun String.normalize(): String = lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    private fun setup(cText: String = "C") {
        setDoc("C", "/** $cText {@comment hidden C} */", 0)
        setDoc("B", "/**\n * B {@comment hidden B}\n * @include [C]\n */", 100)
        setDoc("A", "/**\n * A {@comment hidden A}\n * @include [B]\n */", 200)
    }

    @Test
    fun `diamond includes with comments`() =
        runBlocking {
            setDoc("C", "/** C {@comment hidden C} */", 0)
            setDoc("B", "/**\n * B {@comment hidden B}\n * @include [C]\n */", 100)
            setDoc("A", "/**\n * A {@comment hidden A}\n * @include [B]\n * @include [C]\n */", 200)
            setDoc("D", "/**\n * D {@comment hidden D}\n * @include [A]\n */", 300)

            render("D") shouldBe "D\nA\nB\nC\nC"
            render("A") shouldBe "A\nB\nC\nC"
            render("D") shouldBe "D\nA\nB\nC\nC"
        }

    @Test
    fun `nested includes with comments`() =
        runBlocking {
            setup()
            // cold
            render("A") shouldBe "A\nB\nC"

            // warm, after rendering the dependencies separately
            render("C") shouldBe "C"
            render("B") shouldBe "B\nC"
            render("A") shouldBe "A\nB\nC"

            // after modifying C
            setup(cText = "C2")
            render("A") shouldBe "A\nB\nC2"
            render("B") shouldBe "B\nC2"
            render("A") shouldBe "A\nB\nC2"
        }
}
