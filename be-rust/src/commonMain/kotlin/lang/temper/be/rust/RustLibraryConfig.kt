package lang.temper.be.rust

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

class RustLibraryConfig(
    val config: LibraryConfiguration,
) {
    private val properties = config.extractProperties(
        configKey = RustConfigKeys.CONFIG,
        className = RustConfigKeys.CONFIG_CLASS_NAME,
    )

    @Suppress("SameParameterValue")
    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: config.configExports[globalSymbol])

    fun name(): String {
        return config.backendLibraryName(cfg(RustConfigKeys.NAME, RustConfigKeys.nameKey))
    }

    fun packageNaming(): PackageNaming {
        // TODO Also allow configured crate name completely different from package name.
        return PackageNaming(packageName = name())
    }

    internal fun dependencies(): List<Dep> {
        val deps = TList.unpackOrNull(properties?.get(RustConfigKeys.DEPENDENCIES)) ?: return emptyList()
        return deps.mapNotNull dep@{ depValue ->
            val dependencyText = TString.unpackOrNull(depValue) ?: return@dep null
            val parts = dependencyText.trim().split("@")
            parts.size == 2 || run {
                console.error("""Expected "name@version", not $dependencyText""")
                return@dep null
            }
            val (name, version) = parts
            // Use precise "=version" versioning here for now.
            // TODO In part, this is because our test case of `roaring` breaks compatibility on minor versions.
            // TODO Make this configurable?
            Dep(naming = PackageNaming(packageName = name), version = "=${version}")
        }
    }
}

object RustConfigKeys {
    /** Key for the backend config instance. */
    const val CONFIG = RustBackend.BACKEND_ID

    /** The name of the class for configuring the backend. */
    const val CONFIG_CLASS_NAME = "RustConfig"

    /** Config key to specify cargo dependencies. */
    const val DEPENDENCIES = "dependencies"

    /** The name of the library for this backend. */
    const val NAME = "name"
    internal val nameKey = Symbol("rustName")
}

object RustConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = RustConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    RustConfigKeys.NAME to { V(Value(Types.string)) },
                    RustConfigKeys.DEPENDENCIES to {
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
