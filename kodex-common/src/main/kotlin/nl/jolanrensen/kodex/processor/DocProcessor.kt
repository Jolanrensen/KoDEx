package nl.jolanrensen.kodex.processor

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.exceptions.DocProcessorFailedException
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.withoutFilters
import nl.jolanrensen.kodex.utils.surroundWith
import java.io.Serializable
import java.util.ServiceLoader

/**
 * Abstract class that can be used to create a doc processor.
 *
 * Make sure to add the fully qualified name of your class to your
 * (`resources/META-INF/services/nl.jolanrensen.kodex.DocProcessor`)
 * to make it visible to the service loader in [ProcessDocsAction].
 *
 * @see TagDocProcessor
 */
abstract class DocProcessor : Serializable {

    val name: String = this::class.simpleName ?: "DocProcessor"

    /** Main logging access point. */
    val logger: KLogger = KotlinLogging.logger(name)

    /** Allows any argument to be passed to the processor. */
    var arguments: Map<String, Any?> = emptyMap()
        internal set

    /**
     * Process given [documentablesByPath]. This function will only be called once per [DocProcessor] instance.
     * You can use [DocumentableWrapper.copy] to create a new [DocumentableWrapper] with modified
     * [doc content][DocumentableWrapper.docContent] or use [MutableDocumentableWrapper].
     * Mark the docs that were modified with [isModified][DocumentableWrapper.isModified] and
     * don't forget to update [tags][DocumentableWrapper.tags] accordingly.
     *
     * @param processLimit The process limit parameter that was passed to the [ProcessDocsAction].
     * @param documentablesByPath Documentables by path
     * @return modified docs by path
     */
    protected abstract suspend fun process(
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): DocumentablesByPath

    // ensuring each doc processor instance is only run once
    protected var hasRun = false

    // ensuring each doc processor instance is only run once
    @Throws(DocProcessorFailedException::class)
    suspend fun processSafely(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        if (hasRun) error("This instance of ${this::class.qualifiedName} has already run and cannot be reused.")

        return try {
            process(processLimit, documentablesByPath).withoutFilters()
        } catch (e: Throwable) {
            if (e is DocProcessorFailedException) {
                throw e
            } else {
                throw DocProcessorFailedException(name, cause = e)
            }
        } finally {
            hasRun = true
        }
    }

    /**
     * An optional list of [CompletionInfo] information to display
     * in the autocomplete of the IDE.
     */
    open val completionInfos: List<CompletionInfo>
        get() = emptyList()

    /**
     * Can be overridden to provide custom highlighting for doc content given by [docContent].
     *
     * NOTE: this can contain '*' characters and indents, so make sure to handle that.
     */
    open fun getHighlightsFor(docContent: DocContent): List<HighlightInfo> = emptyList()

    /**
     * Builds a [HighlightInfo] object with the given parameters in the context of this processor.
     * Fills in [HighlightInfo.tagProcessorName] with the name of this processor.
     *
     * @see buildHighlightInfoWithDescription
     */
    protected fun buildHighlightInfo(
        vararg ranges: IntRange,
        type: HighlightType,
        description: String = "",
        related: List<HighlightInfo> = emptyList(),
    ): HighlightInfo =
        HighlightInfo(
            ranges = ranges.toList(),
            type = type,
            related = related,
            description = description,
            tagProcessorName = name,
        )

    /**
     * Builds a [HighlightInfo] object with the given parameters in the context of this processor.
     * Fills in [HighlightInfo.tagProcessorName] with the name of this processor.
     * Builds [HighlightInfo.description] from the [completionInfos] of this processor.
     * Mostly used for The @, tag name and brackets.
     *
     * @see buildHighlightInfo
     */
    protected fun buildHighlightInfoWithDescription(
        vararg ranges: IntRange,
        type: HighlightType,
        tag: String,
        related: List<HighlightInfo> = emptyList(),
    ): HighlightInfo =
        buildHighlightInfo(
            ranges = ranges,
            type = type,
            description = completionInfos // get the description from the completion infos
                .find { it.tag == tag }
                ?.let {
                    "${
                        (it.presentableBlockText ?: it.presentableInlineText)
                            ?.surroundWith("\"")
                            ?.plus(": ")
                            ?: ""
                    }${it.tailText}"
                } ?: "",
            related = related,
        )
}

fun findProcessors(fullyQualifiedNames: List<String>, arguments: Map<String, Any?>): List<DocProcessor> {
    val availableProcessors: Set<DocProcessor> = ServiceLoader.load(DocProcessor::class.java).toSet()

    val filteredProcessors = fullyQualifiedNames
        .mapNotNull { name ->
            availableProcessors.find { it::class.qualifiedName == name }
        }.map {
            // create a new instance of the processor, so it can safely be used multiple times
            // also pass on the arguments
            it::class.java
                .getDeclaredConstructor()
                .newInstance()
                .also { it.arguments = arguments }
        }

    return filteredProcessors
}
