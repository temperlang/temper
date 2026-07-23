@file:Suppress("MaxLineLength")

package lang.temper.frontend.parse

import lang.temper.frontend.StageTestDir
import lang.temper.frontend.assertModuleAtStage
import lang.temper.lexer.Genre
import lang.temper.stage.Stage
import kotlin.test.Test

class ParseStageTest {
    @Test
    fun appendix() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/appendix"),
        stage = Stage.Parse,
        want = """
        |{
        |  parse: {
        |    body: ```
        |        foo()
        |
        |        ```,
        |    appendix: {
        |      foo: [
        |        "bar",
        |        { baz: -800 },
        |        false
        |      ]
        |    }
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun badUnicodeScalarValues() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/bad-unicode-scalar-values"),
        stage = Stage.Parse,
        want = """
            |{
            |  parse: {
            |    body: ```
            |      rgx(list("."), list());
            |      rgx(list(raw "(^|,)\s*"), list());
            |      stringExpr(null, false, "wanna be pair", error (list(raw "\:")), " ", error (list(raw "\ud800")), error (list(raw "\udc00")), "\nso does that have more pos needs?");
            |      stringExpr(null, false, "fine", " ", "escape", " ", "here", error (list(raw "\u")));
            |      stringExpr(null, false, "too big: ", error (list(raw "\u{hi}")), error (list(raw "\u{110000}")), "!", error (list(raw "\u")));
            |      stringExpr(null, false, "space bad: ", " ", error (list(raw "\u{ }")), "!");
            |      stringExpr(null, false, "empty: ");
            |      stringExpr(null, false, "fine: ", " ");
            |      stringExpr(null, false, "also: ", " ", "!");
            |      error (list("`(QuotedGroup`", "\"", "`(Leaf`", "bad order: ", "`Leaf)`", "`(UnicodeRun`", raw "\u{", "`(Comma`", ",", "`(Leaf`", "20", "`Leaf)`", ",", ",", "`(Leaf`", "21", "`Leaf)`", ",", "`(Leaf`", "22", "`Leaf)`", "`Comma)`", "}", "`UnicodeRun)`", "\"", "`QuotedGroup)`"));
            |      stringExpr(raw, true, raw "\u{", "}", raw "\u{", " ", "}");
            |      stringExpr(raw, true, "too big: ", raw "\u{", " ", "hi", ",", " ", "110000", " ", "}", "!", raw "\u");
            |      stringExpr(raw, true, "too big: ", raw "\u{", " ", "hi", \interpolate, " there", ",", " ", "110000", " ", "}", "!", raw "\u");
            |      stringExpr(raw, true, "hi", raw "\u{", \interpolate, " t", "}", "here");
            |      stringExpr(null, false, "wanna be ", pair, " in list:", " ", error (list(raw "\u{d800}")), error (list(raw "\u{dc00}")));
            |      stringExpr(null, false, "interpolate after list not in:", " ", "hi");
            |      "hi";
            |      \interpolate;
            |      hi;
            |      stringExpr(null, false, "hi");
            |      quasiInner(quasiLeaf(\hi));
            |      stringExpr(null, false, "surrogate, not scalar: ", error (list(raw "\ud834")), "!");
            |      stringExpr(null, false, "wanna be pair: ", error (list(raw "\ud800")), error (list(raw "\udc00")));
            |
            |      ```,
            |  },
            |  errors: [
            |    "Expected a Expression here!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun callJoinRewrite() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/call-join-rewrite"),
        stage = Stage.Parse,
        want = """
        |{
        |  parse: {
        |    body: ```
        |    if(a, fn {
        |        b
        |      }, \else_if, fn (f#0) {
        |        f#0(c, fn {
        |            d
        |          }, \else, fn (f#1) {
        |            f#1(fn {
        |                e
        |            })
        |        })
        |    })
        |
        |    ```,
        |  }
        }
        """.trimMargin(),
    )

    @Test
    fun callJoinRewriteForDocs() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/call-join-rewrite-for-docs"),
        stage = Stage.Parse,
        genre = Genre.Documentation,
        want = """
        |{
        |  parse: {
        |    body: ```
        |    if(a, fn {
        |        b
        |      }, \else_if, c, fn {
        |        d
        |      }, \else, fn {
        |        e
        |    })
        |
        |    ```,
        |  }
        }
        """.trimMargin(),
    )

    @Test
    fun angleBracketConfusionErrorMessageIsNotSuperTerrible() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/angle-bracket-confusion-error-message-is-not-super-terrible"),
        stage = Stage.Run,
        want = """
            |{
            |  stageCompleted: "GenerateCode",
            |  errors: [
            |    "Expected a TopLevel here!",
            |    "Interpreter encountered error()!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun unrepresentableIntegersWarnedOn() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/unrepresentable-integers-warned-on"),
        stage = Stage.Parse,
        want = """
            |{
            |  stageCompleted: "Parse",
            |  parse: {
            |    body: ```
            |      let a = -2147483648, b = 2147483647;
            |      REM("ok", null, false);
            |      let c = 2147483648;
            |      REM("ok", null, false);
            |      let d = -2147483648;
            |
            |      ```
            |  },
            |  errors: [
            |    {
            |      "template": "Int32OutOfBounds",
            |      "values": [ 2.147483648e+9 ]
            |    }
            |  ],
            |}
        """.trimMargin(),
    )
}
