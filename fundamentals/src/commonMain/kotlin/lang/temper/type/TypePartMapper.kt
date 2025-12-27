package lang.temper.type

interface TypePartMapper {
    fun mapType(t: StaticType): StaticType
    fun mapBinding(b: TypeActual): TypeActual
    fun mapDefinition(d: TypeDefinition): TypeDefinition
}
