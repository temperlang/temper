package lang.temper.type2

import lang.temper.log.Positioned
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type2.Nullity.OrNull

/**
 * Returns this type but with any type formals in [m] replaced with the given actual bindings.
 *
 * So given `List<T__0>`, if the map relates `T__0` to `Int32`, returns `List<Int32>`.
 *
 * Values in the map are not recursively mapped, so if the map includes the entry
 * `T__0 to List<T__0>` then the inner `T__0` will not be expanded, and applied to
 * `List<T__0>` would return `List<List<T__0>>`.
 * This is the correct behaviour for recursive calls to generic functions.
 */
fun Type2.mapType(m: Map<TypeFormal, Type2>): Type2 =
    if (m.isEmpty()) {
        this
    } else {
        TypeBindingMapper(emptyMap(), m).map(this) as Type2
    }

/**
 * Like [Type2.mapType] but for partial types.
 */
fun TypeOrPartialType.mapType(m: Map<TypeVar, TypeLike>): TypeOrPartialType = if (m.isEmpty()) {
    this
} else {
    TypeBindingMapper(m, emptyMap()).map(this) as TypeOrPartialType
}

fun TypeLike.mapType(
    varMap: Map<TypeVar, TypeLike>,
    defnMap: Map<TypeFormal, TypeLike>,
): TypeLike = if (varMap.isEmpty() && defnMap.isEmpty()) {
    this
} else {
    TypeBindingMapper(varMap, defnMap).mapTypeLike(this)
}

fun TypeLike.mapType(
    varMapper: (TypeVar) -> TypeLike?,
    defnMapper: (TypeFormal) -> TypeLike?,
): TypeLike = TypeBindingMapper(varMapper, defnMapper).mapTypeLike(this)

private class TypeBindingMapper(
    private val varMapper: (TypeVar) -> TypeLike?,
    private val defnMapper: (TypeFormal) -> TypeLike?,
) {
    constructor(
        varMap: Map<TypeVar, TypeLike>,
        defnMap: Map<TypeFormal, TypeLike>,
    ) : this({ varMap[it] }, { defnMap[it] })

    fun mapBinding(b: TypeLike) = when (b) {
        is TypeVarRef -> varMapper(b.typeVar)?.combineNullity(b.nullity) ?: b
        is TypeOrPartialType -> map(b)
    }

    fun map(t: TypeOrPartialType): TypeLike {
        val definition = t.definition
        if (definition is TypeFormal) {
            val remappedDefinition = defnMapper(definition)
            if (remappedDefinition != null) {
                return remappedDefinition.combineNullity(t.nullity)
            }
        }

        var allTypes = true
        val typeParameters = t.bindings.map { binding ->
            mapBinding(binding).also {
                if (it !is Type2) {
                    allTypes = false
                }
            }
        }
        val nullity = t.nullity
        val pos = (t as? Positioned)?.pos
        return if (allTypes) {
            val actuals = typeParameters.map { it as Type2 }
            when (val d = t.definition) {
                is TypeShape -> MkType2(d).actuals(actuals)
                is TypeFormal -> MkType2(d)
            }.nullity(nullity)
                .position(pos)
                .get()
        } else {
            when (val d = t.definition) {
                is TypeShape -> PartialType.from(d, typeParameters, nullity, pos)
                is TypeFormal -> PartialType.from(d, nullity, pos)
            } as PartialType
        }
    }

    fun mapTypeLike(t: TypeLike) = when (t) {
        is TypeOrPartialType -> map(t)
        is TypeVarRef -> mapBinding(t)
    }
}

private fun TypeLike.combineNullity(originalNullity: Nullity): TypeLike {
    val nullity = this.nullity
    return if (nullity == OrNull) {
        this
    } else if (originalNullity == OrNull) {
        withNullity(OrNull)
    } else {
        this
    }
}
