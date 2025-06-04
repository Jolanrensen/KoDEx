package nl.jolanrensen.kodex.docContent

import nl.jolanrensen.kodex.docContent.ReferenceState.INSIDE_ALIASED_REFERENCE
import nl.jolanrensen.kodex.docContent.ReferenceState.INSIDE_REFERENCE
import nl.jolanrensen.kodex.docContent.ReferenceState.NONE
import nl.jolanrensen.kodex.utils.BACKTICKS
import nl.jolanrensen.kodex.utils.CURLY_BRACES
import nl.jolanrensen.kodex.utils.getTagNameOrNull
import nl.jolanrensen.kodex.utils.removeAllElementsFromLast
import org.intellij.lang.annotations.Language
import java.util.SortedMap

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
class DocContent(val value: String) {
    val structuredTags: List<KDocTag>

    init {
        structuredTags = parseKDocTags(value)
    }

    private fun parseKDocTags(rawKdoc: String): List<KDocTag> {
        val tags = mutableListOf<KDocTag>()
        val blocks = rawKdoc.asDocContent().splitPerBlock(ignoreKDocMarkers = true)

        for (block in blocks) {
            val blockText = block.value.trim()
            val tagName = block.getTagNameOrNull()

            if (tagName != null) {
                // Basic parsing: further refinement needed for subject and description
                val tagContentParts = blockText.removePrefix("@$tagName").trimStart().split(" ", limit = 2)
                val subject = if (tagContentParts.isNotEmpty() && !tagContentParts[0].startsWith("\n") && tagContentParts[0].isNotBlank()) tagContentParts[0] else null
                val description = if (tagContentParts.size > 1) tagContentParts[1] else (if (subject == null && tagContentParts.isNotEmpty()) tagContentParts[0] else "")

                tags.add(
                    KDocTag(
                        tagName = tagName,
                        subject = subject,
                        description = DocContent(description)
                    )
                )
            }
        }
        return tags
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocContent

        if (value != other.value) return false
        if (structuredTags != other.structuredTags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + structuredTags.hashCode()
        return result
    }
}

fun String.asDocContent(): DocContent = DocContent(this)

/**
 * Get tag name from the start of some content.
 * Can handle both
 * `  @someTag someContent`
 * and
 * `{@someTag someContent}`
 * and will return "someTag" in these cases.
 */
fun DocContent.getTagNameOrNull(): String? = value.getTagNameOrNull()

/**
 * a.aaaaaa.aaaaa.aa.aaaa
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional (up to max 2) leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes triple backticks and `{@..}` and `${..}` into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitPerBlock(ignoreKDocMarkers: Boolean = false): List<DocContent> {
    val docContent = this@splitPerBlock.value.split('\n')
    return buildList {
        var currentBlock = ""

        /**
         * keeps track of the current blocks
         * denoting `{@..}` with [CURLY_BRACES] and triple "`" with [BACKTICKS]
         */
        val blocksIndicators = mutableListOf<Char>()

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        for (lineToUse in docContent) {
            val lineToCheck = if (ignoreKDocMarkers) {
                lineToUse
                    .trimStart()
                    .removePrefix("*")
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .removeSuffix(" ")
            } else {
                lineToUse
            }

            // start a new block if the line starts with a tag and we're not
            // in a {@..} or ```..``` block
            val lineStartsWithTag = lineToCheck
                .removePrefix(" ")
                .removePrefix(" ")
                .startsWith("@")

            when {
                // start a new block if the line starts with a tag and we're not in a {@..} or ```..``` block
                lineStartsWithTag && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotEmpty()) {
                        this += currentBlock.removeSuffix("\n").asDocContent()
                    }
                    currentBlock = "$lineToUse\n"
                }

                lineToCheck.isEmpty() && blocksIndicators.isEmpty() -> {
                    currentBlock += "\n"
                }

                else -> {
                    if (currentBlock.isEmpty()) {
                        currentBlock = "$lineToUse\n"
                    } else {
                        currentBlock += "$lineToUse\n"
                    }
                }
            }
            var escapeNext = false
            for ((i, char) in lineToCheck.withIndex()) {
                when {
                    escapeNext -> {
                        escapeNext = false
                        continue
                    }

                    char == '\\' ->
                        escapeNext = true

                    // ``` detection
                    char == '`' && lineToCheck.getOrNull(i + 1) == '`' && lineToCheck.getOrNull(i + 2) == '`' ->
                        if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
                }
                if (isInCodeBlock()) continue
                when {
                    // {@ detection
                    char == '{' && lineToCheck.getOrNull(i + 1) == '@' ->
                        blocksIndicators += CURLY_BRACES

                    // ${ detection for ArgDocProcessor
                    char == '{' && lineToCheck.getOrNull(i - 1) == '$' && lineToCheck.getOrNull(i - 2) != '\\' ->
                        blocksIndicators += CURLY_BRACES

                    char == '}' ->
                        blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                }
            }
        }
        this += currentBlock.removeSuffix("\n").asDocContent()
    }
}

/**
 * Split doc content in blocks of content and text belonging to tags, with the range of the block.
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitPerBlockWithRanges(): List<Pair<DocContent, IntRange>> {
    val splitDocContents = this.splitPerBlock()
    var i = 0

    return buildList {
        for ((index, docContent) in splitDocContents.withIndex()) {
            val range =
                if (index == splitDocContents.lastIndex) {
                    i..<i + docContent.value.length // last element has no trailing \n
                } else {
                    i..i + docContent.value.length
                }
            this += Pair(docContent, range)
            i += docContent.value.length + 1
        }
    }
}

/**
 * Finds all inline tag names, including nested ones,
 * together with their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesWithRanges(): List<Pair<String, IntRange>> {
    val text = value
    val map: SortedMap<Int, MutableList<Pair<String, IntRange>>> = sortedMapOf(Comparator.reverseOrder())

    // holds the current start indices of {@tags found
    val queue = ArrayDeque<Int>()

    var escapeNext = false
    for ((i, char) in value.withIndex()) {
        when {
            escapeNext -> escapeNext = false

            char == '\\' -> escapeNext = true

            char == '{' && value.getOrElse(i + 1) { ' ' } == '@' -> {
                queue.addFirst(i)
            }

            char == '}' -> {
                if (queue.isNotEmpty()) {
                    val start = queue.removeFirst()
                    val end = i
                    val depth = queue.size
                    val tag = text.substring(start..end)
                    val tagName = tag.getTagNameOrNull()

                    if (tagName != null) {
                        map.getOrPut(depth) { mutableListOf() } += tagName to start..end
                    }
                }
            }
        }
    }
    return map.values.flatten()
}

/**
 * Finds all inline tag names, including nested ones.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNames(): List<String> = findInlineTagNamesWithRanges().map { it.first }

/** Finds all block tag names. */
fun DocContent.findBlockTagNames(): List<String> =
    splitPerBlock()
        .filter { it.value.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

/** Finds all block tags with ranges. */
fun DocContent.findBlockTagsWithRanges(): List<Pair<String, IntRange>> =
    splitPerBlockWithRanges()
        .filter { it.first.value.trimStart().startsWith("@") }
        .mapNotNull {
            val tagName = it.first.getTagNameOrNull() ?: return@mapNotNull null
            tagName to it.second
        }

/** Finds all tag names, including inline and block tags. */
fun DocContent.findTagNames(): List<String> =
    findInlineTagNames() +
        findBlockTagNames()

/** Is able to find an entire JavaDoc/KDoc comment including the starting indent. */
val docRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")

val javaLinkRegex = Regex("""\{@link.*}""")

private enum class ReferenceState {
    NONE,
    INSIDE_REFERENCE,
    INSIDE_ALIASED_REFERENCE,
}

// TODO?
private enum class CodeBlockState {
    NONE,
    INSIDE_CODE_BLOCK_OUTSIDE_REF,
    INSIDE_CODE_BLOCK_INSIDE_REF,
}

/**
 * Replace KDoc links in doc content with the result of [process].
 *
 * Replaces all `[Aliased][ReferenceLinks]` with `[Aliased][ProcessedPath]`
 * and all `[ReferenceLinks]` with `[ReferenceLinks][ProcessedPath]`.
 */
fun DocContent.replaceKdocLinks(process: (String) -> String): DocContent {
    val kdoc = this.value
    var escapeNext = false
    var referenceState = NONE
    var insideCodeBlock = false

    return buildString {
        var currentBlock = ""

        fun appendCurrentBlock() {
            append(currentBlock)
            currentBlock = ""
        }

        for ((i, char) in kdoc.withIndex()) {
            fun nextChar(): Char? = kdoc.getOrNull(i + 1)

            fun previousChar(): Char? = kdoc.getOrNull(i - 1)

            if (escapeNext) {
                escapeNext = false
            } else {
                when (char) {
                    '\\' ->
                        escapeNext = true

                    '`' ->
                        if (referenceState != NONE) {
                            insideCodeBlock = !insideCodeBlock
                        }

                    '\n', '\r' ->
                        referenceState = NONE

                    '[' ->
                        if (!insideCodeBlock) {
                            referenceState =
                                if (previousChar() == ']') {
                                    INSIDE_ALIASED_REFERENCE
                                } else {
                                    INSIDE_REFERENCE
                                }
                            appendCurrentBlock()
                        }

                    ']' ->
                        if (!insideCodeBlock && nextChar() !in listOf('[', '(')) {
                            currentBlock = processReference(
                                referenceState = referenceState,
                                currentBlock = currentBlock,
                                process = process,
                            )
                            appendCurrentBlock()
                            referenceState = NONE
                        }
                }
            }
            currentBlock += char
        }
        appendCurrentBlock()
    }.asDocContent()
}

@Language("regexp")
private const val LETTER = "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}]"

@Language("regexp")
private const val UNICODE_DIGIT = "\\p{Nd}"

// From https://github.com/Kotlin/kotlin-spec, full regex:
// (?:[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]|_)[[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]_\p{Nd}]*|`[^\r\n`]+`
@Language("regexp")
private const val IDENTIFIER =
    "(?:$LETTER|_)[${LETTER}_$UNICODE_DIGIT]*|`[^\\r\\n`]+`"

// full regex:
// (?:(?:[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]|_)[[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]_\p{Nd}]*|`[^\r\n`]+`)(?:\.(?:(?:[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]|_)[[\p{Lu}\p{Ll}\p{Lt}\p{Lm}\p{Lo}]_\p{Nd}]*|`[^\r\n`]+`)+)*
@Language("regexp")
private const val QUALIFIED_NAME = "(?:$IDENTIFIER)(?:\\.(?:$IDENTIFIER)+)*"

private fun StringBuilder.processReference(
    referenceState: ReferenceState,
    currentBlock: String,
    process: (String) -> String,
): String {
    var currentReferenceBlock = currentBlock
    when (referenceState) {
        INSIDE_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef matches QUALIFIED_NAME.toRegex()) {
                val processedRef = process(originalRef)
                if (processedRef == originalRef) {
                    append("[$originalRef")
                } else {
                    append("[$originalRef][$processedRef")
                }
                currentReferenceBlock = ""
            }
        }

        INSIDE_ALIASED_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef matches QUALIFIED_NAME.toRegex()) {
                val processedRef = process(originalRef)
                append("[$processedRef")
                currentReferenceBlock = ""
            }
        }

        NONE -> Unit
    }
    return currentReferenceBlock
}
