package lang.temper.frontend.disambiguate

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.value.BlockTree
import lang.temper.value.Document
import lang.temper.value.Planting
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import lang.temper.value.vInitSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void
import kotlin.test.Test

class FlattenMultiDeclarationsTest {
    private fun assertFlattenedMultiDeclarations(
        want: String,
        makeRoot: (Planting).(Position) -> UnpositionedTreeTemplate<BlockTree>,
    ) {
        val doc = Document(TestDocumentContext())
        val pos = Position(testCodeLocation, 0, 0)
        // @decoration let foo = f(), bar = g();
        val root = doc.treeFarm.grow(pos) {
            makeRoot(pos)
        }

        flattenMultiDeclarations(root)

        assertStringsEqual(want, root.toPseudoCode(singleLine = false))
    }

    @Test
    fun undecoratedMultiDeclarations() = assertFlattenedMultiDeclarations(
        """
            |before_decls();
            |let foo;
            |var bar;
            |after_decls()
            |
        """.trimMargin(),
    ) {
        Block {
            Call { Rn(ParsedName("before_decls")) }
            // Nested declaration
            Call(BuiltinFuns.commaFn) {
                Decl(ParsedName("foo")) {}
                Decl(ParsedName("bar")) {
                    V(varSymbol)
                    V(void)
                }
            }
            Call { Rn(ParsedName("after_decls")) }
        }
    }

    @Test
    fun flattenMultiWithDecorations() = assertFlattenedMultiDeclarations(
        want = """
            |nym`@`(decoration, let foo = f());
            |nym`@`(decoration, let bar = g());
            |nym`@`(decoration, let baz: Int)
            |
        """.trimMargin(),
    ) {
        Block {
            Call {
                Rn(ParsedName("@"))
                Rn(ParsedName("decoration"))
                Call(BuiltinFuns.commaFn) {
                    Decl {
                        Ln(ParsedName("foo"))
                        V(vInitSymbol)
                        Call {
                            Rn(ParsedName("f"))
                        }
                    }
                    Decl {
                        Ln(ParsedName("bar"))
                        V(vInitSymbol)
                        Call {
                            Rn(ParsedName("g"))
                        }
                    }
                    Decl {
                        Ln(ParsedName("baz"))
                        V(vTypeSymbol)
                        Rn(ParsedName("Int"))
                    }
                }
            }
        }
    }

    @Test
    fun complexDecoratorParamsExtracted() = assertFlattenedMultiDeclarations(
        // Complex decorator parameters are pulled out into temporaries, and it works through
        // nested decorations and regardless of whether the `@` has applied or not.
        want = """
            |let t#0 = a(), t#1 = b();
            |nym`@`(decoration1(42, t#0, "Lorem Ipsum"), nym`@decoration2`(let foo = f(), t#1));
            |nym`@`(decoration1(42, t#0, "Lorem Ipsum"), nym`@decoration2`(let bar = g(), t#1));
            |nym`@`(decoration1(42, t#0, "Lorem Ipsum"), nym`@decoration2`(let baz: Int, t#1))
            |
        """.trimMargin(),
    ) {
        Block {
            Call {
                Rn(ParsedName("@"))
                Call {
                    Rn(ParsedName("decoration1"))
                    V(Value(42, TInt))
                    Call {
                        Rn(ParsedName("a"))
                    }
                    V(Value("Lorem Ipsum", TString))
                }
                Call {
                    Rn(ParsedName("@decoration2"))
                    Call(BuiltinFuns.commaFn) {
                        Decl {
                            Ln(ParsedName("foo"))
                            V(vInitSymbol)
                            Call {
                                Rn(ParsedName("f"))
                            }
                        }
                        Decl {
                            Ln(ParsedName("bar"))
                            V(vInitSymbol)
                            Call {
                                Rn(ParsedName("g"))
                            }
                        }
                        Decl {
                            Ln(ParsedName("baz"))
                            V(vTypeSymbol)
                            Rn(ParsedName("Int"))
                        }
                    }
                    Call {
                        Rn(ParsedName("b"))
                    }
                }
            }
        }
    }
}
