package nl.jolanrensen.kodex.query

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.MutableDocumentableWrapper
import nl.jolanrensen.kodex.processor.DocProcessor

open class DocumentablesByPathFromMap(
    private val allDocs: Map<String, List<DocumentableWrapper>>,
    override val loadedProcessors: List<DocProcessor>,
    final override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    final override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : DocumentablesByPath {

    override val needToQueryAllPaths: Boolean = true

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> =
        when (documentablesToProcessFilter) {
            NO_FILTER -> allDocs

            else -> allDocs.mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
            }
        }

    private val docsToQuery: Map<String, List<DocumentableWrapper>> =
        when (queryFilter) {
            NO_FILTER -> allDocs

            else -> allDocs.mapValues { (_, documentables) ->
                documentables.filter(queryFilter)
            }
        }

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<DocumentableWrapper>? = docsToQuery[path.withoutBackticks()]

    override fun toMutable(): MutableDocumentablesByPath =
        this as? MutableDocumentablesByPath ?: MutableDocumentablesByPathFromMap(
            allDocs = allDocs.toMutable(),
            queryFilter = queryFilter,
            loadedProcessors = loadedProcessors,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        when {
            queryFilter == this.queryFilter -> this

            else -> DocumentablesByPathFromMap(
                allDocs = allDocs,
                loadedProcessors = loadedProcessors,
                queryFilter = queryFilter,
                documentablesToProcessFilter = documentablesToProcessFilter,
            )
        }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        when {
            docsToProcessFilter == this.documentablesToProcessFilter -> this

            else -> DocumentablesByPathFromMap(
                allDocs = allDocs,
                queryFilter = queryFilter,
                loadedProcessors = loadedProcessors,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath =
        when {
            queryFilter == this.queryFilter && docsToProcessFilter == this.documentablesToProcessFilter -> this

            else -> DocumentablesByPathFromMap(
                allDocs = allDocs,
                queryFilter = queryFilter,
                loadedProcessors = loadedProcessors,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }
}

class MutableDocumentablesByPathFromMap(
    private val allDocs: Map<String, List<MutableDocumentableWrapper>>,
    override val loadedProcessors: List<DocProcessor>,
    override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : MutableDocumentablesByPath {

    override val needToQueryAllPaths: Boolean = true

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> =
        when (documentablesToProcessFilter) {
            NO_FILTER -> allDocs

            else -> allDocs.mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
            }
        }

    private val docsToQuery: Map<String, List<MutableDocumentableWrapper>> =
        when (queryFilter) {
            NO_FILTER -> allDocs

            else -> allDocs.mapValues { (_, documentables) ->
                documentables.filter(queryFilter)
            }
        }

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>? = docsToQuery[path.withoutBackticks()]

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        when {
            queryFilter == this.queryFilter -> this

            else -> MutableDocumentablesByPathFromMap(
                allDocs = allDocs,
                queryFilter = queryFilter,
                loadedProcessors = loadedProcessors,
                documentablesToProcessFilter = documentablesToProcessFilter,
            )
        }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        when {
            docsToProcessFilter == this.documentablesToProcessFilter -> this

            else -> MutableDocumentablesByPathFromMap(
                allDocs = allDocs,
                queryFilter = queryFilter,
                loadedProcessors = loadedProcessors,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        when {
            queryFilter == this.queryFilter && docsToProcessFilter == this.documentablesToProcessFilter -> this

            else -> MutableDocumentablesByPathFromMap(
                allDocs = allDocs,
                queryFilter = queryFilter,
                loadedProcessors = loadedProcessors,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }
}
