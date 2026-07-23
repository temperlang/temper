package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.common.Either
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.asciiTitleCase
import lang.temper.common.asciiUnTitleCase
import lang.temper.common.assertStructure
import lang.temper.common.buildListMultimap
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.indexOfNext
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLeaf
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.putMultiList
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testModuleName
import lang.temper.common.toStringViaBuilder
import lang.temper.env.Export
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.ModuleCustomizeHook
import lang.temper.fs.Url
import lang.temper.lexer.Genre
import lang.temper.lexer.languageConfigForExtension
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.testdir.RegeneratedFilesList
import lang.temper.testdir.TestFileBundle
import lang.temper.testdir.readTestDir
import lang.temper.testdir.regenerateFiles
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.NominalType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.ignorableMetadataInTest
import lang.temper.value.Abort
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.staticTypeContained
import lang.temper.value.staySymbol

/**
 * A directory path relative to the directory containing `frontend/commonTest/.../README-stage-tests.md`
 * that specifies test inputs and outputs.  See that README file for details on the test structure.
 *
 * This string is also used to filter test-file regeneration.  If [shouldRegenerateStageTest] returns `true`
 * for it then [assertModuleAtStage] will write the expected outputs to output files instead of reading
 * and comparing which allows using `git diff` to understand the consequences of a change to details of
 * the frontend's intermediate representation.
 */
data class StageTestDir(val url: Url) {
    init {
        check(!url.isAbsolute && url.path != null && url.authority == null) { "$url" }
    }
    constructor(str: String) : this(Url.create(str))
}

private fun shouldRegenerateStageTest(
    stageTestDir: StageTestDir,
    isEmpty: Boolean,
): Boolean {
    // ignore() to suppress unused parameter warnings because in git this should just return false.
    ignore(stageTestDir)
    ignore(isEmpty)

    return false
}

/** The URL for reading test resource files. A `file:` URL which allows enumerating resources. */
internal expect val stageTestDirFileRoot: Url

/** The `file:` URL under which to write changes when regenerating test resource files. */
internal expect val stageTestDirFileSourceRoot: Url

/**
 * A test harness that advances a module until a specific stage, capturing snapshots of the
 * AST so that we can compare them selectively against a desired output.
 */
internal fun assertModuleAtStage(
    want: String = "",
    stageTestDir: StageTestDir = StageTestDir("TODO"),
    stage: Stage,
    genre: Genre = Genre.Library,
    pseudoCodeDetail: PseudoCodeDetail = PseudoCodeDetail.default,
    manualCheck: ((JsonObject) -> Unit)? = null,
    nameSimplifying: Boolean = false,
    moduleResultNeeded: Boolean = false,
    loc: ModuleName? = null,
    stagingFlags: Set<BuiltinName> = emptySet(),
    stackTracesForErrors: Boolean = false,
    logEntryWanted: (LogEntry) -> Boolean = { it.level >= Log.Warn },
) {
    assertModuleAtStage(
        want = want,
        stageTestDir = stageTestDir,
        stage = stage,
        genre = genre,
        pseudoCodeDetail = pseudoCodeDetail,
        manualCheck = manualCheck,
        nameSimplifying = nameSimplifying,
        moduleResultNeeded = moduleResultNeeded,
        loc = loc,
        stagingFlags = stagingFlags,
        stackTracesForErrors = stackTracesForErrors,
        logEntryWanted = logEntryWanted,
    ) { module, moduleAdvancer, testDir, regeneratedFiles ->
        provisionModuleForStageTest(testDir, module, moduleAdvancer, regeneratedFiles)
    }
}

/**
 * A test harness that advances a module until a specific stage, capturing snapshots of the
 * AST so that we can compare them selectively against a desired output.
 */
internal fun assertModuleAtStage(
    want: String = "",
    stageTestDir: StageTestDir,
    stage: Stage,
    genre: Genre = Genre.Library,
    pseudoCodeDetail: PseudoCodeDetail = PseudoCodeDetail.default,
    loc: ModuleName? = null,
    manualCheck: ((JsonObject) -> Unit)? = null,
    nameSimplifying: Boolean = false,
    moduleResultNeeded: Boolean = false,
    stagingFlags: Set<BuiltinName> = emptySet(),
    stackTracesForErrors: Boolean = false,
    logEntryWanted: (LogEntry) -> Boolean = { it.level >= Log.Warn },
    provisionModule: (Module, ModuleAdvancer, TestFileBundle, RegeneratedFilesList?) -> Unit,
) {
    val testDir = readTestDir(stageTestDirFileRoot.resolve(stageTestDir.url))
    val regeneratedFileList: RegeneratedFilesList? =
        if (shouldRegenerateStageTest(stageTestDir, isEmpty = testDir.isEmpty())) {
            console.info("assertModuleAtStage is regenerating test files under ${stageTestDir.url}")
            mutableListOf()
        } else {
            null
        }

    var thousandsOfStepsLeft = 100
    val continueCondition = {
        if (thousandsOfStepsLeft > 0) {
            thousandsOfStepsLeft -= 1
            true
        } else {
            false
        }
    }

    val outputsByStage = mutableMapOf<Stage?, StageSnapshot>()
    var exitKind: ExitKind = ExitKind.Normal
    var isTestModule: (Module) -> Boolean = { _ -> false } // reassigned
    val moduleHook = ModuleCustomizeHook { module, _ ->
        if (isTestModule(module)) {
            val outputTree = module.treeForDebug?.copy(copyInferences = true)
            val stageDone = module.stageCompleted
            outputsByStage[stageDone] = when (stageDone) {
                Stage.Parse -> ParseStageSnapshot(
                    outputTree,
                    module.appendix,
                    pseudoCodeDetail,
                    exitKind,
                )

                Stage.Run -> RunStageSnapshot(module.runResult, exitKind)
                else -> TreeStageSnapshot(
                    outputTree,
                    outputTree?.typeDefinitions,
                    module.exports,
                    module.ok,
                    pseudoCodeDetail,
                    exitKind,
                )
            }
        }
    }

    val listBackedLogSink = ListBackedLogSink()
    val projectLogSink = ValueSimplifyingLogSink(listBackedLogSink, nameSimplifying = nameSimplifying).let {
        if (stackTracesForErrors) {
            DumpStackTracesForThoseErrors(it)
        } else {
            it
        }
    }
    val moduleConfig = ModuleConfig(moduleCustomizeHook = moduleHook)
    val moduleAdvancer = ModuleAdvancer(projectLogSink, moduleConfig = moduleConfig)
    val moduleLoc = loc ?: testModuleName
    moduleAdvancer.configureLibrary(testLibraryName, moduleLoc.libraryRoot())
    val module = moduleAdvancer.createModule(
        loc = moduleLoc,
        console = console,
        continueCondition = continueCondition,
        genre = genre,
        allowDuplicateLogPositions = true,
    )
    isTestModule = { it === module }
    val allStagingFlags = buildSet {
        addAll(stagingFlags)
        if (moduleResultNeeded) {
            add(StagingFlags.moduleResultNeeded)
        }
    }
    if (allStagingFlags.isNotEmpty()) {
        module.addEnvironmentBindings(allStagingFlags.associateWith { TBoolean.valueTrue })
    }
    provisionModule(module, moduleAdvancer, testDir, regeneratedFileList)

    val stopBeforeForMainModule = Stage.after(stage)
    val stopBefore = { m: Module ->
        when {
            stopBeforeForMainModule == null -> null
            m === module -> stopBeforeForMainModule
            // Any other modules that the main module might import
            // need to advance to at least Export to unblock the
            // main module and each other
            stopBeforeForMainModule <= Stage.Export -> Stage.after(Stage.Export)
            else -> stopBeforeForMainModule
        }
    }
    try {
        moduleAdvancer.advanceModules(stopBefore = stopBefore)
    } catch (_: Panic) {
        exitKind = ExitKind.Panic
    } catch (_: Abort) {
        exitKind = ExitKind.Abort
    }
    // Run the module hook manually to generate a final snapshot
    moduleHook.customize(module, false)

    if (!module.ok) {
        module.failLog.logReasonForFailure()
    }

    val stdout = toStringViaBuilder { outputBuffer ->
        listBackedLogSink.allEntries.forEach { logEntry ->
            if (logEntry.template == MessageTemplate.StandardOut) {
                outputBuffer.append(logEntry.values.joinToString(" ")).append('\n')
            }
        }
    }

    val got = listBackedLogSink.wrapErrorsAround(
        object : Structured {
            override fun destructure(structureSink: StructureSink) = structureSink.obj {
                val stageCompleted = module.stageCompleted
                key("stageCompleted", isDefault = stageCompleted == stage) {
                    value(stageCompleted)
                }
                val ok = module.ok
                key("ok", isDefault = ok) { value(ok) }
                for ((stageRun, parts) in outputsByStage) {
                    key(
                        (stageRun?.name ?: "nullStage").asciiUnTitleCase(),
                        if (stageRun != stage) { Hints.u } else { Hints.empty },
                    ) {
                        this.value(parts)
                    }
                }
                key("stdout", isDefault = stdout == "") {
                    value(stdout)
                }
            }
        },
        logEntryWanted,
    )

    if (manualCheck != null) {
        val renumbered = PseudoCodeNameRenumberer.newStructurePostProcessor()(got)
        manualCheck(JsonValueBuilder.build(emptyMap()) { value(renumbered) } as JsonObject)
    } else if (regeneratedFileList != null) {
        val wantJson = JsonValue.parse(indentDoubleHash(want), tolerant = true).result as JsonObject
        fun walk(stage: Stage?, key: String, value: JsonValue) {
            if (value is JsonLeaf<*>) {
                when (value) {
                    is JsonString -> when (key) {
                        ".body" if stage != null -> {
                            val stageStr = stage.name.asciiUnTitleCase()
                            regeneratedFileList.add(
                                Url("expect/$stageStr.temper") to Either.Left(value.s),
                            )
                        }
                        ".body.code" if stage != null -> {
                            val stageStr = stage.name.asciiUnTitleCase()
                            regeneratedFileList.add(
                                Url("expect/$stageStr.temper") to Either.Left(value.s),
                            )
                        }
                        "" if stage == Stage.Run -> {
                            regeneratedFileList.add(
                                Url("expect/run-result.json") to Either.Left(value.toJsonString()),
                            )
                        }
                        ".stdout" if stage == null -> {
                            regeneratedFileList.add(
                                Url("expect/stdout.txt") to Either.Left(value.s),
                            )
                        }
                        ".stageCompleted" if stage == null -> {}
                        else -> TODO("$stage . `$key` got string")
                    }

                    is JsonBoolean -> when (key) {
                        else -> TODO("$key got bool")
                    }
                    is JsonDouble -> when (key) {
                        else -> TODO("$key got double")
                    }
                    is JsonLong -> when (key) {
                        else -> TODO("$key got long")
                    }
                    JsonNull -> when (key) {
                        else -> TODO("$key got null")
                    }
                }
            } else if (key == ".appendix" && stage != null) {
                regeneratedFileList.add(
                    Url("expect/${stage.name.asciiUnTitleCase()}-appendix.json") to
                        Either.Left("${value.toJsonString()}\n"),
                )
            } else if (key == ".types" && stage != null) {
                regeneratedFileList.add(
                    Url("expect/${stage.name.asciiUnTitleCase()}-types.json") to
                        Either.Left("${value.toJsonString()}\n"),
                )
            } else if (value is JsonObject) {
                when (key) {
                    ".exports" if (stage != null) -> {
                        regeneratedFileList.add(
                            Url("expect/${stage.name.asciiUnTitleCase()}-exports.json") to
                                Either.Left("${value.toJsonString()}\n"),
                        )
                    }
                    else -> {
                        for (p in value) {
                            var nextStage = stage
                            var keySuffix = ".${p.key}"
                            if (key == "" && nextStage == null) {
                                nextStage = try {
                                    Stage.valueOf(p.key.asciiTitleCase())
                                } catch (_: IllegalArgumentException) {
                                    null
                                }
                                if (nextStage != null) {
                                    keySuffix = ""
                                }
                            }
                            val nextKey = "$key$keySuffix"
                            walk(nextStage, nextKey, p.value)
                        }
                    }
                }
            } else if (value is JsonArray) {
                when (key) {
                    ".body" if stage != null -> {
                        val relPath = "expect/${stage.name.asciiUnTitleCase()}.lispy"
                        regeneratedFileList.add(
                            Url(relPath) to Either.Left("${value.toJsonString()}\n"),
                        )
                    }
                    ".body.tree" if stage != null -> {
                        val relPath = "expect/${stage.name.asciiUnTitleCase()}.lispy"
                        regeneratedFileList.add(
                            Url(relPath) to Either.Left("${value.toJsonString()}\n"),
                        )
                    }
                    ".errors" -> {
                        regeneratedFileList.add(
                            Url("expect/errors.json") to Either.Left("${value.toJsonString()}\n"),
                        )
                    }
                    "" if stage == Stage.Run -> {
                        regeneratedFileList.add(
                            Url("expect/run-result.json") to Either.Left(value.toJsonString()),
                        )
                    }
                    else -> TODO("$key got array")
                }
            } else {
                TODO("$key $wantJson")
            }
        }
        walk(null, "", wantJson)
    } else {
        assertStructure(
            expectedJson = want.stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            input = got,
            postProcessor = { s -> PseudoCodeNameRenumberer.newStructurePostProcessor()(s) },
        )
    }

    regeneratedFileList?.let {
        regenerateFiles(stageTestDirFileSourceRoot.resolve("${stageTestDir.url}/"), it.toList())
    }
}

internal fun provisionModuleForStageTest(
    testFileBundle: TestFileBundle,
    module: Module,
    moduleAdvancer: ModuleAdvancer,
    regeneratedFilesList: RegeneratedFilesList?,
) {
    val chunks = buildList {
        for ((relPath, content) in testFileBundle.files) {
            if (relPath.segments.first().fullName == "work") {
                add(relPath.copy(segments = relPath.segments.drop(1)) to content)
            }
        }
    }

    val inputsByDir = buildListMultimap {
        for ((path, content) in chunks) {
            val dir = if (path.isDir) {
                path
            } else {
                path.dirName()
            }
            putMultiList(dir, path to content)
        }
    }

    for ((dir, inputs) in inputsByDir) {
        val moduleName = testModuleName.copy(sourceFile = dir)
        val moduleToProvision = if (moduleName == testModuleName) {
            module
        } else {
            moduleAdvancer.createModule(moduleName, module.console)
        }
        for ((filePath, content) in inputs) {
            val languageConfig = languageConfigForExtension(filePath.segments.lastOrNull()?.extension)
            moduleToProvision.deliverContent(
                ModuleSource(
                    filePath = filePath,
                    fetchedContent = content,
                    languageConfig = languageConfig,
                ),
            )
            if (regeneratedFilesList != null) {
                var path = dir
                if (path.isDir) {
                    val ext = languageConfig.dotExtension?.let { ".temper$it" } ?: ".temper"
                    path = path.resolve(path.segments.last().withExtension(ext), isDir = false)
                }
                val srcUrl = Url(null, null, "work/${path.join()}", null, null)
                regeneratedFilesList.add(srcUrl to Either.Left(content))
            }
        }
    }
}

private val (Tree).typeDefinitions: List<TypeDefinitionSnapshot>
    get() {
        val definitions = mutableSetOf<TypeDefinition>()
        TreeVisit.startingAt(this)
            .forEachContinuing {
                val staticType = it.staticTypeContained
                if (staticType is NominalType) {
                    definitions.add(staticType.definition)
                }
            }
            .visitPreOrder()

        val snapshots = mutableMapOf<TypeDefinition, TypeDefinitionSnapshot>()
        fun snapshotDefinition(d: TypeDefinition): TypeDefinitionSnapshot {
            val extant = snapshots[d]
            if (extant != null) { return extant }
            return when (d) {
                is TypeShape -> {
                    fun snapshotTypeShape(
                        d: TypeShape,
                    ): TypeShapeSnapshot {
                        val snapshot = TypeShapeSnapshot(
                            pos = d.pos,
                            word = d.word,
                            name = d.name,
                            abstractness = d.abstractness,
                            metadata = d.metadata.snapshot(),
                            superTypes = d.superTypes.toList(),
                            typeParameters = d.typeParameters.map { TypeParameterShapeSnapshot(it.name) },
                            properties = d.properties.map {
                                PropertyShapeSnapshot(
                                    name = it.name,
                                    symbol = it.symbol,
                                    visibility = it.visibility,
                                    abstractness = it.abstractness,
                                    getter = it.getter,
                                    setter = it.setter,
                                    metadata = it.metadata.snapshot(),
                                )
                            },
                            methods = d.methods.map {
                                MethodShapeSnapshot(
                                    it.name,
                                    it.symbol,
                                    it.visibility,
                                    it.methodKind,
                                    it.openness,
                                    metadata = it.metadata.snapshot(),
                                )
                            },
                            staticProperties = d.staticProperties.map {
                                StaticPropertyShapeSnapshot(
                                    it.name,
                                    it.symbol,
                                    it.visibility,
                                    metadata = it.metadata.snapshot(),
                                )
                            },
                            sealedSubTypes = d.sealedSubTypes?.map {
                                snapshotTypeShape(it)
                            },
                        )
                        return snapshot
                    }
                    snapshotTypeShape(d)
                }
                is TypeFormal -> {
                    val snapshot = TypeFormalSnapshot(
                        d.pos,
                        d.name,
                        d.word,
                        d.variance,
                        d.upperBounds.toList(),
                    )
                    snapshots[d] = snapshot
                    snapshot
                }
            }
        }

        return definitions.map(::snapshotDefinition)
    }

private sealed class StageSnapshot : Structured

private data class ParseStageSnapshot(
    val tree: Tree?,
    val appendix: JsonValue?,
    val pseudoCodeDetail: PseudoCodeDetail,
    val exitKind: ExitKind,
) : StageSnapshot() {
    override fun destructure(structureSink: StructureSink) {
        structureSink.obj {
            key("body") {
                val tree = tree
                if (tree != null) {
                    obj {
                        destructureTreeMultipleRepresentations(this, tree, pseudoCodeDetail)
                    }
                } else {
                    nil()
                }
            }
            key("appendix", isDefault = appendix == null) {
                value(appendix)
            }
            key("exitKind", isDefault = exitKind == ExitKind.Normal) {
                value(exitKind)
            }
        }
    }
}

private data class TreeStageSnapshot(
    val tree: Tree?,
    val typeDefinitions: List<TypeDefinitionSnapshot>?,
    val exports: List<Export>?,
    val passed: Boolean,
    val pseudoCodeDetail: PseudoCodeDetail,
    val exitKind: ExitKind,
) : StageSnapshot() {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("body") {
            val tree = tree
            if (tree != null) {
                obj {
                    destructureTreeMultipleRepresentations(this, tree, pseudoCodeDetail)
                }
            } else {
                nil()
            }
        }
        val passed = passed
        key("passed", isDefault = passed) { value(passed) }
        val typeDefinitions = typeDefinitions
        val hasTypes = true == typeDefinitions?.isNotEmpty()
        key(
            "types",
            Hints.u,
            isDefault = !hasTypes,
        ) {
            if (typeDefinitions != null) {
                val keyToTypeDefinition = typeDefinitions
                    .map { it.name.displayName to it }
                    .sortedBy { it.first }
                obj {
                    keyToTypeDefinition.forEach { (k, d) ->
                        key(k) {
                            d.destructure(this, nameHints = Hints.u)
                        }
                    }
                }
            } else {
                nil()
            }
        }
        val exports = exports ?: emptyList()
        key("exports", Hints.u) {
            obj {
                for ((_, name, exportedValue) in exports) {
                    key(name.baseName.nameText) {
                        value(exportedValue)
                    }
                }
            }
        }
        key("exitKind", isDefault = exitKind == ExitKind.Normal) {
            value(exitKind)
        }
    }
}

private data class RunStageSnapshot(
    val runResult: PartialResult?,
    val exitKind: ExitKind,
) : StageSnapshot() {
    override fun destructure(structureSink: StructureSink) {
        if (runResult != null || exitKind == ExitKind.Normal) {
            structureSink.value(runResult)
        } else {
            structureSink.obj {
                key("exitKind") { value(exitKind) }
            }
        }
    }
}

private enum class ExitKind {
    Normal,
    Abort,
    Panic,
}

private sealed class TypeDefinitionSnapshot : Positioned, Structured {
    abstract val name: TemperName

    abstract fun destructure(structureSink: StructureSink, nameHints: Set<StructureHint>)

    final override fun destructure(structureSink: StructureSink) =
        destructure(structureSink, Hints.empty)
}

private class TypeShapeSnapshot(
    override val pos: Position,
    val word: Symbol?,
    override val name: TemperName,
    val abstractness: Abstractness,
    val metadata: Map<Symbol, List<Value<*>?>>,
    val superTypes: List<NominalType>,
    val typeParameters: List<TypeParameterShapeSnapshot>,
    val properties: List<PropertyShapeSnapshot>,
    val methods: List<MethodShapeSnapshot>,
    val staticProperties: List<StaticPropertyShapeSnapshot>,
    val sealedSubTypes: List<TypeShapeSnapshot>?,
) : TypeDefinitionSnapshot() {
    override fun destructure(structureSink: StructureSink, nameHints: Set<StructureHint>) {
        structureSink.obj {
            key("__DO_NOT_CARE__", Hints.su) { value("__DO_NOT_CARE__") }
            key("name", nameHints) { value(name) }
            key("word", Hints.u) { value(word) }
            key("abstract", isDefault = abstractness == Abstractness.Concrete) {
                value(abstractness == Abstractness.Abstract)
            }
            val typeParameters = typeParameters
            key("typeParameters", isDefault = typeParameters.isEmpty()) { value(typeParameters) }
            val superTypes = superTypes
            key("supers", isDefault = superTypes.isEmpty()) { value(superTypes) }
            val properties = properties
            key("properties", isDefault = properties.isEmpty()) { value(properties) }
            val methods = methods
            key("methods", isDefault = methods.isEmpty()) { value(methods) }
            val staticProperties = staticProperties
            key("staticProperties", isDefault = staticProperties.isEmpty()) { value(staticProperties) }
            val sealedSubTypes = sealedSubTypes?.map { it.name }
            key("sealedSubTypes", isDefault = sealedSubTypes == null) { value(sealedSubTypes) }
            destructureMetadata(this, metadata)
        }
    }
}

private class TypeFormalSnapshot(
    override val pos: Position,
    override val name: ResolvedName,
    val word: Symbol?,
    val variance: Variance,
    val upperBounds: List<NominalType>,
) : TypeDefinitionSnapshot() {
    override fun destructure(structureSink: StructureSink, nameHints: Set<StructureHint>) {
        structureSink.obj {
            key("__DO_NOT_CARE__", Hints.su) { value("__DO_NOT_CARE__") }
            key("name", nameHints) { value(name) }
            key("word", Hints.u) { value(word) }
            key("variance", isDefault = variance == Variance.Default) { value(variance) }
            val extendsOnlyAnyValue = upperBounds.size == 1 &&
                upperBounds[0].let { upperBound ->
                    upperBound.definition.name == WellKnownTypes.anyValueTypeDefinition.name &&
                        upperBound.bindings.isEmpty()
                }
            key("upperBounds", isDefault = extendsOnlyAnyValue) {
                value(upperBounds)
            }
        }
    }
}

private sealed class MemberSnapshot(
    val name: TemperName,
    val symbol: Symbol?,
    val visibility: Visibility?,
    val metadata: Map<Symbol, List<Value<*>?>>,
) : Structured {
    fun destructureCommonProperties(sink: PropertySink) {
        sink.key("name") { value(name) }
        sink.key("symbol", isDefault = symbol == name.toSymbol()) { value(symbol) }
        if (visibility != null) {
            val isVisibilityDefault = visibility == Visibility.Public
            sink.key("visibility", isDefault = isVisibilityDefault) { visibility.destructure(this) }
        }
        destructureMetadata(sink, metadata)
    }
}

private class TypeParameterShapeSnapshot(
    name: TemperName,
) : MemberSnapshot(name, null, null, emptyMap()) {
    override fun destructure(structureSink: StructureSink) {
        structureSink.obj {
            key("name") { value(name) }
        }
    }
}

private class PropertyShapeSnapshot(
    name: TemperName,
    symbol: Symbol,
    visibility: Visibility,
    val abstractness: Abstractness,
    val getter: TemperName?,
    val setter: TemperName?,
    metadata: Map<Symbol, List<Value<*>?>>,
) : MemberSnapshot(name, symbol, visibility, metadata) {
    override fun destructure(structureSink: StructureSink) {
        structureSink.obj {
            destructureCommonProperties(this)
            key(
                "abstract",
                hints = if (getter == null && setter == null) Hints.empty else Hints.u,
            ) { value(abstractness == Abstractness.Abstract) }
            key("getter", isDefault = getter == null) { value(getter) }
            key("setter", isDefault = setter == null) { value(setter) }
        }
    }
}

private class MethodShapeSnapshot(
    name: TemperName,
    symbol: Symbol,
    visibility: Visibility,
    val methodKind: MethodKind,
    val openness: OpenOrClosed,
    metadata: Map<Symbol, List<Value<*>?>>,
) : MemberSnapshot(name, symbol, visibility, metadata) {
    override fun destructure(structureSink: StructureSink) {
        structureSink.obj {
            destructureCommonProperties(this)
            key("kind", isDefault = methodKind == MethodKind.Normal) {
                value(methodKind)
            }
            key("open", isDefault = openness == OpenOrClosed.Open) {
                value(openness == OpenOrClosed.Open)
            }
        }
    }
}

private class StaticPropertyShapeSnapshot(
    name: TemperName,
    symbol: Symbol,
    visibility: Visibility,
    metadata: Map<Symbol, List<Value<*>?>>,
) : MemberSnapshot(name, symbol, visibility, metadata) {
    override fun destructure(structureSink: StructureSink) {
        structureSink.obj {
            destructureCommonProperties(this)
        }
    }
}

private fun MetadataValueMultimap.snapshot(): Map<Symbol, List<Value<*>?>> = buildMap {
    this@snapshot.entries.forEach { (k, v) ->
        when (k) {
            staySymbol -> return@forEach
            else -> {}
        }
        this[k] = v
    }
}

private fun destructureMetadata(sink: PropertySink, metadata: Map<Symbol, List<Value<*>?>>) {
    sink.key("metadata", isDefault = metadata.all { (k) -> k in ignorableMetadataInTest }) {
        obj {
            metadata.forEach { (k, edges) ->
                val hints = if (k in ignorableMetadataInTest) {
                    Hints.u
                } else {
                    emptySet()
                }
                key(k.text, hints) {
                    arr {
                        edges.forEach { v ->
                            value(v)
                        }
                    }
                }
            }
        }
    }
}

val testLibraryName = DashedIdentifier("test-code")

internal class DumpStackTracesForThoseErrors(private val logSink: LogSink) : LogSink {
    override val hasFatal: Boolean get() = logSink.hasFatal

    override fun log(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        if (level >= Log.Error) {
            console.trace(template.format(values))
        }
        logSink.log(level, template, pos, values, fyi)
    }
}

private fun indentDoubleHash(json: String): String {
    val lines = json.lines().toMutableList()
    var i = 0
    val n = lines.size
    var changed = false
    while (i < n) {
        val line = lines[i]
        i += 1
        val lineTrimmed = line.trim()
        if (lineTrimmed.endsWith("```")) {
            val j = lines.indexOfNext(i) {
                it.trim().startsWith("```")
            }
            check(j > i) { "i=$i, j=$j\n$json" }
            if ((i..<j).any { lines[it].startsWith("##") }) {
                val indent = lines[j].substring(0, lines[j].indexOf("```"))
                for (k in i..<j) {
                    val stringLine = lines[k]
                    val prefix =
                        if (stringLine.startsWith("##")) {
                            indent
                        } else if (stringLine.isEmpty()) {
                            ""
                        } else {
                            "  "
                        }
                    lines[k] = "$prefix$stringLine"
                    changed = true
                }
            }
            i = j + 1
        }
    }

    return if (changed) {
        lines.joinToString("\n")
    } else {
        json
    }
}
