package lang.temper.value

/**
 * <!-- snippet: builtin/void -->
 * # *void*
 * The value `void` is the sole value in type [snippet/type/Void].
 *
 * **WARNING**: To interface better across idiomatic void behavior in backends,
 * current plans are to make `Void` disjoint from `AnyValue` such that no
 * `void` value is usable. See also [issue#38].
 */
val void = TVoid.value
