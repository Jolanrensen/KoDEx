package nl.jolanrensen.kodex.intellij

/**
 * Completion info for a tag. Shows up in the completion popup.
 *
 * @param tag The tag name this completion info belongs to.
 * @param blockText The text that will be inserted when the completion is performed at the start of a line.
 * @param presentableBlockText The text that will be shown in the completion popup when the completion is performed
 *   at the start of a line.
 * @param moveCaretOffsetBlock The offset to move the caret to after the completion is performed at the start of a line.
 * @param inlineText The text that will be inserted when the completion is performed in the middle of a line.
 * @param presentableInlineText The text that will be shown in the completion popup when the completion is performed
 *   in the middle of a line.
 * @param moveCaretOffsetInline The offset to move the caret to after the completion is performed in the middle of a line.
 * @param tailText The text that will be shown alongside the presentable text in the completion popup.
 */
class CompletionInfo(
    val tag: String,
    val blockText: String? = "@$tag ",
    val presentableBlockText: String? = "@$tag",
    val moveCaretOffsetBlock: Int? = 0,
    val inlineText: String? = "{@$tag }",
    val presentableInlineText: String? = "{@$tag }",
    val moveCaretOffsetInline: Int? = -1,
    val tailText: String = "",
)
