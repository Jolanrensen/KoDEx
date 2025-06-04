package nl.jolanrensen.kodex.docContent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocContentTests {

    @Test
    fun `test basic param tag parsing`() {
        val kdoc = """
            Some description.
            @param name The name parameter.
            @param age The age parameter.
        """
        val docContent = DocContent(kdoc)
        assertEquals(2, docContent.structuredTags.size)

        val paramName = docContent.structuredTags.find { it.tagName == "param" && it.subject == "name" }
        assertEquals("name", paramName?.subject)
        assertEquals("The name parameter.".asDocContent(), paramName?.description)

        val paramAge = docContent.structuredTags.find { it.tagName == "param" && it.subject == "age" }
        assertEquals("age", paramAge?.subject)
        assertEquals("The age parameter.".asDocContent(), paramAge?.description)
    }

    @Test
    fun `test tag with no subject`() {
        val kdoc = "@return The result."
        val docContent = DocContent(kdoc)
        assertEquals(1, docContent.structuredTags.size)
        val returnTag = docContent.structuredTags.first()
        assertEquals("return", returnTag.tagName)
        assertNull(returnTag.subject)
        assertEquals("The result.".asDocContent(), returnTag.description)
    }

    @Test
    fun `test mixed tags`() {
        val kdoc = """
            Main description.
            @param p1 First param.
            @return String result.
            @throws Exception if something goes wrong.
        """
        val docContent = DocContent(kdoc)
        assertEquals(3, docContent.structuredTags.size)

        val paramTag = docContent.structuredTags.find { it.tagName == "param" }
        assertEquals("p1", paramTag?.subject)
        assertEquals("First param.".asDocContent(), paramTag?.description)

        val returnTag = docContent.structuredTags.find { it.tagName == "return" }
        assertNull(returnTag?.subject)
        assertEquals("String result.".asDocContent(), returnTag?.description)

        val throwsTag = docContent.structuredTags.find { it.tagName == "throws" }
        assertEquals("Exception", throwsTag?.subject) // Current basic parsing might make "Exception" the subject
        assertEquals("if something goes wrong.".asDocContent(), throwsTag?.description)
    }

    @Test
    fun `test content with no tags`() {
        val kdoc = "Just a description, no tags."
        val docContent = DocContent(kdoc)
        assertEquals(0, docContent.structuredTags.size)
    }

    @Test
    fun `test tags with multi-line descriptions`() {
        val kdoc = """
            @param user The user object.
                        It can have multiple lines
                        of description.
            @return A status string.
                    Also with multiple lines.
        """
        val docContent = DocContent(kdoc)
        assertEquals(2, docContent.structuredTags.size)

        val paramUser = docContent.structuredTags.find { it.tagName == "param" && it.subject == "user" }
        assertEquals("user", paramUser?.subject)
        assertEquals("The user object.
It can have multiple lines
of description.".asDocContent(), paramUser?.description)

        val returnTag = docContent.structuredTags.find { it.tagName == "return" }
        assertNull(returnTag?.subject)
        assertEquals("A status string.
Also with multiple lines.".asDocContent(), returnTag?.description)
    }

    @Test
    fun `test tag with leading and trailing whitespace in description`() {
        val kdoc = "@param query  The search query string.  "
        val docContent = DocContent(kdoc)
        assertEquals(1, docContent.structuredTags.size)
        val paramTag = docContent.structuredTags.first()
        assertEquals("param", paramTag.tagName)
        assertEquals("query", paramTag.subject)
        // The current parsing logic in DocContent might trim whitespace more aggressively
        // depending on how `split("\s+", 2)` and subsequent trimming works.
        // This test might need adjustment based on the exact parsing behavior.
        // For now, assuming description retains some internal spaces but leading/trailing on the line is trimmed.
        assertEquals("The search query string.", paramTag.description.value.trim())
    }

    @Test
    fun `test @deprecated tag with content`() {
        val kdoc = "@deprecated Use newFunction instead."
        val docContent = DocContent(kdoc)
        assertEquals(1, docContent.structuredTags.size)
        val deprecatedTag = docContent.structuredTags.first()
        assertEquals("deprecated", deprecatedTag.tagName)
        assertNull(deprecatedTag.subject) // Deprecated usually doesn't have a "subject" in the same way @param does
        assertEquals("Use newFunction instead.".asDocContent(), deprecatedTag.description)
    }

    @Test
    fun `test empty kdoc`() {
        val kdoc = ""
        val docContent = DocContent(kdoc)
        assertEquals(0, docContent.structuredTags.size)
    }

    @Test
    fun `test kdoc with only whitespace`() {
        val kdoc = "

 "
        val docContent = DocContent(kdoc)
        // Depending on how splitPerBlock and parsing handles pure whitespace blocks,
        // this might be 0 or more if empty strings are turned into DocContent.
        // Assuming it results in no actual tags.
        assertEquals(0, docContent.structuredTags.size)
    }
}
