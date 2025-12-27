package lang.temper.builtin

import lang.temper.common.assertStringsEqual
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.withTypeTestHarness
import lang.temper.type2.MkType2
import lang.temper.value.ReifiedType
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class ReifiedTypesTest {
    @Test
    fun listDoesntHaveElementTypeArgument() {
        assertStringsEqual(
            "List",
            Types.list.type.toString().replace(
                Regex("""__\d+\b"""),
                "",
            ),
        )
    }

    @Test
    fun mapKeyValuePredicateAcceptsInt() {
        val mapKeyType = ReifiedType(WellKnownTypes.mapKeyType2)
        assertTrue(mapKeyType.valuePredicate(Value(0, TInt)))
    }

    @Test
    fun listValuePredicateAcceptsListOfStrings() {
        val listType = ReifiedType(
            MkType2(WellKnownTypes.listTypeDefinition)
                .get(),
        )
        // Incomplete types might end up being evaluated at runtime for `as` and `is` calls.
        assertTrue(
            listType.valuePredicate(
                Value(listOf(Value("a", TString)), TList),
            ),
        )
    }

    @Test
    fun listMapsPredicate() {
        val listType = ReifiedType(
            MkType2(WellKnownTypes.listTypeDefinition).actuals(listOf(WellKnownTypes.intType2)).get(),
        )
        val listedType = ReifiedType(
            MkType2(WellKnownTypes.listedTypeDefinition).actuals(listOf(WellKnownTypes.intType2)).get(),
        )

        val listValue = Value(listOf(Value(0, TInt)), TList)

        assertTrue(listType.valuePredicate(listValue))
        assertTrue(listedType.valuePredicate(listValue))
    }

    @Test
    fun functionalInterfaceAllowsFunctionValue() = withTypeTestHarness(
        """
            |@fun interface UnaryBoolOp(x: Boolean): Boolean;
        """.trimMargin(),
    ) {
        val unaryBoolOpType = MkType2(getDefinition("UnaryBoolOp") as TypeShape).get()
        val reifiedType = ReifiedType(unaryBoolOpType)
        assertTrue(reifiedType.valuePredicate(BuiltinFuns.vNotFn))
    }
}
