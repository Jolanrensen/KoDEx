package nl.jolanrensen.kodex

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNamedElement
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContent
import nl.jolanrensen.kodex.documentableWrapper.AnnotationWrapper
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.utils.documentString
import nl.jolanrensen.kodex.utils.findClosestDocComment
import nl.jolanrensen.kodex.utils.getImports
import nl.jolanrensen.kodex.utils.getValue
import nl.jolanrensen.kodex.utils.lastIndexOfNot
import nl.jolanrensen.kodex.utils.paths
import nl.jolanrensen.kodex.utils.programmingLanguage
import nl.jolanrensen.kodex.utils.psi
import nl.jolanrensen.kodex.utils.textRange
import nl.jolanrensen.kodex.utils.toIntRange
import nl.jolanrensen.kodex.utils.toSimpleImportPath
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.annotations
import org.jetbrains.dokka.model.AnnotationValue
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.InheritedMember
import org.jetbrains.dokka.model.ObviousMember
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.WithSupertypes
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.utilities.DokkaLogger
import java.io.File

/**
 * Creates a [DocumentableWrapper] if successful, or `null` if not.
 * This is the preferred way to create a [DocumentableWrapper].
 *
 * @param [documentable] The Dokka [Documentable]. Should be given.
 *   This represents the class, function, or another entity that potentially has some doc comment.
 * @param [source] The Dokka [DocumentableSource], either a [PsiDocumentableSource] for Java,
 *   or a [DescriptorDocumentableSource] for Kotlin. Usually provided by a [WithSources] in Dokka. Should be given.
 *   This represents the source of the [documentable] pointing to a language-specific AST/PSI.
 * @param [logger] [Dokka logger][DokkaLogger] that's needed for [findClosestDocComment]. Should be given.
 */
@OptIn(InternalDokkaApi::class)
fun DocumentableWrapper.Companion.createFromDokkaOrNull(
    documentable: Documentable,
    source: DocumentableSource,
    logger: DokkaLogger,
): DocumentableWrapper? {
    // skip documentables that are inherited and not actually there
    (documentable as? WithExtraProperties<Documentable>)?.let {
        if ((it.extra.allOfType<ObviousMember>() + it.extra.allOfType<InheritedMember>()).isNotEmpty()) {
            return null
        }
    }

    val docComment = findClosestDocComment(
        element = source.psi as PsiNamedElement?,
        logger = logger,
    )

    val paths = documentable.dri.paths
    val file = File(source.path)

    if (!file.exists()) {
        return null
    }

    val fileText: String = file.readText()
        .replace("\r\n", "\n")
        .replace("\r", "\n")

    val docFileTextRange = docComment?.textRange?.let { ogRange ->
        // docComment.textRange is the range of the comment in the file, but depending on the language,
        // it might not exactly match the range of the comment from /** to */. Let's correct that.
        val query = ogRange.substring(fileText)
        val startComment = query.indexOf("/**")
        val endComment = query.lastIndexOf("*/")

        require(startComment != -1) {
            """
                    |Could not find start of comment.
                    |Paths: $paths
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"
            """.trimMargin()
        }
        require(endComment != -1) {
            """
                    |Could not find end of comment.
                    |Paths: $paths
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"
            """.trimMargin()
        }

        TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
    } ?: try {
        // if there is no comment, we give the text range for where a new comment could be.
        // throws an exception if it's not in the file
        val sourceTextRange = source.textRange!!
        TextRange(sourceTextRange.startOffset, sourceTextRange.startOffset)
    } catch (_: Throwable) {
        return null
    }

    // calculate the indent of the doc comment by looking at how many spaces are on the first line before /**
    val docIndent = (
        docFileTextRange.startOffset -
            fileText.lastIndexOfNot('\n', docFileTextRange.startOffset)
    ).coerceAtLeast(0)

    // grab just the contents of the doc without the *-stuff
    val docContent = docFileTextRange.substring(fileText)
        .asDocTextOrNull()
        ?.getDocContent()
        ?: "".asDocContent()

    // Collect the imports from the file
    val imports = source.getImports().map { it.toSimpleImportPath() }

    // Get the raw source of the documentable
    val rawSource = source.psi?.text ?: return null

    // Get the paths of all supertypes of the documentable one level up
    val superPaths = (documentable as? WithSupertypes)
        ?.supertypes
        ?.flatMap { it.value }
        ?.flatMap { it.typeConstructor.dri.paths }
        ?: emptyList()

    val annotations = documentable.annotations().values.flatten().map {
        AnnotationValue(it).getValue() as AnnotationWrapper
    }

    val fileTextRange = source.textRange!!.toIntRange()

    return DocumentableWrapper(
        docContent = docContent,
        programmingLanguage = source.programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        fullyQualifiedPath = paths[0],
        fullyQualifiedExtensionPath = paths.getOrNull(1),
        fullyQualifiedSuperPaths = superPaths,
        file = file,
        docFileTextRange = docFileTextRange.toIntRange(),
        docIndent = docIndent,
        annotations = annotations,
        fileTextRange = fileTextRange,
        origin = documentable,
    )
}

fun DocumentableWrapper.getOrigin(): Documentable = origin as Documentable
