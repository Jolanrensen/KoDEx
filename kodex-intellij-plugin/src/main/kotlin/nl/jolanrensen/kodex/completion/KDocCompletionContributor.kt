@file:Suppress("ktlint:standard:comment-wrapping")

package nl.jolanrensen.kodex.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.ui.IconManager
import com.intellij.util.ProcessingContext
import nl.jolanrensen.kodex.completion.Mode.AFTER_ASTERISK
import nl.jolanrensen.kodex.completion.Mode.AT_TAG_NAME
import nl.jolanrensen.kodex.completion.Mode.IN_TEXT
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.kodexCompletionIsEnabled
import org.jetbrains.kotlin.idea.completion.or
import org.jetbrains.kotlin.idea.completion.singleCharPattern
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens

/**
 * Contributes completions for new special KDoc tags.
 */
class KDocCompletionContributor : CompletionContributor() {
    init {
        if (kodexCompletionIsEnabled) {
            extend(
                CompletionType.BASIC,
                // for block tags: activates after `*` or `/**`
                psiElement().afterLeaf(
                    psiElement(KDocTokens.LEADING_ASTERISK) or psiElement(KDocTokens.START),
                ),
                KDocProcessorTagCompletionProvider(AFTER_ASTERISK),
            )
            extend(
                CompletionType.BASIC,
                // for block tags: activates at exactly `@xxx`, also in the middle of the tag name
                psiElement(KDocTokens.TAG_NAME),
                KDocProcessorTagCompletionProvider(AT_TAG_NAME),
            )
            extend(
                CompletionType.BASIC,
                // for inline tags: activates anywhere in the kdoc text
                psiElement(KDocTokens.TEXT) or psiElement(KDocTokens.CODE_BLOCK_TEXT),
                KDocProcessorTagCompletionProvider(IN_TEXT),
            )
        }
    }
}

enum class Mode {
    AFTER_ASTERISK, // after `*` or `/**`
    AT_TAG_NAME, // at exactly `@xxx`, also in the middle of the tag name
    IN_TEXT, // anywhere in the kdoc text
}

// keeps leading blanks in place, removes the rest
internal fun String.removeDummyIdentifier(): String =
    if (trimStart().startsWith(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
        val numberOfBlanks = length - trimStart().length
        substring(0, numberOfBlanks)
    } else {
        this
    }

class KDocProcessorTagCompletionProvider(private val mode: Mode) : CompletionProvider<CompletionParameters>() {
    private val activeDocProcessors = getLoadedProcessors()

    private val icon by lazy {
        IconManager.getInstance()
            .getIcon("icons/KoDEx-K-lookupElement.svg", KDocCompletionContributor::class.java.classLoader)
    }

    private fun LookupElementBuilder.withIcon(): LookupElementBuilder = withIcon(icon)

    private fun LookupElementBuilder.moveCaret(offset: Int): LookupElementBuilder =
        withInsertHandler { c, _ ->
            EditorModificationUtil.moveCaretRelatively(c.editor, offset)
        }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (!kodexCompletionIsEnabled) return

        val charPattern = when (mode) {
            IN_TEXT, AFTER_ASTERISK -> singleCharPattern('@') or singleCharPattern('{') or singleCharPattern('$')
            AT_TAG_NAME -> singleCharPattern('@')
        }
        // findIdentifierPrefix() requires identifier part characters to be a superset of identifier start characters
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            StandardPatterns.character().javaIdentifierPart() or charPattern,
            StandardPatterns.character().javaIdentifierStart() or charPattern,
        )

        if (parameters.isAutoPopup && prefix.isEmpty()) return

        // check special conditions for adding completions
        when (mode) {
            AFTER_ASTERISK -> {
                val text = parameters.position.text.removeDummyIdentifier().substringBefore('@')
                if (text.isNotBlank() || text.length > 3) return
            }

            AT_TAG_NAME ->
                if (prefix.isNotEmpty() && !prefix.startsWith('@')) return

            IN_TEXT ->
                if (prefix.isNotEmpty() && prefix.first() !in setOf('@', '{', '$')) return
        }

        val resultWithPrefix = result.withPrefixMatcher(prefix)
        for (processor in activeDocProcessors) {
            for (info in processor.completionInfos) {
                // inline tags
                if (info.inlineText != null &&
                    info.presentableInlineText != null &&
                    info.moveCaretOffsetInline != null
                ) {
                    resultWithPrefix.addElement(
                        LookupElementBuilder.create(info.inlineText!!)
                            .withPresentableText(info.presentableInlineText!!)
                            .moveCaret(info.moveCaretOffsetInline!!)
                            .withTailText("  ${info.tailText} (${processor.name})")
                            .withIcon(),
                    )
                }

                // block tags
                if (mode == AT_TAG_NAME || mode == AFTER_ASTERISK) {
                    if (info.blockText != null ||
                        info.presentableBlockText != null ||
                        info.moveCaretOffsetBlock != null
                    ) {
                        resultWithPrefix.addElement(
                            LookupElementBuilder.create(info.blockText!!)
                                .withPresentableText(info.presentableBlockText!!)
                                .moveCaret(info.moveCaretOffsetBlock!!)
                                .withTailText("  ${info.tailText} (${processor.name})")
                                .withIcon(),
                        )
                    }
                }
            }
        }
    }
}
