package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.kodexHighlightingIsEnabled
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import java.awt.Font
import java.util.concurrent.ConcurrentHashMap

/**
 * This class is responsible for highlighting related symbols such as brackets in KDoc comments and
 * highlighting the background when touching it.
 *
 * Created by [KDocHighlightAnnotator].
 *
 * Threading: which ranges to highlight is computed in a single [readAction], because both
 * [Caret.getOffset][com.intellij.openapi.editor.Caret.getOffset] and reading PSI require read access
 * (or the EDT). The resulting markup is then applied on the EDT.
 */
class KDocHighlightListener private constructor(private val editor: Editor) :
    CaretListener,
    Disposable {

        companion object {
            private val log = logger<KDocHighlightListener>()

            private val instanceMap = ConcurrentHashMap<Editor, KDocHighlightListener>()

            fun getInstanceOrNull(editor: Editor): KDocHighlightListener? {
                instanceMap[editor]?.let { return it }
                if (editor.isDisposed) return null

                val listener = KDocHighlightListener(editor)
                val existing = instanceMap.putIfAbsent(editor, listener)
                if (existing != null) {
                    // we lost the race; our instance isn't registered anywhere yet, so drop it ourselves
                    Disposer.dispose(listener)
                    return existing
                }

                // only tie our lifetime to the editor's once we're in the map: for an editor that's already
                // gone this disposes us right away and [dispose] then has to be able to take us back out
                EditorUtil.disposeWithEditor(editor, listener)
                return listener
            }
        }

        private val scope = CoroutineScope(Dispatchers.Default) +
            SupervisorJob() +
            // without a handler the failure is swallowed entirely; kotlinx' fallback handler cannot be
            // initialized from the plugin classloader and dies with a NoClassDefFoundError of its own
            CoroutineExceptionHandler { _, throwable ->
                log.error("Could not update the KDoc highlighting at the carets", throwable)
            }

        /**
         * Requests to run [updateHighlightingAtCarets]; only the newest one is honored.
         * See [scheduleUpdateHighlighting] and the collector in `init`.
         */
        private val updateRequests = MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        private val loadedProcessors = getLoadedProcessors()

        /** Only touched from the EDT, see [applyHighlights]. */
        private val highlighters = mutableListOf<RangeHighlighter>()

        init {
            editor.caretModel.addCaretListener(this, this)

            // collectLatest cancels an in-flight update and waits for it to stop before starting the next,
            // so no two updates can ever touch [highlighters] at the same time
            scope.launch {
                updateRequests.collectLatest { updateHighlightingAtCarets() }
            }
        }

        /** Cancels any in-flight [updateHighlightingAtCarets] and runs a fresh one. */
        internal fun scheduleUpdateHighlighting() {
            updateRequests.tryEmit(Unit)
        }

        override fun caretPositionChanged(event: CaretEvent) = scheduleUpdateHighlighting()

        /** A single range of the editor to highlight, in document offsets. */
        private class Highlight(
            val startOffset: Int,
            val endOffset: Int,
            val layer: Int,
            val attributes: TextAttributes,
        )

        /**
         * Updates the highlighting of related symbols such as brackets.
         */
        private suspend fun updateHighlightingAtCarets() {
            // we're disposed along with the editor, so there's nothing left to clean up here
            if (editor.isDisposed) return

            if (!kodexHighlightingIsEnabled) return applyHighlights(emptyList())

            val project = editor.project ?: return
            val scheme = EditorColorsManager.getInstance().globalScheme

            val highlights = readAction {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                if (psiFile == null) {
                    emptyList()
                } else {
                    editor.caretModel.allCarets.flatMap { highlightsAtCaret(it.offset, psiFile, scheme) }
                }
            }

            applyHighlights(highlights)
        }

        /** Collects the [Highlight]s for the KDoc the caret at [caretOffset] is in. Requires read access. */
        private fun highlightsAtCaret(caretOffset: Int, psiFile: PsiFile, scheme: EditorColorsScheme): List<Highlight> {
            val kdoc = psiFile
                .findElementAt(caretOffset)
                ?.findParentOfType<KDoc>(strict = false)
                ?: return emptyList()

            val highlightInfos = getHighlightInfosFor(kdoc, loadedProcessors)
            val kdocStart = kdoc.startOffset
            val result = mutableListOf<Highlight>()

            // background
            val backgroundToHighlight = highlightInfos
                // take the first background to highlight, as it's generally the deepest
                .firstOrNull {
                    it.ranges.any { caretOffset in (kdocStart + it.extendLastByOne()) } &&
                        it.type == HighlightType.BACKGROUND
                }

            if (backgroundToHighlight != null) {
                // the background may have related backgrounds to also highlight
                val allBackgrounds = backgroundToHighlight
                    .related
                    .filter { it.type == HighlightType.BACKGROUND } +
                    backgroundToHighlight

                for (background in allBackgrounds) {
                    for (range in background.ranges) {
                        result += Highlight(
                            startOffset = kdocStart + range.first,
                            endOffset = kdocStart + range.last + 1,
                            layer = HighlighterLayer.ELEMENT_UNDER_CARET - 1,
                            attributes = textAttributesFor(HighlightType.BACKGROUND),
                        )
                    }
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
                } ?: return result

            for (symbol in relatedToHighlight) {
                for (range in symbol.ranges) {
                    result += Highlight(
                        startOffset = kdocStart + range.first,
                        endOffset = kdocStart + range.last + 1,
                        layer = HighlighterLayer.SELECTION + 100,
                        attributes = relatedHighlightAttributes,
                    )
                }
            }

            return result
        }

        /** Replaces all current [highlighters] with [highlights] on the EDT. */
        @Suppress("ktlint:standard:comment-wrapping")
        private suspend fun applyHighlights(highlights: List<Highlight>) {
            onEdt {
                if (editor.isDisposed) return@onEdt

                clearHighlighters()

                val markupModel = editor.markupModel
                val documentLength = editor.document.textLength

                for (highlight in highlights) {
                    // the document may have changed since the offsets were computed under the read lock,
                    // and out-of-bounds offsets make addRangeHighlighter throw
                    val isInDocument =
                        highlight.startOffset in 0..highlight.endOffset &&
                            highlight.endOffset <= documentLength
                    if (!isInDocument) continue

                    highlighters += markupModel.addRangeHighlighter(
                        // startOffset =
                        highlight.startOffset,
                        // endOffset =
                        highlight.endOffset,
                        // layer =
                        highlight.layer,
                        // textAttributes =
                        highlight.attributes,
                        // targetArea =
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                }
            }
        }

        /**
         * TODO fix using shadow.
         * Runs [block] on the EDT and suspends until it's done.
         *
         * Deliberately built on [Application.invokeLater] instead of `withContext(Dispatchers.EDT)`:
         * the plugin ships its own copy of kotlinx-coroutines (shaded into `kodex-common`), so the
         * `kotlinx.coroutines.Dispatchers` we see is not the one the platform's `Dispatchers.EDT`
         * extension was compiled against, and calling it throws a [LinkageError].
         */
        private suspend fun onEdt(block: () -> Unit) =
            suspendCancellableCoroutine { continuation ->
                // `any()`: highlighting only touches markup, never the document or PSI,
                // so there's no reason to wait for a modal dialog to close
                ApplicationManager.getApplication().invokeLater(
                    {
                        if (continuation.isActive) {
                            continuation.resumeWith(runCatching(block))
                        }
                    },
                    ModalityState.any(),
                )
            }

        /** Must run on the EDT. */
        private fun clearHighlighters() {
            highlighters.forEach { it.dispose() }
            highlighters.clear()
        }

        override fun dispose() {
            instanceMap.remove(editor, this)
            scope.cancel()
            // the highlighters are owned by the editor's markup model, which is disposed along with the editor
            highlighters.clear()
        }
    }
