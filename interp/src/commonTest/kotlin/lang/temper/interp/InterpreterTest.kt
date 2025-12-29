package lang.temper.interp

import lang.temper.ast.flatten
import lang.temper.astbuild.StoredCommentTokens
import lang.temper.astbuild.buildTree
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.AtomicCounter
import lang.temper.common.LeftOrRight
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.cst.CstComment
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.env.ReferentBit
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.lexer.Genre
import lang.temper.lexer.Lexer
import lang.temper.lexer.Operator
import lang.temper.log.FailLog
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.excerpt
import lang.temper.log.toReadablePosition
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.parser.parse
import lang.temper.stage.Stage
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.Abort
import lang.temper.value.ActualValues
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallableValue
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.LinearFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.Stayless
import lang.temper.value.StaylessMacroValue
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.and
import lang.temper.value.elseSymbol
import lang.temper.value.freeTree
import lang.temper.value.initSymbol
import lang.temper.value.or
import lang.temper.value.passedWithType
import lang.temper.value.ssaSymbol
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void
import kotlin.test.Ignore
import kotlin.test.Test

@Suppress("MagicNumber")
class InterpreterTest {

    @Test
    fun aNumber() {
        assertResult(
            expectedJson = """ "123: Int32" """,
            input = """123""",
        )
    }

    @Test
    fun binaryAddition() {
        assertResult(
            expectedJson = """ "579: Int32" """,
            input = """123 + 456""",
        )
    }

    @Test
    fun mixedIntArithmetic() {
        assertResult(
            expectedJson = """ "10839: Int32" """,
            input = """123 + 456 * (43 - -4) / 2""",
        )
    }

    @Test
    fun divByZero() {
        assertResult(
            expectedJson = """
            {
                "result": null,
                "errors": [
                    "Division by zero!"
                ]
            }
            """,
            input = """1 / 0""",
        )
    }

    @Test
    fun divByIntNegZero() {
        assertResult(
            expectedJson = """
            {
                "result": null,
                "errors": [
                    "Division by zero!"
                ]
            }
            """,
            input = """1 / -0""",
            expectedFailLog = """
            f.t:1+0-6: Interpreting
                1 / -0
            f.t:1+0-6: Division by zero
                1 / -0
            """,
        )
    }

    @Test
    fun floatZeroByZero() {
        for (lSign in listOf("+", "-")) {
            for (rSign in listOf("+", "-")) {
                assertResult(
                    expectedJson = """
                    {
                        "result": null,
                        "errors": [
                            "Division by zero!"
                        ]
                    }
                    """,
                    input = """${lSign}0.0 / ${rSign}0.0""",
                    // Presumably we shouldn't have these type errors, but we do at present.
                    expectedFailLog = """
                    f.t:1+0-11: Interpreting
                        ${lSign}0.0 / ${rSign}0.0
                    f.t:1+0-11: Division by zero
                        ${lSign}0.0 / ${rSign}0.0
                    """,
                )
            }
        }
    }

    @Suppress("SpellCheckingInspection") // Up-case Brahmagupta trips a different lint rule
    @Test
    fun brahmaguptasRevenge() {
        assertResult(
            expectedJson = """ "0: Int32" """,
            // Division by zero is recovered from.
            input = """(1 / 0) orelse 0 /* You win this time, Brahmagupta! */""",
        )
    }

    @Test
    fun divByNonZero() {
        assertResult(
            expectedJson = """ "2: Int32" """, // Trust me.
            input = """8 / 3""",
        )
    }

    @Test
    fun assignVariable() = assertResult(
        expectedJson = """ "42: Int32" """,
        input = """
        var x = 6;
        x = x * 7
        """,
    )

    @Test
    fun fibonacci() = assertResult(
        expectedJson = """ "55: Int32" """,
        input = """
        function fib(n) {
          var i = n;
          var a = 0;
          var b = 1;
          while (i > 0) {
            let c = a + b;
            a = b;
            b = c;
            i = i - 1
          }
          a
        }
        fib(10)
        """,
    )

    @Test
    fun fibonacciVariousArgumentPassingConventions() = assertResult(
        expectedJson = """ "[55, 34, 21]: List" """,
        input = """
        let fib(n: Int = 8): Int {
          var i = n;
          var a = 0;
          var b = 1;
          while (i > 0) {
            let c = a + b;
            a = b;
            b = c;
            i = i - 1
          }
          a
        }
        [fib(10), fib(\n, 9), fib()]
        """,
        // TODO: once disambiguation done,
        //     fib(\i, 9)
        // can become
        //     fib(i = 9)
    )

    @Test
    fun scopingIsNotDynamic() = assertResult(
        // This tests the behavior of the interpreter when operating only on ParsedNames
        // which compare for equality based on name texts.
        input = """
        let v = "outer";
        let f() {
            let v = "inner"; // Only masks in scope of f's body not when f calls g
            g()
        }
        let g() {
            v
        }
        f()
        """,
        expectedJson = """["outer", "String"]""",
    )

    @Test
    fun passedInitialization() = assertResult(
        expectedJson = """ "void: Void" """,
        input = """
        let i: Int = 1
        """,
    )

    @Test
    fun failedInitialization() = assertResult(
        expectedJson = """
        {
            "result": null,
            "errors": [
                "Type Int32 rejected value \"1\"!"
            ]
        }
        """,
        input = """
        let i: Int = "1"
        """,
        expectedFailLog = """
        f.t:2+8-24: Interpreting
            let i: Int = "1"
        f.t:2+8-24: Type Int32 rejected value "1"
            let i: Int = "1"
        """.trimIndent(),
    )

    @Test
    fun uninitialized() = assertResult(
        expectedJson = """
        {
            "result": null,
            "errors": [
                "i has not been initialized!"
            ]
        }
        """,
        input = "let i: Int; i",
        //       01234567890123
        //                 1
        expectedFailLog = """
        f.t:1+0-13: Interpreting
            let i: Int; i
        f.t:1+12-13: Interpreting
            i
        f.t:1+12-13: i has not been initialized
            i
        """.trimIndent(),
    )

    @Test
    fun undeclared() = assertResult(
        expectedJson = """
        {
            "result": null,
            "errors": [
                "i has not been declared!"
            ]
        }
        """,
        input = "i",
    )

    @Test
    fun list() = assertResult(
        """
        "[1, 2, 3]: List"
        """,
        input = "[1, 2, 3]",
    )

    @Test
    fun listConstructor() = assertResult(
        """
        "[0]: List"
        """,
        "[Int; 0]",
    )

    @Test
    fun infLoop() = assertResult(
        " \"ABORT! ABORT!! ABORT!!!\" ",
        """
        var i = 0;
        while (true) {
            i = i + 1
        }
        """.trimIndent(),
        expectedFailLog = """
        f.t:1+0 - 4+1: Interpreting
            var i = 0; while (true) {     i = i + 1 }
        f.t:2+0 - 4+1: Interpreting
            while (true) {     i = i + 1 }
        f.t:3+4-13: Interpreting
            i = i + 1
        f.t:3+6-7: Interpretation aborted
            =
        """.trimIndent(),
    )

    @Test
    fun fnValueWithMetadata() = assertResult(
        expectedJson = """
            "\u0192: Function"
        """,
        inputText = "@a fn {42}",
        //           0123456789A
    ) { _, context ->
        val doc = Document(context)
        val loc = context.loc
        FunTree(
            doc,
            Position(loc, 0, 10),
            listOf(
                ValueLeaf(doc, Position(loc, 0, 2), Value(Symbol("a"))),
                ValueLeaf(doc, Position(loc, 2, 2), void),
                BlockTree(
                    doc,
                    Position(loc, 6, 10),
                    listOf(
                        ValueLeaf(doc, Position(loc, 7, 9), Value(42, TInt)),
                    ),
                    LinearFlow,
                ),
            ),
        )
    }

    @Test
    fun callOfFnValueWithMetadata() = assertResult(
        expectedJson = """
            "42: Int32"
        """,
        inputText = "(@a fn {42})()",
        //           012345678901234
        //                     1
    ) { _, context ->
        val doc = Document(context)
        val loc = context.loc
        doc.treeFarm.grow {
            Call(Position(loc, 0, 14)) {
                Fn(Position(loc, 1, 11)) {
                    V(Position(loc, 1, 3), Symbol("a"))
                    V(Position(loc, 3, 3), void)
                    Block(Position(loc, 7, 11)) {
                        V(Position(loc, 8, 10), Value(42, TInt))
                    }
                }
            }
        }
    }

    @Ignore("`DoTransform` doesn't work without partial interp mode first.")
    @Test
    fun resultOfLabeledBlock() = assertResult(
        expectedJson = """ "true: Boolean" """,
        input = "foo: do { true }",
    )

    @Test
    fun banReassignLet() = assertResult(
        expectedJson = """
        {
            "errors": [
                "Cannot set const i again!"
            ],
            "result": null
        }
        """,
        input = "let i = 0; i = 1",
    )

    @Test
    fun commentsDoNotAffectResults() = assertResult(
        // Parsing produces REM function instances.  Those should not affect results.
        expectedJson = """ "2: Int32" """,
        input = """
            |// Comment
            |1 + 1
            |// Trailing comment
        """.trimMargin(),
    )

    @Test
    fun aliasedTypeUsableInTypeContext() = assertResult(
        expectedJson = """
            |{
            |  result: "notYet",
            |  ast: [ "Block", [
            |      [ "Block", [
            |          [ "Decl", [
            |              [ "LeftName", "T__0" ],
            |              [ "Value", "\\init: Symbol" ],
            |              [ "Value", "Boolean: Type" ],
            |              [ "Value", "\\ssa: Symbol" ],
            |              [ "Value", "void: Void" ],
            |            ]
            |          ],
            |          [ "Decl", [
            |              [ "LeftName", "x__1" ],
            |              [ "Value", "\\type: Symbol" ],
            |              [ "Value", "Boolean: Type" ],
            |            ]
            |          ],
            |        ]
            |      ]
            |    ]
            |  ]
            |}
        """.trimMargin(),
        inputText = """
            |let T = String;
            |let x: T;
        """.trimMargin(),
        interpMode = InterpMode.Partial,
        stage = Stage.GenerateCode,
        afterInterpretationAssembleOutput = { result, ast ->
            JsonValueBuilder.build {
                obj {
                    key("result") { value(result) }
                    key("ast") { value(ast) }
                }
            }
        },
    ) { _, docContext ->
        // let T = /* reified type value */;
        val doc = Document(docContext)
        val pos = Position(doc.context.namingContext.loc, 0, 0)

        val nameT = doc.nameMaker.unusedSourceName(ParsedName("T"))
        val nameX = doc.nameMaker.unusedSourceName(ParsedName("x"))

        // Create a mutable, unstable environment
        val unstableEnvironment = BlockEnvironment(EmptyEnvironment)
        unstableEnvironment.declare(
            doc.nameMaker.unusedTemporaryName("t"),
            DeclarationBits(
                reifiedType = null,
                initial = null,
                constness = Constness.NotConst,
                referentSource = ReferentSource.Unknown,
                missing = ReferentBitSet.forBitMask(ReferentBit.Type.bit),
                declarationSite = pos,
            ),
            InterpreterCallback.NullInterpreterCallback,
        )

        val typeT = Types.boolean

        doc.treeFarm.grow(pos) {
            Block {
                Decl {
                    Ln(nameT)
                    V(initSymbol)
                    V(Value(typeT))
                    V(ssaSymbol)
                    V(void)
                }
                Decl {
                    Ln(nameX)
                    V(typeSymbol)
                    Rn(nameT)
                }
            }
        }
    }

    @Test
    fun structuredFlowLoop() = assertResult(
        expectedJson = """
            |[ 42, "Int32" ]
        """.trimMargin(),
        inputText = """
            |var x = 0;      // ref 0
            |
            |var i = 3;      // ref 1
            |for (
            |  ;
            |  i < 10;       // ref 2
            |  i = i + 1     // ref 3
            |) {
            |  x = x + i;    // ref 4
            |}
            |x               // ref 5
        """.trimMargin(),
    ) { _, docContext ->
        val doc = Document(docContext)
        val pos = Position(docContext.loc, 0, 0)
        val x = doc.nameMaker.unusedSourceName(ParsedName("x"))
        val i = doc.nameMaker.unusedSourceName(ParsedName("i"))
        doc.treeFarm.grow(pos) {
            Block(
                flowMaker = {
                    StructuredFlow(
                        ControlFlow.StmtBlock(
                            pos,
                            listOf(
                                ControlFlow.Stmt(BlockChildReference(0, pos)),
                                ControlFlow.Stmt(BlockChildReference(1, pos)),
                                ControlFlow.Loop(
                                    pos = pos,
                                    label = null,
                                    checkPosition = LeftOrRight.Left,
                                    condition = BlockChildReference(2, pos),
                                    increment = ControlFlow.StmtBlock(
                                        pos,
                                        listOf(
                                            ControlFlow.Stmt(BlockChildReference(3, pos)),
                                        ),
                                    ),
                                    body = ControlFlow.StmtBlock(
                                        pos,
                                        listOf(
                                            ControlFlow.Stmt(BlockChildReference(4, pos)),
                                        ),
                                    ),
                                ),
                                ControlFlow.Stmt(BlockChildReference(5, pos)),
                            ),
                        ),
                    )
                },
            ) {
                Decl(x) { // var x = 0
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(Value(0, TInt))
                }
                Decl(i) { // var i = 3
                    V(varSymbol)
                    V(void)
                    V(initSymbol)
                    V(Value(3, TInt))
                }
                Call(BuiltinFuns.lessThanFn) { // i < 10
                    Rn(i)
                    V(Value(10, TInt))
                }
                Call(BuiltinFuns.setLocalFn) { // i = i + 1
                    Ln(i)
                    Call(BuiltinFuns.plusFn) {
                        Rn(i)
                        V(Value(1, TInt))
                    }
                }
                Call(BuiltinFuns.setLocalFn) { // x = x + i
                    Ln(x)
                    Call(BuiltinFuns.plusFn) {
                        Rn(x)
                        Rn(i)
                    }
                }
                Rn(x) // x
            }
        }
    }

    @Test
    fun blockWithDeclarations() = assertResult(
        inputText = """
            |let T = do {
            |  let U = type (U);
            |  U extends AnyValue;
            |  U
            |};
            |T
        """.trimMargin(),
        expectedJson = """
            |{
            |  result: [ "U__1", "Type" ],
            |
            |  treeAfter: ```
            |    do {
            |      let T__0 = do {
            |        let U__1 = type (U__1);
            |        void;
            |        type (U__1)
            |      };
            |      type (U__1)
            |    }
            |    ```
            |}
        """.trimMargin(),
        interpMode = InterpMode.Partial, // Run `extends` macro.
        astMaker = { _, ctx ->
            val doc = Document(ctx)
            val pos = Position(ctx.loc, 0, 0)
            val nameMaker = doc.nameMaker
            val nameT = nameMaker.unusedSourceName(ParsedName("T"))
            val nameU = nameMaker.unusedSourceName(ParsedName("U"))

            val uDef = TypeFormal(pos, nameU, Symbol("U"), Variance.Invariant, AtomicCounter())
            val uType = MkType2(uDef).get()

            doc.treeFarm.grow(pos) {
                Block {
                    Decl(nameT) {
                        V(vInitSymbol)
                        Block {
                            Decl(nameU) {
                                V(vInitSymbol)
                                V(Value(ReifiedType(uType), TType))
                            }
                            Call(ExtendsFn) {
                                Rn(nameU)
                                V(Value(Types.anyValue))
                            }
                            Rn(nameU)
                        }
                    }
                    Rn(nameT)
                }
            }
        },
        afterInterpretationAssembleOutput = { result, treeAfter ->
            JsonValueBuilder.build {
                obj {
                    key("result") { value(result) }
                    key("treeAfter") {
                        value(treeAfter.toPseudoCode(singleLine = false).trimEnd())
                    }
                }
            }
        },
    )

    @Test
    fun partialInterpretationOfRestyFunctions() = assertResult(
        inputText = "listify('a', cat('b'), 'c')",
        interpMode = InterpMode.Partial,
        expectedJson = """
            |{
            |  stateVector: ```
            |    ["a", "b", "c"]
            |    ```,
            |  typeTag: "List",
            |}
        """.trimMargin(),
    ) { _, documentContext ->
        val doc = Document(documentContext)
        val pos = Position(doc.nameMaker.namingContext.loc, 0, 0)
        doc.treeFarm.grow(pos) {
            Block {
                Call(BuiltinFuns.listifyFn) {
                    V(Value("a", TString))
                    Call(BuiltinFuns.strCatFn) {
                        V(Value("b", TString))
                    }
                    V(Value("c", TString))
                }
            }
        }
    }

    @Test
    fun notYetInOrElse() = assertResult(
        inputText = "nyet() orelse da()",
        interpMode = InterpMode.Partial,
        expectedJson = """
            |"notYet"
        """.trimMargin(),
    ) { _, documentContext ->
        val doc = Document(documentContext)
        val pos = Position(doc.nameMaker.namingContext.loc, 0, 0)
        val label = doc.nameMaker.unusedSourceName(ParsedName("orelse"))
        doc.treeFarm.grow(pos) {
            Block(
                pos,
                flowMaker = {
                    val (nyetCall, daCall) = it.edges
                    StructuredFlow(
                        ControlFlow.StmtBlock.wrap(
                            ControlFlow.OrElse(
                                pos,
                                ControlFlow.Labeled(
                                    pos,
                                    label,
                                    null,
                                    ControlFlow.StmtBlock.wrap(
                                        ControlFlow.Stmt(BlockChildReference(nyetCall.edgeIndex, pos)),
                                    ),
                                ),
                                ControlFlow.StmtBlock.wrap(
                                    ControlFlow.Stmt(BlockChildReference(daCall.edgeIndex, pos)),
                                ),
                            ),
                        ),
                    )
                },
            ) {
                Call(Nyet) {}
                Call(Da) {}
            }
        }
    }

    private fun assertResult(
        expectedJson: String,
        input: String,
        expectedFailLog: String? = null,
    ) = assertResult(
        expectedJson,
        inputText = input,
        expectedFailLog = expectedFailLog,
    ) { logSink, context ->
        // Lex the input
        val lexer = Lexer(context.loc, logSink, input)

        // Build a parse tree
        val comments = mutableListOf<CstComment>()
        val cst = parse(lexer, logSink, comments)

        // Build an AST.
        val ast = buildTree(
            cstParts = flatten(cst),
            storedCommentTokens = StoredCommentTokens(comments),
            logSink = logSink,
            documentContext = context,
        )

        // Pre-process the AST to do just enough to get basic language features working.
        preprocess(ast)
    }

    private fun assertResult(
        expectedJson: String,
        inputText: String,
        expectedFailLog: String? = null,
        verbose: Boolean = false,
        stage: Stage = Stage.Run,
        interpMode: InterpMode = InterpMode.Full,
        /**
         * May be overridden to derive the value to compare to [expectedJson] from the interpreter result and the
         * state of the tree after interpretation.  By default, just returns the interpreter result.
         */
        afterInterpretationAssembleOutput: (PartialResult, Tree) -> Structured =
            { result, _ -> result },
        astMaker: (LogSink, TestDocumentContext) -> Tree,
    ) {
        val logSink = ListBackedLogSink()
        val failLog = FailLog(logSink = logSink)

        // Take fewer than 10000 steps.
        var thousandsLeft = 10
        val continueCondition = {
            if (thousandsLeft > 0) {
                thousandsLeft -= 1
                true
            } else {
                false
            }
        }

        val context = TestDocumentContext()

        val loc = context.loc

        val ast = astMaker(logSink, context)
        if (verbose) {
            console.log(ast.toLispy(multiline = true))
        }

        val interpreter = Interpreter(
            failLog,
            logSink,
            stage,
            ast.document.nameMaker,
            continueCondition,
        )

        // Wrapping an AST in a block solves lots of problems; e.g. macro calls always have an
        // incoming edge to hold their expansion.  The compiler does this so that we don't miss
        // coverage by not wrapping.
        val root = BlockTree.wrap(ast)

        // Run the interpreter.
        val got = try {
            afterInterpretationAssembleOutput(
                interpreter.interpret(
                    root,
                    BlockEnvironment(
                        testEnvironment(
                            builtinOnlyEnvironment(
                                EmptyEnvironment,
                                Genre.Library,
                            ),
                        ),
                    ),
                    interpMode,
                ),
                root,
            )
        } catch (_: Abort) {
            ShoutyLoud("ABORT")
        } catch (_: Panic) {
            ShoutyLoud("PANIC")
        }

        if (got is Fail) {
            got.info?.logTo(logSink)
        }

        var pass = false
        try {
            // Compare the results.
            assertStructure(
                expectedJson = expectedJson,
                input = logSink.wrapErrorsAround(got),
            )
            pass = true
        } finally {
            if (!pass) {
                console.log("input: $inputText")
                // console.log("cst\n${ FormattingStructureSink.toJsonString(cst) }")
                // console.log("ast\n${ FormattingStructureSink.toJsonString(ast) }")
                if (got !is Value<*>) {
                    replayLessSpammy(
                        failLog,
                        object : LogSink {
                            override var hasFatal = false
                                private set

                            override fun log(
                                level: Log.Level,
                                template: MessageTemplateI,
                                pos: Position,
                                values: List<Any>,
                                fyi: Boolean,
                            ) {
                                if (level >= Log.Fatal) {
                                    hasFatal = true
                                }
                                excerpt(pos, inputText, console.textOutput)
                                console.log(template.format(values), level = level)
                            }
                        },
                    )
                }
            }
        }

        if (expectedFailLog != null) {
            val gotFailLog = mutableListOf<String>()
            val positions = FilePositions.fromSource(loc, inputText)
            replayLessSpammy(
                failLog,
                object : LogSink {
                    override var hasFatal = false
                        private set

                    override fun log(
                        level: Log.Level,
                        template: MessageTemplateI,
                        pos: Position,
                        values: List<Any>,
                        fyi: Boolean,
                    ) {
                        if (level >= Log.Fatal) {
                            hasFatal = true
                        }
                        val startPos = positions.filePositionAtOffset(pos.left)
                        val endPos = positions.filePositionAtOffset(pos.right)
                        val posPrefix = (startPos to endPos).toReadablePosition("f.t")
                        gotFailLog += "$posPrefix: ${ template.format(values) }"
                        val snippet = inputText.substring(pos.left, pos.right)
                        gotFailLog += "    ${ snippet.replace("\n", " ") }"
                    }
                },
            )
            assertStringsEqual(
                expectedFailLog.trimIndent(),
                gotFailLog.joinToString("\n"),
            )
        }
    }

    private fun replayLessSpammy(failLog: FailLog, logSink: LogSink) {
        failLog.logReasonForFailure(
            object : LogSink {
                private var lastInterpretingPos: Position? = null

                override val hasFatal: Boolean
                    get() = throw IllegalStateException("Why are you asking me?")

                override fun log(
                    level: Log.Level,
                    template: MessageTemplateI,
                    pos: Position,
                    values: List<Any>,
                    fyi: Boolean,
                ) {
                    lastInterpretingPos =
                        if (template == MessageTemplate.Interpreting && values.isEmpty()) {
                            // Filter out adjacent messages about interpreting trees with
                            // the same position.  This visual clutter happens due to
                            // wrapper trees with the same position as their sole child.
                            val last = lastInterpretingPos
                            if (last != null && last.left == pos.left && last.right == pos.right) {
                                return
                            }
                            pos
                        } else {
                            null
                        }
                    logSink.log(
                        level = level,
                        template = template,
                        pos = pos,
                        values = values,
                        fyi = fyi,
                    )
                }
            },
        )
    }
}

private class ShoutyLoud(val str: String) : Structured {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value("$str! $str!! $str!!!")
    }
}

private fun makeSpecialValue(f: (MacroEnvironment) -> PartialResult): Value<MacroValue> = Value(
    object : SpecialFunction {
        override val sigs: List<Signature2>? get() = null
        override fun invoke(
            macroEnv: MacroEnvironment,
            interpMode: InterpMode,
        ): PartialResult = f(macroEnv)
    },
)

private fun makeFunValue(f: (ActualValues, InterpreterCallback) -> PartialResult) = Value(
    object : CallableValue, StaylessMacroValue {
        override val sigs: List<Signature2>? get() = null
        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ) = f(args, cb)
    },
    TFunction,
)

private fun testEnvironment(parent: Environment): Environment =
    immutableEnvironment(
        parent,
        mapOf(
            ParsedName(Operator.OrElse.text!!) to makeSpecialValue {
                val args = it.args
                if (args.size == 2) {
                    when (val result0 = args.evaluate(0, InterpMode.Full)) {
                        is Value<*> -> result0
                        is Fail -> args.evaluate(1, InterpMode.Full)
                        NotYet -> NotYet
                    }
                } else {
                    Fail
                }
            },
            // Some statements
            ParsedName("while") to makeSpecialValue body@{ macroEnv ->
                val args = macroEnv.args
                val n = args.size
                if (n == 0) {
                    macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf(2))
                    return@body Fail
                }
                var result: PartialResult = void
                var bodyFn: CallableValue? = null
                while_loop@
                while (result != Fail) {
                    var lastConditionValue: Value<*> = TBoolean.valueFalse
                    // Loop to handle
                    //    while (a, b, c) ...
                    for (i in 0 until (n - 1)) {
                        if (args.key(i) != null) { return@body Fail }
                        when (val r = args.evaluate(i, InterpMode.Full)) {
                            is Fail, NotYet -> return@body r
                            is Value<*> -> lastConditionValue = r
                        }
                    }
                    if (lastConditionValue.typeTag != TBoolean) {
                        macroEnv.explain(
                            MessageTemplate.ExpectedValueOfType,
                            args.pos(n - 2),
                            listOf(TBoolean),
                        )
                        return@body Fail
                    }
                    if (!TBoolean.unpack(lastConditionValue)) {
                        break
                    }
                    if (bodyFn == null) {
                        // Evaluate body as function once.
                        args.evaluate(n - 1, InterpMode.Full)
                            .passedWithType(TFunction) {
                                if (it is CallableValue) {
                                    bodyFn = it
                                    void
                                } else {
                                    Fail
                                }
                            }.or {
                                macroEnv.explain(
                                    MessageTemplate.ExpectedValueOfType,
                                    args.pos(n - 1),
                                    listOf(TFunction),
                                )
                                return@body Fail
                            }
                    }
                    result = bodyFn!!(ActualValues.Empty, macroEnv, InterpMode.Full)
                }
                result
            },
            ParsedName("if") to makeFunValue { args, cb ->
                when (args.size) {
                    2 -> {
                        val (cond, thenClause) = args.unpackPositioned(2, cb)
                            ?: return@makeFunValue Fail
                        runIf(cond, thenClause, null, cb)
                    }
                    3,
                    -> {
                        if (
                            args.key(0) == null &&
                            args.key(1) == null &&
                            args.key(2) == elseSymbol
                        ) {
                            runIf(args.result(0), args.result(1), args.result(1), cb)
                        } else {
                            cb.explain(MessageTemplate.NoSignatureMatches)
                            Fail
                        }
                    }
                    else -> {
                        cb.explain(MessageTemplate.ArityMismatch, values = listOf(2))
                        Fail
                    }
                }
            },
            // Declaration Macros
            ParsedName("function") to makeSpecialValue { functionMacro(it) },
            // `let x(arg) { body }` parses via the ControlFlow production, not as a DeclTree.
            ParsedName("let") to makeSpecialValue { functionMacro(it) },
        ),
        isLongLived = true,
    )

private fun runIf(
    cond: Value<*>,
    thenClause: Value<*>,
    elseClause: Value<*>?,
    cb: InterpreterCallback,
) = cond.passedWithType(TFunction) { fCond: MacroValue ->
    thenClause.passedWithType(TFunction) { fThen: MacroValue ->
        (elseClause ?: CallableValue.noop).passedWithType(TFunction) { fElse: MacroValue ->
            if (fCond is CallableValue && fThen is CallableValue && fElse is CallableValue) {
                fCond.invoke(ActualValues.Empty, cb, InterpMode.Full).and {
                    it.passedWithType(TBoolean) { b ->
                        val clause = if (b) {
                            fThen
                        } else {
                            fElse
                        }
                        clause.invoke(ActualValues.Empty, cb, InterpMode.Full)
                            .and { r -> r }
                    }
                }
            } else {
                Fail
            }
        }
    }
}

/**
 * Do just enough macro expansion to get basic functionality
 * - Convert (@ var decl) to a decl with metadata that allows for reassignment.
 * - Convert left operand to `=` to a left name.
 */
private fun preprocess(t: Tree): Tree {
    for (edge in t.edges) {
        val before = edge.target
        val after = preprocess(before)
        if (before != after) {
            edge.replace(after)
        }
    }
    if (t is CallTree && t.size == BINARY_OP_CALL_ARG_COUNT) {
        val (callee, arg0, arg1) = t.children
        // Optimistically assume that @ and var are not masked.
        if (
            callee is NameLeaf && callee.content.builtinKey == "@" &&
            arg0 is NameLeaf && arg0.content.builtinKey == "var" &&
            arg1 is DeclTree
        ) {
            arg1.add(newChild = ValueLeaf(arg0.document, arg0.pos, vVarSymbol))
            arg1.add(newChild = ValueLeaf(arg0.document, arg0.pos.rightEdge, void))
            return freeTree(arg1)
        } else if (
            callee is NameLeaf && callee.content.builtinKey == "=" &&
            arg0 is RightNameLeaf
        ) {
            t.edge(1).replace(arg0.copyLeft())
        }
    }
    return t
}

private object Nyet : CallableValue, Stayless {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult = NotYet

    override val sigs: List<Signature2>? get() = null
}

private object Da : CallableValue, Stayless {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult = void

    override val sigs: List<Signature2>? get() = null
}
