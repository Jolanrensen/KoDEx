package nl.jolanrensen.kodex.processor

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.toMutable
import nl.jolanrensen.kodex.query.DocumentablesByPath

/**
 * Version of TagDocProcessor which does not modify any tags,
 * but instead just allows you to visit all tags.
 */
abstract class TagDocAnalyser<out R> : TagDocProcessor() {

    abstract fun analyseBlockTagWithContent(tagWithContent: String, path: String, documentable: DocumentableWrapper)

    abstract fun analyseInlineTagWithContent(tagWithContent: String, path: String, documentable: DocumentableWrapper)

    abstract fun getAnalyzedResult(): R

    suspend fun analyzeSafely(processLimit: Int, documentablesByPath: DocumentablesByPath): TagDocAnalyser<R> {
        processSafely(processLimit, documentablesByPath)
        return this
    }

    final override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        analyseBlockTagWithContent(tagWithContent, path, documentable)
        return tagWithContent
    }

    final override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        analyseInlineTagWithContent(tagWithContent, path, documentable)
        return tagWithContent
    }

    protected final fun analyzeDocumentable(documentable: DocumentableWrapper, processLimit: Int): Boolean =
        processDocumentable(documentable.toMutable(), processLimit)

    final override suspend fun process(
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): DocumentablesByPath = super.process(processLimit, documentablesByPath)

    // we only need one iteration for analyzing as no modifications can be made
    final override fun shouldContinue(i: Int, anyModifications: Boolean, processLimit: Int): Boolean {
        val processLimitReached = i >= processLimit

        // Throw error if process limit is reached or if supported tags keep being present but no modifications are made
        if (processLimitReached) {
            onProcessError()
        }

        return false
    }
}
