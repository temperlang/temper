@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.assertStructure
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.withCapturingConsole
import lang.temper.env.InterpMode
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Document
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.unholeBuiltinName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefineStageTest {
    @Test
    fun callToDotBind() = assertModuleAtStage(
        stage = Stage.Define,
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
        input = """
            |subject.verb(arg)
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      do_bind_verb(subject)(arg)
            |
            |      ```,
            |  }
            |}
        """.trimMargin(),
    )

    /**
     * Try out a bunch of uses of `this` and the dot operator to check
     * [lang.temper.frontend.syntax.DotOperationDesugarer].
     */
    @Test
    fun dotOperationDesugaring() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        interface I {
          next;
        }
        class C(private i = 0) extends I {
          public f() { i }
          public get next() { return f() + 1 }
          private set next(newVal) { this.i = newVal - 1 }
        }
        let c = new C();
        console.log(c.i, c.x, c.x, c.f());
        c.next = 42
        """,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          define: {
            body:
              ```
              let console#0;
              console#0 = getConsole();
              I__0 extends AnyValue;
              @property(\next) @stay @fromType(I__0) let next__7;
              @typeDecl(I__0) @stay let I__0;
              I__0 = type (I__0);
              @typeDecl(C__1) @stay let C__1;
              C__1 = type (C__1);
              interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
                  do {};
                  do {}
              });
              C__1 extends I__0;
              @constructorProperty @property(\i) @visibility(\private) @stay @fromType(C__1) let i__9;
              @method(\f) @visibility(\public) @fn @stay @fromType(C__1) let f__10;
              f__10 = fn f(@impliedThis(C__1) this__2: C__1) {
                fn__11: do {
                  getp(i__9, this__2)
                }
              };
              @property(\next) @visibility(\public) @stay @fromType(C__1) let next__12;
              @method(\next) @getter @visibility(\public) @fn @stay @fromType(C__1) let nym`get.next__13`;
              nym`get.next__13` = fn nym`get.next`(@impliedThis(C__1) this__3: C__1) /* return__14 */{
                fn__15: do {
                  do {
                    return__14 = do_ibind_f(type (C__1), this__3)() + 1;
                    break(\label, fn__15)
                  }
                }
              };
              @method(\next) @setter @visibility(\private) @fn @stay @fromType(C__1) let nym`set.next__16`;
              nym`set.next__16` = fn nym`set.next`(@impliedThis(C__1) this__4: C__1, newVal__17 /* aka newVal */) /* return__1 */: Void {
                fn__18: do {
                  do {
                    let t#0;
                    setp(i__9, this__4, t#0 = newVal__17 - 1);
                    t#0
                  }
                }
              };
              @fn @method(\constructor) @visibility(\public) @stay @fromType(C__1) let constructor__19;
              constructor__19 = fn constructor(@impliedThis(C__1) this__20: C__1, @optional(true) i__1 /* aka i */) /* return__2 */: Void {
                let i__21 /* aka i */;
                i__21 = if(i__1 == null, fn {
                    0
                  }, \else, fn (f#0) {
                    f#0(fn {
                        i__1
                    })
                });
                void;
                do {
                  let t#1;
                  setp(i__9, this__20, t#1 = i__21);
                  t#1
                };
              };
              class(\word, \C, \concrete, true, @typeDefined(C__1) fn {
                  do {};
                  do {};
                  do {};
                  do {};
                  do {};
                  do {};
                  do {}
              });
              let c__22;
              c__22 = new C__1();
              do_bind_log(console#0)(do_get_i(c__22), do_get_x(c__22), do_get_x(c__22), do_bind_f(c__22)());
              do {
                do_set_next(c__22, 42);
                42
              }

              ```,
            types: {
              "AnyValue": { abstract: true },
              "C": {
                supers: ["I__0"],
                properties: [
                  {
                    name: "next__12",
                    abstract: true,
                    getter: "get.next__13",
                    setter: "set.next__16",
                    visibility: "public"
                  },
                  { name: "i__9", abstract: false, visibility: "private" },
                ],
                methods: [
                  { name: "get.next__13",
                    symbol: "next", open: false, visibility: "public", kind: "Getter" },
                  { name: "set.next__16",
                    symbol: "next", open: false, visibility: "private", kind: "Setter" },
                  { name: "f__10", open: false, visibility: "public" },
                  { name: "constructor__19",
                    open: false, visibility: "public", kind: "Constructor" },
                ]
              },
              "I": {
                abstract: true,
                supers: ["AnyValue__0"],
                properties: [
                  { name: "next__7", abstract: true, visibility: "public" }
                ],
              },
              "Void": { supers: [] },
            }
          }
        }
        """,
    )

    @Test
    fun internalVersusExternalBackedPropertyAccess() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        class C(private i) {
          private f() { i = 1 }
        }
        (new C()).i = 2
        """,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          define: {
            body:
              ```
              C__0 extends AnyValue;
              @constructorProperty @property(\i) @visibility(\private) @stay @fromType(C__0) let i__4;
              @method(\f) @visibility(\private) @fn @stay @fromType(C__0) let f__5;
              f__5 = fn f(@impliedThis(C__0) this__1: C__0) {
                fn__6: do {
                  do {
                    setp(i__4, this__1, 1);
                    1
                  }
                }
              };
              @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__7;
              constructor__7 = fn constructor(@impliedThis(C__0) this__8: C__0, i__9 /* aka i */) /* return__0 */: Void {
                do {
                  let t#0;
                  setp(i__4, this__8, t#0 = i__9);
                  t#0
                };
              };
              @typeDecl(C__0) @stay let C__0;
              C__0 = type (C__0);
              class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
                  do {};
                  do {};
                  do {};
                  do {}
              });
              do {
                do_set_i(new C__0(), 2);
                2
              }

              ```,
            types: {
              "AnyValue": { abstract: true },
              "C": {
                supers: ["AnyValue__0"],
                properties: [
                  { name: "i__4", abstract: false, visibility: "private" },
                ],
                methods: [
                  { name: "f__5", open: false, visibility: "private" },
                  { name: "constructor__7",
                    open: false, visibility: "public", kind: "Constructor" },
                ]
              },
              "Void": { supers: [] },
            }
          }
        }
        """,
    )

    @Test
    fun constantFolding() = assertModuleAtStage(
        stage = Stage.Define,
        input = "1 + 1",
        want = """
        {
          syntaxMacro: {
            body: "1 + 1\n" // `+` is still an unresolved name at this point
          },
          define: {
            body: [ "Block", [ [ "Value", "2: Int32" ] ] ]
          }
        }
        """,
    )

    @Test
    fun constantFoldingViaConstExpression() = assertModuleAtStage(
        stage = Stage.Define,
        input = "let one = 1; one + one",
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            // Before define stage, one is not a resolved name, so we are cautious about
            // inlining referents.
            body:
            ```
            let one__0 = 1;
            one__0 + one__0

            ```
          },
          define: {
            body: {
              code:
              ```
              let one__0;
              one__0 = 1;
              2

              ```,
              tree:
              [ "Block", [
                  [ "Decl", [
                      [ "LeftName", "one__0" ],
                      [ "Value", "\\QName: Symbol" ],
                      [ "Value", "\"test-code.one\": String" ],
                      [ "Value", "\\ssa: Symbol" ],
                      [ "Value", "void: Void" ]
                    ]
                  ],
                  [ "Call", [
                      [ "Value", "nym`=`: Function" ],
                      [ "LeftName", "one__0" ],
                      [ "Value", "1: Int32" ]
                    ]
                  ],
                  [ "Value", "2: Int32" ]
                ]
              ]
            }
          }
        }
        """,
    )

    @Test
    fun nonConstReferentNotFoldedIntoConstExpression() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        var one = 1;
        if (falseOpaquePredicate) {
            one = 0;
        }
        one + one
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          syntaxMacro: {
            body:
            ```
            var one__0 = 1;
            if(falseOpaquePredicate, fn {
                one__0 = 0;
            });
            one__0 + one__0

            ```
          },
          define: {
            body:
            ```
            var one__0;
            one__0 = 1;
            if(falseOpaquePredicate, fn {
                one__0 = 0;
            });
            one__0 + one__0

            ```,
          }
        }
        """,
    )

    @Test
    fun userDefinedPureFunctionsInlined() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        let adj = 6;
        let factorMinusAdj(x: Int, y: Int): Int { x * y - adj }
        factorMinusAdj(6, 8)
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          define: {
            body:
              ```
              @fn let factorMinusAdj__1, adj__0;
              adj__0 = 6;
              factorMinusAdj__1 = (@stay fn factorMinusAdj(x__2 /* aka x */: Int32, y__3 /* aka y */: Int32)${
            ""
        } /* return__0 */: Int32 {
                  fn__4: do {
                    x__2 * y__3 - 6
                  }
              });
              42

              ```
          }
        }
        """,
    )

    // Test that type definition values get inlined.
    @Test
    fun typeAliasing() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        class C {}
        let alias = C; // Reified types via aliases should be inlined.
        let o: C;
        let p: alias;
        """.trimIndent(),
        want = """
        {
          syntaxMacro: {
            body:
                [ "Block", [
                    [ "Decl", [
                        ["LeftName", "C__0"],
                        ["Value", "\\init: Symbol"],
                        ["Value", "C__0: Type"],
                        ["Value", "\\typeDecl: Symbol"],
                        ["Value", "C__0: Type"],
                        ["Value", "\\stay: Symbol"],
                        ["Stay", "kotlin.Unit"],
                        ["Value", "\\QName: Symbol"],
                        ["Value", "\"test-code.type C\": String"],
                      ]
                    ],
                    ["Call", [
                        ["RightName", "class"],
                        ["Value", "\\word: Symbol"], ["Value", "\\C: Symbol"],
                        ["Value", "\\concrete: Symbol"], ["Value", "true: Boolean"],
                        ["Fun", [
                            ["Value", "\\typeDefined: Symbol"], ["Value", "C__0: Type"],
                            ["Block", [
                                ["Call", [
                                    [ "Value", "extends: Function" ],
                                    [ "Value", "C__0: Type" ],
                                    [ "Value", "AnyValue: Type" ],
                                  ]
                                ],
                                ["Decl", [
                                    ["LeftName", "constructor__3"],
                                    ["Value", "\\init: Symbol"],
                                    ["Fun", [
                                        ["Decl", [
                                            ["LeftName", "this__4"],
                                            ["Value", "\\type: Symbol"],
                                            ["Value", "C__0: Type"],
                                            ["Value", "\\impliedThis: Symbol"],
                                            ["Value", "C__0: Type"],
                                            ["Value", "\\QName: Symbol"],
                                            ["Value", "\"test-code.type C.constructor().(this)\": String"],
                                          ]
                                        ],
                                        ["Value", "\\word: Symbol"],
                                        ["Value", "\\constructor: Symbol"],
                                        ["Value", "\\returnDecl: Symbol"],
                                        ["Decl", [
                                            ["LeftName", "return__0"],
                                            ["Value", "\\type: Symbol"],
                                            ["Value", "Void: Type"],
                                            ["Value", "\\QName: Symbol"],
                                            ["Value", "\"test-code.type C.constructor().return=\": String"],
                                          ]
                                        ],
                                        ["Block", [
                                          ]
                                        ],
                                      ]
                                    ],
                                    ["Value", "\\method: Symbol"],
                                    ["Value", "\\constructor: Symbol"],
                                    ["Value", "\\visibility: Symbol"],
                                    ["Value", "\\public: Symbol"],
                                    ["Value", "\\QName: Symbol"],
                                    ["Value", "\"test-code.type C.constructor()\": String"],
                                  ]
                                ]
                              ]
                            ]
                          ]
                        ]
                      ]
                    ],
                    [ "Decl", [
                        ["LeftName", "alias__5"],
                        ["Value", "\\init: Symbol"], ["RightName", "C__0"],
                        ["Value", "\\QName: Symbol"],
                        ["Value", "\"test-code.alias\": String"],
                      ]
                    ],
                    [ "Call", [
                        [ "Value", "REM: Function" ],
                        [ "Value", "\"Reified types via aliases should be inlined.\": String" ],
                        [ "Value", "null: Null" ],
                        [ "Value", "false: Boolean" ],
                      ]
                    ],
                    [ "Decl", [
                        ["LeftName", "o__6"],
                        ["Value", "\\type: Symbol"], ["RightName", "C__0"],
                        ["Value", "\\QName: Symbol"],
                        ["Value", "\"test-code.o\": String"],
                      ]
                    ],
                    [ "Decl", [
                        ["LeftName", "p__7"],
                        ["Value", "\\type: Symbol"], ["RightName", "alias__5"],
                        ["Value", "\\QName: Symbol"],
                        ["Value", "\"test-code.p\": String"],
                      ]
                    ],
                    [ "Value", "void: Void" ],
                  ]
                ],
            types: {
              "AnyValue": { abstract: true },
              "C": {
                methods: [
                  { name: "constructor", visibility: "public", open: false, kind: "Constructor" }
                ],
                supers: [ "AnyValue__0" ]
              },
              "Void": { supers: [] },
            }
          },
          define: {
            body:
                [ "Block", [
                    ["Call", [
                        ["Value", "extends: Function"],
                        ["Value", "C__0: Type"],
                        ["Value", "AnyValue: Type"],
                      ]
                    ],
                    ["Decl", [
                        ["LeftName", "constructor__3"],
                        ["Value", "\\fn: Symbol"],
                        ["Value", "void: Void"],
                        ["Value", "\\method: Symbol"],
                        ["Value", "\\constructor: Symbol"],
                        ["Value", "\\visibility: Symbol"],
                        ["Value", "\\public: Symbol"],
                        ["Value", "\\QName: Symbol"],
                        ["Value", "\"test-code.type C.constructor()\": String"],
                        ["Value", "\\ssa: Symbol"],
                        ["Value", "void: Void"],
                        ["Value", "\\stay: Symbol"],
                        ["Stay"],
                        ["Value", "\\parameterNameSymbolsList: Symbol"],
                        ["Value", "[null]: List"],
                        ["Value", "\\fromType: Symbol"],
                        ["Value", "C__0: Type"],
                      ]
                    ],
                    [ "Call", [
                        ["Value", "nym`=`: Function"],
                        ["LeftName", "constructor__3"],
                        ["Fun", [
                            ["Decl", [
                                ["LeftName", "this__4"],
                                ["Value", "\\type: Symbol"],
                                ["Value", "C__0: Type"],
                                ["Value", "\\impliedThis: Symbol"],
                                ["Value", "C__0: Type"],
                                ["Value", "\\QName: Symbol"],
                                ["Value", "\"test-code.type C.constructor().(this)\": String"],
                              ]
                            ],
                            ["Value", "\\word: Symbol"],
                            ["Value", "\\constructor: Symbol"],
                            ["Value", "\\returnDecl: Symbol"],
                            ["Decl", [
                                ["LeftName", "return__0"],
                                ["Value", "\\type: Symbol"],
                                ["Value", "Void: Type"],
                                ["Value", "\\QName: Symbol"],
                                ["Value", "\"test-code.type C.constructor().return=\": String"],
                                ["Value", "\\ssa: Symbol"],
                                ["Value", "void: Void"],
                              ]
                            ],
                            ["Value", "\\stay: Symbol"], ["Stay"],
                            ["Block", [
                              ]
                            ],
                          ]
                        ],
                      ]
                    ],
                    [ "Decl", [
                        ["LeftName", "C__0"],
                        [ "Value", "\\typeDecl: Symbol" ],
                        [ "Value", "C__0: Type" ],
                        [ "Value", "\\stay: Symbol" ],
                        [ "Stay", "kotlin.Unit" ],
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.type C\": String" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ],
                      ]
                    ],
                    [ "Call", [
                        ["Value", "nym`=`: Function"],
                        ["LeftName", "C__0"],
                        ["Value", "C__0: Type"]
                      ]
                    ],
                    ["Call", [
                        ["Value", "class: Function"],
                        ["Value", "\\word: Symbol"], ["Value", "\\C: Symbol"],
                        ["Value", "\\concrete: Symbol"], ["Value", "true: Boolean"],
                        ["Fun", [
                            ["Value", "\\typeDefined: Symbol"], ["Value", "C__0: Type"],
                            ["Block", [
                                ["Block", []],
                                ["Block", []],
                              ]
                            ]
                          ]
                        ],
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "alias__5" ],
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.alias\": String" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ]
                      ]
                    ],
                    [ "Call", [
                        [ "Value", "nym`=`: Function" ],
                        [ "LeftName", "alias__5"],
                        [ "Value", "C__0: Type" ],
                      ]
                    ],
                    [ "Value", "void: Void" ],
                    [ "Decl", [
                        [ "LeftName", "o__6" ],
                        [ "Value", "\\type: Symbol" ],
                        [ "Value", "C__0: Type" ], // Type has been inlined
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.o\": String" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ]
                      ]
                    ],
                    [ "Decl", [
                        [ "LeftName", "p__7" ],
                        [ "Value", "\\type: Symbol" ],
                        [ "Value", "C__0: Type" ], // Type has been inlined
                        [ "Value", "\\QName: Symbol" ],
                        [ "Value", "\"test-code.p\": String" ],
                        [ "Value", "\\ssa: Symbol" ],
                        [ "Value", "void: Void" ]
                      ]
                    ],
                    ["Value", "void: Void"],
                  ]
                ],
            types: {
              "AnyValue": { abstract: true },
              "C": {
                methods: [
                  { name: "constructor__3", visibility: "public", open: false, kind: "Constructor" }
                ],
                supers: [ "AnyValue__0" ],
              },
              "Void": { supers: [] },
            }
          },
        }
        """,
    )

    @Test
    fun inheritedReassignability() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |interface I {
            |  var m;
            |  p;
            |}
            |interface J {
            |  set n() {}
            |}
            |interface K extends I, J {
            |  m; n; o;
            |  p; q;
            |  set o() {}
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          define: {
            body:
              ```
              I__0 extends AnyValue;
              @property(\m) @stay @fromType(I__0) var m__7;
              @property(\p) @stay @fromType(I__0) let p__8;
              @typeDecl(I__0) @stay let I__0;
              I__0 = type (I__0);
              @typeDecl(J__1) @stay let J__1;
              J__1 = type (J__1);
              @typeDecl(K__2) @stay let K__2;
              K__2 = type (K__2);
              interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
                  do {};
                  do {};
                  do {}
              });
              J__1 extends AnyValue;
              @property(\n) @visibility(\public) @stay @fromType(J__1) let n__10;
              @method(\n) @setter @fn @stay @fromType(J__1) let nym`set.n__11`;
              nym`set.n__11` = (@stay fn nym`set.n`(@impliedThis(J__1) this__3: J__1) /* return__0 */: Void {
                  fn__12: do {}
              });
              interface(\word, \J, \concrete, false, @typeDefined(J__1) fn {
                  do {};
                  do {};
                  do {}
              });
              K__2 extends I__0;
              K__2 extends J__1;
              @property(\m) @stay @fromType(K__2) var m__14;
              @property(\n) @stay @fromType(K__2) var n__15;
              @property(\o) @stay @fromType(K__2) var o__16;
              @property(\p) @stay @fromType(K__2) let p__17;
              @property(\q) @stay @fromType(K__2) let q__18;
              @method(\o) @setter @fn @stay @fromType(K__2) let nym`set.o__19`;
              nym`set.o__19` = (@stay fn nym`set.o`(@impliedThis(K__2) this__4: K__2) /* return__1 */: Void {
                  fn__20: do {}
              });
              interface(\word, \K, \concrete, false, @typeDefined(K__2) fn {
                  do {};
                  do {};
                  do {};
                  do {};
                  do {};
                  do {};
                  do {};
                  do {}
              });
              type (K__2)

              ```
          }
        }
        """,
    )

    @Test
    fun functionalInterfaceAbbreviatedSyntax() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |export @fun interface MyFunction(x: Int): Boolean;
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body:
            |      ```
            |      @typeDecl(MyFunction) @stay @functionalInterface let `test//`.MyFunction;
            |      `test//`.MyFunction = type (MyFunction);
            |      do {};
            |      MyFunction extends AnyValue;
            |      @fn @stay @fromType(MyFunction) let apply__0;
            |## No `this` parameter on functional interface apply methods.
            |      apply__0 = fn apply(x__0 /* aka x */: Int32) /* return__0 */: Boolean {
            |        fn__0: do {
            |          pureVirtual()
            |        }
            |      };
            |      interface(\word, \MyFunction, void, void, void, \concrete, false, @typeDefined(MyFunction) fn {
            |          do {};
            |          do {}
            |      });
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun coalesce() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |export let prod(i: Int, j: Int?): Int { i * (j ?? 1) }
            |export let prodWrap(i: Int, j: List<Int?>): Int { i * (j[0] ?? 1) }
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body:
            |      ```
            |      @fn let `test//`.prod, @fn `test//`.prodWrap;
            |      `test//`.prod = (@stay fn prod(i__0 /* aka i */: Int32, j__0 /* aka j */: Int32?) /* return__0 */: Int32 {
            |          fn__0: do {
            |            i__0 *{
            |              if (j__0 != null) {
            |                j__0
            |              } else {
            |                1
            |              }
            |            }
            |          }
            |      });
            |      `test//`.prodWrap = fn prodWrap(i__1 /* aka i */: Int32, j__1 /* aka j */: List<Int32?>) /* return__1 */: Int32 {
            |        fn__1: do {
            |          i__1 * do {
            |            let subject#0;
            |            subject#0 = do_bind_get(j__1)(0);
            |            {
            |              if (subject#0 != null) {
            |                subject#0
            |              } else {
            |                1
            |              }
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
    fun optionalParametersNotInlined() = assertModuleAtStage(
        stage = Stage.Run,
        input = $$"""
        let f(i: Int = 42): Int { i };
        console.log("f( )=${f().toString()}");
        console.log("f(1)=${f(1).toString()}");
        """.trimIndent(),
        want = """{
          define: {
            body:
            ```
            let console#0;
            console#0 = getConsole();
            @fn let f__0;
            f__0 = (@stay fn f(@optional(true) i__0 /* aka i */: Int32?) /* return__0 */: Int32 {
                fn__0: do {
                  let i__1 /* aka i */: Int32;
                  i__1 = if(i__0 == null, fn {
                      42
                    }, \else, fn (f#0) {
                      f#0(fn {
                          i__0
                      })
                  });
                  void;
                  i__1
                }
            });
            do_bind_log(console#0)(cat("f( )=", do_bind_toString(42)()));
            do_bind_log(console#0)(cat("f(1)=", do_bind_toString(1)()));

            ```
          },
          stdout: "f( )=42\nf(1)=1\n",
          run: "void: Void"
        }
        """,
    )

    @Test
    fun conditionallyAssignedConstNotInlined() = assertModuleAtStage(
        stage = Stage.Run,
        input = $$"""
        let f(b: Boolean): Int {
          let i: Int;
          if (b) { i = -1 } else { i = 1 }
          i
        };
        console.log("f(true )=${f(true)}");
        console.log("f(false)=${f(false)}");
        """.trimIndent(),
        want = """{
          define: {
            body:
            ```
            let console#0;
            console#0 = getConsole();
            @fn let f__1;
            f__1 = fn f(b__2 /* aka b */: Boolean) /* return__0 */: Int32 {
              fn__3: do {
                let i__4: Int32;
                if(b__2, fn {
                    i__4 = -1
                  }, \else, fn (f#0) {
                    f#0(fn {
                        i__4 = 1
                    })
                });
                i__4
              }
            };
            do_bind_log(console#0)(cat("f(true )=", f__1(true)));
            do_bind_log(console#0)(cat("f(false)=", f__1(false)));

            ```
          },
          stdout: "f(true )=-1\nf(false)=1\n",
          run: "void: Void"
        }
        """,
    )

    @Test
    fun classesWithDisclosures() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let f(x) {
            |  class ClosesOverNothing {}
            |
            |  interface ClosesOverX {
            |    get p() { x }
            |  }
            |
            |  do {
            |    let y = x + 1;
            |
            |    class ClosesOverY extends ClosesOverX {
            |      public get q() { y }
            |    }
            |    new ClosesOverY()
            |  }
            |
            |  new ClosesOverNothing();
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          define: {
            body:
            ```
            @fn let f__6;
            ClosesOverNothing__0 extends AnyValue;
            @fn @method(\constructor) @visibility(\public) @stay @fromType(ClosesOverNothing__0) let constructor__10;
            constructor__10 = (@stay fn constructor(@impliedThis(ClosesOverNothing__0) this__11: ClosesOverNothing__0) /* return__0 */: Void {});
            ClosesOverX__1 extends AnyValue;
            @property(\p) @visibility(\public) @stay @fromType(ClosesOverX__1) let p__13;
            @method(\p) @getter @fn @stay @fromType(ClosesOverX__1) let nym`get.p__14`;
            nym`get.p__14` = fn nym`get.p`(@impliedThis(ClosesOverX__1) this__3: ClosesOverX__1) {
              fn__15: do {
                getCR(getp(cr__23, this__3), 0)
              }
            };
            @property(\cr__23) @visibility(\protected) @stay @synthetic @fromType(ClosesOverX__1) let cr__23: ClosRec;
            ClosesOverY__2 extends ClosesOverX__1;
            @property(\q) @visibility(\public) @stay @fromType(ClosesOverY__2) let q__18;
            @method(\q) @getter @visibility(\public) @fn @stay @fromType(ClosesOverY__2) let nym`get.q__19`;
            nym`get.q__19` = fn nym`get.q`(@impliedThis(ClosesOverY__2) this__4: ClosesOverY__2) {
              fn__20: do {
                getCR(getp(cr__24, this__4), 0)
              }
            };
            @fn @method(\constructor) @visibility(\public) @stay @fromType(ClosesOverY__2) let constructor__21;
            constructor__21 = (@stay fn constructor(@impliedThis(ClosesOverY__2) this__22: ClosesOverY__2, cr__25: ClosRec, cr__26: ClosRec) /* return__1 */: Void {
                setp(cr__27, this__22, cr__25);
                setp(cr__24, this__22, cr__26)
            });
            @property(\cr__27) @visibility(\private) @stay @synthetic @fromType(ClosesOverY__2) let cr__27: ClosRec;
            @property(\cr__23) @visibility(\private) @stay @synthetic @fromType(ClosesOverY__2) let cr__28: ClosRec;
            @method(\cr__23) @getter @visibility(\protected) @stay @synthetic @fn @fromType(ClosesOverY__2) let nym`get.cr__29`;
            nym`get.cr__29` = fn (@impliedThis(ClosesOverY__2) this__30: ClosesOverY__2) {
              getp(cr__27, this__30)
            };
            @property(\cr__24) @visibility(\private) @stay @synthetic @fromType(ClosesOverY__2) let cr__24: ClosRec;
            f__6 = fn f(x__7 /* aka x */) {
              fn__8: do {
                let cr#31;
                cr#31 = makeCR(\word, x__7, \setter, fn (v#32) {
                    x__7 = v#32
                });
                @typeDecl(ClosesOverNothing__0) @stay let ClosesOverNothing__0;
                ClosesOverNothing__0 = type (ClosesOverNothing__0);
                @typeDecl(ClosesOverX__1) @stay let ClosesOverX__1;
                ClosesOverX__1 = type (ClosesOverX__1);
                class(\word, \ClosesOverNothing, \concrete, true, @typeDefined(ClosesOverNothing__0) fn {
                    do {};
                    do {}
                });
                interface(\word, \ClosesOverX, \concrete, false, @typeDefined(ClosesOverX__1) fn {
                    do {};
                    do {};
                    do {};
                    do {}
                });
                do (fn {
                    let cr#33;
                    cr#33 = makeCR(\word, y__16, \setter, fn (v#34) {
                        y__16 = v#34
                    });
                    @typeDecl(ClosesOverY__2) @stay let ClosesOverY__2;
                    ClosesOverY__2 = type (ClosesOverY__2);
                    let y__16;
                    y__16 = x__7 + 1;
                    class(\word, \ClosesOverY, \concrete, true, @typeDefined(ClosesOverY__2) fn {
                        do {};
                        do {};
                        do {};
                        do {};
                        do {};
                        do {};
                        do {};
                        do {}
                    });
                    new ClosesOverY__2(cr#31, cr#33)
                });
                new ClosesOverNothing__0();
              }
            };

            ```
          }
        }
        """,
    )

    @Test
    fun impliedGettersAndSetters() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |class C(public var j, public k) extends I {}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          define: {
            body: ```
            C__0 extends I;
            @constructorProperty @property(\j) @visibility(\public) @stay @fromType(C__0) var j__2;
            @constructorProperty @property(\k) @visibility(\public) @stay @fromType(C__0) let k__3;
            @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__4;
            constructor__4 = fn constructor(@impliedThis(C__0) this__5: C__0, j__6 /* aka j */, k__7 /* aka k */) /* return__0 */: Void {
              do {
                let t#0;
                setp(j__2, this__5, t#0 = j__6);
                t#0
              };
              do {
                let t#1;
                setp(k__3, this__5, t#1 = k__7);
                t#1
              };
            };
            @getter @method(\j) @fn @visibility(\public) @stay @fromType(C__0) let getj__8;
            getj__8 = fn (@impliedThis(C__0) this__9: C__0) /* return__10 */{
              return__10 = getp(j__2, this__9)
            };
            @setter @method(\j) @fn @visibility(\public) @stay @fromType(C__0) let setj__11;
            setj__11 = fn (@impliedThis(C__0) this__12: C__0, newJ__13) /* return__14 */: Void {
              setp(j__2, this__12, newJ__13);
              return__14 = void
            };
            @getter @method(\k) @fn @visibility(\public) @stay @fromType(C__0) let getk__12;
            getk__12 = fn (@impliedThis(C__0) this__13: C__0) /* return__15 */{
              return__15 = getp(k__3, this__13)
            };
            @typeDecl(C__0) @stay let C__0;
            C__0 = type (C__0);
            class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
                do {};
                do {};
                do {};
                do {};
                do {};
                do {};
                do {}
            });
            type (C__0)

            ```,
            types: {
              // Implied getters and setters show up in the member list.
              "C": {
                properties: [
                  {
                    name: "j__2", visibility: "public", abstract: false,
                    getter: "getj__8", setter: "setj__11",
                    metadata: {
                      "var": ["void: Void"],
                    }
                  },
                  { name: "k__3", abstract: false, visibility: "public", getter: "getk__12" },
                ],
                methods: [
                  {
                    name: "getj__8", symbol: "j",
                    visibility: "public", kind: "Getter", open: false
                  },
                  {
                    name: "setj__11", symbol: "j",
                    visibility: "public", kind: "Setter", open: false
                  },
                  {
                    name: "getk__12", symbol: "k",
                    visibility: "public", kind: "Getter", open: false
                  },
                  {
                    name: "constructor__4",
                    visibility: "public", kind: "Constructor", open: false
                  },
                ]
              },
              "Void": { supers: [] },
            }
          }
        }
        """,
    )

    @Test
    fun propertyOnlyInterface() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        |interface I { public p; }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        |{
        |  define: {
        |    body:
        |      ```
        |      I__0 extends AnyValue;
        |      @property(\p) @visibility(\public) @stay @fromType(I__0) let p__3;
        |      @typeDecl(I__0) @stay let I__0;
        |      I__0 = type (I__0);
        |      interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
        |          do {};
        |          do {}
        |      });
        |      type (I__0)
        |
        |      ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun exportedNamePropagates() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
        |let t = AnyValue;
        |let x: t = 42;
        """.trimMargin(),
        want = """
        |{
        |  define: {
        |    body: {
        |      tree: [ "Block", [
        |          [ "Decl", [
        |              [ "LeftName", "t__2" ],
        |              [ "Value", "\\QName: Symbol" ],
        |              [ "Value", "\"test-code.t\": String" ],
        |              [ "Value", "\\ssa: Symbol" ], [ "Value", "void: Void" ],
        |            ]
        |          ],
        |          [ "Call", [
        |              [ "Value", "nym`=`: Function" ],
        |              [ "LeftName", "t__2" ],
        |              [ "Value", "AnyValue: Type" ],
        |            ]
        |          ],
        |          [ "Decl", [
        |              [ "LeftName", "x__3" ],
        |              [ "Value", "\\type: Symbol" ], [ "Value", "AnyValue: Type" ],
        |              [ "Value", "\\QName: Symbol" ],
        |              [ "Value", "\"test-code.x\": String" ],
        |              [ "Value", "\\ssa: Symbol" ], [ "Value", "void: Void" ],
        |            ]
        |          ],
        |          [ "Call", [
        |              [ "Value", "nym`=`: Function" ],
        |              [ "LeftName", "x__3" ],
        |              [ "Value", "42: Int32" ],
        |            ]
        |          ],
        |          [ "Value", "void: Void" ],
        |        ]
        |      ],
        |
        |      code:
        |        ```
        |        let t__2;
        |        t__2 = type (AnyValue);
        |        let x__3: AnyValue;
        |        x__3 = 42;
        |
        |        ```
        |    }
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun macrosInEscapes() {
        var doNotCallWasCalled = false
        assertModuleAtStage(
            stage = Stage.Define,
            want = """
                |{
                |  stageCompleted: "Define",
                |  define: {
                |    body:
                |        ```
                |        \(doNotCall(1 + 1, unhole(2)))
                |
                |        ```
                |  }
                |}
            """.trimMargin(),
        ) { module, _ ->
            // The idea for escapes is that macros that take escapes can progressively turn
            // CST elements into AST elements and then use some `eval` builtin to unescape the
            // result.
            // Fake a tree after that CST->AST conversion.
            val input = Document(module).treeFarm.grow(Position(module.loc, 0, 0)) {
                Esc {
                    Call {
                        Rn(BuiltinName("doNotCall"))
                        // This call should not be inlined as it's escaped
                        Call(BuiltinFuns.vPlusFn) {
                            V(Value(1, TInt))
                            V(Value(1, TInt))
                        }
                        Call {
                            Rn(unholeBuiltinName)
                            // This call is in a hole so should be inlined
                            Call(BuiltinFuns.vPlusFn) {
                                V(Value(1, TInt))
                                V(Value(1, TInt))
                            }
                        }
                    }
                }
            }

            module.deliverContent(input)
            module.addEnvironmentBindings(
                mapOf(
                    BuiltinName("doNotCall") to Value(
                        object : BuiltinStatelessMacroValue, NamedBuiltinFun {
                            override val name = "doNotCall"
                            override val sigs: List<Signature2>? = null
                            override fun invoke(
                                macroEnv: MacroEnvironment,
                                interpMode: InterpMode,
                            ): PartialResult {
                                doNotCallWasCalled = true
                                return NotYet
                            }
                        },
                    ),
                ),
            )
        }
        assertFalse(doNotCallWasCalled)
    }

    @Test
    fun parameterizedConstructorReference() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |class C<T> {}
            |let f<U>(): C<U> {}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  define: {
            |    body: {
            |      tree: [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "C__0" ],
            |              [ "Value", "\\typeDecl: Symbol" ],
            |              [ "Value", "C__0<T__0>: Type" ],
            |              [ "Value", "\\stay: Symbol" ],
            |              [ "Stay", "kotlin.Unit" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.type C\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "C__0" ],
            |              [ "Value", "C__0: Type" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "f__0" ],
            |              [ "Value", "\\fn: Symbol" ],
            |              [ "Value", "void: Void" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.f()\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "T__0" ],
            |              [ "Value", "\\typeFormal: Symbol" ],
            |              [ "Value", "\\T: Symbol" ],
            |              [ "Value", "\\memberTypeFormal: Symbol" ],
            |              [ "Value", "\\T: Symbol" ],
            |              [ "Value", "\\typeDefined: Symbol" ],
            |              [ "Value", "T__0: Type" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.type C.<T>\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |              [ "Value", "\\fromType: Symbol" ],
            |              [ "Value", "C__0<T__0>: Type" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "T__0" ],
            |              [ "Value", "T__0: Type" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "extends: Function" ],
            |              [ "Value", "C__0<T__0>: Type" ],
            |              [ "Value", "AnyValue: Type" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "constructor__0" ],
            |              ["Value", "\\fn: Symbol"],
            |              ["Value", "void: Void"],
            |              [ "Value", "\\method: Symbol" ],
            |              [ "Value", "\\constructor: Symbol" ],
            |              [ "Value", "\\visibility: Symbol" ],
            |              [ "Value", "\\public: Symbol" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.type C.constructor()\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |              [ "Value", "\\stay: Symbol" ],
            |              [ "Stay" ],
            |              [ "Value", "\\parameterNameSymbolsList: Symbol" ],
            |              [ "Value", "[null]: List" ],
            |              [ "Value", "\\fromType: Symbol" ],
            |              [ "Value", "C__0<T__0>: Type" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "constructor__0" ],
            |              [ "Fun", [
            |                  [ "Decl", [
            |                      [ "LeftName", "this__0" ],
            |                      [ "Value", "\\type: Symbol" ],
            |                      [ "Value", "C__0<T__0>: Type" ],
            |                      [ "Value", "\\impliedThis: Symbol" ],
            |                      [ "Value", "C__0<T__0>: Type" ],
            |                      [ "Value", "\\QName: Symbol" ],
            |                      [ "Value", "\"test-code.type C.constructor().(this)\": String" ],
            |                    ]
            |                  ],
            |                  [ "Value", "\\word: Symbol" ],
            |                  [ "Value", "\\constructor: Symbol" ],
            |                  [ "Value", "\\returnDecl: Symbol" ],
            |                  [ "Decl", [
            |                      [ "LeftName", "return__0" ],
            |                      [ "Value", "\\type: Symbol" ],
            |                      [ "Value", "Void: Type" ],
            |                      [ "Value", "\\QName: Symbol" ],
            |                      [ "Value", "\"test-code.type C.constructor().return=\": String" ],
            |                      [ "Value", "\\ssa: Symbol" ],
            |                      [ "Value", "void: Void" ],
            |                    ]
            |                  ],
            |                  [ "Value", "\\stay: Symbol" ],
            |                  [ "Stay" ],
            |                  [ "Block", [
            |                    ]
            |                  ]
            |                ]
            |              ]
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "class: Function" ],
            |              [ "Value", "\\word: Symbol" ],
            |              [ "Value", "\\C: Symbol" ],
            |              [ "Value", "\\concrete: Symbol" ],
            |              [ "Value", "true: Boolean" ],
            |              [ "Fun", [
            |                  [ "Value", "\\typeDefined: Symbol" ],
            |                  [ "Value", "C__0<T__0>: Type" ],
            |                  [ "Block", [
            |                      [ "Block", [] ],
            |                      [ "Block", [] ],
            |                      [ "Block", [] ],
            |                    ]
            |                  ]
            |                ]
            |              ]
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "U__0" ],
            |              [ "Value", "\\typeFormal: Symbol" ],
            |              [ "Value", "\\U: Symbol" ],
            |              [ "Value", "\\typeDecl: Symbol" ],
            |              [ "Value", "U__0: Type" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.f().<U>\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "U__0" ],
            |              [ "Value", "U__0: Type" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "f__0" ],
            |              [ "Fun", [
            |                  [ "Value", "\\returnDecl: Symbol" ],
            |                  [ "Decl", [
            |                      [ "LeftName", "return__1" ],
            |                      [ "Value", "\\type: Symbol" ],
            |                      [ "Value", "C__0<U__0>: Type" ],
            |                      [ "Value", "\\QName: Symbol" ],
            |                      [ "Value", "\"test-code.f().return=\": String" ],
            |                      [ "Value", "\\ssa: Symbol" ],
            |                      [ "Value", "void: Void" ],
            |                    ]
            |                  ],
            |                  [ "Value", "\\returnedFrom: Symbol" ],
            |                  [ "Value", "true: Boolean" ],
            |                  [ "Value", "\\word: Symbol" ],
            |                  [ "Value", "\\f: Symbol" ],
            |                  [ "Value", "\\typeFormal: Symbol" ],
            |                  [ "Value", "U__0: Type" ],
            |                  [ "Value", "\\stay: Symbol" ],
            |                  [ "Stay" ],
            |                  [ "Block", [
            |                      [ "Value", "\\label: Symbol" ],
            |                      [ "LeftName", "fn__0" ],
            |                    ]
            |                  ]
            |                ]
            |              ]
            |            ]
            |          ],
            |          [ "Value", "void: Void" ],
            |        ]
            |      ],
            |      code: ```
            |          @typeDecl(C__0<T__0>) @stay let C__0;
            |          C__0 = type (C__0);
            |          @fn let f__0;
            |          @typeFormal(\T) @memberTypeFormal(\T) @typeDefined(T__0) @fromType(C__0<T__0>) let T__0;
            |          T__0 = type (T__0);
            |          C__0<T__0> extends AnyValue;
            |          @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0<T__0>) let constructor__0;
            |          constructor__0 = (@stay fn constructor(@impliedThis(C__0<T__0>) this__0: C__0<T__0>) /* return__0 */: Void {});
            |          class(\word, \C, \concrete, true, @typeDefined(C__0<T__0>) fn {
            |              do {};
            |              do {};
            |              do {}
            |          });
            |          @typeFormal(\U) @typeDecl(U__0) let U__0;
            |          U__0 = type (U__0);
            |          f__0 = (@stay fn f<U__0 extends AnyValue> /* return__1 */: (C__0<U__0>) {
            |              fn__0: do {}
            |          });
            |
            |          ```
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun staticRead() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |class C {
            |    public static foo = "FOO";
            |}
            |C.foo
        """.trimMargin(),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  define: {
            |    body: ```
            |        C__0 extends AnyValue;
            |        @staticProperty(\foo) @static @visibility(\public) @stay @fromType(C__0) let foo__0;
            |        foo__0 = "FOO";
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__0: C__0) /* return__0 */: Void {});
            |        @typeDecl(C__0) @stay let C__0;
            |        C__0 = type (C__0);
            |        class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |            do {};
            |            do {};
            |            do {}
            |        });
            |        getStatic(C__0, \foo)
            |
            |        ````,
            |    types: {
            |      "AnyValue": { abstract: true },
            |      "C": {
            |        supers: ["AnyValue__0"],
            |        methods: [
            |          { name: "constructor__0",
            |            visibility: "public", kind: "Constructor", open: false },
            |        ],
            |        staticProperties: [
            |          {
            |            name: "foo__0",
            |            visibility: "public"
            |          }
            |        ]
            |      },
            |      "Void": { supers: [] },
            |    }
            |  },
            |  run: "\"FOO\": String",
            |}
        """.trimMargin(),
    )

    @Test
    fun complexTypeAliases() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let Sn = String?;
            |let Ds = Deque<Sn>;
            |let Dn = Ds?;
            |let s: Sn;
            |let d: Dn;
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: {
            |      tree: [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "Sn__0" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.Sn\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "Sn__0" ],
            |              [ "Value", "String?: Type" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "Ds__0" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.Ds\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "Ds__0" ],
            |              [ "Value", "Deque<String?>: Type" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "Dn__0" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.Dn\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "Dn__0" ],
            |              [ "Value", "Deque<String?>?: Type" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "s__0" ],
            |              [ "Value", "\\type: Symbol" ],
            |              [ "Value", "String?: Type" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.s\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "d__0" ],
            |              [ "Value", "\\type: Symbol" ],
            |              [ "Value", "Deque<String?>?: Type" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.d\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Value", "void: Void" ],
            |        ]
            |      ],
            |      code: ```
            |          let Sn__0;
            |          Sn__0 = type (String?);
            |          let Ds__0;
            |          Ds__0 = type (Deque<String?>);
            |          let Dn__0;
            |          Dn__0 = type (Deque<String?>?);
            |          let s__0: String?, d__0: Deque<String?>?;
            |
            |          ```
            |    }
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun typeArgsKept() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |class What<Thing>(
            |  public ints: List<Int>,
            |  public things: List<Thing>,
            |) {
            |  public work(that: Thing): Void {
            |    let another: What = this;
            |    let more: List = things;
            |  }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  disAmbiguate: {
            |    body:
            |      ```
            |      @typeDecl(What__0<Thing__0>) @hoistLeft(true) @resolution(What__0) @stay let What = type (What__0);
            |      class(\word, What, \concrete, true, @typeDefined(What__0<Thing__0>) fn {
            |          @typeFormal(\Thing) @memberTypeFormal(\Thing) @typeDefined(Thing__0) @resolution(Thing__0) let Thing = type (Thing__0);
            |          What__0<Thing__0> extends AnyValue;
            |          @constructorProperty @property(\ints) @maybeVar @visibility(\public) let ints /* aka ints */: List<Int>;
            |          @constructorProperty @property(\things) @maybeVar @visibility(\public) let things /* aka things */: List<Thing>;
            |          @method(\work) @visibility(\public) let work = fn(\word, work, @impliedThis(What__0<Thing__0>) let this__0: What__0<Thing__0>, let that /* aka that */: Thing, \outType, Void, fn {
            |              let another: What = this(What__0<Thing__0>), more: List = things;
            |          });
            |      });
            |      What
            |
            |      ```,
            |  },
            |  syntaxMacro: {
            |    body:
            |      ```
            |      @typeDecl(What__0<Thing__0>) @stay let What__0 = type (What__0);
            |      class(\word, \What, \concrete, true, @typeDefined(What__0<Thing__0>) fn {
            |          @typeFormal(\Thing) @memberTypeFormal(\Thing) @typeDefined(Thing__0) let Thing__0 = type (Thing__0);
            |          let typeof_things#0 = List<Thing__0>, typeof_ints#0 = List<Int>;
            |          What__0<Thing__0> extends AnyValue;
            |          @constructorProperty @property(\ints) @maybeVar @visibility(\public) let ints__0: typeof_ints#0;
            |          @constructorProperty @property(\things) @maybeVar @visibility(\public) let things__0: typeof_things#0;
            |          @method(\work) @visibility(\public) @fn let work__0 = fn work(@impliedThis(What__0<Thing__0>) this__0: What__0<Thing__0>, that__0 /* aka that */: Thing__0) /* return__0 */: (Void) {
            |            fn__0: do {
            |              let another__0: What__0 = this(What__0<Thing__0>), more__0: List = do_iget_things(type (What__0<Thing__0>), this(What__0<Thing__0>));
            |            }
            |          };
            |          @method(\constructor) @visibility(\public) let constructor__0 = fn constructor(@impliedThis(What__0<Thing__0>) this__1: What__0<Thing__0>, ints__1 /* aka ints */: typeof_ints#0, things__1 /* aka things */: typeof_things#0) /* return__1 */: Void {
            |            do {
            |              let t#0;
            |              do_iset_ints(type (What__0<Thing__0>), this(What__0<Thing__0>), t#0 = ints__1);
            |              t#0
            |            };
            |            do {
            |              let t#1;
            |              do_iset_things(type (What__0<Thing__0>), this(What__0<Thing__0>), t#1 = things__1);
            |              t#1
            |            };
            |          };
            |      });
            |      What__0
            |
            |      ```,
            |  },
            |  define: {
            |    body:
            |      ```
            |      @typeFormal(\Thing) @memberTypeFormal(\Thing) @typeDefined(Thing__0) @fromType(What__0<Thing__0>) let Thing__0;
            |      Thing__0 = type (Thing__0);
            |      let typeof_things#0;
            |      typeof_things#0 = type (List<Thing__0>);
            |      let typeof_ints#0;
            |      typeof_ints#0 = type (List<Int32>);
            |      What__0<Thing__0> extends AnyValue;
            |      @constructorProperty @property(\ints) @visibility(\public) @stay @fromType(What__0<Thing__0>) let ints__0: List<Int32>;
            |      @constructorProperty @property(\things) @visibility(\public) @stay @fromType(What__0<Thing__0>) let things__0: List<Thing__0>;
            |      @method(\work) @visibility(\public) @fn @stay @fromType(What__0<Thing__0>) let work__0;
            |      work__0 = fn work(@impliedThis(What__0<Thing__0>) this__0: What__0<Thing__0>, that__0 /* aka that */: Thing__0) /* return__0 */: Void {
            |        fn__0: do {
            |          let another__0: What__0;
            |          another__0 = this__0;
            |          let more__0: List;
            |          more__0 = getp(things__0, this__0);
            |        }
            |      };
            |      @fn @method(\constructor) @visibility(\public) @stay @fromType(What__0<Thing__0>) let constructor__0;
            |      constructor__0 = fn constructor(@impliedThis(What__0<Thing__0>) this__1: What__0<Thing__0>, ints__1 /* aka ints */: List<Int32>, things__1 /* aka things */: List<Thing__0>) /* return__1 */: Void {
            |        do {
            |          let t#0;
            |          setp(ints__0, this__1, t#0 = ints__1);
            |          t#0
            |        };
            |        do {
            |          let t#1;
            |          setp(things__0, this__1, t#1 = things__1);
            |          t#1
            |        };
            |      };
            |      @getter @method(\ints) @fn @visibility(\public) @stay @fromType(What__0<Thing__0>) let getints__0;
            |      getints__0 = fn (@impliedThis(What__0<Thing__0>) this__2: What__0<Thing__0>) /* return__2 */: (List<Int32>) {
            |        return__2 = getp(ints__0, this__2)
            |      };
            |      @getter @method(\things) @fn @visibility(\public) @stay @fromType(What__0<Thing__0>) let getthings__0;
            |      getthings__0 = fn (@impliedThis(What__0<Thing__0>) this__3: What__0<Thing__0>) /* return__3 */: (List<Thing__0>) {
            |        return__3 = getp(things__0, this__3)
            |      };
            |      @typeDecl(What__0<Thing__0>) @stay let What__0;
            |      What__0 = type (What__0);
            |      class(\word, \What, \concrete, true, @typeDefined(What__0<Thing__0>) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      type (What__0)
            |
            |      ```,
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun functionTypesInline() = assertModuleAtStage(
        stage = Stage.Type,
        input = """
            |let f: fn<T>(List<T>): List<T> = never();
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: {
            |      tree: [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "f__0" ],
            |              [ "Value", "\\type: Symbol" ],
            |              [ "Block", [
            |                  [ "Decl", [
            |                      [ "LeftName", "T__0" ],
            |                      [ "Value", "\\typeFormal: Symbol" ],
            |                      [ "Value", "\\T: Symbol" ],
            |                      [ "Value", "\\typeDecl: Symbol" ],
            |                      [ "Value", "T__0: Type" ],
            |                      [ "Value", "\\QName: Symbol" ],
            |                      [ "Value", "\"test-code.f.<T>\": String" ],
            |                      [ "Value", "\\ssa: Symbol" ],
            |                      [ "Value", "void: Void" ],
            |                    ]
            |                  ],
            |                  [ "Call", [
            |                      [ "Value", "nym`=`: Function" ],
            |                      [ "LeftName", "T__0" ],
            |                      [ "Value", "T__0: Type" ],
            |                    ]
            |                  ],
            |                  [ "Value", "Fn__0<List<T__0>, List<T__0>>: Type" ]
            |                ]
            |              ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.f\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Call", [
            |              [ "Value", "nym`=`: Function" ],
            |              [ "LeftName", "f__0" ],
            |              [ "Call", [
            |                  [ "RightName", "never" ]
            |                ]
            |              ]
            |            ]
            |          ],
            |          [ "Value", "void: Void" ],
            |        ]
            |      ],
            |      code: ```
            |          let f__0: do {
            |            @typeFormal(\T) @typeDecl(T__0) let T__0;
            |            T__0 = type (T__0);
            |            type (fn<T__0 extends AnyValue>(List<T__0>): List<T__0>)
            |          };
            |          f__0 = never();
            |
            |          ```
            |    }
            |  },
            |
            |  type: {
            |    body: ```
            |        @typeFormal(\T) @typeDecl(T__0) let T__0;
            |        T__0 = type (T__0);
            |        let f__0: (fn<T__0 extends AnyValue>(List<T__0>): List<T__0>);
            |        f__0 = never();
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedEmptyType() = assertModuleAtStage(
        input = """
            |let functionThatNestsAType(): Void {
            |    interface EmptyHelper {}    // <-- needs a placeholder at the top level
            |    // See the comments in TmpLControlFlow and ClosureConvertClasses
            |}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @fn let functionThatNestsAType__0;
            |        EmptyHelper__0 extends AnyValue;
            |        @typePlaceholder(EmptyHelper__0) let typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        functionThatNestsAType__0 = fn functionThatNestsAType /* return__0 */: Void {
            |          fn__0: do {
            |            @typeDecl(EmptyHelper__0) @stay let EmptyHelper__0;
            |            EmptyHelper__0 = type (EmptyHelper__0);
            |            interface(\word, \EmptyHelper, \concrete, false, @typeDefined(EmptyHelper__0) fn {
            |                do {}
            |            });
            |            type (EmptyHelper__0)
            |          }
            |        };
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
        stage = Stage.Define,
    )

    @Test
    fun whenBlock() = assertModuleAtStage(
        stage = Stage.Define,
        // Test both valid content and error content together.
        input = """
            |interface A { }
            |class B extends A { }
            |let b = new B();
            |let f(a: A): A {
            |  when (a) {
            |    b -> a;
            |    // Comments are fine, but unrelated statements aren't.
            |    wordYall();
            |    is B -> a;
            |    (fancyExpression + 4) -> a;
            |    4, is C -> a;
            |    else -> a;
            |    // Case after default is also bad, as is missing value.
            |    c ->;
            |    d -> a;
            |    e ->
            |  }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @typeDecl(A__0) @stay let A__0;
            |        A__0 = type (A__0);
            |        @typeDecl(B__0) @stay let B__0;
            |        B__0 = type (B__0);
            |        @fn let f__0;
            |        A__0 extends AnyValue;
            |        @typePlaceholder(A__0) let typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        interface(\word, \A, \concrete, false, @typeDefined(A__0) fn {
            |            do {}
            |        });
            |        B__0 extends A__0;
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(B__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(B__0) this__0: B__0) /* return__0 */: Void {});
            |        class(\word, \B, \concrete, true, @typeDefined(B__0) fn {
            |            do {};
            |            do {}
            |        });
            |        let b__0;
            |        b__0 = new B__0();
            |        f__0 = fn f(a__0 /* aka a */: A__0) /* return__1 */: A__0 {
            |          fn__0: do {
            |            do {
            |              if(a__0 == b__0, fn {
            |                  a__0
            |                }, \else_if, fn (f#0) {
            |                  f#0(is(a__0, B__0), fn {
            |                      a__0
            |                    }, \else_if, fn (f#1) {
            |                      f#1(a__0 == fancyExpression + 4, fn {
            |                          a__0
            |                        }, \else_if, fn (f#2) {
            |                          f#2(if(a__0 == 4, @stay fn {
            |                                true
            |                              }, \else, fn (f#3) {
            |                                f#3(fn {
            |                                    is(a__0, C)
            |                                })
            |                            }), fn {
            |                              a__0
            |                            }, \else, fn (f#4) {
            |                              f#4(fn {
            |                                  a__0
            |                              })
            |                          })
            |                      })
            |                  })
            |              })
            |            }
            |          }
            |        };
            |
            |        ```
            |  },
            |  errors: [
            |    "Operator ThinArrow expects at least 2 operands but got 1!",
            |    "Operator ThinArrow expects at least 2 operands but got 1!",
            |    "Expected a TopLevel here!",
            |    "Expected a TopLevel here!",
            |    "Invalid block content!",
            |    "Invalid block content!",
            |    "Other cases are invalid after else!",
            |    "Invalid block content!"
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun whenGeneric() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let f(maybe: List<Int>?): String {
            |  when (maybe) {
            |    is List<Int> -> "yep";
            |    else -> "nope";
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @fn let f__0;
            |        f__0 = fn f(maybe__0 /* aka maybe */: List<Int32>?) /* return__0 */: String {
            |          fn__0: do {
            |            do {
            |              if(is(maybe__0, List<Int32>), @stay fn {
            |                  "yep"
            |                }, \else, fn (f#0) {
            |                  f#0(@stay fn {
            |                      "nope"
            |                  })
            |              })
            |            }
            |          }
            |        };
            |
            |        ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun castingCall() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |// Prelude on ensuring that it's rename inside property bags.
            |let { a as b } = c;
            |// Now on to the casting call.
            |1 as Int;
            |2 as Mystery;
            |3 as List<Mystery>;
            |4 as;
            |5.as(Int);
            |6.as(Mystery);
            |7.as();
            |8.as;
            |[9][0] as Int;
            |10.toString() as String;
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        void;
            |        let t#0;
            |        t#0 = c;
            |        let b__0;
            |        b__0 = do_get_a(t#0);
            |        void;
            |        as(1, Int32);
            |        as(2, Mystery);
            |        as(3, type (List)<Mystery>);
            |        error (list("`(Leaf`", "4", "`Leaf)`", "as"));
            |        do_bind_as(5)(type (Int32));
            |        do_bind_as(6)(Mystery);
            |        do_bind_as(7)();
            |        do_get_as(8);
            |        as(do_bind_get(list(9))(0), Int32);
            |        as(do_bind_toString(10)(), String);
            |
            |        ```
            |  },
            |  errors: [
            |    "Operator As expects at least 2 operands but got 1!",
            |    "Expected a TopLevel here!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun instantiatesTestHarnesses() {
        val loc = ModuleName(
            dirPath("std", "testing"),
            libraryRootSegmentCount = 1,
            isPreface = false,
        )

        val (fakeStdTestModuleExports, consoleOutput) = withCapturingConsole(Log.Error) { console ->
            val fakeStdModule = Module(
                projectLogSink = LogSink.devNull,
                loc = loc,
                console = console,
                continueCondition = { true },
                mayRun = false,
            )
            fakeStdModule.deliverContent(
                ModuleSource(
                    filePath = filePath("std", "testing"),
                    fetchedContent = """
                        |export class Test {}
                        |export let runTestCases(testCases: List<Pair<String, (fn (Test): Void  throws Bubble)>>): Void {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )

            while (fakeStdModule.canAdvance()) {
                fakeStdModule.advance()
            }

            fakeStdModule.exports!!
        }

        assertEquals("", consoleOutput)

        assertModuleAtStage(
            // For `temper test` integration, we need to add instructions to
            // create instances of each concrete test fixture type when there
            // is a particular marker.
            stage = Stage.Define,
            want = """
                |{
                |  define: {
                |    body: ```
                |          @stay @imported(\(`std//testing/`.runTestCases)) @connected("::runTestCases") let runTestCases__0;
                |          runTestCases__0 = `std//testing/`.runTestCases;
                |          @stay @imported(\(`std//testing/`.Test)) let Test__0;
                |          Test__0 = type (Test);
                |          @implicit let Test__1;
                |          Test__1 = type (Test);
                |          @implicit @fn let runTestCases__1;
                |          runTestCases__1 = (fn runTestCases);
                |          @fn @test("- a test case -") let aTestCase__0;
                |          aTestCase__0 = (@stay fn aTestCase(test#0: Test) /* return__0 */: (Void | Bubble) {});
                |          @stay let `test//`.temper__testReport;
                |          `test//`.temper__testReport = runTestCases__0(list(new Pair("- a test case -", aTestCase__0)));
                |
                |          ```,
                |  }
                |}
            """.trimMargin(),
        ) { module, _ ->
            module.deliverContent(
                ModuleSource(
                    filePath = testCodeLocation,
                    fetchedContent = """
                        |test("- a test case -") {
                        |  // do something
                        |}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            module.addEnvironmentBindings(
                mapOf(
                    StagingFlags.defineStageHookCreateAndRunClasses to TBoolean.valueTrue,
                ),
            )
            module.addImplicitImports(fakeStdTestModuleExports)
        }
    }

    @Test
    fun badTests() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |test();
            |test("hi");
            |test("") {}
            |test(1) {}
            |test("hi", 2);
            |test(hi) {}
            |test("hi") { hi: String => assert(true) { "nope" } }
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @stay @imported(\(`std//testing/`.Test)) let Test__0;
            |        Test__0 = type (Test);
            |        test();
            |        test("hi");
            |        test("", @stay fn (test#0: Test) /* return__0 */: (Void | Bubble) {});
            |        test(1, @stay fn (test#1: Test) /* return__1 */: (Void | Bubble) {});
            |        test("hi", 2);
            |        test(hi, @stay fn (test#2: Test) /* return__2 */: (Void | Bubble) {});
            |        @fn @test("hi") let hi__0;
            |        hi__0 = fn hi(hi__1 /* aka hi */: String) /* return__3 */: (Void | Bubble) {
            |          assert(true, @stay fn {
            |              "nope"
            |          })
            |        };
            |
            |        ```
            |  },
            |  errors: [
            |    "Wrong number of arguments.  Expected 2!",
            |    "Wrong number of arguments.  Expected 2!",
            |    "Expected function type, but got Int32!",
            |    "Wrong number of arguments.  Expected 2!",
            |    "Wrong number of arguments.  Expected 2!",
            |    "Expected a name!",
            |    "Expected value of type String not Int32!",
            |    "Expected function type, but got Int32!",
            |    "Unable to evaluate!",
            |    "Invalid block content!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun goodTests() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |test("- does / this : work?") { assert(true) { "or what?" } }
            |test("does\tthis\nwork") { test => assert(false) { "or that" } }
            |test("again") { t => assert(true) { "whatever" } }
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @stay @imported(\(`std//testing/`.Test)) let Test__0;
            |        Test__0 = type (Test);
            |        @fn @test("- does / this : work?") let doesThisWork__0;
            |        doesThisWork__0 = fn doesThisWork(test#0: Test) /* return__0 */: (Void | Bubble) {
            |          do_bind_assert(test#0)(true, @stay fn {
            |              "or what?"
            |          })
            |        };
            |        @fn @test("does\tthis\nwork") let doesThisWork__1;
            |        doesThisWork__1 = fn doesThisWork(test__0 /* aka test */: Test) /* return__1 */: (Void | Bubble) {
            |          do_bind_assert(test__0)(false, @stay fn {
            |              "or that"
            |          })
            |        };
            |        @fn @test("again") let again__0;
            |        again__0 = fn again(t__0 /* aka t */: Test) /* return__2 */: (Void | Bubble) {
            |          do_bind_assert(t__0)(true, @stay fn {
            |              "whatever"
            |          })
            |        };
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun autoAssertMessage() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |test("hi") { let num = 4; assert(num == 3); }
            |test("ha") { let condition = false; assert(condition); }
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @stay @imported(\(`std//testing/`.Test)) let Test__0;
            |        Test__0 = type (Test);
            |        @fn @test("hi") let hi__0;
            |        hi__0 = fn hi(test#0: Test) /* return__0 */: (Void | Bubble) {
            |          let num__0;
            |          num__0 = 4;
            |          do {
            |            let actual#0;
            |            actual#0 = 4;
            |            let expected#0;
            |            expected#0 = 3;
            |            do_bind_assert(test#0)(false, fn {
            |                cat("expected num == (", do_bind_toString(3)(), ") not (", do_bind_toString(4)(), ")")
            |            })
            |          };
            |        };
            |        @fn @test("ha") let ha__0;
            |        ha__0 = fn ha(test#1: Test) /* return__1 */: (Void | Bubble) {
            |          let condition__0;
            |          condition__0 = false;
            |          do_bind_assert(test#1)(false, @stay fn {
            |              "expected condition"
            |          });
            |        };
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun classExtendsClass() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |class Apple {}
            |class Banana {}
            |interface Cherry {}
            |class Durian extends Apple, Banana & Cherry {}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  define: {
            |    body: ```
            |        Apple__0 extends AnyValue;
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(Apple__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(Apple__0) this__0: Apple__0) /* return__0 */: Void {});
            |        @typeDecl(Apple__0) @stay let Apple__0;
            |        Apple__0 = type (Apple__0);
            |        @typeDecl(Banana__0) @stay let Banana__0;
            |        Banana__0 = type (Banana__0);
            |        @typeDecl(Cherry__0) @stay let Cherry__0;
            |        Cherry__0 = type (Cherry__0);
            |        @typeDecl(Durian__0) @stay let Durian__0;
            |        Durian__0 = type (Durian__0);
            |        class(\word, \Apple, \concrete, true, @typeDefined(Apple__0) fn {
            |            do {};
            |            do {}
            |        });
            |        Banana__0 extends AnyValue;
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(Banana__0) let constructor__1;
            |        constructor__1 = (@stay fn constructor(@impliedThis(Banana__0) this__1: Banana__0) /* return__1 */: Void {});
            |        class(\word, \Banana, \concrete, true, @typeDefined(Banana__0) fn {
            |            do {};
            |            do {}
            |        });
            |        Cherry__0 extends AnyValue;
            |        @typePlaceholder(Cherry__0) let typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        interface(\word, \Cherry, \concrete, false, @typeDefined(Cherry__0) fn {
            |            do {}
            |        });
            |        Durian__0 extends Apple__0;
            |        Durian__0 extends([Banana__0, Cherry__0]);
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(Durian__0) let constructor__2;
            |        constructor__2 = (@stay fn constructor(@impliedThis(Durian__0) this__2: Durian__0) /* return__2 */: Void {});
            |        class(\word, \Durian, \concrete, true, @typeDefined(Durian__0) fn {
            |            do {};
            |            do {};
            |            do {}
            |        });
            |        type (Durian__0)
            |
            |        ```
            |  },
            |  errors: [
            |    "Cannot extend concrete type(s) Apple!",
            |    "Cannot extend concrete type(s) Banana!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun missingVisibilityOnClassMembers() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |class C(p: Int) {
            |  q: Int = p + 1;
            |  private r: Int = p - 1;
            |  f(): Int { r }
            |}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      C__0 extends AnyValue;
            |      @constructorProperty @stay @fromType(C__0) let p__0: Int32;
            |      @stay @fromType(C__0) let q__0: Int32;
            |      @visibility(\private) @stay @fromType(C__0) let r__0: Int32;
            |      @fn @stay @fromType(C__0) let f__0;
            |      f__0 = fn f(@impliedThis(C__0) this__0: C__0) /* return__0 */: Int32 {
            |        fn__0: do {
            |          getp(r__0, this__0)
            |        }
            |      };
            |      @fn @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |      constructor__0 = fn constructor(@impliedThis(C__0) this__1: C__0, p__1 /* aka p */: Int32) /* return__1 */: Void {
            |        do {
            |          let t#0;
            |          setp(p__0, this__1, t#0 = p__1);
            |          t#0
            |        };
            |        do {
            |          let t#1;
            |          setp(q__0, this__1, t#1 = p__1 + 1);
            |          t#1
            |        };
            |        do {
            |          let t#2;
            |          setp(r__0, this__1, t#2 = p__1 - 1);
            |          t#2
            |        };
            |      };
            |      @fn @visibility(\public) @stay @fromType(C__0) let getp__0;
            |      getp__0 = fn (@impliedThis(C__0) this__2: C__0) /* return__2 */: Int32 {
            |        return__2 = getp(p__0, this__2)
            |      };
            |      @fn @visibility(\public) @stay @fromType(C__0) let getq__0;
            |      getq__0 = fn (@impliedThis(C__0) this__3: C__0) /* return__3 */: Int32 {
            |        return__3 = getp(q__0, this__3)
            |      };
            |      @typeDecl(C__0) @stay let C__0;
            |      C__0 = type (C__0);
            |      class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      type (C__0)
            |
            |      ```
            |  },
            |  errors: [
            |    "Members of class C__0 require explicit visibility: [.p, .q, .f(...)]!"
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun regexLiteral() = assertModuleAtStage(
        // These changes currently are applied in SyntaxMacroStage, but it's
        // easier to see the formatting later.
        stage = Stage.Define,
        // Some tests below:
        // - Interpolated string value next to another interpolation. Also test a disappearing empty hole.
        // - Simple interpolated string value since we can't evaluate regex objects at compile time yet.
        input = $$"""
            |let r1 = /a.b*/;
            |let r2 = /a.${b}*/;
            |let r3 = /a.b*/g;
            |let b = r3;
            |let r4 = rgx"a.${b}*";
            |let r5 = rgx"a${"."}${b}*${}?";
            |let r6 = rgx"a${"."}*";
            |let r7 = new Sequence([
            |  new CodePoints("a"),
            |  Dot,
            |  new Repeat(new CodePoints("b"), 0, null),
            |]).compiled();
            |let s = "[a]";
            |let r8 = rgx".${s}.";
        """.trimMargin(),
        want = """
            |{
            |  parse: {
            |    body: ```
            |        let ${
            listOf(
                """r1 = rgx(list("a.b*"), list())""",
                """r2 = rgx(list("a.\u{24}{b}*"), list())""",
                // We don't actually support the following flag syntax at the moment.
                // That's one of the syntax error messages.
                """r3 = rgx(list("(?/g)a.b*"), list())""",
                """b = r3""",
                // And we have a brief interpolation representation from Grammar that's easyish to build.
                // It gets changed later.
                """r4 = rgx(interpolate("a.", \interpolate, b, "*"))""",
                """r5 = rgx(interpolate("a", \interpolate, cat("."), \interpolate, b, "*?"))""",
                """r6 = rgx(interpolate("a", \interpolate, cat("."), "*"))""",
                """r7 = new Sequence(list(new CodePoints(cat("a")), Dot, new Repeat(new CodePoints(cat("b")), 0, null))).compiled()""",
                """s = cat("[a]")""",
                """r8 = rgx(interpolate(".", \interpolate, s, "."))""",
            ).joinToString(", ")
        };
            |
            |        ```
            |  },
            |  disAmbiguate: {
            |    body: ```
            |        @stay @imported(\(`std//regex/`.Sequence)) let Sequence__0 = `std//regex/`.Sequence, ${
            ""
        }@imported(\(`std//regex/`.CodePoints)) CodePoints__0 = `std//regex/`.CodePoints, ${
            ""
        }@imported(\(`std//regex/`.Dot)) Dot__0 = `std//regex/`.Dot, ${
            ""
        }@imported(\(`std//regex/`.Repeat)) Repeat__0 = `std//regex/`.Repeat, ${
            ""
        }@imported(\(`std//regex/`.End)) End__0 = `std//regex/`.End, ${
            listOf(
                // r1 = rgx(list("a.b*"), list())
                """r1 = new Sequence__0(list(new CodePoints__0("a"), Dot__0, new Repeat__0(new CodePoints__0("b"), 0, null, false))).compiled()""",
                // r2 = rgx(list("a.\u{24}{b}*"), list())
                """r2 = new Sequence__0(list(new CodePoints__0("a"), Dot__0, End__0, new CodePoints__0("{b"), new Repeat__0(new CodePoints__0("}"), 0, null, false))).compiled()""",
                """r3 = rgx(list("(?/g)a.b*"), list())""",
                """b = r3""",
                // Here, r4 and r5 interpolate regex objects, but we don't support those yet.
                // These are the other two syntax errors.
                """r4 = rgx(list("a.", "*"), list(b))""",
                """r5 = rgx(list("a", "", "*?"), list(".", b))""",
                // But we do support interpolated string values already, so this one is ok.
                // TODO Wrap stable string values in `new CodePoints` calls if we want to support runtime building.
                // r6 = rgx(list("a", "*"), list("."))
                """r6 = new Sequence__0(list(new CodePoints__0("a"), new Repeat__0(new CodePoints__0("."), 0, null, false))).compiled()""",
                // This one uses Sequence instead of Sequence__0 since it was hand-coded and
                // remains unaffected by the auto-import used above.
                """r7 = new Sequence(list(new CodePoints("a"), Dot, new Repeat(new CodePoints("b"), 0, null))).compiled()""",
                """s = "[a]"""",
                """r8 = rgx(list(".", "."), list(s))""",
            ).joinToString(", ")
        };
            |
            |        ```
            |  },
            |  define: {
            |    body: ```
            |## Here are the auto-imports
            |        @stay @imported(\(`std//regex/`.Sequence)) let Sequence__0;
            |        Sequence__0 = type (Sequence);
            |        @imported(\(`std//regex/`.CodePoints)) let CodePoints__0;
            |        CodePoints__0 = type (CodePoints);
            |        @imported(\(`std//regex/`.Dot)) let Dot__0;
            |        Dot__0 = `std//regex/`.Dot;
            |        @imported(\(`std//regex/`.Repeat)) let Repeat__0;
            |        Repeat__0 = type (Repeat);
            |        @imported(\(`std//regex/`.End)) let End__0;
            |        End__0 = `std//regex/`.End;
            |        let r1__0;
            |## Types have been inlined into `new` operators
            |        r1__0 = do_bind_compiled(new Sequence(list(new CodePoints("a"), Dot__0, new Repeat(new CodePoints("b"), 0, null, false))))();
            |        let r2__0;
            |        r2__0 = do_bind_compiled(new Sequence(list(new CodePoints("a"), Dot__0, End__0, new CodePoints("{b"), new Repeat(new CodePoints("}"), 0, null, false))))();
            |        let r3__0;
            |## (/g) unrecognized in rgx(list("(?/g)a.b*"), list());
            |        r3__0 = error (UnrecognizedToken);
            |        let b__0;
            |        b__0 = r3__0;
            |        let r4__0;
            |## interpolation of b__0 not supported yet in r4 or r5
            |        r4__0 = error (UnrecognizedToken);
            |        let r5__0;
            |        r5__0 = error (UnrecognizedToken);
            |        let r6__0;
            |        r6__0 = do_bind_compiled(new Sequence(list(new CodePoints("a"), new Repeat(new CodePoints("."), 0, null, false))))();
            |        let r7__0;
            |        r7__0 = do_bind_compiled(new Sequence(list(new CodePoints("a"), Dot, new Repeat(new CodePoints("b"), 0, null))))();
            |        let s__0;
            |        s__0 = "[a]";
            |        let r8__0;
            |        r8__0 = do_bind_compiled(new Sequence(list(Dot__0, new CodePoints("[a]"), Dot__0)))();
            |
            |        ```
            |  },
            |  errors: [
            |    "Syntax error!",
            |    "Syntax error!",
            |    "Syntax error!",
            |  ],
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun fullyQualifiedNamesAllocated() = assertModuleAtStage(
        stage = Stage.Define,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(
            showTypeMemberMetadata = true,
            showQNames = true,
        ),
        input = """
            |let x = 1;
            |let x = 2;
            |export let e = x;
            |let f<F>(x: Int, y: F): Int {
            |  let local = x;
            |  let helper(z: Int): Int { local + z }
            |  helper(1)
            |}
            |interface I<T> {
            |  public x: T;
            |  public get y(): Int;
            |  public set y(newY: Int): Void;
            |  public method(): Void;
            |  public static staticMethod<T>(i: I<T>): Void { }
            |}
            |
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      @fn @QName("test-code.f()") let f__0;
            |      @typeDecl(I__0<T__0>) @stay @QName("test-code.type I") let I__0;
            |      I__0 = type (I__0);
            |      @QName("test-code.x#0") let x__0;
            |      x__0 = 1;
            |      @QName("test-code.x#1") let x__1;
            |      x__1 = 2;
            |      @QName("test-code.e") let `test//`.e;
            |      `test//`.e = 2;
            |      @typeFormal(\F) @typeDecl(F__0) @QName("test-code.f().<F>") let F__0;
            |      F__0 = type (F__0);
            |      f__0 = fn f<F__0 extends AnyValue>(@QName("test-code.f().(x)") x__2 /* aka x */: Int32, @QName("test-code.f().(y)") y__0 /* aka y */: F__0) /* return__0 */: Int32 {
            |        fn__0: do {
            |          @fn @QName("test-code.f().helper()") let helper__0, @QName("test-code.f().local=") local__0;
            |          local__0 = x__2;
            |          helper__0 = fn helper(@QName("test-code.f().helper().(z)") z__0 /* aka z */: Int32) /* return__1 */: Int32 {
            |            fn__1: do {
            |              local__0 + z__0
            |            }
            |          };
            |          helper__0(1)
            |        }
            |      };
            |      @typeFormal(\T) @memberTypeFormal(\T) @typeDefined(T__0) @QName("test-code.type I.<T>") @fromType(I__0<T__0>) let T__0;
            |      T__0 = type (T__0);
            |      I__0<T__0> extends AnyValue;
            |      @property(\x) @visibility(\public) @QName("test-code.type I.x") @stay @fromType(I__0<T__0>) let x__3: T__0;
            |      @property(\y) @visibility(\public) @QName("test-code.type I.y") @stay @fromType(I__0<T__0>) let y__1;
            |      @method(\y) @getter @visibility(\public) @fn @QName("test-code.type I.get y()") @stay @fromType(I__0<T__0>) let nym`get.y__2`;
            |      nym`get.y__2` = fn nym`get.y`(@impliedThis(I__0<T__0>) @QName("test-code.type I.get y().(this)") this__0: I__0<T__0>) /* return__2 */: Int32 {
            |        fn__2: do {
            |          pureVirtual()
            |        }
            |      };
            |      @method(\y) @setter @visibility(\public) @fn @QName("test-code.type I.set y()") @stay @fromType(I__0<T__0>) let nym`set.y__3`;
            |      nym`set.y__3` = fn nym`set.y`(@impliedThis(I__0<T__0>) @QName("test-code.type I.set y().(this)") this__1: I__0<T__0>, @QName("test-code.type I.set y().(newY)") newY__0 /* aka newY */: Int32) /* return__3 */: Void {
            |        fn__3: do {
            |          pureVirtual()
            |        }
            |      };
            |      @method(\method) @visibility(\public) @fn @QName("test-code.type I.method()") @stay @fromType(I__0<T__0>) let method__0;
            |      method__0 = fn method(@impliedThis(I__0<T__0>) @QName("test-code.type I.method().(this)") this__2: I__0<T__0>) /* return__4 */: Void {
            |        fn__4: do {
            |          pureVirtual()
            |        }
            |      };
            |      @staticProperty(\staticMethod) @fn @static @visibility(\public) @QName("test-code.type I.staticMethod()") @stay @fromType(I__0<T__0>) let staticMethod__0;
            |      @typeFormal(\T) @typeDecl(T__1) @QName("test-code.type I.staticMethod().<T>") let T__1;
            |      T__1 = type (T__1);
            |      staticMethod__0 = (@stay fn staticMethod<T__1 extends AnyValue>(@QName("test-code.type I.staticMethod().(i)") i__0 /* aka i */: I__0<T__1>) /* return__5 */: Void {
            |          fn__5: do {}
            |      });
            |      void;
            |      interface(\word, \I, \concrete, false, @typeDefined(I__0<T__0>) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      type (I__0)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun sealedTypesChecked() {
        val logSink = ListBackedLogSink()
        val (modules, consoleOutput) = withCapturingConsole { console ->
            val moduleAdvancer = ModuleAdvancer(logSink)
            moduleAdvancer.configureLibrary(
                DashedIdentifier("test-library"), dirPath("test-library"),
            )
            val moduleA = moduleAdvancer.createModule(
                ModuleName(
                    dirPath("test-library", "a"),
                    libraryRootSegmentCount = 1,
                    isPreface = false,
                ),
                console,
            )
            val moduleB = moduleAdvancer.createModule(
                ModuleName(
                    dirPath("test-library", "b"),
                    libraryRootSegmentCount = 1,
                    isPreface = false,
                ),
                console,
            )
            moduleA.deliverContent(
                ModuleSource(
                    filePath = filePath("test-library", "a", "a.temper"),
                    fetchedContent = """
                        |export sealed interface SI {}
                        |
                        |export class A extends SI {}
                        |export class B extends SI {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            moduleB.deliverContent(
                ModuleSource(
                    filePath = filePath("test-library", "b", "b.temper"),
                    fetchedContent = """
                        |let { SI } = import("../a/");
                        |
                        |export class C extends SI {}
                        |
                        |sealed class D {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            moduleAdvancer.advanceModules()
            moduleAdvancer.getAllModules()
        }
        assertStructure(
            """
                |{
                |  // We have a warning about the invalid sealed extension
                |  consoleOutput: ```
                |      3: export class C extends SI {}
                |                       ┗━━━━━━━━━━┛
                |      [test-library/b/b.temper:3+14-26]@D: Cannot extend sealed type SI from test-library/a/a.temper:1+7-13. C is not declared in the same module.
                |      1: export sealed interface SI {}
                |                ┗━━━━┛
                |      5: sealed class D {}
                |         ┗━━━━┛
                |      [test-library/b/b.temper:5+0-6]@D: Only interfaces can be sealed
                |      ```,
                |  "test-library//a/": [
                |    {
                |      name: "test-library//a/.SI",
                |      abstract: true,
                |      supers: ["AnyValue__0"],
                |
                |      // the sealed type list include the ones that pass the checker
                |      sealedSubTypes: [
                |        "test-library//a/.A",
                |        "test-library//a/.B"
                |      ],
                |      metadata: {
                |        sealedType: ["void: Void"],
                |      }
                |    },
                |    {
                |      name: "test-library//a/.A",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__4", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |    {
                |      name: "test-library//a/.B",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__5", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |  ],
                |  "test-library//b/": [
                |    {
                |      name: "test-library//b/.C",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__6", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |    {
                |      name: "D__0",
                |      abstract: false,
                |      supers: ["AnyValue__0"],
                |      methods: [
                |        { name: "constructor__7", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |      metadata: {
                |        sealedType: ["void: Void"],
                |      },
                |    },
                |  ],
                |}
            """.trimMargin(),
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("consoleOutput") {
                        value(consoleOutput.trimEnd())
                    }
                    modules.forEach {
                        key("${it.loc}") {
                            value(it.declaredTypeShapes)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun sealedSubtypesRejectNewTypeParams() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |sealed interface Something<T> {}
            |// Sealed subtypes can't introduce type params.
            |class Subversive<T, U> extends Something<T> {}
            |// But we can (must?) keep type params from parent. And check with a changed name, for bonus fun.
            |interface Simple<V> extends Something<V> {}
            |// And types further down the line can introduce new type params, since we can't cast to them anyway.
            |class Satisfying<T, U> extends Simple<T> {}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      @typeDecl(Something__0<T__0>) @stay @sealedType let Something__0;
            |      Something__0 = type (Something__0);
            |      @typeDecl(Subversive__0<T__1, U__0>) @stay let Subversive__0;
            |      Subversive__0 = type (Subversive__0);
            |      @typeDecl(Simple__0<V__0>) @stay let Simple__0;
            |      Simple__0 = type (Simple__0);
            |      @typeDecl(Satisfying__0<T__2, U__1>) @stay let Satisfying__0;
            |      Satisfying__0 = type (Satisfying__0);
            |      do {};
            |      @typeFormal(\T) @typeDefined(T__0) @fromType(Something__0<T__0>) let T__0;
            |      T__0 = type (T__0);
            |      Something__0<T__0> extends AnyValue;
            |      interface(\word, \Something, \concrete, false, @typeDefined(Something__0<T__0>) fn {
            |          do {};
            |          do {}
            |      });
            |      void;
            |      @typeFormal(\T) @typeDefined(T__1) @fromType(Subversive__0<T__1, U__0>) let T__1;
            |      T__1 = type (T__1);
            |      @typeFormal(\U) @typeDefined(U__0) @fromType(Subversive__0<T__1, U__0>) let U__0;
            |      U__0 = type (U__0);
            |      Subversive__0<T__1, U__0> extends Something__0<T__1>;
            |      @fn @visibility(\public) @stay @fromType(Subversive__0<T__1, U__0>) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(Subversive__0<T__1, U__0>) this__0: Subversive__0<T__1, U__0>) /* return__0 */: Void {});
            |      class(\word, \Subversive, \concrete, true, @typeDefined(Subversive__0<T__1, U__0>) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      void;
            |      @typeFormal(\V) @typeDefined(V__0) @fromType(Simple__0<V__0>) let V__0;
            |      V__0 = type (V__0);
            |      Simple__0<V__0> extends Something__0<V__0>;
            |      interface(\word, \Simple, \concrete, false, @typeDefined(Simple__0<V__0>) fn {
            |          do {};
            |          do {}
            |      });
            |      void;
            |      @typeFormal(\T) @typeDefined(T__2) @fromType(Satisfying__0<T__2, U__1>) let T__2;
            |      T__2 = type (T__2);
            |      @typeFormal(\U) @typeDefined(U__1) @fromType(Satisfying__0<T__2, U__1>) let U__1;
            |      U__1 = type (U__1);
            |      Satisfying__0<T__2, U__1> extends Simple__0<T__2>;
            |      @fn @visibility(\public) @stay @fromType(Satisfying__0<T__2, U__1>) let constructor__1;
            |      constructor__1 = (@stay fn constructor(@impliedThis(Satisfying__0<T__2, U__1>) this__1: Satisfying__0<T__2, U__1>) /* return__1 */: Void {});
            |      class(\word, \Satisfying, \concrete, true, @typeDefined(Satisfying__0<T__2, U__1>) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      type (Satisfying__0)
            |
            |      ```
            |  },
            |  errors: [
            |    "Cannot introduce type parameters in sealed subtype Subversive__0!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun resolutionsStoredWithPostponedCaseCases() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let y = 123;
            |when (x) {
            |  case f(let y) -> handleIt();
            |  else -> fallback();
            |}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        let y__0;
            |        y__0 = 123;
            |        do {
            |          if(postponedCase(([\f, "(", \let, \y, ")"]), x, \y, y__0), fn {
            |              handleIt()
            |            }, \else, fn (f#0) {
            |              f#0(fn {
            |                  fallback()
            |              })
            |          })
            |        }
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonInteropMixedIn() = assertModuleAtStage(
        stage = Stage.Define,
        input = $$"""
            |@json class Point(
            |  public let x: Int,
            |  public let y: Int,
            |) {
            |  public toString(): String { "(${x}, ${y})" }
            |}
        """.trimMargin(),
        // ## lines below are stripped, explanatory comments.
        want = """
            |{
            |  define: {
            |    types: {
            |      "AnyValue": "__DO_NOT_CARE__",
            |      "Int32": "__DO_NOT_CARE__",
            |      "InterchangeContext": "__DO_NOT_CARE__",
            |      "JsonAdapter": "__DO_NOT_CARE__",
            |      "JsonNumeric": "__DO_NOT_CARE__",
            |      "JsonObject": "__DO_NOT_CARE__",
            |      "JsonProducer": "__DO_NOT_CARE__",
            |      "JsonSyntaxTree": "__DO_NOT_CARE__",
            |      "Point": {
            |        supers: [
            |          "AnyValue__0",
            |        ],
            |        methods: [
            |          {
            |            name: "getx__0",
            |            symbol: "x",
            |            visibility: "public",
            |            kind: "Getter",
            |            open: false,
            |          },
            |          {
            |            name: "gety__0",
            |            symbol: "y",
            |            visibility: "public",
            |            kind: "Getter",
            |            open: false,
            |          },
            |          {
            |            name: "toString__0",
            |            visibility: "public",
            |            open: false,
            |          },
            |          {
            |            name: "constructor__1",
            |            visibility: "public",
            |            open: false,
            |            kind: "Constructor",
            |          },
            |          {
            |            name: "encodeToJson__1",
            |            visibility: "public",
            |            open: false,
            |          },
            |        ],
            |        properties: [
            |          {
            |            name: "x__0",
            |            visibility: "public",
            |            abstract: false,
            |            getter: "getx__0",
            |          },
            |          {
            |            name: "y__0",
            |            visibility: "public",
            |            abstract: false,
            |            getter: "gety__0",
            |          },
            |        ],
            |        staticProperties: [
            |          {
            |            name: "decodeFromJson__1",
            |            visibility: "public",
            |          },
            |          {
            |            name: "jsonAdapter__0",
            |            visibility: "public",
            |          },
            |        ],
            |        metadata: {
            |          "json": ["void: Void"],
            |          "QName": ["\"test-code.type Point\": String"],
            |        }
            |      },
            |      "PointJsonAdapter": {
            |        supers: [
            |          [
            |            "Nominal",
            |            "std//json/.JsonAdapter",
            |            "Point__0",
            |          ],
            |        ],
            |        methods: [
            |          {
            |            name: "encodeToJson__0",
            |            visibility: "public",
            |            open: false,
            |          },
            |          {
            |            name: "decodeFromJson__0",
            |            visibility: "public",
            |            open: false,
            |          },
            |          {
            |            name: "constructor__0",
            |            visibility: "public",
            |            open: false,
            |            kind: "Constructor",
            |          },
            |        ],
            |      },
            |      "String": "__DO_NOT_CARE__",
            |      "Void": "__DO_NOT_CARE__",
            |    },
            |    body: ```
            |## Here are members for the generated JSON adapter class
            |      PointJsonAdapter__0 extends JsonAdapter<Point__0>;
            |      @visibility(\public) @fn @stay @fromType(PointJsonAdapter__0) let encodeToJson__0;
            |      encodeToJson__0 = fn (@impliedThis(PointJsonAdapter__0) this__0: PointJsonAdapter__0, x__1: Point__0, p__0: JsonProducer) /* return__0 */: Void {
            |        do_bind_encodeToJson(x__1)(p__0)
            |      };
            |      @visibility(\public) @fn @stay @fromType(PointJsonAdapter__0) let decodeFromJson__0;
            |      decodeFromJson__0 = fn (@impliedThis(PointJsonAdapter__0) this__1: PointJsonAdapter__0, t__0: JsonSyntaxTree, ic__0: InterchangeContext) /* return__1 */: (Point__0 | Bubble) {
            |        getStatic(Point__0, \decodeFromJson)(t__0, ic__0)
            |      };
            |## It's got an implied constructor even though that wasn't mentioned in the JsonInteropPass
            |      @fn @visibility(\public) @stay @fromType(PointJsonAdapter__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(PointJsonAdapter__0) this__2: PointJsonAdapter__0) /* return__2 */: Void {});
            |      @typeDecl(PointJsonAdapter__0) @stay let PointJsonAdapter__0;
            |      PointJsonAdapter__0 = type (PointJsonAdapter__0);
            |## Here's the declaration for the non-generated point type
            |      @typeDecl(Point__0) @stay @json let Point__0;
            |      Point__0 = type (Point__0);
            |      class (\word, \PointJsonAdapter, \concrete, true, @typeDefined(PointJsonAdapter__0) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |      do {};
            |## Here's the explicitly declared point class type variable
            |      Point__0 extends AnyValue;
            |      @constructorProperty @visibility(\public) @stay @fromType(Point__0) let x__0: Int32;
            |      @constructorProperty @visibility(\public) @stay @fromType(Point__0) let y__0: Int32;
            |      @visibility(\public) @fn @stay @fromType(Point__0) let toString__0;
            |      toString__0 = fn toString(@impliedThis(Point__0) this__3: Point__0) /* return__3 */: String {
            |        fn__0: do {
            |          cat("(", getp(x__0, this__3), ", ", getp(y__0, this__3), ")")
            |        }
            |      };
            |      @fn @visibility(\public) @stay @fromType(Point__0) let constructor__1;
            |      constructor__1 = fn constructor(@impliedThis(Point__0) this__4: Point__0, x__2 /* aka x */: Int32, y__1 /* aka y */: Int32) /* return__4 */: Void {
            |        do {
            |          let t#0;
            |          setp(x__0, this__4, t#0 = x__2);
            |          t#0
            |        };
            |        do {
            |          let t#1;
            |          setp(y__0, this__4, t#1 = y__1);
            |          t#1
            |        };
            |      };
            |      @fn @visibility(\public) @stay @fromType(Point__0) let getx__0;
            |      getx__0 = fn (@impliedThis(Point__0) this__5: Point__0) /* return__5 */: Int32 {
            |        return__5 = getp(x__0, this__5)
            |      };
            |      @fn @visibility(\public) @stay @fromType(Point__0) let gety__0;
            |      gety__0 = fn (@impliedThis(Point__0) this__6: Point__0) /* return__6 */: Int32 {
            |        return__6 = getp(y__0, this__6)
            |      };
            |## Here is the encodeToJson method added to point.
            |      @visibility(\public) @fn @stay @fromType(Point__0) let encodeToJson__1;
            |      encodeToJson__1 = fn (@impliedThis(Point__0) this__7: Point__0, p__1: JsonProducer) /* return__7 */: Void {
            |        do_bind_startObject(p__1)();
            |        do_bind_objectKey(p__1)("x");
            |## `this` in the generated expression `this.x` got rewritten to `this__7`, after
            |## the regular type processing pass adds that implied parameter.
            |        do_bind_int32Value(p__1)(getp(x__0, this__7));
            |        do_bind_objectKey(p__1)("y");
            |        do_bind_int32Value(p__1)(getp(y__0, this__7));
            |        do_bind_endObject(p__1)();
            |      };
            |      @static @visibility(\public) @fn @stay @fromType(Point__0) let decodeFromJson__1;
            |      decodeFromJson__1 = fn (t__1: JsonSyntaxTree, ic__1: InterchangeContext) /* return__8 */: (Point__0 | Bubble) {
            |        let obj__0;
            |        obj__0 = as(t__1, JsonObject);
            |        let x__3: Int32, y__2: Int32;
            |        x__3 = do_bind_asInt32(as(do_bind_propertyValueOrBubble(obj__0)("x"), JsonNumeric))();
            |        y__2 = do_bind_asInt32(as(do_bind_propertyValueOrBubble(obj__0)("y"), JsonNumeric))();
            |        new Point__0(x__3, y__2)
            |      };
            |      @static @visibility(\public) @fn @stay @fromType(Point__0) let jsonAdapter__0;
            |      jsonAdapter__0 = (@stay fn /* return__9 */: (JsonAdapter<Point__0>) {
            |          new PointJsonAdapter__0()
            |      });
            |      class(\word, \Point, \concrete, true, @typeDefined(Point__0) fn {
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {};
            |          do {}
            |      });
            |## The terminal expression is not affected.
            |      type (Point__0)
            |
            |      ```
            |  },
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun nullableTypesResolved() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let intOrNull: Int?;
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: {
            |      tree: [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "intOrNull__0" ],
            |              [ "Value", "\\type: Symbol" ],
            |              [ "Value", "Int32?: Type" ],
            |              [ "Value", "\\QName: Symbol" ],
            |              [ "Value", "\"test-code.intOrNull\": String" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Value", "void: Void" ],
            |        ]
            |      ],
            |      code: ```
            |        let intOrNull__0: Int32?;
            |
            |        ```
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun propertyBagsDesugarToPositionalParameters() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let { Point } = import("./point");
            |export let p = { x: 1, y: 2 };
            |export let q = { y: p.y, x: p.x };
            |
            |$TEST_INPUT_MODULE_BREAK ./point/point.temper
            |export class Point(public x: Int, public y: Int) {}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @stay @imported(\(`test//point/`.Point)) let Point__0;
            |        Point__0 = type (Point);
            |        let `test//`.p;
            |## Here's a reworked property bag that we don't muck with, much.
            |        `test//`.p = new Point(1, 2);
            |        let `test//`.q;
            |## This one becomes a do-block because we need to preserve OoO.
            |        `test//`.q = do {
            |          let y#0;
            |          y#0 = do_get_y(`test//`.p);
            |          let x#0;
            |          x#0 = do_get_x(`test//`.p);
            |          new Point(x#0, y#0)
            |        };
            |
            |        ````
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun propertyBagsDesugaringWithOptionalParameters() = assertModuleAtStage(
        stage = Stage.Define,
        input = """
            |let { C } = import("./c");
            |export let c = { x: 1, z: 2 }
            |
            |$TEST_INPUT_MODULE_BREAK ./c/c.temper
            |export class C(public x: Int, public y: Int = 0, public z: Int = 0) {}
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |        @stay @imported(\(`test//c/`.C)) let C__0;
            |        C__0 = type (C);
            |        let `test//`.c;
            |## Here's a reworked property bag that we don't muck with, much.
            |        `test//`.c = new C(1, null, 2);
            |
            |        ````
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )
}
