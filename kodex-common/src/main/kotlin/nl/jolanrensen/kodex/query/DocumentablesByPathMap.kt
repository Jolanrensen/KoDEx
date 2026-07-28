package nl.jolanrensen.kodex.query

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.utils.IntRangeReplacingObjectOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.ObjectInputStream

/** Map from [DocumentableWrapper.fullyQualifiedPath] to [DocumentableWrapper]. */
typealias DocumentablesByPathMap = Map<String, List<DocumentableWrapper>>

fun DocumentablesByPathMap.writeCacheTo(file: File) {
    val map = LinkedHashMap(this)
    file.parentFile?.mkdirs()
    IntRangeReplacingObjectOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
        out.writeObject(map)
    }
}

@Suppress("UNCHECKED_CAST")
fun readDocumentablesByPathMapFromCache(file: File): DocumentablesByPathMap =
    file.inputStream()
        .use { ObjectInputStream(it).readObject() } as DocumentablesByPathMap
