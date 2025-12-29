package lang.temper.be.cli

import lang.temper.common.RResult

/**
 * A base class that abstracts launching a process over JVM vs. JS, or in different environments.
 *
 * See [CliEnv] to obtain an instance.
 */
abstract class CliTool(
    val specifics: ToolSpecifics,
) {
    /** The name of the tool that was looked up for future invocations. */
    abstract val name: String

    /** Typically the full path to the tool. */
    abstract val command: String

    /** Specify a similar tool with a new command path. */
    abstract fun withCommandPath(newCommand: String): CliTool

    abstract val cliEnv: CliEnv

    /** Add the specific command this tool runs to the Command instance. */
    abstract fun specify(cmd: Command): CommandDetail

    /** Run a command. */
    abstract fun run(cmd: Command): RResult<EffortSuccess, CliFailure>

    /**
     * Terminates the JVM process and lets the exit result be the
     * result of the command as libc `execve`.
     *
     * This is meant to allow a backend-specific interactive shell to
     * take over interaction with the user.
     *
     * Use [run] for everything else.
     *
     * @return only returns on failure to exec.
     */
    abstract fun runAsLast(cmd: Command)
}
