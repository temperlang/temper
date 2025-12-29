@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.PureCallableValue
import lang.temper.builtin.Types
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.env.InterpMode
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.stage.Stage
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.StaySink
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Test

class TypeStageTest {
    @Test
    fun emptyFile() = assertModuleAtStage(
        input = "",
        stage = Stage.Type,
        want = """
        |{
        |  type: {
        |    body:
        |    ```
        |
        |    ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun emptyReplChunk() = assertModuleAtStage(
        input = "",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        |{
        |  type: {
        |    body:
        |    ```
        |    let return__0;
        |    return__0 = void
        |
        |    ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun ifTransformed() = assertModuleAtStage(
        input = "if (c) { f() } else { g() }",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let return__2;
            if (c) {
              return__2 = f();
            } else {
              return__2 = g();
            };

            ```
          }
        }
        """,
    )

    @Test
    fun whileTransformed() = assertModuleAtStage(
        input = "while (c) { f() }",
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            void;
            while (c) {
              f();
            }

            ```
          }
        }
        """,
    )

    @Test
    fun whileTransformedInReplContext() = assertModuleAtStage(
        input = "while (c) { f() }",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let return__1;
            while (c) {
              f();
            };
            return__1 = void

            ```
          }
        }
        """,
    )

    @Test
    fun doWhileTransformed() = assertModuleAtStage(
        input = "do { f() } while (c)",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let return__2;${
            "" // TODO: don't we need this to be var for the assignment below?
        }
            do {
              return__2 = f();
            } while (c);

            ```
          }
        }
        """,
    )

    @Test
    fun doOnceTransformed() = assertModuleAtStage(
        input = "(do { f() })",
        stage = Stage.Type,
        moduleResultNeeded = true,
        want = """
        {
          type: {
            body:
            ```
            let return__0;
            return__0 = f();

            ```
          }
        }
        """,
    )

    @Test
    fun nestedFn() = assertModuleAtStage(
        input = """
            let g() { do { f() } }
            g()
        """.trimIndent(),
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let return__2, @fn g__0;
            g__0 = fn g /* return__5 */{
              void;
              fn__0: do {
                return__5 = f();
              }
            };
            return__2 = g__0();

            ```
          }
        }
        """,
    )

    @Test
    fun builtinInlined() = assertModuleAtStage(
        input = " nym`+` ",
        moduleResultNeeded = true,
        stage = Stage.Run,
        want = """
        {
          run: "fn (nym`+` × 6): Function",
          syntaxMacro: {
            body: [ "Block", [ [ "RightName", "+" ] ] ],
          },
          type: {
            body:
              ```
              let return__0;
              return__0 = (fn (nym`+` × 6))

              ```
          }
        }
        """,
    )

    @Test
    fun minimalForTransformed() = assertModuleAtStage(
        input = "for (;;) {}",
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            while (true) {}

            ```
          }
        }
        """,
    )

    @Test
    fun forWithExpressionParts() = assertModuleAtStage(
        input = "for (init; cond; incr) {}",
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            init;
            for (;
              cond;
              incr) {}

            ```
          }
        }
        """,
    )

    @Test
    fun asCheckWithIncompleteTypeCompleted() = assertModuleAtStage(
        input = "export let noStrings(): Listed<String> { [] as Listed }",
        stage = Stage.Type,
        want = """
            |{
            |  type: {
            |    body:
            |    ```
            |    @fn let `test//`.noStrings;
            |    `test//`.noStrings = (@stay fn noStrings /* return__0 */: (Listed<String>) {
            |        void;
            |        fn__0: do {
            |          var fail#0;
            |## Above, `as Listed`, here `as(..., Listed<String>)`
            |          return__0 = hs(fail#0, as(list(), Listed<String>));
            |          if (fail#0) {
            |            bubble()
            |          };
            |        }
            |    })
            |
            |    ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun jumpToLabel() = assertModuleAtStage(
        input = "break foo",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body: {
              tree:
                [ "Block",
                  [
                    [ "Decl", [
                        [ "LeftName", "return__0" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ],
                      ]
                    ],
                    [ "break", "\\foo", [] ],
                  ],
                  "StructuredFlow",
                ],
              code:
                ```
                let return__0;
                break foo;

                ```
            }
          }
        }
        """,
        // TODO: Why does the assignment `return = void` not happen before the jump?
    )

    @Test
    fun jumpDefaultLabel() = assertModuleAtStage(
        input = "continue",
        moduleResultNeeded = true,
        stage = Stage.Type,
        want = """
        {
          type: {
            body: {
              tree: [ "Block",
                [
                  [ "Decl", [
                      [ "LeftName", "return__0" ],
                      [ "Value", "\\ssa: Symbol" ],
                      [ "Value", "void: Void" ],
                    ]
                  ],
                  [ "continue", [] ],
                ],
                "StructuredFlow",
              ],
              code:
              ```
              let return__0;
              continue;

              ```
            }
          }
        }
        """,
    )

    @Test
    fun forWithIfsAndJumpsTransformed() = assertModuleAtStage(
        input = """
          for (init; cond; incr) {
            if (a) {
              f();
            } else if (b) {
              break;
            } else {
              continue
            }
          }
        """.trimIndent(),

        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            init;
            for (;
              cond;
              incr) {
              if (a) {
                f()
              } else if (b) {
                break;
              }
            }

            ```
          }
        }
        """,
    )

    @Test
    fun minimalForOfTransformed() = assertModuleAtStage(
        input = """for (let x of ["foo"]) { console.log(x) }""",
        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let console#0;
            console#0 = getConsole();
            do_bind_forEach(list("foo"))(fn (x__0) /* return__0 */{
                do_bind_log(console#0)(x__0);
                return__0 = void
            });

            ```
          }
        }
        """,
    )

    @Test
    fun breakInForOf() = repeat(2) {
        assertModuleAtStage(
            input = """
                |for (let x of ["a", "b", "c", "d"]) {
                |  if (x == "c") { break }
                |  console.log(x)
                |}
            """.trimMargin(),
            stage = Stage.Run,
            // Showing extra detail helps clarify that `List.forEach`'s <T> gets rebound to String.
            pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
            want = """
                |{
                |  type: {
                |    body:
                |    ```
                |    let console#0 ⦂ Console;
                |    console#0 = getConsole();
                |    let this__0: List<String>;
                |    this__0 = list ⋖ String ⋗("a", "b", "c", "d");${
                "" // Here we start the inlined callee body.
            }
                |    let n__0 ⦂ Int32;
                |    n__0 = do_get_length(this__0);
                |    var i__0 ⦂ Int32;
                |    i__0 = 0;
                |    while (i__0 < n__0) {
                |      let el__0: String;
                |      el__0 = do_bind_get(this__0)(i__0);
                |      i__0 = i__0 + 1;${
                "" // Here we start the inlined block lambda parameters.
            }
                |      let x__0 ⦂ String;
                |      x__0 = el__0;${
                "" // Here we start the inlined block lambda body.
                // Note the absence of a void-like return declaration.
            }
                |      if (x__0 == "c") {
                |        break;
                |      };
                |      do_bind_log(console#0)(x__0);${
                "" // Did not inline `return__0 = void`.  Not ok for local vars.
                // Here's the end of the inlined block lambda.
            }
                |    };${
                "" // Here's the end of the inlined callee body.  No `return__0 = void` here either.
            }

                |    ```
                |  },
                |  run: "void: Void",
                |  stdout: ```
                |    a
                |    b
                |
                |    ```
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun breakFromInnerLoopToOuter() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |do {
            |  outer: while (true) {
            |    while (true) {
            |      break outer;
            |    }
            |    console.log("no");
            |  }
            |  console.log("yes");
            |}
        """.trimMargin(),
        // The reason the loops show up below is that
        // simplifyControlFlow does not try to determine
        // whether every body path breaks and therefore
        // the condition is never re-checked.
        want = """
            |{
            |  type: {
            |    body: ```
            |        outer__0: do {
            |          body#0: do {}
            |        };
            |        do_bind_log(getConsole())("yes");
            |
            |        ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun continueFromInnerLoopToOuter() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |do {
            |  var i: Int = 0;
            |  outer: while (i < 5) {
            |    i += 1;
            |    while (i < 10) {
            |      if (i < 6) {
            |        continue outer;
            |      }
            |      i += 10;
            |    }
            |  }
            |  i
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  run: [5, "Int32"]
            |}
        """.trimMargin(),
    )

    @Test
    fun blockPulledThroughDecl() = assertModuleAtStage(
        input = "let x: int = (do { if (a) { 42 } else { 0 } })",

        stage = Stage.Type,
        want = """
        {
          type: {
            body:
            ```
            let x__1: int;
            if (a) {
              x__1 = 42
            } else {
              x__1 = 0
            };

            ```
          }
        }
        """,
    )

    @Test
    fun desugarPrefixOperators() = assertModuleAtStage(
        stage = Stage.Run,
        input = "do { var x: Int = 3; ++x; ++x; --x; x }",
        moduleResultNeeded = true,
        want = """
        {
            run: "4: Int32",
            define : {
              body:
              ```
              do (@stay fn {
                  var x__0: Int32;
                  x__0 = 3;
                  x__0 = x__0 + 1;
                  x__0 = x__0 + 1;
                  x__0 = x__0 - 1;
                  x__0
              })

              ```
            }
        }
        """,
    )

    @Test
    fun desugarPostfixOperators() = assertModuleAtStage(
        stage = Stage.Run,
        // The increment from the last one intentionally doesn't show in the result
        input = "do { var x: Int = 3; x++; x++; x--; x++ }",
        moduleResultNeeded = true,
        want = """
        {
          run: "4: Int32",
          define : {
            body:
            ```
            do (@stay fn {
                var x__4: Int32;
                x__4 = 3;
                do {
                  let postfixReturn#0;
                  postfixReturn#0 = x__4;
                  x__4 = x__4 + 1;
                  postfixReturn#0
                };
                do {
                  let postfixReturn#1;
                  postfixReturn#1 = x__4;
                  x__4 = x__4 + 1;
                  postfixReturn#1
                };
                do {
                  let postfixReturn#2;
                  postfixReturn#2 = x__4;
                  x__4 = x__4 - 1;
                  postfixReturn#2
                };
                do {
                  let postfixReturn#3;
                  postfixReturn#3 = x__4;
                  x__4 = x__4 + 1;
                  postfixReturn#3
                }
            })

            ```
          }
        }
        """,
    )

    @Test
    fun desugarCompoundAssignments() = assertModuleAtStage(
        stage = Stage.Run,
        input = "do { var x: Int = 10; x -= 9; x += 4; x *= 3; x /= 5; x }",
        //               10       1       5      15       3  3
        moduleResultNeeded = true,
        want = """
        {
            "syntaxMacro": {
                "body":
                ```
                do (fn {
                    var x__0: Int = 10;
                    x__0 = x__0 - 9;
                    x__0 = x__0 + 4;
                    x__0 = x__0 * 3;
                    x__0 = x__0 / 5;
                    x__0
                })

                ```
            },
            "type": {
                "body":
                ```
                let return__0;
                var t#0, fail#0, x__0: Int32;
                x__0 = 10;
                x__0 = x__0 - 9;
                x__0 = x__0 + 4;
                x__0 = x__0 * 3;
                t#0 = hs(fail#0, x__0 / 5);
                if (fail#0) {
                  bubble()
                };
                x__0 = t#0;
                return__0 = x__0;

                ```
            },
            "run": "3: Int32"
        }
        """,
    )

    @Test
    fun desugarCompoundAssignmentsComplexRHS() = assertModuleAtStage(
        stage = Stage.Run,
        input = "var x: Int = 10; var y: Int = 1; x += (y + 1); x",
        want = """
            |{
            |  run : "12: Int32",
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
    )

    @Test
    @Suppress("SpellCheckingInspection") // Fixing "Brahmagupta's" triggers other lint rules
    fun brahmaguptasRevenge() = assertModuleAtStage(
        stage = Stage.Run,
        input = "(0 / 0) orelse 0",
        moduleResultNeeded = true,
        want = """
        {
          type: {
            body:
              ```
              let return__0;
              var t#1, fail#3;
              orelse#1: {
                t#1 = hs(fail#3, 0 / 0);
                if (fail#3) {
                  break orelse#1;
                };
                return__0 = t#1
              } orelse {
                return__0 = 0
              };

              ```
          },
          run: "0: Int32"
        }
        """,
    )

    @Test
    fun returningUntyped() = assertModuleAtStage(
        stage = Stage.Type,
        input = "fn { return 42 }",
        moduleResultNeeded = true,
        want = """
        {
          disAmbiguate: {
            body:
              ```
              fn(fn {
                  return 42
              })

              ```
          },
          syntaxMacro: {
            body:
              ```
              fn /* return__0 */{
                fn__1: do {
                  do {
                    return__0 = 42;
                    break(\label, fn__1)
                  }
                }
              }

              ```
          },
          type: {
            body:
              ```
              let return__2;
              return__2 = (@stay fn /* return__0 */{
                  fn__1: do {
                    return__0 = 42
                  }
              })

              ```
          }
        }
        """,
    )

    @Test
    fun returningWithReturnTypeMetadata() = assertModuleAtStage(
        stage = Stage.Type,
        input = "fn () : Int { return 42 }",
        moduleResultNeeded = true,
        want = """
        {
          disAmbiguate: {
            body:
              ```
              fn(\outType, Int, fn {
                  return 42
              })

              ```
          },
          syntaxMacro: {
            body:
              ```
              fn /* return__0 */: (Int) {
                fn__1: do {
                  do {
                    return__0 = 42;
                    break(\label, fn__1)
                  }
                }
              }

              ```
          },
          type: {
            body:
              ```
              let return__2;
              return__2 = (@stay fn /* return__0 */: Int32 {
                  fn__1: do {
                    return__0 = 42
                  }
              })

              ```
          }
        }
        """,
    )

    @Test
    fun returnThatViolatesReturnType() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = "fn () : Boolean { return 42 }",
        moduleResultNeeded = true,
        want = """
        {
          disAmbiguate: {
            body:
              ```
              fn(\outType, Boolean, fn {
                  return 42
              })

              ```
          },
          syntaxMacro: {
            body:
              ```
              fn /* return__0 */: (Boolean) {
                fn__1: do {
                  do {
                    return__0 = 42;
                    break(\label, fn__1)
                  }
                }
              }

              ```
          },
          type: {
            body:
              ```
              let return__2;
              return__2 = (@stay fn /* return__0 */: Boolean {
                  fn__1: do {
                    return__0 = 42
                  }
              })

              ```
          },
          generateCode: {
            body:
              ```
              let return__2;
              return__2 = (@stay fn /* return__0 */: Boolean {
                  return__0 = 42
              })

              ```
          },
          errors: [
            "Cannot assign to Boolean from Int32!",
            "Expected subtype of Boolean, but got Int32!",
          ]
        }
        """,
    )

    @Test
    fun deepStringToString() = assertModuleAtStage(
        stage = Stage.Type,
        // Only one of these toStrings is actually needed.
        input = "fn (i: Int): String { i.toString().toString().toString() }",
        want = """
        {
          type: {
            body:
              ```
              fn (i__0 /* aka i */: Int32) /* return__0 */: String {
                void;
                fn__0: do {
                  return__0 = do_bind_toString(do_bind_toString(do_bind_toString(i__0)())())();
                }
              }

              ```
          }
        }
        """,
    )

    @Test
    fun fnWithMixedReturnAndImpliedResultPaths() = assertModuleAtStage(
        stage = Stage.Type,
        input = "fn(b) { if (b) { return 1 } 0 }",
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body:
              ```
              fn (b__0 /* aka b */) /* return__1 */{
                fn__2: do {
                  if(b__0, fn {
                      do {
                        return__1 = 1;
                        break(\label, fn__2)
                      }
                  });
                  0
                }
              }

              ```
          },
          type: {
            body:
              ```
              let return__3;
              return__3 = (@stay fn (b__0 /* aka b */) /* return__1 */{
                  fn__2: do {
                    if (b__0) {
                      return__1 = 1;
                      break fn__2;
                    };
                    return__1 = 0
                  }
              })

              ```
          }
        }
        """,
    )

    @Test
    fun yieldsSeparated() = assertModuleAtStage(
        stage = Stage.Type,
        provisionModule = { module: Module, _ ->
            val input = $$"""
                |ignore { (): GeneratorResult<Empty> extends GeneratorFn =>
                |  while (true) {
                |    "${ 123 }";
                |    yield;
                |  }
                |}
            """.trimMargin()
            module.addEnvironmentBindings(
                mapOf(BuiltinName(ImpureIgnoreFn.name) to Value(ImpureIgnoreFn)),
            )
            module.deliverContent(
                ModuleSource(
                    filePath = testCodeLocation, fetchedContent = input,
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
        },
        want = """
        {
          type: {
            body: {
              code:
              ```
              ignore(fn /* return__0 */{
                  return__0 = adaptGeneratorFnSafe(@wrappedGeneratorFn fn /* return__1 */: (GeneratorResult<Empty>) implements GeneratorFn {
                      return__1 = implicits.doneResult<Empty>();
                      void;
                      while (true) {
                        cat(do_bind_toString(123)());
                        yield()
                      }
                  })
              });

              ```,

              tree: [ "Block", [
                  [ "Call", [
                      [ "RightName", "ignore" ],
                      [ "Fun", [
                          [ "Value", "\\returnDecl: Symbol" ],
                          [ "Decl", [
                              [ "LeftName", "return__0" ],
                              [ "Value", "\\ssa: Symbol" ],
                              [ "Value", "void: Void" ],
                            ]
                          ],

                          [ "Block", [
                              [ "Call", [
                                  [ "Value", "nym`=`: Function" ],
                                  [ "LeftName", "return__0" ],

                                  [ "Call", [
                                      [ "Value", "adaptGeneratorFnSafe: Function" ],
                                      [ "Fun", [
                                          [ "Value", "\\returnDecl: Symbol" ],
                                          [ "Decl", [
                                              [ "LeftName", "return__1" ],
                                              [ "Value", "\\type: Symbol" ],
                                              [ "Value", "GeneratorResult<Empty>: Type" ],
                                              [ "Value", "\\ssa: Symbol" ],
                                              [ "Value", "void: Void" ],
                                            ]
                                          ],

                                          [ "Value", "\\super: Symbol" ],
                                          [ "Value", "GeneratorFn: Type" ],

                                          [ "Value", "\\wrappedGeneratorFn: Symbol" ],
                                          [ "Value", "void: Void" ],

                                          [ "Block", [
                                              [ "Call", [
                                                  [ "Value", "nym`=`: Function" ],
                                                  [ "LeftName", "return__1" ],
                                                  [ "Call", [
                                                      [ "Call", [
                                                          [ "Value", "nym`<>`: Function" ],
                                                          [ "RightName", "implicits.doneResult" ],
                                                          [ "Value", "Empty: Type" ],
                                                        ]
                                                      ]
                                                    ]
                                                  ]
                                                ]
                                              ],
                                              [ "Value", "void: Void" ],
                                              [ "while",
                                                [ "Value", "true: Boolean" ],
                                                [
                                                  [ "stmt-block", [
                                                      [ "Call", [
                                                          [ "Value", "cat: Function" ],
                                                          [ "Call", [
                                                              [ "Call", [
                                                                  [ "Value", "do_bind_toString: Function" ],
                                                                  [ "Value", "123: Int32" ],
                                                                ]
                                                              ],
                                                            ]
                                                          ],
                                                        ]
                                                      ],
                                                      [ "Call", [
                                                          [ "Value", "yield: Function" ],
                                                        ]
                                                      ],
                                                    ]
                                                  ],
                                                  [ "stmt-block", [] ],
                                                ]
                                              ],
                                            ],
                                            "StructuredFlow",
                                          ],
                                        ]
                                      ]
                                    ]
                                  ]
                                ]
                              ],
                            ],
                            "StructuredFlow",
                          ]
                        ],
                      ]
                    ],
                  ],
                  [ "Value", "void: Void" ],
                ],
                "StructuredFlow",
              ],
            }
          },
        }
        """,
    )

    @Test
    fun functionWithArgumentsAndReturnType() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
        fn sum2i(x: Int, y: Int): Int {
          return x + y;
        }
        """,
        moduleResultNeeded = true,
        want = """
        {
            "syntaxMacro": {
                "body":
                ```
                do {
                  @fn let sum2i__0;
                  sum2i__0 = fn sum2i(x__0 /* aka x */: Int, y__0 /* aka y */: Int) /* return__0 */: (Int) {
                    fn__0: do {
                      do {
                        return__0 = x__0 + y__0;
                        break(\label, fn__0)
                      };
                    }
                  };
                  sum2i__0
                }

                ```
            },
            "type": {
                "body":
                ```
                let return__1, @fn sum2i__0;
                sum2i__0 = (@stay fn sum2i(x__0 /* aka x */: Int32, y__0 /* aka y */: Int32) /* return__0 */: Int32 {
                    fn__0: do {
                      return__0 = x__0 + y__0
                    }
                });
                return__1 = (fn sum2i)

                ```
            }
        }
        """,
    )

    @Test
    fun typeMismatchInCall() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
        |let i(x: Int) { x }
        |i("0") orelse console.log("bad");
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
        |{
        |  type: {
        |    body:
        |      ```
        |      let return__0;
        |      var t#0;
        |      t#0 = getConsole();
        |      @fn let i__0;
        |      i__0 = (@stay fn i(x__0 /* aka x */: Int32) /* return__1 */{
        |          fn__0: do {
        |            return__1 = x__0
        |          }
        |      });
        |      orelse#0: {
        |## We don't inline the below which has a type error even though its
        |## body's semantics would result in "0" if x could be bound.
        |        (fn i)("0")
        |      } orelse {
        |        do_bind_log(t#0)("bad");
        |      };
        |      return__0 = void
        |
        |      ```
        |  }
        |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun ifNotNullMulti() = assertModuleAtStage(
        stage = Stage.Type,
        // TODO Support auto-not-null on multiple conditions. Or after blocks with early exit. Or ...
        // TODO Meanwhile, this provides some exploration fodder for such work in the future.
        input = """
            |export let multi(a: Int?, b: Int?): Int {
            |  if (a != null && b != null) { return a * b; }
            |  0
            |}
            |export let post(a: Int?): Int {
            |  if (a == null) { return 0; }
            |  2 * a
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  type: {
            |    body:
            |      ```
            |      let return__2, @fn `test//`.multi, @fn `test//`.post;
            |      `test//`.multi = (@stay fn multi(a__0 /* aka a */: Int32?, b__0 /* aka b */: Int32?) /* return__0 */: Int32 {
            |          var t#0;
            |          fn__0: do {
            |            if (!isNull(a__0)) {
            |              t#0 = !isNull(b__0)
            |            } else {
            |              t#0 = false
            |            };
            |            if (t#0) {
            |              return__0 = a__0 * b__0;
            |              break fn__0;
            |            };
            |            return__0 = 0
            |          }
            |      });
            |      `test//`.post = (@stay fn post(a__1 /* aka a */: Int32?) /* return__1 */: Int32 {
            |          fn__1: do {
            |            if (isNull(a__1)) {
            |              return__1 = 0;
            |              break fn__1;
            |            };
            |            return__1 = 2 * a__1
            |          }
            |      });
            |      return__2 = void
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun ifVsNestedIf() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |export let useIf(i: Int) { if (i < 0) { -1 } else if (i > 0) { 1 } else { 0 } }
            |export let useIfElse(i: Int) { if (i < 0) { -1 } else { if (i > 0) { 1 } else { 0 } } }
            |export let a = if (true) { 1 } else { 0 };
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body:
            |      ```
            |      @fn let `test//`.useIf, @fn `test//`.useIfElse;
            |      `test//`.useIf = fn useIf(i__0 /* aka i */: Int32) {
            |        fn__0: do {
            |          if(i__0 < 0, @stay fn {
            |              -1
            |            }, \else_if, fn (f#0) {
            |              f#0(i__0 > 0, @stay fn {
            |                  1
            |                }, \else, fn (f#1) {
            |                  f#1(@stay fn {
            |                      0
            |                  })
            |              })
            |          })
            |        }
            |      };
            |      `test//`.useIfElse = fn useIfElse(i__1 /* aka i */: Int32) {
            |        fn__1: do {
            |          if(i__1 < 0, @stay fn {
            |              -1
            |            }, \else, fn (f#2) {
            |              f#2(fn {
            |                  if(i__1 > 0, @stay fn {
            |                      1
            |                    }, \else, fn (f#3) {
            |                      f#3(@stay fn {
            |                          0
            |                      })
            |                  })
            |              })
            |          })
            |        }
            |      };
            |      let `test//`.a;
            |      `test//`.a = if(true, @stay fn {
            |          1
            |        }, \else, fn (f#4) {
            |          f#4(@stay fn {
            |              0
            |          })
            |      });
            |
            |      ```
            |  },
            |  type: {
            |    body:
            |      ```
            |      @fn let `test//`.useIf, @fn `test//`.useIfElse;
            |      `test//`.useIf = (@stay fn useIf(i__0 /* aka i */: Int32) /* return__0 */{
            |          void;
            |          fn__0: do {
            |            if (i__0 < 0) {
            |              return__0 = -1
            |            } else if (i__0 > 0) {
            |              return__0 = 1
            |            } else {
            |              return__0 = 0
            |            };
            |          }
            |      });
            |      `test//`.useIfElse = (@stay fn useIfElse(i__1 /* aka i */: Int32) /* return__1 */{
            |          void;
            |          fn__1: do {
            |            if (i__1 < 0) {
            |              return__1 = -1
            |            } else {
            |              if (i__1 > 0) {
            |                return__1 = 1
            |              } else {
            |                return__1 = 0
            |              };
            |            };
            |          }
            |      });
            |      let `test//`.a;
            |      `test//`.a = 1
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun ifElseResultNeeded() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |if (a == b) { c } else { d }
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
        {
          type: {
            body:
              ```
              let return__0;
              if (a == b) {
                return__0 = c
              } else {
                return__0 = d
              };

              ```
          }
        }
        """,
    )

    @Test
    fun ifIsNullResultNeeded() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |export let thing(x: Int?): Int { if (x == null) { 0 } else { x + 1 } }
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
        {
          type: {
            body:
              ```
              let return__0, @fn `test//`.thing;
              `test//`.thing = (@stay fn thing(x__0 /* aka x */: Int32?) /* return__1 */: Int32 {
                  void;
                  fn__0: do {
                    if (isNull(x__0)) {
                      return__1 = 0
                    } else {
                      return__1 = notNull(x__0) + 1
                    };
                  }
              });
              return__0 = void

              ```
          }
        }
        """,
    )

    @Test
    fun staticAccess() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |class C { public static let foo = "foo"; }
            |C.foo == "foo"
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "true: Boolean"
            |}
        """.trimMargin(),
    )

    @Test
    fun amazingEvaporatingClasses() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
        interface I {
          method()
        }
        class C(private property) extends I {
          public method { property }
        }
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          type: {
            body:
            ```
            let return__15;
            @fn @stay @fromType(I__0) let method__6;
            method__6 = fn method(@impliedThis(I__0) this__2: I__0) /* return__16 */{
              fn__0: do {
                pureVirtual()
              }
            };
            @typeDecl(I__0) @stay let I__0;
            I__0 = type (I__0);
            @typeDecl(C__1) @stay let C__1;
            C__1 = type (C__1);
            @constructorProperty @visibility(\private) @stay @fromType(C__1) let property__9;
            @visibility(\public) @fn @stay @fromType(C__1) let method__10;
            method__10 = (@stay fn method(@impliedThis(C__1) this__3: C__1) /* return__17 */{
                fn__1: do {
                  return__17 = getp(property__9, this__3)
                }
            });
            @fn @visibility(\public) @stay @fromType(C__1) let constructor__12;
            constructor__12 = (@stay fn constructor(@impliedThis(C__1) this__13: C__1, property__14 /* aka property */) /* return__18 */: Void {
                setp(property__9, this__13, property__14);
                return__18 = void
            });
            return__15 = type (C__1)

            ```
          }
        }
        """,
    )

    @Test
    fun explicitTypeArgumentsRemainInTree() = assertModuleAtStage(
        stage = Stage.Type,
        want = """
        {
          disAmbiguate: {
            body: {
              code:
                ```
                echo<Int>(42)

                ```,
              tree:
                  [ "Block", [
                      [ "Call", [
                          [ "Call", [
                              [ "Value", "nym`<>`: Function" ],
                              [ "RightName", "echo" ],
                               [ "RightName", "Int" ],
                             ]
                           ],
                           [ "Value", "42: Int32" ],
                         ]
                       ]
                     ]
                   ]
            }
          },
          type: {
            body:
                // What's important here is that the tree still has the type arguments.
                // No intervening stage has inlined the result of the call to 42.
                // TODO: Allow the interpreter to collapse calls to generic functions with
                // explicit arguments, just do not allow it to inline away the explicit arguments
                // but leave the call.
                ```
                let return__0;
                return__0 = echo<Int32>(42);

                ```
          }
        }
        """.trimIndent(),
        moduleResultNeeded = true,
    ) { module, _ ->
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation,
                fetchedContent = "echo<Int>(42)",
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("echo") to Value(
                    object : PureCallableValue, NamedBuiltinFun {
                        override val name: String = "echo"

                        override fun invoke(
                            args: ActualValues,
                            cb: InterpreterCallback,
                            interpMode: InterpMode,
                        ): PartialResult =
                            if (cb.stage == Stage.Run) {
                                if (args.size >= 1) {
                                    args[0]
                                } else {
                                    void
                                }
                            } else {
                                NotYet
                            }

                        override val sigs: List<Signature2>? = null

                        override fun addStays(s: StaySink) = Unit

                        override val callMayFailPerSe: Boolean = false
                    },
                ),
            ),
        )
    }

    @Test
    fun implicitReturnForDocGenre() = assertModuleAtStage(
        stage = Stage.Type,
        genre = Genre.Documentation,
        input = """
            |let f(): Void {}
            |let g(b: Boolean): Int {
            |  if (b) { 42 } else { 0 }
            |}
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body: ```
            |    @fn let f__0, @fn g__0;
            |    f__0 = (@stay fn f /* return__0 */: (preserve(Void, type (Void))) {});
            |    g__0 = (@stay fn g(b__0 /* aka b */: preserve(Boolean, type (Boolean))) /* return__1 */: (preserve(Int, type (Int32))) {
            |        preserve(if, ifForDocGen)(b__0, do {
            |            returnForDocGen(42)
            |          }, do {
            |            returnForDocGen(0)
            |        })
            |    });
            |
            |    ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun skippedAndSwappedArgs() = assertModuleAtStage(
        stage = Stage.Type,
        // Purposely include named args with side effects to show it's ok because temporaries.
        input = """
        |class Hi(private a: Int = 1, private b: Int = 2, private c: Int = 3) {}
        |var n = 1;
        |{ c: do { n += 1; n }, a: n + 1 };
        """.trimMargin(),
        want = """
        |{
        |    "type": {
        |        "body":
        |        ```
        |        var t#0;
        |        @constructorProperty @visibility(\private) @stay @fromType(Hi__0) let a__0: Int32;
        |        @constructorProperty @visibility(\private) @stay @fromType(Hi__0) let b__0: Int32;
        |        @constructorProperty @visibility(\private) @stay @fromType(Hi__0) let c__0: Int32;
        |        @fn @visibility(\public) @stay @fromType(Hi__0) let constructor__0;
        |        constructor__0 = (@stay fn constructor(@impliedThis(Hi__0) this__0: Hi__0, @optional(true) a__1 /* aka a */: Int32?, @optional(true) b__1 /* aka b */: Int32?, @optional(true) c__1 /* aka c */: Int32?) /* return__0 */: Void {
        |            let a__2 /* aka a */: Int32;
        |            if (isNull(a__1)) {
        |              a__2 = 1
        |            } else {
        |              a__2 = notNull(a__1)
        |            };
        |            let b__2 /* aka b */: Int32;
        |            if (isNull(b__1)) {
        |              b__2 = 2
        |            } else {
        |              b__2 = notNull(b__1)
        |            };
        |            let c__2 /* aka c */: Int32;
        |            if (isNull(c__1)) {
        |              c__2 = 3
        |            } else {
        |              c__2 = notNull(c__1)
        |            };
        |            setp(a__0, this__0, a__2);
        |            setp(b__0, this__0, b__2);
        |            setp(c__0, this__0, c__2);
        |            return__0 = void
        |        });
        |        @typeDecl(Hi__0) @stay let Hi__0;
        |        Hi__0 = type (Hi__0);
        |        var n__0;
        |        n__0 = 1;
        |        n__0 = n__0 + 1;
        |        t#0 = n__0;
        |        new Hi__0(n__0 + 1, null, t#0);
        |
        |        ```
        |    }
        |}
        """.trimMargin(),
    )

    @Test
    fun deepDefaultMethod() = assertModuleAtStage( // See issue#1305
        stage = Stage.Run,
        input = """
            |interface A { public hi(): String { "hello" } }
            |interface B extends A {}
            |class C extends B {}
            |new C().hi()
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  run: ["hello", "String"]
            |}
        """.trimMargin(),
    )

    @Test
    fun bareReturnVoid() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |let f(returnEarly: Boolean): Void {
            |  if (returnEarly) { return }
            |  console.log("Did not return early");
            |}
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body:
            |    ```
            |    let console#0;
            |    console#0 = getConsole();
            |    @fn let f__0;
            |    f__0 = fn f(returnEarly__0 /* aka returnEarly */: Boolean) /* return__1 */: Void {
            |      void;
            |      fn__0: do {
            |        if (returnEarly__0) {
            |          return__1 = void;
            |          break fn__0;
            |        };
            |        do_bind_log(console#0)("Did not return early");
            |        return__1 = void
            |      }
            |    }
            |
            |    ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun makeEmptyExplicitVoid() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |let f(): Void {}
            |let g(): Void { f() }
            |g();
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body:
            |    ```
            |    @fn let f__0, @fn g__0;
            |    f__0 = (@stay fn f /* return__1 */: Void {
            |        return__1 = void;
            |        fn__0: do {}
            |    });
            |    g__0 = (@stay fn g /* return__2 */: Void {
            |        fn__1: do {
            |          return__2 = void
            |        }
            |    })
            |
            |    ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun issue1828MissingReturn() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |let a(i: List<Int>): List<Int> {
            |  if (i.length == 0) { return [] }; // One explicit return
            |  let n = new ListBuilder<Int>();
            |  n.map { (it): Int => 2 * it }      // One implied return
            |}
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body: ```
            |      @fn let a__0;
            |      a__0 = fn a(i__0 /* aka i */: List<Int32>) /* return__1 */: (List<Int32>) {
            |        void;
            |        fn__0: do {
            |          if (do_get_length(i__0) == 0) {
            |            return__1 = list();
            |            break fn__0;
            |          };
            |          let n__0;
            |          n__0 = new ListBuilder<Int32>();
            |          return__1 = do_bind_map(n__0)(@stay fn (it__0 /* aka it */) /* return__2 */: Int32 {
            |              return__2 = 2 * it__0
            |          });
            |        }
            |      }
            |
            |      ```,
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun extensionHintsResolved() = assertModuleAtStage(
        stage = Stage.Type,
        moduleResultNeeded = true,
        input = """
            |@extension("isZero")
            |let isZero(x: Int): Boolean { x == 0 }
            |class Zero {
            |  public let isZero(): Boolean { true }
            |}
            |
            |0.isZero() && new Zero().isZero()
            |
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body: ```
            |      let return__0, @fn @extension("isZero") isZero__0;
            |      @typeDecl(Zero__0) @stay let Zero__0;
            |      Zero__0 = type (Zero__0);
            |      isZero__0 = (@stay fn isZero(x__0 /* aka x */: Int32) /* return__1 */: Boolean {
            |          fn__0: do {
            |            return__1 = x__0 == 0
            |          }
            |      });
            |      @visibility(\public) @fn @stay @fromType(Zero__0) let isZero__1;
            |      isZero__1 = (@stay fn isZero(@impliedThis(Zero__0) this__0: Zero__0) /* return__2 */: Boolean {
            |          fn__1: do {
            |            return__2 = true
            |          }
            |      });
            |      @fn @visibility(\public) @stay @fromType(Zero__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(Zero__0) this__1: Zero__0) /* return__3 */: Void {
            |          return__3 = void
            |      });
            |      if (isZero__0(0)) {
            |        return__0 = do_bind_isZero(new Zero__0())();
            |      } else {
            |        return__0 = false
            |      };
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun staticExtensionHintsResolved() = assertModuleAtStage(
        stage = Stage.Type,
        moduleResultNeeded = true,
        input = """
            |@staticExtension(Int, "isZero")
            |let isZero(x: Int): Boolean { x == 0 }
            |
            |Int.isZero(0) && isZero(0)
            |
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      @fn @staticExtension({
            |          class: Pair__0, key: type (Int32), value: "isZero"
            |      }) let isZero__0;
            |      isZero__0 = (@stay fn isZero(x__0 /* aka x */: Int32) /* return__0 */: Boolean {
            |          fn__0: do {
            |            x__0 == 0
            |          }
            |      });
            |      if((do_bind_isZero[static isZero__0])(type (Int32))(0), @stay fn {
            |          true
            |        }, \else, fn (f#0) {
            |          f#0(@stay fn {
            |              false
            |          })
            |      })
            |
            |      ```,
            |  },
            |  type: {
            |    body: ```
            |      let return__1, @fn @staticExtension({
            |          class: Pair__0, key: type (Int32), value: "isZero"
            |      }) isZero__0;
            |      isZero__0 = (@stay fn isZero(x__0 /* aka x */: Int32) /* return__0 */: Boolean {
            |          fn__0: do {
            |            return__0 = x__0 == 0
            |          }
            |      });
            |      if (isZero__0(0)) {
            |        return__1 = true
            |      } else {
            |        return__1 = false
            |      };
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun bindingCalleesNotPulledOut() = assertModuleAtStage(
        input = $$"""
            |let f(hi: String): Void {
            |  let s: String;
            |  console.log((s = "Hello, ${hi}!"));
            |  console.log(s);
            |}
        """.trimMargin(),
        stage = Stage.Type,
        want = """
            |{
            |  type: {
            |    body: ```
            |      let console#0;
            |      console#0 = getConsole();
            |      @fn let f__0;
            |      f__0 = fn f(hi__0 /* aka hi */: String) /* return__0 */: Void {
            |        void;
            |        fn__0: do {
            |          let s__0: String;
            |          s__0 = cat("Hello, ", hi__0, "!");
            |          do_bind_log(console#0)(s__0);
            |          do_bind_log(console#0)(s__0);
            |          return__0 = void
            |        }
            |      }
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun importedExtensionsUsable() = assertModuleAtStage(
        stage = Stage.Type,
        want = """
            |{
            |  type: {
            |    body: ```
            |        @stay @imported(\(`half//`.intHalf)) @fn @extension("half") let intHalf__0;
            |        intHalf__0 = (fn intHalf);
            |        do_bind_log(getConsole())(do_bind_toString(intHalf__0(84))());
            |## extension resolved across module boundaries     ^^^^^^^^^^
            |
            |        ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        provisionModule = { module, moduleAdvancer ->
            val otherModule = moduleAdvancer.createModule(
                ModuleName(
                    sourceFile = dirPath("half"),
                    libraryRootSegmentCount = 1,
                    isPreface = false,
                ),
                module.console,
            )
            otherModule.deliverContent(
                ModuleSource(
                    filePath = filePath("half", "half.temper"),
                    fetchedContent = """
                        |@extension("half")
                        |export let intHalf(x: Int): Int {
                        |  x / 2
                        |}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            module.deliverContent(
                ModuleSource(
                    filePath = testCodeLocation,
                    fetchedContent = """
                        |let { intHalf } = import("../half");
                        |console.log(84.half().toString());
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
        },
    )

    @Test
    fun unaryPlusWashesOut() = assertModuleAtStage(
        stage = Stage.FunctionMacro,
        input = """
            |// For these first two, the unary plus survives to the typer
            |// but is then removed so there's one less thing to translate.
            |export let fi(i: Int): Int { +i }
            |export let ff(f: Float64): Float64 { +f }
            |// This use of `+` is illegal so remains in the tree.
            |export let fs(s: String): String { +s }
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body:
            |      ```
            |      @fn let `test//`.fi, @fn `test//`.ff, @fn `test//`.fs;
            |      `test//`.fi = (@stay fn fi(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          fn__0: do {
            |            return__0 = identity(i__0)
            |          }
            |      });
            |      `test//`.ff = (@stay fn ff(f__0 /* aka f */: Float64) /* return__1 */: Float64 {
            |          fn__1: do {
            |            return__1 = identity(f__0)
            |          }
            |      });
            |      `test//`.fs = (@stay fn fs(s__0 /* aka s */: String) /* return__2 */: String {
            |          fn__2: do {
            |            return__2 = +s__0
            |          }
            |      })
            |
            |      ```
            |  },
            |  functionMacro: {
            |    body:
            |      ```
            |      @fn let `test//`.fi, @fn `test//`.ff, @fn `test//`.fs;
            |      `test//`.fi = (@stay fn fi(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          fn__0: do {
            |## Now it's gone
            |            return__0 = i__0
            |          }
            |      });
            |      `test//`.ff = (@stay fn ff(f__0 /* aka f */: Float64) /* return__1 */: Float64 {
            |          fn__1: do {
            |            return__1 = f__0
            |          }
            |      });
            |      `test//`.fs = (@stay fn fs(s__0 /* aka s */: String) /* return__2 */: String {
            |          fn__2: do {
            |## This stays here so that TypeChecker can flag it as an error later.
            |            return__2 = +s__0
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun orElsePanic() = assertModuleAtStage(
        stage = Stage.Type,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
        moduleResultNeeded = true,
        input = """
            |export let f(): String throws Bubble {
            |  bubble()
            |}
            |
            |let x = f() orelse panic();
            |
            |x
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body:
            |      ```
            |      let return__0 ⦂ String;
            |      var t#0 ⦂ String, fail#0 ⦂ Boolean;
            |      @fn let `test//`.f ⦂(fn (): String | Bubble);
            |      `test//`.f = (@stay fn f /* return__1 */: (String | Bubble) {
            |          fn__0: do {
            |            bubble ⋖ String ⋗()
            |          }
            |      });
            |      let x__0 ⦂ String;
            |      orelse#0: {
            |        t#0 = hs ⋖ String ⋗(fail#0, (fn f)());
            |        if (fail#0) {
            |          break orelse#0;
            |        };
            |        x__0 = t#0
            |      } orelse {
            |        x__0 = panic ⋖ String ⋗()
            |      };
            |      return__0 = x__0
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )
}

private object ImpureIgnoreFn : NamedBuiltinFun, CallableValue {
    override val name: String = "ignore"
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                hackMapOldStyleToNew(
                    MkType.fn(
                        typeFormals = emptyList(),
                        valueFormals = emptyList(),
                        restValuesFormal = null,
                        returnType = Types.void.type,
                    ),
                ),
            ),
        ),
    )
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult = void

    override val callMayFailPerSe: Boolean = false
}
