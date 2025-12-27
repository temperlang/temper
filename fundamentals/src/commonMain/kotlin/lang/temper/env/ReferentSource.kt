package lang.temper.env

/** The source of values referenced by a [name][lang.temper.name.Name]. */
enum class ReferentSource {
    Unknown,

    /**
     * Results of evaluating the reference come from a single, statically identified sub-tree.
     * See also caveats on [`\ssa`][lang.temper.value.ssaSymbol].
     */
    SingleSourceAssigned,
}
