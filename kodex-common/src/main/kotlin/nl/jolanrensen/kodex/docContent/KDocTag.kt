package nl.jolanrensen.kodex.docContent

/**
 * Represents a KDoc tag.
 *
 * @property tagName The name of the tag (e.g., "param", "return", "throws").
 * @property subject The subject of the tag, if any (e.g., the parameter name for a @param tag).
 * @property description The description or content of the tag.
 */
data class KDocTag(
    val tagName: String,
    val subject: String? = null,
    val description: DocContent,
)
