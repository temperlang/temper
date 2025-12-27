package lang.temper.type

import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.format.toStringViaTokenSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

class StaticTypeTest {
    @Test
    fun rendering() {
        assertStringsEqual(
            "Top",
            toStringViaTokenSink { TopType.renderTo(it) },
        )
        assertStringsEqual(
            "Never",
            toStringViaTokenSink { OrType.emptyOrType.renderTo(it) },
        )
        assertStringsEqual(
            "Invalid",
            toStringViaTokenSink { InvalidType.renderTo(it) },
        )
        assertStringsEqual(
            "Bubble",
            toStringViaTokenSink { BubbleType.renderTo(it) },
        )
        TypeTestHarness(
            """
            |interface Foo<T, U>;
            |interface A;
            |interface B;
            |interface Bar;
            """.trimMargin(),
        ).run {
            assertStringsEqual(
                "Foo__0<A__3, B__4> | Bar__5",
                toStringViaTokenSink {
                    MkType.or(
                        MkType.nominal(
                            getDefinition("Foo")!!,
                            listOf(type("A"), type("B")),
                        ),
                        type("Bar"),
                    ).renderTo(it)
                },
            )
        }
        TypeTestHarness(
            """
            |interface Foo;
            |interface Bar;
            |interface Baz;
            |interface Boo;
            |interface Far;
            """.trimMargin(),
        ).run {
            assertStringsEqual(
                "Foo__0 & Bar__1 | Baz__2 & (Boo__3 | Far__4)",
                toStringViaTokenSink {
                    MkType.or(
                        MkType.and(
                            type("Foo"),
                            type("Bar"),
                        ),
                        MkType.and(
                            type("Baz"),
                            MkType.or(
                                type("Boo"),
                                type("Far"),
                            ),
                        ),
                    ).renderTo(it)
                },
            )
        }
    }

    @Test
    fun nullableTypeRendering() {
        val nullType = MkType.nominal(WellKnownTypes.nullTypeDefinition)
        assertStringsEqual(
            "Null",
            toStringViaTokenSink {
                nullType.renderTo(it)
            },
        )
        assertStringsEqual(
            "String?",
            toStringViaTokenSink {
                MkType.or(WellKnownTypes.stringType, nullType)
                    .renderTo(it)
            },
        )
        assertStringsEqual(
            "String?",
            toStringViaTokenSink {
                MkType.or(nullType, WellKnownTypes.stringType)
                    .renderTo(it)
            },
        )
        assertStringsEqual(
            "List<String?>?",
            toStringViaTokenSink {
                MkType.or(
                    MkType.nominal(
                        WellKnownTypes.listTypeDefinition,
                        listOf(
                            MkType.or(WellKnownTypes.stringType, nullType),
                        ),
                    ),
                    nullType,
                ).renderTo(it)
            },
        )
        TypeTestHarness("interface A; interface B").run {
            assertStringsEqual(
                "(A__0 & B__1)?",
                toStringViaTokenSink {
                    MkType.or(
                        MkType.and(type("A"), type("B")),
                        nullType,
                    ).renderTo(it)
                },
            )
        }
    }

    @Test
    fun nominalEqualityBasedOnDefinitionNotName() {
        val harness0 = TypeTestHarness("class T")
        val harness1 = TypeTestHarness("class T")

        val t00 = MkType.nominal(harness0.getDefinition("T")!!)
        val t01 = MkType.nominal(t00.definition)
        val t10 = MkType.nominal(harness1.getDefinition("T")!!)
        val t11 = MkType.nominal(t10.definition)

        assertEquals(t00, t00)
        assertEquals(t00, t01)
        assertEquals(t10, t10)
        assertEquals(t10, t11)

        assertNotEquals(t00, t10)
        assertNotEquals(t00, t11)
        assertNotEquals(t01, t10)
        assertNotEquals(t01, t11)
    }

    @Test
    fun parseAndRerender() = TypeTestHarness(
        """
        |class Two<T, U>;
        |interface Nominal;
        |interface A;
        |interface B;
        |interface Foo;
        |interface Bar;
        |interface Baz;
        |interface Boo;
        |interface Far;
        """.trimMargin(),
    ).run {
        val typeTextToRepresentation = listOf(
            "Top" to TopType,
            "Never" to OrType.emptyOrType,
            "Invalid" to InvalidType,

            "Two<A, B> | Bar" to """
                    ["Or", ["Nominal", "Two__0", "A__4", "B__5"], "Bar__7"]
            """,

            "Foo & Bar | Baz & (Boo | Far)" to """
                [
                     "Or",
                    [
                        "And",
                        "Foo__6",
                        "Bar__7"
                    ],
                    [
                        "And",
                        "Baz__8",
                        [
                            "Or",
                            "Boo__9",
                            "Far__10"
                        ]
                    ]
                ]
            """,
        )

        for ((sourceText, want) in typeTextToRepresentation) {
            val t = type(sourceText)
            when (want) {
                is StaticType -> assertEquals(want, t, sourceText)
                is String -> assertStructure(want, t, sourceText)
                else -> fail("Unrecognized $want for $sourceText")
            }
        }
    }
}
