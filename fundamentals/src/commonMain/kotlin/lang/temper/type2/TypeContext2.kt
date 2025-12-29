package lang.temper.type2

import lang.temper.common.Cons
import lang.temper.common.LeftOrRight
import lang.temper.common.console
import lang.temper.common.lruCacheWithSize
import lang.temper.common.subListToEnd
import lang.temper.log.Positioned
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Nullity.OrNull
import lang.temper.value.functionalInterfaceSymbol
import kotlin.math.max

private const val DEBUG = false
private inline fun <T> debug(message: () -> String, action: () -> T): T =
    if (DEBUG) {
        console.group(str = message(), f = action)
    } else {
        action()
    }
private inline fun debug(message: () -> String) {
    if (DEBUG) {
        console.log(str = message())
    }
}

/**
 * Helper to handle cases where a type operation is given *Never\<A>* and *B*
 * and we need to special case handling because *Never\<A>* is subtype of *A*.
 */
private inline fun <O> neverNonNeverLand(
    u: Type2,
    t: Type2,
    /** Returned if body is not invoked because we don't have a well-formed never type and a non-never type */
    noMatch: O,
    /** Receives *A* and *B* from above regardless of the order [u] and [t] are passed in. */
    body: (neverTypeMember: Type2, nonNeverType: Type2, neverType: DefinedType) -> O,
): O = when {
    u.definition == t.definition -> noMatch
    // Now we know both can't be Never<...>
    t.definition == neverTypeDefinition && t.bindings.size == 1 -> body(t.bindings[0], u, t as DefinedType)
    u.definition == neverTypeDefinition && u.bindings.size == 1 -> body(u.bindings[0], t, u as DefinedType)
    else -> noMatch
}

/**
 * Helper to handle cases where a type operation is given *Never\<A>* and *B*
 * and we need to special case handling because *Never\<A>* is subtype of *A*.
 */
private inline fun <O> neverNonNeverLandPartial(
    u: TypeOrPartialType,
    t: TypeOrPartialType,
    /** Returned if body is not invoked because we don't have a well-formed never type and a non-never type */
    noMatch: O,
    /** Receives *A* and *B* from above regardless of the order [u] and [t] are passed in. */
    body: (neverTypeMember: TypeLike, nonNeverType: TypeOrPartialType, neverType: TypeOrPartialType) -> O,
): O = when {
    u.definition == t.definition -> noMatch
    // Now we know both can't be Never<...>
    t.definition == neverTypeDefinition && t.bindings.size == 1 -> body(t.bindings[0], u, t as DefinedType)
    u.definition == neverTypeDefinition && u.bindings.size == 1 -> body(u.bindings[0], t, u as DefinedType)
    else -> noMatch
}

/**
 * A short-lived type solver helper that caches repeated type operations.
 *
 * It allows computing relationships between types.
 *
 * ## Type boundaries
 *
 * Least upper bounds and greatest lower bounds.
 *
 * If some types have a common supertype, the least upper bound is the supertype of all that
 * does not have a distinct subtype that is also a supertype of the given types.
 *
 * The greatest lower bound is similar, but it is the tightest subtype of all.
 *
 *      interface I {}
 *      interface J extends I {}
 *      interface K extends J {}
 *      interface L extends I {}
 *      class A extends J {}
 *      class B extends K {}
 *      class C extends L {}
 *      class D extends J, L {}
 *
 * Those definitions lead to an inheritance tree like:
 *
 *              I
 *             / \
 *            /   \
 *           J     L
 *          /|\   / \
 *         / | \ /   \
 *        A  K  D     C
 *           |
 *           |
 *           B
 *
 * Given those, these LUB (least upper bound) and GLB (greatest lower bound) apply.
 *
 * LUB(I, J) = I
 * LUB(J, L) = I
 * LUB(A, D) = J
 * LUB(B, C) = I
 * LUB(C, D) = L
 * LUB(C, D, A) = I
 *
 * GLB(I, A) = A
 * GLB(I, J, A) = A
 * GLB(J, L) = D     // Valid but not really useful for unsealed interfaces.
 *
 * ## Type projection
 *
 * From a parameterized type and a supertype definition, get its supertype parameterized.
 *
 *      interface I<T> {}
 *      class C<U> extends I<List<U>> {}
 *
 * For example, in the context of those definitions, projecting
 * `C<String>` to `I` yields `I<List<String>>`.
 */
class TypeContext2 {
    private val stsCache = lruCacheWithSize<TypeOrPartialType, SuperTypeTree2<*>>()

    fun superTypeTreeOf(t: TypeOrPartialType): SuperTypeTree2<TypeOrPartialType> {
        @Suppress("UNCHECKED_CAST") // Type checked via class tricks above
        return stsCache.getOrPut(t) {
            if (t is Type2) {
                SuperTypeTree2.of(t, Type2::class)
            } else {
                SuperTypeTree2.of(t, TypeOrPartialType::class)
            }
        } as SuperTypeTree2<TypeOrPartialType>
    }

    fun superTypeTreeOf(t: Type2): SuperTypeTree2<Type2> {
        @Suppress("UNCHECKED_CAST") // Type checked via class tricks above
        return superTypeTreeOf(t as TypeOrPartialType) as SuperTypeTree2<Type2>
    }

    /**
     * Cache of paths of `extends` relationship between type definitions.
     *
     * Sometimes we need to know the shortest path from a raw type to a particular supertype.
     *
     * If `A extends B` and `B extends C` but `A` does not directly extend `C` then there might
     * be a cache entry here from `A to C` to `[A, B, C]`.
     */
    private val extendsPathCache =
        mutableMapOf<Pair<TypeDefinition, TypeDefinition>, List<TypeDefinition>?>()

    /**
     * The least upper bound.
     */
    fun lub(t: Type2, u: Type2): List<Type2> = debug({ "lub($t, $u)" }) lubHelper@{
        if (t == u) {
            return@lubHelper listOf(t)
        }

        // Reduce the number of cases the below has to handle by separating out nullity
        if (t.nullity == OrNull || u.nullity == OrNull) {
            return@lubHelper lub(t.withNullity(NonNull), u.withNullity(NonNull))
                .map { it.withNullity(OrNull) }
        }

        // Unpack Never when comparing to a non-never type.
        // TODO: Ban Never<X> as an upper bound for type formals.  That unnecessarily complicates
        // LUB/GLB for no benefit and allows for nonsensical definitions like <T extends Never<T>>
        neverNonNeverLand(t, u, Unit) { neverArg, nonNever, _ ->
            debug({ ". lub unpacked Never Never< $neverArg >, $nonNever" }) {
                return@lubHelper lub(neverArg, nonNever)
            }
        }

        lubMemoTable.getOrPut(t to u) {
            if (t is TypeParamRef || u is TypeParamRef) {
                if (t is TypeParamRef && u is TypeParamRef) {
                    // We could have a situation like <T, U extends T> or vice versa, or
                    // transitively: <T, W extends T, U extends W>
                    if (isSubType(t, u)) {
                        return@getOrPut listOf(u)
                    }
                    if (isSubType(u, t)) {
                        return@getOrPut listOf(t)
                    }
                }
                fun boundsOf(t: Type2) = when (val d = t.definition) {
                    is TypeFormal -> d.upperBounds.map { hackMapOldStyleToNew(it) }
                    is TypeShape -> listOf(t)
                }

                val tBounds = boundsOf(t)
                val uBounds = boundsOf(u)
                buildSet {
                    for (tb in tBounds) {
                        for (ub in uBounds) {
                            addAll(lub(tb, ub))
                        }
                    }
                }.toList()
            } else {
                check(t is DefinedType && u is DefinedType)
                leastCommonSuperTypes(listOf(t, u)).toList()
            }
        }
    }.also {
        debug { "-> $it" }
    }

    private val lubMemoTable = mutableMapOf<Pair<Type2, Type2>, List<Type2>>()

    fun leastCommonSuperTypes(types: Iterable<Type2>): Set<Type2> =
        leastCommonSupers(
            types,
            { it: Type2 -> superTypeTreeOf(it) },
            { a: TypeLike, b: TypeLike -> isSubType(a as Type2, b as Type2) },
        )

    fun leastCommonSuperPartialTypes(
        types: Iterable<TypeOrPartialType>,
        optimistic: Boolean,
    ): Set<TypeOrPartialType> =
        leastCommonSupers(
            types,
            { it: TypeOrPartialType -> superTypeTreeOf(it) },
            { a: TypeLike, b: TypeLike ->
                isSubTypePartial(a, b, optimistic = optimistic)
            },
        )

    private fun <TYPE : TypeOrPartialType> leastCommonSupers(
        types: Iterable<TYPE>,
        superTypeTreeOf: (TYPE) -> SuperTypeTree2<TYPE>,
        isSubType: (TypeLike, TypeLike) -> Boolean,
    ): Set<TYPE> {
        val superGroups = types.map {
            superTypeTreeOf(it).byDefinition
        }
        // First narrow down to common generic definitions.
        val commons = superGroups.map { it.keys }.intersectAll()
        // For now, just find valid parameterizations in common that are supertypes of all.
        val valids = commons.associateWith { typeDef ->
            val allTypes = superGroups.flatMap { it[typeDef] ?: emptyList() }
            // TODO Variance-aware supertype bindings. Or even just go to all `Wildcard` bindings?
            allTypes.filter { type -> types.all { isSubType(it, type) } }.toSet()
        }.filter { it.value.isNotEmpty() }
        // Keep only the nearest/least type definitions, and presume we want the earlier valid types for each.
        val validTypes = valids.values.flatten()
        val leasts = buildSet {
            addAll(valids.keys)
            for (type in validTypes) {
                // Remove all supertypes.
                // If types don't extend alternate bindings of themselves, this logic should work.
                for (superTypeDef in superTypeTreeOf(type).byDefinition.keys) {
                    // But not self.
                    if (superTypeDef != type.definition) {
                        remove(superTypeDef)
                    }
                }
            }
        }
        return buildSet {
            for (v in valids) {
                if (v.key in leasts) {
                    addAll(v.value)
                }
            }
        }
    }

    private fun <T> List<Set<T>>.intersectAll() = buildSet {
        if (this@intersectAll.isNotEmpty()) {
            this.addAll(this@intersectAll[0])
        }
        for (group in this@intersectAll.subListToEnd(1)) {
            this.retainAll(group)
        }
    }

    /**
     * The greatest lower bound of two types.
     */
    fun glb(t: Type2, u: Type2): Type2? =
        debug({ "glb($t, $u)" }) glbHelper@{
            if (t == u) {
                return@glbHelper t
            }

            if (t.nullity == OrNull || u.nullity == OrNull) {
                val glbNonNull =
                    glb(t.withNullity(NonNull), u.withNullity(NonNull))
                return@glbHelper if (t.nullity == OrNull && u.nullity == OrNull) {
                    glbNonNull?.withNullity(OrNull)
                } else {
                    glbNonNull
                }
            }

            neverNonNeverLand(t, u, Unit) { neverArg, nonNever, nt ->
                debug({ ". glb unpacked Never< $neverArg >, $nonNever" }) {
                    val pos = (nt as? Positioned)?.pos
                    return@glbHelper glb(neverArg, nonNever)?.let {
                        MkType2(neverTypeDefinition)
                            .actuals(listOf(it))
                            .position(pos)
                            .get()
                    }
                }
            }

            return@glbHelper if (isSubType(t, u)) {
                t
            } else if (isSubType(u, t)) {
                u
            } else {
                null
            }
        }

    /**
     * The greatest lower bound of two types.
     */
    fun glbPartial(t: TypeLike, u: TypeLike): TypeLike? =
        debug({ "glbPartial($t, $u)" }) glbHelper@{
            if (t == u) {
                return@glbHelper t
            }

            if (t !is TypeOrPartialType || u !is TypeOrPartialType) {
                return@glbHelper null
            }

            if (t.nullity == OrNull || u.nullity == OrNull) {
                val glbNonNull =
                    glbPartial(t.withNullity(NonNull), u.withNullity(NonNull))
                return@glbHelper if (t.nullity == OrNull && u.nullity == OrNull) {
                    glbNonNull?.withNullity(OrNull)
                } else {
                    glbNonNull
                }
            }

            neverNonNeverLandPartial(t, u, Unit) { neverArg, nonNever, nt ->
                debug({ ". glb unpacked Never< $neverArg >, $nonNever" }) {
                    val pos = (nt as? Positioned)?.pos
                    return@glbHelper glbPartial(neverArg, nonNever)?.let {
                        PartialType.from(
                            neverTypeDefinition,
                            listOf(it),
                            NonNull,
                            pos = pos,
                        )
                    }
                }
            }

            return@glbHelper if (isSubTypePartial(t, u, optimistic = false)) {
                t
            } else if (isSubTypePartial(u, t, optimistic = false)) {
                u
            } else {
                null
            }
        }

    /**
     * Generic nominal type systems can be infinite.
     * https://docs.oracle.com/javase/specs/jls/se10/html/jls-4.html notes:
     *
     * > Like lub (§4.10.4), upward projection and downward projection may
     * > produce infinite types, due to the recursion on type variable bounds.
     *
     * To avoid infinite recursion in isSubType on types like
     *     Comparable<T extends Comparable<T>>
     * we capture pairs that we're checking and assume true if we reenter
     * with the same pair.
     */
    private val optimisticIfSubTypesRecursivelyReached =
        mutableSetOf<Pair<TypeOrPartialType, TypeOrPartialType>>()

    /**
     * True if t1 is a subtype of t2 per a subtype relation analogous to
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.10.2
     *
     * @param t the possible subtype.
     * @param u the possible supertype.
     */
    fun isSubType(t: Type2, u: Type2): Boolean = debug({ "isSubType($t, $u)" }) isSub@{
        // Changes to this need to also be reflected in isSubTypeOptimistic

        if (t == u) {
            return@isSub true
        } // Non-strict

        // TODO: when we have nullable upper bounds on type parameters, we'll need to unpack type bounds
        // before checking nullity so that a non-null TypeParamRef is a subtype of one of its nullable bounds.
        when (t.nullity) {
            // If t is nullable, then u needs to be nullable, and we can simplify things
            // by recursing with non-nullable types.
            //
            // This handles cases like:
            //
            // A? <: B   -> false because the left admits null, a value which B does not.
            // A? <: B? <-> A <: B
            OrNull ->
                return@isSub (u.nullity == OrNull) &&
                    isSubType(
                        t.withNullity(NonNull),
                        u.withNullity(NonNull),
                    )

            NonNull -> if (u.nullity == OrNull) {
                return@isSub isSubType(
                    t,
                    u.withNullity(NonNull),
                )
            }
        }

        if (t.definition != u.definition && t.definition == neverTypeDefinition && t.bindings.size == 1) {
            // Special case  Never<Int> <: Int
            // This does not use neverNonNeverLand because there is no symmetry
            //      Int </: Never<Int>
            if (isSubBinding(t.bindings[0], u, 0, neverTypeDefinition)) {
                return@isSub true
            }
            // Carry on for cases like
            //     Never<...> <: AnyValue
        }

        val infiniteLoopAvoidanceKey = t to u
        if (!optimisticIfSubTypesRecursivelyReached.add(infiniteLoopAvoidanceKey)) {
            return@isSub true
        }
        try {
            // A type is a subtype of another when we can search the supertype tree and
            // find a supertype.
            val definition = t.definition
            if (definition === u.definition) {
                // If the names match, check the bindings.
                val arity = t.bindings.size
                if (arity == u.bindings.size && arity == definition.formals.size) {
                    if (arity == 0) {
                        return@isSub true
                    }
                    if (
                        t.bindings.indices.all { i ->
                            val tb = t.bindings[i]
                            val ub = u.bindings[i]
                            isSubBinding(tb, ub, i, definition)
                        }
                    ) {
                        return@isSub true
                    }
                }
            } else {
                // Try supertypes with a name match
                val tSupers = superTypeTreeOf(t)
                if (tSupers[u.definition].any { tSuper -> isSubType(tSuper, u) }) {
                    return@isSub true
                }
            }

            // HACK: Until we stop converting back and forth between FunctionTypes and Type2,
            // which launders the exact functional interface type, treat all functional interface
            // types with the same signature as equivalent.
            if (functionalInterfaceSymbol in t.definition.metadata &&
                functionalInterfaceSymbol in u.definition.metadata
            ) {
                val sigT = withType(t, fn = { _, s, _ -> s }, fallback = { null })
                val sigU = withType(u, fn = { _, s, _ -> s }, fallback = { null })
                if (sigT != null && sigT == sigU) {
                    return@isSub true
                }
            }

            return@isSub false
        } finally {
            optimisticIfSubTypesRecursivelyReached.remove(infiniteLoopAvoidanceKey)
        }
    }.also {
        debug { "-> $it" }
    }

    /**
     * Like [isSubType] but is optimistic around [TypeVarRef]s.
     *
     * @param t the possible subtype.
     * @param u the possible supertype.
     */
    fun isSubTypeOptimistic(t: TypeLike, u: TypeLike): Boolean = isSubTypePartial(t, u, true)

    /**
     * Like [isSubType] but for partial types.
     *
     * @param t the possible subtype.
     * @param u the possible supertype.
     * @param optimistic whether to assume two distinct type variables are equivalent (true) or not (false).
     */
    fun isSubTypePartial(t: TypeLike, u: TypeLike, optimistic: Boolean): Boolean = debug({
        "isSubTypePartial($t, $u)"
    }) isSub@{
        if (t == u) {
            return@isSub true
        } // Non-strict

        if (t !is TypeOrPartialType || u !is TypeOrPartialType) {
            return@isSub optimistic
        }

        // TODO: when we have nullable upper bounds on type parameters, we'll need to unpack type bounds
        // before checking nullity so that a non-null TypeParamRef is a subtype of one of its nullable bounds.
        when (t.nullity) {
            // If t is nullable, then u needs to be nullable, and we can simplify things
            // by recursing with non-nullable types.
            //
            // This handles cases like:
            //
            // A? <: B   -> false because the left admits null, a value which B does not.
            // A? <: B? <-> A <: B
            OrNull ->
                return@isSub (u.nullity != NonNull) &&
                    isSubTypePartial(
                        t.withNullity(NonNull),
                        u.withNullity(NonNull),
                        optimistic = optimistic,
                    )

            NonNull -> if (u.nullity != NonNull) {
                return@isSub isSubTypePartial(
                    t,
                    u.withNullity(NonNull),
                    optimistic = optimistic,
                )
            }
        }

        if (t.definition != u.definition && t.definition == neverTypeDefinition && t.bindings.size == 1) {
            // Special case  Never<Int> <: Int
            // This does not use neverNonNeverLand because there is no symmetry
            //      Int </: Never<Int>
            if (isSubBindingPartial(t.bindings[0], u, 0, neverTypeDefinition, optimistic = optimistic)) {
                return@isSub true
            }
            // Carry on for cases like
            //     Never<...> <: AnyValue
        }

        val infiniteLoopAvoidanceKey = t to u
        if (!optimisticIfSubTypesRecursivelyReached.add(infiniteLoopAvoidanceKey)) {
            return@isSub true
        }
        try {
            // A type is a subtype of another when we can search the supertype tree and
            // find a supertype.
            val definition = t.definition
            if (definition === u.definition) {
                // If the names match, check the bindings.
                val arity = t.bindings.size
                if (arity == u.bindings.size && arity == definition.formals.size) {
                    if (arity == 0) {
                        return@isSub true
                    }
                    if (
                        t.bindings.indices.all { i ->
                            val tb = t.bindings[i]
                            val ub = u.bindings[i]
                            isSubBindingPartial(tb, ub, i, definition, optimistic = optimistic)
                        }
                    ) {
                        return@isSub true
                    }
                }
            } else {
                // Try supertypes with a name match
                val tSupers = superTypeTreeOf(t)
                if (tSupers[u.definition].any { tSuper -> isSubTypePartial(tSuper, u, optimistic = optimistic) }) {
                    return@isSub true
                }
            }
            return@isSub false
        } finally {
            optimisticIfSubTypesRecursivelyReached.remove(infiniteLoopAvoidanceKey)
        }
    }.also {
        debug { "-> $it" }
    }

    private fun isSubBinding(
        b: Type2,
        c: Type2,
        formalIndex: Int,
        typeDef: TypeDefinition,
    ): Boolean {
        if (b == c) { // Handles value bindings
            return true
        }
        val formal = typeDef.formals[formalIndex]
        return when (formal.variance) {
            Variance.Invariant -> false // b != c above
            Variance.Covariant -> isSubType(b, c)
            Variance.Contravariant -> isSubType(c, b)
        }
    }

    private fun isSubBindingPartial(
        b: TypeLike,
        c: TypeLike,
        formalIndex: Int,
        typeDef: TypeDefinition,
        optimistic: Boolean,
    ): Boolean {
        if (b == c) { // Handles value bindings
            return true
        }
        if (b !is TypeOrPartialType || c !is TypeOrPartialType) { // optimistically
            return optimistic
        }
        val formal = typeDef.formals[formalIndex]
        return when (formal.variance) {
            Variance.Invariant -> false // b != c above
            Variance.Covariant -> isSubTypePartial(b, c, optimistic = optimistic)
            Variance.Contravariant -> isSubTypePartial(c, b, optimistic = optimistic)
        }
    }

    /**
     * The shortest path from [s] to [t] where for every adjacent pair the
     * first `extends` the second.
     *
     * We go breadth-first to find the shortest path of `extends` relationships.
     * The path might be ambiguous as in the below:
     *
     *      interface MultiplyInherited                              {}
     *      interface A                 extends    MultiplyInherited {}
     *      interface B                 extends A, MultiplyInherited {}
     *      class     C                 extends A, B                 {}
     *
     * There are multiple paths from C to MultiplyInherited:
     *
     * 1. C extends A extends MultiplyInherited
     * 2. C extends B extends MultiplyInherited
     * 3. C extends B extends A extends MultiplyInherited
     *
     * This method never considers the third because it is longer.
     * It prefers the first over the second because it considers C's supertypes in the order in
     * which they are declared.
     *
     * @return null if [s] is not a subtype (directly or indirectly) of [t].
     */
    fun extendsPath(s: TypeDefinition, t: TypeDefinition): List<TypeDefinition>? =
        if (s == t) {
            listOf(s)
        } else {
            extendsPathCache.getOrPut(s to t) {
                val partialExtendsChains = ArrayDeque<Cons.NotEmpty<TypeDefinition>>()
                val seen = mutableSetOf<TypeDefinition>()
                partialExtendsChains.add(Cons.NotEmpty(s, Cons.Empty))
                var fullChain: Cons.NotEmpty<TypeDefinition>? = null
                while (true) {
                    val chain = partialExtendsChains.removeFirstOrNull() ?: break
                    val head = chain.head
                    if (head in seen) {
                        continue
                    } // In case of inheritance cycles.
                    seen.add(head)
                    for (superType in head.superTypes) {
                        val superTypeDefinition = superType.definition
                        val longerChain = Cons(superTypeDefinition, chain)
                        if (superTypeDefinition == t) {
                            fullChain = longerChain
                            break
                        }
                        if (superTypeDefinition !in chain) {
                            partialExtendsChains.add(longerChain)
                        }
                    }
                }
                fullChain?.reversed()
            }
        }

    fun admitsNull(typeOrPartialType: TypeOrPartialType): Boolean {
        if (typeOrPartialType.nullity == OrNull) {
            return true
        }
        // TODO: Once TypeFormal's allow upper bounds, look at the definition
        // for nullable upper bounds.
        return false
    }

    fun admitsNullFuzzingTypeParamRef(typeOrPartialType: TypeOrPartialType): Boolean? {
        if (typeOrPartialType is TypeParamRef) {
            return null
        }
        // TODO: Once TypeFormal's allow upper bounds, get rid of this tri-state variant.
        return admitsNull(typeOrPartialType)
    }

    /**
     * Checks whether one signature is more specific than the other.
     */
    internal fun overloadSpecificity(a: Signature2, b: Signature2): OverloadSpecificity? =
        overloadSpecificityMemoTable.getOrPut(a to b) computeSpecificity@{
            val nToCheckA = a.requiredInputTypes.size + a.optionalInputTypes.size +
                (if (a.restInputsType != null) 1 else 0)
            val nToCheckB = b.requiredInputTypes.size + b.optionalInputTypes.size +
                (if (b.restInputsType != null) 1 else 0)

            // We'll walk over the arguments pairwise making sure we try one rest argument
            // if appropriate.
            // We're looking to identify which has an argument that is more specific than
            // the other and none that are less specific.
            // This is the number of value formals we'll compare.
            val n = max(nToCheckA, nToCheckB)

            // But first,
            // we need a way to compare the signatures for some corresponding set of
            // type arguments.
            val maxTypeArity = max(a.typeFormals.size, b.typeFormals.size)
            val typeArgs = (0 until maxTypeArity).map {
                TypeVar("${VAR_PREFIX_CHAR}Q${specificityVarCounter++}")
            }
            val aMap = a.typeFormals.mapIndexed { i, typeFormal ->
                typeFormal to TypeVarRef(typeArgs[i], NonNull)
            }.toMap()
            val bMap = b.typeFormals.mapIndexed { i, typeFormal ->
                typeFormal to TypeVarRef(typeArgs[i], NonNull)
            }.toMap()

            var moreSpecific: LeftOrRight? = null
            var distinguishingArgumentIndices: MutableList<Int>? = null
            for (i in 0 until n) {
                val aValueFormal = a.valueFormalForActual(i) ?: return@computeSpecificity null
                val bValueFormal = b.valueFormalForActual(i) ?: return@computeSpecificity null
                // Contextualize the types using the assumed type parameter equivalence
                val aCT = aValueFormal.type.mapType(emptyMap(), aMap)
                val bCT = bValueFormal.type.mapType(emptyMap(), bMap)
                if (aCT == bCT) {
                    // no effect on specificity
                    continue // do not add i to list below
                }
                if (isSubTypePartial(aCT, bCT, optimistic = false)) {
                    // aCT <: bCT
                    if (moreSpecific == LeftOrRight.Right) { return@computeSpecificity null }
                    moreSpecific = LeftOrRight.Left
                } else if (isSubTypePartial(bCT, aCT, optimistic = false)) {
                    if (moreSpecific == LeftOrRight.Left) { return@computeSpecificity null }
                    moreSpecific = LeftOrRight.Right
                } else {
                    // Incomparable types.
                    return@computeSpecificity null
                }
                if (distinguishingArgumentIndices == null) {
                    distinguishingArgumentIndices = mutableListOf()
                }
                distinguishingArgumentIndices.add(i)
            }

            if (moreSpecific != null && distinguishingArgumentIndices?.isEmpty() == false) {
                OverloadSpecificity(moreSpecific, distinguishingArgumentIndices.toList())
            } else {
                null
            }
        }
    private var specificityVarCounter: Long = 0
    private val overloadSpecificityMemoTable = mutableMapOf<Pair<Signature2, Signature2>, OverloadSpecificity?>()
}

internal data class OverloadSpecificity(
    val moreSpecific: LeftOrRight,
    val distinguishingArgumentIndices: List<Int>,
)
