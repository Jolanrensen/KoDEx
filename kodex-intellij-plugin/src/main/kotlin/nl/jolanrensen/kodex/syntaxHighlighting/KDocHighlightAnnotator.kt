package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.intellij.contains
import nl.jolanrensen.kodex.kodexHighlightingIsEnabled
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.kdoc.psi.api.KDoc

/**
 * This class is responsible for coloring KDoc tags in the editor.
 */
class KDocHighlightAnnotator :
    Annotator,
    DumbAware {

    // are used stateless
    private val loadedProcessors = getLoadedProcessors()

    @Suppress("ktlint:standard:comment-wrapping")
    private fun HighlightInfo.createAsAnnotator(kdoc: KDoc, holder: AnnotationHolder) =
        ranges.forEach { range ->
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .let {
                    if (description.isNotBlank()) {
                        it.tooltip("$description ($tagProcessorName)")
                    } else {
                        it
                    }
                }
//                .needsUpdateOnTyping()
                .range(
                    TextRange(
                        /* startOffset = */ kdoc.startOffset + range.first,
                        /* endOffset = */ kdoc.startOffset + range.last + 1,
                    ),
                )
                .enforcedTextAttributes(textAttributesFor(type))
                .create()
        }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!kodexHighlightingIsEnabled) return
        if (element !is KDoc) return

        getHighlightInfosFor(element, loadedProcessors).forEach {
            // handled by KDocHighlightListener
            if (it.type != HighlightType.BACKGROUND) {
                it.createAsAnnotator(element, holder)
            }
        }

        val editor = element.findExistingEditor() ?: return
        KDocHighlightListener.getInstance(editor).scheduleUpdateHighlighting()
    }
}
