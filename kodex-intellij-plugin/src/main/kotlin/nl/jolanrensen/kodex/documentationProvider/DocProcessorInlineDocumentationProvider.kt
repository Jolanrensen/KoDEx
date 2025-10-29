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
import io.ktor.utils.io.CancellationException
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.kodexInlineRenderingIsEnabled
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.utils.docComment
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import kotlin.collections.plusAssign

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
        private val modifiedDocumentation: PsiDocCommentBase?, // Can be null if we render from structured tags
        private val processorService: DocProcessorService,
    ) : InlineDocumentation {

        override fun getDocumentationRange(): TextRange = originalDocumentation.textRange

        override fun getDocumentationOwnerRange(): TextRange? = originalOwner.textRange

        private fun buildDocumentationHtml(docContent: DocContent): String {
            val html = StringBuilder()

            // Extract main description
            // A simple way: find the first line starting with @ after trimming leading * and space
            val rawKDoc = docContent.value
            var firstTagIndex = -1
            val lines = rawKDoc.lines()
            for ((index, line) in lines.withIndex()) {
                val trimmedLine = line.trimStart().removePrefix("*").trimStart()
                if (trimmedLine.startsWith("@")) {
                    // Calculate the actual start index in the original rawKDoc
                    firstTagIndex = rawKDoc.indexOf(line)
                    break
                }
            }

            val mainDescription = if (firstTagIndex != -1) {
                rawKDoc.substring(0, firstTagIndex).trimEnd()
            } else {
                rawKDoc.trimEnd()
            }
            html.append(mainDescription.replace("\n", "<br>")) // Basic break lines for now

            // Render tags
            var firstParam = true
            var openedDl = false
            for (tag in docContent.structuredTags) {
                when (tag.tagName) {
                    "param" -> {
                        if (tag.subject != null) {
                            if (firstParam) {
                                html.append("<h4>Parameters:</h4><dl>")
                                openedDl = true
                                firstParam = false
                            }
                            html.append("<dt>${tag.subject}</dt><dd>${tag.description.value.replace("\n", "<br>")}</dd>")
                        }
                    }
                    // TODO: Handle other tags like @return, @throws, @see, etc.
                    // "return" -> html.append("<h4>Returns:</h4><p>${tag.description.value.replace("\n", "<br>")}</p>")
                    // "throws" -> {
                    //    html.append("<h4>Throws:</h4>")
                    //    html.append("<dl><dt>${tag.subject}</dt><dd>${tag.description.value.replace("\n", "<br>")}</dd></dl>")
                    // }
                }
            }
            if (openedDl) {
                html.append("</dl>")
            }

            return html.toString()
        }

        override fun renderText(): String? {
            val docContent = runBlockingCancellable {
                processorService.getDocumentableWrapperOrNull(originalOwner)
                    ?.let { processorService.getProcessedDocumentableWrapperOrNull(it) }
                    ?.docContent
            }

            if (docContent != null) {
                val html = buildDocumentationHtml(docContent)
                return JavaDocExternalFilter.filterInternalDocInfo(html)
            }

            // Fallback to existing logic if no structured tags or wrapper / docContent
            val docComment = modifiedDocumentation as? KDoc ?: originalDocumentation as? KDoc ?: return null
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

    // Replaced by direct injection in constructor of DocProcessorInlineDocumentation
    // private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()
    //
    // private fun getService(project: Project) =
    //    serviceInstances.getOrPut(project) { DocProcessorService.Companion.getInstance(project) }

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
        } catch (_: ProcessCanceledException) {
            return emptyList()
        } catch (_: CancellationException) {
            return emptyList()
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
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(declaration) }

            // We need the original KDoc for range, and modified for content (or structured tags)
            val originalKDoc = declaration.docComment as? KDoc ?: return null
            val modifiedKDoc = modifiedElement?.docComment as? KDoc

            return DocProcessorInlineDocumentation(
                originalDocumentation = originalKDoc,
                originalOwner = declaration,
                modifiedDocumentation = modifiedKDoc, // Can be null if modifiedElement is null or has no comment
                processorService = service,
            )
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }
}
