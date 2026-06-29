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
import kotlinx.coroutines.CancellationException
import nl.jolanrensen.kodex.kodexInlineRenderingIsEnabled
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.utils.docComment
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

/**
 * inline, used for rendering single doc comment in file, does not work for multiple, Issue #54,
 * this is handled by [DocProcessorDocumentationProvider].
 *
 * TODO slow, runs a lot!
 */
class DocProcessorInlineDocumentationProvider : InlineDocumentationProvider {

    init {
        println("DocProcessorInlineDocumentationProvider (K2) created")
    }

    class DocProcessorInlineDocumentation(
        private val originalDocumentation: PsiDocCommentBase,
        private val originalOwner: KtDeclaration,
        private val modifiedDocumentation: PsiDocCommentBase,
    ) : InlineDocumentation {

        override fun getDocumentationRange(): TextRange = originalDocumentation.textRange

        override fun getDocumentationOwnerRange(): TextRange? = originalOwner.textRange

        override fun renderText(): String? {
            val docComment = modifiedDocumentation as? KDoc ?: return null
            val result = buildString {
                renderKDoc(
                    contentTag = docComment.getDefaultSection(),
                    sections = docComment.getAllSections(),
                )
            }
            return JavaDocExternalFilter.filterInternalDocInfo(result)
        }

        override fun getOwnerTarget(): DocumentationTarget =
            createKotlinDocumentationTarget(originalOwner, originalOwner)
    }

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.Companion.getInstance(project) }

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentation]
     */
    val kotlin = KotlinInlineDocumentationProvider()

    // TODO works but is somehow overridden by CompatibilityInlineDocumentationProvider
    // TODO temporarily solved by diverting to DocProcessorDocumentationProvider, Issue #54
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val service = getService(file.project)
        if (!service.isEnabled || !kodexInlineRenderingIsEnabled) return emptyList()

        try {
            val result = mutableListOf<InlineDocumentation>()
            PsiTreeUtil.processElements(file) {
                val owner = it as? KtDeclaration ?: return@processElements true
                val originalDocumentation = owner.docComment ?: return@processElements true
                result += findInlineDocumentation(file, originalDocumentation.textRange) ?: return@processElements true

                true
            }

            return result
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            return emptyList()
        }
    }

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
            val modified = runBlockingCancellable { service.getModifiedElement(declaration) }

            if (modified == null) return null

            return DocProcessorInlineDocumentation(
                originalDocumentation = declaration.docComment as KDoc,
                originalOwner = declaration,
                modifiedDocumentation = modified.docComment as KDoc,
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }
}
