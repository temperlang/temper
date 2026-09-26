package lang.temper.type

import lang.temper.name.ResolvedName

class TypeBindingMapper(private val formalNameToActual: Map<ResolvedName, StaticType>) : TypePartMapper {
    constructor(formalToActual: Iterable<Map.Entry<TypeFormal, StaticType>>) : this(
        buildMap {
            for (e in formalToActual) {
                this[e.key.name] = e.value
            }
        },
    )

    override fun mapType(t: StaticType): StaticType =
        if (t is NominalType && t.bindings.isEmpty()) {
            formalNameToActual[t.definition.name] ?: t
        } else {
            t
        }

    override fun mapDefinition(d: TypeDefinition) = d

    override fun toString(): String = "TypeBindingMapper($formalNameToActual)"
}
