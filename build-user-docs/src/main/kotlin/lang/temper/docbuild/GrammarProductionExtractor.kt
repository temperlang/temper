package lang.temper.docbuild

import lang.temper.astbuild.GrammarDiagrams
import lang.temper.astbuild.GrammarDoc
import lang.temper.be.js.Js
import lang.temper.be.js.JsIdentifierName
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.asciiLowerCase
import lang.temper.common.toStringViaBuilder
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.Position
import lang.temper.log.UnknownCodeLocation
import lang.temper.log.filePath
import lang.temper.log.plus

/**
 * Generates railroad diagrams for selected productions.
 */
internal object GrammarProductionExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (!(content is KotlinContent && from == grammarFilePath)) {
            return
        }

        val productions = mutableMapOf<String, GrammarProductionInfo>()
        fun storeProductionInfo(name: String, docToken: KotlinToken?, startOffset: Int) {
            val old = productions[name]
            if (old?.docString != null) { return } // No new info
            productions[name] = GrammarProductionInfo(
                name,
                docString = docToken?.text,
                startOffset = startOffset,
            )
        }

        // Our goal is to get position metadata and explanatory comments for each production.
        //
        // Within `object Productions { ... }` in Grammar.kt, we look for
        // `val ProductionName` at the start of a file.
        // And after that we look for the BNF-ish DSL, `ProductionName `：＝` GrammarCombinator`,
        // at the start of a line.
        //
        // Most productions we will see twice, and if there is a `/**...*/` style comment that
        // ends on the line preceding it, we associate that as the doc string which shows up
        // in reference/syntax.md.
        //
        // Later, we'll augment these of grammar definitions with lexical definitions like
        // Word and NumericLiteral from GrammarDiagrams.overriddenDefinitions.
        val tokenList = content.tokens
        var lastDocToken: KotlinToken? = null
        var inObjectProductionNames = false
        val tokensSameLine = mutableListOf<KotlinToken>()
        var lineStartOffset = 0
        var tokenStartOffset = 0
        for (token in tokenList) {
            val text = token.text
            tokenStartOffset += text.length
            val char0 = text[0]
            if (char0 <= ' ') { // space token
                if ('\n' in text) {
                    if (tokensSameLine.size >= 2) {
                        val (t0, t1) = tokensSameLine
                        if (inObjectProductionNames) {
                            if (t0.type == KotlinTokenType.`val` && t1.type == KotlinTokenType.IDENTIFIER) {
                                // If we see `val MyProduction` then store that info.
                                storeProductionInfo(t1.text, lastDocToken, lineStartOffset)
                            }
                        } else if (
                            t0.type == KotlinTokenType.IDENTIFIER &&
                            t1.type == KotlinTokenType.IDENTIFIER && t1.text == "`：＝`"
                        ) {
                            // If we see a production definition DSL like the below, then store the info.
                            //     MyProduction `：＝` ...
                            storeProductionInfo(t0.text, lastDocToken, lineStartOffset)
                        }
                        lastDocToken = null
                    }

                    tokensSameLine.clear()
                    lineStartOffset = tokenStartOffset
                }
                continue
            }
            if (token.type == KotlinTokenType.KDoc) {
                lastDocToken = token
            }
            if (inObjectProductionNames) {
                if (text == "}") {
                    inObjectProductionNames = false
                    lastDocToken = null
                }
            } else if (text == "ProductionNames" && tokensSameLine.lastOrNull()?.text == "object") {
                inObjectProductionNames = true
                lastDocToken = null
            }
            tokensSameLine.add(token)
        }

        GrammarDiagrams.overriddenProductionNames.forEach { productionName ->
            storeProductionInfo(productionName, null, 0)
        }

        val derivation = ExtractedBy(this)

        productions.values.forEach { (name, docString, startOffset) ->
            val shortTitle = "$name Syntax"

            val info = ProductionGrammarInfo(name, productions.keys.toSet())
            val altTextMarkdown =
                // `mkdocs` treats alt text as HTML, not MD
                MarkdownEscape.htmlCompatibleEscape(info.altText)

            // Generate a markdown snippet describing the production
            onto.add(
                Snippet(
                    id = SnippetId(listOf("syntax", name), extension = ".md"),
                    shortTitle = shortTitle,
                    source = from,
                    sourceStartOffset = startOffset,
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        toStringViaBuilder { sb ->
                            sb.append("## Syntax for *$name*\n")
                            sb.append('\n')
                            sb.append("![")
                            sb.append(altTextMarkdown)
                            sb.append("][snippet/syntax/")
                            sb.append(name)
                            sb.append(GrammarDiagrams.GRAMMAR_DIAGRAM_EXTENSION)
                            sb.append("]\n")
                            if (docString != null) {
                                sb.append('\n')
                                sb.append(commentContentToSnippetContent(docString).text)
                            }
                        },
                    ),
                    isIntermediate = false,
                    derivation = ExtractedBy(this),
                ),
            )
            // We need to generate some JS which uses the railroad-diagram JS library to
            // generate SVG
            val jsSnippet = Snippet(
                id = SnippetId(listOf("syntax", name), extension = ".js"),
                shortTitle = shortTitle,
                source = from,
                sourceStartOffset = startOffset,
                mimeType = MimeType.javascript,
                content = TextDocContent(info.jsToGenerateSvg),
                isIntermediate = true,
                derivation = derivation,
            )
            onto.add(
                jsSnippet,
            )
            // And actually generate the SVG.
            onto.add(
                Snippet(
                    id = SnippetId(listOf("syntax", name), extension = GrammarDiagrams.GRAMMAR_DIAGRAM_EXTENSION),
                    shortTitle = shortTitle,
                    source = from,
                    sourceStartOffset = startOffset,
                    mimeType = MimeType.svg,
                    content = ShellCommandDocContent(
                        "make-railroad",
                        listOf(jsSnippet.id.filePath.toString()),
                        groupTogether = true,
                    ),
                    isIntermediate = false,
                    derivation = derivation,
                ),
            )
        }
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> =
        if (priorInsertion != null) {
            RSuccess(priorInsertion)
        } else {
            RFailure(IllegalStateException(BACKPORT_ERROR_MESSAGE))
        }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        problemTracker.error(BACKPORT_ERROR_MESSAGE)
        return false
    }

    val grammarFilePath = filePath(
        "astbuild",
        "src",
        "commonMain",
        "kotlin",
        "lang",
        "temper",
        "astbuild",
        "Grammar.kt",
    )

    override val supportsBackPorting: Boolean = false
}

private data class GrammarProductionInfo(
    val name: String,
    val docString: String?,
    val startOffset: Int,
)

private class ProductionGrammarInfo(
    val productionName: String,
    allProductionNames: Set<String>,
) {
    val diagram = GrammarDiagrams.forProductionNamed(productionName)
    val altText: String get() = "$productionName := ${diagram.toBnf()}"
    val jsToGenerateSvg: String

    init {
        val absUrlPathToSyntaxFile =
            (MkdocsConfig.absPathToDocRoot + filePath("reference", "syntax"))
                .join("/") // URL separator, not FS separator
        val componentAst: Js.Expression = GrammarDiagramConverter {
            if (it.text in allProductionNames) {
                // From snippet/syntax/<ProdName>/snippet.svg to syntax/#
                // is ../../../syntax/#, but when we inline them we need a stable reference.
                "/$absUrlPathToSyntaxFile/#syntax-for-${it.text.asciiLowerCase()}"
            } else {
                null
            }
        }.convert(diagram)
        // BaseOutTree.toString() formats via token stream
        val componentJs = "$componentAst"
        val titleJs = Js.StringLiteral(
            componentAst.pos,
            toStringViaBuilder {
                it.append("<title>") // SVG title provides screen-reader text
                it.append(MarkdownEscape.htmlCompatibleEscape(altText))
                it.append("</title>")
            },
        )

        jsToGenerateSvg = """
        |const rr = require("@prantlf/railroad-diagrams");
        |const fs = require("fs");
        |
        |const component = $componentJs;
        |
        |const diagram = new rr.Diagram(component);
        |
        |let title = $titleJs;
        |
        |let scopedCssFile = require.resolve('@prantlf/railroad-diagrams/railroad-diagrams.css');
        |let scopedCss = fs.readFileSync(scopedCssFile, 'utf8');
        |let doNotInlineStyleTag = '\n<style><![CDATA[\n' + scopedCss + '\n]]></style>\n';
        |
        |let svg = diagram.format().toString();
        |let beforeEndOfFirstTag = svg.indexOf('>');
        |svg = svg.substring(0, beforeEndOfFirstTag)
        |    + ' role="img"'
        |    + ' xmlns="http://www.w3.org/2000/svg" version="1.1"'
        |    + ' xmlns:xlink="http://www.w3.org/1999/xlink">'
        |    + title
        |    + doNotInlineStyleTag
        |    + svg.substring(beforeEndOfFirstTag + 1);
        |
        |module.exports = svg;
        |
        """.trimMargin()
    }
}

private class GrammarDiagramConverter(
    private val nonTerminalToHref: (GrammarDoc.NonTerminal) -> String?,
) {
    private val p = Position(UnknownCodeLocation, 0, 0)

    private fun id(nameText: String) = Js.Identifier(p, JsIdentifierName(nameText), null)
    private fun str(stringText: String) = Js.StringLiteral(p, stringText)
    private fun int(i: Int) = Js.NumericLiteral(p, i)
    private fun strOrNull(stringText: String?) = stringText?.let { Js.StringLiteral(p, it) }
    private fun optionalBag(
        vararg pairs: Pair<String, Js.Expression?>,
    ): Js.ObjectExpression? {
        val props = pairs.mapNotNull { (k, v) ->
            if (v != null) { Js.ObjectProperty(p, str(k), v) } else { null }
        }
        return if (props.isEmpty()) {
            null
        } else {
            Js.ObjectExpression(p, props)
        }
    }
    private fun undefinedValue() =
        Js.UnaryExpression(p, Js.Operator(p, "void"), Js.NumericLiteral(p, 0))

    private val railroadDiagramImport = id("rr") // See import statement above

    private fun rrNewCall(
        rrMethodName: String,
        vararg actuals: Js.Actual?,
    ): Js.Expression {
        val arguments = mutableListOf<Js.Actual>()
        for ((i, actual) in actuals.withIndex()) {
            if (actual != null) {
                while (arguments.size < i) {
                    arguments.add(undefinedValue())
                }
                arguments.add(actual)
            }
        }
        return Js.NewExpression(
            p,
            Js.MemberExpression(
                p,
                railroadDiagramImport,
                id(rrMethodName),
            ),
            arguments = arguments,
        )
    }

    @Suppress("SpreadOperator") // Not performance critical
    fun convert(c: GrammarDoc.Component?): Js.Expression {
        return when (c) {
            null -> undefinedValue()
            is GrammarDoc.AlternatingSequence -> rrNewCall(
                "AlternatingSequence",
                convert(c.option1),
                convert(c.option2),
            )
            is GrammarDoc.Choice -> rrNewCall(
                "Choice",
                int(c.index),
                *convert(c.children),
            )
            is GrammarDoc.Group -> rrNewCall(
                "Group",
                convert(c.child),
                convert(c.label),
            )
            is GrammarDoc.HorizontalChoice -> rrNewCall(
                "HorizontalChoice",
                *convert(c.children),
            )
            is GrammarDoc.MultipleChoice -> rrNewCall(
                "MultipleChoice",
                int(c.index),
                str(
                    when (c.type) {
                        GrammarDoc.MultipleChoice.AnyOrAll.Any -> "any"
                        GrammarDoc.MultipleChoice.AnyOrAll.All -> "all"
                    },
                ),
                *convert(c.children),
            )
            is GrammarDoc.OneOrMore -> rrNewCall(
                "OneOrMore",
                convert(c.child),
                convert(c.repeat),
            )
            is GrammarDoc.Optional -> rrNewCall(
                "Optional",
                convert(c.child),
                convert(c.skip),
            )
            is GrammarDoc.OptionalSequence -> rrNewCall(
                "OptionalSequence",
                *convert(c.children),
            )
            is GrammarDoc.Sequence -> rrNewCall(
                "Sequence",
                *convert(c.children),
            )
            is GrammarDoc.Stack -> rrNewCall(
                "Stack",
                *convert(c.children),
            )
            is GrammarDoc.ZeroOrMore -> rrNewCall(
                "ZeroOrMore",
                convert(c.child),
                convert(c.repeat),
                convert(c.skip),
            )
            is GrammarDoc.Comment -> rrNewCall(
                "Comment",
                str(c.text),
                optionalBag(
                    "href" to strOrNull(c.href),
                    "title" to strOrNull(c.title),
                    "cls" to strOrNull(c.cls),
                ),
            )
            is GrammarDoc.NonTerminal -> rrNewCall(
                "NonTerminal",
                str(c.text),
                optionalBag(
                    "href" to strOrNull(c.href ?: nonTerminalToHref(c)),
                    "title" to strOrNull(c.title),
                    "cls" to strOrNull(c.cls),
                ),
            )
            GrammarDoc.Skip -> rrNewCall("Skip")
            is GrammarDoc.Terminal -> rrNewCall(
                "Terminal",
                str(c.text),
                optionalBag(
                    "href" to strOrNull(c.href),
                    "title" to strOrNull(c.title),
                    "cls" to strOrNull(c.cls),
                ),
            )
        }
    }

    private fun convert(components: Iterable<GrammarDoc.Component>) =
        components.map { convert(it) }.toTypedArray()

    private fun convert(skip: GrammarDoc.SkipOrNoSkip) = when (skip) {
        GrammarDoc.SkipOrNoSkip.NoSkip -> null
        GrammarDoc.SkipOrNoSkip.Skip -> str("skip")
    }
}

private const val BACKPORT_ERROR_MESSAGE =
    "Cannot back-port changes to the builtin environment.  ${
        ""
    }Maybe edit Grammar.kt, GrammarDiagrams.kt or move changes into nested snippets."
