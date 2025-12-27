package lang.temper.be.cli

import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.fs.escapeShellString
import lang.temper.log.FilePath
import java.io.IOException

/**
 * A command requested to be run against a [CliTool].
 *
 * @param args the command arguments
 * @param cwd a path within the environment
 * @param env a map of environment variables to set
 * @param aux the auxiliary paths to read after the command runs
 */
data class Command(
    val args: List<String>,
    val cwd: FilePath = FilePath.emptyPath,
    val env: Map<String, String> = mapOf(),
    val aux: Map<Aux, FilePath> = mapOf(),
    val reproduce: Map<Advice, String> = mapOf(),
) {
    init {
        require(cwd.isDir) { "the working directory should be a directory but is '$cwd'" }
        require(aux.values.all { it.isFile }) { "All auxiliary paths must be file paths" }
    }
}

/**
 * The details of a command that would actually be run by a [CliTool]
 * @param command the exact command that should be run within the environment.
 * @param args generally the same args as the requested command
 * @param cwd the path that the command was changed to before running
 * @param env the environment variables passed to the command
 * @param auxPaths the paths that will be read to gather auxiliary data
 * @see [CliTool.specify] to construct
 */
data class CommandDetail(
    val command: String,
    val args: List<String>,
    val cwd: String? = null,
    val env: Map<String, String> = mapOf(),
    val auxPaths: Map<Aux, String> = mapOf(),
) {
    val short: String get() = command

    fun verbose(): String = buildString {
        if (cwd != null) {
            append("cd ").append(cwd).append("; ")
        }
        env.forEach { (k, v) -> append(k).append('=').append(escapeShellString(v)).append(' ') }
        append(command)
        args.forEach { arg ->
            append(' ')
            if (arg.length < LONG_ARG_LENGTH) {
                append(escapeShellString(arg))
            } else {
                append(escapeShellString(arg.substring(0, LONG_ARG_LENGTH)) + "...")
            }
        }
        auxPaths.forEach { (aux, path) ->
            append(' ').append(aux.name).append("=$(<").append(escapeShellString(path)).append(")")
        }
    }
}

private const val LONG_ARG_LENGTH = 32

/** Minimal details contained by an effort to run a command. */
interface EffortBase {
    val command: CommandDetail?

    /** Used by [print] */
    operator fun get(adviceType: Advice): String?
    fun withAux(aux: Aux, value: String): Effort

    object Nil : EffortBase {
        override val command: CommandDetail? = null
        override fun get(adviceType: Advice): String? = null
        override fun withAux(aux: Aux, value: String): Effort = Effort(
            auxOut = mapOf(aux to value),
            cliEnv = null,
        )
    }
}

fun EffortBase.explain(advice: Boolean = false): List<Pair<String, String>> = buildList {
    val eff = this@explain
    if (eff is EffortSuccess) {
        eff.command?.let { cmd ->
            add("Command" to cmd.verbose())
            val auxPaths = cmd.auxPaths
            for ((aux, content) in eff.auxOut) {
                add("${auxPaths[aux]}" to content)
            }
        }
        add("Stdout" to eff.stdout)
    }
    if (advice) {
        for (a in Advice.entries) {
            eff[a]?.let {
                add(a.header to it)
            }
        }
    }
}

fun EffortBase?.cleanup() {
    when (this) {
        is Effort -> this.cliEnv?.release()
    }
}

/** Details when a command successfully runs. */
interface EffortSuccess : EffortBase {
    val stdout: String
    val auxOut: Map<Aux, String>

    fun scrubStdout(func: (String) -> String): EffortSuccess
}

/** Details when a command fails to run. */
interface EffortFail : EffortBase {
    val exitCode: Int?
}

/**
 * A container for the result of attempting to run a command.
 * @param stdout The text output produced by the sun
 * @param auxOut Additional text based output created by the invocation.
 * @param exitCode A standard Posix exit code; only guarantee is that 0 means success
 * @param reproduceAdvice Instructions for the user for how to see the code that was run
 * @param cliEnv a reference to the containing cliEnv for cleanup
 */
data class Effort(
    override val stdout: String = "",
    override val auxOut: Map<Aux, String> = mapOf(),
    override val exitCode: Int? = null,
    override val command: CommandDetail? = null,
    val auxErr: Map<Aux, IOException> = mapOf(),
    val reproduceAdvice: Map<Advice, String> = mapOf(),
    val cliEnv: CliEnv?,
) : EffortSuccess, EffortFail {
    fun asResult(): RResult<EffortSuccess, CliFailure> =
        if (exitCode == 0 && auxErr.isEmpty()) RSuccess(this) else RFailure(CommandFailed(this))

    override fun scrubStdout(func: (String) -> String) = copy(stdout = func(stdout))

    override fun get(adviceType: Advice): String? =
        reproduceAdvice[adviceType] ?: cliEnv?.maybeFreeze()?.get(adviceType)

    override fun withAux(aux: Aux, value: String): Effort = copy(auxOut = mapOf(aux to value) + auxOut)
}

/** Advice to reproduce an issue. */
enum class Advice(val header: String) {
    /** Advice specific to the consumer */
    Caller("Caller"),

    /** Advice to get into the environment, such as a temp directory. */
    CliEnv("Steps to access the test"),

    /** Advice to run the command itself */
    CliTool("Command that was run"),

    /** Advice specific to the individual step that ran */
    Step("Individual step"),
}

enum class Aux {
    /** Capture junit results. */
    JunitXml,

    /** The path may be disregarded; this will capture stderr. */
    Stderr,

    /** Capture build activity */
    BuildLogs,
}

fun Command.maybeLogBeforeRunning(cliTool: CliTool, shellPreferences: ShellPreferences) {
    when (shellPreferences.verbosity) {
        ShellPreferences.Verbosity.Quiet -> {}
        ShellPreferences.Verbosity.Verbose ->
            shellPreferences.console.info("running shell command: `${cliTool.specify(this).verbose()}`")
    }
}
