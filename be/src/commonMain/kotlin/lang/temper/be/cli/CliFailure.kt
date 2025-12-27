package lang.temper.be.cli

import lang.temper.common.ClosedOpenRange
import lang.temper.fs.NativePath
import lang.temper.name.PartialSemVer
import lang.temper.name.SemVer

/** Bundles additional information about the CLI failures */
open class CliFailure(
    message: String,
    cause: Throwable? = null,
    var effort: EffortBase? = null,
) : IllegalStateException(message, cause)

open class CommandFailed(
    effort: EffortFail,
    cause: Throwable? = null,
) : CliFailure(
    "${effort.command?.short} failed: exit code = ${effort.exitCode}",
    cause = cause,
    effort = effort,
)

class CommandNotFound(
    /** names looked for */
    val names: List<String>,
    /** paths looked in */
    val paths: List<String>,
) : CliFailure("Command not found; none of $names were found in $paths", effort = EffortBase.Nil)

class MissingConfig(
    val configPath: NativePath,
) : CliFailure("Configuration file missing or incomplete: $configPath", effort = EffortBase.Nil)

class CommandNotConfigured : CliFailure(
    "That tool is not specified in the Specifics object",
    effort = EffortBase.Nil,
)

class TruncatedOutput(
    effort: EffortFail,
) : CliFailure("Runaway output truncated from ${effort.command?.short}", effort = effort)

class SemVerParseError(
    effort: EffortSuccess,
    cause: Throwable? = null,
) : CliFailure(
    "${effort.command?.short} didn't return a parseable version",
    cause = cause,
    effort = effort,
)

class CommandIncorrectVersion(
    version: SemVer,
    acceptableVersion: ClosedOpenRange<PartialSemVer>,
    effort: EffortSuccess,
) : CliFailure(
    "${effort.command} returns `$version`; not in $acceptableVersion",
    effort = effort,
)

class CommandOldVersion(
    version: SemVer,
    acceptableVersion: PartialSemVer,
    effort: EffortSuccess,
) : CliFailure(
    "${effort.command} returns `$version`; not >= $acceptableVersion",
    effort = effort,
)
