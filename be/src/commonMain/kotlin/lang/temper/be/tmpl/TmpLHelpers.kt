package lang.temper.be.tmpl

import lang.temper.ast.anyChildDepth
import lang.temper.be.Backend
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.format.TokenSink
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleNamingContext
import lang.temper.lexer.Genre
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.library.LibraryConfigurations
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.helpfulFromMetadataValue
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormal2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.mapType
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.DependencyCategory
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.Value
import lang.temper.value.docStringSymbol
import lang.temper.value.noneSymbol
import lang.temper.value.qNameSymbol
import lang.temper.value.reachSymbol
import lang.temper.value.testSymbol
import lang.temper.value.typeDeclSymbol

internal fun dotNameMatchesName(dotName: TmpL.DotName, name: TmpL.Id): Boolean =
    dotName.dotNameText == (name.name as? SourceName)?.baseName?.nameText

internal fun dotNameToOutputToken(dotName: TmpL.DotName) =
    dotNameToOutputToken(dotName.dotNameText)

internal fun dotNameToOutputToken(dotNameText: String) =
    ParsedName(dotNameText).toToken(inOperatorPosition = false)

fun garbageExpr(pos: Position, msg: String) =
    TmpL.GarbageExpression(pos, TmpL.Diagnostic(pos, msg))

fun garbageCallable(pos: Position, msg: String) =
    TmpL.GarbageCallable(pos, TmpL.Diagnostic(pos, msg))

fun nontranslatableExpr(pos: Position, msg: String) =
    TmpL.GarbageExpression(pos, TmpL.Diagnostic(pos, "Nontranslatable: $msg"))

fun garbageStatement(pos: Position, msg: String) =
    TmpL.GarbageStatement(pos, TmpL.Diagnostic(pos, msg))

fun garbageTopLevel(pos: Position, msg: String) =
    TmpL.GarbageTopLevel(pos, TmpL.Diagnostic(pos, msg))

fun garbageModule(
    pos: Position,
    sourceLibrary: DashedIdentifier,
    loc: ModuleName,
    origin: NamingContext,
    msg: String,
    outputPath: FilePath,
) =
    TmpL.Module(
        pos = pos,
        moduleMetadata = TmpL.ModuleMetadata(
            pos = pos,
            dependencyCategory = (origin as? ModuleNamingContext)?.owner?.dependencyCategory
                ?: DependencyCategory.Production,
        ),
        codeLocation = TmpL.CodeLocationMetadata(
            sourceLibrary = sourceLibrary,
            codeLocation = loc,
            origin = origin,
            outputPath = outputPath,
        ),
        deps = listOf(),
        imports = listOf(),
        topLevels = listOf(garbageTopLevel(pos, msg)),
        result = null,
    )

const val TESTING_BASENAME = "testing"

val DashedIdentifier.isStdLib: Boolean get() = this == DashedIdentifier.temperStandardLibraryIdentifier

val TmpL.Module.isStdLib: Boolean
    get() {
        val parent = this.parent as? TmpL.ModuleSet ?: return false
        val codeLocation = codeLocation.codeLocation
        val config = parent.libraryConfigurations.byLibraryRoot[codeLocation.libraryRoot()]
        return config?.libraryName?.isStdLib == true
    }

val TmpL.Module.libraryName: DashedIdentifier?
    get() {
        val parent = this.parent as? TmpL.ModuleSet ?: return null
        return parent.libraryConfigurations.currentLibraryConfiguration.libraryName
    }

fun matchesStdTesting(
    moduleName: ModuleName,
    libraryConfigurations: LibraryConfigurations,
): Boolean {
    if (moduleName.isPreface) {
        return false
    }
    val config = libraryConfigurations.byLibraryRoot[moduleName.libraryRoot()]
    if (config?.libraryName == DashedIdentifier.temperStandardLibraryIdentifier) {
        val baseName = moduleName.sourceFile.lastOrNull()
            ?.withTemperAwareExtension("")?.baseName
        return baseName == TESTING_BASENAME
    }
    return false
}

/**
 * Maps [TmpL.Actual] arguments, ignoring symbols provided for names.
 *
 * This duplicates a subset of the logic in LazyActualsList init and also TypeChecker extractTypedActuals.
 * At present, this version is the simplest of the three, retaining no symbol information for now.
 */
inline fun <T> List<TmpL.Actual>.mapGeneric(translateActual: (TmpL.Actual) -> T) = buildList {
    var check = true
    for (actual in this@mapGeneric) {
        if (check && actual is TmpL.ValueReference && actual.value.typeTag == TSymbol) {
            check = false
            continue
        }
        check = true
        add(translateActual(actual))
    }
}

/**
 * Maps [TmpL.Actual] arguments, ignoring symbols provided for names.
 *
 * This duplicates a subset of the logic in LazyActualsList init and also TypeChecker extractTypedActuals.
 * At present, this version is the simplest of the three, retaining no symbol information for now.
 */
inline fun <T> List<TmpL.Actual>.mapGenericIndexed(translateActual: (Int, TmpL.Actual) -> T) = buildList {
    var check = true
    var index = 0
    for (actual in this@mapGenericIndexed) {
        if (check && actual is TmpL.ValueReference && actual.value.typeTag == TSymbol) {
            check = false
            continue
        }
        check = true
        add(translateActual(index++, actual))
    }
}

/** Map actual arguments and their expected formal parameter type. */
fun <T> TmpL.CallExpression.mapParameters(
    keepsThis: Boolean = false,
    optionalAsNullable: Boolean = false,
    /** If in rest args, the value formal will have that kind. */
    translate: (TmpL.Actual, Type2?, ValueFormal2?) -> T,
): List<T> {
    val adjustedSig = adjustedSig(keepThis = keepsThis)
    fun formalType(formal: ValueFormal2?): Type2? {
        // TODO Some of this should be unneeded once we replace unset with null generally.
        return when {
            formal != null && optionalAsNullable && formal.kind == ValueFormalKind.Optional ->
                formal.type.withNullity(Nullity.OrNull)
            else -> formal?.type
        }
    }
    return buildList {
        parameters.mapIndexedTo(this) { index, actual ->
            val formal = adjustedSig.valueFormalForActual(index)
            val type = formalType(formal)
            translate(actual, type, formal)
        }
        val nFormals = adjustedSig.requiredInputTypes.size + adjustedSig.optionalInputTypes.size
        // Pad nulls for missing trailing args.
        if (optionalAsNullable) {
            while (this.size < nFormals) {
                val index = this.size
                val formal = adjustedSig.valueFormalForActual(index)
                val nullableType = (
                    formalType(formal)
                        ?: WellKnownTypes.invalidType2
                    )
                    .withNullity(Nullity.OrNull)
                val nullValue = TmpL.ValueReference(pos, nullableType, TNull.value)
                add(translate(nullValue, nullableType, formal))
            }
        }
    }
}

fun TmpL.CallExpression.adjustedSig(keepThis: Boolean = false): Signature2 {
    val sig = contextualizedSig
    val dropThis = !keepThis && sig.hasThisFormal
    return when {
        dropThis -> sig.copy(requiredInputTypes = sig.requiredInputTypes.drop(1), hasThisFormal = false)
        else -> sig
    }
}

fun TmpL.Actual.isNullValue() = this is TmpL.ValueReference && value.typeTag is TNull

val TmpL.Actual.typeOrInvalid
    get() = when (this) {
        is TmpL.Expression -> type
        is TmpL.RestSpread -> WellKnownTypes.invalidType2
    }

fun TmpL.FunctionDeclaration.idKind() = when {
    metadata.any { it.key.symbol == typeDeclSymbol } -> TmpL.IdKind.Type
    else -> TmpL.IdKind.Value
}

fun TmpL.FunctionDeclaration.idReach() = when (name.name) {
    // TODO(tjp, names): Bring public method identification here also?
    is ExportedName -> TmpL.IdReach.External
    else -> TmpL.IdReach.Internal
}

fun Visibility.idReach() = if (this >= Visibility.Protected) {
    TmpL.IdReach.External
} else {
    TmpL.IdReach.Internal
}

fun TmpL.Visibility.idReach() = if (this.ordinal >= TmpL.Visibility.Protected.ordinal) {
    TmpL.IdReach.External
} else {
    TmpL.IdReach.Internal
}

fun TmpL.VisibilityModifier.idReach() = visibility.idReach()

fun standardResolver(name: ResolvedName) = when (name) {
    is ExportedName -> name.baseName
    else -> name
}

fun CodeLocation.source() = when (this) {
    is ModuleName -> this.sourceFile
    is ImplicitsCodeLocation -> filePath("implicits.temper")
    else -> null
}

fun CodeLocation.sameSource(other: CodeLocation): Boolean? {
    val left = this.source() ?: return null
    val right = other.source() ?: return null
    return left == right
}

/** Some type bounds are implied on several backends. Semantics here are sloppy but pragmatic. */
fun TmpL.NominalType.isCommonlyImplied(): Boolean {
    return when (typeName.sourceDefinition) {
        WellKnownTypes.equatableTypeDefinition, WellKnownTypes.mapKeyTypeDefinition -> true
        else -> false
    }
}

/**
 * Determines how a name is declared.
 *
 * The search begins at `this` TmpL node (typically, within an expression) and reads parent scopes to find
 * the declaration responsible for the given name.
 *
 * @return the TmpL AST that defined the name.
 */
fun TmpL.Tree.findDeclaration(
    name: ResolvedName,
    resolver: (ResolvedName) -> TemperName = ::standardResolver,
): Pair<TmpL.Module, TmpL.NameDeclaration>? {
    var node: TmpL.Tree? = this
    var currentModule: TmpL.Module? = null
    var id = resolver(name)
    var importedModule: CodeLocation? = null

    fun match(elem: TmpL.NameDeclaration) = resolver(elem.name.name) == id

    fun modToDecl(decl: TmpL.NameDeclaration): Pair<TmpL.Module, TmpL.NameDeclaration>? {
        var check = node
        while (check != null) {
            if (check is TmpL.Module) {
                return check to decl
            }
            check = check.parent as? TmpL.Tree
        }
        // May want to error here if being run on disconnected snippets.
        return null
    }

    while (node != null) {
        when (node) {
            is TmpL.BlockStatement -> {
                for (stmt in node.statements) {
                    if (stmt is TmpL.LocalDeclaration && match(stmt)) return modToDecl(stmt)
                    if (stmt is TmpL.LocalFunctionDeclaration && match(stmt)) return modToDecl(stmt)
                }
            }

            is TmpL.TypeDeclaration -> if (match(node)) return modToDecl(node)
            is TmpL.FunctionLike -> {
                if (match(node)) return modToDecl(node)
                val params = node.parameters
                params.restParameter?.let {
                    if (match(it)) return@findDeclaration modToDecl(it)
                }
                for (arg in params.parameters) {
                    if (match(arg)) return modToDecl(arg)
                }
            }

            is TmpL.Module -> {
                currentModule = node
                for (decl in node.topLevels) {
                    if (decl is TmpL.NameDeclaration && match(decl)) return node to decl
                }
                for (imp in node.imports) {
                    val ext = imp.externalName.name
                    val loc = imp.localName?.name ?: ext
                    if (resolver(loc) == id) {
                        id = resolver(ext)
                        importedModule = imp.externalModuleName
                        break
                    }
                }
            }

            is TmpL.ModuleSet -> {
                val imp = importedModule
                val filter = if (imp != null) {
                    { module: TmpL.Module ->
                        module.codeLocation.codeLocation.sameSource(imp) == true
                    }
                } else {
                    { module: TmpL.Module ->
                        module !== currentModule
                    }
                }
                for (peerModule in node.modules) {
                    if (filter(peerModule)) {
                        for (decl in peerModule.topLevels) {
                            if (decl is TmpL.NameDeclaration && match(decl)) return peerModule to decl
                        }
                    }
                }
            }

            else -> {}
        }
        node = node.parent as? TmpL.Tree
    }
    return null
}

fun TmpL.Tree.referencedNames(): Sequence<ResolvedName> = sequence {
    when (this@referencedNames) {
        is TmpL.Id -> yield(name)
        is TmpL.TypeName -> yield(sourceDefinition.name)
        else -> for (kid in children) {
            yieldAll(kid.referencedNames())
        }
    }
}

/** Find mutable vars declared in this scope that are referenced in functions beneath this node. */
fun TmpL.Tree.mutableCaptures(): Set<ResolvedName> {
    // Skip this because it's likely already a function, and we want to handle functions below specially, not this one.
    val vars = buildSet {
        for (kid in children) {
            addAll(kid.varsDeclared(includeBlocks = true) { !it.assignOnce })
        }
    }
    val functions = buildSet {
        for (kid in children) {
            addAll(kid.functionsDeclared())
        }
    }
    return buildSet {
        for (function in functions) {
            for (name in function.referencedNames()) {
                if (name in vars) {
                    add(name)
                }
            }
        }
    }
}

/** Find functions at or beneath this node but not beneath other functions. */
fun TmpL.Tree.functionsDeclared(): Sequence<TmpL.FunctionLike> = sequence {
    when (this@functionsDeclared) {
        is TmpL.FunctionLike -> yield(this@functionsDeclared)
        else -> for (kid in children) {
            yieldAll(kid.functionsDeclared())
        }
    }
}

/** Find var names declared within this scope. */
fun TmpL.Tree.varsDeclared(
    includeBlocks: Boolean = false,
    keep: (TmpL.VarLike) -> Boolean = { true },
): Sequence<ResolvedName> = sequence {
    when (this@varsDeclared) {
        is TmpL.BlockStatement -> if (!includeBlocks) {
            return@sequence
        }

        is TmpL.FunctionLike, is TmpL.NestingStatement -> {
            return@sequence
        }

        is TmpL.VarLike -> if (keep(this@varsDeclared)) {
            yield(name.name)
            return@sequence
        }

        else -> {}
    }
    for (kid in children) {
        // Exclude nested blocks in any case.
        yieldAll(kid.varsDeclared(keep = keep))
    }
}

object GetStaticSupport : InlineTmpLSupportCode {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<TmpL.Tree>>,
        returnType: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression {
        val (arg0, arg1) = arguments
        val subjectType = (arg0.expr as TmpL.ValueReference).value.typeBestEffort as DefinedNonNullType
        val typeName = TmpL.TemperTypeName(arg0.expr.pos, subjectType.definition)
        val symRef = arg1.expr as TmpL.ValueReference
        val propId = TmpL.ExternalPropertyId(symRef.pos, TmpL.DotName(symRef.pos, TSymbol.unpack(symRef.value).text))
        return TmpL.GetBackedProperty(
            pos,
            subject = typeName,
            property = propId,
            type = returnType,
        )
    }

    override val needsThisEquivalent: Boolean get() = false

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("inlineGetStatic")
    }
}

/** Scan a declaration for a key to look up the relevant value. */
operator fun TmpL.Declaration.get(key: Symbol) =
    metadata.find { it.key.symbol == key }?.value

/**
 * Modules usually carry the library name, so we can interpret them for the output root.
 * However, in some cases, such as currently for docgen and repl, we don't yet provide
 * library names, so we need to relativize based on the input path.
 */
class LibraryRootContext(
    /** The input path to the library root containing Temper source. */
    val inRoot: FilePath,
    /** The output path to the library root, based on the library name, that will contain backend code. */
    val outRoot: FilePath,
) {
    fun rootFor(module: TmpL.Module) = rootFor(module.codeLocation.origin)
    fun rootFor(module: Module) = rootFor(module.namingContext)
    fun rootFor(origin: NamingContext) = when (origin) {
        is ModuleNamingContext -> outRoot
        // This currently can happen at least in docgen and repl.
        else -> inRoot
    }
}

val TmpL.Type.withoutNullOrBubble: TmpL.Type get() = this.withoutAtom {
    it is TmpL.BubbleType ||
        (it is TmpL.NominalType && it.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition)
}

fun canBeNull(t: TmpL.Type): Boolean = when (t) {
    is TmpL.TypeUnion -> t.types.any { canBeNull(it) }
    is TmpL.TypeIntersection -> t.types.all { canBeNull(it) }
    is TmpL.NominalType -> t.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition
    else -> false
}

val TmpL.Type.withoutNull: TmpL.Type get() = this.withoutAtom {
    it is TmpL.NominalType && it.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition
}

val TmpL.Type.withoutBubbleOrNull: TmpL.Type get() = this.withoutAtom {
    it is TmpL.BubbleType ||
        (
            it is TmpL.NominalType &&
                it.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition
            )
}

fun TmpL.Type.withoutAtom(predicate: (TmpL.Type) -> Boolean): TmpL.Type = when (this) {
    is TmpL.TypeIntersection -> this
    is TmpL.TypeUnion -> {
        var hasDifferences = false
        val typesWithout: List<TmpL.Type> = buildList {
            types.mapNotNullTo(this@buildList) {
                val t = it.withoutAtom(predicate)
                if (t is TmpL.NeverType) {
                    hasDifferences = true
                    null
                } else {
                    if (t !== it) { hasDifferences = true }
                    t
                }
            }
        }

        if (hasDifferences) {
            when (typesWithout.size) {
                0 -> TmpL.NeverType(pos)
                1 -> typesWithout.first().deepCopy()
                else -> TmpL.TypeUnion(pos, typesWithout.map { it.deepCopy() })
            }
        } else {
            this
        }
    }
    is TmpL.NeverType -> this
    is TmpL.BubbleType,
    is TmpL.FunctionType,
    is TmpL.GarbageType,
    is TmpL.NominalType,
    is TmpL.TopType,
    -> if (predicate(this)) {
        TmpL.NeverType(this.pos)
    } else {
        this
    }
}

private fun Iterable<TmpL.DeclarationMetadata>.documentationOf(): OccasionallyHelpful? =
    this.lookupMetaData(docStringSymbol, unpackVal = ::helpfulFromMetadataValue)

/** An enum that allows switching on frequently used types from Implicits ignoring type parameters */
enum class ImplicitTypeTag {
    Boolean,
    Int,
    Float64,
    Function,
    String,
    List,
    ListBuilder,
    Map,
    MapBuilder,
    Null,
    Void,
    Other,
}

val TmpL.AType.implicitTypeTag: ImplicitTypeTag get() {
    val t = ot
    val nt = t.withoutBubbleOrNull as? TmpL.NominalType
    return nt?.implicitTypeTag
        ?: if (t is TmpL.NominalType && t.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition) {
            ImplicitTypeTag.Null
        } else {
            ImplicitTypeTag.Other
        }
}

val TmpL.NominalType.implicitTypeTag: ImplicitTypeTag get() = when (this.typeName.sourceDefinition as? TypeShape) {
    WellKnownTypes.booleanTypeDefinition -> ImplicitTypeTag.Boolean
    WellKnownTypes.float64TypeDefinition -> ImplicitTypeTag.Float64
    WellKnownTypes.functionTypeDefinition -> ImplicitTypeTag.Function
    WellKnownTypes.intTypeDefinition -> ImplicitTypeTag.Int
    WellKnownTypes.listTypeDefinition -> ImplicitTypeTag.List
    WellKnownTypes.listBuilderTypeDefinition -> ImplicitTypeTag.ListBuilder
    WellKnownTypes.mapTypeDefinition -> ImplicitTypeTag.Map
    WellKnownTypes.mapBuilderTypeDefinition -> ImplicitTypeTag.MapBuilder
    WellKnownTypes.nullTypeDefinition -> ImplicitTypeTag.Null
    WellKnownTypes.stringTypeDefinition -> ImplicitTypeTag.String
    WellKnownTypes.voidTypeDefinition -> ImplicitTypeTag.Void
    else -> ImplicitTypeTag.Other
}

val TmpL.FunctionLike.documentation: OccasionallyHelpful
    get() = autodocFor(this)

val TmpL.Declaration.documentation: OccasionallyHelpful?
    get() = this.metadata.documentationOf()

val TmpL.Member.documentation: OccasionallyHelpful?
    get() = this.metadata.documentationOf()

private inline fun <T> Iterable<TmpL.DeclarationMetadata>.lookupMetaData(
    sym: Symbol,
    unpackVal: (Value<*>) -> T? = { error("unexpected value") },
    unpackName: (QName) -> T? = { error("unexpected name") },
    missing: T? = null,
): T? {
    for (pair in this) {
        if (pair.key.symbol == sym) {
            val result: T? = when (val value = pair.value) {
                is TmpL.NameData -> unpackName(value.qName)
                is TmpL.ValueData -> unpackVal(value.value)
            }
            if (result != null) {
                return result
            }
        }
    }
    return missing
}

val Iterable<TmpL.DeclarationMetadata>.qName: QName?
    get() = this.lookupMetaData(qNameSymbol, unpackVal = {
        QName.fromString(TString.unpack(it)).result
    })

/** Returns null for unreachable top levels. */
fun TmpL.TopLevel.dependencyCategory() = when (this) {
    is TmpL.Test -> DependencyCategory.Test
    is TmpL.Declaration -> metadata.dependencyCategory()
    is TmpL.ModuleInitBlock -> metadata.dependencyCategory()
    else -> DependencyCategory.Production
}

fun TmpL.BlockStatement?.isPureVirtual(pureVirtualSupportCode: SupportCode): Boolean =
    isPureVirtual { it is TmpL.SupportCodeWrapper && it.supportCode == pureVirtualSupportCode }

/**
 * Test whether a body is pure virtual. This shallowly checks if the special pure virtual function is called.
 */
fun TmpL.BlockStatement?.isPureVirtual(isFnPureVirtual: (TmpL.Callable) -> Boolean): Boolean {
    return this?.statements?.any { statement ->
        when (statement) {
            is TmpL.Assignment -> statement.right
            is TmpL.ExpressionStatement -> statement.expression
            is TmpL.ReturnStatement -> statement.expression
            else -> null
        }?.let { expr ->
            (expr as? TmpL.CallExpression)?.let { isFnPureVirtual(it.fn) }
        } ?: false
    } ?: true
}

/**
 * Split into local var init for property storage, then remaining statements using `this`.
 * Presumes a constructor that needs to construct an instance between these two, then return it.
 */
fun List<TmpL.Statement>.splitConstructorBody(): Pair<List<TmpL.Statement>, List<TmpL.Statement>> {
    val initStatements = mutableListOf<TmpL.Statement>()
    val useStatements = mutableListOf<TmpL.Statement>()
    var reachedUse = false
    for (statement in this) {
        reachedUse = reachedUse || statement.anyChildDepth(
            within = { tree ->
                when (tree) {
                    // We don't nest classes, so these must be for our enclosing type.
                    is TmpL.GetBackedProperty, is TmpL.SetBackedProperty -> false
                    else -> true
                }
            },
            // Likewise, any `this` must be for the enclosing type.
            predicate = { it is TmpL.This },
        ) || statement.anyChildDepth(
            // We do nest functions, so only pay attention to outer returns.
            within = { it !is TmpL.FunctionLike },
            predicate = { it is TmpL.ReturnStatement && it.expression == null },
        )
        when {
            !reachedUse -> initStatements
            else -> useStatements
        }.add(statement.deepCopy())
    }
    return initStatements to useStatements
}

private fun List<TmpL.DeclarationMetadata>.dependencyCategory() =
    when (val reach = find { it.key.symbol == reachSymbol }?.value) {
        is TmpL.ValueData -> when (val reachUnpacked = TSymbol.unpack(reach.value)) {
            noneSymbol -> null
            testSymbol -> DependencyCategory.Test
            else -> error("unexpected reach: $reachUnpacked")
        }
        else -> DependencyCategory.Production
    }

class TentativeTmpL internal constructor(
    internal val tmpLTranslator: TmpLTranslator,
    internal val nascentModule: NascentModule,
)

fun <BE : Backend<BE>> Backend<BE>.injectSuperCallMethods(
    tentativeTmpl: TentativeTmpL,
    injectInto: (TmpL.TypeDeclaration) -> Boolean,
    chooseSuperName: (MethodKind, String) -> String,
) {
    val nascentModule = tentativeTmpl.nascentModule
    val translator = tentativeTmpl.tmpLTranslator
    val supportNetwork = this.supportNetwork
    val namingContext = nascentModule.codeLocation.origin
    for (topLevel in nascentModule.topLevels) {
        if (topLevel is TmpL.TypeDeclaration && injectInto(topLevel)) {
            topLevel.injectSuperCallMethods<BE>(translator, supportNetwork, namingContext, chooseSuperName)
        }
    }
}

/** Creates methods that call super methods not present in this type. */
internal fun <BE : Backend<BE>> TmpL.TypeDeclaration.injectSuperCallMethods(
    translator: TmpLTranslator,
    supportNetwork: SupportNetwork,
    namingContext: NamingContext,
    chooseSuperName: (MethodKind, String) -> String,
) {
    val missingMethods = this.inherited
    // TODO Pass genre into here?
    val genre = Genre.Library
    val nameMaker = ResolvedNameMaker(namingContext, genre)
    // TODO Better garbage logging?
    val typeTranslator = TypeTranslator(supportNetwork, Genre.Library) { pos, _ -> TmpL.GarbageType(pos) }
    // Generate TmpL so remaining logic, including property pairing, flows as usual.
    val injectedMethods = missingMethods.mapNotNull missingMethods@{ missingMethod ->
        val method = missingMethod.memberOverride.superTypeMember as? MethodShape
            ?: return@missingMethods null
        val funType = missingMethod.memberOverride.superTypeMemberTypeInSubTypeContext as? Signature2
            ?: return@missingMethods null
        val nameHints = method.parameterInfo?.names ?: listOf()
        val dotName = TmpL.DotName(pos, method.symbol.text)
        val name = TmpL.Id(pos, method.name as ResolvedName) // Unique if we don't have overloading.
        val metadata = translator.translateDeclarationMetadataValueMultimap(method.metadata)
        val parameters = funType.allValueFormals.mapIndexedNotNull { i, valueFormal ->
            // TODO Unify with logic in TypeTranslator?
            if (valueFormal.kind == ValueFormalKind.Rest) {
                null
            } else {
                val nameHint = nameHints.getOrNull(i)?.text ?: "inp"
                TmpL.Formal(
                    pos = pos,
                    metadata = emptyList(),
                    name = TmpL.Id(pos, nameMaker.unusedTemporaryName(nameHint)),
                    type = typeTranslator.translateType(pos, valueFormal.type).aType,
                    descriptor = valueFormal.type,
                )
            }
        }.let { parameters ->
            TmpL.Parameters(
                pos = pos,
                parameters = parameters,
                restParameter = funType.restInputsType?.let { restValuesFormal ->
                    TmpL.RestFormal(
                        pos = pos,
                        metadata = emptyList(),
                        name = TmpL.Id(pos, nameMaker.unusedTemporaryName("rest")),
                        type = typeTranslator.translateType(pos, restValuesFormal).aType,
                        descriptor = restValuesFormal,
                    )
                },
                thisName = parameters[0].name.deepCopy(),
            )
        }
        val returnType = typeTranslator.translateType(pos, funType.returnType2)
        val typeParameters = funType.typeFormals.map { typeFormal -> // TODO Unify with logic in TmpLTranslator?
            val tfPos = typeFormal.pos
            TmpL.TypeFormal(
                pos = tfPos,
                name = TmpL.Id(tfPos, typeFormal.name),
                upperBounds = typeFormal.upperBounds.map {
                    typeTranslator.translateType(tfPos.rightEdge, hackMapOldStyleToNew(it))
                        as TmpL.NominalType
                },
                definition = typeFormal,
            )
        }.let { TmpL.TypeParameters(pos, it) }
        val overridden: List<TmpL.SuperTypeMethod> = emptyList() // TODO should this point to the super?
        val visibility = TmpL.VisibilityModifier(pos, method.visibility.toTmpL())
        val body = TmpL.BlockStatement(
            pos,
            statements = listOf(
                TmpL.CallExpression(
                    pos,
                    fn = chooseSuperName(method.methodKind, dotName.dotNameText).let { superName ->
                        TmpL.MethodReference(
                            pos,
                            subject = TmpL.TemperTypeName(pos, method.enclosingType),
                            methodName = TmpL.DotName(pos, superName),
                            method = StaticPropertyShape(
                                enclosingType = method.enclosingType,
                                name = BuiltinName(superName), // Expected generated to match this.
                                stay = null,
                                symbol = Symbol(superName),
                                visibility = Visibility.Protected, // TODO Support configuration of this?
                            ),
                            type = funType,
                        )
                    },
                    parameters = parameters.parameters.zip(funType.requiredInputTypes + funType.optionalInputTypes)
                        .mapIndexed { index, (parameter, valueFormal) ->
                            val paramName = parameter.name.deepCopy()
                            when (index) {
                                0 -> TmpL.This(pos, paramName, valueFormal as DefinedNonNullType)
                                else -> TmpL.Reference(paramName, valueFormal)
                            }
                        },
                    type = funType.returnType2,
                ).let { call ->
                    val retType = funType.returnType2
                    when {
                        // TODO: The Never<Void> distinction is not important,
                        // but for Result<Void> we should probably look at the bubble branch strategy.
                        retType == WellKnownTypes.voidType2 ->
                            TmpL.ExpressionStatement(pos, call)

                        else -> TmpL.ReturnStatement(pos, call)
                    }
                },
            ),
        )
        when (method.methodKind) {
            MethodKind.Normal -> TmpL.NormalMethod(
                pos,
                body = body,
                dotName = dotName,
                mayYield = false, // TODO Need function definition for this?
                memberShape = method,
                metadata = metadata,
                parameters = parameters,
                name = name,
                returnType = returnType.aType,
                typeParameters = TmpL.ATypeParameters(typeParameters),
                overridden = overridden,
                visibility = visibility,
            )

            MethodKind.Getter -> TmpL.Getter(
                pos,
                body = body,
                dotName = dotName,
                metadata = metadata,
                memberShape = method,
                propertyShape = propertyShapeForSetterOrGetter(method),
                name = name,
                parameters = parameters,
                returnType = returnType.aType,
                typeParameters = TmpL.ATypeParameters(typeParameters),
                overridden = overridden,
                visibility = visibility,
            )

            MethodKind.Setter -> TmpL.Setter(
                pos,
                body = body,
                dotName = dotName,
                metadata = metadata,
                memberShape = method,
                propertyShape = propertyShapeForSetterOrGetter(method),
                name = name,
                parameters = parameters,
                returnType = returnType.aType,
                typeParameters = TmpL.ATypeParameters(typeParameters),
                overridden = overridden,
                visibility = visibility,
            )

            MethodKind.Constructor -> error("unexpected")
        }
    }
    // Combine all.
    this.members += injectedMethods
}

/** Typically for constructing dependency metadata. */
data class DependencyGrouping(
    val productionNames: Set<DashedIdentifier>,
    val testNames: Set<DashedIdentifier>,
) {
    companion object {
        fun fromModuleImports(modules: Iterable<TmpL.Module>): DependencyGrouping {
            val productionNames = mutableListOf<DashedIdentifier>()
            val testNames = mutableListOf<DashedIdentifier>()
            for (module in modules) {
                imports@ for (import in module.imports) {
                    val path = import.path as? TmpL.CrossLibraryPath ?: continue@imports
                    when (val reach = import.metadata.find { it.key.symbol == reachSymbol }?.value) {
                        is TmpL.ValueData -> when (TSymbol.unpack(reach.value)) {
                            testSymbol -> testNames
                            else -> null
                        }
                        null -> productionNames
                        else -> error("unexpected")
                    }?.add(path.libraryName)
                }
            }
            return DependencyGrouping(
                // Semi arbitrary order by default, and we want to deduplicate, so might as well sort for prettiness.
                productionNames = productionNames.toSortedSet(),
                testNames = testNames.toSortedSet(),
            )
        }
    }
}

fun TmpL.Statement.isYieldingStatement(): Boolean =
    when (this) {
        is TmpL.BoilerplateCodeFoldBoundary,
        is TmpL.BreakStatement,
        is TmpL.ContinueStatement,
        is TmpL.EmbeddedComment,
        is TmpL.GarbageStatement,
        is TmpL.Declaration,
        is TmpL.ModuleInitFailed,
        is TmpL.ReturnStatement,
        is TmpL.SetProperty,
        is TmpL.ThrowStatement,
        -> false

        is TmpL.YieldStatement -> true
        is TmpL.ExpressionStatement -> expression is TmpL.AwaitExpression
        is TmpL.HandlerScope -> handled is TmpL.AwaitExpression
        is TmpL.Assignment -> when (val right = this.right) {
            is TmpL.Expression -> right is TmpL.AwaitExpression
            is TmpL.HandlerScope -> right.isYieldingStatement()
        }

        is TmpL.BlockStatement ->
            this.statements.any { it.isYieldingStatement() }
        is TmpL.ComputedJumpStatement ->
            this.cases.any { it.body.isYieldingStatement() }
        is TmpL.IfStatement -> this.consequent.isYieldingStatement() ||
            this.alternate?.isYieldingStatement() == true
        is TmpL.LabeledStatement -> this.statement.isYieldingStatement()
        is TmpL.TryStatement -> this.tried.isYieldingStatement() ||
            this.recover.isYieldingStatement()
        is TmpL.WhileStatement -> this.body.isYieldingStatement()
    }

fun qNameFor(d: TmpL.Declaration): QName? {
    val md = d.metadata.firstOrNull { it.key.symbol == qNameSymbol }
    when (val value = md?.value) {
        null, is TmpL.NameData -> {}
        is TmpL.ValueData -> {
            val str = TString.unpackOrNull(value.value)
            if (str != null) {
                when (val r = QName.fromString(str)) {
                    is RFailure<*> -> error("$r")
                    is RSuccess<QName, *> -> return r.result
                }
            }
        }
    }
    return null
}

fun Visibility.toTmpL() = when (this) {
    Visibility.Private -> TmpL.Visibility.Private
    Visibility.Protected -> TmpL.Visibility.Protected
    Visibility.Public -> TmpL.Visibility.Public
}

val TmpL.Type.aType get() = TmpL.AType(this)
val TmpL.NewType.aType get() = TmpL.AType(this)

internal fun contextualizeSig(sig: Signature2, bindings: Map<TypeFormal, Type2>): Signature2 =
    sig.mapType(bindings).copy(typeFormals = emptyList())

fun toSigBestEffort(descriptor: Descriptor?) = when (descriptor) {
    null -> null
    is Signature2 -> descriptor
    is Type2 -> withType(descriptor, fn = { _, sig, _ -> sig }, fallback = { null })
}
