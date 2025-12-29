package lang.temper.value

import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.StaticType
import lang.temper.type2.Nullity
import lang.temper.type2.withType

/**
 * The value contained at this tree or `null`.
 * The value contained is [ValueLeaf.content] if this is a [ValueLeaf].
 * It is the second child's contained value if this is a call to [PreserveFn].
 */
val Tree.valueContained: Value<*>? get() = when (this) {
    is ValueLeaf -> content
    is CallTree -> {
        if (size == PRESERVE_FN_CALL_SIZE && child(0).valueContained(TFunction) is PreserveFn) {
            (child(2) as? ValueLeaf)?.content
        } else {
            null
        }
    }
    else -> null
}

/**
 * The value contained [unpacked][TypeTag.unpackOrNull] using [rt] or `null`.
 * The value contained is [ValueLeaf.content] if this is a [ValueLeaf].
 * It is the second child's contained value if this is a call to [PreserveFn].
 */
fun <T : Any> Tree.valueContained(rt: TypeTag<T>): T? {
    return rt.unpackOrNull(valueContained)
}

/**
 * Like [valueContained] for [TSymbol] but does not look through preserve since symbols need to
 * be literally provided to be keys.
 */
val Tree.symbolContained: Symbol?
    get() = TSymbol.unpackOrNull((this as? ValueLeaf)?.content)

/** [valueContained] for [TFunction] */
val Tree.functionContained: MacroValue? get() = valueContained(TFunction)

/** [valueContained] for [TBoolean] */
val Tree.booleanContained: Boolean? get() = valueContained(TBoolean)

/** [valueContained] for [TType] */
val Tree.reifiedTypeContained: ReifiedType? get() = valueContained(TType)

val Tree.staticTypeContained: StaticType?
    get() = this.reifiedTypeContained?.type

val Tree.nameContained: TemperName? get() = (this as? NameLeaf)?.content

fun Tree.typeDefContained() = reifiedTypeContained?.type2?.let {
    withType(
        it,
        never = { _, _, _ -> null },
        result = { _, _, _ -> null },
        fallback = { t ->
            if (t.nullity == Nullity.NonNull) { t.definition } else { null }
        },
    )
}
fun Tree.typeNameContained() = typeDefContained()?.name

// Helpers for edges.

val TEdge.valueContained get() = this.target.valueContained
val TEdge.symbolContained get() = this.target.symbolContained
val TEdge.booleanContained get() = this.target.booleanContained
val TEdge.functionContained get() = this.target.functionContained
val TEdge.reifiedTypeContained get() = this.target.reifiedTypeContained
val TEdge.staticTypeContained get() = this.target.staticTypeContained
val TEdge.nameContained get() = this.target.nameContained
fun <T : Any> TEdge.valueContained(rt: TypeTag<T>): T? {
    return rt.unpackOrNull(valueContained)
}
