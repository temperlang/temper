@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.Types
import lang.temper.common.NoneShortOrLong
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.env.InterpMode
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.lexer.Genre
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.Document
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.Value
import lang.temper.value.initSymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.publicSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.visibilitySymbol
import lang.temper.value.wordSymbol
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxMacroStageTest {
    @Test
    fun blockScoping() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
        let a = 1;
        (do {
          let a = 2;
          // Why do I feel compelled to write `let ... in` here?
          // I wish I knew how to quit you, OCaml!
          a
        }) + a
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          run: "3: Int32",
          syntaxMacro: {
            body: {
              code:
                ```
                let a__0 = 1;
                do (fn {
                    let a__1 = 2;
                    REM("Why do I feel compelled to write `let ... in` here?\nI wish I knew how to quit you, OCaml!", null, false);
                    a__1
                }) + a__0

                ```,
              tree:
                [ "Block", [
                    [ "Decl", [
                        [ "LeftName", "a__0" ],
                        [ "Value", [ "init", "Symbol" ] ],
                        [ "Value", [ 1, "Int32" ] ],
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.a\": String" ],
                      ]
                    ],
                    [ "Call", [
                        [ "RightName", "+" ],
                        [ "Call", [
                            [ "RightName", "do" ],
                            [ "Fun", [
                                [ "Block", [
                                    [ "Decl", [
                                        [ "LeftName", "a__1" ],
                                        [ "Value", [ "init", "Symbol" ] ],
                                        [ "Value", [ 2, "Int32" ] ],
                                        [ "Value", "\\QName: Symbol" ],
                                        [ "Value", "\"test-code.a=\": String" ],
                                      ]
                                    ],
                                    [ "Call", [
                                        [ "Value", "REM: Function" ],
                                        [ "Value", ```
                                          "Why do I feel compelled to write `let ... in` here?\nI wish I knew how to quit you, OCaml!": String
                                          ``` ],
                                        [ "Value", "null: Null" ],
                                        [ "Value", "false: Boolean" ],
                                      ]
                                    ],
                                    [ "RightName", "a__1" ]
                                  ]
                                ]
                              ]
                            ]
                          ]
                        ],
                        [ "RightName", "a__0" ]
                      ]
                    ]
                  ]
                ]
            }
          }
        }
        """,
    )

    /*
    In Java and Rust,
    {
      int i = 0;
      {
        int i = i;
      }
    }
    is legal since, the `i` used in the initializer binds in a scope that excludes the name being
    initialized.  So Java treats every initialization
        T n = e;
        // following statements in the same block
    the same as
        T temporary = e;
        {
          T n = temporary;
          // following statements in the same block
        }

    JavaScript has a temporal dead zone though so
    {
      let i = 0;
      {
        let i = i;
      }
    }
    is illegal since the `i` in the initializer binds to the uninitialized inner `let`.

    The Rust and Kotlin communities' experiences with shadowing starting lexically after
    initialization show that this feature is widely appreciated.
     */
    @Test
    fun useInLetInitializer() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        let i = 0;
        do {
          let i = i;
          f(i);
        }
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body: [ "Block", [
                [ "Decl", [
                    [ "LeftName", "i__0" ],
                    [ "Value", "\\init: Symbol" ],
                    [ "Value", "0: Int32" ],
                    [ "Value", "\\QName: Symbol" ],
                    [ "Value", "\"test-code.i\": String" ],
                  ]
                ],
                [ "Call", [
                    [ "RightName", "do" ],
                    [ "Fun", [ [ "Block", [
                        [ "Decl", [
                            [ "LeftName", "i__1" ],
                            [ "Value", "\\init: Symbol" ],
                            [ "RightName", "i__0" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.i=\": String" ],
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "f" ],
                            [ "RightName", "i__1" ],
                          ]
                        ],
                        [ "Value", "void: Void" ],
                    ] ] ] ]
                  ]
                ]
              ]
            ]
          }
        }
        """,
    )

    @Test
    fun backReferenceInFormalInitializer() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "let f(i = j, j = 42, k = f) {}",
        want = """
        {
          syntaxMacro: {
            body: [ "Block", [
                [ "Decl", [
                    [ "LeftName", "f__0" ],
                    [ "Value", "\\fn: Symbol" ],
                    [ "Value", "void: Void" ],
                    [ "Value", "\\QName: Symbol" ],
                    [ "Value", "\"test-code.f()\": String" ],
                  ]
                ],
                [ "Call", [
                    [ "Value", "nym`=`: Function" ],
                    [ "LeftName", "f__0" ],
                    [ "Fun", [
                        [ "Decl", [
                            [ "LeftName", "i__1" ],
                            [ "Value", "\\default: Symbol" ],
                            [ "RightName", "j__2" ],
                            [ "Value", "\\word: Symbol" ],
                            [ "Value", "\\i: Symbol" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.f().(i)\": String" ],
                          ]
                        ],
                        [ "Decl", [
                            [ "LeftName", "j__2" ],
                            [ "Value", "\\default: Symbol" ],
                            [ "Value", "42: Int32" ],
                            [ "Value", "\\word: Symbol" ],
                            [ "Value", "\\j: Symbol" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.f().(j)\": String" ],
                          ]
                        ],
                        [ "Decl", [
                            [ "LeftName", "k__3" ],
                            [ "Value", "\\default: Symbol" ],
                            [ "RightName", "f__0" ],
                            [ "Value", "\\word: Symbol" ],
                            [ "Value", "\\k: Symbol" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.f().(k)\": String" ],
                          ]
                        ],
                        [ "Value", "\\returnedFrom: Symbol" ],
                        [ "Value", "true: Boolean" ],
                        [ "Value", "\\word: Symbol" ],
                        [ "Value", "\\f: Symbol" ],
                        [ "Block", [
                            [ "Value", "\\label: Symbol" ],
                            [ "LeftName", "fn__4" ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ],
                [ "Value", "void: Void" ]
              ]
            ]
          }
        }
        """,
    )

    /**
     * When someone does
     *
     *     let f = fn (...) {...};
     *
     * adopt a name useful for debugging.
     *
     * Similarly to if they did
     *
     *     let f = fn f(...) { ... };
     */
    @Test
    fun letOfFn() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        let f = fn (x) {};
        let g = fn g(y) {};
        """.trimIndent(),
        want = """
        {
          syntaxMacro: {
            body:
            [ "Block", [
                [ "Decl", [
                    [ "LeftName", "f__0" ],
                    [ "Value", "\\init: Symbol" ],
                    [ "Fun", [
                        [ "Decl", [
                            [ "LeftName", "x__1" ],
                            [ "Value", "\\word: Symbol" ],
                            [ "Value", "\\x: Symbol" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.f().(x)\": String" ],
                          ]
                        ],
                        [ "Value", "\\returnedFrom: Symbol" ],
                        [ "Value", "true: Boolean" ],
                        [ "Value", "\\word: Symbol" ],
                        [ "Value", "\\f: Symbol" ],
                        [ "Block", [
                            [ "Value", "\\label: Symbol" ],
                            [ "LeftName", "fn__2" ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "\\fn: Symbol" ],
                    [ "Value", "void: Void" ],
                    [ "Value", "\\QName: Symbol" ],
                    [ "Value", "\"test-code.f()\": String" ],
                  ]
                ],

                [ "Decl", [
                    [ "LeftName", "g__3" ],
                    [ "Value", "\\init: Symbol" ],
                    [ "Fun", [
                        [ "Decl", [
                            [ "LeftName", "y__4" ],
                            [ "Value", "\\word: Symbol" ],
                            [ "Value", "\\y: Symbol" ],
                            [ "Value", "\\QName: Symbol" ],
                            [ "Value", "\"test-code.g().(y)\": String" ],
                          ]
                        ],
                        [ "Value", "\\returnedFrom: Symbol" ],
                        [ "Value", "true: Boolean" ],
                        [ "Value", "\\word: Symbol" ],
                        [ "Value", "\\g: Symbol" ],
                        [ "Block", [
                            [ "Value", "\\label: Symbol" ],
                            [ "LeftName", "fn__5" ]
                          ]
                        ]
                      ]
                    ],
                    [ "Value", "\\fn: Symbol" ],
                    [ "Value", "void: Void" ],
                    [ "Value", "\\QName: Symbol" ],
                    [ "Value", "\"test-code.g()\": String" ],
                  ]
                ],
                [ "Value", "void: Void" ],
              ]
            ]
          }
        }
        """,
    )

    @Test
    fun multiDeclarations() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "let [a, b is S, c = x]: T = f()",
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body:
            [ "Block", [
                [ "Block", [
                    [ "Decl", [
                        [ "LeftName", "t#0" ],
                        [ "Value", "\\init: Symbol" ],
                        [ "RightName", "S" ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "t#1" ],
                        [ "Value", "\\init: Symbol" ],
                        [ "RightName", "x" ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "t#2" ],
                        [ "Value", "\\init: Symbol" ],
                        [ "RightName", "T" ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "a__3" ],
                        [ "Value", "\\type: Symbol" ],
                        [ "RightName", "t#2" ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "b__4" ],
                        [ "Value", "\\type: Symbol" ],
                        [ "Call", [
                            [ "RightName", "&" ],
                            [ "RightName", "t#0" ],
                            [ "RightName", "t#2" ]
                          ]
                        ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "c__5" ],
                        [ "Value", "\\type: Symbol" ],
                        [ "RightName", "t#2" ] ,
                        [ "Value", "\\init: Symbol" ],
                        [ "RightName", "t#1" ],
                      ]
                    ],
                    [ "Call", [
                        [ "Value", "nym`=`: Function" ],
                        [ "Call", [
                            [ "Value", "nym`,`: Function" ],
                            [ "LeftName", "a__3" ],
                            [ "LeftName", "b__4" ],
                            [ "LeftName", "c__5" ],
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "f" ],
                          ]
                        ],
                      ]
                    ],
                  ]
                ]
              ]
            ]
          }
        }
        """,
    )

    @Test
    fun assignmentsInMultiDeclsResolveProperly() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "let [x, y] = f(); x + y",
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body: [ "Block", [
                [ "Block", [
                    [ "Decl", [ [ "LeftName", "x__0" ] ] ],
                    [ "Decl", [ [ "LeftName", "y__1" ] ] ],
                    [ "Call", [
                        [ "Value", "nym`=`: Function" ],
                        [ "Call", [
                            [ "Value", "nym`,`: Function" ],
                            [ "LeftName", "x__0" ],
                            [ "LeftName", "y__1" ]
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "f" ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ],
                [ "Call", [
                    [ "RightName", "+" ],
                    [ "RightName", "x__0" ],
                    [ "RightName", "y__1" ]
                  ]
                ]
              ]
            ]
          }
        }
        """,
    )

    @Test
    fun quotedNames() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        let nym`x`, y;
        f(x, y, nym`x`, nym`y`)
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body:
            ```
            let x__0, y__1;
            f(x__0, y__1, x__0, y__1)

            ```
          }
        }
        """,
    )

    @Test
    fun thisThisIsOkButThatThisIsNot() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |class C { private me = this }
            |let me = this;
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  errors: [
            |    "`this` may only appear inside a type definition!"
            |  ],
            |  disAmbiguate: {
            |    body:
            |      ```
            |      @typeDecl(C__0) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            |      class(\word, C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @property(\me) @maybeVar @visibility(\private) let me = this(C__0);
            |      });
            |      let me = this();
            |
            |      ```,
            |    types: {
            |      C: { word: "C" },
            |      AnyValue: { abstract: true },
            |    }
            |  },
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(C__0) @stay let C__0 = type (C__0);
            |      class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @property(\me) @maybeVar @visibility(\private) let me__3;
            |          @method(\constructor) @visibility(\public) let constructor__4 = fn constructor(@impliedThis(C__0) this__5: C__0) /* return__0 */: Void {
            |            do {
            |              let t#0;
            |              do_iset_me(type (C__0), this(C__0), t#0 = this(C__0));
            |              t#0
            |            };
            |          };
            |      });
            |      let me__7 = error ();
            |
            |      ```,
            |    types: {
            |      AnyValue: { abstract: true },
            |      C: {
            |        word: "C",
            |        properties: [
            |          { name: "me", symbol: "me", abstract: false, visibility: "private" },
            |        ],
            |        methods: [
            |          { name: "constructor", kind: "Constructor", visibility: "public", open: false },
            |        ],
            |        supers: [ "AnyValue__0" ],
            |      },
            |      Void: { supers: [] },
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun makingThisUnambiguous() = assertModuleAtStage(
        // TODO: IdRenumberer is not used to rewrite inlined values.
        // That affects the rendering of reified types.
        stage = Stage.SyntaxMacro,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        input = """
            |interface I {
            |  x;
            |}
            |
            |let x, y, z;
            |
            |class C<T>(public y) extends I {
            |  private f() {
            |    x + y + z // x is inherited, y is locally defined, z is closed over.
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |    syntaxMacro: {
            |        body:
            |            ```
            |            @typeDecl(I__0) @stay let I__0 = type (I__0);
            |            @typeDecl(C__0<T__2>) @stay let C__0 = type (C__0);
            |            interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
            |                I__0 extends AnyValue;
            |                @property(\x) @maybeVar let x__6;
            |            });
            |            let x__7, y__8, z__9;
            |            class(\word, \C, \concrete, true, @typeDefined(C__0<T__2>) fn {
            |                @typeFormal(\T) @memberTypeFormal(\T) @typeDefined(T__2) let T__2 = type (T__2);
            |                C__0<T__2> extends I__0;
            |                @constructorProperty @property(\y) @maybeVar @visibility(\public) let y__14;
            |                @method(\f) @visibility(\private) @fn let f__12 = fn f(@impliedThis(C__0<T__2>) this__2: C__0<T__2>) {
            |                  fn__13: do {
            |                    do_iget_x(type (C__0<T__2>), this(C__0<T__2>)) + do_iget_y(type (C__0<T__2>), this(C__0<T__2>)) + z__9
            |                  }
            |                };
            |                @method(\constructor) @visibility(\public) let constructor__15 = fn constructor(@impliedThis(C__0<T__2>) this__16: C__0<T__2>, y__17 /* aka y */) /* return__0 */: Void {
            |                  do {
            |                    let t#0;
            |                    do_iset_y(type (C__0<T__2>), this(C__0<T__2>), t#0 = y__17);
            |                    t#0
            |                  };
            |                };
            |            });
            |            C__0
            |
            |            ```,
            |        types: {
            |          AnyValue: { abstract: true },
            |          C: {
            |            word: "C",
            |            typeParameters: [
            |              { name: "T__2" },
            |            ],
            |            supers: ["I__0"],
            |            properties: [
            |              { name: "y", symbol: "y", abstract: false, visibility: "public" },
            |            ],
            |            methods: [
            |              { name: "f", symbol: "f", open: false, visibility: "private" },
            |              { name: "constructor", open: false, visibility: "public",
            |                kind: "Constructor" },
            |            ],
            |          },
            |          I: {
            |            word: "I",
            |            abstract: true,
            |            properties: [
            |              { name: "x", symbol: "x", abstract: true, visibility: "public" }, // Not resolved until Syntax stage
            |            ],
            |            supers: [ "AnyValue__0" ]
            |          },
            |          T: { word: "T" },
            |          Void: { supers: [] },
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun dotsToSymbols() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let foo = f(), bar;
            |foo.bar + bar;
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  syntaxMacro: {
            |    body: {
            |      code:
            |        ```
            |        let foo__0 = f(), bar__1;
            |        do_get_bar(foo__0) + bar__1;
            |
            |        ```,
            |      tree: [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "foo__0" ],
            |              [ "Value", "\\init: Symbol" ],
            |              [ "Call", [
            |                  [ "RightName", "f" ]
            |                ]
            |              ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.foo\": String" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "bar__1" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.bar\": String" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "RightName", "+" ],
            |              [ "Call", [
            |                  [ "Value", "do_get_bar: Function" ],
            |                  [ "RightName", "foo__0" ],
            |                ]
            |              ],
            |              [ "RightName", "bar__1" ]
            |            ]
            |          ],
            |          [ "Value", "void: Void" ],
            |        ]
            |      ]
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun getterAndSetterInheritVisibilityFromProperty() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |class(private _x) {
            |  public x;
            |  get x() { _x }
            |  set x(newValue) { _x = newValue }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(Anon__0) @stay let t#0 = type (Anon__0);
            |      class(\concrete, true, @typeDefined(Anon__0) fn {
            |          Anon__0 extends AnyValue;
            |          @constructorProperty @property(\_x) @maybeVar @visibility(\private) let _x__5;
            |          @property(\x) @visibility(\public) var x__6;
            |          @method(\x) @getter @fn let nym`get.x__7` = fn nym`get.x`(@impliedThis(Anon__0) this__2: Anon__0) {
            |            fn__8: do {
            |              do_iget__x(type (Anon__0), this(Anon__0))
            |            }
            |          };
            |          @method(\x) @setter @fn let nym`set.x__9` = fn nym`set.x`(@impliedThis(Anon__0) this__3: Anon__0, newValue__10 /* aka newValue */) /* return__0 */: Void {
            |            fn__11: do {
            |              do {
            |                let t#1;
            |                do_iset__x(type (Anon__0), this(Anon__0), t#1 = newValue__10);
            |                t#1
            |              }
            |            }
            |          };
            |          @method(\constructor) @visibility(\public) let constructor__12 = fn constructor(@impliedThis(Anon__0) this__13: Anon__0, _x__14 /* aka _x */) /* return__1 */: Void {
            |            do {
            |              let t#2;
            |              do_iset__x(type (Anon__0), this(Anon__0), t#2 = _x__14);
            |              t#2
            |            };
            |          };
            |      });
            |      type (Anon__0)
            |
            |      ```,
            |    types: {
            |      Anon: {
            |        properties: [
            |          { name: "_x", visibility: "private", abstract: false },
            |          {
            |            name: "x", abstract: true, visibility: "public",
            |            getter: "get.x", setter: "set.x"
            |          },
            |        ],
            |        methods: [
            |          { name: "get.x", symbol: "x", visibility: "public", open: false, kind: "Getter" },
            |          { name: "set.x", symbol: "x", visibility: "public", open: false, kind: "Setter" },
            |          { name: "constructor", "visibility": "public", open: false, kind: "Constructor" },
            |        ],
            |        supers: [ "AnyValue__0" ]
            |      },
            |      AnyValue: { abstract: true },
            |      Void: { supers: [] },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun methodWithoutBody() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        interface I {
          method();
        }
        """,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          syntaxMacro: {
            body:
              ```
              @typeDecl(I__0) @stay let I__0 = type (I__0);
              interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
                  I__0 extends AnyValue;
                  @method(\method) @fn let method__4 = fn method(@impliedThis(I__0) this__1: I__0) {
                    fn__5: do {
                      pureVirtual()
                    }
                  };
              });
              I__0

              ```
          }
        }
        """,
    )

    @Test
    fun forLoopExtractsDeclarations() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "for (var i = 0; i < 3; i += 1) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    do {
                      var i__0 = 0;
                      for(\__flowInit, {class: Empty__0}, \cond, i__0 < 3, \incr, i__0 = i__0 + 1, fn {
                          body;
                      })
                    }

                    ```
                }
            }
        """,
    )

    @Test
    fun forOfLoopVarAvailableInBody() = assertModuleAtStage(
        // for...of loop's loop variable is visible only within the body.
        // It is scoped to the body, and to allow it to be visible within the
        // expression right of `of` would lead to confusion.
        input = """
            |let x = f();
            |for (let x of x) {
            |  x
            |}
            |x
        """.trimMargin(),
        stage = Stage.SyntaxMacro,
        want = """
            |{
            |  syntaxMacro: {
            |    body:
            |    ```
            |    let x__0 = f();
            |    do_bind_forEach(x__0)(fn (x__1) {
            |        x__1
            |    });
            |    x__0
            |
            |    ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun forLoopExtractsMultipleDeclarations() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "for (var i: Int = 0, x = 3; i < 3; i += 1) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    do {
                      var i__0: Int = 0, x__1 = 3;
                      for(\__flowInit, {class: Empty__0}, \cond, i__0 < 3, \incr, i__0 = i__0 + 1, fn {
                          body;
                      })
                    }

                    ```
                }
            }
        """,
    )

    @Test
    fun forLoopKeepsLabel() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "label: for (var i = 0; i < 3; i += 1) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    do {
                      var i__0 = 0;
                      label__0: do {
                        for(\__flowInit, {class: Empty__0}, \cond, i__0 < 3, \incr, i__0 = i__0 + 1, fn {
                            body;
                        })
                      }
                    }

                    ```
                }
            }
        """,
    )

    @Test
    fun forLoopExtractsDeclarationsMinimal() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "for(;;) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    for(fn {
                        body;
                    })

                    ```
                }
            }
        """,
    )

    @Test
    fun forLoopExtractsDeclarationsJustInit() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "for(let i = 0;;) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    do {
                      let i__0 = 0;
                      for(\__flowInit, {class: Empty__0}, fn {
                          body;
                      })
                    }

                    ```
                }
            }
        """,
    )

    @Test
    fun forLoopLikeExtractsDeclarations() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "foo (let i = 0; i < 3; i += 1) { body; }",
        want = """
            {
                "syntaxMacro": {
                    "body":
                    ```
                    do {
                      let i__0 = 0;
                      foo(\__flowInit, {class: Empty__0}, \cond, i__0 < 3, \incr, i__0 = i__0 + 1, fn {
                          body;
                      })
                    }

                    ```
                }
            }
        """,
    )

    @Test
    fun namesResolveToExportedNames() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "export let x = 42; x",
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body: {
              code: ```
              let `test//`.x = 42;
              `test//`.x

              ```,
              tree:
                [ "Block", [
                    [ "Decl", [
                        [ "LeftName", { type: "ExportedName", baseName: "x" } ],
                        [ "Value", "\\init: Symbol" ],
                        [ "Value", "42: Int32" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ],
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.x\": String" ],
                      ]
                    ],
                    [ "RightName", { type: "ExportedName", baseName: "x" } ]
                  ]
                ]
            }
          }
        }
        """,
    )

    @Test
    fun genericFn() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |let identity<T extends AnyValue>(x: T): T { x }
            |identity(42)
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  // By the end of the syntax stage, the function has been rewritten to include the
            |  // formal declarations, including super-type info.
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @fn let identity__0;
            |      @typeFormal(\T) @typeDecl(T__0) let T__0 = type (T__0);
            |      T__0 extends AnyValue;
            |      identity__0 = fn identity<T__0 extends AnyValue>(x__0 /* aka x */: T__0) /* return__0 */: (T__0) {
            |        fn__0: do {
            |          x__0
            |        }
            |      };
            |      identity__0(42)
            |
            |      ```,
            |  },
            |  // By the end of the define stage, additional processing has happened.
            |  define: {
            |    body:
            |      ```
            |      @fn let identity__0;
            |      @typeFormal(\T) @typeDecl(T__0) let T__0;
            |      T__0 = type (T__0);
            |      T__0 extends AnyValue;
            |      identity__0 = (@stay fn identity<T__0 extends AnyValue>(x__0 /* aka x */: T__0) /* return__0 */: T__0 {
            |          fn__0: do {
            |            x__0
            |          }
            |      });
            |      42
            |
            |      ```,
            |  },
            |  run: "42: Int32"
            |}
        """.trimMargin(),
    )

    @Test
    fun fnFormalArgsDoNotCrossScopes() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let T = "T";
            |let f<T extends AnyValue>(x: T): T { x }
            |let t = T;
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @fn let f__0, T__0 = "T";
            |      @typeFormal(\T) @typeDecl(T__1) let T__1 = type (T__1);
            |      T__1 extends AnyValue;
            |      f__0 = fn f<T__1>(x__0 /* aka x */: T__1) /* return__0 */: (T__1) {
            |        fn__0: do {
            |          x__0
            |        }
            |      };
            |      let t__0 = T__0;
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun classFormalArgsDoNotCrossScopes() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let T = "T";
            |interface I<T> { t: T }
            |let t = T;
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(I__0<T__0>) @stay let I__0 = type (I__0);
            |      let T__1 = "T";
            |      interface(\word, \I, \concrete, false, @typeDefined(I__0<T__0>) fn {
            |          @typeFormal(\T) @memberTypeFormal(\T) @typeDefined(T__0) let T__0 = type (T__0);
            |          I__0<T__0> extends AnyValue;
            |          @property(\t) @maybeVar let t__0: T__0;
            |      });
            |      let t__1 = T__1;
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun letFunctionBodyRequired() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """let f()""",
        want = """
        {
            "syntaxMacro": {
                "body": "error (MalformedDeclaration)\n",
            },
        }
        """,
    )

    @Test
    fun letFunctionBodyRequiredWithoutName() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        // Earlier, `let()` and `fn()` both hard crashed.
        input = """let()""",
        want = """
        {
            "syntaxMacro": {
                "body": "error (MalformedDeclaration)\n",
            },
        }
        """,
    )

    @Test
    fun letFunctionNameRequired() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """let() {}""",
        want = """
        {
            "syntaxMacro": {
                "body": "error (MissingName)\n",
            },
        }
        """,
    )

    @Test
    fun objectPunning() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let x = 1;
            |{ x }
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |    syntaxMacro: {
            |        "body": ```
            |            let x__0 = 1;
            |            new(\x, x__0)
            |
            |            ```
            |    },
            |    errors: ["No signature matches!"],
            |}
        """.trimMargin(),
    )

    @Test
    fun whoDecoratesTheDecorators() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        provisionModule = { module, _ ->
            module.deliverContent(
                ModuleSource(
                    filePath = testCodeLocation,
                    fetchedContent = """
                        |// Stack many decorators on a declaration and make sure they eliminate themselves.
                        |interface I {
                        |  @foo("FOO") public static var thing;
                        |}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            // We need some more decorators to stack.  Invent one.
            val vFoo = Value(
                MetadataDecorator(Symbol("foo"), argumentTypes = listOf(Types.string)) {
                    it.evaluate(1, interpMode = InterpMode.Partial)
                },
            )
            module.addEnvironmentBindings(
                mapOf(
                    ParsedName("@foo") to vFoo,
                    BuiltinName("@foo") to vFoo,
                ),
            )
        },
        want = """
            |{
            |  syntaxMacro: {
            |    body: {
            |      code: ```
            |        @typeDecl(I__0) @stay let I__0 = type (I__0);
            |        REM("Stack many decorators on a declaration and make sure they eliminate themselves.", null, false);
            |        interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
            |            I__0 extends AnyValue;
            |            @staticProperty(\thing) @static @visibility(\public)${
            ""
        } @foo("FOO") var thing__0;
            |        });
            |        I__0
            |
            |        ```,
            |        tree: [ "Block", [
            |            [ "Decl", [
            |                [ "LeftName", "I__0" ],
            |                [ "Value", "\\init: Symbol" ],
            |                [ "Value", "I__0: Type" ],
            |                [ "Value", "\\typeDecl: Symbol" ],
            |                [ "Value", "I__0: Type" ],
            |                [ "Value", "\\stay: Symbol" ],
            |                [ "Stay", "kotlin.Unit" ],
            |                [ "Value", "\\QName: Symbol" ],
            |                [ "Value", "\"test-code.type I\": String" ],
            |              ]
            |            ],
            |            [ "Call", [
            |                [ "Value", "REM: Function" ],
            |                [ "Value",
            |"\"Stack many decorators on a declaration and make sure they eliminate themselves.\": String"
            |                ],
            |                [ "Value", "null: Null" ],
            |                [ "Value", "false: Boolean" ],
            |              ]
            |            ],
            |            [ "Call", [
            |                [ "RightName", "interface" ],
            |                [ "Value", "\\word: Symbol" ],
            |                [ "Value", "\\I: Symbol" ],
            |                [ "Value", "\\concrete: Symbol" ],
            |                [ "Value", "false: Boolean" ],
            |                [ "Fun", [
            |                    [ "Value", "\\typeDefined: Symbol" ],
            |                    [ "Value", "I__0: Type" ],
            |                    [ "Block", [
            |                        [ "Call", [
            |                            [ "Value", "extends: Function" ],
            |                            [ "Value", "I__0: Type" ],
            |                            [ "Value", "AnyValue: Type" ],
            |                          ]
            |                        ],
            |                        [ "Decl", [
            |                            [ "LeftName", "thing__0" ],
            |                            [ "Value", "\\staticProperty: Symbol" ],
            |                            [ "Value", "\\thing: Symbol" ],
            |                            [ "Value", "\\var: Symbol" ],
            |                            [ "Value", "void: Void" ],
            |                            [ "Value", "\\static: Symbol" ],
            |                            [ "Value", "void: Void" ],
            |                            [ "Value", "\\visibility: Symbol" ],
            |                            [ "Value", "\\public: Symbol" ],
            |                            [ "Value", "\\foo: Symbol" ],
            |                            [ "Value", "\"FOO\": String" ],
            |                            [ "Value", "\\QName: Symbol" ],
            |                            [ "Value", "\"test-code.type I.thing\": String" ],
            |                          ]
            |                        ],
            |                      ]
            |                    ]
            |                  ]
            |                ],
            |              ]
            |            ],
            |            [ "RightName", "I__0" ],
            |          ]
            |        ]
            |      }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun blockLambda() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = "f { (arg: ArgType): ReturnType => arg }",
        want = """
        |{
        |  syntaxMacro: {
        |    body: {
        |      code: ```
        |      f(fn (arg__0 /* aka arg */: ArgType) /* return__0 */: (ReturnType) {
        |          arg__0
        |      })
        |
        |      ```,
        |      tree:
        |          [ "Block", [
        |              [ "Call", [
        |                  [ "RightName", "f" ],
        |                  [ "Fun", [
        |                      [ "Decl", [
        |                          [ "LeftName", "arg__0" ],
        |                          [ "Value", "\\type: Symbol" ],
        |                          [ "RightName", "ArgType" ],
        |                          [ "Value", "\\word: Symbol" ],
        |                          [ "Value", "\\arg: Symbol" ],
        |                          [ "Value", "\\QName: Symbol" ],
        |                          [ "Value", "\"test-code.(arg)\": String" ],
        |                        ]
        |                      ],
        |                      [ "Value", "\\returnDecl: Symbol" ],
        |                      [ "Decl", [
        |                          [ "LeftName", "return__0" ],
        |                          [ "Value", "\\type: Symbol" ],
        |                          [ "RightName", "ReturnType" ],
        |                        ]
        |                      ],
        |                      [ "Block", [
        |                          [ "RightName", "arg__0" ],
        |                        ]
        |                      ]
        |                    ]
        |                  ]
        |                ]
        |              ]
        |            ]
        |          ]
        |    }
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun mutuallyReferencingInterfaceTypes() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        |interface I { j: J }
        |interface J { i: I }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        |{
        |  syntaxMacro: {
        |    body: ```
        |        @typeDecl(I__0) @stay let I__0 = type (I__0);
        |        @typeDecl(J__0) @stay let J__0 = type (J__0);
        |        interface(\word, \J, \concrete, false, @typeDefined(J__0) fn {
        |            J__0 extends AnyValue;
        |            @property(\i) @maybeVar let i__0: I__0;
        |        });
        |        interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
        |            I__0 extends AnyValue;
        |            @property(\j) @maybeVar let j__0: J__0;
        |        });
        |        J__0
        |
        |        ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun mutuallyReferencingClassTypes() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        |class C(private d: D?) {}
        |class D(private c: C) {}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        |{
        |  syntaxMacro: {
        |    body: ```
        |        @typeDecl(C__0) @stay let C__0 = type (C__0);
        |        @typeDecl(D__0) @stay let D__0 = type (D__0);
        |        class(\word, \D, \concrete, true, @typeDefined(D__0) fn {
        |            D__0 extends AnyValue;
        |            @constructorProperty @property(\c) @maybeVar @visibility(\private) let c__0: C__0;
        |            @method(\constructor) @visibility(\public) let constructor__0 = fn constructor(@impliedThis(D__0) this__1: D__0, c__1 /* aka c */: C__0) /* return__1 */: Void {
        |              do {
        |                let t#0;
        |                do_iset_c(type (D__0), this(D__0), t#0 = c__1);
        |                t#0
        |              };
        |            };
        |        });
        |        class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
        |            C__0 extends AnyValue;
        |            let typeof_d#0 = D__0?;
        |            @constructorProperty @property(\d) @maybeVar @visibility(\private) let d__0: typeof_d#0;
        |            @method(\constructor) @visibility(\public) let constructor__1 = fn constructor(@impliedThis(C__0) this__0: C__0, d__1 /* aka d */: typeof_d#0) /* return__0 */: Void {
        |              do {
        |                let t#1;
        |                do_iset_d(type (C__0), this(C__0), t#1 = d__1);
        |                t#1
        |              };
        |            };
        |        });
        |        D__0
        |
        |        ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun mutuallyReferencingFunctionDefinition() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        |// These do not converge since neither has a base case, but they demonstrate hoisting.
        |let f(x) { g(x / 2) }
        |let g(x) { f(x - 1) }
        """.trimMargin(),
        want = """
        |{
        |  syntaxMacro: {
        |    body: ```
        |        @fn let f__0, @fn g__0;
        |        REM("These do not converge since neither has a base case, but they demonstrate hoisting.", null, false);${
            // Arbitrary order swap here is ok.
            ""
        }
        |        g__0 = fn g(x__0 /* aka x */) {
        |          fn__0: do {
        |            f__0(x__0 - 1)
        |          }
        |        };
        |        f__0 = fn f(x__1 /* aka x */) {
        |          fn__1: do {
        |            g__0(x__1 / 2)
        |          }
        |        };
        |
        |        ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun rewriteConnectedDecorator() = assertModuleAtStage(
        // Fake std to get access to `@connected`.
        loc = ModuleName(
            sourceFile = filePath(
                STANDARD_LIBRARY_NAME,
                "fake-part-of-std.temper",
            ),
            libraryRootSegmentCount = 1,
            isPreface = false,
        ),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        stage = Stage.SyntaxMacro,
        input = """
            |class Hi {
            |  @connected("Hi::there")
            |  private there();
            |}
        """.trimMargin(),
        want = """
            |{
            |    syntaxMacro: {
            |        body: ```
            |        @typeDecl(Hi__0) @stay let Hi__0 = type (Hi__0);
            |        class(\word, \Hi, \concrete, true, @typeDefined(Hi__0) fn {
            |            Hi__0 extends AnyValue;
            |            @method(\there) @visibility(\private) @connected("Hi::there") @fn let there__0 = (@connected("Hi::there") fn there(@impliedThis(Hi__0) this__0: Hi__0) {
            |                fn__0: do {
            |                  pureVirtual()
            |                }
            |            });
            |            @method(\constructor) @visibility(\public) let constructor__0 = fn constructor(@impliedThis(Hi__0) this__1: Hi__0) /* return__0 */: Void {};
            |        });
            |        Hi__0
            |
            |        ```,
            |    },
            |}
        """.trimMargin(),
    )

    @Test
    fun reorder() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |    let f(): Int { let i = 4; let g(): Int { i + j }; g() }
            |    let j = i + 1;
            |    let i = 1;
        """.trimMargin(),
        want = """
            |{
            |    syntaxMacro: {
            |        body: ```
            |        @fn let f__0, i__0 = 1, j__0 = i__0 + 1;
            |        f__0 = fn f /* return__0 */: (Int) {
            |          fn__0: do {
            |            @fn let g__0, i__1 = 4;
            |            g__0 = fn g /* return__1 */: (Int) {
            |              fn__1: do {
            |                i__1 + j__0
            |              }
            |            };
            |            g__0()
            |          }
            |        };
            |
            |        ```,
            |    },
            |}
        """.trimMargin(),
    )

    @Test
    fun genericFunctionInDocs() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        genre = Genre.Documentation,
        input = "let f<T, U extends T>(x: T): U { x }",
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |        @fn let f__0;
            |        @typeFormal(\T) @typeDecl(T__0) @withinDocFold let T__0 = type (T__0);
            |        @typeFormal(\U) @typeDecl(U__0) @withinDocFold let U__0 = type (U__0);
            |        U__0 extends T__0;
            |        f__0 = fn f<T__0 extends AnyValue, U__0 extends T__0>(x__0 /* aka x */: T__0) /* return__0 */: (U__0) {
            |          x__0
            |        };
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun untypedFunArgs() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        genre = Genre.Documentation,
        input = "hi { (x: Int, y): String => x }",
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |        hi(fn (x__0 /* aka x */: Int, y__0 /* aka y */) /* return__0 */: (String) {
            |            x__0
            |        })
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun objectLiteralNoMatches() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        |{ hi: 5 }
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
        |{
        |  syntaxMacro: {
        |    body:
        |      ```
        |      new(\hi, 5)
        |
        |      ```
        |  },
        |  errors: ["No signature matches!"]
        |}
        """.trimMargin(),
    )

    @Test
    fun objectLiteralMultipleMatches() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
        |class Apple(public hi: Int) {}
        |class Banana(public hi: Int) {}
        |{ hi: 5 }
        """.trimMargin(),
        moduleResultNeeded = true,
        nameSimplifying = true,
        manualCheck = ::checkObjectLiteralMultipleMatches,
    )

    @Test
    fun staticMethods() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |class C {
            |  public static f(i: Int): Int { i + 1 }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @typeDecl(C__0) @stay let C__0 = type (C__0);
            |      class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @staticProperty(\f) @fn @static @visibility(\public) let f__0 = fn f(i__0 /* aka i */: Int) /* return__0 */: (Int) {
            |            fn__0: do {
            |              i__0 + 1
            |            }
            |          };
            |          @method(\constructor) @visibility(\public) let constructor__0 = fn constructor(@impliedThis(C__0) this__0: C__0) /* return__1 */: Void {};
            |      });
            |      C__0
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    private fun checkObjectLiteralMultipleMatches(got: JsonObject) {
        // Check that we retained the anonymous constructor call.
        val code = (((got["syntaxMacro"] as JsonObject)["body"] as JsonObject)["code"] as JsonString).content
        assertTrue("new(\\hi, 5)" in code)
        // Check that we got the expected error, without relying on specific numbering.
        val errors = (got["errors"] as JsonArray).map { (((it as JsonObject)["formatted"]) as JsonString).content }
        assertEquals(1, errors.size)
        assertEquals("Multiple types have matching constructors: Apple, Banana!", errors[0])
    }

    @Test
    fun objectLiteralMultipleMatchesNested() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        // The ObjectLiterals functional test checks non-ambiguous cases for nested scopes, so check an ambiguous case
        // here to prove we still do that.
        input = """
        |class Apple(private hi: Int) {}
        |let nest(): Void {
        |  class Banana(private hi: Int, public ha: Int = 0) {}
        |  { hi: 5 }
        |}
        """.trimMargin(),
        nameSimplifying = true,
        manualCheck = ::checkObjectLiteralMultipleMatches,
    )

    @Test
    fun objectLiteralOverloads() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        // At time of writing, these overloads fail at later stages but work correctly here.
        // Also, check usage both before and after type definition.
        input = """
        |{ hi: 5 }
        |class Thing {
        |  public constructor(hi: Int) { }
        |  public constructor(lo: Int) { }
        |}
        |{ lo: 5 }
        """.trimMargin(),
        manualCheck = { got ->
            // Check that we transformed both calls.
            val code = (got.lookup("syntaxMacro", "body", "code") as? JsonString)!!.content
            assertContains(code, Regex("""new Thing\w+\(\\hi, 5\)"""))
            assertContains(code, Regex("""new Thing\w+\(\\lo, 5\)"""))
        },
    )

    @Suppress("MaxLineLength")
    @Test
    fun storingDocStringWithFn() = assertModuleAtStage(
        stage = Stage.Define,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Short),
        input = """
            |/**
            | * tldr, f(x) = x.
            | *
            | * When x is an Int.
            | *
            | *          ^  _
            | *          |  /|
            | *     y =  | /
            | *     f(x) |/
            | *       <--0--->
            | *         /|  x
            | *        / |
            | *      |/_ v
            | *
            | * (ASCII art is hard)
            | */
            |let f(x: Int): Int { x }
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @fn let f__0;
            |      REM("tldr, f(x) = x.\n\nWhen x is an Int.\n\n         ^  _\n         |  /|\n    y =  | /\n    f(x) |/\n      <--0--->\n        /|  x\n       / |\n     |/_ v\n\n(ASCII art is hard)", true, false);
            |      f__0 = (@docString(...) fn f(x__0 /* aka x */: Int) /* return__0 */: (Int) {
            |          fn__0: do {
            |            x__0
            |          }
            |      });
            |
            |      ```,
            |  },
            |  define: {
            |    body: ```
            |      @fn let f__0;
            |      void;
            |## And the comment just fades away.
            |      f__0 = (@docString(...) @stay fn f(x__0 /* aka x */: Int32) /* return__0 */: Int32 {
            |          fn__0: do {
            |            x__0
            |          }
            |      });
            |
            |      ```,
            |  },
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun docStringsFromMarkdown() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Long),
        languageConfig = MarkdownLanguageConfig(),
        input = """
            |# Geometry
            |
            |Point represents a two-dimensional point.
            |
            |    class Point(
            |
            |Point's factory takes two coordinates.  TODO: another factory for polar form.
            |
            |x is the x coordinate.
            |
            |      public x: Float64,
            |
            |y is the y coordinate.
            |
            |      public y: Float64,
            |    ) {
            |
            |magnitude is the distance of this point from the origin.
            |
            |It is always >= 0.
            |
            |      magnitude(): Float64 { (x * x + y * y).sqrt() }
            |
            |    }
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |## No "Geometry" for the class doc comment
            |        @typeDecl(Point__0) @stay @docString((["Point represents a two-dimensional point.", "Point represents a two-dimensional point.", "test/test.temper"])) let Point__0 = type (Point__0);
            |        REM("Point represents a two-dimensional point.", true, true);
            |        class(\word, \Point, \concrete, true, @typeDefined(Point__0) fn {
            |            Point__0 extends AnyValue;
            |## x's docs don't talk about the factory
            |            @docString((["x is the x coordinate.", "x is the x coordinate.", "test/test.temper"])) @constructorProperty @maybeVar @visibility(\public) let x__0: Float64;
            |            @docString((["y is the y coordinate.", "y is the y coordinate.", "test/test.temper"])) @constructorProperty @maybeVar @visibility(\public) let y__0: Float64;
            |            REM("magnitude is the distance of this point from the origin.", true, true);
            |            REM("It is always >= 0.", true, true);
            |## magnitude has its doc string
            |            @fn let magnitude__0 = (@docString((["magnitude is the distance of this point from the origin.", "magnitude is the distance of this point from the origin.\n\nIt is always >= 0.", "test/test.temper"])) fn magnitude(@impliedThis(Point__0) this__0: Point__0) /* return__0 */: (Float64) {
            |                fn__0: do {
            |                  do_bind_sqrt(do_iget_x(type (Point__0), this(Point__0)) * do_iget_x(type (Point__0), this(Point__0)) + do_iget_y(type (Point__0), this(Point__0)) * do_iget_y(type (Point__0), this(Point__0)))()
            |                }
            |            });
            |            @visibility(\public) let constructor__0 = fn constructor(@impliedThis(Point__0) this__1: Point__0, x__1 /* aka x */: Float64, y__1 /* aka y */: Float64) /* return__1 */: Void {
            |              do {
            |                let t#0;
            |                do_iset_x(type (Point__0), this(Point__0), t#0 = x__1);
            |                t#0
            |              };
            |              do {
            |                let t#1;
            |                do_iset_y(type (Point__0), this(Point__0), t#1 = y__1);
            |                t#1
            |              };
            |            };
            |        });
            |        Point__0
            |
            |        ```,
            |  },
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun storingDocStringWithType() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Long),
        input = """
            |/** Foo is a pretty cool type */
            |class Foo {}
            |;
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @typeDecl(Foo__0) @stay @docString((["Foo is a pretty cool type", "Foo is a pretty cool type", "test/test.temper"])) let Foo__0 = type (Foo__0);
            |      REM("Foo is a pretty cool type", true, false);
            |      class(\word, \Foo, \concrete, true, @typeDefined(Foo__0) fn {
            |          Foo__0 extends AnyValue;
            |          @visibility(\public) let constructor__0 = fn constructor(@impliedThis(Foo__0) this__0: Foo__0) /* return__0 */: Void {};
            |      });
            |
            |      ```,
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun storingDocStringWithExportedType() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Short),
        input = """
            |/** I am a pretty cool type */
            |export interface I {}
            |;
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @typeDecl(I) @stay @docString(...) let `test//`.I = type (I);
            |      do {};
            |      REM("I am a pretty cool type", true, false);
            |      interface(\word, \I, \concrete, false, @typeDefined(I) fn {
            |          I extends AnyValue
            |      });
            |
            |      ```,
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun commentsOnSettersAndGetters() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |class C {
            |  /** Returns 1 */
            |  public get x(): Int { 1 }
            |  /** You can set it but it'll still be 1. */
            |  public set x(newValue: Int): Void {}
            |}
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body:
            |      ```
            |      @typeDecl(C__0) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            |      class(\word, C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          REM("Returns 1", true, false);
            |          @method(\x) @getter @visibility(\public) let nym`get.x` = fn(\word, nym`get.x`, @impliedThis(C__0) let this__0: C__0, \outType, Int, fn {
            |              1
            |          });
            |          REM("You can set it but it'll still be 1.", true, false);
            |          @method(\x) @setter @visibility(\public) let nym`set.x` = fn(\word, nym`set.x`, @impliedThis(C__0) let this__1: C__0, let newValue /* aka newValue */: Int, \outType, Void, fn {});
            |      });
            |      C
            |
            |      ```
            |  },
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(C__0) @stay let C__0 = type (C__0);
            |      class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @property(\x) @visibility(\public) let x__0;
            |          REM("Returns 1", true, false);
            |          @method(\x) @getter @visibility(\public) @fn let nym`get.x__1` = (@docString(...) fn nym`get.x`(@impliedThis(C__0) this__0: C__0) /* return__0 */: (Int) {
            |              fn__0: do {
            |                1
            |              }
            |          });
            |          REM("You can set it but it'll still be 1.", true, false);
            |          @method(\x) @setter @visibility(\public) @fn let nym`set.x__2` = (@docString(...) fn nym`set.x`(@impliedThis(C__0) this__1: C__0, newValue__0 /* aka newValue */: Int) /* return__1 */: (Void) {
            |              fn__1: do {}
            |          });
            |          @method(\constructor) @visibility(\public) let constructor__0 = fn constructor(@impliedThis(C__0) this__2: C__0) /* return__2 */: Void {};
            |      });
            |      C__0
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(
            docStringDetail = NoneShortOrLong.Short,
            showTypeMemberMetadata = true,
        ),
    )

    @Test
    fun consoleBound() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let console = getConsole("myConsole");
            |console.log("Hi!");
            |builtins.console.log("Bye!");
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |        let console#0 = getConsole(), console__0 = getConsole("myConsole");
            |        do_bind_log(console__0)("Hi!");
            |        do_bind_log(console#0)("Bye!");
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun chainNull() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        // Note that we currently can't properly infer `a != null` for `a.string.end` yet. TODO Infer such.
        input = """
            |class StringHolder(public string: String) {}
            |export let maybeLength(a: StringHolder?, min: Int): Int? {
            |  a?.string?.countBetween(String.begin, a.string.end)?.max(min)
            |}
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(StringHolder__0) @stay let StringHolder__0 = type (StringHolder__0);
            |      @fn let `test//`.maybeLength;
            |      class(\word, \StringHolder, \concrete, true, @typeDefined(StringHolder__0) fn {
            |          StringHolder__0 extends AnyValue;
            |          @constructorProperty @maybeVar @visibility(\public) let string__0: String;
            |          @visibility(\public) let constructor__0 = fn constructor(@impliedThis(StringHolder__0) this__0: StringHolder__0, string__1 /* aka string */: String) /* return__0 */: Void {
            |            do {
            |              let t#0;
            |              do_iset_string(type (StringHolder__0), this(StringHolder__0), t#0 = string__1);
            |              t#0
            |            };
            |          };
            |      });
            |      `test//`.maybeLength = fn maybeLength(a__0 /* aka a */: StringHolder__0?, min__0 /* aka min */: Int) /* return__1 */: (Int?) {
            |        fn__0: do {
            |          {
            |            let subject#0;
            |            subject#0 = {
            |              let subject#1;
            |              subject#1 = {
            |                if (isNull(a__0)) {
            |                  null
            |                } else {
            |                  do_get_string(notNull(a__0))
            |                }
            |              };
            |              if (isNull(subject#1)) {
            |                null
            |              } else {
            |                do_bind_countBetween(notNull(subject#1))(do_get_begin(String), do_get_end(do_get_string(a__0)))
            |              }
            |            };
            |            if (isNull(subject#0)) {
            |              null
            |            } else {
            |              do_bind_max(notNull(subject#0))(min__0)
            |            }
            |          }
            |        }
            |      };
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun nullChainingDesugaring() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |let { C, g, complexSubject } = import("./c");
            |
            |let f(c: C?): Void {
            |  g(c?.prop);
            |  g(complexSubject(c)?.prop);
            |
            |  g(c?.method());
            |  g(complexSubject(c)?.method());
            |}
            |
            |$TEST_INPUT_MODULE_BREAK ./c/c.temper
            |// A class to null chain to.
            |export class C(public prop: String) {
            |  public method(): String;
            |}
            |
            |// Somewhere to send null chaining uses.
            |export let g(x: String?): Void { if (x != null) { console.log(x) }; }
            |
            |// Calls to complexSubject shouldn't be duplicated
            |export let complexSubject(c: C?): C? { c }
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @stay @imported(\(`test//c/`.C)) let C__0 = type (C), @imported(\(`test//c/`.g)) @fn g__0 = `test//c/`.g, @imported(\(`test//c/`.complexSubject)) @fn complexSubject__0 = (fn complexSubject), @fn f__0;
            |      f__0 = fn f(c__0 /* aka c */: C__0?) /* return__0 */: (Void) {
            |        fn__0: do {
            |          g__0({
            |              if (isNull(c__0)) {
            |                null
            |              } else {
            |                do_get_prop(notNull(c__0))
            |              }
            |          });
            |          g__0({
            |              let subject#0;
            |              subject#0 = complexSubject__0(c__0);
            |              if (isNull(subject#0)) {
            |                null
            |              } else {
            |                do_get_prop(notNull(subject#0))
            |              }
            |          });
            |          g__0({
            |              if (isNull(c__0)) {
            |                null
            |              } else {
            |                do_bind_method(notNull(c__0))()
            |              }
            |          });
            |          g__0({
            |              let subject#1;
            |              subject#1 = complexSubject__0(c__0);
            |              if (isNull(subject#1)) {
            |                null
            |              } else {
            |                do_bind_method(notNull(subject#1))()
            |              }
            |          });
            |        }
            |      };
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun consoleUnbound() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |do { let console = getConsole("myConsole"); }
            |console.log("Hi!");
            |builtins.console.log("Bye!");
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |        let console#0 = getConsole();
            |        do (fn {
            |            let console__0 = getConsole("myConsole");
            |        });
            |        do_bind_log(console#0)("Hi!");
            |        do_bind_log(console#0)("Bye!");
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun referencedToPreResolvedPropertyNamesRecognizedAsThisReferences() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        // Ensure that when a mixin uses a generated, resolved property name
        // that we infer the `this.` on it.
        // The generated code looks like the below:
        //
        // class C(public let i__0: Int) {
        //   public let f(): Int {
        //     i__0
        //   }
        // }
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @typeDecl(C__0) @stay let C__0 = type (C__0);
            |      class (\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @visibility(\public) @constructorProperty @maybeVar let i__0: Int32;
            |          @visibility(\public) let f__0 = fn (@impliedThis(C__0) this__0: C__0) /* return__0 */: Int32 {
            |## The resolved i reference here turned into a do_iget_i
            |            do_iget_i(type (C__0), this(C__0))
            |          };
            |          @visibility(\public) let constructor__0 = fn constructor(@impliedThis(C__0) this__1: C__0, i__1 /* aka i */: Int32) /* return__1 */: Void {
            |            do {
            |              let t#0;
            |              do_iset_i(type (C__0), this(C__0), t#0 = i__1);
            |              t#0
            |            };
            |          };
            |      });
            |      C__0
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) { module, _ ->
        val document = Document(module)
        val pos = Position(module.loc, 0, 0)
        val i = document.nameMaker.unusedSourceName(ParsedName("i"))
        module.deliverContent(
            document.treeFarm.grow(pos) {
                Block {
                    Call(ClassDefinitionMacro) {
                        V(wordSymbol)
                        Rn(ParsedName("C"))
                        Decl(i) {
                            V(typeSymbol)
                            V(Value(Types.int))
                            V(visibilitySymbol)
                            V(publicSymbol)
                        }
                        Fn {
                            Block {
                                Decl(ParsedName("f")) {
                                    V(initSymbol)
                                    Fn {
                                        V(outTypeSymbol)
                                        V(Value(Types.int))
                                        Block {
                                            Rn(i)
                                        }
                                    }
                                    V(visibilitySymbol)
                                    V(publicSymbol)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    @Test
    fun noPropertyConstructorPropertiesInPropertyBag() {
        val input = """
            |class C(private x: Int, @noProperty let y: Int) {
            |  private z: Int = y + 1;
            |}
            |
            |export let cs = [
            |  { x: 1, y: 2 },
            |  { x: 1, y: 2, z: 3 }, // ERROR: z not allowed here
            |]
        """.trimMargin()
        val problemSubstring = "{ x: 1, y: 2, z: 3 }"
        val problemLeft = input.indexOf(problemSubstring)
        val problemRight = problemLeft + problemSubstring.length
        assertModuleAtStage(
            stage = Stage.SyntaxMacro,
            input = input,
            want = """
                |{
                |  syntaxMacro: {
                |    body: ```
                |          @typeDecl(C__0) @stay let C__0 = type (C__0);
                |          class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
                |              C__0 extends AnyValue;
                |              @constructorProperty @maybeVar @visibility(\private) let x__0: Int;
                |              do {};
                |              @maybeVar @visibility(\private) let z__0: Int;
                |              @visibility(\public) let constructor__0 = fn constructor(@impliedThis(C__0) this__0: C__0, x__1 /* aka x */: Int, @constructorProperty y__0 /* aka y */: Int) /* return__0 */: Void {
                |                do {
                |                  let t#0;
                |                  do_iset_x(type (C__0), this(C__0), t#0 = x__1);
                |                  t#0
                |                };
                |                do {
                |                  let t#1;
                |                  do_iset_z(type (C__0), this(C__0), t#1 = y__0 + 1);
                |                  t#1
                |                };
                |              };
                |          });
                |          let `test//`.cs = list(new C__0(\x, 1, \y, 2), new(\x, 1, \y, 2, \z, 3));
                |
                |          ```
                |  },
                |  errors: [
                |    {
                |      template: "NoSignatureMatches",
                |      values: [],
                |      left: $problemLeft,
                |      right: $problemRight,
                |    },
                |  ],
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun setterInvocationUsedInExpressionContext() = assertModuleAtStage(
        stage = Stage.SyntaxMacro,
        input = """
            |// A chained assignment involving a setter invocation.
            |x = o.p = f()
        """.trimMargin(),
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      REM("A chained assignment involving a setter invocation.", null, false);
            |      x = do {
            |        let t#0;
            |## Here we capture the right operand in t#0,
            |## so the value assigned to x does not depend
            |## on any setter's return value.
            |        do_set_p(o, t#0 = f());
            |        t#0
            |      }
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )
}
