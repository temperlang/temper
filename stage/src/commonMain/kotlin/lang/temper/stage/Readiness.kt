package lang.temper.stage

enum class Readiness {
    /** Not available and/or satisfied. */
    Unready,

    /** Ready for use. */
    Ready,

    /** Possibly not ready, but will have [Evaporated] before any next opportunity to use. */
    GoingOutOfStyle,

    /** The time at which it might have been ready has passed. */
    Evaporated,
}
