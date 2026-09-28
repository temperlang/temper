package lang.temper.be.js

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.console
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.staging.buildConfigType
import lang.temper.library.LibraryConfiguration
import lang.temper.library.backendLibraryName
import lang.temper.log.LogSink
import lang.temper.name.Symbol
import lang.temper.value.BlockTree
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value

class JsLibraryConfig(
    val config: LibraryConfiguration,
) {
    private val properties = config.extractProperties(
        configKey = JsConfigKeys.CONFIG,
        className = JsConfigKeys.CONFIG_CLASS_NAME,
    )

    @Suppress("SameParameterValue")
    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: config.configExports[globalSymbol])

    fun name(): String {
        return config.backendLibraryName(cfg(JsConfigKeys.NAME, JsConfigKeys.nameKey))
    }

    internal fun dependencies(): List<JsDependency> {
        val deps = TList.unpackOrNull(properties?.get(JsConfigKeys.DEPENDENCIES)) ?: return emptyList()
        return deps.mapNotNull dep@{ depValue ->
            val dependencyText = TString.unpackOrNull(depValue) ?: return@dep null
            val parts = dependencyText.trim().split("@")
            parts.size == 2 || run {
                console.error("""Expected "name@version", not $dependencyText""")
                return@dep null
            }
            val (name, version) = parts
            JsDependency(name = name, versionString = version, temperLibraryName = null)
        }
    }
}

object JsConfigKeys {
    /** Key for the backend config instance. */
    const val CONFIG = "js"

    /** The name of the class for configuring the backend. */
    const val CONFIG_CLASS_NAME = "JsConfig"

    /** Config key to specify npm dependencies. */
    const val DEPENDENCIES = "dependencies"

    /** The name of the library for this backend. */
    const val NAME = "name"
    internal val nameKey = Symbol("jsName")
}

object JsConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = JsConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    JsConfigKeys.NAME to { V(Value(Types.string)) },
                    JsConfigKeys.DEPENDENCIES to {
                        Call(BuiltinFuns.angleFn) {
                            V(Value(Types.list))
                            V(Value(Types.string))
                        }
                    },
                ),
            )
        }
    }
}
