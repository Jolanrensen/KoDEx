package nl.jolanrensen.kodex.documentableWrapper

import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.findTagNames
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper.Companion
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.withoutFilters
import nl.jolanrensen.kodex.utils.size
import java.io.File
import java.util.UUID

/**
 * Wrapper around a [Dokka's Documentable][org.jetbrains.dokka.model.Documentable], that adds easy access
 * to several useful properties and functions.
 *
 * Instantiate it with [documentable][org.jetbrains.dokka.model.Documentable], [source][org.jetbrains.dokka.model.DocumentableSource] and [logger][org.jetbrains.dokka.utilities.DokkaLogger] using
 * [DocumentableWrapper.createFromDokkaOrNull][Companion.createFromDokkaOrNull].
 *
 * [docContent], [tags], and [isModified] are designed te be changed and will be read when
 * writing modified docs to files.
 * Modify either in immutable fashion using [copy], or in mutable fashion using [toMutable].
 *
 * All other properties are read-only and based upon the source-documentable.
 *
 * @property [programmingLanguage] The [programming language][ProgrammingLanguage] of the documentable.
 * @property [imports] The imports of the file in which the documentable can be found.
 * @property [rawSource] The raw source code of the documentable, including documentation. May need to be trimmed.
 * @property [sourceHasDocumentation] Whether the original documentable has a doc comment or not.
 * @property [fullyQualifiedPath] The fully qualified path of the documentable, its key if you will.
 * @property [fullyQualifiedExtensionPath] If the documentable is an extension function/property:
 *   "(The path of the receiver).(name of the documentable)".
 * @property [fullyQualifiedSuperPaths] The fully qualified paths of the super classes of the documentable.
 * @property [file] The file in which the documentable can be found.
 * @property [docFileTextRange] The text range of the [file] where the original comment can be found.
 *   This is the range from `/**` to `*/`. If there is no comment, the range is empty. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [docIndent] The amount of spaces the comment is indented with. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [identifier] A unique identifier for this documentable, will survive [copy] and [asMutable].
 * @property [annotations] A list of annotations present on this documentable.
 * @property [fileTextRange] The range in the file this documentable is defined in.
 *
 * @property [docContent] Just the contents of the comment, without the `*`-stuff. Can be modified with [copy] or via
 *   [toMutable].
 * @property [tags] List of tag names present in this documentable. Can be modified with [copy] or via
 *   [toMutable]. Must be updated manually if [docContent] is modified.
 * @property [isModified] Whether the [docContent] was modified. Can be modified with [copy] or via
 *   [toMutable]. Must be updated manually if [docContent] is modified.
 *
 * @property [htmlRangeStart] Optional begin marker used by [ExportAsHtmlDocProcessor] for the
 *   [@ExportAsHtml][ExportAsHtml] annotation.
 * @property [htmlRangeEnd] Optional end marker used by [ExportAsHtmlDocProcessor] for the
 *   [@ExportAsHtml][ExportAsHtml] annotation.
 *
 * @see [MutableDocumentableWrapper]
 */
open class DocumentableWrapper(
    val programmingLanguage: ProgrammingLanguage,
    val imports: List<SimpleImportPath>,
    val rawSource: String,
    val sourceHasDocumentation: Boolean,
    val fullyQualifiedPath: String,
    val fullyQualifiedExtensionPath: String?,
    val fullyQualifiedSuperPaths: List<String>,
    val file: File,
    val docFileTextRange: IntRange,
    val docIndent: Int,
    val annotations: List<AnnotationWrapper>,
    val fileTextRange: IntRange,
    val identifier: UUID = computeIdentifier(
        imports = imports,
        file = file,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
        textRangeStart = fileTextRange.first,
    ),
    val origin: Any,
    open val docContent: DocContent,
    open val tags: Set<String>,
    open val isModified: Boolean,
    open val htmlRangeStart: Int?,
    open val htmlRangeEnd: Int?,
) {

    companion object {

        /**
         * Computes a unique identifier for a documentable based on its [fullyQualifiedPath] and
         * its [fullyQualifiedExtensionPath].
         */
        fun computeIdentifier(
            imports: List<SimpleImportPath>,
            file: File,
            fullyQualifiedPath: String,
            fullyQualifiedExtensionPath: String?,
            fullyQualifiedSuperPaths: List<String>,
            textRangeStart: Int,
        ): UUID =
            UUID.nameUUIDFromBytes(
                byteArrayOf(
                    file.path.hashCode().toByte(),
                    fullyQualifiedPath.hashCode().toByte(),
                    fullyQualifiedExtensionPath.hashCode().toByte(),
                    textRangeStart.hashCode().toByte(),
                    *imports.map { it.hashCode().toByte() }.toByteArray(),
                    *fullyQualifiedSuperPaths.map { it.hashCode().toByte() }.toByteArray(),
                ),
            )
    }

    constructor(
        docContent: DocContent,
        programmingLanguage: ProgrammingLanguage,
        imports: List<SimpleImportPath>,
        rawSource: String,
        fullyQualifiedPath: String,
        fullyQualifiedExtensionPath: String?,
        fullyQualifiedSuperPaths: List<String>,
        file: File,
        docFileTextRange: IntRange,
        docIndent: Int,
        annotations: List<AnnotationWrapper>,
        fileTextRange: IntRange,
        origin: Any,
        htmlRangeStart: Int? = null,
        htmlRangeEnd: Int? = null,
    ) : this(
        programmingLanguage = programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        sourceHasDocumentation = docContent.value.isNotEmpty() && docFileTextRange.size > 1,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
        file = file,
        docFileTextRange = docFileTextRange,
        fileTextRange = fileTextRange,
        docIndent = docIndent,
        docContent = docContent,
        annotations = annotations,
        tags = docContent.findTagNames().toSet(),
        isModified = false,
        htmlRangeStart = htmlRangeStart,
        htmlRangeEnd = htmlRangeEnd,
        origin = origin,
    )

    val paths = listOfNotNull(fullyQualifiedPath, fullyQualifiedExtensionPath)

    private var allTypes: Set<DocumentableWrapper>? = null

    /**
     * Retrieves all types of this [DocumentableWrapper], including its supertypes.
     * It caches the results in [allTypes].
     */
    fun getAllTypes(documentables: DocumentablesByPath): Set<DocumentableWrapper> {
        if (allTypes == null) {
            val documentablesNoFilters = documentables.withoutFilters()

            allTypes = buildSet {
                this += this@DocumentableWrapper

                for (path in fullyQualifiedSuperPaths) {
                    documentablesNoFilters.query(path, this@DocumentableWrapper)?.forEach {
                        this += it.getAllTypes(documentablesNoFilters)
                    }
                }
            }
        }
        return allTypes!!
    }

    /** Returns a copy of this [DocumentableWrapper] with the given parameters. */
    open fun copy(
        docContent: DocContent = this.docContent,
        tags: Set<String> = this.tags,
        isModified: Boolean = this.isModified,
    ): DocumentableWrapper =
        DocumentableWrapper(
            programmingLanguage = programmingLanguage,
            imports = imports,
            rawSource = rawSource,
            sourceHasDocumentation = sourceHasDocumentation,
            fullyQualifiedPath = fullyQualifiedPath,
            fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
            fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
            file = file,
            docFileTextRange = docFileTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
            annotations = annotations,
            identifier = identifier,
            fileTextRange = fileTextRange,
            origin = origin,
            htmlRangeStart = htmlRangeStart,
            htmlRangeEnd = htmlRangeEnd,
        )
}

fun DocumentableWrapper.getDocHashcode(): Int = docContent.hashCode()

fun DocumentableWrapper.getDocContentForHtmlRange(): DocContent {
    val lines = docContent.value.lines()
    val start = htmlRangeStart ?: 0
    val end = htmlRangeEnd ?: lines.lastIndex
    return lines.subList(start, end + 1).joinToString("\n").asDocContent()
}

/** Query file for doc text range. */
fun DocumentableWrapper.queryFileForDocTextRange(): String = file.readText().substring(docFileTextRange)
