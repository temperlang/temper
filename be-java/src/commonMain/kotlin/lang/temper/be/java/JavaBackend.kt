package lang.temper.be.java

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.names.NameSelection
import lang.temper.be.tmpl.LibraryRootContext
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.Symbol
import lang.temper.name.rootModuleName
import lang.temper.be.java.Java as J
import lang.temper.value.DependencyCategory as DepCat

/**
 * <!-- snippet: backend/java -->
 * # Java Backend
 *
 * ⎀ backend/java/id
 *
 * Translates Temper to Java source and later to JARs.
 *
 * Targets [Java 17].
 *
 * To get started with this backend, see [the tutorial](../tutorial/index.md#use-java).
 *
 * There is also a [Java 8][snippet/backend/java8] backend that
 * produces similar output but which does not depend on newer
 * Java runtime library features.  Except where specified in
 * the Java8 backend documentation, the notes here also apply
 * to that backend.
 *
 * ## Translation notes
 *
 * TODO: Explain how Temper function types translate to Java
 * SMI types.
 *
 * TODO: Explain how `null`able types are translated
 *
 * TODO: Explain how Temper types translate to primitive or boxed
 * types.
 *
 * TODO: Explain overload generation for optional arguments.
 *
 * TODO: Explain how Temper source files are split into Java files.
 *
 * TODO: Explain how `console.log` connects.
 *
 * ## Tooling notes
 *
 * TODO: Explain how to pick a Java package for a library and
 * Maven identifiers.
 *
 * TODO: Explain how Temper tests correspond to JUnit and the
 * version of JUnit we require
 *
 * TODO: Explain the generated POM and JAR metadata.
 *
 * TODO: Generating multi-version JARs is something we plan to do.
 *
 * [Java 17]: https://docs.oracle.com/javase/specs/jls/se17/html/index.html
 *
 * <!-- snippet: backend/java8 -->
 * # Java 8 Backend
 *
 * ⎀ backend/java8/id
 *
 * Translates Temper to Java 8 source, and later to JARs
 * for compatibility with older Java Virtual Machines (JVMs).
 *
 * Except where noted here, the documentation for the main
 * [Java][snippet/backend/java] backend also applies to
 * this legacy, Java 8 backend.
 *
 * Targets [Java 8].
 *
 * [Java 8]: https://docs.oracle.com/javase/specs/jls/se8/html/index.html
 */
class JavaBackend private constructor(
    val factory: JavaFactory,
    setup: BackendSetup<JavaBackend>,
) : Backend<JavaBackend>(factory.backendId, setup) {
    /** Use the JavaFactory to wrap LibraryConfigurations. */
    private val javaLibConfigs = factory.javaLibraryConfigs(libraryConfigurations)

    override fun tentativeTmpL(): TmpL.ModuleSet = TmpLTranslator.translateModules(
        logSink,
        readyModules,
        supportNetwork,
        libraryConfigurations = libraryConfigurations,
        dependencyResolver = dependencyResolver,
        tentativeOutputPathFor = { allocateTextFile(it, sourceFileExtension) },
    )

    private val names: JavaNames =
        JavaNames(
            javaLang = factory.lang,
            libraries = javaLibConfigs,
        )

    private var rootMainClass: QualifiedName? = null

    override fun translate(finished: TmpL.ModuleSet) = buildList {
        JavaTranslator(names, dependenciesBuilder).let { trans ->
            names.scanNames(finished)
            finished.modules.flatMap { tmpLModule ->
                trans.translate(tmpLModule)
            }
        }.forEach { program ->
            // Modifies program and returns it; but assume that the original value is expended.
            val simplified = simplifyNames(program)
            val result = TranslatedFileSpecification(
                // The output dir is the source root + package
                simplified.filePathTo(),
                sourceMimeType,
                simplified,
            )
            add(result)
        }
        // See if we already have a top-level module.
        val rootName = javaLibConfigs.current.libraryRoot.rootModuleName()
        val rootInfo = javaLibConfigs.moduleInfo(rootName)
        rootMainClass = rootInfo.entryQualifiedName
        val moduleNames = finished.modules.map { it.codeLocation.codeLocation }
        if (!config.abbreviated && rootName !in moduleNames) {
            // Don't have one, so build library global and main classes.
            addMergedInitClasses(javaLibConfigs, moduleNames, rootInfo)
        }
    }

    override fun selectNames(): List<NameSelection> = names.allNames()

    override fun libraryRootContext(libraryConfiguration: LibraryConfiguration) = LibraryRootContext(
        inRoot = libraryConfiguration.libraryRoot,
        outRoot = dirPath(libraryConfiguration.libraryName.text.safeIdentifier()),
    )

    /** Add per module resources. */
    override fun preWrite(outputFiles: List<OutputFileSpecification>): List<OutputFileSpecification> {
        val additions = mutableMapOf<FilePath, OutputFileSpecification>()
        val javaMime = factory.backendMeta.mimeTypeMap[FileType.Module]
        val javaMainClasses = mutableListOf<QualifiedName>()
        val testClasses = mutableListOf<QualifiedName>()
        val dependencies = mutableMapOf<Artifact, J.SourceDirectory>()
        val currentConfig = javaLibConfigs.current
        fun addDep(artifact: Artifact, sourceDir: J.SourceDirectory) {
            when (sourceDir) {
                // Main always wins, but test doesn't. And maven hates having both.
                J.SourceDirectory.MainJava -> dependencies[artifact] = sourceDir
                J.SourceDirectory.TestJava -> dependencies.putIfAbsent(artifact, sourceDir)
            }
        }

        fun addDep(dep: Dependency) = addDep(dep.artifact, dep.sourceDir)
        for (dep in factory.defaultDependencies) {
            addDep(dep)
        }
        // Include configured dependencies. From all libraries if in bundled mode, otherwise just our own.
        for (dep in currentConfig.dependencies) {
            addDep(dep)
        }
        // Find further dependencies by package.
        val packageArtifacts = javaLibConfigs.all
            .map { config ->
                val packageName = javaLibConfigs.packageNameFor(config.libraryRoot, config)
                // Use trailing dot for easy prefix check.
                "$packageName." to config.artifact
            }

        val mainPackages = mutableSetOf<QualifiedName>()
        val testPackages = mutableSetOf<QualifiedName>()
        for (file in outputFiles) {
            val fileSpec = (file as? TranslatedFileSpecification) ?: continue
            val program = fileSpec.content as? J.Program ?: continue
            val meta = program.programMeta
            val sourceDir = meta.sourceDirectory
            val isTest = sourceDir == J.SourceDirectory.TestJava
            if (isTest) {
                addDep(junitDependency)
            }
            if (program is J.TopLevelClassDeclaration) {
                neededNames@ for (name in program.programMeta.neededNames) {
                    // TODO Work out something less O(n * m). Meanwhile, we expect low artifact count.
                    val nameString = name.toString()
                    for ((packageName, artifact) in packageArtifacts) {
                        if (nameString.startsWith(packageName)) {
                            addDep(artifact, sourceDir)
                        }
                    }
                }
            }
            val className = program.className
            if (className != null) {
                if (meta.entryPoint == J.EntryPoint.MainMethod) {
                    javaMainClasses.add(className)
                }
                if (meta.testClass) {
                    testClasses.add(className)
                }
            }
            val pkgName = program.packageName
            if (pkgName != null) {
                for (resource in factory.perModuleResources[pkgName] ?: listOf()) {
                    additions[sourceDir.filePath + resource.rsrcPath] =
                        ResourceFileSpecification(sourceDir.filePath, resource, mimeType = javaMime)
                }
                for (spec in factory.perModuleFileSpecs[pkgName] ?: listOf()) {
                    val path = sourceDir.filePath + spec.path
                    additions[path] = MetadataFileSpecification(path, spec.mimeType, spec.content)
                }
                (if (isTest) testPackages else mainPackages).add(pkgName)
            }
        }
        if (rootMainClass!! !in javaMainClasses) {
            // Automerged top-level main isn't there by default, so insert it first.
            javaMainClasses.add(0, rootMainClass!!)
        }
        if (this.config.makeMetaDataFile) {
            val config = javaLibConfigs.current
            val pomFile = MetadataFileSpecification(
                path = factory.pomPath,
                mimeType = factory.pomMime,
                content = pomXml(
                    projectArtifact = config.artifact,
                    config = config.base,
                    javaVersion = factory.lang.majorVersion,
                    javaMainClasses = javaMainClasses,
                    testClasses = testClasses,
                    selectJdk = factory.specifics.pomSelectJdk,
                    dependencies = dependencies.entries.map { Dependency(it.value, it.key) },
                ).toString(),
            )
            dependenciesBuilder.addMetadata(
                currentConfig.base.libraryName,
                JavaMetadataKey.PomFilePath(factory.lang),
                pomFile.path,
            )
            additions[pomFile.path] = pomFile
        }

        dependenciesBuilder.addMetadata(
            currentConfig.base.libraryName,
            JavaMetadataKey.LibraryArtifact(factory.lang),
            currentConfig.artifact,
        )
        this.dependenciesBuilder.addMetadata(
            currentConfig.base.libraryName,
            JavaMetadataKey.MainClass(factory.lang),
            rootMainClass!!,
        )
        this.dependenciesBuilder.addMetadata(
            currentConfig.base.libraryName,
            JavaMetadataKey.MainClasses(factory.lang),
            javaMainClasses.toList(),
        )
        this.dependenciesBuilder.addMetadata(
            currentConfig.base.libraryName,
            JavaMetadataKey.ArtifactToSourceDirForDependencies(factory.lang),
            dependencies.toMap(),
        )
        this.dependenciesBuilder.addMetadata(
            currentConfig.base.libraryName,
            JavaMetadataKey.Packages(factory.lang),
            PackageLists(main = mainPackages.toSet(), test = testPackages.toSet()),
        )

        return outputFiles + additions.values
    }

    override val supportNetwork: SupportNetwork = factory.lang.supportNetwork

    @PluginBackendId("java8")
    @BackendSupportLevel(isSupported = true, isTested = true)
    data object Java8 : JavaFactory(JavaLang.Java8)

    @PluginBackendId("java")
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    data object Java17 : JavaFactory(JavaLang.Java17)

    internal data object FunctionalTestJava8 : FunctionalTestJavaFactory(JavaLang.Java8)
    internal data object FunctionalTestJava17 : FunctionalTestJavaFactory(JavaLang.Java17)

    companion object {
        // Standard extension for a Java source file
        const val sourceFileExtension = ".java"

        // Package files get a specific name. Prohibited as a Java identifier because package is a reserved word
        const val packageFileName = "package$sourceFileExtension"

        // Module files get a specific name. Prohibited as a Java identifier because module is a reserved word
        const val moduleFileName = "module$sourceFileExtension"

        /** Config files may export a name with this text to specify the Maven library name */
        val javaLibraryNameConfigKey = Symbol("javaName")

        /** Config files may export a name with this text to specify the Maven group id */
        val javaLibraryGroupConfigKey = Symbol("javaGroup")

        /** Config files may export a name with this text to specify the Maven artifact id */
        val javaLibraryArtifactConfigKey = Symbol("javaArtifact")

        /** Config files may export a name with this text to specify the Java `package` name */
        val javaPackageConfigKey = Symbol("javaPackage")

        /** Config key to specify Maven dependencies */
        val javaDependenciesKey = Symbol("javaDependencies")

        // source MIME type
        val sourceMimeType = MimeType("text", "x-java-source")
        // class file MIME
        // const val classFileExtension = ".class"
        // val classMimeType = MimeType("application", "java-vm")
        // archive file MIME
        // const val jarFileExtension = ".jar"
        // val jarMimeType = MimeType("application", "java-archive")
    }

    sealed class JavaFactory(val lang: JavaLang) : Factory<JavaBackend> {
        private val tag get() = lang.backendId.uniqueId

        final override val backendId = lang.backendId

        // Determines a module prefix using library configuration
        open fun javaLibraryConfigs(base: LibraryConfigurations) =
            JavaLibraryConfigs(base)

        override val backendMeta = BackendMeta(
            backendId,
            languageLabel = LanguageLabel(tag),
            fileExtensionMap = mapOf(FileType.Module to sourceFileExtension),
            mimeTypeMap = mapOf(FileType.Module to MimeType("text", "java")),
        )
        override val specifics: JavaSpecifics = lang.specifics
        private val baseDirPath = dirPath("lang", "temper", "be", "java")
        val immediateLibraryResources: List<ResourceDescriptor> = declareResources(
            baseDirPath + dirPath("temper-core", "src", "main", "java"),
            filePath("temper", "core", "Core.java"),
            filePath("temper", "core", "Generator.java"),
            filePath("temper", "core", "NonNull.java"),
            filePath("temper", "core", "Nullable.java"),
            filePath("temper", "core", "Stub.java"),
            filePath("temper", "core", "Util.java"),
            filePath("temper", "core", "net", "Core.java"),
            filePath("temper", "core", "net", "NetResponse.java"),
        )
        override val coreLibraryResources: List<ResourceDescriptor> = run {
            val javaPath = dirPath("src", "main", "java")

            @Suppress("SpreadOperator")

            declareResources(
                baseDirPath + dirPath("temper-core"),
                filePath("pom.xml"),
                *immediateLibraryResources.map { javaPath + it.rsrcPath }.toTypedArray(),
            )
        }
        val perModuleResources: Map<QualifiedName, List<ResourceDescriptor>> = mapOf(
            temperRegexPkg to declareResources(
                baseDirPath + dirPath("temper-std", "src", "main", "java"),
                filePath("temper", "std", "regex", "Core.java"),
            ),
        )
        open val perModuleFileSpecs: Map<QualifiedName, List<MetadataFileSpecification>> = emptyMap()
        internal open val defaultDependencies = listOf(temperCoreDependency)

        val pomPath = filePath("pom.xml")
        val pomMime = MimeType("text", "xml")

        override fun make(setup: BackendSetup<JavaBackend>) = JavaBackend(this, setup)
    }

    /**
     * Workaround imperfect handling of std library in functional test prep.
     * TODO Once functional test setup uses more of the standard build process, this shouldn't be needed.
     */
    internal sealed class FunctionalTestJavaFactory(lang: JavaLang) : JavaFactory(lang) {
        /** Used by JavaNames to bundle std// modules with the primary module. */
        override fun javaLibraryConfigs(base: LibraryConfigurations): JavaLibraryConfigs =
            object : JavaLibraryConfigs(base) {
                /** Force bundling by rewriting the prefix. */
                override fun modulePrefixHandler(
                    modulePath: FilePath?,
                    config: JavaLibraryConfig?,
                    current: JavaLibraryConfig,
                ): List<String> = when {
                    modulePath == null -> config?.prefix ?: listOf()
                    modulePath.startsWith("std") -> current.prefix
                    modulePath.startsWith("temper", "std") -> current.prefix
                    else -> config?.prefix ?: listOf()
                }

                /** Drop extraneous 'std:std' libraries that are forcibly bundled. */
                override fun filterLibrary(lib: JavaLibraryConfig): Boolean {
                    return lib != current && lib.libraryName != "std"
                }
            }

        // For now, we just copy in temper-core for functional tests.
        override val defaultDependencies get() = emptyList<Dependency>()
        override val perModuleFileSpecs = run {
            val javaMime = backendMeta.mimeTypeMap[FileType.Module]
            mapOf(
                QualifiedName.knownSafe("work", "regex") to
                    super.perModuleResources[temperRegexPkg]!!.map { resourceDescriptor ->
                        val name = resourceDescriptor.rsrcPath.last().fullName
                        // Move this into work.regex for functional tests that still use "bundled" mode for now.
                        val content = resourceDescriptor.load().replace(
                            "package temper.std.regex;",
                            "package work.regex;",
                        )
                        // Not metadata, but it's treated just as raw content, so it's fine.
                        MetadataFileSpecification(filePath("work", "regex", name), javaMime, content)
                    },
            )
        }
    }
}

enum class JavaLang(private val major: Int, val specifics: JavaSpecifics, uniqueId: String) {
    Java8(JAVA8, Java8Specifics, "java8"),
    Java17(JAVA17, Java17Specifics, "java"),
    ;

    val backendId = BackendId(uniqueId)

    val majorVersion: String get() = specifics.majorVersionText

    /**
     * APIs are versioned in the stdlib by the `@since` annotation, which lists the major version when they were
     * introduced. This method returns true if its major version is at least the given version.
     */
    fun atLeastJdk(version: Int) = version <= major
}

/** Java SE major version to test against [JavaLang.atLeastJdk] */
const val JAVA8 = 8

/** Java SE major version to test against [JavaLang.atLeastJdk] */
const val JAVA9 = 9

/** Java SE major version to test against [JavaLang.atLeastJdk] */
const val JAVA17 = 17

val JavaLang.factory: JavaBackend.JavaFactory get() = when (this) {
    JavaLang.Java8 -> JavaBackend.Java8
    JavaLang.Java17 -> JavaBackend.Java17
}

// Internal numbering went from 1.8 to 9 in https://openjdk.org/jeps/223

/** Parse a major version, accepting versions pre- and post-JEP 223. */
fun parseJavaMajorVersion(major: String): Int? {
    val parts = major.split('.', limit = 2)
    val m1 = parts[0].toIntOrNull() ?: return null
    val m2 = parts.getOrNull(1)?.toIntOrNull()
    return when {
        m1 < 1 -> null
        m1 == 1 -> when {
            m2 == null -> null
            m2 <= 0 -> 1
            else -> m2
        }
        else -> m1
    }
}

/** Write a major version, generating versions pre- and post-JEP 223. */
fun stringifyJavaMajorVersion(major: Int): List<String> =
    if (major >= JAVA8) {
        listOf("$major")
    } else {
        listOf("1.$major", "$major")
    }

fun FilePath.startsWith(vararg baseNames: String): Boolean {
    val segs = segments
    if (baseNames.size > segs.size) {
        return false
    }
    for (idx in baseNames.indices) {
        if (baseNames[idx] != segs[idx].baseName) {
            return false
        }
    }
    return true
}

/**
 * Like for other backends, this is stopgap for mostly deprecated file modules
 * where we still can't express a root/top-level module for a library.
 */
private fun MutableList<Backend.OutputFileSpecification>.addMergedInitClasses(
    javaLibConfigs: JavaLibraryConfigs,
    moduleNames: List<ModuleName>,
    rootInfo: ModuleInfo,
) {
    val sourceDir = J.SourceDirectory.MainJava.filePath.resolve(
        dirPath(rootInfo.packageName.parts.map { it.outputNameText }),
    )
    fun javaFileSpec(content: String, name: OutName) = Backend.MetadataFileSpecification(
        path = sourceDir.resolve(filePath("$name${JavaBackend.sourceFileExtension}")),
        mimeType = JavaBackend.sourceMimeType,
        content = content,
    )
    // In case any modules are pure test, exclude them from load.
    val isTest = mapNotNull translateds@{ result ->
        val fileSpec = (result as? Backend.TranslatedFileSpecification) ?: return@translateds null
        val program = fileSpec.content as? J.Program ?: return@translateds null
        val isTest = program.programMeta.sourceDirectory == J.SourceDirectory.TestJava
        (program.className ?: return@translateds null).toString() to isTest
    }.toMap()
    // But beyond that, gather up every recognized global class.
    val globalClassLoads = moduleNames.mapNotNull modules@{ moduleName ->
        val moduleInfo = javaLibConfigs.moduleInfo(moduleName)
        val className = moduleInfo.qualifiedClassName(DepCat.Production).toString()
        isTest[className] == false || return@modules null
        // Class names don't have quotes in them, so this should be safe.
        "            Class.forName(\"$className\");"
    }.joinToString("\n")
    val globalsContent = """
        |package ${rootInfo.packageName};
        |public class ${rootInfo.globalsClassName} {
        |    private ${rootInfo.globalsClassName}() {}
        |    static {
        |        try {
        |$globalClassLoads
        |        } catch (ClassNotFoundException e) {
        |            throw new NoClassDefFoundError(e.getMessage());
        |        }
        |    }
        |}
    """.trimMargin()
    add(javaFileSpec(content = globalsContent, name = rootInfo.globalsClassName))
    val mainContent = """
        |package ${rootInfo.packageName};
        |import temper.core.Core;
        |public class ${rootInfo.entryClassName} {
        |    private ${rootInfo.entryClassName}() {}
        |    public static void main(String[] args) throws ClassNotFoundException {
        |        Core.initSimpleLogging();
        |        Class.forName("${rootInfo.qualifiedClassName(DepCat.Production)}");
        |    }
        |}
    """.trimMargin()
    add(javaFileSpec(content = mainContent, name = rootInfo.entryClassName))
}
