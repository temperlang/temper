package lang.temper.be.tmpl

import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.lexer.Associativity
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.type.TypeTestHarness
import lang.temper.type2.Nullity
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.value.TNull
import kotlin.test.Test
import lang.temper.type.WellKnownTypes as WKT

class TmpLRenderTest {
    @Test
    fun associativity() {
        val pos = Position(testCodeLocation, 0, 0)
        val elements = listOf("a", "b", "c").map {
            TmpL.Reference(TmpL.Id(pos, BuiltinName(it)), WKT.booleanType2)
        }
        val cases = listOf(
            Triple(TmpLOperator.AmpAmp, Associativity.Left, "(a && b) && c"),
            Triple(TmpLOperator.AmpAmp, Associativity.Right, "a && b && c"),
            Triple(TmpLOperator.PlusInt, Associativity.Left, "a + b + c"),
            Triple(TmpLOperator.PlusInt, Associativity.Right, "a + (b + c)"),
        )
        for ((op, assoc, want) in cases) {
            assertStringsEqual(
                want,
                "${
                    operatorJoin(
                        TmpL.ValueReference(
                            pos,
                            WKT.anyValueType2.withNullity(Nullity.OrNull),
                            TNull.value,
                        ),
                        elements,
                        assoc,
                    ) { left, right ->
                        TmpL.InfixOperation(
                            pos,
                            left,
                            TmpL.InfixOperator(pos, op),
                            right,
                        )
                    }
                }",
            )
        }
    }

    @Test
    fun memberUse() = TypeTestHarness(
        """
            |class C {}
        """.trimMargin(),
    ).run {
        val pos = Position(testCodeLocation, 0, 0)
        val propertyUse = TmpL.GetAbstractProperty(
            pos = pos,
            subject = TmpL.Reference(
                TmpL.Id(pos, BuiltinName("subject")),
                hackMapOldStyleToNew(type("C")),
            ),
            property = TmpL.ExternalPropertyId(
                pos,
                TmpL.DotName(pos, "prop"),
            ),
            type = WKT.anyValueType2,
        )
        assertStringsEqual("subject.prop", "$propertyUse")
    }
}
