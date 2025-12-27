package lang.temper.type2

import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.assertStringsEqual
import lang.temper.format.join
import lang.temper.format.toStringViaTokenSink
import lang.temper.type.TypeShape
import lang.temper.type.withTypeTestHarness
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class TypeContext2Test {
    private var tc = TypeContext2()

    @AfterTest
    fun replaceTypeContext() {
        tc = TypeContext2()
    }

    @Test
    fun hackFunInterfacesEquivalent() = withTypeTestHarness(
        """
            |@fun interface StringSink(s: String): Void;
        """.trimMargin(),
    ) {
        // Temporarily treat all fun interfaces with the same signature as
        // subtypes of one another at least until we aren't doing FunctionType<->Type2
        // conversions back and forth which launder the specific interface type.
        val t1 = type2("StringSink")
        val t2 = type2("fn (String): Void")
        assertSubTypeTable(
            listOf(t1, t2),
            """
                |╔════════════════╦══════════╦════════════════╗
                |║                ║StringSink║Fn<String, Void>║
                |╠════════════════╬══════════╬════════════════╣
                |║StringSink      ║✓         ║✓               ║
                |╠════════════════╬══════════╬════════════════╣
                |║Fn<String, Void>║✓         ║✓               ║
                |╚════════════════╩══════════╩════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun subtypeNotGeneric() = withTypeTestHarness {
        val intType = type2("Int")
        val intOrNullType = type2("Int?")
        val strType = type2("String")
        val topType = type2("AnyValue?")
        val neverIntType = type2("Never<Int>")

        // (isSubType, subCandidate, superCandidate)
        val subTypeCases = listOf(
            // Reflexive
            Triple(true, neverIntType, neverIntType),
            Triple(true, topType, topType),
            // Relations between Bottom and Top
            Triple(true, neverIntType, topType),
            Triple(false, topType, neverIntType),
            // Relations involving defined types.
            Triple(true, intType, intType),
            Triple(true, strType, strType),
            Triple(false, intType, strType),
            Triple(false, strType, intType),
            Triple(true, intType, topType),
            Triple(false, topType, intType),
            Triple(true, neverIntType, intType),
            Triple(false, intType, neverIntType),
            // A type or null is a supertype of that type
            Triple(true, intType, intOrNullType),
            Triple(false, intOrNullType, intType),
            Triple(true, intOrNullType, intOrNullType),
        )

        for ((want, t, u) in subTypeCases) {
            if (want != tc.isSubType(t, u)) {
                fail("$t <: $u should be $want")
            }
        }
    }

    @Test
    fun glb() = withTypeTestHarness {
        val intType = type2("Int")
        val intOrNullType = type2("Int?")
        val strType = type2("String")
        val anyValueType = type2("AnyValue")
        val topType = type2("AnyValue?")
        val neverIntType = type2("Never<Int>")

        // (glb, t, u)
        val glbCases: List<Triple<Type2?, Type2, Type2>> = listOf(
            // Reflexive
            Triple(anyValueType, anyValueType, anyValueType),
            Triple(intType, intType, intType),
            Triple(strType, strType, strType),
            Triple(strType, type2("String"), strType),
            // Relations between Bottom and Top
            Triple(neverIntType, neverIntType, topType),
            Triple(neverIntType, topType, neverIntType),
            // Relations involving defined types.
            Triple(intType, intType, intType),
            Triple(strType, strType, strType),
            Triple(strType, strType, anyValueType),
            Triple(intType, anyValueType, intType),
            Triple(null, intType, strType),
            Triple(null, strType, intType),
            Triple(intType, intType, topType),
            Triple(intType, topType, intType),
            Triple(neverIntType, neverIntType, intType),
            Triple(neverIntType, intType, neverIntType),
            Triple(null, strType, neverIntType),
            // Or null
            Triple(intType, intType, intOrNullType),
            Triple(intType, intOrNullType, intType),
            Triple(intOrNullType, intOrNullType, intOrNullType),
        )

        val headers = listOf("GLB", "t", "u").map { MultilineOutput.of(it) }

        val wantTable = TextTable(
            headers,
            glbCases.map { (want, t, u) ->
                listOf(
                    listOfNotNull(want).asMultilineOutput,
                    t.asMultilineOutput,
                    u.asMultilineOutput,
                )
            },
        )
        var failed = false
        val gotTable = TextTable(
            headers,
            glbCases.map { (want, t, u) ->
                val got = tc.glb(t, u)
                if (want != got) { failed = true }
                listOf(
                    listOfNotNull(got).asMultilineOutput,
                    t.asMultilineOutput,
                    u.asMultilineOutput,
                )
            },
        )

        // Size columns equally to minimize diff
        val columnWidthHints = wantTable.columnWidths.zip(gotTable.columnWidths).map {
            max(it.first, it.second)
        }

        assertStringsEqual(
            wantTable.toString(columnWidthHints),
            gotTable.toString(columnWidthHints),
        )
        assertFalse(failed) // In case text is spuriously equal
    }

    @Test
    fun lubSimplify() = withTypeTestHarness(
        """
        |interface Apple<T>;
        |interface Banana;
        |interface Cherry<T> extends Apple<T>;
        |interface Durian extends Apple<Banana>;
        |interface Etrog;
        |interface Fig extends Banana & Etrog;
        |interface Grape extends Banana;
        |interface Honeydew extends Etrog & Grape;
        |interface Imbe extends Banana;
        """.trimMargin(),
    ) {
        // Sloppily run several asserts here because good enough.
        // X?
        assertEquals(
            listOf(type2("AnyValue")),
            tc.lub(type2("Durian"), type2("Null")),
        )
        // Common type parameter bindings.
        assertEquals(
            listOf(type2("Apple<Banana>")),
            tc.lub(type2("Durian"), type2("Cherry<Banana>")),
        )
        // Distinct bindings.
        assertEquals(
            listOf(type2("AnyValue")),
            tc.lub(type2("Durian"), type2("Cherry<Int>")),
        )
        // Multiple least common.
        assertEquals(
            listOf(type2("Banana"), type2("Etrog")),
            tc.lub(type2("Fig"), type2("Honeydew")),
        )
        // More than 2 types, so use leastCommonSuperTypes directly.
        assertEquals(
            setOf(type2("Banana")),
            tc.leastCommonSuperTypes(
                listOf(
                    type2("Fig"),
                    type2("Honeydew"),
                    type2("Imbe"),
                ),
            ),
        )
    }

    @Test
    fun commonSuperTypesWithTypeParameterReferences() = withTypeTestHarness(
        """
            |interface I<T extends String>;
            |interface J<U extends AnyValue>;
        """.trimMargin(),
    ) {
        val typeTFromI = MkType2(
            (type2("I").definition as TypeShape).typeParameters[0].definition,
        ).get()
        val typeUFromJ = MkType2(
            (type2("J").definition as TypeShape).typeParameters[0].definition,
        ).get()

        val cases = listOf(
            Triple(typeTFromI, typeUFromJ, type2("AnyValue")),
            Triple(typeUFromJ, typeTFromI, type2("AnyValue")),
            Triple(type2("String"), typeTFromI, type2("String")),
            Triple(typeUFromJ, type2("Int"), type2("AnyValue")),
        )

        val typeContext = TypeContext2()
        for ((a, b, common) in cases) {
            val got = typeContext.leastCommonSuperTypes(listOf(a, b))
            assertEquals(setOf(common), got, "common($a, $b)")
        }
    }

    @Test
    fun nonGenericCommonSupertype2() = withTypeTestHarness(
        //     A      E
        //    / \
        //   B   C
        //  /
        // D
        """
        |interface A;
        |interface B extends A;
        |interface C extends A;
        |interface D extends B;
        |interface E;
        """.trimMargin(),
    ) {
        val a = type2("A")
        val b = type2("B")
        val c = type2("C")
        val d = type2("D")
        val e = type2("E")

        val types = listOf(a, b, c, d, e)

        val isAPairs = listOf(
            a to a,
            b to b,
            c to c,
            d to d,
            e to e,
            b to a,
            c to a,
            d to a,
            d to b,
        )

        for (t in types) {
            for (u in types) {
                val want = (t to u) in isAPairs
                if (want != tc.isSubType(t, u)) {
                    fail("$t isSubType $u should be $want")
                }
            }
        }
    }

    @Test
    fun genericCommonSupertype2() = withTypeTestHarness(
        //     G<AnyValue>
        //    /    \
        //  G<A>  G<B>
        //  /        \
        // H          I
        """
        |interface A;
        |interface B;
        |interface G<out T>;       // covariant parameter allows G<A> <: G<AnyValue>
        |interface H extends G<A>;
        |interface I extends G<B>;
        """.trimMargin(),
    ) {
        val h = type2("H")
        val i = type2("I")
        val a = type2("A")
        val b = type2("B")
        val gOfA = type2("G<A>")
        val gOfB = type2("G<B>")
        val gAnyValue = type2("G<AnyValue>")

        val types = listOf(a, b, gOfA, gOfB, gAnyValue, h, i)

        val isAPairs = types.map { it to it } + listOf(
            gOfA to gAnyValue,
            gOfB to gAnyValue,
            h to gAnyValue,
            i to gAnyValue,
            h to gOfA,
            i to gOfB,
        )

        for (t in types) {
            for (u in types) {
                val want = (t to u) in isAPairs
                if (want != tc.isSubType(t, u)) {
                    fail("$t <: $u should be $want")
                }
            }
        }
    }

    @Test
    fun genericNominalSubtypes() = withTypeTestHarness(
        """
        |interface I<E> {}
        |class C<F> extends I<F> {}
        """.trimMargin(),
    ) {
        assertSubTypeTable(
            listOf(
                type2("I<String>"),
                type2("I<Int>"),
                type2("C<String>"),
                type2("C<Int>"),
            ),
            """
            |╔═════════╦═════════╦════════╦═════════╦════════╗
            |║         ║I<String>║I<Int32>║C<String>║C<Int32>║
            |╠═════════╬═════════╬════════╬═════════╬════════╣
            |║I<String>║✓        ║✕       ║✓        ║✕       ║
            |╠═════════╬═════════╬════════╬═════════╬════════╣
            |║I<Int32> ║✕        ║✓       ║✕        ║✓       ║
            |╠═════════╬═════════╬════════╬═════════╬════════╣
            |║C<String>║✕        ║✕       ║✓        ║✕       ║
            |╠═════════╬═════════╬════════╬═════════╬════════╣
            |║C<Int32> ║✕        ║✕       ║✕        ║✓       ║
            |╚═════════╩═════════╩════════╩═════════╩════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun covariantListSubtypes() = withTypeTestHarness {
        assertSubTypeTable(
            listOf(
                type2("List<AnyValue>"),
                type2("List<Int>"),
                type2("List<String>"),
            ),
            """
            |╔══════════════╦══════════════╦═══════════╦════════════╗
            |║              ║List<AnyValue>║List<Int32>║List<String>║
            |╠══════════════╬══════════════╬═══════════╬════════════╣
            |║List<AnyValue>║✓             ║✓          ║✓           ║
            |╠══════════════╬══════════════╬═══════════╬════════════╣
            |║List<Int32>   ║✕             ║✓          ║✕           ║
            |╠══════════════╬══════════════╬═══════════╬════════════╣
            |║List<String>  ║✕             ║✕          ║✓           ║
            |╚══════════════╩══════════════╩═══════════╩════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun invariantListBuilderSubtypes() = withTypeTestHarness {
        assertSubTypeTable(
            listOf(
                type2("ListBuilder<AnyValue>"),
                type2("ListBuilder<Int>"),
                type2("ListBuilder<String>"),
            ),
            """
            |╔═════════════════════╦═════════════════════╦══════════════════╦═══════════════════╗
            |║                     ║ListBuilder<AnyValue>║ListBuilder<Int32>║ListBuilder<String>║
            |╠═════════════════════╬═════════════════════╬══════════════════╬═══════════════════╣
            |║ListBuilder<AnyValue>║✓                    ║✕                 ║✕                  ║
            |╠═════════════════════╬═════════════════════╬══════════════════╬═══════════════════╣
            |║ListBuilder<Int32>   ║✕                    ║✓                 ║✕                  ║
            |╠═════════════════════╬═════════════════════╬══════════════════╬═══════════════════╣
            |║ListBuilder<String>  ║✕                    ║✕                 ║✓                  ║
            |╚═════════════════════╩═════════════════════╩══════════════════╩═══════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun trivialSubTypeUsingNeverForOutVarianceParameter() = withTypeTestHarness(
        """
            |interface VariantSuper<out T extends AnyValue> extends AnyValue;
            |
            |class TrivialSubType extends VariantSuper<Never<Int>>;
        """.trimMargin(),
    ) {
        assertSubTypeTable(
            listOf(
                type2("TrivialSubType"),
                type2("VariantSuper<Int>"),
            ),
            """
                |╔═══════════════════╦══════════════╦═══════════════════╗
                |║                   ║TrivialSubType║VariantSuper<Int32>║
                |╠═══════════════════╬══════════════╬═══════════════════╣
                |║TrivialSubType     ║✓             ║✕                  ║
                |╠═══════════════════╬══════════════╬═══════════════════╣
                |║VariantSuper<Int32>║✓             ║✓                  ║
                |╚═══════════════════╩══════════════╩═══════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun orNullViaBinding() = withTypeTestHarness(
        """
            |interface I<T>;
            |class OrNullI<N> extends I<N?>;
        """.trimMargin(),
    ) {
        assertSubTypeTable(
            listOf(
                type2("I<String?>"),
                type2("OrNullI<String>"),
            ),
            """
                |╔═══════════════╦══════════╦═══════════════╗
                |║               ║I<String?>║OrNullI<String>║
                |╠═══════════════╬══════════╬═══════════════╣
                |║I<String?>     ║✓         ║✓              ║
                |╠═══════════════╬══════════╬═══════════════╣
                |║OrNullI<String>║✕         ║✓              ║
                |╚═══════════════╩══════════╩═══════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun swappedBindings() = withTypeTestHarness(
        """
            |interface I<T, U>;
            |// Swap the <T, U> order to test that effect.
            |class C<T, U> extends I<U, T>;
        """.trimMargin(),
    ) {
        assertSubTypeTable(
            listOf(
                type2("I<String, Int>"),
                // I<String, Int> is a super type of C<Int, String>.
                type2("C<Int, String>"),
                // Not so for C<String, Int>.
                type2("C<String, Int>"),
            ),
            """
                |╔════════════════╦════════════════╦════════════════╦════════════════╗
                |║                ║I<String, Int32>║C<Int32, String>║C<String, Int32>║
                |╠════════════════╬════════════════╬════════════════╬════════════════╣
                |║I<String, Int32>║✓               ║✓               ║✕               ║
                |╠════════════════╬════════════════╬════════════════╬════════════════╣
                |║C<Int32, String>║✕               ║✓               ║✕               ║
                |╠════════════════╬════════════════╬════════════════╬════════════════╣
                |║C<String, Int32>║✕               ║✕               ║✓               ║
                |╚════════════════╩════════════════╩════════════════╩════════════════╝
            """.trimMargin(),
        )
    }

    private fun assertSubTypeTable(
        types: List<Type2>,
        want: String,
    ) {
        fun typeToCell(type: Type2): MultilineOutput = type.asMultilineOutput

        val table = TextTable(
            headerRow = listOf(MultilineOutput.Empty) + types.map(::typeToCell),
            otherRows = types.map { rowType ->
                listOf(typeToCell(rowType)) +
                    types.map { columnType ->
                        val text = if (tc.isSubType(columnType, rowType)) {
                            "✓"
                        } else {
                            "✕"
                        }
                        MultilineOutput.of(text)
                    }
            },
        )
        val got = table.toString()
        assertStringsEqual(want, got)
    }
}

private val Type2.asMultilineOutput get() = MultilineOutput.of(
    toStringViaTokenSink { renderTo(it) }
        .replace(trailingNumbersFixupPattern) {
            it.groups[1]!!.value
        },
)

private val List<Type2>.asMultilineOutput get() = MultilineOutput.of(
    toStringViaTokenSink { join(it) }
        .replace(trailingNumbersFixupPattern) {
            it.groups[1]!!.value
        },
)

private val trailingNumbersFixupPattern = Regex("""\b(\w+)__\d+\b""")
