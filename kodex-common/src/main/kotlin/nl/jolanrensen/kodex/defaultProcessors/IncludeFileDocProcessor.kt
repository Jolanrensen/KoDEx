package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage.JAVA
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.processor.TagDocProcessor
import nl.jolanrensen.kodex.utils.getTagArguments
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import java.io.FileNotFoundException

/**
 * @see IncludeFileDocProcessor
 */
const val INCLUDE_FILE_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.IncludeFileDocProcessor"

/**
 * This tag doc processor will include the contents of a file in the docs.
 * For example:
 * `@includeFile (../../someFile.txt)`
 *
 * `@includeFile` keeps the content of the block below the includeFile statement intact.
 *
 * TODO: issue #9: Additional settings
 */
class IncludeFileDocProcessor : TagDocProcessor() {

    companion object {
        const val TAG = "includeFile"
    }

    override val providesTags: Set<String> = setOf(TAG)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = TAG,
                blockText = "@$TAG ()",
                presentableBlockText = "@$TAG (FILE)",
                moveCaretOffsetBlock = -1,
                inlineText = "{@$TAG ()}",
                presentableInlineText = "{@$TAG (FILE)}",
                moveCaretOffsetInline = -2,
                tailText = "Copy FILE content here. Use relative paths. Accepts 1 argument.",
            ),
        )

    private fun processContent(line: String, documentable: DocumentableWrapper): String {
        val includeFileArguments = line.getTagArguments(
            tag = TAG,
            numberOfArguments = 2,
        )
        val filePath = includeFileArguments
            .first()
            .trim()
            // TODO: issue #10: figure out how to make file location clickable both for Java and Kotlin
            .removePrefix("(")
            .removeSuffix(")")
            .trim()

        // for stuff written after the @includeFile tag, save and include it later
        val extraContent = includeFileArguments.getOrElse(1) { "" }

        val currentDir: File? = documentable.file.parentFile
        val targetFile = currentDir?.resolve(filePath)

        if (targetFile == null || !targetFile.exists()) {
            throw FileNotFoundException("File $filePath (-> ${targetFile?.absolutePath}) does not exist.")
        }

        if (targetFile.isDirectory) {
            throw IllegalArgumentException("File $filePath (-> ${targetFile.absolutePath}) is a directory.")
        }

        val content = targetFile.readText()

        return when (documentable.programmingLanguage) {
            JAVA ->
                StringEscapeUtils.escapeHtml4(content)
                    .replace("@", "&#64;")
                    .replace("*/", "&#42;&#47;")

            KOTLIN -> content
        }.let {
            if (extraContent.isNotEmpty()) {
                buildString {
                    append(it)
                    if (!extraContent.first().isWhitespace()) {
                        append(" ")
                    }
                    append(extraContent)
                }
            } else {
                it
            }
        }
    }

    /**
     * How to process the `{@includeFile (../tags)}` when it's inline.
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
     * How to process the `@includeFile tag` when it's a block tag.
     *
     * [tagWithContent] is the content after the `@includeFile tag`, e.g. `"(../../someFile.txt)"`
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
