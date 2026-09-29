package lang.temper.be.lua

import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.staging.buildConfigType
import lang.temper.library.LibraryConfiguration
import lang.temper.log.LogSink
import lang.temper.value.BlockTree

class LuaLibraryConfig(
    val config: LibraryConfiguration,
) {
    private val properties = config.extractProperties(
        configKey = LuaConfigKeys.CONFIG,
        className = LuaConfigKeys.CONFIG_CLASS_NAME,
    )

    // TODO Fill in this placeholder, including things below.
}

object LuaConfigKeys {
    /** Key for the backend config instance. */
    const val CONFIG = "lua"

    /** The name of the class for configuring the backend. */
    const val CONFIG_CLASS_NAME = "LuaConfig"
}

object LuaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = LuaConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(),
            )
        }
    }
}
