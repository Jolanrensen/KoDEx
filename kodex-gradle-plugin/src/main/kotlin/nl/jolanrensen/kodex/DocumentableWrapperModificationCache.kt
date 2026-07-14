package nl.jolanrensen.kodex

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapperModification
import nl.jolanrensen.kodex.documentableWrapper.applyTo
import nl.jolanrensen.kodex.query.DocumentablesByPath
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID

class DocumentableWrapperModificationCache(cache: Map<UUID, DocumentableWrapperModification>) : Serializable {

    // Store in a plain JDK map.
    val cache: Map<UUID, DocumentableWrapperModification> = LinkedHashMap(cache)

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        ObjectOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            out.writeObject(this)
        }
    }

    companion object {
        fun readFrom(file: File): DocumentableWrapperModificationCache =
            ObjectInputStream(BufferedInputStream(file.inputStream())).use { input ->
                input.readObject() as DocumentableWrapperModificationCache
            }
    }
}

fun Iterable<DocumentableWrapperModificationCache>.merge(): DocumentableWrapperModificationCache =
    DocumentableWrapperModificationCache(
        buildMap {
            this@merge.forEach {
                it.cache.forEach { (key, value) ->
                    put(key, value)
                }
            }
        },
    )

fun DocumentablesByPath.applyCacheWhere(
    cache: DocumentableWrapperModificationCache,
    predicate: (DocumentableWrapper) -> Boolean,
): DocumentablesByPath {
    if (cache.cache.isEmpty()) return this
    val mutable = this.toMutable()

    // apply cache everywhere the predicate holds, ...
    val cacheAppliedDocumentables = mutableSetOf<UUID>()
    for ((_, docs) in mutable.documentablesToProcess) {
        for (doc in docs) {
            if (!predicate(doc)) continue
            cache.cache[doc.identifier]?.applyTo(doc)
            cacheAppliedDocumentables += doc.identifier
        }
    }
    // ... these docs no longer need to be processed, only queryable
    return mutable.withDocsToProcessFilter { it.identifier !in cacheAppliedDocumentables }
}
