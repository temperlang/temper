@file:Suppress("MatchingDeclarationName") // Temporary to make PR readable

package lang.temper.be.py

import lang.temper.ast.OutTree
import lang.temper.ast.anyChildRecursive
import lang.temper.ast.boundaryDescent
import lang.temper.ast.deepCopy
import lang.temper.be.Dependencies
import lang.temper.be.py.PyDottedIdentifier.Companion.dotted
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpL.TypeFormal
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.TmpLOperatorDefinition
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.aType
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.documentation
import lang.temper.be.tmpl.isCommonlyImplied
import lang.temper.be.tmpl.isYieldingStatement
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapGeneric
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.common.buildListMultimap
import lang.temper.common.isNotEmpty
import lang.temper.common.putMultiList
import lang.temper.common.stackWithElementIfNotNull
import lang.temper.lexer.Genre
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.Position
import lang.temper.log.last
import lang.temper.log.spanningPosition
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedType
import lang.temper.value.DependencyCategory
import lang.temper.value.Helpful
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
import lang.temper.value.Value
import lang.temper.value.impliedThisSymbol

class PyTranslator(
    val pyNames: PyNames,
    private val defaultGenre: Genre,
    val pythonVersion: PythonVersion,
    private val dependenciesBuilder: Dependencies.Builder<PyBackend>? = null,
) {
    // Track support code needs for production and for test.
    private var dependencyMode = DependencyCategory.Production
    private val productionSupportBatch = SupportBatch()
    private val testSupportBatch = SupportBatch()
    private val support: MutableSet<PySupportCode> get() = activeSupportBatch.support
    private val sharedSupport: MutableSet<PySupportCode> get() = activeSupportBatch.sharedSupport
    private val activeSupportBatch: SupportBatch get() = when (dependencyMode) {
        DependencyCategory.Production -> productionSupportBatch
        DependencyCategory.Test -> testSupportBatch
    }

    private val generatorDoAwaitNameStack = mutableListOf<PyIdentifierName>()

    /** Get a name that is backed by support code. */
    fun request(code: PySupportCode): OutName {
        support.add(code)
        return pyNames.supportCodeName(code)
    }

    /** For [postprocessing][PyModule.saveSupport] to request [PySupportCode]s required by this module. */
    fun support(): Set<PySupportCode> {
        val copy = support.toSet()
        support.clear()
        return copy
    }

    /**
     * For [postprocessing][PyModule.saveSupport] to request shared support codes
     * [declared][TmpL.SupportCodeDeclaration] in this module.
     */
    fun sharedSupport(): Set<PySupportCode> {
        val copy = sharedSupport.toSet()
        sharedSupport.clear()
        return copy
    }

    fun <T> withDependencyMode(dependencyCategory: DependencyCategory, action: () -> T): T {
        val oldMode = dependencyMode
        try {
            dependencyMode = dependencyCategory
            return action()
        } finally {
            dependencyMode = oldMode
        }
    }

    private var libraryName: DashedIdentifier? = null

    fun translate(t: TmpL.Module): List<Py.Program> {
        pyNames.module = t.codeLocation.codeLocation
        libraryName = t.libraryName
        val result = mutableListOf<Py.Stmt>()
        val tests = mutableListOf<Py.Stmt>()
        val ungroupedImports = mutableListOf<Py.ImportFrom>()
        translateImports(t.imports, ungroupedImports)
        t.topLevels.forEach {
            at(it) topLevel@{
                val dependencyCategory = it.dependencyCategory() ?: return@topLevel
                withDependencyMode(dependencyCategory) {
                    val stmt = translate(it)
                    when (dependencyCategory) {
                        DependencyCategory.Production -> result
                        DependencyCategory.Test -> tests
                    }.addAll(stmt)
                }
            }
        }
        val imports = run {
            val grouped = buildListMultimap {
                for (imp in ungroupedImports) {
                    this.putMultiList(imp.module.module, imp)
                }
            }
            grouped.map { (_, imps) ->
                val first = imps.first()
                Py.ImportFrom(
                    imps.spanningPosition(first.pos),
                    first.module.deepCopy(),
                    imps.flatMap {
                        it.names.deepCopy()
                    },
                )
            }
        }

        result.addAll(0, imports)
        t.result?.let { finalResult ->
            result.add(
                Py.Assign.simple(
                    pyName(finalResult.pos, PyBackend.exportName),
                    expr(finalResult),
                ),
            )
        }
        return buildList {
            // Still generate a prod if we have no tests, since we sometimes expect that.
            if (result.isNotEmpty() || tests.isEmpty()) {
                add(
                    Py.Program(
                        t.pos,
                        result,
                        DependencyCategory.Production,
                        defaultGenre,
                        t.codeLocation.outputPath,
                    ),
                )
            }
            if (tests.isNotEmpty()) {
                tests.addAll(0, imports.deepCopy())
                add(
                    Py.Program(
                        t.pos,
                        tests,
                        DependencyCategory.Test,
                        Genre.Library,
                        convertToTestPath(t.codeLocation.outputPath),
                    ),
                )
            }
        }
    }

    private fun convertToTestPath(filePath: FilePath): FilePath {
        var isDir = false
        val segments = buildList {
            // Put under tests dir instead of under library name.
            add(PyBackend.testsDir)
            if (filePath.segments.size > 1) {
                addAll(filePath.segments.subList(1, filePath.segments.size - 1))
                // And prefix file name for test recognition.
                add(FilePathSegment("${PyBackend.TEST_FILE_PREFIX}${filePath.last().fullName}"))
            } else {
                // It would also be nice just to ask if filePath is a dir, but that doesn't always seem to work out.
                // But we definitely need to operate and dir/init level if we have nothing below the library name.
                // TODO Infer by presence or absence of extension?
                isDir = true
            }
        }
        return FilePath(segments, isDir = isDir)
    }

    private fun translateImports(allImports: Iterable<TmpL.Import>, pyImports: MutableCollection<Py.ImportFrom>) {
        val groupedBySource =
            mutableMapOf<PyDottedIdentifier, Pair<Py.ImportDotted, MutableList<TmpL.Import>>>()
        for (oneImport in allImports) {
            if (oneImport.sig is TmpL.ImportedConnection) { continue }
            val path = oneImport.path ?: continue
            val fromName = translateLibraryName(path)
            groupedBySource.getOrPut(fromName.module) {
                fromName to mutableListOf()
            }.second.add(oneImport)
        }

        for ((module, imports) in groupedBySource.values) {
            fun addImport(pos: Position, externalName: TmpL.Id, localName: Py.Identifier) {
                val ext = pyNames.importedName(externalName.name)
                val externalPyName = dotted(ext)
                val localAndExternalNamesMatch = externalPyName.asSimpleName() == localName.outName
                pyImports.add(
                    Py.ImportFrom(
                        pos = pos,
                        module = module,
                        names = listOf(
                            Py.ImportAlias(
                                pos = pos,
                                name = Py.ImportDotted(externalName.pos, externalPyName),
                                asname = if (localAndExternalNamesMatch) { null } else { localName },
                            ),
                        ),
                    ),
                )
            }
            for (imp in imports) {
                val localName = imp.localName
                if (localName != null) {
                    val pyLocalName = ident(localName)
                    addImport(imp.pos, imp.externalName, pyLocalName)
                    pyNames.importName(imp.externalName.name, pyLocalName.outName)
                } else {
                    pyNames.importNameAsNeeded(imp.externalName) { pyName: PyIdentifierName ->
                        addImport(imp.pos, imp.externalName, Py.Identifier(imp.pos.leftEdge, pyName))
                    }
                }
            }
        }
    }

    private fun translateLibraryName(path: TmpL.ModulePath): Py.ImportDotted {
        val modulePath = path.translatedPath.withTemperAwareExtension("")
        val parts = modulePath.segments
            .map { safeModuleName(it.fullName).outputNameText }
        return Py.ImportDotted(path.pos, dotted(parts))
    }

    private fun translate(t: TmpL.TopLevel): List<Py.Stmt> {
        return when (t) {
            is TmpL.GarbageTopLevel -> listOf(garbageStmt(t.pos, t.diagnostic))
            is TmpL.TypeDeclaration -> classDef(t)
            is TmpL.TypeConnection ->
                listOf(Py.CommentLine(t.pos, "Type ${t.name.name} connected to ${t.to.typeName}"))
            is TmpL.PooledValueDeclaration -> listOf(Py.Assign.simple(name(t.name), expr(t.init)))
            is TmpL.SupportCodeDeclaration -> translateSupportDecl(t)
            is TmpL.FunctionDeclaration -> translateFunction(t)
            is TmpL.Test -> translateTest(t)
            is TmpL.ModuleLevelDeclaration ->
                when (val rhs = t.init) {
                    null -> listOf(Py.AnnAssign(t.pos, name(t.name), translateAnnotation(t.type), null))
                    else -> listOf(Py.AnnAssign(t.pos, name(t.name), translateAnnotation(t.type), expr(rhs)))
                }
            is TmpL.ModuleInitBlock -> translate(t.body)
            is TmpL.BoilerplateCodeFoldBoundary -> listOf(translateBoilerplateCodeFoldBoundary(t))
            is TmpL.EmbeddedComment -> listOf(translateEmbeddedComment(t))
        }
    }

    private fun classDef(t: TmpL.TypeDeclaration): List<Py.Stmt> {
        val stmtList = mutableListOf<Py.Stmt>()
        val genericBases = mutableListOf<Py.CallArg>()
        val bases = mutableListOf<Py.CallArg>()
        val classDecorators = mutableListOf<Py.Decorator>()

        val className = ident(t.name)
        val classDocumentation = t.documentation?.prettyPleaseHelp()

        var needsGeneric = true
        val needsProto = t.kind == TmpL.TypeDeclarationKind.Interface
        t.superTypes.forEach { nominalType ->
            if (nominalType.params.isNotEmpty()) {
                needsGeneric = false
            }
            val superType = translateNominalType(nominalType)
            if (superType is Py.Subscript) {
                // Types should be ordered so that supertypes are defined before subtypes,
                // but the same is not true of type parameters.  Quote any type parameters.
                //
                //    JsonAdapter[C] -> JsonAdapter['C']
                //
                // Unlike other places type expressions appear in Python, in a class definition,
                // the super-types are needed as regular values so the whole cannot be quoted.
                superType.slice = superType.slice.map {
                    when (it) {
                        is Py.Expr -> Py.TypeStr(it.pos, it.deepCopy())
                        is Py.Slice -> it
                    }
                }
            }
            bases.add(Py.CallArg(nominalType.pos, value = superType))
        }

        if (t.typeParameters.ot.typeParameters.isNotEmpty()) {
            defineTypeVars(
                stmtList,
                t.typeParameters.ot.typeParameters,
                covariant = t.kind == TmpL.TypeDeclarationKind.Interface,
            )
            if (needsGeneric) {
                genericBases.add(
                    Py.CallArg(
                        t.pos,
                        value = Py.Subscript(
                            t.pos,
                            request(GenericType).asRName(t.pos),
                            t.typeParameters.ot.typeParameters.map { name(it.name) },
                        ),
                    ),
                )
            }
        }
        if (needsProto) {
            bases.add(
                Py.CallArg(
                    t.pos,
                    arg = pyIdent(t.pos, "metaclass"),
                    value = request(AbstractBaseClassMeta).asRName(t.pos),
                ),
            )
            if (pythonVersion == PythonVersion.MypyC) {
                classDecorators.add(
                    Py.Decorator(
                        t.pos,
                        name = listOf(
                            request(TraitDecorator).asPyId(t.pos),
                        ),
                        args = listOf(),
                        called = false,
                    ),
                )
            }
        }

        fun translateBodyOf(
            s: TmpL.FunctionDeclarationOrMethod,
            ignoreBody: Boolean = false,
            renames: List<Py.Stmt>? = null,
            undefinedError: (() -> Py.Expr)? = null,
        ): List<Py.Stmt> = buildList {
            // TODO: substitute python parameter names for TmpL names
            // See be-java's javadoc(...) helpers.
            val fnDocumentation = s.documentation?.prettyPleaseHelp()
            if (fnDocumentation != null) {
                add(translateDocString(fnDocumentation, s.pos))
            }

            val body = when {
                ignoreBody -> null
                else -> s.body
            }
            val stmts = body?.let { translate(it, renames) }
                // Using @abstractmethod requires ABCMeta, which we only do for interfaces.
                // TODO If for an interface, use @abstractmethod outside here?
                ?: listOf(Py.Raise(s.pos, undefinedError?.let { it() } ?: request(NotImplementedError).asPyName(s.pos)))
            if (stmts.isEmpty()) {
                Py.Pass(s.pos)
            } else {
                addAll(stmts)
            }
        }

        val body = mutableListOf<Py.Stmt>()
        val defer = mutableListOf<Py.Stmt>()

        val slots = mutableListOf<Py.Str>()

        if (classDocumentation != null) {
            body.add(translateDocString(classDocumentation, t.pos))
        }

        if (t.kind != TmpL.TypeDeclarationKind.Interface) {
            t.members.forEach members@{ member ->
                if (member is TmpL.Property) {
                    val propertyType = translateAnnotation(member.type)
                    when (member) {
                        is TmpL.InstanceProperty -> {
                            if (member.memberShape.abstractness == Abstractness.Abstract) {
                                return@members
                            }
                            val memName = pyNames.name(member.name)
                            slots.add(Py.Str(member.pos, memName.outputNameText))
                            memName.asPyName(member.name.pos) to propertyType
                        }
                        is TmpL.StaticProperty -> {
                            pyName(member.name.pos, member.adjustedNameText()) to Py.Subscript(
                                member.type.pos,
                                // Have to wrap static property types in `ClassVar[...]`.
                                request(ClassVarType).asRName(member.type.pos),
                                listOf(propertyType),
                            )
                        }
                    }.let { (name, type) ->
                        body.add(Py.AnnAssign(member.pos, name, type, null))
                    }
                }
            }

            body.add(Py.Assign.simple(pyName(t.pos, "__slots__"), Py.Tuple(t.pos, slots)))
        } else {
            val defined = mutableSetOf<String>()
            t.members.forEach { member ->
                if (member is TmpL.Getter && member.body != null) {
                    defined.add(member.dotName.dotNameText)
                }
            }

            t.members.forEach { member ->
                if (member is TmpL.Property) {
                    if (member.dotName.dotNameText in defined) {
                        return@forEach
                    }

                    body.add(
                        Py.FunctionDef(
                            member.pos,
                            decoratorList = listOf(
                                propertyDecorator(member.pos),
                                Py.Decorator(
                                    member.pos,
                                    name = listOf(
                                        request(AbstractMethod).asPyId(member.pos),
                                    ),
                                    args = listOf(),
                                    called = false,
                                ),
                            ),
                            name = pyIdent(member.pos, temperToPython(member.dotName.dotNameText)),
                            args = Py.Arguments(
                                member.pos,
                                listOf(
                                    Py.Arg(member.pos, arg = pyIdent(member.pos, "self")),
                                ),
                            ),
                            body = listOf(
                                when (val doc = member.documentation) {
                                    is Helpful -> translateDocString(doc, member.pos)
                                    else -> Py.Pass(member.pos) // ok because explicitly abstract
                                },
                            ),
                            returns = translateAnnotation(member.type),
                        ),
                    )
                }
            }
        }

        val constructors = mutableListOf<Py.FunctionDef>()
        val setterNames = buildSet {
            for (member in t.members) {
                if (member is TmpL.Setter) {
                    add(member.dotName.dotNameText)
                }
            }
        }
        t.members.forEach { s ->
            if (s is TmpL.GetterOrSetter && t.kind == TmpL.TypeDeclarationKind.Interface) {
                val sbody = s.body
                if (sbody == null || hasPureVirtual(sbody)) {
                    return@forEach
                }
            }
            when (s) {
                is TmpL.GarbageStatement -> body.addAll(translate(s))
                is TmpL.NormalMethod,
                is TmpL.StaticMethod,
                -> body.add(
                    translateFunctionDef(s as TmpL.DotAccessibleMethod, stmtList) { decs, args, renames ->
                        Py.FunctionDef(
                            s.pos,
                            name = dotName(s).asPyId(s.pos),
                            args = args,
                            body = translateBodyOf(s, renames = renames),
                            returns = translateAnnotation(s.returnType),
                            decoratorList = buildList {
                                addAll(decs)
                                if (s is TmpL.StaticMember) {
                                    val decPos = s.pos.leftEdge
                                    add(
                                        Py.Decorator(
                                            decPos,
                                            name = listOf(
                                                Py.Identifier(decPos, PyIdentifierName("staticmethod")),
                                            ),
                                            args = emptyList(),
                                            called = false,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                )

                is TmpL.Getter -> {
                    val name = temperToPython(s.dotName.dotNameText)
                    when {
                        s.visibility.visibility == TmpL.Visibility.Public || s.body == null -> {
                            body.add(
                                Py.FunctionDef(
                                    s.pos,
                                    decoratorList = listOf(propertyDecorator(s.pos.leftEdge)),
                                    name = pyIdent(s.pos, name),
                                    args = translateParameters(s), // Shouldn't need public names for getters.
                                    body = translateBodyOf(s, undefinedError = { undefinedGetterError(s) }),
                                    returns = translateAnnotation(s.returnType),
                                ),
                            )
                        }
                        else -> {
                            // Auto-fail getter so we can also support setters.
                            if (s.dotName.dotNameText in setterNames) {
                                body.add(
                                    Py.FunctionDef(
                                        s.pos,
                                        decoratorList = listOf(propertyDecorator(s.pos.leftEdge)),
                                        name = pyIdent(s.pos, name),
                                        args = translateParameters(s), // Shouldn't need public names for getters.
                                        body = translateBodyOf(
                                            s,
                                            ignoreBody = true,
                                            undefinedError = { undefinedGetterError(s) },
                                        ),
                                        returns = translateAnnotation(s.returnType),
                                    ),
                                )
                            }
                            // Private but usable ugly get method.
                            body.add(
                                Py.FunctionDef(
                                    s.pos,
                                    name = pyIdent(s.pos, name.privateGetter()),
                                    args = translateParameters(s), // Shouldn't need public names for getters.
                                    body = translateBodyOf(s, undefinedError = { undefinedGetterError(s) }),
                                    returns = translateAnnotation(s.returnType),
                                ),
                            )
                        }
                    }
                }

                is TmpL.Setter -> {
                    val name = temperToPython(s.dotName.dotNameText)
                    when (s.visibility.visibility) {
                        TmpL.Visibility.Public -> {
                            body.add(
                                Py.FunctionDef(
                                    s.pos,
                                    decoratorList = listOf(
                                        Py.Decorator(
                                            s.pos,
                                            name = listOf(
                                                pyIdent(s.pos, name),
                                                pyIdent(s.pos, "setter"),
                                            ),
                                            args = listOf(),
                                            called = false,
                                        ),
                                    ),
                                    name = pyIdent(s.pos, name),
                                    args = translateParameters(s), // Shouldn't need public names for setters.
                                    body = translateBodyOf(s),
                                    returns = translateAnnotation(s.returnType),
                                ),
                            )
                        }
                        else -> {
                            body.add(
                                Py.FunctionDef(
                                    s.pos,
                                    name = pyIdent(s.pos, name.privateSetter()),
                                    args = translateParameters(s), // Shouldn't need public names for setters.
                                    body = translateBodyOf(s),
                                    returns = translateAnnotation(s.returnType),
                                ),
                            )
                        }
                    }
                }

                is TmpL.Constructor -> {
                    constructors.add(
                        translateFunctionDef(s, stmtList) { decs, args, renames ->
                            Py.FunctionDef(
                                pos = t.pos,
                                decoratorList = decs,
                                name = pyIdent(s.pos, "__init__"),
                                args = args,
                                body = translate(s.body, renames),
                                returns = PyConstant.None.at(s.pos),
                            )
                        },
                    )
                    body.add(constructors.last())
                }

                is TmpL.InstanceProperty -> {}

                // Defer all static assignment until after class body, in case any use the class itself.
                is TmpL.StaticProperty -> defer.add(
                    Py.Assign(
                        s.pos,
                        listOf(className.asName().attribute(s.adjustedNameText())),
                        expr(s.expression),
                    ),
                )
            }
        }

        stmtList.add(
            Py.ClassDef(
                t.pos,
                decoratorList = classDecorators,
                name = className,
                args = genericBases + bases,
                body = body,
            ),
        )
        stmtList.addAll(defer)
        return stmtList
    }

    private fun undefinedGetterError(prop: TmpL.DotAccessible) = Py.Call(
        prop.pos,
        request(AttributeError).asPyName(prop.pos),
        listOf(Py.CallArg(prop.pos, value = Py.Str(prop.pos, "${prop.dotName.dotNameText} getter unavailable"))),
    )

    private fun defineTypeVars(
        stmtList: MutableList<Py.Stmt>,
        typeParameters: List<TypeFormal>,
        covariant: Boolean = false,
    ) {
        typeParameters.forEach { typeParam ->
            val typeParamName = pyNames.name(typeParam.name)
            val pos = typeParam.pos
            val typeVarArgs = mutableListOf(
                Py.CallArg(pos, value = typeParamName.asStr(pos)),
            )
            // Keep bounds that aren't already implicit in Python.
            val upperBounds = typeParam.upperBounds.filter { !it.isCommonlyImplied() }
            if (upperBounds.size == 1) {
                typeVarArgs.add(
                    Py.CallArg(
                        pos,
                        arg = pyIdent(pos, "bound"),
                        value = translateNominalType(upperBounds[0]),
                    ),
                )
            } else if (upperBounds.size > 1) {
                typeVarArgs.add(
                    Py.CallArg(
                        pos,
                        arg = pyIdent(pos, "bound"),
                        value = Py.Subscript(
                            pos,
                            request(UnionType).asRName(pos),
                            upperBounds.map(::translateNominalType),
                        ),
                    ),
                )
            }
            if (covariant) {
                typeVarArgs.add(
                    Py.CallArg(
                        pos,
                        arg = pyIdent(pos, "covariant"),
                        value = PyConstant.True.at(pos),
                    ),
                )
            }
            val typeVarExpr = Py.Call(pos, request(TypeVarType).asRName(pos), typeVarArgs)
            stmtList.add(
                Py.Assign.simple(typeParamName.asPyName(pos), typeVarExpr) as Py.Stmt,
            )
        }
    }

    private fun translate(s: TmpL.Statement, renames: List<Py.Stmt>?) = renames.orEmpty() + translate(s)

    private fun rightHasPureVirtual(rhs: TmpL.RightHandSide): Boolean {
        if (rhs is TmpL.CallExpression) {
            val fn = rhs.fn
            if (fn is TmpL.InlineSupportCodeWrapper) {
                return fn.supportCode == PureVirtualBody
            }
        }
        return false
    }

    // similar to isPureVirtual, but for Python
    private fun hasPureVirtual(s: TmpL.BlockStatement): Boolean = s.statements.any {
        when (it) {
            is TmpL.Assignment -> rightHasPureVirtual(it.right)
            is TmpL.ExpressionStatement -> rightHasPureVirtual(it.expression)
            else -> false
        }
    }

    private fun translate(s: TmpL.Statement): List<Py.Stmt> = when (s) {
        is TmpL.GarbageStatement -> listOf(garbageStmt(s.pos, s.diagnostic))
        is TmpL.LocalDeclaration ->
            when (val rhs = s.init) {
                null -> listOf(Py.AnnAssign(s.pos, name(s.name), translateAnnotation(s.type), null))
                else -> listOf(Py.AnnAssign(s.pos, name(s.name), translateAnnotation(s.type), expr(rhs)))
            }

        is TmpL.BlockStatement -> if (hasPureVirtual(s)) {
            listOf(
                Py.Raise(
                    pos = s.pos,
                    exc = expr(TmpL.BubbleSentinel(s.pos)),
                ),
            )
        } else {
            s.statements.flatMap { translate(it) }
        }
        is TmpL.BoilerplateCodeFoldBoundary -> listOf(translateBoilerplateCodeFoldBoundary(s))
        is TmpL.EmbeddedComment -> listOf(translateEmbeddedComment(s))
        is TmpL.LocalFunctionDeclaration -> translateFunction(s)
        is TmpL.ExpressionStatement -> {
            when (val expression = s.expression) {
                is TmpL.CallExpression -> when (val translated = translateCall(expression)) {
                    // PyInlineSupportCode can produce statements.
                    is Py.Stmt -> listOf(translated)
                    is Py.Expr -> listOf(Py.ExprStmt(s.pos, translated))
                    else -> listOf(
                        garbageStmt(s.pos, "$s", "inline support code produced neither Stmt nor Expr"),
                    )
                }

                is TmpL.ValueReference if expression.value.typeTag is TNull -> listOf()
                else -> listOf(Py.ExprStmt(s.pos, expr(s.expression)))
            }
        }
        is TmpL.HandlerScope -> listOf(
            garbageStmt(s.pos, "hs", "hs(...) not converted to try"),
        )

        is TmpL.Assignment ->
            when (val rhs = s.right) {
                is TmpL.HandlerScope -> listOf(
                    garbageStmt(s.pos, "assign hs", "hs(...) not converted to try"),
                )

                is TmpL.Expression -> listOf(Py.Assign.simple(name(s.left), expr(rhs)))
            }

        is TmpL.BreakStatement -> listOf(breakStmt(s))
        is TmpL.ContinueStatement -> listOf(continueStmt(s))
        is TmpL.IfStatement -> translateIf(s)
        is TmpL.LabeledStatement -> labelBlock(s)
        is TmpL.ModuleInitFailed -> listOf(Py.Raise(s.pos, request(ImportError).asPyName(s.pos)))
        is TmpL.YieldStatement -> listOf(Py.ExprStmt(s.pos, Py.Yield(s.pos)))
        is TmpL.ReturnStatement -> {
            when (val tmplExpr = s.expression) {
                null -> listOf(
                    Py.Return(
                        s.pos,
                    ),
                )
                else -> {
                    val returned = expr(tmplExpr)
                    if (returned is Py.Constant && returned.value == PyConstant.Ellipsis) {
                        listOf(
                            Py.Raise(
                                pos = s.pos,
                                exc = expr(TmpL.BubbleSentinel(s.pos)),
                            ),
                        )
                    } else {
                        listOf(
                            Py.Return(s.pos, value = returned),
                        )
                    }
                }
            }
        }
        is TmpL.SetProperty -> translateSetProperty(s)
        is TmpL.ThrowStatement -> translateThrow(s)
        is TmpL.TryStatement -> translateTry(s)
        is TmpL.WhileStatement -> translateWhile(s)
        // Currently compute jumps are only used with coroutine strategy mode not
        // opted into by the support network.
        is TmpL.ComputedJumpStatement -> TODO()
    }

    private fun translateSetProperty(s: TmpL.SetProperty): List<Py.Stmt> {
        val left = s.left
        return when (val prop = left.property) {
            is TmpL.InternalPropertyId -> listOf(
                Py.Assign(
                    s.pos,
                    listOf(Py.Attribute(left.pos, value = subject(left.subject), attr = ident(prop.name))),
                    expr(s.right),
                ),
            )

            is TmpL.ExternalPropertyId -> {
                // If it's private, use explicit setter method.
                val type = left.subject.typeDefinition()
                if (type is TypeShape) {
                    type.methods.firstOrNull { method ->
                        method.methodKind == MethodKind.Setter &&
                            method.symbol.text == prop.name.dotNameText &&
                            method.visibility != Visibility.Public
                    }?.let {
                        val name = temperToPython(prop.name.dotNameText)
                        return@translateSetProperty Py.Call(
                            s.pos,
                            func = Py.Attribute(
                                left.pos,
                                value = subject(left.subject),
                                attr = pyIdent(prop.pos, name.privateSetter()),
                            ),
                            args = listOf(Py.CallArg(s.right.pos, value = expr(s.right))),
                        ).let { listOf(Py.ExprStmt(s.pos, it)) }
                    }
                }
                // Otherwise, just use setter property.
                listOf(
                    Py.Assign(
                        s.pos,
                        listOf(
                            Py.Attribute(
                                left.pos,
                                value = subject(left.subject),
                                attr = dotName(prop.name).asPyId(prop.pos),
                            ),
                        ),
                        expr(s.right),
                    ),
                )
            }
        }
    }

    private fun translateIf(stmt: TmpL.IfStatement): List<Py.Stmt> {
        val test = expr(stmt.test)
        val body = translate(stmt.consequent)
        val elifs = mutableListOf<Py.Elif>()
        // We collapse else if by iterating until the else is not an If.
        var alternate = stmt.alternate
        while (alternate is TmpL.IfStatement) {
            elifs.add(
                Py.Elif(
                    alternate.pos,
                    expr(alternate.test),
                    translate(alternate.consequent),
                ),
            )
            alternate = alternate.alternate
        }
        return listOf(
            Py.If(
                stmt.pos,
                test = test,
                body = body,
                elifs = elifs,
                orElse = alternate?.let { translate(it) }.orEmpty(),
            ),
        )
    }

    private fun translateThrow(
        stmt: TmpL.ThrowStatement,
    ): List<Py.Stmt> = listOf(
        Py.Raise(
            pos = stmt.pos,
            exc = expr(TmpL.BubbleSentinel(stmt.pos)),
        ),
    )

    private fun translateTry(
        stmt: TmpL.TryStatement,
    ): List<Py.Stmt> = listOf(
        Py.Try(
            pos = stmt.pos,
            body = translate(stmt.tried),
            handlers = listOf(
                Py.ExceptHandler(
                    stmt.recover.pos,
                    // Restrict to Exception to avoid catching MemoryError, StopIteration,
                    // and things we define like Label.
                    type = request(Exception).asPyName(stmt.tried.pos.rightEdge),
                    name = null,
                    body = translate(stmt.recover),
                ),
            ),
        ),
    )

    private fun translateWhile(
        stmt: TmpL.WhileStatement,
        wrap: (List<Py.Stmt>) -> List<Py.Stmt> = { it },
    ): List<Py.Stmt> =
        listOf(
            Py.While(
                stmt.pos,
                test = expr(stmt.test),
                body = wrap(translate(stmt.body)),
                orElse = listOf(),
            ),
        )

    /** Gets the arg renaming done first for publicly named args. */
    private inline fun translateFunctionDef(
        func: TmpL.FunctionDeclarationOrMethod,
        preStmts: MutableList<Py.Stmt>,
        finish: (List<Py.Decorator>, Py.Arguments, renames: List<Py.Stmt>) -> Py.FunctionDef,
    ): Py.FunctionDef {
        defineTypeVars(preStmts, func.typeParameters.ot.typeParameters)
        val decorators = mutableListOf<Py.Decorator>()

        val renames = mutableListOf<Py.Stmt>()
        // This renames only the parameter-level names and leaves the internal names unchanged, supported by renames.
        // Presently, we only ever call Python functions from Temper positionally, so this should be safe.
        val args = translateParameters(func, renames)

        // If it's a generator function, we have to thread through an extra `do_await` param,
        // and adapt it.
        val doAwaitParamName = if (func.mayYield) {
            val decPos = func.pos.leftEdge
            decorators.add(
                Py.Decorator(decPos, listOf(request(AdaptGeneratorFactory).asPyId(decPos)), emptyList(), false),
            )
            val doAwait = PyIdentifierName(pyNames.unusedName("do_await_%d"))
            args.args = listOf(Py.Arg(decPos, doAwait)) + args.args
            doAwait
        } else {
            null
        }

        // Translate the function
        generatorDoAwaitNameStack.stackWithElementIfNotNull(doAwaitParamName) {
            return@translateFunctionDef finish(decorators, args, renames)
        }
    }

    private fun translateParameters(
        func: TmpL.FunctionDeclarationOrMethod,
        renames: MutableList<Py.Stmt>? = null,
    ): Py.Arguments {
        val args = mutableListOf<Py.Arg>()
        val params = func.parameters
        // Temper semantics allow required after optional, at least for lambda blocks, so track that.
        var anyOptional = false
        params.forEachFormal { pos, id, type, kind ->
            val name = id.name
            val isOptional = anyOptional || kind == ArgKind.Optional
            // Explicit optionals already carry any needed type. TODO Translate `?` types elsewhere to Optional?
            val isBonusOptional = isOptional && kind != ArgKind.Optional
            val annotation = when {
                isBonusOptional -> Py.Subscript(
                    pos,
                    request(OptionalType).asRName(pos),
                    listOf(translateAnnotation(type)),
                )
                else -> translateAnnotation(type)
            }
            val arg = if (isOptional && renames != null) {
                val (pub, pri) = pyNames.privatizeName(name)
                when (type) {
                    null -> renames.add(
                        Py.Assign.simple(
                            pri.asPyName(id.pos),
                            pub.asPyName(id.pos),
                        ),
                    )
                    else -> renames.add(
                        Py.AnnAssign(
                            id.pos,
                            pri.asPyName(id.pos),
                            annotation.deepCopy(),
                            pub.asPyName(id.pos),
                        ),
                    )
                }
                pub
            } else {
                pyNames.name(name)
            }.asPyId(id.pos)
            args.add(
                Py.Arg(
                    pos,
                    arg = arg,
                    annotation = when (kind) {
                        ArgKind.This -> null
                        else -> annotation
                    },
                    defaultValue = if (isOptional) {
                        // TODO(tjp, args): How to insert assertion into body for not really optional?
                        // TODO(tjp, args): JS and potentially others also need such assertions.
                        anyOptional = true
                        PyConstant.Unset.at(pos)
                    } else {
                        null
                    },
                    prefix = when (kind) {
                        ArgKind.This, ArgKind.Required, ArgKind.Optional -> Py.ArgPrefix.None
                        ArgKind.Rest -> Py.ArgPrefix.Star
                    },
                ),
            )
        }
        return Py.Arguments(params.pos, args = args)
    }

    private fun translateFunction(func: TmpL.FunctionDeclaration): List<Py.Stmt> = buildList {
        translateFunctionDef(func, this) { decs, args, renames ->
            Py.FunctionDef(
                pos = func.pos,
                decoratorList = decs,
                name = ident(func.name),
                args = args,
                // TODO(tjp, names): We also need to have renamed globals for rare cases of conflict with named args.
                body = translateFunctionBody(renames, func),
                returns = translateAnnotation(func.returnType),
            )
        }.also { add(it) }
    }

    private fun translateTest(func: TmpL.Test): List<Py.Stmt> {
        dependenciesBuilder?.addTest(libraryName, func)
        // Just make a test class per test block/method for now. TODO Combine all per module into single class?
        val result = Py.ClassDef(
            func.pos,
            name = Py.Identifier(func.pos, PyIdentifierName(pyNames.unusedName("TestCase%d"))),
            args = listOf(Py.CallArg(pos = func.pos, value = request(UnittestTestCase).asRName(func.pos))),
            body = listOf(
                Py.FunctionDef(
                    func.pos,
                    name = testName(func.name),
                    // Tests shouldn't have parameters nor need renames.
                    args = Py.Arguments(func.pos, listOf(Py.Arg(func.pos, arg = pyIdent(func.pos, "self")))),
                    // But mypy says it can't type the inside if not typed at function sig, so include return type.
                    returns = PyConstant.None.at(func.pos),
                    body = buildList {
                        // Docstring with raw test name. Because shortDescription in unittest.
                        add(Py.Str(func.name.pos, func.rawName).stmt())
                        // test: Test = Test()
                        var testInstanceName: Py.Name? = null
                        func.parameters.parameters.firstOrNull()?.let testParam@{ testParam ->
                            val nominalType = (testParam.type.ot as? TmpL.NominalType)
                                ?: return@testParam
                            val typeName = translateTypeName(nominalType.typeName)
                            testInstanceName = name(testParam.name)
                            add(
                                Py.AnnAssign(
                                    testParam.pos,
                                    target = testInstanceName,
                                    annotation = typeName,
                                    value = Py.Call(
                                        testParam.pos,
                                        func = typeName,
                                        args = emptyList(),
                                    ),
                                ),
                            )
                        }
                        // try: body finally: test.soft_fail_to_hard()
                        add(
                            Py.Try(
                                func.pos,
                                body = translateFunctionBody(emptyList(), func),
                                finalbody = listOf(
                                    when (testInstanceName) {
                                        null -> Py.Pass(func.pos)
                                        else -> Py.ExprStmt(func.pos, testInstanceName.method("soft_fail_to_hard"))
                                    },
                                ),
                            ),
                        )
                    },
                ),
            ),
        )
        return listOf(result)
    }

    private fun translateFunctionBody(
        renames: List<Py.Stmt>,
        func: TmpL.FunctionLike,
    ) = buildList {
        // TODO We also need to have renamed globals for rare cases of conflict with named args.
        // TODO Is the above still a valid concern?
        // TODO Why don't method bodies currently include declareReferences?
        val documentation = func.documentation?.prettyPleaseHelp()
        if (documentation != null) {
            add(translateDocString(documentation, func.pos))
        }

        // Python decides whether a `def` is a generator based on the presence of
        // a `yield` statement.
        // Put `if False: yield` in to satisfy this syntactic requirement if
        // nothing else does.
        val mayYield = when (func) {
            is TmpL.FunctionDeclaration -> func.mayYield
            is TmpL.Member,
            is TmpL.Test,
            -> false
        }
        if (mayYield && func.body?.statements?.none { it.isYieldingStatement() } == true) {
            val startPos = func.body!!.pos.leftEdge
            add(
                Py.If(
                    pos = startPos,
                    test = PyConstant.bool(false).at(startPos),
                    body = listOf(Py.ExprStmt(Py.Yield(startPos))),
                ),
            )
        }

        addAll(renames)
        addAll(declareReferences(func))
        val bodyStmts = (func.body?.statements ?: emptyList()).flatMap { translate(it) }
        if (bodyStmts.isNotEmpty()) {
            addAll(bodyStmts)
        } else {
            add(Py.Pass(func.pos.rightEdge))
        }
    }

    /**
     * In TmpL, and most languages, a name is declared everywhere it should be visible.
     * Python is a bit unusual: a name is implicitly declared by assignment and is visible
     * throughout the functional scope.
     *
     * To assign to a name that exists outside the enclosing scope, the name needs to be
     * explicitly declared as not local to the scope.
     *
     * Thus, we find any assignments to nonlocal names and add explicit `global` and `nonlocal`
     * statements for them.
     */
    private fun declareReferences(func: TmpL.FunctionLike): List<Py.Stmt> {
        val (globals, nonLocals) = findNonLocalUsages(func).partition { (name, _) ->
            pyNames.isDeclaredTopLevel(name)
        }

        fun tidy(stmts: List<Pair<ResolvedName, Position>>, con: (List<Py.Identifier>) -> Py.Stmt) =
            stmts.map { (n, pos) -> pyNames.name(n) to pos }
                .sortedBy { it.first.outputNameText }
                .chunked(NUM_GLOBALS_PER_STMT) { names ->
                    con(names.map { (n, pos) -> n.asPyId(pos) })
                }

        return tidy(globals) { lst -> Py.Global(func.pos, lst) } +
            tidy(nonLocals) { lst -> Py.Nonlocal(func.pos, lst) }
    }

    /**
     * Walk up to the containing top-level statement, and then scan from there to find any local declarations
     * *outside* our function. These are handled by Python's `nonlocal` statement.
     */
    private fun findNonLocalUsages(func: TmpL.FunctionLike): List<Pair<ResolvedName, Position>> {
        val assigns = mutableMapOf<ResolvedName, Position>()
        val locals = mutableSetOf<ResolvedName>()
        func.boundaryDescent { t ->
            when (t) {
                is TmpL.Formal -> {
                    locals.add(t.name.name)
                    true
                }
                is TmpL.RestFormal -> {
                    locals.add(t.name.name)
                    true
                }
                // Recognize a declared name or even a function, but don't descend
                is TmpL.LocalDeclaration -> {
                    locals.add(t.name.name)
                    false
                }
                // When a name is assigned but not locally declared, then we need
                // a `nonlocal` or `global` statement
                is TmpL.Assignment -> {
                    assigns.putIfAbsent(t.left.name, t.pos)
                    true
                }
                else -> true
            }
        }
        return assigns.mapNotNull { (n, p) -> if (n !in locals) n to p else null }
    }

    /**
     * Translate a support code declaration to an import. This was created by calling [PySupportNetwork] to request
     * the code and embedding it in a declaration.
     */
    private fun translateSupportDecl(decl: TmpL.SupportCodeDeclaration): List<Py.Stmt> {
        val code = decl.init.supportCode
        val declName = pyNames.name(decl.name)
        val scName = pyNames.supportCodeName(code)
        val stmts = mutableListOf<Py.Stmt>()
        if (declName != scName) {
            stmts.add(Py.Assign.simple(declName.asPyName(decl.pos), scName.asRName(decl.pos)))
        }
        when (code) {
            is PyGlobalCode -> {
                stmts.addAll(code.factory(decl.pos, declName.outputNameText))
            }

            is PySeparateCode -> {
                this.sharedSupport.add(code) // Put imports into SupportDecl so they're mobile.
            }

            is PyInlineSupportCode -> stmts.add(
                garbageStmt(decl.pos, "translateSupportDecl($decl)", "Can't declare inline support code"),
            )

            else -> stmts.add(garbageStmt(decl.pos, "translateSupportDecl($decl)", "Expected a PySupportCode"))
        }
        return stmts
    }

    private fun breakStmt(stmt: TmpL.BreakStatement): Py.Stmt = stmt.label?.let { label ->
        name(label.id).method("break_").stmt(pos = stmt.pos)
    } ?: Py.Break(stmt.pos)

    private fun continueStmt(stmt: TmpL.ContinueStatement): Py.Stmt = stmt.label?.let { label ->
        name(label.id).method("continue_").stmt(pos = stmt.pos)
    } ?: Py.Continue(stmt.pos)

    @Suppress("KotlinConstantConditions") // Be explicit about hasBreak, hasContinue for clarity
    private fun labelBlock(stmt: TmpL.LabeledStatement): List<Py.Stmt> {
        val label = stmt.label.id
        return when (val innerStmt = stmt.statement) {
            is TmpL.WhileStatement -> {
                val labelName = label.name
                val hasBreak = stmt.anyChildRecursive {
                    it is TmpL.BreakStatement && it.label?.id?.name == labelName
                }
                val hasContinue = stmt.anyChildRecursive {
                    it is TmpL.ContinueStatement && it.label?.id?.name == labelName
                }

                var outerWrap: (List<Py.Stmt>) -> List<Py.Stmt> = { it }
                var innerWrap: (List<Py.Stmt>) -> List<Py.Stmt> = { it }

                if (hasBreak && hasContinue) {
                    outerWrap = {
                        listOf(
                            Py.With.simple(
                                stmt.pos,
                                con = request(LabelPairContextManager),
                                asName = pyNames.name(label),
                                body = it,
                            ),
                        )
                    }
                    innerWrap = {
                        listOf(
                            Py.With.simple(
                                stmt.pos,
                                con = name(label).attribute("continuing", pos = stmt.pos),
                                asName = null,
                                body = it,
                            ),
                        )
                    }
                } else if (hasBreak && !hasContinue) {
                    outerWrap = {
                        listOf(
                            Py.With.simple(
                                stmt.pos,
                                con = request(LabelContextManager),
                                asName = pyNames.name(label),
                                body = it,
                            ),
                        )
                    }
                } else if (!hasBreak && hasContinue) {
                    outerWrap = {
                        listOf(
                            Py.Assign.simple(
                                name(label),
                                request(LabelContextManager).asRName(stmt.pos).call(pos = innerStmt.pos),
                            ),
                        ) + it
                    }
                    innerWrap = {
                        listOf(Py.With.simple(stmt.pos, con = name(label), asName = null, body = it))
                    }
                } else if (!hasBreak && !hasContinue) {
                    // Warn and continue
                    outerWrap = { listOf(garbageStmt(stmt.pos, "labelBlock", "break/continue $label not found")) + it }
                }

                outerWrap(translateWhile(innerStmt, innerWrap))
            }

            else -> listOf(
                Py.With.simple(
                    stmt.pos,
                    con = request(LabelContextManager),
                    asName = pyNames.name(label),
                    body = translate(innerStmt),
                ),
            )
        }
    }

    private fun translateBoilerplateCodeFoldBoundary(x: TmpL.BoilerplateCodeFoldBoundary) =
        codeFoldCommentLine(x)

    companion object {
        fun codeFoldCommentLine(x: TmpL.BoilerplateCodeFoldBoundary): Py.CommentLine =
            Py.CommentLine(x.pos, x.markerText)
    }

    private fun translateEmbeddedComment(x: TmpL.EmbeddedComment) =
        Py.CommentLine(x.pos, x.commentText)

    internal fun expr(x: TmpL.Expression): Py.Expr = when (x) {
        is TmpL.GarbageExpression -> garbageExpr(x.pos, x.diagnostic)
        is TmpL.ValueReference -> value(x.pos, x.value)
        is TmpL.AwaitExpression -> {
            val pos = x.pos
            val doAwait = generatorDoAwaitNameStack.lastOrNull()
            val promiseExpr = expr(x.promise)
            if (doAwait != null) {
                Py.Yield(
                    pos = pos,
                    value = Py.Call(
                        pos = pos,
                        func = Py.Name(pos, doAwait, null),
                        args = listOf(Py.CallArg(promiseExpr)),
                    ),
                )
            } else {
                // Top-level await is not allowed, but this is better
                // than just putting out garbageExpr.
                Py.Await(pos, expr(x.promise))
            }
        }
        is TmpL.BubbleSentinel -> Py.Call(
            x.pos,
            func = request(BubbleException).asRName(x.pos),
            args = emptyList(),
        )
        is TmpL.Reference -> name(x.id)
        is TmpL.This -> name(x.id) // `self` is just another name in Python
        is TmpL.CallExpression -> when (val translated = translateCall(x)) {
            is Py.Expr -> translated
            is Py.ExprStmt -> translated.value
            else -> garbageExpr(
                x.pos,
                src = "$x",
                diagnostic = "Inline support code produced ${
                    translated::class.simpleName
                } where expression was needed",
            )
        }
        is TmpL.InfixOperation -> infixOp(x)
        is TmpL.PrefixOperation -> preOp(x)
        is TmpL.InstanceOfExpression -> exprInstanceOf(x.pos, x.expr, x.checkedType.ot)
        is TmpL.CastExpression -> castExpr(x.pos, x.expr, x.checkedType.ot)
        is TmpL.UncheckedNotNullExpression -> notNullExpr(x.expression)
        is TmpL.RestParameterExpression -> todo(x)
        is TmpL.RestParameterCountExpression -> todo(x)
        is TmpL.GetAbstractProperty -> getProperty(x)
        is TmpL.GetBackedProperty -> getProperty(x)
        is TmpL.SupportCodeWrapper -> supportCode(x)
        is TmpL.FunInterfaceExpression -> translateCallable(x.callable)
    }

    internal fun subject(x: TmpL.Subject): Py.Expr = when (x) {
        is TmpL.Expression -> expr(x)
        is TmpL.TypeName -> translateTypeName(x)
    }

    private fun translateCall(x: TmpL.CallExpression): Py.Tree =
        when (val fn = x.fn) {
            is TmpL.InlineSupportCodeWrapper -> when (val sc = fn.supportCode) {
                is PyInlineSupportCode -> {
                    sc.inlineToTree(
                        x.pos,
                        x.parameters.mapGeneric {
                            TypedArg(translateParamExpr(it), it.typeOrInvalid)
                        },
                        x.type,
                        this,
                    )
                }

                else -> garbageExpr(x.pos, "expr", "inline should be PyInlineSupportCode got $sc")
            }

            else -> Py.Call(
                x.pos,
                translateCallable(x.fn),
                x.parameters.mapGeneric(::translateParam),
            )
        }

    private fun constructorReference(ref: TmpL.ConstructorReference): Py.Expr =
        translateTypeName(ref.typeName)

    private fun infixOp(operation: TmpL.InfixOperation): Py.Expr = Py.BinExpr(
        operation.pos,
        left = expr(operation.left),
        op = when (operation.op.tmpLOperator) {
            TmpLOperator.AmpAmp -> BinaryOpEnum.BoolAnd
            TmpLOperator.BarBar -> BinaryOpEnum.BoolOr
            TmpLOperator.PlusInt -> BinaryOpEnum.Add
            TmpLOperator.EqEqInt -> BinaryOpEnum.Eq
            TmpLOperator.LeInt -> BinaryOpEnum.LtEq
            TmpLOperator.LtInt -> BinaryOpEnum.Lt
            TmpLOperator.GeInt -> BinaryOpEnum.GtEq
            TmpLOperator.GtInt -> BinaryOpEnum.Gt
        }.atom(operation.op.pos),
        right = expr(operation.right),
    )

    private fun preOp(operation: TmpL.PrefixOperation): Py.Expr {
        val operand = expr(operation.operand)
        return when (operation.op.kind) {
            TmpLOperatorDefinition.Bang -> operand.booleanNegate(pos = operation.pos)
            TmpLOperatorDefinition.PreDash -> UnaryOpEnum.UnarySub.invoke(operand, pos = operation.pos)
        }
    }

    private fun getProperty(px: TmpL.GetAbstractProperty): Py.Expr {
        return getProperty(px, expr(px.subject), (px.subject.type as? DefinedType)?.definition)
    }

    private fun getProperty(px: TmpL.GetBackedProperty): Py.Expr {
        return getProperty(px, subject(px.subject), px.subject.typeDefinition())
    }

    private fun getProperty(px: TmpL.GetProperty, subject: Py.Expr, type: TypeDefinition?): Py.Expr {
        val property = px.property
        val propertyName = when {
            // If it's private, handle specially.
            property is TmpL.ExternalPropertyId && type is TypeShape -> {
                // Check for instance getters.
                type.methods.firstOrNull { method ->
                    method.methodKind == MethodKind.Getter &&
                        method.symbol.text == property.name.dotNameText &&
                        method.visibility != Visibility.Public
                }?.let {
                    val name = temperToPython(property.name.dotNameText)
                    // Get out early with an explicit call.
                    return@getProperty Py.Call(
                        px.pos,
                        func = Py.Attribute(px.pos, subject, pyIdent(px.pos, name.privateGetter())),
                        args = listOf(),
                    )
                }
                // Check for static properties if we didn't have an instance getter.
                type.staticProperties.firstOrNull { propShape ->
                    propShape.symbol.text == property.name.dotNameText &&
                        propShape.visibility != Visibility.Public
                }?.let { pyIdent(property.name.pos, temperToPython(property.name.dotNameText).privatize()) }
            }
            else -> null
        } ?: pyPropertyName(property)
        // Use getter property if we didn't get out early above.
        return Py.Attribute(px.pos, subject, propertyName)
    }

    private fun supportCode(px: TmpL.SupportCodeWrapper): Py.Expr =
        request(px.supportCode as PySupportCode).asPyName(px.pos)

    private fun exprInstanceOf(pos: Position, expression: TmpL.Expression, type: TmpL.Type): Py.Expr {
        val subj = expr(expression)
        val pyType = translateType(type, argless = true)
        return request(IsInstance).asRName(pos).call(subj, pyType, pos = pos)
    }

    private fun castExpr(pos: Position, expression: TmpL.Expression, targetType: TmpL.Type): Py.Expr {
        val subj = expr(expression)
        fun cast(caster: PySupportCode, vararg py: Py.Expr) =
            request(caster).asRName(pos).call(subj, *py, pos = pos)

        val typeDefinition = (targetType as? TmpL.NominalType)?.typeName?.sourceDefinition
        return when (typeDefinition) {
            WellKnownTypes.voidTypeDefinition, WellKnownTypes.nullTypeDefinition -> cast(CastNone)
            // We can't use type args here because that fails in `isinstance`.
            // TypeError: Subscripted generics cannot be used with class and instance checks
            // Alternatively, we could strip type args at runtime, but that's expensive and misleading.
            else -> cast(CastByType, translateType(targetType, argless = true))
        }
    }

    private fun notNullExpr(x: TmpL.Expression): Py.Expr = expr(x)

    private fun translateCallable(f: TmpL.Callable): Py.Expr = when (f) {
        is TmpL.MethodReference -> subject(f.subject).attribute(methodReferenceNameText(f), pos = f.pos)
        is TmpL.InlineSupportCodeWrapper -> garbageExpr(f.pos, "translateCallable", "$f should have been eliminated")
        is TmpL.FnReference -> name(f.id)
        is TmpL.ConstructorReference -> constructorReference(f)
        is TmpL.FunInterfaceCallable -> expr(f.expr)
        is TmpL.GarbageCallable ->
            garbageExpr(f.pos, "${f.javaClass.name}: $f", f.diagnostic?.text)
    }

    private fun translateParam(p: TmpL.Actual): Py.CallArg = Py.CallArg(p.pos, value = translateParamExpr(p))

    private fun translateParamExpr(p: TmpL.Actual): Py.Expr = when (p) {
        is TmpL.Expression -> expr(p)
        is TmpL.RestSpread -> Py.Starred(p.pos, name(p.parameterName))
    }

    private fun translateFunctionType(type: TmpL.FunctionType, argless: Boolean = false): Py.Expr {
        val callable = request(CallableType).asRName(type.pos)
        return when {
            argless -> callable
            else -> {
                val args = mutableListOf<Py.Expr>()
                type.valueFormals.formals.map { formal ->
                    if (formal.name?.symbol != impliedThisSymbol) {
                        args.add(translateType(formal.type))
                    }
                }
                Py.Subscript(
                    type.pos,
                    callable,
                    listOf(
                        Py.ListExpr(type.pos, args),
                        translateType(type.returnType),
                    ),
                )
            }
        }
    }

    private fun translateNominalType(type: TmpL.NominalType, argless: Boolean = false): Py.Expr =
        if (!argless && type.params.isNotEmpty()) {
            fun mapParams(params: Iterable<TmpL.AType> = type.params) =
                params.map { translateType(it.ot) }
            fun invoke(
                code: PySupportCode,
                params: Iterable<TmpL.AType> = type.params,
            ): Py.Subscript = Py.Subscript(
                type.pos,
                request(code).asRName(type.pos),
                mapParams(params),
            )
            val typeDefinition = when (val typeName = type.typeName) {
                is TmpL.TemperTypeName -> typeName.typeDefinition
                is TmpL.ConnectedToTypeName -> null
            }
            when (typeDefinition) {
                WellKnownTypes.emptyTypeDefinition -> Py.Tuple(type.pos, emptyList())
                WellKnownTypes.listTypeDefinition,
                WellKnownTypes.listedTypeDefinition,
                -> invoke(SequenceType)
                WellKnownTypes.listBuilderTypeDefinition -> invoke(MutableSequenceType)
                WellKnownTypes.pairTypeDefinition -> invoke(PairConstructor)
                WellKnownTypes.mapTypeDefinition -> invoke(MappingProxyType)
                WellKnownTypes.mapBuilderTypeDefinition -> invoke(TypingDict)
                WellKnownTypes.generatorTypeDefinition,
                WellKnownTypes.safeGeneratorTypeDefinition,
                -> invoke(
                    TypingGenerator,
                    buildList {
                        addAll(type.params)
                        val rPos = type.pos.rightEdge
                        val nullType = TmpL.NominalType(
                            rPos,
                            TmpL.TemperTypeName(rPos, WellKnownTypes.nullTypeDefinition),
                            emptyList(),
                        )
                        // docs.python.org/3/library/typing.html#typing.Generator explains:
                        // > A generator can be annotated by the generic type
                        // > `Generator[YieldType, SendType, ReturnType]`.
                        // > ...
                        // > If your generator will only yield values, set the SendType and ReturnType to None
                        repeat(2) { add(nullType.aType) }
                    },
                )
                // ValueResult is a tuple of 1, DoneResult is None
                WellKnownTypes.valueResultTypeDefinition -> Py.Tuple(type.pos, mapParams())
                WellKnownTypes.doneResultTypeDefinition -> PyConstant.None.at(type.pos)
                WellKnownTypes.generatorResultTypeDefinition -> Py.BinExpr(
                    // [T] | None
                    type.pos,
                    Py.Tuple(type.pos, mapParams()),
                    Py.BinaryOp(type.pos.rightEdge, BinaryOpEnum.BitwiseOr, PyOperatorDefinition.BitwiseOr),
                    PyConstant.None.at(type.pos.rightEdge),
                )
                WellKnownTypes.stringIndexOptionTypeDefinition,
                WellKnownTypes.noStringIndexTypeDefinition,
                WellKnownTypes.stringIndexTypeDefinition,
                -> invoke(IntType)
                else -> Py.Subscript(
                    type.pos,
                    translateTypeName(type.typeName),
                    type.params.map {
                        translateType(it.ot)
                    },
                )
            }
        } else {
            translateTypeName(type.typeName)
        }

    private fun translateTypeName(typeName: TmpL.TypeName): Py.Expr = when (typeName) {
        is TmpL.TemperTypeName -> {
            val word = typeName.typeDefinition.word
            (typeDefsToPyExpr[word]?.invoke(this, typeName.pos))
                ?: (typeDefsToSepCode[word]?.let { request(it).asRName(typeName.pos) })
                ?: pyNames.name(typeName.typeDefinition.name).asPyName(typeName.pos)
        }
        is TmpL.ConnectedToTypeName -> {
            val connectedType = typeName.name as PyConnectedType
            val outName = request(connectedType)
            Py.Name(
                pos = typeName.pos,
                id = PyIdentifierName(outName.outputNameText),
                sourceIdentifier = outName.sourceName as? TemperName,
            )
        }
    }

    private fun translateTypeUnion(type: TmpL.TypeUnion, argless: Boolean = false): Py.Expr {
        val types = type.types.filter { types ->
            when (types) {
                is TmpL.BubbleType -> false
                is TmpL.NeverType -> false
                is TmpL.GarbageType -> false
                is TmpL.FunctionType -> true
                is TmpL.TypeIntersection -> true
                is TmpL.TypeUnion -> true
                is TmpL.NominalType -> when (val typeName = types.typeName) {
                    is TmpL.TemperTypeName -> when (typeName.typeDefinition) {
                        WellKnownTypes.voidTypeDefinition -> false
                        WellKnownTypes.closureRecordTypeDefinition -> false
                        WellKnownTypes.typeTypeDefinition -> false
                        else -> true
                    }
                    is TmpL.ConnectedToTypeName -> true
                }
                is TmpL.TopType -> true
            }
        }
        return when (types.size) {
            0 -> PyConstant.None.at(type.pos)
            1 -> translateType(types[0], argless = argless)
            else -> Py.Subscript(
                type.pos,
                request(UnionType).asRName(type.pos),
                types.map { translateType(it, argless = argless) },
            )
        }
    }

    private fun translateTypeIntersection(type: TmpL.TypeIntersection): Py.Expr {
        return Py.Str(type.pos, "$type")
    }

    private fun translateType(type: TmpL.AType, argless: Boolean = false): Py.Expr =
        translateType(type.ot, argless = argless)

    private fun translateType(type: TmpL.Type, argless: Boolean = false): Py.Expr = when (type) {
        is TmpL.GarbageType -> request(AnyType).asRName(type.pos)
        is TmpL.TopType -> request(AnyType).asRName(type.pos)
        is TmpL.FunctionType -> translateFunctionType(type, argless = argless)
        is TmpL.NeverType -> request(NoReturnType).asRName(type.pos)
        is TmpL.BubbleType -> request(NoReturnType).asRName(type.pos)
        is TmpL.NominalType -> translateNominalType(type, argless = argless)
        is TmpL.TypeUnion -> translateTypeUnion(type, argless = argless)
        is TmpL.TypeIntersection -> translateTypeIntersection(type)
    }

    private fun translateAnnotation(type: TmpL.AType?): Py.Expr =
        translateAnnotation(type?.ot)

    private fun translateAnnotation(type: TmpL.Type?): Py.Expr =
        if (type != null) {
            Py.TypeStr(type.pos, translateType(type))
        } else {
            request(AnyType).asRName(unknownPos)
        }

    private fun translateDocString(documentation: Helpful, pos: Position): Py.Stmt {
        val docPos = pos.leftEdge
        // TODO(mvs): Is there some variant of indented triple-quoted string we could use?
        return Py.ExprStmt(docPos, Py.Str(docPos, documentation.longHelp()))
    }

    private fun value(pos: Position, v: Value<*>): Py.Expr =
        when (val rt = v.typeTag) {
            TBoolean -> PyConstant.bool(TBoolean.unpack(v)).at(pos)
            TFloat64 -> when (val unpacked = TFloat64.unpackParsed(v).value) {
                Double.NEGATIVE_INFINITY -> UnaryOpEnum.UnarySub(request(mathInf).asRName(pos), pos = pos)
                Double.POSITIVE_INFINITY -> request(mathInf).asRName(pos)
                Math.E -> request(Float64E).asRName(pos) // TODO Negative E and PI?
                Math.PI -> request(Float64Pi).asRName(pos)
                else -> when (unpacked.isNaN()) {
                    true -> request(mathNan).asRName(pos)
                    false -> Py.Num(pos = pos, n = unpacked)
                }
            }

            TInt -> Py.Num(pos, TInt.unpack(v))
            TInt64 -> Py.Num(pos, TInt64.unpack(v))
            TString -> Py.Str(pos, TString.unpack(v))
            TFunction -> todo(pos, rt, v)
            TList -> Py.Tuple(pos, TList.unpack(v).map { value(pos, it) })
            TListBuilder -> Py.ListExpr(pos, TListBuilder.unpack(v).map { value(pos, it) })
            TMap, TMapBuilder -> todo(pos, rt, v)
            TNull -> PyConstant.None.at(pos)
            TProblem -> todo(pos, rt, v)
            TStageRange -> todo(pos, rt, v)
            TSymbol -> request(SymbolType).asPyName(pos).call(Py.Str(pos, TSymbol.unpack(v).text), pos = pos)
            TType -> todo(pos, rt, v)
            TVoid -> PyConstant.None.at(pos)
            TClosureRecord -> todo(pos, rt, v)
            is TClass -> todo(pos, rt, v)
        }

    private inline fun <reified T : OutTree<*>> todo(t: T, msg: String? = null): Py.Expr =
        garbageExpr(t.pos, "${t.javaClass.name}: $t", msg)

    private inline fun <reified T : Any, reified U : Any> todo(
        pos: Position,
        t: T,
        u: U,
        msg: String? = null,
    ): Py.Expr = garbageExpr(pos, "${t.javaClass.name}: $t, ${u.javaClass.name}: $u", msg)
}

/** The number of global declarations per `global` statement. */
const val NUM_GLOBALS_PER_STMT = 8

@Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
private inline fun <reified T : Any, U : Any> at(t: T, f: () -> U): U {
    try {
        return f()
    } catch (src: Error) {
        throw ContextError("In ${t.javaClass.name}: $t", src)
    } catch (src: Exception) {
        throw ContextException("In ${t.javaClass.name}: $t", src)
    }
}

private class ContextError(context: String, src: Error) : Error(context, src)

private class ContextException(context: String, src: Exception) : Exception(context, src)

private class SupportBatch {
    val support: MutableSet<PySupportCode> = mutableSetOf()
    val sharedSupport: MutableSet<PySupportCode> = mutableSetOf()
}
