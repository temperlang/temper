package lang.temper.frontend

import lang.temper.common.AtomicCounter
import lang.temper.common.Console
import lang.temper.common.CustomValueFormatter
import lang.temper.common.Log
import lang.temper.common.isNotEmpty
import lang.temper.common.json.JsonValue
import lang.temper.common.soleElementOrNull
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.cst.CstComment
import lang.temper.cst.CstInner
import lang.temper.env.Environment
import lang.temper.format.FilteringLogSink
import lang.temper.format.FormattingLogSink
import lang.temper.frontend.define.DefineStage
import lang.temper.frontend.disambiguate.DisAmbiguateStage
import lang.temper.frontend.export.ExportStage
import lang.temper.frontend.function.FunctionMacroStage
import lang.temper.frontend.generate.GenerateCodeStage
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.frontend.implicits.considerPrivilegedEnvironmentBindings
import lang.temper.frontend.implicits.standardLibraryConnecteds
import lang.temper.frontend.importstage.ImportStage
import lang.temper.frontend.lex.LexStage
import lang.temper.frontend.parse.ParseStage
import lang.temper.frontend.runtime.RuntimeEmulationStage
import lang.temper.frontend.syntax.SyntaxMacroStage
import lang.temper.frontend.typestage.TypeStage
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.immutableEnvironment
import lang.temper.interp.importExport.Export
import lang.temper.interp.importExport.Exporter
import lang.temper.interp.importExport.Importer
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.lexer.Genre
import lang.temper.lexer.TokenSource
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.ConfigurationKey
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.FileDependent
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.snapshot
import lang.temper.name.DashedIdentifier
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.StaticType
import lang.temper.type.TypeShape
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.DependencyCategory
import lang.temper.value.DependencyCategoryConfigurable
import lang.temper.value.DocumentContext
import lang.temper.value.Fail
import lang.temper.value.InternalFeatureKey
import lang.temper.value.PartialResult
import lang.temper.value.Promises
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import kotlin.math.max

class Module(
    projectLogSink: LogSink,
    override val loc: ModuleLocation,
    val console: Console,
    /**
     * Called periodically during long-running stages to decide whether to key working or
     * [lang.temper.value.Abort].
     */
    continueCondition: () -> Boolean,
    /**
     * Whether staging ends with the last stage before [Stage.Run] or whether execution may
     * proceed to [Stage.Run].
     * Modules that may be run might have [exports] and other derived information that come from
     * running instead of being derived from partial interpretation.
     */
    val mayRun: Boolean = false,
    sharedLocationContext: SharedLocationContext? = null,
    override val genre: Genre = Genre.Library,
    allowDuplicateLogPositions: Boolean = false,
    namingContext: NamingContext? = null,
) : Advancable,
    ConfigurationKey,
    DocumentContext,
    DependencyCategoryConfigurable,
    Exporter,
    FileDependent,
    Importer,
    Structured {

    private var _sources = listOf<ModuleSource>()

    // DocumentContext bits
    override val definitionMutationCounter = AtomicCounter()
    override val namingContext: ModuleNamingContext = when (namingContext) {
        null -> ModuleNamingContext(owner = this)
        is ModuleNamingContext -> namingContext
        else -> ModuleNamingContext(namingContext, owner = this)
    }

    override val configurationKey: ConfigurationKey get() = this
    override val sharedLocationContext = ModuleLocationContext(sharedLocationContext)

    val logSink: ModuleSpecificLogSink = ModuleSpecificLogSink(
        projectLogSink = projectLogSink,
        module = this,
        allowDuplicateLogPositions = allowDuplicateLogPositions,
        customValueFormatter = (projectLogSink as? FormattingLogSink)?.customValueFormatter
            ?: CustomValueFormatter.Nope,
        messageFilter = (projectLogSink as? FilteringLogSink)?.messageFilter ?: { true },
    )
    val failLog = FailLog(logSink = logSink)
    val projectLogSink get() = logSink.projectLogSink
    val allowDuplicateLogPositions get() = logSink.allowDuplicateLogPositions

    override fun formatPosition(pos: Position): String = logSink.formatPosition(pos)

    val continueCondition: () -> Boolean
    private var continueConditionReturnedFalse = false // Must be a better name for this.

    init {
        val cc = continueCondition

        // We need questions like canAdvance to be repeatable so don't want to call stateful
        // functions like continueCondition directly.
        // Instead, we wrap it to flip a bit on its first false return.
        // This has the side effect of also making this.continueCondition monotonic which is nice.
        fun continueConditionWrapper(): Boolean = when {
            continueConditionReturnedFalse -> false
            cc() -> true
            else -> {
                continueConditionReturnedFalse = true
                false
            }
        }
        this.continueCondition = ::continueConditionWrapper
    }

    var features = interpreterFeatureImplementations
        private set

    var connecteds: Map<String, (Signature2) -> Value<*>> = standardLibraryConnecteds()
        private set

    /** Add the given feature implementations to the existing ones. */
    fun useFeatures(newFeatures: Map<InternalFeatureKey, Value<*>>) {
        val combinedFeatures = mutableMapOf<InternalFeatureKey, Value<*>>()
        combinedFeatures.putAll(this.features)
        combinedFeatures.putAll(newFeatures)
        this.features = combinedFeatures.toMap()
    }

    /**
     * Wraps an environment to expose module accessible, but not module mutable bindings.
     * This is used by [interpretiveDanceStage] to wrap the builtin environment with an environment
     * from the compiler.  For example, a non-preface module will bring preface module declarations
     * into scope.
     */
    private fun wrapBuiltinEnvironment(parent: Environment): Environment {
        return immutableEnvironment(parent, stableEnvironmentBindings, isLongLived = true)
    }

    private var stableEnvironmentBindings = emptyMap<TemperName, Value<*>>()

    /** Adds bindings to those exposed by [wrapBuiltinEnvironment] */
    fun addEnvironmentBindings(newBindings: Map<TemperName, Value<*>>) {
        val allBindings = mutableMapOf<TemperName, Value<*>>()
        allBindings.putAll(stableEnvironmentBindings)
        allBindings.putAll(newBindings)
        stableEnvironmentBindings = allBindings.toMap()
        // Reset top level bindings in case previously set.
        _topLevelBindings = null
    }

    /**
     * An outer module which is implicitly imported during [Stage.Import].
     * This is used to connect a module to its preface.
     * Names exported from the preface are implicitly available to each module instance.
     */
    var outer: Module? = null

    private val additionalImplicitImports = mutableListOf<Export>()

    /** Used by the REPL to chain together modules. */
    fun addImplicitImports(newImports: Iterable<Export>) {
        additionalImplicitImports.addAll(newImports)
    }

    private val _importRecords = mutableListOf<Importer.ImportRecord>()
    override fun recordImportMetadata(importRecord: Importer.ImportRecord) {
        _importRecords.add(importRecord)
    }

    val importRecords get() = _importRecords.toList()

    private var _stageCompleted: Stage? = null

    private var _filePositions: Map<FilePath, FilePositions> = emptyMap()
    private var _outputName: ResolvedName? = null
    private var _outputType: StaticType? = null
    private var _runResult: PartialResult? = null
    private var _ok: Boolean = true
    private var _exports: List<Export>? = null
    private var _topLevelBindings: TopLevelBindings? = null
    private var _declaredTypeShapes: List<TypeShape> = emptyList()
    private var inProgress = false

    override val ok get() = _ok
    override val filePositions get() = _filePositions
    val sources get() = _sources

    /**
     * The promises used during [Stage.Run] or null to use new ones.
     * In the REPL, we need promises created in one REPL chunk (a module) to
     * persist so that another chunk can resolve it.
     */
    var promises: Promises? = null

    @Suppress("VariableNaming")
    private var _tree: BlockTree? = null

    /** The AST produced by the code generation phase. */
    override val generatedCode get() = _tree

    /** The result of [Stage.Run] if computed. */
    val runResult get() = _runResult

    /**
     * The name of the variable that will hold the module result if any.
     * `null` unless [StagingFlags.moduleResultNeeded] and we have reached [Stage.Type] and
     * run the MakeResultsExplicit pass.
     */
    val outputName get() = _outputName

    /** The type of [outputName] if any, and if types have been inferred and stored. */
    val outputType get() = _outputType
    override val exports get() = _exports

    internal val topLevelBindings get() = _topLevelBindings

    val declaredTypeShapes get() = _declaredTypeShapes

    /**
     * Includes builtins and any [additional bindings][addEnvironmentBindings]
     * supplied during configuration.
     */
    internal val freeNameEnvironment: Environment? get() = _topLevelBindings?.parent

    val stageCompleted get() = _stageCompleted
    val nextStage: Stage? get() = Stage.after(stageCompleted)

    /**
     * If the module is being [advance]d then the stage that it is attempting to complete;
     * otherwise the last stage completed.
     */
    val currentStage: Stage? get() = if (inProgress) { nextStage } else { stageCompleted }

    // TODO is this still needed?
    val treeForDebug get() = _tree

    /**
     * Content associated with ancillary tools that follows the second `;;;` super-token.
     *
     * When a module body has content like
     *
     *     // preface stuff
     *     ;;;
     *     // body stuff
     *     ;;;
     *     { "appendix": "stuff" }
     *
     * we parse the appendix stuff as Json and make it available here.
     *
     * This will eventually be used to:
     * - allow tools like linters to store per-file configuration
     * - allow versioning the language, by running a one-time tool over all legacy files that adds
     *   `{ "Temper": { "languageVersion": "1.0-deprecated" } }`
     * - allow opting legacy files out of error checks added to the compiler after they were written
     *   by running a one-time tool over legacy files
     *
     * so that when users start from a blank file, they're opting into the newest, strictest
     * version of the language.
     */
    var appendix: JsonValue? = null
        private set

    override fun canAdvance(): Boolean = when {
        inProgress -> false
        continueConditionReturnedFalse -> false
        !_ok -> false
        else -> {
            val nextStage = Stage.after(_stageCompleted)
            nextStage != null && prerequisitesMetFor(nextStage)
        }
    }

    override fun advance() {
        if (canAdvance()) {
            val stageToPerform = Stage.after(this._stageCompleted)!!
            val logMark = failLog.markBeforeRecoverableFailure()
            failLog.explain(
                MessageTemplate.StartingStage,
                Position(loc, 0, 0),
                listOf(stageToPerform),
            )
            inProgress = true
            considerPrivilegedEnvironmentBindings(this)

            val stageSpecificLogSink: LogSink = object : LogSink {
                override var hasFatal = false
                    private set

                override fun log(
                    level: Log.Level,
                    template: MessageTemplateI,
                    pos: Position,
                    values: List<Any>,
                    fyi: Boolean,
                ) {
                    if (level >= Log.Fatal) { this.hasFatal = true }
                    this@Module.logSink.log(
                        level = level,
                        template = template,
                        pos = pos,
                        values = values,
                        fyi = fyi,
                    )
                }
            }

            fun done(ok: Boolean, stageCallback: () -> Unit) = complete(
                stageToPerform,
                logMark,
                // Check module logSink also because failLog forwards there.
                ok && !stageSpecificLogSink.hasFatal && !logSink.hasFatal,
                stageCallback,
            )

            // Allocate a top-level environment after setup code has had a
            // chance to call addEnvironmentBindings
            if (_topLevelBindings == null && stageToPerform > Stage.Parse) {
                _topLevelBindings = TopLevelBindings(
                    wrapBuiltinEnvironment(
                        builtinEnvironment(
                            ModularNameEnvironment(
                                EmptyEnvironment,
                            ),
                            genre = genre,
                            skipImplicits = loc is ImplicitsCodeLocation ||
                                stableEnvironmentBindings[StagingFlags.skipImportImplicits] == TBoolean.valueTrue,
                        ),
                    ),
                )
            }

            // TODO: If a stage fails with Abort or Panic, then notify the module manager that
            // dependencies will never be satisfied.
            (_topLevelBindings ?: StageExclusion.NotExclusive).whileSynchronized {
                when (stageToPerform) {
                    Stage.Lex -> {
                        val sources = _sources.map { source ->
                            if (source.tree == null && source.tokenSource == null) {
                                val lang = source.languageConfig!!
                                val fetchedContent = source.fetchedContent!!
                                // TODO What on exception?
                                LexStage(lang, source.filePath ?: loc, fetchedContent, stageSpecificLogSink)
                                    .process {
                                        // Now that we've lexed the fetched content, we can release it to be
                                        // garbage collected if we've got a way to re-fetch something approximate
                                        // for error message snippets.
                                        val fetchedContentPostLex =
                                            if (source.filePath != null && source.snapshot != null) {
                                                null
                                            } else {
                                                source.fetchedContent
                                            }
                                        source.copy(tokenSource = it, fetchedContent = fetchedContentPostLex)
                                    }
                            } else {
                                source
                            }
                        }
                        done(true) { _sources = sources }
                    }

                    Stage.Parse -> {
                        var singleAppendix: JsonValue? = null
                        val sources = _sources.map { source ->
                            if (source.tree == null) {
                                // TODO What on exception?
                                ParseStage(source.tokenSource!!, stageSpecificLogSink, this)
                                    .process {
                                            cst: CstInner, root: BlockTree, appendix: JsonValue?,
                                            comments: List<CstComment>,
                                        ->
                                        if (appendix != null) {
                                            // TODO Separate appendix file for dir modules?
                                            check(singleAppendix == null)
                                            singleAppendix = appendix
                                        }
                                        source.copy(
                                            comments = comments,
                                            cst = cst,
                                            tokenSource = null,
                                            tree = root,
                                        )
                                    }
                            } else {
                                source
                            }
                        }
                        val tree = mergeTrees(sources, loc = (loc as? FileRelatedCodeLocation)?.sourceFile)
                        done(true) {
                            appendix = singleAppendix
                            _sources = sources
                            _tree = tree
                        }
                        // Provide snapshot after we're done with the *whole* tree.
                        Debug.Frontend.ParseStage.After.snapshot(configurationKey, AstSnapshotKey, tree)
                    }

                    Stage.Import -> {
                        val okBefore = this._ok
                        ImportStage(
                            this,
                            _tree!!,
                            failLog,
                            stageSpecificLogSink,
                            additionalImplicitImports.toList(),
                        ).process { outputs: StageOutputs ->
                            val result = outputs.result
                            done(result !is Fail) {
                                if (okBefore && result !is Fail) {
                                    // Release individual trees for GC unless we might want to debug them further.
                                    _sources = _sources.map { it.copy(cst = null, tree = null) }
                                }
                                this._tree = outputs.root
                                this.additionalImplicitImports.clear()
                            }
                        }
                    }

                    Stage.DisAmbiguate -> {
                        DisAmbiguateStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                }
                            }
                    }

                    Stage.SyntaxMacro -> {
                        SyntaxMacroStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs ->
                                done(outputs.result !is Fail) {
                                    this._tree = outputs.root
                                }
                            }
                    }

                    Stage.Define -> {
                        DefineStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                }
                            }
                    }

                    Stage.Type -> {
                        TypeStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs, outputName: ResolvedName?, outputType: StaticType? ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                    this._outputName = outputName
                                    this._outputType = outputType
                                }
                            }
                    }

                    Stage.FunctionMacro -> {
                        FunctionMacroStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                }
                            }
                    }

                    Stage.Export -> {
                        ExportStage(this, _tree!!, failLog, stageSpecificLogSink)
                            .process { outputs: StageOutputs ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                    this._exports = outputs.exports
                                    this._declaredTypeShapes = outputs.declaredTypeShapes
                                }
                            }
                    }

                    Stage.Query -> {
                        // TODO: implement me
                        done(true) {}
                    }

                    Stage.GenerateCode -> {
                        GenerateCodeStage(this, _tree!!, failLog, logSink)
                            .process { outputs: StageOutputs ->
                                val result = outputs.result
                                done(result !is Fail) {
                                    this._tree = outputs.root
                                    this._exports = outputs.exports
                                    this._declaredTypeShapes = outputs.declaredTypeShapes
                                }
                            }
                    }

                    Stage.Run -> {
                        val stageResults = RuntimeEmulationStage(
                            this@Module,
                            _topLevelBindings!!,
                            _outputName,
                            _tree!!,
                            failLog,
                            logSink,
                            continueCondition,
                            features,
                            connecteds,
                            promises = promises,
                        ).process()
                        val runResult = stageResults.result
                        val gotValueResult = runResult is Value<*>
                        if (!gotValueResult) {
                            logSink.log(
                                Log.Warn,
                                MessageTemplate.NonValueResultFromRunStage,
                                Position(loc, 0, 0),
                                listOf(runResult),
                            )
                        }
                        if (runResult is Fail) {
                            runResult.info?.logTo(logSink)
                        }
                        done(ok = gotValueResult) {
                            this._runResult = runResult
                            this._exports = stageResults.exports
                        }
                    }
                }
            }
        }
    }

    override val finished: Boolean
        get() {
            val stage = _stageCompleted
            return stage != null && stage >= Stage.GenerateCode
        }

    /**
     * May be called by special environments to do additional work between specific stages.
     */
    fun hookTree(action: (BlockTree) -> BlockTree) {
        val tree = _tree ?: return
        _tree = action(tree)
    }

    private fun prerequisitesMetFor(stage: Stage) = when (stage) {
        Stage.Lex -> _sources.isNotEmpty() && _sources.all { source ->
            (source.fetchedContent != null && source.languageConfig != null) ||
                source.tokenSource != null || source.tree != null
        }
        Stage.Parse -> _sources.isNotEmpty() && _sources.all { it.tokenSource != null || it.tree != null }
        Stage.Import,
        Stage.DisAmbiguate,
        Stage.SyntaxMacro,
        ->
            _tree != null
        Stage.Define,
        Stage.Type,
        Stage.FunctionMacro,
        Stage.Export,
        Stage.Query,
        Stage.GenerateCode,
        ->
            _tree != null
        Stage.Run -> mayRun && _tree != null
    }

    private fun complete(stage: Stage, failMark: FailLog.FailMark, ok: Boolean, done: () -> Unit) {
        require(Stage.after(this._stageCompleted) == stage && inProgress)
        this.inProgress = false
        if (ok) {
            failMark.rollback()
        } else {
            this._ok = false
            failLog.explain(
                MessageTemplate.StagePromotionFailed,
                Position(loc, 0, 0),
                listOf(stage),
                pinned = true,
            )
        }
        done()
        this._stageCompleted = stage
    }

    /** Called by fetcher to provide pre-processed tokens. */
    fun deliverContent(
        tokenSource: TokenSource,
        filePositions: FilePositions?,
    ) {
        deliverContent(
            ModuleSource(
                filePath = tokenSource.codeLocation as? FilePath,
                filePositions = filePositions,
                tokenSource = tokenSource,
            ),
        )
    }

    /** Used by test harnesses to deliver a hand-crafted ast tree to test corner conditions. */
    fun deliverContent(ast: Tree) =
        deliverContent(listOf(ModuleSource(tree = BlockTree.maybeWrap(ast))))

    fun deliverContent(source: ModuleSource) = deliverContent(listOf(source))

    fun deliverContent(sources: Iterable<ModuleSource>) {
        check(stageCompleted == null && _tree == null)

        val sourceList = _sources.toMutableList()
        val newFilePositions = _filePositions.toMutableMap()

        for (origSource in sources) {
            var source = origSource
            val tree = source.tree
            val filePath = source.filePath
            val fetchedContent = source.fetchedContent
            if (tree != null) {
                require(tree.document.context == this)
            }
            if (filePath != null && fetchedContent != null && source.filePositions == null) {
                source = source.copy(filePositions = FilePositions.fromSource(filePath, fetchedContent))
            }
            val filePositions = source.filePositions
            if (filePath != null && filePath !in newFilePositions && filePositions != null) {
                newFilePositions[filePath] = filePositions
            }
            sourceList.add(source)
        }
        _sources = sourceList.toList()
        _filePositions = newFilePositions.toMap()
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("stageCompleted") { value(_stageCompleted) }
        key("ok", isDefault = _ok) { value(_ok) }
        key("parse", Hints.u) {
            arr {
                for (source in _sources) {
                    obj {
                        // TODO File path or additional?
                        key("cst", Hints.u) { value(source.cst) }
                    }
                }
            }
        }
        val ast = _tree
        key("tree", Hints.u, isDefault = ast == null) {
            if (ast != null) {
                destructureModuleBody(this, ast, PseudoCodeDetail.default)
            }
        }
        key("result", Hints.n, isDefault = _runResult == null) {
            value(_runResult)
        }
    }

    override fun toString() = "(Module $loc)"

    inner class ModuleLocationContext(
        private val sharedLocationContext: SharedLocationContext?,
    ) : SharedLocationContext {
        override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? {
            // First, see if this loc is well-known or the information is locally available.
            val known = when (loc) {
                // TODO: Can we just use the shared location logic in ModuleAdvancer?
                this@Module.loc -> {
                    when (v) {
                        CodeLocationKey.SourceCodeKey ->
                            _sources.soleElementOrNull?.fetchedContent?.let { v.cast(it) }
                        CodeLocationKey.FilePositionsKey ->
                            (loc as? FileRelatedCodeLocation)?.sourceFile?.let { sourceFile ->
                                filePositions[sourceFile]?.let { v.cast(it) }
                            }
                        else -> null
                    }
                }
                is FilePath -> {
                    when (v) {
                        CodeLocationKey.FilePositionsKey -> _filePositions[loc]?.let { v.cast(it) }
                        else -> null
                    }
                }
                ImplicitsCodeLocation -> {
                    when (v) {
                        CodeLocationKey.SourceCodeKey -> v.cast(ImplicitsModule.code)
                        CodeLocationKey.FilePositionsKey -> v.cast(implicitsFilePositions)
                        else -> null
                    }
                }
                else -> null
            }
            return known ?: sharedLocationContext?.get(loc, v)
        }
    }

    companion object {
        private val implicitsFilePositions get() = ImplicitsModule.implicitsFilePositions
    }

    /** Whether this module represents Temper implicits/builtins, including for source editing purposes. */
    val isEffectivelyImplicits = when (loc) {
        // Recognize fake implicits, such as when editing them as ordinary files.
        is ModuleName ->
            !loc.isPreface &&
                sharedLocationContext?.get(loc, LibraryNameLocationKey) ==
                DashedIdentifier.temperImplicitsLibraryIdentifier
        // Recognize real implicits, as provided through specialized machinery.
        is ImplicitsCodeLocation -> true
    }

    /** Whether this module is from the Temper standard library, including for source editing purposes. */
    val isEffectivelyStd get() = loc.isEffectivelyStd(sharedLocationContext)

    override var dependencyCategory: DependencyCategory
        get() = _dependencyCategory
        set(value) = when (_dependencyCategory) {
            DependencyCategory.Production -> _dependencyCategory = value
            DependencyCategory.Test -> require(value == _dependencyCategory) { "can't revert to production from test" }
        }

    private var _dependencyCategory = DependencyCategory.Production
}

fun ModuleLocation.isEffectivelyStd(sharedLocationContext: SharedLocationContext?): Boolean {
    if (this is ModuleName) {
        if (this.isPreface) { return false }
        val libraryName = sharedLocationContext?.get(this, LibraryNameLocationKey)
        if (libraryName == DashedIdentifier.temperStandardLibraryIdentifier) {
            return true
        }
    }
    if (this is FileRelatedCodeLocation) {
        // Recognize fake std, such as when editing them as ordinary files.
        val sourceFile = this.sourceFile
        if (sourceFile.segments.firstOrNull()?.fullName == STANDARD_LIBRARY_NAME) {
            return true
        }
    }
    return false
}

internal fun destructureModuleBody(
    sink: StructureSink,
    ast: Tree,
    pseudoCodeDetail: PseudoCodeDetail,
) = sink.obj {
    key("body", Hints.n) {
        obj {
            destructureTreeMultipleRepresentations(this, ast, pseudoCodeDetail)
        }
    }
}

internal fun destructureTreeMultipleRepresentations(
    sink: PropertySink,
    ast: Tree,
    pseudoCodeDetail: PseudoCodeDetail,
) {
    sink.key("code", Hints.s) {
        val code = ast.toPseudoCode(
            singleLine = false,
            detail = pseudoCodeDetail,
        )
        value(code)
    }
    sink.key("tree", Hints.s) { value(ast) }
}

fun Iterable<Module>.mergedNamingContext(mergedLoc: ModuleLocation): NamingContext {
    var maxUid = 0
    for (m in this) {
        maxUid = max(maxUid, m.namingContext.peekUnusedNameUid())
    }
    return object : NamingContext(AtomicCounter(maxUid.toLong())) {
        override val loc: ModuleLocation = mergedLoc
    }
}
