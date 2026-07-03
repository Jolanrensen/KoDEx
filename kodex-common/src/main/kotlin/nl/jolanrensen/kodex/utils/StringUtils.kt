package nl.jolanrensen.kodex.utils

/**
 * Last index of not [char] moving from startIndex down to 0.
 * Returns 0 if char is not found (since last index looked at is 0).
 * Returns `this.size` if [char] is found at [startIndex].
 *
 * @receiver
 * @param char Char
 * @param startIndex Start index
 * @return
 */
fun String.lastIndexOfNot(char: Char, startIndex: Int = lastIndex): Int {
    for (i in startIndex downTo 0) {
        if (this[i] == char) return i + 1
    }
    return 0
}

/**
 * Removes "\" from the String, but only if it is not escaped.
 */
fun String.removeEscapeCharacters(escapeChars: List<Char> = listOf('\\')): String =
    buildString {
        var escapeNext = false
        for (char in this@removeEscapeCharacters) {
            if (escapeNext) {
                escapeNext = false
            } else if (char in escapeChars) {
                escapeNext = true
                continue
            }
            append(char)
        }
    }

fun String.getIndicesOfEscapeChars(escapeChars: List<Char> = listOf('\\')): List<Int> =
    buildList {
        var escapeNext = false
        for ((i, char) in this@getIndicesOfEscapeChars.withIndex()) {
            if (escapeNext) {
                escapeNext = false
            } else if (char in escapeChars) {
                escapeNext = true
                this += i
                continue
            }
        }
    }

/**
 * Replaces multiple ranges with their respective replacements.
 * The replacements can be of any size but cannot overlap. Ranges
 * are relative to the source, [this], the merging is done by the function.
 *
 * Note: a range like `i .. i - 1` will not replace anything, but it will insert something
 * new at index `i`.
 */
fun String.replaceNonOverlappingRanges(vararg rangeToReplacement: Pair<IntRange, String>): String {
    val textRange = this.indices.associateWith { this[it].toString() }.toMutableMap()
    for ((range, replacement) in rangeToReplacement) {
        if (range.size == 0 && replacement.isEmpty()) continue

        if (range.isEmpty()) {
            textRange[range.last] += replacement
        } else {
            range.forEach(textRange::remove)
            textRange[range.first] = replacement
        }
    }
    return textRange.toSortedMap().values.joinToString("")
}

/**
 * Replace all matches of [regex], even if they overlap a part of their match.
 * Matches are temporarily replaced with [intermediateReplacementChar] (so that we don't get an infinite loop dependent
 * on the result of [transform]) before being actually replaced with the result of [transform].
 */
@Deprecated("No use for it anymore?")
fun CharSequence.replaceAll(
    regex: Regex,
    limit: Int = 10_000,
    // must not match the regex
    intermediateReplacementChar: Char = ' ',
    transform: (MatchResult) -> CharSequence,
): String {
    var text = this.toString()

    var i = 0
    val replacements = mutableMapOf<IntRange, String>()
    while (regex in text) {
        text = text.replace(regex) {
            val range = it.range
            replacements[range] = transform(it).toString()

            intermediateReplacementChar
                .toString()
                .repeat(range.count())
                .also { require(regex !in it) { "intermediateReplacementChar must not match the regex" } }
        }

        if (i++ > limit) {
            println("WARNING: replaceWhilePresent limit reached for $regex in $this")
            break
        }
    }

    return text.replaceNonOverlappingRanges(*replacements.map { it.key to it.value }.toTypedArray())
}

fun String.indexOfFirstOrNullWhile(char: Char, startIndex: Int = 0, whileCondition: (Char) -> Boolean): Int? {
    try {
        for (i in startIndex until length) {
            if (this[i] == char) return i
            if (!whileCondition(this[i])) break
        }
        return null
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
}

fun String.indexOfLastOrNullWhile(char: Char, startIndex: Int = lastIndex, whileCondition: (Char) -> Boolean): Int? {
    try {
        for (i in startIndex downTo 0) {
            if (this[i] == char) return i
            if (!whileCondition(this[i])) break
        }
        return null
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
}

val IntRange.size get() = last - first + 1

fun String.getLineAndCharacterOffset(offset: Int): Pair<Int, Int> {
    var line = 1
    var character = 1
    for (i in 0 until offset) {
        if (this[i] == '\n') {
            line++
            character = 1
        } else {
            character++
        }
    }

    return Pair(line, character)
}

fun String.surroundWith(start: String, end: String = start): String = "$start$this$end"
