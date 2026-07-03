package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.toDocumentablesByPath

/**
 * @see NoDocProcessor
 */
const val NO_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.NoDocProcessor"

/**
 * A doc processor that simply removes all docs from the sources.
 */
class NoDocProcessor : DocProcessor() {
    override suspend fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        documentablesByPath
            .documentablesToProcess
            .map { (path, documentables) ->
                path to documentables.map {
                    it.copy(
                        tags = emptySet(),
                        docContent = "".asDocContent(),
                        isModified = true,
                    )
                }
            }.toDocumentablesByPath(documentablesByPath.loadedProcessors)
}
