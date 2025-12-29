package lang.temper.type2

import lang.temper.common.AtomicCounter
import lang.temper.log.unknownPos
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.type.FunctionType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeTestHarness
import lang.temper.type.Variance
import lang.temper.type.withTypeTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class PartialTypeReconciliationsTest {
    @Test
    fun unnested() = withTypeTestHarness {
        assertReconciliations(
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Common),
            ),
            common = setOf(typeLike("ʼa"), partialType("String")),
        )
    }

    @Test
    fun listElementTypeCommon() = withTypeTestHarness {
        assertReconciliations(
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Common),
            ),
            common = setOf(partialType("List<ʼa>"), partialType("List<String>")),
        )
    }

    @Test
    fun listElementTypeLowerToUpper() = withTypeTestHarness {
        assertReconciliations(
            uppers = setOf(partialType("List<ʼa>")),
            lowers = setOf(partialType("List<String>")),
            // The thing with a common bound, x, is in this relationship
            //
            //    List<String> <: x <: List<ʼa>
            //
            // List is covariant on its type parameter.
            // We can substitute for x and infer
            // x is in (List<Any?>, List<Any>, List<String?>, List<String>)
            //
            // ʼa is in (Any?, Any, String?, String)
            //
            // From that, we can infer:
            //
            //    String <: ʼ
            //
            // String is a lower bound on ʼa.
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Lower),
            ),
        )
    }

    @Test
    fun listElementTypeUpperToLower() = withTypeTestHarness("interface I;") {
        assertReconciliations(
            uppers = setOf(partialType("List<I>")),
            lowers = setOf(partialType("List<ʼa>")),
            // List<a> <: x <: List<I>
            //
            // List is covariant in its type parameter.
            // x is in (List<I>, List<SomeSubTypeOfI>, List<Never<I>>, ...)
            //
            // I <: ʼa
            //
            // Here, ʼa is the upper bound.
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("I"), BoundKind.Upper),
            ),
        )
    }

    /** Like [listElementTypeUpperToLower] but upper is empty and common is still above lower */
    @Test
    fun listElementTypeCommonToLower() = withTypeTestHarness("interface I;") {
        assertReconciliations(
            common = setOf(partialType("List<I>")),
            lowers = setOf(partialType("List<ʼa>")),
            //    List<ʼa> = x <: List<I>
            // But with similar reasoning to the above.
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("I"), BoundKind.Upper),
            ),
        )
    }

    @Test
    fun listElementTypeUpperToLowerWithFinalBound() = withTypeTestHarness {
        assertReconciliations(
            // By the equivalent of the reasoning above:
            //    List<ʼa> <: x <: List<String>
            // From that we can infer:
            //    Never<String> <: ʼa <: String
            // Here, String is an upper bound on ʼa.
            uppers = setOf(partialType("List<String>")),
            lowers = setOf(partialType("List<ʼa>")),
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Upper),
                PartialTypeReconciliation(TypeVar("ʼa"), type2("Never<String>"), BoundKind.Lower),
            ),
        )
    }

    @Test
    fun listElementTypeLowerToUpperWithFinalBound() = withTypeTestHarness {
        assertReconciliations(
            uppers = setOf(partialType("List<ʼa>")),
            lowers = setOf(partialType("List<String>")),
            // List<String> <: x <: List<ʼa>
            // String <: ʼa
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Lower),
            ),
        )
    }

    // For the below, we're testing reconciliation under various variance situations.
    //
    // F<INP, MID, OUT> is contravariant on INP, invariant on MID, and covariant on OUT.
    //
    // Additionally, there are some simple interface types making an extends chain.
    // Ab is short for "abstract type."
    //
    // Ab2 extends Ab1 extends Ab0
    private val typesForTesting = """
        |interface F<in INP, MID, out OUT>;
        |
        |interface Ab0;
        |interface Ab1 extends Ab0;
        |interface Ab2 extends Ab1;
    """.trimMargin()

    @Test
    fun commonToCommonInpAb1() = withTypeTestHarness(typesForTesting) {
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            common = setOf(
                partialType("F<ʼx, Int, Int>"), partialType("F<Ab1, Int, Int>"),
            ),
        )
    }

    @Test
    fun upperToLowerInpAb1() = withTypeTestHarness(typesForTesting) {
        // F<Ab1, ...>
        //     ↓
        //
        //     ↓
        // F<ʼx, ...>
        //
        // A subtype of the type for a function that takes an Ab1 has to take Ab1 and possibly other values.
        // Ab1 is a lower bound on the type of values the subtype can take.
        //
        //     let g<X>(): F<X, Int, Int> { ... }
        //     let w: F<Ab1, Int, Int> = g<Ab0>();
        //     let x: F<Ab1, Int, Int> = g<Ab1?>();
        //     let y: F<Ab1, Int, Int> = g<Ab1>();
        //     // let z: F<Ab1, Int, Int> = g<Ab2>(); // ILLEGAL
        //
        // Ab1 <: x
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Lower)),
            uppers = setOf(partialType("F<Ab1, Int, Int>")),
            lowers = setOf(partialType("F<ʼx, Int, Int>")),
        )
    }

    @Test
    fun lowerToLowerInpAb1() = withTypeTestHarness(typesForTesting) {
        // F<Ab1, ...> &
        // F<ʼx, ...>
        //     ↓
        //
        //     ↓
        //
        // We don't know how Ab1 relates to x, so we can't generate any reconciliations.
        assertReconciliations(
            want = setOf(),
            lowers = setOf(partialType("F<Ab1, Int, Int>"), partialType("F<ʼx, Int, Int>")),
        )
    }

    @Test
    fun commonToLowerInpAb1() = withTypeTestHarness(typesForTesting) {
        //
        //     ↓
        // F<Ab1, ...>
        //     ↓
        // F<ʼx, ...>
        //
        // Same reasoning as upperToLowerInpAb1
        //
        // Ab1 <: ʼx
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Lower)),
            common = setOf(partialType("F<Ab1, Int, Int>")),
            lowers = setOf(partialType("F<ʼx, Int, Int>")),
        )
    }

    @Test
    fun lowerToUpperInpAb1() = withTypeTestHarness(typesForTesting) {
        // F<ʼx, ...>
        //     ↓
        //
        //     ↓
        // F<Ab1, ...>
        //
        // An upper bound on a function that can accept any Ab1 is a
        // function that can accept any of (Ab1, Ab0, AnyValue, Ab1?, Ab0?, AnyValue?).
        //
        // Ab1 is a lower bound of all of those.
        //
        //     let h(): F<Ab1, Int, Int> { ... }
        //     // let v: F<Ab0?, Int, Int> = h(); // ILLEGAL
        //     // let w: F<Ab0, Int, Int> = h(); // ILLEGAL
        //     // let x: F<Ab1?, Int, Int> = h(); // ILLEGAL
        //     let y: F<Ab1, Int, Int> = h();
        //     let z: F<Ab2, Int, Int> = h();
        //
        // ʼx <: Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Upper)),
            uppers = setOf(partialType("F<ʼx, Int, Int>")),
            lowers = setOf(partialType("F<Ab1, Int, Int>")),
        )
    }

    @Test
    fun lowerToCommonInpAb1() = withTypeTestHarness(typesForTesting) {
        //
        //     ↓
        // F<ʼx, ...>
        //     ↓
        // F<Ab1, ...>
        //
        // Same reasoning as lowerToUpperInpAb1
        //
        // ʼx <: Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Upper)),
            uppers = setOf(partialType("F<ʼx, Int, Int>")),
            lowers = setOf(partialType("F<Ab1, Int, Int>")),
        )
    }

    @Test
    fun commonToCommonMidAb1() = withTypeTestHarness(typesForTesting) {
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            common = setOf(
                partialType("F<Int, ʼx, Int>"), partialType("F<Int, Ab1, Int>"),
            ),
        )
    }

    @Test
    fun upperToLowerMidAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., Ab1, ...>
        //        ↓
        //
        //        ↓
        // F<..., ʼx, ...>
        //
        //     let g<X>(): F<Int, X, Int> { ... }
        //     // let x: F<Int, Ab1, Int> = g<Ab0>(); // ILLEGAL
        //     // let y: F<Int, Ab1, Int> = g<Ab1?>(); // ILLEGAL
        //     let z: F<Int, Ab1, Int> = g<Ab1>();
        //     // let w: F<Int, Ab1, Int> = g<Ab2>(); // ILLEGAL
        //
        // An F<..., Ab1, ...> can use as in/out only Ab1.
        // It can accept an Ab2 or some other sub-type of Ab1, but it has to accept any Ab1.
        // That means ʼx has to be Ab1.
        //
        // ʼx == Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            uppers = setOf(partialType("F<Int, Ab1, Int>")),
            lowers = setOf(partialType("F<Int, ʼx, Int>")),
        )
    }

    @Test
    fun lowerToLowerMidAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., Ab1, ...> &
        // F<..., ʼx, ...>
        //        ↓
        //
        //        ↓
        //
        // Like upperToLowerMidAb1.  We can compare two lower bounds and come
        // to conclusions without having a clear order between the two because
        // the corresponding type formal is invariant.
        //
        // ʼx == Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            lowers = setOf(partialType("F<Int, ʼx, Int>"), partialType("F<Int, Ab1, Int>")),
        )
    }

    @Test
    fun commonToLowerMidAb1() = withTypeTestHarness(typesForTesting) {
        //
        //        ↓
        // F<..., Ab1, ...>
        //        ↓
        // F<..., ʼx, ...>
        //
        // An F<..., Ab1, ...> can take as input Ab1, Ab2, or some sub-type.
        // But the ʼx has to bind to Ab1 since it still needs to accept all Ab1.
        //
        //     let g<X>(): F<Int, X, Int> { ... }
        //     // let x: F<Int, Ab1, Int> = g<Ab0>(); // ILLEGAL
        //     // let y: F<Int, Ab1, Int> = g<Ab1?>(); // ILLEGAL
        //     let z: F<Int, Ab1, Int> = g<Ab1>();
        //     // let w: F<Int, Ab1, Int> = g<Ab2>(); // ILLEGAL
        //
        // ʼx == Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            common = setOf(partialType("F<Int, Ab1, Int>")),
            lowers = setOf(partialType("F<Int, ʼx, Int>")),
        )
    }

    @Test
    fun lowerToUpperMidAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., ʼx, ...>
        //        ↓
        //
        //        ↓
        // F<..., Ab1, ...>
        //
        // Anything the function returns needs to fit in an Ab1-typed variable,
        // and needs to be acceptable as an input.
        //
        //     let g(): F<Int, Ab1, Int> { ... }
        //     // let x: F<Int, Ab0, Int> = g(); // ILLEGAL
        //     let y: F<Int, Ab1, Int> = g();
        //     // let z: F<Int, Ab1?, Int> = g(); // ILLEGAL
        //     // let w: F<Int, Ab2, Int> = g(); // ILLEGAL
        //
        // ʼx == Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            uppers = setOf(partialType("F<Int, ʼx, Int>")),
            lowers = setOf(partialType("F<Int, Ab1, Int>")),
        )
    }

    @Test
    fun lowerToCommonMidAb1() = withTypeTestHarness(typesForTesting) {
        //
        //        ↓
        // F<..., ʼx, ...>
        //        ↓
        // F<..., Ab1, ...>
        //
        // Same reasoning as lowerToUpperMidAb1
        //
        // ʼx == Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            uppers = setOf(partialType("F<Int, ʼx, Int>")),
            lowers = setOf(partialType("F<Int, Ab1, Int>")),
        )
    }

    @Test
    fun commonToCommonOutAb1() = withTypeTestHarness(typesForTesting) {
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Common)),
            common = setOf(
                partialType("F<Int, Int, ʼx>"), partialType("F<Int, Int, Ab1>"),
            ),
        )
    }

    @Test
    fun upperToLowerOutAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., Ab1>
        //        ↓
        //
        //        ↓
        // F<..., ʼx>
        //
        // A caller of F<..., Ab1> knows that its output is-a Ab1.
        // A caller of a subtype of F<..., Ab1> knows that its output is-a Ab1,
        // but it could be limited to returning Ab2 or some unknown sub-type.
        // A valid binding for ʼx would be Ab1, Ab2, SubTypeOfAb1, or Never<...> of those.
        //
        //     let g<X>(): F<Int, Int, X> { ... }
        //     let x: F<Int, Int, Ab1> = g<Ab1>();
        //     let y: F<Int, Int, Ab1> = g<Ab2>();
        //     val z: F<Int, Int, Ab1> = g<Never<Ab1>>()
        //     // let v: F<Int, Int, Ab1> = g<Ab0>(); // ILLEGAL
        //     // let w: F<Int, Int, Ab1> = g<Ab1?>(); // ILLEGAL
        //
        // ʼx <: Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Upper)),
            uppers = setOf(partialType("F<Int, Int, Ab1>")),
            lowers = setOf(partialType("F<Int, Int, ʼx>")),
        )
    }

    @Test
    fun lowerToLowerOutAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., Ab1> &
        // F<..., ʼx>
        //        ↓
        //
        //        ↓
        //
        // No clear relationship between those two so we can't conclude anything.
        assertReconciliations(
            want = setOf(),
            lowers = setOf(partialType("F<Int, Int, Ab1>"), partialType("F<Int, Int, ʼx>")),
        )
    }

    @Test
    fun commonToLowerOutAb1() = withTypeTestHarness(typesForTesting) {
        //
        //        ↓
        // F<..., Ab1>
        //        ↓
        // F<..., ʼx>
        //
        // Same reasoning as upperToLowerOutAb1
        //
        // ʼx <: Ab1
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Upper)),
            common = setOf(partialType("F<Int, Int, Ab1>")),
            lowers = setOf(partialType("F<Int, Int, ʼx>")),
        )
    }

    @Test
    fun lowerToUpperOutAb1() = withTypeTestHarness(typesForTesting) {
        // F<..., ʼx>
        //        ↓
        //
        //        ↓
        // F<..., Ab1>
        //
        // If ʼx is one of (Ab1, Ab1?, Ab0, Ab0?, AnyValue, AnyValue?), then
        // it's legal to assign a function that returns an Ab1 to a variable
        // whose declared type returns one of those.
        //
        //     let g(): F<Int, Int, Ab1> { ... }
        //     let x: F<Int, Int, Ab0?> = g();
        //     let y: F<Int, Int, Ab1> = g();
        //     // let z: F<Int, Int, Ab2> = g(); // ILLEGAL
        //
        // A function that is typed as returning an
        //
        // Ab1 <: ʼx
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Lower)),
            uppers = setOf(partialType("F<Int, Int, ʼx>")),
            lowers = setOf(partialType("F<Int, Int, Ab1>")),
        )
    }

    @Test
    fun lowerToCommonOutAb1() = withTypeTestHarness(typesForTesting) {
        //
        //        ↓
        // F<..., ʼx>
        //        ↓
        // F<..., Ab1>
        //
        // Same reasoning as lowerToUpperOutAb1
        //
        // Ab1 <: ʼx
        assertReconciliations(
            want = setOf(PartialTypeReconciliation(TypeVar("ʼx"), type2("Ab1"), BoundKind.Lower)),
            uppers = setOf(partialType("F<Int, Int, ʼx>")),
            lowers = setOf(partialType("F<Int, Int, Ab1>")),
        )
    }

    @Test
    fun reconcileTypeArgWithDeclaredTypeLowers() = withTypeTestHarness {
        // In context of <T extends Listed<String>>, two lower bounds: T and List<a>
        //
        //                   x
        //                  / \
        //                 /   \
        //                /     \
        //               /       \
        //              /         \
        //             /           \
        //            /             \
        //           /               \
        //   Listed<String> <=>  Listed<('a)>
        //                            |
        //                         List<('a)>

        val (typeT) = makeTypeFormalsForTest("T" to type("Listed<String>"))
        assertReconciliations(
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Common),
            ),
            lowers = setOf(typeT, partialType("List<ʼa>")),
        )
    }

    @Test
    fun reconcileTypeArgWithDeclaredType() = withTypeTestHarness {
        val (typeT) = makeTypeFormalsForTest("T" to type("Listed<String>"))
        assertReconciliations(
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("String"), BoundKind.Upper),
                PartialTypeReconciliation(TypeVar("ʼa"), type2("Never<String>"), BoundKind.Lower),
            ),
            uppers = setOf(typeT),
            common = setOf(partialType("List<ʼa>")),
        )
    }

    @Test
    fun commonListWithUpperListed() = withTypeTestHarness {
        assertReconciliations(
            uppers = setOf(partialType("Listed<Int>")),
            common = setOf(partialType("List<ʼa>")),
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("Int"), BoundKind.Upper),
                PartialTypeReconciliation(TypeVar("ʼa"), type2("Never<Int>"), BoundKind.Lower),
            ),
        )
    }

    @Test
    fun functionalTypes() = withTypeTestHarness {
        val common = setOf(partialType("fn(Int): ʼa"))
        val lowers = setOf(type2("fn(Int): Int"))

        assertReconciliations(
            common = common,
            lowers = lowers,
            want = setOf(
                PartialTypeReconciliation(TypeVar("ʼa"), type2("Int"), BoundKind.Lower),
            ),
        )
    }

    @Test
    fun nonNeverBoundAboveNeverBound() = withTypeTestHarness {
        val a = TypeVar("ʼa")
        val uppers = setOf(type2("String"))
        val common = setOf(partialType("Never<ʼa>"))
        // Never<String> is a subtype of String, so we can compute a bound for ʼa
        assertReconciliations(
            uppers = uppers,
            common = common,
            want = setOf(
                PartialTypeReconciliation(a, type2("String"), BoundKind.Upper),
                PartialTypeReconciliation(a, type2("Never<String>"), BoundKind.Lower),
            ),
        )
    }

    private fun assertReconciliations(
        want: Set<PartialTypeReconciliation>,
        lowers: Set<TypeLike> = emptySet(),
        common: Set<TypeLike> = emptySet(),
        uppers: Set<TypeLike> = emptySet(),
    ) {
        val tc = TypeContext2()
        val got = reconcilePartialTypes(
            lowers = lowers,
            common = common,
            uppers = uppers,
            typeContext = tc,
        )
        assertEquals(
            want.sortedWith(ReconciliationComparator).toSet(),
            got.sortedWith(ReconciliationComparator).toSet(),
        )
    }
}

fun TypeTestHarness.type2(s: String): Type2 = typeLike(s) as Type2

fun TypeTestHarness.partialType(s: String): TypeOrPartialType =
    typeLike(s) as TypeOrPartialType

/** For testing, map names that start with a lowercase letter like (a0, a1) to [TypeVar]s */
fun TypeTestHarness.typeLike(s: String): TypeLike {
    // First we allocate type formals for each TypeVar.
    // Later we'll remap the nominal type to a PartialType.
    val vars = mutableMapOf<String, TypeFormal>()
    val counter = AtomicCounter()

    val oldStyleType = type(
        s,
        extraDefinitions = { nameText ->
            if (nameText.firstOrNull() == VAR_PREFIX_CHAR) {
                vars.getOrPut(nameText) {
                    TypeFormal(
                        unknownPos,
                        BuiltinName(nameText),
                        Symbol(nameText),
                        Variance.Invariant,
                        counter,
                    )
                }
            } else {
                null
            }
        },
    )
    val varsBack = vars.map {
        it.value to TypeVarRef(TypeVar(it.key), Nullity.NonNull)
    }.toMap()
    return hackMapOldStyleToNew(oldStyleType).mapType(emptyMap(), varsBack)
}

fun TypeTestHarness.sig(s: String): Signature2 {
    val t = type(s) as FunctionType

    val hasThisFormal = t.valueFormals.firstOrNull()?.let {
        !it.isOptional && it.symbol?.text == "this"
    } ?: false
    val requiredValueFormals = mutableListOf<Type2>()
    val optionalValueFormals = mutableListOf<Type2>()
    for (f in t.valueFormals) {
        if (f.isOptional) {
            optionalValueFormals.add(f.type)
        } else {
            check(optionalValueFormals.isEmpty())
            requiredValueFormals.add(f.type)
        }
    }

    return Signature2(
        returnType2 = hackMapOldStyleToNew(t.returnType),
        hasThisFormal = hasThisFormal,
        requiredInputTypes = requiredValueFormals.toList(),
        optionalInputTypes = optionalValueFormals.toList(),
        restInputsType = t.restValuesFormal?.let { hackMapOldStyleToNew(it) },
        typeFormals = t.typeFormals,
    )
}

// Order reconciliations so we can have nicer test diffs
private object ReconciliationComparator : Comparator<Reconciliation> {
    override fun compare(o1: Reconciliation?, o2: Reconciliation?): Int = when {
        o1 == null -> if (o2 == null) 0 else -1
        o2 == null -> 1
        o1 is VarReconciliation && o2 is VarReconciliation -> {
            val (aBounded, aBound, aKind) = o1
            val (bBounded, bBound, bKind) = o2
            var delta = aBounded.compareTo(bBounded)
            if (delta == 0) {
                delta = aBound.compareTo(bBound)
                if (delta == 0) {
                    delta = aKind.compareTo(bKind)
                }
            }
            delta
        }
        o1 is VarReconciliation -> -1
        o1 is PartialTypeReconciliation && o2 is PartialTypeReconciliation -> {
            var delta = o1.kind.compareTo(o2.kind)
            if (delta == 0) {
                delta = o1.typeVar.name.compareTo(o2.typeVar.name)
                if (delta == 0 && o1.bound != o2.bound) {
                    delta = "${o1.bound}".compareTo("${o2.bound}")
                }
            }
            delta
        }
        else -> 1
    }
}

private operator fun TypeVarRef.compareTo(other: TypeVarRef): Int {
    var delta = this.typeVar.name.compareTo(other.typeVar.name)
    if (delta == 0) {
        delta = this.nullity.compareTo(other.nullity)
    }
    return delta
}
