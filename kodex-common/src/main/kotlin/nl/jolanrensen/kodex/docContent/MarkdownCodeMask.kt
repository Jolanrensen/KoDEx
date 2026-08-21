package nl.jolanrensen.kodex.docContent

/** The indent at which a line becomes an indented code block. */
private const val CODE_BLOCK_INDENT = "   "

/** The characters a code fence can be made of, see [CodeFence]. */
private val CODE_FENCE_CHARS = charArrayOf('`', '~')

/** A code fence needs at least this many [CODE_FENCE_CHARS] in a row, see [CodeFence]. */
private const val MIN_CODE_FENCE_LENGTH = 3

/** A code fence may be indented by at most this many spaces, see [CodeFence]. */
private const val MAX_CODE_FENCE_INDENT = 3

/**
 * A code fence, so the run of [char]s that opens or closes a fenced code block,
 * together with the [infoString] that follows it on the same line.
 *
 * See [the GFM spec](https://github.github.com/gfm/#fenced-code-blocks).
 */
private class CodeFence(val char: Char, val length: Int, val infoString: String) {

    /**
     * A backtick fence cannot have a backtick in its info string,
     * as that would make it an inline code span instead.
     */
    val canOpenBlock: Boolean
        get() = char != '`' || '`' !in infoString

    /** Only a fence of the same [char], at least as long, and followed by nothing else, closes [openFence]. */
    fun closes(openFence: CodeFence): Boolean =
        char == openFence.char &&
            length >= openFence.length &&
            infoString.isBlank()
}

/** Reads the [CodeFence] this line starts with, or `null` if it doesn't start with one. */
private fun String.codeFenceOrNull(): CodeFence? {
    val indent = takeWhile { it == ' ' }.length
    if (indent > MAX_CODE_FENCE_INDENT) return null

    val char = getOrNull(indent)?.takeIf { it in CODE_FENCE_CHARS } ?: return null
    val length = drop(indent).takeWhile { it == char }.length
    if (length < MIN_CODE_FENCE_LENGTH) return null

    return CodeFence(char = char, length = length, infoString = substring(indent + length))
}

/**
 * Marks each index of the doc content with whether it's part of markdown code.
 *
 * Something counts as code when it's inside
 * a fenced code block (from a line opening with a [CodeFence] up to and including the line that
 * closes it),
 * an indented code block (a line starting with [CODE_BLOCK_INDENT]),
 * or an inline code span (text on a single line surrounded by an equal number of backticks).
 * Backticks are ignored if "\" escaped.
 */
fun DocContent.getCodeMask(): BooleanArray {
    val kdoc = this.value
    val mask = BooleanArray(kdoc.length)
    var openFence: CodeFence? = null
    var lineStart = 0

    while (lineStart < kdoc.length) {
        val newline = kdoc.indexOf('\n', lineStart)
        val lineEnd = if (newline == -1) kdoc.length else newline // exclusive
        val line = kdoc.substring(lineStart, lineEnd)
        val fence = line.codeFenceOrNull()

        when {
            // inside a fenced block every line is code, including the fence lines themselves;
            // only a matching fence closes it, any other fence is just content
            openFence != null -> {
                mask.fill(true, lineStart, lineEnd)
                if (fence?.closes(openFence) == true) openFence = null
            }

            fence != null && fence.canOpenBlock -> {
                mask.fill(true, lineStart, lineEnd)
                openFence = fence
            }

            line.startsWith(CODE_BLOCK_INDENT) ->
                mask.fill(true, lineStart, lineEnd)

            else -> mask.markCodeSpansOfLine(line, lineStart)
        }
        lineStart = lineEnd + 1
    }
    return mask
}

/**
 * Marks all inline code spans of [line], which starts at [lineStart] in the doc content, in this mask.
 *
 * Like in markdown, a span is opened by a run of backticks and closed by the next run of the same
 * length; a run without a matching one is just text.
 */
private fun BooleanArray.markCodeSpansOfLine(line: String, lineStart: Int) {
    var openingRun: IntRange? = null
    var i = 0
    while (i < line.length) {
        when (line[i]) {
            // skip the escaped char
            '\\' -> i++

            '`' -> {
                var runEnd = i
                while (line.getOrNull(runEnd + 1) == '`') runEnd++

                when {
                    openingRun == null -> openingRun = i..runEnd

                    // only a run of the same length closes the span
                    runEnd - i == openingRun.last - openingRun.first -> {
                        fill(true, lineStart + openingRun.first, lineStart + runEnd + 1)
                        openingRun = null
                    }
                }
                i = runEnd
            }
        }
        i++
    }
}
