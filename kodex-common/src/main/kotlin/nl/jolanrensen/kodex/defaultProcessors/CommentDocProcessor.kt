package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.processor.TagDocProcessor

/**
 * @see CommentDocProcessor
 */
const val COMMENT_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.CommentDocProcessor"

/**
 * Adds `{@comment tags}` that will be removed from the docs upon processing.
 *
 * For example:
 * ```kotlin
 * /**
 * * {@comment This is a comment}
 * * This is not a comment
 * * @comment This is also a comment
 * * and this too
 * * @otherTag This is not a comment
 * */
 * ```
 * would turn into
 * ```kotlin
 * /**
 * * This is not a comment
 * *
 * * @otherTag This is not a comment
 * */
 * ```
 */
class CommentDocProcessor : TagDocProcessor() {

    companion object {
        const val TAG = "comment"
    }

    override val providesTags: Set<String> = setOf(TAG)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = TAG,
                tailText = "Comment something. Will be removed from the docs. Takes any number of arguments.",
            ),
        )

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = ""

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = ""

    override fun getHighlightsForInlineTag(
        tagName: String,
        rangeInDocContent: IntRange,
        docContent: DocContent,
    ): List<HighlightInfo> =
        buildList {
            // '{'
            val leftBracket = buildHighlightInfoWithDescription(
                rangeInDocContent.first..rangeInDocContent.first,
                type = HighlightType.COMMENT,
                tag = TAG,
            )

            // '@' and tag name
            this += buildHighlightInfoWithDescription(
                (rangeInDocContent.first + 1)..(rangeInDocContent.first + 1 + tagName.length),
                type = HighlightType.COMMENT_TAG,
                tag = TAG,
            )

            // comment contents
            this += buildHighlightInfo(
                (rangeInDocContent.first + 1 + tagName.length + 1)..rangeInDocContent.last - 1,
                type = HighlightType.COMMENT,
            )

            // '}
            val rightBracket = buildHighlightInfoWithDescription(
                rangeInDocContent.last..rangeInDocContent.last,
                type = HighlightType.COMMENT,
                tag = TAG,
            )

            // link left and right brackets
            this += leftBracket.copy(related = listOf(rightBracket))
            this += rightBracket.copy(related = listOf(leftBracket))

            // background
            this += buildHighlightInfo(
                rangeInDocContent,
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
                type = HighlightType.COMMENT_TAG,
                tag = TAG,
            )

            // comment contents
            this += buildHighlightInfo(
                (rangeInDocContent.first + 1 + tagName.length)..rangeInDocContent.last,
                type = HighlightType.COMMENT,
            )

            // background
            this += buildHighlightInfo(
                rangeInDocContent,
                type = HighlightType.BACKGROUND,
            )
        }
}
