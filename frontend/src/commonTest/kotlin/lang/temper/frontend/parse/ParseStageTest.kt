@file:Suppress("MaxLineLength")

package lang.temper.frontend.parse

import lang.temper.frontend.assertModuleAtStage
import lang.temper.lexer.Genre
import lang.temper.stage.Stage
import kotlin.test.Test

class ParseStageTest {
    @Test
    fun appendix() = assertModuleAtStage(
        stage = Stage.Parse,
        input = """
        |foo()
        |;;;
        |{
        |  "foo": ["bar", { "baz": -800 }, false]
        |}
        """.trimMargin(),
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
        stage = Stage.Parse,
        // Purposely do some things that might throw off sloppy position estimation.
        // And include regex, even with good escapes, to make sure we handle such.
        input = $$"""
            |/./;
            |/(^|,)\s*/;
            |$${'"'}""
            |"wanna${} be pair\: \ud800\udc00
            |"so does that have more pos needs?
            |;
            |"fine\u0020escape${" "}here\u";
            |"too big: \u{hi,110000}!\u";
            |"space bad: \u{20, 21}";
            |"empty: \u{}";
            |"fine: \u{20}";
            |"also: \u{20,21}";
            |"bad order: \u{,20,,21,22}";
            |raw"\u{}\u{ }";
            |raw"too big: \u{ hi, 110000 }!\u";
            |raw"too big: \u{ hi${" there"}, 110000 }!\u";
            |raw"hi\u{${" t"}}here";
            |"wanna be ${pair} in list:\u{2${}0,d800,dc00}";
            |"interpolate after list not in:\u{20}${"hi"}";
            |"hi";
            |${hi};
            |"${"hi"}";
            |\{hi};
            |"surrogate, not scalar: \ud834!";
            |"wanna be pair: \ud800\udc00";
        """.trimMargin(),
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
        stage = Stage.Parse,
        input = """
        |if (a) { b } else if (c) { d } else { e }
        """.trimMargin(),
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
        stage = Stage.Parse,
        genre = Genre.Documentation,
        input = """
        |if (a) { b } else if (c) { d } else { e }
        """.trimMargin(),
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
        stage = Stage.Run,
        input = """
        |let or(a: Boolean, b: Boolean): Boolean { a || b }
        |let a = 1;
        |// The below has a use of angle-brackets, not a use
        |// of less-than and a use of greater-than.
        |or(a< 2, a > 0);
        |//  ^---- Missing space causes a parse failure.
        """.trimMargin(),
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
        stage = Stage.Parse,
        input = """
            |let a = 2147483648;
            |let b = 2147483647; // ok
            |let c = 2147483648I64; // ok
            |let d = 0x8000_0000; // ok because idioms
        """.trimMargin(),
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
