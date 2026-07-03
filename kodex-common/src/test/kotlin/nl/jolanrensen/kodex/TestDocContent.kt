package nl.jolanrensen.kodex

import io.kotest.matchers.shouldBe
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.asDocText
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContent
import nl.jolanrensen.kodex.docContent.getDocContentWithMap
import nl.jolanrensen.kodex.docContent.toDocText
import org.junit.jupiter.api.Test

class TestDocContent {

    @Test
    fun `Simple doc`() {
        val kdoc = """
            /**
             *       Hello World!
             *
             * @see [com.example.plugin.KdocIncludePlugin]
             */
        """.trimIndent().asDocText()

        val expected = "\n      Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]\n"
            .asDocContent()

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Simple doc no surrounding newlines 1`() {
        val kdoc = """
            /** Hello World!
             *
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent().asDocText()

        val expected = "Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Simple doc no surrounding newlines 2`() {
        val kdoc = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent().asDocText()

        val expected = "Hello World!\n@see [com.example.plugin.KdocIncludePlugin]"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Single line doc`() {
        val kdoc = """
            /** Hello World! */
        """.trimIndent().asDocText()

        val expected = "Hello World!"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Single line doc with newlines`() {
        val kdoc = """
            /**
             * Hello World!
             */
        """.trimIndent().asDocText()

        val expected = "\nHello World!\n"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Wrongly formatted doc`() {
        val kdoc = """
            /**
             *Hello World!
             */
        """.trimIndent().asDocText()

        val expected = "\nHello World!\n"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }

        val kdoca = """
            /**
             * Hello World!
             */
        """.trimIndent().asDocText()

        kdoc.getDocContent().toDocText() shouldBe kdoca

        kdoca.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoca.value[y]
        }
    }

    @Test
    fun `Doc inside doc`() {
        val kdoc = """
            /**
             * Hello World! /** Some doc inside the doc */
             */
        """.trimIndent().asDocText()

        val expected = "\nHello World! /** Some doc inside the doc */\n"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Wrong doc`() {
        val kdoc = """
            
            /**
             * Wrong kdoc
             */
             fun hello() {}
             
        """.trimIndent().asDocTextOrNull()

        kdoc shouldBe null
    }

    @Test
    fun `Empty doc`() {
        val kdoc = """
            /** */
        """.trimIndent().asDocText()

        val expected = ""
            .asDocContent()
        kdoc.getDocContent() shouldBe expected
        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }

    @Test
    fun `Empty doc newlines`() {
        val kdoc = """
            /**
             *
             */
        """.trimIndent().asDocText()

        val expected = "\n\n"
            .asDocContent()
        kdoc.getDocContent() shouldBe expected
        kdoc.getDocContent().toDocText() shouldBe kdoc

        kdoc.getDocContentWithMap().second.forEachIndexed { x, y ->
            expected.value[x] shouldBe kdoc.value[y]
        }
    }
}
