package lang.temper.cli.repl

import lang.temper.stage.Stage

/**
 * A container for optional per-session configuration of the REPL. The user should never need to specify any options
 * here for normal usage.
 */
data class ReplConfig(
    val dumpStages: Set<Stage>,
    val separator: ReplSeparator = ReplSeparator.default,
    val prompt: ReplPrompt = ReplPrompt.default,
)

/** The default configuration should behave properly. */
val defaultConfig = ReplConfig(dumpStages = setOf())
