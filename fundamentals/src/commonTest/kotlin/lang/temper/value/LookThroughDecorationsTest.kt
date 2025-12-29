package lang.temper.value

import lang.temper.common.TestDocumentContext
import lang.temper.common.testCodeLocation
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class LookThroughDecorationsTest {
    companion object {
        val testPos = Position(testCodeLocation, 0, 0)
    }

    @Test
    fun unpackUnappliedAtCall() {
        val block = stubDocument().treeFarm.grow(testPos) {
            Block { // @ foo decorated
                Call {
                    Rn(ParsedName("@"))
                    Rn(ParsedName("foo"))
                    Rn(ParsedName("decorated"))
                }
            }
        }
        assertEquals(
            UnappliedDecoration("@foo", 2, emptyList()),
            unpackUnappliedDecoration(block.edge(0)),
        )
    }

    @Test
    fun unpackUnappliedAtCallWithArguments() {
        val block = stubDocument().treeFarm.grow(testPos) {
            Block { // @ foo(false) decorated
                Call {
                    Rn(ParsedName("@"))
                    Call {
                        Rn(ParsedName("foo"))
                        V(TBoolean.valueFalse)
                    }
                    Rn(ParsedName("decorated"))
                }
            }
        }
        assertEquals(
            UnappliedDecoration(
                "@foo",
                2,
                listOf(
                    block.child(0).child(1).edge(1),
                ),
            ),
            unpackUnappliedDecoration(block.edge(0)),
        )
    }

    @Test
    fun unpackUnappliedAtNameCall() {
        val block = stubDocument().treeFarm.grow(testPos) {
            Block { // @foo decorated
                Call {
                    Rn(ParsedName("@foo"))
                    Rn(ParsedName("decorated"))
                }
            }
        }
        assertEquals(
            UnappliedDecoration(
                "@foo",
                1,
                emptyList(),
            ),
            unpackUnappliedDecoration(block.edge(0)),
        )
    }

    @Test
    fun unpackUnappliedNamedFunction() {
        val env = builtinEnvironment(EmptyEnvironment, Genre.Library)
        val builtinDecorator =
            env[
                BuiltinName("@static"),
                InterpreterCallback.NullInterpreterCallback,
            ] as? Value<*>
                ?: fail("Missing builtin")
        val block = stubDocument().treeFarm.grow(testPos) {
            Block { // @static decorated
                Call {
                    V(builtinDecorator)
                    Rn(ParsedName("decorated"))
                }
            }
        }
        assertEquals(
            UnappliedDecoration(
                "@static",
                1,
                emptyList(),
            ),
            unpackUnappliedDecoration(block.edge(0)),
        )
    }
}

private fun stubDocument(): Document = Document(TestDocumentContext())
