package lang.temper.tooling.buildrun

import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.Console
import lang.temper.common.currents.CancelGroup
import lang.temper.fs.OutputRoot
import lang.temper.fs.RealWritableFileSystem
import lang.temper.fs.copyResources
import lang.temper.fs.rmrf
import lang.temper.name.BackendId
import lang.temper.name.interpBackendId
import lang.temper.supportedBackends.lookupFactory

fun supplyCoreLibrary(
    outFs: RealWritableFileSystem,
    backendIds: Iterable<BackendId>,
    cancelGroup: CancelGroup,
    console: Console,
) {
    val outRoot = outFs.javaRoot
    for (backendId in backendIds) {
        if (backendId == interpBackendId) { continue }
        val coreRoot = outRoot.resolve(backendId.uniqueId).resolve("temper-core")
        val factory = lookupFactory(backendId)!!
        // Delete before new copy to keep things clean.
        coreRoot.rmrf()
        copyResources(factory.coreLibraryResources, coreRoot)
        val outDir = OutputRoot(
            RealWritableFileSystem(outRoot) {
                console.error(it)
            },
        )
        if (factory.processCoreLibraryResourcesNeeded) {
            // Only set up a CliEnv if we'll need it, since that does needless validation otherwise.
            CliEnv.using(factory.specifics, ShellPreferences.default(console), cancelGroup, outDir) {
                factory.processCoreLibraryResources(this, console)
            }
        }
    }
}
