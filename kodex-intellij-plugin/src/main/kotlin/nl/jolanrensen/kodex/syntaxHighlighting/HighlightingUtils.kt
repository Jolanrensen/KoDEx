package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContentWithMap
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.intellij.applyMapping
import nl.jolanrensen.kodex.intellij.contains
import nl.jolanrensen.kodex.intellij.removeIndices
import nl.jolanrensen.kodex.processor.DocProcessor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import java.awt.Font

internal fun textAttributesFor(highlightType: HighlightType): TextAttributes {
    val scheme = EditorColorsManager.getInstance().globalScheme

    val metadataAttributes by lazy { scheme.getAttributes(DefaultLanguageHighlighterColors.METADATA) }
    val kdocLinkAttributes by lazy { scheme.getAttributes(KotlinHighlightingColors.KDOC_LINK) }
    val commentAttributes by lazy { scheme.getAttributes(KotlinHighlightingColors.BLOCK_COMMENT) }
    val declarationAttributes by lazy { scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME) }

    val backgroundHighlightColor = scheme.getAttributes(KotlinHighlightingColors.SMART_CAST_VALUE)
        .backgroundColor

    return when (highlightType) {
        HighlightType.BRACKET ->
            metadataAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
            }

        HighlightType.TAG ->
            metadataAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = metadataAttributes.foregroundColor
            }

        HighlightType.TAG_KEY ->
            kdocLinkAttributes.clone().apply {}

        HighlightType.TAG_VALUE ->
            declarationAttributes.clone().apply {}

        HighlightType.COMMENT ->
            commentAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
            }

        HighlightType.COMMENT_TAG ->
            commentAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = commentAttributes.foregroundColor
            }

        // handled by KDocHighlightListener, should only be applied when touching
        HighlightType.BACKGROUND -> TextAttributes().apply {
            backgroundColor = backgroundHighlightColor
        }
    }
}

internal fun getHighlightInfosFor(kdoc: KDoc, loadedProcessors: List<DocProcessor>): List<HighlightInfo> {
    val docText = kdoc.text.asDocTextOrNull() ?: return emptyList()

    // convert the doc text to doc content to retrieve the highlights from the processors
    val (docContent, mapping) = docText.getDocContentWithMap()

    val docContentHighlightInfos = buildList<HighlightInfo> {
        for (processor in loadedProcessors) {
            val highlightInfo = processor.getHighlightsFor(docContent)
                // exclude highlights that are already covered by previous processors
                // TODO fix order for processors with multiple tags, like get/set
                .removeIndices { index ->
                    this.any { index in it }
                }

            addAll(highlightInfo)
        }
    }

    return docContentHighlightInfos.applyMapping(mapping::get) // map back to doc text indices
}

internal fun IntRange.extendLastByOne() = first..last + 1

internal operator fun Int.plus(range: IntRange) = range.first + this..range.last + this

internal operator fun IntRange.plus(int: Int) = this.first + int..this.last + int
