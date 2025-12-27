package lang.temper.type

import lang.temper.common.console
import lang.temper.common.containsAny
import lang.temper.common.lruCacheWithSize
import lang.temper.common.subListToEnd
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withType
import lang.temper.value.functionalInterfaceSymbol

private fun isTop(t: StaticType) = t == TopType
private fun isBottom(t: StaticType) = t.isNeverType
private fun isBubble(t: StaticType) = t == BubbleType
private fun isValueType(t: StaticType) = t is NominalType || t is FunctionType

private const val DEBUG = false
private inline fun <T> debug(message: () -> String, action: () -> T): T =
    if (DEBUG) {
        console.group(str = message(), f = action)
    } else {
        action()
    }

class TypeContext {
    private val sbsCache = lruCacheWithSize<StaticType, SimpleBoundSets?>()
    private fun simpleBoundsSetOf(t: StaticType) = sbsCache.getOrPut(t) { SimpleBoundSets(t) }
    private val stsCache = lruCacheWithSize<StaticType, SuperTypeTree>()
    private fun superTypeTreeOf(t: NominalType) = stsCache.getOrPut(t) {
        SuperTypeTree.of(t)
    }

    /**
     * The least upper bound.
     * Analogous to [JLS 4.10.4][1]
     *
     * If [simplify], don't return any newly minted [OrType] instances.
     * TODO Also simplify any existing `or` types?
     *
     * [1]: https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.4
     */
    fun lub(t: StaticType, u: StaticType, simplify: Boolean = false): StaticType =
        debug({ "lub($t, $u, $simplify)" }) lubHelper@{
            if (t == u) {
                t
            } else if (t == InvalidType || u == InvalidType) {
                InvalidType
            } else if (isTop(t) || isBottom(u)) {
                t
            } else if (isTop(u) || isBottom(t)) {
                u
            } else if ((isBubble(t) && isValueType(u)) || (isValueType(t) && isBubble(u))) {
                TopType
            } else {
                val bs1 = simpleBoundsSetOf(t)
                if (bs1 != null) {
                    val bs2 = simpleBoundsSetOf(u)
                    if (bs2 != null) {
                        val low1 = bs1.low
                        val high1 = bs1.high
                        val low2 = bs2.low
                        val high2 = bs2.high
                        if (low1.containsAll(high2)) { return@lubHelper t }
                        if (low2.containsAll(high1)) { return@lubHelper u }
                        if (!(high1.containsAny(high2)) && !simplify) {
                            return@lubHelper MkType.or(t, u)
                        }
                    }
                }
                (mergeUpOrNull(t, u) ?: mergeUpOrNull(u, t))?.let { return@lubHelper it }
                if (simplify && t is NominalType && u is NominalType) {
                    return leastCommonSuperType(listOf(t, u))
                }
                // Tried everything else. Take the easy way out.
                if (simplify) {
                    TopType
                } else {
                    MkType.or(t, u)
                }
            }
        }

    /**
     * Returns either [TopType], a [NominalType], or an [AndType] of
     * [NominalType] instances.
     */
    fun leastCommonSuperType(types: Iterable<NominalType>): StaticType {
        val superGroups = types.map { superTypeTreeOf(it).byDefinition }
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
        // In the end, `and` returns `TopType`, first, or `AndType`
        return MkType.and(valids.filter { it.key in leasts }.values.flatten())
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
    fun glb(t: StaticType, u: StaticType): StaticType =
        debug({ "glb($t, $u)" }) glbHelper@{
            if (t == u) {
                t
            } else if (t == InvalidType || u == InvalidType) {
                InvalidType
            } else if (isBottom(t) || isTop(u)) {
                t
            } else if (isBottom(u) || isTop(t)) {
                u
            } else if ((isBubble(t) && isValueType(u)) || (isValueType(t) && isBubble(u))) {
                OrType.emptyOrType
            } else if (u is OrType) {
                MkType.or(u.members.map { uPart -> glb(t, uPart) })
            } else if (t is OrType) {
                MkType.or(t.members.map { tPart -> glb(tPart, u) })
            } else if (isSubType(t, u)) {
                t
            } else if (isSubType(u, t)) {
                u
            } else {
                if (isValueType(t) && isValueType(u)) {
                    // Never if they're disjoint
                    if (t is FunctionType && u is FunctionType) {
                        // Not equal and no sub-typing relationship
                        return@glbHelper OrType.emptyOrType
                    } else if (t is NominalType && u is NominalType) {
                        // If they're disjoint, then Never.
                        // Above we showed there's no subtype relationship, so if either is closed,
                        // they're disjoint.
                        val td = t.definition
                        val ud = u.definition
                        if (
                            (td is TypeShape && td.abstractness == Abstractness.Concrete) ||
                            (ud is TypeShape && ud.abstractness == Abstractness.Concrete)
                        ) {
                            return@glbHelper OrType.emptyOrType
                        }
                    } else {
                        // One is nominal, the other is a function type, but we know the function
                        // type is not a subtype of the other, so the nominal type is not
                        // Function or AnyValue
                        return@glbHelper OrType.emptyOrType
                    }
                    // TODO: If either is a reference to a TypeFormal and the other is outside that
                    // formal's bounds, then NeverType
                }
                MkType.and(t, u)
            }
        }

    private fun mergeUpOrNull(t: StaticType, u: StaticType): StaticType? {
        val merged = mergeUp(t, u)
        return if (merged == InvalidType) null else merged
    }

    /**
     *
     */
    private fun mergeUp(t: StaticType, u: StaticType): StaticType {
        if (isSubType(u, t)) {
            return t
        }
        when (u) {
            is OrType -> {
                for (uMember in u.members) {
                    val upper = mergeUp(t, uMember)
                    if (upper == uMember) {
                        return u
                    } else if (upper != InvalidType) {
                        return MkType.or(u.members.filter { it != uMember } + upper)
                    }
                }
            }
            else -> {}
        }
        return InvalidType
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
        mutableSetOf<Pair<StaticType, StaticType>>()

    /**
     * True if t1 is a subtype of t2 per a subtype relation analogous to
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.10.2
     *
     * @param t the possible sub-type.
     * @param u the possible super-type.
     */
    fun isSubType(t: StaticType, u: StaticType): Boolean {
        if (t == u) { return true } // Non-strict
        if (t == InvalidType || u == InvalidType) { return false }
        if (isBottom(t) || isTop(u)) { return true }
        if ((isBubble(t) && isValueType(u)) || (isValueType(t) && isBubble(u))) {
            // Bubble is disjoint from all nominal and function types.
            return false
        }

        val infiniteLoopAvoidanceKey = t to u
        if (!optimisticIfSubTypesRecursivelyReached.add(infiniteLoopAvoidanceKey)) {
            return true
        }
        try {
            if (u is OrType) {
                // A type is a subtype of a union when it is a subtype of any member.
                if (u.members.any { um -> isSubType(t, um) }) {
                    return true
                }
            } else if (u is AndType) {
                // A type is a subtype of an intersection when it is a subtype of each member.
                if (u.members.all { um -> isSubType(t, um) }) {
                    return true
                }
            }
            if (t is OrType) {
                // A union is a subtype when every member is a subtype.
                if (t.members.all { tm -> isSubType(tm, u) }) {
                    return true
                }
            } else if (t is AndType) {
                // An intersection is a subtype when a member is a subtype.
                if (t.members.any { tm -> isSubType(tm, u) }) {
                    return true
                }
            }
            // A nominal type is a subtype of another when we can search the super-type tree and
            // find a super-type.
            if (t is NominalType && u is NominalType) {
                val definition = t.definition
                if (definition === u.definition) {
                    // If the names match, check the bindings.
                    val arity = t.bindings.size
                    if (arity == u.bindings.size && arity == definition.formals.size) {
                        if (arity == 0) {
                            return true
                        }
                        if (
                            t.bindings.indices.all { i ->
                                val tb = t.bindings[i]
                                val ub = u.bindings[i]
                                isSubBinding(tb, ub, i, definition)
                            }
                        ) {
                            return true
                        }
                    }
                } else {
                    // Try super-types with a name match
                    val tSupers = superTypeTreeOf(t)
                    if (tSupers[u.definition].any { tSuper -> isSubType(tSuper, u) }) {
                        return true
                    }
                }
            }
            if (t is FunctionType && u is FunctionType) {
                // If type parameters and arity are compatible, then check input and output types.
                val tValueFormals = t.valueFormals
                if (
                    tValueFormals.size == u.valueFormals.size &&
                    (t.restValuesFormal == null) == (u.restValuesFormal == null)
                ) {
                    val uCompatible = formalCompatibleFunctionType(u, t.typeFormals)
                    if (uCompatible != null) {
                        val uValueFormals = uCompatible.valueFormals
                        val tRestValuesFormal = t.restValuesFormal
                        val uRestValuesFormal = uCompatible.restValuesFormal
                        if (t.typeFormals == uCompatible.typeFormals) {
                            return isSubType(t.returnType, uCompatible.returnType) &&
                                tValueFormals.indices.all { valueFormalIndex ->
                                    // Check (u, t) instead of (t, u)
                                    // because we're contravariant on inputs.
                                    isSubType(
                                        uValueFormals[valueFormalIndex].staticType,
                                        tValueFormals[valueFormalIndex].staticType,
                                    )
                                } && (
                                    tRestValuesFormal == null ||
                                        isSubType(uRestValuesFormal!!, tRestValuesFormal)
                                    )
                        }
                    }
                }
            }

            // HACK: Treat all functional types with equivalent signatures as equivalent
            // until we have removed enough conversions between StaticType and Type2
            // to not launder the specific functional interface
            val tSig = sigForFunctional(t)
            val uSig = sigForFunctional(u)
            if (tSig != null && tSig == uSig) {
                return true
            }

            if (t is FunctionType && u is NominalType) {
                return u == WellKnownTypes.anyValueType || u == WellKnownTypes.functionType
            }
            return false
        } finally {
            optimisticIfSubTypesRecursivelyReached.remove(infiniteLoopAvoidanceKey)
        }
    }

    /**
     * If we're comparing two types like
     * *     fn<T__0>(T__0): T__0
     *
     * and
     * *     fn<T__1>(T__1): T__1
     *
     * the names of the formals do not matter.
     *
     * We substitute the names so that we can do structural comparison when sub-typing.
     */
    private fun formalCompatibleFunctionType(
        t: FunctionType,
        compatibleTypeFormals: List<TypeFormal>,
    ): FunctionType? {
        val typeFormals = t.typeFormals
        if (compatibleTypeFormals.size != typeFormals.size) {
            return null
        }
        if (typeFormals.isEmpty()) { return t }
        val compatibleTypeFormalMap = mutableMapOf<TypeFormal, TypeFormal>()
        for (i in typeFormals.indices) {
            compatibleTypeFormalMap[typeFormals[i]] = compatibleTypeFormals[i]
        }
        return MkType.map(
            t,
            object : TypePartMapper {
                override fun mapType(t: StaticType): StaticType = t

                override fun mapBinding(b: TypeActual): TypeActual = b

                override fun mapDefinition(d: TypeDefinition): TypeDefinition =
                    compatibleTypeFormalMap[d] ?: d
            },
            mutableMapOf(),
        ) as FunctionType
    }

    private data class SubBindingCheckRecord(
        val b: TypeActual,
        val c: TypeActual,
        val formalIndex: Int,
        val typeDef: TypeDefinition,
    )
    private val optimisticIfBindingsRecursivelyReached =
        mutableSetOf<SubBindingCheckRecord>()

    /**
     * @param b The potential sub-binding
     * @param c The potential super-binding
     * @param formalIndex The index such that b and c should
     *     be compatible with `typeDef.formals[formalIndex]`.
     *     This function may assume that b and c are compatible.
     *     That assumption is checked separately.
     */
    private fun isSubBinding(
        b: TypeActual,
        c: TypeActual,
        formalIndex: Int,
        typeDef: TypeDefinition,
    ): Boolean {
        if (b == c) { // Handles value bindings
            return true
        }
        if (b is InfiniBinding || c is InfiniBinding) {
            val subBindingCheckRecord = SubBindingCheckRecord(b, c, formalIndex, typeDef)
            if (subBindingCheckRecord in optimisticIfBindingsRecursivelyReached) {
                return true
            }
            val bf = if (b is InfiniBinding) b.get() else b
            val cf = if (c is InfiniBinding) c.get() else c
            optimisticIfBindingsRecursivelyReached.add(subBindingCheckRecord)
            try {
                return isSubBinding(bf, cf, formalIndex, typeDef)
            } finally {
                optimisticIfBindingsRecursivelyReached.remove(subBindingCheckRecord)
            }
        }
        if (b is StaticType && c is StaticType) {
            val formal = typeDef.formals[formalIndex]
            return when (formal.variance) {
                Variance.Invariant -> false // b != c above
                Variance.Covariant -> isSubType(b, c)
                Variance.Contravariant -> isSubType(c, b)
            }
        }
        if (c is Wildcard) {
            // We don't need to perform bound checks because we separately check whether actual
            // bindings are compatible with formals.
            return true
        }
        return false
    }

    /**
     * Turn any OrType instances beyond Bubble or Null into common supertypes.
     * The initial implementation is limited and needs thoroughly reviewed, tested, and presumably worked over.
     * If non-nominal types are included, there might be unwanted results. Other edge cases might also exist.
     * TODO Is there already some functionality elsewhere that does this?
     */
    fun simplifyOrTypes(type: StaticType): StaticType {
        fun simplifyOrType(orType: OrType): StaticType {
            val (nominals, others) = orType.members.partition { member ->
                when (member) {
                    is BubbleType -> false
                    is NominalType -> when (member.definition) {
                        WellKnownTypes.nullTypeDefinition -> false
                        else -> true
                    }

                    else -> false
                }
            }

            @Suppress("UNCHECKED_CAST")
            val common = when {
                nominals.isEmpty() -> null // such as `Null | Bubble`
                else -> leastCommonSuperType(nominals as List<NominalType>)
            }
            return when (others.isEmpty()) {
                true -> common!!
                false -> MkType.or(common?.let { listOf(it) + others } ?: others)
            }
        }
        return when (type) {
            is NominalType -> MkType.nominal(
                type.definition,
                bindings = type.bindings.map { actual ->
                    when (actual) {
                        is StaticType -> simplifyOrTypes(actual)
                        else -> actual
                    }
                },
            )

            is OrType -> simplifyOrType(type)
            else -> type
        }
    }
}

private fun sigForFunctional(t: StaticType): Signature2? {
    when (t) {
        is FunctionType -> {}
        is NominalType -> {
            if (functionalInterfaceSymbol !in t.definition.metadata) {
                return null
            }
        }
        else -> return null
    }
    return withType(
        hackMapOldStyleToNew(t),
        fallback = { null },
        fn = { _, sig, _ -> sig },
    )
}
