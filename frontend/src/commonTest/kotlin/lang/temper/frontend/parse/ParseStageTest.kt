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
            |      cat("wanna be pair", error (list(raw "\:")), " ", error (list(raw "\ud800")), error (list(raw "\udc00")), "\nso does that have more pos needs?");
            |      cat("fine", " ", "escape", cat(" "), "here", error (list(raw "\u")));
            |      cat("too big: ", error (list(raw "\u{hi}")), error (list(raw "\u{110000}")), "!", error (list(raw "\u")));
            |      cat("space bad: ", " ", error (list(raw "\u{ }")), "!");
            |      cat("empty: ");
            |      cat("fine: ", " ");
            |      cat("also: ", " ", "!");
            |      error (list("`(QuotedGroup`", "\"", "`(Leaf`", "bad order: ", "`Leaf)`", "`(UnicodeRun`", raw "\u{", "`(Comma`", ",", "`(Leaf`", "20", "`Leaf)`", ",", ",", "`(Leaf`", "21", "`Leaf)`", ",", "`(Leaf`", "22", "`Leaf)`", "`Comma)`", "}", "`UnicodeRun)`", "\"", "`QuotedGroup)`"));
            |      raw(interpolate(raw "\u{", "}", raw "\u{", " ", "}"));
            |      raw(interpolate("too big: ", raw "\u{", " ", "hi", ",", " ", "110000", " ", "}", "!", raw "\u"));
            |      raw(interpolate("too big: ", raw "\u{", " ", "hi", \interpolate, cat(" there"), ",", " ", "110000", " ", "}", "!", raw "\u"));
            |      raw(interpolate("hi", raw "\u{", \interpolate, cat(" t"), "}", "here"));
            |      cat("wanna be ", pair, " in list:", " ", error (list(raw "\u{d800}")), error (list(raw "\u{dc00}")));
            |      cat("interpolate after list not in:", " ", cat("hi"));
            |      cat("hi");
            |      unhole(fn {
            |          hi
            |      });
            |      cat(cat("hi"));
            |      quasiInner(quasiLeaf(\hi));
            |      cat("surrogate, not scalar: ", error (list(raw "\ud834")), "!");
            |      cat("wanna be pair: ", error (list(raw "\ud800")), error (list(raw "\udc00")));
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
        |or(a < 2, a > 0);
        |//            ^---- Causes a parse failure.
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
}
