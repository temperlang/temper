package lang.temper.be.csharp

import lang.temper.be.Backend
import lang.temper.be.Dependencies
import lang.temper.be.DescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.TmpLOperatorDefinition
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.canBeNull
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.isCommonlyImplied
import lang.temper.be.tmpl.isYieldingStatement
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapParameters
import lang.temper.be.tmpl.orInvalid
import lang.temper.be.tmpl.toSigBestEffort
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.be.tmpl.withoutNullOrBubble
import lang.temper.common.ParseDouble
import lang.temper.common.compatRemoveLast
import lang.temper.common.isNotEmpty
import lang.temper.lexer.Genre
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.resolveFile
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeBubble
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.DefinedType
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.isVoidLike
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.DependencyCategory
import lang.temper.value.MetadataValueMapHelpers.get
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
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
import lang.temper.value.YieldingFnKind
import lang.temper.value.connectedSymbol
import kotlin.math.abs

/** Use a new translator for each temper module. */
internal class CSharpTranslator(
    private val dependenciesBuilder: Dependencies.Builder<CSharpBackend>,
    val module: TmpL.Module,
    val names: CSharpNames,
    val libraryConfigurations: LibraryConfigurations,
    val genre: Genre,
) {
    private val nameMaker = (module.parent as TmpL.ModuleSet).let { moduleSet ->
        ResolvedNameMaker(moduleSet.mergedNamingContext, moduleSet.genre)
    }
    private val fileSpecs = mutableListOf<Backend.TranslatedFileSpecification>()

    /** Functions can nest, so keep a stack of info that we need on each. */
    private val functionContextStack = mutableListOf(FunctionContext())

    private val loc: ModuleName = module.codeLocation.codeLocation
    private val libraryRoot = loc.libraryRoot()
    private val moduleInits = mutableListOf<CSharp.Statement>()
    private val moduleMembers = mutableListOf<CSharp.ClassMember>()
    private val moduleMemberNames = mutableSetOf<TemperName>()
    private var setterValueName: ResolvedName? = null

    /** The namespace under the root namespace. */
    private val subspace: List<String> = chooseSubspace(module)

    internal val fullNamespace: List<String>
    internal val qualifiedGlobalClassName: List<String>

    init {
        val (namespace, globalName) = makeNamespaceAndGlobalName(names.rootNamespace, subspace)
        fullNamespace = namespace
        qualifiedGlobalClassName = globalName
    }

    private var imports: Map<ResolvedName, ExportedName> = module.imports.mapNotNull { imp ->
        imp.localName?.let { it.name to imp.externalName.name as ExportedName }
    }.toMap()

    private val testClassMembers = mutableListOf<CSharp.ClassMember>()
    private var testInProcess: Boolean = false
    private var thisName: ResolvedName? = null
    private var typeDeclarationInProcess: TmpL.TypeDeclaration? = null
    private val typeContext2 = TypeContext2()
    private var typeNameInProcess: String? = null
    private val varTypes = dependenciesBuilder.getMetadata(
        libraryConfigurations.currentLibraryConfiguration.libraryName,
        DescriptorsForDeclarations.Key(CSharpBackend.Factory),
    )?.nameToDescriptor ?: mapOf()

    fun translateModule(): List<Backend.TranslatedFileSpecification> {
        // Prepass then process top levels.
        for (topLevel in module.topLevels) {
            if (topLevel.dependencyCategory() == DependencyCategory.Production) {
                (topLevel as? TmpL.NameDeclaration)?.let { moduleMemberNames.add(it.name.name) }
            }
        }
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        // Example namespace, dir, file, & type naming from reference app.
        // https://github.com/dotnet/eShop/blob/main/src/OrderProcessor/Events/GracePeriodConfirmedIntegrationEvent.cs
        val specs = buildList {
            add(buildGlobalClass(module.pos))
            addAll(fileSpecs)
            buildTestClass(module.pos)?.let { add(it) }
        }
        for (spec in specs) {
            NameSimplifier(spec).simplify()
        }
        return specs
    }

    private fun buildGlobalClass(pos: Position): Backend.TranslatedFileSpecification {
        val globalClassName = qualifiedGlobalClassName.last()
        val clazz = CSharp.TypeDecl(
            pos,
            id = globalClassName.toIdentifier(pos),
            mods = CSharp.TypeModifiers(
                pos.leftEdge,
                modAccess = CSharp.ModAccess.Public,
                modStatic = CSharp.ModStatic.Static,
                modTypeKind = CSharp.ModTypeKind.Class,
            ),
            members = buildList {
                addAll(moduleMembers)
                if (moduleInits.isNotEmpty()) {
                    // Static constructor to init module.
                    // Module initializers might be an alternative, but static constructors ought to do.
                    // https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/proposals/csharp-9.0/module-initializers
                    add(
                        CSharp.StaticConstructorDecl(
                            pos,
                            id = globalClassName.toIdentifier(pos),
                            body = CSharp.BlockStatement(pos, statements = moduleInits),
                        ),
                    )
                }
            },
        )
        val compilationUnit = CSharp.CompilationUnit(
            pos,
            decls = listOf(
                CSharp.NamespaceDecl(
                    pos,
                    names = fullNamespace.map { it.toIdentifier(pos) },
                    decls = listOf(clazz),
                ),
            ),
        )
        return Backend.TranslatedFileSpecification(
            path = dirPath(SRC_PROJECT_DIR).resolve(dirPath(subspace)).resolveFile("$globalClassName.cs"),
            mimeType = CSharpBackend.mimeType,
            content = compilationUnit,
        )
    }

    private fun buildTestClass(pos: Position): Backend.TranslatedFileSpecification? {
        testClassMembers.isNotEmpty() || return null
        val testClassName = makeTestClassName(fullNamespace)
        val clazz = CSharp.TypeDecl(
            pos,
            id = testClassName.toIdentifier(pos),
            attributes = listOf(
                CSharp.AttributeSection(
                    pos,
                    attributes = listOf(
                        CSharp.Attribute(
                            pos,
                            name = StandardNames.microsoftVisualStudioTestToolsUnitTestingTestClass.toTypeName(pos),
                        ),
                    ),
                ),
            ),
            mods = CSharp.TypeModifiers(
                pos.leftEdge,
                modAccess = CSharp.ModAccess.Public,
                modTypeKind = CSharp.ModTypeKind.Class,
            ),
            members = testClassMembers,
        )
        val compilationUnit = CSharp.CompilationUnit(
            pos,
            decls = listOf(
                CSharp.NamespaceDecl(
                    pos,
                    // Typically this would be namespaced separately under UnitTests, but that doesn't seem to be
                    // required, and Temper semantics make it easier to live in the same namespace. We aren't worried
                    // about new collisions, since we originated from the same module, anyway.
                    names = fullNamespace.map { it.toIdentifier(pos) },
                    decls = listOf(clazz),
                ),
            ),
        )
        return Backend.TranslatedFileSpecification(
            path = dirPath(TEST_PROJECT_DIR).resolve(dirPath(subspace)).resolveFile("$testClassName.cs"),
            mimeType = CSharpBackend.mimeType,
            content = compilationUnit,
        )
    }

    private fun chooseSubspace(module: TmpL.Module): List<String> =
        chooseSubspace(module.codeLocation.codeLocation)

    /** "library-root/foo-bar/baz/" -> ["FooBar", "Baz"] */
    private fun chooseSubspace(moduleName: ModuleName): List<String> {
        if (moduleName.relativePath() == FilePath.emptyPath) {
            // Not a subspace
            return emptyList()
        }
        val libraryConfiguration =
            libraryConfigurations.byLibraryRoot.getValue(moduleName.libraryRoot())
        val modulePath = Backend.defaultFilePathForSource(
            libraryConfiguration, moduleName, "",
        )
        val subspace = modulePath.segments.map { segment ->
            segment.withTemperAwareExtension("").fullName.dashToPascal()
        }
        return subspace
    }

    private fun chooseVisibility(decl: TmpL.NameDeclaration) = when (decl.name.name) {
        is ExportedName -> CSharp.ModAccess.Public
        else -> CSharp.ModAccess.Internal
    }

    private fun extractDefaultMaybe(
        extras: MutableList<CSharp.ClassMember>?,
        id: CSharp.Identifier,
        method: TmpL.FunctionDeclarationOrMethod,
        prefix: String,
    ): CSharp.BlockStatement? {
        if (
            extras == null ||
            (method.parent as TmpL.TypeDeclaration).kind != TmpL.TypeDeclarationKind.Interface ||
            method.body.isPureVirtual()
        ) {
            return null
        }
        // We have an implementation. If we're in an interface, we need to make a protected static default helper.
        // https://learn.microsoft.com/en-us/dotnet/csharp/advanced-topics/interface-implementation/default-interface-methods-versions#extend-the-default-implementation
        // TODO Prevent name collisions.
        val defaultId = "${prefix}${id.outName.outputNameText}Default".toIdentifier(id.pos)
        val pos = method.pos
        try {
            // Find thisName before translating body.
            thisName = method.parameters.thisName?.name
            val (typeParameters, whereConstraints) = translateTypeParameters(method.typeParameters)
            extras.add(
                CSharp.MethodDecl(
                    pos,
                    id = defaultId,
                    result = translateType(method.returnType),
                    mods = CSharp.MethodModifiers(
                        pos,
                        modAccess = CSharp.ModAccess.Protected,
                        modStatic = CSharp.ModStatic.Static,
                    ),
                    typeParameters = typeParameters.map { it.second },
                    whereConstraints = whereConstraints,
                    parameters = translateParameters(method.parameters, keepThis = true),
                    body = translateMethodBody(method.body),
                ),
            )
        } finally {
            thisName = null
        }
        // Change the original body to call the default.
        // TODO Arrow function instead of returnOrNot?
        val thisName = method.parameters.thisName?.name
        return CSharp.BlockStatement(
            pos,
            listOf(
                CSharp.InvocationExpression(
                    pos,
                    expr = CSharp.Identifier(pos, defaultId.outName),
                    args = method.parameters.parameters.map { formal ->
                        when (formal.name.name) {
                            thisName -> makeKeywordReference(formal.pos, "this")
                            else -> translateId(formal.name)
                        }
                    },
                ).returnOrNot(pos, method.returnType),
            ),
        )
    }

    private fun findMainType(type: Type2) = type.definition

    private fun findRest(function: TmpL.FunctionLike) {
        function.parameters.restParameter?.let { functionContextStack.last().restFormal = it }
    }

    private fun findRootNamespace(name: ExportedName): String {
        val nameRootNamespace = when (val other = name.origin.loc) {
            is ModuleName -> when (val otherRoot = other.libraryRoot()) {
                libraryRoot -> null
                else -> names.rootNamespacesByRoot[otherRoot]
            }

            else -> null
        } ?: names.rootNamespace
        return nameRootNamespace
    }

    private fun makeIgnore(expr: CSharp.Expression): CSharp.StatementExpression {
        return CSharp.InvocationExpression(
            expr.pos,
            expr = StandardNames.temperCoreCoreIgnore.toStaticMember(expr.pos),
            args = listOf(expr),
        )
    }

    private fun makeRestAssignment(): List<CSharp.Statement> {
        return when (val formal = functionContextStack.last().restFormal) {
            null -> listOf()

            // Declare a local for wrapping the rest/params array in a readonly list for local use.
            else -> listOf(
                CSharp.LocalVariableDecl(
                    formal.pos,
                    type = CSharp.ConstructedType(
                        formal.pos,
                        type = StandardNames.systemCollectionsGenericIReadOnlyList.toTypeName(formal.pos),
                        args = listOf(translateType(formal.type)),
                    ),
                    variables = listOf(
                        CSharp.VariableDeclarator(
                            formal.pos,
                            // The local gets the name that the rest of the body expects.
                            variable = translateId(formal.name),
                            initializer = CSharp.InvocationExpression(
                                formal.pos,
                                expr = StandardNames.systemArrayAsReadOnly.toStaticMember(formal.pos),
                                args = listOf(paramsify(translateId(formal.name))),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    private fun makeSafeName(wantedName: String) = "${wantedName}_" // TODO Explore better safety options.

    private fun makeSafeNameMaybe(typeName: String, memberName: String): String {
        return when (memberName) {
            typeName -> makeSafeName(memberName)
            else -> memberName
        }
    }

    // One example of test class naming from the reference app:
    // https://github.com/dotnet/eShop/blob/0b07251070a94f21453e8139f6107f87c8329771/tests/ClientApp.UnitTests/Services/MarketingServiceTests.cs
    private fun makeTestClassName(fullNamespace: List<String>) = "${fullNamespace.last()}Tests"

    private fun markVoidish(decl: TmpL.FunctionDeclarationOrMethod) {
        functionContextStack.last().voidish = decl.returnType.isVoidish()
    }

    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        try {
            when (topLevel.dependencyCategory()) {
                DependencyCategory.Production -> {}
                DependencyCategory.Test -> testInProcess = true
                null -> return
            }
            when (topLevel) {
                is TmpL.ModuleFunctionDeclaration -> processModuleFunctionDeclaration(topLevel)
                is TmpL.ModuleInitBlock -> processModuleInitBlock(topLevel)
                is TmpL.ModuleLevelDeclaration -> processModuleLevelDeclaration(topLevel)
                is TmpL.SupportCodeDeclaration -> processSupportCodeDeclaration(topLevel)
                is TmpL.Test -> processTest(topLevel)
                is TmpL.TypeDeclaration -> processTypeDeclaration(topLevel)
                else -> {} // TODO Make this `TODO()` now?
            }
        } finally {
            testInProcess = false
        }
    }

    private fun processModuleFunctionDeclaration(decl: TmpL.ModuleFunctionDeclaration) {
        val mods = CSharp.MethodModifiers(
            decl.pos.leftEdge,
            modAccess = chooseVisibility(decl),
            modStatic = CSharp.ModStatic.Static,
        )
        testOrModuleMembers.addAll(
            translateFunctionDeclarationToClassMembers(decl, mods = mods),
        )
    }

    private val testOrModuleMembers get() = when {
        testInProcess -> testClassMembers
        else -> moduleMembers
    }

    private fun translateFunctionDeclarationToClassMembers(
        decl: TmpL.FunctionDeclaration,
        mods: CSharp.MethodModifiers?,
    ): List<CSharp.MethodDecl> {
        return withFunctionContext {
            functionContextStack.last().returnType = decl.sig.returnType2
            findRest(decl)
            markVoidish(decl)

            val id = translateId(decl.name)
            val result = translateType(decl.returnType)
            val (typeParameters, whereConstraints) = translateTypeParameters(decl.typeParameters)
            val parameters = translateParameters(decl.parameters)
            var body = translateBlockStatement(decl.body, prelude = makeRestAssignment())

            buildList {
                if (!decl.mayYield) {
                    add(
                        CSharp.MethodDecl(
                            pos = decl.pos,
                            id = id,
                            result = result,
                            mods = mods,
                            typeParameters = typeParameters.map { it.second },
                            parameters = parameters,
                            whereConstraints = whereConstraints,
                            body = body,
                        ),
                    )
                } else {
                    body = YieldOrDoNotYieldThereIsNoTry(::unusedName)
                        .convertFnBody(body)
                    // C# decides whether a method body is an enumerable based on the
                    // lexical presence of a yield in the body.
                    // Temper `async` method bodies can use yielding builtins, but may not.
                    // Add `if (false) { yield return null; }` if there are no other yielding statements.
                    if (decl.body.statements.none { it.isYieldingStatement() }) {
                        val beforeBodyPos = body.pos.leftEdge
                        body.statements = listOf(
                            CSharp.IfStatement(
                                beforeBodyPos,
                                makeKeywordReference(beforeBodyPos, "false"),
                                CSharp.BlockStatement(
                                    beforeBodyPos,
                                    listOf(
                                        CSharp.YieldReturn(
                                            beforeBodyPos,
                                            makeKeywordReference(beforeBodyPos, "null"),
                                        ),
                                    ),
                                ),
                                null,
                            ),
                        ) + body.statements
                    }

                    // TODO: When allocating names, we need to allocate two for the generator.
                    val helperId = OutName("coroHelper${id.outName.outputNameText}", null)
                    val generatorResultType = (result as CSharp.ConstructedType).let {
                        // IEnumerable<T> -> IGenerator<T>
                        val iEnumerable = StandardNames.systemCollectionsGenericIEnumerable
                        val iGenerator = StandardNames.temperCoreIGenerator
                        check(
                            (it.type as? CSharp.QualTypeName)?.id?.map {
                                    id ->
                                id.outName.outputNameText
                            } == iEnumerable.qualifiedName,
                        )
                        CSharp.ConstructedType(
                            it.pos,
                            iGenerator.toTypeName(it.type.pos),
                            it.args.map { arg -> arg.deepCopy() },
                        )
                    }
                    val promiseResultType = generatorResultType.args.first()

                    // First, define a helper that returns an IEnumerable
                    // then define the main method which adapts that to
                    // the function type.
                    //
                    // private static IEnumerable<T> coroHelperfoo(...) {
                    //   ...
                    // }
                    // public static IGenerator<T> foo(...) {
                    //   return TemperCore.AdaptGenerator<T>(helper, ...);
                    // }
                    add(
                        CSharp.MethodDecl(
                            pos = decl.pos,
                            id = CSharp.Identifier(decl.name.pos.leftEdge, helperId),
                            result = result,
                            mods = mods?.deepCopy()?.let {
                                it.modAccess = CSharp.ModAccess.Private
                                it
                            },
                            typeParameters = typeParameters.map { it.second },
                            parameters = parameters,
                            whereConstraints = whereConstraints,
                            // TODO: Should this be prefixed with something like the below so that
                            // a non-yielding body still is recognized as a generator?
                            //     if (false)
                            //     {
                            //         yield return null;
                            //     }
                            // Do we know how to get a zero value if null isn't usable.
                            body = body,
                        ),
                    )
                    add(
                        CSharp.MethodDecl(
                            pos = decl.pos,
                            id = id,
                            result = generatorResultType,
                            mods = mods,
                            typeParameters = typeParameters.map { it.second },
                            parameters = parameters,
                            whereConstraints = whereConstraints,
                            body = run {
                                val wrapperPos = decl.body.pos.rightEdge
                                CSharp.BlockStatement(
                                    wrapperPos,
                                    listOf(
                                        CSharp.ReturnStatement(
                                            wrapperPos,
                                            CSharp.InvocationExpression(
                                                wrapperPos,
                                                StandardNames.temperCoreCoreAdaptGenerator.toStaticMember(wrapperPos),
                                                typeArgs = buildList {
                                                    add(promiseResultType.deepCopy())
                                                    typeParameters.mapTo(this) { (tf, tp) ->
                                                        when (tp) {
                                                            is CSharp.Identifier -> CSharp.TypeArgRef(
                                                                CSharp.Identifier(wrapperPos, tp.outName),
                                                                tf.definition,
                                                            )
                                                        }
                                                    }
                                                },
                                                args = buildList {
                                                    add(CSharp.Identifier(wrapperPos, helperId))
                                                    parameters.mapTo(this) {
                                                        when (it) {
                                                            is CSharp.FixedParameter ->
                                                                CSharp.Identifier(wrapperPos, it.name.outName)
                                                            // TODO: Do we need some kind of explode syntax here?
                                                            // Or should we be stripping the vararg declaration
                                                            // from the wrapper parameter declaration?
                                                            is CSharp.ParameterArray ->
                                                                CSharp.Identifier(wrapperPos, it.name.outName)
                                                        }
                                                    }
                                                },
                                            ),
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun processModuleInitBlock(block: TmpL.ModuleInitBlock) {
        for (statement in block.body.statements) {
            moduleInits.addAll(translateStatement(statement))
        }
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration) {
        val wantedType = varTypes[decl.name.name] as? Type2
        // Declare separately from init ...
        testOrModuleMembers.add(
            CSharp.FieldDecl(
                decl.pos,
                type = translateType(decl.type),
                mods = CSharp.FieldModifiers(
                    decl.pos,
                    modAccess = chooseVisibility(decl),
                    modStatic = CSharp.ModStatic.Static,
                ),
                variables = listOf(
                    CSharp.VariableDeclarator(
                        decl.pos,
                        variable = translateId(decl.name),
                        initializer = null,
                    ),
                ),
            ),
        )
        // ... so that any side effects in init can retain order in the static constructor. Init examples here:
        // https://learn.microsoft.com/en-us/dotnet/csharp/programming-guide/classes-and-structs/static-constructors
        decl.init?.let { init ->
            moduleInits.add(
                CSharp.ExpressionStatement(
                    decl.pos,
                    CSharp.Operation(
                        decl.pos,
                        left = translateId(decl.name),
                        operator = CSharp.Operator(decl.pos, CSharpOperator.Assign),
                        right = translateExpression(init, wantedType = wantedType),
                    ),
                ),
            )
        }
    }

    private fun processSupportCodeDeclaration(decl: TmpL.SupportCodeDeclaration) {
        when (val supportCode = decl.init.supportCode) {
            is CSharpDeclaredInline -> {
                // Just presume we can init in place for now. TODO Depends on support code implementations.
                testOrModuleMembers.add(
                    CSharp.FieldDecl(
                        decl.pos,
                        type = translateTypeFromFrontend(decl.pos, decl.init.type),
                        mods = CSharp.FieldModifiers(
                            decl.pos,
                            modAccess = chooseVisibility(decl),
                            modStatic = CSharp.ModStatic.Static,
                        ),
                        variables = listOf(
                            CSharp.VariableDeclarator(
                                decl.pos,
                                variable = translateId(decl.name),
                                initializer = supportCode.inlineToTree(decl.pos, translator = this),
                            ),
                        ),
                    ),
                )
            }

            else -> {}
        }
    }

    private fun processTest(test: TmpL.Test) {
        val id = translateId(test.name)
        dependenciesBuilder.addTest(module.libraryName, test, id.outName.outputNameText)
        val method = CSharp.MethodDecl(
            test.pos,
            id = id,
            attributes = listOf(
                CSharp.AttributeSection(
                    test.pos,
                    attributes = listOf(
                        CSharp.Attribute(
                            test.pos,
                            name = StandardNames.microsoftVisualStudioTestToolsUnitTestingTestMethod
                                .toTypeName(test.pos),
                        ),
                    ),
                ),
            ),
            mods = CSharp.MethodModifiers(test.pos, modAccess = CSharp.ModAccess.Public),
            parameters = emptyList(),
            whereConstraints = CSharp.WhereConstraints(test.pos.leftEdge, emptyList()),
            result = StandardNames.keyVoid.toType(test.pos),
            body = CSharp.BlockStatement(
                test.pos,
                statements = buildList {
                    // Test test = new Test();
                    val testParam = test.parameters.parameters.firstOrNull()!!
                    val type = translateType(testParam.type)
                    val testInstanceId = translateId(testParam.name)
                    val testDecl = CSharp.LocalVariableDecl(
                        test.pos,
                        type = type,
                        variables = listOf(
                            CSharp.VariableDeclarator(
                                test.pos,
                                variable = testInstanceId,
                                initializer = CSharp.ObjectCreationExpression(
                                    test.pos,
                                    type = type.deepCopy(),
                                    args = emptyList(),
                                ),
                            ),
                        ),
                    )
                    add(testDecl)
                    // try { ... } finally { test.softFailToHard(); }
                    val block = CSharp.TryStatement(
                        test.pos,
                        tryBlock = translateBlockStatement(test.body),
                        finallyBlock = CSharp.BlockStatement(
                            test.pos,
                            statements = listOf(
                                CSharp.ExpressionStatement(
                                    test.pos,
                                    expr = CSharp.InvocationExpression(
                                        test.pos,
                                        expr = CSharp.MemberAccess(
                                            test.pos,
                                            expr = testInstanceId.deepCopy(),
                                            id = "SoftFailToHard".toIdentifier(test.pos),
                                        ),
                                        args = listOf(),
                                    ),
                                ),
                            ),
                        ),
                    )
                    add(block)
                },
            ),
        )
        testClassMembers.add(method)
    }

    private fun processTypeDeclaration(decl: TmpL.TypeDeclaration) {
        typeDeclarationInProcess = decl
        try {
            // Extract getters and setters so we can pair them.
            val props = decl.members.filterIsInstance<TmpL.InstanceProperty>().associateBy { it.memberShape.symbol }
            val getters = decl.members.filterIsInstance<TmpL.Getter>().associateBy { it.dotName.dotNameText }
            val setters = decl.members.filterIsInstance<TmpL.Setter>().associateBy { it.dotName.dotNameText }
            val pos = decl.pos
            val id = translateId(
                decl.name,
                style = NameStyle.PrettyPascal,
            ).maybeToInterfaceName(decl.kind == TmpL.TypeDeclarationKind.Interface)
            typeNameInProcess = id.outName.outputNameText
            val (typeParameters, whereConstraints) = translateTypeParameters(decl.typeParameters)
            val clazz = CSharp.TypeDecl(
                pos,
                id = id,
                typeParameters = typeParameters.map { it.second },
                baseTypes = decl.superTypes.map { translateNominalType(it) },
                whereConstraints = whereConstraints,
                mods = CSharp.TypeModifiers(
                    pos,
                    modAccess = chooseVisibility(decl),
                    modTypeKind = translateTypeKind(decl.kind),
                ),
                members = decl.members.flatMap { member ->
                    translateMember(member, props = props, getters = getters, setters = setters)
                },
            )
            val fullNamespace = listOf(names.rootNamespace) + subspace
            val compilationUnit = CSharp.CompilationUnit(
                pos,
                decls = listOf(
                    CSharp.NamespaceDecl(
                        pos,
                        names = fullNamespace.map { it.toIdentifier(pos) },
                        decls = listOf(clazz),
                    ),
                ),
            )
            val dir = when {
                testInProcess -> dirPath(TEST_PROJECT_DIR)
                else -> dirPath(SRC_PROJECT_DIR)
            }.resolve(dirPath(subspace))
            fileSpecs.add(
                Backend.TranslatedFileSpecification(
                    path = dir.resolveFile("${clazz.id.outName.outputNameText}.cs"),
                    mimeType = CSharpBackend.mimeType,
                    content = compilationUnit,
                ),
            )
        } finally {
            typeDeclarationInProcess = null
            typeNameInProcess = null
        }
    }

    private fun translateActual(actual: TmpL.Actual, wantedType: Type2? = null): CSharp.Arg {
        // Mostly here to support named args explicitly. Maybe get around to that sometime.
        return translateActualExpression(actual, wantedType = wantedType)
    }

    private fun translateActualExpression(actual: TmpL.Actual, wantedType: Type2? = null): CSharp.Expression {
        return when {
            actual is TmpL.Expression -> translateExpression(actual, castIfFunction = true, wantedType = wantedType)
            else -> TODO()
        }
    }

    private fun translateAssignment(statement: TmpL.Assignment): CSharp.Statement =
        translateAssignment(statement.pos, statement.left, statement.right as TmpL.Expression)

    private fun translateAssignment(
        pos: Position,
        left: TmpL.Id,
        right: TmpL.Expression,
    ): CSharp.Statement {
        return CSharp.ExpressionStatement(
            pos,
            CSharp.Operation(
                pos,
                left = translateId(left),
                operator = CSharp.Operator(pos, CSharpOperator.Assign),
                right = translateExpression(right, wantedType = varTypes[left.name] as? Type2),
            ),
        )
    }

    private fun translateBlockStatement(
        block: TmpL.BlockStatement,
        prelude: List<CSharp.Statement> = listOf(),
    ): CSharp.BlockStatement {
        return CSharp.BlockStatement(
            block.pos,
            statements = prelude + block.statements.flatMap { translateStatement(it) },
        )
    }

    private fun translateBreakStatement(statement: TmpL.BreakStatement): CSharp.Statement {
        return when (val label = statement.label) {
            null -> CSharp.BreakStatement(statement.pos) // TODO If we ever generate switch, be careful here!!!
            // This works because of how we generate labeled blocks. Goto is different than break.
            else -> CSharp.GotoStatement(statement.pos, translateId(label.id))
        }
    }

    private fun translateContinueStatement(statement: TmpL.ContinueStatement): CSharp.Statement {
        return when (val label = statement.label) {
            null -> CSharp.ContinueStatement(statement.pos)
            else -> CSharp.GotoStatement(statement.pos, translateId(label.id))
        }
    }

    private fun translateCallExpression(call: TmpL.CallExpression): CSharp.Expression {
        return when (val fn = call.fn) {
            is TmpL.ConstructorReference -> translateConstructorReferenceCall(call)
            is TmpL.InlineSupportCodeWrapper -> when (val supportCode = fn.supportCode) {
                is CSharpInlineSupportCode -> supportCode.inlineToTree(
                    call.pos,
                    arguments = call.mapParameters { arg, wantedType, _ ->
                        TypedArg(translateActual(arg, wantedType = wantedType), arg.typeOrInvalid)
                    },
                    returnType = call.type,
                    translator = this,
                ) as CSharp.Expression

                else -> TODO()
            }

            else -> CSharp.InvocationExpression(
                call.pos,
                expr = translateCallable(fn),
                args = call.mapParameters { arg, wantedType, _ -> translateActual(arg, wantedType = wantedType) },
            )
        }
    }

    private fun translateCallable(callable: TmpL.Callable): CSharp.PrimaryExpression {
        return when (callable) {
            is TmpL.FnReference -> translateReference(callable) as CSharp.PrimaryExpression
            is TmpL.MethodReference -> translateMethodReference(callable)
            is TmpL.GarbageCallable -> makeGarbageExpression(callable.pos, callable.diagnostic?.text)
            is TmpL.FunInterfaceCallable ->
                translateExpression(callable.expr) as? CSharp.PrimaryExpression
                    ?: makeGarbageExpression(callable.pos, callable.expr.toString())
            is TmpL.InlineSupportCodeWrapper -> error("Handled when translating call")
            is TmpL.ConstructorReference -> error("Handled by translateConstructorReferenceCall")
        }
    }

    private fun translateCastExpression(expr: TmpL.CastExpression): CSharp.Expression {
        return when {
            expr.expr.type.isNullable() && !expr.type.isNullable() && !expr.type.isValueType() -> {
                CSharp.InvocationExpression(
                    expr.pos,
                    expr = StandardNames.temperCoreCoreCastToNonNull.toStaticMember(expr.pos),
                    typeArgs = listOf(translateType(expr.checkedType)),
                    args = listOf(translateExpression(expr.expr)),
                )
            }

            else -> CSharp.CastExpression(
                expr.pos,
                type = translateType(expr.checkedType),
                expr = translateExpression(expr.expr),
            )
        }
    }

    internal fun translateIsNull(
        pos: Position,
        expr: CSharp.Expression,
        expressionType: Type2,
    ): CSharp.Expression =
        if (expressionType.isOptionalTypeArg()) {
            // translatedExpr.HasValue
            CSharp.Operation(
                pos,
                null,
                CSharp.Operator(expr.pos.leftEdge, CSharpOperator.BoolComplement),
                CSharp.MemberAccess(
                    expr.pos,
                    expr as CSharp.PrimaryExpression,
                    CSharp.Identifier(
                        expr.pos.rightEdge,
                        OutName("HasValue", null),
                    ),
                ),
            )
        } else {
            CSharp.Operation(
                pos,
                expr,
                CSharp.Operator(pos.rightEdge, CSharpOperator.Equals),
                makeKeywordReference(pos.rightEdge, "null"),
            )
        }

    private fun translateInstanceofExpression(expr: TmpL.InstanceOfExpression): CSharp.Expression {
        val expressionType = expr.expr.type
        val translatedExpression = translateExpression(expr.expr)

        val checkedType = expr.checkedType.ot
        if (
            checkedType is TmpL.NominalType &&
            checkedType.typeName.sourceDefinition == WellKnownTypes.nullTypeDefinition
        ) {
            return translateIsNull(expr.pos, translatedExpression, expressionType)
        }

        val checkedTypeDef = (checkedType.withoutNullOrBubble as? TmpL.NominalType)
            ?.typeName?.sourceDefinition
        val needsNullCheck = expressionType.isNullable() && !canBeNull(checkedType) &&
            !expressionType.isValueType()
        val needsTypeCheck = if (checkedTypeDef is TypeShape) {
            val checkedSuperTypeTree =
                typeContext2.superTypeTreeOf(expressionType.withNullity(Nullity.NonNull))
            checkedSuperTypeTree[checkedTypeDef].isEmpty()
        } else {
            false
        }

        return when {
            // (x as T) != null
            needsNullCheck && needsTypeCheck -> CSharp.Operation(
                expr.pos,
                CSharp.Operation(
                    expr.pos,
                    translatedExpression,
                    CSharp.Operator(expr.expr.pos.rightEdge, CSharpOperator.As),
                    translateType(expr.checkedType) as CSharp.Expression,
                ),
                CSharp.Operator(expr.pos.rightEdge, CSharpOperator.NotEquals),
                makeKeywordReference(expr.pos.rightEdge, "null"),
            )
            // x != null
            needsNullCheck -> CSharp.Operation(
                expr.pos,
                translatedExpression,
                CSharp.Operator(expr.expr.pos.rightEdge, CSharpOperator.NotEquals),
                makeKeywordReference(expr.checkedType.pos, "null"),
            )
            // x is T
            needsTypeCheck -> CSharp.Operation(
                expr.pos,
                translatedExpression,
                CSharp.Operator(expr.expr.pos.rightEdge, CSharpOperator.Is),
                translateType(expr.checkedType) as CSharp.Expression,
            )
            else -> makeKeywordReference(expr.pos, "true")
        }
    }

    private fun translateNotNullExpression(expr: TmpL.UncheckedNotNullExpression): CSharp.Expression {
        val pos = expr.pos

        // TODO: should we be doing any collections adjustment after verifying that it's not null?
        val translated = translateExpression(expr.expression, wantedType = expr.type)
        return when {
            // x: T?        /   Optional<T>
            // x => x.Value
            expr.expression.type.isOptionalTypeArg() ||
                // x: int?       /   Nullable<T>
                // x => x.Value
                expr.type.isValueType() ->
                CSharp.MemberAccess(
                    pos = pos,
                    expr = translated as CSharp.PrimaryExpression,
                    id = "Value".toIdentifier(pos),
                )

            // x => x!
            // Typically just get a warning without this, but better to avoid those.
            // https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/operators/null-forgiving
            // TODO C# < 8 doesn't indicate nullable for reference types.
            else -> CSharp.Operation(
                pos = pos,
                left = translated,
                operator = CSharp.Operator(pos, CSharpOperator.NullForgiving),
                right = null,
            )
        }
    }

    private fun translateConstructor(constructor: TmpL.Constructor): List<CSharp.ClassMember> {
        constructor.memberShape.enclosingType
        return buildList {
            add(
                CSharp.MethodDecl(
                    constructor.pos,
                    id = CSharp.Identifier(
                        constructor.pos,
                        OutName(constructor.memberShape.enclosingType.name.toStyle(NameStyle.PrettyPascal), null),
                    ),
                    result = null,
                    mods = CSharp.MethodModifiers(
                        constructor.pos,
                        modAccess = CSharp.ModAccess.Public, // letting type visibility control access for now
                    ),
                    parameters = translateParameters(constructor.parameters),
                    whereConstraints = CSharp.WhereConstraints(constructor.pos.leftEdge, emptyList()),
                    body = translateBlockStatement(constructor.body),
                ),
            )
        }
    }

    private fun translateConstructorReferenceCall(call: TmpL.CallExpression): CSharp.Expression {
        if (call.type == WellKnownTypes.invalidType2) {
            return makeGarbageExpression(call.pos, "Invalid type for `new`")
        }
        // The constructor call gives us the type we're constructing.
        // The constructor reference fn inside the call is static type *Type*.
        val nominalType = call.type as DefinedNonNullType
        // Overriding type here rather than in support network lets us reuse more logic more easily.
        val name = when (nominalType.definition) {
            WellKnownTypes.listBuilderTypeDefinition -> StandardNames.systemCollectionsGenericList
            else -> null
        }
        // For constructor calls, we only have the actual type args through the frontend static type.
        // From digging, it looks like other backends so far don't need them, but without support method trickery to
        // infer them, we don't get them for free on constructor calls in C#. No `<>` diamond operator like in Java.
        val type = translateNominalTypeFromFrontend(call.pos, nominalType, name = name)
        return CSharp.ObjectCreationExpression(
            call.pos,
            type = type,
            args = call.mapParameters { arg, wantedType, _ -> translateActual(arg, wantedType = wantedType) },
        )
    }

    private fun translateDotName(name: TmpL.DotName, typeName: String): CSharp.Identifier {
        // C# doesn't allow non-constructor member names to match enclosing class names, so uniquify as needed.
        val safeName = makeSafeNameMaybe(typeName = typeName, memberName = name.dotNameText.camelToPascal())
        return CSharp.Identifier(name.pos, OutName(safeName, null))
    }

    private fun translateExportedGlobalName(pos: Position, name: ExportedName): CSharp.Expression {
        val baseNameText = name.toStyle(NameStyle.PrettyPascal)
        val baseId = baseNameText.toIdentifier(pos)
        return when (name.origin.loc) {
            loc -> {
                names.putSelection(loc, name, baseNameText)
                baseId
            }
            else -> {
                val subspace = chooseSubspace(name.origin.loc as ModuleName)
                val (_, typeIds) = makeNamespaceAndGlobalName(findRootNamespace(name), subspace)
                val type = CSharp.UnboundType(
                    CSharp.QualTypeName(pos, id = typeIds.map { it.toIdentifier(pos) }),
                )
                CSharp.MemberAccess(pos, expr = type, id = baseId)
            }
        }
    }

    private fun translateExpression(
        expr: TmpL.Expression,
        castIfFunction: Boolean = false,
        wantedType: Type2? = null,
    ): CSharp.Expression {
        val result = when (expr) {
            is TmpL.AwaitExpression -> error("await not caught by statement path")
            is TmpL.BubbleSentinel -> TODO()
            is TmpL.CallExpression -> translateCallExpression(expr)
            is TmpL.CastExpression -> translateCastExpression(expr)
            is TmpL.FunInterfaceExpression -> translateCallable(expr.callable)
            is TmpL.InstanceOfExpression -> translateInstanceofExpression(expr)
            is TmpL.UncheckedNotNullExpression -> translateNotNullExpression(expr)
            is TmpL.GarbageExpression -> translateGarbageExpression(expr)
            is TmpL.GetProperty -> translatePropertyReference(expr).second
            is TmpL.InfixOperation -> translateInfixOperation(expr)
            is TmpL.PrefixOperation -> translatePrefixOperation(expr)
            is TmpL.Reference -> translateReference(expr, castIfFunction = castIfFunction)
            is TmpL.RestParameterCountExpression -> TODO()
            is TmpL.RestParameterExpression -> TODO()
            is TmpL.This -> translateThis(expr)
            is TmpL.ValueReference -> translateValueReference(expr)
        }
        return result.wrapCollectionTypeIfNeeded(
            type = findMainType(expr.type),
            wantedType = wantedType?.let { findMainType(it) },
        )
    }

    private fun translateExpressionStatement(statement: TmpL.ExpressionStatement): CSharp.Statement? {
        return when (val expr = translateExpression(statement.expression)) {
            is CSharp.Identifier -> return null // ignore stray `null` mentions at least
            is CSharp.Operation -> if (expr.operator.operator.makesStatement) {
                expr
            } else {
                makeIgnore(expr)
            }
            is CSharp.StatementExpression -> expr
            else -> makeIgnore(expr)
        }.let { CSharp.ExpressionStatement(statement.pos, it) }
    }

    private fun translateFloat64Value(pos: Position, value: ParseDouble): CSharp.Expression {
        return when (value) {
            ParseDouble.NegativeInfinity -> StandardNames.keyDoubleNegativeInfinity.toStaticMember(pos)
            ParseDouble.PositiveInfinity -> StandardNames.keyDoublePositiveInfinity.toStaticMember(pos)
            ParseDouble.NaN -> StandardNames.keyDoubleNan.toStaticMember(pos)
            ParseDouble.NegativeZero,
            is ParseDouble.RegularNegative,
            -> CSharp.Operation(
                pos,
                left = null,
                operator = CSharp.Operator(pos, CSharpOperator.Minus),
                right = CSharp.NumberLiteral(pos, abs(value.value)),
            )

            else -> when (value.value) {
                Math.E -> StandardNames.systemMathE.toStaticMember(pos)
                Math.PI -> StandardNames.systemMathPi.toStaticMember(pos)
                else -> CSharp.NumberLiteral(pos, value.value)
            }
        }
    }

    private fun translateFormal(formal: TmpL.Formal, preventOptional: Boolean = false): CSharp.MethodParameter {
        val type = translateType(formal.type)
        return CSharp.FixedParameter(
            formal.pos,
            name = translateId(
                formal.name,
                style = when {
                    formal.optional -> NameStyle.PrettyCamel
                    else -> NameStyle.Ugly
                },
            ),
            type = type,
            defaultValue = when {
                formal.optional && !preventOptional -> makeKeywordReference(formal.pos, "null")
                else -> null
            },
        )
    }

    private fun translateFunctionType(type: TmpL.FunctionType): CSharp.Type {
        val voidish = type.returnType.isVoidish()
        val funTypeName = returnVoidishToCSharpFunctionNamedType(type.pos, voidish = voidish)
        return when {
            voidish && type.valueFormals.formals.isEmpty() -> CSharp.UnboundType(funTypeName)
            else -> CSharp.ConstructedType(
                type.pos,
                type = funTypeName,
                args = buildList {
                    for (formal in type.valueFormals.formals) {
                        add(translateType(formal.type))
                    }
                    if (!voidish) {
                        add(translateType(type.returnType))
                    }
                },
            )
        }
    }

    private fun translateGarbageExpression(expr: TmpL.GarbageExpression) =
        makeGarbageExpression(expr.pos, expr.diagnostic?.text)

    private fun translateGetterSetter(
        props: Map<Symbol, TmpL.InstanceProperty>,
        getter: TmpL.Getter?,
        setter: TmpL.Setter?,
    ): List<CSharp.ClassMember> {
        val name = getter?.dotName ?: setter!!.dotName
        val pos = getter?.pos ?: setter!!.pos
        val type = getter?.returnType ?: setter!!.parameters.parameters[1].type
        val visibility = listOfNotNull(getter, setter).map { it.visibility }.maxBy { it.visibility.ordinal }
        val id = translateDotName(name, typeName = typeNameInProcess!!)
        val wasPut = names.putSelection(loc, (getter?.name ?: setter!!.name).name, id.outName.outputNameText)
        if (!wasPut) {
            // Auto-generated accessors, so look for the name of the internal prop.
            props[getter?.memberShape?.symbol ?: setter!!.memberShape.symbol]?.let { prop ->
                names.putSelection(loc, prop.name.name, id.outName.outputNameText)
            }
        }
        val extras = when (typeDeclarationInProcess!!.kind) {
            TmpL.TypeDeclarationKind.Interface -> mutableListOf<CSharp.ClassMember>()
            else -> null
        }
        val property = CSharp.PropertyDecl(
            pos,
            id = id,
            type = translateType(type),
            mods = CSharp.MethodModifiers(
                pos,
                modAccess = translateMemberVisibility(visibility),
            ),
            accessors = buildList {
                if (getter != null) {
                    add(
                        CSharp.PropertyAccessor(
                            getter.pos,
                            mods = CSharp.PropertyAccessorModifiers(
                                pos,
                                modAccess = translateMemberVisibility(getter.visibility, default = visibility),
                                modAccessorKind = CSharp.ModAccessorKind.Get,
                            ),
                            body = extractDefaultMaybe(extras = extras, id = id, method = getter, prefix = "Get")
                                ?: translateMethodBody(getter.body),
                        ),
                    )
                }
                if (setter != null) {
                    // We don't nest class definitions, so we only need one setter value name at a time.
                    setterValueName = setter.parameters.parameters[1].name.name
                    try {
                        add(
                            CSharp.PropertyAccessor(
                                setter.pos,
                                mods = CSharp.PropertyAccessorModifiers(
                                    pos,
                                    modAccess = translateMemberVisibility(setter.visibility, default = visibility),
                                    modAccessorKind = CSharp.ModAccessorKind.Set,
                                ),
                                body = extractDefaultMaybe(extras = extras, id = id, method = setter, prefix = "Set")
                                    ?: translateMethodBody(setter.body),
                            ),
                        )
                    } finally {
                        setterValueName = null
                    }
                }
            },
        )
        return buildList {
            add(property)
            extras?.let { addAll(it) }
        }
    }

    internal fun translateId(id: TmpL.Id, style: NameStyle = NameStyle.Ugly): CSharp.Identifier {
        return translateIdMaybe(id = id, style = style) as CSharp.Identifier
    }

    /** Unless we transform some tmpl imports to static imports, not all ids become simple ids. */
    private fun translateIdMaybe(id: TmpL.Id, style: NameStyle = NameStyle.Ugly): CSharp.Expression {
        return translateName(id.pos, name = id.name, style = style)
    }

    private fun unusedName(pos: Position, nameHint: String): CSharp.Identifier =
        translateName(pos, nameMaker.unusedTemporaryName(nameHint))
            as CSharp.Identifier

    private fun translateIfStatement(ifElse: TmpL.IfStatement): CSharp.Statement {
        return CSharp.IfStatement(
            ifElse.pos,
            // Translating test lets us know if we're handling an optional check.
            test = translateExpression(ifElse.test),
            consequent = translateStatement(ifElse.consequent).toStatementOrBlock(ifElse.pos.leftEdge),
            // Unset checks don't nest, so we can use flat state tracking here.
            alternate = ifElse.alternate?.let { translateStatement(it).toStatementBlockOrNull(ifElse.pos) },
        )
    }

    private fun translateInfixOperation(expr: TmpL.InfixOperation): CSharp.Expression {
        return CSharp.Operation(
            expr.pos,
            left = translateExpression(expr.left),
            operator = translateInfixOperator(expr.op),
            right = translateExpression(expr.right),
        )
    }

    private fun translateInfixOperator(op: TmpL.InfixOperator): CSharp.Operator {
        return CSharp.Operator(
            op.pos,
            operator = when (op.tmpLOperator) {
                TmpLOperator.AmpAmp -> CSharpOperator.LogicalAnd
                TmpLOperator.BarBar -> CSharpOperator.LogicalOr
                TmpLOperator.EqEqInt -> CSharpOperator.Equals
                TmpLOperator.GeInt -> CSharpOperator.GreaterEquals
                TmpLOperator.GtInt -> CSharpOperator.GreaterThan
                TmpLOperator.LeInt -> CSharpOperator.LessEquals
                TmpLOperator.LtInt -> CSharpOperator.LessThan
                TmpLOperator.PlusInt -> CSharpOperator.Addition
            },
        )
    }

    private fun translateInstanceProperty(prop: TmpL.InstanceProperty): CSharp.ClassMember? {
        if (prop.memberShape.abstractness == Abstractness.Abstract) {
            return null
        }
        return CSharp.FieldDecl(
            prop.pos,
            mods = CSharp.FieldModifiers(
                prop.pos,
                modAccess = CSharp.ModAccess.Private,
                modWritable = when (prop.assignOnce) {
                    true -> CSharp.ModWritable.ReadOnly
                    false -> CSharp.ModWritable.ReadWrite
                },
            ),
            type = translateType(prop.type),
            variables = listOf(
                CSharp.VariableDeclarator(
                    prop.pos,
                    variable = translateId(prop.name),
                    initializer = null,
                ),
            ),
        )
    }

    private fun translateLabeledStatement(statement: TmpL.LabeledStatement): CSharp.Statement {
        // Makes: `{ statement; label: {} }`, which works for break.
        // TODO For continue, we'll need to insert something into the statement, if it's a loop.
        return CSharp.BlockStatement(
            statement.pos,
            statements =
            translateStatement(statement.statement) + listOf(
                CSharp.LabeledStatement(
                    statement.pos,
                    label = translateId(statement.label.id),
                    statement = CSharp.BlockStatement(statement.pos, listOf()),
                ),
            ),
        )
    }

    private fun translateLocalDeclaration(decl: TmpL.LocalDeclaration): CSharp.Statement {
        val wantedType = varTypes[decl.name.name] as? Type2
        return CSharp.LocalVariableDecl(
            decl.pos,
            type = translateType(decl.type),
            variables = listOf(
                CSharp.VariableDeclarator(
                    decl.pos,
                    variable = translateId(decl.name),
                    initializer = decl.init?.let { translateExpression(it, wantedType = wantedType) },
                ),
            ),
        )
    }

    private fun translateLocalFunctionDeclaration(decl: TmpL.LocalFunctionDeclaration): List<CSharp.Statement> {
        return translateFunctionDeclarationToClassMembers(decl, mods = null)
    }

    private fun translateMember(
        member: TmpL.MemberOrGarbage,
        props: Map<Symbol, TmpL.InstanceProperty>,
        getters: Map<String, TmpL.Getter>,
        setters: Map<String, TmpL.Setter>,
    ): List<CSharp.ClassMember> {
        return withFunctionContext {
            when (member) {
                is TmpL.GarbageStatement -> TODO()
                is TmpL.Getter ->
                    translateGetterSetter(props = props, getter = member, setter = setters[member.dotName.dotNameText])
                is TmpL.NormalMethod -> translateMethod(member, modStatic = CSharp.ModStatic.Instance)
                is TmpL.StaticMethod -> translateMethod(member, modStatic = CSharp.ModStatic.Static)
                is TmpL.Constructor -> translateConstructor(member)
                is TmpL.InstanceProperty -> listOfNotNull(translateInstanceProperty(member))
                is TmpL.StaticProperty -> listOf(translateStaticProperty(member))
                is TmpL.Setter -> when (member.dotName.dotNameText in getters) {
                    true -> listOf() // handled with getter
                    false -> translateGetterSetter(props = props, getter = null, setter = member)
                }
            }
        }
    }

    private fun translateMemberVisibility(
        visibility: TmpL.VisibilityModifier,
        default: TmpL.VisibilityModifier?,
    ): CSharp.ModAccess? {
        return when (visibility.visibility) {
            default?.visibility -> null
            TmpL.Visibility.Public -> CSharp.ModAccess.Public
            else -> CSharp.ModAccess.Private
        }
    }

    private fun translateMemberVisibility(visibility: TmpL.VisibilityModifier): CSharp.ModAccess {
        return translateMemberVisibility(visibility, default = null)!!
    }

    private fun translateMethodBody(body: TmpL.BlockStatement?): CSharp.BlockStatement? {
        return when (body.isPureVirtual()) {
            true -> null
            false -> body?.let { translateBlockStatement(it, prelude = makeRestAssignment()) }
        }
    }

    private fun translateMethodReference(ref: TmpL.MethodReference): CSharp.PrimaryExpression {
        val (typeDefinition, expr) = translateSubject(ref.subject)
        val typeName = (typeDefinition ?: return expr).name.toStyle(NameStyle.PrettyPascal)
        val method = ref.method
            ?: return makeGarbageExpression(ref.pos, "Missing method in $typeName")
        val methodName = makeSafeNameMaybe(
            typeName = typeName,
            memberName = method.name.toStyle(NameStyle.PrettyPascal),
        )
        return CSharp.MemberAccess(
            ref.pos,
            expr = expr,
            id = CSharp.Identifier(ref.methodName.pos, OutName(methodName, method.name)),
        )
    }

    private fun translateName(pos: Position, name: ResolvedName, style: NameStyle = NameStyle.Ugly): CSharp.Expression {
        // Styling depends on a variety of matters, including context.
        // TODO Prepass with user configuration infrastructure.
        val effective = imports[name] ?: name
        var actualStyle = style
        val nameText = when (effective) {
            setterValueName -> "value"
            is ExportedName -> return translateExportedGlobalName(pos, name = effective)
            // 3 "_" on temps for better uniqueness.
            is Temporary -> "${effective.nameHint.cleaned()}___${effective.uid}"
            else -> {
                actualStyle = when {
                    name in functionContextStack.last().optionals -> NameStyle.PrettyCamel
                    else -> style
                }
                effective.toStyle(actualStyle)
            }
        }
        if (actualStyle != NameStyle.Ugly) {
            names.putSelection(loc, name, nameText)
        }
        return OutName(nameText, effective).toIdentifier(pos)
    }

    private fun translateNominalType(type: TmpL.NominalType): CSharp.Type {
        val csharpType = when (type.typeName) {
            is TmpL.ConnectedToTypeName -> translateTypeName(type.typeName).second
            is TmpL.TemperTypeName -> {
                val definition = type.typeName.sourceDefinition
                translateUnconnectedTypeDefinition(definition, pos = type.pos)
            }
        }
        return if (type.params.isEmpty()) {
            csharpType
        } else {
            val csharpTypeName = when (csharpType) {
                is CSharp.ConstructedType,
                is CSharp.TypeArgRef,
                is CSharp.NullableType,
                -> return csharpType
                is CSharp.UnboundType -> csharpType.name
            }
            val args = type.params.map {
                translateType(it)
            }
            // TODO: Is ConstructedType appropriate for an interface type or reference to a type parameter?
            CSharp.ConstructedType(
                type.pos,
                type = csharpTypeName as CSharp.QualTypeName, // Expected to apply when we have bindings.
                args = args,
            )
        }
    }

    /** Similar to [translateNominalType] but works on frontend types, also with an optional name override. */
    private fun translateNominalTypeFromFrontend(
        pos: Position,
        nominalType: DefinedNonNullType,
        name: TypeName? = null,
    ): CSharp.Type {
        check( // We should have unpacked any special stuff earlier
            withType(
                nominalType,
                fallback = { true },
                fn = { _, _, _ -> false },
                result = { _, _, _ -> false },
            ),
        ) { "$nominalType" }
        val definition = nominalType.definition

        // TODO: Shouldn't we have a TmpL.TypeName here?
        val connectedKey = definition.metadata[connectedSymbol, TString]
        val (typeName: CSharp.UnboundTypeName, bindings: List<Type2>) = connectedKey?.let {
            val connectedType = CSharpSupportNetwork.translatedConnectedType(pos, connectedKey, genre, nominalType)
            connectedType?.let { connectedType.first.toTypeName(pos) to connectedType.second }
        } ?: run {
            if (name != null) {
                name.toTypeName(pos) to nominalType.bindings
            } else {
                when (val type = translateUnconnectedTypeDefinition(definition, pos = pos)) {
                    is CSharp.NullableType,
                    is CSharp.ConstructedType,
                    is CSharp.TypeArgRef,
                    -> return@translateNominalTypeFromFrontend type
                    is CSharp.UnboundType -> type.name to nominalType.bindings
                }
            }
        }

        return when (bindings.isEmpty()) {
            true -> CSharp.UnboundType(typeName)
            false -> {
                val args = bindings.map {
                    translateTypeFromFrontend(pos, it)
                }
                CSharp.ConstructedType(
                    pos = typeName.pos,
                    type = typeName, // Expected to apply when we have bindings.
                    args = args,
                )
            }
        }
    }

    private fun translateMethod(method: TmpL.Method, modStatic: CSharp.ModStatic): List<CSharp.ClassMember> {
        functionContextStack.last().returnType = toSigBestEffort(method.memberShape.descriptor).orInvalid.returnType2
        return buildList {
            val wantedId = translateId(method.name, style = NameStyle.PrettyPascal)
            var id = when (val wantedName = wantedId.outName.outputNameText) {
                typeNameInProcess ->
                    CSharp.Identifier(wantedId.pos, OutName(makeSafeName(wantedName), wantedId.outName.sourceName))

                else -> wantedId
            }
            findRest(method)
            markVoidish(method)
            val pos = method.pos
            val (typeParameters, whereConstraints) = translateTypeParameters(method.typeParameters)
            val resultType = translateType(method.returnType)
            val isVoidish = method.returnType.isVoidish()
            val parameters = translateParameters(method.parameters)
            var modAccess = translateMemberVisibility((method as TmpL.DotAccessible).visibility)

            val adjustments = (method as? TmpL.InstanceMethod)?.adjustments

            if (adjustments != null) {
                val inputAdjustments = adjustments.inputAdjustments
                val outputAdjustment = adjustments.outputAdjustment

                val bodyPos = method.body?.pos ?: pos.rightEdge
                val adjustedParameters = parameters.mapIndexed { i, param ->
                    val paramCopy = param.deepCopy()
                    val adj = inputAdjustments.getOrNull(i + 1)
                    if (adj is WrappedInOptional) {
                        val paramTypeCopy = when (val t = param.type) {
                            is CSharp.NullableType -> t.type
                            else -> t
                        }.deepCopy()
                        val adjustedType = optionalTypeOf(paramTypeCopy)
                        when (paramCopy) {
                            is CSharp.FixedParameter -> paramCopy.type = adjustedType
                            is CSharp.ParameterArray -> paramCopy.type = adjustedType
                        }
                    }
                    paramCopy
                }
                var wrapperResultType = resultType
                if (outputAdjustment != null) {
                    wrapperResultType = optionalTypeOf(
                        (wrapperResultType as? CSharp.NullableType)?.type
                            ?: wrapperResultType,
                    )
                }
                val privateId = translateId(TmpL.Id(id.pos, method.memberShape.name as ResolvedName))
                var callToPrivate = CSharp.InvocationExpression(
                    pos = bodyPos,
                    expr = privateId.deepCopy(),
                    args = parameters.mapIndexed { i, p ->
                        val ref = p.name.deepCopy()
                        if (inputAdjustments.getOrNull(i + 1) is WrappedInOptional) {
                            CSharp.CastExpression(
                                ref.pos,
                                p.type.deepCopy(),
                                ref,
                            )
                        } else {
                            ref
                        }
                    },
                )
                if (outputAdjustment is WrappedInOptional) {
                    callToPrivate = CSharp.InvocationExpression(
                        pos = callToPrivate.pos,
                        expr = CSharp.MemberAccess(
                            callToPrivate.pos.leftEdge,
                            StandardNames.temperCoreOptional.toType(callToPrivate.pos.leftEdge),
                            "Of".toIdentifier(callToPrivate.pos.leftEdge),
                        ),
                        typeArgs = listOf(
                            translateTypeFromFrontend(callToPrivate.pos.leftEdge, outputAdjustment.nonNullableType),
                        ),
                        args = listOf(callToPrivate),
                    )
                }
                add(
                    CSharp.MethodDecl(
                        pos = pos,
                        id = id.deepCopy(),
                        result = wrapperResultType.deepCopy(),
                        mods = CSharp.MethodModifiers(
                            pos.leftEdge,
                            modAccess = modAccess,
                            modStatic = modStatic,
                        ),
                        typeParameters = typeParameters.map { it.second.deepCopy() },
                        parameters = adjustedParameters,
                        whereConstraints = whereConstraints.deepCopy(),
                        body = CSharp.BlockStatement(
                            pos = bodyPos,
                            listOf(
                                if (isVoidish) {
                                    CSharp.ExpressionStatement(bodyPos, callToPrivate)
                                } else {
                                    CSharp.ReturnStatement(bodyPos, callToPrivate)
                                },
                            ),
                        ),
                    ),
                )

                // The specialized method needs to be private so that overriding
                // in a C# sub-type works.
                modAccess = CSharp.ModAccess.Private
                id = privateId
            }

            add(
                CSharp.MethodDecl(
                    pos = pos,
                    id = id,
                    result = resultType,
                    mods = CSharp.MethodModifiers(
                        pos.leftEdge,
                        modAccess = modAccess,
                        modStatic = modStatic,
                    ),
                    typeParameters = typeParameters.map { it.second },
                    parameters = parameters,
                    whereConstraints = whereConstraints,
                    body = extractDefaultMaybe(extras = this, id = id, prefix = "", method = method)
                        ?: translateMethodBody(method.body),
                ),
            )
        }
    }

    /** Retains explicit `this` parameter if [keepThis]. */
    private fun translateParameters(
        parameters: TmpL.Parameters,
        keepThis: Boolean = false,
    ): List<CSharp.MethodParameter> {
        val thisName = parameters.thisName?.name
        val firstOptionalIndex = parameters.parameters.indexOfFirst { it.optional }
        if (firstOptionalIndex >= 0) {
            functionContextStack.last().optionals =
                parameters.parameters.filter { it.optional }.map { it.name.name }.toSet()
        }
        val hasRequiredAfterOptional = when {
            firstOptionalIndex < 0 -> false
            else -> {
                val lastRequiredIndex = parameters.parameters.indexOfLast { !it.optional }
                lastRequiredIndex > firstOptionalIndex
            }
        }
        return buildList {
            for (parameter in parameters.parameters) {
                if (keepThis || parameter.name.name != thisName) {
                    add(translateFormal(parameter, preventOptional = hasRequiredAfterOptional))
                }
            }
            parameters.restParameter?.let { add(translateRestFormal(it)) }
        }
    }

    private fun translatePrefixOperation(expr: TmpL.PrefixOperation): CSharp.Expression {
        return when (expr.op.kind) {
            TmpLOperatorDefinition.Bang -> CSharp.Operation(
                expr.pos,
                left = null,
                operator = CSharp.Operator(expr.op.pos, CSharpOperator.BoolComplement),
                right = translateExpression(expr.operand),
            )

            TmpLOperatorDefinition.PreDash -> TODO()
        }
    }

    private fun translatePropertyId(property: TmpL.PropertyId, typeName: String): CSharp.Identifier {
        return when (property) {
            is TmpL.ExternalPropertyId -> translateDotName(property.name, typeName = typeName)
            is TmpL.InternalPropertyId -> translateId(property.name)
        }
    }

    private fun translatePropertyReference(
        get: TmpL.PropertyReference,
        findType: Boolean = false,
    ): Pair<Type2?, CSharp.Expression> {
        val (typeDefinition, expr) = translateSubject(get.subject)
        val typeName = (typeDefinition ?: return null to expr).name.toStyle(NameStyle.PrettyPascal)
        val type = when (findType) {
            true -> (typeDefinition as? TypeShape)?.let { shape ->
                shape.properties.find { shapeProperty ->
                    when (val property = get.property) {
                        is TmpL.ExternalPropertyId -> property.name.dotNameText == shapeProperty.name.displayName
                        is TmpL.InternalPropertyId -> property.name.name == shapeProperty.name
                    }
                }
            }?.descriptor

            false -> null
        }
        return type to CSharp.MemberAccess(
            get.pos,
            expr = expr,
            id = translatePropertyId(get.property, typeName = typeName),
        )
    }

    private fun translateReference(reference: TmpL.AnyReference, castIfFunction: Boolean = false): CSharp.Expression {
        val pos = reference.pos
        val name = reference.id.name
        val id = translateIdMaybe(reference.id)
        return when {
            // Reference from other types to module globals.
            (typeDeclarationInProcess != null || testInProcess) && name in moduleMemberNames -> CSharp.MemberAccess(
                pos,
                expr = CSharp.UnboundType(
                    CSharp.QualTypeName(pos, id = qualifiedGlobalClassName.map { it.toIdentifier(pos) }),
                ),
                id = id as CSharp.Identifier,
            )

            else -> id
        }.let { ref ->
            if (castIfFunction) {
                val sig = when (val descriptor = reference.type) {
                    is Type2 -> withType(
                        descriptor,
                        fallback = { null },
                        fn = { _, sig, _ -> sig },
                    )
                    is Signature2 -> descriptor
                }
                // Actually needed only some of the times we're putting it in now.
                // For example, we *shouldn't* need it when it's already a var reference with Func type.
                // Also mostly seems to be an issue when C# has to infer type actuals in a call.
                // TODO Be more selective about when we actually need this.
                if (sig != null) {
                    CSharp.CastExpression(
                        pos = pos,
                        type = translateTypeFromFrontend(pos, sig = sig),
                        expr = ref,
                    )
                } else {
                    ref
                }
            } else {
                ref
            }
        }
    }

    private fun translateRestFormal(formal: TmpL.RestFormal): CSharp.MethodParameter {
        return CSharp.ParameterArray(
            formal.pos,
            name = paramsify(translateId(formal.name)),
            type = translateType(formal.type),
        )
    }

    private fun translateReturnStatement(ret: TmpL.ReturnStatement): CSharp.Statement? {
        val expr = ret.expression
        return when {
            expr != null && functionContextStack.last().voidish -> {
                // Something that returns `bubble()` for type `Bubble` ends up here. Maybe other cases?
                val translated = when (val translated = translateExpression(expr)) {
                    is CSharp.StatementExpression -> translated
                    else -> makeIgnore(translated)
                }
                CSharp.ExpressionStatement(ret.pos, expr = translated)
            }

            setterValueName == null -> {
                val returnType = functionContextStack.lastOrNull()?.returnType
                val wantedType = returnType?.let {
                    excludeBubble(it)
                }
                CSharp.ReturnStatement(
                    ret.pos,
                    expr = expr?.let { translateExpression(it, wantedType = wantedType) },
                )
            }

            // Refuse returns inside setters, which we generate for default `var` properties.
            // TODO Track better than we're not inside some lambda inside a setter.
            else -> null
        }
    }

    private fun translateSetProperty(statement: TmpL.SetProperty): CSharp.Statement {
        val (wantedType, property) = translatePropertyReference(statement.left, findType = true)
        return CSharp.ExpressionStatement(
            statement.pos,
            CSharp.Operation(
                statement.pos,
                left = property,
                operator = CSharp.Operator(statement.pos, CSharpOperator.Assign),
                right = translateExpression(statement.right, wantedType = wantedType),
            ),
        )
    }

    private fun translateStatement(statement: TmpL.Statement): List<CSharp.Statement> {
        run {
            // C#'s `yield return` is statement level, so we can't just turn
            // `x = await p` into an expression-like use of `yield return`.
            val yieldingStmts = tryTranslateYieldingStatement(statement)
            if (yieldingStmts != null) {
                return@translateStatement yieldingStmts
            }
        }
        return when (statement) {
            is TmpL.Assignment -> translateAssignment(statement)
            is TmpL.BoilerplateCodeFoldEnd -> TODO()
            is TmpL.BoilerplateCodeFoldStart -> TODO()
            is TmpL.BreakStatement -> translateBreakStatement(statement)
            is TmpL.ContinueStatement -> translateContinueStatement(statement)
            is TmpL.EmbeddedComment -> TODO()
            is TmpL.ExpressionStatement -> return listOfNotNull(translateExpressionStatement(statement))
            is TmpL.GarbageStatement -> TODO()
            is TmpL.HandlerScope -> TODO()
            is TmpL.LocalDeclaration -> translateLocalDeclaration(statement)
            is TmpL.LocalFunctionDeclaration -> return translateLocalFunctionDeclaration(statement)
            is TmpL.ModuleInitFailed -> TODO()
            // Currently compute jumps are only used with coroutine strategy mode not
            // opted into by the SupportNetwork
            is TmpL.ComputedJumpStatement -> TODO()
            is TmpL.BlockStatement -> translateBlockStatement(statement)
            is TmpL.IfStatement -> translateIfStatement(statement)
            is TmpL.LabeledStatement -> translateLabeledStatement(statement)
            is TmpL.TryStatement -> translateTryStatement(statement)
            is TmpL.WhileStatement -> translateWhileStatement(statement)
            is TmpL.YieldStatement -> error("$statement not handled in translateYieldingStatement")
            is TmpL.ReturnStatement -> return listOfNotNull(translateReturnStatement(statement))
            is TmpL.SetProperty -> translateSetProperty(statement)
            is TmpL.ThrowStatement -> translateThrowStatement(statement)
        }.let { listOf(it) }
    }

    private fun translateStaticProperty(prop: TmpL.StaticProperty): CSharp.ClassMember {
        return CSharp.FieldDecl(
            prop.pos,
            mods = CSharp.FieldModifiers(
                prop.pos,
                modAccess = translateMemberVisibility(prop.visibility),
                modStatic = CSharp.ModStatic.Static,
                modWritable = CSharp.ModWritable.ReadOnly, // We don't expect writable statics, do we?
            ),
            type = translateType(prop.type),
            variables = listOf(
                CSharp.VariableDeclarator(
                    prop.pos,
                    variable = translateId(prop.name, style = NameStyle.PrettyPascal),
                    initializer = translateExpression(prop.expression),
                ),
            ),
        )
    }

    private fun translateSubject(subject: TmpL.Subject): Pair<TypeDefinition?, CSharp.PrimaryExpression> {
        return when (subject) {
            is TmpL.Expression -> {
                val type = subject.type
                type.definition to translateExpression(subject) as CSharp.PrimaryExpression
            }

            is TmpL.TypeName -> {
                val (d, t) = translateTypeName(subject)
                d to (t as CSharp.PrimaryExpression)
            }
        }
    }

    private fun translateThis(expr: TmpL.Expression): CSharp.Expression {
        return when (thisName) {
            null -> makeKeywordReference(expr.pos, "this")
            else -> translateName(expr.pos, name = thisName!!)
        }
    }

    private fun translateThrowStatement(statement: TmpL.ThrowStatement): CSharp.Statement {
        val pos = statement.pos
        return CSharp.ExpressionStatement(
            pos,
            CSharp.ThrowExpression(
                pos,
                CSharp.ObjectCreationExpression(
                    pos,
                    type = StandardNames.systemException.toType(pos),
                    args = listOf(),
                ),
            ),
        )
    }

    private fun translateTryStatement(statement: TmpL.TryStatement): CSharp.Statement {
        return CSharp.TryStatement(
            statement.pos,
            tryBlock = translateStatement(statement.tried).toBlock(statement.tried.pos),
            catchBlock = translateStatement(statement.recover).toBlock(statement.recover.pos),
        )
    }

    private fun tryTranslateYieldingStatement(statement: TmpL.Statement): List<CSharp.Statement>? {
        // Look for several patterns:
        //
        // 1. assignedTo = await p;
        // 2. await p;
        // 3. yield;
        // 4. yield x;  // NOT YET SUPPORTED IN TMPL

        val assignedTo: TmpL.Id?
        val yieldingNode: TmpL.BaseTree
        val arg: TmpL.Expression?
        val kind: YieldingFnKind
        when (statement) {
            is TmpL.Assignment -> { // 1
                val await = (statement.right as? TmpL.AwaitExpression) ?: return null
                assignedTo = statement.left
                yieldingNode = await
                arg = await.promise
                kind = YieldingFnKind.await
            }
            is TmpL.ExpressionStatement -> { // 2
                val await = (statement.expression as? TmpL.AwaitExpression) ?: return null
                assignedTo = null
                yieldingNode = await
                arg = await.promise
                kind = YieldingFnKind.await
            }
            is TmpL.YieldStatement -> { // 3 & 4
                assignedTo = null
                yieldingNode = statement
                arg = null // TODO: when yield supports an expression fix this
                kind = YieldingFnKind.yield
            }
            else -> return null
        }
        return when (kind) {
            YieldingFnKind.yield -> listOf(
                CSharp.YieldReturn(yieldingNode.pos, makeKeywordReference(yieldingNode.pos, "null")),
            )
            YieldingFnKind.await -> buildList {
                // 0. make sure that we can get the promise multiply without
                val promiseExpr = translateExpression(arg!!)
                val promiseId = promiseExpr as? CSharp.Identifier
                    ?: run {
                        val id = unusedName(promiseExpr.pos.leftEdge, "promise")
                        val declPos = statement.pos.leftEdge
                        add(
                            CSharp.LocalVariableDecl(
                                declPos,
                                translateTypeFromFrontend(declPos, arg.type),
                                listOf(
                                    CSharp.VariableDeclarator(declPos, id, promiseExpr),
                                ),
                            ),
                        )
                        id
                    }
                // 1. register for notification: yield return Async.AwakeUpon(promise);
                add(
                    CSharp.YieldReturn(
                        yieldingNode.pos,
                        CSharp.InvocationExpression(
                            pos = yieldingNode.pos,
                            expr = StandardNames.temperCoreAsyncAwakeUpon.toStaticMember(yieldingNode.pos.leftEdge),
                            args = listOf(promiseId.deepCopy()),
                        ),
                    ),
                )
                // 2. on resumption, read the promise and store the result in assignedTo if appropriate
                val readPromise = CSharp.MemberAccess(
                    yieldingNode.pos,
                    promiseId.deepCopy(),
                    "Result".toIdentifier(yieldingNode.pos.rightEdge),
                )
                if (assignedTo != null) {
                    add(
                        CSharp.ExpressionStatement(
                            CSharp.Operation(
                                statement.pos,
                                left = translateId(assignedTo),
                                operator = CSharp.Operator(assignedTo.pos.rightEdge, CSharpOperator.Assign),
                                right = readPromise,
                            ),
                        ),
                    )
                } else {
                    add(
                        CSharp.ExpressionStatement(
                            CSharp.InvocationExpression(
                                pos = readPromise.pos,
                                expr = StandardNames.temperCoreCoreIgnore.toStaticMember(readPromise.pos.leftEdge),
                                args = listOf(readPromise),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun translateType(type: TmpL.AType, mayYield: Boolean = false): CSharp.Type =
        translateType(type.ot, mayYield = mayYield)

    private fun translateType(type: TmpL.Type, mayYield: Boolean = false): CSharp.Type {
        val result: CSharp.Type = when (type) {
            is TmpL.FunctionType -> translateFunctionType(type)
            is TmpL.TypeIntersection -> TODO()
            is TmpL.TypeUnion -> translateTypeUnion(type)
            is TmpL.NominalType -> translateNominalType(type)

            // We only get to bubble type when it's alone.
            // We don't have never tests, but void seems most appropriate.
            is TmpL.BubbleType,
            is TmpL.NeverType,
            -> StandardNames.keyVoid.toType(type.pos)

            // Nullable object seems best for top.
            // And garbage might as well be whatever.
            is TmpL.TopType,
            is TmpL.GarbageType,
            -> CSharp.NullableType(StandardNames.keyObject.toType(type.pos))
        }
        // TODO: Is this needed?
        return when {
            mayYield -> CSharp.ConstructedType(
                type.pos,
                type = StandardNames.systemCollectionsGenericIEnumerable.toTypeName(type.pos),
                args = listOf(result),
            )

            else -> result
        }
    }

    internal fun translateTypeFromFrontend(pos: Position, sig: Signature2): CSharp.Type {
        val voidLike = sig.returnType2.isVoidLike
        val funType = returnVoidishToCSharpFunctionNamedType(pos, voidish = voidLike)
        val valueFormals = sig.allValueFormals
        return when {
            voidLike && valueFormals.isEmpty() -> CSharp.UnboundType(funType)
            else -> CSharp.ConstructedType(
                pos = pos,
                type = funType,
                args = buildList {
                    for (formal in valueFormals) {
                        add(translateTypeFromFrontend(pos, formal.type))
                    }
                    if (!voidLike) {
                        add(translateTypeFromFrontend(pos, sig.returnType2))
                    }
                },
            )
        }
    }

    internal fun translateTypeFromFrontend(pos: Position, type: Type2): CSharp.Type {
        val coreFrontendType =
            if (type is DefinedType && type.definition == WellKnownTypes.resultTypeDefinition) {
                type.bindings[0]
            } else {
                type
            }
        val nullable = coreFrontendType.nullity == Nullity.OrNull
        val translatedNonNull = when (coreFrontendType) {
            is DefinedType -> {
                val nonNullType = coreFrontendType.withNullity(Nullity.NonNull) as DefinedNonNullType
                withType(
                    nonNullType,
                    fn = { _, sig, _ ->
                        translateTypeFromFrontend(pos, sig)
                    },
                    fallback = {
                        translateNominalTypeFromFrontend(pos, nonNullType)
                    },
                )
            }
            is TypeParamRef ->
                translateUnconnectedTypeDefinition(coreFrontendType.definition, pos = pos)
        }

        return if (nullable) {
            when (translatedNonNull) {
                is CSharp.NonNullableType -> CSharp.NullableType(pos, type = translatedNonNull)
                is CSharp.NullableType -> translatedNonNull
                // See comment in translateTypeUnion regarding nullity and type parameters.
                is CSharp.TypeArgRef -> optionalTypeOf(translatedNonNull, pos)
            }
        } else {
            translatedNonNull
        }
    }

    private fun translateUnconnectedTypeDefinition(def: TypeDefinition, pos: Position? = null): CSharp.Type {
        val typePos = pos ?: def.pos
        return when (def) {
            is TypeShape -> {
                val type = translateUnconnectedTypeDefinitionName(def).toType(typePos)
                    as CSharp.NonNullableType
                when (def) {
                    WellKnownTypes.nullTypeDefinition -> CSharp.NullableType(typePos, type)
                    else -> type
                }
            }
            is TypeFormal -> CSharp.TypeArgRef(typePos, translateName(typePos, def.name) as CSharp.Identifier, def)
        }
    }

    private fun translateUnconnectedTypeDefinitionName(typeDefinition: TypeShape): AbstractTypeName {
        val fromWellKnownType = when (typeDefinition) {
            WellKnownTypes.anyValueTypeDefinition, WellKnownTypes.nullTypeDefinition -> StandardNames.keyObject
            WellKnownTypes.booleanTypeDefinition -> StandardNames.keyBool
            WellKnownTypes.denseBitVectorTypeDefinition -> StandardNames.systemCollectionsBitArray
            WellKnownTypes.dequeTypeDefinition -> StandardNames.systemCollectionsGenericQueue
            WellKnownTypes.doneResultTypeDefinition -> StandardNames.temperCoreDoneResult
            WellKnownTypes.float64TypeDefinition -> StandardNames.keyDouble
            WellKnownTypes.generatorTypeDefinition,
            WellKnownTypes.safeGeneratorTypeDefinition,
            -> StandardNames.systemCollectionsGenericIEnumerable
            WellKnownTypes.generatorResultTypeDefinition -> StandardNames.temperCoreGeneratorResult
            WellKnownTypes.intTypeDefinition -> StandardNames.keyInt
            WellKnownTypes.int64TypeDefinition -> StandardNames.keyLong

            WellKnownTypes.listTypeDefinition,
            WellKnownTypes.listedTypeDefinition,
            -> StandardNames.systemCollectionsGenericIReadOnlyList

            WellKnownTypes.listBuilderTypeDefinition -> StandardNames.systemCollectionsGenericIList

            WellKnownTypes.mapTypeDefinition,
            WellKnownTypes.mappedTypeDefinition,
            -> StandardNames.systemCollectionsGenericIReadOnlyDictionary

            // Same issue for Dictionary vs IDictionary and IReadOnlyDictionary as for list matters above.
            WellKnownTypes.mapBuilderTypeDefinition -> StandardNames.systemCollectionsGenericIDictionary
            WellKnownTypes.valueResultTypeDefinition -> StandardNames.temperCoreValueResult
            WellKnownTypes.pairTypeDefinition -> StandardNames.systemCollectionsGenericKeyValuePair
            WellKnownTypes.stringTypeDefinition -> StandardNames.keyString
            WellKnownTypes.typeTypeDefinition -> StandardNames.systemType
            WellKnownTypes.voidTypeDefinition -> StandardNames.keyVoid
            else -> null
        }
        if (fromWellKnownType != null) { return fromWellKnownType }

        var namespace = listOf<String>()
        return when (val sourceLocation = typeDefinition.sourceLocation) {
            ImplicitsCodeLocation -> when (val name = typeDefinition.name) {
                // For some reason GlobalConsole isn't an ExportedName.
                is ResolvedParsedName -> when (name.baseName.nameText) {
                    "Console", "GlobalConsole" -> StandardNames.temperCoreILoggingConsole
                    else -> null
                }

                else -> null
            }

            is ModuleName -> {
                (typeDefinition.name as? ExportedName)?.let { name ->
                    if (sourceLocation != loc) {
                        val rootNamespace = findRootNamespace(name).split(".")
                        namespace = rootNamespace + chooseSubspace(name.origin.loc as ModuleName)
                    }
                }
                null
            }
        } ?: typeDefinition.name.toStyle(NameStyle.PrettyPascal).let { plain ->
            val name = when (typeDefinition.isInterface()) {
                true -> plain.toInterfaceName()
                false -> plain
            }
            // This survives the degenerate case of empty namespace.
            namespace.toSpaceName().type(name)
        }
    }

    private fun translateTypeKind(kind: TmpL.TypeDeclarationKind) = when (kind) {
        TmpL.TypeDeclarationKind.Class -> CSharp.ModTypeKind.Class
        TmpL.TypeDeclarationKind.Interface -> CSharp.ModTypeKind.Interface
        TmpL.TypeDeclarationKind.Enum -> TODO()
    }

    /** Types aren't actually fully usable expressions, but it works well enough for our needs. */
    private fun translateTypeName(name: TmpL.TypeName): Pair<TypeDefinition, CSharp.Type> {
        return when (name) {
            is TmpL.ConnectedToTypeName ->
                name.sourceDefinition to (name.name as AbstractTypeName).toType(name.pos)

            is TmpL.TemperTypeName ->
                // The type is expected to (effectively) be a primary expression in these cases.
                // "Effectively" because we cheat in treating types as expressions to simplify things.
                name.typeDefinition to translateUnconnectedTypeDefinition(name.typeDefinition)
        }
    }

    private fun translateTypeParameters(
        typeParameters: TmpL.ATypeParameters,
    ) = translateTypeParameters(typeParameters.ot)

    /** Produces a list of type formals and a list of where clauses */
    private fun translateTypeParameters(
        typeParameters: TmpL.TypeParameters,
    ): Pair<List<Pair<TmpL.TypeFormal, CSharp.TypeParameter>>, CSharp.WhereConstraints> {
        val csTypeParameters = mutableListOf<Pair<TmpL.TypeFormal, CSharp.TypeParameter>>()
        val constraintLists = mutableListOf<CSharp.WhereConstraintList>()
        for (typeParameter in typeParameters.typeParameters) {
            val id = translateId(typeParameter.name)
            csTypeParameters.add(typeParameter to id)
            val constraints = mutableListOf<CSharp.WhereConstraint>()
            for (upperBound in typeParameter.upperBounds) {
                val sourceDefinition = upperBound.typeName.sourceDefinition
                if (sourceDefinition == WellKnownTypes.anyValueTypeDefinition || upperBound.isCommonlyImplied()) {
                    // Cases that are supported by default.
                    continue
                }
                val ub = translateNominalType(upperBound)
                val abstractness = (sourceDefinition as? TypeShape)?.abstractness
                val constraint = CSharp.UpperBoundWhereConstraint(ub.pos, ub)
                if (abstractness == Abstractness.Concrete) {
                    // Class types go first, interfaces and type parameter constraints second
                    constraints.add(0, constraint)
                } else {
                    constraints.add(constraint)
                }
            }
            if (constraints.isNotEmpty()) {
                constraintLists.add(
                    CSharp.WhereConstraintList(typeParameter.pos, id.deepCopy(), constraints),
                )
            }
        }
        return csTypeParameters.toList() to CSharp.WhereConstraints(
            typeParameters.pos.rightEdge,
            constraintLists.toList(),
        )
    }

    private fun translateTypeUnion(type: TmpL.TypeUnion): CSharp.Type {
        var nullable = false
        val coreTypes = buildSet {
            type.types.filterTo(this) { part ->
                when (part) {
                    is TmpL.BubbleType -> false // Just ignore.
                    is TmpL.NominalType -> when (part.typeName.sourceDefinition) {
                        WellKnownTypes.nullTypeDefinition -> {
                            nullable = true
                            false
                        }

                        else -> true
                    }

                    else -> true
                }
            }
        }
        val core = when (coreTypes.size) {
            0 -> return StandardNames.keyVoid.toType(type.pos)
            1 -> translateType(coreTypes.first())
            else -> TODO("${coreTypes.joinToString(" | ")} at ${type.pos}")
        }

        if (nullable && core is CSharp.TypeArgRef) {
            // `T?` -> `Optional<T>` because T? is not the same as int? when T binds to int.
            //
            // `int?` means Nullable<int>.
            //
            // T? is either a struct type or a reference type.  If T is a struct type then
            // T? is just an ignorable annotation with meaning to some extra checks when the
            // compilation unit includes `#nullable enable`.
            //
            // To be able to assign and get `null` out of a C# translation of Temper `T?`
            // while still allowing generic Temper class's type formals to bind to both C# struct
            // and reference types, we need to represent them differently.
            // `T?` maps to `Optional<T>` which is currently a home-brewed optional but
            // which is API compatible with the same-named type in DotNext so that we can transition
            // to it when our supported version is >= DotNext's.
            return optionalTypeOf(core, pos = type.pos)
        }

        return when (nullable) {
            true -> CSharp.NullableType(type.pos, type = core as CSharp.NonNullableType)
            false -> core
        }
    }

    private fun translateTypeValue(expr: TmpL.ValueReference): CSharp.Expression {
        val type = TType.unpack(expr.value)
        return CSharp.TypeofExpression(
            pos = expr.pos,
            type = translateTypeFromFrontend(expr.pos, type.type2),
        )
    }

    private fun translateValueReference(expr: TmpL.ValueReference): CSharp.Expression {
        return when (expr.value.typeTag) {
            TBoolean -> makeKeywordReference(expr.pos, "${TBoolean.unpack(expr.value)}")
            TFloat64 -> translateFloat64Value(expr.pos, TFloat64.unpackParsed(expr.value))
            TInt -> CSharp.NumberLiteral(expr.pos, TInt.unpack(expr.value))
            TInt64 -> CSharp.NumberLiteral(expr.pos, TInt64.unpack(expr.value))
            TString -> CSharp.StringLiteral(expr.pos, TString.unpack(expr.value))
            is TClass -> TODO()
            TClosureRecord -> TODO()
            TFunction -> TODO()
            TList -> TODO()
            TListBuilder -> TODO()
            TMap -> TODO()
            TMapBuilder -> TODO()
            // void should only be in broken code anyway
            TVoid -> makeKeywordReference(expr.pos, "null")
            TNull -> {
                var translation: CSharp.Expression? = null
                // If the null has type `T?` then it should actually be a reference to the
                // None optional.
                val parentOfNull = expr.parent
                // HACK: we only handle assignments of null, not null passed to generic functions.
                // TODO: the TmpL translator should be inserting casts, or the Typer should
                // be typing `null` literals as a nullable type that agrees with the function
                // call receiving the literal.
                if (parentOfNull is TmpL.Assignment && parentOfNull.right === expr) {
                    val type = parentOfNull.type
                    val csharpType = translateTypeFromFrontend(expr.pos, type)
                    if (csharpType.isOptionalTypeArg) {
                        // C::Optional<T>.None
                        translation = CSharp.MemberAccess(
                            expr.pos,
                            csharpType as CSharp.ConstructedType,
                            CSharp.Identifier(expr.pos.rightEdge, OutName("None", null)),
                        )
                    }
                }

                if (translation == null) {
                    translation = makeKeywordReference(expr.pos, "null")
                }
                translation
            }
            TProblem -> TODO()
            TStageRange -> TODO()
            TSymbol -> TODO()
            TType -> translateTypeValue(expr)
        }
    }

    private fun translateWhileStatement(loop: TmpL.WhileStatement): CSharp.Statement {
        return CSharp.WhileStatement(
            loop.pos,
            test = translateExpression(loop.test),
            body = translateStatement(loop.body).toBlock(loop.body.pos),
        )
    }

    private inline fun <T> withFunctionContext(action: () -> T): T {
        functionContextStack.add(FunctionContext())
        return try {
            action()
        } finally {
            functionContextStack.compatRemoveLast()
        }
    }
}

/** Info we track for each function, which can nest in TmpL. Not much for now. */
private class FunctionContext {
    var optionals = setOf<ResolvedName>()
    var voidish: Boolean = false
    var restFormal: TmpL.RestFormal? = null
    var returnType: Type2? = null
}

/** Might not always compile, which is ok. If it does, it throws, which is ok. */
private fun makeGarbageExpression(pos: Position, text: String?) = CSharp.InvocationExpression(
    pos,
    expr = StandardNames.temperCoreCoreGarbage.toStaticMember(pos),
    args = text?.let { listOf(CSharp.StringLiteral(pos, text)) } ?: listOf(),
)

internal fun makeGlobalName(fullNamespace: QualifiedName) = "${fullNamespace.last()}Global"

/** This is awkward, but it centralizes common logic that's used in two places. Maybe reorg code better sometime. */
private fun makeNamespaceAndGlobalName(
    rootNamespace: String,
    subspace: List<String>,
): Pair<List<String>, List<String>> {
    val fullNamespace: List<String> = rootNamespace.split(".") + subspace
    val qualifiedGlobalClassName: List<String> = fullNamespace + listOf(makeGlobalName(fullNamespace))
    return fullNamespace to qualifiedGlobalClassName
}

/** Returns the id modified to use as a `params` parameter array name. */
private fun paramsify(id: CSharp.Identifier): CSharp.Identifier {
    id.outName = OutName("${id.outName.outputNameText}_params", id.outName.sourceName)
    return id
}

fun returnVoidishToCSharpFunctionNamedType(pos: Position, voidish: Boolean): CSharp.QualTypeName {
    // TODO System.Func supports up to 16 args + return type. We could manufacture types if more.
    return when {
        voidish -> StandardNames.systemAction
        else -> StandardNames.systemFunc
    }.toTypeName(pos)
}

internal fun makeKeywordReference(pos: Position, name: String): CSharp.Expression {
    // This is all we need for now, but maybe could specialize if needed in the future.
    return name.toIdentifier(pos)
}

internal val CSharp.Type.isOptionalTypeArg: Boolean get() {
    if (this is CSharp.ConstructedType && this.args.size == 1) {
        val name = this.type as? CSharp.QualTypeName
        val arg = this.args[0]
        return name?.matches(StandardNames.temperCoreOptional) == true && arg is CSharp.TypeArgRef &&
            arg.definition?.let { excludeTypeFormalFromOptionalTransform(it) } != true
    }
    return false
}

internal fun Type2.isOptionalTypeArg(): Boolean {
    val sawNull = TypeContext2().admitsNull(this)
    val sawTypeFormal = this is TypeParamRef &&
        !excludeTypeFormalFromOptionalTransform(this.definition)
    return sawNull && sawTypeFormal
}

private fun excludeTypeFormalFromOptionalTransform(typeFormal: TypeFormal): Boolean {
    // HACK: Due to a failure to turn generic comparison into an operation,
    // we're boxing arguments to generic comparison.
    val name = typeFormal.name
    return name is BuiltinName && name.builtinKey == "cmpT"
}

internal fun optionalTypeOf(type: CSharp.Type, pos: Position = type.pos): CSharp.ConstructedType =
    CSharp.ConstructedType(
        pos,
        StandardNames.temperCoreOptional.toTypeName(pos.leftEdge),
        listOf(type),
    )
