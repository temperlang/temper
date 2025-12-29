package lang.temper.parser

import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.defaultErrorDumper
import lang.temper.common.testCodeLocation
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.format.TextOutputTokenSink
import lang.temper.lexer.Lexer
import lang.temper.value.TemperFormattingHints
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseTest {
    private fun assertParseTree(
        want: String,
        input: String,
    ) {
        val logSink = ListBackedLogSink()
        val lexer = Lexer(testCodeLocation, logSink, input)
        val root = parse(lexer, logSink)
        val got = logSink.wrapErrorsAround(root)
        assertStructure(
            expectedJson = want,
            input = got,
            message = "Parsed `$input`",
            errorDumper = { message, wantJson, gotJson ->
                console.group("Root") {
                    fun walk(cst: ConcreteSyntaxTree, depth: Int) {
                        when (cst) {
                            is CstInner ->
                                console.log("${"  ".repeat(depth)}${cst.operator.name}:")
                            is CstLeaf -> {
                                console.log(
                                    "${"  ".repeat(depth)}`${
                                        cst.tokenText.replace("\n", "\\n")
                                    }`",
                                )
                            }
                        }
                        for (i in 0 until cst.childCount) {
                            walk(cst.child(i), depth + 1)
                        }
                    }
                    walk(root, 0)
                }

                TemperFormattingHints.makeFormattingTokenSink(
                    TextOutputTokenSink(console.textOutput),
                ).use {
                    root.renderTo(it)
                }
                defaultErrorDumper(message, wantJson, gotJson)
            },
        )
    }

    @Test
    fun simpleValues() {
        assertParseTree(
            """[ "123" ]""",
            "123",
        )
    }

    @Test
    fun innerSemi() {
        assertParseTree(
            """[ ["123"], ";", ["456"] ]""",
            "123; 456",
        )
    }

    @Test
    fun trailingSemi() {
        assertParseTree(
            """[ ["123"], ";" ]""",
            "123;",
        )
    }

    @Test
    fun simpleArithmetic() {
        assertParseTree(
            """[ ["1"], "+", ["1"] ]""",
            "1 + 1",
        )
    }

    @Test
    fun missingOperand() {
        assertParseTree(
            """
            {
              result: [ ["1"], "+" ],
              errors: [
                {
                  template: "TooFewOperands",
                  values: [ "Plus", 2, 1 ],
                  left: 0,
                  right: 3
                }
              ]
            }
            """,
            "1 +",
        )
    }

    @Test
    fun positionInfo() {
        assertParseTree(
            """
            {
                operator: "+=",
                left: 0,
                right: 6,
                operands: [
                    {
                        operator: "Leaf",
                        left: 0,
                        right: 1,
                        operands: [
                            { text: "x", left: 0, right: 1 }
                        ]
                    },
                    { text: "+=", left: 2, right: 4 },
                    {
                        left: 5,
                        right: 6,
                        operator: "Leaf",
                        operands: [
                            { text: "y", left: 5, right: 6 }
                        ]
                    }
                ]
            }
            """,
            input = "x += y",
            //       0123456
        )
    }

    @Test
    fun leftAssociativity() = assertParseTree(
        """
        [
            [
                [ "1" ],
                "-",
                [ "1" ]
            ],
            "-",
            [ "1" ]
        ]
        """,
        "1 - 1 - 1",
    )

    @Test
    fun mixedOperatorsHighRight() = assertParseTree(
        """
        [
            [ "1" ],
            "-",
            [
                [ "1" ],
                "*",
                [ "1" ]
            ]
        ]
        """,
        "1 - 1 * 1",
    )

    @Test
    fun mixedOperatorsHighLeft() = assertParseTree(
        """
        [
            [
                [ "1" ],
                "*",
                [ "1" ]
            ],
            "-",
            [ "1" ]
        ]
        """,
        "1 * 1 - 1",
    )

    @Test
    fun rightAssociativity() = assertParseTree(
        """
        [
            [ "a" ],
            "=",
            [
                [ "b" ],
                "=",
                [ "c" ]
            ]
        ]
        """,
        "a = b = c",
    )

    @Test
    fun nestedPrefixOps() = assertParseTree(
        """
        [
            "!",
            [
                "!",
                [ "b" ]
            ]
        ]
        """,
        "! !b",
    )

    @Test
    fun logicalOperators() = assertParseTree(
        """
        [
            [
                [ "a" ],
                "&&",
                [ "b" ]
            ],
            "||",
            [
                [ "c" ],
                "==",
                [ "d" ]
            ]
        ]
        """,
        "a && b || c == d",
    )

    @Test
    fun simpleLessThanOperationAtTopLevel() = assertParseTree(
        """
        [
            [ "a" ],
            "<",
            [ "b" ]
        ]
        """,
        "a<b", // Nothing that interferes with angle bracket interpretation before end of input
    )

    @Test
    fun angleFollowedByCloserTreatedAsAngleBrackets() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [ "a" ],
                "<",
                [
                    [ "b" ],
                    ",",
                    [ "c" ],
                ],
                ">",
            ],
            ")"
        ]
        """,
        "f(a<b, c>)",
    )

    @Test
    fun angleNotFollowedByCloserTreatedAsComparison() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [
                    [ "a" ],
                    "<",
                    [ "b" ]
                ],
                ",",
                [ "c" ],
            ],
            ")"
        ]
        """,
        "f(a<b, c)",
    )

    @Test
    fun ampAmpInsideAnglesBreaksBracketing() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [
                    [ "a" ],
                    "<",
                    [ "b" ]
                ],
                "&&",
                [
                    [ "c" ],
                    ">",
                    [ "d" ]
                ]
            ],
            ")"
        ]
        """,
        "f(a<b && c>d)",
    )

    @Test
    fun barBarInsideAnglesBreaksBracketing() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [
                    [ "a" ],
                    "<",
                    [ "b" ]
                ],
                "||",
                [
                    [ "c" ],
                    ">",
                    [ "d" ]
                ]
            ],
            ")"
        ]
        """,
        "f(a<b || c>d)",
    )

    @Test
    fun xorInsideAnglesDoesntBreakBracketing() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [
                    [ "a" ],
                    "<",
                    [
                        [ "b" ],
                        "^",
                        [ "c" ],
                    ]
                ],
                ">",
                [ "d" ]
            ],
            ")"
        ]
        """,
        "f(a < b^c > d)",
    )

    @Test
    fun emptyString() = assertParseTree(
        input = "\"\"",
        want = """
        |[
        |  "(",
        |  [
        |    "\"",
        |    "\"",
        |  ],
        |  ")",
        |]
        """.trimMargin(),
    )

    @Test
    fun stringEscapes() = assertParseTree(
        input = """
            |raw"bonus: \u{,,20,,}";
            |"split no comma: \u{10${'$'}{}000}";
            |raw"hi\u{${'$'}{}2${'$'}{/*hi*/}0,${'$'}{"t"}${'$'}{}}here";
            |"\"hi\u{20,${'$'}{"there"},21,22}";
            |// hi
            |/(^|,)\s*/;
            |"wan${'$'}{/*hi*/}na\u0020be\x20:\ud800\udc00";
            |"whatever: ${'$'}{hi}!";
            |"bad code: \u{110000}!";
            |"bad code\u{3a,20, 110000 }!";
        """.trimMargin(),
        want = """
            |[
            |  [["raw"], "(", ["\"",
            |    ["bonus: "],
            |    [
            |      "\\u{", [
            |        ",", ",", ["20"], ",", ",",
            |      ], "}"
            |    ],
            |  "\""], ")"],
            |  ";",
            |  ["(", ["\"",
            |    ["split no comma: "],
            |    [
            |      "\\u{",
            |      ["10000"],
            |      "}"
            |    ],
            |  "\""], ")"],
            |  ";",
            |  [["raw"], "(", ["\"",
            |    ["hi"],
            |    [
            |      "\\u{", [
            |        ["20"], ",",
            |        ["${'$'}{", ["(", ["\"",
            |          ["t"],
            |        "\""], ")"], "}"],
            |      ], "}"
            |    ],
            |    ["here"],
            |  "\""], ")"],
            |  ";",
            |  ["(", ["\"",
            |    ["\\\"", "hi"],
            |    [
            |      "\\u{", [
            |        ["20"], ",",
            |        ["${'$'}{", ["(", ["\"",
            |          ["there"],
            |        "\""], ")"], "}"], ",",
            |        ["21"], ",",
            |        ["22"],
            |      ], "}"
            |    ],
            |  "\""], ")"],
            |  ";",
            |  ["/(^|,)", "\\s", "*/"],
            |  ";",
            |  ["(", ["\"", [
            |    "wanna", "\\u0020", "be", "\\x20", ":", "\\ud800", "\\udc00",
            |  ], "\""], ")"],
            |  ";",
            |  ["(", ["\"",
            |    ["whatever: "],
            |    ["${'$'}{", [
            |      "hi",
            |    ], "}"],
            |    ["!"],
            |  "\""], ")"],
            |  ";",
            |  ["(", ["\"",
            |    ["bad code: "],
            |    [
            |      "\\u{",
            |      ["110000"],
            |      "}"
            |    ],
            |    ["!"],
            |  "\""], ")"],
            |  ";",
            |  ["(", ["\"",
            |    ["bad code"],
            |    [
            |      "\\u{", [
            |        ["3a"], ",",
            |        ["20"], ",",
            |        [" ", "110000", " "],
            |      ], "}"
            |    ],
            |    ["!"],
            |  "\""], ")"],
            |  ";",
            |]
        """.trimMargin(),
    )

    @Test
    fun stringOnlyEmbed() = assertParseTree(
        input = "\"${INTERP_EMBED}a}\"",
        want = """
        |[
        |  "(",
        |  [
        |    "\"",
        |    [
        |      "$INTERP_EMBED",
        |      [ "a" ],
        |      "}",
        |    ],
        |    "\"",
        |  ],
        |  ")",
        |]
        """.trimMargin(),
    )

    @Test
    fun emptyEmbed() = assertParseTree(
        // Test using an empty interpolation ${} between '$' and '{'
        // to allow literally the string "${".
        input = """ "$$INTERP_EMBED}{" """,
        want = """
        |[
        |  "(",
        |  [
        |    "\"",
        |    ["$INTERP_EMBED"],
        |    "\"",
        |  ],
        |  ")",
        |]
        """.trimMargin(),
    )

    @Test
    fun runOfQuotesInMultiQuotedString() {
        val q3 = "\"\"\""
        assertParseTree(
            input = """
                |$q3
                |"foo $q3 bar
            """.trimMargin(),
            want = """
                |[
                |  "(",
                |  [
                |    "\"\"\"",
                |    ["foo \"\"\" bar"],
                |    "\"\"\"",
                |  ],
                |  ")"
                |]
            """.trimMargin(),
        )
    }

    @Test
    fun greaterThanInsideTemplateStringDoesNotCompleteAngle() = assertParseTree(
        """
        [
          [
            ["a"],
            "<",
            [
              "(",
              [
                "\"",
                ["..."],
                [
                  "$INTERP_EMBED",
                  [
                    ["b"],
                    ">",
                    ["-", ["c"]]
                  ],
                  "}"
                ],
                ["..."],
                "\"",
              ],
              ")",
            ],
          ],
          ";",
          [
            [
              ["a"],
              "<",
              [
                "(",
                [
                  "\"",
                  ["..."],
                  [
                    "$INTERP_EMBED",
                    [
                      ["b"],
                      ">",
                      ["-", ["c"]]
                    ],
                    "}",
                  ],
                  ["..."],
                  "\"",
                ],
                ")",
              ],
              ">",
            ],
            "-",
            ["d"]
          ],
          ";"
        ]
        """,
        """
        a < "...$INTERP_EMBED b > - c }...";       // Compares a to a composed string
        a < "...$INTERP_EMBED b > - c }..." > - d; // a parameterized w/ a string template minus d
        """,
    )

    @Test
    fun angleBracketsAfterColon() = assertParseTree(
        """
        [
            [ "f" ],
            "(",
            [
                [ "d" ],
                ":",
                [
                    [ "a" ],
                    "<",
                    [
                        [ "b" ],
                        ",",
                        [ "c" ]
                    ],
                    ">"
                ]
            ],
            ")"
        ]
        """,
        "f(d: a<b, c>)",
    )

    @Test
    fun angleBracketsOutsideArgumentList() = assertParseTree(
        """
        [
            [
                [
                    [
                        "let",
                        "a"
                    ],
                    ":",
                    [
                        [ "Array" ],
                        "<",
                        [
                            [ "Array" ],
                            "<",
                            [ "number" ],
                            ">"
                        ],
                        ">"
                    ]
                ],
                "=",
                [
                    "[",
                    [
                        "[",
                        "]"
                    ],
                    "]"
                ]
            ],
            ";"
        ]
        """,
        // multiple closers in  '>>='
        "let a: Array<Array<number>>= [[]];",
    )

    @Test
    fun tag() = assertParseTree(
        input = "<bar>",
        want = """
        [
          "<",
          [ "bar" ],
          ">"
        ]
        """,
    )

    @Test
    fun closeTag() = assertParseTree(
        input = "<bar> . x . </bar>",
        want = """
        [
          [
            [
              "<",
              [ "bar" ],
              ">",
            ],
            ".",
            [ "x" ],
          ],
          ".",
          [
            "</",
            [ "bar" ],
            ">",
          ],
        ]
        """,
    )

    @Test
    fun tagWithMultipleAttributes() = assertParseTree(
        input = """<html:a b="c" d="e" f:g="h">""",
        want = """
        [
          "<",
          [
            [ "html" ],
            ":",
            [ "a" ],
          ],
          [
            [ "b" ],
            "=",
            [
              "(",
              [ "\"", ["c"], "\"" ],
              ")"
            ]
          ],
          [
            [ "d" ],
            "=",
            [
              "(",
              [ "\"", ["e"], "\"" ],
              ")"
            ]
          ],
          [
            [
              [ "f" ],
              ":",
              [ "g" ]
            ],
            "=",
            [
              "(",
              [ "\"", ["h"], "\"" ],
              ")"
            ]
          ],
          ">",
        ]
        """,
    )

    @Test
    fun multiTagExample() = assertParseTree(
        input = """<foo:bar one="1" two="2"> & <a href=url title="title"> & </a>""",
        want = """
        [
          [
            "<",
            [
              [ "foo" ],
              ":",
              [ "bar" ],
            ],
            [
              [ "one" ],
              "=",
              [ "(", [ "\"", ["1"], "\"" ], ")" ]
            ],
            [
              [ "two" ],
              "=",
              [ "(", [ "\"", ["2"], "\"" ], ")" ]
            ],
            ">",
          ],
          "&",
          [
            [
              "<",
              [ "a" ],
              [
                [ "href" ],
                "=",
                [ "url" ],
              ],
              [
                [ "title" ],
                "=",
                [ "(", [ "\"", ["title"], "\"" ], ")" ]
              ],
              ">",
            ],
            "&",
            [
              "</",
              [ "a" ],
              ">",
            ]
          ]
        ]
        """,
    )

    @Test
    fun noAngleBracketConfusion() = assertParseTree(
        """
        [
            [
                [
                    [
                        "let",
                        "x"
                    ],
                    ":",
                    [
                        [ "Boolean" ],
                        "?",
                    ],
                ],
                "=",
                [
                    [
                        [ "b" ],
                        "<",
                        [ "c" ]
                    ],
                    "&&",
                    [
                        [ "c" ],
                        ">",
                        [ "d" ]
                    ]
                ]
            ],
            ";"
        ]
        """,
        "let x: Boolean? = b < c && c > d;",
    )

    @Test
    fun angleBracketsInTypescriptesqueTypeDeclaration() = assertParseTree(
        """
        [
            [
                [
                    "type",
                    "nums"
                ],
                "=",
                [
                    [ "Set" ],
                    "<",
                    [ "number" ],
                    ">"
                ]
            ],
            ";"
        ]
        """,
        "type nums = Set<number>;",
    )

    @Test
    fun ifStmt() = assertParseTree(
        """
        [
            [
                [ "if" ],
                "(",
                [ "e" ],
                ")"
            ],
            "{",
            [ "stmt" ],
            "}"
        ]
        """,
        "if (e) { stmt }",
    )

    @Test
    fun ifElseStmt() = assertParseTree(
        """
        [
            [
                [
                    [ "if" ],
                    "(",
                    [ "e" ],
                    ")"
                ],
                "{",
                [ "stmt" ],
                "}"
            ],
            "callJoin:",
            [
                [ "else" ],
                "{",
                [ "stmt" ],
                "}"
            ]
        ]
        """,
        "if (e) { stmt } else { stmt }",
    )

    @Test
    fun whileStmt() = assertParseTree(
        """
        [
            [
                [ "while" ],
                "(",
                [ "e" ],
                ")"
            ],
            "{",
            [ "stmt" ],
            "}"
        ]
        """,
        "while (e) { stmt }",
    )

    @Test
    fun forStmt() = assertParseTree(
        """
        [
            [
                [ "for" ],
                "(",
                [
                    [ "e" ],
                    ";",
                    [ "e" ],
                    ";",
                    [ "e" ]
                ],
                ")"
            ],
            "{",
            [ "stmt" ],
            "}"
        ]
        """,
        "for (e;e;e) { stmt }",
    )

    @Test
    fun forInStmt() = assertParseTree(
        """
        [
            [
                [ "for" ],
                "(",
                [
                    [
                        "t",
                        "e"
                    ],
                    "in",
                    [ "e" ]
                ],
                ")"
            ],
            "{",
            [ "stmt" ],
            "}"
        ]
        """,
        "for (t e in e) { stmt }",
    )

    @Test
    fun tryWithValue() = assertParseTree(
        """
        [
            [
                [ "try" ],
                "(",
                [ "decl" ],
                ")"
            ],
            "{",
            [ "stuff" ],
            "}"
        ]
        """,
        "try (decl) { stuff }",
    )

    @Test
    fun tryWithCatchAndFinally() = assertParseTree(
        """
        [
            [
                [
                    [ "try" ],
                    "(",
                    [ "decl" ],
                    ")"
                ],
                "{",
                [ "stuff" ],
                "}"
            ],
            "callJoin:",
            [
                [
                    [
                        [ "catch" ],
                        "(",
                        [ "e" ],
                        ")"
                    ],
                    "{",
                    [ "stuff" ],
                    "}"
                ],
                "callJoin:",
                [
                    [ "finally" ],
                    "{",
                    [ "stuff" ],
                    "}"
                ]
            ]
        ]
        """,
        "try (decl) { stuff } catch (e) { stuff } finally { stuff }",
    )

    @Test
    fun javascriptesqueFunctionDeclaration() = assertParseTree(
        """
        [
            [
                [ "function" ],
                "(",
                [
                    [ "a" ],
                    ",",
                    [ "b" ],
                    ",",
                    [ "c" ]
                ],
                ")"
            ],
            "{",
            [ "stuff" ],
            "}"
        ]
        """,
        "function (a,b,c) { stuff }",
    )

    @Test
    fun withStmt() = assertParseTree(
        """
        [
            [
                [ "with" ],
                "(",
                [ "x" ],
                ")"
            ],
            "{",
            [
                [ "stuff" ],
                "(",
                ")"
            ],
            "}"
        ]
        """,
        "with (x) { stuff() }",
    )

    @Test
    fun emptyBlock() = assertParseTree(
        """
        [
            "{",
            "}"
        ]
        """,
        "{}",
    )

    @Test
    fun blockWithSemi() = assertParseTree(
        """
        [
            "{",
            [ ";" ],
            "}"
        ]
        """,
        "{;}",
    )

    @Test
    fun blockAroundFunctionApplication() = assertParseTree(
        """
        [
            "{",
            [
                [
                    [ "f" ],
                    "(",
                    ")"
                ],
                ";"
            ],
            "}"
        ]
        """,
        "{f();}",
    )

    @Test
    fun charLiteral() = assertParseTree(
        input = "'a'",
        want = """
        [
          "(",
          [
            "'",
            ["a"],
            "'"
          ],
          ")",
        ]
        """,
    )

    @Test
    fun ampInfixPrefixAmbiguity() = assertParseTree(
        input = "- .1 & Q",
        want = """
        [
            [
                "-",
                [ ".1" ]
            ],
            "&",
            [ "Q" ]
        ]
        """,
    )

    @Test
    fun semicolonBeforePreIncrement() = assertParseTree(
        input = "; ++ fe",
        want = """
        {
          operator: "Semi",
          operands: [
            ";",
            {
              operator: "PreIncr",
              operands: [
                "++",
                [ "fe" ]
              ]
            }
          ]
        }
        """,
    )

    @Test
    fun doWhile() = assertParseTree(
        """
        [
            [
                [
                    [ "do" ],
                    "{",
                    [ "stuff" ],
                    "}"
                ],
                "callJoin:",
                [
                    [ "while" ],
                    "(",
                    [
                        "!",
                        [ "done" ]
                    ],
                    ")"
                ]
            ],
            ";"
        ]
        """,
        "do { stuff } while (!done);",
    )

    @Test
    fun labeledLoop() = assertParseTree(
        """
        [
            [ "label" ],
            ":",
            [
                [
                    [ "for" ],
                    "(",
                    [
                        ";",
                        ";"
                    ],
                    ")"
                ],
                "{",
                "}"
            ]
        ]
        """,
        "label: for (; ;) {}",
    )

    @Test
    fun succinctObjectAsTypeAndExpression() = assertParseTree(
        """
        [
            [
                [
                    [
                        "let",
                        "x"
                    ],
                    ":",
                    [
                        "{",
                        [
                            [ "key" ],
                            ":",
                            [ "valueType" ]
                        ],
                        "}"
                    ]
                ],
                "=",
                [
                    "{",
                    [
                        [ "key" ],
                        ":",
                        [ "valueExpr" ]
                    ],
                    "}"
                ]
            ],
            ";"
        ]
        """,
        "let x : { key: valueType } = { key: valueExpr };",
    )

    @Test
    fun whileStmtFollowedByBlock() = assertParseTree(
        // This would not be a problem if there were a line break after {}.
        // TODO: lint error on this pattern
        input = "while (b) {} {}",
        want = """
        [
            [
                [
                    [ "while" ],
                    "(",
                    [ "b" ],
                    ")"
                ],
                "{",
                "}"
            ],
            "{",
            "}"
        ]
        """,
    )

    @Test
    fun returnStmtWithExpression() = assertParseTree(
        """
        [
            [
                "return",
                [ "x" ]
            ],
            ";"
        ]
        """,
        "return x;",
    )

    @Test
    fun returnStmtWithParenthesizedExpression() = assertParseTree(
        """
        [
            [
                "return",
                [
                    "(",
                    [ "x" ],
                    ")"
                ]
            ],
            ";"
        ]
        """,
        "return (x);",
    )

    @Test
    fun returnNoExpr() = assertParseTree(
        """
        [
            [ "return" ],
            ";"
        ]
        """,
        "return;",
    )

    @Test
    fun returnOfExpressionWithPrefixOperator() = assertParseTree(
        """
        [
            [
                "return",
                [
                    "-",
                    [ "1" ]
                ]
            ],
            ";"
        ]
        """,
        "return -1;",
    )

    @Test
    fun mixedPrefixAndInfix() = assertParseTree(
        """
        [
            [
                [ "x" ],
                "-",
                [
                    "-",
                    [ "y" ]
                ]
            ],
            ";"
        ]
        """,
        "x - -y;",
    )

    @Test
    fun signInfixOperatorAmbiguity() = assertParseTree(
        """
        [
            [
                [ "x" ],
                "-",
                [ "1" ]
            ],
            ";"
        ]
        """,
        "x -1;",
    )

    @Test
    fun throwStmtWithLowPrecedenceExpression() = assertParseTree(
        """
        [
            [
                "throw",
                [
                    [ "e" ],
                    "||",
                    [
                        [
                            "new",
                            [ "Error" ]
                        ],
                        "(",
                        ")"
                    ]
                ]
            ],
            ";"
        ]
        """,
        "throw e || new Error();",
    )

    @Test
    fun throwNoExpression() = assertParseTree(
        """
        {
          result:
          [
              [ "throw" ],
              ";"
          ],
          errors: [
            {
              template: "TooFewOperands",
              values: [ "Throw", 1, 0 ],
              left: 0, right: 5
            }
          ]
        }
        """,
        "throw;",
    )

    @Test
    fun simpleFunctionApplication() = assertParseTree(
        """
        [
            [
                [ "f" ],
                "(",
                [
                    [ "actual0" ],
                    ",",
                    [ "actual1" ],
                    ",",
                    [ "actual2" ]
                ],
                ")"
            ],
            ";"
        ]
        """,
        "f(actual0,actual1,actual2);",
    )

    @Test
    fun forLoop() = assertParseTree(
        """
        [
            [
                [ "for" ],
                "(",
                [
                    [ "init" ],
                    ";",
                    [ "cond" ],
                    ";",
                    [ "incr" ]
                ],
                ")"
            ],
            "{",
            "}"
        ]
        """,
        "for (init;cond;incr) {}",
    )

    @Test
    fun forMissingAllOptionalPieces() = assertParseTree(
        """
        [
            [
                [ "for" ],
                "(",
                [
                    ";",
                    ";"
                ],
                ")"
            ],
            "{",
            "}"
        ]
        """,
        "for (; ;) {}",
    )

    @Test
    fun runOfNoOpsInABlock() = assertParseTree(
        """
        [
            "{",
            [
              ";",
              ";",
              ";"
            ],
            "}"
        ]
        """,
        "{ ; ; ; }",
    )

    @Test
    fun taggedTemplate() = assertParseTree(
        """
        [
            [
                [
                    [ "f" ],
                    "(",
                    [
                        "\"",
                        [ "__" ],
                        [
                            "$INTERP_EMBED",
                            [
                                [ "x" ],
                                ";"
                            ],
                            "}",
                        ],
                        [ "__" ],
                        [
                            "$INTERP_EMBED",
                            [
                                [ "y" ],
                                "=",
                                [
                                    [ "z" ],
                                    "(",
                                    ")"
                                ]
                            ],
                            "}",
                        ],
                        [ "__" ],
                        "\"",
                    ],
                    ")"
                ],
                "+",
                [
                    "(",
                    [
                        "\"",
                        [ "str" ],
                        "\"",
                    ],
                    ")"
                ]
            ],
            ";"
        ]
        """,
        """
         f"__$INTERP_EMBED x; }__$INTERP_EMBED y = z() }__" + "str";
         //                   ^                      ^ `=` does not drop out of `+`
         //                   | Semicolon does not drop out to statement context
        """,
    )

    @Test
    fun nonStandardOperators() = assertParseTree(
        """
        [
            [
                [ "for" ],
                "(",
                [
                    [
                        [
                            "val",
                            "a"
                        ],
                        "=",
                        [ "1" ]
                    ],
                    ";",
                    [
                        [
                            [ "a" ],
                            "+",
                            [ "b" ]
                        ],
                        "<=>",
                        [
                            [
                                [ "c" ],
                                "*",
                                [ "d" ]
                            ],
                            "+.",
                            [ "e" ]
                        ]
                    ],
                    ";",
                    [
                        "++",
                        [ "a" ]
                    ]
                ],
                ")"
            ],
            "{",
            [
                [ "doTheThing" ],
                "(",
                [ "a" ],
                ")"
            ],
            "}"
        ]
        """,
        "for (val a = 1; a + b <=> c * d +. e; ++a) { doTheThing(a) }",
    )

    @Test
    fun incrementedBlock() = assertParseTree(
        input = "{ { } ++ i }",
        want = """
        [
          "{",
          [
            [ "{", "}" ],
            "++"
          ],
          [ "i" ],
          "}"
        ]
        """,
    )

    @Test
    fun negationAfterBlock() = assertParseTree(
        input = "{ { } - i }",
        want = """
        [
          "{",
          [
            [ "{", "}" ],
            "-",
            [ "i" ]
          ],
          "}"
        ]
        """,
    )

    @Test
    fun curliesInNew() = assertParseTree(
        input = "x = new { k: v }",
        want = """
        [
          [ "x" ],
          "=",
          [
            "new",
            [
              "{",
              [
                [ "k" ],
                ":",
                [ "v" ]
              ],
              "}"
            ]
          ]
        ]
        """,
    )

    @Test
    fun curlyCompare() = assertParseTree(
        input = "{ a } > { b }",
        want = """
        [
            [
                "{",
                    [ "a" ],
                "}"
            ],
            ">",
            [
                "{",
                    [ "b" ],
                "}"
            ]
        ]
        """,
    )

    @Test
    fun simpleCompare() = assertParseTree(
        input = "a > b",
        want = """
        [
            [ "a" ],
            ">",
            [ "b" ]
        ]
        """,
    )

    @Test
    fun multiLetUnpackedRenamedTyped() = assertParseTree(
        // At time of writing, this is preliminary exploration.
        input = "let { a is D: { b } as c } = f();",
        want = """
        [
            [
                [
                    [ "let" ],
                    "{",
                    [
                        [
                            [ "a" ],
                            "is",
                            [ "D" ]
                        ],
                        ":",
                        [
                            [
                                "{",
                                [ "b" ],
                                "}"
                            ],
                            "as",
                            [ "c" ]
                        ],
                    ],
                    "}"
                ],
                "=",
                [
                    [ "f" ],
                    "(",
                    ")"
                ]
            ],
            ";"
        ]
        """,
    )

    @Test
    fun declWithAsAndIs() = assertParseTree(
        input = """
            |let { a is S, b, c as d is T } = f()
        """.trimMargin(),
        want = """
            [
              [
                [ "let" ],
                "{",
                [
                  [
                    [ "a" ],
                    "is",
                    [ "S" ],
                  ],
                  ",",
                  [ "b" ],
                  ",",
                  [
                    [
                      ["c"],
                      "as",
                      ["d"],
                    ],
                    "is",
                    ["T"],
                  ]
                ],
                "}",
              ],
              "=",
              [
                ["f"],
                "(", ")",
              ]
            ]
        """.trimIndent(),

    )

    @Test
    fun colonsAroundWhile() = assertParseTree(
        input = "x: while (1) {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [ "while" ],
              "(",
              [ "1" ],
              ")"
            ],
            "{",
            "}"
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundFor() = assertParseTree(
        input = "x: for (;;) {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [ "for" ],
              "(",
              [ ";;" ],
              ")"
            ],
            "{",
            "}"
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundDoWhile() = assertParseTree(
        input = "x: do {} while (c)",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [ "do" ],
              "{", "}"
            ],
            "callJoin:",
            [
              [ "while" ],
              "(",
              [ "c" ],
              ")"
            ]
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundIf() = assertParseTree(
        input = "x: if (c) {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [ "if" ],
              "(",
              [ "c" ],
              ")",
            ],
            "{",
            "}"
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundIfElse() = assertParseTree(
        input = "x: if (c) {} else {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [
                [ "if" ],
                "(",
                [ "c" ],
                ")",
              ],
              "{",
              "}"
            ],
            "callJoin:",
            [
              [ "else" ],
              "{",
              "}"
            ]
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundIfElseIf() = assertParseTree(
        input = "x: if (c) {} else if (d) {} else {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [
                [ "if" ],
                "(",
                [ "c" ],
                ")",
              ],
              "{",
              "}"
            ],
            "callJoin:",
            [
              [
                [
                  [ "else", "if" ],
                  "(",
                  [ "d" ],
                  ")",
                ],
                "{",
                "}"
              ],
              "callJoin:",
              [
                [ "else" ],
                "{",
                "}"
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun colonsAroundTry() = assertParseTree(
        input = "x: try (c) {} catch (e) {} finally {}",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [
                [ "try" ],
                "(",
                [ "c" ],
                ")",
              ],
              "{",
              "}"
            ],
            "callJoin:",
            [
              [
                [
                  [ "catch" ],
                  "(",
                  [ "e" ],
                  ")"
                ],
                "{",
                "}"
              ],
              "callJoin:",
              [
                [ "finally" ],
                "{",
                "}"
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun stuffAfterLabeledStmt() = assertParseTree(
        input = "x: while (c) {}\ncontinue",
        want = """
        [
          [
            [ "x" ],
            ":",
            [
              [
                [ "while" ],
                "(",
                [ "c" ],
                ")"
              ],
              "{",
              "}"
            ]
          ],
          ";",
          [ "continue" ]
        ]
        """,
    )

    @Test
    fun ambiguousInfixStuffAfterLabeledStmt() = assertParseTree(
        // Is `+` supposed to be an infix or prefix operator.  Probably missing a semicolon.
        // Not a problem when there's a linebreak between the close bracket and `+` because of ASI.
        input = "x: while (c) {} +f()",
        want = """
        [
          [ "x" ],
          ":",
          [
            [
              [
                [ "while" ],
                "(",
                [ "c" ],
                ")"
              ],
              "{",
              "}"
            ],
            "+",
            [
              [ "f" ],
              "(",
              ")"
            ]
          ]
        ]
        """,
    )

    @Test
    fun fuzzTestFinding1() = assertParseTree(
        input = "e: { }\nthrow - Wt",
        want = """
        [
          [
            [ "e" ],
            ":",
            [
              "{",
              "}"
            ]
          ],
          ";",
          [
            "throw",
            [
              "-",
              [ "Wt" ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun fuzzTestFinding2() = assertParseTree(
        input = "if (a) {\n  b\n} else {\n  c\n}\nbreak;",
        want = """
        [
          [
            [
              [
                [ "if" ],
                "(",
                [ "a" ],
                ")"
              ],
              "{",
              [ "b" ],
              "}"
            ],
            "callJoin:",
            [
              [ "else" ],
              "{",
              [ "c" ],
              "}"
            ]
          ],
          ";",
          [ "break" ],
          ";"
        ]
        """,
    )

    @Test
    fun multiLet() = assertParseTree(
        input = "let [a: T, b = 1] = f()",
        want = """
        [
          [
            [ "let" ],
            "[",
            [
              [
                [ "a" ],
                ":",
                [ "T" ],
              ],
              ",",
              [
                [ "b" ],
                "=",
                [ "1" ],
              ],
            ],
            "]",
          ],
          "=",
          [
            [ "f" ],
            "(",
            ")"
          ]
        ]
        """,
    )

    @Test
    fun multiLetCurly() = assertParseTree(
        input = "let { a: T, b = 1, c: { d, e: U } } = f()",
        want = """
        [
          [
            [ "let" ],
            "{",
            [
              [
                [ "a" ],
                ":",
                [ "T" ],
              ],
              ",",
              [
                [ "b" ],
                "=",
                [ "1" ],
              ],
              ",",
              [
                [ "c" ],
                ":",
                [
                  "{",
                  [
                    [ "d" ],
                    ",",
                    [
                      [ "e" ],
                      ":",
                      [ "U" ]
                    ]
                  ],
                  "}"
                ]
              ]
            ],
            "}",
          ],
          "=",
          [
            [ "f" ],
            "(",
            ")"
          ]
        ]
        """,
    )

    @Test
    fun multiLetFirstInMultiDecl() = assertParseTree(
        input = "let [a: T, b = 1] = f(), c = null",
        want = """
        [
          [
            [
              [ "let" ],
              "[",
              [
                [
                  [ "a" ],
                  ":",
                  [ "T" ],
                ],
                ",",
                [
                  [ "b" ],
                  "=",
                  [ "1" ],
                ],
              ],
              "]",
            ],
            "=",
            [
              [ "f" ],
              "(",
              ")"
            ]
          ],
          ",",
          [
            [ "c" ],
            "=",
            [ "null" ],
          ]
        ]
        """,
    )

    @Test
    fun multiLetSecondInMultiDecl() = assertParseTree(
        input = "let a = null, (b: T, c = 1) = f()",
        want = """
        [
          [
            [ "let", "a" ],
            "=",
            [ "null" ],
          ],
          ",",
          [
            [
              "(",
              [
                [
                  [ "b" ],
                  ":",
                  [ "T" ],
                ],
                ",",
                [
                  [ "c" ],
                  "=",
                  [ "1" ],
                ],
              ],
              ")",
            ],
            "=",
            [
              [ "f" ],
              "(",
              ")"
            ]
          ]
        ]
        """,
    )

    @Test
    fun blockLambda1() = assertParseTree(
        input = """
            |f { => };
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [ "f" ],
            |    "{",
            |    [
            |      "=>",
            |    ],
            |    "}"
            |  ],
            |  ";",
            |]
        """.trimMargin(),
    )

    @Test
    fun blockLambda2() = assertParseTree(
        input = """
            |f {};
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [ "f" ],
            |    "{",
            |    "}"
            |  ],
            |  ";",
            |]
        """.trimMargin(),
    )

    @Test
    fun blockLambda3() = assertParseTree(
        input = """
            |f { (fn: Generator) =>
            |  stmt;
            |}
        """.trimMargin(),
        want = """
            |[
            |  [ "f" ],
            |  "{",
            |  [
            |    [
            |      "(",
            |      [
            |        [ "fn" ],
            |        ":",
            |        [ "Generator" ],
            |      ],
            |      ")",
            |    ],
            |    "=>",
            |    [
            |      ["stmt"],
            |      ";"
            |    ]
            |  ],
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun blockLambda4() = assertParseTree(
        input = """
            |f { (fn: Generator, a: T) =>
            |}
        """.trimMargin(),
        want = """
            |[
            |  [ "f" ],
            |  "{",
            |  [
            |    [
            |      "(",
            |      [
            |        [ [ "fn" ], ":", [ "Generator" ] ],
            |        ",",
            |        [ [ "a" ], ":", [ "T" ] ],
            |      ],
            |      ")",
            |    ],
            |    "=>",
            |  ],
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun blockLambda5() = assertParseTree(
        input = """
            |f { (fn: Generator, a: T): RT =>
            |  body
            |}
        """.trimMargin(),
        want = """
            |[
            |  [ "f" ],
            |  "{",
            |  [
            |    [
            |      [
            |        "(",
            |        [
            |          [ [ "fn" ], ":", [ "Generator" ] ],
            |          ",",
            |          [ [ "a" ], ":", [ "T" ] ],
            |        ],
            |        ")",
            |      ],
            |      ":",
            |      [ "RT" ],
            |    ],
            |    "=>",
            |    ["body"],
            |  ],
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun blockLambda6() = assertParseTree(
        input = """
            |f { (fn: Generator, a: T) =>
            |  body0;
            |  body1
            |}
        """.trimMargin(),
        want = """
            |[
            |  [ "f" ],
            |  "{",
            |  [
            |    [
            |      "(",
            |      [
            |        [ [ "fn" ], ":", [ "Generator" ] ],
            |        ",",
            |        [ [ "a" ], ":", [ "T" ] ],
            |      ],
            |      ")",
            |    ],
            |    "=>",
            |    [
            |      ["body0"],
            |      ";",
            |      ["body1"],
            |    ]
            |  ],
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun positions() {
        val input = listOf(
            "" + "\n",
            //    0
            "" + "        function fib(i) {\n",
            //             1         2
            //    12345678901234567890123456
            "" + "          let a = 0; \n",
            //       3         4
            //    7890123456789012345678
            "" + "          let b = 1;\n",
            //     5         6
            //    901234567890123456789
            "" + "          while (i > 0) {\n",
            //    7         8         9
            //    01234567890123456789012345
            "" + "            let c = a + b;\n",
            //        1         1         1
            //        0         1         2
            //    678901234567890123456789012
            "" + "            a = b;\n",
            //           1         1
            //           3         4
            //    3456789012345678901
            "" + "            b = c;\n",
            //            1         1
            //            5         6
            //    2345678901234567890
            "" + "          }\n",
            //             1
            //             7
            //    123456789012
            "" + "          a;\n",
            //           1
            //           8
            //    3456789012345
            "" + "        }\n",
            //        1
            //        9
            //    6789012345
            "" + "        fib(8);\n",
            //        2         2
            //        0         1
            //    6789012345678901
            "" + "        ",
            //            2
            //            2
            //    234567890
        ).joinToString("")
        assertEquals(220, input.length)

        assertParseTree(
            input = input,
            want = """
            {
              operator: "Semi",
              left: 9, right: 211,
              operands: [
                {
                  operator: "Curly",
                  left: 9, right: 195,
                  operands: [
                    {
                      operator: "Paren",
                      left: 9,
                      right: 24,
                      operands: [
                        {
                          operator: "Leaf", left: 9, right: 21,
                          operands: [
                            { text: "function", type: "Word", left: 9, right: 17 },
                            { text: "fib", type: "Word", left: 18, right: 21 }
                          ]
                        },
                        { text: "(", type: "Punctuation", left: 21, right: 22 },
                        {
                          operator: "Leaf", left: 22, right: 23,
                          operands: [
                            { text: "i", type: "Word", left: 22, right: 23 }
                          ]
                        },
                        { text: ")", type: "Punctuation", left: 23, right: 24 },
                      ]
                    },
                    { text: "{", type: "Punctuation", left: 25, right: 26 },
                    {
                      operator: "Semi",
                      left: 37,
                      right: 185,
                      operands: [
                        {
                          operator: "Eq",
                          left: 37, right: 46,
                          operands: [
                            {
                              operator: "Leaf", left: 37, right: 42,
                              operands: [
                                { text: "let", type: "Word", left: 37, right: 40 },
                                { text: "a", type: "Word", left: 41, right: 42 }
                              ]
                            },
                            { text: "=", type: "Punctuation", left: 43, right: 44 },
                            {
                              operator: "Leaf", left: 45, right: 46,
                              operands: [
                                { text: "0", type: "Number", left: 45, right: 46 }
                              ]
                            },
                          ]
                        },
                        { text: ";", type: "Punctuation", left: 46, right: 47 },
                        {
                          operator: "Eq",
                          left: 59, right: 68,
                          operands: [
                            {
                              operator: "Leaf", left: 59, right: 64,
                              operands: [
                                { text: "let", type: "Word", left: 59, right: 62 },
                                { text: "b", type: "Word", left: 63, right: 64 }
                              ]
                            },
                            { text: "=", type: "Punctuation", left: 65, right: 66 },
                            {
                              operator: "Leaf", left: 67, right: 68,
                              operands: [
                                { text: "1", type: "Number", left: 67, right: 68 }
                              ]
                            }
                          ]
                        },
                        { text: ";", type: "Punctuation", left: 68, right: 69 },
                        {
                          operator: "Curly",
                          left: 80, right: 172,
                          operands: [
                            {
                              operator: "Paren",
                              left: 80, right: 93,
                              operands: [
                                {
                                  operator: "Leaf",
                                  left: 80, right: 85,
                                  operands: [
                                    { text: "while", type: "Word", left: 80, right: 85 },
                                  ]
                                },
                                { text: "(", type: "Punctuation", left: 86, right: 87 },
                                {
                                  operator: "Gt",
                                  left: 87, right: 92,
                                  operands: [
                                    {
                                      operator: "Leaf",
                                      left: 87, right: 88,
                                      operands: [
                                        { text: "i", type: "Word", left: 87, right: 88 },
                                      ]
                                    },
                                    { text: ">", type: "Punctuation", left: 89, right: 90 },
                                    {
                                      operator: "Leaf",
                                      left: 91, right: 92,
                                      operands: [
                                        { text: "0", type: "Number", left: 91, right: 92 },
                                      ]
                                    },
                                  ]
                                },
                                { text: ")", type: "Punctuation", left: 92, right: 93 },
                              ]
                            },
                            { text: "{", type: "Punctuation", left: 94, right: 95 },
                            {
                              operator: "Semi",
                              left: 108, right: 160,
                              operands: [
                                {
                                  operator: "Eq",
                                  left: 108, right: 121,
                                  operands: [
                                    {
                                      operator: "Leaf",
                                      left: 108, right: 113,
                                      operands: [
                                        { text: "let", type: "Word", left: 108, right: 111 },
                                        { text: "c", type: "Word", left: 112, right: 113 },
                                      ]
                                    },
                                    { text: "=", type: "Punctuation", left: 114, right: 115 },
                                    {
                                      operator: "Plus",
                                      left: 116, right: 121,
                                      operands: [
                                        {
                                          operator: "Leaf",
                                          left: 116, right: 117,
                                          operands: [
                                            { text: "a", type: "Word", left: 116, right: 117 },
                                          ]
                                        },
                                        { text: "+", type: "Punctuation", left: 118, right: 119 },
                                        {
                                          operator: "Leaf",
                                          left: 120, right: 121,
                                          operands: [
                                            { text: "b", type: "Word", left: 120, right: 121 },
                                          ]
                                        },
                                      ]
                                    },
                                  ]
                                },
                                { text: ";", type: "Punctuation", left: 121, right: 122 },
                                {
                                  operator: "Eq",
                                  left: 135, right: 140,
                                  operands: [
                                    {
                                      operator: "Leaf",
                                      left: 135, right: 136,
                                      operands: [
                                        { text: "a", type: "Word", left: 135, right: 136 },
                                      ]
                                    },
                                    { text: "=", type: "Punctuation", left: 137, right: 138 },
                                    {
                                      operator: "Leaf",
                                      left: 139, right: 140,
                                      operands: [
                                        { text: "b", type: "Word", left: 139, right: 140 },
                                      ]
                                    },
                                  ]
                                },
                                { text: ";", type: "Punctuation", left: 140, right: 141 },
                                {
                                  operator: "Eq",
                                  left: 154, right: 159,
                                  operands: [
                                    {
                                      operator: "Leaf",
                                      left: 154, right: 155,
                                      operands: [
                                        { text: "b", type: "Word", left: 154, right: 155 },
                                      ]
                                    },
                                    { text: "=", type: "Punctuation", left: 156, right: 157 },
                                    {
                                      operator: "Leaf",
                                      left: 158, right: 159,
                                      operands: [
                                        { text: "c", type: "Word", left: 158, right: 159 },
                                      ]
                                    },
                                  ]
                                },
                                { text: ";", type: "Punctuation", left: 159, right: 160 },
                              ]
                            },
                            { text: "}", type: "Punctuation", left: 171, right: 172 },
                          ]
                        },
                        { text: ";", type: "Punctuation", left: 172, right: 172, synthetic: true },
                        {
                          operator: "Leaf", left: 183, right: 184,
                          operands: [
                            { text: "a", type: "Word", left: 183, right: 184 }
                          ]
                        },
                        { text: ";", type: "Punctuation", left: 184, right: 185 },
                      ]
                    },
                    { text: "}", type: "Punctuation", left: 194, right: 195 },
                  ]
                },
                { text: ";", type: "Punctuation", left: 195, right: 195, synthetic: true },
                {
                  operator: "Paren",
                  left: 204, right: 210,
                  operands: [
                    {
                      operator: "Leaf", left: 204, right: 207,
                      operands: [
                        { text: "fib", type: "Word", left: 204, right: 207 }
                      ]
                    },
                    { text: "(", type: "Punctuation", left: 207, right: 208 },
                    {
                      operator: "Leaf", left: 208, right: 209,
                      operands: [
                        { text: "8", type: "Number", left: 208, right: 209 }
                      ]
                    },
                    { text: ")", type: "Punctuation", left: 209, right: 210 }
                  ]
                },
                { text: ";", type: "Punctuation", left: 210, right: 211 }
              ]
            }
            """,
        )
    }

    @Test
    fun minimalClassDecl() = assertParseTree(
        input = """
            class C;
        """,
        want = """
        [
          [ "class", "C" ],
          ";"
        ]
        """,
    )

    @Test
    fun minimalClassDeclInBlock() = assertParseTree(
        input = "{ class C }",
        want = """
        [
          "{",
          [ "class", "C" ],
          "}"
        ]
        """,
    )

    @Test
    fun classDeclWithBody() = assertParseTree(
        input = """
            class C {
                x: Int;
                f();
            }
        """,
        want = """
        [
          [ "class", "C" ],
          "{",
          [
            [
              ["x"],
              ":",
              ["Int"],
            ],
            ";",
            [
              ["f"],
              "(",
              ")",
            ],
            ";"
          ],
          "}"
        ]
        """,
    )

    @Test
    fun classDeclWithComputedPropertyAndSuperTypes() = assertParseTree(
        input = """
            class Cee extends Dee<Eee>, Eff {
                get ex() { 42 };
            }
        """,
        want = """
        [
          [
            [ "class", "Cee" ],
            "extends",
            [
              ["Dee"],
              "<",
              ["Eee"],
              ">",
            ],
            ",",
            ["Eff"],
          ],
          "{",
          [
            [
              [
                ["get", "ex"],
                "(",
                ")",
              ],
              "{",
              ["42"],
              "}",
            ],
            ";",
          ],
          "}"
        ]
        """,
    )

    @Test
    fun anglesAndLt() = assertParseTree(
        input = """Foo<A, B>() < Bar<C<D>>()""",
        want = """
        [
          [
            [
              ["Foo"],
              "<",
              [
                [ "A" ],
                ",",
                [ "B" ]
              ],
              ">",
            ],
            "(",
            ")",
          ],
          "<",
          [
            [
              ["Bar"],
              "<",
              [
                ["C"],
                "<",
                ["D"],
                ">"
              ],
              ">"
            ],
            "(",
            ")",
          ],
        ]
        """,
    )

    @Test
    fun wildcardTypeParameters() = assertParseTree(
        input = "A<*>, B< * >, C<* >, D< *>, E<F<*>>",
        want = """
        [
          [
            ["A"],
            "<",
            [ "*" ],
            ">"
          ],
          ",",
          [
            ["B"],
            "<",
            [ "*" ],
            ">"
          ],
          ",",
          [
            ["C"],
            "<",
            [ "*" ],
            ">"
          ],
          ",",
          [
            ["D"],
            "<",
            [ "*" ],
            ">"
          ],
          ",",
          [
            ["E"],
            "<",
            [
              ["F"],
              "<",
              [ "*" ],
              ">",
            ],
            ">"
          ]
        ]
        """,
    )

    @Test
    fun infixWords() = assertParseTree(
        input = """
        foo(x) {
          a
        } bar(y) {
          b
        } baz {
          c
        }
        """.trimIndent(),
        want = """
        [
          [
            [
              ["foo"],
              "(",
              ["x"],
              ")",
            ],
            "{",
            ["a"],
            "}",
          ],
          "callJoin:",
          [
            [
              [
                ["bar"],
                "(",
                ["y"],
                ")",
              ],
              "{",
              ["b"],
              "}",
            ],
            "callJoin:",
            [
              ["baz"],
              "{",
              ["c"],
              "}",
            ]
          ]
        ]
        """,
    )

    @Test
    fun adjacentWords() = assertParseTree(
        input = "public class Foo extends Bar",
        want = """
        [
          "@",
          [ "public" ],
          [
            [ "class", "Foo" ],
            "extends",
            [ "Bar" ],
          ]
        ]
        """,
    )

    @Test
    fun colonAmbiguity() = assertParseTree(
        input = """
        label: while (c) {}
        label: do {}
        fn g(): T {}
        p: T = i;
        let x: T;
        let x: T = i;
        let f(): T {}
        """.trimIndent(),
        want = """
        [
          [
            ["label"],
            ":",
            [
              [
                ["while"],
                "(",
                ["c"],
                ")"
              ],
              "{",
              "}"
            ]
          ],
          ";",
          [
            ["label"],
            ":",
            [
              ["do"],
              "{",
              "}"
            ]
          ],
          ";",
          [
            [
              [
                ["fn", "g"],
                "(",
                ")"
              ],
              ":",
              ["T"],
            ],
            "{",
            "}"
          ],
          ";",
          [
            [
              ["p"],
              ":",
              [ "T" ],
            ],
            "=",
            ["i"]
          ],
          ";",
          [
            ["let", "x"],
            ":",
            ["T"],
          ],
          ";",
          [
            [
              ["let", "x"],
              ":",
              ["T"],
            ],
            "=",
            ["i"],
          ],
          ";",
          [
            [
              [
                ["let", "f"],
                "(",
                ")",
              ],
              ":",
              ["T"],
            ],
            "{",
            "}",
          ],
        ]
        """,
    )

    @Test
    fun quotedName() = assertParseTree(
        input = "let nym`foo`",
        want = """
        [
          "let",
          "nym`foo`"
        ]
        """,
    )

    @Test
    fun declarationWordsImplyLet() = assertParseTree(
        input = "var foo; const bar",
        want = """
        [
          [
            "@",
            [ "var" ],
            [ "let", "foo" ]
          ],
          ";",
          [
            "@",
            [ "const" ],
            [ "let", "bar" ]
          ]
        ]
        """,
    )

    @Test
    fun extendsCommaAmbiguity() = assertParseTree(
        input = "class C<T extends D, U> extends E, F {}",
        want = """
        [
          [
            [
              ["class", "C"],
              "<",
              [
                [
                  ["T"],
                  "extends",
                  ["D"]
                ],
                ",", // Comma does not follow extends
                ["U"]
              ],
              ">",
            ],
            "extends",
            ["E"],
            ",", // Comma does follow extends
            ["F"]
          ],
          "{",
          "}"
        ]
        """,
    )

    @Test
    fun extendsNestingInForbids() = assertParseTree(
        input = "class C<T extends D forbids E, U> extends E, F forbids G, H {}",
        want = """
        [
          [
            [
              [
                ["class", "C"],
                "<",
                [
                  [
                    [
                      ["T"],
                      "extends",
                      ["D"]
                    ],
                    "forbids",
                    ["E"],
                  ],
                  ",", // Comma does not follow extends
                  ["U"]
                ],
                ">",
              ],
              "extends",
              ["E"],
              ",", // Comma does follow extends
              ["F"]
            ],
            "forbids",
            ["G"],
            ",",
            ["H"],
          ],
          "{",
          "}"
        ]
        """,
    )

    @Test
    fun newOfParameterized() = assertParseTree(
        // In the below, the `<T>` applies to the `C`, not `new C` so that the argument to `new` is
        // a whole type.  The call is outside, so we can treat `new` as an operator that extracts
        // a factory for a type.
        input = "new C<T>()",
        want = """
        [
          [
            "new",
            [
              ["C"],
              "<",
              ["T"],
              ">"
            ]
          ],
          "(",
          ")"
        ]
        """,
    )

    @Test
    fun decorationUngrouped() = assertParseTree(
        input = "@A() fn {}",
        want = """
        [
          "@",
          [
            ["A"],
            "(",
            ")"
          ],
          [
            ["fn"],
            "{",
            "}"
          ]
        ]
        """,
    )

    @Test
    fun decoratedGrouped() = assertParseTree(
        input = "@A() (fn {})",
        want = """
        [
          "@",
          [
            ["A"],
            "(", ")"
          ],
          [
            "(",
            [
              ["fn"],
              "{", "}"
            ],
            ")"
          ]
        ]
        """,
    )

    @Test
    fun keyValuePairs() = assertParseTree(
        input = "({ a: b, c: d })",
        want = """
        [
          "(",
          [
            "{",
            [
              {
                operator: "LowColon",
                operands: [
                  [ "a" ],
                  ":",
                  [ "b" ]
                ]
              },
              ",",
              {
                operator: "HighColon",
                operands: [
                  [ "c" ],
                  ":",
                  [ "d" ]
                ]
              },
            ],
            "}",
          ],
          ")",
        ]
        """,
    )

    @Test
    fun asiBeforeParentheses() = assertParseTree(
        input = """
        foo: {} // ASI wanted here
        (x)
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            ":",
            [ "{", "}" ]
          ],
          ";",
          [
            "(",
            [ "x" ],
            ")"
          ]
        ]
        """,
    )

    @Test
    fun asiBeforeSquares() = assertParseTree(
        input = """
        foo: {} // ASI wanted here
        [x]
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            ":",
            [ "{", "}" ]
          ],
          ";",
          [
            "[",
            [ "x" ],
            "]"
          ]
        ]
        """,
    )

    @Test
    fun asiBeforeAmbiguousInfixPrefixOperator() = assertParseTree(
        input = """
        foo: {} // ASI wanted here
        + x
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            ":",
            [ "{", "}" ]
          ],
          ";",
          [
            "+",
            [ "x" ],
          ]
        ]
        """,
    )

    @Test
    fun asiBeforePrefixOperator() = assertParseTree(
        input = """
        foo: {} // ASI wanted here
        ~ x
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            ":",
            [ "{", "}" ]
          ],
          ";",
          [
            "~",
            [ "x" ],
          ]
        ]
        """,
    )

    @Test
    fun noAsiBeforeOnlyInfixOperator() = assertParseTree(
        input = """
        foo {}
        , x
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            "{", "}"
          ],
          ",",
          [ "x" ],
        ]
        """,
    )

    @Test
    fun asiBeforePrefixAndPostfixOperator() = assertParseTree(
        input = """
        foo {} // ASI wanted here
        ++
        x
        """.trimIndent(),
        want = """
        [
          [
            ["foo"],
            "{", "}"
          ],
          ";",
          [
            "++",
            [ "x" ]
          ],
        ]
        """,
    )

    @Test
    fun colonInColon() = assertParseTree(
        input = "let f: fn (): Int",
        want = """
        |[
        |  ["let", "f"],
        |  ":",
        |  [
        |    [
        |      ["fn"],
        |      "(", ")"
        |    ],
        |    ":",
        |    ["Int"]
        |  ]
        |]
        """.trimMargin(),
    )

    @Test
    fun bracketsAreBrackets() = assertParseTree(
        // f() may return a generic function so the below is a function call that returns a function
        // that we supply explicit type actuals to, and then call.
        input = """f()<O, P>()""",
        want = """
        |[
        |  [
        |    [
        |      ["f"],
        |      "(",
        |      ")",
        |    ],
        |    "<",
        |    [
        |      ["O"],
        |      ",",
        |      ["P"],
        |    ],
        |    ">",
        |  ],
        |  "(",
        |  ")",
        |]
        """.trimMargin(),
    )

    @Test
    fun dotChains() = assertParseTree(
        input = """
            |foo.bar().boo()
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      [
            |        ["foo"],
            |        ".",
            |        ["bar"],
            |      ],
            |      "(",
            |      ")",
            |    ],
            |    ".",
            |    ["boo"],
            |  ],
            |  "(",
            |  ")"
            |]
        """.trimMargin(),
    )

    @Test
    fun dotChainsWithLambdas() = assertParseTree(
        input = """
            |foo.bar().baz { 42 }.boo()
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      [
            |        [
            |          [
            |            ["foo"],
            |            ".",
            |            ["bar"],
            |          ],
            |          "(",
            |          ")",
            |        ],
            |        ".",
            |        ["baz"],
            |      ],
            |      "{",
            |      ["42"],
            |      "}",
            |    ],
            |    ".",
            |    ["boo"],
            |  ],
            |  "(",
            |  ")"
            |]
        """.trimMargin(),
    )

    @Test
    fun explicitActualsForMethod() = assertParseTree(
        input = "foo.bar<C, D>()",
        want = """
            |[
            |  [
            |    [
            |      [ "foo" ],
            |      ".",
            |      [ "bar" ],
            |    ],
            |    "<",
            |    [
            |      [ "C" ],
            |      ",",
            |      [ "D" ],
            |    ],
            |    ">"
            |  ],
            |  "(",
            |  ")",
            |]
        """.trimMargin(),
    )

    @Test
    fun newWithParens() = assertParseTree(
        input = """
            |// Type expressions can be parenthesized because expressions can be parenthesized.
            |new (Type)(argument);
            |
            |// Is `x` a type expression or an argument?
            |// In the view that `new` is an operator that derives a factory for a type, then `x`
            |// is a type.  This is consistent with JS/TS where `new (Array)` is a valid expression,
            |// which because JS doesn't require an argument list to a constructor expression,
            |// evaluates to a newly allocated *Array*.
            |new(x);
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      "new",
            |      [
            |        "(",
            |        // Type expressions can be parenthesized
            |        [ "Type" ],
            |        ")",
            |      ]
            |    ],
            |    "(",
            |    [ "argument" ],
            |    ")",
            |  ],
            |  ";",
            |  [
            |    "new",
            |    [
            |      "(",
            |      [ "x" ],
            |      ")",
            |    ]
            |  ],
            |  ";",
            |]
        """.trimMargin(),
    )

    @Test
    fun orelseAssociativity() = assertParseTree(
        input = "f() orelse g() orelse h()",
        // `orelse` should probably nest the way `try catch` do, even though there's no semantic
        // difference with a different nesting.
        // In an exception-using language, you might write
        //     try { f() } catch { try { g() } catch { h() } }
        // to express the idea that we try `f()`, and if it fails, proceed with `g()`, and if that also fails, `h()`.
        // So
        //     f() orelse g() orelse h()
        // should probably nest
        //     f() orelse (g() orelse h())
        want = """
            |[
            |  [ ["f"], "(", ")" ],
            |  "orelse",
            |  [
            |    [ ["g"], "(", ")" ],
            |    "orelse",
            |    [ ["h"], "(", ")" ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun whenBlock() = assertParseTree(
        input = """
            |when (x) {
            |  pattern0, pattern1                  -> consequent1();
            |  pattern2, pattern3 given condition1 -> consequent2();
            |  pattern4                            -> consequent3();
            |  given condition2                    -> consequent4();
            |  else                                -> consequent5();
            |}
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [ "when" ],
            |    "(",
            |    [ "x" ],
            |    ")"
            |  ],
            |  "{",
            |  [
            |    [
            |      [
            |        [ "pattern0" ],
            |        ",",
            |        [ "pattern1" ],
            |      ],
            |      "->",
            |      [
            |        [ "consequent1" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [
            |        [
            |          [ "pattern2" ],
            |          ",",
            |          [ "pattern3" ],
            |        ],
            |        "given",
            |        [ "condition1" ],
            |      ],
            |      "->",
            |      [
            |        [ "consequent2" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [ "pattern4" ],
            |      "->",
            |      [
            |        [ "consequent3" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [
            |        "given",
            |        [ "condition2" ],
            |      ],
            |      "->",
            |      [
            |        [ "consequent4" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [ "else" ],
            |      "->",
            |      [
            |        [ "consequent5" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |  ],
            |  "}"
            |]
        """.trimMargin(),
    )

    @Test
    fun whenBlockArgless() = givenBlock(key = "when", pre = """[ "when" ],""", post = "")

    @Test
    fun givenBlock() = givenBlock(key = "given", pre = """"given", [""", post = "]")

    /** Consider two different options above for a feature we don't support yet. */
    fun givenBlock(key: String, pre: String, post: String) = assertParseTree(
        input = """
            |$key {
            |  condition1 -> consequent1();
            |  condition2 -> consequent2();
            |  else       -> consequent3();
            |}
        """.trimMargin(),
        want = """
            |[
            |  $pre
            |  "{",
            |  [
            |    [
            |      [ "condition1" ],
            |      "->",
            |      [
            |        [ "consequent1" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [ "condition2" ],
            |      "->",
            |      [
            |        [ "consequent2" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [ "else" ],
            |      "->",
            |      [
            |        [ "consequent3" ],
            |        "(",
            |        ")",
            |      ],
            |    ],
            |    ";",
            |  ],
            |  "}",
            |  $post
            |]
        """.trimMargin(),
    )

    @Test
    fun whenCurlyIs() = assertParseTree(
        input = """
            |when (x) {
            |  apple -> do {
            |    banana
            |  }
            |  is Cherry -> durian;
            |}
        """.trimMargin(),
        want = """
        [
            [
                [ "when" ],
                "(",
                [ "x" ],
                ")"
            ],
            "{",
            [
                [
                    [ "apple" ],
                    "->",
                    [
                        [ "do" ],
                        "{",
                        [ "banana" ],
                        "}"
                    ],
                ],
                ";",
                [
                    [
                        "is",
                        [ "Cherry" ]
                    ],
                    "->",
                    [ "durian" ],
                ],
                ";",
            ],
            "}"
        ]
        """.trimIndent(),
    )

    @Test
    fun angleBracketConfusion() = assertParseTree(
        input = "or(a < 2, a > 0)",
        want = """
            |[
            |  [ "or" ],
            |  "(",
            |  [
            |    ["a"],
            |    "<",
            |    [
            |      ["2"],
            |      ",",
            |      ["a"],
            |    ],
            |    ">",
            |  ],
            |  ["0"], // This is kind of hanging off the end here.
            |  ")"
            |]
        """.trimMargin(),
    )

    @Test
    fun postfixBang() = assertParseTree(
        input = "iMightBeNull !",
        want = """
            |[
            |  [ "iMightBeNull" ],
            |  "!",
            |]
        """.trimMargin(),
    )

    @Test
    fun someLogicWithEmbeddedComment() = assertParseTree(
        input = """
            |return yearDelta -
            |   // If the end month/day is before the start's then we
            |   // don't have a full year.
            |   (monthDelta < 0 || monthDelta == 0 && end.day < start.day)
        """.trimMargin(),
        want = """
            |[
            |  "return",
            |  [
            |    [ "yearDelta" ],
            |    "-",
            |    [
            |      "(",
            |      [
            |        [
            |          [ "monthDelta" ],
            |          "<",
            |          [ "0" ],
            |        ],
            |        "||",
            |        [
            |          [
            |            [ "monthDelta" ],
            |            "==",
            |            [ "0" ],
            |          ],
            |          "&&",
            |          [
            |            [
            |              [ "end" ],
            |              ".",
            |              [ "day" ],
            |            ],
            |            "<",
            |            [
            |              [ "start" ],
            |              ".",
            |              [ "day" ],
            |            ],
            |          ],
            |        ],
            |      ],
            |      ")",
            |    ],
            |  ],
            |]
        """.trimMargin(),
    )

    @Test
    fun colonLeftOfAnd() = assertParseTree(
        // Return type is an intersection
        input = """
            |fn (Int): A & B
        """.trimMargin(),
        want = """
            |[
            |  [
            |    ["fn"],
            |    "(",
            |    ["Int"],
            |    ")",
            |  ],
            |  ":",
            |  [
            |    ["A"],
            |    "&",
            |    ["B"],
            |  ],
            |]
        """.trimMargin(),
    )

    @Test
    fun colonLeftOfAndNoParens() = assertParseTree(
        // Return type is an intersection
        input = """
            |fn: A & B
        """.trimMargin(),
        want = """
            |[
            |  ["fn"],
            |  ":",
            |  [
            |    ["A"],
            |    "&",
            |    ["B"],
            |  ],
            |]
        """.trimMargin(),
    )

    @Test
    fun selectiveColonCases() = assertParseTree(
        input = """
            |mixin("before"):
            |  before;
            |mixin("body"):
            |  body;
            |  mixin("not");
            |  x = BEFORE;
        """.trimMargin(),
        want = """
            |[
            |  [[["mixin"], "(", ["(", ["\"", ["before"], "\""], ")"], ")"], ":"],
            |  ["before"],
            |  ";",
            |
            |  [[["mixin"], "(", ["(", ["\"", ["body"], "\""], ")"], ")"], ":"],
            |  ["body"],
            |  ";",
            |
            |  [["mixin"], "(", ["(", ["\"", ["not"], "\""], ")"], ")"],
            |  ";",
            |
            |  [
            |    ["x"],
            |    "=",
            |    ["BEFORE"],
            |  ],
            |  ";",
            |]
        """.trimMargin(),
    )

    @Test
    fun letTypedOfLoop() = assertParseTree(
        input = """
            |for (let x of elements) { body }
            |for (let x: T of elements) { body }
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      ["for"],
            |      "(",
            |      [
            |        ["let", "x"],
            |        "of",
            |        ["elements"],
            |      ],
            |      ")",
            |    ],
            |    "{",
            |    ["body"],
            |    "}",
            |  ],
            |  ";",
            |  [
            |    [
            |      ["for"],
            |      "(",
            |      [
            |        [
            |          ["let", "x"],
            |          ":",
            |          ["T"],
            |        ],
            |        "of",
            |        ["elements"],
            |      ],
            |      ")",
            |    ],
            |    "{",
            |    ["body"],
            |    "}",
            |  ],
            |]
        """.trimMargin(),
    )

    @Test
    fun ofDecoratedLet() = assertParseTree(
        input = """
            |@decoration let name: Type = expr of otherExpr
        """.trimMargin(),
        want = """
            |[
            |  [
            |    "@",
            |    ["decoration"],
            |    [
            |      [
            |        ["let", "name"],
            |        ":",
            |        ["Type"],
            |      ],
            |      "=",
            |      ["expr"],
            |    ],
            |  ],
            |  "of",
            |  ["otherExpr"],
            |]
        """.trimMargin(),
    )

    @Test
    fun keywordAfterDot() = assertParseTree(
        input = """
            |a.is
        """.trimMargin(),
        want = """
            |[
            |  [ "a" ],
            |  ".",
            |  [ "is" ],
            |]
        """.trimMargin(),
    )

    @Test
    fun caseInArrow() = assertParseTree(
        input = """
            |match {
            |  case a -> f(1);
            |  case b(), case c() -> ...;
            |}
        """.trimMargin(),
        want = """
            |[
            |  ["match"],
            |  "{",
            |  [
            |    [
            |      [
            |        "case",
            |        ["a"],
            |      ],
            |      "->",
            |      [
            |        ["f"],
            |        "(",
            |        ["1"],
            |        ")",
            |      ],
            |    ],
            |    ";",
            |    [
            |      [
            |        [
            |          "case",
            |          [
            |            ["b"],
            |            "(", ")",
            |          ],
            |        ],
            |        ",",
            |        [
            |          "case",
            |          [
            |            ["c"],
            |            "(", ")",
            |          ],
            |        ],
            |      ],
            |      "->",
            |      ["..."],
            |    ],
            |    ";",
            |  ],
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun postfixColon() = assertParseTree(
        input = """
            |let f(): T? {}
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      [
            |        "let",
            |        "f",
            |      ],
            |      "(",
            |      ")",
            |    ],
            |    ":",
            |    [
            |      [ "T" ],
            |      "?",
            |    ]
            |  ],
            |  "{",
            |  "}",
            |]
        """.trimMargin(),
    )

    @Test
    fun throws2() = assertParseTree(
        input = """
            |let f(): Ok? throws Oops | Whoopsy { null }
        """.trimMargin(),
        want = """
            |[
            |  [
            |    [
            |      [
            |        "let",
            |        "f",
            |      ],
            |      "(",
            |      ")",
            |    ],
            |    ":",
            |    [
            |      [
            |        [ "Ok" ],
            |        "?",
            |      ],
            |      "throws",
            |      [ "Oops" ],
            |      "|",
            |      [ "Whoopsy" ],
            |    ]
            |  ],
            |  "{",
            |  [ "null" ],
            |  "}",
            |]
        """.trimMargin(),
    )
}

const val INTERP_EMBED = "\${"
