package lang.temper.frontend.syntax

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.value.Document
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import lang.temper.value.vHoistLeftSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.void
import kotlin.test.Test

class HoistLeftTest {
    @Test
    fun doNotHoistOverSimilarlyNamed() {
        val documentContext = TestDocumentContext()
        val document = Document(documentContext)
        val block = document.treeFarm.grow(Position(documentContext.loc, 0, 0)) {
            Block {
                Decl(ParsedName("foo")) {
                    V(vInitSymbol)
                    V(Value(0, TInt))
                }
                // Here are two declarations that might want to co-reference.
                Decl(ParsedName("bar")) {
                    V(vHoistLeftSymbol)
                    V(void)
                }
                Call(BuiltinFuns.setLocalFn) {
                    Ln(ParsedName("bar"))
                    Fn {
                        Block {
                            Call {
                                Rn(ParsedName("foo"))
                            }
                        }
                    }
                }
                // This declaration should not be pulled over the homonymous one
                Decl(ParsedName("foo")) {
                    V(vHoistLeftSymbol)
                    V(void)
                }
                Call(BuiltinFuns.setLocalFn) {
                    Ln(ParsedName("foo"))
                    Fn {
                        Block {
                            Call {
                                Rn(ParsedName("bar"))
                            }
                        }
                    }
                }
            }
        }
        hoistLeft(block)
        assertStringsEqual(
            """let bar, foo = 0, foo; bar = fn {foo()}; foo = fn {bar()}""",
            block.toPseudoCode(),
        )
    }

    @Test
    fun doNotHoistOverSimilarlyNamedExported() {
        val documentContext = TestDocumentContext()
        val document = Document(documentContext)
        val block = document.treeFarm.grow(Position(documentContext.loc, 0, 0)) {
            Block {
                Decl(ExportedName(documentContext.namingContext, ParsedName("foo"))) {
                    V(vInitSymbol)
                    V(Value(0, TInt))
                }
                // Here are two declarations that might want to co-reference.
                Decl(ParsedName("bar")) {
                    V(vHoistLeftSymbol)
                    V(void)
                }
                Call(BuiltinFuns.setLocalFn) {
                    Ln(ParsedName("bar"))
                    Fn {
                        Block {
                            Call {
                                Rn(ParsedName("foo"))
                            }
                        }
                    }
                }
                // This declaration should not be pulled over the homonymous one
                Decl(ParsedName("foo")) {
                    V(vHoistLeftSymbol)
                    V(void)
                }
                Call(BuiltinFuns.setLocalFn) {
                    Ln(ParsedName("foo"))
                    Fn {
                        Block {
                            Call {
                                Rn(ParsedName("bar"))
                            }
                        }
                    }
                }
            }
        }
        hoistLeft(block)
        assertStringsEqual(
            """let bar, `test//`.foo = 0, foo; bar = fn {foo()}; foo = fn {bar()}""",
            block.toPseudoCode(),
        )
    }
}
