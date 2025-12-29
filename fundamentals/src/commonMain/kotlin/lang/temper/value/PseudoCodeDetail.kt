package lang.temper.value

import lang.temper.common.NoneShortOrLong

/**
 * How much detail to show when [rendering pseudocode][toPseudoCode].
 */
data class PseudoCodeDetail(
    /** Whether to show [inferred types][TypeInferences] */
    val showInferredTypes: Boolean = false,
    /** Whether to replace function bodies with `...` */
    val elideFunctionBodies: Boolean = false,
    /** How much [docStringSymbol] metadata to show. */
    val docStringDetail: NoneShortOrLong = NoneShortOrLong.None,
    /**
     * If false and the top-level tree is a block, skip its curly
     * brackets (`{` and `}`) to present its content as top-levels.
     */
    val preserveOuterCurlies: Boolean = false,
    /**
     * Whether to show metadata like [propertySymbol] and [methodSymbol]
     * on declaration extracted from type declarations.
     */
    val showTypeMemberMetadata: Boolean = false,
    /**
     * Whether [lang.temper.name.QName]s should be shown as decorations
     * on declarations. They're shown if this bit is true or if [verboseMetadata]
     * is long.
     */
    val showQNames: Boolean = false,
    /**
     * Whether to show interstitial comments marking the region boundaries of
     * `orelse`.
     */
    val showFlowMarkers: Boolean = false,
    /**
     * How to show declaration metadata that is often spammy like [ssaSymbol].
     *
     * - [NoneShortOrLong.None]: silently ignore spammy metadata
     * - [NoneShortOrLong.Short]: show a `@...` when there is ignored metadata
     * - [NoneShortOrLong.Long]: show the spammy metadata
     */
    val verboseMetadata: NoneShortOrLong = NoneShortOrLong.None,
    /**
     * False to leave *DotHelper* calls as `do_get_i(subject)` instead of
     * resugaring them to `subject.i`.
     */
    val resugarDotHelpers: Boolean = false,
    /**
     * True for any tree that should be marked with comments to draw viewers
     * attention.
     *
     * This aids debugging by allowing highlighting a particular node in the
     * context of a larger code block that might lexically match other nodes.
     */
    val highlight: ((Tree) -> Boolean)? = null,
) {
    companion object {
        val default = PseudoCodeDetail()
    }
}
