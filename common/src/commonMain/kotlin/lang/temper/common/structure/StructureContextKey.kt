package lang.temper.common.structure

/**
 * A key that allows looking up some context that allows different parts of a destructuring
 * operation to coordinate.
 */
interface StructureContextKey<T : Any> {
    /**
     * Runtime cast of a value that is should be associated with this key to its parametric type.
     */
    fun asValueTypeOrNull(x: Any): T?
}
