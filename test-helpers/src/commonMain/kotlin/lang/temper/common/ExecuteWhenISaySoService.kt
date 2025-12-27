package lang.temper.common

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** An executor service that executes tasks when told to instead of using worker threads. */
class ExecuteWhenISaySoService : AbstractExecutorService() {
    private val pending = ArrayDeque<Runnable>()
    private var isShutdown = false
    private var isTerminated = false

    /** Execute a task that has been removed from the pending list and update status bits. */
    private fun performTask(task: Runnable) {
        task.run()
        if (isShutdown && pending.isEmpty()) {
            isTerminated = true
        }
    }

    fun hasPendingTask() = pending.isNotEmpty()

    fun performFirstPending() {
        pending.removeFirstOrNull()?.let { performTask(it) }
    }

    fun performPendingTasks() {
        var nToRun = pending.size
        // A task may enqueue new tasks, but we were only asked to execute the
        // ones that were enqueued at the start of this call.
        while (nToRun != 0) {
            performTask(pending.removeFirst())
            if (isTerminated) { break }
            nToRun -= 1
        }
    }

    fun performTasksUntilNonePending() {
        while (pending.isNotEmpty() && !isTerminated) {
            performTask(pending.removeFirst())
        }
    }

    override fun execute(command: Runnable) {
        check(!isShutdown)
        pending.add(command)
    }

    override fun shutdown() {
        isShutdown = true
        if (pending.isEmpty()) { isTerminated = true }
    }

    override fun shutdownNow(): MutableList<Runnable> {
        val notExecuted = pending.toMutableList()
        pending.clear()
        shutdown()
        return notExecuted
    }

    override fun isShutdown(): Boolean = isShutdown

    override fun isTerminated(): Boolean = isTerminated

    /** @throws UnsupportedOperationException */
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        if (isTerminated) { return true }
        throw UnsupportedOperationException()
    }
}
