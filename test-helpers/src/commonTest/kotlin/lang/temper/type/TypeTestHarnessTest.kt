package lang.temper.type

import lang.temper.common.assertStringsEqual
import lang.temper.common.soleMatchingOrNull
import lang.temper.format.toStringViaTokenSink
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.isAnyValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TypeTestHarnessTest {
    @Test
    fun typeHarnessTypeParsing() {
        val typeHarness = TypeTestHarness(
            """
                |interface SomeType {}
                |interface OtherType {}
                |class WithFormalParam<T> {}
                |class WithBoundedParam<T extends SomeType> {}
                |class WithSuperType extends SomeType, OtherType {}
            """.trimMargin(),
        )

        assertEquals(
            "SomeType",
            (typeHarness.getDefinition("SomeType")?.name as? ResolvedParsedName)?.baseName?.nameText,
        )

        assertEquals(
            TopType,
            typeHarness.run {
                type("Top")
            },
        )

        fun renderType(t: StaticType) = toStringViaTokenSink {
            t.renderTo(it)
        }

        assertEquals(
            "WithFormalParam__2<Int32>",
            renderType(
                typeHarness.run {
                    type("WithFormalParam<Int>")
                },
            ),
        )

        assertEquals(
            "OtherType__1 | SomeType__0 | Bubble",
            renderType(
                typeHarness.run {
                    type("OtherType | SomeType | Bubble")
                },
            ),
        )

        assertEquals(
            "OtherType__1 & SomeType__0",
            renderType(
                typeHarness.run {
                    type("OtherType & SomeType")
                },
            ),
        )

        assertTrue(
            typeHarness.run {
                (type("AnyValue") as NominalType).definition.isAnyValueType
            },
        )
    }

    @Test
    fun testFunctionType() = TypeTestHarness("").run {
        assertEquals(
            MkType.fn(
                emptyList(),
                listOf(WellKnownTypes.anyValueType),
                null,
                WellKnownTypes.stringType,
            ),
            type("fn (AnyValue): String"),
        )
        assertEquals(
            MkType.fn(
                emptyList(),
                listOf(
                    TopType,
                    MkType.nominal(WellKnownTypes.anyValueTypeDefinition),
                ),
                null,
                MkType.nominal(WellKnownTypes.voidTypeDefinition),
            ),
            type("fn (Top, AnyValue): Void"),
        )
        assertStringsEqual(
            "fn<IN__0, OUT__1>(IN__0): OUT__1",
            type("fn <IN, OUT>(IN): OUT").toString(),
        )
        assertStringsEqual(
            "fn (Int32, ...Int32): Int32",
            type("fn (Int, ...Int): Int").toString(),
        )
    }

    @Test
    fun testFunctionTypeComplexActuals() = TypeTestHarness("").run {
        assertEquals(
            MkType.fnDetails(
                typeFormals = emptyList(),
                valueFormals = listOf(
                    FunctionType.ValueFormal(Symbol("x"), WellKnownTypes.booleanType),
                    FunctionType.ValueFormal(null, WellKnownTypes.stringType, isOptional = true),
                ),
                restValuesFormal = null,
                returnType = WellKnownTypes.intType,
            ),
            type("fn (x: Boolean, _? : String): Int"),
        )
    }

    @Test
    fun orType() = TypeTestHarness("").run {
        val t = type("Int | Bubble")
        assertIs<OrType>(t)
        assertEquals(
            MkType.or(
                MkType.nominal(WellKnownTypes.intTypeDefinition),
                BubbleType,
            ),
            t,
        )
    }

    @Test
    fun throwsType() = TypeTestHarness("").run {
        val t = type("Int throws Bubble")
        assertIs<OrType>(t)
        assertEquals(
            MkType.or(
                WellKnownTypes.intType,
                BubbleType,
            ),
            t,
        )
    }

    @Test
    fun andType() = TypeTestHarness(
        """
            |interface C;
            |interface D;
        """.trimMargin(),
    ).run {
        val t = type("C & D")
        assertIs<AndType>(t)
        assertEquals(
            MkType.and(
                type("C"),
                type("D"),
            ),
            t,
        )
    }

    @Test
    fun functionInterface() = TypeTestHarness(
        "@fun interface Filter<T>(x: T): Boolean;",
    ).run {
        val t = type("Filter<String>")
        assertIs<NominalType>(t)
        assertEquals(listOf(WellKnownTypes.stringType), t.bindings)

        val defn = t.definition
        assertIs<TypeShape>(defn)
        assertEquals("Filter", defn.word?.text)

        assertContains(defn.metadata, functionalInterfaceSymbol)

        val method = defn.methods.soleMatchingOrNull { it.methodKind == MethodKind.Normal }
        assertNotNull(method)
        assertEquals(method.name.toSymbol()?.text, "apply")
        assertEquals("(T__1) -> Boolean", method.descriptor?.toString())
    }

    @Test
    fun hackNeverCompatibility() = withTypeTestHarness {
        // During the type -> type2 transition, we need to express both old-style Never
        // and new style Never<T>, Never<Void>.
        // The latter converts to the former.
        assertEquals(OrType.emptyOrType, type("Never"))
        assertEquals(
            MkType.nominal(WellKnownTypes.neverTypeDefinition, listOf(WellKnownTypes.voidType)),
            type("Never<Void>"),
        )
    }
}
