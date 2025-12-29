package lang.temper.lexer

/**
 * The kind of Temper processing being performed.
 */
enum class Genre {
    /**
     * Temper code of this genre is meant to be translated into library code in target languages,
     * and/or test library code.
     *
     * The processing pipeline may introduce [temporary variables][lang.temper.name.Temporary],
     * move code, and eliminate code as necessary to preserve semantic consistency.
     *
     * Inputs in this genre have minimal syntactic constraints.
     *
     * The pipeline need make no attempt to preserve comments in code in this genre.
     */
    Library,

    /**
     * Temper code of this genre shows how to use (or how not to use) a library.
     *
     * It is meant to be translated to nice-looking, idiomatic code in target languages.
     *
     * The processing pipeline must not introduce confusing temporary variables,
     * make drastic changes to code structure, or lose comments.
     *
     * The pipeline may ignore minor semantic distinctions.
     * Target languages including (Java, JS) treat `<`, applied to strings, as comparing by UTF-16
     * code-unit, so `"\uFFFF" > "\uD800\uDC00"` is true; but other languages compare by code-point
     * causing that comparison to be false.
     * In this genre, the pipeline may assume that `oneString < anotherString` uses a *notional*
     * comparison operator and ignore that distinction when translating that phrase to idiomatic
     * target languages' code.
     */
    Documentation,
}
