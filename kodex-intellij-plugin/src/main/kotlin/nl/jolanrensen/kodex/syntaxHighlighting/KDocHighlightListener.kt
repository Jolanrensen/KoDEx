package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.kodexHighlightingIsEnabled
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * This class is responsible for highlighting related symbols such as brackets in KDoc comments and
 * highlighting the background when touching it.
 *
 * Created by [ExportAsHtmlAnnotator].
 */
class KDocHighlightListener private constructor(private val editor: Editor) :
    CaretListener,
    KeyListener,
    Disposable {

    companion object {
        private val instanceMap = mutableMapOf<Editor, KDocHighlightListener>()

        fun getInstance(editor: Editor): KDocHighlightListener =
            instanceMap.getOrPut(editor) { KDocHighlightListener(editor) }
    }

    init {
        editor.caretModel.addCaretListener(this, this)
        editor.contentComponent.addKeyListener(this, this)
    }


    val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    private val loadedProcessors = getLoadedProcessors()
    private val highlighters = mutableListOf<RangeHighlighter>()

    /** The currently running highlighting job, if any. Only one runs at a time; see [scheduleUpdateHighlighting]. */
    @Volatile
    private var updateJob: Job? = null

    /**
     * Cancels any in-flight [updateHighlightingAtCarets] and launches a fresh one,
     * guaranteeing only the newest update runs inside [scope].
     */
    @Synchronized
    internal fun scheduleUpdateHighlighting() {
        val previous = updateJob
        updateJob = scope.launch {
            // wait for the previous (now superseded) update to fully stop before starting,
            // so they never touch `highlighters` concurrently
            previous?.cancelAndJoin()
            updateHighlightingAtCarets()
        }
    }

    override fun caretPositionChanged(event: CaretEvent) = scheduleUpdateHighlighting()

    override fun keyTyped(e: KeyEvent?) = scheduleUpdateHighlighting()

    override fun keyPressed(e: KeyEvent?) = Unit

    override fun keyReleased(e: KeyEvent?) = scheduleUpdateHighlighting()

    /**
     * Updates the highlighting of related symbols such as brackets.
     */
    @Suppress("ktlint:standard:comment-wrapping")
    private suspend fun updateHighlightingAtCarets() {
        clearHighlighters()
        if (editor.isDisposed) return dispose()
        if (!kodexHighlightingIsEnabled) return

        val scheme = EditorColorsManager.getInstance().globalScheme
        val markupModel = editor.markupModel as MarkupModelEx
        val psiFile = PsiDocumentManager.getInstance(editor.project ?: return)
            .getPsiFile(editor.document) ?: return

        for (it in editor.caretModel.allCarets) {
            updateHighlightingAtCaret(it, psiFile, scheme, markupModel)
        }
    }

    private suspend fun updateHighlightingAtCaret(
        caret: Caret,
        psiFile: PsiFile,
        scheme: EditorColorsScheme,
        markupModel: MarkupModelEx,
    ) {
        val caretOffset = caret.offset
        val kdoc = readAction {
            psiFile.findElementAt(caretOffset)?.findParentOfType<KDoc>(strict = false)
        }
            ?: return
        val highlightInfos = getHighlightInfosFor(kdoc, loadedProcessors)
        val kdocStart = kdoc.startOffset

        // background
        val backgroundToHighlight = highlightInfos
            // take the first background to highlight, as it's generally the deepest
            .firstOrNull {
                it.ranges.any {
                    caretOffset in (kdocStart + it.extendLastByOne())
                } && it.type == HighlightType.BACKGROUND
            }

        backgroundToHighlight
            ?.let {
                // the background may have related backgrounds to also highlight
                it.related.filter { it.type == HighlightType.BACKGROUND } + it
            }?.forEach {
                for (range in it.ranges) {
                    highlighters += markupModel.addRangeHighlighter(
                        // startOffset =
                        kdocStart + range.first,
                        // endOffset =
                        kdocStart + range.last + 1,
                        // layer =
                        HighlighterLayer.ELEMENT_UNDER_CARET - 1,
                        // textAttributes =
                        textAttributesFor(HighlightType.BACKGROUND),
                        // targetArea =
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                }
            }

        // related symbols such as brackets
        val relatedHighlightAttributes by lazy {
            scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
                .clone()
                .apply {
                    fontType = Font.BOLD + Font.ITALIC
                }
        }

        val relatedToHighlight = highlightInfos
            // only add bracket matching for the background that is highlighted currently
            .filter { it.type != HighlightType.BACKGROUND }
            .let { if (backgroundToHighlight != null) it.plus(backgroundToHighlight) else it }
            .firstNotNullOfOrNull {
                // we're trying to highlight brackets, not backgrounds
                val related = it.related.filter { it.type != HighlightType.BACKGROUND }
                if (related.isNotEmpty() && it.ranges.any { caretOffset in (kdocStart + it.extendLastByOne()) }) {
                    if (it.type == HighlightType.BACKGROUND) {
                        related
                    } else {
                        related + it
                    }
                } else {
                    null
                }
            } ?: return

        for (it in relatedToHighlight) {
            for (range in it.ranges)
                highlighters += markupModel.addRangeHighlighter(
                    // startOffset =
                    kdocStart + range.first,
                    // endOffset =
                    kdocStart + range.last + 1,
                    // layer =
                    HighlighterLayer.SELECTION + 100,
                    // textAttributes =
                    relatedHighlightAttributes,
                    // targetArea =
                    HighlighterTargetArea.EXACT_RANGE,
                )
        }
    }

    private fun clearHighlighters() {
        highlighters.forEach { it.dispose() }
        highlighters.clear()
    }

    override fun dispose() {
        clearHighlighters()
        instanceMap.remove(editor)
    }
}
