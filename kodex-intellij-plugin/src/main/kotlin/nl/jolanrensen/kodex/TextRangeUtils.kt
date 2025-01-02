package nl.jolanrensen.kodex

import com.intellij.openapi.util.TextRange

fun TextRange.toIntRange(): IntRange = startOffset until endOffset

fun IntRange.toTextRange(): TextRange = TextRange(start, endInclusive + 1)