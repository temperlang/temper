package lang.temper.be.csharp

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

class CSharpLibraryConfig(
    val config: LibraryConfiguration,
) {
    private val properties = config.extractProperties(
        configKey = CSharpConfigKeys.CONFIG,
        className = CSharpConfigKeys.CONFIG_CLASS_NAME,
    )

    @Suppress("SameParameterValue")
    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: config.configExports[globalSymbol])

    fun rootNamespace(): String {
        val name = cfg(CSharpConfigKeys.ROOT_NAMESPACE, CSharpConfigKeys.rootNamespaceKey)
        return config.backendLibraryName(name) { it.dashToPascal() }
    }

    fun dependencies(): List<PackageReference> {
        val deps = TList.unpackOrNull(properties?.get(CSharpConfigKeys.DEPENDENCIES)) ?: return emptyList()
        return deps.mapNotNull dep@{ depValue ->
            val dependencyText = TString.unpackOrNull(depValue) ?: return@dep null
            val parts = dependencyText.trim().split(":")
            parts.size == PackageReference::class.primaryConstructor!!.parameters.size || run {
                console.error("""Expected "name:version", not $dependencyText""")
                return@dep null
            }
            val (name, version) = parts
            PackageReference(name = name, version = version)
        }
    }
}

object CSharpConfigKeys {
    /** Key for the backend config instance. */
    const val CONFIG = CSharpBackend.BACKEND_ID

    /** The name of the class for configuring the backend. */
    const val CONFIG_CLASS_NAME = "CSharpConfig"

    /** Config key to specify NuGet dependencies. */
    const val DEPENDENCIES = "dependencies"

    /** The root namespace for the dotnet library. */
    const val ROOT_NAMESPACE = "rootNamespace"
    internal val rootNamespaceKey = Symbol("csharpRootNamespace")
}

object CSharpConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = CSharpConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    CSharpConfigKeys.ROOT_NAMESPACE to { V(Value(Types.string)) },
                    CSharpConfigKeys.DEPENDENCIES to {
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
