package nl.jolanrensen.kodex.documentableWrapper

import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage.JAVA
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.NO_FILTER

/**
 * Queries the [documentables] map for a [org.jetbrains.dokka.model.DocumentableSource]'s [fullyQualifiedPath] or [fullyQualifiedExtensionPath] that exists for
 * the given [query]. If there is no [org.jetbrains.dokka.model.DocumentableSource] for the given [query] but the path
 * still exists as a key in the [documentables] map, then that path is returned.
 */
fun DocumentableWrapper.queryDocumentablesForPath(
    query: String,
    documentablesNoFilters: DocumentablesByPath,
    documentables: DocumentablesByPath,
    canBeExtension: Boolean = true,
    pathIsValid: (String, DocumentableWrapper) -> Boolean = { _, _ -> true },
    filter: (DocumentableWrapper) -> Boolean = { true },
): String? {
    val queryResult = queryDocumentables(
        query = query,
        documentablesNoFilters = documentablesNoFilters,
        documentables = documentables,
        canBeExtension = canBeExtension,
        filter = filter,
    )
    val docPath = queryResult?.let {
        // take either the normal path to the doc or the extension path depending on which is valid and
        // causes the smallest number of collisions
        queryResult.paths
            .filter { path -> pathIsValid(path, queryResult) }
            .minByOrNull { documentables.query(it, this)?.size ?: 0 }
    }

    if (docPath != null) return docPath

    // if there is no doc for the query, then we just return the first matching path
    // this can happen for function overloads with the same name.

    if (queryResult != null) {
        return queryResult.fullyQualifiedPath
    }

    val queries = getAllFullPathsFromHereForTargetPath(
        targetPath = query,
        documentablesNoFilters = documentablesNoFilters,
        canBeExtension = canBeExtension,
    )
    // todo fix for intellij?
    return queries.firstOrNull {
        documentables.query(it, this) != null
    }
}

/**
 * Queries the [documentables] map for a [DocumentableWrapper] that exists for
 * the given [query].
 * Returns `null` if no [DocumentableWrapper] is found for the given [query].
 *
 * @param canBeCache Whether the query can be a cache or not. Mosty only used by the
 *   IntelliJ plugin and [IncludeDocProcessor].
 */
fun DocumentableWrapper.queryDocumentables(
    query: String,
    documentablesNoFilters: DocumentablesByPath,
    documentables: DocumentablesByPath,
    canBeExtension: Boolean = true,
    canBeCache: Boolean = false,
    filter: (DocumentableWrapper) -> Boolean = { true },
): DocumentableWrapper? {
    val queries: List<String> = buildList {
        if (documentables.needToQueryAllPaths) {
            this += getAllFullPathsFromHereForTargetPath(
                targetPath = query,
                documentablesNoFilters = documentablesNoFilters,
                canBeExtension = canBeExtension,
            )

            if (programmingLanguage == JAVA) { // support KotlinFileKt.Notation from java
                val splitQuery = query.split(".")
                if (splitQuery.firstOrNull()?.endsWith("Kt") == true) {
                    this += getAllFullPathsFromHereForTargetPath(
                        targetPath = splitQuery.drop(1).joinToString("."),
                        documentablesNoFilters = documentablesNoFilters,
                        canBeExtension = canBeExtension,
                    )
                }
            }
        } else {
            this += query
        }
    }

    return queries.firstNotNullOfOrNull {
        documentables.query(
            path = it,
            queryContext = this,
            canBeCache = canBeCache,
        )?.firstOrNull(filter)
    }
}

/**
 * Returns all possible paths using [targetPath] and the imports in this file.
 */
private fun DocumentableWrapper.getPathsUsingImports(targetPath: String): List<String> =
    buildList {
        for (import in imports) {
            val qualifiedName = import.pathStr
            val identifier = import.importedName

            if (import.isAllUnder) {
                this += qualifiedName.removeSuffix("*") + targetPath
            } else if (targetPath.startsWith(identifier!!)) {
                this += targetPath.replaceFirst(identifier, qualifiedName)
            }
        }
    }

/**
 * Returns a list of paths that match the given [targetPath] in the context of this documentable.
 * It takes the current [DocumentableWrapper.fullyQualifiedPath] and [imports][DocumentableWrapper.getPathsUsingImports] into account.
 *
 * For example, given `bar` inside the documentable `Foo`, it would return
 * - `bar`
 * - `Foo.bar`
 * - `FooSuperType.bar`
 * - `package.full.path.bar`
 * - `package.full.bar`
 * - `package.bar`
 * - `someImport.bar`
 * - `someImport2.bar`
 * etc.
 */
fun DocumentableWrapper.getAllFullPathsFromHereForTargetPath(
    targetPath: String,
    documentablesNoFilters: DocumentablesByPath,
    canBeExtension: Boolean = true,
): List<String> {
    require(documentablesNoFilters.run { queryFilter == NO_FILTER && documentablesToProcessFilter == NO_FILTER }) {
        "DocumentablesByPath must not have any filters in `getAllFullPathsFromHereForTargetPath()`."
    }
    val paths = getAllTypes(documentablesNoFilters).flatMap { it.paths }
    val subPaths = buildSet {
        for (path in paths) {
            val current = path.split(".").toMutableList()
            while (current.isNotEmpty()) {
                add(current.joinToString("."))
                current.removeLast()
            }
        }
    }

    val queries = buildSet {
        // get all possible full target paths with all possible sub paths
        for (subPath in subPaths) {
            this += "$subPath.$targetPath"
        }

        // check imports too
        this.addAll(
            getPathsUsingImports(targetPath),
        )

        // finally, add the path itself in case it's a top level/fq path
        this += targetPath

        // target path could be pointing at something defined on a supertype of the target
        if (!canBeExtension) return@buildSet
        val (targetPathReceiver, target) = targetPath.split(".").let {
            if (it.size <= 1) return@buildSet
            it.dropLast(1).joinToString(".") to it.last()
        }

        // if that is the case, we need to find the type of the receiver and get all full paths from there too
        @Suppress("NamedArgsPositionMismatch")
        val targetType = queryDocumentables(
            query = targetPathReceiver,
            documentablesNoFilters = documentablesNoFilters,
            documentables = documentablesNoFilters,
            canBeExtension = false,
        ) { it != this@getAllFullPathsFromHereForTargetPath } ?: return@buildSet

        val targetTypes = targetType.getAllTypes(documentablesNoFilters)
        addAll(targetTypes.map { "${it.fullyQualifiedPath}.$target" })
    }

    return queries.toList()
}
