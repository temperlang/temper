package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.AtomicCounter
import lang.temper.common.Console
import lang.temper.common.ForwardOrBack
import lang.temper.common.LeftOrRight
import lang.temper.common.ListBackedLogSink
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.TestDocumentContext
import lang.temper.common.console
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.toStringViaTextOutput
import lang.temper.env.InterpMode
import lang.temper.format.CollectedTokens
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.Debug
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.excerpt
import lang.temper.log.toReadablePosition
import lang.temper.log.unknownPos
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BlockChildReference
import lang.temper.value.ControlFlow
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.Document
import lang.temper.value.MacroEnvironment
import lang.temper.value.MaximalPaths
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.blockPartialEvaluationOrder
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.isEmptyBlock
import lang.temper.value.orderedPathIndices
import lang.temper.value.toPseudoCode
import lang.temper.value.void
import kotlin.test.Test
import kotlin.test.assertEquals

class WeaverTest {
    @Test
    fun linear() = assertWovenRoot(
        input = """
            |f(1);
            |f(2)
        """.trimMargin(),
        want = """
            |[[ var t#2 ]];
            |[[ var fail#0 ]];
            |[[ var fail#1 ]];
            |[[ hs(fail#0, f(1)) ]];
            |if ([[ fail#0 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ t#2 = hs(fail#1, f(2)) ]];
            |if ([[ fail#1 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ t#2 ]];
        """.trimMargin(),
    )

    @Test
    fun nestedIfs() = assertWovenRoot(
        input = """
            |if (b(1)) {
            |  f(2);
            |  if (f(3)) {
            |    f(4)
            |  } else {
            |    f(5)
            |  }
            |} else {
            |  f(6)
            |}
        """.trimMargin(),
        want = """
            |[[ var t#8 ]];
            |[[ var t#9 ]];
            |[[ var t#10 ]];
            |[[ var t#11 ]];
            |[[ var t#12 ]];
            |[[ var t#13 ]];
            |[[ var t#14 ]];
            |[[ var t#15 ]];
            |[[ var fail#2 ]];
            |[[ var fail#3 ]];
            |[[ var fail#4 ]];
            |[[ var fail#5 ]];
            |[[ var fail#6 ]];
            |[[ var fail#7 ]];
            |[[ t#8 = hs(fail#2, b(1)) ]];
            |if ([[ fail#2 ]]) {
            |  [[ bubble() ]];
            |}
            |if ([[ t#8 ]]) {
            |  [[ hs(fail#3, f(2)) ]];
            |  if ([[ fail#3 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#9 = hs(fail#4, f(3)) ]];
            |  if ([[ fail#4 ]]) {
            |    [[ bubble() ]];
            |  }
            |  if ([[ t#9 ]]) {
            |    [[ t#10 = hs(fail#5, f(4)) ]];
            |    if ([[ fail#5 ]]) {
            |      [[ bubble() ]];
            |    }
            |    [[ t#12 = t#10 ]];
            |  } else {
            |    [[ t#11 = hs(fail#6, f(5)) ]];
            |    if ([[ fail#6 ]]) {
            |      [[ bubble() ]];
            |    }
            |    [[ t#12 = t#11 ]];
            |  }
            |  [[ t#13 = t#12 ]];
            |  [[ t#15 = t#13 ]];
            |} else {
            |  [[ t#14 = hs(fail#7, f(6)) ]];
            |  if ([[ fail#7 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#15 = t#14 ]];
            |}
            |[[ t#15 ]];
        """.trimMargin(),
    )

    @Test
    fun labeledBreaksResolved() = assertWovenRoot(
        input = """
            |label: do {
            |  if (b(1)) {
            |    break label;
            |  }
            |  f(2);
            |}
        """.trimMargin(),
        // The numerical suffix on label shows resolution.
        want = """
            |[[ var t#3 ]];
            |[[ var t#4 ]];
            |label__0: do {
            |  [[ var fail#1 ]];
            |  [[ var fail#2 ]];
            |  [[ t#3 = hs(fail#1, b(1)) ]];
            |  if ([[ fail#1 ]]) {
            |    [[ bubble() ]];
            |  }
            |  if ([[ t#3 ]]) {
            |    break label__0;
            |  } else {}
            |  [[ t#4 = hs(fail#2, f(2)) ]];
            |  if ([[ fail#2 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#4 ]];
            |}
        """.trimMargin(),
    )

    @Test
    fun localFailsResolvedToBreaks() = assertWovenRoot(
        input = """
            |do { ff(1) } orelse do { f(2) }
        """.trimMargin(),
        want = """
            |[[ var t#3 ]];
            |[[ var t#4 ]];
            |[[ var t#5 ]];
            |[[ var t#6 ]];
            |[[ var t#7 ]];
            |[[ var fail#1 ]];
            |[[ var fail#2 ]];
            |orelse#0: do {
            |  [[ t#3 = hs(fail#1, ff(1)) ]];
            |  if ([[ fail#1 ]]) {
            |    break orelse#0;
            |  }
            |  [[ t#4 = t#3 ]];
            |  [[ t#7 = t#4 ]];
            |} orelse {
            |  [[ t#5 = hs(fail#2, f(2)) ]];
            |  if ([[ fail#2 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#6 = t#5 ]];
            |  [[ t#7 = t#6 ]];
            |}
            |[[ t#7 ]];
        """.trimMargin(),
    )

    @Test
    fun localFailsWithoutSurroundingBlocks() = assertWovenRoot(
        input = """
            |ff(1) orelse f(2)
        """.trimMargin(),
        want = """
            |[[ var t#3 ]];
            |[[ var t#4 ]];
            |[[ var t#5 ]];
            |[[ var fail#1 ]];
            |[[ var fail#2 ]];
            |orelse#0: do {
            |  [[ t#3 = hs(fail#1, ff(1)) ]];
            |  if ([[ fail#1 ]]) {
            |    break orelse#0;
            |  }
            |  [[ t#5 = t#3 ]];
            |} orelse {
            |  [[ t#4 = hs(fail#2, f(2)) ]];
            |  if ([[ fail#2 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#5 = t#4 ]];
            |}
            |[[ t#5 ]];
        """.trimMargin(),
    )

    @Test
    fun loopContainingBreakAndContinue() = assertWovenRoot(
        input = """
            |while (b(0)) {
            |  if (b(1)) {
            |    f(2);
            |    break;
            |  } else if (b(3)) {
            |    f(4);
            |    continue;
            |  } else {
            |    f(5);
            |  }
            |  f(6);
            |}
            |f(7)
        """.trimMargin(),
        want = """
            |[[ var t#10 ]];
            |[[ var t#11 ]];
            |[[ var t#12 ]];
            |[[ var t#13 ]];
            |[[ var t#14 ]];
            |[[ var fail#2 ]];
            |[[ var fail#3 ]];
            |[[ var fail#4 ]];
            |[[ var fail#5 ]];
            |[[ var fail#6 ]];
            |[[ var fail#7 ]];
            |[[ var fail#8 ]];
            |[[ var fail#9 ]];
            |for (;
            |  [[ true ]];
            |) {
            |  [[ t#10 = hs(fail#2, b(0)) ]];
            |  if ([[ fail#2 ]]) {
            |    [[ bubble() ]];
            |  }
            |  if ([[ !t#10 ]]) {
            |    break;
            |  }
            |  [[ t#11 = hs(fail#3, b(1)) ]];
            |  if ([[ fail#3 ]]) {
            |    [[ bubble() ]];
            |  }
            |  if ([[ t#11 ]]) {
            |    [[ hs(fail#4, f(2)) ]];
            |    if ([[ fail#4 ]]) {
            |      [[ bubble() ]];
            |    }
            |    break;
            |  } else {
            |    [[ t#12 = hs(fail#5, b(3)) ]];
            |    if ([[ fail#5 ]]) {
            |      [[ bubble() ]];
            |    }
            |    if ([[ t#12 ]]) {
            |      [[ hs(fail#6, f(4)) ]];
            |      if ([[ fail#6 ]]) {
            |        [[ bubble() ]];
            |      }
            |      continue;
            |    } else {
            |      [[ hs(fail#7, f(5)) ]];
            |      if ([[ fail#7 ]]) {
            |        [[ bubble() ]];
            |      }
            |    }
            |  }
            |  [[ t#13 = hs(fail#8, f(6)) ]];
            |  if ([[ fail#8 ]]) {
            |    [[ bubble() ]];
            |  }
            |  [[ t#13 ]];
            |}
            |[[ t#14 = hs(fail#9, f(7)) ]];
            |if ([[ fail#9 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ t#14 ]];
        """.trimMargin(),
    )

    @Test
    fun simpleDoWhileLoop() = assertWovenRoot(
        input = """
            |do {
            |  f(0);
            |} while (b(1));
        """.trimMargin(),
        want = """
            |[[ var t#3 ]];
            |[[ var fail#1 ]];
            |[[ var fail#2 ]];
            |do {
            |  continue#4 & continue#4: do {
            |    [[ hs(fail#2, f(0)) ]];
            |    if ([[ fail#2 ]]) {
            |      [[ bubble() ]];
            |    }
            |  }
            |  [[ t#3 = hs(fail#1, b(1)) ]];
            |  if ([[ fail#1 ]]) {
            |    [[ bubble() ]];
            |  }
            |  if ([[ !t#3 ]]) {
            |    break;
            |  }
            |} while ([[ true ]]);
        """.trimMargin(),
    )

    @Test
    fun matchNums() = assertWovenRoot(
        input = """
            |when (x) {
            |  0 -> "zero";
            |  1 -> "one";
            |  2 -> "two";
            |  else -> "many";
            |}
        """.trimMargin(),
        want = """
            |[[ var t#3 ]];
            |if ([[ x == 0 ]]) {
            |  [[ t#3 = "zero" ]];
            |} else if ([[ x == 1 ]]) {
            |  [[ t#3 = "one" ]];
            |} else if ([[ x == 2 ]]) {
            |  [[ t#3 = "two" ]];
            |} else {
            |  [[ t#3 = "many" ]];
            |}
            |[[ t#3 ]];
        """.trimMargin(),
    )

    @Test
    fun divOrElse() = assertWovenRoot(
        input = """
            |(x / y) orelse -1
        """.trimMargin(),
        want = """
            |[[ var t#2 ]];
            |[[ var t#3 ]];
            |[[ var fail#1 ]];
            |orelse#0: do {
            |  [[ t#2 = hs(fail#1, x / y) ]];
            |  if ([[ fail#1 ]]) {
            |    break orelse#0;
            |  }
            |  [[ t#3 = t#2 ]];
            |} orelse {
            |  [[ t#3 = -1 ]];
            |}
            |[[ t#3 ]];
        """.trimMargin(),
    )

    @Test
    fun ifThenOrElseAssigned() = assertWovenRoot(
        input = """
            |let s(): String { panic() }
            |
            |let x: String? = (
            |  if (b) {
            |    s()
            |  } else {
            |    s()
            |  }
            |) orelse null // This `null` was not getting assigned to the same temporary as other branches.
        """.trimMargin(),
        want = """
            |[[ var t#6 ]];
            |[[ var t#7 ]];
            |[[ @fn let s__2 ]];
            |[[ s__2 = (@stay fn s /* return__1 */: String {
            |    fn__4: do {
            |      panic()
            |    }
            |}) ]];
            |[[ let x__3: String? ]];
            |orelse#5: do {
            |  if ([[ b ]]) {
            |    [[ t#6 = (fn s)() ]];
            |  } else {
            |    [[ t#6 = (fn s)() ]];
            |  }
            |  [[ t#7 = t#6 ]];
            |} orelse {
            |  [[ t#7 = null ]];
            |}
            |[[ x__3 = t#7 ]];
        """.trimMargin(),
    )

    @Test
    fun testPositionMetadata() = assertWovenRoot(
        input = """
            |f(0);
            |if (b(1)) {
            |  f(2);
            |} // Implied empty else block here
            |f(3);
        """.trimMargin(),
        outputFormat = WovenOutputFormat.SourceSnippetPerPath,
        want = """
            |Path#0 -> Path#7, Path#1
            |test:1+0-4
            |1: f(0);
            |   ┗━━┛
            |
            |Path#1 -> Path#7, Path#2
            |test:1+4 - 2+8
            |  ┏━━━━┓
            |1:┃f(0);
            |2:┃if (b(1)) {
            |  ┗━━━━━━━┛
            |
            |Path#2 -> Path#3, Path#4
            |test:2+4-8
            |2: if (b(1)) {
            |       ┗━━┛
            |
            |Path#3 -> Path#7, Path#5
            |test:2+10 - 3+6
            |  ┏━━━━━━━━━━┓
            |2:┃if (b(1)) {
            |3:┃  f(2);
            |  ┗━━━━━┛
            |
            |Path#4 -> Path#6
            |test:4+1
            |4: } // Implied empty el
            |    ⇧
            |
            |Path#5 -> Path#6
            |test:3+6
            |3: f(2);
            |       ⇧
            |
            |Path#6 -> Path#7, Path#8
            |test:5+0-4
            |5: f(3);
            |   ┗━━┛
            |
            |Path#7
            |test:1+0
            |1: f(0);
            |   ⇧
            |
            |Path#8
            |test:5+0-4
            |5: f(3);
            |   ┗━━┛
        """.trimMargin(),
    )

    @Test
    fun returningBubblesJustBubbles() = assertWovenRoot(
        input = """
            |let x = bubble();
            |x
        """.trimMargin(),
        want = """
            |[[ let x__0 ]];
            |[[ bubble() ]];
            |[[ x__0 ]];
        """.trimMargin(),
    )

    @Test
    fun pureVirtualIsNotAResult() = assertWovenRoot(
        want = """
            |[[ pureVirtual() ]];
        """.trimMargin(),
    ) { module: Module ->
        val document = Document(module)
        module.addEnvironmentBindings(
            mapOf(
                StagingFlags.moduleResultNeeded to TBoolean.valueTrue,
            ),
        )
        module.deliverContent(
            document.treeFarm.grow(Position(module.loc, 0, 0)) {
                Call(BuiltinFuns.pureVirtualFn) {}
            },
        )
    }

    @Test
    fun awaitIsPulledToRoot() = assertWovenRoot(
        // Calls to `await` pause, so the interpreter needs `await` in predictable places.
        // Pausing is at the statement level, so the interpreter can handle:
        // - await calls, `await(p);`, that are statements in ControlFlow.Stmt entries.
        // - assignments of await calls, `t#1 = await(p);`, that are likewise statements.
        input = """
            |let p: Promise<Int> = g();
            |export let sum = await p + await p;
        """.trimMargin(),
        want = """
            |[[ var t#4 ]];
            |[[ var t#5 ]];
            |[[ var t#6 ]];
            |[[ var fail#1 ]];
            |[[ var fail#2 ]];
            |[[ var fail#3 ]];
            |[[ let p__0: Promise<Int32> ]];
            |[[ t#4 = hs(fail#1, g()) ]];
            |if ([[ fail#1 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ p__0 = t#4 ]];
            |[[ let `test//`.sum ]];
            |[[ t#5 = hs(fail#2, await p__0) ]];
            |if ([[ fail#2 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ t#6 = hs(fail#3, await p__0) ]];
            |if ([[ fail#3 ]]) {
            |  [[ bubble() ]];
            |}
            |[[ `test//`.sum = t#5 + t#6 ]];
        """.trimMargin(),
    )

    private fun assertWovenRoot(
        input: String,
        want: String,
        outputFormat: WovenOutputFormat = WovenOutputFormat.StructureEmbeddingExpressions,
    ): Unit = assertWovenRoot(
        want = want,
        outputFormat = outputFormat,
    ) { module ->
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation,
                fetchedContent = input,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
    }

    private fun assertWovenRoot(
        want: String,
        outputFormat: WovenOutputFormat = WovenOutputFormat.StructureEmbeddingExpressions,
        provisionModule: (Module) -> Unit,
    ) {
        val logSink = ListBackedLogSink()
        var stepsLeft = INTERP_STEP_LIMIT
        var wovenControlFlow: ControlFlow? = null
        var maximalPaths: MaximalPaths? = null
        val referents = mutableMapOf<Int, CollectedTokens>()
        val voidReferents = mutableSetOf<Int>()
        val module = Module(
            logSink,
            testModuleName,
            console,
            continueCondition = {
                stepsLeft-- >= 0
            },
        )
        Debug.configure(
            module,
            Console(
                console.textOutput,
                snapshotter = object : Snapshotter {
                    override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
                        AstSnapshotKey.useIfSame(key, state) { block ->
                            if (stepId == Debug.Frontend.TypeStage.AfterWeave.loggerName) {
                                val flow = (block.flow as StructuredFlow).controlFlow.deepCopy()
                                wovenControlFlow = flow
                                maximalPaths = forwardMaximalPaths(block)
                                for (childIndex in blockPartialEvaluationOrder(block)) {
                                    val child = block.child(childIndex)
                                    if (child is ValueLeaf && child.content == void) {
                                        voidReferents.add(childIndex)
                                    }
                                    referents[childIndex] = CollectedTokens.collect {
                                        child.toPseudoCode(it)
                                    }
                                }
                            }
                        }
                    }
                },
            ),
        )
        module.addEnvironmentBindings(extraEnvironmentBindings)
        provisionModule(module)
        val inputSourceText = module.sources.firstOrNull()?.fetchedContent?.toString()

        val advancer = ModuleAdvancer(logSink)
        advancer.addModule(module)
        advancer.advanceModules(stopBefore = Stage.after(Stage.Type))

        val got = if (wovenControlFlow == null) {
            toStringViaTextOutput {
                logSink.toConsole(
                    Console(it),
                    sources = if (inputSourceText != null) {
                        mapOf(
                            module.loc to
                                (inputSourceText to FilePositions.fromSource(module.loc, inputSourceText)),
                        )
                    } else {
                        mapOf()
                    },
                )
            }
        } else {
            when (outputFormat) {
                WovenOutputFormat.StructureEmbeddingExpressions ->
                    dumpStructureEmbedding(wovenControlFlow, referents, voidReferents)

                WovenOutputFormat.SourceSnippetPerPath -> dumpSourceSnippetPerPath(
                    maximalPaths!!,
                    inputSourceText!!,
                )
            }
        }

        assertEquals(want.trimEnd(), got.trimEnd())
    }
}

private const val INTERP_STEP_LIMIT = 10_000

private class PlaceholderFunction(
    override val name: String,
    override val callMayFailPerSe: Boolean,
    returnType: Type2,
    typeFormals: List<TypeFormal> = emptyList(),
) : NamedBuiltinFun {
    override val sigs = listOf(
        Signature2(
            returnType2 = if (callMayFailPerSe) {
                MkType2.result(returnType, WellKnownTypes.bubbleType2).get()
            } else {
                returnType
            },
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.intType2),
            typeFormals = typeFormals,
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult =
        when (interpMode) {
            InterpMode.Partial -> NotYet
            InterpMode.Full -> void
        }
}

private val extraEnvironmentBindings: Map<TemperName, Value<*>> = run {
    val testDocumentContext = TestDocumentContext()
    val nameMaker = ResolvedNameMaker(testDocumentContext.namingContext, Genre.Library)
    val tSymbol = Symbol("T")
    val tf = TypeFormal(
        unknownPos,
        nameMaker.unusedSourceName(ParsedName(tSymbol.text)),
        tSymbol,
        Variance.Invariant,
        AtomicCounter(),
        listOf(WellKnownTypes.anyValueType),
    )
    val t = MkType2(tf).get()
    mapOf(
        StagingFlags.skipImportImplicits to TBoolean.valueTrue,
        StagingFlags.moduleResultNeeded to TBoolean.valueTrue,
        BuiltinName("f") to Value(PlaceholderFunction("f", false, WellKnownTypes.voidType2)),
        BuiltinName("ff") to Value(PlaceholderFunction("ff", true, WellKnownTypes.voidType2)),
        BuiltinName("b") to Value(PlaceholderFunction("b", false, WellKnownTypes.booleanType2)),
        BuiltinName("bb") to Value(PlaceholderFunction("bb", true, WellKnownTypes.booleanType2)),
        BuiltinName("g") to Value(PlaceholderFunction("g", false, t, listOf(tf))),
    )
}

private enum class WovenOutputFormat {
    StructureEmbeddingExpressions,
    SourceSnippetPerPath,
}

private fun dumpStructureEmbedding(
    wovenControlFlow: ControlFlow,
    referents: Map<Int, CollectedTokens>,
    voidReferents: Set<Int>,
) = toStringViaTokenSink(singleLine = false) { sink ->
    fun renderRef(ref: BlockChildReference?) {
        sink.emit(OutputToken("[[", OutputTokenType.Punctuation))
        referents[ref?.index]?.replay(sink, skipLastLinebreak = true)
        sink.emit(OutputToken("]]", OutputTokenType.Punctuation))
    }
    fun render(cf: ControlFlow) {
        when (cf) {
            is ControlFlow.If -> {
                sink.emit(OutToks.ifWord)
                sink.emit(OutToks.leftParen)
                renderRef(cf.condition)
                sink.emit(OutToks.rightParen)
                render(cf.thenClause)
                val elseClause = cf.elseClause
                if (!elseClause.isEmptyBlock()) {
                    sink.emit(OutToks.elseWord)
                    if (elseClause.stmts.size == 1 && elseClause.stmts[0] is ControlFlow.If) {
                        render(elseClause.stmts[0])
                    } else {
                        render(elseClause)
                    }
                }
            }
            is ControlFlow.Loop -> {
                val label = cf.label
                if (label != null) {
                    sink.emit(label.toToken(inOperatorPosition = false))
                    sink.emit(OutToks.colon)
                }
                val isDoWhile = cf.checkPosition == LeftOrRight.Right
                sink.emit(
                    if (isDoWhile) {
                        OutToks.doWord
                    } else {
                        OutToks.forWord
                    },
                )
                if (!isDoWhile || !cf.increment.isEmptyBlock()) {
                    sink.emit(OutToks.leftParen)
                    sink.emit(OutToks.semi)
                    if (!isDoWhile) {
                        renderRef(cf.ref)
                    }
                    sink.emit(OutToks.semi)
                    if (!cf.increment.isEmptyBlock()) {
                        render(cf.increment)
                    }
                    sink.emit(OutToks.rightParen)
                }
                render(cf.body)
                if (isDoWhile) {
                    sink.emit(OutToks.whileWord)
                    sink.emit(OutToks.leftParen)
                    renderRef(cf.condition)
                    sink.emit(OutToks.rightParen)
                    sink.emit(OutToks.semi)
                }
            }
            is ControlFlow.Jump -> {
                val keyword = when (cf) {
                    is ControlFlow.Break -> OutToks.breakWord
                    is ControlFlow.Continue -> OutToks.continueWord
                }
                sink.emit(keyword)
                val targetToken = when (val target = cf.target) {
                    is DefaultJumpSpecifier -> null
                    is NamedJumpSpecifier ->
                        target.label.toToken(inOperatorPosition = false)
                    is UnresolvedJumpSpecifier -> {
                        val nameTok = ParsedName(target.symbol.text)
                            .toToken(inOperatorPosition = false)
                        nameTok.copy(text = "\\${nameTok.text}")
                    }
                }
                targetToken?.let { sink.emit(it) }
                sink.emit(OutToks.semi)
            }
            is ControlFlow.Labeled -> {
                sink.emit(cf.breakLabel.toToken(inOperatorPosition = false))
                val continueLabel = cf.continueLabel
                if (continueLabel != null) {
                    sink.emit(OutToks.amp)
                    sink.emit(continueLabel.toToken(inOperatorPosition = false))
                }
                sink.emit(OutToks.colon)
                sink.emit(OutToks.doWord)
                render(cf.stmts)
            }
            is ControlFlow.OrElse -> {
                render(cf.orClause)
                sink.emit(OutToks.orElseWord)
                render(cf.elseClause)
            }
            is ControlFlow.Stmt -> renderRef(cf.ref)
            is ControlFlow.StmtBlock -> {
                val wrap = cf.parent != null
                if (wrap) {
                    sink.emit(OutToks.leftCurly)
                }
                for (stmt in cf.stmts) {
                    if (stmt.ref?.index?.let { it in voidReferents } == true) {
                        continue
                    }
                    render(stmt)
                    if (stmt is ControlFlow.Stmt) {
                        sink.emit(OutToks.semi)
                    }
                }
                if (wrap) {
                    sink.emit(OutToks.rightCurly)
                }
            }
        }
    }
    render(wovenControlFlow)
}

private fun dumpSourceSnippetPerPath(
    maximalPaths: MaximalPaths,
    input: String,
): String {
    val filePositions = FilePositions.fromSource(
        maximalPaths[maximalPaths.entryPathIndex].diagnosticPosition.loc,
        input,
    )

    val pathOrder = orderedPathIndices(maximalPaths, ForwardOrBack.Back)
    return toStringViaTextOutput(isTtyLike = false) { out ->
        for (pathIndex in pathOrder) {
            val path = maximalPaths[pathIndex]
            val headerString = buildString {
                append("Path").append(path.pathIndex)
                if (path.followers.isNotEmpty()) {
                    append(" -> ")
                    path.followers.joinTo(this) {
                        "Path${it.pathIndex}"
                    }
                }
            }
            out.emitLine(headerString)
            val pos = path.diagnosticPosition
            val posPair = filePositions.filePositionAtOffset(pos.left) to
                filePositions.filePositionAtOffset(pos.right)
            out.emitLine(posPair.toReadablePosition("test"))
            excerpt(pos, input, out)
            out.endLine()
        }
    }
}
