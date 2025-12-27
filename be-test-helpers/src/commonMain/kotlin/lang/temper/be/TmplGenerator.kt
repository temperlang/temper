package lang.temper.be

import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.aType
import lang.temper.common.AtomicCounter
import lang.temper.common.OpenOrClosed
import lang.temper.common.TriState
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.NameMaker
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.TypeShapeImpl
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormal2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.value.DependencyCategory
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.log.unknownPos as p0

/**
 * Contains utilities to generate TmpL code for tests and to maintain a naming context.
 */
class TmplGenerator(
    internal val extension: String,
) {
    private val namingContext by lazy {
        object : NamingContext(AtomicCounter(NAMING_UID_START)) {
            override val loc: ModuleLocation = exampleCodeLoc
        }
    }

    internal val origin get() = namingContext
    internal val libraryName = DashedIdentifier.from("test")!!

    /** Follow convention of [TmpLTranslator.mergedNameMaker] */
    private val nameMaker: NameMaker by lazy { ResolvedNameMaker(namingContext, Genre.Library) }

    /** Follow convention of [TmpLTranslator.unusedName] */
    private fun unusedName(parsedName: ParsedName): ResolvedName =
        nameMaker.unusedTemporaryName(parsedName.nameText)

    // Names.

    /** This is how most [TmpL.Id] elements are constructed in [TmpLTranslator] */
    fun makeId(name: String): TmpL.Id = makeId(makeParsedName(name))
    fun makeId(name: ResolvedName) = TmpL.Id(p0, name = name)
    fun makeJumpLabel(name: String) = TmpL.JumpLabel(makeId(name))
    fun makeJumpLabel(name: ResolvedName) = TmpL.JumpLabel(makeId(name))

    fun makeExportedName(name: String) = ExportedName(namingContext, ParsedName(name))

    fun makeParsedName(name: String): ResolvedName = unusedName(ParsedName(name))

    fun makeSourceName(name: String): SourceName = nameMaker.unusedSourceName(ParsedName(name))
    fun makeSourceName(name: ParsedName): SourceName = nameMaker.unusedSourceName(name)

    fun makeLabel(name: String): ResolvedName = makeParsedName(name)
    fun makeRef(name: ResolvedName, type: Type2) = TmpL.Reference(p0, id = makeId(name), type = type)

    /** This is how some pre-named [TmpL.Id] elements are constructed. */
    fun makeBuiltin(name: String) = BuiltinName(name)

    // Module level.

    fun moduleSet(module: TmpL.Module): TmpL.ModuleSet {
        val work = FilePath.emptyPath.resolve(FilePathSegment("work"), true)
        val lc = LibraryConfiguration(
            libraryName,
            work,
            emptyList(),
            { null },
        )
        val libConfigs = LibraryConfigurationsBundle.from(listOf(lc)).withCurrentLibrary(lc)
        return TmpL.ModuleSet(
            genre = Genre.Library,
            libraryConfigurations = libConfigs,
            mergedNamingContext = object : NamingContext(AtomicCounter(NAMING_UID_START_FOR_MODULESET)) {
                override val loc: ModuleLocation
                    get() = TODO("Not yet implemented")
            },
            modules = listOf(module),
            pos = p0,
        )
    }

    fun module(block: ModuleGenerator.() -> Unit): TmpL.Module =
        ModuleGenerator(this).apply(block).generate()

    // Statements.

    fun block(vararg stmts: TmpL.Statement): TmpL.BlockStatement =
        TmpL.BlockStatement(p0, stmts.toList())

    private fun blockLazy(vararg stmts: TmpL.Statement): TmpL.Statement =
        if (stmts.size == 1) stmts[0] else block(*stmts)

    fun localDecl(
        name: TmpL.Id,
        type: TmpL.AType,
        descriptor: Type2,
        metadata: Iterable<TmpL.DeclarationMetadata> = emptyList(),
        init: TmpL.Expression? = null,
        assignOnce: Boolean = true,
    ) = TmpL.LocalDeclaration(
        p0,
        metadata = metadata,
        name = name,
        init = init,
        assignOnce = assignOnce,
        type = type,
        descriptor = descriptor,
    )

    fun call(
        which: ResolvedName,
        calleeType: Signature2,
        vararg args: TmpL.Actual,
    ): TmpL.CallExpression =
        TmpL.CallExpression(
            pos = p0,
            fn = TmpL.FnReference(makeId(which), calleeType),
            parameters = args.toList(),
            type = calleeType.returnType2,
        )

    fun call(
        which: String,
        calleeType: Signature2,
        vararg args: TmpL.Actual,
    ): TmpL.CallExpression = call(makeParsedName(which), calleeType, *args)

    fun label(label: ResolvedName, stmt: TmpL.Statement) =
        TmpL.LabeledStatement(p0, label = makeJumpLabel(label), statement = stmt)

    fun whileLoop(test: TmpL.Expression, vararg body: TmpL.Statement): TmpL.WhileStatement =
        TmpL.WhileStatement(p0, test = test, body = blockLazy(*body))

    fun ifThen(test: TmpL.Expression, vararg body: TmpL.Statement): TmpL.IfStatement =
        TmpL.IfStatement(p0, test = test, consequent = blockLazy(*body), alternate = null)

    fun breakStmt(label: ResolvedName? = null) = TmpL.BreakStatement(p0, label = label?.let { makeJumpLabel(it) })
    fun continueStmt(label: ResolvedName? = null) = TmpL.ContinueStatement(p0, label = label?.let { makeJumpLabel(it) })
    fun returnStmt(expr: TmpL.Expression?) = TmpL.ReturnStatement(p0, expression = expr)

    // Expressions.

    fun value(what: Any): TmpL.ValueReference = when (what) {
        is Boolean -> TmpL.ValueReference(p0, WellKnownTypes.booleanType2, Value(what, TBoolean))
        is Int -> TmpL.ValueReference(p0, WellKnownTypes.intType2, Value(what, TInt))
        is Long -> TmpL.ValueReference(p0, WellKnownTypes.int64Type2, Value(what, TInt64))
        is String -> TmpL.ValueReference(p0, WellKnownTypes.stringType2, Value(what, TString))
        else -> TODO()
    }

    fun nullValue(type: Type2) = TmpL.ValueReference(p0, type.withNullity(Nullity.OrNull), TNull.value)

    fun import(name: String, block: ImportGenerator.() -> Unit) =
        ImportGenerator(this, name).let {
            it.block()
            it.generate()
        }

    // Types
    fun fn(returnType: Type2, vararg inputs: Type2) =
        Signature2(returnType, hasThisFormal = false, inputs.toList())

    val noneToBoolean: Signature2 by lazy {
        fn(returnType = WellKnownTypes.booleanType2)
    }
    val noneToVoid: Signature2 by lazy {
        fn(returnType = WellKnownTypes.voidType2)
    }
    val stringToVoid: Signature2 by lazy {
        fn(returnType = WellKnownTypes.voidType2, WellKnownTypes.stringType2)
    }
    val stringToInt: Signature2 by lazy {
        fn(returnType = WellKnownTypes.intType2, WellKnownTypes.stringType2)
    }

    companion object {
        val libraryConfigurations =
            LibraryConfigurationsBundle.from(listOf(exampleLibraryConfiguration))
                .withCurrentLibrary(exampleLibraryConfiguration)
    }
}

class ModuleGenerator(
    internal val tmplGen: TmplGenerator,
) {
    var dependencyCategory = DependencyCategory.Production
    val deps = mutableListOf<TmpL.LibraryDependency>()
    val imports = mutableListOf<TmpL.Import>()
    var outputPath: FilePath? = null
    val topLevels = mutableListOf<TmpL.TopLevel>()
    var result: TmpL.Expression? = null

    private fun outputPathFromOrigin(): FilePath {
        val sourceFile = (tmplGen.origin.loc as? FileRelatedCodeLocation)?.sourceFile
        val baseName = sourceFile?.lastOrNull()?.baseName ?: "output"
        return filePath("$baseName${tmplGen.extension}")
    }

    fun generate(): TmpL.Module = TmpL.Module(
        p0,
        moduleMetadata = TmpL.ModuleMetadata(p0, dependencyCategory),
        codeLocation = TmpL.CodeLocationMetadata(
            sourceLibrary = tmplGen.libraryName,
            codeLocation = tmplGen.origin.loc as ModuleName,
            origin = tmplGen.origin,
            outputPath = outputPath ?: outputPathFromOrigin(),
        ),
        deps = deps,
        imports = imports,
        topLevels = topLevels,
        result = result,
    )

    fun typeDecl(name: ResolvedName, block: TypeGenerator.() -> Unit) {
        topLevels.add(
            TypeGenerator(tmplGen, name)
                .apply(block).generate(),
        )
    }

    fun decl(
        name: ResolvedName,
        type: Type2,
        metadata: Iterable<TmpL.DeclarationMetadata> = emptyList(),
        init: TmpL.Expression? = null,
        assignOnce: Boolean = true,
    ) {
        topLevels.add(
            TmpL.ModuleLevelDeclaration(
                p0,
                metadata = metadata,
                name = TmpL.Id(p0, name),
                init = init,
                assignOnce = assignOnce,
                type = type.asTmpLType().aType,
                descriptor = type,
            ),
        )
    }

    fun moduleFunction(name: ResolvedName, block: MethodFuncGenerator.() -> Unit) {
        MethodFuncGenerator(tmplGen, name)
            .let {
                it.body = tmplGen.block()
                it.block()
                val metadata = it.metadata
                val name = tmplGen.makeId(name)
                val typeParameters = it.typeParameters()
                val parameters = it.parameters()
                val returnType = it.returnType
                topLevels.add(
                    TmpL.ModuleFunctionDeclaration(
                        p0,
                        metadata = metadata,
                        name = name,
                        typeParameters = typeParameters,
                        parameters = parameters,
                        returnType = returnType.asTmpLType().aType,
                        body = it.body!!,
                        mayYield = it.mayYield,
                        sig = Signature2(
                            returnType2 = returnType,
                            hasThisFormal = it.thisName != null,
                            requiredInputTypes = it.sigFormals.mapNotNull { vf ->
                                if (vf.kind == ValueFormalKind.Required) {
                                    vf.type
                                } else {
                                    null
                                }
                            },
                            optionalInputTypes = it.sigFormals.mapNotNull { vf ->
                                if (vf.kind == ValueFormalKind.Optional) {
                                    vf.type
                                } else {
                                    null
                                }
                            },
                            restInputsType = it.restFormal?.second,
                            typeFormals = it.typeFormals.toList(),
                        ),
                    ),
                )
            }
    }

    fun initBlock(vararg block: TmpL.Statement) {
        topLevels.add(TmpL.ModuleInitBlock(p0, body = tmplGen.block(*block)))
    }

    fun import(name: String, block: ImportGenerator.() -> Unit) {
        ImportGenerator(tmplGen, name).let {
            it.block()
            imports.add(it.generate())
        }
    }
}

class TypeGenerator(
    private val tmplGen: TmplGenerator,
    private val name: ResolvedName,
) {
    val metadata = mutableListOf<TmpL.DeclarationMetadata>()
    val formals = mutableListOf<TmpL.TypeFormal>()
    val superTypes = mutableListOf<TmpL.NominalType>()
    var kind = TmpL.TypeDeclarationKind.Class
    var abstractness = Abstractness.Concrete
    val members = mutableListOf<TmpL.Member>()
    val modName: ModularName get() {
        val mn: ModularName = when (name) {
            is ExportedName -> name
            is SourceName -> tmplGen.makeExportedName(name.baseName.nameText)
            is Temporary -> name
            is BuiltinName -> TODO()
        }
        return mn
    }

    val typeShape: TypeShapeImpl by lazy {
        TypeShapeImpl(
            pos = p0,
            word = name.toSymbol(),
            name = modName,
            abstractness = abstractness,
            mutationCount = AtomicCounter(),
        )
    }

    fun generate() = TmpL.TypeDeclaration(
        p0,
        metadata = metadata,
        name = tmplGen.makeId(name),
        typeParameters = TmpL.ATypeParameters(
            TmpL.TypeParameters(p0, formals),
        ),
        superTypes = superTypes,
        members = members,
        inherited = emptyList(), // TODO: derive from typeShape and members?
        kind = kind,
        typeShape = typeShape,
    )

    fun constructor(name: ResolvedName, block: MethodFuncGenerator.() -> Unit) {
        MethodFuncGenerator(
            tmplGen,
            name = name,
        ).let {
            it.methodKind = MethodKind.Constructor
            it.body = tmplGen.block()
            block(it)
            members.add(
                TmpL.Constructor(
                    p0,
                    name = tmplGen.makeId(it.name),
                    typeParameters = it.typeParameters(),
                    parameters = it.parameters(),
                    body = it.body!!,
                    memberShape = it.memberShape(typeShape),
                    metadata = emptyList(),
                    returnType = TmpL.NominalType(
                        p0,
                        TmpL.TemperTypeName(p0, WellKnownTypes.voidTypeDefinition),
                        emptyList(),
                    ).aType,
                    visibility = it.visibilityTmpl(),
                ),
            )
        }
    }

    fun method(name: ResolvedName, block: MethodFuncGenerator.() -> Unit) {
        MethodFuncGenerator(
            tmplGen,
            name = name,
        ).let {
            it.methodKind = MethodKind.Normal
            block(it)
            members.add(
                TmpL.NormalMethod(
                    pos = p0,
                    dotName = it.dotName(),
                    name = tmplGen.makeId(name),
                    typeParameters = it.typeParameters(),
                    parameters = it.parameters(),
                    returnType = it.returnType.asTmpLType().aType,
                    body = it.body,
                    metadata = emptyList(),
                    overridden = emptyList(),
                    visibility = it.visibilityTmpl(),
                    mayYield = it.mayYield,
                    memberShape = it.memberShape(typeShape),
                ),
            )
        }
    }

    fun property(name: ResolvedName, block: PropertyGenerator.() -> Unit) {
        PropertyGenerator(
            tmplGen,
            name = name,
        ).let {
            block(it)
            members.add(
                TmpL.InstanceProperty(
                    p0,
                    metadata = emptyList(),
                    dotName = it.dotName(),
                    name = tmplGen.makeId(name),
                    type = it.type.asTmpLType().aType,
                    visibility = it.visibilityTmpl(),
                    memberShape = it.memberShape(typeShape),
                    assignOnce = it.assignOnce,
                    descriptor = it.type,
                ),
            )
        }
    }
}

abstract class MemberGenerator(
    val tmplGen: TmplGenerator,
    val name: ResolvedName,
) {
    var visibility: TmpL.Visibility = TmpL.Visibility.Public
    var openness: OpenOrClosed = OpenOrClosed.Open
    val metadata = mutableListOf<TmpL.DeclarationMetadata>()
    val stay: StayLeaf? = null

    val srcName by lazy {
        when (name) {
            is SourceName -> name
            is ResolvedParsedName -> tmplGen.makeSourceName(name.baseName)
            is Temporary -> tmplGen.makeSourceName(name.nameHint)
        }
    }

    val symbolResolved by lazy {
        srcName.toSymbol()
    }

    fun dotName() = TmpL.DotName(
        p0,
        when (name) {
            is SourceName -> name.baseName.nameText
            is ResolvedParsedName -> name.baseName.nameText
            is Temporary -> name.nameHint
        },
    )

    fun visibilityTmpl() = TmpL.VisibilityModifier(p0, visibility)

    abstract fun memberShape(enclosingType: TypeShape): MemberShape
}

class MethodFuncGenerator(
    tmplGen: TmplGenerator,
    name: ResolvedName,
) : MemberGenerator(tmplGen, name) {
    var methodKind: MethodKind = MethodKind.Normal
    val typeFormals = mutableListOf<TypeFormal>()
    var thisName: ResolvedName? = null
    val formals = mutableListOf<TmpL.Formal>()
    val sigFormals = mutableListOf<ValueFormal2>()
    val restFormal: Pair<ResolvedName, Type2>? = null
    var body: TmpL.BlockStatement? = null
    var mayYield: Boolean = false
    var returnType: Type2 = WellKnownTypes.voidType2

    fun addTypeFormal(typeFormal: TypeFormal) {
        typeFormals.add(typeFormal)
    }

    fun addFormal(name: ResolvedName, type: Type2, tmpLType: TmpL.AType, optionalState: TriState = TriState.FALSE) {
        val formalKind = when (optionalState) {
            TriState.FALSE -> ValueFormalKind.Required
            TriState.TRUE, TriState.OTHER -> ValueFormalKind.Optional
        }
        sigFormals.add(ValueFormal2(type, formalKind))
        formals.add(
            TmpL.Formal(
                p0,
                emptyList(),
                tmplGen.makeId(name),
                tmpLType,
                optionalState = optionalState,
                descriptor = type,
            ),
        )
    }

    fun addFormal(name: ResolvedName, type: Type2, optionalState: TriState = TriState.FALSE) {
        val actualType = if (optionalState != TriState.FALSE) {
            type.withNullity(Nullity.OrNull)
        } else {
            type
        }
        addFormal(name, type, actualType.asTmpLType().aType, optionalState)
    }

    fun parameters() = TmpL.Parameters(
        p0,
        thisName = thisName?.let { tmplGen.makeId(it) },
        parameters = formals,
        restParameter = restFormal?.let { (n, t) ->
            TmpL.RestFormal(p0, emptyList(), tmplGen.makeId(n), t.asTmpLType().aType, t)
        },
    )

    fun typeParameters() = TmpL.ATypeParameters(
        TmpL.TypeParameters(p0, typeFormals.map { it.asTmpLTypeFormal() }),
    )

    override fun memberShape(enclosingType: TypeShape): VisibleMemberShape = MethodShape(
        enclosingType = enclosingType,
        name = srcName,
        symbol = symbolResolved,
        stay = stay,
        visibility = visibility.fromTmpL(),
        methodKind = methodKind,
        openness = openness,
    )

    fun exampleFunction() {
        val optionalArg = tmplGen.makeParsedName("optionalArg")
        val optionalArgType = WellKnownTypes.intType2
        val returnId = tmplGen.makeId("return")
        val requiredArg = tmplGen.makeParsedName("requiredArg")

        addFormal(requiredArg, WellKnownTypes.stringType2)
        addFormal(optionalArg, optionalArgType, optionalState = TriState.TRUE)
        returnType = WellKnownTypes.intType2
        visibility = TmpL.Visibility.Public

        body = tmplGen.block(
            tmplGen.localDecl(
                returnId,
                returnType.asTmpLType().aType,
                returnType,
            ),
            // Just pretend to do optional coalescing because correct code is awkward and unneeded.
            tmplGen.ifThen(
                TmpL.ValueReference(p0, TBoolean.valueTrue),
                TmpL.Assignment(p0, tmplGen.makeId(optionalArg), tmplGen.value(1), WellKnownTypes.intType2),
            ),
            returnId assignTo TmpL.Reference(p0, tmplGen.makeId(optionalArg), optionalArgType),
            tmplGen.returnStmt(TmpL.Reference(p0, returnId, optionalArgType)),
        )
    }

    fun exampleMethod() {
        // Reuse other function example as much as possible, just for convenience.
        exampleFunction()

        visibility = TmpL.Visibility.Public
        mayYield = false
        methodKind = MethodKind.Normal
        openness = OpenOrClosed.Closed
    }
}

class PropertyGenerator(
    tmplGen: TmplGenerator,
    name: ResolvedName,
) : MemberGenerator(tmplGen, name) {

    var type: Type2 = WellKnownTypes.anyValueType2
    var assignOnce: Boolean = false
    var abstractness = Abstractness.Concrete
    var getter: TemperName? = null
    var setter: TemperName? = null

    override fun memberShape(enclosingType: TypeShape): PropertyShape {
        return PropertyShape(
            enclosingType = enclosingType,
            name = srcName,
            symbol = symbolResolved,
            stay = stay,
            visibility = visibility.fromTmpL(),
            abstractness = abstractness,
            getter = getter,
            setter = getter,
        )
    }
}

class ImportGenerator(val tmplGen: TmplGenerator, val name: String) {
    var externalName: BuiltinName? = null
    var localName: SourceName? = tmplGen.makeSourceName(name)
    val metadata = mutableListOf<TmpL.DeclarationMetadata>()
    var type: Type2 = WellKnownTypes.anyValueType2
    var libraryName = DashedIdentifier.from("example")!!
    var importTo: ModuleName = ModuleName(
        sourceFile = filePath("example.temper.md"),
        libraryRootSegmentCount = 1,
        isPreface = false,
    )
    var translatedPath = filePath("example")

    private var importingSame = true

    fun importSame() {
        importingSame = true
    }
    fun importCross() {
        importingSame = false
    }

    fun path(): TmpL.ModulePath =
        if (importingSame) {
            TmpL.SameLibraryPath(
                p0,
                libraryName,
                importTo,
                translatedPath,
            )
        } else {
            TmpL.CrossLibraryPath(
                p0,
                libraryName,
                importTo,
                translatedPath,
            )
        }

    private var importValue = true
    fun importValue() {
        importValue = true
    }
    fun importType() {
        importValue = false
    }

    fun sig(): TmpL.ImportSignature = if (importValue) {
        TmpL.ImportedValue(p0, metadata, type.asTmpLType().aType)
    } else {
        TmpL.ImportedType(
            p0,
            metadata,
            typeShape = type.definition as TypeShape,
        )
    }

    fun generate() = TmpL.Import(
        p0,
        externalName = tmplGen.makeId(externalName ?: BuiltinName(name)),
        localName = localName ?. let(tmplGen::makeId),
        sig = sig(),
        path = path(),
    )

    fun exampleSameLibrary() {
        importSame()
        libraryName = exampleLibraryConfiguration.libraryName
        importTo = otherExampleCodeLoc
        translatedPath = FilePath(
            listOf(otherExampleCodeLoc.sourceFile.last().withExtension(tmplGen.extension)),
            isDir = false,
        )
    }

    fun example() {
        type = MkType2(WellKnownTypes.float64TypeDefinition).get()
        exampleSameLibrary()
    }
}

infix fun TmpL.Id.assignTo(expr: TmpL.RightHandSide) = this.assignTo(expr, type = null)

fun TmpL.Id.assignTo(expr: TmpL.RightHandSide, type: Type2? = null) = TmpL.Assignment(
    pos = p0,
    left = this,
    right = expr,
    type = type ?: when (expr) {
        is TmpL.Expression -> expr.type
        is TmpL.HandlerScope -> when (val h = expr.handled) {
            is TmpL.Expression -> h.type
            is TmpL.SetAbstractProperty -> h.right.type
        }
    },
)

internal fun Type2.asTmpLNominal() = TmpL.NominalType(
    p0,
    typeName = TmpL.TemperTypeName(p0, this.definition),
    params = this.bindings.map { it.asTmpLType().aType },
)

internal fun TypeFormal.asTmpLTypeFormal() = TmpL.TypeFormal(
    p0,
    name = TmpL.Id(p0, this.name),
    definition = this,
    upperBounds = this.superTypes.map { hackMapOldStyleToNew(it).asTmpLNominal() },
)

fun Type2.asTmpLType(): TmpL.Type {
    if (nullity == Nullity.OrNull) {
        return TmpL.TypeUnion(
            p0,
            listOf(
                withNullity(Nullity.NonNull).asTmpLType(),
                TmpL.NominalType(
                    p0,
                    typeName = TmpL.TemperTypeName(p0, WellKnownTypes.nullTypeDefinition),
                    params = emptyList(),
                ),
            ),
        )
    }
    return when (this.definition) {
        WellKnownTypes.invalidTypeDefinition -> TmpL.GarbageType(p0)
        WellKnownTypes.resultTypeDefinition -> {
            TmpL.TypeUnion(
                p0,
                listOf(
                    bindings[0].asTmpLType(),
                    TmpL.BubbleType(p0),
                ),
            )
        }
        WellKnownTypes.neverTypeDefinition -> TmpL.NeverType(p0)
        WellKnownTypes.anyValueTypeDefinition -> TmpL.TopType(p0)
        else -> this.asTmpLNominal()
    }
}

fun TmpL.Expression.stmt() = TmpL.ExpressionStatement(this.pos, expression = this)

val exampleCodeLoc = ModuleName(
    sourceFile = filePath("project", "module", "implement.temper"),
    libraryRootSegmentCount = 1,
    isPreface = false,
)

val otherExampleCodeLoc = ModuleName(
    sourceFile = filePath("project", "module", "other.temper"),
    libraryRootSegmentCount = 1,
    isPreface = false,
)

val exampleLibraryConfiguration = LibraryConfiguration(
    libraryName = DashedIdentifier("example-library"),
    libraryRoot = exampleCodeLoc.libraryRoot(),
    supportedBackendList = emptyList(),
    classifyTemperSource = { null },
)

internal const val NAMING_UID_START = 7L
internal const val FORTY_TWO = 42
internal const val FORTY_NINE = 49
internal const val NAMING_UID_START_FOR_MODULESET = 5L

private fun TmpL.Visibility.fromTmpL() = when (this) {
    TmpL.Visibility.Private -> Visibility.Private
    TmpL.Visibility.Protected -> Visibility.Protected
    TmpL.Visibility.Public -> Visibility.Public
}
