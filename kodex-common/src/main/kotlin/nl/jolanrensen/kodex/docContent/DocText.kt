package nl.jolanrensen.kodex.docContent

/**
 * The entire comment, including the `*`-stuff and potential leading/trailing spaces.
 */
@JvmInline
value class DocText(val value: String) {

    init {
        require(value.isNotBlank() && value.trimStart().startsWith("/**") && value.trimEnd().endsWith("*/")) {
            "DocText must start with '/**' and end with '*/'. Got: \"$value\""
        }
    }

    override fun toString(): String = value
}

fun String.asDocText(): DocText = DocText(this)

fun String.asDocTextOrNull(): DocText? =
    if (isNotBlank() && trimStart().startsWith("/**") && trimEnd().endsWith("*/")) {
        DocText(this)
    } else {
        null
    }

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun DocText.getDocContent(): DocContent = getDocContentWithMap().first

/**
 * Returns the actual content of the KDoc/Javadoc comment
 *
 * [Pair.second] contains the mapping from the indices of the result to the indices
 * of the same character in the original string: `result\[key\] == [this]\[value\]`
 */
fun DocText.getDocContentWithMap(): Pair<DocContent, List<Int>> {
    // result[key] == this@getDocContentWithMapOrNull[value]
    val resultToOriginalMap = mutableMapOf<Int, Int>()

    val lines = value.split('\n').withIndex()

    var originalCharIndex = 0
    var resultCharIndex = 0
    val result = lines.joinToString("\n") { (lineIndex, it) ->
        var line = it

        // adds the number of removed characters in the result line to originalCharIndex
        fun String.alsoUpdateOriginalCharIndex(): String = also { originalCharIndex += line.length - length }

        // start of the comment
        if (lineIndex == 0) {
            line = line.trimStart().removePrefix("/**")
                .alsoUpdateOriginalCharIndex()
        }
        // end of the comment
        if (lineIndex == lines.count() - 1) {
            val lastLine = line.trimStart()

            line = if (lastLine == "*/") {
                ""
            } else {
                lastLine.removePrefix("*").alsoUpdateOriginalCharIndex()
                    .removeSuffix("*/")
                    .removeSuffix(" ") // optional extra space at the end
            }
        }
        // middle of the comment (not start nor end)
        if (lineIndex != 0 && lineIndex != lines.count() - 1) {
            line = line.trimStart().removePrefix("*")
                .alsoUpdateOriginalCharIndex()
        }

        // remove optional extra space at the start
        line = line.removePrefix(" ")
            .alsoUpdateOriginalCharIndex()

        // update the map for all characters now in the result line
        for (j in line.indices) {
            resultToOriginalMap[resultCharIndex + j] = originalCharIndex + lineIndex + j
        }
        // ..and the \n character
        resultToOriginalMap[resultCharIndex + line.length] = originalCharIndex + lineIndex + line.length

        // update the two indices for the next iteration
        resultCharIndex += line.length + 1
        originalCharIndex += line.length

        line
    }

    // remove the final \n character
    resultToOriginalMap.remove(resultToOriginalMap.size - 1)

    return result.asDocContent() to resultToOriginalMap.values.toList()
}

/**
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun DocContent.toDocText(indent: Int = 0): DocText =
    this.value
        .split('\n')
        .toMutableList()
        .let {
            it[0] = if (it[0].isEmpty()) "/**" else "/** ${it[0]}"

            val lastIsBlank = it.last().isBlank()

            it[it.lastIndex] = it[it.lastIndex].trim() + " */"

            it.mapIndexed { index, s ->
                buildString {
                    if (index != 0) append("\n")
                    append(" ".repeat(indent))

                    if (!(index == 0 || index == it.lastIndex && lastIsBlank)) {
                        append(" *")
                        if (s.isNotEmpty()) append(" ")
                    }
                    append(s)
                }
            }.joinToString("")
        }.asDocText()
