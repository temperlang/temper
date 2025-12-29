package lang.temper.value

import lang.temper.ast.flatten
import lang.temper.astbuild.StoredCommentTokens
import lang.temper.astbuild.buildTree
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.AppendingTextOutput
import lang.temper.common.ListBackedLogSink
import lang.temper.common.NoneShortOrLong
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.toStringViaBuilder
import lang.temper.cst.CstComment
import lang.temper.interp.emptyValue
import lang.temper.lexer.Lexer
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.Symbol
import lang.temper.parser.parse
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.ExternalSet
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import kotlin.test.Test

class PseudoCodeTest {
    private val dumpToConsole = false

    @Test
    fun value() = assertPseudoCode(
        input = "123",
        want = "123\n",
    )

    @Test
    fun prefixOp() = assertPseudoCode(
        input = "-123",
        want = "-123\n",
    )

    @Test
    fun binaryOpWithEmbeddedNewline() = assertPseudoCode(
        input = "x +\n  y",
        want = "x +\n  y\n",
    )

    @Test
    fun needsParens() = assertPseudoCode(
        input = "(1 + 2) * 3",
        want = "(1 + 2) * 3\n",
    )

    @Test
    fun divisionIsWeird() = assertPseudoCode(
        input = "(1 + 2) / 3",
        want = "(1 + 2) / 3\n",
    )

    @Test
    fun funAndCalls() = assertPseudoCode(
        input = """
        do {
          f();
          g();
        }
        """.trimIndent(),
        want = """
        do(fn {
            f();
            g();
        })

        """.trimIndent(),
    )

    @Test
    fun letDecl() = assertPseudoCode(
        input = """
        let x : T =
           42
        """.trimIndent(),
        want = """
        let x: T
          = 42

        """.trimIndent(),
    )

    @Test
    fun strings() = assertPseudoCode(
        input = " \"foo\\n\" ",
        want = "cat(\"foo\", \"\\n\")\n",
    )

    @Test
    fun fnWithBlocklessBody() = assertPseudoCode(
        want = """
            |fn (x) {
            |  x
            |}
            |
        """.trimMargin(),
    ) { doc, pos ->
        FunTree(
            doc,
            pos,
            listOf(
                DeclTree(doc, pos, listOf(LeftNameLeaf(doc, pos, ParsedName("x")))),
                RightNameLeaf(doc, pos, ParsedName("x")),
            ),
        )
    }

    @Test
    fun fnWithVarActual() = assertPseudoCode(
        want = "fn (var x) {}\n",
    ) { doc, pos ->
        FunTree(
            doc,
            pos,
            listOf(
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("x")),
                        ValueLeaf(doc, pos, vVarSymbol),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                BlockTree(doc, pos, emptyList(), LinearFlow),
            ),
        )
    }

    @Test
    fun fnWithComplexBody() = assertPseudoCode(
        want = """
            |fn {
            |  g();
            |  f()
            |}
            |
        """.trimMargin(),
    ) { doc, pos ->
        doc.treeFarm.grow(pos) {
            Fn {
                Block(
                    {
                        // Produce a subsystem that has child 1 before child 0
                        StructuredFlow(
                            ControlFlow.StmtBlock(
                                pos,
                                listOf(
                                    ControlFlow.Stmt(BlockChildReference(1, pos)),
                                    ControlFlow.Stmt(BlockChildReference(0, pos)),
                                ),
                            ),
                        )
                    },
                ) {
                    Call { Rn(ParsedName("f")) }
                    Call { Rn(ParsedName("g")) }
                }
            }
        }
    }

    @Test
    fun ifWithVoidElse() = assertPseudoCode(
        want = """
            |if (f()) {
            |  g()
            |}
            |
        """.trimMargin(),
    ) { doc, pos ->
        doc.treeFarm.grow(pos) {
            Block(
                flowMaker = {
                    StructuredFlow(
                        ControlFlow.StmtBlock(
                            pos,
                            listOf(
                                ControlFlow.If(
                                    pos,
                                    condition = BlockChildReference(0, pos),
                                    thenClause = ControlFlow.StmtBlock(
                                        pos,
                                        listOf(ControlFlow.Stmt(BlockChildReference(1, pos))),
                                    ),
                                    elseClause = ControlFlow.StmtBlock(
                                        pos,
                                        listOf(ControlFlow.Stmt(BlockChildReference(2, pos))),
                                    ),
                                ),
                            ),
                        ),
                    )
                },
            ) {
                Call { Rn(ParsedName("f")) }
                Call { Rn(ParsedName("g")) }
                V(void)
            }
        }
    }

    @Test
    fun formatNoFilePositions() {
        assertPseudoCode(
            input = """
            if (a) {
              if (b) {
                if (c) {
                  let [d, e] = f(a, b, c)
                }
              }
            }
            """.trimIndent(),
            // TODO: space between "let" and "["
            want = """
            if(a, fn {
                if(b, fn {
                    if(c, fn {
                        let[d, e] = f(a, b, c);
                    })
                })
            })

            """.trimIndent(),
            usePositionInfo = false,
        )
    }

    @Test
    fun semiBeforeStatementContinuingWordUsedAsIdentifier() {
        assertPseudoCode(
            input = """
            do {
              do {}
              else()
            }
            """.trimIndent(),
            want = """
            do(fn {
                do(fn {});
                else()
            })

            """.trimIndent(),
            usePositionInfo = false,
        )
    }

    @Test
    fun whileLoop() = assertPseudoCode(
        input = "while (c) { body }",
        want = "while(c, fn {body})\n",
    )

    @Test
    fun doWhileLoop() = assertPseudoCode(
        input = "do { body } while (cond)",
        want = "do(fn {body}, \\nym`callJoin:`, \\while, cond)\n",
    )

    @Test
    fun angleBrackets() = assertPseudoCode(
        input = "Foo<A, B>() < Bar<C<D>>()",
        want = "Foo<A, B>() < Bar<C<D>>()\n",
    )

    @Test
    fun almostAName() = assertPseudoCode(
        want = "nym`foo.bar`\n",
    ) { doc, pos ->
        RightNameLeaf(doc, pos, ParsedName("foo.bar"))
    }

    @Test
    fun reservedWord() = assertPseudoCode(
        want = "nym`nym`\n",
    ) { doc, pos ->
        RightNameLeaf(doc, pos, ParsedName("nym"))
    }

    @Test
    fun dotSyntax() = assertPseudoCode(
        want = "foo.bar\n",
        inputText = "foo.bar",
    ) { doc, pos ->
        doc.treeFarm.grow(pos) {
            Call {
                Rn(dotBuiltinName)
                Rn(ParsedName("foo"))
                V(Symbol("bar"))
            }
        }
    }

    @Test
    fun newSyntax() = assertPseudoCode(
        want = "new C(x), new C<T>(x, y, z)\n",
        input = "new C(x), new C<T>(x, y, z)",
    )

    @Test
    fun bunchOfVar() = assertPseudoCode(
        inputText = "var x; var y; var z;",
        want = "var x, y, z;\n",
    ) { doc, pos ->
        BlockTree(
            doc,
            pos,
            listOf(
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("x")),
                        ValueLeaf(doc, pos, vVarSymbol),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("y")),
                        ValueLeaf(doc, pos, vVarSymbol),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("z")),
                        ValueLeaf(doc, pos, vVarSymbol),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
            ),
            LinearFlow,
        )
    }

    @Test
    fun mixedConstAndLetDecls() = assertPseudoCode(
        inputText = "let a; let b; const c; var v; var w; let d;",
        // Do not fold y into let v, w, x, y, z
        want = "let a, b, @const c; var v, w; let d;\n",
    ) { doc, pos ->
        BlockTree(
            doc,
            pos,
            listOf(
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("a")),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("b")),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("c")),
                        ValueLeaf(doc, pos, Value(Symbol("const"))),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("v")),
                        ValueLeaf(doc, pos, Value(Symbol("var"))),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("w")),
                        ValueLeaf(doc, pos, Value(Symbol("var"))),
                        ValueLeaf(doc, pos, void),
                    ),
                ),
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, ParsedName("d")),
                    ),
                ),
            ),
            LinearFlow,
        )
    }

    @Test
    fun fnWithNoInputsAndUntypedOutput() = assertPseudoCode(
        want = "fn /* o */{}\n",
        inputText = "fn {}",
        usePositionInfo = false,
    ) { d, p ->
        FunTree(
            d,
            p,
            listOf(
                ValueLeaf(d, p, Value(returnDeclSymbol)),
                DeclTree(
                    d,
                    p,
                    listOf(
                        LeftNameLeaf(d, p, ParsedName("o")),
                    ),
                ),
                BlockTree(d, p, listOf(), LinearFlow),
            ),
        )
    }

    @Test
    fun fnWithNoInputsAndTypedOutput() = assertPseudoCode(
        want = "fn: Void {}\n",
        inputText = "fn: Void {}",
        usePositionInfo = false,
    ) { d, p ->
        FunTree(
            d,
            p,
            listOf(
                ValueLeaf(d, p, Value(outTypeSymbol)),
                ValueLeaf(d, p, Types.vVoid),
                BlockTree(d, p, listOf(), LinearFlow),
            ),
        )
    }

    @Test
    fun initialExpressionsVsDefaultExpressions() = assertPseudoCode(
        want = """
        let goodDecl = 123, @default(123) badDecl;
        fn (goodParam = 123, @init(123) badParam) {}

        """.trimIndent(),
        usePositionInfo = false,
    ) { d, p ->
        BlockTree(
            d,
            p,
            listOf(
                DeclTree(
                    d,
                    p,
                    listOf(
                        LeftNameLeaf(d, p, ParsedName("goodDecl")),
                        ValueLeaf(d, p, vInitSymbol),
                        ValueLeaf(d, p, Value(123, TInt)),
                    ),
                ),
                DeclTree(
                    d,
                    p,
                    listOf(
                        LeftNameLeaf(d, p, ParsedName("badDecl")),
                        ValueLeaf(d, p, vDefaultSymbol),
                        ValueLeaf(d, p, Value(123, TInt)),
                    ),
                ),
                FunTree(
                    d,
                    p,
                    listOf(
                        DeclTree(
                            d,
                            p,
                            listOf(
                                LeftNameLeaf(d, p, ParsedName("goodParam")),
                                ValueLeaf(d, p, vDefaultSymbol),
                                ValueLeaf(d, p, Value(123, TInt)),
                            ),
                        ),
                        DeclTree(
                            d,
                            p,
                            listOf(
                                LeftNameLeaf(d, p, ParsedName("badParam")),
                                ValueLeaf(d, p, vInitSymbol),
                                ValueLeaf(d, p, Value(123, TInt)),
                            ),
                        ),
                        BlockTree(d, p, listOf(), LinearFlow),
                    ),
                ),
            ),
            LinearFlow,
        )
    }

    @Test
    fun exportedNameRendering() = assertPseudoCode(
        inputText = """
            |let `//foo.temper`.exportedSymbol;
            |`//foo.temper`.exportedSymbol;
        """.trimMargin(),
        want = """
            |let `//foo.temper`.exportedSymbol; `//foo.temper`.exportedSymbol
            |
        """.trimMargin(),
    ) { doc, pos ->
        val origin = object : NamingContext() {
            override val loc = ModuleName(
                sourceFile = filePath("foo.temper"),
                libraryRootSegmentCount = 0,
                isPreface = false,
            )
        }
        val name = ExportedName(
            origin = origin,
            baseName = ParsedName("exportedSymbol"),
        )
        BlockTree(
            doc,
            pos,
            listOf(
                DeclTree(doc, pos, listOf(LeftNameLeaf(doc, pos, name))),
                RightNameLeaf(doc, pos, name),
            ),
            LinearFlow,
        )
    }

    @Test
    fun distinguishTypeValuesFromTypeNames() = assertPseudoCode(
        inputText = """
            |// Uses a type in type position and in an annotation
            |@unrelatedTo(String) let x: Int = 42;
            |// Uses a type in value position
            |let String = type (String);
            |// Uses types in type position for parameter and return
            |let f(x: AnyValue): AnyValue {
            |  // Body uses a type in value position
            |  type (AnyValue)
            |}
            |// Macros that intentionally take type values
            |String extends AnyValue;
            |new DenseBitVector(42);
        """.trimMargin(),
        want = """
            |REM("Uses a type in type position", null);
            |@unrelatedTo(String) let x: Int32 = 42;
            |REM("Uses a type in value position", null);
            |let String = type (String);
            |REM("Uses types in type position for parameter and return", null);
            |let f = fn (x: AnyValue) /* return */: AnyValue {
            |  REM("Body uses a type in value position", null);
            |  type (AnyValue)
            |};
            |REM("Macros that intentionally take type values", null);
            |String extends AnyValue;
            |new DenseBitVector (42)
            |
        """.trimMargin(),
        usePositionInfo = false,
    ) { doc, pos ->
        doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.embeddedCommentFn) {
                    V(Value("Uses a type in type position", TString))
                    V(TNull.value)
                }
                Decl(ParsedName("x")) {
                    V(typeSymbol)
                    V(Types.vInt)
                    V(initSymbol)
                    V(Value(42, TInt))
                    V(Symbol("unrelatedTo"))
                    V(Types.vString)
                }
                Call(BuiltinFuns.embeddedCommentFn) {
                    V(Value("Uses a type in value position", TString))
                    V(TNull.value)
                }
                Decl(ParsedName(TString.name.builtinKey!!)) {
                    V(initSymbol)
                    V(Types.vString)
                }
                Call(BuiltinFuns.embeddedCommentFn) {
                    V(Value("Uses types in type position for parameter and return", TString))
                    V(TNull.value)
                }
                Decl(ParsedName("f")) {
                    V(initSymbol)
                    Fn {
                        Decl(ParsedName("x")) {
                            V(typeSymbol)
                            V(Types.vAnyValue)
                        }
                        V(returnDeclSymbol)
                        Decl(ParsedName("return")) {
                            V(typeSymbol)
                            V(Types.vAnyValue)
                        }
                        Block {
                            Call(BuiltinFuns.embeddedCommentFn) {
                                V(Value("Body uses a type in value position", TString))
                                V(TNull.value)
                            }
                            V(Types.vAnyValue)
                        }
                    }
                }
                Call(BuiltinFuns.embeddedCommentFn) {
                    V(Value("Macros that intentionally take type values", TString))
                    V(TNull.value)
                }
                Call {
                    Rn(BuiltinName("extends"))
                    V(Types.vString)
                    V(Types.vAnyValue)
                }
                Call {
                    Rn(BuiltinName("new"))
                    V(
                        Value(
                            ReifiedType(
                                MkType2(WellKnownTypes.denseBitVectorTypeDefinition).get(),
                            ),
                        ),
                    )
                    V(Value(42, TInt))
                }
            }
        }
    }

    private fun twoDeclarationsOneWithInferences(doc: Document, pos: Position): BlockTree {
        val block = doc.treeFarm.grow(pos) {
            Block {
                Decl(ParsedName("a")) {
                    V(vInitSymbol)
                    V(Value(1, TInt))
                }
                Decl(ParsedName("b")) {
                    V(vTypeSymbol)
                    V(Types.vInt)
                    V(vInitSymbol)
                    V(Value(2, TInt))
                }
            }
        }
        ((block.child(0) as DeclTree).child(0) as LeftNameLeaf).typeInferences =
            BasicTypeInferences(
                MkType.nominal(WellKnownTypes.intTypeDefinition),
                emptyList(),
            )
        return block
    }

    @Test
    fun typeInferencesAreOptional() = assertPseudoCode(
        inputText = "let a = 1, b: Int = 2;",
        want = "let a = 1, b: Int32 = 2;\n",
        detail = PseudoCodeDetail.default,
    ) { doc, pos ->
        twoDeclarationsOneWithInferences(doc, pos)
    }

    @Test
    fun typeInferencesOptedInto() = assertPseudoCode(
        inputText = "let a = 1, b: Int = 2;",
        want = "let a ⦂ Int32 = 1, b: Int32 = 2;\n",
        detail = PseudoCodeDetail(showInferredTypes = true),
    ) { doc, pos ->
        twoDeclarationsOneWithInferences(doc, pos)
    }

    @Test
    fun restFormal() {
        assertPseudoCode(want = "fn (...x: String) {}\n") { doc, pos ->
            val stringList = MkType2(WellKnownTypes.listTypeDefinition)
                .actuals(listOf(WellKnownTypes.stringType2))
                .get()

            doc.treeFarm.grow(pos) {
                Fn {
                    Decl {
                        Ln(ParsedName("x"))
                        V(vRestFormalSymbol)
                        V(void)
                        V(vTypeSymbol)
                        V(Value(ReifiedType(stringList, hasExplicitActuals = true)))
                    }
                    Block {}
                }
            }
        }
    }

    @Test
    fun nestedBlocksGetDo() = assertPseudoCode(
        want = """
            |foo;
            |do {
            |  bar
            |};
            |baz
            |
        """.trimMargin(),
    ) { doc, pos ->
        doc.treeFarm.grow(pos) {
            Block {
                Rn(ParsedName("foo"))
                Block {
                    Rn(ParsedName("bar"))
                }
                Rn(ParsedName("baz"))
            }
        }
    }

    private fun docStringOnSimpleDeclExampleTest(want: String, detail: PseudoCodeDetail) =
        assertPseudoCode(want = want, detail = detail) { doc, pos ->
            doc.treeFarm.grow {
                Decl(pos) {
                    Ln(ParsedName("x"))
                    V(initSymbol)
                    V(Value(1, TInt))
                    V(docStringSymbol)
                    V(
                        Value(
                            listOf(
                                Value("A very long doc string", TString),
                                Value("A very long doc string\n\netc.", TString),
                                Value("path/to/a/file.temper", TString),
                            ),
                            TList,
                        ),
                    )
                }
            }
        }

    @Test
    fun declDocStringsAreLong() = docStringOnSimpleDeclExampleTest(
        want = """
            |let x = 1
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default,
    )

    @Test
    fun declDocStringsAreLongButThatIsOk() = docStringOnSimpleDeclExampleTest(
        want = """
            |@docString(...) let x = 1
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Short),
    )

    private fun qNameExampleTest(
        want: String,
        detail: PseudoCodeDetail,
    ) = assertPseudoCode(
        want = want,
        inputText = """let nym`foo.bar` = 123""",
        detail = detail,
    ) { doc, pos ->
        val parsedName = ParsedName("foo.bar")
        val qName = QName.fromString("""my-lib/foo/bar.foo\.bar""").result!!
        doc.treeFarm.grow(pos) {
            Decl {
                Ln(doc.nameMaker.unusedSourceName(parsedName))
                V(vQNameSymbol)
                V(Value("$qName", TString))
                V(vInitSymbol)
                V(Value(123, TInt))
            }
        }
    }

    @Test
    fun qNamesAreOftenRedundant() = qNameExampleTest(
        want = """
            |let nym`foo.bar__0` = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default,
    )

    @Test
    fun whenYouWantToDebugQNamesTheyShouldNotBeUgly() = qNameExampleTest(
        want = """
            |@QName(raw "my-lib/foo/bar.foo\.bar") let nym`foo.bar__0` = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default.copy(showQNames = true),
    )

    @Test
    fun qNameMetadataIsMetadata() = qNameExampleTest(
        want = """
            |@QName(raw "my-lib/foo/bar.foo\.bar") let nym`foo.bar__0` = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default.copy(verboseMetadata = NoneShortOrLong.Long),
    )

    private fun docStringOnFunctionExampleTest(want: String, detail: PseudoCodeDetail) =
        assertPseudoCode(want = want, detail = detail) { doc, pos ->
            doc.treeFarm.grow {
                Fn(pos) {
                    Decl(ParsedName("x"))
                    V(docStringSymbol)
                    V(
                        Value(
                            listOf(
                                Value("Some docs", TString),
                                Value("Some docs\n\netc.", TString),
                                Value("path/to/a/file.temper", TString),
                            ),
                            TList,
                        ),
                    )
                    Block {}
                }
            }
        }

    @Test
    fun fnDocStringsAreLong() = docStringOnFunctionExampleTest(
        want = """
            |fn (x) {}
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default,
    )

    @Test
    fun fnDocStringsAreLongButThatIsOkWithinReason() = docStringOnFunctionExampleTest(
        want = """
            |@docString(...) fn (x) {}
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Short),
    )

    @Test
    fun fnDocStringsAreLongButThatIsOk() = docStringOnFunctionExampleTest(
        want = """
            |@docString((["Some docs", "Some docs\n\netc.", "path/to/a/file.temper"])) fn (x) {}
            |
        """.trimMargin(),
        detail = PseudoCodeDetail.default.copy(docStringDetail = NoneShortOrLong.Long),
    )

    private fun growLetXEq123WithSsaMetadata(d: Document, p: Position): DeclTree =
        // @ssa let x = 123
        d.treeFarm.grow(p) {
            Decl {
                Ln(ParsedName("x"))
                V(vSsaSymbol)
                V(void)
                V(initSymbol)
                V(Value(123, TInt))
            }
        }

    @Test
    fun spammyMetadataNone() = assertPseudoCode(
        want = """
            |let x = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail(verboseMetadata = NoneShortOrLong.None),
        makeInput = ::growLetXEq123WithSsaMetadata,
    )

    @Test
    fun spammyMetadataShort() = assertPseudoCode(
        want = """
            |@_ let x = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail(verboseMetadata = NoneShortOrLong.Short),
        makeInput = ::growLetXEq123WithSsaMetadata,
    )

    @Test
    fun spammyMetadataLong() = assertPseudoCode(
        want = """
            |@ssa let x = 123
            |
        """.trimMargin(),
        detail = PseudoCodeDetail(verboseMetadata = NoneShortOrLong.Long),
        makeInput = ::growLetXEq123WithSsaMetadata,
    )

    @Test
    fun resugaringDots() = assertPseudoCode(
        want = """
            |x.j = x.i + x.f(1)
            |
        """.trimMargin(),
        detail = PseudoCodeDetail(resugarDotHelpers = true),
        makeInput = { doc, pos ->
            doc.treeFarm.grow(pos) {
                Call(DotHelper(ExternalSet, Symbol("j"), emptyList())) {
                    Rn(ParsedName("x"))
                    Call(BuiltinFuns.plusFn) {
                        Call(DotHelper(ExternalGet, Symbol("i"), emptyList())) {
                            Rn(ParsedName("x"))
                        }
                        Call {
                            Call(DotHelper(ExternalBind, Symbol("f"), emptyList())) {
                                Rn(ParsedName("x"))
                            }
                            V(Value(1, TInt))
                        }
                    }
                }
            }
        },
    )

    @Test
    fun postfixOperators() = assertPseudoCode(
        input = "C<A>?",
        want = """
            |C<A>?
            |
        """.trimMargin(),
        usePositionInfo = false,
    )

    @Test
    fun highlightingOneFCall() = assertPseudoCode(
        want = """
            |f();
            |f(); /*->*/
            |f() /*<-*/;
            |f()
            |
        """.trimMargin(),
        detail = PseudoCodeDetail(resugarDotHelpers = true) {
            it is CallTree && it.incoming?.edgeIndex == 2
        },
        makeInput = { doc, pos ->
            doc.treeFarm.grow(pos) {
                val fName = BuiltinName("f")
                Block {
                    Call { Rn(fName) }
                    Call { Rn(fName) }
                    Call { Rn(fName) }
                    Call { Rn(fName) }
                }
            }
        },
    )

    @Test
    fun inlineBracketsForShortObjects() = assertPseudoCode(
        // Some objects like {class: Empty__0} should be rendered on one
        // line.
        want = """
            |let x = {class: Empty__9};
            |
        """.trimMargin(),
        makeInput = { doc, pos ->
            doc.treeFarm.grow(pos) {
                Block {
                    Decl(ParsedName("x")) {
                        V(initSymbol)
                        V(emptyValue)
                    }
                    V(void)
                }
            }
        },
    )

    private fun assertPseudoCode(
        input: String,
        want: String,
        usePositionInfo: Boolean = true,
        detail: PseudoCodeDetail = PseudoCodeDetail.default,
    ) = assertPseudoCode(
        want = want,
        inputText = input,
        usePositionInfo = usePositionInfo,
        detail = detail,
    ) { d, p ->
        val logSink = ListBackedLogSink()
        val lexer = Lexer(p.loc, logSink, input)
        val comments = mutableListOf<CstComment>()
        val cst = parse(lexer, logSink, comments)
        buildTree(
            cstParts = flatten(cst),
            storedCommentTokens = StoredCommentTokens(comments),
            logSink = logSink,
            documentContext = d.context,
        )
    }

    private fun assertPseudoCode(
        want: String,
        inputText: String? = null,
        usePositionInfo: Boolean = inputText != null,
        detail: PseudoCodeDetail = PseudoCodeDetail.default,
        makeInput: (doc: Document, pos: Position) -> Tree,
    ) {
        val context = TestDocumentContext()
        val input = makeInput(Document(context), Position(context.loc, 0, 0))

        val filePositions = if (usePositionInfo) {
            require(inputText != null)
            FilePositions.fromSource(context.loc, inputText)
        } else {
            FilePositions.nil
        }

        val got = toStringViaBuilder { sb ->
            val textOutput = AppendingTextOutput(sb)
            input.toPseudoCode(textOutput, filePositions, detail = detail)
        }

        if (dumpToConsole) {
            console.log("Pseudocode for ${ input.toLispy() }")
            input.toPseudoCode(console.textOutput, filePositions, singleLine = false)
        }

        assertStringsEqual(want, got, message = inputText)
    }
}
