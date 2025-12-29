package lang.temper.common.structure

/**
 * Allows emitting key/value pairs as part of a [Structured] object ([StructureSink.obj]).
 */
interface PropertySink : StructureContext {
    /**
     * Specifies a key/value pair.
     *
     * @param key the name of the property.
     * @param emitValue should do one call to [StructureSink.value], [StructureSink.arr],
     *     [StructureSink.obj], or [StructureSink.nil] to specify the value.
     * @param hints scoped to this property.  See [StructureHint] for the meaning of each.
     */
    fun key(
        key: String,
        hints: Set<StructureHint> = emptySet(),
        emitValue: (StructureSink).() -> Unit,
    )

    /**
     * Specifies a key/value pair.
     *
     * @param key the name of the property.
     * @param emitValue should do one call to [StructureSink.value], [StructureSink.arr],
     *     [StructureSink.obj], or [StructureSink.nil] to specify the value.
     * @param hints scoped to this property.  See [StructureHint] for the meaning of each.
     * @param isDefault true if the property's value is the default value.  When true,
     *     hints implicitly contains [StructureHint.Unnecessary].
     */
    fun key(
        key: String,
        hints: Set<StructureHint> = emptySet(),
        isDefault: Boolean,
        emitValue: (StructureSink).() -> Unit,
    ) = key(
        key = key,
        hints = if (isDefault) {
            hints + StructureHint.Unnecessary
        } else {
            hints
        },
        emitValue = emitValue,
    )

    /**
     * Specifies a key/value pair.
     *
     * @param key the name of the property.
     * @param emitValue should do one call to [StructureSink.value], [StructureSink.arr],
     *     [StructureSink.obj], or [StructureSink.nil] to specify the value.
     * @param h0 a hint scoped to this property.
     * @param isDefault true if the property's value is the default value.  When true,
     *     hints implicitly contains [StructureHint.Unnecessary].
     */
    fun key(
        key: String,
        h0: StructureHint,
        isDefault: Boolean = false,
        emitValue: StructureSink.() -> Unit,
    ) = key(
        key = key,
        hints = if (isDefault) setOf(h0, StructureHint.Unnecessary) else setOf(h0),
        emitValue = emitValue,
    )

    /**
     * Specifies a key/value pair.
     *
     * @param key the name of the property.
     * @param emitValue should do one call to [StructureSink.value], [StructureSink.arr],
     *     [StructureSink.obj], or [StructureSink.nil] to specify the value.
     * @param h0 a hint scoped to this property.
     * @param h1 a hint scoped to this property.
     * @param isDefault true if the property's value is the default value.  When true,
     *     hints implicitly contains [StructureHint.Unnecessary].
     */
    fun key(
        key: String,
        h0: StructureHint,
        h1: StructureHint,
        isDefault: Boolean = false,
        emitValue: StructureSink.() -> Unit,
    ) = key(
        key = key,
        hints = if (isDefault) { setOf(h0, h1, StructureHint.Unnecessary) } else { setOf(h0, h1) },
        emitValue = emitValue,
    )
}
