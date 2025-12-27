package lang.temper.cli.repl

import lang.temper.ast.TreeVisit
import lang.temper.builtin.OPTIONAL_PRINT_FEATURE_KEY
import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Flushable
import lang.temper.common.Log
import lang.temper.common.ignore
import lang.temper.common.temperEscaper
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TextOutputTokenSink
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.fs.Directories
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.importExport.Export
import lang.temper.interp.importExport.ImportMacro
import lang.temper.interp.importExport.createLocalBindingsForImport
import lang.temper.lexer.Lexer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.Debug
import lang.temper.log.FilePositions
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.SharedLocationContext
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.tooling.buildrun.PrintOverrideFn
import lang.temper.value.Abort
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.ErrorFn
import lang.temper.value.Fail
import lang.temper.value.NameLeaf
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Promises
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TProblem
import lang.temper.value.Value
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.implicitSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.optionalImportSymbol
import lang.temper.value.toLispy
import lang.temper.value.valueContained
import lang.temper.value.void
import java.util.Collections
import java.util.concurrent.ExecutorService

private val spaceToken = OutputToken(" ", OutputTokenType.Space)
private val dollarToken = OutputToken("$", OutputTokenType.Punctuation)

/**
 * A REPL (Read Eval Print Loop) that allows a user to interactively enter chunks of Temper input.
 *
 * It groups lines until they form a set of complete tokens that have a closing bracket for every
 * open bracket, and processes each such group as a module run through the runtime emulation stage
 * to produce a result which is logged.
 *
 * Each module implicitly imports all the exports of each previous module, and each module
 * implicitly exports its explicit, top-level declarations.
 */
internal class Repl(
    val console: Console,
    val directories: Directories,
    val executorService: ExecutorService,
    private val config: ReplConfig = defaultConfig,
    private val overrideBindings: Map<TemperName, Value<*>>? = null,
) : AutoCloseable, Flushable {
    private var allExports = mutableMapOf<ParsedName, Export>()
    var commandCount = INITIAL_COMMAND_COUNT
        private set
    private val openBracketStack = mutableListOf<OutputToken>()
    val openBracketDepth get() = openBracketStack.size
    private val pendingInput = StringBuilder()
    private val consoleTokensOut = TextOutputTokenSink(console.textOutput)
    private val sources = mutableMapOf<ReplChunkIndex, String>()
    private val positionInfos = mutableMapOf<CodeLocation, FilePositions>()

    // Share promise resolution across chunks so that one chunk can create a
    // promise builder and a later one can resolve&|await it.
    val promises = Promises()

    // Only mention the help topic for listing unresolved promises once.
    private var deliveredUnresolvedWarning = false

    val allExportedBaseNames: Set<ParsedName>
        get() = Collections.unmodifiableSet(allExports.keys)

    private val sharedLocationContext = object : SharedLocationContext {
        @Suppress("UNCHECKED_CAST") // Sound when actual type agrees with keys' <T>s
        override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? {
            return when (v) {
                CodeLocationKey.SourceCodeKey -> sourceFromLoc(loc) as? T
                CodeLocationKey.FilePositionsKey -> run positions@{
                    // Even just removing this whole branch and giving null instead still passes repl tests,
                    // but it seems worth giving a real answer anyway in case it matters for some cases.
                    positionInfos.getOrPut(loc) {
                        FilePositions.fromSource(loc, sourceFromLoc(loc) ?: return@positions null)
                    }
                } as T?
                else -> null
            }
        }
    }

    private fun sourceFromLoc(loc: CodeLocation) = ReplChunkIndex.from(loc)?.let { sources[it] }

    // Since we use the repl more for diagnosis than just running code, use raw names for now.
    internal val logSink: LogSink = ReplLogSink(this, sharedLocationContext)
    internal val snapshotStore = SnapshotStore(
        directories.userCacheDir,
        storeTtyLikeSnapshots = console.textOutput.isTtyLike,
    )
    private var lastModule: Module? = null

    /** The last location referenced in a diagnostic macro, or the most recent. */
    internal var lastLocReferenced: ReplChunkIndex? = null
    internal val extraBindings: Map<TemperName, Value<*>> = buildMap {
        val replBuiltins = listOf(
            ReplHelpFn(this@Repl),
            ReplDescribeFn(this@Repl),
            ReplTranslateFn(this@Repl),
        )
        for (replBuiltin in replBuiltins) {
            this[BuiltinName(replBuiltin.name)] = Value(replBuiltin)
        }
        this[StagingFlags.moduleResultNeeded] = TBoolean.valueTrue
        this[StagingFlags.allowTopLevelAwait] = TBoolean.valueTrue
    }

    private val replModules = mutableMapOf<ReplChunkIndex, Module>()
    internal val externalModules = ModulesExternalToRepl(console, logSink, executorService)

    /**
     * When a REPL command `import`s something we add it here so that we can resolve it
     * using [externalModules] after the module finishes its current stage.
     */
    private val pendingImports = mutableListOf<PendingImportForRepl>()

    /**
     * The range of indices for [ReplChunkIndex] produced by this Repl so far.
     */
    val validCommandCounts: IntRange
        get() = INITIAL_COMMAND_COUNT until commandCount

    init {
        // Load early so that user typing is not interrupted by a pause to stage the
        // implicits module.
        ignore(ImplicitsModule.module)
    }

    override fun close() {
        try {
            flush()
        } finally {
            snapshotStore.close()
        }
    }

    override fun flush() {
        processLineRobustly("", expectMore = false)
    }

    /**
     * The prompt.  When rendered, this may be a generic prompt (`$`), or may indicate what needs to
     * be closed to complete a multi-line command.
     */
    private val prompt: TokenSerializable = Prompt()
    private inner class Prompt : TokenSerializable {
        override fun renderTo(tokenSink: TokenSink) {
            if (config.prompt == ReplPrompt.Enable) {
                if (openBracketStack.isEmpty()) {
                    tokenSink.emit(dollarToken)
                } else {
                    // Indent to match the initial "$".
                    tokenSink.emit(spaceToken)
                }
                tokenSink.emit(spaceToken)
            }
        }
    }

    fun promptAsString(isTtyLike: Boolean = AppendingTextOutput.DEFAULT_IS_TTY_LIKE): String =
        toStringViaBuilder { sb ->
            if (config.prompt == ReplPrompt.Enable) {
                val textOutput = AppendingTextOutput(sb, isTtyLike)
                val tokensOut = TextOutputTokenSink(textOutput)
                this.prompt.renderTo(tokensOut)
                tokensOut.finish()
            }
        }

    /** Process a line and write out the content, trapping TODO()s. */
    fun processLineRobustly(inputChunk: String) = processLineRobustly(inputChunk, expectMore = true)

    /** Process a line and write out the content. */
    fun processLine(inputChunk: String) = processLine(inputChunk, expectMore = true)

    /**
     * @param expectMore whether more input might be forthcoming.
     */
    private fun processLineRobustly(inputChunk: String, expectMore: Boolean) {
        // TODO Maybe handle more error types here in the future.
        try {
            processLine(inputChunk, expectMore = expectMore)
        } catch (e: NotImplementedError) {
            // First line of "to do" call should be enough to track it down.
            console.errorDense(e)
        }
    }

    private fun processLine(inputChunk: String, expectMore: Boolean) {
        logSink.resetUsage()
        // See if this chunk combined with the last forms a whole command
        if (pendingInput.isNotEmpty()) {
            pendingInput.append('\n') // readLine strips the newline from the end.
        }
        pendingInput.append(inputChunk)

        val separator = config.separator
        if (pendingInput.isBlank() || (expectMore && separator == ReplSeparator.Eof)) {
            // There's no content to process, or we must wait for more because
            // we only process one chunk at end of file.
            return
        }

        val chunkIndex = ReplChunkIndex(commandCount)
        if (expectMore) { // Look for reasons to wait.
            // TODO: Maybe store context at end of last valid token with pending so that we can
            // restart lexing instead of being O(n**2) when merging many lines together one
            // after another.
            val lexer = Lexer(chunkIndex.filePath, LogSink.devNull, pendingInput, ignoreTrailingSynthetics = true)
            openBracketStack.clear()

            // If the line is only a comment token, look for more content.
            // This simplifies writing documentation comments for declarations
            // like the below since the comment is part of the same parse tree
            // as the declaration it documents.
            //
            //    /** documentation for f */
            //    let f() { ... }
            var isOnlyComments = true

            var lastNonIgnorable: TemperToken? = null
            for (token in lexer) {
                val tokenText = token.tokenText
                if (!token.tokenType.ignorable) {
                    isOnlyComments = false
                    lastNonIgnorable = token
                }
                if (token.mayBracket) {
                    when {
                        (
                            token.tokenType == TokenType.Punctuation &&
                                ('(' in tokenText || '[' in tokenText || '{' in tokenText)
                            ) ||
                            token.tokenType == TokenType.LeftDelimiter ->

                            openBracketStack.add(
                                OutputToken(
                                    tokenText,
                                    OutputTokenType.Punctuation,
                                    TokenAssociation.Bracket,
                                ),
                            )
                        (
                            token.tokenType == TokenType.Punctuation &&
                                (')' in tokenText || ']' in tokenText || '}' in tokenText)
                            ) ||
                            token.tokenType == TokenType.RightDelimiter ->

                            openBracketStack.removeLastOrNull()
                    }
                }
            }
            if (openBracketStack.isNotEmpty() || isOnlyComments) {
                // Allow the user to enter more content to complete the command.
                return
            }
            @Suppress("KotlinConstantConditions") // Eof cannot reach here. It's handled above.
            when (separator) {
                ReplSeparator.Newline -> {} // Cool
                ReplSeparator.SemiSemi ->
                    if (lastNonIgnorable?.tokenType == TokenType.Punctuation &&
                        lastNonIgnorable.tokenText == ";;"
                    ) {
                        // Cool.  But we don't want to parse that token.
                        pendingInput.replace(lastNonIgnorable.pos.left, lastNonIgnorable.pos.right, "  ")
                    } else {
                        return
                    }
                ReplSeparator.Eof -> {} // Handled above
            }
        }

        val commandText = "$pendingInput"
        pendingInput.clear()

        sources[chunkIndex] = commandText // Store command so we can generate snippets
        commandCount += 1 // Next loc will be different

        // Execute the command
        var stepsThousands = MAX_STEPS_THOUSANDS
        val continueCondition = {
            stepsThousands -= 1
            stepsThousands > 0
        }
        // Set up the module
        val module = Module(
            logSink,
            chunkIndex.moduleName,
            console,
            continueCondition,
            mayRun = true,
            sharedLocationContext = sharedLocationContext,
            // For now, infer general verbosity from log level. TODO Thread detailed verbosity info to this point.
            allowDuplicateLogPositions = console.level <= Log.Fine,
        )
        replModules[chunkIndex] = module
        module.addEnvironmentBindings(extraBindings)
        overrideBindings?.let { module.addEnvironmentBindings(it) }
        module.deliverContent(
            ModuleSource(
                filePath = chunkIndex.filePath,
                fetchedContent = commandText,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        // TODO: could we set module.outer and chain implicit exports that way
        module.addImplicitImports(allExports.values)
        module.useFeatures(
            mapOf(
                // Prints should not go through the log sink.
                OPTIONAL_PRINT_FEATURE_KEY to
                    Value(PrintOverrideFn { console.log(it) }),
                // The REPL should be able to import from std, and
                // (TODO) other libraries flagged from the command line.
                ImportMacro.IMPORT_PENDING_FEATURE_KEY to
                    Value(ReplImportFeatureMacro(this)),
            ),
        )
        module.promises = promises
        // Make sure that module snapshots go to the appropriate place
        val snapshottingConsole = Console(
            console.textOutput,
            logLevel = Log.Warn,
            snapshotter = snapshotStore.snapshotterFor(chunkIndex),
        )
        Debug.Frontend.configure(module, snapshottingConsole)
        // Stage the module
        while (module.canAdvance()) {
            try {
                module.advance()
            } catch (panic: Panic) {
                ignore(panic)
                console.error("Interpretation ended due to runtime panic")
                if (console.logs(Log.Fine)) {
                    console.error(panic)
                }
            } catch (abort: Abort) {
                ignore(abort)
                console.error("Interpretation aborted")
                if (console.logs(Log.Fine)) {
                    console.error(abort)
                }
            }
            processImports(module)
            module.stageCompleted?.let { stage ->
                if (stage in config.dumpStages) {
                    module.treeForDebug?.let { t ->
                        console.group(stage.name) {
                            console.textOutput.emitLine(t.toLispy(multiline = true))
                        }
                    }
                }
            }
            when (module.stageCompleted) {
                Stage.DisAmbiguate -> {
                    // Implicitly export top level declarations so that they are visible to later
                    // command texts
                    module.hookTree { root -> exportTopLevels(module, root) }
                    // Wrap in a block after we export tops and before we'd reorder them.
                    module.hookTree { BlockTree.wrap(it) }
                }
                Stage.SyntaxMacro -> {
                    // Top reorder done, so re-expose the original tops.
                    // TODO Can syntax macros examine if at top level?
                    module.hookTree { root ->
                        // We might have inserted things before the original block, so find it.
                        val blockIndex = root.children.indexOfFirst { it is BlockTree }
                        val block = freeTarget(root.edge(blockIndex)) as BlockTree
                        // And put the extras into it.
                        val extras = root.children.subList(0, blockIndex).map { freeTree(it) }
                        block.insert(0) { extras.forEach { Replant(it) } }
                        block
                    }
                }
                else -> Unit
            }
        }
        val result = if (module.stageCompleted == Stage.Run) {
            logFailMessage(module.runResult, module.logSink)
            module.runResult
        } else {
            null
        } ?: Fail

        module.treeForDebug?.let {
            val problems = mutableListOf<LogEntry>()
            TreeVisit.startingAt(it)
                .forEachContinuing { t ->
                    if (t is CallTree && t.childOrNull(0)?.functionContained == ErrorFn) {
                        t.children.mapNotNullTo(problems) { child ->
                            child.valueContained(TProblem)
                        }
                    }
                }
                .visitPreOrder()
            problems.sortBy { problem -> problem.pos.left }
            problems.forEach { problem ->
                problem.logTo(module.logSink)
            }
        }

        val newExports: List<Export>? = module.exports

        val numUnresolvedPromises = promises.numUnresolved
        if (result !is Value<*> && numUnresolvedPromises != 0) {
            if (!deliveredUnresolvedWarning && console.logs(Log.Info)) {
                console.textOutput.emitLineChunk("To see more info about unresolved promises, enter `")
                consoleTokensOut.word(ReplHelpFn.NAME)
                consoleTokensOut.emit(OutToks.leftParen)
                consoleTokensOut.quoted(temperEscaper.escape(UnresolvedPromisesHelp.NAME))
                consoleTokensOut.emit(OutToks.rightParen)
                console.textOutput.emitLineChunk("`.")
                console.textOutput.endLine()
                deliveredUnresolvedWarning = true
            }
            console.log(
                when (numUnresolvedPromises) {
                    1 -> "1 promise is unresolved."
                    else -> "$numUnresolvedPromises promises are unresolved."
                },
            )
        }
        chunkIndex.renderTo(consoleTokensOut)
        consoleTokensOut.emit(OutToks.colon)
        consoleTokensOut.emit(spaceToken)
        result.renderTo(consoleTokensOut)
        consoleTokensOut.endLine()

        lastModule = module
        newExports?.let {
            for (export in it) {
                if (export.value != null) {
                    allExports[export.name.baseName] = export
                }
            }
        }
    }

    /**
     * Process `import`s added by [ReplImportFeatureMacro] during the
     * last stage advancement of a module derived from a REPL chunk.
     */
    private fun processImports(module: Module) {
        if (pendingImports.isEmpty()) { return }

        val toProcess = pendingImports.toList()
        pendingImports.clear()

        for (pendingImport in toProcess) {
            val stayLeaf = pendingImport.stayLeaf
            var importSucceeded = false

            val exporter = externalModules[pendingImport]
            if (exporter != null) {
                if (stayLeaf != null) {
                    val declTree = stayLeaf.incoming?.source as? DeclTree
                    if (declTree != null) {
                        createLocalBindingsForImport(
                            declTree = declTree,
                            importer = module,
                            exporter = exporter,
                            logSink = logSink,
                            specifier = pendingImport.resolvedModuleSpecifier.text,
                        )
                        importSucceeded = true
                    }
                } else {
                    importSucceeded = true
                }
            }
            if (!importSucceeded) {
                val error = LogEntry(
                    Log.Error,
                    MessageTemplate.ImportFailed,
                    pendingImport.specifierPos,
                    listOf(pendingImport.resolvedModuleSpecifier.text),
                )
                val errorLocation = stayLeaf?.incoming?.source?.incoming
                if (errorLocation != null) {
                    convertToErrorNode(errorLocation, error)
                } else {
                    error.logTo(module.logSink)
                }
            }
        }
    }

    fun reset() {
        openBracketStack.clear()
        pendingInput.clear()
    }

    private fun logFailMessage(result: PartialResult?, to: LogSink) {
        (result as? Fail)?.info?.logTo(to)
    }

    internal fun appendPendingLines(out: StringBuilder) {
        if (pendingInput.isNotEmpty()) {
            out.append(pendingInput)
            out.append('\n')
        }
    }

    fun appendPending(out: StringBuilder) {
        val pendingInput = pendingInput
        if (pendingInput.isNotEmpty()) {
            out.append(pendingInput)
            out.append('\n')
        }
    }

    fun getModule(loc: ReplChunkIndex): Module? = replModules[loc]

    internal fun addPendingImport(pending: PendingImportForRepl) {
        pendingImports.add(pending)
    }
}

internal const val INITIAL_COMMAND_COUNT = 0
internal const val REPL_LOC_PREFIX = "interactive#"
internal val REPL_LOC_SYMBOL = Symbol(REPL_LOC_PREFIX.replace("#", ""))

private const val MAX_STEPS_THOUSANDS = 100

private fun exportTopLevels(module: Module, root: BlockTree): BlockTree {
    fun maybeExport(edge: TEdge) {
        val e = lookThroughDecorations(edge)
        val t = e.target

        if (t is DeclTree) {
            val nameEdge = t.edgeOrNull(0)
            val nameLeaf = nameEdge?.target as? NameLeaf
            val name = nameLeaf?.content
            if (
                name is ParsedName &&
                // Do not re-export implicit imports
                t.parts?.metadataSymbolMap?.contains(implicitSymbol) == false
            ) {
                nameEdge.replace {
                    Ln(ExportedName(module.namingContext, name))
                }
                // This metadata helps us avoid over dependency when translating
                // REPL chunks.  See ImportStage for details.
                val leftEdge = t.pos.leftEdge
                t.insert {
                    V(leftEdge, optionalImportSymbol)
                    V(leftEdge, void)
                }
            }
        } else if (
            t is CallTree && t.size != 0 &&
            (t.child(0) as? RightNameLeaf)?.content?.builtinKey == "let"
        ) {
            val leftEdge = t.pos.leftEdge
            // Export `let` calls which are used to define functions.
            e.replace {
                Call(t.pos) { // See note about optionalImportSymbol above
                    V(leftEdge, Value(optionalImportDecorator))
                    Call(t.pos) {
                        Rn(leftEdge, BuiltinName("@export"))
                        Replant(freeTree(t))
                    }
                }
            }
        }
    }
    for (edge in root.edges) {
        maybeExport(edge)
    }
    return root
}

private val optionalImportDecorator = MetadataDecorator(
    optionalImportSymbol,
) {
    void
}
