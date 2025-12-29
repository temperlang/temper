package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.asciiUnTitleCase
import lang.temper.common.assertStructure
import lang.temper.common.buildListMultimap
import lang.temper.common.console
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.putMultiList
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.toStringViaBuilder
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.ModuleCustomizeHook
import lang.temper.interp.importExport.Export
import lang.temper.lexer.Genre
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
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

/** See input parameter to [assertModuleAtStage] */
const val TEST_INPUT_MODULE_BREAK = "////!module:"

/**
 * A test harness that advances a module until a specific stage, capturing snapshots of the
 * AST so that we can compare them selectively against a desired output.
 */
fun assertModuleAtStage(
    want: String = "",
    /**
     * The temper text which is parsed using [languageConfig].
     *
     * If the string [TEST_INPUT_MODULE_BREAK] occurs in the text, then this input
     * will be split up into multiple different modules which may import one another.
     *
     * This is useful for defining one main module to test, which is the first chunk,
     * but having it import other files whose gory details do not show up in [want].
     *
     * [TEST_INPUT_MODULE_BREAK] should be followed by a '/' separated path relative
     * to [testCodeLocation]'s parent directory.
     * That path will be used to derive the module name for the subsidiary modules.
     * By directory-module convention, source files will be grouped by the containing
     * directory.
     *
     * For example:
     *
     *     let { foo } = import("./foo");
     *     console.log("Main module code goes here");
     *     console.log(foo);
     *
     *     ////!module: ./foo/foo.temper
     *     export let foo = "FOO";
     *
     */
    input: String,
    stage: Stage,
    genre: Genre = Genre.Library,
    pseudoCodeDetail: PseudoCodeDetail = PseudoCodeDetail.default,
    manualCheck: ((JsonObject) -> Unit)? = null,
    nameSimplifying: Boolean = false,
    moduleResultNeeded: Boolean = false,
    loc: ModuleName? = null,
    stagingFlags: Set<BuiltinName> = emptySet(),
    languageConfig: LanguageConfig = StandaloneLanguageConfig,
    logEntryWanted: (LogEntry) -> Boolean = { it.level >= Log.Warn },
) = assertModuleAtStage(
    want = want,
    stage = stage,
    genre = genre,
    pseudoCodeDetail = pseudoCodeDetail,
    manualCheck = manualCheck,
    nameSimplifying = nameSimplifying,
    moduleResultNeeded = moduleResultNeeded,
    loc = loc,
    stagingFlags = stagingFlags,
    logEntryWanted = logEntryWanted,
) { module, moduleAdvancer ->
    val chunks = buildList {
        var path: FilePath = testCodeLocation
        val contentBuilder = StringBuilder()
        for (line in input.lines()) {
            if (line.startsWith(TEST_INPUT_MODULE_BREAK)) {
                add(path to contentBuilder.toString())
                contentBuilder.clear()
                var pathStr = line.substring(TEST_INPUT_MODULE_BREAK.length)
                var isDir = false
                if (pathStr.endsWith(UNIX_FILE_SEGMENT_SEPARATOR)) {
                    isDir = true
                    pathStr = pathStr.dropLast(UNIX_FILE_SEGMENT_SEPARATOR.length)
                }
                val relPath = pathStr.trim().split(UNIX_FILE_SEGMENT_SEPARATOR).map {
                    when (it) {
                        "." -> SameDirPseudoFilePathSegment
                        ".." -> ParentPseudoFilePathSegment
                        else -> FilePathSegment(it)
                    }
                }
                path = testCodeLocation.resolvePseudo(relPath, isDir = isDir) ?: error(line)
            } else {
                contentBuilder.append(line).append('\n')
            }
        }
        add(path to contentBuilder.toString())
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
            moduleToProvision.deliverContent(
                ModuleSource(
                    filePath = filePath,
                    fetchedContent = content,
                    languageConfig = languageConfig,
                ),
            )
        }
    }
}

/**
 * A test harness that advances a module until a specific stage, capturing snapshots of the
 * AST so that we can compare them selectively against a desired output.
 */
fun assertModuleAtStage(
    want: String = "",
    stage: Stage,
    genre: Genre = Genre.Library,
    pseudoCodeDetail: PseudoCodeDetail = PseudoCodeDetail.default,
    loc: ModuleName? = null,
    manualCheck: ((JsonObject) -> Unit)? = null,
    nameSimplifying: Boolean = false,
    moduleResultNeeded: Boolean = false,
    stagingFlags: Set<BuiltinName> = emptySet(),
    logEntryWanted: (LogEntry) -> Boolean = { it.level >= Log.Warn },
    provisionModule: (Module, ModuleAdvancer) -> Unit,
) {
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
    val projectLogSink = ValueSimplifyingLogSink(listBackedLogSink, nameSimplifying = nameSimplifying)
    val moduleConfig = ModuleConfig(moduleCustomizeHook = moduleHook)
    val moduleAdvancer = ModuleAdvancer(projectLogSink, moduleConfig = moduleConfig)
    val moduleLoc = loc ?: testModuleName
    moduleAdvancer.configureLibrary(testLibraryName, moduleLoc.libraryRoot())
    val module = moduleAdvancer.createModule(
        loc = moduleLoc,
        console = console,
        continueCondition = continueCondition,
        mayRun = true,
        genre = genre,
        allowDuplicateLogPositions = true,
    )
    @Suppress("AssignedValueIsNeverRead") // Used from module hook which runs after.
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
    provisionModule(module, moduleAdvancer)

    val stopBeforeForMainModule = Stage.after(stage)
    val stopBefore = { m: Module ->
        when {
            stopBeforeForMainModule == null -> null
            m === module -> stopBeforeForMainModule
            // Any other modules that the main module might import
            // need to be advance to at least Export to unblock the
            // main module and each other
            stopBeforeForMainModule <= Stage.Export -> Stage.after(Stage.Export)
            else -> stopBeforeForMainModule
        }
    }
    try {
        moduleAdvancer.advanceModules(stopBefore = stopBefore)
    } catch (_: Panic) {
        @Suppress("AssignedValueIsNeverRead") // Referenced by hook to snapshot
        exitKind = ExitKind.Panic
    } catch (_: Abort) {
        @Suppress("AssignedValueIsNeverRead") // Referenced by hook to snapshot
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
    } else {
        assertStructure(
            expectedJson = want,
            input = got,
            postProcessor = { s -> PseudoCodeNameRenumberer.newStructurePostProcessor()(s) },
        )
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
        key(
            "exports",
            isDefault = exports.isEmpty(),
        ) {
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
