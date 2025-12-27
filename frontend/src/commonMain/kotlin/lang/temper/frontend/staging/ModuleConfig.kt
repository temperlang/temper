package lang.temper.frontend.staging

import lang.temper.common.Snapshotter
import lang.temper.lexer.Genre
import lang.temper.log.LogSink
import lang.temper.name.ModuleName

/**
 * Bits related to how to create modules.
 */
data class ModuleConfig(
    /** Given a path to a source file and a project log sink, creates a logger */
    val makeModuleLogSink: ((ModuleName, LogSink) -> LogSink) =
        { _, projectLogSink -> projectLogSink },
    /**
     * True to advance through [lang.temper.stage.Stage.Run]
     * instead of [lang.temper.stage.Stage.GenerateCode]
     */
    val mayRun: Boolean = false,
    /**
     * Applied to each owned module before advancing any.
     * May be used to register custom builtins, for example.
     */
    val moduleCustomizeHook: ModuleCustomizeHook = ModuleCustomizeHook.CustomizeNothing,
    val snapshotter: Snapshotter? = null,
    val genre: Genre = Genre.Library,
) {
    companion object {
        val default = ModuleConfig()
    }
}
