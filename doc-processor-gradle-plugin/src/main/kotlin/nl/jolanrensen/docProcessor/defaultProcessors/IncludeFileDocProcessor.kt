package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.getFileTarget
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.dokka.analysis.PsiDocumentableSource

/**
 * @see IncludeFileDocProcessor
 */
const val INCLUDE_FILE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeFileDocProcessor"

/**
 * This tag doc processor will include the contents of a file in the docs.
 * For example:
 * `@includeFile (../../someFile.txt)`
 *
 * TODO include settings for filtering in the file, optional triple quotes, etc.
 */
class IncludeFileDocProcessor : TagDocProcessor() {

    private val tag = "includeFile"
    override fun tagIsSupported(tag: String): Boolean =
        tag == this.tag

    private fun processContent(
        line: String,
        documentable: DocumentableWithSource,
        path: String
    ): String {
        val filePath = line.getFileTarget(tag)
        val currentDir = documentable.file.parentFile!!
        val targetFile = currentDir.resolve(filePath)

        if (!targetFile.exists()) error("IncludeFileProcessor ERROR: File $filePath (-> ${targetFile.absolutePath}) does not exist. Called from $path.")
        if (targetFile.isDirectory) error("IncludeFileProcessor ERROR: File $filePath (-> ${targetFile.absolutePath}) is a directory. Called from $path.")

        val content = targetFile.readText()
        val currentIsJava = documentable.source is PsiDocumentableSource

        return if (currentIsJava) {
            StringEscapeUtils.escapeHtml(content)
                .replace("@", "&#64;")
                .replace("*/", "&#42;&#47;")
        } else {
            content
        }
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = tagWithContent
        .split('\n')
        .mapIndexed { i, line ->
            if (i == 0) {
                processContent(
                    line = line,
                    documentable = documentable,
                    path = path
                )
            } else {
                line
            }
        }
        .joinToString("\n")
}