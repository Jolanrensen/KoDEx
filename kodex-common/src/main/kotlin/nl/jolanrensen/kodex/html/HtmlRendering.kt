package nl.jolanrensen.kodex.html

import nl.jolanrensen.kodex.docContent.DocContent
import org.intellij.lang.annotations.Language
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

fun DocContent.renderToHtml(theme: Boolean, stripReferences: Boolean, includeResizeScript: Boolean): String {
    // TODO https://github.com/JetBrains/markdown
    val flavour = GFMFlavourDescriptor(
        useSafeLinks = false,
        absolutizeAnchorLinks = false,
        makeHttpsAutoLinks = false,
    )
    val md = MarkdownParser(flavour).buildMarkdownTreeFromString(this.value)
    val linkMap = LinkMap.buildLinkMap(md, this.value)
    val providers = flavour.createHtmlGeneratingProviders(
        linkMap = linkMap,
        baseURI = null,
    ).toMutableMap()

    if (stripReferences) {
        val refLinkGenerator = MyReferenceLinksGeneratingProvider()
        providers[MarkdownElementTypes.FULL_REFERENCE_LINK] = refLinkGenerator
        providers[MarkdownElementTypes.SHORT_REFERENCE_LINK] = refLinkGenerator
    }

    // TODO https://github.com/JetBrains/markdown/pull/150
    val codeSpanGenerator = TableAwareCodeSpanGeneratingProvider()
    providers[MarkdownElementTypes.CODE_SPAN] = codeSpanGenerator

    val body = HtmlGenerator(
        markdownText = this.value,
        root = md,
        providers = providers,
    ).generateHtml().let {
        when {
            it.startsWith("<p>") -> it.replaceFirst("<p>", "<p style='margin-top:0;padding-top:0;'>")
            else -> it
        }
    }

    return buildString {
        appendLine("<html>")
        if (theme) {
            appendLine("<head>")
            appendLine("<style type=\"text/css\">")
            @Language("css")
            val a = appendLine(
                """
                :root {
                    --background: #fff;
                    --background-odd: #f5f5f5;
                    --background-hover: #d9edfd;
                    --header-text-color: #474747;
                    --text-color: #848484;
                    --text-color-dark: #000;
                    --text-color-medium: #737373;
                    --text-color-pale: #b3b3b3;
                    --inner-border-color: #aaa;
                    --bold-border-color: #000;
                    --link-color: #296eaa;
                    --link-color-pale: #296eaa;
                    --link-hover: #1a466c;
                }
                :root[theme="dark"], :root [data-jp-theme-light="false"] {
                    --background: #303030;
                    --background-odd: #3c3c3c;
                    --background-hover: #464646;
                    --header-text-color: #dddddd;
                    --text-color: #b3b3b3;
                    --text-color-dark: #dddddd;
                    --text-color-medium: #b2b2b2;
                    --text-color-pale: #737373;
                    --inner-border-color: #707070;
                    --bold-border-color: #777777;
                    --link-color: #008dc0;
                    --link-color-pale: #97e1fb;
                    --link-hover: #00688e;
                }
                body {
                    font-family: "JetBrains Mono",SFMono-Regular,Consolas,"Liberation Mono",Menlo,Courier,monospace;
                }
                :root {
                    color: #19191C;
                    background-color: #fff;
                }
                :root[theme="dark"] {
                    background-color: #19191C;
                    color: #FFFFFFCC
                }
                """.trimIndent(),
            )
            appendLine("</style>")
            if (includeResizeScript) {
                @Language("html")
                val b = appendLine(
                    """
                        <script>
                            function sendHeight() {
                                const body = document.body;
                                const html = document.documentElement;

                                const height = Math.max(
                                    body.scrollHeight,
                                    body.offsetHeight,
                                    html.clientHeight,
                                    html.scrollHeight,
                                    html.offsetHeight
                                );

                                parent.postMessage({ type: 'iframeHeight', height }, '*');
                            }


                            function repeatHeightCalculation(maxRetries = 10, interval = 100) {
                                let retries = 0;
                                const intervalId = setInterval(() => {
                                    sendHeight();
                                    retries++;
                                    if (retries >= maxRetries) clearInterval(intervalId);
                                }, interval);
                            }

                            window.addEventListener('load', () => {
                                repeatHeightCalculation();
                            });


                            const observer = new MutationObserver(() => repeatHeightCalculation(5, 50));
                            observer.observe(document.body, {
                                childList: true,
                                subtree: true,
                                characterData: true,
                                attributes: true
                            });

                            window.addEventListener('resize', sendHeight);
                        </script>
                    """.trimIndent()
                )
            }
            appendLine("</head>")
        }
        appendLine(body)
        appendLine("<html/>")
    }
}

class MyReferenceLinksGeneratingProvider : GeneratingProvider {

    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        // reference
        val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            ?: return

        // optional alias
        val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }

        val nodeToUse = linkTextNode ?: label

        visitor.consumeTagOpen(node, "code")
        TransparentInlineHolderProvider(1, -1).processNode(visitor, text, nodeToUse)
        visitor.consumeTagClose("code")
    }
}

/**
 * Special version of [org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor.CodeSpanGeneratingProvider],
 * that will correctly escape table pipes if the code span is inside a table cell.
 */
open class TableAwareCodeSpanGeneratingProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val isInsideTable = isInsideTable(node)
        val nodes = collectContentNodes(node)
        val output = nodes.withIndex().joinToString(separator = "") { (i, it) ->
            if (i == nodes.lastIndex && it.type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
                // Backslash escapes do not work in code spans.
                // Yet, a code span like `this\` is recognized as "BACKTICK", "TEXT", "ESCAPED_BACKTICKS"
                // So if the last node is ESCAPED_BACKTICKS, we need to manually render it as "\"
                return@joinToString "\\"
            }

            processChild(it, text, isInsideTable).replaceNewLines()
        }.trimForCodeSpan()
        visitor.consumeTagOpen(node, "code")
        visitor.consumeHtml(output)
        visitor.consumeTagClose("code")
    }

    /** From GFM spec: First, line endings are converted to spaces.*/
    protected fun CharSequence.replaceNewLines(): CharSequence = replace("\\r\\n?|\\n".toRegex(), " ")

    /**
     * From GFM spec:
     * If the resulting string both begins and ends with a space character,
     * but does not consist entirely of space characters,
     * a single space character is removed from the front and back.
     * This allows you to include code that begins or ends with backtick characters,
     * which must be separated by whitespace from the opening or closing backtick strings.
     */
    protected fun CharSequence.trimForCodeSpan(): CharSequence =
        if (isBlank()) {
            this
        } else {
            removeSurrounding(" ", " ")
        }

    protected fun isInsideTable(node: ASTNode): Boolean = node.getParentOfType(GFMTokenTypes.CELL) != null

    protected fun collectContentNodes(node: ASTNode): List<ASTNode> {
        check(node.children.size >= 2)

        // Backslash escapes do not work in code spans.
        // Yet, a code span like `this\` is recognized as "BACKTICK", "TEXT", "ESCAPED_BACKTICKS"
        // Let's keep the last ESCAPED_BACKTICKS and manually render it as "\"
        if (node.children.last().type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
            return node.children.drop(1)
        }

        return node.children.subList(1, node.children.size - 1)
    }

    protected fun processChild(node: ASTNode, text: String, isInsideTable: Boolean): CharSequence {
        if (!isInsideTable) {
            return HtmlGenerator.leafText(text, node, replaceEscapesAndEntities = false)
        }
        val nodeText = node.getTextInNode(text).toString()
        val escaped = nodeText.replace("\\|", "|")
        return EntityConverter.replaceEntities(escaped, processEntities = false, processEscapes = false)
    }
}
