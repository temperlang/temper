package lang.temper.fs

import lang.temper.common.currents.JoiningCancellable
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.jsonEscaper
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.joinPathTo

private interface ChildProcessSpecification {
    val cwd: FilePath
    val env: Map<String, String>
    val command: String
    val args: List<String>
}

abstract class ChildProcessBuilder(
    cwd: FilePath,
    override val command: String,
) : ChildProcessSpecification {
    private var _cwd = FilePath.emptyPath
    private val _env = mutableMapOf<String, String>()
    private val _args = mutableListOf<String>()
    protected val defaultCwd = cwd
    init {
        cd(cwd)
    }

    fun env(name: String, value: String) {
        _env[name] = value
    }

    fun arg(argText: String) {
        _args.add(argText)
    }

    fun cd(path: FilePath) {
        require(path.isDir) { "$_cwd" }
        for (pathSegment in path.segments) {
            _cwd = _cwd.resolve(pathSegment, isDir = true)
        }
    }

    override val cwd: FilePath get() = _cwd
    override val env: Map<String, String> get() = _env.toMap()
    override val args: List<String> get() = _args.toList()

    override fun toString(): String = stringifyChildProcess(this, defaultCwd = defaultCwd)
}

/**
 * A child process that has yet to be executed.
 */
abstract class PendingChildProcess(
    val defaultCwd: FilePath,
    override val cwd: FilePath,
    override val env: Map<String, String>,
    override val command: String,
    override val args: List<String>,
) : ChildProcessSpecification {
    abstract fun execute(): RunningChildProcess<Int>
    abstract fun executeCapturing(): RunningChildProcess<Pair<Int, String>>

    override fun toString(): String = stringifyChildProcess(this, defaultCwd = defaultCwd)
}

abstract class RunningChildProcess<R : Any>(
    val exitFuture: RFuture<R, Nothing>,
) : JoiningCancellable {
    /**
     * Sends a SIGHUP to the process to indicate its result is no longer
     * needed, but we still need to know when shutdown occurs.
     *
     * Cancelling [exitFuture] means we no longer care about exit.
     * So that sends SIGHUP to the underlying process and completes with a
     * cancellation exception possibly before the process exits.
     *
     * This is different.  Cancelling the process when you still care about
     * when it exits.
     *
     * This allows for waiting for clean shutdown by the program so that code
     * that chains from the result future gets a result, which may be an error
     * exit code, and can itself shut down cleanly.
     *
     * See also OutputFileManager's lease management.
     */
    abstract override fun cancelSignalling(mayInterruptIfRunning: Boolean): SignalRFuture
}

private fun stringifyChildProcess(
    cp: ChildProcessSpecification,
    defaultCwd: FilePath,
) = buildString {
    val cwd = cp.cwd
    if (defaultCwd != cwd) { // This can be long and in the common case, it's irrelevant.
        append("cd ")
        defaultCwd.relativePathTo(cwd).joinPathTo(isDir = cwd.isDir, sb = this)
        append(" && ")
    }
    cp.env.forEach {
        maybeQuoteOnto(it.key)
        append('=')
        maybeQuoteOnto(it.value)
        append(' ')
    }
    maybeQuoteOnto(cp.command)
    for (arg in cp.args) {
        append(' ')
        maybeQuoteOnto(arg)
    }
}

private fun StringBuilder.maybeQuoteOnto(s: String) {
    if (s.isEmpty() || s.any { it.isShellyMetaChar }) {
        jsonEscaper.escapeTo(s, this)
    } else {
        append(s)
    }
}

private val Char.isShellyMetaChar: Boolean
    get() = when (this) {
        in '\u0000'..' ' -> true // Control codes and space
        '.', '/', '-', '_' -> false
        in '0'..'9', in 'a'..'z', in 'A'..'Z' -> false
        in '\u0080'..'\uFFFF' -> false // Not ASCII
        else -> true
    }
