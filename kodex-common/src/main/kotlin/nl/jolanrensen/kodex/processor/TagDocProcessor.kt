package nl.jolanrensen.kodex.processor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.findBlockTagsWithRanges
import nl.jolanrensen.kodex.docContent.findInlineTagNamesWithRanges
import nl.jolanrensen.kodex.docContent.getTagNameOrNull
import nl.jolanrensen.kodex.docContent.splitPerBlockWithRanges
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.MutableDocumentableWrapper
import nl.jolanrensen.kodex.exceptions.TagDocProcessorFailedException
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.MutableDocumentablesByPath
import nl.jolanrensen.kodex.utils.getTagArgumentWithRangeByIndexOrNull

/**
 * Specific [DocProcessor] that processes just tags.
 *
 * Extending classes need to define which tags they can handle in [tagIsSupported],
 * how they handle block tags and inline tags in [processBlockTagWithContent] and [processInlineTagWithContent],
 * respectively.
 *
 * [filterDocumentables] can be overridden to change the value of `filteredDocumentables` in
 * [processBlockTagWithContent], and [processInlineTagWithContent] and which documentables are processed at all.
 *
 * [onProcessError] can be overridden to change the error message when the process limit is reached
 * (defined in [ProcessDocsAction.Parameters.processLimit] and enforced in [shouldContinue]).
 *
 * [shouldContinue] can also be overridden to change the conditions under which the process's while-loops should
 * continue or stop and when an error should be thrown.
 * By default, it will throw an error if the process limit (defined in [ProcessDocsAction.Parameters.processLimit])
 * is reached and if supported tags are present but no modifications are made (indicating an infinite loop).
 */
abstract class TagDocProcessor : DocProcessor() {

    /**
     * The tags this processor offers, like "sample".
     *
     * This may not be the same as the tags it can actually handle ([tagIsSupported]),
     * but will be used in autocompletion in the IDE.
     */
    abstract val providesTags: Set<String>

    /** The tags to be replaced, like "sample", checks [providesTags] by default but can be overridden. */
    open fun tagIsSupported(tag: String): Boolean = tag in providesTags

    /** Returns true if [tagIsSupported] is in this [DocumentableWrapper]. */
    val DocumentableWrapper.hasSupportedTag
        get() = tags.any(::tagIsSupported)

    /**
     * An optionally modified list of [CompletionInfo] information to modify how the tags
     * of this [TagDocProcessor] are displayed in the autocomplete of the IDE.
     */
    override val completionInfos: List<CompletionInfo>
        get() = providesTags.map { CompletionInfo(it, name) }

    protected lateinit var mutableDocumentablesByPath: MutableDocumentablesByPath

    /**
     * Allows you to access the documentables to be processed as well as to gain
     * the ability to query for any documentable in the source.
     */
    val documentablesByPath: DocumentablesByPath
        get() = mutableDocumentablesByPath

    /**
     * Optionally, you can filter the [DocumentableWrapper]s that will appear in
     * [processBlockTagWithContent] and [processInlineTagWithContent] as `filteredDocumentables`.
     * This can be useful as an optimization.
     *
     * This filter will also be applied to all the docs that are processed. Normally, all docs with
     * a supported tag will be processed. If you want to filter them more strictly, override this method too.
     */
    open fun <T : DocumentableWrapper> filterDocumentablesToProcess(documentable: T): Boolean = true

    open fun <T : DocumentableWrapper> filterDocumentablesToQuery(documentable: T): Boolean = true

    /**
     * You can optionally sort the documentables to optimize the order in which they are processed.
     * [TagDocAnalyser] can be used for that.
     */
    open suspend fun <T : DocumentableWrapper> sortDocumentables(
        documentables: List<T>,
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): Iterable<T> = documentables

    /**
     * Whether this processor can process multiple documentables in parallel.
     * If `true`, the processor will be run in parallel for each documentable.
     * If `false`, the processor will be run sequentially for each documentable.
     */
    open val canProcessParallel: Boolean = true

    /**
     * Provide a meaningful error message when the given process limit is reached.
     *
     * @return [Nothing] must throw an error
     * @throws [IllegalStateException] On proces limit reached
     */
    @Throws(IllegalStateException::class)
    open fun onProcessError(): Nothing = error("Process limit reached")

    /**
     * Check the recursion limit to break the while loop.
     * By default, it will call [onProcessError] if the process limit (defined in [ProcessDocsAction.Parameters.processLimit])
     * is reached and if supported tags are present but no modifications are made (indicating an infinite loop).
     *
     * @param [i] The current iteration.
     * @param [anyModifications] `true` if any modifications were made, `false` otherwise.
     * @param [processLimit] The process limit from [ProcessDocsAction.Parameters].
     * @return `true` if recursion limit is reached, `false` otherwise.
     */
    @Throws(IllegalStateException::class)
    open fun shouldContinue(i: Int, anyModifications: Boolean, processLimit: Int): Boolean {
        val processLimitReached = i >= processLimit

        val hasSupportedTags = mutableDocumentablesByPath
            .documentablesToProcess
            .any { it.value.any { it.hasSupportedTag } }
        val atLeastOneRun = i > 0
        val tagsArePresentButNoModifications = hasSupportedTags && !anyModifications && atLeastOneRun

        // Throw error if process limit is reached or if supported tags keep being present but no modifications are made
        if (processLimitReached || tagsArePresentButNoModifications) {
            onProcessError()
        }

        return hasSupportedTags
    }

    /**
     * Process a block tag with content ([tagWithContent]) into whatever you like.
     *
     * @param [tagWithContent] Tag with content block to process.
     *  For example: `@tag Some content`
     *  Can contain newlines, and does include tag.
     *  The block continues until the next tag block starts.
     * @param [path] The path of the doc where the tag is found.
     * @param [documentable] The Documentable belonging to the current tag.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String

    /**
     * Process an inline tag with content ([tagWithContent]) into whatever you like.
     *
     * @param [tagWithContent] Tag with content to process.
     *  For example: `{@tag Some content}`
     *  Contains {} and tag.
     * @param [path] The path of the doc where the tag is found.
     * @param [documentable] The Documentable beloning to the current tag.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String

    /**
     * This is the main function that will be called by the [DocProcessor].
     * It will process all documentables found in the source set and replace all
     * supported tags ([tagIsSupported]) and their content with the result of [processBlockTagWithContent]
     * and [processInlineTagWithContent].
     * The processing will continue until there are no more supported tags found in all
     * filtered documentables. This means that recursion (a.k.a tags that create other supported tags)
     * is possible. However, there is a limit to prevent infinite recursion ([ProcessDocsAction.Parameters.processLimit]).
     */
    override suspend fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        coroutineScope {
            // Convert to mutable
            mutableDocumentablesByPath = documentablesByPath
                .toMutable()
                .withQueryFilter(::filterDocumentablesToQuery)
                .withDocsToProcessFilter(::filterDocumentablesToProcess)

            // Main recursion loop that will continue until all supported tags are replaced
            // or the process limit is reached.
            var i = 0
            while (true) {
                val filteredDocumentablesWithTag = mutableDocumentablesByPath
                    .documentablesToProcess
                    .flatMap { it.value }
                    .filter { it.hasSupportedTag }
                    .distinctBy { it.identifier }
                    .let { sortDocumentables(it, processLimit, documentablesByPath) }

                var anyModifications = false
                if (canProcessParallel) {
                    anyModifications = filteredDocumentablesWithTag
                        .mapNotNull { documentable ->
                            if (!documentable.hasSupportedTag) {
                                null
                            } else {
                                async {
                                    processDocumentable(documentable, processLimit)
                                }
                            }
                        }.awaitAll()
                        .any { it }
                } else {
                    for (documentable in filteredDocumentablesWithTag) {
                        if (!documentable.hasSupportedTag) continue
                        anyModifications = processDocumentable(documentable, processLimit)
                    }
                }

                val shouldContinue = shouldContinue(
                    i = i++,
                    anyModifications = anyModifications,
                    processLimit = processLimit,
                )
                if (!shouldContinue) break
            }

            return@coroutineScope mutableDocumentablesByPath
        }

    protected fun processDocumentable(documentable: MutableDocumentableWrapper, processLimit: Int): Boolean {
        val docContent = documentable.docContent
        val processedDoc = processTagsInContent(
            docContent = docContent,
            path = documentable.fullyQualifiedPath,
            documentable = documentable,
            processLimit = processLimit,
        )

        val wasModified = docContent != processedDoc
        if (wasModified) {
            documentable.modifyDocContentAndUpdate(processedDoc)
        }
        return wasModified
    }

    private fun processTagsInContent(
        docContent: DocContent,
        path: String,
        documentable: MutableDocumentableWrapper,
        processLimit: Int,
    ): DocContent {
        logger.info { "Processing inline tags in doc at '$path'" }

        // Process the inline tags first
        val processedInlineTagsDoc: DocContent = run {
            var text = docContent

            var i = 0
            while (true) {
                val inlineTagNames = text
                    .findInlineTagNamesWithRanges()
                    .filter { (tagName, _) -> tagIsSupported(tagName) }

                if (inlineTagNames.isEmpty()) break

                var wasModified = false
                for ((_, range) in inlineTagNames) {
                    val tagContent = text.value.substring(range)

                    // sanity check
                    require(tagContent.startsWith("{@") && tagContent.endsWith("}")) {
                        "Tag content must start with '{@' and end with '}'"
                    }

                    val newTagContent = try {
                        processInlineTagWithContent(
                            tagWithContent = tagContent,
                            path = path,
                            documentable = documentable,
                        )
                    } catch (e: Throwable) {
                        throw TagDocProcessorFailedException(
                            processorName = name,
                            documentable = documentable,
                            currentDoc = text,
                            rangeInCurrentDoc = range,
                            cause = e,
                        )
                    }
                    text = text.value.replaceRange(range, newTagContent).asDocContent()

                    wasModified = tagContent != newTagContent

                    // Restart the loop since ranges might have changed,
                    // continue if there were no modifications
                    if (wasModified) break
                }

                // while true safety check
                val shouldContinue = shouldContinue(
                    i = i++,
                    anyModifications = wasModified,
                    processLimit = processLimit,
                )
                if (!shouldContinue) break
            }

            return@run text
        }

        logger.info { "Processing block tags in doc at '$path'" }

        // Then process the normal tags
        val processedDoc: DocContent = processedInlineTagsDoc
            .splitPerBlockWithRanges()
            .map { (split, rangeDocContent) ->
                val shouldProcess =
                    split.value.trimStart().startsWith("@") &&
                        split.getTagNameOrNull()
                            ?.let(::tagIsSupported) == true

                if (shouldProcess) {
                    try {
                        processBlockTagWithContent(
                            tagWithContent = split.value,
                            path = path,
                            documentable = documentable,
                        ).asDocContent()
                    } catch (e: Throwable) {
                        throw TagDocProcessorFailedException(
                            processorName = name,
                            documentable = documentable,
                            currentDoc = processedInlineTagsDoc,
                            rangeInCurrentDoc = rangeDocContent,
                            cause = e,
                        )
                    }
                } else {
                    split
                }
            }.let { list ->
                // skip empty blocks, making sure to keep the first and last blocks
                // even if they are empty, to preserve original newlines
                list.filterIndexed { i, it ->
                    i == 0 || i == list.lastIndex || it.value.isNotEmpty()
                }
            }.joinToString("\n").asDocContent()

        return processedDoc
    }

    fun getArgumentHighlightOrNull(
        argumentIndex: Int,
        rangeInDocContent: IntRange,
        docContent: DocContent,
        tagName: String,
        numberOfArguments: Int,
        type: HighlightType,
        related: List<HighlightInfo> = emptyList(),
    ): HighlightInfo? {
        val line = docContent.value.substring(rangeInDocContent)
        val (_, rangeInLine) = line.getTagArgumentWithRangeByIndexOrNull(
            index = argumentIndex,
            tag = tagName,
            numberOfArguments = numberOfArguments,
        ) ?: return null

        return buildHighlightInfo(
            rangeInLine.first + rangeInDocContent.first..rangeInLine.last + rangeInDocContent.first,
            type = type,
            related = related,
        )
    }

    protected open fun getHighlightsForInlineTag(
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

            // background
            this += buildHighlightInfo(
                rangeInDocContent,
                type = HighlightType.BACKGROUND,
                related = listOf(leftBracket, rightBracket),
            )
        }

    protected open fun getHighlightsForBlockTag(
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

            // background
            this += buildHighlightInfo(
                rangeInDocContent,
                type = HighlightType.BACKGROUND,
            )
        }

    override fun getHighlightsFor(docContent: DocContent): List<HighlightInfo> =
        buildList {
            // {@inline tags}
            val inlineTags = docContent.findInlineTagNamesWithRanges()
            for ((tagName, range) in inlineTags) {
                if (!tagIsSupported(tagName)) continue
                this += getHighlightsForInlineTag(tagName, range, docContent)
            }

            // @block tags
            val blockTags = docContent.findBlockTagsWithRanges()
            for ((tagName, range) in blockTags) {
                if (!tagIsSupported(tagName)) continue
                this += getHighlightsForBlockTag(tagName, range, docContent)
            }
        }
}
