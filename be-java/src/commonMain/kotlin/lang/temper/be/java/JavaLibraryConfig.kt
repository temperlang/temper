package lang.temper.be.java

import lang.temper.common.subListToEnd
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.library.backendLibraryName
import lang.temper.library.versionOrDefault
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.ModuleName
import lang.temper.name.Symbol
import lang.temper.value.TString

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
    val libraryName: String get() = base.backendLibraryName(JavaBackend.javaLibraryNameConfigKey)
    val libraryRoot: FilePath get() = base.libraryRoot
    private fun cfg(sym: Symbol) = TString.unpackOrNull(base.configExports[sym])

    private val libraryGroup: String
        get() =
            cfg(JavaBackend.javaLibraryGroupConfigKey)
                ?: cfg(JavaBackend.javaPackageConfigKey)
                // Dashes not allowed in group names per
                // maven.apache.org/guides/mini/guide-naming-conventions.html
                ?: libraryName.safeIdentifier()
    private val libraryArtifact: String get() = cfg(JavaBackend.javaLibraryArtifactConfigKey) ?: libraryName

    internal val dependencies by lazy {
        val dependenciesText = cfg(JavaBackend.javaDependenciesKey) ?: return@lazy emptyList()
        dependenciesText.split(commaSeparated).map { dependencyText ->
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
            when (val javaPackageMetadataString = cfg(JavaBackend.javaPackageConfigKey)) {
                "" -> emptyList()
                null -> listOf(libraryName)
                else -> javaPackageMetadataString.split(".")
            }
}

private val commaSeparated = Regex("""\s*,\s*""")
