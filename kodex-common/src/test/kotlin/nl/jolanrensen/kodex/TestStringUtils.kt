package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.replaceKdocLinks
import nl.jolanrensen.kodex.utils.indexOfFirstOrNullWhile
import nl.jolanrensen.kodex.utils.indexOfLastOrNullWhile
import nl.jolanrensen.kodex.utils.lastIndexOfNot
import nl.jolanrensen.kodex.utils.removeEscapeCharacters
import nl.jolanrensen.kodex.utils.replaceNonOverlappingRanges
import org.junit.jupiter.api.Test

class TestStringUtils {

    @Test
    fun `Remove escape chars`() {
        val content = """
            Simplistic JSON path implementation.
            Supports just keys (in bracket notation), double quotes, arrays, and wildcards.
            
            Examples:
            `\${'$'}["store"]["book"][*]["author"]`
            
            `\${'$'}[1]` will match `\${'$'}[*]`
        """.trimIndent()

        println(content.removeEscapeCharacters())
    }

    @Test
    fun `Last index of not`() {
        val string = "Hello World!"

        string.lastIndexOfNot('a') shouldBe 0
        string.lastIndexOfNot('H') shouldBe 1
        string.lastIndexOfNot('l') shouldBe 10
        string.lastIndexOfNot('l', 5) shouldBe 4
    }

    @Test
    fun `Replace ranges`() {
        val someText = """
            Hello World!
            This is some text.
            It has many lines.
            I hope you like it.
        """.trimIndent()

        val replacements = arrayOf(
            0..4 to "Hi",
            6..11 to "World",
            26..29 to "other text then before",
        )

        someText.replaceNonOverlappingRanges(*replacements) shouldBe
            """
                    Hi World
                    This is some other text then before.
                    It has many lines.
                    I hope you like it.
            """.trimIndent()
    }

    @Test
    fun `Replace all link regexes`() {
        val someText = """
            [H[ello] [World]!
            This is [text][text].
            It has [`many lines`] [NewPath] [Not processed] [Also\].
            I hope you ```[like][it]``` [right?]
               - `{@get [Fun]}(NaN|+Inf|-Inf)` is `NaN`
            `\[ `__`.`__[**`where`**][Update.where]**`  {  `**[`rowValueCondition`][SelectingRows.RowValueCondition.WithExample]**`  }  `**`]`
        """.trimIndent().asDocContent()

        val res = someText.replaceKdocLinks { "NewPath.$it" }

        res shouldBe """
            [H[ello][NewPath.ello] [World][NewPath.World]!
            This is [text][NewPath.text].
            It has [`many lines`][NewPath.`many lines`] [NewPath][NewPath.NewPath] [Not processed] [Also\].
            I hope you ```[like][NewPath.it]``` [right?]
               - `{@get [Fun][NewPath.Fun]}(NaN|+Inf|-Inf)` is `NaN`
            `\[ `__`.`__[**`where`**][NewPath.Update.where]**`  {  `**[`rowValueCondition`][NewPath.SelectingRows.RowValueCondition.WithExample]**`  }  `**`]`
        """.trimIndent().asDocContent()
    }

    @Test
    fun `Replace KDoc links difficult`() {
        val someText =
            """`MyType::myColumn`[`[`][ColumnsContainer.get]`MyOtherType::myOtherColumn`[`]`][`Columns Container`.get]"""
                .asDocContent()

        val res = someText.replaceKdocLinks { "$it.New Path" }

        res shouldBe
            """`MyType::myColumn`[`[`][ColumnsContainer.get.`New Path`]`MyOtherType::myOtherColumn`[`]`][`Columns Container`.get.`New Path`]"""
                .asDocContent()
    }

    @Test
    fun `Test index of first`() {
        val string = "Hello World!"

        string.indexOfFirstOrNullWhile('H') { true } shouldBe 0
        string.indexOfFirstOrNullWhile('H') { false } shouldBe 0
        string.indexOfFirstOrNullWhile('e') { false } shouldBe null
        string.indexOfFirstOrNullWhile('l') { true } shouldBe 2
        string.indexOfFirstOrNullWhile('l', 5) { true } shouldBe 9
        string.indexOfFirstOrNullWhile('i', 0) { true } shouldBe null
    }

    @Test
    fun `Test index of last`() {
        val string = "Hello World!"

        string.indexOfLastOrNullWhile('H') { true } shouldBe 0
        string.indexOfLastOrNullWhile('H') { false } shouldBe null
        string.indexOfLastOrNullWhile('l') { true } shouldBe 9
        string.indexOfLastOrNullWhile('l', 5) { true } shouldBe 3
        string.indexOfLastOrNullWhile('i', 0) { true } shouldBe null
    }
}
