package lang.temper.type

interface TypePartMapper {
    fun mapType(t: StaticType): StaticType
    fun mapDefinition(d: TypeDefinition): TypeDefinition
}
