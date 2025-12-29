package lang.temper.type

import lang.temper.type2.DefinedType
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.withNullity
import lang.temper.type2.withType

/** `T?` -> `T` */
fun excludeNull(t: StaticType): StaticType =
    excludeAtom(t) { it.isNullType }

/** `T throws Bubble` -> `T` */
fun excludeBubble(t: StaticType): StaticType =
    excludeAtom(t) { it == BubbleType }

/** `T throws Bubble` -> `T` */
fun excludeBubble(t: Type2): Type2 =
    if (t is DefinedType && t.definition == WellKnownTypes.resultTypeDefinition) {
        t.bindings.getOrNull(0)
    } else {
        null
    } ?: t

/** The composition of [excludeNull] and [excludeBubble] */
fun excludeNullAndBubble(t: StaticType): StaticType =
    excludeAtom(t) { it is BubbleType || it.isNullType }

/** The composition of [excludeNull] and [excludeBubble] */
fun excludeNullAndBubble(t: Type2): Type2 = withType(
    t,
    fallback = { it.withNullity(Nullity.NonNull) },
    result = { pass, _, _ -> pass.withNullity(Nullity.NonNull) },
)

fun canBeNull(t: StaticType): Boolean = anyAtom(t) {
    when (val d = (it as? NominalType)?.definition) {
        is TypeFormal -> {
            SuperTypeTree(d.superTypes)[WellKnownTypes.nullTypeDefinition].isNotEmpty()
        }
        is TypeShapeImpl ->
            d == WellKnownTypes.nullTypeDefinition ||
                d == WellKnownTypes.anyValueTypeDefinition
        null -> false
    }
}

fun excludeAtom(t: StaticType, exclude: (StaticType) -> Boolean): StaticType {
    if (exclude(t)) {
        return OrType.emptyOrType
    }
    return when (t) {
        is FunctionType,
        BubbleType,
        InvalidType,
        TopType,
        -> t
        is AndType,
        is OrType,
        -> {
            val isAnd = t is AndType
            val members = if (isAnd) { t.members } else { (t as OrType).members }
            var membersExcluded: MutableList<StaticType>? = null
            for ((i, m) in members.withIndex()) {
                val mp = excludeAtom(m, exclude)
                if (m !== mp && membersExcluded == null) {
                    membersExcluded = mutableListOf()
                    for ((j, om) in members.withIndex()) {
                        if (j == i) { break }
                        membersExcluded.add(om)
                    }
                }
                membersExcluded?.add(mp)
            }
            if (membersExcluded == null) {
                t
            } else if (isAnd) {
                MkType.and(membersExcluded)
            } else {
                MkType.or(membersExcluded)
            }
        }
        is NominalType -> t
    }
}

fun anyAtom(t: StaticType, match: (StaticType) -> Boolean): Boolean {
    if (match(t)) { return true }
    return when (t) {
        is OrType -> t.members.any { anyAtom(it, match) }
        is AndType -> t.members.any { anyAtom(it, match) }
        InvalidType,
        BubbleType,
        is FunctionType,
        is NominalType,
        TopType,
        -> false
    }
}

fun mapTypeAtomsThroughIntersection(t: StaticType, m: (StaticType) -> StaticType): StaticType =
    when (t) {
        is AndType -> {
            val newMembers = t.members.map {
                mapTypeAtomsThroughIntersection(it, m)
            }
            MkType.and(newMembers)
        }
        else -> m(t)
    }

fun mapFunctionTypesThroughIntersection(t: StaticType, m: (FunctionType) -> StaticType): StaticType =
    mapTypeAtomsThroughIntersection(t) {
        if (it is FunctionType) { m(it) } else { it }
    }

fun extractNominalTypes(t: StaticType) = extractAtoms(t) {
    it as? NominalType
}

fun <T : StaticType> extractAtoms(
    t: StaticType,
    matchesOrNull: (StaticType) -> T?,
): Set<T> = buildSet {
    extractAtomsOnto(t, this, matchesOrNull)
}

fun <T : StaticType> extractAtomsOnto(
    t: StaticType,
    out: MutableCollection<T>,
    matchesOrNull: (StaticType) -> T?,
) {
    val x = matchesOrNull(t)
    if (x != null) {
        out.add(x)
    } else {
        when (t) {
            InvalidType,
            BubbleType,
            is FunctionType,
            is NominalType,
            TopType,
            -> {}
            is AndType -> t.members.forEach { extractAtomsOnto(it, out, matchesOrNull) }
            is OrType -> t.members.forEach { extractAtomsOnto(it, out, matchesOrNull) }
        }
    }
}
