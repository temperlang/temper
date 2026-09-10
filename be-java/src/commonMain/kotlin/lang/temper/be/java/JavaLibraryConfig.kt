package lang.temper.be.java

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.console
import lang.temper.common.subListToEnd
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.staging.buildConfigType
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.library.backendLibraryName
import lang.temper.library.versionOrDefault
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.LogSink
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.value.BlockTree
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.TClass
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value

open class JavaLibraryConfigs(
    val base: LibraryConfigurations,
) {
    val all: Collection<JavaLibraryConfig> get() =
        byRoot.values.filter(::filterLibrary)
    private val byRoot = base.byLibraryRoot.mapValues { (_, lib) ->
        JavaLibraryConfig(lib)
    }
    private fun byLibraryRoot(path: FilePath): JavaLibraryConfig? = byRoot[path]
    val current: JavaLibraryConfig =
        byRoot[base.currentLibraryConfiguration.libraryRoot]
            ?: JavaLibraryConfig(base.currentLibraryConfiguration)

    /** Override the configuration prefix for e.g. FunctionalTests. */
    open fun modulePrefixHandler(modulePath: FilePath?, config: JavaLibraryConfig?, current: JavaLibraryConfig) =
        config?.prefix ?: listOf()

    /** Filter libraries returned by  for e.g. FunctionalTests. */
    open fun filterLibrary(lib: JavaLibraryConfig): Boolean = lib != current

    /** Determines the Java package associated with a Temper module name. */
    fun moduleInfo(moduleName: ModuleName): ModuleInfo = moduleInfo(
        moduleName,
        byLibraryRoot(moduleName.libraryRoot()),
    )

    fun packageNameFor(modulePath: FilePath?, config: JavaLibraryConfig?): QualifiedName {
        val prefix = modulePrefixHandler(modulePath, config, current)
        if (modulePath == null) {
            return QualifiedName.safe(prefix)
        }
        val segments = if (prefix.isEmpty()) {
            modulePath.segments
        } else {
            prefix.map(::FilePathSegment) +
                modulePath.segments.subListToEnd(config?.libraryRoot?.segments?.size ?: 0)
        }
        // Use a FilePath for special processing below. And likely not a dir, but carry the value.
        return QualifiedName.fromTemperPath(FilePath(segments, isDir = modulePath.isDir))
    }

    /** Determines the Java package associated with the path to a Temper module. */
    private fun moduleInfo(moduleName: ModuleName, config: JavaLibraryConfig?): ModuleInfo =
        ModuleInfo(
            packageName = packageNameFor(moduleName.sourceFile, config),
            module = moduleName,
        )
}

class JavaLibraryConfig(
    val base: LibraryConfiguration,
) {
    val libraryName: String get() = base.backendLibraryName(JavaConfigKeys.libraryNameGlobal)
    val libraryRoot: FilePath get() = base.libraryRoot

    private val properties = base.configExports[Symbol(JavaConfigKeys.CONFIG)]?.let value@{ value ->
        // Check that we have a class instance.
        val typeShape = (value.typeTag as? TClass)?.typeShape ?: run {
            // TODO Provide a LogSink to backends?
            console.error("Expected class instance for ${JavaConfigKeys.CONFIG} config")
            return@value null
        }
        // Check the type name.
        // We currently generate within the context of the config module, so the origin isn't special.
        (typeShape.name as? ExportedName)?.baseName?.nameText == JavaConfigKeys.CONFIG_CLASS_NAME || run {
            console.error("Expected config class ${JavaConfigKeys.CONFIG_CLASS_NAME}, not ${typeShape.name}")
            return@value null
        }
        // Good enough for now. Extract a pretty map.
        (value.stateVector as InstancePropertyRecord).properties.map { instance ->
            (instance.key as SourceName).baseName.nameText to instance.value
        }.toMap()
    }

    private fun cfg(propertyName: String, globalSymbol: Symbol) =
        TString.unpackOrNull(properties?.get(propertyName) ?: base.configExports[globalSymbol])

    private fun cfgPackage(): String? = cfg(JavaConfigKeys.PACKAGE, JavaConfigKeys.packageGlobal)

    private val libraryGroup: String
        get() =
            cfg(JavaConfigKeys.GROUP, JavaConfigKeys.libraryGroupGlobal)
                ?: cfgPackage()
                // Dashes not allowed in group names per
                // maven.apache.org/guides/mini/guide-naming-conventions.html
                ?: libraryName.safeIdentifier()

    private val libraryArtifact: String
        get() = cfg(JavaConfigKeys.ARTIFACT, JavaConfigKeys.libraryArtifactGlobal) ?: libraryName

    internal val dependencies by lazy {
        val deps = TList.unpackOrNull(
            properties?.get(JavaConfigKeys.DEPENDENCIES) ?: base.configExports[JavaConfigKeys.dependenciesGlobal],
        ) ?: return@lazy emptyList()
        deps.mapNotNull dep@{ depValue ->
            val dependencyText = TString.unpackOrNull(depValue) ?: return@dep null
            val (groupId, artifactId, version) = dependencyText.trim().split(":")
            val artifact = Artifact(groupId, artifactId, version)
            // For javaDependencies, treat all as main. Factor logic if we make a javaTestDependencies later.
            Dependency(Java.SourceDirectory.MainJava, artifact)
        }
    }

    val artifact by lazy {
        Artifact(
            groupId = libraryGroup,
            artifactId = libraryArtifact,
            version = base.versionOrDefault(),
        )
    }

    val prefix: List<String>
        get() =
            when (val javaPackageMetadataString = cfgPackage()) {
                "" -> emptyList()
                null -> listOf(libraryName)
                else -> javaPackageMetadataString.split(".")
            }
}

object JavaConfigKeys {
    /** Key for the be-java (and be-java8) config instance. */
    const val CONFIG = "java"

    /** The name of the class for configuring be-java. */
    const val CONFIG_CLASS_NAME = "JavaConfig"

    /** Config files may export a name with this text to specify the Maven library name */
    const val NAME = "name"
    val libraryNameGlobal = Symbol("javaName")

    /** Config files may export a name with this text to specify the Maven group id */
    const val GROUP = "group"
    val libraryGroupGlobal = Symbol("javaGroup")

    /** Config files may export a name with this text to specify the Maven artifact id */
    const val ARTIFACT = "artifact"
    val libraryArtifactGlobal = Symbol("javaArtifact")

    /** Config files may export a name with this text to specify the Java `package` name */
    const val PACKAGE = "package"
    val packageGlobal = Symbol("javaPackage")

    /** Config key to specify Maven dependencies */
    const val DEPENDENCIES = "dependencies"
    val dependenciesGlobal = Symbol("javaDependencies")
}

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = JavaConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    JavaConfigKeys.NAME to { V(Value(Types.string)) },
                    JavaConfigKeys.PACKAGE to { V(Value(Types.string)) },
                    JavaConfigKeys.GROUP to { V(Value(Types.string)) },
                    JavaConfigKeys.ARTIFACT to { V(Value(Types.string)) },
                    JavaConfigKeys.DEPENDENCIES to {
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
