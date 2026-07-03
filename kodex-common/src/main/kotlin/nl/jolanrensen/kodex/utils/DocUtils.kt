package nl.jolanrensen.kodex.utils

// used to keep track of the current blocks
internal const val CURLY_BRACES = '{'
internal const val SQUARE_BRACKETS = '['
internal const val PARENTHESES = '('
internal const val ANGULAR_BRACKETS = '<'
internal const val BACKTICKS = '`'
internal const val DOUBLE_QUOTES = '"'
internal const val SINGLE_QUOTES = '\''

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArguments(tag: String, numberOfArguments: Int): List<String> =
    getTagArgumentsWithRanges(tag, numberOfArguments).map { it.first }

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArgumentsWithRanges(tag: String, numberOfArguments: Int): List<Pair<String, IntRange>> {
    require("@$tag" in this) { "Could not find @$tag in $this" }
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var i = 0
    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
        i++
    }

    val prevContentLength = content.length

    // remove leading spaces
    content = content.trimStart()

    // remove tag
    content = content.removePrefix("@$tag").trimStart()

    i += prevContentLength - content.length

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isWhitespace() && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotBlank()) {
                        this += Pair(
                            first = currentBlock,
                            second = i..<(i + currentBlock.length),
                        )
                        i += currentBlock.length
                    }
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) {
                when (char) {
                    '{' -> blocksIndicators += CURLY_BRACES

                    '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)

                    '[' -> blocksIndicators += SQUARE_BRACKETS

                    ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)

                    '(' -> blocksIndicators += PARENTHESES

                    ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)

                    '<' -> blocksIndicators += ANGULAR_BRACKETS

                    '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)

                    '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) {
                        blocksIndicators += DOUBLE_QUOTES
                    }

                    '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) {
                        blocksIndicators += SINGLE_QUOTES
                    }

                    // TODO: issue #11: html tags
                }
            }
            if (isDone() || !(currentBlock.isEmpty() && char.isWhitespace())) {
                currentBlock += char
            }
        }

        this += Pair(
            first = currentBlock,
            second = i..<(i + currentBlock.length),
        )
    }

    val trimmedArguments = arguments.mapIndexed { i, (ogContent, ogRange) ->
        when (i) {
            // last argument will be kept as is, removing one "splitting" space if it starts with one
            arguments.lastIndex ->
                if (ogContent.startsWith(" ") || ogContent.startsWith("\t")) {
                    Pair(ogContent.drop(1), ogRange.first + 1..ogRange.last)
                } else {
                    Pair(ogContent, ogRange)
                }

            else -> { // other arguments will be trimmed at the start only. A newline counts as a space
                val trimmed = ogContent.removePrefix("\n").trimStart(' ', '\t')
                Pair(trimmed, ogRange.first + (ogContent.length - trimmed.length)..ogRange.last)
            }
        }
    }

    return trimmedArguments
}

fun String.getTagArgumentWithRangeByIndexOrNull(
    index: Int,
    tag: String,
    numberOfArguments: Int,
): Pair<String, IntRange>? = getTagArgumentsWithRanges(tag, numberOfArguments).getOrNull(index)

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 *
 * @param tag The tag name, without the "@", will be removed after removing optional surrounding {}'s
 * @param numberOfArguments The number of arguments to retrieve. This will be the size of the @return list.
 *   The last argument will contain all remaining content, no matter if it can be split or not.
 * @param onRogueClosingChar Optional lambda that will be called when a '}', ']', ')', or '>' is found without respective
 *   opening char. Won't be triggered if '\' escaped.
 * @param isSplitter Defaults to `{ isWhitespace() }`. Can be used to change the splitting behavior.
 */
fun String.getTagArguments(
    tag: String,
    numberOfArguments: Int,
    onRogueClosingChar: (closingChar: Char, argument: Int, indexInArg: Int) -> Unit,
    isSplitter: Char.() -> Boolean,
): List<String> {
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
    }

    // remove leading spaces
    content = content.trimStart { it.isSplitter() }

    // remove tag
    content = content.removePrefix("@").removePrefix(tag).trimStart { it.isSplitter() }

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> {
                    escapeNext = false
                    continue
                }

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isSplitter() && blocksIndicators.isEmpty() -> {
                    if (!currentBlock.all { it.isSplitter() }) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) {
                when (char) {
                    '{' -> blocksIndicators += CURLY_BRACES

                    '}' ->
                        blocksIndicators
                            .removeAllElementsFromLast(CURLY_BRACES)
                            .let { if (!it) onRogueClosingChar('}', this.size, currentBlock.length) }

                    '[' -> blocksIndicators += SQUARE_BRACKETS

                    ']' ->
                        blocksIndicators
                            .removeAllElementsFromLast(SQUARE_BRACKETS)
                            .let { if (!it) onRogueClosingChar(']', this.size, currentBlock.length) }

                    '(' -> blocksIndicators += PARENTHESES

                    ')' ->
                        blocksIndicators
                            .removeAllElementsFromLast(PARENTHESES)
                            .let { if (!it) onRogueClosingChar(')', this.size, currentBlock.length) }

                    '<' -> blocksIndicators += ANGULAR_BRACKETS

                    '>' ->
                        blocksIndicators
                            .removeAllElementsFromLast(ANGULAR_BRACKETS)
                            .let { if (!it) onRogueClosingChar('>', this.size, currentBlock.length) }

                    '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) {
                        blocksIndicators += DOUBLE_QUOTES
                    }

                    '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) {
                        blocksIndicators += SINGLE_QUOTES
                    }

                    // TODO: issue #11: html tags
                }
            }
            if (isDone() || !currentBlock.all { it.isSplitter() } || !char.isSplitter()) {
                currentBlock += char
            }
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            // last argument will be kept as is, removing one "splitting" space if it starts with one
            arguments.lastIndex ->
                if (it.first().isSplitter() && it.firstOrNull() != '\n') {
                    it.drop(1)
                } else {
                    it
                }

            else -> // other arguments will be trimmed. A newline counts as a space
                it.removePrefix("\n").trimStart { it.isSplitter() }
        }
    }

    return trimmedArguments
}

/**
 * Decodes something like `[Alias][Foo]` to `Foo`
 * But also `{@link Foo#main(String[])}` to `Foo.main`
 */
fun String.decodeCallableTarget(): String =
    trim()
        .removePrefix("[")
        .removeSuffix("]")
        .let {
            // for aliased tags like [Foo][Bar]
            if ("][" in it) {
                it.substringAfter("][")
            } else {
                it
            }
        }.trim()
        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")
        .trim()
        // for javaDoc, attempt to be able to read
        // @include {@link Main#main(String[])} as "Main.main"
        .removePrefix("{") // alternatively for javaDoc
        .removeSuffix("}")
        .removePrefix("@link")
        .trim()
        .replace('#', '.')
        .replace(Regex("""\(.*\)"""), "")
        .trim()

/**
 * Get tag name from the start of some content.
 * Can handle both
 * `  @someTag someContent`
 * and
 * `{@someTag someContent}`
 * and will return "someTag" in these cases.
 */
fun String.getTagNameOrNull(): String? =
    takeIf { it.trimStart().startsWith('@') || it.startsWith("{@") }
        ?.trimStart()
        ?.removePrefix("{")
        ?.removePrefix("@")
        ?.takeWhile { !it.isWhitespace() && it != '{' && it != '}' }

/**
 * Finds and removes the last occurrence of [element] from the list and, if found, all elements after it.
 * Returns true if [element] was found and removed, false otherwise.
 */
fun <T> MutableList<T>.removeAllElementsFromLast(element: T): Boolean {
    val index = lastIndexOf(element)
    if (index == -1) return false
    val indicesToRemove = index..lastIndex
    for (i in indicesToRemove.reversed()) {
        removeAt(i)
    }
    return true
}

fun IntRange.coerceIn(start: Int = Int.MIN_VALUE, endInclusive: Int = Int.MAX_VALUE) =
    first.coerceAtLeast(start)..last.coerceAtMost(endInclusive)

/**
 * Coerces the start and end of the range to be at most [endInclusive].
 */
fun IntRange.coerceAtMost(endInclusive: Int) = first.coerceAtMost(endInclusive)..last.coerceAtMost(endInclusive)

/**
 * Maps the given range with [mapping] to one or multiple ranges.
 */
fun IntRange.mapToRanges(mapping: (Int) -> Int): List<IntRange> {
    if (isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    val iterator = iterator()
    var start = mapping(iterator.next())
    var end = start

    iterator.forEach {
        val mappedNum = mapping(it)
        if (mappedNum != end + 1) {
            ranges.add(start..end)
            start = mappedNum
        }
        end = mappedNum
    }

    ranges.add(start..end) // Add the last range
    return ranges.filterNot { it.isEmpty() }
}

/**
 * Maps [this] range to one or multiple ranges by removing all numbers that return `true` for [remove].
 */
fun IntRange.remove(remove: (Int) -> Boolean): List<IntRange> {
    if (isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    val iterator = iterator()
    var start = iterator.next()
    while (remove(start)) {
        if (!iterator.hasNext()) return emptyList()
        start = iterator.next()
    }
    var end = start

    for (it in iterator) {
        if (remove(it)) {
            ranges.add(start..end)
            start = it + 1
        }
        end = it
    }

    if (!remove(end)) ranges.add(start..end)
    return ranges.filterNot { it.isEmpty() }
}

fun IntRange.remove(vararg ints: Int): List<IntRange> = remove { it in ints }
