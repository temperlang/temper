package lang.temper.frontend.disambiguate

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.value.BlockTree
import lang.temper.value.Document
import lang.temper.value.Planting
import lang.temper.value.TInt
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.condSymbol
import lang.temper.value.flowInitSymbol
import lang.temper.value.incrSymbol
import lang.temper.value.labelSymbol
import lang.temper.value.ofBuiltinName
import lang.temper.value.toPseudoCode
import lang.temper.value.vInitSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.void
import kotlin.test.Test

class ExtractFlowInitDeclarationsTest {
    private fun assertExtracted(
        want: String,
        makeRoot: (Planting).(Position) -> UnpositionedTreeTemplate<BlockTree>,
    ) {
        val doc = Document(TestDocumentContext())
        val pos = Position(testCodeLocation, 0, 0)
        // @decoration let foo = f(), bar = g();
        val root = doc.treeFarm.grow(pos) {
            makeRoot(pos)
        }

        val logSink = ListBackedLogSink()

        extractFlowInitDeclarations(root, logSink = logSink)

        val errors = logSink.allEntries.filter { it.level >= Log.Error }

        val got = buildString {
            append(root.toPseudoCode(singleLine = false))
            if (errors.isNotEmpty()) {
                append('\n')
                errors.forEach {
                    append("\n// ERROR: ")
                    append(it.messageText)
                }
            }
        }

        assertStringsEqual(want, got)
    }

    @Test
    fun forLoopWithOneDeclaration() = assertExtracted(
        """
            |before_loop;
            |do {
            |  let i;
            |  for(\__flowInit, {class: Empty__9}, \cond, i < 0, \incr, ++i, fn {
            |      body
            |  })
            |};
            |after_loop
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Rn(ParsedName("before_loop"))
            Call {
                Rn(ParsedName("for"))
                V(flowInitSymbol)
                Decl(ParsedName("i"))
                V(condSymbol)
                Call {
                    Rn(ParsedName("<"))
                    Rn(ParsedName("i"))
                    V(Value(0, TInt))
                }
                V(incrSymbol)
                Call {
                    Rn(ParsedName("++"))
                    Rn(ParsedName("i"))
                }
                Fn {
                    Block {
                        Rn(ParsedName("body"))
                    }
                }
            }
            Rn(ParsedName("after_loop"))
        }
    }

    @Test
    fun forLoopWithMultipleDeclarations() = assertExtracted(
        """
            |before_loop;
            |do {
            |  let i, let j;
            |  for(\__flowInit, {class: Empty__9}, \cond, i < 0, \incr, ++i, fn {
            |      body
            |  })
            |};
            |after_loop
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Rn(ParsedName("before_loop"))
            Call {
                Rn(ParsedName("for"))
                V(flowInitSymbol)
                Call(BuiltinFuns.commaFn) {
                    Decl(ParsedName("i"))
                    Decl(ParsedName("j"))
                }
                V(condSymbol)
                Call {
                    Rn(ParsedName("<"))
                    Rn(ParsedName("i"))
                    V(Value(0, TInt))
                }
                V(incrSymbol)
                Call {
                    Rn(ParsedName("++"))
                    Rn(ParsedName("i"))
                }
                Fn {
                    Block {
                        Rn(ParsedName("body"))
                    }
                }
            }
            Rn(ParsedName("after_loop"))
        }
    }

    @Test
    fun forLoopWithNoDeclaration() = assertExtracted(
        """
            |before_loop;
            |for(\__flowInit, do {
            |    i, j
            |  }, \cond, i < 0, \incr, ++i, fn {
            |    body
            |});
            |after_loop
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Rn(ParsedName("before_loop"))
            Call {
                Rn(ParsedName("for"))
                V(flowInitSymbol)
                Call(BuiltinFuns.commaFn) {
                    Rn(ParsedName("i"))
                    Rn(ParsedName("j"))
                }
                V(condSymbol)
                Call {
                    Rn(ParsedName("<"))
                    Rn(ParsedName("i"))
                    V(Value(0, TInt))
                }
                V(incrSymbol)
                Call {
                    Rn(ParsedName("++"))
                    Rn(ParsedName("i"))
                }
                Fn {
                    Block {
                        Rn(ParsedName("body"))
                    }
                }
            }
            Rn(ParsedName("after_loop"))
        }
    }

    @Test
    fun forLoopWithDecoratedDeclaration() = assertExtracted(
        """
            |before_loop;
            |do {
            |  nym`@`(foo, let i);
            |  for(\__flowInit, {class: Empty__9}, \cond, i < 0, \incr, ++i, fn {
            |      body
            |  })
            |};
            |after_loop
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Rn(ParsedName("before_loop"))
            Call {
                Rn(ParsedName("for"))
                V(flowInitSymbol)
                Call {
                    Rn(ParsedName("@"))
                    Rn(ParsedName("foo"))
                    Decl(ParsedName("i"))
                }
                V(condSymbol)
                Call {
                    Rn(ParsedName("<"))
                    Rn(ParsedName("i"))
                    V(Value(0, TInt))
                }
                V(incrSymbol)
                Call {
                    Rn(ParsedName("++"))
                    Rn(ParsedName("i"))
                }
                Fn {
                    Block {
                        Rn(ParsedName("body"))
                    }
                }
            }
            Rn(ParsedName("after_loop"))
        }
    }

    @Test
    fun labeledForLoopWithDeclaration() = assertExtracted(
        """
            |before_loop;
            |do {
            |  do {
            |    let i;
            |    do {
            |      \label;
            |      \LABEL;
            |      for(\__flowInit, {class: Empty__9}, \cond, i < 0, \incr, ++i, fn {
            |          body
            |      })
            |    }
            |  }
            |};
            |after_loop
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Rn(ParsedName("before_loop"))
            Block {
                V(labelSymbol)
                V(Symbol("LABEL"))
                Call {
                    Rn(ParsedName("for"))
                    V(flowInitSymbol)
                    Decl(ParsedName("i"))
                    V(condSymbol)
                    Call {
                        Rn(ParsedName("<"))
                        Rn(ParsedName("i"))
                        V(Value(0, TInt))
                    }
                    V(incrSymbol)
                    Call {
                        Rn(ParsedName("++"))
                        Rn(ParsedName("i"))
                    }
                    Fn {
                        Block {
                            Rn(ParsedName("body"))
                        }
                    }
                }
            }
            Rn(ParsedName("after_loop"))
        }
    }

    @Test
    fun forOfLoopWithLabel() = assertExtracted(
        """
            |\label;
            |\my_loop;
            |for({class: Empty__9} of xs, fn (x) {
            |    f(x)
            |})
            |
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            V(labelSymbol)
            V(Symbol("my_loop"))
            Call {
                Rn(ParsedName("for"))
                Call {
                    Rn(ofBuiltinName)
                    Decl {
                        Ln(ParsedName("x"))
                    }
                    Rn(ParsedName("xs"))
                }
                Fn {
                    Call {
                        Rn(ParsedName("f"))
                        Rn(ParsedName("x"))
                    }
                }
            }
        }
    }

    @Test
    fun forOfMustHaveDecl() = assertExtracted(
        // for (x of xs)
        """
            |var x;
            |for(error (ExpectedDeclarationForOf) of xs, fn {
            |    f(x)
            |})
            |
            |
            |// ERROR: Declaration required for `of`!
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Decl {
                Ln(ParsedName("x"))
                V(vVarSymbol)
                V(void)
            }
            Call {
                Rn(ParsedName("for"))
                Call {
                    Rn(ofBuiltinName)
                    Rn(ParsedName("x"))
                    Rn(ParsedName("xs"))
                }
                Fn {
                    Call {
                        Rn(ParsedName("f"))
                        Rn(ParsedName("x"))
                    }
                }
            }
        }
    }

    @Test
    fun forOfLoopWithDisallowedInit() = assertExtracted(
        """
            |for({class: Empty__9} of xs, fn (@init(error (OfDeclarationInitializerDisallowed)) x) {
            |    f(x)
            |})
            |
            |
            |// ERROR: Initializer not allowed on `of` declaration!
        """.trimMargin(),
    ) { pos ->
        Block(pos) {
            Call {
                Rn(ParsedName("for"))
                Call {
                    Rn(ofBuiltinName)
                    Decl {
                        Ln(ParsedName("x"))
                        V(vInitSymbol)
                        V(Value(123, TInt))
                    }
                    Rn(ParsedName("xs"))
                }
                Fn {
                    Call {
                        Rn(ParsedName("f"))
                        Rn(ParsedName("x"))
                    }
                }
            }
        }
    }
}
