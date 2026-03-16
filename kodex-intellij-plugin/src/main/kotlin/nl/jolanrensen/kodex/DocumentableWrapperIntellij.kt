package nl.jolanrensen.kodex

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContent
import nl.jolanrensen.kodex.documentableWrapper.AnnotationWrapper
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.utils.docComment
import nl.jolanrensen.kodex.utils.getImports
import nl.jolanrensen.kodex.utils.lastIndexOfNot
import nl.jolanrensen.kodex.utils.programmingLanguage
import nl.jolanrensen.kodex.utils.toIntRange
import nl.jolanrensen.kodex.utils.toSimpleImportPath
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import java.io.File

fun DocumentableWrapper.Companion.createFromIntellijOrNull(
    documentable: PsiElement,
    useK2: Boolean,
): DocumentableWrapper? {
    require(documentable is KtDeclaration || documentable is PsiDocCommentOwner) {
        "Documentable must be a KtDeclaration or PsiDocCommentOwner, but was ${documentable::class.simpleName}"
    }

    val path = documentable.kotlinFqName?.asString() ?: return null
    val extensionPath: String? = if (documentable.isExtensionDeclaration()) {
        if (useK2) {
            // k2 method
            (documentable as? KtElement)?.let {
                analyze(it) {
                    (documentable as? org.jetbrains.kotlin.psi.KtCallableDeclaration)
                        ?.receiverTypeReference
                        ?.type
                        ?.fullyExpandedType
                        ?.expandedSymbol
                        ?.psi
                        ?.kotlinFqName
                        ?.toString()
                        ?.let { "$it.${documentable.name}" }
                }
            }
        } else {
            // k1 method
            @OptIn(K1Deprecation::class)
            (documentable as? KtDeclaration)
                ?.descriptor
                ?.let { it as CallableDescriptor }
                ?.extensionReceiverParameter
                ?.type
                ?.fqName
                ?.asString()
                ?.let { "$it.${documentable.name}" }
        }
    } else {
        null
    }
    val paths = listOfNotNull(path, extensionPath)

    val file = File(
        documentable
            .containingFile
            .originalFile
            .virtualFile
            .path,
    )

//    if (!file.exists()) {
//        return null
//    }

    val fileText: String = documentable.containingFile.originalFile.text

    val docComment = documentable.docComment

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
                    |Comment Content: "${docComment.text.asDocTextOrNull()?.getDocContent()}"
                    |Query: "$query"
            """.trimMargin()
        }
        require(endComment != -1) {
            """
                    |Could not find end of comment.
                    |Paths: $paths
                    |Comment Content: "${docComment.text.asDocTextOrNull()?.getDocContent()}"
                    |Query: "$query"
            """.trimMargin()
        }

        TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
    } ?: try {
        // if there is no comment, we give the text range for where a new comment could be.
        // throws an exception if it's not in the file
        val sourceTextRange = documentable.textRange!!
        TextRange(sourceTextRange.startOffset, sourceTextRange.startOffset)
    } catch (_: Throwable) {
        return null
    }

    // calculate the indent of the doc comment by looking at how many spaces are on the first line before /**
    val docIndent = try {
        (
            docFileTextRange.startOffset -
                fileText.lastIndexOfNot('\n', docFileTextRange.startOffset)
        ).coerceAtLeast(0)
    } catch (_: Throwable) {
        0
    }

    // grab just the contents of the doc without the *-stuff
    val docContent = docFileTextRange.substring(fileText).asDocTextOrNull()?.getDocContent()
        ?: "".asDocContent()

    // Collect the imports from the file
    val imports = documentable.getImports().map { it.toSimpleImportPath() }

    // Get the raw source of the documentable
    val rawSource = documentable.text

    // TODO
    val annotations: List<AnnotationWrapper> = emptyList()

    val fileTextRange = documentable.textRange!!.toIntRange()

    return DocumentableWrapper(
        docContent = docContent,
        programmingLanguage = documentable.programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        fullyQualifiedPath = path,
        fullyQualifiedExtensionPath = extensionPath,
        // not needed, resolution is done by intellij engine
        fullyQualifiedSuperPaths = emptyList(),
        file = file,
        docFileTextRange = docFileTextRange.toIntRange(),
        docIndent = docIndent,
        annotations = annotations,
        fileTextRange = fileTextRange,
        origin = documentable,
    )
}

fun DocumentableWrapper.getOrigin(): PsiElement = origin as PsiElement
