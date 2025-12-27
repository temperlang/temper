package lang.temper.docbuild

import lang.temper.astbuild.GrammarDiagrams
import lang.temper.common.MimeType
import lang.temper.fs.read
import lang.temper.fs.resolve
import lang.temper.fs.temperRoot
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarProductionExtractorTest {
    @Test
    fun documentedJsonBoolean() {
        val snippets = mutableListOf<Snippet>()
        GrammarProductionExtractor.extractSnippets(
            GrammarProductionExtractor.grammarFilePath,
            KotlinContent(
                temperRoot.resolve(GrammarProductionExtractor.grammarFilePath).read(),
            ),
            MimeType.kotlinSource,
            snippets,
        )
        val jsonBooleanSnippets = snippets.filter { "JsonBoolean" in it.id.parts }
        val jsonBooleanContentMap = jsonBooleanSnippets.associate {
            it.id.extension to it.content
        }
        assertEquals(
            mapOf(
                ".md" to TextDocContent(
                    """
                    |## Syntax for *JsonBoolean*
                    |
                    |![JsonBoolean &#58;&#61; &#34;false&#34; &#124; &#34;true&#34;]${
                        ""
                    }[snippet/syntax/JsonBoolean.svg]
                    |
                    |Truth values are represented using the keywords `false` and `true`.
                    """.trimMargin(),
                ),
                ".js" to TextDocContent(
                    """
                    |const rr = require("@prantlf/railroad-diagrams");
                    |const fs = require("fs");
                    |
                    |const component = new (rr.Choice)(1, new (rr.Terminal)("false"),${
                        ""} new (rr.Terminal)("true"));
                    |
                    |const diagram = new rr.Diagram(component);
                    |
                    |let title = "\u003ctitle\u003eJsonBoolean \u0026#58;\u0026#61;${
                        ""} \u0026#34;false\u0026#34; \u0026#124;${
                        ""} \u0026#34;true\u0026#34;\u003c/title\u003e";
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
                    """.trimMargin(),
                ),
                GrammarDiagrams.GRAMMAR_DIAGRAM_EXTENSION to ShellCommandDocContent(
                    "make-railroad",
                    listOf("build-user-docs/build/snippet/syntax/JsonBoolean/snippet.js"),
                    groupTogether = true,
                ),
            ),
            jsonBooleanContentMap,
        )
    }
}
