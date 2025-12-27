package lang.temper.type

import lang.temper.common.AtomicCounter
import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class TypeContextTest {

    private var tc = TypeContext()

    @AfterTest
    fun replaceTypeContext() {
        tc = TypeContext()
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
        val t1 = type("StringSink")
        val t2 = type("fn (String): Void")
        assertSubTypeTable(
            listOf(t1, t2),
            """
                |╔═════════════════╦══════════╦═════════════════╗
                |║                 ║StringSink║fn (String): Void║
                |╠═════════════════╬══════════╬═════════════════╣
                |║StringSink       ║✓         ║✓                ║
                |╠═════════════════╬══════════╬═════════════════╣
                |║fn (String): Void║✓         ║✓                ║
                |╚═════════════════╩══════════╩═════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun subtypeNotGeneric() = TypeTestHarness("").run {
        val intType = type("Int")
        val strType = type("String")
        val anyThrowsBubble = type("AnyValue throws Bubble")
        val neverType = OrType.emptyOrType

        // (isSubType, subCandidate, superCandidate)
        val subTypeCases = listOf(
            // Reflexive
            Triple(true, InvalidType, InvalidType),
            Triple(true, neverType, neverType),
            Triple(true, TopType, TopType),
            Triple(true, BubbleType, BubbleType),
            // InvalidType is disjoint from other types
            Triple(false, neverType, InvalidType),
            Triple(false, InvalidType, neverType),
            Triple(false, TopType, InvalidType),
            Triple(false, InvalidType, TopType),
            Triple(false, BubbleType, InvalidType),
            Triple(false, InvalidType, BubbleType),
            // Relations between Bottom and Top
            Triple(true, neverType, TopType),
            Triple(false, TopType, neverType),
            // Relations between Bubble and Bottom/Top
            Triple(true, BubbleType, TopType),
            Triple(false, TopType, BubbleType),
            Triple(false, BubbleType, neverType),
            Triple(true, neverType, BubbleType),
            // Relations involving defined types.
            Triple(true, intType, intType),
            Triple(true, strType, strType),
            Triple(false, intType, strType),
            Triple(false, strType, intType),
            Triple(true, intType, TopType),
            Triple(false, TopType, intType),
            Triple(true, neverType, intType),
            Triple(false, intType, neverType),
            // Bubble is disjoint from nominal types
            Triple(false, intType, BubbleType),
            Triple(false, strType, BubbleType),
            Triple(false, BubbleType, intType),
            Triple(false, BubbleType, strType),
            // Top is equivalent to (AnyValue throws Bubble)
            Triple(true, TopType, anyThrowsBubble),
            Triple(true, anyThrowsBubble, TopType),
        )

        for ((want, t, u) in subTypeCases) {
            if (want != tc.isSubType(t, u)) {
                fail("$t isSubType $u should be $want")
            }
        }
    }

    @Test
    fun glb() = TypeTestHarness("").run {
        val intType = type("Int")
        val strType = type("String")
        val anyValueType = type("AnyValue")
        val aFnType = type("fn (): Void")
        val functionType = type("Function")
        val neverType = OrType.emptyOrType

        // (glb, t, u)
        val glbCases = listOf(
            // Reflexive
            Triple(InvalidType, InvalidType, InvalidType),
            Triple(neverType, neverType, neverType),
            Triple(TopType, TopType, TopType),
            Triple(BubbleType, BubbleType, BubbleType),
            Triple(anyValueType, anyValueType, anyValueType),
            Triple(functionType, functionType, functionType),
            Triple(aFnType, aFnType, aFnType),
            // InvalidType poisons
            Triple(InvalidType, neverType, InvalidType),
            Triple(InvalidType, InvalidType, neverType),
            Triple(InvalidType, TopType, InvalidType),
            Triple(InvalidType, InvalidType, TopType),
            Triple(InvalidType, BubbleType, InvalidType),
            Triple(InvalidType, InvalidType, BubbleType),
            Triple(InvalidType, InvalidType, functionType),
            Triple(InvalidType, InvalidType, aFnType),
            Triple(InvalidType, anyValueType, InvalidType),
            // Relations between Bottom and Top
            Triple(neverType, neverType, TopType),
            Triple(neverType, TopType, neverType),
            // Relations between Bubble and Bottom/Top
            Triple(BubbleType, BubbleType, TopType),
            Triple(BubbleType, TopType, BubbleType),
            Triple(neverType, BubbleType, neverType),
            Triple(neverType, neverType, BubbleType),
            // Relations involving defined types.
            Triple(intType, intType, intType),
            Triple(strType, strType, strType),
            Triple(strType, strType, anyValueType),
            Triple(intType, anyValueType, intType),
            Triple(neverType, intType, strType),
            Triple(neverType, strType, intType),
            Triple(intType, intType, TopType),
            Triple(intType, TopType, intType),
            Triple(neverType, neverType, intType),
            Triple(neverType, intType, neverType),
            // Bubble is disjoint from nominal types and function types
            Triple(neverType, intType, BubbleType),
            Triple(neverType, strType, BubbleType),
            Triple(neverType, BubbleType, intType),
            Triple(neverType, BubbleType, strType),
            Triple(neverType, BubbleType, anyValueType),
            Triple(neverType, anyValueType, BubbleType),
            Triple(neverType, BubbleType, aFnType),
            Triple(neverType, aFnType, BubbleType),
        )

        val headers = listOf("GLB", "t", "u").map { MultilineOutput.of(it) }

        val wantTable = TextTable(
            headers,
            glbCases.map { (want, t, u) ->
                listOf(
                    want.asMultilineOutput,
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
                    got.asMultilineOutput,
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
    fun glbOfUnions() = TypeTestHarness("").run {
        assertEquals(
            type("Int throws Bubble"),
            tc.glb(type("Int throws Bubble"), type("AnyValue throws Bubble")),
        )
    }

    @Test
    fun lubSimplify() = TypeTestHarness(
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
    ).run {
        // Sloppily run several asserts here because good enough.
        // X?
        assertEquals(
            type("AnyValue"),
            tc.lub(type("Durian"), type("Null"), simplify = true),
        )
        // Common type parameter bindings.
        assertEquals(
            type("Apple<Banana>"),
            tc.lub(type("Durian"), type("Cherry<Banana>"), simplify = true),
        )
        // Distinct bindings.
        assertEquals(
            type("AnyValue"),
            tc.lub(type("Durian"), type("Cherry<Int>"), simplify = true),
        )
        // Multiple least common.
        assertEquals(
            type("Banana & Etrog"),
            tc.lub(type("Fig"), type("Honeydew"), simplify = true),
        )
        // More than 2 nominal types, so use leastCommonSuperType directly.
        assertEquals(
            type("Banana"),
            tc.leastCommonSuperType(
                listOf(
                    type("Fig") as NominalType,
                    type("Honeydew") as NominalType,
                    type("Imbe") as NominalType,
                ),
            ),
        )
    }

    @Test
    fun nonGenericCommonSuperType() = TypeTestHarness(
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
    ).run {
        val a = type("A")
        val b = type("B")
        val c = type("C")
        val d = type("D")
        val e = type("E")

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
    fun genericCommonSuperType() = TypeTestHarness(
        //     G<*>
        //    /    \
        //  G<A>  G<B>
        //  /        \
        // H          I
        """
        |interface A;
        |interface B;
        |interface G<T>;
        |interface H extends G<A>;
        |interface I extends G<B>;
        """.trimMargin(),
    ).run {
        val h = type("H")
        val i = type("I")
        val a = type("A")
        val b = type("B")
        val gOfA = type("G<A>")
        val gOfB = type("G<B>")
        val gStar = type("G<*>")

        val types = listOf(a, b, gOfA, gOfB, gStar, h, i)

        val isAPairs = types.map { it to it } + listOf(
            gOfA to gStar,
            gOfB to gStar,
            h to gStar,
            i to gStar,
            h to gOfA,
            i to gOfB,
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
    fun recursiveGenericType() {
        val testPos = Position(testCodeLocation, 0, 0)
        val nameMaker = ResolvedNameMaker(
            object : NamingContext() {
                override val loc = testModuleName
            },
            Genre.Library,
        )
        val mutCount = AtomicCounter()

        // An example of an infinite type definition is:
        //     Comparable<T extends Comparable<T>>
        val comparableDefinition = TypeShapeImpl(
            testPos,
            Symbol("Comparable"),
            nameMaker,
            Abstractness.Abstract,
            mutCount,
        )
        // Use indirection so T can appear in its own upper bound.
        val tBinding = InfiniBinding()
        val tDefinition = TypeFormal(
            testPos,
            nameMaker.unusedSourceName(ParsedName("T")),
            Symbol("T"),
            Variance.Covariant, // extends
            mutCount,
            upperBounds = listOf(MkType.nominal(comparableDefinition, listOf(tBinding))),
        )
        tBinding.set(MkType.nominal(tDefinition))
        comparableDefinition.typeParameters.add(
            TypeParameterShape(comparableDefinition, tDefinition, tDefinition.word!!, null),
        )

        // class Int extends Comparable<Int>
        val intDefinition = TypeShapeImpl(
            testPos,
            Symbol("Int"),
            nameMaker,
            Abstractness.Concrete,
            mutCount,
        )
        intDefinition.superTypes.add(
            MkType.nominal(comparableDefinition, listOf(MkType.nominal(intDefinition))),
        )

        // class Boolean extends Comparable<Boolean>
        val booleanDefinition = TypeShapeImpl(
            testPos,
            Symbol("Boolean"),
            nameMaker,
            Abstractness.Concrete,
            mutCount,
        )
        booleanDefinition.superTypes.add(
            MkType.nominal(comparableDefinition, listOf(MkType.nominal(booleanDefinition))),
        )

        val tInt = MkType.nominal(intDefinition)
        val tBoolean = MkType.nominal(booleanDefinition)
        val comparableOfInt = MkType.nominal(comparableDefinition, listOf(tInt))
        val comparableOfBoolean = MkType.nominal(comparableDefinition, listOf(tBoolean))
        val comparableStar = MkType.nominal(comparableDefinition, listOf(Wildcard))

        val types = listOf(
            comparableOfInt,
            comparableOfBoolean,
            comparableStar,
            tInt,
            tBoolean,
        )

        val isAPairs = types.map { it to it } + listOf(
            comparableOfInt to comparableStar,
            comparableOfBoolean to comparableStar,
            tInt to comparableOfInt,
            tInt to comparableStar,
            tBoolean to comparableOfBoolean,
            tBoolean to comparableStar,
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

    /**
     * ✓ below when the top is a sub-type of the left.
     *
     * |                    | `fn (Int): T` | `fn (String): T` | `fn (AnyValue): T` |
     * | ------------------ | ------------- | ---------------- | ------------------ |
     * | `fn (Int):      T` | ✓             | ✕                | ✓                  |
     * | `fn (String):   T` | ✕             | ✓                | ✓                  |
     * | `fn (AnyValue): T` | ✕             | ✕                | ✓                  |
     *
     * A sub-type can be assigned to a cell that allows the super-type.
     *
     *      var f: fn (Int):      Void = fn (x: Int):      Void {};
     *      var g: fn (AnyValue): Void = fn (x: AnyValue): Void {};
     *
     *      // Since a function that can take any value can also accept any Int argument, the below
     *      // is allowed.
     *      f = g; // ✓
     *      // fn (AnyValue): Void   is a sub-type of   fn (Int): Void
     *
     *      // But a function that only accepts Ints cannot handle everything passed as an AnyValue,
     *      // so the below is prohibited.
     *      g = f; // ✕
     *      // fn (Int): Void    is not a sub-type of   fn (AnyValue): Void
     */
    @Test
    fun fnTypesDifferByParameter() {
        val anyValueType = MkType.nominal(WellKnownTypes.anyValueTypeDefinition)
        val intType = MkType.nominal(WellKnownTypes.intTypeDefinition)
        val stringType = MkType.nominal(WellKnownTypes.stringTypeDefinition)
        val voidType = MkType.nominal(WellKnownTypes.voidTypeDefinition)

        assertSubTypeTable(
            listOf(
                MkType.fn(emptyList(), listOf(intType), null, voidType),
                MkType.fn(emptyList(), listOf(stringType), null, voidType),
                MkType.fn(emptyList(), listOf(anyValueType), null, voidType),
            ),
            """
            |╔═══════════════════╦════════════════╦═════════════════╦═══════════════════╗
            |║                   ║fn (Int32): Void║fn (String): Void║fn (AnyValue): Void║
            |╠═══════════════════╬════════════════╬═════════════════╬═══════════════════╣
            |║fn (Int32): Void   ║✓               ║✕                ║✓                  ║
            |╠═══════════════════╬════════════════╬═════════════════╬═══════════════════╣
            |║fn (String): Void  ║✕               ║✓                ║✓                  ║
            |╠═══════════════════╬════════════════╬═════════════════╬═══════════════════╣
            |║fn (AnyValue): Void║✕               ║✕                ║✓                  ║
            |╚═══════════════════╩════════════════╩═════════════════╩═══════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun fnTypesDifferByRestType() {
        val anyValueType = MkType.nominal(WellKnownTypes.anyValueTypeDefinition)
        val functionType = MkType.nominal(WellKnownTypes.functionTypeDefinition)
        val intType = MkType.nominal(WellKnownTypes.intTypeDefinition)
        val stringType = MkType.nominal(WellKnownTypes.stringTypeDefinition)
        val voidType = MkType.nominal(WellKnownTypes.voidTypeDefinition)

        @Suppress("LongLine") // Table
        assertSubTypeTable(
            listOf(
                MkType.fn(emptyList(), emptyList(), intType, voidType),
                MkType.fn(emptyList(), emptyList(), stringType, voidType),
                MkType.fn(emptyList(), emptyList(), anyValueType, voidType),
                functionType,
            ),
            """
            |╔══════════════════════╦═══════════════════╦════════════════════╦══════════════════════╦════════╗
            |║                      ║fn (...Int32): Void║fn (...String): Void║fn (...AnyValue): Void║Function║
            |╠══════════════════════╬═══════════════════╬════════════════════╬══════════════════════╬════════╣
            |║fn (...Int32): Void   ║✓                  ║✕                   ║✓                     ║✕       ║
            |╠══════════════════════╬═══════════════════╬════════════════════╬══════════════════════╬════════╣
            |║fn (...String): Void  ║✕                  ║✓                   ║✓                     ║✕       ║
            |╠══════════════════════╬═══════════════════╬════════════════════╬══════════════════════╬════════╣
            |║fn (...AnyValue): Void║✕                  ║✕                   ║✓                     ║✕       ║
            |╠══════════════════════╬═══════════════════╬════════════════════╬══════════════════════╬════════╣
            |║Function              ║✓                  ║✓                   ║✓                     ║✓       ║
            |╚══════════════════════╩═══════════════════╩════════════════════╩══════════════════════╩════════╝
            """.trimMargin(),
        )
    }

    /**
     * ✓ below when the top is a sub-type of the left.
     *
     * |                    | `fn (I): Int` | `fn (I): String` | `fn (I): AnyValue` |
     * | ------------------ | ------------- | ---------------- | ------------------ |
     * | `fn (I): Int)`     | ✓             | ✕                | ✕                  |
     * | `fn (I): String`   | ✕             | ✓                | ✕                  |
     * | `fn (I): AnyValue` | ✓             | ✓                | ✓                  |
     *
     * A sub-type can be assigned to a cell that allows the super-type.
     *
     *      var f: fn (I):      Int = fn (x: I):      Int { 0 };
     *      var g: fn (I): AnyValue = fn (x: I): AnyValue { x };
     *
     *      // Since the result of a function that only ever returns an Int will fit anywhere
     *      // the caller is prepared for any value, the below is allowed.
     *      g = f; // ✓
     *      // fn (I): Int            is a sub-type of   fn (I): AnyValue
     *
     *      // But a function that can return any value cannot be relied upon to produce an Int, so
     *      // the below is prohibited.
     *      f = g; // ✕
     *      // fn (I): AnyValue   is not a sub-type of   fn (I): Int
     */
    @Test
    fun fnTypesDifferByReturnType() {
        val anyValueType = MkType.nominal(WellKnownTypes.anyValueTypeDefinition)
        val functionType = MkType.nominal(WellKnownTypes.functionTypeDefinition)
        val intType = MkType.nominal(WellKnownTypes.intTypeDefinition)
        val stringType = MkType.nominal(WellKnownTypes.stringTypeDefinition)

        @Suppress("LongLine") // Table
        assertSubTypeTable(
            listOf(
                MkType.fn(emptyList(), listOf(intType), null, intType),
                MkType.fn(emptyList(), listOf(intType), null, stringType),
                MkType.fn(emptyList(), listOf(intType), null, anyValueType),
                functionType,
            ),
            """
            |╔════════════════════╦═════════════════╦══════════════════╦════════════════════╦════════╗
            |║                    ║fn (Int32): Int32║fn (Int32): String║fn (Int32): AnyValue║Function║
            |╠════════════════════╬═════════════════╬══════════════════╬════════════════════╬════════╣
            |║fn (Int32): Int32   ║✓                ║✕                 ║✕                   ║✕       ║
            |╠════════════════════╬═════════════════╬══════════════════╬════════════════════╬════════╣
            |║fn (Int32): String  ║✕                ║✓                 ║✕                   ║✕       ║
            |╠════════════════════╬═════════════════╬══════════════════╬════════════════════╬════════╣
            |║fn (Int32): AnyValue║✓                ║✓                 ║✓                   ║✕       ║
            |╠════════════════════╬═════════════════╬══════════════════╬════════════════════╬════════╣
            |║Function            ║✓                ║✓                 ║✓                   ║✓       ║
            |╚════════════════════╩═════════════════╩══════════════════╩════════════════════╩════════╝
            """.trimMargin(),
        )
    }

    @Ignore
    @Test
    fun fnTypesVsUnion() = TypeTestHarness(
        """
        |interface A;
        |interface B;
        |interface C;
        """.trimMargin(),
    ).run {
        val aType = type("A")
        val bType = type("B")
        val cType = type("C")

        val aToC = MkType.fn(emptyList(), listOf(aType), null, cType)
        val bToC = MkType.fn(emptyList(), listOf(bType), null, cType)
        val unionOfFunctionTypes = MkType.or(aToC, bToC)
        val functionTypeOfUnions = MkType.fn(emptyList(), listOf(MkType.or(aType, bType)), null, cType)

        assertSubTypeTable(
            listOf(
                aToC,
                bToC,
                unionOfFunctionTypes,
                functionTypeOfUnions,
            ),
            """
            |╔═════════════════════╦═════════╦═════════╦═════════════════════╦═════════════╗
            |║                     ║fn (A): C║fn (B): C║fn (A): C | fn (B): C║fn (A | B): C║
            |╠═════════════════════╬═════════╬═════════╬═════════════════════╬═════════════╣
            |║fn (A): C            ║✓        ║✕        ║✕                    ║✓            ║
            |╠═════════════════════╬═════════╬═════════╬═════════════════════╬═════════════╣
            |║fn (B): C            ║✕        ║✓        ║✕                    ║✓            ║
            |╠═════════════════════╬═════════╬═════════╬═════════════════════╬═════════════╣
            |║fn (A): C | fn (B): C║✓        ║✓        ║✓                    ║✓            ║
            |╠═════════════════════╬═════════╬═════════╬═════════════════════╬═════════════╣
            |║fn (A | B): C        ║✕        ║✕        ║✕                    ║✓            ║
            |╚═════════════════════╩═════════╩═════════╩═════════════════════╩═════════════╝
            """.trimMargin(),
            // `fn (A | B): C` is a subtype of the union of functions because it's a sub-type of
            // at least one member.  It is actually a sub-type of both, but it needs be of only one.
        )
    }

    @Test
    fun genericFnSubtype() = TypeTestHarness("").run {
        val tToListT = type("fn<T>(T): List<T>") as FunctionType
        val uToInt = type("fn<U>(U): Int")
        val vToListV = type("fn<V>(V): List<V>")
        val tToListInt = type("fn<T>(T): List<Int>")

        assertSubTypeTable(
            listOf(tToListT, uToInt, vToListV, tToListInt),
            """
            |╔═════════════════════╦═════════════════╦═══════════════╦═════════════════╦═════════════════════╗
            |║                     ║fn<T>(T): List<T>║fn<U>(U): Int32║fn<V>(V): List<V>║fn<T>(T): List<Int32>║
            |╠═════════════════════╬═════════════════╬═══════════════╬═════════════════╬═════════════════════╣
            |║fn<T>(T): List<T>    ║✓                ║✕              ║✓                ║✕                    ║
            |╠═════════════════════╬═════════════════╬═══════════════╬═════════════════╬═════════════════════╣
            |║fn<U>(U): Int32      ║✕                ║✓              ║✕                ║✕                    ║
            |╠═════════════════════╬═════════════════╬═══════════════╬═════════════════╬═════════════════════╣
            |║fn<V>(V): List<V>    ║✓                ║✕              ║✓                ║✕                    ║
            |╠═════════════════════╬═════════════════╬═══════════════╬═════════════════╬═════════════════════╣
            |║fn<T>(T): List<Int32>║✕                ║✕              ║✕                ║✓                    ║
            |╚═════════════════════╩═════════════════╩═══════════════╩═════════════════╩═════════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun genericNominalSubtypes() = TypeTestHarness(
        """
        |interface I<E> {}
        |class C<F> extends I<F> {}
        """.trimMargin(),
    ).run {
        assertSubTypeTable(
            listOf(
                type("I<String>"),
                type("I<Int>"),
                type("C<String>"),
                type("C<Int>"),
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
    fun covariantListSubtypes() = TypeTestHarness("").run {
        assertSubTypeTable(
            listOf(
                type("List<AnyValue>"),
                type("List<Int>"),
                type("List<String>"),
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
    fun invariantListBuilderSubtypes() = TypeTestHarness("").run {
        assertSubTypeTable(
            listOf(
                type("ListBuilder<AnyValue>"),
                type("ListBuilder<Int>"),
                type("ListBuilder<String>"),
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
    fun trivialSubTypeUsingNeverForOutVarianceParameter() = TypeTestHarness(
        """
            |interface VariantSuper<out T extends AnyValue> extends AnyValue;
            |
            |class TrivialSubType extends VariantSuper<Never>;
        """.trimMargin(),
    ).run {
        assertSubTypeTable(
            listOf(
                type("TrivialSubType"),
                type("VariantSuper<Null>"),
            ),
            """
                |╔══════════════════╦══════════════╦══════════════════╗
                |║                  ║TrivialSubType║VariantSuper<Null>║
                |╠══════════════════╬══════════════╬══════════════════╣
                |║TrivialSubType    ║✓             ║✕                 ║
                |╠══════════════════╬══════════════╬══════════════════╣
                |║VariantSuper<Null>║✓             ║✓                 ║
                |╚══════════════════╩══════════════╩══════════════════╝
            """.trimMargin(),
        )
    }

    @Test
    fun orNullViaBinding() = TypeTestHarness(
        """
            |interface I<T>;
            |class OrNullI<N> extends I<N?>;
        """.trimMargin(),
    ).run {
        assertSubTypeTable(
            listOf(
                type("I<String?>"),
                type("OrNullI<String>"),
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
    fun swappedBindings() = TypeTestHarness(
        """
            |interface I<T, U>;
            |// Swap the <T, U> order to test that effect.
            |class C<T, U> extends I<U, T>;
        """.trimMargin(),
    ).run {
        assertSubTypeTable(
            listOf(
                type("I<String, Int>"),
                // I<String, Int> is a super type of C<Int, String>.
                type("C<Int, String>"),
                // Not so for C<String, Int>.
                type("C<String, Int>"),
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
        types: List<StaticType>,
        want: String,
    ) {
        fun typeToCell(type: StaticType): MultilineOutput = type.asMultilineOutput

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

private val (StaticType).asMultilineOutput get() = MultilineOutput.of(
    toStringViaTokenSink { renderTo(it) }
        .replace(trailingNumbersFixupPattern) {
            it.groups[1]!!.value
        },
)

private val trailingNumbersFixupPattern = Regex("""\b(\w+)__\d+\b""")
