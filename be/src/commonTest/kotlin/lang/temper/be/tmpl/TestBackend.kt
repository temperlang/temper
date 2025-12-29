package lang.temper.be.tmpl

import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutTree
import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.BaseOutTree
import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.common.MimeType
import lang.temper.common.asciiTitleCase
import lang.temper.common.console
import lang.temper.common.putMultiList
import lang.temper.format.CodeFormatter
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattingHints
import lang.temper.format.OperatorDefinition
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.fs.ResourceDescriptor
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.name
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeNullAndBubble
import lang.temper.type2.DefinedType
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.toLispy

internal open class TestBackend(
    override val supportNetwork: TestSupportNetwork,
    setup: BackendSetup<TestBackend>,
) : Backend<TestBackend>(tmpLBackendId, setup) {
    override fun tentativeTmpL(): TmpL.ModuleSet =
        try {
            TmpLTranslator.translateModules(
                logSink,
                readyModules,
                supportNetwork,
                libraryConfigurations = libraryConfigurations,
                dependencyResolver = dependencyResolver,
                tentativeOutputPathFor = { module ->
                    allocateTextFile(module, EXTENSION)
                },
            )
        } catch (e: NotImplementedError) {
            console.group("Error translating") {
                for (module in readyModules) {
                    console.error(module.loc.diagnostic)
                    console.error(
                        module.generatedCode?.toLispy(multiline = true)
                            ?: "<no generated code>",
                    )
                }
            }
            throw e
        }

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        // Coherence checks for generated TmpL
        run {
            val hasOutOfScopeDeclarations = finished.modules.filter { useOutOfScopeOfDeclaration(it) }
            check(hasOutOfScopeDeclarations.isEmpty()) {
                hasOutOfScopeDeclarations.joinToString("\n\n") {
                    it.toString(singleLine = false)
                }
            }
        }

        return finished.modules.map { module ->
            val outFile = module.codeLocation.outputPath
            TranslatedFileSpecification(outFile, mimeType = mimeType, module)
        }
    }

    companion object {
        const val EXTENSION = ".tmpl"
        val mimeType = MimeType("text", "x-tmpl")
        val backendId = tmpLBackendId
    }

    open class TestFactory : Backend.Factory<TestBackend> {
        override val backendId: BackendId = Companion.backendId
        override val backendMeta: BackendMeta = BackendMeta(
            backendId = tmpLBackendId,
            languageLabel = LanguageLabel("tmpl"),
            fileExtensionMap = mapOf(FileType.Module to EXTENSION),
            mimeTypeMap = mapOf(FileType.Module to mimeType),
        )
        override val specifics: RunnerSpecifics get() = error("Only a test backend")
        override val coreLibraryResources: List<ResourceDescriptor> = emptyList()

        override fun make(setup: BackendSetup<TestBackend>): TestBackend =
            error("Only a test backend")
    }

    object Factory : TestFactory()
}

internal open class TestSupportNetwork(
    override val bubbleStrategy: BubbleBranchStrategy = BubbleBranchStrategy.IfHandlerScopeVar,
    override val coroutineStrategy: CoroutineStrategy = CoroutineStrategy.TranslateToGenerator,
    private val representationOfVoid: RepresentationOfVoid = RepresentationOfVoid.ReifyVoid,
    override val functionTypeStrategy: FunctionTypeStrategy = FunctionTypeStrategy.ToFunctionType,
    override val needsLocalNameForExternallyDefinedFunction: Boolean = false,
    override val needsLocalNameForExternallyDefinedType: Boolean = false,
    override val needsLocalNameForExternallyDefinedValue: Boolean = false,
    override val mayAssignInBothTryAndRecover: Boolean = true,
    val isConnected: (String) -> Boolean = { true },
) : SupportNetwork {
    override val backendDescription: String = "TmpL Test Backend"

    fun copy(
        bubbleStrategy: BubbleBranchStrategy = this.bubbleStrategy,
        coroutineStrategy: CoroutineStrategy = this.coroutineStrategy,
        representationOfVoid: RepresentationOfVoid = this.representationOfVoid,
        needsLocalNameForExternallyDefinedFunction: Boolean = this.needsLocalNameForExternallyDefinedFunction,
        needsLocalNameForExternallyDefinedType: Boolean = this.needsLocalNameForExternallyDefinedType,
        needsLocalNameForExternallyDefinedValue: Boolean = this.needsLocalNameForExternallyDefinedValue,
        mayAssignInBothTryAndRecover: Boolean = this.mayAssignInBothTryAndRecover,
        isConnected: (String) -> Boolean = this.isConnected,
    ): TestSupportNetwork = TestSupportNetwork(
        bubbleStrategy = bubbleStrategy,
        coroutineStrategy = coroutineStrategy,
        representationOfVoid = representationOfVoid,
        needsLocalNameForExternallyDefinedFunction = needsLocalNameForExternallyDefinedFunction,
        needsLocalNameForExternallyDefinedType = needsLocalNameForExternallyDefinedType,
        needsLocalNameForExternallyDefinedValue = needsLocalNameForExternallyDefinedValue,
        mayAssignInBothTryAndRecover = mayAssignInBothTryAndRecover,
        isConnected = isConnected,
    )

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode {
        if (builtin.builtinOperatorId == BuiltinOperatorId.BooleanNegation) {
            return BooleanNegationTestSupportCode
        }
        val sigs = builtin.sigs
        val soleSig = if (sigs?.size == 1) {
            sigs[0] as? Signature2
        } else {
            null
        }
        return TestSupportCode(ParsedName(builtin.name), soleSig)
    }

    override fun optionalSupportCode(optionalSupportCodeKind: OptionalSupportCodeKind) =
        TestOptionalSupportCode(optionalSupportCodeKind) to Signature2(WellKnownTypes.anyValueType2, false, listOf())

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? {
        if (!isConnected(connectedKey)) {
            return null
        }
        val baseName = ParsedName(
            connectedKey.split("::")
                .joinToString("") { it.asciiTitleCase() },
        )
        return when (connectedKey) {
            "Int32::toString" -> IntToStringAsTmpL
            "List::length" -> InlineTestSupportCode(baseName = baseName)
            // Pretend we're a call on a test instance even if we hack around that here for now.
            "Test::assert" -> InlineTestSupportCode(baseName = ParsedName("assert_true"))
            else -> TestSupportCode(baseName = baseName, signature = null)
        }
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? {
        if (!isConnected(connectedKey)) {
            return null
        }
        return TestTargetLanguageTypeName(connectedKey) to
            if (temperType is DefinedType) {
                temperType.bindings
            } else {
                emptyList()
            }
    }

    override fun representationOfVoid(genre: Genre): RepresentationOfVoid = representationOfVoid

    data class TestTargetLanguageTypeName(
        val connectedKey: String,
    ) : TargetLanguageTypeName {
        override fun renderTo(tokenSink: TokenSink) {
            tokenSink.name(ParsedName(connectedKey), inOperatorPosition = false)
        }
    }

    /**
     * Inserts boxing operation when going from a type named \*Boxes to a more general type
     * and unboxing operations when going from a general type to a special type named \*Boxes.
     */
    override fun maybeInsertImplicitCast(
        fromActualType: Type2,
        fromDeclaredType: Type2,
        fromAdjustment: SignatureAdjustments.SignatureAdjustment?,
        toActualType: Type2,
        toDeclaredType: Type2,
        toAdjustment: SignatureAdjustments.SignatureAdjustment?,
        builtinOperatorId: BuiltinOperatorId?,
    ): SupportCode? {
        fun boxes(t: Type2): Boolean {
            val simpler = excludeNullAndBubble(t)
            return when (val definition = simpler.definition) {
                is TypeFormal -> definition.superTypes.any { boxes(hackMapOldStyleToNew(it)) }
                is TypeShape ->
                    true ==
                        (definition.name as? ResolvedParsedName)
                            ?.baseName?.nameText
                            ?.endsWith("Boxes")
            }
        }
        val needsBoxing = boxes(toActualType) && !boxes(toDeclaredType)
        val needsUnboxing = boxes(fromActualType) && !boxes(fromDeclaredType)
        return when {
            needsBoxing == needsUnboxing -> null
            needsBoxing -> TestSupportCode(ParsedName("box"), null)
            else -> TestSupportCode(ParsedName("unbox"), null)
        }
    }
}

private data class TestSupportCode(
    override val baseName: ParsedName,
    val signature: Signature2?,
) : NamedSupportCode {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("builtins", OutputTokenType.Word))
        tokenSink.emit(OutToks.dot)
        tokenSink.emit(baseName.toToken(inOperatorPosition = false))
        if (signature != null) {
            tokenSink.emit(OutputToken.makeSlashStarComment("$signature"))
        }
    }
}

private object BooleanNegationTestSupportCode : NamedSupportCode, FunctionSupportCode, InlineTmpLSupportCode {
    override val baseName = ParsedName("!")

    override val needsThisEquivalent: Boolean = false

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<TmpL.Tree>>,
        returnType: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression {
        if (arguments.size == 1) {
            val operand = arguments.first().expr as? TmpL.Expression
            if (operand != null) {
                return TmpL.PrefixOperation(
                    pos,
                    TmpL.PrefixOperator(
                        pos.leftEdge,
                        TmpLOperator.Bang,
                    ),
                    operand,
                )
            }
        }
        return TmpL.GarbageExpression(TmpL.Diagnostic(pos, "Wrong arguments to $this"))
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.prefixBang)
    }
}

private data class InlineTestSupportCode(
    override val baseName: ParsedName,
    override val needsThisEquivalent: Boolean = true,
) : NamedSupportCode, FunctionSupportCode, InlineSupportCode<StubOutputTree, Unit> {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(baseName.toToken(inOperatorPosition = false))
    }

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<StubOutputTree>>,
        returnType: Type2,
        translator: Unit,
    ) =
        StubOutputTree(pos)
}

private object IntToStringAsTmpL : InlineTmpLSupportCode {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<TmpL.Tree>>,
        returnType: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression =
        TmpL.InfixOperation(
            pos,
            TmpL.ValueReference(pos.leftEdge, Value("", TString)),
            // Backends can examine argument types to ensure type safety.
            TmpL.InfixOperator(pos.leftEdge, TmpLOperator.PlusInt),
            arguments[0].expr as TmpL.Expression,
        )

    override val needsThisEquivalent: Boolean
        get() = true

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("Int", OutputTokenType.Word))
        tokenSink.emit(OutputToken("::", OutputTokenType.Punctuation))
        tokenSink.emit(OutputToken("toString", OutputTokenType.Word))
    }
}

private class StubOutputTree(pos: Position) : BaseOutTree<StubOutputTree>(pos) {
    override fun formattingHints(): FormattingHints =
        FormattingHints.Default
    override val childMemberRelationships: ChildMemberRelationships
        get() = cmr

    override fun deepCopy(): OutTree<StubOutputTree> = StubOutputTree(pos)

    override val codeFormattingTemplate: CodeFormattingTemplate
        get() = CodeFormattingTemplate.fromFormatString("stub")

    override val operatorDefinition: OperatorDefinition?
        get() = null

    companion object {
        private val cmr = ChildMemberRelationships(emptyList())
    }
}

internal data class TestOptionalSupportCode(
    val optionalSupportCodeKind: OptionalSupportCodeKind,
) : SeparatelyCompiledSupportCode {
    override val source: DashedIdentifier get() = DashedIdentifier.temperCoreLibraryIdentifier
    override val stableKey: ParsedName = ParsedName(optionalSupportCodeKind.name)

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(stableKey.toToken(inOperatorPosition = false))
    }
}

internal val tmpLBackendId = BackendId("tmpL")

private fun forEachTree(t: TmpL.Tree, f: (TmpL.Tree) -> Unit) {
    f(t)
    for (childIndex in 0 until t.childCount) {
        forEachTree(t.child(childIndex), f)
    }
}

/**
 * [translateFlow] in `TmpLControlFlow` tends to wrap code in blocks like
 *
 *     s_123: {
 *       ...; break s_123; ...
 *     }
 *
 * These labeled blocks are introduced as a result of decompiling weaved control flows into
 * structural code.
 *
 * But these blocks should not separate declarations of local variables from their uses.
 *
 * @return true if any uses of locals is separate from the scope of its declaration.
 */
private fun useOutOfScopeOfDeclaration(root: TmpL.Module): Boolean {
    val decls = mutableListOf<TmpL.NameDeclaration>()
    forEachTree(root) {
        when (it) {
            is TmpL.LocalDeclaration -> decls.add(it)
            is TmpL.TypeDeclaration -> decls.add(it)
            else -> {}
        }
    }
    val nameToDecl = decls.associateBy { it.name.name }
    val usesByName =
        mutableMapOf<ResolvedName, MutableList<TmpL.Id>>()
    forEachTree(root) {
        if (it is TmpL.Id && it.name in nameToDecl && it.parent !is TmpL.OriginalName) {
            usesByName.putMultiList(it.name, it)
        }
    }

    var allInScope = true
    for ((name, uses) in usesByName) {
        val decl = nameToDecl.getValue(name)
        val scope = when (val parent = decl.parent) {
            is TmpL.TypeDeclaration -> parent.parent
            else -> parent
        }
        for (use in uses) {
            var inScope = false
            var t: TmpL.Tree? = use
            while (t != null) {
                if (t === scope) {
                    inScope = true
                    break
                }
                t = t.parent as TmpL.Tree?
            }
            if (!inScope) {
                fun renderOneLine(x: OutTree<TmpL.Tree>?): String =
                    "$x".replace("\n", " \\ ")
                console.error(
                    "${renderOneLine(use)} @ ${use.pos} in ${
                        renderOneLine(use.parent?.parent ?: use.parent)
                    } is not in scope of ${renderOneLine(decl)}",
                )
                allInScope = false
            }
        }
    }

    if (!allInScope) {
        console.log(
            toStringViaTokenSink(singleLine = false) {
                CodeFormatter(it).format(root)
            },
        )
    }

    return !allInScope
}
