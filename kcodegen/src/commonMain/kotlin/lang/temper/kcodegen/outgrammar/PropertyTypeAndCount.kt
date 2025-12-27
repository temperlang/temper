package lang.temper.kcodegen.outgrammar

internal data class PropertyTypeAndCount(
    val type: PropertyType,
    val count: PropertyCount,
) {
    fun toKotlinCode(mutability: Mutability, forCopy: Boolean) =
        type.toKotlinCode(count, mutability, forCopy)
}
