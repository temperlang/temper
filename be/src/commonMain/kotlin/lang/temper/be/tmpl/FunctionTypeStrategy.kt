package lang.temper.be.tmpl

/**
 * Ways to represent types for function-like values.
 */
enum class FunctionTypeStrategy {
    /** Use ad-hoc types to represent like [TmpL.FunctionType]. Good for functional languages. */
    ToFunctionType,

    /**
     * Use an abstract type, often parameterized, to represent a function as a value.
     * For example, *Predicate< String >* might represent, as object values, functions that
     * take one string and return a boolean.
     */
    ToFunctionalInterface,
}
