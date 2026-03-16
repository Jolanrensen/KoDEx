@file:Suppress("UnstableApiUsage")

package nl.jolanrensen.kodex.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import io.ktor.utils.io.CancellationException
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.utils.docComment
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Image
import java.util.function.Consumer

/**
 * k1-like method to render multiple documentation items at once, TODO issue #54
 * Likely to be called often and fail even more, so catching all exceptions.
 */
@OptIn(K1Deprecation::class)
class DocProcessorDocumentationProvider :
    AbstractDocumentationProvider(),
    ExternalDocumentationProvider {

    init {
        println("DocProcessorDocumentationProvider (K2) created")
    }

    private val kotlin = KotlinDocumentationProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.Companion.getInstance(project) }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        kotlin.getQuickNavigateInfo(element, originalElement)

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? =
        kotlin.getUrlFor(element, originalElement)

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file !is KtFile) return
        if (!getService(file.project).isEnabled) return

        try {
            // capture all comments in the file
            PsiTreeUtil.processElements(file) {
                val comment = (it as? KtDeclaration)?.docComment
                if (comment != null) {
                    sink.accept(comment)
                }
                true
            }
        } catch (_: ProcessCanceledException) {
        } catch (_: CancellationException) {
        } catch (e: Throwable) {
            // e.printStackTrace()
        }
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val service = getService(element.project)
        if (!service.isEnabled) return null
        try {
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(element) }
            return kotlin.generateDoc(modifiedElement ?: element, originalElement)
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            // e.printStackTrace()
            return null
        }
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        val service = getService(comment.project)
        if (!service.isEnabled) return null
        try {
            val owner = comment.owner ?: return null
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(owner) }
            return kotlin.generateRenderedDoc(modifiedElement?.docComment ?: comment)
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            // e.printStackTrace()
            return null
        }
    }

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? =
        kotlin.findDocComment(file, range)

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLookupItem(psiManager, `object`, element)

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        context: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLink(psiManager, link, context)

    @Deprecated("Deprecated in Java")
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
    ): PsiElement? = kotlin.getCustomDocumentationElement(editor, file, contextElement)

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? =
        getCustomDocumentationElement(
            editor = editor,
            file = file,
            contextElement = contextElement,
        )

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? =
        kotlin.getLocalImageForElement(element, imageSpec)

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean =
        CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean =
        kotlin.canPromptToConfigureDocumentation(element)

    override fun promptToConfigureDocumentation(element: PsiElement?) = kotlin.promptToConfigureDocumentation(element)
}
