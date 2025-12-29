@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.common.testCodeLocation
import lang.temper.interp.MetadataDecorator
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Test

class DisAmbiguateStageTest {
    @Test
    fun unknownFunctionWithFormalGetsError() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        //             ↓↓↓     ↓↓↓↓↓      ↓↓↓
        input = "foo f(a = 1, b: Int) { g(a = 1, b) }",
        //       0123456789012345678901234567890123456
        //                 1         2         3
        want = """
        {
          "disAmbiguate": {
            body:
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "foo" ],
                        [ "Value", [ "word", "Symbol" ] ],
                        [ "LeftName", "f" ],
                        // Named actuals forbidden.
                        [ "Call", [ [ "Value", ["error", "Function"] ] ] ],
                        // Formal stuff discarded.
                        [ "RightName", "b" ],

                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "g" ],
                                    [ "Call", [ [ "Value", ["error", "Function"] ] ] ],
                                    [ "RightName", "b" ],
                                  ]
                                ]
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                  ]
                ],
          },
          errors: [
            {
              template: "NamedActual",
              values: [],
              left: 25,
              right: 28
            },
            {
              template: "NamedActual",
              values: [],
              left: 6,
              right: 9
            },
            {
              template: "MalformedActual",
              values: [],
              left: 14,
              right: 19
            },
          ]
        }
        """,
    )

    @Test
    fun formalsFormalizedAndActualsActualized() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "let f(a = 1, b: Int) { g(a = 1, b) }",
        //       0123456789012345678901234567890123456
        //                 1         2         3
        want = """
        {
          disAmbiguate: {
            body:
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "let" ],
                        [ "Value", [ "word", "Symbol" ] ],
                        [ "LeftName", "f" ],
                        // First formalized formal
                        [ "Decl", [
                            [ "LeftName", "a" ],
                            [ "Value", [ "default", "Symbol" ] ],
                            [ "Value", [ 1, "Int32" ] ],
                            [ "Value", [ "word", "Symbol" ] ],
                            [ "Value", [ "a", "Symbol" ] ]
                          ]
                        ],
                        [ "Decl", [
                            [ "LeftName", "b" ],
                            [ "Value", [ "type", "Symbol" ] ],
                            [ "RightName", "Int" ],
                            [ "Value", [ "word", "Symbol" ] ],
                            [ "Value", [ "b", "Symbol" ] ]
                          ]
                        ],
                        [ "Fun", [
                            [ "Block", [
                                [ "Call", [
                                    [ "RightName", "g" ],
                                    [ "Call", [ [ "Value", ["error", "Function"] ] ] ],
                                    [ "RightName", "b" ],
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
          },
          errors: [
            "${MessageTemplate.NamedActual.formatString}!"
          ]
        }
        """,
    )

    @Test
    fun formalsAndActualsWithEmbeddedComments() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "let f(/** docs */ a: Int) { g(/** here too? */ 1) }",
        want = """
            |{
            |  disAmbiguate: {
            |    body:
            |        [ "Block", [
            |            [ "Call", [
            |                [ "RightName", "let" ],
            |                [ "Value", [ "word", "Symbol" ] ],
            |                [ "LeftName", "f" ],
            |
            |                [ "Decl", [
            |                    [ "LeftName", "a" ],
            |                    [ "Value", [ "type", "Symbol" ] ],
            |                    [ "RightName", "Int" ],
            |                    [ "Value", [ "word", "Symbol" ] ],
            |                    [ "Value", [ "a", "Symbol" ] ],
            |                    [ "Value", [ "docString", "Symbol" ] ],
            |                    [ "Value", [ "[\"docs\", \"docs\", \"test/test.temper\"]", "List" ] ],
            |                  ]
            |                ],
            |                [ "Fun", [
            |                    [ "Block", [
            |                        [ "Call", [
            |                            [ "RightName", "g" ],
            |                            [ "Value", [ 1, "Int32" ] ],
            |                          ]
            |                        ]
            |                      ]
            |                    ]
            |                  ]
            |                ]
            |              ]
            |            ]
            |          ]
            |        ]
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun annotatedFormal() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "fn f(@A @B x) {}",
        want = """
        {
          disAmbiguate: {
            body:
                [ "Block", [
                    [ "Call", [
                        [ "RightName", "fn" ],
                        [ "Value", "\\word: Symbol" ],
                        [ "LeftName", "f" ],
                        [ "Call", [
                            [ "RightName", "@A" ],
                            [ "Call", [
                                [ "RightName", "@B" ],
                                [ "Decl", [
                                    [ "LeftName", "x" ],
                                    [ "Value", "\\word: Symbol" ],
                                    [ "Value", "\\x: Symbol" ]
                                  ]
                                ]
                              ]
                            ]
                          ]
                        ],
                        [ "Fun", [ [ "Block", [] ] ] ]
                      ]
                    ]
                  ]
                ],
          }
        }
        """,
    )

    @Test
    fun stagingAnnotation() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "@(A..S) fn (x) {}",
        want = """
        {
          disAmbiguate: {
            body:
                [ "Block", [
                    [ "Call", [
                        [ "Call", [ // @ distributed over ..
                            // Application of this doesn't happen until syntax stage where declaration
                            // macros desugar.
                            [ "RightName", ".." ],
                            [ "RightName", "@A" ],
                            [ "RightName", "@S" ]
                          ]
                        ],
                        [ "Call", [
                            [ "RightName", "fn" ],
                            [ "Decl", [
                                [ "LeftName", "x" ],
                                [ "Value", [ "word", "Symbol" ] ],
                                [ "Value", [ "x", "Symbol" ] ]
                              ]
                            ],
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
          },
          /* TODO: actual application of @A..S to the function.
          syntax: {
            body: [ "Decl", [
                [ "Value", "\\liveness: Symbol" ],
                [ "Value", [ "@(A..S)", "StageRange" ] ],
              ]
            ]
          }
          */
        }
        """,
    )

    @Test
    fun bunchOfStuff() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = $$"""
        a + b * c;

        42;

        let x = 1;

        // What is going on here?
        console.log("foo ${"bar ${"qux ${xyzzy}"}"} baz" );

        // comment
        """.trimIndent(),
        want = """
        {
          disAmbiguate: {
            body:
              ```
              a + b * c;
              42;
              let x = 1;
              REM("What is going on here?", null, false);
              console.log(cat("foo ", cat("bar ", cat("qux ", xyzzy)), " baz"));

              ```
          }
        }
        """,
    )

    @Test
    fun blockFormals() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            f { x: Int, y: Int => x + y }
        """,
        want = """
        {
          disAmbiguate: {
            body: {
              tree:
                  [ "Block", [
                      [ "Call", [
                          [ "RightName", "f" ],
                          [ "Fun", [
                              [ "Decl", [
                                  [ "LeftName", "x" ],
                                  [ "Value", "\\type: Symbol" ],
                                  [ "RightName", "Int" ],
                                  [ "Value", "\\word: Symbol" ],
                                  [ "Value", "\\x: Symbol" ]
                                ]
                              ],
                              [ "Decl", [
                                  [ "LeftName", "y" ],
                                  [ "Value", "\\type: Symbol" ],
                                  [ "RightName", "Int" ],
                                  [ "Value", "\\word: Symbol" ],
                                  [ "Value", "\\y: Symbol" ]
                                ]
                              ],
                              [ "Block", [
                                  [ "Call", [
                                      [ "RightName", "+" ],
                                      [ "RightName", "x" ],
                                      [ "RightName", "y" ]
                                    ]
                                  ]
                                ]
                              ]
                            ]
                          ]
                        ]
                      ]
                    ]
                  ],

              "code":
                ```
                f(fn (x /* aka x */: Int, y /* aka y */: Int) {
                    x + y
                })

                ```
            }
          }
        }
        """,
    )

    @Test
    fun classBodyAmbiguityReduction() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
          class C { // This is a class body
            var decl = 0;
            let cDecl;
            property0;
            public property1: T;
            property2 = initial;
            property3: T = initial;
            method1() { 123 }
            method2(): T { 123 }
            method3(x: U = 123) { 123 }
            let method4(x: V) { 123 }
            get p(@Foo this): T { property1 }
            set p(x) { this.property1 = x }
          }
          do { // This is not a class body, and the parts about properties/methods are ALL LIES!
            var decl = 0;
            let cDecl;
            property0;
            public property1: T;
            property2 = initial;
            property3: T = initial;
            method1() { 123 }
            method2(): T { 123 }
            method3(x: U = 123) { 123 } // Error on line 24
            let method4(x: V) { 123 }
            get p(@Foo this): T { property1 }
            set p(x) { this.property1 = x }
          }
        """.trimIndent(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        {
          disAmbiguate: {
            body:
            ```
            @typeDecl(C__0) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            class(\word, C, \concrete, true, @typeDefined(C__0) fn {
                C__0 extends AnyValue;
                REM("This is a class body", null, false);
                @property(\decl) var decl = 0;
                @property(\cDecl) let cDecl;
                @property(\property0) @maybeVar let property0;
                @property(\property1) @maybeVar @visibility(\public) let property1: T;
                @property(\property2) @maybeVar let property2 = initial;
                @property(\property3) @maybeVar let property3: T = initial;
                @method(\method1) let method1 = fn(\word, method1, @impliedThis(C__0) let this__1: C__0, fn {
                    123
                });
                @method(\method2) let method2 = fn(\word, method2, @impliedThis(C__0) let this__2: C__0, \outType, T, fn {
                    123
                });
                @method(\method3) let method3 = fn(\word, method3, @impliedThis(C__0) let this__3: C__0, @default(123) let x /* aka x */: U, fn {
                    123
                });
                @method(\method4) let method4 = fn(\word, method4, @impliedThis(C__0) let this__4: C__0, let x /* aka x */: V, fn {
                    123
                });
                @method(\p) @getter let nym`get.p` = fn(\word, nym`get.p`, nym`@Foo`(@impliedThis(C__0) let this__5: C__0), \outType, T, fn {
                    property1
                });
                @method(\p) @setter let nym`set.p` = fn(\word, nym`set.p`, @impliedThis(C__0) let this__6: C__0, let x /* aka x */, \outType, type (Void), fn {
                    this(C__0).property1 = x
                });
            });
            do(fn {
                REM("This is not a class body, and the parts about properties/methods are ALL LIES!", null, false);
                var decl = 0;
                let cDecl;
                property0;
                nym`@public`((property1): (T));
                property2 = initial;
                ((property3): (T)) = initial;
                method1(fn {
                    123
                });
                method2(\outType, T, fn {
                    123
                });
                method3(error (), fn {
                    123
                });
                REM("Error on line 24", null, false);
                let(\word, method4, let x /* aka x */: V, fn {
                    123
                });
                get(\word, p, nym`@Foo`(this()), \outType, T, fn {
                    property1
                });
                set(\word, p, x, fn {
                    this().property1 = x
                })
            })

            ```,
            types: {
              AnyValue: {
                abstract: true
              },
              C: {
                word: "C"
              },
              Void: {
                supers: []
              },
            },
          },
          errors: [
            "${MessageTemplate.MalformedActual.formatString}!",
            "${MessageTemplate.NamedActual.formatString}!",
          ]
        }
        """,
    )

    @Test
    fun typeFormalsOnClassDeclaration() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
        class C<T, U extends D, out V, in W> extends A, B {}
        """.trimIndent(),
        want = """
        {
          disAmbiguate: {
            body:
            ```
            @typeDecl(C__0<T__1, U__2, V__3, W__4>) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            class(\word, C, \concrete, true, @typeDefined(C__0<T__1, U__2, V__3, W__4>) fn {
                @typeFormal(\T) @typeDefined(T__1) @resolution(T__1) let T = type (T__1);
                @typeFormal(\U) @typeDefined(U__2) @resolution(U__2) let U = type (U__2);
                @typeFormal(\V) @typeDefined(V__3) @resolution(V__3) @variance(1) let V = type (V__3);
                @typeFormal(\W) @typeDefined(W__4) @resolution(W__4) @variance(-1) let W = type (W__4);
                U extends D;
                C__0<T__1, U__2, V__3, W__4> extends A;
                C__0<T__1, U__2, V__3, W__4> extends B
            });
            C

            ```,
            types: {
              C: {
                word: "C",
                typeParameters: [
                  { name: "T__1" },
                  { name: "U__2" },
                  { name: "V__3" },
                  { name: "W__4" },
                ]
              },
              T: { name: "T__1", word: "T" },
              U: { name: "U__2", word: "U", upperBounds: [] }, // UpperBound D should fill in later
              V: { name: "V__3", word: "V", variance: "Covariant" },
              W: { name: "W__4", word: "W", variance: "Contravariant" },
            }
          }
        }
        """,
    )

    @Test
    fun genericMethodsDisallowedInInterface() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        // Generic instance methods should be reported. Variety here is just to be sure about internal forms.
        // Static methods in interfaces can be generic if they want.
        input = """
            |interface Whatever {
            |  blather<A>(a: A): A;
            |  public let bling<B, C extends Whatever>(b: B, c: C): B { b }
            |  static blot<T>(d: D): D;
            |}
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        @typeDecl(Whatever__0) @hoistLeft(true) @resolution(Whatever__0) @stay let Whatever = type (Whatever__0);
            |        interface(\word, Whatever, \concrete, false, @typeDefined(Whatever__0) fn {
            |            Whatever__0 extends AnyValue;
            |            let blather = fn(\word, blather, \typeFormal, do {
            |                @resolution(A__0) @typeFormal(\A) @typeDecl(A__0) let A = type (A__0);
            |                type (A__0)
            |              }, @impliedThis(Whatever__0) let this__0: Whatever__0, let a /* aka a */: A, \outType, A, fn {
            |                pureVirtual()
            |            });
            |            @visibility(\public) let bling = fn(\word, bling, \typeFormal, do {
            |                @resolution(B__0) @typeFormal(\B) @typeDecl(B__0) let B = type (B__0);
            |                type (B__0)
            |              }, \typeFormal, do {
            |                @resolution(C__0) @typeFormal(\C) @typeDecl(C__0) let C = type (C__0);
            |                C extends Whatever;
            |                type (C__0)
            |              }, @impliedThis(Whatever__0) let this__1: Whatever__0, let b /* aka b */: B, let c /* aka c */: C, \outType, B, fn {
            |                b
            |            });
            |            @fn @static let blot = fn(\word, blot, \typeFormal, do {
            |                @resolution(T__1) @typeFormal(\T) @typeDecl(T__1) let T = type (T__1);
            |                type (T__1)
            |              }, let d /* aka d */: D, \outType, D, fn {
            |                pureVirtual()
            |            });
            |        });
            |        Whatever
            |
            |        ```
            |  },
            |  errors: [
            |    "Illegal type parameter A. Overridable methods don't allow generics!",
            |    "Illegal type parameter B. Overridable methods don't allow generics!",
            |    "Illegal type parameter C. Overridable methods don't allow generics!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun multipleKeywordAnnotationsAllFire() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "public static let x",
        want = """
        {
          disAmbiguate: {
            body:
                [ "Block", [
                    [ "Decl", [
                        [ "LeftName", "x" ],
                        [ "Value", "\\static: Symbol" ],
                        [ "Value", "void: Void" ],
                        [ "Value", "\\visibility: Symbol" ],
                        [ "Value", "\\public: Symbol" ],
                      ]
                    ]
                  ]
                ]
          }
        }
        """,
    )

    @Test
    fun unrecognizedDecorationsPreservedForLater() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "@foo @bar let x",
        want = """
        {
          disAmbiguate: {
            body: "nym`@foo`(nym`@bar`(let x))\n"
          },
        }
        """,
    )

    @Test
    fun decoratedArgument() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "let f(@foo(1) x: T) {}",
        want = """
        {
          disAmbiguate: {
            body: ```
              let(\word, f, nym`@foo`(let x /* aka x */: T, 1), fn {})

              ```
          },
        }
        """,
    )

    @Test
    fun everyTypeButImplicitsHasASuperType() {
        assertModuleAtStage(
            stage = Stage.DisAmbiguate,
            input = "interface I {}",
            want = """
            |{
            |  disAmbiguate: {
            |    body:
            |      ```
            |      @typeDecl(I__0) @hoistLeft(true) @resolution(I__0) @stay let I = type (I__0);
            |      interface(\word, I, \concrete, false, @typeDefined(I__0) fn {
            |          I__0 extends AnyValue
            |      });
            |      I
            |
            |      ```,
            |
            |    types: {
            |      I: { name: "I__0", abstract: true },
            |      AnyValue: { abstract: true },
            |    }
            |  }
            |}
            """.trimMargin(),
        )
    }

    @Test
    fun annotationsOnFormals() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        // annotations on x do not apply to y as would be the case if `@foo var x = 0, y` were
        // to appear as a top-level, not a function formal parameter
        input = "fn (@foo var x = 0, y) {}",
        want = """
        {
          disAmbiguate: {
            body: "fn(nym`@foo`(@default(0) var x /* aka x */), let y /* aka y */, fn {})\n"
          }
        }
        """,
    )

    @Test
    fun genericMethod() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |class C {
            |  public let f<T>(x: T): T { x }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  disAmbiguate: {
            |    body:
            |      ```
            |      @typeDecl(C__0) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            |      class(\word, C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @method(\f) @visibility(\public) let f = fn(\word, f, \typeFormal, do {
            |              @resolution(T__0) @typeFormal(\T) @typeDecl(T__0) let T = type (T__0);
            |              type (T__0)
            |            }, @impliedThis(C__0) let this__0: C__0, let x /* aka x */: T,${
            ""
        } \outType, T, fn {
            |              x
            |          });
            |      });
            |      C
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun typeDecoratorCanAccessTypeAndDeclaration() = assertModuleAtStage(
        stage = Stage.Define,
        want = """
        {
          "disAmbiguate": {
            // At the end of disambiguate, the decorator applies to the declaration
            "body":
              ```
              do {};
              nym`@foo`(@typeDecl(I__0) @hoistLeft(true) @resolution(I__0) @stay let I = type (I__0));
              interface(\word, I, \concrete, false, @typeDefined(I__0) fn {
                  I__0 extends AnyValue
              });
              I

              ```
          },
          "define": {
            // By the end of define, the decorator has successfully applied itself, and added
            // metadata to the type shape.
            "body":
              ```
              @typeDecl(I__0) @stay @TypeDecoratedByFoo let I__0;
              I__0 = type (I__0);
              do {};
              I__0 extends AnyValue;
              @typePlaceholder(I__0) let typePlaceholder#0: Empty;
              typePlaceholder#0 = {class: Empty__0};
              interface(\word, \I, \concrete, false, @typeDefined(I__0) fn {
                  do {}
              });
              type (I__0)

              ```,
            "types": {
              AnyValue: { abstract: true },
              I: {
                name: "I__0",
                word: "I",
                abstract: true,
                metadata: {
                  "TypeDecoratedByFoo": [ "void: Void" ],
                },
                supers: [
                  {
                    module: "implicits",
                    abbrev: "AnyValue__0",
                    uid: 0
                  }
                ]
              },
              Empty: {
                supers: ["AnyValue__0", "Equatable__0"],
                methods: [
                  {
                    name: "constructor__0",
                    visibility: "private",
                    kind: "Constructor",
                    open: false
                  },
                ],
                metadata: {
                  connected: ["\"Empty\": String"],
                }
              },
            }
          }
        }
        """,
    ) { module, _ ->
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation,
                fetchedContent = """@foo interface I {}""",
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("@foo") to Value(
                    MetadataDecorator(symbolKey = Symbol("TypeDecoratedByFoo")) { void },
                ),
            ),
        )
    }

    @Test
    fun enumDesugaring() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
        |enum E { A, B, C }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        |{
        |  disAmbiguate: {
        |    body: ```
        |    @typeDecl(E__0) @hoistLeft(true) @resolution(E__0) @stay let E = type (E__0);
        |    class(\word, E, \concrete, true, @enumType @typeDefined(E__0) fn {
        |        E__0 extends AnyValue;
        |        @constructorProperty @visibility(\public) @property(\ordinal) let ordinal: Int32;
        |        @constructorProperty @visibility(\public) @property(\name) let name: String;
        |        @visibility(\public) @enumMember @staticProperty(\A) @static let A = new E(0, "A");
        |        @visibility(\public) @enumMember @staticProperty(\B) @static let B = new E(1, "B");
        |        @visibility(\public) @enumMember @staticProperty(\C) @static let C = new E(2, "C");
        |    });
        |    E
        |
        |    ```
        |  }
        |}
        """.trimMargin(),
    )

    @Test
    fun squareBracketDesugaring() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |a[i] = b[j];
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        a.set(i, b.get(j));
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun multiDeclDecoratorApplication() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |@foo var x, y;
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        nym`@foo`(var x);
            |        nym`@foo`(var y);
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun multiInit() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "@foo let { a is S, b, c as d is T }: U = f();",
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        nym`@foo`(let t#0: U = f());
            |        nym`@foo`(let a: S = t#0.a);
            |        nym`@foo`(let b = t#0.b);
            |        nym`@foo`(let d: T = t#0.c);
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun multiInitMultiRenameError() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "let { a as b as c as d } = f();",
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        let t#0 = f(), b = t#0.a;
            |
            |        ```
            |  },
            |  errors: [
            |    "${MessageTemplate.MultipleRenames.formatString}!",
            |    "${MessageTemplate.MultipleRenames.formatString}!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun wildcardDestructureError() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "let { ... } = f()",
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        let t#0 = f();
            |
            |        ```
            |  },
            |  errors: [
            |    "${MessageTemplate.WildcardWithoutImport.formatString}!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun multiInitErrorInClass() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = "class Something { let { a, b } = f(); }",
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        @typeDecl(Something__0) @hoistLeft(true) @resolution(Something__0) @stay let Something = type (Something__0);
            |        class(\word, Something, \concrete, true, @typeDefined(Something__0) fn {
            |            Something__0 extends AnyValue;
            |            let t#0 = f();
            |            @property(\a) let a = t#0.a;
            |            @property(\b) let b = t#0.b;
            |        });
            |        Something
            |
            |        ```
            |  },
            |  errors: ["Declaration is malformed!"]
            |}
        """.trimMargin(),
    )

    @Test
    fun commentInDocTypeDefinition() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        genre = Genre.Documentation,
        input = """
            |class C {
            |  // Comment in type definition
            |  public x: Int;
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        @typeDecl(C__0) @hoistLeft(true) @resolution(C__0) @stay let C = type (C__0);
            |        class(\word, C, \concrete, true, @typeDefined(C__0) fn {
            |            C__0 extends AnyValue;
            |            REM("Comment in type definition", null, false);
            |            @property(\x) @maybeVar @visibility(\public) let x: Int;
            |        });
            |        identityForDocGen(C)
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun exportedClassesHaveExportedNames() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |export class C {}
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |        do {};
            |        @typeDecl(C) @hoistLeft(true) @resolution(`test//`.C) @stay let `test//`.C = type (C);
            |        class(\word, C, \concrete, true, @typeDefined(C) fn {
            |            C extends AnyValue
            |        });
            |        C
            |
            |        ```,
            |    types: {
            |      C: {
            |        name: {
            |          type: "ExportedName",
            |          module: "test//",
            |          baseName: "C",
            |        },
            |      },
            |      AnyValue: {
            |        abstract: true,
            |      },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun exportedClassesWithExtraDecoratorsHaveExportedNames() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |              export @bar class     C {}
            |      @foo(1) export      interface D {}
            |      @foo()  export @bar class     E {}
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |          do {};
            |          nym`@export`(nym`@bar`(@typeDecl(C) @hoistLeft(true) @resolution(`test//`.C) @stay let C = type (C)));
            |          class(\word, C, \concrete, true, @typeDefined(C) fn {
            |              C extends AnyValue
            |          });
            |          do {};
            |          nym`@foo`(@typeDecl(D) @hoistLeft(true) @resolution(`test//`.D) @stay let `test//`.D = type (D), 1);
            |          interface(\word, D, \concrete, false, @typeDefined(D) fn {
            |              D extends AnyValue
            |          });
            |          do {};
            |          nym`@foo`(nym`@export`(nym`@bar`(@typeDecl(E) @hoistLeft(true) @resolution(`test//`.E) @stay let E = type (E))));
            |          class(\word, E, \concrete, true, @typeDefined(E) fn {
            |              E extends AnyValue
            |          });
            |          E
            |
            |          ```,
            |    types: {
            |      AnyValue: {
            |        abstract: true,
            |      },
            |      C: {
            |        name: {
            |          type: "ExportedName",
            |          module: "test//",
            |          baseName: "C",
            |        },
            |      },
            |      D: {
            |        name: {
            |          type: "ExportedName",
            |          module: "test//",
            |          baseName: "D",
            |        },
            |        abstract: true,
            |      },
            |      E: {
            |        name: {
            |          type: "ExportedName",
            |          module: "test//",
            |          baseName: "E",
            |        },
            |      },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun classesCanDeclarePropertiesInParenthetical() = assertModuleAtStage(
        stage = Stage.DisAmbiguate,
        input = """
            |class Point(
            |  public let x: Float64,
            |  public let y: Float64,
            |) extends AntValue {
            |  public let distanceFromOrigin: Float64 = (x * x + y * y).sqrt();
            |}
        """.trimMargin(),
        want = """
            |{
            |  disAmbiguate: {
            |    body: ```
            |      @typeDecl(Point__0) @hoistLeft(true) @resolution(Point__0) @stay let Point = type (Point__0);
            |      class(\word, Point, \concrete, true, @typeDefined(Point__0) fn {
            |          Point__0 extends AntValue;
            |          @constructorProperty @maybeVar @visibility(\public) let x /* aka x */: Float64;
            |          @constructorProperty @maybeVar @visibility(\public) let y /* aka y */: Float64;
            |          @visibility(\public) let distanceFromOrigin: Float64 = (x * x + y * y).sqrt();
            |      });
            |      Point
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )
}
