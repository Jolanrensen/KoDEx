package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.OpenFileAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import nl.jolanrensen.kodex.annotations.ExportAsHtml
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.getDocContentForHtmlRange
import nl.jolanrensen.kodex.html.renderToHtml
import nl.jolanrensen.kodex.kodexRenderingIsEnabled
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.utils.annotationNames
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.util.getValueArgumentsInParentheses
import java.io.File
import javax.swing.Icon

/**
 * Provides `< >` icons in the gutter for KDocs with the `@ExportAsHtml` tag.
 * Clicking the icon will open the exported HTML file in the editor.
 */
class ExportAsHtmlAnnotator : Annotator {

    @Suppress("UnstableApiUsage")
    class GutterIcon(val declaration: KtDeclaration, val annotation: KtAnnotationEntry) : GutterIconRenderer() {
        override fun getTooltipText(): String = "Click to preview exported HTML"

        override fun getClickAction(): AnAction =
            object : AnAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    val service = DocProcessorService.getInstance(annotation.project)
                    val documentableWrapper = service.getDocumentableWrapperOrNull(declaration) ?: return
                    val processedDocumentableWrapper = runBlockingCancellable {
                        service.getProcessedDocumentableWrapperOrNull(documentableWrapper)
                    } ?: return

                    val arguments = annotation.getValueArgumentsInParentheses()

                    val htmlFile = exportToHtmlFile(arguments, processedDocumentableWrapper)
                    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(htmlFile)

                    OpenFileAction.openFile(
                        file = virtualFile ?: return,
                        project = e.project ?: return,
                    )
                }
            }

        override fun isNavigateAction(): Boolean = true

        override fun getIcon(): Icon = AllIcons.FileTypes.Html

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GutterIcon

            if (declaration != other.declaration) return false
            if (annotation != other.annotation) return false

            return true
        }

        override fun hashCode(): Int {
            var result = declaration.hashCode()
            result = 31 * result + annotation.hashCode()
            return result
        }

        override fun toString(): String = "GutterIcon(declaration=$declaration, annotation=$annotation)"
    }

    private fun annotate(declaration: KtDeclaration, annotation: KtAnnotationEntry, holder: AnnotationHolder) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .needsUpdateOnTyping()
            .gutterIconRenderer(GutterIcon(declaration, annotation))
            .range(annotation.textRange)
            .create()
    }

    // we'll count this as "doc processor enabled" and not highlighting, as it
    // needs to actually run the preprocessors itself.
    private val isEnabled get() = kodexRenderingIsEnabled

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!isEnabled) return
        if (element !is KtDeclaration) return
        if (element.docComment == null) return

        val hasExportAsHtmlTag = element.annotationNames.any {
            ExportAsHtml::class.simpleName!! in it
        }
        if (!hasExportAsHtmlTag) return

        val annotation = element.annotationEntries.firstOrNull {
            ExportAsHtml::class.simpleName!! in it.shortName!!.asString()
        } ?: return

        annotate(element, annotation, holder)
    }
}

private fun exportToHtmlFile(annotationArguments: List<ValueArgument>, doc: DocumentableWrapper): File {
    val themeArg = annotationArguments.firstOrNull {
        it.getArgumentName()?.asName?.toString() == ExportAsHtml::theme.name
    } ?: annotationArguments.getOrNull(0)?.takeIf {
        !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
    }
    val theme = themeArg?.getArgumentExpression()?.text?.toBoolean() ?: true

    val stripReferencesArg = annotationArguments.firstOrNull {
        it.getArgumentName()?.asName?.toString() == ExportAsHtml::stripReferences.name
    } ?: annotationArguments.getOrNull(1)?.takeIf {
        !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
    }
    val stripReferences = stripReferencesArg?.getArgumentExpression()?.text?.toBoolean() ?: true

    val html = doc
        .getDocContentForHtmlRange()
        .renderToHtml(theme = theme, stripReferences = stripReferences, includeResizeScript = false)
    val file = File.createTempFile(doc.fullyQualifiedPath, ".html")
    file.writeText(html)

    return file
}
