package lang.temper.be

import lang.temper.be.syncstaging.applyBackendsSynchronously
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.orThrow
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.partitionSourceFilesIntoModules
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.NullSystemAccess
import lang.temper.fs.OutputRoot
import lang.temper.interp.importExport.Exporter
import lang.temper.interp.importExport.LOCAL_FILE_SPECIFIER_PREFIX
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.LogSink
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.value.TBoolean
import lang.temper.value.toPseudoCode

fun inputFileMapFromJson(inputJsonPathToContent: String): List<Pair<FilePath, String>> {
    val filePathsToContent = mutableMapOf<FilePath, String>()
    fun walk(path: FilePath, json: JsonValue): Unit = when (json) {
        is JsonObject -> {
            json.properties.forEach { property ->
                val pathFromKey = FilePath(
                    buildList {
                        addAll(path.segments)
                        property.key.split(UNIX_FILE_SEGMENT_SEPARATOR)
                            .mapNotNullTo(this) { filePathSegmentText ->
                                if (filePathSegmentText.isNotEmpty()) {
                                    FilePathSegment(filePathSegmentText)
                                } else {
                                    null
                                }
                            }
                    },
                    isDir = false,
                )
                walk(pathFromKey, property.value)
            }
        }
        is JsonString -> {
            check(path !in filePathsToContent) { "$path" }
            filePathsToContent[path.copy(isDir = false)] = json.s
        }
        is JsonArray,
        is JsonBoolean,
        is JsonDouble,
        is JsonLong,
        is JsonNull,
        -> throw IllegalArgumentException("$json")
    }
    walk(FilePath.emptyPath, JsonValue.parse(inputJsonPathToContent, tolerant = true).orThrow())
    return filePathsToContent.map { it.key to it.value }
}

fun <BACKEND : Backend<BACKEND>> generateCode(
    inputs: List<Pair<FilePath, String>>,
    factory: Backend.Factory<BACKEND>,
    backendConfig: Backend.Config,
    genre: Genre,
    moduleResultNeeded: Boolean,
    logSink: LogSink,
    lookupFactory: (BackendId) -> Backend.Factory<*>? = factoryFinder(factory),
): OutputRoot {
    val outputRoot = OutputRoot(MemoryFileSystem())
    val backendOrganization = organizeBackends(
        listOf(factory.backendId),
        lookupFactory = lookupFactory,
        onMissingFactory = { error(it) },
    )
    for (bucket in backendOrganization.backendBuckets) {
        for (backendId in bucket) {
            generateCode(
                inputs = inputs,
                factory = backendOrganization.factoriesById.getValue(backendId),
                activeFactories = backendOrganization.factoriesById.values,
                backendConfig = backendConfig,
                genre = genre,
                moduleResultNeeded = moduleResultNeeded,
                logSink = logSink,
                outputRoot = outputRoot,
            )
        }
    }
    return outputRoot
}

fun <BACKEND : Backend<BACKEND>> generateCode(
    inputs: List<Pair<FilePath, String>>,
    factory: Backend.Factory<BACKEND>,
    backendConfig: Backend.Config,
    genre: Genre,
    moduleResultNeeded: Boolean,
    logSink: LogSink,
    outputRoot: OutputRoot,
    activeFactories: Iterable<Backend.Factory<*>> = listOf(factory),
) {
    val backendId = factory.backendId
    val libraryRoot = dirPath()
    val libraryName = DashedIdentifier("my-test-library")
    val backendLib = dirPath(backendId.uniqueId, libraryName.text)
    val outputDir = outputRoot.makeDirs(backendLib)
    val moduleConfig = ModuleConfig(
        moduleCustomizeHook = { module, isNew ->
            for (activeFactory in activeFactories) {
                module.addEnvironmentBindings(activeFactory.environmentBindings)
            }
            if (isNew && (module.loc as? ModuleName)?.isPreface == false) {
                module.addEnvironmentBindings(
                    mapOf(StagingFlags.moduleResultNeeded to TBoolean.value(moduleResultNeeded)),
                )
            }
        },
    )
    val advancer = ModuleAdvancer(logSink, moduleConfig = moduleConfig)
    advancer.configureLibrary(
        LibraryConfiguration(
            libraryName = libraryName,
            libraryRoot = libraryRoot,
            supportedBackendList = listOf(backendId),
            classifyTemperSource = { StandaloneLanguageConfig },
        ),
    )
    val exporters = mutableMapOf<String, Exporter>()
    for ((srcPath, content) in inputs) {
        advancer.registerContentForSource(srcPath, content)
    }

    val sourceTree = MemoryFileSystem()
    for ((srcPath, content) in inputs) {
        sourceTree.write(srcPath, content.toByteArray())
    }
    val sourceTreeSnapshot = FilteringFileSystemSnapshot(sourceTree, FileFilterRules.Allow)
    partitionSourceFilesIntoModules(
        sourceTreeSnapshot, advancer, logSink, console, genre = genre,
        makeTentativeLibraryConfiguration = { name, root ->
            LibraryConfiguration(name, root, emptyList(), ::defaultClassifyTemperSource)
        },
    )

    for (module in advancer.getAllModules()) {
        val name = module.loc as? ModuleName
        if (name?.isPreface == false) {
            val srcPath = name.sourceFile
            exporters["$LOCAL_FILE_SPECIFIER_PREFIX$srcPath"] = module
        }
    }

    advancer.advanceModules()

    val libraryConfiguration = advancer.getLibraryConfiguration(libraryRoot)!!
    val libraryConfigurationsBundle = LibraryConfigurationsBundle.from(
        advancer.getAllLibraryConfigurations(),
    )

    val cancelGroup = makeCancelGroupForTest()
    val dependencies = Dependencies.Builder<BACKEND>(libraryConfigurationsBundle)
    val backends = buildMap {
        advancer.getPartitionedModules().forEach { (_, lib) ->
            val (config, moduleList) = lib
            val buildFileCreator = if (config == libraryConfiguration) {
                outputDir.systemAccess(cancelGroup)
            } else {
                NullSystemAccess(outputRoot.path.resolve(backendLib), cancelGroup)
            }
            this[config] = factory.make(
                BackendSetup(
                    config.libraryName,
                    dependencies,
                    moduleList,
                    buildFileCreator,
                    NullSystemAccess(outputRoot.path.resolve(backendLib), cancelGroup),
                    logSink,
                    NullDependencyResolver,
                    backendConfig,
                ),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    try {
        applyBackendsSynchronously(cancelGroup, backends.values.toList())
    } catch (e: RuntimeException) {
        console.group("modules") {
            advancer.getAllModules().forEach {
                console.group(it.loc.diagnostic) {
                    it.treeForDebug?.toPseudoCode(console.textOutput, singleLine = false)
                }
            }
        }
        throw e
    }
}
