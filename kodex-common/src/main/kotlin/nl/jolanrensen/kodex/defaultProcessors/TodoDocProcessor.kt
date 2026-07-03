package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.toDocumentablesByPath

/**
 * @see TodoDocProcessor
 */
const val TODO_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.TodoDocProcessor"

/**
 * A doc processor that adds a doc with `TODO`
 * where the docs are missing.
 */
class TodoDocProcessor : DocProcessor() {
    override suspend fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        documentablesByPath
            .documentablesToProcess
            .map { (path, documentables) ->
                path to documentables.map {
                    if (it.docContent.value.isBlank() || !it.sourceHasDocumentation) {
                        it.copy(
                            docContent = "TODO".asDocContent(),
                            isModified = true,
                        )
                    } else {
                        it
                    }
                }
            }.toDocumentablesByPath(documentablesByPath.loadedProcessors)
}
