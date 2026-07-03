package nl.jolanrensen.kodex.services

import nl.jolanrensen.kodex.processor.DocAnalyser
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.DocumentablesByPathWithCache

class PostIncludeDocProcessorCacheCollector(private val cacheHolder: DocumentablesByPathWithCache) :
    DocAnalyser<Unit>() {

    override fun getAnalyzedResult() = Unit

    override fun analyze(processLimit: Int, documentablesByPath: DocumentablesByPath) {
        documentablesByPath.documentablesToProcess.values.forEach {
            it.forEach { documentable ->
                cacheHolder.updatePostIncludeDocContentResult(documentable)
            }
        }
    }
}
