package nl.jolanrensen.kodex.documentationProvider

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import nl.jolanrensen.kodex.services.DocProcessorService
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierList
import java.util.concurrent.CancellationException

// TODO not sure what this does yet, but let's include it
class DocProcessorDocumentationTargetProvider : DocumentationTargetProvider {

    init {
        println("DocProcessorDocumentationTargetProvider created")
    }

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.getInstance(project) }

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val service = getService(file.project)
        if (!service.isEnabled) return emptyList()
//        if (!file.language.`is`(KotlinLanguage.INSTANCE)) return emptyList()
        // show documentation based on java presentation
//        if (file.navigationElement is KtFile && originalElement?.containingFile is PsiJavaFile) return null

        val element = file.findElementAt(offset) ?: return emptyList()
        if (!element.isModifier()) return emptyList()

        try {
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(element) }
                ?: return emptyList()
            val kotlinDocTarget = createKotlinDocumentationTarget(
                element = modifiedElement,
                originalElement = element,
            ).takeUnless {
                val navigationElement = element.navigationElement

                // there are cases when documentation viewed from Java files
                // should NOT be based on Kotlin representation, but on original Java
                if (element.containingFile !is PsiJavaFile) {
                    return@takeUnless false
                }

                // top level functions and properties are accessible via file-wrapper class
                // `foo.kt` is represented in Java as `FooKt`.
                navigationElement is KtFile
            }
            return listOfNotNull(kotlinDocTarget)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            return emptyList()
        }
    }
}

internal fun PsiElement?.isModifier() =
    this != null && parent is KtModifierList && KtTokens.MODIFIER_KEYWORDS_ARRAY.firstOrNull { it.value == text } != null
