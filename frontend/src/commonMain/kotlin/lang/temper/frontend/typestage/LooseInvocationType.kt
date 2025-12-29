package lang.temper.frontend.typestage

import lang.temper.type.FunctionType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.TypeActual
import lang.temper.type.TypeDefinition
import lang.temper.type.TypePartMapper
import lang.temper.type.Wildcard

/** Replaces all type actuals with [Wildcard] */
internal fun looseType(t: StaticType): StaticType {
    return MkType.map(t, LooseTypeMapper)
}

private object LooseTypeMapper : TypePartMapper {
    override fun mapType(t: StaticType): StaticType =
        if (t is FunctionType && t.typeFormals.isNotEmpty()) {
            MkType.fnDetails(
                typeFormals = emptyList(),
                valueFormals = t.valueFormals,
                restValuesFormal = t.restValuesFormal,
                returnType = t.returnType,
            )
        } else {
            t
        }

    override fun mapBinding(b: TypeActual): TypeActual = Wildcard

    override fun mapDefinition(d: TypeDefinition): TypeDefinition = d
}
