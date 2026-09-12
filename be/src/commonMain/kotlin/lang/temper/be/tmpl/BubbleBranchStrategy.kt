package lang.temper.be.tmpl

/** Strategy for how to decompile *Bubble* branches. */
enum class BubbleBranchStrategy {
    /**
     * Calls that can bubble are represented using
     * [*Result*][lang.temper.type.WellKnownTypes.resultTypeDefinition] types.
     *
     *     bubblyFn(x)
     *
     * That becomes:
     *
     *     let r#123: Result<...> = bubbleFn(x);
     *     if (!isOkResult(r#123)) { return r#123 }
     *
     * That `return#123` works if the result is not handled locally, bubbling
     * the result up to the caller, but if this happens in the left of an
     * [*orelse*][lang.temper.value.ControlFlow.OrElse], we instead rewrite to
     * a local jump like: `break orelse#234`.
     *
     * If the result is assigned to a local, we need to switch it around a bit.
     *
     *     y = bubbleFn(x)
     *
     * We need to unwrap the result before assigning to `y`.
     *
     *     let r#123: Result<...> = bubbleFn(x);
     *     if (!isOkResult(r#123)) {
     *       return r#123
     *     } else {
     *       y = unpackResult(r#123);
     *     }
     *
     * The *GenerateCodeStage* ensures that backends will not
     * encounter other patterns of bubbling calls; it captures
     * bubbling calls in temporaries to avoid bubble calls nested
     * in other calls.
     *
     * @see ResultHelperFnPlaceholders
     */
    Results,

    /**
     * Bubbles are treated as exceptions, so the left side of each
     * [*orelse*][lang.temper.value.ControlFlow.OrElse] needs to be wrapped
     * in a [`try` and the right side forms the `catch` clause][TmpL.TryStatement].
     *
     * There is no need to explicitly unpack results as for [Results].
     */
    Exceptions,
}
