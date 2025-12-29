package lang.temper.be.cli

import lang.temper.common.currents.CancelGroup
import lang.temper.fs.escapeShellString
import java.nio.file.Path

class PredefinedLocalCliEnv(
    specifics: Specifics,
    shellPreferences: ShellPreferences,
    cancelGroup: CancelGroup,
    private val outDir: Path,
) : LocalCliEnv(specifics, shellPreferences, cancelGroup) {
    override fun init() {
        require(localRootBacking == null) { "Can't init twice without release" }
        localRootBacking = outDir
    }

    override fun release() {
        // Neither create nor destroy here.
        localRootBacking = null
    }

    override fun maybeFreeze(): Map<Advice, String> {
        return mapOf(
            Advice.CliEnv to """
                |The environment is in predefined directory:
                |cd ${escapeShellString("$outDir")}
            """.trimMargin(),
        )
    }
}
