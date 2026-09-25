package lang.temper.be.csharp

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.console
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.staging.buildConfigType
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.library.LibraryConfiguration
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

    @Suppress("SameParameterValue") // I want this structure, anyway.
    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: config.configExports[globalSymbol])

    fun rootNamespace(): String {
        return cfg(CSharpConfigKeys.ROOT_NAMESPACE, CSharpConfigKeys.namespaceKey)
            ?: when (config.libraryName.text) {
                // Hardcode std because we don't yet get config exports in funtests.
                STANDARD_LIBRARY_NAME -> STD_ROOT_NAMESPACE
                else -> config.libraryName.text.dashToPascal()
            }
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
    /** Key for the be-csharp config instance. */
    const val CONFIG = "csharp"

    /** The name of the class for configuring be-csharp. */
    const val CONFIG_CLASS_NAME = "CSharpConfig"

    /** Config key to specify NuGet dependencies. */
    const val DEPENDENCIES = "dependencies"

    /** The name of the class for configuring be-csharp. */
    const val ROOT_NAMESPACE = "rootNamespace"
    internal val namespaceKey = Symbol("csharpRootNamespace")
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
