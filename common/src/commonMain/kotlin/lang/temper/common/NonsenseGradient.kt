package lang.temper.common

/**
 * Allows heuristically partitioning items and choosing the most reliable grouping.
 * For example, use [NotSuss] items if we have them, but fall back to [PossibleNonsese]
 * where we don't.
 */
enum class NonsenseGradient {
    TotalNonsense,
    ProbableNonsense,
    PossibleNonsense,
    NotSuss,
}
