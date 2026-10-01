@file:Suppress("UnstableApiUsage")

package nl.jolanrensen.kodex.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nl.jolanrensen.kodex.kodexInlineRenderingIsEnabled
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.utils.docComment
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.CancellationException

/**
 * inline, used for rendering single doc comment in file, also works for multiple, Issue #54,
 * fixed by loading this provider twice; as "first" and as "last".
 *
 * TODO slow, runs a lot!
 */
class DocProcessorInlineDocumentationProvider : InlineDocumentationProvider {

    init {
        println("DocProcessorInlineDocumentationProvider (K2) created")
    }

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.Companion.getInstance(project) }

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentation]
     */
    val kotlin = KotlinInlineDocumentationProvider()

    // the order for this one needs to be "last"
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val service = getService(file.project)
        if (!service.isEnabled || !kodexInlineRenderingIsEnabled) return emptyList()

        val result = mutableListOf<InlineDocumentation>()
        PsiTreeUtil.processElements(file) {
            val declaration = it as? KtDeclaration ?: return@processElements true
            val originalComment = declaration.docComment ?: return@processElements true

            // a single failing doc comment should not prevent the others from being rendered by KoDEx
            val documentation = try {
                createInlineDocumentation(service, declaration, originalComment)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
            if (documentation != null) result += documentation

            true
        }

        return result
    }

    // the order for this one needs to be "first" XD
    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
        val service = getService(file.project)
        if (!service.isEnabled || !kodexInlineRenderingIsEnabled) return null

        try {
            val comment = PsiTreeUtil.getParentOfType(
                file.findElementAt(textRange.startOffset),
                PsiDocCommentBase::class.java,
            ) ?: return null

            if (comment.textRange != textRange) return null

            val declaration = comment.owner as? KtDeclaration ?: return null
            return createInlineDocumentation(service, declaration, comment)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Returns `null` if KoDEx could not process the doc comment, meaning the original rendering should be used.
     */
    private fun createInlineDocumentation(
        service: DocProcessorService,
        declaration: KtDeclaration,
        originalComment: PsiDocCommentBase,
    ): DocProcessorInlineDocumentation? {
        val modified = runBlockingCancellable {
            service.getModifiedElement(declaration)
        } ?: return null

        return DocProcessorInlineDocumentation(
            originalDocumentation = originalComment,
            originalOwner = declaration,
            // null if the processed doc comment is empty, in which case it was removed from the modified element
            modifiedDocumentation = modified.docComment as KDoc?,
        )
    }
}

class DocProcessorInlineDocumentation(
    private val originalDocumentation: PsiDocCommentBase,
    private val originalOwner: KtDeclaration,
    private val modifiedDocumentation: KDoc?,
) : InlineDocumentation {

    override fun getDocumentationRange(): TextRange = originalDocumentation.textRange

    override fun getDocumentationOwnerRange(): TextRange? = originalOwner.textRange

    override fun renderText(): String? {
        val docComment = modifiedDocumentation ?: return ""
        val result = buildString {
            renderKDoc(
                contentTag = docComment.getDefaultSection(),
                sections = docComment.getAllSections(),
            )
        }
        return JavaDocExternalFilter.filterInternalDocInfo(result)
    }

    override fun getOwnerTarget(): DocumentationTarget = createKotlinDocumentationTarget(originalOwner, originalOwner)
}
