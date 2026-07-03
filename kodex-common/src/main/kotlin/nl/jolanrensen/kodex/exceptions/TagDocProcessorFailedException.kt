package nl.jolanrensen.kodex.exceptions

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.toDocText
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.utils.coerceAtMost
import nl.jolanrensen.kodex.utils.getLineAndCharacterOffset

/**
 * This exception is thrown when a [TagDocProcessor] fails to process a tag.
 * It contains the current state of the doc, the range of the tag that failed to process,
 * and the content of the tag that failed to process.
 */
open class TagDocProcessorFailedException(
    processorName: String,
    val documentable: DocumentableWrapper,
    val currentDoc: DocContent,
    val rangeInCurrentDoc: IntRange,
    cause: Throwable? = null,
) : DocProcessorFailedException(
        processorName = processorName,
        cause = cause,
        message = renderMessage(
            documentable = documentable,
            rangeInCurrentDoc = rangeInCurrentDoc,
            processorName = processorName,
            currentDoc = currentDoc,
            cause = cause,
        ),
    ) {
    companion object {
        internal fun renderMessage(
            documentable: DocumentableWrapper,
            rangeInCurrentDoc: IntRange,
            processorName: String,
            currentDoc: DocContent,
            cause: Throwable?,
        ): String =
            try {
                buildString {
                    val docRangeInFile = documentable.docFileTextRange
                    val fileText = documentable.file.readText()
                    val (docLine, docChar) = fileText.getLineAndCharacterOffset(docRangeInFile.first)
                    val (exceptionLine, exceptionChar) = fileText.getLineAndCharacterOffset(rangeInCurrentDoc.first)

                    fun highlightException(it: String) = "<a href=\"\">$it</a>"

                    appendLine("Doc processor $processorName failed processing doc:")
                    appendLine("Doc location: ${documentable.file.absolutePath}:$docLine:$docChar")
                    appendLine("Exception location: ${documentable.file.absolutePath}:$exceptionLine:$exceptionChar")
                    appendLine(
                        "Tag throwing the exception: ${
                            highlightException(
                                currentDoc.value.substring(
                                    rangeInCurrentDoc.coerceAtMost(
                                        currentDoc.value.lastIndex,
                                    ),
                                ),
                            )
                        }",
                    )
                    cause?.message?.let {
                        appendLine("Reason for the exception: $it")
                    }
                    appendLine("\u200E")
                    appendLine("Current state of the doc with the ${highlightException("cause for the exception")}:")
                    appendLine("--------------------------------------------------")
                    appendLine(
                        try {
                            currentDoc.value.replaceRange(
                                range = rangeInCurrentDoc.coerceAtMost(currentDoc.value.lastIndex),
                                replacement = highlightException(
                                    currentDoc.value.substring(
                                        rangeInCurrentDoc.coerceAtMost(currentDoc.value.lastIndex),
                                    ),
                                ),
                            ).asDocContent()
                        } catch (e: Throwable) {
                            currentDoc
                        }.toDocText(),
                    )
                    appendLine("--------------------------------------------------")
                }
            } catch (renderMessageException: Throwable) {
                "Failed to render message for TagDocProcessorFailedException: ${renderMessageException.message}."
            }
    }

    fun renderMessage(): String =
        renderMessage(
            documentable = documentable,
            rangeInCurrentDoc = rangeInCurrentDoc,
            processorName = processorName,
            currentDoc = currentDoc,
            cause = cause,
        )

    fun renderDoc(): DocContent =
        try {
            buildString {
                val docRangeInFile = documentable.docFileTextRange
                val fileText = documentable.file.readText()
                val (docLine, docChar) = fileText.getLineAndCharacterOffset(docRangeInFile.first)
                val (exceptionLine, exceptionChar) = fileText.getLineAndCharacterOffset(rangeInCurrentDoc.first)

                fun highlightException(it: String) = "!!!$it!!!"

                val indent = "&nbsp;&nbsp;&nbsp;&nbsp;"
                val lineBreak = "\n$indent\n"

                appendLine("# Error in DocProcessor")
                appendLine("## Doc processor $processorName failed processing doc.")
                appendLine()
                appendLine("### Doc location:")
                appendLine()
                appendLine("`${documentable.file.path}:$docLine:$docChar`")
                appendLine(lineBreak)
                appendLine("### Exception location:")
                appendLine()
                appendLine("`${documentable.file.absolutePath}:$exceptionLine:$exceptionChar`")
                appendLine(lineBreak)
                appendLine("### Tag throwing the exception:")
                appendLine()
                appendLine(
                    "**`${currentDoc.value.substring(rangeInCurrentDoc.coerceAtMost(currentDoc.value.lastIndex))}`**",
                )
                appendLine(lineBreak)
                appendLine("### Reason for the exception:")
                appendLine()
                cause?.message?.let {
                    for (line in it.lines()) {
                        appendLine(line)
                        appendLine()
                    }
                }
                appendLine(lineBreak)
                appendLine("### Stack Trace")
                cause?.stackTrace?.let {
                    for (line in it.joinToString("\n").lines()) {
                        appendLine(line)
                        appendLine()
                    }
                }
                appendLine(lineBreak)
                appendLine("Current state of the doc with the `${highlightException("cause for the exception")}`:")
                appendLine()
                appendLine("--------------------------------------------------")
                appendLine("```")
                appendLine(
                    try {
                        currentDoc.value.replaceRange(
                            range = rangeInCurrentDoc,
                            replacement = highlightException(
                                currentDoc.value.substring(rangeInCurrentDoc.coerceAtMost(currentDoc.value.lastIndex)),
                            ),
                        ).asDocContent()
                    } catch (e: Throwable) {
                        currentDoc
                    }.toDocText(),
                )
                appendLine("```")
                appendLine("--------------------------------------------------")
            }.asDocContent()
        } catch (e: Throwable) {
            "Failed to render message as KDoc for TagDocProcessorFailedException: ${e.message}, $message."
                .asDocContent()
        }
}
