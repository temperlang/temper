package lang.temper.common

import lang.temper.common.structure.Structured

/**
 * When refining an intermediate representation, we may want to periodically snapshot the state.
 * This allows the REPL to record processing steps and play them back to the user on demand, but
 * is expensive during normal operation, so this is normally a no-op.
 *
 * A tool like the REPL may register one of these with a [Console], typically one configured
 * via [Debug].
 */
interface Snapshotter {
    /**
     * @param stepId A database key text used to distinguish this from other snapshots.
     */
    fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR)
}

/**
 * `object`s may extend this to define a type-safe way for [Snapshotter] implementations to accept
 * a state, do some work to store it, and then return control so that processing may continue.
 */
interface SnapshotKey<IR : Structured> {
    /**
     * A key that may be used to identify this kind of snapshot in a database,
     * possibly in conjunction with an identifier for the processing stage, like `stepId` above.
     *
     * This is meant to allow generic snapshotting.
     */
    val databaseKeyText: String

    /**
     * [Snapshotter]s may use this method when given a key they know about to cast the state in a
     * type-safe way.
     *
     *     MyFavoriteKey.useIfSame(key, state) { stateNarrowlyTyped -> ... }
     *         ?: // try other keys or a generic strategy
     */
    fun <IR2 : Structured, O : Any> useIfSame(
        other: SnapshotKey<IR2>,
        state: IR2,
        f: (IR) -> O,
    ): O? =
        if (this == other) {
            @Suppress("UNCHECKED_CAST") // Sound when IR does not have nested type parameters
            val stateCast = state as IR
            f(stateCast)
        } else {
            null
        }
}
