package lang.temper.tooling.buildrun

import lang.temper.be.Dependencies
import lang.temper.common.Log
import lang.temper.frontend.Module
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.name.BackendId

sealed class BuildResult {
    abstract val ok: Boolean
    abstract val maxLogLevel: Log.Level
}

data class BuildInitFailed(
    override val ok: Boolean,
    override val maxLogLevel: Log.Level,
) : BuildResult()

data class BuildNotNeededResult(
    override val ok: Boolean,
    override val maxLogLevel: Log.Level,
) : BuildResult()

data class BuildDoneResult(
    override val ok: Boolean,
    override val maxLogLevel: Log.Level,
    val partitionedModules: List<Pair<LibraryConfiguration, List<Module>>>,
    val modulesInOrder: List<Module>,
    val libraryConfigurations: LibraryConfigurationsBundle,
    val dependencies: Map<BackendId, Dependencies<*>>,
    val taskResults: DoRunResult?,
) : BuildResult()
