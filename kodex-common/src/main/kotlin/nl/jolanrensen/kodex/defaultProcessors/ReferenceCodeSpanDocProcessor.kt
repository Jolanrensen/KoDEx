package nl.jolanrensen.kodex.defaultProcessors

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.replaceKdocAliases
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.utils.surroundWith

const val REFERENCE_CODE_SPAN_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.ReferenceCodeSpanDocProcessor"

class ReferenceCodeSpanDocProcessor : DocProcessor() {
    override suspend fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        coroutineScope {
            val mutableDocs = documentablesByPath
                .toMutable()
                .withDocsToProcessFilter { it.sourceHasDocumentation }

            mutableDocs
                .documentablesToProcess
                .flatMap { (_, docs) ->
                    docs.map {
                        launch {
                            val newContent = it.docContent.addCodeSpansToAliases()
                            it.modifyDocContentAndUpdate(newContent)
                        }
                    }
                }.joinAll()

            return@coroutineScope mutableDocs
        }
}

internal fun DocContent.addCodeSpansToAliases(): DocContent =
    replaceKdocAliases(ignoreReferencesInCode = true) { aliasOrReference ->
        aliasOrReference
            .removeSurrounding("<code>", "</code>")
            .surroundWith("<code>", "</code>")
    }
