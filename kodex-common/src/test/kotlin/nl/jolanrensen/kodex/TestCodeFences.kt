package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.defaultProcessors.addCodeSpansToAliases
import nl.jolanrensen.kodex.docContent.asDocContent
import org.junit.jupiter.api.Test

class TestCodeFences {

    private fun check(input: String, expected: String) =
        input.trimIndent().asDocContent().addCodeSpansToAliases() shouldBe expected.trimIndent().asDocContent()

    @Test
    fun `shorter run does not close longer backtick fence`() =
        check(
            """
            ````
            [a]
            ```
            [b]
            ````
            [c]
            """,
            """
            ````
            [a]
            ```
            [b]
            ````
            [<code>c</code>][c]
            """,
        )

    @Test
    fun `longer run closes tilde fence, backticks do not`() =
        check(
            """
            ~~~
            [a]
            ```
            ~~~~
            [b]
            """,
            """
            ~~~
            [a]
            ```
            ~~~~
            [<code>b</code>][b]
            """,
        )

    @Test
    fun `backtick in info string is no fence`() =
        check(
            """
            ```a`b
            [a]
            """,
            """
            ```a`b
            [<code>a</code>][a]
            """,
        )

    @Test
    fun `tilde fence may have backtick in info string`() =
        check(
            """
            ~~~a`b
            [a]
            ~~~
            [b]
            """,
            """
            ~~~a`b
            [a]
            ~~~
            [<code>b</code>][b]
            """,
        )

    @Test
    fun `closing fence may not be followed by text`() =
        check(
            """
            ```
            [a]
            ``` foo
            [b]
            """,
            """
            ```
            [a]
            ``` foo
            [b]
            """,
        )

    @Test
    fun `unclosed fence runs to the end`() =
        check(
            """
            [a]
            ```
            [b]
            """,
            """
            [<code>a</code>][a]
            ```
            [b]
            """,
        )

    @Test
    fun `fence may be indented up to three spaces`() =
        check(
            """
            ${"   "}```
            [a]
            ${"   "}```
            [b]
            """,
            """
            ${"   "}```
            [a]
            ${"   "}```
            [<code>b</code>][b]
            """,
        )

    @Test
    fun `two spaces of indent and less than three chars is no fence`() =
        check(
            """
              ``
            [a]
            """,
            """
              ``
            [<code>a</code>][a]
            """,
        )
}
