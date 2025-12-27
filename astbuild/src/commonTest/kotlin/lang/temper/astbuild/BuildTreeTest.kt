package lang.temper.astbuild

import lang.temper.ast.LeftParenthesis
import lang.temper.ast.RightParenthesis
import lang.temper.ast.flatten
import lang.temper.common.ListBackedLogSink
import lang.temper.common.TestDocumentContext
import lang.temper.common.affectedByIssue11
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.kotlinBackend
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.cst.CstComment
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.Lexer
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.parser.parse
import kotlin.test.Ignore
import kotlin.test.Test

class BuildTreeTest {
    private fun assertAst(
        wantJson: String,
        input: String,
        startProduction: String = "Root",
        lang: LanguageConfig = StandaloneLanguageConfig,
    ) {
        val logSink = ListBackedLogSink()
        val lexer = Lexer(testCodeLocation, logSink, input, lang = lang)
        val comments = mutableListOf<CstComment>()
        val root = parse(lexer, logSink, comments)

        val cstParts = flatten(root)

        val documentContext = TestDocumentContext(testModuleName)

        val storedCommentTokens = StoredCommentTokens(comments.toList())

        var passed = false
        try {
            val ast = buildTree(
                cstParts = cstParts.toList(),
                storedCommentTokens = storedCommentTokens,
                logSink = logSink,
                startProduction = startProduction,
                documentContext = documentContext,
            )
            val toCompare = logSink.wrapErrorsAround(ast)

            assertStructure(
                expectedJson = wantJson,
                input = toCompare,
            )
            passed = true
        } finally {
            if (!passed) {
                console.group("cstParts") {
                    var indent = 0
                    cstParts.forEachIndexed { i, p ->
                        if (p is RightParenthesis) {
                            indent -= 1
                        }
                        console.log("\t$i\t${"  ".repeat(indent)}$p")
                        if (p is LeftParenthesis) {
                            indent += 1
                        }
                    }
                }
            }
        }
    }

    @Test
    fun annotatedFn() {
        assertAst(
            """
            [ "Call", [
                [ "RightName", "@" ],
                [ "Call", [
                    [ "RightName", ".." ],
                    [ "RightName", "A" ],
                    [ "RightName", "T" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "fn" ],
                    [ "Value", "\\word: Symbol" ],
                    [ "LeftName", "f" ],
                    [ "RightName", "a" ],
                    [ "Fun", [ [ "Block", [] ] ] ]
                  ]
                ]
              ]
            ]
            """,
            "@(A..T) fn f(a) {}",
        )
    }

    @Test
    fun simpleValue() {
        assertAst(
            input = "123",
            wantJson = """
            |[ "Value", [ 123, "Int32" ] ]
            """.trimMargin(),
        )
    }

    @Test
    fun trailingSemi() {
        assertAst(
            input = "123; 456;", // Should be equivalent to "123; 456; void"
            wantJson = """
            |[ "Block", [
            |  [ "Value", [ 123, "Int32" ] ],
            |  [ "Value", [ 456, "Int32" ] ],
            |  [ "Value", "void: Void" ],
            |]]
            """.trimMargin(),
        )
    }

    @Test
    fun trailingSemiBlock() {
        assertAst(
            // Trailing semi after last block.
            input = """
            |do { 123 }
            |do { 456 };
            |do { 789 };
            """.trimMargin(),
            wantJson = """
            |[ "Block", [
            |  [ "Call", [ [ "RightName", "do" ], [ "Fun", [ [ "Block", [
            |    [ "Value", [ 123, "Int32" ] ],
            |  ] ] ] ] ] ],
            |  [ "Call", [ [ "RightName", "do" ], [ "Fun", [ [ "Block", [
            |    [ "Value", [ 456, "Int32" ] ],
            |  ] ] ] ] ] ],
            |  [ "Call", [ [ "RightName", "do" ], [ "Fun", [ [ "Block", [
            |    [ "Value", [ 789, "Int32" ] ],
            |  ] ] ] ] ] ],
            |  [ "Value", "void: Void" ],
            |]]
            """.trimMargin(),
        )
    }

    @Test
    fun infixExprStmt() {
        assertAst(
            """
            [ "Call", [
                [ "RightName",  "+" ],
                [ "Value", [ 1, "Int32" ] ],
                [ "Value", [ 2, "Int32" ] ]
              ]
            ]
            """,
            "(1 + 2)",
        )
    }

    @Test
    fun infixExpr() {
        assertAst(
            """
            [ "Call", [
                [ "RightName",  "+" ],
                [ "Value", [ 1, "Int32" ] ],
                [ "Value", [ 2, "Int32" ] ]
              ]
            ]
            """,
            "(1 + 2)",
            startProduction = "Expr",
        )
    }

    @Test
    fun infixExprPositions() {
        assertAst(
            """
            {
              type: "Call",
              left: 1,
              right: 6, // Exclusive
              children: [
                {
                  type: "RightName",
                  left: 3, right: 4,
                  content: "+"
                },
                {
                  type: "Value",
                  left: 1, right: 2,
                  content: {
                    stateVector: 1,
                    typeTag: "Int32"
                  }
                },
                {
                  type: "Value",
                  left: 5, right: 6,
                  content: {
                    stateVector: 2,
                    typeTag: "Int32"
                  }
                }
              ]
            }
            """,
            input = "(1 + 2)",
            //       012345678
        )
    }

    @Test
    fun prefixExpr() {
        assertAst(
            input = "++x",
            startProduction = "Expr",
            wantJson = """
            [ "Call", [
                [ "RightName", "++" ],
                [ "RightName", "x" ],
              ]
            ]
            """,
        )
    }

    @Test
    fun noPrefixInfixAmbiguityExpr() {
        assertAst(
            input = "x - -y",
            startProduction = "Expr",
            wantJson = """
            [ "Call", [
                [ "RightName", "-" ],
                [ "RightName", "x" ],
                [ "Call", [
                    [ "RightName", "-" ],
                    [ "RightName", "y" ]
                  ]
                ]
              ]
            ]
            """,
        )
    }

    @Test
    fun mixedPrecedenceInfix() = assertAst(
        input = "x = a + b * c",
        wantJson = """
        [ "Call", [
            [ "RightName", "=" ],
            [ "RightName", "x" ],
            [ "Call", [
                [ "RightName", "+" ],
                [ "RightName", "a" ],
                [ "Call", [
                    [ "RightName", "*" ],
                    [ "RightName", "b" ],
                    [ "RightName", "c" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun bracketsContainingThings() = assertAst(
        input = "do { foo(1); bar(); baz(x,++y); }",
        wantJson = """
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "foo" ],
                        [ "Value", [ 1, "Int32" ] ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "bar" ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "baz" ],
                        [ "RightName", "x" ],
                        [ "Call", [
                            [ "RightName", "++" ],
                            [ "RightName", "y" ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                ] ] ] ]
            ] ]
        """,
    )

    @Test
    fun blockOfTopLevels() = assertAst(
        input = """
        do {
            let x = 0;
            while(bar()) { ++x; }
            return x;
        }
        """,
        wantJson = """
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Decl", [
                        [ "LeftName", "x" ],
                        [ "Value", [ "init", "Symbol" ] ],
                        [ "Value", [ 0, "Int32" ] ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "while" ],
                        [ "Call", [
                            [ "RightName", "bar" ]
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "++" ],
                                    [ "RightName", "x" ]
                                  ]
                                ],
                                [ "Value", "void: Void" ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                ] ]
            ] ] ] ]
        """,
    )

    @Test
    fun returnOfList() = assertAst(
        input = "return [a, b, c]",
        wantJson = """
        [ "Call", [
            [ "RightName", "return" ],
            [ "Call", [
                [ "Value", "list: Function" ],
                [ "RightName", "a" ],
                [ "RightName", "b" ],
                [ "RightName", "c" ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun complexIf() = assertAst(
        input = "if (a) { x++; } else if (b) { y++; } else { z--; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "if" ],
            [ "RightName", "a" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "Value", "postfixApply: Function" ],
                        [ "RightName", "++" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "else_if", "Symbol" ] ],
            [ "RightName", "b" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "Value", "postfixApply: Function" ],
                        [ "RightName", "++" ],
                        [ "RightName", "y" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "else", "Symbol" ] ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "Value", "postfixApply: Function" ],
                        [ "RightName", "--" ],
                        [ "RightName", "z" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun whenBlock() = assertAst(
        // At top level, we should be able to treat `someValue` as a value even without `${someValue}` escaping.
        // In some nested setting like `[${someValue}]`, we might need to use escape holes for values instead of
        // captures, but even for object literals, we might not *need* them. For example, we could say
        // `{ blah: someValue }`, and know that `someValue` has to be a value because for renaming, we intend to
        // use `as` rather than `:`.
        input = """
        |let x = when (thing) {
        |  is SomeType -> thing.blah;
        |  someValue, other,
        |    is SomeOtherType -> 2;
        |  else -> 3;
        |}
        """.trimMargin(),
        wantJson = """
        [ "Decl", [
            [ "LeftName", "x" ],
            [ "Value", [ "init", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "when" ],
                [ "RightName", "thing" ],
                [ "Fun", [
                    [ "Block", [
                        [ "Value", "\\case_is: Symbol" ],
                        [ "RightName", "SomeType" ],
                        [ "Call", [
                            [ "RightName", "." ],
                            [ "RightName", "thing" ],
                            [ "Value", "\\blah: Symbol" ]
                        ] ],
                        [ "Value", "\\case: Symbol" ],
                        [ "RightName", "someValue" ],
                        [ "Value", "\\case: Symbol" ],
                        [ "RightName", "other" ],
                        [ "Value", "\\case_is: Symbol" ],
                        [ "RightName", "SomeOtherType" ],
                        [ "Value", "2: Int32" ],
                        [ "Value", "\\default: Symbol" ],
                        [ "Value", "3: Int32" ],
                        [ "Value", "void: Void" ],
                    ] ]
                ] ]
            ] ]
        ] ]
        """.trimIndent(),
    )

    @Test
    fun postfixQuestionMarkForNullableType() = assertAst(
        input = """
            |let f(x: T?): T? {
            |  let y: T? = x;
            |  y
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "let" ],
            |    [ "Value", "\\word: Symbol" ],
            |    [ "LeftName", "f" ],
            |    [ "Block", [
            |        [ "Value", "\\_complexArg_: Symbol" ],
            |        [ "RightName", "x" ],
            |        [ "Value", "\\type: Symbol" ],
            |        [ "Call", [
            |            [ "RightName", "?" ],
            |            [ "RightName", "T" ],
            |          ]
            |        ],
            |      ],
            |    ],
            |    [ "Value", "\\outType: Symbol" ],
            |    [ "Call", [
            |        [ "RightName", "?" ],
            |        [ "RightName", "T" ],
            |      ]
            |    ],
            |    [ "Fun", [
            |        [ "Block", [
            |            [ "Decl", [
            |                [ "LeftName", "y" ],
            |                [ "Value", "\\type: Symbol" ],
            |                [ "Call", [
            |                    [ "RightName", "?" ],
            |                    [ "RightName", "T" ],
            |                  ]
            |                ],
            |                [ "Value", "\\init: Symbol" ],
            |                [ "RightName", "x" ],
            |              ]
            |            ],
            |            [ "RightName", "y" ],
            |          ],
            |        ],
            |      ],
            |    ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun forLoopAllParts() = assertAst(
        input = "for (x = 0; x < 10; ++x) { f(x); }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "__flowInit", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "=" ],
                [ "RightName", "x" ],
                [ "Value", [ 0, "Int32" ] ]
              ]
            ],
            [ "Value", [ "cond", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "<" ],
                [ "RightName", "x" ],
                [ "Value", [ 10, "Int32" ] ]
              ]
            ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "++" ],
                [ "RightName", "x" ],
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "f" ],
                        [ "RightName", "x" ],
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun errorDoesNotEscapeDo() = assertAst(
        input = "do { ! /* MISSING OPERAND */ } while (cond)",
        //       01234567890123456789012345678901234567890123456789012
        //                 1         2         3         4         5
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "Value", "error: Function" ],
                        [ "Call", [
                            [ "Value", "list: Function" ],
                            [ "Value", [ "!", "String" ] ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "while", "Symbol" ] ],
            [ "RightName", "cond" ]
          ],
          {
            errors: [
              {
                template: "TooFewOperands",
                values: [ "Bang", 1, 0 ],
                left: 5,
                right: 6,
                loc: "test/test.temper"
              },
              {
                template: "Unparsable",
                values: [ "TopLevel" ],
                left: 5,
                right: 6,
                loc: "test/test.temper"
              }
            ]
          }
        ]
        """,
    )

    @Test
    fun infixOperatorMissingRightOperand() = assertAst(
        input = "x += (y * ) + 3",
        //       01234567890123456
        //                 1
        wantJson = """
        [ "Call", [
            [ "RightName", "+=" ],
            [ "RightName", "x" ],
            [ "Call", [
                [ "RightName", "+" ],
                [ "Call", [
                    [ "Value", "error: Function" ],
                    [ "Call", [
                        [ "Value", "list: Function" ],
                        [ "Value", [ "`(Star`", "String" ] ],
                        [ "Value", [ "`(Leaf`", "String" ] ],
                        [ "Value", [ "y", "String" ] ],
                        [ "Value", [ "`Leaf)`", "String" ] ],
                        [ "Value", [ "*", "String" ] ],
                        [ "Value", [ "`Star)`", "String" ] ],
                      ]
                    ]
                  ]
                ],
                [ "Value", [ 3, "Int32" ] ]
              ]
            ]
          ],
          {
            errors: [
              {
                template: "TooFewOperands",
                values: [ "Star", 2, 1 ],
                left: 6,
                right: 9,
                loc: "test/test.temper"
              },
              {
                template: "Unparsable",
                values: [ "Expression" ],
                left: 6,
                right: 9,
                loc: "test/test.temper"
              }
            ]
          }
        ]
        """,
    )

    @Test
    fun errorDoesNotEscapeFunctionBody() = assertAst(
        input = "function f(x) { return x + ; }",
        //       012345678901234567890123456789
        //                 1         2
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "RightName", "x" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "Call", [
                            [ "Value", "error: Function" ],
                            [ "Call", [
                                [ "Value", "list: Function" ],
                                [ "Value", [ "`(Plus`", "String" ] ],
                                [ "Value", [ "`(Leaf`", "String" ] ],
                                [ "Value", [ "x", "String" ] ],
                                [ "Value", [ "`Leaf)`", "String" ] ],
                                [ "Value", [ "+", "String" ] ],
                                [ "Value", [ "`Plus)`", "String" ] ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ],
          {
            errors: [
              {
                level: "error",
                template: "TooFewOperands",
                values: [ "Plus", 2, 1 ],
                left: 23,
                right: 26,
                loc: "test/test.temper"
              },
              {
                level: "error",
                template: "Unparsable",
                values: [ "Argument" ],
                left: 23,
                right: 26,
                loc: "test/test.temper"
              },
            ]
          }
        ]
        """,
    )

    @Test
    fun errorDoesNotEscapeActualArgument() = assertAst(
        input = "f(x, y +, z)",
        //       01234567890123
        //                 1
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "RightName", "x" ],
            [ "Call", [
                [ "Value", "error: Function" ],
                [ "Call", [
                    [ "Value", "list: Function" ],
                    [ "Value", [ "`(Plus`", "String" ] ],
                    [ "Value", [ "`(Leaf`", "String" ] ],
                    [ "Value", [ "y", "String" ] ],
                    [ "Value", [ "`Leaf)`", "String" ] ],
                    [ "Value", [ "+", "String" ] ],
                    [ "Value", [ "`Plus)`", "String" ] ],
                  ]
                ]
              ]
            ],
            [ "RightName", "z" ],
          ],
          {
            errors: [
              {
                template: "TooFewOperands",
                values: [ "Plus", 2, 1 ],
                left: 5,
                right: 8,
                loc: "test/test.temper"
              },
              {
                template: "Unparsable",
                values: [ "Argument" ],
                left: 5,
                right: 8,
                loc: "test/test.temper"
              }
            ]
          }
        ]
        """,
    )

    @Test
    fun statementLevelErrorStopsAtSemi() = assertAst(
        input = "do { ++; f(); }",
        //       012345678901234
        //                 1
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [ [ "Block", [
                [ "Call", [
                    [ "Value", "error: Function" ],
                    [ "Call", [
                        [ "Value", "list: Function" ],
                        [ "Value", ["++", "String"] ],
                      ]
                    ]
                  ]
                ],
                [ "Call", [
                    [ "RightName", "f" ]
                  ]
                ],
                [ "Value", "void: Void" ],
              ] ] ] ]
          ],
          {
            errors: [
              {
                template: "TooFewOperands",
                values: [ "PreIncr", 1, 0 ],
                left: 5,
                right: 7,
                loc: "test/test.temper"
              },
              {
                template: "Unparsable",
                values: [ "TopLevel" ],
                left: 5,
                right: 7,
                loc: "test/test.temper"
              }
            ]
          }
        ]
        """,
    )

    @Test
    fun declarationNoFluff() = assertAst(
        input = "let x;",
        wantJson = """
            [ "Block", [
                [ "Decl", [
                    [ "LeftName", "x" ]
                  ]
                ],
                [ "Value", "void: Void" ],
            ] ]
        """,
    )

    @Test
    fun declarationWithInitializer() = assertAst(
        input = "let x = 1",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Value", [ 1, "Int32" ] ]
              ]
            ]
        """,
    )

    @Test
    fun declarationWithType() = assertAst(
        input = "let x : number",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ]
              ]
            ]
        """,
    )

    @Test
    fun declarationWithBellsAndWhistles() = assertAst(
        input = "let x : number = 1",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Value", [ 1, "Int32" ] ]
              ]
            ]
        """,
    )

    @Test
    fun multiDeclaration() = assertAst(
        input = "let x : number, y = 1;",
        wantJson = """
        [ "Block", [
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ]
              ]
            ],
            [ "Decl", [
                [ "LeftName", "y" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Value", [ 1, "Int32" ] ]
              ]
            ],
            [ "Value", "void: Void" ],
          ]
        ]
        """,
    )

    @Test
    fun multiDeclarationX3() = assertAst(
        input = "let x, y: T, z;",
        wantJson = """
        [ "Block", [
            [ "Decl", [
                [ "LeftName", "x" ],
              ]
            ],
            [ "Decl", [
                [ "LeftName", "y" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "T" ]
              ]
            ],
            [ "Decl", [
                [ "LeftName", "z" ],
              ]
            ],
            [ "Value", "void: Void" ],
          ]
        ]
        """,
    )

    @Test
    fun complexTypeInDeclaration() = assertAst(
        input = "let x : T<A, B> | false = a < b && new T()",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "Call", [
                    [ "RightName", "|" ],
                    [ "Call", [
                        [ "Value", "nym`<>`: Function" ],
                        [ "RightName", "T" ],
                        [ "RightName", "A" ],
                        [ "RightName", "B" ]
                      ]
                    ],
                    [ "RightName", "false" ]
                  ]
                ],
                [ "Value", [ "init", "Symbol" ] ],
                // a < b && new T()
                [ "Call", [
                    [ "RightName", "&&" ],
                    [ "Call", [
                        [ "RightName", "<" ],
                        [ "RightName", "a" ],
                        [ "RightName", "b" ],
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "new" ],
                        [ "RightName", "T" ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
        """,
    )

    @Test
    fun constructorExpressionOfParameterizedType() = assertAst(
        input = "x = new T<u>(a < b, (c > d))",
        wantJson = """
        [ "Call", [
            [ "RightName", "=" ],
            [ "RightName", "x" ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Call", [
                    [ "Value", "nym`<>`: Function" ],
                    [ "RightName", "T" ],
                    [ "RightName", "u" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "<" ],
                    [ "RightName", "a" ],
                    [ "RightName", "b" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", ">" ],
                    [ "RightName", "c" ],
                    [ "RightName", "d" ],
                  ]
                ],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun declarationInForHeader() = assertAst(
        input = "for (var i = 0; cond; incr) { body }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "__flowInit", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "var" ],
                [ "Decl", [
                    [ "LeftName", "i" ],
                    [ "Value", [ "init", "Symbol" ] ],
                    [ "Value", [ 0, "Int32" ] ]
                  ]
                ]
              ]
            ],
            [ "Value", [ "cond", "Symbol" ] ],
            [ "RightName", "cond" ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "RightName", "incr" ],
            [ "Fun", [
                [ "Block", [
                    [ "RightName", "body" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun multiDeclarationInForHeader() = assertAst(
        input = "for (var x : number = 1, n = 10; i < n; ++i) { body; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "__flowInit", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "var" ],
                [ "Call", [
                    [ "Value", "nym`,`: Function" ],
                    [ "Decl", [
                        [ "LeftName", "x" ],
                        [ "Value", [ "type", "Symbol" ] ],
                        [ "RightName", "number" ],
                        [ "Value", [ "init", "Symbol" ] ],
                        [ "Value", [ 1, "Int32" ] ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "n" ],
                        [ "Value", [ "init", "Symbol" ] ],
                        [ "Value", [ 10, "Int32" ] ]
                      ]
                    ],
                  ]
                ]
              ]
            ],
            [ "Value", [ "cond", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "<" ],
                [ "RightName", "i" ],
                [ "RightName", "n" ],
              ]
            ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "++" ],
                [ "RightName", "i" ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "RightName", "body" ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun commaExprInForInitializer() = assertAst(
        input = "for (x = 1, y = 10; x < y; ++x, --y) { console.log(x, y); }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "__flowInit", "Symbol" ] ],
            [ "Call", [
                [ "Value", "nym`,`: Function" ],
                [ "Call", [
                    [ "RightName", "=" ],
                    [ "RightName", "x" ],
                    [ "Value", [ 1, "Int32" ] ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "=" ],
                    [ "RightName", "y" ],
                    [ "Value", [ 10, "Int32" ] ],
                  ]
                ],
              ]
            ],
            [ "Value", [ "cond", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "<" ],
                [ "RightName", "x" ],
                [ "RightName", "y" ],
              ]
            ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "Call", [
                [ "Value", "nym`,`: Function" ],
                [ "Call", [
                    [ "RightName", "++" ],
                    [ "RightName", "x" ]
                  ]
                ],
                [ "Call", [
                    [ "RightName", "--" ],
                    [ "RightName", "y" ]
                  ]
                ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "Call", [
                            [ "RightName", "." ],
                            [ "RightName", "console" ],
                            [ "Value", "\\log: Symbol" ],
                          ]
                        ],
                        [ "RightName", "x" ],
                        [ "RightName", "y" ],
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun forWithConditionOnly() = assertAst(
        input = "for (; f();) { g(); }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "cond", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "f" ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "g" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun foreverWithDoubleSemi() = assertAst(
        input = "for (;;) { if (f()) { break; } }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "if" ],
                        [ "Call", [
                            [ "RightName", "f" ]
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "break" ]
                                  ]
                                ],
                                [ "Value", "void: Void" ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun foreverTwoSemis() = assertAst(
        input = "for (; ;) { if (f()) { break; } }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "if" ],
                        [ "Call", [
                            [ "RightName", "f" ],
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "break" ]
                                  ]
                                ],
                                [ "Value", "void: Void" ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun forIncrOnlyWithDoubleSemi() = assertAst(
        input = "for (;; f()) { break label; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "f" ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "break" ],
                        [ "Value", [ "label", "Symbol" ] ],
                        [ "RightName", "label" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun forIncrOnlyTwoSemis() = assertAst(
        input = "for (; ; f()) { break label; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "for" ],
            [ "Value", [ "incr", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "f" ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "break" ],
                        [ "Value", [ "label", "Symbol" ] ],
                        [ "RightName", "label" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun minimalFunctionDeclaration() = assertAst(
        input = "function f() { return; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun complexFormal() = assertAst(
        input = "function f(x: number = 42) { return x; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ],
                [ "Value", [ "default", "Symbol" ] ],
                [ "Value", [ 42, "Int32" ] ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun decoratedFormal() = assertAst(
        input = "let f(@Dec(0) x: T) {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "let" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "Call", [
                    [ "RightName", "Dec" ],
                    [ "Value", "0: Int32" ],
                  ]
                ],
                [ "Block", [
                    [ "Value", [ "_complexArg_", "Symbol" ] ],
                    [ "RightName", "x" ],
                    [ "Value", [ "type", "Symbol" ] ],
                    [ "RightName", "T" ],
                  ]
                ]
              ]
            ],
            [ "Fun", [
                [ "Block", [
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun formalWithSupports() = assertAst(
        input = "let f(x supports T = defaultExpr, y = other) { stuff }",
        wantJson = """
        [ "Call", [
            [ "RightName", "let" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "x" ],
                [ "Value", [ "reifies", "Symbol" ] ],
                [ "RightName", "T" ],
                [ "Value", [ "default", "Symbol" ] ],
                [ "RightName", "defaultExpr" ],
              ]
            ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "y" ],
                [ "Value", [ "default", "Symbol" ] ],
                [ "RightName", "other" ],
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "RightName", "stuff" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun returnWithExpr() = assertAst(
        input = "function f(x) { return x; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "RightName", "x" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun functionDeclarationWithReturnType() = assertAst(
        input = "fn f(x: number): number { return (x); }",
        // This becomes an application of the macro `function`.
        wantJson = """
        [ "Call", [
            [ "RightName", "fn" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ]
              ]
            ],
            [ "Value", [ "outType", "Symbol" ] ],
            [ "RightName", "number" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun functionReturnTypeNotAmbiguous() = assertAst(
        input = "function f(x): number {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "RightName", "x" ],
            // If we used the same symbol for the type of the whole as the type of x, then any last
            // formal missing a type might borrow the function's type.
            [ "Value", [ "outType", "Symbol" ] ],
            [ "RightName", "number" ],
            [ "Fun", [
                [ "Block", [] ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun throwsInSignatureType() = assertAst(
        input = "let parseInt(x: String): Int throws BadNumericString | ExceedsPrecisionLimits {}",
        // This becomes an application of the macro `function`.
        wantJson = """
        [ "Call", [
            [ "RightName", "let" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "parseInt" ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "String" ]
              ]
            ],
            [ "Value", [ "outType", "Symbol" ] ],
            [ "Call", [
                 [ "RightName", "throws" ],
                 [ "RightName", "Int" ],
                 [ "RightName", "BadNumericString" ],
                 [ "RightName", "ExceedsPrecisionLimits" ],
              ]
            ],
            [ "Fun", [
                [ "Block", [
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun notACallWithReturnType() = assertAst(
        // A call must have at least one of
        // - A parenthesized block of arguments
        // - A template string
        // - A trailing block
        // so this is not a call to `f`.
        // This is an important distinction to make for how the disAmbiguate stage converts between
        // properties and methods.
        input = "public f: C<T>",
        wantJson = """
        |["Call", [
        |    ["RightName", "@"],
        |    ["RightName", "public"],
        |    ["Call", [
        |        ["RightName", ":"],
        |        ["RightName", "f"],
        |        ["Call", [
        |            ["Value", "nym`<>`: Function"],
        |            ["RightName", "C"],
        |            ["RightName", "T"],
        |          ]
        |        ]
        |      ]
        |    ]
        |  ]
        |]
        """.trimMargin(),
    )

    @Test
    fun functionWithBellsAndWhistles() = assertAst(
        input = "fn f(x: number, y = null): number|string { return y ?? \"\" + x; }",
        wantJson = """
        [ "Call", [
            [ "RightName", "fn" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "number" ]
              ]
            ],
            [ "Block", [
                [ "Value", [ "_complexArg_", "Symbol" ] ],
                [ "RightName", "y" ],
                [ "Value", [ "default", "Symbol" ] ],
                [ "RightName", "null" ]
              ]
            ],
            [ "Value", [ "outType", "Symbol" ] ],
            [ "Call", [
                [ "RightName", "|" ],
                [ "RightName", "number" ],
                [ "RightName", "string" ],
              ]
            ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "Call", [
                            [ "RightName", "??" ],
                            [ "RightName", "y" ],
                            [ "Call", [
                                [ "RightName", "+" ],
                                [ "Call", [
                                    [ "RightName", "cat" ],
                                  ]
                                ],
                                [ "RightName", "x" ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun constructorWithMultipleTypeActuals() = assertAst(
        input = "new T<A, B>(x)",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Call", [
                [ "Value", "nym`<>`: Function" ],
                [ "RightName", "T" ],
                [ "RightName", "A" ],
                [ "RightName", "B" ],
              ]
            ],
            [ "RightName", "x" ],
          ]
        ]
        """,
    )

    @Test
    fun spreadOperatorInArrayCtor() = assertAst(
        input = "[a, ...b, c]",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "RightName", "a" ],
            [ "Call", [
                [ "RightName", "..." ],
                [ "RightName", "b" ],
              ]
            ],
            [ "RightName", "c" ],
          ]
        ]
        """,
    )

    @Test
    fun holeInArrayCtor() = assertAst(
        input = "[, b, , d, ]",
        //       0123456789012
        //                 1
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "Call", [
                { type: "RightName", left: 1, right: 2, content: "hole" }
              ]
            ],
            [ "RightName", "b" ],
            [ "Call", [
                { type: "RightName", left: 6, right: 7, content: "hole" }
              ]
            ],
            [ "RightName", "d" ],
          ]
        ]
        """,
    )

    @Test
    fun objectConstructor() = assertAst(
        input = "{ a: b, c: c }",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ], // In lieu of known type expression.
            [ "Value", [ "a", "Symbol" ] ],
            [ "RightName", "b" ],
            [ "Value", [ "c", "Symbol" ] ],
            [ "RightName", "c" ],
          ]
        ]
        """,
    )

    @Test
    fun bracketOperator() = assertAst(
        input = "array[i]",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "[]" ],
            [ "RightName", "array" ],
            [ "RightName", "i" ]
          ]
        ]
        """,
    )

    @Test
    fun regexLiteral() = assertAst(
        input = "/a*|b+|[cd]/g",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "rgx" ],
            [ "Call", [
                [ "Value", [ "list", "Function" ] ],
                [ "Value", [ "(?/g)a*|b+|[cd]", "String" ] ],
            ] ],
            [ "Call", [
                [ "Value", [ "list", "Function" ] ],
            ] ],
          ]
        ]
        """,
    )

    @Test
    fun stringWithInterpolation() = assertAst(
        input = $$" \"a${ b }(${ c })\" ",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "cat" ],
            [ "Value", [ "a", "String" ] ],
            [ "RightName", "b" ],
            [ "Value", [ "(", "String" ] ],
            [ "RightName", "c" ],
            [ "Value", [ ")", "String" ] ],
          ]
        ]
        """,
    )

    @Test
    fun stringLiteral() = assertAst(
        input = "\"....\"",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "cat" ],
            [ "Value", [ "....", "String" ] ]
          ]
        ]
        """,
    )

    @Test
    fun stringWithCommaOperatorInInterpolation() = assertAst(
        input = $$" \"a${ b }${c}${d,d}...${e}\" ",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "cat" ],
            [ "Value", [ "a", "String" ] ],
            [ "RightName", "b" ],
            [ "RightName", "c" ],
            [ "Call", [
                [ "Value", "nym`,`: Function" ],
                [ "RightName", "d" ],
                [ "RightName", "d" ]
              ]
            ],
            [ "Value", [ "...", "String" ] ],
            [ "RightName", "e" ],
          ]
        ]
        """,
    )

    @Test
    fun stringInterpolationHole() = assertAst(
        // Text       "$${}{"
        // Positions 012345678
        input = $$$""" "$${}{" """,

        wantJson = $$"""
        [ "Call", [
            [ "RightName", "cat" ],
            {
              type: "Value",
              content: [ "${", "String" ],
              // Position spans two separate string parts.
              left: 2,
              right: 7,
            }
          ]
        ]
        """.trimIndent(),
    )

    @Test
    fun prefixOperator() = assertAst(
        input = "-1",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "-" ],
            [ "Value", [ 1, "Int32" ] ]
          ]
        ]
        """,
    )

    @Test
    fun numberEmbeddedDash() = assertAst(
        input = ".1e-2",
        startProduction = "Expr",
        wantJson = """
        [ "Value", "0.001: Float64" ]
        """,
    )

    @Test
    fun taggedTemplateString() = assertAst(
        input = $$"""f"a${ b }" """,
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Call", [
                [ "Value", [ "interpolate", "Function" ] ],
                [ "Value", [ "a", "String" ] ],
                [ "Value", [ "interpolate", "Symbol" ] ],
                [ "RightName", "b" ],
            ] ],
          ]
        ]
        """,
    )

    @Test
    fun longerTaggedTemplateString() = assertAst(
        input = $$"""f"foo${ x() }bar" """,
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Call", [
                [ "Value", [ "interpolate", "Function" ] ],
                [ "Value", [ "foo", "String" ] ],
                [ "Value", [ "interpolate", "Symbol" ] ],
                [ "Call", [
                    [ "RightName", "x" ],
                  ]
                ],
                [ "Value", [ "bar", "String" ] ],
            ] ],
          ]
        ]
        """,
    )

    @Test
    fun taggedTemplateNoInterpolation() = assertAst(
        input = """f"foo\bar" """,
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Call", [
                [ "Value", [ "interpolate", "Function" ] ],
                [ "Value", [ "foo", "String" ] ],
                [ "Value", [ "\\b", "String" ] ],
                [ "Value", [ "ar", "String" ] ],
            ] ],
          ]
        ]
        """,
    )

    @Test
    fun trailingCommaInArrayConstructor() = assertAst(
        input = "[x,]",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "RightName", "x" ]
          ]
        ]
        """,
    )

    @Test
    fun blockOfNop() = assertAst(
        input = "do {;}",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [ [ "Block", [
                [ "Value", "void: Void" ],
            ] ] ] ]
        ] ]
        """,
    )

    @Test
    fun blockOfNopNop() = assertAst(
        input = "do {; ;}",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [ [ "Block", [
                [ "Value", "void: Void" ],
            ] ] ] ]
        ] ]
        """,
        // TODO: decide what to do with {;;}
    )

    @Test
    fun minimalWhileLoop() = assertAst(
        input = "while (b) {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "while" ],
            [ "RightName", "b" ],
            [ "Fun", [
                [ "Block", [] ] // The last child is the body so we need an empty block here.
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun minimalDoWhileLoop() = assertAst(
        input = "do { } while (b)",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [ [ "Block", [] ] ] ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "while", "Symbol" ] ],
            [ "RightName", "b" ]
          ]
        ]
        """,
    )

    @Test
    fun doOnce() = assertAst(
        input = "do { x }",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [
                [ "Block", [
                    [ "RightName", "x" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun doOnceInParens() = assertAst(
        input = "(do { x })",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [
                [ "Block", [
                    [ "RightName", "x" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun minimalTryCatch() = assertAst(
        input = "try { } catch (e) {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "try" ],
            [ "Fun", [ [ "Block", [] ] ] ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "catch", "Symbol" ] ],
            [ "RightName", "e" ],
            [ "Fun", [ [ "Block", [] ] ] ]
          ]
        ]
        """,
    )

    // TODO: What do we want this to mean?  With line breaks in between, ASI ensures that we
    // get three blocks.  Otherwise, is it an application of brackets to the result of a block?
    @Ignore
    @Test
    fun threeBlocksInARow() = assertAst(
        input = "{} {} {}",
        wantJson = """
        [ "Block", [
            [ "Block", [] ],
            [ "Block", [] ],
            [ "Block", [] ],
          ]
        ]
        """,
    )

    @Test
    fun numberGreaterThanNumber() = assertAst(
        input = "5 > 1",
        wantJson = """
        [ "Call", [
            [ "RightName", ">" ],
            [ "Value", [ 5, "Int32" ] ],
            [ "Value", [ 1, "Int32" ] ]
        ] ]
        """,
    )

    @Test
    fun bagGreaterThanNumber() = assertAst(
        input = "{ a } > 1",
        wantJson = """
        [ "Call", [
            [ "RightName", ">" ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\a: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ],
            [ "Value", [ 1, "Int32" ] ]
        ] ]
        """,
    )

    @Test
    fun bagGreaterThanBag() = assertAst(
        input = "{ a } > { a: 1 }",
        wantJson = """
        [ "Call", [
            [ "RightName", ">" ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\a: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\a: Symbol" ],
                [ "Value", [ 1, "Int32" ] ]
            ] ]
        ] ]
        """,
    )

    @Test
    fun genericCallBlock() = assertAst(
        input = "f<T> {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "T" ],
            [ "Fun", [ [ "Block", [] ] ] ]
        ] ]
        """,
    )

    @Test
    fun whileLoopThenBlock() = assertAst(
        input = """
            while (b) {} // depends on semicolon inserted here
            do {}
        """,
        wantJson = """
        [ "Block", [
            [ "Call", [
                [ "RightName", "while" ],
                [ "RightName", "b" ],
                [ "Fun", [
                     [ "Block", [] ]
                  ]
                ],
              ]
            ],
            [ "Call", [
                [ "Value", "REM: Function" ],
                [ "Value", "\"depends on semicolon inserted here\": String" ],
                [ "Value", "null: Null" ],
                [ "Value", "false: Boolean" ],
              ]
            ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [] ] ] ]
            ] ]
          ]
        ]
        """,
    )

    @Test
    fun curlyQuasi() = assertAst(
        input = $$"let blockParseTreeData = \\{ f(${ x }); }",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "blockParseTreeData" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Call", [
                    [ "RightName", "quasiInner" ],
                    [ "Call", [
                        [ "RightName", "quasiInner" ],
                        [ "Call", [
                            [ "RightName", "quasiInner" ],
                            [ "Call", [
                                [ "RightName", "quasiLeaf" ],
                                [ "Value", [ "f", "Symbol" ] ],
                              ]
                            ]
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "quasiLeaf" ],
                            [ "Value", [ "(", "String" ] ],
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "unhole" ],
                            [ "Fun", [
                                [ "RightName", "x" ],
                              ]
                            ]
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "quasiLeaf" ],
                            [ "Value", [ ")", "String" ] ],
                          ]
                        ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "quasiLeaf" ],
                        [ "Value", [ ";", "String" ] ],
                      ]
                    ]
                  ]
                ]
              ]
            ]
        """,
    )

    @Test
    fun parenthesizedQuasi() = assertAst(
        input = $$"let ifAst = \\( if (${ x }) { f(${ x }) } )",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "ifAst" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Esc", [
                    [ "Call", [
                        [ "RightName", "if" ],
                        [ "Call", [
                            [ "RightName", "unhole" ],
                            [ "Fun", [
                                [ "RightName", "x" ],
                              ]
                            ]
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "f" ],
                                    [ "Call", [
                                        [ "RightName", "unhole" ],
                                        [ "Fun", [
                                            [ "RightName", "x" ],
                                          ]
                                        ]
                                      ]
                                    ]
                                  ]
                                ]
                              ]
                            ],
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
        """,
    )

    @Test
    fun expressionCanMissSemisAtEndOfBlockOrFile() = assertAst(
        input = "do {foo()}\nfoo()",
        wantJson = """
        [ "Block", [
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [ [ "RightName", "foo" ] ] ],
                ] ] ] ]
            ] ],
            [ "Call", [ [ "RightName", "foo" ] ] ]
        ] ]
        """,
    )

    @Test
    fun generatedTest1() = assertAst(
        input = "let gpp3co : D7",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "gpp3co" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "D7" ]
              ]
            ]
        """,
    )

    @Test
    fun generatedTest2() = assertAst(
        input = "do { do { ; } ; }",
        wantJson = """
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "do" ],
                        [ "Fun", [ [ "Block", [
                            [ "Value", "void: Void" ],
                        ] ] ] ]
                    ] ],
                    [ "Value", "void: Void" ],
                ] ] ] ]
            ] ]
        """,
    )

    @Test
    fun generatedTest3() = assertAst(
        input = "; 1",
        wantJson = """[ "Value", [ 1, "Int32" ] ]""",
    )

    @Test
    fun generatedTest4() = assertAst(
        input = "let DMdS : vZ < hkKNZ9x , dzW , YrMi >",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "DMdS" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "Call", [
                    [ "Value", "nym`<>`: Function" ],
                    [ "RightName", "vZ" ],
                    [ "RightName", "hkKNZ9x" ],
                    [ "RightName", "dzW" ],
                    [ "RightName", "YrMi" ]
                  ]
                ]
              ]
            ]
        """,
    )

    @Test
    fun multipleFormals() = assertAst(
        input = "function f(a, b, c, d) {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "RightName", "a" ],
            [ "RightName", "b" ],
            [ "RightName", "c" ],
            [ "RightName", "d" ],
            [ "Fun", [ [ "Block", [] ] ] ]
        ] ]
        """,
    )

    @Test
    fun noFormals() = assertAst(
        input = "function f() {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "function" ],
            [ "Value", [ "word", "Symbol" ] ],
            [ "LeftName", "f" ],
            [ "Fun", [
                [ "Block", [] ]
            ] ]
        ] ]
        """,
    )

    @Test
    fun multipleProperties() = assertAst(
        input = "{ a: 1, b: 2, c: 3, d: 4, }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ], // In lieu of known type expression.
            [ "Value", [ "a", "Symbol" ] ],
            [ "Value", [ 1, "Int32" ] ],
            [ "Value", [ "b", "Symbol" ] ],
            [ "Value", [ 2, "Int32" ] ],
            [ "Value", [ "c", "Symbol" ] ],
            [ "Value", [ 3, "Int32" ] ],
            [ "Value", [ "d", "Symbol" ] ],
            [ "Value", [ 4, "Int32" ] ],
          ]
        ]
        """,
    )

    @Test
    fun returnCanMissSemisAtEndOfBlockOrFile() = assertAst(
        input = "do {return}\ndo {return(y)}\nreturn x",
        wantJson = """
        [ "Block", [
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ]
                    ] ],
                ] ] ] ]
            ] ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "return" ],
                        [ "RightName", "y" ]
                    ] ],
                ] ] ] ]
            ] ],
            [ "Call", [
                [ "RightName", "return" ],
                [ "RightName", "x" ]
            ] ]
        ] ]
        """,
    )

    @Test
    fun doWhileCanMissSemiAtEndOfBlockOrFile() = assertAst(
        input = "do{ do { } while (b) } while (a)",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "do" ],
                        [ "Fun", [
                            [ "Block", [] ]
                        ] ],
                        [ "Value", [ "callJoin:", "Symbol" ] ],
                        [ "Value", [ "while", "Symbol" ] ],
                        [ "RightName", "b" ]
                    ] ]
                ] ]
            ] ],
            [ "Value", [ "callJoin:", "Symbol" ] ],
            [ "Value", [ "while", "Symbol" ] ],
            [ "RightName", "a" ]
        ] ]
        """,
    )

    @Ignore
    @Test
    fun generatedTest6() = assertAst(
        // Assignment has a precedence mismatch with the `:` operator.
        // The precedence needs to be this way so that in
        //     let x: ... = ...
        // the type after the type between the `:` and the `=` is contained.
        // TODO: look into whether we can solve this by putting `:` at the same level as `=` but
        //     left-associative instead of right.
        // TODO: or parse property values as lower precedence than assignment operators.
        input = "{ Ajg : nagEm = { } }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", [ "Word", "Ajg" ] ],
            [ "Call", [
                [ "RightName", "=" ],
                [ "RightName", "nagEm" ],
                [ "Call", [
                    [ "RightName", "new" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun generatedTest7() = assertAst(
        input = "if ( x , y ) { }",
        wantJson = """
        [ "Call", [
            [ "RightName", "if" ],
            [ "RightName", "x" ],
            [ "RightName", "y" ],
            [ "Fun", [ [ "Block", [] ] ] ]
        ] ]
        """,
    )

    @Test
    fun generatedTest8() = assertAst(
        input = "1_2_3",
        wantJson = """
        [ "Value", [ 123, "Int32" ] ]
        """,
    )

    @Test
    fun generatedTest9() = assertAst(
        // TODO Retain precision for literals? Error on overlarge? Just wrap?
        // TODO We wrap for now, if within Int64 limits.
        input = "0xD48f0F134",
        wantJson = """
        [ "Value", [ 0x48f0F134, "Int32" ] ]
        """,
    )

    @Test
    fun detailedPositionInfo() {
        val input = listOf(
            // Using `"" + ` helps line up indices with characters below.
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
            "" + "          a\n",
            //           1
            //           8
            //    3456789012345
            "" + "        }\n",
            //        1
            //        9
            //    6789012345
            "" + "        fib(8)\n",
            //        2         2
            //        0         1
            //    6789012345678901
            "" + "        ",
            //            2
            //            2
            //    234567890
        ).joinToString("")
        assertAst(
            input = input,
            wantJson = """
            {
                "type": "Block",
                "loc": "test/test.temper",
                "left": 9,
                "right": 209,
                "children": [
                    {
                        "type": "Call",
                        "left": 9,
                        "right": 194,
                        "children": [
                            {
                                "type": "RightName",
                                "left": 9,
                                "right": 17,
                                "content": "function"
                            },
                            {
                                "type": "Value",
                                "left": 18,
                                "right": 18,
                                "content": {
                                    "stateVector": "word",
                                    "typeTag": "Symbol"
                                }
                            },
                            {
                                "type": "LeftName",
                                "left": 18,
                                "right": 21,
                                "content": "fib"
                            },
                            {
                                "type": "RightName",
                                "left": 22,
                                "right": 23,
                                "content": "i"
                            },
                            {
                                "type": "Fun",
                                "left": 25,
                                "right": 194,
                                "children": [
                                    {
                                        "type": "Block",
                                        "left": 25,
                                        "right": 194,
                                        "children": [
                                            {
                                                "type": "Decl",
                                                "left": 37,
                                                "right": 46,
                                                "children": [
                                                    {
                                                        "type": "LeftName",
                                                        "left": 41,
                                                        "right": 42,
                                                        "content": "a"
                                                    },
                                                    {
                                                        "type": "Value",
                                                        "left": 43,
                                                        "right": 44,
                                                        "content": {
                                                            "stateVector": "init",
                                                            "typeTag": "Symbol"
                                                        }
                                                    },
                                                    {
                                                        "type": "Value",
                                                        "left": 45,
                                                        "right": 46,
                                                        "content": {
                                                            "stateVector": 0,
                                                            "typeTag": "Int32"
                                                        }
                                                    }
                                                ]
                                            },
                                            {
                                                "type": "Decl",
                                                "left": 59,
                                                "right": 68,
                                                "children": [
                                                    {
                                                        "type": "LeftName",
                                                        "left": 63,
                                                        "right": 64,
                                                        "content": "b"
                                                    },
                                                    {
                                                        "type": "Value",
                                                        "left": 65,
                                                        "right": 66,
                                                        "content": {
                                                            "stateVector": "init",
                                                            "typeTag": "Symbol"
                                                        }
                                                    },
                                                    {
                                                        "type": "Value",
                                                        "left": 67,
                                                        "right": 68,
                                                        "content": {
                                                            "stateVector": 1,
                                                            "typeTag": "Int32"
                                                        }
                                                    }
                                                ]
                                            },
                                            {
                                                "type": "Call",
                                                "left": 80,
                                                "right": 172,
                                                "children": [
                                                    {
                                                        "type": "RightName",
                                                        "left": 80,
                                                        "right": 85,
                                                        "content": "while"
                                                    },
                                                    {
                                                        "type": "Call",
                                                        "left": 87,
                                                        "right": 92,
                                                        "children": [
                                                            {
                                                                "type": "RightName",
                                                                "left": 89,
                                                                "right": 90,
                                                                "content": "\u003e"
                                                            },
                                                            {
                                                                "type": "RightName",
                                                                "left": 87,
                                                                "right": 88,
                                                                "content": "i"
                                                            },
                                                            {
                                                                "type": "Value",
                                                                "left": 91,
                                                                "right": 92,
                                                                "content": {
                                                                    "stateVector": 0,
                                                                    "typeTag": "Int32"
                                                                }
                                                            }
                                                        ]
                                                    },
                                                    {
                                                        "type": "Fun",
                                                        "left": 94,
                                                        "right": 172,
                                                        "children": [
                                                            {
                                                                "type": "Block",
                                                                "left": 94,
                                                                "right": 172,
                                                                "children": [
                                                                    {
                                                                        "type": "Decl",
                                                                        "left": 108,
                                                                        "right": 121,
                                                                        "children": [
                                                                            {
                                                                                "type": "LeftName",
                                                                                "left": 112,
                                                                                "right": 113,
                                                                                "content": "c"
                                                                            },
                                                                            {
                                                                                "type": "Value",
                                                                                "left": 114,
                                                                                "right": 115,
                                                                                "content": {
                                                                                    "stateVector": "init",
                                                                                    "typeTag": "Symbol"
                                                                                }
                                                                            },
                                                                            {
                                                                                "type": "Call",
                                                                                "left": 116,
                                                                                "right": 121,
                                                                                "children": [
                                                                                    {
                                                                                        "type": "RightName",
                                                                                        "left": 118,
                                                                                        "right": 119,
                                                                                        "content": "+"
                                                                                    },
                                                                                    {
                                                                                        "type": "RightName",
                                                                                        "left": 116,
                                                                                        "right": 117,
                                                                                        "content": "a"
                                                                                    },
                                                                                    {
                                                                                        "type": "RightName",
                                                                                        "left": 120,
                                                                                        "right": 121,
                                                                                        "content": "b"
                                                                                    }
                                                                                ]
                                                                            }
                                                                        ]
                                                                    },
                                                                    {
                                                                        "type": "Call",
                                                                        "left": 135,
                                                                        "right": 140,
                                                                        "children": [
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 137,
                                                                                "right": 138,
                                                                                "content": "="
                                                                            },
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 135,
                                                                                "right": 136,
                                                                                "content": "a"
                                                                            },
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 139,
                                                                                "right": 140,
                                                                                "content": "b"
                                                                            }
                                                                        ]
                                                                    },
                                                                    {
                                                                        "type": "Call",
                                                                        "left": 154,
                                                                        "right": 159,
                                                                        "children": [
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 156,
                                                                                "right": 157,
                                                                                "content": "="
                                                                            },
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 154,
                                                                                "right": 155,
                                                                                "content": "b"
                                                                            },
                                                                            {
                                                                                "type": "RightName",
                                                                                "left": 158,
                                                                                "right": 159,
                                                                                "content": "c"
                                                                            }
                                                                        ]
                                                                    },
                                                                    {
                                                                        "type": "Value",
                                                                        "loc": "test/test.temper",
                                                                        "left": 159,
                                                                        "right": 160,
                                                                        "content": {
                                                                            "stateVector": null,
                                                                            "typeTag": "Void",
                                                                            "abbrev": "void: Void"
                                                                        },
                                                                        "typeInferences": null
                                                                    }
                                                                ]
                                                            }
                                                        ]
                                                    }
                                                ]
                                            },
                                            {
                                                "type": "RightName",
                                                "left": 183,
                                                "right": 184,
                                                "content": "a"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "type": "Call",
                        "left": 203,
                        "right": 209,
                        "children": [
                            {
                                "type": "RightName",
                                "left": 203,
                                "right": 206,
                                "content": "fib"
                            },
                            {
                                "type": "Value",
                                "left": 207,
                                "right": 208,
                                "content": {
                                    "stateVector": 8,
                                    "typeTag": "Int32"
                                }
                            }
                        ]
                    }
                ]
            }
            """,
        )
    }

    @Test
    fun orElse() = assertAst(
        input = "do { f() } orelse do { if (x) { g(); } }",
        wantJson = """
        [ "Call", [
            [ "RightName", "orelse" ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "f" ]
                      ]
                    ]
                ] ] ] ]
            ] ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "if" ],
                        [ "RightName", "x" ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "g" ]
                                  ]
                                ],
                                [ "Value", "void: Void" ],
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                ] ] ] ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun orElseBag() = assertAst(
        input = "{ a } orelse { b }",
        wantJson = """
        [ "Call", [
            [ "RightName", "orelse" ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\a: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\b: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ]
        ] ]
        """,
    )

    @Test
    fun listOfBags() = assertAst(
        input = "[ { a }, { b } ]",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\a: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ],
            [ "Call", [
                [ "RightName", "new" ],
                [ "Value", "void: Void" ],
                [ "Value", "\\b: Symbol" ],
                [ "Call", [
                    [ "Value", "desugarPun: Function" ],
                ] ]
            ] ]
        ] ]
        """,
    )

    @Test
    fun blockAsExpr() = assertAst(
        input = "do { f() }",
        startProduction = "Expr",
        wantJson = """
        [ "Call", [
            [ "RightName", "do" ],
            [ "Fun", [ [ "Block", [
                [ "Call", [
                    [ "RightName", "f" ]
                  ]
                ]
            ] ] ] ]
        ] ]
        """,
    )

    @Test
    fun emptyList() = assertAst(
        input = "[]",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ]
          ]
        ]
        """,
    )

    @Test
    fun listOnlyType() = assertAst(
        input = "[Int;]",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "Value", [ "type", "Symbol" ] ],
            [ "RightName", "Int" ]
          ]
        ]
        """,
    )

    @Test
    fun listOnlySpread() = assertAst(
        input = "[...x]",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "Call", [
                [ "RightName", "..." ],
                [ "RightName", "x" ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun listTypeWithElements() = assertAst(
        input = "[Foo;a,,c,]",
        wantJson = """
        [ "Call", [
            [ "Value", "list: Function" ],
            [ "Value", [ "type", "Symbol" ] ],
            [ "RightName", "Foo" ],
            [ "RightName", "a" ],
            [ "Call", [
                [ "RightName", "hole" ]
              ]
            ],
            [ "RightName", "c" ],
          ]
        ]
        """,
    )

    @Test
    fun quotedIdentifiers() = assertAst(
        input = "let nym`=` = \"=\"",
        wantJson = """
            [ "Decl", [
                [ "LeftName", "=" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Call", [
                    [ "RightName", "cat" ],
                    [ "Value", [ "=", "String" ] ]
                  ]
                ]
              ]
            ]
        """,
    )

    @Test
    fun emptyQuotedIdentifierDisallowed() = assertAst(
        input = " nym`` ",
        wantJson = """
        [ "Call", [
            [ "Value", "error: Function" ],
            [ "Call", [
                [ "Value", "list: Function" ],
                [ "Value", [ "nym``", "String" ] ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun letDeclWithLifespan() = assertAst(
        input = "@(S..T) let x : T = 123",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "Call", [
                [ "RightName", ".." ],
                [ "RightName", "S" ],
                [ "RightName", "T" ],
              ]
            ],
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", [ "type", "Symbol" ] ],
                [ "RightName", "T" ],
                [ "Value", [ "init", "Symbol" ] ],
                [ "Value", [ 123, "Int32" ] ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun controlFlowWithLifespan() = assertAst(
        input = "@(S..T) fn () { }",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "Call", [
                [ "RightName", ".." ],
                [ "RightName", "S" ],
                [ "RightName", "T" ],
              ]
            ],
            [ "Call", [
                [ "RightName", "fn" ],
                [ "Fun", [
                    [ "Block", [] ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun controlFlowWithWordsAndLifespan() = assertAst(
        input = "@(S..T) fn f() { }",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "Call", [
                [ "RightName", ".." ],
                [ "RightName", "S" ],
                [ "RightName", "T" ],
              ]
            ],
            [ "Call", [
                [ "RightName", "fn" ],
                [ "Value", [ "word", "Symbol" ] ],
                [ "LeftName", "f" ],
                [ "Fun", [
                    [ "Block", [] ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun symbolLiterals() = assertAst(
        input = """ \foo """,
        wantJson = """
        [ "Value", [ "foo", "Symbol" ] ]
        """,
    )

    @Test
    fun escapedSymbolLiterals() = assertAst(
        input = """ \nym`foo\u0020bar` """,
        wantJson = """
        [ "Value", [ "foo bar", "Symbol" ] ]
        """,
    )

    @Test
    fun controlFlowFunctionsDoNotHaveTopLevelDecls() = assertAst(
        // Decls in the top level of a FunTree are formal parameters.
        input = """ f { let x } """,
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Fun", [
                [ "Block", [
                    [ "Decl", [
                        [ "LeftName", "x" ],
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun labeledStmtBreak() = assertAst(
        input = "foo: do { break foo; }",
        wantJson = """
        [ "Block", [
            [ "Value", "\\label: Symbol" ],
            [ "LeftName", "foo" ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [
                    [ "Call", [
                        [ "RightName", "break" ],
                        [ "Value", "\\label: Symbol" ],
                        [ "RightName", "foo" ]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                ] ] ] ]
            ] ]
          ]
        ]
        """,
    )

    @Test
    fun labeledStmtBlock() = assertAst(
        input = "foo: do {}",
        wantJson = """
        [ "Block", [
            [ "Value", "\\label: Symbol" ],
            [ "LeftName", "foo" ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [ [ "Block", [] ] ] ]
            ] ]
          ]
        ]
        """,
    )

    @Test
    fun labeledControlFlow() = assertAst(
        input = "foo: until (x) { stuff(); }",
        wantJson = """
        [ "Block", [
            [ "Value", "\\label: Symbol" ],
            [ "LeftName", "foo" ],
            [ "Call", [
                [ "RightName", "until" ],
                [ "RightName", "x" ],
                [ "Fun", [
                    [ "Block", [
                        [ "Call", [
                            [ "RightName", "stuff" ]
                          ]
                        ],
                        [ "Value", "void: Void" ],
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun abnormalQuotedIdentifier() {
        if (kotlinBackend.affectedByIssue11) {
            return
        }
        assertAst(
            input = "let nym`\u03D3`, nym`\\u03D3` = \"\u03D3\";",
            wantJson = """
            [ "Block", [
                [ "Decl", [
                    [ "Call", [
                        [ "Value", "error: Function" ],
                        [ "Call", [
                            [ "Value", "list: Function" ],
                            [ "Value", [ "nym`\u03d3`", "String" ] ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ],
                [ "Decl", [
                    [ "Call", [
                        [ "Value", "error: Function" ],
                        [ "Call", [
                            [ "Value", "list: Function" ],
                            [ "Value", [ "nym`\\u03D3`", "String" ] ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "\\init: Symbol" ],
                    // Non-NFKC string content is fine.
                    [ "Call", [ [ "RightName", "cat" ], [ "Value", [ "\u03d3", "String" ] ] ] ]
                  ]
                ],
                [ "Value", "void: Void" ],
              ]
            ]
            """,
        )
    }

    @Test
    fun multiAssign() = assertAst(
        input = "[a, b] = f()",
        wantJson = """
        [ "Call", [
            [ "RightName", "=" ],
            [ "Call", [
                [ "Value", "list: Function" ],
                [ "RightName", "a" ],
                [ "RightName", "b" ]
              ]
            ],
            [ "Call", [
                [ "RightName", "f" ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun multiLet() = assertAst(
        input = "let [a, b, c is T] = f()",
        wantJson = """
            [ "Decl", [
                [ "Call", [
                    [ "Value", "nym`,`: Function" ],
                    [ "LeftName", "a" ],
                    [ "LeftName", "b" ],
                    [ "LeftName", "c" ],
                    [ "Value", "\\type: Symbol" ],
                    [ "RightName", "T" ]
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "f" ] ] ],
              ]
            ]
        """,
    )

    @Test
    fun multiLetInFirstPosition() = assertAst(
        input = "let [a] = f(), b = g()",
        wantJson = """
        [ "Block", [
            [ "Decl", [
                [ "Call", [
                    [ "Value", "nym`,`: Function" ],
                    [ "LeftName", "a" ]
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "f" ] ] ]
              ]
            ],
            [ "Decl", [
                [ "LeftName", "b" ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "g" ] ] ],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun multiLetInSecondPosition() = assertAst(
        input = "let a: T, [b] = f()",
        wantJson = """
        [ "Block", [
            [ "Decl", [
                [ "LeftName", "a" ],
                [ "Value", "\\type: Symbol" ],
                [ "RightName", "T" ]
              ]
            ],
            [ "Decl", [
                [ "Call", [
                    [ "Value", "nym`,`: Function" ],
                    [ "LeftName", "b" ]
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "f" ] ] ],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun letDotDotDot() = assertAst(
        input = "let [...] = import(\"thing\")",
        wantJson = """
            [ "Decl", [
                [ "Call", [
                    [ "Value", "nym`,`: Function" ],
                    [ "Value", "\\...: Symbol" ],
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [
                    [ "RightName", "import" ],
                    [ "Call", [
                        [ "RightName", "cat" ],
                        [ "Value", "\"thing\": String" ],
                      ]
                    ]
                  ]
                ]
              ]
            ]
        """.trimMargin(),
    )

    @Test
    fun multiLetNamed() = assertAst(
        input = "let { a is S, b, c as d is T } = f()",
        wantJson = """
            [ "Decl", [
                [ "Call", [
                    [ "RightName", "{}" ],
                    [ "LeftName", "a" ],
                    [ "Value", "\\type: Symbol" ],
                    [ "RightName", "S" ],
                    [ "LeftName", "b" ],
                    [ "LeftName", "c" ],
                    [ "Value", "\\as: Symbol" ],
                    [ "LeftName", "d" ],
                    [ "Value", "\\type: Symbol" ],
                    [ "RightName", "T" ]
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "f" ] ] ],
              ]
            ]
        """.trimMargin(),
    )

    @Test
    fun multiLetNamedTrailingCommaMultiPos() = assertAst(
        input = "let { a } = o, { b, } = p;",
        wantJson = """
            [ "Block", [
                [ "Decl", [
                    [ "Call", [
                        [ "RightName", "{}" ],
                        [ "LeftName", "a" ],
                      ]
                    ],
                    [ "Value", "\\init: Symbol" ],
                    [ "RightName", "o" ],
                  ]
                ],
                [ "Decl", [
                    [ "Call", [
                        [ "RightName", "{}" ],
                        [ "LeftName", "b" ],
                      ]
                    ],
                    [ "Value", "\\init: Symbol" ],
                    [ "RightName", "p" ],
                  ]
                ],
                [ "Value", "void: Void" ],
              ]
            ]
        """.trimMargin(),
    )

    @Test
    fun multiLetNamedNested() = assertAst(
        input = "let { a: { b }, c: { d, e } } = f()",
        wantJson = """
            [ "Decl", [
                [ "Call", [
                    [ "RightName", "{}" ],
                    [ "LeftName", "a" ],
                    [ "Value", "\\as: Symbol" ],
                    [ "Call", [
                        [ "RightName", "{}" ],
                        [ "LeftName", "b" ]
                      ]
                    ],
                    [ "LeftName", "c" ],
                    [ "Value", "\\as: Symbol" ],
                    [ "Call", [
                        [ "RightName", "{}" ],
                        [ "LeftName", "d" ],
                        [ "LeftName", "e" ]
                      ]
                    ]
                  ]
                ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [ [ "RightName", "f" ] ] ],
              ]
            ]
        """.trimMargin(),
    )

    @Test
    fun blockLambdaEmptyArgumentListNoInit() = assertAst(
        input = """
          f { () =>
            body
          }
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Fun", [
                // TODO: is having an explicit empty argument list different
                // from having no argument list when it comes to signature inference.
                [ "Block", [
                    [ "RightName", "body" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun classDeclaration1() = assertAst(
        input = """
            class Minimal {}
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            [ "RightName", "class" ],
            [ "Value", ["word", "Symbol"] ],
            [ "LeftName", "Minimal" ],
            [ "Fun", [
                [ "Block", [] ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun classDeclaration2() = assertAst(
        input = """
            interface IFoo extends IBar {
                f(): Never;
            }
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            ["RightName", "interface"],
            ["Value", "\\word: Symbol"],
            ["LeftName", "IFoo"],
            ["Value", "\\super: Symbol"],
            ["RightName", "IBar"],
            ["Fun", [
                ["Block", [
                    ["Call", [
                        ["RightName", "f"],
                        ["Value", "\\outType: Symbol"],
                        ["RightName", "Never"]
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun classDeclaration3() = assertAst(
        input = """
            class Complex<E> extends A, B {
                y: E;
                constructor(...) { }
                get x() { 42 }
            }
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            [ "RightName", "class" ],
            [ "Value", "\\word: Symbol" ],
            [ "LeftName", "Complex" ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "E" ],
            [ "Value", "\\super: Symbol" ],
            [ "RightName", "A" ],
            [ "Value", "\\super: Symbol" ],
            [ "RightName", "B" ],
            [ "Fun", [
                [ "Block", [
                    [ "Call", [
                        [ "RightName", ":" ],
                        [ "RightName", "y" ],
                        [ "RightName", "E" ],
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "constructor" ],
                        [ "Block", [
                            [ "Value", "\\_complexArg_: Symbol" ],
                            [ "Value", "\\...: Symbol" ],
                            [ "Value", "true: Boolean" ],
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [] ]
                          ]
                        ]
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "get" ],
                        [ "Value", "\\word: Symbol" ],
                        [ "LeftName", "x" ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Value", "42: Int32" ]
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun classDeclaration4() = assertAst(
        input = """
            |class C<T>(a: Int, let b: Boolean) extends Super {}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "class" ],
            |    [ "Value", "\\word: Symbol" ],
            |    [ "LeftName", "C" ],
            |    [ "Value", "\\typeArg: Symbol" ],
            |    [ "RightName", "T" ],
            |    [ "Block", [
            |        [ "Value", "\\_complexArg_: Symbol" ],
            |        [ "RightName", "a" ],
            |        [ "Value", "\\type: Symbol" ],
            |        [ "RightName", "Int" ],
            |      ],
            |    ],
            |    [ "Block", [
            |        [ "Value", "\\_complexArg_: Symbol" ],
            |        [ "Decl", [
            |            [ "LeftName", "b" ],
            |            [ "Value", "\\type: Symbol" ],
            |            [ "RightName", "Boolean" ],
            |          ]
            |        ]
            |      ],
            |    ],
            |    [ "Value", "\\super: Symbol" ],
            |    [ "RightName", "Super" ],
            |    [ "Fun", [
            |        [ "Block", [
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun classDeclaration5() = assertAst(
        input = """
            |/** doc on class */
            |export class C(
            |  /** docs for p */
            |  p: Int,
            |  /** docs for q */
            |  public let q: Int,
            |) {}
        """.trimMargin(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "Value", "REM: Function" ],
            |        [ "Value", "\"doc on class\": String" ],
            |        [ "Value", "true: Boolean" ],
            |        [ "Value", "false: Boolean" ],
            |      ],
            |    ],
            |    [ "Call", [
            |        [ "RightName", "@" ],
            |        [ "RightName", "export" ],
            |        [ "Call", [
            |            [ "RightName", "class" ],
            |            [ "Value", "\\word: Symbol" ],
            |            [ "LeftName", "C" ],
            |            [ "Call", [
            |                [ "Value", "nym`@docComment`: Function" ],
            |                [ "Block", [
            |                    [ "Value", "\\_complexArg_: Symbol" ],
            |                    [ "RightName", "p" ],
            |                    [ "Value", "\\type: Symbol" ],
            |                    [ "RightName", "Int" ],
            |                  ]
            |                ],
            |                [ "Value", "[\"docs for p\"]: List" ],
            |                [ "Value", "false: Boolean" ],
            |              ],
            |            ],
            |            [ "Call", [
            |                [ "Value", "nym`@docComment`: Function" ],
            |                [ "Call", [
            |                    [ "RightName", "@" ],
            |                    [ "RightName", "public" ],
            |                    [ "Block", [
            |                        [ "Value", "\\_complexArg_: Symbol" ],
            |                        [ "Decl", [
            |                            [ "LeftName", "q" ],
            |                            [ "Value", "\\type: Symbol" ],
            |                            [ "RightName", "Int" ],
            |                          ]
            |                        ]
            |                      ]
            |                    ]
            |                  ]
            |                ],
            |                [ "Value", "[\"docs for q\"]: List" ],
            |                [ "Value", "false: Boolean" ],
            |              ],
            |            ],
            |            [ "Fun", [
            |                [ "Block", [] ]
            |              ]
            |            ]
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun decoratingComplexArgsWithMarkdownComments() = assertAst(
        input = """
            |    f(
            |      // Now for some arguments.
            |
            |x is an integer.
            |
            |      x: Int,
            |    );
        """.trimMargin(),
        lang = MarkdownLanguageConfig(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "RightName", "f" ],
            |        [ "Call", [
            |            [ "Value", "nym`@docComment`: Function" ],
            |            [ "Block", [
            |                [ "Value", "\\_complexArg_: Symbol" ],
            |                [ "RightName", "x" ],
            |                [ "Value", "\\type: Symbol" ],
            |                [ "RightName", "Int" ],
            |              ],
            |            ],
            |            [ "Value", "[\"x is an integer.\"]: List" ],
            |            [ "Value", "true: Boolean" ],
            |          ],
            |        ],
            |      ]
            |    ],
            |    [ "Value", "void: Void" ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun classDeclaration6() = assertAst(
        input = """
            class C<E> extends A, B forbids C supports D<E>, F {}
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            [ "RightName", "class" ],
            [ "Value", "\\word: Symbol" ],
            [ "LeftName", "C" ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "E" ],
            [ "Value", "\\super: Symbol" ],
            [ "RightName", "A" ],
            [ "Value", "\\super: Symbol" ],
            [ "RightName", "B" ],
            [ "Value", "\\forbids: Symbol" ],
            [ "RightName", "C" ],
            [ "Value", "\\supports: Symbol" ],
            [ "Call", [
                [ "Value", "nym`<>`: Function" ],
                [ "RightName", "D" ],
                [ "RightName", "E" ],
              ]
            ],
            [ "Value", "\\supports: Symbol" ],
            [ "RightName", "F" ],
            [ "Fun", [
                [ "Block", [
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun callWithWordAndTypeButNoClosure() = assertAst(
        input = "let name(): RT",
        wantJson = """
        |[ "Call", [
        |    ["RightName", "let"],
        |    ["Value", "\\word: Symbol"],
        |    ["LeftName", "name"],
        |    ["Value", "\\outType: Symbol"],
        |    ["RightName", "RT"],
        |  ]
        |]
        """.trimMargin(),
    )

    @Test
    fun abstractGetterAndMethod() = assertAst(
        input = """
        |interface I {
        |    let method(): RT;
        |    get prop(): T;
        |    let noRt();
        |}
        """.trimMargin(),
        wantJson = """
        |["Call", [
        |    ["RightName", "interface"],
        |    ["Value", "\\word: Symbol"],
        |    ["LeftName", "I"],
        |    ["Fun", [
        |        ["Block", [
        |            ["Call", [
        |                ["RightName", "let"],
        |                ["Value", "\\word: Symbol"],
        |                ["LeftName", "method"],
        |                ["Value", "\\outType: Symbol"],
        |                ["RightName", "RT"],
        |              ]
        |            ],
        |            ["Call", [
        |                ["RightName", "get"],
        |                ["Value", "\\word: Symbol"],
        |                ["LeftName", "prop"],
        |                ["Value", "\\outType: Symbol"],
        |                ["RightName", "T"],
        |              ]
        |            ],
        |            ["Call", [
        |                ["RightName", "let"],
        |                ["Value", "\\word: Symbol"],
        |                ["LeftName", "noRt"],
        |              ]
        |            ],
        |            [ "Value", "void: Void" ],
        |          ]
        |        ]
        |      ]
        |    ]
        |  ]
        |]
        """.trimMargin(),
    )

    @Test
    fun generatorBellsAlsoWhistles() = assertAst(
        input = """
            |f { (self: GeneratorFn, x: T): RT extends GeneratorFn, DoesItBlendable =>
            |  body0;
            |  body1
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "f" ],
            |    [ "Fun", [
            |        [ "Block", [
            |            [ "Value", "\\_complexArg_: Symbol" ],
            |            [ "RightName", "self" ],
            |            [ "Value", "\\type: Symbol" ],
            |            [ "RightName", "GeneratorFn" ],
            |          ]
            |        ],
            |        [ "Block", [
            |            [ "Value", "\\_complexArg_: Symbol" ],
            |            [ "RightName", "x" ],
            |            [ "Value", "\\type: Symbol" ],
            |            [ "RightName", "T" ],
            |          ]
            |        ],
            |        [ "Value", "\\outType: Symbol" ],
            |        [ "RightName", "RT" ],
            |        [ "Value", "\\super: Symbol" ],
            |        [ "RightName", "GeneratorFn" ],
            |        [ "Value", "\\super: Symbol" ],
            |        [ "RightName", "DoesItBlendable" ],
            |        [ "Block", [
            |            [ "RightName", "body0" ],
            |            [ "RightName", "body1" ],
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun passedBlockWithFormals() = assertAst(
        input = """
        f { (fn: Generator, x: T): RT =>
          body0;
          body1;
        }
        """,
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Fun", [
                [ "Block", [
                    ["Value", "\\_complexArg_: Symbol"],
                    ["RightName", "fn"],
                    ["Value", "\\type: Symbol"],
                    ["RightName", "Generator"],
                  ]
                ],
                [ "Block", [
                    ["Value", "\\_complexArg_: Symbol"],
                    ["RightName", "x"],
                    ["Value", "\\type: Symbol"],
                    ["RightName", "T"],
                  ]
                ],
                [ "Value", "\\outType: Symbol" ],
                [ "RightName", "RT" ],
                [ "Block", [
                    [ "RightName", "body0" ],
                    [ "RightName", "body1" ],
                    [ "Value", "void: Void" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun orAmbiguity() = assertAst( // See issue #56
        input = """
        let unionOfInts  =         1 | 2 | 3;
        let unionOfTypes = Never | 1 | 2 | 3;
        """,
        wantJson = """
        [ "Block", [
            [ "Decl", [
                [ "LeftName", "unionOfInts" ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [
                    [ "RightName", "|" ],
                    [ "Value", "1: Int32" ],
                    [ "Call", [
                        [ "RightName", "|" ],
                        [ "Value", "2: Int32" ],
                        [ "Value", "3: Int32" ],
                      ]
                    ]
                  ]
                ]
              ]
            ],
            [ "Decl", [
                [ "LeftName", "unionOfTypes" ],
                [ "Value", "\\init: Symbol" ],
                [ "Call", [
                    [ "RightName", "|" ],
                    [ "RightName", "Never" ],
                    [ "Call", [
                        [ "RightName", "|" ],
                        [ "Value", "1: Int32" ],
                        [ "Call", [
                            [ "RightName", "|" ],
                            [ "Value", "2: Int32" ],
                            [ "Value", "3: Int32" ],
                          ]
                        ]
                      ]
                    ]
                  ]
                ]
              ]
            ],
            [ "Value", "void: Void" ],
          ]
        ]
        """,
    )

    @Test
    fun singleCommaExpressionNotElided() = assertAst( // See issue #56
        // Putting a single item in a comma operator is an idiom that means
        // leave this alone during syntactic desugaring.
        input = "macroCall(),",
        wantJson = """
        [ "Call", [
            [ "Value", "nym`,`: Function" ],
            [ "Call", [
                [ "RightName", "macroCall" ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun commaInParenthesesIsReservedSyntax() = assertAst(
        input = "(a, b, c)",
        wantJson = """
        |[ "Call", [
        |    [ "Value", "error: Function" ],
        |    [ "Call", [
        |        [ "Value", "list: Function" ],
        |        [ "Value", [ "`(Comma`", "String" ] ],
        |        [ "Value", [ "`(Leaf`", "String" ] ],
        |        [ "Value", "\"a\": String" ],
        |        [ "Value", [ "`Leaf)`", "String" ] ],
        |        [ "Value", "\",\": String" ],
        |        [ "Value", [ "`(Leaf`", "String" ] ],
        |        [ "Value", "\"b\": String" ],
        |        [ "Value", [ "`Leaf)`", "String" ] ],
        |        [ "Value", "\",\": String" ],
        |        [ "Value", [ "`(Leaf`", "String" ] ],
        |        [ "Value", "\"c\": String" ],
        |        [ "Value", [ "`Leaf)`", "String" ] ],
        |        [ "Value", [ "`Comma)`", "String" ] ],
        |      ]
        |    ],
        |  ],
        |  {
        |    errors: [
        |      {
        |        level: "error",
        |        template: "SyntaxReservedForTuples",
        |        values: [],
        |        left: 1,
        |        right: 8,
        |      },
        |    ]
        |  }
        |]
        """.trimMargin(),
    )

    @Test
    fun usesOfThis() = assertAst(
        input = "this.foo, this, nym`this`",
        wantJson = """
        [ "Call", [
            [ "Value", "nym`,`: Function" ],
            [ "Call", [
                [ "RightName", "." ],
                [ "Call", [ [ "Value", "this: Function" ] ] ],
                [ "Value", "\\foo: Symbol" ],
              ]
            ],
            [ "Call", [ [ "Value", "this: Function" ] ] ],
            [ "RightName", "this" ]
          ]
        ]
        """,
    )

    @Test
    fun builtins() = assertAst(
        input = "builtins.foo, builtins.nym`bar-baz`, nym`builtins`.boo",
        wantJson = """
        [ "Call", [
            [ "Value", "nym`,`: Function" ],
            [ "RightName", "foo" ], // TODO: check is BuiltinName
            [ "RightName", "bar-baz" ], // TODO: check is BuiltinName
            [ "Call", [
                [ "RightName", "." ],
                [ "RightName", "builtins" ],
                [ "Value", "\\boo: Symbol" ],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun superIsReservedSyntax() = assertAst(
        input = "super.f(nym`super`)",
        wantJson = """
        [ "Call", [
            [ "Call", [
                [ "RightName", "." ],
                [ "Call", [
                    [ "Value", "error: Function" ],
                    [ "Call", [
                        [ "Value", "list: Function" ],
                        [ "Value", "\"super\": String" ],
                      ]
                    ]
                  ]
                ],
                [ "Value", "\\f: Symbol" ],
              ]
            ],
            [ "RightName", "super" ],
          ],
          {
            errors: [
              "Expected a Word (not \"super\") here!",
            ]
          }
        ]
        """,
    )

    @Test
    fun slashIdMeansSymbolEvenWhenItIsAlsoAUnicodeEscapeSequence() = assertAst(
        input = """ \u0061 """,
        wantJson = """
        [ "Value", [ "u0061", "Symbol" ] ]
        """,
    )

    @Test
    fun insideEscapedNameUnicodeEscapeSequencesDecoded() = assertAst(
        input = """ nym`\u0061` """,
        wantJson = """
        [ "RightName", "a" ]
        """,
    )

    @Test
    fun rightOperandToDotIsASymbol() = assertAst(
        input = "foo.bar",
        wantJson = """
        [ "Call", [
            [ "RightName", "." ],
            [ "RightName", "foo" ],
            [ "Value", "\\bar: Symbol" ],
          ]
        ]
        """,
    )

    @Test
    fun asPseudoMethod() = assertAst(
        input = "a.as<B>()",
        wantJson = """
        [ "Call", [
            [ "Call", [
                [ "RightName", "." ],
                [ "RightName", "a" ],
                [ "Value", "\\as: Symbol" ],
            ] ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "B" ],
        ] ]
        """,
    )

    @Test
    fun isPseudoMethod() = assertAst(
        input = "a.is<B>()",
        wantJson = """
        [ "Call", [
            [ "Call", [
                [ "RightName", "." ],
                [ "RightName", "a" ],
                [ "Value", "\\is: Symbol" ],
            ] ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "B" ],
        ] ]
        """,
    )

    @Test
    fun genericMethod() = assertAst(
        input = "a.hi<B>()",
        wantJson = """
        [ "Call", [
            [ "Call", [
                [ "RightName", "." ],
                [ "RightName", "a" ],
                [ "Value", "\\hi: Symbol" ],
            ] ],
            [ "Value", "\\typeArg: Symbol" ],
            [ "RightName", "B" ],
        ] ]
        """,
    )

    @Test
    fun explicitNewWithType() = assertAst(
        input = "new Thing(x)",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "RightName", "Thing" ],
            [ "RightName", "x" ]
          ]
        ]
        """,
    )

    @Test
    fun explicitNewWithParameterizedType() = assertAst(
        input = "new Thing<T>(x)",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Call", [
                [ "Value", "nym`<>`: Function" ],
                [ "RightName", "Thing" ],
                [ "RightName", "T" ]
              ]
            ],
            [ "RightName", "x" ]
          ]
        ]
        """,
    )

    @Test
    fun newViaObjectSyntax() = assertAst(
        input = "{ x: y }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ],
            [ "Value", "\\x: Symbol" ],
            [ "RightName", "y" ]
          ]
        ]
        """,
    )

    @Test
    fun objectSyntaxWithPun() = assertAst(
        input = "{ x: y, z, w: 0 }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ],
            [ "Value", "\\x: Symbol" ],
            [ "RightName", "y" ],
            [ "Value", "\\z: Symbol" ],
            [ "Call", [
                [ "Value", "desugarPun: Function" ],
              ]
            ],
            [ "Value", "\\w: Symbol" ],
            [ "Value", "0: Int32" ]
          ]
        ]
        """,
    )

    @Test
    fun newViaObjectSyntaxWithExplicitType() = assertAst(
        input = "{ class: C<T>, x: y }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Call", [
                [ "Value", "nym`<>`: Function" ],
                [ "RightName", "C" ],
                [ "RightName", "T" ],
              ]
            ],
            [ "Value", "\\x: Symbol" ],
            [ "RightName", "y" ]
          ]
        ]
        """,
    )

    @Test
    fun newClassPropertyMustBeFirst() = assertAst(
        input = "{ x: y, class: C }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ],
            [ "Value", "\\x: Symbol" ],
            [ "RightName", "y" ],
            [ "Call", [
                [ "Value", "error: Function" ],
                [ "Call", [
                    [ "Value", "list: Function" ],
                    [ "Value", [ "`(Leaf`", "String" ] ],
                    [ "Value", [ "class", "String" ] ],
                    [ "Value", [ "`Leaf)`", "String" ] ],
                  ]
                ]
              ]
            ],
            [ "RightName", "C" ],
          ],
          [
            {
              template: "ClassPropertyMustAppearFirst",
              values: []
            }
          ]
        ]
        """,
    )

    @Test
    fun newViaObjectSyntaxWithOnlyExplicitType() = assertAst(
        input = "{ class: C<T> }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Call", [
                [ "Value", "nym`<>`: Function" ],
                [ "RightName", "C" ],
                [ "RightName", "T" ],
              ]
            ],
          ]
        ]
        """,
    )

    @Test
    fun quotedThisIsAPropertyNameInObjectSyntax() = assertAst(
        input = "{ nym`class`: t }",
        wantJson = """
        [ "Call", [
            [ "RightName", "new" ],
            [ "Value", "void: Void" ], // Placeholder for type argument
            [ "Value", "\\class: Symbol" ],
            [ "RightName", "t" ],
          ]
        ]
        """,
    )

    /**
     * Depends on [lang.temper.parser.TokenSourceAdapter]'s conversion of
     * keywords preceding words to annotations.
     */
    @Test
    fun implicitPublicStaticAnnotation() = assertAst(
        input = "public static let x",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "public" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "static" ],
                [ "Decl", [
                    [ "LeftName", "x" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun exportDeclaration() = assertAst(
        input = "export let x = 1",
        wantJson = """
        [ "Call", [
            ["RightName", "@"],
            ["RightName", "export"],
            ["Decl", [
                ["LeftName", "x"],
                ["Value", "\\init: Symbol"],
                ["Value", "1: Int32"],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun explicitPublicStaticAnnotation() = assertAst(
        input = "@public @static let x",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "public" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "static" ],
                [ "Decl", [
                    [ "LeftName", "x" ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun annotationsOnAmbiguousArguments() = assertAst(
        input = "let f(@Foo bar: T) {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "let" ],
            [ "Value", "\\word: Symbol" ],
            [ "LeftName", "f" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "Foo" ],
                [ "Block", [
                    [ "Value", "\\_complexArg_: Symbol" ],
                    [ "RightName", "bar" ],
                    [ "Value", "\\type: Symbol" ],
                    [ "RightName", "T" ],
                  ]
                ]
              ]
            ],
            [ "Fun", [
                [ "Block", [] ],
              ]
            ]
          ],
        ]
        """,
    )

    /** `const` acts as a modifier for `let`, but can appear without `let`. */
    @Test
    fun constDecl() = assertAst(
        input = "const one = 1",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "const" ],
            [ "Decl", [
                [ "LeftName", "one" ],
                [ "Value", "\\init: Symbol" ],
                [ "Value", "1: Int32" ],
              ]
            ]
          ]
        ]
        """,
    )

    /** Similarly to `const`, var modifies `let`, even by itself. */
    @Test
    fun varDecl() = assertAst(
        input = "var x = 1",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "var" ],
            [ "Decl", [
                [ "LeftName", "x" ],
                [ "Value", "\\init: Symbol" ],
                [ "Value", "1: Int32" ],
              ]
            ]
          ]
        ]
        """,
    )

    /** `var` modifies everything in a multi declaration. */
    @Test
    fun varMultiDecl() = assertAst(
        input = "var x, y",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "var" ],
            [ "Call", [
                [ "Value", "nym`,`: Function" ],
                [ "Decl", [
                    [ "LeftName", "x" ],
                  ]
                ],
                [ "Decl", [
                    [ "LeftName", "y" ],
                  ]
                ],
              ]
            ]
          ]
        ]
        """,
    )

    /**
     * Here the `let` is implied but not via the direct Decl production, but rather as an invocation
     * of the `let` macro that creates a declaration and a function expression.
     */
    @Test
    fun constFunctionDecl() = assertAst(
        input = "const f() {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "const" ],
            [ "Call", [
                [ "RightName", "let" ],
                [ "Value", "\\word: Symbol" ],
                [ "LeftName", "f" ],
                [ "Fun", [
                    [ "Block", [] ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    /** Here const is a pure modifier to the function macro which has its own name. */
    @Test
    fun constFunction() = assertAst(
        input = "const function f() {}",
        wantJson = """
        [ "Call", [
            [ "RightName", "@" ],
            [ "RightName", "const" ],
            [ "Call", [
                [ "RightName", "function" ],
                [ "Value", "\\word: Symbol" ],
                [ "LeftName", "f" ],
                [ "Fun", [
                    [ "Block", [] ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun callPositions() = assertAst(
        input = "print(foo)",
        //       0         1
        //       012345678901
        wantJson = """
        {
          type: "Call",
          left: 0,
          right: 10,
          children: [
            {
              type: "RightName",
              left: 0,
              right: 5,
              content: "print"
            },
            {
              type: "RightName",
              left: 6,
              right: 9,
              content: "foo"
            }
          ]
        }
        """,
    )

    @Test
    fun varDeclInArgumentList() = assertAst(
        input = """
        fn f(var x) {}
        """.trimIndent(),
        wantJson = """
        [ "Call", [
            [ "RightName", "fn" ],
            [ "Value", "\\word: Symbol" ],
            [ "LeftName", "f" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "var" ],
                [ "Block", [
                    [ "Value", "\\_complexArg_: Symbol" ],
                    [ "Decl", [
                        [ "LeftName", "x" ],
                      ]
                    ]
                  ]
                ]
              ]
            ],
            [ "Fun", [
                [ "Block", []],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun decorationsWithAndWithoutParentheses() = assertAst(
        input = "@D() (fn {}); @D (fn {})",
        wantJson = """
        [ "Block", [
            [ "Call", [
                [ "RightName", "@" ],
                [ "Call", [
                    [ "RightName", "D" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "fn" ],
                    [ "Fun", [
                        [ "Block", [] ]
                      ]
                    ]
                  ]
                ]
              ]
            ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "RightName", "D" ],
                [ "Call", [
                    [ "RightName", "fn" ],
                    [ "Fun", [
                        [ "Block", [] ]
                      ]
                    ]
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun decoratedArg() = assertAst(
        input = "f(@D() (fn {}))",
        wantJson = """
        [ "Call", [
            [ "RightName", "f" ],
            [ "Call", [
                [ "RightName", "@" ],
                [ "Call", [
                    [ "RightName", "D" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "fn" ],
                    [ "Fun", [
                        [ "Block", [] ]
                      ]
                    ]
                  ]
                ]
              ]
            ],
          ]
        ]
        """,
    )

    @Test
    fun decorationWithArgumentOverComplicatedLet() = assertAst(
        input = "@Decorator(arg) let [x, y] = 1",
        wantJson = """
        | [ "Call", [
        |     [ "RightName", "@" ],
        |     [ "Call", [
        |         [ "RightName", "Decorator" ],
        |         [ "RightName", "arg" ],
        |       ]
        |     ],
        |     [ "Decl", [
        |         [ "Call", [
        |             [ "Value", "nym`,`: Function" ],
        |             [ "LeftName", "x" ],
        |             [ "LeftName", "y" ],
        |           ]
        |         ],
        |         [ "Value", "\\init: Symbol" ],
        |         [ "Value", "1: Int32" ],
        |       ]
        |     ]
        |   ]
        | ]
        """.trimMargin(),
    )

    @Test
    fun arrowWithExprBody() = assertAst(
        input = "hi { x: T => x + x }",
        wantJson = """
        [ "Call", [
            [ "RightName", "hi" ],
            [ "Fun", [
                [ "Block", [
                    [ "Value", "\\_complexArg_: Symbol" ],
                    [ "RightName", "x" ],
                    [ "Value", "\\type: Symbol" ],
                    [ "RightName", "T" ],
                ]],
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "+" ],
                        [ "RightName", "x" ],
                        [ "RightName", "x" ],
                    ]]
                ]]
            ]]
        ]]
        """,
    )

    @Test
    fun labeledDoWhile() = assertAst(
        input = "label : do { a = b } while ( c = d )",
        wantJson = """
        [ "Block", [
            [ "Value", "\\label: Symbol" ],
            [ "LeftName", "label" ],
            [ "Call", [
                [ "RightName", "do" ],
                [ "Fun", [
                    [ "Block", [
                        [ "Call", [
                            [ "RightName", "=" ],
                            [ "RightName", "a" ],
                            [ "RightName", "b" ],
                          ]
                        ]
                      ]
                    ]
                  ]
                ],
                [ "Value", [ "callJoin:", "Symbol" ] ],
                [ "Value", "\\while: Symbol" ],
                [ "Block", [
                    [ "Value", "\\_complexArg_: Symbol" ],
                    [ "RightName", "c" ],
                    [ "Value", "\\default: Symbol" ],
                    [ "RightName", "d" ],
                  ]
                ]
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun multilineStringIndentation() = assertAst(
        input = $$"""
            |/* some stuff */ $${"\"\"\""}
            |    "First line
            |    "  Second ${"      single line string"}
            |    "Third line ${
            |        $${"\"\"\""}
            |            "This is not part \n        of the same string group
            |    }
            |    "Fourth line
            |    "Fifth line
        """.trimMargin(),
        wantJson = """
        [ "Block", [
            [ "Call", [
                [ "Value", "REM: Function" ],
                [ "Value", "\"some stuff\": String" ],
                [ "Value", "null: Null" ],
                [ "Value", "false: Boolean" ],
              ]
            ],
            [ "Call", [
                [ "RightName", "cat" ],
                [ "Value", { stateVector: "First line\n  Second ", typeTag: "String" } ],
                [ "Call", [
                    [ "RightName", "cat" ],
                    [ "Value", { stateVector: "      single line string", typeTag: "String" } ],
                  ]
                ],
                [ "Value", { stateVector: "\nThird line ", typeTag: "String" } ],
                [ "Call", [
                    [ "RightName", "cat" ],
                    [ "Value", {
                        stateVector: "This is not part ",
                        typeTag: "String"
                    } ],
                    [ "Value", {
                        stateVector: "\n",
                        typeTag: "String"
                    } ],
                    [ "Value", {
                        stateVector: "        of the same string group",
                        typeTag: "String"
                    } ],
                  ]
                ],
                [ "Value", { stateVector: "\nFourth line\nFifth line", typeTag: "String" } ],
              ]
            ]
          ]
        ]
        """,
    )

    @Test
    fun runOfQuotesInQuadrupleQuotedString() {
        val q3 = "\"\"\""
        assertAst(
            input = """
                |$q3
                |"A multi-quoted string with
                |"a run of 3 embedded quotes( $q3 )
            """.trimMargin(),
            wantJson = """
                |[ "Call", [
                |    [ "RightName", "cat" ],
                |    [ "Value", [
                |        "A multi-quoted string with\na run of 3 embedded quotes( \"\"\" )", "String"
                |      ]
                |    ]
                |  ]
                |]
            """.trimMargin(),
        )
    }

    @Test
    fun json() = assertAst(
        startProduction = "Json",
        input = """
            |{
            |  "array": [1, -2, 3.0],
            |  "arrayOfOneAndEmptyArray": [[]],
            |  "objOfOneAndEmptyObj": { "empty": {} },
            |  "str": "foo\u0020-bar",
            |  "null": null,
            |  "booleans": [false, true]
            |}
        """.trimMargin(),
        wantJson = """
            |["Call", [
            |    ["RightName", "{}"],
            |    ["Value", "\"array\": String"],
            |    ["Call", [
            |        ["RightName", "[]"],
            |        ["Value", "1: Int32"],
            |        ["Call", [["RightName", "-"], ["Value", "2: Int32"]]],
            |        ["Value", "3.0: Float64"],
            |      ]
            |    ],
            |    ["Value", "\"arrayOfOneAndEmptyArray\": String"],
            |    ["Call", [
            |        ["RightName", "[]"],
            |        ["Call", [
            |            ["RightName", "[]"],
            |          ]
            |        ]
            |      ]
            |    ],
            |    ["Value", "\"objOfOneAndEmptyObj\": String"],
            |    ["Call", [
            |        ["RightName", "{}"],
            |        ["Value", "\"empty\": String"],
            |        ["Call", [
            |            ["RightName", "{}"],
            |          ]
            |        ]
            |      ]
            |    ],
            |    ["Value", "\"str\": String"],
            |    ["Value", "\"foo -bar\": String"],
            |    ["Value", "\"null\": String"],
            |    ["Value", "null: Null"],
            |    ["Value", "\"booleans\": String"],
            |    ["Call", [
            |        ["RightName", "[]"],
            |        ["Value", "false: Boolean"],
            |        ["Value", "true: Boolean"],
            |      ]
            |    ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun genericLetDecl() = assertAst(
        input = """
            |let f<A, B extends Super1 & Super2, in C, out D extends Super3>()
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "let" ],
            |    [ "Value", "\\word: Symbol" ], [ "LeftName", "f" ],
            |
            |    [ "Value", "\\typeArg: Symbol" ],
            |    [ "RightName", "A" ],
            |
            |    [ "Value", "\\typeArg: Symbol" ],
            |    [ "Call", [
            |        [ "RightName", "extends" ],
            |        [ "RightName", "B" ],
            |        [ "Call", [
            |            [ "RightName", "&" ],
            |            [ "RightName", "Super1" ],
            |            [ "RightName", "Super2" ],
            |          ]
            |        ]
            |      ]
            |    ],
            |
            |    [ "Value", "\\typeArg: Symbol" ],
            |    [ "Call", [
            |        [ "RightName", "@in" ],
            |        [ "RightName", "C" ],
            |      ]
            |    ],
            |
            |    [ "Value", "\\typeArg: Symbol" ],
            |    [ "Call", [
            |        [ "RightName", "extends" ],
            |        [ "Call", [
            |            [ "RightName", "@out" ],
            |            [ "RightName", "D" ],
            |          ]
            |        ],
            |        [ "RightName", "Super3" ],
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun restFormals() = assertAst(
        input = "let foo(...bar: String) {}",
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "let" ],
            |    [ "Value", "\\word: Symbol" ],
            |    [ "LeftName", "foo" ],
            |    [ "Block", [
            |        [ "Value", "\\_complexArg_: Symbol" ],
            |        [ "RightName", "bar" ],
            |        [ "Value", "\\...: Symbol" ],
            |        [ "Value", "void: Void" ],
            |        [ "Value", "\\type: Symbol" ],
            |        [ "RightName", "String" ],
            |      ]
            |    ],
            |    [ "Fun", [
            |        [ "Block", [] ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun trailingDot() {
        assertAst(
            input = "a.",
            wantJson = """
            [ "Call", [
                [ "RightName",  "." ],
                [ "RightName",  "a" ]
              ],
              {
                errors: [
                  {
                    template: "TooFewOperands",
                    values: [ "Dot", 2, 1 ],
                    left: 0,
                    right: 2,
                    loc: "test/test.temper"
                  }
                ]
              }
            ]
            """,
        )
    }

    @Test
    fun mapCall() = assertAst(
        input = """ls.map {x => x.toString(10)}.join(", ")""",
        wantJson = """
            |["Call", [
            |    ["Call", [
            |        ["RightName", "."],
            |        ["Call", [
            |             ["Call", [
            |                 ["RightName", "."],
            |                 ["RightName", "ls"],
            |                 ["Value", "\\map: Symbol"],
            |               ]
            |             ],
            |             ["Fun", [
            |                 [ "Block", [
            |                     [ "Value", [ "_complexArg_", "Symbol" ] ],
            |                     [ "RightName", "x" ],
            |                   ]
            |                 ],
            |                 ["Block", [
            |                     ["Call", [
            |                         ["Call", [
            |                             ["RightName", "."],
            |                             ["RightName", "x"],
            |                             ["Value", "\\toString: Symbol"],
            |                           ]
            |                         ],
            |                         ["Value", "10: Int32"],
            |                       ]
            |                     ]
            |                   ]
            |                 ]
            |               ]
            |             ]
            |           ]
            |        ],
            |        ["Value", "\\join: Symbol"],
            |      ]
            |    ],
            |    [ "Call", [
            |        ["RightName", "cat"],
            |        ["Value", "\", \": String"],
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun commentsBeforeStatements() = assertAst(
        input = """
            |// Foo
            |f();
            |// Bar
            |do {
            |  // Baz
            |  g();
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "Value", "REM: Function" ],
            |        [ "Value", "\"Foo\": String" ],
            |        [ "Value", "null: Null" ],
            |        [ "Value", "false: Boolean" ],
            |      ]
            |    ],
            |    [ "Call", [
            |        [ "RightName", "f" ],
            |      ]
            |    ],
            |    [ "Call", [
            |        [ "Value", "REM: Function" ],
            |        [ "Value", "\"Bar\": String" ],
            |        [ "Value", "null: Null" ],
            |        [ "Value", "false: Boolean" ],
            |      ]
            |    ],
            |    [ "Call", [
            |        [ "RightName", "do" ],
            |        [ "Fun", [ [ "Block", [
            |            [ "Call", [
            |                [ "Value", "REM: Function" ],
            |                [ "Value", "\"Baz\": String" ],
            |                [ "Value", "null: Null" ],
            |                [ "Value", "false: Boolean" ],
            |              ]
            |            ],
            |            [ "Call", [
            |                [ "RightName", "g" ],
            |              ]
            |            ],
            |            [ "Value", "void: Void" ],
            |        ] ] ] ]
            |    ] ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun commentsAtEndIgnored() = assertAst(
        // Comments at the end, represented as calls might introduce
        // semantic confusion; we wouldn't want a comment to be
        // captured as a block result.
        // TODO: double-check comments before `break`, valueless `return`.
        input = """
            |// Before
            |f();
            |// After
        """.trimMargin(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "Value", "REM: Function" ],
            |        [ "Value", "\"Before\": String" ],
            |        [ "Value", "null: Null" ],
            |        [ "Value", "false: Boolean" ],
            |      ]
            |    ],
            |    [ "Call", [
            |        [ "RightName", "f" ],
            |      ]
            |    ],
            |    [ "Value", "void: Void" ],
            |    // But no comment for After
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun commentsAtEndOfBlocksIgnored() = assertAst(
        input = """
            |do {
            |  // Foo
            |  g();
            |  // Bar
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "do" ],
            |    [ "Fun", [ [ "Block", [
            |        [ "Call", [
            |            [ "Value", "REM: Function" ],
            |            [ "Value", "\"Foo\": String" ],
            |            [ "Value", "null: Null" ],
            |            [ "Value", "false: Boolean" ],
            |          ]
            |        ],
            |        [ "Call", [
            |            [ "RightName", "g" ],
            |          ]
            |        ],
            |        [ "Value", "void: Void" ],
            |        // But no comment for Bar
            |    ] ] ] ]
            |] ]
        """.trimMargin(),
    )

    @Test
    fun commentsDoNotCrossOverNopToOccupyResultPosition() = assertAst(
        input = """
            |// Foo
            |f();
            |// Bar
            |;
            |// Baz
        """.trimMargin(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "Value", "REM: Function" ],
            |        [ "Value", "\"Foo\": String" ],
            |        [ "Value", "null: Null" ],
            |        [ "Value", "false: Boolean" ],
            |      ]
            |    ],
            |    [ "Call", [
            |        [ "RightName", "f" ],
            |      ]
            |    ],
            |    [ "Value", "void: Void" ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun commentsDoNotInterruptTopLevels() = assertAst(
        input = "public /*annotations*/ f()",
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "@" ],
            |    [ "RightName", "public" ],
            |    [ "Call", [
            |        [ "RightName", "f" ],
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun angleBracketConfusion() = assertAst(
        // This is a use of an angle bracket, not less-than and greater-than.
        input = "or(a < 2, a > 0)",
        wantJson = """
            |[ "Call", [
            |    [ "Value", "error: Function" ],
            |    [ "Call", [
            |        [ "Value", "list: Function" ],
            |        [ "Value", [ "`(Leaf`", "String" ] ],
            |        [ "Value", [ "or", "String" ] ],
            |        [ "Value", [ "`Leaf)`", "String" ] ],
            |        [ "Value", [ "(", "String" ] ],
            |        [ "Value", [ "`(Angle`", "String" ] ],
            |        [ "Value", [ "`(Leaf`", "String" ] ],
            |        [ "Value", [ "a", "String" ] ],
            |        [ "Value", [ "`Leaf)`", "String" ] ],
            |        [ "Value", [ "<", "String" ] ],
            |        [ "Value", [ "`(Comma`", "String" ] ],
            |        [ "Value", [ "`(Leaf`", "String" ] ],
            |        [ "Value", [ "2", "String" ] ],
            |        [ "Value", [ "`Leaf)`", "String" ] ],
            |        [ "Value", [ ",", "String" ] ],
            |        [ "Value", [ "`(Leaf`", "String" ] ],
            |        [ "Value", [ "a", "String" ] ],
            |        [ "Value", [ "`Leaf)`", "String" ] ],
            |        [ "Value", [ "`Comma)`", "String" ] ],
            |        [ "Value", [ ">", "String" ] ],
            |        [ "Value", [ "`Angle)`", "String" ] ],
            |        [ "Value", [ "`(Leaf`", "String" ] ],
            |        [ "Value", [ "0", "String" ] ],
            |        [ "Value", [ "`Leaf)`", "String" ] ],
            |        [ "Value", [ ")", "String" ] ],
            |      ]
            |    ],
            |  ],
            |  {
            |    errors: [
            |      "Expected a TopLevel here!",
            |    ]
            |  }
            |]
        """.trimMargin(),
    )

    @Test
    fun decoratedLetOfParameter() = assertAst(
        input = """
            |for (@D let x of elements) {}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "for" ],
            |    [ "Call", [
            |        [ "RightName", "of" ],
            |        [ "Call", [
            |            [ "RightName", "@" ],
            |            [ "RightName", "D" ],
            |            [ "Decl", [
            |                [ "LeftName", "x" ],
            |              ]
            |            ],
            |          ],
            |        ],
            |        [ "RightName", "elements" ],
            |      ]
            |    ],
            |    [ "Fun", [
            |        [ "Block", [] ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun letOfStatement() = assertAst(
        input = """
            |for (let x of elements) {
            |  body
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "for" ],
            |    [ "Call", [
            |        ["RightName", "of"],
            |        [ "Decl", [
            |            [ "LeftName", "x" ],
            |          ]
            |        ],
            |        [ "RightName", "elements" ],
            |      ]
            |    ],
            |    [ "Fun", [
            |        [ "Block", [
            |            [ "RightName", "body" ],
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun yieldWithEmptyArgList() = assertAst(
        input = """
            |yield();
        """.trimMargin(),
        wantJson = """
            |[ "Block", [
            |    [ "Call", [
            |        [ "RightName", "yield" ],
            |      ]
            |    ],
            |    [ "Value", "void: Void" ],
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun caseKeywordWhenCases() = assertAst(
        input = """
            |when (x) {
            |  case foo(bar) -> y;
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "when" ],
            |    [ "RightName", "x", ],
            |    [ "Fun", [
            |        [ "Block", [
            |            [ "Value", "\\case_case: Symbol" ],
            |            [ "Call", [
            |                [ "Value", "postponedCase: Function" ],
            |                [ "Call", [
            |                    [ "Value", "list: Function" ],
            |                    [ "Value", "\\foo: Symbol" ],
            |                    [ "Value", "\"(\": String" ],
            |                    [ "Value", "\\bar: Symbol" ],
            |                    [ "Value", "\")\": String" ],
            |                  ],
            |                ],
            |              ],
            |            ],
            |            [ "RightName", "y" ],
            |            [ "Value", "void: Void" ],
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )

    @Test
    fun whenCaseBreaks() = assertAst(
        input = """
            |when (x) {
            |  foo, bar -> f();
            |  else -> break;
            |}
        """.trimMargin(),
        wantJson = """
            |[ "Call", [
            |    [ "RightName", "when" ],
            |    [ "RightName", "x", ],
            |    [ "Fun", [
            |        [ "Block", [
            |            [ "Value", "\\case: Symbol" ],
            |            [ "RightName", "foo" ],
            |            [ "Value", "\\case: Symbol" ],
            |            [ "RightName", "bar" ],
            |            [ "Call", [
            |                [ "RightName", "f" ],
            |              ],
            |            ],
            |            [ "Value", "\\default: Symbol" ],
            |            [ "Call", [
            |                [ "RightName", "break" ],
            |              ],
            |            ],
            |            [ "Value", "void: Void" ],
            |          ]
            |        ]
            |      ]
            |    ]
            |  ]
            |]
        """.trimMargin(),
    )
}
