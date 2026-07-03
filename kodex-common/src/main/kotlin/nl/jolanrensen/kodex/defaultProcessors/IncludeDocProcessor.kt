package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContent
import nl.jolanrensen.kodex.docContent.javaLinkRegex
import nl.jolanrensen.kodex.docContent.replaceKdocLinks
import nl.jolanrensen.kodex.docContent.toDocText
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage.JAVA
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.kodex.documentableWrapper.getAllFullPathsFromHereForTargetPath
import nl.jolanrensen.kodex.documentableWrapper.queryDocumentables
import nl.jolanrensen.kodex.documentableWrapper.queryDocumentablesForPath
import nl.jolanrensen.kodex.documentableWrapper.queryFileForDocTextRange
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.processor.TagDocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.withoutFilters
import nl.jolanrensen.kodex.utils.decodeCallableTarget
import nl.jolanrensen.kodex.utils.getTagArguments
import org.apache.commons.text.StringEscapeUtils
import org.jgrapht.traverse.NotDirectedAcyclicGraphException
import org.jgrapht.traverse.TopologicalOrderIterator
import kotlin.collections.plusAssign

/**
 * @see IncludeDocProcessor
 */
const val INCLUDE_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor"

const val INCLUDE_DOC_PROCESSOR_PRE_SORT = "$INCLUDE_DOC_PROCESSOR.PRE_SORT"

/**
 * Allows you to @include docs from other linkable elements.
 * `@include` keeps the content of the block below the include statement intact.
 *
 * For example:
 * ```kotlin
 * /**
 *  * @include [SomeClass]
 *  * Hi!
 *  */
 * ```
 * would turn into
 * ```kotlin
 * /**
 *  * This is the docs of SomeClass
 *  * @see [SomeOtherClass][com.example.somePackage.SomeOtherClass]
 *  * Hi!
 *  */
 * ```
 * NOTE: `[links]` that are present in included docs are recognized and replaced by their
 * fully qualified names, so that they still work in the docs. If you don't want this to happen,
 * simply break the link like [this\]. The escape character can be removed by [RemoveEscapeCharsProcessor] and the link will
 * not be replaced.
 *
 * NOTE: Newlines at the beginning and end of included doc are removed, making including:
 * ```kotlin
 * /** Hello */
 * ```
 * equivalent to including:
 * ```kotlin
 * /**
 *  * Hello
 *  */
 * ```
 *
 * NOTE: If you need to substitute something in the included docs, you can use [ARG_DOC_PROCESSOR] in addition to this.
 * For example:
 *
 * File A:
 * ```kotlin
 * /** NOTE: The {@get operation} operation is part of the public API. */
 *  internal interface ApiNote
 * ```
 *
 * File B:
 *```kotlin
 * /**
 *  * Some docs
 *  * @include [ApiNote]
 *  * @setArg operation update
 *  */
 * fun update() {}
 * ```
 *
 * File B after processing:
 * ```kotlin
 * /**
 *  * Some docs
 *  * NOTE: The update operation is part of the public API.
 *  */
 * fun update() {}
 * ```
 *
 */
class IncludeDocProcessor : TagDocProcessor() {

    companion object {
        const val TAG = "include"
    }

    override val providesTags: Set<String> = setOf(TAG)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = TAG,
                blockText = "@$TAG []",
                presentableBlockText = "@$TAG [ELEMENT]",
                moveCaretOffsetBlock = -1,
                inlineText = "{@$TAG []}",
                presentableInlineText = "{@$TAG [ELEMENT]}",
                moveCaretOffsetInline = -2,
                tailText = "Copy KDocs of ELEMENT here. Accepts 1 argument.",
            ),
        )

    /**
     * Filter documentables to only include linkable elements (classes, functions, properties, etc) and
     * have any documentation. This will save performance when looking up the target of the @include tag.
     */
    override fun <T : DocumentableWrapper> filterDocumentablesToProcess(documentable: T): Boolean =
        documentable.sourceHasDocumentation

    /**
     * Filter documentables to only include linkable elements (classes, functions, properties, etc) and
     * have any documentation. This will save performance when looking up the target of the @include tag.
     */
    override fun <T : DocumentableWrapper> filterDocumentablesToQuery(documentable: T): Boolean =
        documentable.sourceHasDocumentation

    /**
     * Documentables interact, so no parallel processing is possible.
     */
    override val canProcessParallel: Boolean = false

    /**
     * Provides a helpful message when a circular reference is detected.
     */
    override fun onProcessError(): Nothing {
        val circularRefs = documentablesByPath
            .documentablesToProcess
            .filter { it.value.any { it.hasSupportedTag } }
            .entries
            .joinToString("\n\n") { (path, documentables) ->
                buildString {
                    appendLine("$path:")
                    appendLine(
                        documentables.joinToString("\n\n") {
                            it.queryFileForDocTextRange().asDocTextOrNull()
                                ?.getDocContent()
                                ?.toDocText(4)?.value
                                ?: ""
                        },
                    )
                }
            }
        error("Circular references detected in @include statements:\n$circularRefs")
    }

    /**
     * Queries the path targeted by the @include tag and returns the docs of that element to
     * overwrite the @include tag.
     */
    private fun processContent(line: String, documentable: DocumentableWrapper): String {
        val unfilteredDocumentablesByPath by lazy { documentablesByPath.withoutFilters() }
        val includeArguments = line.getTagArguments(tag = TAG, numberOfArguments = 2)
        val includePath = includeArguments.first().decodeCallableTarget()
        // for stuff written after the @include tag, save and include it later
        val extraContent = includeArguments.getOrElse(1) { "" }

        logger.debug {
            "Running include processor for ${
                documentable.fullyQualifiedPath
            }/${documentable.fullyQualifiedExtensionPath}, line @include $includePath"
        }

        // query the filtered documentables for the @include paths
        val targetDocumentable = documentable.queryDocumentables(
            query = includePath,
            documentables = documentablesByPath,
            documentablesNoFilters = unfilteredDocumentablesByPath,
            // for IntelliJ plugin
            canBeCache = true,
        ) { it.identifier != documentable.identifier }

        if (targetDocumentable == null) {
            val targetDocumentableNoFilter = documentable.queryDocumentables(
                query = includePath,
                documentables = documentablesByPath,
                documentablesNoFilters = unfilteredDocumentablesByPath,
            )
            val attemptedQueries = documentable.getAllFullPathsFromHereForTargetPath(
                targetPath = includePath,
                documentablesNoFilters = unfilteredDocumentablesByPath,
            ).joinToString("\n") { "|  $it" }

            val targetPath = documentable.queryDocumentablesForPath(
                query = includePath,
                documentables = unfilteredDocumentablesByPath,
                documentablesNoFilters = unfilteredDocumentablesByPath,
            )

            error(
                when {
                    targetDocumentableNoFilter == documentable ->
                        "Self-reference detected."

                    targetPath != null ->
                        """
                        |Reference found, but no documentation found for: "$includePath".
                        |Including documentation from outside the library or from type-aliases is currently not supported.
                        |Attempted queries: [
                        $attemptedQueries
                        |]
                        """.trimMargin()

                    else ->
                        """
                        |Reference not found: "$includePath".
                        |Attempted queries: [
                        $attemptedQueries
                        |]
                        """.trimMargin()
                },
            )
        }

        var targetContent = targetDocumentable.docContent
            .value
            .removePrefix("\n")
            .removeSuffix("\n")
            .asDocContent()

        targetContent = when (documentable.programmingLanguage) {
            // if the content contains links to other elements, we need to expand the path
            // providing the original name or alias as new alias.
            KOTLIN -> targetContent.replaceKdocLinks { query ->
                targetDocumentable.queryDocumentablesForPath(
                    query = query,
                    documentables = unfilteredDocumentablesByPath,
                    documentablesNoFilters = unfilteredDocumentablesByPath,
                    pathIsValid = { path, it ->
                        // ensure that the given path points to the same element in the destination place and
                        // that it's queryable
                        documentable.queryDocumentables(
                            query = path,
                            documentables = unfilteredDocumentablesByPath,
                            documentablesNoFilters = unfilteredDocumentablesByPath,
                        ) == it // TODO? not sure why identifier check gets the wrong results here
                    },
                ) ?: query
            }

            JAVA -> {
                // TODO: issue #8: Expanding Java reference links
                if (javaLinkRegex in targetContent.value) {
                    logger.warn {
                        "Java {@link statements} are not replaced by their fully qualified path. " +
                            "Make sure to use fully qualified paths in {@link statements} when " +
                            "@including docs with {@link statements}."
                    }
                }

                // Escape HTML characters in Java docs
                StringEscapeUtils.escapeHtml4(targetContent.value)
                    .replace("@", "&#64;")
                    .replace("*/", "&#42;&#47;")
                    .asDocContent()
            }
        }

        if (extraContent.isNotEmpty()) {
            targetContent = buildString {
                append(targetContent)
                if (!extraContent.first().isWhitespace()) {
                    append(" ")
                }
                append(extraContent)
            }.asDocContent()
        }

        // replace the include statement with the kdoc of the queried node (if found)
        return targetContent.value
    }

    /**
     * How to process the `{@include tag}` when it's inline.
     *
     * [processContent] can handle inner tags perfectly fine.
     */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String =
        processContent(
            line = tagWithContent,
            documentable = documentable,
        )

    /**
     * How to process the `@include tag` when it's a block tag.
     *
     * [tagWithContent] is the content after the `@include tag`, e.g. `"[SomeClass]"`
     * including any new lines below.
     * We will only replace the first line and skip the rest.
     */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String =
        processContent(
            line = tagWithContent,
            documentable = documentable,
        )

    override suspend fun <T : DocumentableWrapper> sortDocumentables(
        documentables: List<T>,
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): Iterable<T> {
        val preSort = arguments[INCLUDE_DOC_PROCESSOR_PRE_SORT] as? Boolean ?: true
        if (!preSort) return documentables

        val dag = IncludeDocAnalyzer.getAnalyzedResult(processLimit, documentablesByPath)
        val orderedList = try {
            TopologicalOrderIterator(dag).asSequence().mapTo(mutableListOf()) { it.identifier }
        } catch (e: NotDirectedAcyclicGraphException) {
            return documentables
        }

        return documentables.sortedBy { doc ->
            orderedList.indexOf(doc.identifier)
        }
    }

    override fun getHighlightsForInlineTag(
        tagName: String,
        rangeInDocContent: IntRange,
        docContent: DocContent,
    ): List<HighlightInfo> =
        buildList {
            // Left '{'
            val leftBracket = buildHighlightInfoWithDescription(
                rangeInDocContent.first..rangeInDocContent.first,
                type = HighlightType.BRACKET,
                tag = tagName,
            )

            // '@' and tag name
            this += buildHighlightInfoWithDescription(
                (rangeInDocContent.first + 1)..(rangeInDocContent.first + 1 + tagName.length),
                type = HighlightType.TAG,
                tag = tagName,
            )

            // Right '}'
            val rightBracket = buildHighlightInfoWithDescription(
                rangeInDocContent.last..rangeInDocContent.last,
                type = HighlightType.BRACKET,
                tag = tagName,
            )

            // Linking brackets
            this += leftBracket.copy(related = listOf(rightBracket))
            this += rightBracket.copy(related = listOf(leftBracket))

            // [Key]
            val key = getArgumentHighlightOrNull(
                argumentIndex = 0,
                docContent = docContent,
                rangeInDocContent = rangeInDocContent,
                tagName = tagName,
                numberOfArguments = 2,
                type = HighlightType.TAG_KEY,
            )
            if (key != null) this += key

            // background, only include the attributes above
            this += buildHighlightInfo(
                rangeInDocContent.first..(key?.ranges?.last()?.last ?: (rangeInDocContent.first + tagName.length)),
                rangeInDocContent.last..rangeInDocContent.last,
                type = HighlightType.BACKGROUND,
                related = listOf(leftBracket, rightBracket),
            )
        }

    override fun getHighlightsForBlockTag(
        tagName: String,
        rangeInDocContent: IntRange,
        docContent: DocContent,
    ): List<HighlightInfo> =
        buildList {
            // '@' and tag name
            this += buildHighlightInfoWithDescription(
                rangeInDocContent.first..(rangeInDocContent.first + tagName.length),
                type = HighlightType.TAG,
                tag = tagName,
            )

            // [Key]
            val key = getArgumentHighlightOrNull(
                argumentIndex = 0,
                docContent = docContent,
                rangeInDocContent = rangeInDocContent,
                tagName = tagName,
                numberOfArguments = 2,
                type = HighlightType.TAG_KEY,
            )
            if (key != null) this += key

            // background, only include the attributes above
            this += buildHighlightInfo(
                rangeInDocContent.first..(key?.ranges?.last()?.last ?: (rangeInDocContent.first + tagName.length)),
                type = HighlightType.BACKGROUND,
            )
        }
}
