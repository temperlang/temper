package lang.temper.be.tmpl

import lang.temper.builtin.Assign
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.format.toStringViaTokenSink
import lang.temper.interp.docgenalts.AltIfFn
import lang.temper.interp.docgenalts.AltReturnFn
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.type.MkType
import lang.temper.type.TypeTestHarness
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.BreakOrContinue
import lang.temper.value.CallTypeInferences
import lang.temper.value.Document
import lang.temper.value.JumpSpecifier
import lang.temper.value.Planting
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.vTypeSymbol
import kotlin.test.Test

class TmpLControlFlowTest {
    internal class TestGoalTranslator(
        override val supportNetwork: SupportNetwork,
        override val cfOptions: CfOptions,
        override val genre: Genre,
        override val bodyFor: BodyFor = BodyForModule,
    ) : GoalTranslator {

        override val translator: TmpLTranslator
            get() = TODO("Not yet implemented")

        override fun translateJump(p: Position, kind: BreakOrContinue, target: JumpSpecifier): Stmt {
            TODO("Not yet implemented")
        }

        override fun translateFreeFailure(p: Position): Stmt {
            TODO("Not yet implemented")
        }

        override fun translateExit(p: Position): Stmt {
            TODO("Not yet implemented")
        }
    }

    private fun assertFlow(
        want: String,
        expectedErrors: List<String> = emptyList(),
        nrbStrategy: BubbleBranchStrategy = BubbleBranchStrategy.Results,
        representationOfVoid: RepresentationOfVoid = RepresentationOfVoid.ReifyVoid,
        genre: Genre = Genre.Library,
        bodyFor: BodyFor = BodyForModule,
        makeBlock: (Planting).(ResolvedNameMaker) -> UnpositionedTreeTemplate<BlockTree>,
    ) {
        val doc = Document(TestDocumentContext())
        doc.markNamesResolved()
        val nameMaker = doc.nameMaker as ResolvedNameMaker

        val cfOptions = CfOptions(nrbStrategy, representationOfVoid)

        val testGoalTranslator = TestGoalTranslator(
            supportNetwork = TestSupportNetwork(
                bubbleStrategy = nrbStrategy,
                coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
                representationOfVoid = representationOfVoid,
            ),
            cfOptions = cfOptions,
            genre = genre,
            bodyFor = bodyFor,
        )

        val loc = doc.context.namingContext.loc
        val block = doc.treeFarm.grow(Position(loc, 0, 0)) {
            this.makeBlock(nameMaker)
        }

        val logSink = ListBackedLogSink()

        val flow = translateFlow(block, testGoalTranslator, nameMaker, cfOptions, outputName = null)
        val got = toStringViaTokenSink(singleLine = false) { flow.diagnosticToTokenSink(it) }

        assertStringsEqual(
            expectedErrors.joinToString("\n"),
            logSink.allEntries
                .filter { it.level >= Log.Error }
                .joinToString("\n") { it.messageText },
        )
        assertStringsEqual(want.trimEnd(), got.trimEnd())
    }

    @Test
    fun groupDeclarationsWithInitializers() = assertFlow(
        want = (
            """
                |{
                |  CombinedDeclaration {
                |    let t#1;
                |    = 123
                |  };
                |  CombinedDeclaration {
                |    let t#2;
                |    = t#1
                |  };
                |  CombinedDeclaration {
                |    let A__0;
                |    = t#2
                |  };
                |}
            """.trimMargin()
            ),
    ) { nameMaker ->
        val a = nameMaker.unusedSourceName(ParsedName("A"))
        val t1 = nameMaker.unusedTemporaryName("t")
        val t2 = nameMaker.unusedTemporaryName("t")
        Block {
            Decl(t1) {}
            Decl(t2) {}
            Decl(a) {} // This declaration should slide forward to group with its initializer
            Call(BuiltinFuns.setLocalFn) {
                Ln(t1)
                V(Value(123, TInt))
            }
            Call(BuiltinFuns.setLocalFn) {
                Ln(t2)
                Rn(t1)
            }
            Call(BuiltinFuns.setLocalFn) {
                Ln(a)
                Rn(t2)
            }
        }
    }

    @Test
    fun leaveHandlerScopeCallsAtBlockLevel() = assertFlow(
        want = (
            """
                |{
                |  CombinedDeclaration {
                |    let t#1;
                |    = 123
                |  };
                |  CombinedDeclaration {
                |    let t#2;
                |    = f()
                |  };
                |  CombinedDeclaration {
                |    let A__0;
                |    = t#2
                |  };
                |}
            """.trimMargin()
            ),
    ) { nameMaker ->
        val a = nameMaker.unusedSourceName(ParsedName("A"))
        val t1 = nameMaker.unusedTemporaryName("t")
        val t2 = nameMaker.unusedTemporaryName("t")
        TypeTestHarness("").run {
            val intType = WellKnownTypes.intType
            val noneToInt = MkType.fn(listOf(), listOf(), null, intType)
            Block {
                Decl { Ln(t1, intType) }
                Decl { Ln(t2, intType) }
                Decl { Ln(a, intType) } // This declaration should slide forward to group with its initializer
                Assign(t1, intType) {
                    V(Value(123, TInt), intType)
                }
                Assign(t2, intType) {
                    Call(type = CallTypeInferences(intType, noneToInt, mapOf(), listOf())) {
                        Rn(BuiltinName("f"), noneToInt)
                    }
                }
                Call(BuiltinFuns.setLocalFn) {
                    Ln(a, intType)
                    Rn(t2, intType)
                }
            }
        }
    }

    @Test
    fun typedDeclarationCombined() = assertFlow(
        want = """
            |{
            |  CombinedDeclaration {
            |    let x__0: Int32;
            |    = 123
            |  };
            |}
        """.trimMargin(),
    ) { nameMaker ->
        val x = nameMaker.unusedSourceName(ParsedName("x"))
        Block {
            Decl(x) {
                V(vTypeSymbol)
                V(Types.vInt)
            }
            Call(BuiltinFuns.setLocalFn) {
                Ln(x)
                V(Value(123, TInt))
            }
        }
    }

    @Test
    fun docGenAltControlFlow() = assertFlow(
        want = """
            |{
            |  if (cond__0) {
            |    {
            |      f__1();
            |      return g__2();
            |    }
            |  } else {
            |    {}
            |  };
            |  return null;
            |}
        """.trimMargin(),
        genre = Genre.Documentation,
        bodyFor = BodyForFun(
            Signature2(
                MkType2(WellKnownTypes.intTypeDefinition).canBeNull().get(),
                false, listOf(),
            ),
        ),
    ) { nameMaker ->
        val (cond, f, g) = listOf("cond", "f", "g").map { nameMaker.unusedSourceName(ParsedName(it)) }
        Block {
            Call(AltIfFn) {
                Rn(cond)
                Block {
                    Call {
                        Rn(f)
                    }
                    Call(AltReturnFn) {
                        Call {
                            Rn(g)
                        }
                    }
                }
            }

            Call(AltReturnFn) {
                V(TNull.value)
            }
        }
    }
}
