package nl.jolanrensen.kodex.query

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.MutableDocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.getAllFullPathsFromHereForTargetPath
import nl.jolanrensen.kodex.documentableWrapper.toMutable
import nl.jolanrensen.kodex.processor.DocProcessor
import java.util.UUID

typealias DocumentableWrapperFilter = (DocumentableWrapper) -> Boolean

@Suppress("ClassName")
internal data object NO_FILTER : DocumentableWrapperFilter {
    override fun invoke(p1: DocumentableWrapper): Boolean = true
}

interface DocumentablesByPath {

    val queryFilter: DocumentableWrapperFilter

    val documentablesToProcessFilter: DocumentableWrapperFilter

    val documentablesToProcess: Map<String, List<DocumentableWrapper>>

    /**
     * Whether [DocumentableWrapper.getAllFullPathsFromHereForTargetPath] needs to be used to do
     * [queries][query].
     * Usually `false` for IntelliJ plugin, since it can do relative querying,
     * but `true` for the Gradle plugin.
     */
    val needToQueryAllPaths: Boolean

    val loadedProcessors: List<DocProcessor>

    /**
     * Returns a list of [DocumentableWrapper]s for the given [path].
     *
     * Returns empty list if [path] exists in the project
     * but no [DocumentableWrapper] is found.
     *
     * Returns `null` if no [DocumentableWrapper] is found for the given [path] and [path]
     * does not exist in the project.
     *
     * @param path the path to be queried. This needs to call [withoutBackticks]!
     */
    fun query(path: String, queryContext: DocumentableWrapper, canBeCache: Boolean = false): List<DocumentableWrapper>?

    operator fun get(identifier: UUID): DocumentableWrapper? =
        documentablesToProcess
            .values
            .firstNotNullOfOrNull { it.firstOrNull { it.identifier == identifier } }

    fun toMutable(): MutableDocumentablesByPath

    fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath

    fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath

    fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath

    companion object {
        fun of(map: Map<String, List<DocumentableWrapper>>, loadedProcessors: List<DocProcessor>): DocumentablesByPath =
            DocumentablesByPathFromMap(map, loadedProcessors)

        fun of(
            map: Map<String, List<MutableDocumentableWrapper>>,
            loadedProcessors: List<DocProcessor>,
        ): MutableDocumentablesByPath = MutableDocumentablesByPathFromMap(map, loadedProcessors)
    }

    fun String.withoutBackticks() = replace("`", "")
}

interface MutableDocumentablesByPath : DocumentablesByPath {

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>?

    override operator fun get(identifier: UUID): MutableDocumentableWrapper? =
        documentablesToProcess.values
            .firstNotNullOfOrNull { it.firstOrNull { it.identifier == identifier } }

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>

    override fun toMutable(): MutableDocumentablesByPath = this
}

@Suppress("UNCHECKED_CAST")
fun <T : DocumentablesByPath> T.withoutFilters(): T =
    when {
        queryFilter == NO_FILTER && documentablesToProcessFilter == NO_FILTER -> this
        else -> this.withFilters(NO_FILTER, NO_FILTER) as T
    }

fun Map<String, List<DocumentableWrapper>>.toDocumentablesByPath(
    loadedProcessors: List<DocProcessor>,
): DocumentablesByPath = DocumentablesByPath.of(this, loadedProcessors)

fun Iterable<Pair<String, List<DocumentableWrapper>>>.toDocumentablesByPath(
    loadedProcessors: List<DocProcessor>,
): DocumentablesByPath = toMap().toDocumentablesByPath(loadedProcessors)

/**
 * Converts a [Map]<[String], [List]<[DocumentableWrapper]>> to
 * [Map]<[String], [List]<[MutableDocumentableWrapper]>>.
 *
 * The [MutableDocumentableWrapper] is a copy of the original [DocumentableWrapper].
 */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, List<DocumentableWrapper>>.toMutable(): Map<String, List<MutableDocumentableWrapper>> =
    mapValues { (_, documentables) ->
        if (documentables.all { it is MutableDocumentableWrapper }) {
            documentables as List<MutableDocumentableWrapper>
        } else {
            documentables.map { it.toMutable() }
        }
    }

fun <K, V> MutableMap<K, MutableList<V>>.add(key: K, value: V) = getOrPut(key) { mutableListOf() }.add(value)
