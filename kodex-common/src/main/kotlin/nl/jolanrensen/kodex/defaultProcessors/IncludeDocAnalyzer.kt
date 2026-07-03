package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.queryDocumentables
import nl.jolanrensen.kodex.processor.TagDocAnalyser
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.withoutFilters
import nl.jolanrensen.kodex.utils.Edge
import nl.jolanrensen.kodex.utils.buildSimpleDirectedGraph
import nl.jolanrensen.kodex.utils.decodeCallableTarget
import nl.jolanrensen.kodex.utils.getTagArguments
import org.jgrapht.graph.SimpleDirectedGraph
import java.util.Collections
import java.util.UUID

/**
 * Generates a dependency graph of the [DocumentablesByPath] based on `@include tags`.
 *
 * Runtime Θ(n) where n is the number of documentables.
 */
internal class IncludeDocAnalyzer :
    TagDocAnalyser<SimpleDirectedGraph<DocumentableWrapper, Edge<DocumentableWrapper>>>() {
    companion object {
        suspend fun getAnalyzedResult(
            processLimit: Int,
            documentablesByPath: DocumentablesByPath,
            analyzeQueriesToo: Boolean = false,
        ): SimpleDirectedGraph<DocumentableWrapper, Edge<DocumentableWrapper>> =
            IncludeDocAnalyzer()
                .apply { this.analyzeQueriesToo = analyzeQueriesToo }
                .analyzeSafely(processLimit, documentablesByPath)
                .getAnalyzedResult()
    }

    override val providesTags: Set<String> = setOf(IncludeDocProcessor.TAG)

    override fun analyseBlockTagWithContent(tagWithContent: String, path: String, documentable: DocumentableWrapper) =
        analyseContent(tagWithContent, documentable)

    override fun analyseInlineTagWithContent(tagWithContent: String, path: String, documentable: DocumentableWrapper) =
        analyseContent(tagWithContent, documentable)

    private val unfilteredDocumentablesByPath by lazy { documentablesByPath.withoutFilters() }
    private val dependencies: MutableSet<Edge<DocumentableWrapper>> = Collections.synchronizedSet(mutableSetOf())
    internal var analyzeQueriesToo = false
    private val analyzedDocumentables = mutableSetOf<UUID>()

    private fun analyseContent(line: String, documentable: DocumentableWrapper) {
        val includeArguments = line.getTagArguments(tag = IncludeDocProcessor.TAG, numberOfArguments = 2)
        val includePath = includeArguments.first().decodeCallableTarget()

        // query the filtered documentables for the @include paths
        val targetDocumentable = documentable.queryDocumentables(
            query = includePath,
            documentables = documentablesByPath,
            documentablesNoFilters = unfilteredDocumentablesByPath,
        ) { it.identifier != documentable.identifier }

        if (targetDocumentable != null) {
            // this depends on target, so add an edge from target to this, that makes sure the target goes first
            dependencies += Edge(
                from = targetDocumentable,
                to = documentable,
            )

            if (analyzeQueriesToo && targetDocumentable.identifier !in analyzedDocumentables) {
                analyzeDocumentable(targetDocumentable, 10_000)
            }
        }
        analyzedDocumentables += documentable.identifier
    }

    override fun getAnalyzedResult(): SimpleDirectedGraph<DocumentableWrapper, Edge<DocumentableWrapper>> {
        require(hasRun) { "analyze must be called before getAnalyzedResult" }

        val dag = buildSimpleDirectedGraph {
            for (dep in dependencies) {
                addEdge(dep.from, dep.to, dep)
            }
        }

        return dag
    }

    override fun tagIsSupported(tag: String): Boolean = tag == IncludeDocProcessor.TAG

    override fun <T : DocumentableWrapper> filterDocumentablesToProcess(documentable: T): Boolean =
        documentable.sourceHasDocumentation

    override fun <T : DocumentableWrapper> filterDocumentablesToQuery(documentable: T): Boolean =
        documentable.sourceHasDocumentation
}
