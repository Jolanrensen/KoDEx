package nl.jolanrensen.kodex.defaultProcessors

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import nl.jolanrensen.kodex.docContent.replaceKdocAliases
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath

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
                            val newContent = it.docContent.replaceKdocAliases { aliasOrReference ->
                                if (aliasOrReference.startsWith('`') && aliasOrReference.endsWith('`')) {
                                    aliasOrReference
                                } else {
                                    "`$aliasOrReference`"
                                }
                            }
                            it.modifyDocContentAndUpdate(newContent)
                        }
                    }
                }.joinAll()

            return@coroutineScope mutableDocs
        }
}
