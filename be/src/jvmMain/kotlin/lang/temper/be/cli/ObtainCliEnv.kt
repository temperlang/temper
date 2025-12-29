package lang.temper.be.cli

import lang.temper.common.currents.CancelGroup
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.fs.RealFileSystem

/**
 * If given a real output root, work in it. Otherwise, create a separate space.
 */
actual fun obtainCliEnv(
    specifics: Specifics,
    shellPreferences: ShellPreferences,
    outDir: OutDir?,
    cancelGroup: CancelGroup,
): CliEnv {
    val fs = (outDir as? OutputRoot)?.fs
    return if (fs is RealFileSystem) {
        PredefinedLocalCliEnv(specifics, shellPreferences, cancelGroup, outDir = fs.javaRoot)
    } else {
        LocalCliEnv(specifics, shellPreferences, cancelGroup)
    }
}

actual val cliEnvImplemented: Boolean get() = true

actual val installShouldFailFast by lazy {
    System.getProperty(INSTALL_FF_NAME, "no").lowercase() in listOf("yes", "true")
}
