package lang.temper.be.tmpl

import lang.temper.ast.TreeVisit
import lang.temper.ast.deepCopy
import lang.temper.be.Backend
import lang.temper.be.MetadataDependencyResolver
import lang.temper.be.MetadataKeyFactory
import lang.temper.be.QNameMapping
import lang.temper.be.TargetLanguageTypeName
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.RttiCheckFunction
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.builtin.isRemCall
import lang.temper.builtin.isSetPropertyCall
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.buildListMultimap
import lang.temper.common.console
import lang.temper.common.isNotEmpty
import lang.temper.common.putMultiList
import lang.temper.common.subListToEnd
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.AdaptGeneratorFn
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleNamingContext
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.frontend.mergedNamingContext
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.LongLivedUserFunction
import lang.temper.interp.New
import lang.temper.interp.emptyValue
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.library.definingName
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.SharedLocationContext
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.InternalModularName
import lang.temper.name.ModularName
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.BindMemberAccessor
import lang.temper.type.DotHelper
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.SetMemberAccessor
import lang.temper.type.StaticType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.booleanType2
import lang.temper.type.WellKnownTypes.bubbleType2
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleActualsToNew
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackMapOldStyleToNewAllowNever
import lang.temper.type2.hackMapOldStyleToNewOrNull
import lang.temper.type2.hackTryStaticTypeToSig
import lang.temper.type2.invalidSig
import lang.temper.type2.isVoidLike
import lang.temper.type2.mapType
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockTree
import lang.temper.value.BreakOrContinue
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.CoverFunction
import lang.temper.value.DeclTree
import lang.temper.value.DependencyCategory
import lang.temper.value.Document
import lang.temper.value.ErrorFn
import lang.temper.value.EscTree
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.JumpSpecifier
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroValue
import lang.temper.value.MetadataMap
import lang.temper.value.MetadataValueMapHelpers.get
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PanicFn
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TEdge
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.connectedSymbol
import lang.temper.value.consoleParsedName
import lang.temper.value.factorySignatureFromConstructorSignature
import lang.temper.value.fnParsedName
import lang.temper.value.fnSymbol
import lang.temper.value.freeTree
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.impliedThisSymbol
import lang.temper.value.importedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.isBubbleCall
import lang.temper.value.isNewCall
import lang.temper.value.isYieldCall
import lang.temper.value.parameterNameSymbolsListSymbol
import lang.temper.value.reachSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.resolutionSymbol
import lang.temper.value.returnedFromSymbol
import lang.temper.value.ssaSymbol
import lang.temper.value.staticSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.staySymbol
import lang.temper.value.symbolContained
import lang.temper.value.testSymbol
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typeFromSignature
import lang.temper.value.typeMemberMetadataSymbols
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.visibilitySymbol
import lang.temper.value.withinDocFoldSymbol
import lang.temper.value.wordSymbol

private const val DEBUG = false
private inline fun debug(message: () -> Any?) {
    if (DEBUG) {
        val o = message()
        if (o != Unit) {
            console.log("$o")
        }
    }
}

/**
 * Translates the Lispy Temper AST to the [layered form][TmpL].
 */
class TmpLTranslator internal constructor(
    internal val logSink: LogSink,
    internal val document: Document,
    internal val pool: ConstantPool,
    internal val topLevels: MutableList<TmpL.TopLevel>,
    internal val libraryConfigurations: LibraryConfigurations,
    internal val sharedLocationContext: SharedLocationContext?,
    internal val moduleIndex: Int,
    internal val dependencyResolver: MetadataDependencyResolver<*>,
    mergedNamingContext: NamingContext,
    internal val supportNetwork: SupportNetwork,
) {
    companion object {
        fun translateModules(
            logSink: LogSink,
            modules: List<Module>,
            supportNetwork: SupportNetwork,
            libraryConfigurations: LibraryConfigurations,
            dependencyResolver: MetadataDependencyResolver<*>,
            /** Produces a path relative to the output root for the [current library][libraryConfigurations]. */
            tentativeOutputPathFor: (Module) -> FilePath,
            withTentative: (TentativeTmpL) -> Unit = {},
        ): TmpL.ModuleSet {
            val stubLocationForLibrary = run {
                val libraryRoot = libraryConfigurations.currentLibraryConfiguration.libraryRoot
                ModuleName(
                    libraryRoot,
                    libraryRoot.segments.size,
                    isPreface = false,
                )
            }
            val mergedNamingContext = modules.mergedNamingContext(stubLocationForLibrary)
            val typeInferencesForName = mutableMapOf<ResolvedName, TypeInferences>()
            val stayToName = mutableMapOf<StayLeaf, ResolvedName>()
            val declarationMetadataForName = buildMap {
                modules.forEach { module ->
                    module.generatedCode?.let { root ->
                        TreeVisit.startingAt(root)
                            .forEachContinuing node@{ t ->
                                when (t) {
                                    is CallTree -> {
                                        // When functions are out-of-order and seen as Value leaves, they need preknown.
                                        // In a call tree, left name means assignment.
                                        val name = (t.childOrNull(1) as? LeftNameLeaf)?.content ?: return@node
                                        val fn = (t.childOrNull(2) as? FunTree) ?: return@node
                                        val metadata = fn.parts?.metadataSymbolMap ?: return@node
                                        val stay = metadata[staySymbol]?.target as? StayLeaf ?: return@node
                                        stayToName[stay] = (name as? ResolvedName) ?: return@node
                                    }
                                    is DeclTree -> {
                                        val parts = t.parts
                                        val name = parts?.name?.content as? ResolvedName ?: return@node
                                        this[name] = parts.metadataSymbolMap
                                            .mapNotNull { (symbol, tEdge) ->
                                                val value = tEdge.target.valueContained
                                                value?.let { symbol to it }
                                            }
                                            .toMap()
                                        val typeInferences = parts.name.typeInferences
                                        if (typeInferences != null) {
                                            typeInferencesForName[name] = typeInferences
                                        }
                                    }
                                    else -> {}
                                }
                            }
                            .visitPreOrder()
                    }
                }
            }

            val sharedNameTables = ConstantPool.SharedNameTables(
                declarationMetadataForName = declarationMetadataForName,
                typeInferencesForName = typeInferencesForName,
                stayToName = stayToName,
            )

            val docModuleLocations = modules.mapNotNullTo(mutableSetOf()) {
                if (it.genre == Genre.Documentation) {
                    it.loc
                } else {
                    null
                }
            }
            val genre = if (docModuleLocations.isNotEmpty()) {
                Genre.Documentation
            } else {
                Genre.Library
            }

            // We do a bit of post-processing on support code references.
            // So we delay the final module making step after looking at all modules' top-levels.
            val nascentModules = mutableListOf<NascentModule>()
            for ((moduleIndex, module) in modules.withIndex()) {
                val root = module.generatedCode
                if (root == null) {
                    logSink.log(
                        level = Log.Fatal,
                        template = MessageTemplate.NotGeneratingCodeFor,
                        pos = Position(module.loc, 0, 0),
                        values = listOf(module.loc),
                    )
                    continue
                }
                val topLevels = mutableListOf<TmpL.TopLevel>()
                val constantPool = ConstantPool(module.loc, sharedNameTables, topLevels)
                val translator = TmpLTranslator(
                    logSink,
                    root.document,
                    constantPool,
                    topLevels,
                    libraryConfigurations,
                    module.sharedLocationContext,
                    moduleIndex,
                    dependencyResolver,
                    mergedNamingContext,
                    supportNetwork,
                )
                constantPool.translator = translator
                val nascentModule = translator.translate(
                    module.loc as ModuleName, // Do not translate implicits
                    module.dependencyCategory,
                    root,
                    module.outputName,
                    module.outputType?.let { hackMapOldStyleToNew(it) },
                    tentativeOutputPathFor(module),
                )
                withTentative(TentativeTmpL(translator, nascentModule))
                nascentModules.add(nascentModule)
            }

            // Remove top level declarations that are unused.
            // - SupportCodeDeclarations are unused if they declare InlineSupportCode and are only
            //   ever used as the callee in a CallExpression.
            // - InternalModularNames are unused if they are \connected references to InlineSupportCode
            //   that are similarly only ever used as the callee in a CallExpression.
            val inlineSupportCodeUsedInNonCalleePosition = run {
                val used = mutableSetOf<InlineSupportCode<*, *>>()
                fun findUsed(t: TmpL.Tree) {
                    if (t is TmpL.SupportCodeDeclaration) { return }
                    if (t is TmpL.SupportCodeWrapper && t !is TmpL.InlineSupportCodeWrapper) {
                        (t.supportCode as? InlineSupportCode<*, *>)?.let { used.add(it) }
                    }
                    for (i in 0 until t.childCount) {
                        findUsed(t.child(i))
                    }
                }
                nascentModules.forEach { nascentModule ->
                    nascentModule.topLevels.forEach { findUsed(it) }
                }
                used.toSet()
            }
            nascentModules.forEach { nascentModule ->
                val topLevels = nascentModule.topLevels
                topLevels.removeAll shouldRemove@{
                    if (it is TmpL.SupportCodeDeclaration) {
                        val supportCode = it.init.supportCode
                        if (
                            supportCode is InlineSupportCode<*, *> &&
                            supportCode !in inlineSupportCodeUsedInNonCalleePosition
                        ) {
                            return@shouldRemove true
                        }
                    }
                    if (it is TmpL.ModuleLevelDeclaration) {
                        val name = it.name.name
                        if (name is InternalModularName) {
                            val metadata = sharedNameTables.declarationMetadataForName[name]
                            val connectedKey = TString.unpackOrNull(metadata?.get(connectedSymbol))
                            if (connectedKey != null) {
                                val supportCode =
                                    supportNetwork.translateConnectedReference(it.pos, connectedKey, genre)
                                if (
                                    supportCode is InlineSupportCode<*, *> &&
                                    supportCode !in inlineSupportCodeUsedInNonCalleePosition
                                ) {
                                    return@shouldRemove true
                                }
                            }
                        }
                    }
                    false
                }
            }

            if (docModuleLocations.isNotEmpty()) {
                spliceDocPrefixesBackIntoModules(nascentModules, docModuleLocations)
            }

            val tmpLModules = nascentModules.mapNotNull {
                if (it.isTranslationNeeded) {
                    val (deps, imports) =
                        it.translator.groupImportsIn(it.topLevels)
                    TmpL.Module(
                        pos = it.pos,
                        codeLocation = it.codeLocation,
                        moduleMetadata = it.moduleMetadata,
                        deps = deps,
                        imports = imports,
                        topLevels = it.topLevels,
                        result = it.result,
                    )
                } else {
                    null
                }
            }

            return TmpL.ModuleSet(
                pos = Position(libraryConfigurations.currentLibraryConfiguration.libraryRoot, 0, 0),
                modules = tmpLModules.toList(),
                genre = genre,
                libraryConfigurations = libraryConfigurations,
                mergedNamingContext = mergedNamingContext,
            )
        }
    }

    init {
        check(document.isResolved) // No mucking around with parsed names
    }

    internal val genre = document.context.genre
    private val builtinEnv = builtinEnvironment(EmptyEnvironment, genre)
    private val nrbStrategy = supportNetwork.bubbleStrategy
    private val coroutineStrategy = supportNetwork.coroutineStrategy
    internal val cfOptions = CfOptions(
        nrbStrategy = nrbStrategy,
        representationOfVoid = supportNetwork.representationOfVoid(genre),
    )
    internal val mergedNameMaker = ResolvedNameMaker(mergedNamingContext, genre)
    internal fun unusedName(parsedName: ParsedName): Temporary =
        mergedNameMaker.unusedTemporaryName(parsedName.nameText)
    private val thisNames = mutableMapOf<ResolvedName, DefinedNonNullType?>()
    private val topLevelMetadata = mutableMapOf<TemperName, List<TmpL.DeclarationMetadata>>()
    private val globalConsole =
        ImplicitsModule.module.exports!!.find { it.name.baseName == consoleParsedName }!!.value!!
    internal val translationAssistant: TranslationAssistant = TranslationAssistantImpl(this)
    internal val typeContext2 = TypeContext2()

    /** Allows backend-type agnostic code to fetch metadata for the appropriate backend */
    internal class MetadataFetcher<BACKEND : Backend<BACKEND>>(
        private val dr: MetadataDependencyResolver<BACKEND>,
        private val libraryConfigurations: LibraryConfigurations,
    ) {
        fun <VALUE> read(
            nameInLibrary: TemperName,
            keyFactory: MetadataKeyFactory<VALUE>,
        ): VALUE? {
            val loc = (nameInLibrary as? ModularName)?.origin?.loc ?: return null
            val key = keyFactory.acquireKey(dr.backend)
            val libraryRoot = dr.libraryRootFor(loc)
            val libraryName = libraryConfigurations.byLibraryRoot[libraryRoot]?.libraryName
                ?: return null
            return dr.getMetadata(libraryName, key)
        }
    }

    // Introduce an existential variable so that translation code can be
    // backend-type agnostic while still using the right metadata keys.
    private fun <BACKEND : Backend<BACKEND>>
        MetadataDependencyResolver<BACKEND>.metadataFetcher(): MetadataFetcher<BACKEND> =
        MetadataFetcher(this, libraryConfigurations)

    internal fun metadataFetcher() = dependencyResolver.metadataFetcher()

    /**
     * Executes [f] in a scope where [thisName] as a right name, translates to
     * a [TmpL.This] instead of to a simple [TmpL.Reference]
     */
    internal fun <T> withThisName(
        thisName: ResolvedName?,
        thisType: DefinedNonNullType?,
        /**
         * If true, do not bind this because the method is being pulled out
         * into a static or module level function declaration.
         */
        isPulledOut: Boolean,
        f: () -> T,
    ): T {
        check(thisName !in thisNames)
        if (thisName != null && !isPulledOut) {
            thisNames[thisName] = thisType
        }
        try {
            return f()
        } finally {
            thisNames.remove(thisName)
        }
    }

    private fun translate(
        loc: ModuleName,
        dependencyCategory: DependencyCategory,
        rootBlock: BlockTree,
        outputName: ResolvedName?,
        outputType: Type2?,
        tentativeOutputPath: FilePath,
    ): NascentModule {
        var result: TmpL.Expression? = null
        debug {
            console.log("Got outputName=$outputName")
        }
        var returnLabel: ResolvedName? = null
        class TranslateModuleExit : GoalTranslator {
            override val translator: TmpLTranslator get() = this@TmpLTranslator

            override fun translateFreeFailure(p: Position): Stmt {
                return when (cfOptions.nrbStrategy) {
                    BubbleBranchStrategy.CatchBubble -> OneStmt(TmpL.ThrowStatement(p))
                    BubbleBranchStrategy.IfHandlerScopeVar -> Stmts(
                        p,
                        listOfNotNull(
                            outputName?.let {
                                TmpL.Assignment(
                                    pos = p,
                                    left = TmpL.Id(p, it),
                                    right = TmpL.BubbleSentinel(p),
                                    type = bubbleType2,
                                )
                            },
                            TmpL.ModuleInitFailed(p),
                        ),
                    )
                }
            }

            override fun translateExit(p: Position): Stmt {
                if (returnLabel == null) {
                    returnLabel = unusedName(ParsedName("moduleExit"))
                }
                return OneStmt(
                    TmpL.BreakStatement(p, TmpL.JumpLabel(TmpL.Id(p, returnLabel, null))),
                )
            }

            override fun translateJump(p: Position, kind: BreakOrContinue, target: JumpSpecifier): Stmt =
                DefaultGoalTranslator(translator).translateJump(p, kind, target)
        }

        var translation = translateTopLevels(rootBlock, outputName, TranslateModuleExit())
        when (val rl = returnLabel) {
            null -> {}
            else -> {
                translation = labelTopLevelInitBlocks(
                    rootBlock.pos,
                    TmpL.Id(rootBlock.pos.leftEdge, rl),
                    translation,
                )
            }
        }
        topLevels.addAll(translation)
        if (outputName != null) {
            if (
                cfOptions.representationOfVoid == RepresentationOfVoid.ReifyVoid ||
                !outputType.isVoidLike
            ) {
                val exportPos = rootBlock.pos.rightEdge
                val leaf = RightNameLeaf(rootBlock.document, exportPos, outputName)
                if (outputType != null) {
                    leaf.typeInferences = BasicTypeInferences(hackMapNewStyleToOld(outputType), emptyList())
                }
                result = translateExpression(leaf)
            }
        }

        if (genre == Genre.Documentation) {
            // Add fold boundaries around top-levels as appropriate.
            var inDocCodeFold = false
            fun needsDocCodeFold(t: TmpL.TopLevel): Boolean = when (t) {
                is TmpL.Declaration -> t.metadata.any { it.key.symbol == withinDocFoldSymbol }
                else -> false
            }
            var i = 0
            while (i < topLevels.size) {
                val topLevel = topLevels[i++]
                val newInDocCodeFold = needsDocCodeFold(topLevel)
                if (newInDocCodeFold != inDocCodeFold) {
                    topLevels.add(
                        index = i - 1, // Before i++
                        element = if (newInDocCodeFold) {
                            TmpL.BoilerplateCodeFoldStart(topLevel.pos.leftEdge)
                        } else {
                            TmpL.BoilerplateCodeFoldEnd(topLevel.pos.leftEdge)
                        },
                    )
                    inDocCodeFold = newInDocCodeFold
                    i++ // Skip forward because of insertion
                }
            }
            if (inDocCodeFold) {
                topLevels.add(TmpL.BoilerplateCodeFoldEnd(topLevels.last().pos.rightEdge))
            }
        }

        val moduleStart = rootBlock.pos.leftEdge
        val namingContext = rootBlock.document.context.namingContext
        val sourceLibrary = libraryConfigurations.byLibraryRoot.getValue(loc.libraryRoot()).libraryName
        return NascentModule(
            pos = rootBlock.pos,
            codeLocation = TmpL.CodeLocationMetadata(sourceLibrary, loc, namingContext, tentativeOutputPath),
            moduleMetadata = TmpL.ModuleMetadata(moduleStart, dependencyCategory),
            topLevels = topLevels,
            result = result,
            translator = this,
        )
    }

    private fun translateTopLevels(
        tree: BlockTree,
        outputName: ResolvedName?,
        goalTranslator: GoalTranslator,
    ): List<TmpL.TopLevel> {
        val preTranslated = translateFlow(
            tree = tree,
            goalTranslator = goalTranslator,
            nameMaker = mergedNameMaker,
            options = cfOptions,
            outputName = outputName,
        )
        return toTopLevels(preTranslated)
    }

    /** Called back from [PreTranslated] to convert statements to analogous top levels. */
    private fun toTopLevels(preTranslated: PreTranslated): List<TmpL.TopLevel> {
        val converter = object {
            val initStmts = mutableListOf<TmpL.Statement>()
            val topLevels = mutableListOf<TmpL.TopLevel>()

            fun flushInitStmts(metadata: List<TmpL.DeclarationMetadata> = emptyList()) {
                if (initStmts.isNotEmpty()) {
                    val pos = initStmts.spanningPosition(initStmts.first().pos)
                    val initBlock = TmpL.ModuleInitBlock(
                        pos,
                        metadata = metadata,
                        body = TmpL.BlockStatement(pos, initStmts.toList()),
                    )
                    initStmts.clear()
                    topLevels.add(initBlock)
                }
            }

            fun misplaced(pos: Position, description: String) {
                flushInitStmts()
                topLevels.add(untranslatableTopLevel(pos, "misplaced $description"))
            }

            fun convertStatementToTopLevels(stmt: TmpL.Statement) {
                when (stmt) {
                    is TmpL.TopLevel -> {
                        flushInitStmts()
                        topLevels.add(stmt)
                    }
                    is TmpL.GarbageStatement -> {
                        flushInitStmts()
                        topLevels.add(TmpL.GarbageTopLevel(stmt.pos, stmt.diagnostic))
                    }
                    is TmpL.BlockStatement ->
                        stmt.takeBody().forEach { convertStatementToTopLevels(it) }
                    is TmpL.Assignment,
                    is TmpL.BreakStatement,
                    is TmpL.ComputedJumpStatement,
                    is TmpL.ExpressionStatement,
                    is TmpL.HandlerScope,
                    is TmpL.IfStatement,
                    is TmpL.LabeledStatement,
                    is TmpL.ModuleInitFailed,
                    is TmpL.ReturnStatement,
                    is TmpL.SetAbstractProperty,
                    is TmpL.ThrowStatement,
                    is TmpL.TryStatement,
                    is TmpL.WhileStatement,
                    ->
                        initStmts.add(stmt)
                    is TmpL.LocalDeclaration -> {
                        flushInitStmts()
                        if (stmt.metadata.isNotEmpty()) {
                            topLevelMetadata[stmt.name.name] = stmt.metadata
                        }
                        topLevels.add(
                            TmpL.ModuleLevelDeclaration(
                                pos = stmt.pos,
                                metadata = stmt.metadata,
                                name = stmt.name.deepCopy(),
                                init = stmt.init?.deepCopy(),
                                assignOnce = stmt.assignOnce,
                                type = stmt.type.deepCopy(),
                                descriptor = stmt.descriptor,
                            ),
                        )
                    }
                    is TmpL.LocalFunctionDeclaration -> {
                        flushInitStmts()
                        topLevels.add(
                            when (stmt.metadata.find { it.key.symbol == testSymbol }) {
                                null -> TmpL.ModuleFunctionDeclaration(
                                    pos = stmt.pos,
                                    metadata = stmt.metadata,
                                    name = stmt.name.deepCopy(),
                                    typeParameters = stmt.typeParameters.deepCopy(),
                                    parameters = stmt.parameters.deepCopy(),
                                    returnType = stmt.returnType.deepCopy(),
                                    body = stmt.body.deepCopy(),
                                    mayYield = stmt.mayYield,
                                    sig = stmt.sig,
                                )
                                else -> {
                                    val value = stmt.metadata.find { it.key.symbol == testSymbol }?.value
                                    val rawName = TString.unpackOrNull((value as? TmpL.ValueData)?.value)
                                        ?: when (val name = stmt.name.name) {
                                            is ResolvedParsedName -> name.baseName
                                            else -> name
                                        }.displayName
                                    // We expect the raw name to be available,
                                    // but we have a simple fallback here anyway.
                                    TmpL.Test(
                                        pos = stmt.pos,
                                        metadata = stmt.metadata,
                                        name = stmt.name.deepCopy(),
                                        rawName = rawName,
                                        parameters = stmt.parameters.deepCopy(),
                                        returnType = stmt.returnType,
                                        body = stmt.body.deepCopy(),
                                    )
                                }
                            },
                        )
                    }
                    // Module loading will continue without you.
                    is TmpL.ContinueStatement -> misplaced(stmt.pos, "continue")
                    // Good luck pausing module loading.
                    is TmpL.YieldStatement -> misplaced(stmt.pos, "yield")
                    // Stay in your type, setP!
                    is TmpL.SetBackedProperty -> misplaced(stmt.pos, "setP")
                }
            }

            fun processTopLevel(pt: PreTranslated) {
                when (pt) {
                    is PreTranslated.Break,
                    is PreTranslated.CombinedDeclaration,
                    is PreTranslated.Continue,
                    is PreTranslated.DocFoldBoundary,
                    is PreTranslated.Goal,
                    is PreTranslated.If,
                    is PreTranslated.LabeledStmt,
                    is PreTranslated.Return,
                    is PreTranslated.TreeWrapper,
                    is PreTranslated.WhenInt,
                    is PreTranslated.WhileLoop,
                    -> {
                        pt.toStatement(this@TmpLTranslator).stmtList.forEach {
                            convertStatementToTopLevels(it)
                        }
                    }
                    is PreTranslated.Block -> {
                        pt.fixedElements.forEach { processTopLevel(it) }
                    }
                    is PreTranslated.Try -> {
                        if (pt.justRethrows) {
                            processTopLevel(pt.tried)
                        } else {
                            pt.toStatement(this@TmpLTranslator).stmtList.forEach {
                                convertStatementToTopLevels(it)
                            }
                        }
                    }
                    is PreTranslated.CombinedTypeDefinition -> {
                        flushInitStmts()
                        val typeShape = pt.typeShape

                        // Don't generate code for a type that is connected to a backend specific type
                        val connectedKey = TString.unpackOrNull(
                            typeShape.metadata[connectedSymbol]?.firstOrNull(),
                        )
                        val type = MkType2(typeShape)
                            .actuals(typeShape.typeParameters.map { MkType2(it.definition).get() })
                            .get()
                        val connection: Pair<TargetLanguageTypeName, List<Type2>>? =
                            if (connectedKey != null) {
                                supportNetwork.translatedConnectedType(pt.pos, connectedKey, genre, type)
                            } else {
                                null
                            }

                        val (typeDeclaration, otherStuff) = translateTypeDeclaration(
                            pt.pos,
                            pt.typeShape,
                            pt.typeIdDecl,
                            pt.members,
                            connection,
                            this@TmpLTranslator,
                        )

                        // Are we going to put the type definition in the topLevels list?
                        // We might not put it anywhere if it's a connected list.
                        // If it's a connected type, and its whole public API is connected, then
                        // we don't need to emit the type definition at all.
                        var definitionNeeded = true
                        if (functionalInterfaceSymbol in pt.typeShape.metadata &&
                            supportNetwork.functionTypeStrategy == FunctionTypeStrategy.ToFunctionType
                        ) {
                            definitionNeeded = false
                        } else if (connection != null) {
                            check(connectedKey != null)
                            val neededMembers = typeDeclaration.members.filter { member ->
                                member.neededIfEnclosingTypeIsConnected
                            }
                            if (neededMembers.isEmpty()) {
                                definitionNeeded = false
                            }
                            val rightPos = typeDeclaration.pos.rightEdge
                            topLevels.add(
                                TmpL.TypeConnection(
                                    pos = typeDeclaration.pos,
                                    metadata = typeDeclaration.metadata,
                                    name = typeDeclaration.name.deepCopy(),
                                    typeParameters = typeDeclaration.typeParameters.deepCopy(),
                                    superTypes = typeDeclaration.superTypes.deepCopy(),
                                    to = TmpL.NominalType(
                                        pos = rightPos,
                                        typeName = TmpL.ConnectedToTypeName(rightPos, typeShape, connection.first),
                                        params = connection.second.map {
                                            typeTranslator.translateTypeActual(rightPos, it)
                                        },
                                        connectsFrom = type,
                                    ),
                                    connectedKey = TmpL.ConnectedKey(pt.pos.rightEdge, connectedKey),
                                    kind = typeDeclaration.kind,
                                    typeShape = typeDeclaration.typeShape,
                                ),
                            )
                        }

                        if (definitionNeeded) {
                            topLevels.add(typeDeclaration)
                        }

                        topLevels.addAll(otherStuff)
                    }
                    is PreTranslated.ConvertedCoroutine -> { // Handled in function translation
                        flushInitStmts()
                        topLevels.add(untranslatableTopLevel(pt.pos, "Misplaced coroutine body"))
                    }
                    is PreTranslated.Garbage -> {
                        flushInitStmts()
                        topLevels.add(untranslatableTopLevel(pt.pos, pt.diagnosticString))
                    }
                }
            }
        }
        converter.processTopLevel(preTranslated)
        converter.flushInitStmts()
        val topLevels = converter.topLevels.map { postProcess(it) }
        return sortedTopLevels(topLevels)
    }

    private fun postProcess(topLevel: TmpL.TopLevel): TmpL.TopLevel {
        return when {
            supportNetwork.needsLabeledBreakFromSwitch ->
                labelBreaksFromSwitchIfNeeded(topLevel, mergedNameMaker) as TmpL.TopLevel
            else -> topLevel
        }
    }

    internal fun translateStatement(tree: Tree): Stmt =
        when (val cp = translateStatementOrExpression(tree)) {
            is Stmt -> cp
            is OneExpr -> OneStmt(TmpL.ExpressionStatement(cp.pos, cp.expr))
        }

    internal fun translateDeclarationToStmt(
        pos: Position,
        tree: DeclTree,
        allowFunctionDecl: Boolean,
        initial: Tree?,
    ): Stmt {
        val td = translateDeclaration(
            tree,
            allowFunctionDecl = allowFunctionDecl,
            initial = initial,
        )
        return when (td) {
            is UntranslatableDeclaration -> OneStmt(untranslatableStmt(tree.pos, td.reason))
            is TranslatedDeclaration -> Stmts(pos, td.stmtList)
            is NoDeclarationNeeded -> Stmts(pos, emptyList())
        }
    }

    private fun translateDeclaration(
        tree: DeclTree,
        allowFunctionDecl: Boolean,
    ): TranslatedDeclarationResult = translateDeclaration(
        tree,
        allowFunctionDecl,
        tree.parts?.metadataSymbolMap?.get(initSymbol)?.target,
    )

    private fun translateDeclaration(
        tree: DeclTree,
        allowFunctionDecl: Boolean,
        initial: Tree? = null,
    ): TranslatedDeclarationResult {
        val declParts = tree.parts ?: return UntranslatableDeclaration("Malformed declaration")
        val id = translatePattern(declParts.name)
            ?: return UntranslatableDeclaration("Untranslatable name")

        val assignOnce = varSymbol !in declParts.metadataSymbolMap

        // Convert a declaration with a function initializer to a function declaration
        if (allowFunctionDecl && initial is FunTree) {
            return translateFunctionDeclaration(
                id = id,
                declPos = tree.pos,
                init = initial,
                metadata = declParts.metadataSymbolMap,
                doTranslateFunctionDefinition = ::translateFunctionDefinition,
            )
        }

        val initialTmpL = initial?.let {
            if (allowFunctionDecl && it is ValueLeaf && assignOnce) {
                translateValueAsExpression(it, id.name, declParts.metadataSymbolMap)
            } else {
                translateExpression(it)
            }
        }
        if (initialTmpL is TmpL.AnyReference && initialTmpL.id.name == id.name) {
            return NoDeclarationNeeded
        }
        if (typeDeclSymbol in declParts.metadataSymbolMap) {
            // These are redundant with TmpL.TypeDeclarations so we can skip them.
            return NoDeclarationNeeded
        }

        val typePos = declParts.type?.target?.pos ?: declParts.name.pos
        val (type, descriptor) =
            declParts.name.typeInferences?.type?.let { t ->
                val type = hackMapOldStyleToNew(t)
                translateType(typePos, type) to type
            } ?: (untranslatableType(typePos, "missing type") to WellKnownTypes.invalidType2)

        val simpleDecl = TmpL.LocalDeclaration(
            pos = tree.pos,
            metadata = translateDeclarationMetadata(declParts.metadataSymbolMap),
            name = id,
            type = type.aType,
            init = initialTmpL,
            assignOnce = assignOnce,
            descriptor = descriptor,
        )

        return TranslatedDeclaration(
            declaredName = id,
            declaringStatement = simpleDecl,
        )
    }

    private fun translateDeclarationMetadata(
        metadata: MetadataMap,
        funMetadata: MetadataMap?,
    ) = translateDeclarationMetadata(
        libraryConfigurations.currentLibraryConfiguration.libraryName,
        if (funMetadata.isNullOrEmpty()) { metadata } else { metadata + funMetadata },
        getQNameMap(),
    )

    internal fun translateDeclarationMetadata(
        metadata: MetadataMap,
    ) = translateDeclarationMetadata(
        libraryConfigurations.currentLibraryConfiguration.libraryName,
        metadata,
        getQNameMap(),
    )

    internal fun translateDeclarationMetadataValueMultimap(
        metadata: MetadataValueMultimap,
    ): List<TmpL.DeclarationMetadata> = translateDeclarationMetadataValueMultimap(
        libraryConfigurations.currentLibraryConfiguration.libraryName,
        metadata,
        getQNameMap(),
    )

    private fun getQNameMap(): Map<ResolvedName, QName> =
        dependencyResolver.getMetadata(
            libraryConfigurations.currentLibraryConfiguration.libraryName,
            QNameMapping.key(dependencyResolver.backend.backendId),
        ) ?: emptyMap()

    internal fun translateSimpleFunctionDeclaration(
        id: TmpL.Id,
        declPos: Position,
        init: FunTree,
        metadata: MetadataMap,
    ): TmpL.LocalFunctionDeclaration? {
        val tf = translateFunctionDeclaration(
            id = id,
            declPos = declPos,
            init = init,
            metadata = metadata,
            doTranslateFunctionDefinition = ::translateFunctionDefinition,
        )
        if (tf.stmtList.size == 1) {
            val d = tf.declaringStatement as? TmpL.LocalFunctionDeclaration
            if (d != null) {
                return d
            }
        }
        untranslatableStmt(declPos, "Could not translate to simple function")
        return null
    }

    private fun <INIT> translateFunctionDeclaration(
        id: TmpL.Id,
        declPos: Position,
        init: INIT,
        metadata: MetadataMap,
        extraMetadata: List<TmpL.DeclarationMetadata> = emptyList(),
        doTranslateFunctionDefinition: (INIT, ResolvedName) -> TranslatedFunction?,
    ): TranslatedDeclaration {
        val constName = id.name

        val parts = (init as? FunTree)?.parts
        val stay = parts?.metadataSymbolMap?.get(staySymbol)?.target as? StayLeaf
        if (stay != null) {
            // TODO: Is there a case where we might reach a value with the stay before we reach
            // the named declaration of the underlying function?
            pool.associateWithStay(stay, constName)
        }

        val tmpLMetadata = translateDeclarationMetadata(metadata, parts?.metadataSymbolMap)
        val tf = doTranslateFunctionDefinition(init, constName)
            ?: return TranslatedDeclaration(id, untranslatableStmt(declPos, "Untranslatable function"))

        val localFunctionDecl = TmpL.LocalFunctionDeclaration(
            pos = declPos,
            metadata = tmpLMetadata + extraMetadata,
            name = TmpL.Id(id.pos, constName),
            typeParameters = tf.typeParameters,
            parameters = tf.parameters,
            returnType = tf.returnType.aType,
            body = tf.body,
            mayYield = tf.mayYield,
            sig = tf.sig,
        )
        return TranslatedDeclaration(id, localFunctionDecl)
    }

    private fun translateStatementOrExpression(tree: Tree): StmtOrExpr = when (tree) {
        is BlockTree -> OneStmt(untranslatableStmt(tree)) // We need context for `return`.
        is DeclTree -> when (val translatedDeclaration = translateDeclaration(tree, allowFunctionDecl = true)) {
            is UntranslatableDeclaration -> OneStmt(untranslatableStmt(tree.pos, translatedDeclaration.reason))
            is TranslatedDeclaration -> Stmts(tree.pos, translatedDeclaration.stmtList)
            is NoDeclarationNeeded -> Stmts(tree.pos, emptyList())
        }
        is CallTree -> when {
            isYieldCall(tree) -> OneStmt(TmpL.YieldStatement(tree.pos)) // TODO: yielded value expression
            isAssignmentCall(tree) -> Stmts(tree.pos, translateAssignment(tree, null))
            isSetPropertyCall(tree) -> translateSetP(tree, failedId = null)
            isHandlerScopeCall(tree) -> {
                val failedTree = tree.child(1) as LeftNameLeaf
                val failedName = failedTree.content as ResolvedName
                val handledTree = tree.child(2)
                if (isAssignmentCall(handledTree)) {
                    val assignment = handledTree as CallTree
                    val failedId = TmpL.Id(failedTree.pos, failedName)
                    Stmts(tree.pos, translateAssignment(assignment, failedId))
                } else {
                    if (isSetPropertyCall(handledTree)) {
                        val failedId = TmpL.Id(failedTree.pos, failedName)
                        translateSetP(handledTree, failedId = failedId)
                    } else {
                        val statement = when (val call = translateHandlerScopeCallByStrategy(tree)) {
                            is TmpL.Statement -> call
                            is TmpL.Expression -> TmpL.ExpressionStatement(call)
                        }
                        OneStmt(statement)
                    }
                }
            }
            isBubbleCall(tree) -> OneStmt(TmpL.ThrowStatement(tree.pos))
            isRemCall(tree) -> translatedEmbeddedComment(tree)
            // Setter invocations are statement-like but the others
            // go through translateCall which consolidates parameterized
            // call handling.
            isSetterCall(tree) -> translateDotHelperCall(tree)
            else -> OneExpr(translateCall(tree))
        }
        is FunTree,
        is NameLeaf,
        is ValueLeaf,
        -> OneExpr(translateExpression(tree))
        is StayLeaf -> OneExpr(untranslatableExpr(tree))
        is EscTree -> OneExpr(untranslatableExpr(tree)) // TODO
    }

    /**
     * Translates an assignments.
     *
     * @return statements that should precede the handleable that performs the assigment,
     *     and the handleable.
     */
    private fun translateAssignment(
        tree: CallTree,
        failId: TmpL.Id?,
    ): List<TmpL.Statement> {
        require(isAssignmentCall(tree))
        val left = tree.child(1) as LeftNameLeaf
        val rightTree = tree.child(2)
        return translateAssignment(tree.pos, left = left, right = rightTree, failId = failId)
    }

    private fun translateAssignment(
        pos: Position,
        left: LeftNameLeaf,
        right: Tree,
        failId: TmpL.Id?,
    ): List<TmpL.Statement> {
        val leftName = left.content as ResolvedName
        var rightTree = right
        val statements = mutableListOf<TmpL.Statement>()
        if (rightTree is FunTree) {
            // Make sure every function is declared via a name
            val fnName = unusedName(fnParsedName)
            val translatedDeclaration = translateFunctionDeclaration(
                id = TmpL.Id(rightTree.pos.leftEdge, fnName),
                declPos = pos,
                init = rightTree,
                metadata = emptyMap(),
                extraMetadata = topLevelMetadata[leftName] ?: emptyList(),
                doTranslateFunctionDefinition = ::translateFunctionDefinition,
            )
            statements.addAll(translatedDeclaration.stmtList)
            rightTree = RightNameLeaf(rightTree.document, rightTree.pos, fnName)
        }
        if (failId != null) {
            statements.add(
                TmpL.Assignment(
                    pos.leftEdge,
                    failId.deepCopy(),
                    TmpL.ValueReference(pos.leftEdge, TBoolean.valueFalse),
                    booleanType2,
                ),
            )
        }

        var rightExpr = if (isHandlerScopeCall(rightTree)) {
            translateHandlerScopeCallByStrategy(rightTree)
        } else {
            translateExpression(rightTree)
        }

        if (rightExpr is TmpL.ValueReference) {
            // Value-references do not have constructors, so there is no opportunity
            // to insert implicit casts but some values, especially `null` could
            // benefit from reinterpretation.
            // Simulate an output cast injection here to catch that case.
            rightExpr = maybeInjectCastForOutput(
                expr = rightExpr,
                actualCalleeType = Signature2(rightExpr.type, hasThisFormal = false, listOf()),
                declaredCalleeType = hackMapOldStyleToNewOrNull(left.typeInferences?.type)?.let {
                    Signature2(it, hasThisFormal = false, listOf())
                },
                adjustments = null,
                builtinOperatorId = null,
            )
            // If rightExpr is `null`, then the actual type will be `Null` and the
            // declared type something like `Foo | Null`.
        }

        statements.add(
            TmpL.Assignment(pos, TmpL.Id(left.pos, leftName), rightExpr, left.typeOrInvalid),
        )
        return statements.toList()
    }

    private fun translateSetP(
        setPCall: Tree,
        failedId: TmpL.Id?,
    ): Stmt {
        val pos = setPCall.pos
        val (_, propNameTree, objTree, rightTree) = setPCall.children

        val propName = (propNameTree as? LeftNameLeaf)?.content as? SourceName
            ?: return OneStmt(untranslatableStmt(setPCall))

        val setpStatement = TmpL.SetBackedProperty(
            pos,
            TmpL.PropertyLValue(
                listOf(objTree, propNameTree).spanningPosition(pos),
                subject = translateExpression(objTree),
                property = TmpL.InternalPropertyId(TmpL.Id(propNameTree.pos, propName)),
            ),
            translateExpression(rightTree),
        )

        return if (failedId != null) {
            Stmts(
                pos,
                listOf(
                    // TODO: Figure out how setp can fail?
                    // Does it check types for the right and/or obj?
                    // If it can't, mark it as not failing per se.
                    TmpL.Assignment(
                        pos.leftEdge,
                        failedId,
                        TmpL.ValueReference(pos.leftEdge, TBoolean.valueFalse),
                        booleanType2,
                    ),
                    setpStatement,
                ),
            )
        } else {
            OneStmt(setpStatement)
        }
    }

    private fun translatePattern(tree: Tree): TmpL.Id? = when (tree) {
        is LeftNameLeaf -> when (val name = tree.content) {
            is ModularName -> TmpL.Id(tree.pos, name as ResolvedName)
            is ParsedName, // Well, that's unexpected.
            is BuiltinName,
            -> // Can't assign builtins
                null
        }
        else -> null
    }

    internal fun translateExpression(tree: Tree): TmpL.Expression {
        return try {
            when (tree) {
                is BlockTree -> untranslatableExpr(tree)
                is DeclTree -> untranslatableExpr(tree)
                is CallTree -> translateCall(tree)
                is LeftNameLeaf -> untranslatableExpr(tree)
                is RightNameLeaf -> translateReference(tree, asCallable = false) as TmpL.Expression
                is ValueLeaf -> translateValueAsExpression(tree)
                is FunTree -> {
                    val stay = tree.parts?.metadataSymbolMap?.get(staySymbol)?.target as? StayLeaf
                    val name = stay?.let { pool.nameForStay(it) }
                        ?: unusedName(ParsedName("anon"))
                    val fn = translateFunctionDefinition(tree, nameInPool = name)
                    if (fn?.id != null) {
                        if (stay != null) {
                            pool.associateWithStay(stay, fn.id.name)
                        }
                        // TODO: Pull this into the narrowest containing block.
                        topLevels.add(
                            TmpL.ModuleFunctionDeclaration(
                                pos = fn.pos,
                                metadata = emptyList(),
                                name = fn.id,
                                typeParameters = fn.typeParameters,
                                parameters = fn.parameters,
                                returnType = fn.returnType.aType,
                                body = fn.body,
                                mayYield = fn.mayYield,
                                sig = fn.sig,
                            ),
                        )
                        TmpL.Reference(fn.id.deepCopy(), type = tree.typeOrInvalid)
                    } else {
                        garbageExpr(tree.pos, "Untranslatable Function")
                    }
                }
                is StayLeaf -> untranslatableExpr(tree)
                is EscTree -> untranslatableExpr(tree) // TODO
            }
        } catch (e: NotImplementedError) {
            console.errorDense(e)
            untranslatableExpr(tree.pos, e.message ?: tree.toLispy())
        }
    }

    internal fun translateCallable(tree: Tree): TmpL.Callable {
        return try {
            val expressionOrCallable = when (tree) {
                is BlockTree -> null
                is DeclTree -> null
                is CallTree -> translateCall(tree)
                is LeftNameLeaf -> null
                is RightNameLeaf -> translateReference(tree, asCallable = true) as TmpL.Callable
                is ValueLeaf -> translateValueAsCallable(tree)
                is FunTree -> null
                is StayLeaf -> null
                is EscTree -> null
            }
            when (expressionOrCallable) {
                is TmpL.Callable -> expressionOrCallable
                is TmpL.Expression -> {
                    // This is reached by several legit paths.
                    // - function inputs with a functional interface type
                    // - reads of class/interface properties with a functional interface type
                    // - calls to functions that return a functional interface value
                    // - references to a named function, e.g. returning an inner function as a functional interface
                    // - narrowing or widening casts of one of the above to a functional interface type
                    // - possibly other uses

                    // If we have a functional interface type on the tree, we can
                    // use a converting cast from a value to a callable.
                    val temperType = tree.typeOrInvalid
                    withType(
                        temperType,
                        fn = { _, sig, _ ->
                            TmpL.FunInterfaceCallable(expressionOrCallable.pos, expressionOrCallable, sig)
                        },
                        fallback = { null },
                    )
                }
                null -> null
            }
        } catch (e: NotImplementedError) {
            console.errorDense(e)
            e.message?.let { garbageCallable(tree.pos, it) }
        } ?: untranslatableCallable(tree)
    }

    private fun translateReference(tree: RightNameLeaf, asCallable: Boolean): TmpL.ExpressionOrCallable {
        val type = lazy {
            if (asCallable) {
                tree.sig.orInvalid
            } else {
                tree.typeOrInvalid
            }
        }

        when (val name = tree.content) {
            is ModularName -> {
                val pos = tree.pos
                if (name in thisNames) {
                    val thisType = thisNames[name]
                    return if (thisType != null) {
                        TmpL.This(TmpL.Id(pos, name as ResolvedName), type = thisType)
                    } else {
                        untranslatableExpr(pos, "Missing type info for `this` reference: $name")
                    }
                }

                var supportCode: SupportCode? = null
                if (name is ExportedName) {
                    val originExporter = (name.origin as? ModuleNamingContext)?.owner
                    val export = originExporter?.exportMatching(name)
                    val connectedKey = TString.unpackOrNull(
                        export?.declarationMetadata?.get(connectedSymbol)?.lastOrNull(),
                    )
                    supportCode = connectedKey?.let {
                        supportNetwork.translateConnectedReference(pos, connectedKey, genre)
                    }
                }
                val resolvedName = if (supportCode != null) {
                    pool.fillIfAbsent(pos, supportCode, desc = type.value, metadata = emptyMap())
                } else {
                    name
                }
                return when (val t = type.value) {
                    is Signature2 -> TmpL.FnReference(TmpL.Id(pos, resolvedName), t)
                    is Type2 -> TmpL.Reference(TmpL.Id(pos, resolvedName), t)
                }
            }
            // ParsedNames should have been resolved
            is ParsedName -> return if (asCallable) {
                untranslatableCallable(tree)
            } else {
                untranslatableExpr(tree)
            }
            is BuiltinName -> {
                val pos = tree.pos
                // Treat imports of implicits separately
                // BuiltinNames, if not implicits, should have folded to constants or been replaced with error
                // nodes for being unresolvable.
                val value = builtinEnv[name, InterpreterCallback.NullInterpreterCallback] as? Value<*>

                // First, see if this is an exported type's constructor.  If it is a connected type, it
                // has a single constructor with the `@connected` decoration.
                val constructorConnectedKeyAndSig: Pair<String, Signature2>? = run findConnectedKey@{
                    val reifiedType = TType.unpackOrNull(value)
                    val exportedType = reifiedType?.type2
                    val exportedTypeDefinition = (exportedType as? DefinedNonNullType)?.definition
                    val constructors = exportedTypeDefinition?.methods?.filter {
                        it.methodKind == MethodKind.Constructor
                    }
                    if (constructors?.size == 1) {
                        val constructor: MethodShape = constructors[0]
                        val decl = constructor.stay?.incoming?.source as? DeclTree
                        val constructorMetadata = decl?.parts?.metadataSymbolMap
                        if (constructorMetadata != null) {
                            val connectedKeyTree = constructorMetadata[connectedSymbol]?.target
                            if (connectedKeyTree is ValueLeaf) {
                                val connectedKey = TString.unpackOrNull(connectedKeyTree.content)
                                if (connectedKey != null) {
                                    val sig = constructor.descriptor
                                        ?.let { constructorSig ->
                                            factorySignatureFromConstructorSignature(constructorSig)
                                        }
                                    if (sig != null) {
                                        return@findConnectedKey connectedKey to sig
                                    }
                                }
                            }
                        }
                    }
                    null
                }

                var translation: TmpL.ExpressionOrCallable? = null
                if (constructorConnectedKeyAndSig != null) { // An exported constructor
                    val (key, sig) = constructorConnectedKeyAndSig
                    val supportCode = supportNetwork.translateConnectedReference(pos, key, genre)
                    if (supportCode != null) {
                        val supportCodeName =
                            pool.fillIfAbsent(pos, supportCode, desc = sig, metadata = emptyMap())
                        translation = when (val t = type.value) {
                            is Signature2 -> TmpL.FnReference(TmpL.Id(pos, supportCodeName), t)
                            is Type2 -> TmpL.Reference(TmpL.Id(pos, supportCodeName), t)
                        }
                    }
                }
                return translation ?: translateBuiltinName(pos, name, value, type.value)
            }
        }
    }

    private fun translateBuiltinName(
        pos: Position,
        name: BuiltinName,
        /** The value exported from implicits */
        value: Value<*>?,
        type: Descriptor,
    ): TmpL.ExpressionOrCallable = when (genre) {
        Genre.Library ->
            if (value != null) {
                val pooled = PooledValue(value, type)
                val sharedName = pool.fillIfAbsent(
                    pos,
                    pooled,
                    desc = type,
                    metadata = emptyMap(),
                )
                when (type) {
                    is Type2 -> TmpL.Reference(TmpL.Id(pos, sharedName), type)
                    is Signature2 -> TmpL.FnReference(TmpL.Id(pos, sharedName), type)
                }
            } else {
                val supportCode = supportNetwork.getSupportCode(pos, PanicFn, genre)
                if (supportCode != null && type is Type2) {
                    // Panic if code reaches a reference with no declared referent
                    val panicFnSig = PanicFn.sigs[1] // The non-void sig
                    val callable = supportCodeReference(supportCode, null, pos, panicFnSig, emptyMap())
                    maybeInline(
                        TmpL.CallExpression(
                            pos,
                            callable,
                            TmpL.CallTypeActuals(
                                pos.leftEdge,
                                listOf(translateType(pos, type).aType),
                                mapOf(panicFnSig.typeFormals[0] to type),
                            ),
                            listOf(),
                            type,
                        ),
                    )
                } else {
                    when (type) {
                        is Type2 -> untranslatableExpr(pos, "$name not available from implicits")
                        is Signature2 -> untranslatableCallable(pos, "$name not available from implicits")
                    }
                }
            }
        Genre.Documentation -> when (type) {
            is Type2 -> TmpL.Reference(TmpL.Id(pos, name), type)
            is Signature2 -> TmpL.FnReference(TmpL.Id(pos, name), type)
        }
    }

    private fun translateValueAsExpression(
        tree: ValueLeaf,
        /** A name to use for any pooled declaration. */
        associatedName: ResolvedName? = null,
        /** Metadata to use for any pooled declaration. */
        metadata: MetadataMap = emptyMap(),
    ): TmpL.Expression = translateValue(
        tree = tree,
        associatedName = associatedName,
        metadata = metadata,
        asCallable = false,
    ) as TmpL.Expression

    private fun translateValueAsCallable(
        tree: ValueLeaf,
        /** A name to use for any pooled declaration. */
        associatedName: ResolvedName? = null,
        /** Metadata to use for any pooled declaration. */
        metadata: MetadataMap = emptyMap(),
    ): TmpL.Callable = translateValue(
        tree = tree,
        associatedName = associatedName,
        metadata = metadata,
        asCallable = true,
    ) as TmpL.Callable

    private fun translateValue(
        tree: ValueLeaf,
        /** A name to use for any pooled declaration. */
        associatedName: ResolvedName? = null,
        /** Metadata to use for any pooled declaration. */
        metadata: MetadataMap = emptyMap(),
        asCallable: Boolean = false,
    ): TmpL.ExpressionOrCallable {
        // TODO(tjp): Find connected methods here, too, such as for constructors.
        val value = tree.content
        val descriptor = if (asCallable) tree.sig.orInvalid else tree.typeOrInvalid
        val pos = tree.pos

        if (!asCallable && value == emptyValue) {
            // Translate as if a call to Implicit's empty()
            return translateCall(
                tree.document.treeFarm.grow(pos) {
                    Call(type = emptyCallType) {
                        Rn(BuiltinName("empty"), type = emptyCallType.variant)
                    }
                },
            )
        }

        val pooledValue = PooledValue(value, descriptor)
        // If it's in the pool, use it
        var pooledValueRef: TmpL.AnyReference? = when (descriptor) {
            is Type2 -> pool.refOrNull(pos, pooledValue, descriptor)
            is Signature2 -> pool.fnRefOrNull(pos, pooledValue, descriptor)
        }
        if (pooledValueRef != null) {
            return pooledValueRef
        }
        // If there is a stay leaf associated with it, use that.
        val stay = stayForValue(value)
        val stayName = stay?.let {
            pool.nameForStay(stay)
                ?: if (shouldPool(pooledValue.value) && pool.valueNeedsPooling(pooledValue)) {
                    null // fall back to below
                } else {
                    // If there's not a connected key, and we have a canonical name,
                    // use that and let our auto-linking based on TmpL.Imports
                    // fix up the name later.
                    exportedNameForStay(stay, pool.selfLoc)
                }
        }

        if (stayName != null) {
            return when (descriptor) {
                is Type2 -> TmpL.Reference(TmpL.Id(pos, stayName), descriptor)
                is Signature2 -> TmpL.FnReference(TmpL.Id(pos, stayName), descriptor)
            }
        }

        // Otherwise, try to populate the pool
        if (shouldPool(pooledValue.value)) {
            pool.fillIfAbsent(
                pos = pos,
                poolable = pooledValue,
                suggestedName = associatedName,
                desc = descriptor,
                metadata = metadata,
            )
            pooledValueRef = when (descriptor) {
                is Type2 -> pool.refOrNull(pos, pooledValue, descriptor)
                is Signature2 -> pool.fnRefOrNull(pos, pooledValue, descriptor)
            }
            if (pooledValueRef != null) {
                return pooledValueRef
            }
        }
        return if (descriptor is Type2) {
            TmpL.ValueReference(pos, descriptor, value)
        } else {
            untranslatableCallable(tree)
        }
    }

    internal fun maybeInjectCastForInput(
        expr: TmpL.Actual,
        argIndex: Int,
        actualCalleeType: Signature2?,
        declaredCalleeType: Signature2?,
        adjustments: SignatureAdjustments?,
        builtinOperatorId: BuiltinOperatorId?,
    ): TmpL.Actual {
        if (expr !is TmpL.Expression) { return expr }
        val actualArg = actualCalleeType?.valueFormalForActual(argIndex)
            ?: return expr
        val declaredArg = declaredCalleeType?.valueFormalForActual(argIndex)
            ?: return expr
        val type = expr.type
        val cast = supportNetwork.maybeInsertImplicitCast(
            fromActualType = type,
            fromDeclaredType = type,
            fromAdjustment = null,
            toActualType = actualArg.type,
            toDeclaredType = declaredArg.type,
            toAdjustment = adjustments?.inputAdjustments?.getOrNull(argIndex),
            builtinOperatorId = builtinOperatorId,
        )
        return maybeInjectCast(cast, expr)
    }

    internal fun maybeInjectCastForOutput(
        expr: TmpL.Expression,
        actualCalleeType: Signature2?,
        declaredCalleeType: Signature2?,
        adjustments: SignatureAdjustments?,
        builtinOperatorId: BuiltinOperatorId?,
    ): TmpL.Expression {
        if (actualCalleeType == null || declaredCalleeType == null) { return expr }
        val type = expr.type
        val cast = supportNetwork.maybeInsertImplicitCast(
            fromActualType = actualCalleeType.returnType2,
            fromDeclaredType = declaredCalleeType.returnType2,
            fromAdjustment = adjustments?.outputAdjustment,
            toActualType = type,
            toDeclaredType = type,
            toAdjustment = null,
            builtinOperatorId = builtinOperatorId,
        )
        return maybeInjectCast(cast, expr)
    }

    private fun maybeInjectCast(cast: SupportCode?, expr: TmpL.Expression): TmpL.Expression {
        val type = expr.type
        return if (cast != null) {
            val callee = supportCodeReference(
                cast,
                null,
                expr.pos.leftEdge,
                Signature2(type, hasThisFormal = false, listOf(type)),
                emptyMap(),
            )
            maybeInline(
                TmpL.CallExpression(
                    pos = expr.pos,
                    fn = callee,
                    parameters = listOf(expr),
                    type = type,
                ),
            )
        } else {
            expr
        }
    }

    private fun translateCall(tree: CallTree): TmpL.Expression {
        var translation: TmpL.Expression? = null
        var calleeTranslation: TmpL.Callable? = null

        val originalCallee = tree.childOrNull(0)
            ?: return garbageExpr(tree.pos, "Missing callee")
        var tentativeCallee = originalCallee
        val isNewCall = isNewCall(tree)
        val originalCalleeSig = if (isNewCall) {
            hackTryStaticTypeToSig(tree.typeInferences?.variant)
        } else {
            originalCallee.sig
        }.orInvalid

        var typeActuals = emptyList<Tree>()
        if (isAngleBracketApplication(tentativeCallee)) {
            typeActuals = tentativeCallee.children.subListToEnd(2) // skip <> and callee
            tentativeCallee = tentativeCallee.child(1)
        }

        val effectiveCallee = tentativeCallee
        val builtinOperatorId = (effectiveCallee.functionContained as? NamedBuiltinFun)
            ?.builtinOperatorId

        // First, unpack special calls that are themselves called.
        //
        // (Call (Call gets Type \staticMethodName) ...args)
        // is a call to a static method with args.
        //
        // TODO: This is probably where we need to handle calls to
        // nym`<>` that provide type arguments to the eventual callee.
        val staticMethodCallee = unpackStaticMethodCallee(effectiveCallee)
        if (staticMethodCallee != null) {
            val connectedKey = staticMethodCallee.member.metadata[connectedSymbol, TString]
            if (connectedKey != null) {
                val supportCode =
                    supportNetwork.translateConnectedReference(tree.pos, connectedKey, genre)
                calleeTranslation = when (supportCode) {
                    null -> null
                    is InlineSupportCode<*, *> -> {
                        return maybeInjectCastForOutput(
                            maybeInline(
                                TmpL.CallExpression(
                                    pos = tree.pos,
                                    fn = TmpL.InlineSupportCodeWrapper(
                                        pos = originalCallee.pos,
                                        type = originalCalleeSig,
                                        supportCode = supportCode,
                                    ),
                                    parameters = (1 until tree.size).map {
                                        val arg = tree.child(it)
                                        maybeInjectCastForInput(
                                            expr = translateExpression(arg),
                                            argIndex = it - 1,
                                            actualCalleeType = effectiveCallee.sig,
                                            declaredCalleeType = hackTryStaticTypeToSig(tree.typeInferences?.variant),
                                            adjustments = null,
                                            builtinOperatorId = builtinOperatorId,
                                        )
                                    },
                                    type = tree.typeOrInvalid,
                                ),
                            ),
                            actualCalleeType = effectiveCallee.sig,
                            declaredCalleeType = hackTryStaticTypeToSig(tree.typeInferences?.variant),
                            adjustments = null,
                            builtinOperatorId = builtinOperatorId,
                        )
                    }

                    else -> {
                        val name = pool.fillIfAbsent(
                            originalCallee.pos,
                            supportCode,
                            originalCalleeSig,
                            emptyMap(),
                            null,
                        )
                        TmpL.FnReference(TmpL.Id(originalCallee.pos, name), originalCalleeSig)
                    }
                }
            }
            // If the method is not connected, we translate the call more simply.
            if (calleeTranslation == null) {
                calleeTranslation =
                    translateGetStaticOp(effectiveCallee, originalCalleeSig, returnCallable = true)
                        as TmpL.Callable
            }
        }

        // DotHelpers for setters, do_{i,}set_... calls, are statement like so handled elsewhere.
        // There are two varieties of DotHelper calls we need to handle here:
        // - Uses of do_{i,}get_... that correspond to property reads
        // - Calls to nested uses of calls to do_{i,}bind_... that correspond to method calls
        val dotHelperTranslation = if (effectiveCallee.functionContained is DotHelper) {
            translateDotHelperCall(
                tree,
                callee = effectiveCallee,
                outerCallTree = null,
                typeActuals = typeActuals,
            )
        } else if (dotHelperFromCallOrNull(effectiveCallee)?.memberAccessor is BindMemberAccessor) {
            check(effectiveCallee is CallTree)
            translateDotHelperCall(
                effectiveCallee,
                callee = effectiveCallee.child(0),
                outerCallTree = tree,
                typeActuals = typeActuals,
            )
        } else {
            null
        }
        if (dotHelperTranslation != null) {
            return (dotHelperTranslation as OneExpr).expr
        }

        if (calleeTranslation == null && effectiveCallee is ValueLeaf) {
            // Special handling for some macros that survive to code generation.
            val value = effectiveCallee.content
            when (val fn = TFunction.unpackOrNull(value)) {
                BuiltinFuns.commaFn -> {
                    translation = untranslatableExpr(tree.pos, "comma functions should be eliminated")
                }
                // Should be captured by statement processing.
                BuiltinFuns.setLocalFn, BuiltinFuns.setpFn -> {
                    translation = untranslatableExpr(tree.pos, "misplaced assignment")
                }
                // Should be captured by statement processing.
                BuiltinFuns.handlerScope -> {
                    translation = untranslatableExpr(tree.pos, "misplaced hs")
                }
                BuiltinFuns.getpFn -> {
                    if (tree.size == GETP_ARITY) {
                        val propNameTree = tree.child(1)
                        val objTree = tree.child(2)
                        val propName = (propNameTree as? NameLeaf)?.content as? SourceName
                        if (propName != null) {
                            translation = TmpL.GetBackedProperty(
                                tree.pos,
                                subject = translateExpression(objTree),
                                property = TmpL.InternalPropertyId(
                                    TmpL.Id(propNameTree.pos, propName),
                                ),
                                type = tree.typeOrInvalid,
                            )
                        }
                    }
                    if (translation == null) {
                        translation = untranslatableExpr(
                            tree.pos,
                            "malformed property read",
                        )
                    }
                }
                is GetStaticOp ->
                    translation = translateGetStaticOp(tree, originalCalleeSig, returnCallable = false)
                        as TmpL.Expression
                BuiltinFuns.notNullFn -> {
                    translation = TmpL.UncheckedNotNullExpression(
                        pos = tree.pos,
                        expression = translateExpression(tree.child(1)),
                        type = tree.typeOrInvalid,
                    )
                }
                is RttiCheckFunction -> {
                    if (tree.size == AS_ARITY) {
                        translation = translateRttiCall(fn, tree)
                    }
                    if (translation == null) {
                        translation = untranslatableExpr(tree.pos, "malformed cast")
                    }
                }
                New -> {
                    if (tree.size >= 2) {
                        var constructorTree = tree.child(1)
                        if (isAngleBracketApplication(constructorTree)) {
                            constructorTree = constructorTree.child(1)
                            // TODO: capture type actuals and add them to the call expression.
                        }
                        val constructedType = hackMapOldStyleToNewOrNull(constructorTree.staticTypeContained)
                        if (constructedType is DefinedNonNullType) {
                            val typeName = translateTypeName(
                                pos = constructorTree.pos,
                                type = constructedType,
                                followConnected = true,
                            )
                            val typeShape = constructedType.definition
                            val args = (2 until tree.size).map {
                                translateExpression(tree.child(it))
                            }
                            // We don't really support constructor overloading today and considering simplifying even
                            // more in the future, so just get the first constructor.
                            val method = typeShape.methods.find { it.methodKind == MethodKind.Constructor }
                            val connectedKey = method?.let {
                                val metadata = (it.stay?.incoming?.source as? DeclTree)?.parts?.metadataSymbolMap
                                val connectedKey = metadata?.get(connectedSymbol)?.target
                                    ?.valueContained(TString)
                                connectedKey
                            }

                            val connectedConstructor = connectedKey?.let {
                                supportNetwork.translateConnectedReference(
                                    constructorTree.pos,
                                    it,
                                    genre,
                                )
                            }
                            val constructorType = originalCalleeSig
                            val constructorPos = constructorTree.pos
                            val constructorCallee: TmpL.Callable =
                                // If it's a connected constructor, declare support code and reference that
                                connectedConstructor?.let { poolable ->
                                    val constructorName =
                                        pool.fillIfAbsent(constructorPos, poolable, constructorType, emptyMap())
                                    TmpL.FnReference(
                                        TmpL.Id(constructorPos, constructorName),
                                        constructorType,
                                    )
                                }
                                    // Otherwise, create a `new` callee
                                    ?: TmpL.ConstructorReference(
                                        pos = constructorPos,
                                        type = constructorType,
                                        typeName = typeName,
                                        method = method,
                                    )

                            // TODO: Where do we put type actuals
                            translation = maybeInline(
                                TmpL.CallExpression(
                                    pos = tree.pos,
                                    fn = constructorCallee,
                                    parameters = args,
                                    type = tree.typeOrInvalid,
                                ),
                            )
                        }
                    }
                    if (translation == null) {
                        translation = untranslatableExpr(tree.pos, tree.toLispy())
                    }
                }
                BuiltinFuns.pureVirtualFn -> {
                    // The Java backend relies on propagating the actual pure virtual function to detect
                    // whether an interface method should be `default` or `abstract`.
                    // Other backends can return a sensible "pure virtual" value, e.g. Python can return
                    // `NotImplemented`.
                }
                BuiltinFuns.await -> {
                    translation = TmpL.AwaitExpression(
                        tree.pos,
                        translateExpression(tree.child(1)),
                        tree.typeOrInvalid,
                    )
                }
                ErrorFn -> {
                    val message = buildString {
                        for (i in 1 until tree.size) {
                            val errorValue = tree.child(i).valueContained
                                ?: continue
                            if (this.isNotEmpty()) { append(" ; ") }
                            when {
                                errorValue.typeTag == TProblem ->
                                    append(TProblem.unpack(errorValue).messageText)
                                else ->
                                    errorValue.stringify(this, typeInfoIsRedundant = true)
                            }
                        }
                    }
                    translation = TmpL.GarbageExpression(
                        tree.pos,
                        if (message.isEmpty()) { null } else { TmpL.Diagnostic(tree.pos, message) },
                    )
                }
            }
        }

        if (translation == null) {
            val callable: TmpL.Callable = calleeTranslation ?: run findCalleeExpr@{
                var fnValue: MacroValue? = null
                var connectedKey: String? = null
                when (effectiveCallee) {
                    is ValueLeaf -> fnValue = TFunction.unpackOrNull(effectiveCallee.content)
                    is RightNameLeaf -> {
                        val calleeName = effectiveCallee.content
                        connectedKey = pool.sharedNameTables.declarationMetadataForName[calleeName]
                            ?.get(connectedSymbol)
                            ?.let { it: Value<*> ->
                                TString.unpackOrNull(it)
                            }
                    }
                    else -> {}
                }
                if (fnValue != null) {
                    connectedKey = genericComparisonHackaround(tree, fnValue)
                }

                var supportCode: SupportCode? = null
                if (connectedKey != null) {
                    supportCode = supportNetwork.translateConnectedReference(originalCallee.pos, connectedKey, genre)
                }
                if (fnValue != null && supportCode == null) {
                    supportCode =
                        supportNetwork.getSupportCode(effectiveCallee.pos, fnValue, genre)
                }
                if (supportCode is InlineSupportCode<*, *>) {
                    pool.poolRequirements(originalCallee.pos, supportCode)
                    return@findCalleeExpr TmpL.InlineSupportCodeWrapper(
                        pos = originalCallee.pos,
                        supportCode = supportCode,
                        type = originalCalleeSig,
                    )
                }

                val tentativeCalleeExpr: TmpL.Callable = when {
                    supportCode != null -> supportCodeReference(
                        supportCode, connectedKey, originalCallee.pos, originalCalleeSig, emptyMap(),
                    )
                    else -> translateCallable(effectiveCallee)
                }
                tentativeCalleeExpr
            }

            var actualCalleeType = effectiveCallee.sig
            val typeInferences = tree.typeInferences
            if (typeInferences?.bindings2?.isNotEmpty() == true) {
                actualCalleeType =
                    actualCalleeType?.mapType(
                        hackMapOldStyleActualsToNew(typeInferences.bindings2),
                    )
            }
            val declaredCalleeType = hackTryStaticTypeToSig(typeInferences?.variant)
            translation = TmpL.CallExpression(
                pos = tree.pos,
                fn = callable,
                typeActuals = translateCallTypeActuals(
                    pos = callable.pos.rightEdge,
                    typeActualTrees = typeActuals,
                    callInferences = tree.typeInferences,
                    sig = callable.type,
                ),
                parameters = (1 until tree.size).map { i ->
                    val arg = tree.child(i)
                    maybeInjectCastForInput(
                        expr = translateExpression(arg),
                        argIndex = i - 1,
                        actualCalleeType = actualCalleeType,
                        declaredCalleeType = declaredCalleeType,
                        adjustments = null,
                        builtinOperatorId = builtinOperatorId,
                    )
                },
                type = tree.typeInferences?.type?.let {
                    hackMapOldStyleToNewAllowNever(it)
                }.orInvalid,
            )

            translation = maybeInjectCastForOutput(
                expr = maybeInline(translation),
                actualCalleeType = effectiveCallee.sig,
                declaredCalleeType = declaredCalleeType,
                adjustments = null,
                builtinOperatorId = builtinOperatorId,
            )
        }

        return translation
    }

    /** Takes the trees inside the angle bracket operator and turns them into type actuals. */
    internal fun translateCallTypeActuals(
        pos: Position,
        typeActualTrees: List<Tree>,
        callInferences: CallTypeInferences?,
        sig: Signature2,
    ): TmpL.CallTypeActuals {
        val aTypes = mutableListOf<TmpL.AType>()
        val bindings = mutableMapOf<TypeFormal, Type2>()
        if (typeActualTrees.isNotEmpty()) {
            for ((i, typeTree) in typeActualTrees.withIndex()) {
                val typeFromTree = typeTree.reifiedTypeContained?.type2
                if (typeFromTree != null) {
                    aTypes.add(translateType(typeTree.pos, typeFromTree).aType)
                    sig.typeFormals.getOrNull(i)?.let { typeFormal ->
                        bindings[typeFormal] = typeFromTree
                    }
                } else {
                    aTypes.add(untranslatableType(typeTree.pos, "Missing actual type").aType)
                }
            }
        } else {
            val bindingsByTypeFormal = callInferences?.bindings2 ?: emptyMap()
            for (tf in sig.typeFormals) {
                val binding = bindingsByTypeFormal[tf]
                    ?.let { hackMapOldStyleToNew(it as StaticType) }
                    ?: WellKnownTypes.invalidType2
                bindings[tf] = binding
                // TODO: Do some backends benefit from implicit type actuals?
                // aTypes.add(translateType(pos, binding).aType)
            }
        }
        return TmpL.CallTypeActuals(
            pos = if (typeActualTrees.isNotEmpty()) {
                typeActualTrees.spanningPosition(typeActualTrees.first().pos)
            } else {
                pos
            },
            types = aTypes.toList(),
            bindings = bindings.toMap(),
        )
    }
    private fun translateGetStaticOp(
        tree: Tree,
        originalCalleeSig: Signature2,
        /**
         * If it's a call to a function like static, then we need to return a callable.
         * We could infer this from tree structure:
         * If it's a call, either the parent is the call or the parent is an application of `<>`, attaching
         * explicit type actuals to the callee.
         * But, the translation path needs to extract the required kind so this bit is passed in.
         */
        returnCallable: Boolean,
    ): TmpL.ExpressionOrCallable {
        if (tree.size != GETS_ARITY) {
            return if (returnCallable) {
                untranslatableCallable(tree.pos, "Malformed GetStatic")
            } else {
                untranslatableExpr(tree.pos, "Malformed GetStatic")
            }
        }

        val (_, typeChild, propertyChild) = tree.children
        val type = hackMapOldStyleToNewOrNull(typeChild.staticTypeContained)
        val definition = (type as? DefinedNonNullType)?.definition
        val property = propertyChild.symbolContained
        if (definition == null || property == null) {
            return if (returnCallable) {
                untranslatableCallable(tree.pos, "Malformed GetStatic")
            } else {
                untranslatableExpr(tree.pos, "Malformed GetStatic")
            }
        }

        val shape = definition.staticProperties
            .firstOrNull { it.symbol == property }
        val connectedKey = shape?.metadata?.get(connectedSymbol, TString)
        val typeIsConnected = definition.metadata[connectedSymbol, TString]
            ?.let { typeConnectedKey ->
                null != supportNetwork.translatedConnectedType(
                    definition.pos, typeConnectedKey, genre, type,
                )
            } ?: false
        val supportCode = connectedKey?.let {
            supportNetwork.translateConnectedReference(tree.pos, connectedKey, genre)
        }
        val isPulledOutStatic = shape != null && shouldPullOutMember(
            memberShape = shape,
            memberIsConnected = supportCode != null,
            typeIsConnected = typeIsConnected,
            isConstructor = false,
        )
        if (isPulledOutStatic) {
            // We have a static member that is not connected which was pulled
            // out when translating the containing type definition.
            @Suppress("SENSELESS_COMPARISON")
            check(shape != null)
            return if (returnCallable) {
                TmpL.FnReference(
                    TmpL.Id(tree.pos, shape.name as ResolvedName, null),
                    tree.sig!!,
                )
            } else {
                TmpL.Reference(
                    TmpL.Id(tree.pos, shape.name as ResolvedName, null),
                    tree.typeOrInvalid,
                )
            }
        }
        if (
            connectedKey != null &&
            // TODO: Connections for methods are handled above, but if a method
            // is not immediately called, we probably need to generate a shared
            // wrapper lambda because InlineSupportCode for methods are supposed
            // to receive the call arguments.
            fnSymbol !in shape.metadata
        ) {
            when (supportCode) {
                null -> {}
                is InlineSupportCode<*, *> -> {
                    return maybeInline(
                        TmpL.CallExpression(
                            pos = tree.pos,
                            fn = TmpL.InlineSupportCodeWrapper(
                                pos = tree.child(0).pos,
                                supportCode = supportCode,
                                type = originalCalleeSig,
                            ),
                            parameters = emptyList(),
                            type = tree.typeOrInvalid,
                        ),
                    )
                }
                else -> {
                    val sig = tree.sig ?: originalCalleeSig
                    val name = pool.fillIfAbsent(
                        tree.pos,
                        supportCode,
                        sig,
                        emptyMap(),
                        null,
                    )
                    return if (returnCallable) {
                        TmpL.FnReference(TmpL.Id(tree.pos, name), sig)
                    } else {
                        TmpL.Reference(TmpL.Id(tree.pos, name), tree.typeOrInvalid)
                    }
                }
            }
        }
        val subject = translateTypeName(
            typeChild.pos,
            type,
            // Non-connected statics still need to be put on a Temper generated type.
            followConnected = false,
        )
        val dotName = TmpL.DotName(
            propertyChild.pos,
            dotNameText = property.text,
        )

        return if (returnCallable) {
            val propertyShape = type.definition.staticProperties.firstOrNull {
                it.symbol == property
            }
            TmpL.MethodReference(
                pos = tree.pos,
                subject = subject,
                methodName = dotName,
                type = originalCalleeSig,
                method = propertyShape,
            )
        } else {
            TmpL.GetBackedProperty(
                pos = tree.pos,
                subject = subject,
                property = TmpL.ExternalPropertyId(dotName),
                type = tree.typeOrInvalid,
            )
        }
    }

    private fun translateRttiCall(
        fn: RttiCheckFunction,
        callTree: CallTree,
    ): TmpL.Expression? {
        val expr = callTree.child(1)
        val targetTree = callTree.child(2)

        val sourceType = expr.typeOrInvalid
        val sourceTypeNotNull = sourceType.withNullity(NonNull)

        val targetType = hackMapOldStyleToNewOrNull(targetTree.staticTypeContained)
            ?: return garbageExpr(targetTree.pos, "Expected type, not ${targetTree.toPseudoCode()}")
        if (fn.runtimeTypeOperation.asLike) {
            if (sourceType == targetType) {
                return translateExpression(expr)
            }
            if (targetType == sourceTypeNotNull) {
                // Casting away null.
                return TmpL.UncheckedNotNullExpression(callTree.pos, translateExpression(expr), targetType)
            }
        }

        // Add `typeTagType` so backends that need more explicit type arguments don't need to
        // repeat logic for both tmpl types and frontend types.
        // TODO Remove `typeTag` if all backends can use `typeTagType`?
        // TODO Verify that type args get through on `typeTagType`?
        // TODO If they don't, build it from `tree.typeOrInvalid` minus BubbleType?
        val sourceTmpLTypeNotNull = translateType(expr.pos, sourceTypeNotNull)
        val targetTmpLType = translateType(targetTree.pos, targetType)
        val pos = callTree.pos
        val rto = fn.runtimeTypeOperation

        val expression = translateExpression(expr)

        val supportCode = (sourceTmpLTypeNotNull as? TmpL.NominalType)?.let { st ->
            (targetTmpLType as? TmpL.NominalType)?.let { tt ->
                // The simplifyRttiCheck should set us up for success here.
                supportNetwork.translateRuntimeTypeOperation(pos, rto, st, tt)
            }
        }
        if (supportCode != null) {
            val returnType = when (rto) {
                RuntimeTypeOperation.As, RuntimeTypeOperation.AssertAs ->
                    MkType2(resultTypeDefinition)
                        .actuals(listOf(targetType, bubbleType2))
                        .get()
                RuntimeTypeOperation.Is -> booleanType2
            }
            val supportCodeType = Signature2(returnType, hasThisFormal = false, listOf(expression.type))

            val wrapper = supportCodeReference(supportCode, null, pos, supportCodeType, emptyMap())
            return maybeInline(
                TmpL.CallExpression(pos, wrapper, parameters = listOf(expression), type = returnType),
            )
        }

        val type = callTree.typeOrInvalid
        return when (rto) {
            RuntimeTypeOperation.As, RuntimeTypeOperation.AssertAs -> TmpL.CastExpression(
                pos = pos,
                expr = expression,
                checkedType = targetTmpLType.aType,
                type = type,
                checkedFrontendType = targetType,
            )
            RuntimeTypeOperation.Is -> TmpL.InstanceOfExpression(
                pos = pos,
                expr = expression,
                checkedType = targetTmpLType.aType,
                checkedFrontendType = targetType,
            )
        }
    }

    internal fun maybeInline(call: TmpL.CallExpression): TmpL.Expression {
        val fn = call.fn
        if (fn is TmpL.SupportCodeWrapper) {
            val supportCode = fn.supportCode
            if (supportCode is InlineTmpLSupportCode) {
                val parameters = call.parameters.map {
                    TypedArg<TmpL.Tree>(it, it.typeOrInvalid)
                }
                return supportCode.inlineToTree(call.pos, parameters, call.type, this)
            }
        }

        // If the callee is a reference to an inlinable support code reference, mark that.
        // This lets us elide unneeded SupportCodeDeclarations so that backends do not try to import
        // support code that they do not need.  The elision happens after translating the module set
        // in the outer module loop above.
        if (fn is TmpL.FnReference) {
            val supportCode = pool.getSupportCodeReferenceForName(fn.id.name)
            if (supportCode is InlineSupportCode<*, *>) {
                pool.poolRequirements(fn.pos, supportCode)
                return if (supportCode is InlineTmpLSupportCode) {
                    val parameters = call.parameters.map {
                        TypedArg<TmpL.Tree>(it, it.typeOrInvalid)
                    }
                    supportCode.inlineToTree(call.pos, parameters, call.type, this)
                } else {
                    TmpL.CallExpression(
                        pos = call.pos,
                        fn = TmpL.InlineSupportCodeWrapper(
                            fn.pos,
                            supportCode = supportCode,
                            type = fn.type,
                        ),
                        parameters = call.parameters.deepCopy(),
                        type = call.type,
                    )
                }
            }
        }

        return call
    }

    private fun translateFunctionDefinition(
        tree: FunTree,
        nameInPool: ResolvedName? = null,
    ): TranslatedFunction? {
        val parts = tree.parts ?: return null

        val id: TmpL.Id? = when (val word = parts.word) {
            null -> nameInPool?.let { TmpL.Id(tree.pos, it) }
            else -> {
                when (val e = translatePattern(word)) {
                    is TmpL.Id -> e
                    else ->
                        nameInPool?.let { TmpL.Id(tree.pos, it) } ?: untranslatableIdentifier(word)
                }
            }
        }

        return translateFunctionDefinitionFromParts(
            pos = tree.pos,
            id = id,
            fnParts = parts,
            sig = hackTryStaticTypeToSig(tree.typeInferences?.type)!!,
        )
    }

    private fun translateFunctionDefinitionFromParts(
        pos: Position,
        id: TmpL.Id?,
        fnParts: FnParts,
        sig: Signature2,
    ): TranslatedFunction? {
        // These are var because they are tentative until we've done
        // any unwrapping that we need to do
        val origFnType = sig
        var fnSig = sig

        var mayYield = fnParts.mayYield ?: false

        var isWrappedCoro = false
        // If we're going to have to rewrap the coroutine because we need to convert
        // it to a state machine, the adapter to use.
        var coroAdapter: AdaptGeneratorFn? = null
        // If this function is just a wrapper for a coroutine, we might need to collapse
        // the wrapper into the coroutine.
        val (bodyTree: BlockTree, returnDecl: DeclTree?) = run unwrapCoro@{
            if (!mayYield) {
                val bodyTree = fnParts.body
                val returnDecl = fnParts.returnDecl
                if (returnDecl != null) {
                    maybeUnwrapCoroutine(bodyTree, returnDecl)
                        ?.let { (unwrapped, adapter) ->
                            val unwrappedParts = unwrapped.parts
                            if (unwrappedParts != null) {
                                mayYield = true
                                isWrappedCoro = true
                                if (coroutineStrategy == CoroutineStrategy.TranslateToRegularFunction) {
                                    coroAdapter = adapter
                                }
                                fnSig = hackTryStaticTypeToSig(unwrapped.typeInferences?.type)!!
                                return@unwrapCoro ensureIsBlock(unwrappedParts.body) to unwrappedParts.returnDecl
                            }
                        }
                }
            }
            null
        } ?: Pair(
            ensureIsBlock(fnParts.body),
            when (genre) {
                Genre.Library -> fnParts.returnDecl
                // Functions in Genre.Documentation should have their own explicit `return`s.
                Genre.Documentation -> null
            },
        )

        val (returnType, returnTemperType, wrappedReturnType) = run {
            val typePos = fnParts.returnDecl?.pos ?: pos.leftEdge

            fun retTypeForFn(sigT: Signature2?, returnDecl: DeclTree?): Type2 {
                // If the type from the Typer contains a function type, use its return type.
                // This prefers any declared return type.
                return sigT?.returnType2
                    // Otherwise, look at the return decl if we have one.
                    ?: hackMapOldStyleToNewOrNull(returnDecl?.parts?.name?.typeInferences?.type)
                    ?: WellKnownTypes.invalidType2
            }

            val type = retTypeForFn(fnSig, returnDecl)
            val retType = translateType(typePos, type)

            // If we have unwrapped a coroutine to get at the body, but will need to
            // re-wrap it, then make sure we have that type too.
            if (mayYield) {
                val unwrappedType = retTypeForFn(origFnType, fnParts.returnDecl)
                Triple(translateType(typePos, unwrappedType), unwrappedType, type to retType)
            } else {
                Triple(retType, type, null)
            }
        }
        val pureVirtualCall = extractPureVirtualCall(fnParts.body)

        val formals = fnParts.formals
        val typeParameters = mutableListOf<TmpL.TypeFormal>()
        val params = mutableListOf<TmpL.Formal>()
        val bodyParts = mutableListOf<TmpL.Statement>()

        // If we need a return statement, use the declared return variable as the
        // place to store it.
        val output = when (returnDecl) {
            null -> null
            else -> {
                val returnParts = returnDecl.parts ?: return null
                val outputName = returnParts.name
                if (
                    cfOptions.representationOfVoid == RepresentationOfVoid.ReifyVoid ||
                    !hasVoidLikeType(outputName) ||
                    mayYield
                ) {
                    returnDecl to returnParts
                } else {
                    null
                }
            }
        }
        val outputName = output?.second?.name?.content as ResolvedName?

        for ((_, typeFormal) in fnParts.typeFormals) {
            val tfPos = typeFormal?.pos ?: continue
            typeParameters.add(
                TmpL.TypeFormal(
                    pos = tfPos,
                    name = TmpL.Id(tfPos, typeFormal.name),
                    upperBounds = typeFormal.upperBounds.map {
                        translateNominalType(
                            tfPos.rightEdge,
                            hackMapOldStyleToNew(it) as DefinedNonNullType,
                        )
                    },
                    definition = typeFormal,
                ),
            )
        }

        // Languages that don't reify Void typically don't need an explicit
        // `return` statement at the end of their function body.
        val needsReturnAtEnd = output != null && // See void-like check
            pureVirtualCall == null &&
            !mayYield // Return is superfluous with yield or generated by coro converter

        var thisName: TmpL.Id? = null
        val restFormal: TmpL.RestFormal? = fnParts.restFormal?.let {
            val name: TmpL.Id =
                when (val d = translateDeclaration(it.tree, allowFunctionDecl = false)) {
                    is TranslatedDeclaration -> d.declaredName
                    is UntranslatableDeclaration ->
                        TmpL.Id(it.position, unusedName(ParsedName("fail")))
                    is NoDeclarationNeeded -> error("always needed when allowFunctionDecl is false")
                }
            val metadata = translateDeclarationMetadata(
                it.tree.parts?.metadataSymbolMap ?: emptyMap(),
            )
            val descriptor = hackMapOldStyleToNew(it.type)
            TmpL.RestFormal(
                it.position,
                metadata, name,
                type = translateType(it.position, descriptor).aType,
                descriptor = MkType2(WellKnownTypes.listTypeDefinition)
                    .actuals(listOf(descriptor))
                    .get(),
            )
        }

        // Convert formals. We also need to interleave any copying of temporary parameter names in to the local
        // parameter name. TODO Any examples of when temporary names apply?
        for (formal in formals) {
            val d = translateDeclaration(formal, allowFunctionDecl = false)
            val parts = formal.parts!!
            val metadataMap = parts.metadataSymbolMap
            val thisEdge = metadataMap[impliedThisSymbol]
            val name: TmpL.Id
            val type: TmpL.AType
            val descriptor: Type2
            if (d is TranslatedDeclaration) {
                check(d.isSimple) // Since function declarations not allowed
                val localDecl = d.declaringStatement as TmpL.LocalDeclaration
                name = d.declaredName.deepCopy()
                type = localDecl.type
                descriptor = localDecl.descriptor
            } else {
                name = untranslatableIdentifier(formal)
                type = untranslatableType(formal.pos, "missing type info").aType
                descriptor = WellKnownTypes.invalidType2
            }
            val optional = parts.isOptional
            val assignOnce = varSymbol !in metadataMap
            val metadata = translateDeclarationMetadata(metadataMap)
            params.add(
                TmpL.Formal(
                    pos = formal.pos,
                    metadata = metadata,
                    name = name,
                    type = type.deepCopy(),
                    assignOnce = assignOnce,
                    optionalState = optional,
                    descriptor = descriptor,
                ),
            )
            if (thisEdge != null && parts.name.content in thisNames) {
                thisName = TmpL.Id(thisEdge.target.pos, name.name)
            }
        }
        // TODO: how does this do anything?  Is there anything on bodyParts at this point?
        combineRedundantAdjacentStatements(bodyParts)

        val offsetForReturnStatement = if (needsReturnAtEnd) {
            bodyParts.size
        } else {
            null
        }

        var bodyReturned = false
        class TranslateFunctionExit : GoalTranslator {
            override val translator: TmpLTranslator get() = this@TmpLTranslator
            override val supportNetwork: SupportNetwork get() = this@TmpLTranslator.supportNetwork
            override fun translateExit(p: Position): Stmt {
                bodyReturned = true
                return OneStmt(
                    TmpL.ReturnStatement(
                        p,
                        when (output) {
                            null -> null
                            else -> {
                                val outputNameLeaf = output.second.name
                                val rightName = outputNameLeaf.copyRight()
                                rightName.typeInferences = outputNameLeaf.typeInferences
                                translator.translateExpression(rightName)
                            }
                        },
                    ),
                )
            }

            override fun translateFreeFailure(p: Position): Stmt = OneStmt(
                when (nrbStrategy) {
                    BubbleBranchStrategy.IfHandlerScopeVar -> TmpL.ReturnStatement(p, TmpL.BubbleSentinel(p))
                    BubbleBranchStrategy.CatchBubble -> TmpL.ThrowStatement(p)
                },
            )

            override fun translateJump(p: Position, kind: BreakOrContinue, target: JumpSpecifier): Stmt =
                DefaultGoalTranslator(translator).translateJump(p, kind, target)
        }

        var convertYields = false
        if (mayYield) {
            check(isWrappedCoro)
            // If it were not unwrapped, we would need to insert a call around
            // it to preserve semantics
            when (coroutineStrategy) {
                CoroutineStrategy.TranslateToRegularFunction -> {
                    mayYield = false // We delete the yields in the conversion below.
                    convertYields = true
                }
                CoroutineStrategy.TranslateToGenerator -> {}
            }
        }
        val preTranslatedBody = if (pureVirtualCall != null) {
            PreTranslated.TreeWrapper(pureVirtualCall)
        } else {
            translateFlow(
                tree = bodyTree,
                // Make sure `return` and other goals do the right thing.
                goalTranslator = TranslateFunctionExit(),
                nameMaker = mergedNameMaker,
                options = cfOptions,
                outputName = if (convertYields) {
                    // We need to distinguish assignments to the unused return decl
                    // so that we can reuse `return` for `yield`.
                    output?.second?.name?.content
                } else {
                    null
                },
                stateMachineConversionType = if (convertYields) {
                    returnTemperType
                } else {
                    null
                },
            )
        }
        if (preTranslatedBody is PreTranslated.ConvertedCoroutine) {
            preTranslatedBody.persistentDeclarations.flatMapTo(bodyParts) {
                it.toStatement(this).asStmtList()
            }
            var bodyBlock = preTranslatedBody.body.toStatement(this).asBlock()
            if (preTranslatedBody.variablesToNullAdjust.isNotEmpty()) {
                bodyBlock = nullAdjustConvertedCoroutineBody(bodyBlock, preTranslatedBody.variablesToNullAdjust)
            }
            val innerFnName = TmpL.Id(bodyBlock.pos.leftEdge, unusedName(ParsedName("convertedCoroutine")))
            val (innerFnReturnTemperType, innerFnReturnType) = wrappedReturnType!!
            val generatorType = sig.returnType2
            val innerFnSig = Signature2(
                returnType2 = innerFnReturnTemperType,
                hasThisFormal = false,
                requiredInputTypes = listOf(generatorType),
            )

            val currentGeneratorParam = run {
                val paramPos = bodyBlock.pos.leftEdge
                val metadata = emptyList<TmpL.DeclarationMetadata>()
                TmpL.Formal(
                    paramPos,
                    metadata,
                    TmpL.Id(paramPos, preTranslatedBody.generatorName),
                    translateType(paramPos, returnTemperType).aType,
                    returnTemperType,
                )
            }

            bodyParts.add(
                TmpL.LocalFunctionDeclaration(
                    pos = bodyBlock.pos,
                    metadata = emptyList(),
                    name = innerFnName,
                    typeParameters = TmpL.ATypeParameters(
                        TmpL.TypeParameters(bodyBlock.pos.leftEdge, emptyList()),
                    ),
                    parameters = TmpL.Parameters(
                        bodyBlock.pos.leftEdge,
                        null,
                        listOf(currentGeneratorParam),
                        null,
                    ),
                    returnType = innerFnReturnType.aType,
                    mayYield = false, // yields erased,
                    sig = innerFnSig,
                    body = bodyBlock,
                ),
            )
            bodyReturned = true
            val returnPos = bodyBlock.pos.rightEdge
            val adaptFn: TmpL.Callable = when (
                val supportCode = supportNetwork.getSupportCode(returnPos, coroAdapter!!, genre)
            ) {
                null -> TmpL.GarbageCallable(
                    TmpL.Diagnostic(
                        returnPos,
                        "Backend converts coroutines but does not support $coroAdapter",
                    ),
                )
                else -> supportCodeReference(
                    supportCode,
                    null,
                    returnPos,
                    coroAdapter.sig,
                    mapOf(),
                )
            }
            val innerFnType = hackMapOldStyleToNew(
                // TODO: We need to define a functional interface for converted coroutines, or just a Fn1
                MkType.fn(
                    listOf(),
                    innerFnSig.allValueFormals.map { hackMapNewStyleToOld(it.type) },
                    null,
                    hackMapNewStyleToOld(innerFnSig.returnType2),
                ),
            )
            bodyParts.add(
                TmpL.ReturnStatement(
                    returnPos,
                    maybeInline(
                        TmpL.CallExpression(
                            returnPos,
                            adaptFn,
                            parameters = listOf(
                                // TODO: use a functional interface type.
                                TmpL.Reference(returnPos, innerFnName.deepCopy(), innerFnType),
                            ),
                            type = returnTemperType,
                        ),
                    ),
                ),
            )
        } else {
            val preTranslated = if (mayYield && outputName != null) {
                simplifyGeneratorFnReturns(preTranslatedBody, outputName)
            } else {
                preTranslatedBody
            }
            bodyParts.addAll(preTranslated.toStatement(this).asStmtList())
        }

        if (!bodyReturned && offsetForReturnStatement != null) {
            // Wrap a body in `let return__123; ...; return return__123` unless all
            // paths throw.
            // This fits well with most languages' static need-return checks without
            // falling afoul of illegal-dead-code checks.
            if (canControlLeaveStatements(bodyParts)) {
                // Put the declaration of the return-holding variable at the front
                bodyParts.addAll(
                    offsetForReturnStatement,
                    translateStatement(output!!.first).stmtList,
                )

                bodyParts.addAll(
                    TranslateFunctionExit().translateExit(bodyTree.pos.rightEdge).stmtList,
                )
            }
        }
        simplifyFunctionBodyParts(bodyParts)
        val bodyBlock = TmpL.BlockStatement(bodyTree.pos, bodyParts.toList())

        check(!mayYield || coroutineStrategy == CoroutineStrategy.TranslateToGenerator)
        return TranslatedFunction(
            pos = pos,
            id = id,
            typeParameters = TmpL.ATypeParameters(
                TmpL.TypeParameters(
                    id?.pos?.rightEdge ?: pos.leftEdge,
                    typeParameters.toList(),
                ),
            ),
            parameters = TmpL.Parameters(
                pos,
                thisName = thisName,
                parameters = params.toList(),
                restParameter = restFormal,
            ),
            returnType = returnType,
            body = bodyBlock,
            mayYield = mayYield,
            sig = sig,
        )
    }

    internal fun translateType(tree: Tree): TmpL.Type {
        val typeFromTree = tree.reifiedTypeContained?.type2
        return if (typeFromTree != null) {
            translateType(tree.pos, typeFromTree)
        } else {
            untranslatableType(tree.pos, "Cannot typify ${tree.toLispy()}")
        }
    }

    private val typeTranslator = TypeTranslator(supportNetwork, genre, ::untranslatableType)

    internal fun translateType(pos: Position, type: Type2) =
        typeTranslator.translateType(pos, type)

    internal fun translateNominalType(pos: Position, type: DefinedNonNullType) =
        typeTranslator.translateType(pos, type) as TmpL.NominalType

    private fun translateTypeName(pos: Position, type: DefinedNonNullType, followConnected: Boolean) =
        typeTranslator.translateTypeName(pos, type, followConnected = followConnected)

    private fun translateDotHelperCall(callTree: CallTree): StmtOrExpr =
        translateDotHelperCall(
            callTree,
            callTree.child(0),
            null,
            emptyList(),
        )

    private fun translateDotHelperCall(
        callTree: CallTree,
        callee: Tree,
        outerCallTree: CallTree?,
        typeActuals: List<Tree>,
    ): StmtOrExpr {
        val dotTranslation = TranslateDotHelper.translate(
            callTree = callTree,
            callee = callee,
            outerCallTree = outerCallTree,
            typeActuals = typeActuals,
            translator = this,
        )
        return when (val t = dotTranslation.translation) {
            is Either.Left -> OneExpr(
                maybeInjectCastForOutput(
                    expr = t.item,
                    actualCalleeType = dotTranslation.actualCalleeType,
                    declaredCalleeType = dotTranslation.declaredCalleeType,
                    adjustments = dotTranslation.adjustments,
                    builtinOperatorId = null,
                ),
            )
            is Either.Right -> OneStmt(t.item)
        }
    }

    private fun translateHandlerScopeCallByStrategy(tree: Tree) = when (nrbStrategy) {
        BubbleBranchStrategy.IfHandlerScopeVar -> translateHandlerScopeCall(tree as CallTree)
        // Account for caveat in .operationTried in TmpLControlFlow.
        BubbleBranchStrategy.CatchBubble -> translateExpression(tree.child(2))
    }

    private fun translateHandlerScopeCall(callTree: CallTree): TmpL.HandlerScope {
        check(isHandlerScopeCall(callTree))
        val failedTree = callTree.child(1) as LeftNameLeaf
        val failedName = failedTree.content as ResolvedName

        val handledTree = callTree.child(2)
        val handled = if (isDotHelperCall(handledTree)) {
            when (val translation = translateDotHelperCall(handledTree as CallTree)) {
                is OneExpr -> translation.expr
                is OneStmt -> when (val stmt = translation.stmt) {
                    is TmpL.ExpressionStatement -> stmt.expression
                    is TmpL.Handled -> stmt
                    else -> null
                }
                else -> null
            } ?: garbageExpr(handledTree.pos, "unhandleable dot operation")
        } else {
            translateExpression(handledTree)
        }

        return TmpL.HandlerScope(
            callTree.pos,
            TmpL.Id(failedTree.pos, failedName),
            handled,
        )
    }

    private fun translatedEmbeddedComment(tree: Tree): Stmt {
        val commentText = TString.unpack((tree.child(1) as ValueLeaf).content)
        // Make sure that backends that translate to line comments do not need to check
        // for line breaks themselves.
        val lines = commentText.replace(anyLineBreak, "\n").split("\n")
        val pos = tree.pos
        val posRight = pos.rightEdge
        return Stmts(
            pos,
            lines.mapIndexed { index, s ->
                // Reduce confusion between comment lines and
                // she-bangs or pre-processor directives, so that
                //     // !/usr/bin/echo
                // does not get naively mapped to
                //     #!/usr/bin/echo
                val sSafe = hashWord.replace(s, "$1_$2")
                TmpL.EmbeddedComment(if (index == 0) { pos } else { posRight }, sSafe)
            },
        )
    }

    private fun untranslatableStmt(tree: Tree): TmpL.Statement {
        logCannotTranslate(tree, Either.Left(tree), logSink)
        return TmpL.GarbageStatement(
            tree.pos,
            TmpL.Diagnostic(tree.pos, tree.toLispy()),
        )
    }

    private fun untranslatableCallable(tree: Tree): TmpL.GarbageCallable {
        logCannotTranslate(tree, Either.Left(tree), logSink)
        return TmpL.GarbageCallable(
            tree.pos,
            TmpL.Diagnostic(tree.pos, tree.toLispy()),
        )
    }

    private fun untranslatableExpr(tree: Tree): TmpL.GarbageExpression {
        logCannotTranslate(tree, Either.Left(tree), logSink)
        return TmpL.GarbageExpression(
            tree.pos,
            TmpL.Diagnostic(tree.pos, tree.toLispy()),
        )
    }

    private fun untranslatableIdentifier(tree: Tree): TmpL.Id {
        logCannotTranslate(tree, Either.Left(tree), logSink)
        return TmpL.Id(
            tree.pos,
            unusedName(ParsedName("untranslatable")),
        )
    }

    internal fun untranslatableCallable(pos: Position, diagnostic: String): TmpL.GarbageCallable {
        logCannotTranslate(pos, Either.Right(diagnostic), logSink)
        return TmpL.GarbageCallable(
            TmpL.Diagnostic(pos, diagnostic),
        )
    }

    internal fun untranslatableExpr(pos: Position, diagnostic: String): TmpL.GarbageExpression {
        logCannotTranslate(pos, Either.Right(diagnostic), logSink)
        return TmpL.GarbageExpression(
            TmpL.Diagnostic(pos, diagnostic),
        )
    }

    internal fun untranslatableStmt(pos: Position, diagnostic: String): TmpL.GarbageStatement {
        logCannotTranslate(pos, Either.Right(diagnostic), logSink)
        return TmpL.GarbageStatement(
            TmpL.Diagnostic(pos, diagnostic),
        )
    }

    private fun untranslatableTopLevel(pos: Position, diagnostic: String): TmpL.GarbageTopLevel {
        logCannotTranslate(pos, Either.Right(diagnostic), logSink)
        return TmpL.GarbageTopLevel(
            TmpL.Diagnostic(pos, diagnostic),
        )
    }

    private fun untranslatableType(pos: Position, diagnostic: String) = untranslatableType(logSink)(pos, diagnostic)

    /** Whether the value should be stored in the global constant pool. */
    private fun shouldPool(value: Value<*>): Boolean {
        return when (value.typeTag) {
            TBoolean, TInt, TInt64, TFloat64, TString, TNull, TType, TVoid -> false
            TFunction -> when (val f = TFunction.unpack(value)) {
                is DotHelper -> false
                is BuiltinStatelessMacroValue -> true
                is LongLivedUserFunction ->
                    !f.hasYielded && f.closedOverEnvironment is EmptyEnvironment
                is CoverFunction -> f.covered.all { shouldPool(Value(it)) }
                else -> false
            }
            is TClass -> false
            TProblem, TList, TListBuilder, TMap, TMapBuilder, TStageRange, TSymbol, TClosureRecord -> false
        }
    }

    /**
     * As part of pooling this enforces the output ordering
     */
    internal fun translateValueForPool(
        pos: Position,
        nameInPool: ResolvedName,
        pooledValue: PooledValue,
        metadata: MetadataMap,
    ): TmpL.TopLevel {
        val value = pooledValue.value
        when (val typeTag = value.typeTag) {
            TFunction -> { // TODO: TType
                when (val fn = TFunction.unpack(value)) {
                    is NamedBuiltinFun -> {
                        val supportCode = supportNetwork.getSupportCode(pos, fn, genre)
                        return declareSupportCode(
                            pos, nameInPool, supportCode, pooledValue.type as Signature2,
                            metadata, fn.name,
                        )
                    }
                    is LongLivedUserFunction -> {
                        if (!fn.hasYielded && fn.closedOverEnvironment is EmptyEnvironment) {
                            val stayLeaf = fn.stayLeaf
                            val nameForStay = pool.nameForStay(stayLeaf)
                            if (nameForStay != null) {
                                @Suppress("KotlinUnreachableCode")
                                return TmpL.ModuleLevelDeclaration(
                                    pos,
                                    metadata = translateDeclarationMetadata(metadata),
                                    name = TmpL.Id(pos, nameInPool),
                                    init = translateExpression(RightNameLeaf(document, pos, nameForStay)),
                                    assignOnce = true,
                                    type = translateType(pos, TODO("${typeFromSignature(fn.signature)}")).aType,
                                    descriptor = TODO("${typeFromSignature(fn.signature)}"),
                                )
                            }
                            val funTree = stayLeaf.incoming?.source as? FunTree
                            if (funTree != null) {
                                val tf = translateFunctionDefinition(funTree, nameInPool)
                                if (tf != null) {
                                    return TmpL.ModuleFunctionDeclaration(
                                        pos = pos,
                                        metadata = translateDeclarationMetadata(
                                            metadata,
                                            funMetadata = funTree.parts?.metadataSymbolMap,
                                        ),
                                        name = tf.id!!,
                                        typeParameters = TmpL.ATypeParameters(
                                            TmpL.TypeParameters(pos, emptyList()),
                                        ),
                                        parameters = tf.parameters,
                                        returnType = tf.returnType.aType,
                                        body = tf.body,
                                        mayYield = tf.mayYield,
                                        sig = tf.sig,
                                    )
                                }
                            }
                        }
                        // TODO: We need to translate the factory, and then retrofit it with
                        // extra state, so that we can produce a value that resumes.
                    }
                    else -> Unit
                }
                return untranslatableTopLevel(pos, "value $value")
            }
            TBoolean, TFloat64, TInt, TInt64, TNull, TStageRange, TString, TSymbol, TType, TVoid -> {
                val type = when (typeTag) {
                    TBoolean, TFloat64, TInt, TInt64, TNull, TStageRange, TString, TSymbol, TType, TVoid ->
                        value.typeBestEffort!!
                    TFunction, is TClass, TClosureRecord, TList, TListBuilder, TMap, TMapBuilder,
                    TProblem, TType,
                    -> error("handled elsewhere in the parent when")
                }
                return TmpL.PooledValueDeclaration(
                    pos = pos,
                    metadata = translateDeclarationMetadata(metadata),
                    name = TmpL.Id(pos, nameInPool),
                    init = TmpL.ValueReference(pos, type, value),
                    descriptor = type,
                )
            }
            // TODO: pool stable lists, tuples, and class instances
            is TClass -> when {
                value === globalConsole -> {
                    // This value is the global console. Customize handling on it for now.
                    // TODO This code actually isn't used anymore, but keep it for example usage of other things.
                    // TODO When is this better off removed?
                    val connectedKey = consoleParsedName.nameText
                    val supportCode = supportNetwork.translateConnectedReference(pos, connectedKey, genre)
                    return declareSupportCode(
                        pos, nameInPool, supportCode, pooledValue.type as Signature2, metadata, connectedKey,
                    )
                }
                else -> {}
            }
            TClosureRecord, TList, TListBuilder, TMap, TMapBuilder -> Unit
            TProblem -> return untranslatableTopLevel(pos, TProblem.unpack(value).messageText)
        }

        return untranslatableTopLevel(pos, toStringViaTokenSink { value.renderTo(it) })
    }

    internal fun supportCodeReference(
        supportCode: SupportCode,
        connectedKey: String?,
        pos: Position,
        type: Signature2,
        metadata: MetadataMap,
    ): TmpL.FnReference {
        val poolable = PooledSupportCode(supportCode, moduleIndex)
        val name = pool.nameFor(poolable)
            ?: run {
                val nameInPool = unusedName(baseNameFor(poolable))
                val supportCodeDecl = declareSupportCode(pos, nameInPool, supportCode, type, metadata, connectedKey)
                topLevels.add(supportCodeDecl)
                nameInPool
            }
        return TmpL.FnReference(pos, TmpL.Id(pos, name, null), type)
    }

    private fun declareSupportCode(
        pos: Position,
        nameInPool: ResolvedName,
        supportCode: SupportCode?,
        type: Signature2,
        metadata: MetadataMap,
        connectedName: String?,
    ): TmpL.TopLevel {
        return if (supportCode != null) {
            val supportCodeRef = wrapSupportCode(pos, supportCode, type)
            val declaration = TmpL.SupportCodeDeclaration(
                pos = pos,
                metadata = translateDeclarationMetadata(metadata),
                name = TmpL.Id(pos, nameInPool),
                init = supportCodeRef,
                descriptor = type,
            )
            pool.associateSupportCode(declaration)
            declaration
        } else {
            untranslatableTopLevel(
                pos,
                "builtin $connectedName not supported by ${supportNetwork.backendDescription}",
            )
        }
    }

    internal fun wrapSupportCode(
        pos: Position,
        supportCode: SupportCode,
        descriptor: Signature2,
    ): TmpL.SupportCodeWrapper {
        pool.poolRequirements(pos, supportCode)
        return if (supportCode is InlineSupportCode<*, *>) {
            TmpL.InlineSupportCodeWrapper(pos, descriptor, supportCode)
        } else {
            TmpL.SimpleSupportCodeWrapper(pos, descriptor, supportCode)
        }
    }
}

/**
 * If there's some kind of complicated return control flow going on during module initialization,
 * we need a label around the init block contents that we can `break` to.
 *
 * This walks over the top levels, making sure that there is a single init block wrapped in the
 * given label.
 */
private fun labelTopLevelInitBlocks(
    pos: Position,
    label: TmpL.Id,
    topLevels: List<TmpL.TopLevel>,
): List<TmpL.TopLevel> {
    val minInitBlockIndex = topLevels.indexOfFirst { it is TmpL.ModuleInitBlock }
    if (minInitBlockIndex < 0) {
        return topLevels
    }
    val maxInitBlockIndex = topLevels.indexOfLast { it is TmpL.ModuleInitBlock }
    require(maxInitBlockIndex >= minInitBlockIndex)
    val before = topLevels.subList(0, minInitBlockIndex)
    val after = topLevels.subList(maxInitBlockIndex + 1, topLevels.size)
    val middleUnlabeled = topLevels.subList(minInitBlockIndex, maxInitBlockIndex + 1)

    // Group init block content and other content together so that we can construct a single init
    // block that subsumes the others and which is labeled.
    val middle = mutableListOf<TmpL.TopLevel>()
    val initBlocks = mutableListOf<TmpL.ModuleInitBlock>()
    for (topLevel in middleUnlabeled) {
        when (topLevel) {
            is TmpL.ModuleInitBlock -> initBlocks.add(topLevel)
            is TmpL.GarbageTopLevel,
            is TmpL.BoilerplateCodeFoldBoundary,
            is TmpL.EmbeddedComment,
            is TmpL.TypeDeclaration,
            is TmpL.TypeConnection,
            is TmpL.PooledValueDeclaration,
            is TmpL.SupportCodeDeclaration,
            is TmpL.ModuleFunctionDeclaration,
            is TmpL.Test,
            -> middle.add(topLevel)
            is TmpL.ModuleLevelDeclaration -> when (val init = topLevel.init) {
                null -> middle.add(topLevel)
                else -> {
                    // Convert to an assignment in the init block.
                    middle.add(topLevel)
                    topLevel.init = null
                    initBlocks.add(
                        TmpL.ModuleInitBlock(
                            init.pos,
                            body = TmpL.BlockStatement(
                                init.pos,
                                listOf(
                                    TmpL.Assignment(
                                        init.pos,
                                        topLevel.name.deepCopy(),
                                        init,
                                        init.type,
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }
    check(initBlocks.isNotEmpty())

    // Now construct that block
    val initBlockPos = initBlocks.spanningPosition(pos)
    middle.add(
        TmpL.ModuleInitBlock(
            initBlockPos,
            body = TmpL.BlockStatement(
                initBlockPos,
                listOf(
                    TmpL.LabeledStatement(
                        initBlockPos,
                        TmpL.JumpLabel(label),
                        TmpL.BlockStatement(
                            initBlockPos,
                            initBlocks.flatMap {
                                it.body.takeBody()
                            },
                        ),
                    ),
                ),
            ),
        ),
    )

    return before + middle + after
}

private sealed class TranslatedDeclarationResult

private data object NoDeclarationNeeded : TranslatedDeclarationResult()

private class UntranslatableDeclaration(val reason: String) : TranslatedDeclarationResult()

private class TranslatedDeclaration(
    val declaredName: TmpL.Id,
    val declaringStatement: TmpL.Statement,
) : TranslatedDeclarationResult() {
    val isSimple get() = declaringStatement is TmpL.LocalDeclaration

    val stmtList: List<TmpL.Statement> get() = listOf(declaringStatement)
}

internal fun isAssignmentCall(tree: Tree): Boolean {
    if (tree !is CallTree || tree.size != BINARY_OP_CALL_ARG_COUNT) {
        return false
    }
    val callee = tree.childOrNull(0)
    if (callee !is ValueLeaf) { return false }
    return callee.content == BuiltinFuns.vSetLocalFn && tree.child(1) is LeftNameLeaf
}

internal fun isVoidLikeAssignment(tree: Tree) = isAssignmentCall(tree) && hasVoidLikeType(tree)

internal fun dotHelperFromCallOrNull(tree: Tree): DotHelper? =
    (tree as? CallTree)?.childOrNull(0)?.functionContained as? DotHelper

internal fun isDotHelperCall(tree: Tree): Boolean =
    dotHelperFromCallOrNull(tree) != null

internal fun isSetterCall(tree: Tree): Boolean =
    dotHelperFromCallOrNull(tree)?.memberAccessor is SetMemberAccessor

private class TranslatedFunction(
    val pos: Position,
    val id: TmpL.Id?,
    val typeParameters: TmpL.ATypeParameters,
    val parameters: TmpL.Parameters,
    val returnType: TmpL.Type,
    val body: TmpL.BlockStatement,
    val mayYield: Boolean,
    val sig: Signature2,
)

private const val GETP_ARITY = 3 // callee and (symbol, obj)
internal const val GETS_ARITY = 3 // callee and (type, symbol)
private const val AS_ARITY = 3 // callee and (expr, reifiedType)

private fun (TmpLTranslator).groupImportsIn(
    topLevels: List<TmpL.TopLevel>,
): Pair<List<TmpL.LibraryDependency>, List<TmpL.Import>> {
    // Keep a list of libraries that we depend upon because of support code.
    val requiredLibraries = mutableSetOf<DashedIdentifier>()

    // Find the local names so we know what not to import.
    val locallyDeclared = buildSet {
        addAll(pool.sharedNameTables.pooledToName.values)
        fun findLocalNames(t: TmpL.Tree) {
            if (t is TmpL.NameDeclaration) {
                (t.name.name as? ModularName)?.let { mn -> add(mn) }
            }
            t.children.forEach { findLocalNames(it) }
        }
        topLevels.forEach(::findLocalNames)
    }

    // Find all the exported names from other libraries
    val externalNames = mutableMapOf<ModularName, Position>()
    val reachabilities = mutableMapOf<QName, TmpL.DeclarationMetadata>()
    fun lookForNames(t: TmpL.Tree) {
        fun allowDefinition(definition: TypeDefinition): Boolean =
            // Backends should assume well known types are ambiently available
            definition is TypeShape && !WellKnownTypes.isWellKnown(definition)

        val name = when (t) {
            is TmpL.Id -> if (t.parent is TmpL.JumpLabel) {
                null // not importable
            } else {
                t.name
            }
            is TmpL.TemperTypeName -> {
                val definition = t.typeDefinition
                if (allowDefinition(definition)) definition.name else null
            }
            is TmpL.SupportCodeWrapper -> {
                t.supportCode.requires.mapNotNullTo(requiredLibraries) {
                    when (it) {
                        is LibrarySupportCodeRequirement -> it.libraryName
                        is OtherSupportCodeRequirement -> null // Handled by constant pool
                    }
                }
                null
            }
            is TmpL.ModuleLevelDeclaration -> {
                // Extract reachability metadata from decls.
                // TODO See if we actually have any imported names that don't appear in decls?
                val imported = t.metadata.find { it.key.symbol == importedSymbol }?.value
                if (imported != null) {
                    t.metadata.find { it.key.symbol == reachSymbol }?.let { reachability ->
                        val qName = (imported as TmpL.NameData).qName
                        reachabilities[qName] = reachability
                    }
                }
                // No export name tracking for this node itself.
                null
            }
            else -> null
        }
        if (name is ModularName) {
            if (name !in externalNames && name !in locallyDeclared) {
                externalNames[name] = t.pos
            }
        }
        t.children.forEach { lookForNames(it) }
    }
    for (topLevel in topLevels) {
        lookForNames(topLevel)
    }

    data class ExternalNameGroup(
        val loc: ModuleLocation,
        val configuration: LibraryConfiguration?,
        val namesAndPositions: List<Triple<ModularName, Position, TmpL.DeclarationMetadata?>>,
    )

    // Group them by module and reachability.
    val externalNamesGroups = buildListMultimap {
        for ((externalName, pos) in externalNames) {
            val libraryName = libraryConfigurations.definingName(externalName)?.libraryName
                ?: continue
            val loc = externalName.origin.loc
            val qNameMap = dependencyResolver.getQNameMap(libraryName)
            val qName = qNameMap[externalName]
            val reachability = reachabilities[qName]
            val reachabilitySymbol =
                reachability?.let { TSymbol.unpack((reachability.value as TmpL.ValueData).value) }
            putMultiList(loc to reachabilitySymbol, Triple(externalName, pos, reachability))
        }
    }.map { (locReachability, namesAndPositions) ->
        val (loc, _) = locReachability
        val libraryRoot = (loc as? ModuleName)?.libraryRoot()
        val libraryConfig = libraryConfigurations.byLibraryRoot[libraryRoot]
        ExternalNameGroup(loc, libraryConfig, namesAndPositions)
    }.sortedBy {
        // Sort by library name.  It's just nice to see imports sorted that way
        it.configuration?.libraryName
    }

    val reqPos = topLevels.firstOrNull()?.pos?.leftEdge
    requiredLibraries.remove(libraryConfigurations.currentLibraryConfiguration.libraryName)
    val libraryReqs = requiredLibraries.sortedBy { it.text }
        .map {
            // !! is safe because if there's a requirement it must've been inside a top-level
            TmpL.LibraryDependency(reqPos!!, it)
        }

    // TODO Why do we group just to flat map? Did this used to matter? Do we still rely on order?
    val imports = externalNamesGroups.flatMap { (_, _, namesAndPositions) ->
        namesAndPositions.map { (externalName, pos, reachability) ->
            TmpL.Import(
                pos,
                externalName = TmpL.Id(pos, externalName, null),
                localName = null,
                // Filled in later when *Backend* moves from tentative TmpL to finished TmpL
                sig = null,
                path = null,
                // Sig also might have metadata later, but that comes from the definition, not the import.
                metadata = reachability?.let { listOf(it) } ?: emptyList(),
            )
        }
    }

    return libraryReqs to imports
}

private fun isAngleBracketApplication(t: Tree) =
    t is CallTree && t.size >= 2 && (t.child(0) as? ValueLeaf)?.content == BuiltinFuns.vAngleFn

internal val Value<*>.typeBestEffort: Type2? get() =
    typeTagToStaticType[typeTag]

// www.unicode.org/standard/reports/tr13/tr13-5.html#Definitions
val anyLineBreak = Regex("""\r\n?|[\n\u000B\u000C\u0085\u2028\u2029]""")

// Allow converting comments with things that look like pre-processor directive
// to things that don't.
private val hashWord = Regex("^(#+!*)([a-zA-Z])")

val Tree?.typeOrInvalid: Type2 get() = this?.typeInferences?.type?.let { hackMapOldStyleToNew(it) }
    ?: WellKnownTypes.invalidType2

val Tree?.sig: Signature2? get() = this?.typeInferences?.type?.let { hackTryStaticTypeToSig(it) }

internal fun logCannotTranslate(
    positioned: Positioned,
    fault: Either<Tree, String>,
    logSink: LogSink,
) {
    logSink.log(
        level = Log.Error,
        template = MessageTemplate.CannotTranslate,
        pos = positioned.pos,
        values = listOf(
            when (fault) {
                is Either.Left -> fault.item.toLispy(multiline = false)
                is Either.Right -> fault.item
            },
        ),
    )
}

internal fun untranslatableType(logSink: LogSink): (Position, String) -> TmpL.GarbageType =
    { pos, diagnostic ->
        logCannotTranslate(pos, Either.Right(diagnostic), logSink)
        TmpL.GarbageType(TmpL.Diagnostic(pos, diagnostic))
    }

private fun stayForValue(value: Value<*>): StayLeaf? {
    val fn = TFunction.unpackOrNull(value)
    if (fn != null) {
        if (fn is LongLivedUserFunction) {
            return fn.stayLeaf
        }
    }
    return null
}

private fun exportedNameForStay(
    stayLeaf: StayLeaf,
    selfLoc: ModuleLocation,
): ExportedName? {
    // If a function value or other value that has a StayLeaf, is being
    // referenced across module boundaries, look for the canonical name
    // by which it was exported, so that we can rewrite the name to a local
    // name after the imports are gathered.
    // See FinishTmpLImports for where that happens.

    val parent = stayLeaf.incoming?.source ?: return null
    var canonName: TemperName? = null
    if (parent is FunTree) {
        val grandparent = parent.incoming?.source
        if (
            grandparent != null && isAssignmentCall(grandparent) &&
            grandparent.edge(2) == parent.incoming
        ) {
            canonName = (grandparent.child(1) as? LeftNameLeaf)?.content
        }
    }
    if (canonName is ExportedName) {
        val loc = canonName.origin.loc
        if (loc != selfLoc) {
            // If it's not part of the current module
            // then we could import and link to it.
            return canonName
        }
    }

    return null
}

private val TmpL.MemberOrGarbage.neededIfEnclosingTypeIsConnected: Boolean
    get() {
        if (this is TmpL.Method) {
            val memberShape = this.memberShape
            if (memberShape is MethodShape && memberShape.isPureVirtual) {
                return false
            }
        }
        val visibility = when (this) {
            is TmpL.Garbage -> return true // Do not mask translation errors
            is TmpL.Constructor -> return true
            // Properties are not needed if the getters and setters are connected
            // visible and the type itself is connected.
            is TmpL.InstanceProperty -> return false
            is TmpL.Getter -> visibility
            is TmpL.Setter -> visibility
            is TmpL.NormalMethod -> visibility
            is TmpL.StaticMethod -> visibility
            is TmpL.StaticProperty -> visibility
        }
        return visibility.visibility != TmpL.Visibility.Private
    }

// Metadata translation.

private fun translateDeclarationMetadata(
    sourceLibrary: DashedIdentifier,
    metadataMap: MetadataMap,
    qNameMap: Map<ResolvedName, QName>,
): List<TmpL.DeclarationMetadata> = translateDeclarationMetadataSeries(
    buildList {
        metadataMap.forEach { (key, edge) ->
            val datum = datumFor(sourceLibrary, key, edge, qNameMap)
            if (datum != null) { add(datum) }
        }
        sortBy { it.key.text }
    },
)

internal fun translateDeclarationMetadataValueMultimap(
    sourceLibrary: DashedIdentifier,
    metadata: MetadataValueMultimap,
    qNameMap: Map<ResolvedName, QName>,
): List<TmpL.DeclarationMetadata> = translateDeclarationMetadataSeries(
    buildList {
        metadata.keys.forEach { key ->
            val edges = metadata.getEdges(key)
            edges.forEach {
                val datum = datumFor(sourceLibrary, key, it, qNameMap)
                if (datum != null) { add(datum) }
            }
        }
    },
)

private data class Metadatum(
    val sourceLibrary: DashedIdentifier,
    val key: Symbol,
    val valueOrName: Either<Value<*>, QName>,
)

private fun translateDeclarationMetadataSeries(
    data: Iterable<Metadatum>,
) = data.mapNotNull { (sourceLibrary, keySymbol, valueOrName) ->
    // Skip metadata that is available via other avenues
    if (keySymbol in typeMemberMetadataSymbols) { return@mapNotNull null }
    when (keySymbol) {
        connectedSymbol, fromTypeSymbol, fnSymbol,
        parameterNameSymbolsListSymbol, resolutionSymbol, returnedFromSymbol,
        ssaSymbol, staticSymbol, typeFormalSymbol,
        typeSymbol, typePlaceholderSymbol,
        varSymbol, visibilitySymbol, wordSymbol,
        ->
            return@mapNotNull null
        else -> Unit
    }
    val value: TmpL.MetadataValue = when (valueOrName) {
        is Either.Left -> TmpL.ValueData(
            sourceLibrary = sourceLibrary,
            value = valueOrName.item,
        )
        is Either.Right -> TmpL.NameData(sourceLibrary, valueOrName.item)
    }
    TmpL.DeclarationMetadata(
        sourceLibrary = sourceLibrary,
        key = TmpL.MetadataKey(sourceLibrary, keySymbol),
        value = value,
    )
}

private fun datumFor(
    sourceLibrary: DashedIdentifier,
    key: Symbol,
    edge: TEdge,
    qNameMap: Map<ResolvedName, QName>,
): Metadatum? {
    val tree = when (val target = edge.target) {
        is EscTree -> target.childOrNull(0) as? NameLeaf // extract any escaped name
        is ValueLeaf -> target
        else -> null
    } ?: return null
    val valueOrName = when (tree) {
        is ValueLeaf -> Either.Left(tree.content)
        is NameLeaf -> qNameMap[(tree.content as? ResolvedName)]?.let { Either.Right(it) }
        else -> null
    } ?: return null
    return Metadatum(sourceLibrary, key, valueOrName)
}

private fun ensureIsBlock(functionBody: Tree): BlockTree {
    if (functionBody is BlockTree) { return functionBody }
    val incoming = functionBody.incoming!!
    incoming.replace {
        Block(functionBody.pos) {
            Replant(freeTree(functionBody))
        }
    }
    return incoming.target as BlockTree
}

private fun extractPureVirtualCall(tree: Tree): CallTree? {
    if (tree is BlockTree && tree.size == 1) {
        return extractPureVirtualCall(tree.child(0))
    }
    if (isAssignmentCall(tree)) {
        return extractPureVirtualCall(tree.child(2))
    }
    if (tree is CallTree) {
        val callee = tree.childOrNull(0)
        if (callee?.functionContained == BuiltinFuns.pureVirtualFn) {
            return tree
        }
    }
    return null
}

private fun genericComparisonHackaround(
    call: CallTree,
    callee: MacroValue,
): String? {
    if (call.size != BINARY_OP_CALL_ARG_COUNT) { return null }
    // This hack is equivalent to the one in generic comparison that connects StringIndex
    // comparison to StringIndexOption.compareTo.
    // Here, we rewrite to a method application so that our normal connection machinery
    // can specialize it.
    val builtinOperatorId = (callee as? NamedBuiltinFun)?.builtinOperatorId
        ?: return null
    val (_, leftArg, rightArg) = call.children
    val leftType = hackMapOldStyleToNewOrNull(leftArg.typeInferences?.type)
    val rightType = hackMapOldStyleToNewOrNull(rightArg.typeInferences?.type)
    if (leftType.isStringIndexOptionType && rightType.isStringIndexOptionType) {
        // Turn generic comparison operations on StringIndex and StringIndexOption and NoStringIndex into
        when (builtinOperatorId) {
            BuiltinOperatorId.LtGeneric -> return "StringIndexOption::compareTo::lt"
            BuiltinOperatorId.LeGeneric -> return "StringIndexOption::compareTo::le"
            BuiltinOperatorId.GeGeneric -> return "StringIndexOption::compareTo::ge"
            BuiltinOperatorId.GtGeneric -> return "StringIndexOption::compareTo::gt"
            BuiltinOperatorId.EqGeneric -> return "StringIndexOption::compareTo::eq"
            BuiltinOperatorId.NeGeneric -> return "StringIndexOption::compareTo::ne"
            BuiltinOperatorId.CmpGeneric -> return "StringIndexOption::compareTo"
            else -> {}
        }
    }
    return null
}

private val Type2?.isStringIndexOptionType get() =
    // TODO: does not account for <T extends StringIndexOption>
    this?.nullity == NonNull && this.definition in stringIndexOptionTypes
private val stringIndexOptionTypes = setOf(
    WellKnownTypes.stringIndexOptionTypeDefinition,
    WellKnownTypes.stringIndexTypeDefinition,
    WellKnownTypes.noStringIndexTypeDefinition,
)

private fun <BE : Backend<BE>> MetadataDependencyResolver<BE>.getQNameMap(
    libraryName: DashedIdentifier,
): Map<ResolvedName, QName> {
    val qNameKey = QNameMapping.key<BE>(backend.backendId)
    return getMetadata(libraryName, qNameKey) ?: emptyMap()
}

val Type2?.orInvalid get() = this ?: WellKnownTypes.invalidType2
val Signature2?.orInvalid get() = this ?: invalidSig

val invalidSig = invalidSig

/** The signature for processed `test { ... }` blocks */
val testSig = Signature2(
    returnType2 = WellKnownTypes.voidType2,
    hasThisFormal = false,
    requiredInputTypes = listOf(), // TODO: do we need a test object passed in?
)

private val emptyCallType = CallTypeInferences(
    WellKnownTypes.emptyType,
    MkType.fn(listOf(), listOf(), null, WellKnownTypes.emptyType),
    mapOf(),
    listOf(),
)
