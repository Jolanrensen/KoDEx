package nl.jolanrensen.kodex.documentableWrapper

import nl.jolanrensen.kodex.docContent.DocContent
import java.io.Serializable

data class DocumentableWrapperModification(
    val docContent: DocContent,
    val tags: Set<String>,
    val htmlRangeEnd: Int?,
    val htmlRangeStart: Int?,
) : Serializable

fun DocumentableWrapper.toModificationOrNull(): DocumentableWrapperModification? {
    if (!isModified) return null
    return DocumentableWrapperModification(
        docContent = docContent,
        tags = tags,
        htmlRangeEnd = htmlRangeEnd,
        htmlRangeStart = htmlRangeStart,
    )
}

fun MutableDocumentableWrapper.apply(modification: DocumentableWrapperModification) {
    isModified = true
    docContent = modification.docContent
    tags = modification.tags
    htmlRangeEnd = modification.htmlRangeEnd
    htmlRangeStart = modification.htmlRangeStart
}

fun DocumentableWrapperModification.applyTo(documentableWrapper: MutableDocumentableWrapper) {
    documentableWrapper.apply(this)
}
