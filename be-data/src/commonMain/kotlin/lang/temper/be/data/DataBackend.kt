package lang.temper.be.data

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.CommandNotConfigured
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.data.DataBackend.Factory.BACKEND_ID
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ComputedJumpStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.TypedArg
import lang.temper.common.Log
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.format.TokenSink
import lang.temper.fs.OutDir
import lang.temper.fs.ResourceDescriptor
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogEntry
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.LanguageLabel
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.declareDataFileSymbol

/**
 * <!-- snippet: backend/data -->
 * # Data Backend
 *
 * Outputs data files defined via [snippet/builtin/dataFile].
 *
 * ⎀ backend/data/id
 */
class DataBackend private constructor(
    setup: BackendSetup<DataBackend>,
) : Backend<DataBackend>(backendId = Factory.backendId, setup) {
    private val outRoot = dirPath(libraryConfigurations.currentLibraryConfiguration.libraryName.text)

    override fun tentativeTmpL(): TmpL.ModuleSet = TmpLTranslator.translateModules(
        logSink,
        readyModules,
        supportNetwork,
        tentativeOutputPathFor = { outRoot },
        libraryConfigurations = libraryConfigurations,
        dependencyResolver = dependencyResolver,
    )

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val translations = buildList<OutputFileSpecification> {
            for (mod in finished.modules) {
                for (md in mod.moduleMetadata.metadata) {
                    val pos = mod.moduleMetadata.pos
                    if (md.key.symbol == declareDataFileSymbol) {
                        val (path: FilePath?, mimeType: MimeType?, data: String?) = run unpack@{
                            val ls = TList.unpackOrNull((md.value as? TmpL.ValueData)?.value)
                            if (ls?.size == NUM_DATA_FILE_DECORATION_PARTS) {
                                val (pathStr, mimeTypeStr, data) = ls.map {
                                    TString.unpackOrNull(it)
                                }
                                var path: FilePath? = null
                                if (pathStr.isNullOrEmpty()) {
                                    logSink.log(DataBackendMessage.ExpectedPath, pos, listOf(ls[0]))
                                } else {
                                    var base = mod.codeLocation.codeLocation.relativePath()
                                    val partStrs = pathStr.split('/').toMutableList()
                                    if (partStrs.first() == "") { // Started with /
                                        base = FilePath.emptyPath
                                        partStrs.removeFirst()
                                    }
                                    val parts = mutableListOf<FilePathSegmentOrPseudoSegment>()
                                    var partErr: LogEntry? = null
                                    for (part in partStrs) {
                                        when (part) {
                                            "." -> parts.add(SameDirPseudoFilePathSegment)
                                            ".." -> parts.add(ParentPseudoFilePathSegment)
                                            "" -> {
                                                partErr = LogEntry(
                                                    DataBackendMessage.InvalidFilePathSegment,
                                                    pos,
                                                    listOf(part, pathStr),
                                                )
                                            }
                                            else -> parts.add(FilePathSegment(part))
                                        }
                                    }
                                    if (partErr != null) {
                                        partErr.logTo(logSink)
                                    } else {
                                        val resolved = base.resolvePseudo(parts, isDir = false)
                                        if (resolved != null) {
                                            path = resolved
                                        } else {
                                            logSink.log(
                                                DataBackendMessage.CannotResolveFilePath,
                                                pos,
                                                listOf(parts.join(isDir = false), base),
                                            )
                                        }
                                    }
                                }
                                var mimeType: MimeType? = null
                                when (val r = mimeTypeStr?.let { MimeType.parse(it) }) {
                                    is RSuccess -> { mimeType = r.result }
                                    null -> logSink.log(
                                        DataBackendMessage.ExpectedMimeType,
                                        pos,
                                        listOf(ls[1]),
                                    )
                                    is RFailure -> logSink.log(
                                        DataBackendMessage.BadMimeType,
                                        pos,
                                        listOf(mimeTypeStr, r.failure),
                                    )
                                }
                                return@unpack Triple(path, mimeType, data)
                            }
                            Triple(null, null, null)
                        }

                        if (path != null && mimeType != null && data != null) {
                            add(
                                MetadataFileSpecification(
                                    path,
                                    mimeType,
                                    data,
                                ),
                            )
                        } else {
                            logSink.log(DataBackendMessage.MalformedDataFileDeclaration, pos, listOf())
                        }
                    }
                }
            }
        }

        return translations
    }

    override val supportNetwork: SupportNetwork = DataSupportNetwork

    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    @PluginBackendId(BACKEND_ID)
    data object Factory : Backend.Factory<DataBackend> {
        /**
         * <!-- snippet: backend/data/id -->
         * BackendID: `data`
         */
        const val BACKEND_ID = "data"

        override val backendId = BackendId(BACKEND_ID)
        override val backendMeta: BackendMeta = BackendMeta(
            languageLabel = LanguageLabel("data"),
            backendId = backendId,
            fileExtensionMap = mapOf(),
            mimeTypeMap = mapOf(),
        )

        override val specifics: RunnerSpecifics get() = DoNothingDataSpecifics

        override val coreLibraryResources: List<ResourceDescriptor> = listOf()

        override val processCoreLibraryResourcesNeeded = false

        override fun make(setup: BackendSetup<DataBackend>): Backend<DataBackend> = DataBackend(setup)
    }
}

internal enum class DataBackendMessage(
    override val formatString: String,
    override val suggestedLevel: Log.Level = Log.Error,
) : LeveledMessageTemplate {
    MalformedDataFileDeclaration("be-data found malformed data file declaration"),
    ExpectedMimeType("be-data expected mime type string, got %s"),
    BadMimeType("be-data could not parse mime type string `%s`: %s"),
    ExpectedPath("be-data expected path string, got %s"),
    InvalidFilePathSegment("be-data found invalid file path segment `%s` in `%s`"),

    /** Too many `..` segments. */
    CannotResolveFilePath("be-data could not resolve path `%s` relative to `%s`"),
}

internal object DoNothingDataSpecifics : RunnerSpecifics {
    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> = RFailure(CommandNotConfigured())

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> = listOf() // Not much of an effort tbh

    override val tools: List<ToolSpecifics> = listOf()
    override val backendId: BackendId = DataBackend.Factory.backendId
}

/**
 * We don't run a translator, so there's nothing that actually uses this.
 */
internal object DataSupportNetwork : SupportNetwork {
    override val backendDescription: String
        get() = "data backend"
    override val bubbleStrategy: BubbleBranchStrategy
        get() = BubbleBranchStrategy.Exceptions // why not?
    override val coroutineStrategy: CoroutineStrategy
        get() = CoroutineStrategy.TranslateToGenerator // sure
    override val functionTypeStrategy: FunctionTypeStrategy
        get() = FunctionTypeStrategy.ToFunctionType // arrow types are neat
    override val computedJumpStrategy: ComputedJumpStrategy
        get() = ComputedJumpStrategy.NeverUse

    override fun representationOfVoid(genre: Genre): RepresentationOfVoid = RepresentationOfVoid.ReifyVoid

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode = DoNotCareSupportCode

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode = DoNotCareSupportCode

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>> = DoNotCareTypeName to temperType.bindings
}

private const val NUM_DATA_FILE_DECORATION_PARTS = 3 // path, mime type, data

private data object DoNotCareSupportCode : InlineSupportCode<Nothing, Nothing> {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("doNotCare")
    }

    override val needsThisEquivalent: Boolean
        get() = false

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Nothing>>,
        returnType: Type2,
        translator: Nothing,
    ): Nothing {
        error("Should not be called")
    }
}

private data object DoNotCareTypeName : TargetLanguageTypeName {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("doNotCare")
    }
}
