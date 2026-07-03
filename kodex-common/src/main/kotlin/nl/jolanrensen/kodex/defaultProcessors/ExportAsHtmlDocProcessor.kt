package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.MutableDocumentableWrapper
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.processor.TagDocProcessor
import nl.jolanrensen.kodex.utils.getTagNameOrNull

/**
 * @see ExportAsHtmlDocProcessor
 */
const val EXPORT_AS_HTML_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.ExportAsHtmlDocProcessor"

/**
 * Adds `@exportAsHtmlStart` and `@exportAsHtmlEnd` tags that cam
 * specify a range of the doc to export to HTML for the [@ExportAsHtml][ExportAsHtml] annotation.
 *
 * - You can use both block- and inline tags.
 * - The range is inclusive, as the tags are stripped from the doc.
 * - The range is specified by the line number of the tag in the doc.
 * - Both tags are optional, if not specified, the start or end of the doc will be used.
 * - Don't use the same tag multiple times; only the first occurrence will be used.
 *
 * @see ExportAsHtml
 */
class ExportAsHtmlDocProcessor : TagDocProcessor() {

    companion object {
        const val EXPORT_AS_HTML_START = "exportAsHtmlStart"
        const val EXPORT_AS_HTML_END = "exportAsHtmlEnd"
    }

    override val providesTags: Set<String> = setOf(EXPORT_AS_HTML_START, EXPORT_AS_HTML_END)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = EXPORT_AS_HTML_START,
                inlineText = "{@$EXPORT_AS_HTML_START}",
                presentableInlineText = "{@$EXPORT_AS_HTML_START}",
                tailText = "Set start of @ExportAsHtml range. Takes no arguments.",
            ),
            CompletionInfo(
                tag = EXPORT_AS_HTML_END,
                inlineText = "{@$EXPORT_AS_HTML_END}",
                presentableInlineText = "{@$EXPORT_AS_HTML_END}",
                tailText = "Set end of @ExportAsHtml range. Takes no arguments.",
            ),
        )

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        val tag = tagWithContent.getTagNameOrNull() ?: return tagWithContent
        updateHtmlRangeInDoc(tag, documentable)
        val content = tagWithContent.trimStart().removePrefix("@$tag")
        return content
    }

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        val tag = tagWithContent.getTagNameOrNull() ?: return tagWithContent
        updateHtmlRangeInDoc(tag, documentable)
        val content = tagWithContent.removePrefix("{@$tag").removeSuffix("}")
        return content
    }

    private fun updateHtmlRangeInDoc(tag: String, documentable: DocumentableWrapper) {
        require(documentable is MutableDocumentableWrapper) {
            "DocumentableWrapper must be MutableDocumentableWrapper to use this processor."
        }
        val lineInDoc = documentable.docContent.value.lines().indexOfFirst {
            it.contains("@$tag")
        }
        when (tag) {
            EXPORT_AS_HTML_START -> documentable.htmlRangeStart = lineInDoc
            EXPORT_AS_HTML_END -> documentable.htmlRangeEnd = lineInDoc
        }
    }

    // as the tag keeps the content after it, we need to modify the background highlighting a bit
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

            // background, only include the attributes above
            this += buildHighlightInfo(
                rangeInDocContent.first..(rangeInDocContent.first + tagName.length),
                type = HighlightType.BACKGROUND,
            )
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

            // background, only include the attributes above
            this += buildHighlightInfo(
                rangeInDocContent.first..(rangeInDocContent.first + 1 + tagName.length),
                rangeInDocContent.last..rangeInDocContent.last,
                type = HighlightType.BACKGROUND,
                related = listOf(leftBracket, rightBracket),
            )
        }
}
