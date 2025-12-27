package lang.temper.value

import lang.temper.common.Log
import lang.temper.common.mutableIdentityMapOf
import lang.temper.common.mutableWeakIdentityMapOf
import lang.temper.common.putMultiList
import lang.temper.common.subListToEnd
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned

/**
 * A promise is represented during interpretation as an instance of the property class
 */
typealias PromiseKey = InstancePropertyRecord

/**
 * For an interpreter run, keeps track of promise resolutions.
 */
class Promises {
    // Promises are partitioned into two groups:
    // - resolved promises for which we need to track the resolution
    // - unresolved promises for which we may need to generate a diagnostic message if failure to resolve
    //   causes the computation to hang
    private val resolved = mutableWeakIdentityMapOf<PromiseKey, Result>()
    private val unresolved = mutableIdentityMapOf<PromiseKey, Position>()

    // For each promise, keep track of which coroutines should be scheduled when it's resolved
    private val awaiting = mutableIdentityMapOf<PromiseKey, MutableList<Awaiter>>()
    private val ready = ArrayDeque<Awaiter>()

    /** When a promise is created, register the call site for diagnostic purposes */
    fun registerNewPromise(pk: PromiseKey, creationPos: Position) {
        unresolved[pk] = creationPos
    }

    /**
     * resolve a promise (and its associated future)
     * @return true if the resolution happens, or
     *   false if it didn't because there was already a resolution or [pk] was
     *   never passed to [registerNewPromise].
     */
    fun resolve(pk: PromiseKey, resolution: Result): Boolean =
        if (unresolved.remove(pk) != null) {
            // Its important that second and subsequent calls to
            // PromiseBuilder's resolve methods do not clobber
            // the first, because resolution stability is a semantic
            // guarantee.
            resolved[pk] = resolution
            awaiting.remove(pk)?.let {
                ready.addAll(it)
            }
            true
        } else {
            false
        }

    /**
     * Called to register an awaiter to be added to the ready list when [pk] is resolved.
     */
    fun await(
        pk: PromiseKey,
        awaiter: Awaiter,
        /**
         * The position of the await call that is blocking.
         * This is used if interpretation hangs to show who is waiting for what.
         */
        awaitPos: Position,
    ) {
        if (pk in unresolved) {
            awaiting.putMultiList(pk, awaiter.withPosition(awaitPos))
        }
    }

    val unresolvedPromisePositions get() = buildSet {
        addAll(unresolved.values)
    }

    /** The diagnostic position of the promise blocking the given function */
    fun unresolvedOn(pausedFn: CallableValue?): Position? {
        for ((pk, awaiters) in awaiting) {
            for (awaiter in awaiters) {
                if (awaiter.stepFunction === pausedFn) {
                    val pos = unresolved[pk]
                    if (pos != null) { return pos }
                }
            }
        }
        return null
    }

    val numUnresolved get() = unresolved.size

    /** null if pk is unresolved, otherwise, its resolution */
    operator fun get(pk: PromiseKey): Result? = resolved[pk]

    /**
     * Returns and consumes the next ready task.
     */
    fun nextReadyTask() = ready.removeFirstOrNull()

    /**
     * Enqueues a task so that [nextReadyTask] will include it after
     * any previously enqueued.
     *
     * See [lang.temper.interp.Interpreter.interpretAndWaitForAsyncTasksToSettle]
     */
    fun enqueueReadyTask(awaiter: Awaiter) {
        ready.add(awaiter)
    }

    /**
     * An awaiter can be called to run a turn when a promise resolves.
     */
    data class Awaiter(
        override val pos: Position,
        val stepFunction: MacroValue,
    ) : Positioned {
        fun withPosition(pos: Position): Awaiter = copy(pos = pos)
    }
}

fun Promises.warnAboutUnresolved(logSink: LogSink) {
    val unresolved = unresolvedPromisePositions.toList()
    if (unresolved.isNotEmpty()) {
        val first = unresolved.first()
        val rest = if (unresolved.size != 1) {
            unresolved.subListToEnd(1)
        } else {
            null
        }
        logSink.log(
            Log.Warn,
            MessageTemplate.RunEndedWithUnresolvedPromise,
            first,
            listOfNotNull(rest),
        )
    }
}
