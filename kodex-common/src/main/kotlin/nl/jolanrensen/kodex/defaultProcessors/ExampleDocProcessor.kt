package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.processor.TagDocProcessor
import nl.jolanrensen.kodex.utils.getTagArguments

class ExampleDocProcessor : TagDocProcessor() {

    /** We'll intercept @example tags. */
    override val providesTags: Set<String> = setOf("example")

    /** How `{@inner tags}` are processed. */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)

    /** How `  @normal tags` are processed. */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)

    // We can use the same function for both processInnerTagWithContent and processTagWithContent
    private fun processContent(tagWithContent: String): String {
        // We can log stuff if we want to using https://github.com/oshai/kotlin-logging
        logger.info { "Hi from the example logs!" }

        // We can get the content after the @example tag.
        val contentWithoutTag = tagWithContent
            .getTagArguments(tag = "example", numberOfArguments = 1)
            .single()
            .trimEnd() // remove trailing whitespaces/newlines

        // While we can play with the other arguments, let's just return some simple modified content
        var newContent =
            "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""

        // Since we trimmed all trailing newlines from the content, we'll add one back if they were there.
        if (tagWithContent.endsWith("\n")) {
            newContent += "\n"
        }

        return newContent
    }
}
