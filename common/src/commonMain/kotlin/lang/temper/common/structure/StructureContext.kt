package lang.temper.common.structure

interface StructureContext {
    /** Retrieve the context value associated with the current structuring operation. */
    fun <T : Any> context(key: StructureContextKey<T>): T?
}
