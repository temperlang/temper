package lang.temper.value

/** Describes the time at which a function should be invoked. */
enum class FunctionSpecies {
    /**
     * Macros should be evaluated during [partial][lang.temper.env.InterpMode.Partial] evaluation.
     * They operate on the syntax tree to modify it, and not to produce a result.
     */
    Macro,

    /**
     * There is a small, closed set of special functions that may be evaluated at any time.
     * They can be called as macros, or during full interpretation to produce a result
     * and may survive to runtime and so need to be translated by
     * backends.
     */
    Special,

    /**
     * It only makes sense to interpret a normal function [fully][lang.temper.env.InterpMode.Full].
     */
    Normal,

    /**
     * A pure function, like a [Normal] function, is meant to be evaluated fully for a result.
     * But pure functions may be evaluated out of order and their result is stable so may
     * be inlined in to the syntax tree.
     */
    Pure,
}
