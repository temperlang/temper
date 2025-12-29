package lang.temper.be.cli

import lang.temper.common.Console

data class ShellPreferences(
    val verbosity: Verbosity,
    val onFailure: OnFailure,
    val console: Console,

    /** For overriding system environment PATH, such as for testing. */
    val pathElements: List<String>? = null,
) {
    /** How much information to report about how we're setting up the environment for the shell. */
    enum class Verbosity {
        Quiet,
        Verbose,
    }

    /**
     * Whether to leave the constructed environment around so that the user can choose
     * to interactively debug the failure.
     */
    enum class OnFailure {
        Release,
        Freeze,
    }

    companion object {
        fun default(console: Console) = ShellPreferences(
            Verbosity.Quiet,
            OnFailure.Release,
            console,
        )
        fun functionalTests(console: Console) = ShellPreferences(
            Verbosity.Quiet,
            OnFailure.Freeze,
            console,
        )
        fun mypyc(console: Console) = ShellPreferences(
            Verbosity.Quiet,
            OnFailure.Freeze,
            console,
        )
    }
}
