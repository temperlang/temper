package lang.temper.be.py

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
import kotlin.reflect.full.primaryConstructor

class PyLibraryConfig(
    val config: LibraryConfiguration,
) {
    private val properties = config.extractProperties(
        configKey = PyConfigKeys.CONFIG,
        className = PyConfigKeys.CONFIG_CLASS_NAME,
    )

    @Suppress("SameParameterValue")
    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: config.configExports[globalSymbol])

    fun name(): String {
        return config.backendLibraryName(cfg(PyConfigKeys.NAME, PyConfigKeys.nameKey))
    }

    internal fun dependencies(): List<Dependency> {
        val deps = TList.unpackOrNull(properties?.get(PyConfigKeys.DEPENDENCIES)) ?: return emptyList()
        return deps.mapNotNull dep@{ depValue ->
            val dependencyText = TString.unpackOrNull(depValue) ?: return@dep null
            val parts = dependencyText.trim().split("==")
            parts.size == Dependency::class.primaryConstructor!!.parameters.size || run {
                console.error("""Expected "name==version", not $dependencyText""")
                return@dep null
            }
            val (name, version) = parts
            Dependency(name = name, version = version)
        }
    }
}

object PyConfigKeys {
    /** Key for the backend config instance. */
    const val CONFIG = PY_BACKEND_ID

    /** The name of the class for configuring the backend. */
    const val CONFIG_CLASS_NAME = "PyConfig"

    /** Config key to specify pypi dependencies. */
    const val DEPENDENCIES = "dependencies"

    /** The name of the library for this backend. */
    const val NAME = "name"
    internal val nameKey = Symbol("pyName")
}

object PyConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = PyConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    PyConfigKeys.NAME to { V(Value(Types.string)) },
                    PyConfigKeys.DEPENDENCIES to {
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
