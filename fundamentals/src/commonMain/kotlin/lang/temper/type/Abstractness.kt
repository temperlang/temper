package lang.temper.type

/** Whether a type or part thereof is abstract or concrete. */
enum class Abstractness {
    /**
     * An abstract type is one that cannot be created directly, often because parts of it are as-yet
     * unspecified.
     * An abstract member specifies that any concrete sub-type of the containing type must provide
     * a non-abstract realization of the member.
     */
    Abstract,

    /**
     * The opposite of [Abstract].  There may be instances of a concrete types that are not
     * also instances of some distinct sub-type.
     *
     * Concrete properties have storage space and are not just computed by reference to other
     * properties or methods.
     *
     * Concrete methods have a function body so concrete method definitions do not need overriding
     * in concrete types that have the containing type as a (non-strict) super-type.
     */
    Concrete,
}
