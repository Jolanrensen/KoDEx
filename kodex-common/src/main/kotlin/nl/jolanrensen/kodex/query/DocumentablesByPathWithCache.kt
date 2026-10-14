package nl.jolanrensen.kodex.query

import nl.jolanrensen.kodex.defaultProcessors.IncludeDocAnalyzer
import nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.kodex.defaultProcessors.IncludeFileDocProcessor
import nl.jolanrensen.kodex.defaultProcessors.SampleDocProcessor
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.MutableDocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.getDocHashcode
import nl.jolanrensen.kodex.documentableWrapper.toMutable
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.utils.Edge
import nl.jolanrensen.kodex.utils.emptySimpleDirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator
import org.jgrapht.traverse.TopologicalOrderIterator
import java.util.UUID

open class DocumentablesByPathWithCache(
    val processLimit: Int,
    override val loadedProcessors: List<DocProcessor>,
    val queryNew: (context: DocumentableWrapper, link: String) -> List<DocumentableWrapper>?,
    val logDebug: (message: () -> String) -> Unit,
) : DocumentablesByPath,
    MutableDocumentablesByPath {

    override var queryFilter: DocumentableWrapperFilter = NO_FILTER

    override var documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER

    // graph representing the dependencies between documentables
    private var dependencyGraph = emptySimpleDirectedGraph<UUID>()

    // holds the hashcodes of the source of the documentables, to be updated each time a documentable is queried
    private val docContentSourceHashCodeCache: MutableMap<UUID, Int> = mutableMapOf()

    // holds the resulting docs of the documentables, to be updated each time a documentable has been processed
    // Can be deleted if needsRebuild is true
    // cannot be used in query(), see postIncludeDocContentCache instead
    private val docContentResultCache: MutableMap<UUID, DocContent> = mutableMapOf()

    // holds the intermediate (post-@include) docContent states of the docs
    // this is used on query
    private val postIncludeDocContentCache: MutableMap<UUID, DocContent> = mutableMapOf()

    private var docToProcess: MutableDocumentableWrapper? = null
    private var docsToProcess: MutableMap<String, MutableList<MutableDocumentableWrapper>> = mutableMapOf()

    // caches the identifiers resulting from a query (by query context and path)
    private var queryCache: MutableMap<Pair<UUID, String>, List<UUID>> = mutableMapOf()

    // single shared mutable instance per documentable for the current run.
    // This ensures that processors running before @include (like @comment removal) affect the content
    // that is @included in other docs, and that nested @includes are resolved in the included content.
    private var instances: MutableMap<UUID, MutableDocumentableWrapper> = mutableMapOf()

    private fun canonical(doc: DocumentableWrapper): MutableDocumentableWrapper =
        instances.getOrPut(doc.identifier) { doc.toMutable() }

    private fun isInDocsToProcess(identifier: UUID): Boolean =
        docsToProcess.values.any { docs -> docs.any { it.identifier == identifier } }

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>
        get() = when {
            docToProcess == null -> emptyMap()

            documentablesToProcessFilter == NO_FILTER -> docsToProcess

            else -> docsToProcess.mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
            }
        }

    fun getDocContentResult(docId: UUID): DocContent? = docContentResultCache[docId]

    /**
     * called from [nl.jolanrensen.kodex.services.PostIncludeDocProcessorCacheCollector]
     */
    fun updatePostIncludeDocContentResult(documentable: DocumentableWrapper) {
        postIncludeDocContentCache[documentable.identifier] = documentable.docContent
    }

    private val includeDocProcessorLoaded by lazy { loadedProcessors.any { it is IncludeDocProcessor } }
    private val sampleDocProcessorLoaded by lazy { loadedProcessors.any { it is SampleDocProcessor } }
    private val includeFileDocProcessorLoaded by lazy { loadedProcessors.any { it is IncludeFileDocProcessor } }

    // TODO make generic
    private fun DocumentableWrapper.hasExternalDependency(): Boolean {
        if (sampleDocProcessorLoaded) {
            val sampleDocProcessor = loadedProcessors.filterIsInstance<SampleDocProcessor>().single()
            if (sampleDocProcessor.providesTags.any { it in this.tags }) return true
        }
        if (includeFileDocProcessorLoaded) {
            val includeFileDocProcessor = loadedProcessors.filterIsInstance<IncludeFileDocProcessor>().single()
            if (includeFileDocProcessor.providesTags.any { it in this.tags }) return true
        }

        return false
    }

    /**
     * we set a context and a documentable to process
     * returns whether it needs a rebuild
     */
    suspend fun updatePreProcessing(docToProcess: DocumentableWrapper): Boolean {
        this.instances = mutableMapOf()
        this.queryCache = mutableMapOf()

        val doc = canonical(docToProcess)
        this.docToProcess = doc
        this.docsToProcess = doc.paths
            .associateWith { mutableListOf(doc) }
            .toMutableMap()

        // build local dependency graph from docToProcess and update the global dependencyGraph
        val graph =
            if (includeDocProcessorLoaded) {
                IncludeDocAnalyzer.getAnalyzedResult(
                    // catch circular dependencies
                    processLimit = processLimit - 1,
                    documentablesByPath = this,
                    analyzeQueriesToo = true,
                )
            } else {
                // if @include is not loaded, supply an empty graph
                emptySimpleDirectedGraph()
            }
        for (vertex in graph.vertexSet()) {
            if (!dependencyGraph.containsVertex(vertex.identifier)) {
                dependencyGraph.addVertex(vertex.identifier)
            }

            val oldIncomingEdges = dependencyGraph.incomingEdgesOf(vertex.identifier)
            val newIncomingEdges = graph
                .incomingEdgesOf(vertex)
                .map {
                    Edge(it.from.identifier, it.to.identifier)
                }.toSet()

            for (newEdge in (newIncomingEdges - oldIncomingEdges)) {
                dependencyGraph.addVertex(newEdge.from)
                dependencyGraph.addVertex(newEdge.to)
                dependencyGraph.addEdge(newEdge.from, newEdge.to, newEdge)
            }
            for (removedEdge in (oldIncomingEdges - newIncomingEdges)) {
                dependencyGraph.removeEdge(removedEdge)
            }
        }

        // graph may not contain doc if it has no dependencies, so add it to the list
        val orderedList = TopologicalOrderIterator(graph)
            .asSequence()
            .toList()
            .let { if (doc !in it) it + doc else it }

        // rebuild docsToProcess, this time ordered and with dependent docs for PostIncludeDocProcessor that are not
        // up to date.
        var needsRebuild = false
        for (dependencyDoc in orderedList) {
            val mutable = canonical(dependencyDoc)

            // put doc into process queue if it needs a rebuild
            if (needsRebuild(mutable)) {
                needsRebuild = true
                mutable.paths.forEach {
                    val docs = docsToProcess.getOrPut(it) { mutableListOf() }
                    if (docs.none { it.identifier == mutable.identifier }) docs.add(mutable)
                }
            }
        }

        return needsRebuild
    }

    /** retrieve a doc by identifier from queryCache or docsToProcess */
    override fun get(identifier: UUID): MutableDocumentableWrapper? =
        instances[identifier]
            ?: super<MutableDocumentablesByPath>.get(identifier)

    override val needToQueryAllPaths: Boolean = false

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>? {
        val path = path.withoutBackticks()
        val key = Pair(queryContext.identifier, path)
        val cachedIds = queryCache[key]
        val res = if (cachedIds != null) {
            cachedIds.mapNotNull { instances[it] }
        } else {
            val found = queryNew(queryContext, path)
                ?.map { canonical(it) }
                ?: return null
            queryCache[key] = found.map { it.identifier }
            found
        }.filter(queryFilter)

        // load cached results directly into queries.
        // Docs that are (re)processed in this run are shared instances and already contain the results of
        // earlier processors, so they must not be overwritten by (stale) cache.
        if (canBeCache) {
            for (doc in res) {
                if (isInDocsToProcess(doc.identifier)) continue
                // try to receive a post-include cached version
                val docContentResult = postIncludeDocContentCache[doc.identifier]
                if (docContentResult != null) {
                    doc.modifyDocContentAndUpdate(docContentResult)
                    logDebug {
                        "loading post-include cached ${
                            doc.fullyQualifiedPath
                        }/${doc.fullyQualifiedExtensionPath}: $docContentResult"
                    }
                }
            }
        }

        return res
    }

//    private val loadedProcessors = load

    private fun needsRebuild(doc: DocumentableWrapper): Boolean {
        // if source has changed, return true
        val sourceHasChanged = doc.getDocHashcode() != docContentSourceHashCodeCache[doc.identifier]
        val doesNotContainResultCache = docContentResultCache[doc.identifier] == null
        val doesNotContainVertex = !dependencyGraph.containsVertex(doc.identifier)

        // also return true if it contains a @sample or @includeFile tag as we cannot track changes to those for now
        val hasExternalDependency = doc.hasExternalDependency()

        if (doesNotContainVertex) dependencyGraph.addVertex(doc.identifier)

        val dependencies = dependencyGraph.incomingEdgesOf(doc.identifier).map { it.from }

        // else, check all dependencies for modifications
        val dependencyDocs = dependencies.map { get(it) }

        val dependenciesNeedsRebuild = dependencyDocs
            .map { it == null || needsRebuild(it) } // mapping first to increase the documentableWrapperCache
            .any { it }

        val needsRebuild = sourceHasChanged ||
            doesNotContainVertex ||
            doesNotContainResultCache ||
            dependenciesNeedsRebuild ||
            hasExternalDependency

        if (needsRebuild) {
            // update the caches
            docContentSourceHashCodeCache[doc.identifier] = doc.getDocHashcode()

            docContentResultCache.remove(doc.identifier)
            postIncludeDocContentCache.remove(doc.identifier)

            // and remove caches of docs dependent on this doc
            BreadthFirstIterator(dependencyGraph, doc.identifier).forEach {
                docContentResultCache.remove(it)
                postIncludeDocContentCache.remove(it)
            }
        }

        return needsRebuild
    }

    fun updatePostProcessing() {
        for (doc in docsToProcess.values.flatten()) {
            docContentResultCache[doc.identifier] = doc.docContent
        }
    }

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.queryFilter = queryFilter }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.documentablesToProcessFilter = docsToProcessFilter }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        apply {
            this.queryFilter = queryFilter
            this.documentablesToProcessFilter = docsToProcessFilter
        }
}
