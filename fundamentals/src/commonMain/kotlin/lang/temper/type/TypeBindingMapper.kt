package lang.temper.type

import lang.temper.name.ResolvedName

class TypeBindingMapper(private val formalNameToActual: Map<ResolvedName, TypeActual>) : TypePartMapper {
    constructor(formalToActual: Iterable<Map.Entry<TypeFormal, TypeActual>>) : this(
        buildMap {
            for (e in formalToActual) {
                this[e.key.name] = e.value
            }
        },
    )

    override fun mapType(t: StaticType): StaticType =
        if (t is NominalType && t.bindings.isEmpty()) {
            formalNameToActual[t.definition.name] as? StaticType ?: t
        } else {
            t
        }

    override fun mapBinding(b: TypeActual): TypeActual =
        if (b is NominalType && b.bindings.isEmpty()) {
            formalNameToActual[b.definition.name] ?: b
        } else {
            b
        }

    override fun mapDefinition(d: TypeDefinition) = d
}
