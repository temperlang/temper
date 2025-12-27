package lang.temper.be.java

import lang.temper.ast.deepSlice
import lang.temper.ast.toLispy
import lang.temper.be.Dependencies
import lang.temper.be.java.JavaOperator.Assign
import lang.temper.be.tmpl.FnAutodoc
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.autodocFor
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.isStdLib
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapGeneric
import lang.temper.be.tmpl.mapGenericIndexed
import lang.temper.be.tmpl.toSigBestEffort
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.be.tmpl.withoutNull
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.common.modifiedUtf8ByteCount
import lang.temper.common.modifiedUtf8LenExceeds
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.type.Abstractness
import lang.temper.type.Visibility
import lang.temper.type.isVoidLike
import lang.temper.type.mentionsInvalid
import lang.temper.type.simplify
import lang.temper.type2.Signature2
import lang.temper.type2.withType
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
import lang.temper.be.java.Java as J
import lang.temper.value.DependencyCategory as DepCat

class JavaTranslator(
    /** a shared instance for consistent name mapping */
    private val topNames: JavaNames,
    private val dependenciesBuilder: Dependencies.Builder<JavaBackend>? = null,
) {
    /** Spin off an instance for a given module */
    private fun forModule(module: ModuleName, programMeta: J.ProgramMeta = J.ProgramMeta(unknownPos)): ModuleScope =
        ModuleScope(
            names = topNames.forModule(module),
            programMeta = programMeta,
            dependenciesBuilder = dependenciesBuilder,
        )

    private fun forSnippet(): ModuleScope {
        return forModule(testModuleName, J.ProgramMeta(unknownPos))
    }

    /** Prescan a snippet; used by [lang.temper.be.java.JavaTranslatorTest]. */
    fun translateSnippet(stmt: TmpL.Statement): J.BlockLevelStatement = forSnippet().stmt(stmt)

    /** Prescan a snippet; used by [lang.temper.be.java.JavaTranslatorTest]. */
    fun translateSnippet(expr: TmpL.Expression): J.Expression = forSnippet().expr(expr)

    /** Prescan a snippet; used by [lang.temper.be.java.JavaTranslatorTest]. */
    fun translateSnippet(module: TmpL.Module): List<J.Program> = forSnippet().module(module)

    /** Translate a single module. */
    fun translate(t: TmpL.Module): List<J.Program> {
        val hasEntry = !t.isStdLib
        val meta = J.ProgramMeta(
            t.pos,
            entryPoint = if (hasEntry) J.EntryPoint.MainMethod else J.EntryPoint.None,
        )
        return forModule(t.codeLocation.codeLocation, programMeta = meta).module(module = t)
    }

    /** Contains parameters and a preamble statement to introduce a method. */
    private data class ParamsPreamble(
        val parameters: List<J.MethodParameter>,
        val preamble: List<J.BlockLevelStatement>,
        val tmpLIdToParamName: Map<TmpL.Id, OutName>,
    ) {
        val parametersNoPreamble: List<J.MethodParameter> get() {
            require(preamble.isEmpty()) { "Didn't expect a preamble" }
            return parameters
        }
    }

    /** An overload needs to know its parameters and either the original or call-forwarding body. */
    private data class Overload(
        val parameters: ParamsPreamble,
        val body: J.BlockStatement,
    )

    inner class ModuleScope(
        private val names: JavaNames,
        private val programMeta: J.ProgramMeta,
        private val dependenciesBuilder: Dependencies.Builder<JavaBackend>?,
    ) {
        val programs: MutableList<J.Program> = mutableListOf()
        val moduleInfo: ModuleInfo get() = names.currentModuleInfo!!
        private val moduleFields: MutableList<J.ClassBodyDeclaration> = mutableListOf()
        private val moduleFuncs: MutableList<J.ClassBodyDeclaration> = mutableListOf()
        private val moduleInit: MutableList<J.BlockLevelStatement> = mutableListOf()
        private val moduleTestDecls: MutableList<J.ClassBodyDeclaration> = mutableListOf()
        private val moduleTestInit: MutableList<J.BlockLevelStatement> = mutableListOf()
        private var libraryName: DashedIdentifier? = null
        private var processingTestCode = false

        private fun activeDecls(decls: MutableList<J.ClassBodyDeclaration>) = when {
            processingTestCode -> moduleTestDecls
            else -> decls
        }

        /** Sometimes init code is decl init, so support that for test code. */
        private fun activeInit() = when {
            processingTestCode -> moduleTestInit
            else -> moduleInit
        }

        private fun modulePackageStatement(pos: Position) =
            J.PackageStatement(pos, packageName = moduleInfo.packageName.toQualIdent(pos))

        private fun programMeta(pos: Position, isEntry: Boolean = false, isTestClass: Boolean = false) =
            J.ProgramMeta(
                pos,
                entryPoint = if (isEntry) programMeta.entryPoint else J.EntryPoint.None,
                sourceDirectory = if (isTestClass) J.SourceDirectory.TestJava else J.SourceDirectory.MainJava,
                testClass = isTestClass,
            )

        fun module(module: TmpL.Module): List<J.Program> {
            libraryName = module.libraryName
            val result = module.result
            topLevels@ for (tl in module.topLevels) {
                try {
                    when (tl.dependencyCategory()) {
                        DepCat.Production -> {}
                        DepCat.Test -> processingTestCode = true
                        null -> continue@topLevels
                    }
                    when (tl) {
                        is TmpL.BoilerplateCodeFoldBoundary -> moduleInit.add(boundary(tl))
                        is TmpL.EmbeddedComment -> moduleInit.add(J.CommentLine(tl.pos, tl.commentText))
                        is TmpL.GarbageTopLevel -> moduleInit.add(garbageComment(tl))
                        is TmpL.FunctionDeclaration -> moduleFunction(tl)
                        is TmpL.ModuleInitBlock -> tl.body.statements.forEach {
                            moduleInit.add(stmt(it))
                        }
                        is TmpL.ModuleLevelDeclaration -> modLevelDeclare(tl)
                        is TmpL.PooledValueDeclaration -> TODO()
                        is TmpL.SupportCodeDeclaration -> {}
                        is TmpL.Test -> moduleTest(tl)
                        is TmpL.TypeDeclaration ->
                            when (tl.kind) {
                                TmpL.TypeDeclarationKind.Class -> classDeclare(tl)
                                TmpL.TypeDeclarationKind.Interface -> interfaceDeclare(tl)
                                TmpL.TypeDeclarationKind.Enum -> TODO()
                            }
                        is TmpL.TypeConnection -> {}
                    }
                } finally {
                    processingTestCode = false
                }
            }
            var hasResult = false
            val resultType = if (result != null && !result.type.isVoidLike && !result.type.mentionsInvalid) {
                hasResult = true
                JavaType.fromFrontend(result.type, names)
            } else {
                Void.asReferenceType()
            }.toTypeAst(result?.pos ?: module.pos)
            val pos = result?.pos ?: module.pos
            val fieldName = names.specialIdent(pos, MODULE_EXPORT_NAME)
            if (hasResult) {
                moduleFields.add(
                    J.FieldDeclaration(
                        pos,
                        mods = J.FieldModifiers(pos, modAccess = J.ModAccess.Public, modStatic = J.ModStatic.Static),
                        type = resultType,
                        variable = fieldName,
                        initializer = null,
                    ),
                )
                moduleInit.add(
                    Assign.assign(left = fieldName.asNameExpr(), right = expr(result!!)).exprStatement(),
                )
            }
            if (programMeta.entryPoint == J.EntryPoint.MainMethod) {
                entryPoint(module, hasResult = hasResult)
            }

            globalishProgram(module.pos, name = moduleInfo.globalsClassName, isTestClass = false) {
                // TODO Once TmpL helpers in place, prune and/or move to test class unused non-exported globals & types.
                addAll(moduleFields)
                addAll(moduleFuncs)
                moduleInit.asBlockNullable(module.pos) ?. let {
                    add(
                        J.Initializer(
                            module.pos,
                            modStatic = J.ModStatic.Static,
                            body = it,
                        ),
                    )
                }
            }
            utility(programs)
            if (moduleTestDecls.isNotEmpty()) {
                globalishProgram(module.pos, name = moduleInfo.testClassName, isTestClass = true) {
                    addAll(moduleTestDecls)
                }
            }
            return programs
        }

        /** Build a class for static members. */
        private fun globalishProgram(
            pos: Position,
            name: OutName,
            isTestClass: Boolean,
            buildMembers: MutableList<J.ClassBodyDeclaration>.() -> Unit,
        ) {
            val classBody: List<J.ClassBodyDeclaration> = buildList {
                add(
                    J.ConstructorDeclaration(
                        pos,
                        mods = J.ConstructorModifiers(pos, modAccess = J.ModAccess.Private),
                        name = J.Identifier(pos, name),
                        parameters = listOf(),
                        body = J.BlockStatement(pos, listOf()),
                    ),
                )
                buildMembers()
            }
            programs.add(
                J.TopLevelClassDeclaration(
                    pos = pos,
                    packageStatement = modulePackageStatement(pos),
                    programMeta = programMeta(pos, isTestClass = isTestClass),
                    classDef = J.ClassDeclaration(
                        pos = pos,
                        mods = J.ClassModifiers(
                            pos,
                            modAccess = J.ModAccess.Public,
                            modFinal = J.ModSealedFinal.Final,
                        ),
                        name = J.Identifier(pos, name),
                        body = classBody,
                    ),
                ),
            )
        }

        private fun entryPoint(module: TmpL.Module, hasResult: Boolean) {
            val pos = module.pos
            val accessResult = if (hasResult) {
                moduleInfo
                    .qualifiedClassName(DepCat.Production)
                    .qualifyKnownSafe(MODULE_EXPORT_NAME)
                    .toNameExpr(pos)
            } else {
                javaLangClassForName.staticMethod(
                    J.StringLiteral(pos, moduleInfo.qualifiedClassName(DepCat.Production).fullyQualified),
                    pos = pos,
                )
            }.asExprStmtExpr()
                .exprStatement()
            val waitUntilTasksComplete =
                temperWaitUntilTasksComplete.staticMethod(pos = pos.rightEdge)
                    .exprStatement()

            val mainThrows: List<J.ClassType> = if (hasResult) {
                listOf()
            } else {
                listOf(javaLangClassNotFoundException.toClassType(pos))
            }

            programs.add(
                J.TopLevelClassDeclaration(
                    pos,
                    packageStatement = modulePackageStatement(pos),
                    programMeta = programMeta(pos, isEntry = true, false),
                    classDef = J.ClassDeclaration(
                        pos,
                        mods = J.ClassModifiers(pos, modAccess = J.ModAccess.Public, modFinal = J.ModSealedFinal.Final),
                        name = J.Identifier(pos, moduleInfo.entryClassName),
                        body = listOf(
                            J.ConstructorDeclaration(
                                module.pos,
                                mods = J.ConstructorModifiers(module.pos, modAccess = J.ModAccess.Private),
                                name = J.Identifier(module.pos, moduleInfo.entryClassName),
                                parameters = listOf(),
                                body = J.BlockStatement(module.pos, listOf()),
                            ),
                            J.MethodDeclaration(
                                pos,
                                mods = J.MethodModifiers(
                                    pos,
                                    modAccess = J.ModAccess.Public,
                                    modStatic = J.ModStatic.Static,
                                ),
                                result = J.VoidType(pos),
                                name = J.Identifier(pos, "main"),
                                parameters = listOf(
                                    J.FormalParameter(
                                        pos,
                                        type = J.ArrayType(pos, javaLangString.toClassType(pos)),
                                        name = J.Identifier(pos, "args"),
                                    ),
                                ),
                                exceptionTypes = mainThrows,
                                body = J.BlockStatement(
                                    pos,
                                    listOf(
                                        J.ExpressionStatement(
                                            pos,
                                            temperInitSimpleLogging.staticMethod(emptyList(), pos),
                                        ),
                                        accessResult,
                                        waitUntilTasksComplete,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        fun utility(programs: MutableList<J.Program>) {
            val pos = unknownPos
            val samPackageName = (names.currentModuleInfo ?: return).packageName.qualifyKnownSafe(SAM_PACKAGE_NAME)
            // For now, any SAM types that need to be synthesized
            sams@ for ((funcType, samType) in names.samTypes) {
                if (!samType.synthetic) continue
                val (pkgLead, klassName) = samType.klassName.split()
                // Only build sams for the current module.
                pkgLead == samPackageName || continue@sams
                val resultJ = JavaType.fromFrontend(funcType.returnType2, names).toResultTypeAst(pos)
                val methodName = J.Identifier(pos, samType.method)
                var firstOptional = 0
                val methodParams: List<J.MethodParameter> = buildList {
                    var pCount = 1
                    var inRequired = true
                    for (formal in funcType.valueFormalsExceptThis) {
                        if (inRequired) {
                            if (formal.isOptional) {
                                inRequired = false
                            } else {
                                firstOptional++
                            }
                        }
                        var type = JavaType.fromFrontend(formal.type, names)
                        if (formal.isOptional) type = type.makeNullable()
                        add(
                            J.FormalParameter(
                                pos,
                                type = type.toTypeAst(pos),
                                name = J.Identifier(
                                    pos,
                                    "arg${pCount++}",
                                ),
                            ),
                        )
                    }
                    funcType.restInputsType?.let { type ->
                        add(
                            J.VariableArityParameter(
                                pos,
                                type = JavaType.fromFrontend(type, names).toTypeAst(pos),
                                name = J.Identifier(pos, "arg${pCount++}"),
                            ),
                        )
                    }
                }
                val typeParams = J.TypeParameters(
                    pos,
                    funcType.typeFormals.map {
                        JavaTypeParam.fromFormal(it, names).toTypeParamAst(it.pos)
                    },
                )
                val numParams = methodParams.size
                val methods = (firstOptional..numParams).map { n ->
                    J.InterfaceMethodDeclaration(
                        pos = pos,
                        mods = if (n < numParams) J.ModInterfaceMethod.Default else J.ModInterfaceMethod.Abstract,
                        result = resultJ.deepCopy(),
                        name = methodName.deepCopy(),
                        parameters = methodParams.deepSlice(0, n),
                        typeParams = typeParams.deepCopy(),
                        body = if (n < numParams) {
                            val call = J.InstanceMethodInvocationExpr(
                                pos = pos,
                                expr = null,
                                method = methodName.deepCopy(),
                                args = methodParams.mapIndexedNotNull { p, parameter ->
                                    when {
                                        p < n -> parameter.name.asNameExpr()
                                        parameter is J.VariableArityParameter -> null
                                        else -> J.NullLiteral(pos)
                                    }?.asArgument()
                                },
                            )
                            when {
                                funcType.returnType2.isVoidLike -> call.exprStatement()
                                else -> J.ReturnStatement(pos, call)
                            }.asBlock()
                        } else {
                            null
                        },
                    )
                }
                programs.add(
                    J.TopLevelClassDeclaration(
                        pos,
                        packageStatement = J.PackageStatement(pos, pkgLead.toQualIdent(pos)),
                        // For now, leave all SAM types in main code. TODO Figure out when to split them up?
                        programMeta = programMeta(pos, isTestClass = false),
                        classDef = J.InterfaceDeclaration(
                            pos,
                            // TODO When should SAM types be public or not?
                            mods = J.InterfaceModifiers(pos, modAccess = J.ModAccess.Public),
                            name = J.Identifier(pos, klassName),
                            body = methods,
                        ),
                    ),
                )
            }
        }

        private fun boundary(t: TmpL.BoilerplateCodeFoldBoundary) = when (t) {
            is TmpL.BoilerplateCodeFoldEnd -> J.CommentLine(t.pos, t.markerText)
            is TmpL.BoilerplateCodeFoldStart -> J.CommentLine(t.pos, t.markerText)
        }

        private fun overloads(
            adj: BoxedTypeAdjustments?,
            px: TmpL.Parameters,
            originalBody: J.BlockStatement,
            callForwardBody: (List<J.Argument>) -> J.BlockStatement,
        ): List<Overload> = buildList {
            val originalFormals = withAdjustments(parameters(px), adj)
            add(Overload(originalFormals, originalBody))
            for (idx in px.parameters.lastIndex downTo 0) {
                val overloadParameters = px.deepCopy()
                overloadParameters.restParameter = null
                // Stop generating overloads once we have a required parameter.
                if (!overloadParameters.parameters[idx].optional) {
                    break
                }
                overloadParameters.parameters = overloadParameters.parameters.subList(0, idx).toList()

                val overloadFormals = withAdjustments(parameters(overloadParameters), adj)
                val callForwardArgs: List<J.Argument> = originalFormals.parameters.mapIndexed { ix, formal ->
                    if (ix in overloadFormals.parameters.indices) {
                        formal.name.deepCopy().asNameExpr()
                    } else {
                        J.NullLiteral(formal.pos)
                    }.asArgument()
                }
                add(Overload(overloadFormals, callForwardBody(callForwardArgs)))
            }
        }

        private fun moduleFunction(t: TmpL.FunctionDeclaration) {
            val autodoc = autodocFor(t)
            val body = stmt(t.body).asBlock(t.pos).preface(
                translateMetadata(t.pos.leftEdge, t.metadata),
            )
            val name = names.moduleFunction(t.name).second.toIdentifier(t.name.pos)
            val result = resultType(names, t.returnType, pos = t.returnType.pos)
            val mods = J.MethodModifiers(t.pos, modAccess = access(t.name), modStatic = J.ModStatic.Static)
            val typeParams: J.TypeParameters = typeFormals(t.typeParameters)
            val overloads = overloads(null, t.parameters, body) { args ->
                J.StaticMethodInvocationExpr(
                    t.pos,
                    type = null,
                    method = name.deepCopy(),
                    args = args,
                ).exprOrReturnStatement(shouldReturn = result !is J.VoidType).asBlock()
            }
            for (over in overloads) {
                val parameters = over.parameters
                activeDecls(moduleFuncs).add(
                    J.MethodDeclaration(
                        pos = t.pos,
                        javadoc = javadoc(autodoc, parameters.tmpLIdToParamName),
                        typeParams = typeParams,
                        result = result,
                        mods = mods,
                        name = name,
                        parameters = parameters.parameters,
                        body = over.body.preface(parameters.preamble),
                    ),
                )
            }
        }

        private fun moduleTest(t: TmpL.Test) {
            val name = names.moduleFunction(t.name).second.toIdentifier(t.name.pos)
            dependenciesBuilder?.addTest(libraryName, t, name.outName.outputNameText)

            fun wrapTest(testParam: TmpL.Formal?): J.BlockStatement {
                val nominalType = (testParam?.type?.ot as? TmpL.NominalType)
                    ?: return block(t.body)
                val typeRef = classTypeRef(nominalType)
                val testInstanceId = names.lookupRegularLocalNameObj(testParam.name).outName
                // Test test = new Test();
                val localDecl =
                    J.LocalVariableDeclaration(
                        t.pos,
                        type = typeRef,
                        name = testInstanceId.toIdentifier(testParam.name.pos),
                        expr = J.InstanceCreationExpr(
                            testParam.pos,
                            type = typeRef,
                            args = emptyList(),
                        ),
                    )
                // try { body } finally { test.softFailToHard(); }
                val tryFinally = J.TryStatement(
                    t.body.pos,
                    bodyBlock = block(t.body),
                    catchBlocks = emptyList(),
                    finallyBlock = J.BlockStatement(
                        t.body.pos,
                        listOf(
                            J.ExpressionStatement(
                                t.body.pos,
                                testInstanceId.toNameExpr(t.pos).method("softFailToHard"),
                            ),
                        ),
                    ),
                )
                return J.BlockStatement(
                    t.body.pos,
                    listOf(
                        localDecl,
                        tryFinally,
                    ),
                )
            }
            moduleTestDecls.add(
                J.MethodDeclaration(
                    t.pos,
                    anns = listOf(
                        junitTest.toAnnotation(t.pos),
                    ),
                    result = J.VoidType(t.pos),
                    mods = J.MethodModifiers(t.pos, modAccess = J.ModAccess.Public), // not static
                    name = name,
                    parameters = emptyList(),
                    body = wrapTest(t.parameters.parameters.firstOrNull())
                        .preface(translateMetadata(t.pos, t.metadata)),
                ),
            )
        }

        private fun translateMetadata(pos: Position, metadata: List<TmpL.DeclarationMetadata>) =
            if (RENDER_META_COMMENTS) {
                metadata.map { metaComment(pos, it) }
            } else {
                emptyList()
            }

        private fun modLevelDeclare(t: TmpL.ModuleLevelDeclaration) {
            if (RENDER_META_COMMENTS) {
                for (meta in t.metadata) {
                    moduleInit.add(metaComment(t.pos.leftEdge, meta))
                }
            }
            val typeExpr = JavaType.fromTmpL(t.type, names)
            val fieldName = names.moduleField(t.name).second.toIdentifier(t.pos)
            activeDecls(moduleFields).add(
                J.FieldDeclaration(
                    t.pos,
                    mods = J.FieldModifiers(
                        t.pos,
                        modAccess = access(t.name),
                        modFinal = final(t.assignOnce),
                        modStatic = J.ModStatic.Static,
                    ),
                    type = typeExpr.toTypeAst(t.type.pos),
                    variable = fieldName,
                    initializer = null,
                ),
            )
            t.init ?. let { init ->
                activeInit().add(
                    Assign.assign(
                        left = fieldName.asNameExpr(t.pos),
                        right = expr(init),
                    ).exprStatement(t.pos),
                )
            }
        }

        private fun classTypeRef(type: TmpL.NominalType) =
            JavaType.fromTmpL(type, names).asReferenceType().toTypeAst(type.pos)

        /**
         * Make a builder class for the given target class and function that provides parameters.
         * TODO Maybe adjustable to calling factories and/or other methods/functions?
         */
        private fun classBuilder(
            className: J.Identifier,
            typeParams: J.TypeParameters,
            fn: TmpL.FunctionLike,
        ): J.ClassDeclaration? {
            // If only `this` plus up to 1 more, don't bother with builder. TODO Instead checked named/optional?
            fn.parameters.parameters.count { it.name != fn.parameters.thisName } <= 1 && return null
            // And for now, skip those with rest parameters. TODO Extract to list value?
            fn.parameters.restParameter != null && return null
            // Build the builder.
            val pos = fn.pos
            val builderName = OutName("Builder", null).toIdentifier(pos)
            val builderQual = J.QualIdentifier(pos, listOf(builderName.deepCopy()))
            return J.ClassDeclaration(
                pos = pos,
                mods = J.ClassModifiers(
                    pos = pos,
                    modAccess = J.ModAccess.Public,
                    modFinal = J.ModSealedFinal.Final,
                    modStatic = J.ModStatic.Static,
                ),
                // Going to single constructor, and if we have no nest classes in Temper, then fixed name is fine.
                name = builderName,
                params = typeParams.deepCopy(),
                body = buildList {
                    val publicMods = J.MethodModifiers(pos, modAccess = J.ModAccess.Public)
                    val typeArgs = when {
                        typeParams.params.isEmpty() -> null
                        else -> J.TypeArguments(
                            pos,
                            args = typeParams.params.map { typeParam ->
                                val annType = J.AnnotatedQualIdentifier(
                                    typeParam.pos,
                                    pkg = listOf(),
                                    anns = listOf(),
                                    type = typeParam.type.deepCopy(),
                                )
                                J.ReferenceTypeArgument(typeParam.pos, annType = annType)
                            },
                        )
                    }
                    // TODO Constructor for positional-only params.
                    // Named params to fields/setters. TODO Just optionals here for now?
                    val fields = mutableListOf<J.Identifier>()
                    val requireds = mutableListOf<J.Identifier>()
                    val requiredFields = mutableListOf<J.Identifier>()
                    val requiredNonNulls = mutableListOf<J.Identifier>()
                    params@ for (param in fn.parameters.parameters) {
                        param.name == fn.parameters.thisName && continue
                        // Value field.
                        val fieldName = names.field(param.name)
                        fields.add(fieldName)
                        val fieldJavaType = JavaType.fromTmpL(param.type, names)
                        val fieldType = fieldJavaType.toTypeAst(param.type.pos)
                        val fieldVar = J.VariableDeclarator(param.name.pos, variable = fieldName, initializer = null)
                        J.FieldDeclaration(param.pos, type = fieldType, variables = listOf(fieldVar)).also { add(it) }
                        // Field to track if the value has been set.
                        val setName = when {
                            param.optional -> null
                            fieldJavaType is ReferenceType && !fieldJavaType.isNullable -> {
                                // Non-nullable reference type, so null indicates unset.
                                requiredNonNulls.add(fieldName)
                                null
                            }
                            else -> {
                                // Either a primitive type or a nullable reference type, so we can't check nullness.
                                val setName = J.Identifier(fieldName.pos, "${fieldName.outName.outputNameText}__set")
                                requireds.add(setName)
                                requiredFields.add(fieldName)
                                val setType = Primitive.JavaBoolean.toTypeAst(fieldType.pos)
                                val setVar = J.VariableDeclarator(fieldVar.pos, variable = setName, initializer = null)
                                val setVars = listOf(setVar)
                                J.FieldDeclaration(param.pos, type = setType, variables = setVars).also { add(it) }
                                setName
                            }
                        }
                        // Setter for the field with chain return.
                        J.MethodDeclaration(
                            pos = param.pos,
                            mods = publicMods.deepCopy(),
                            result = J.ClassType(pos, type = builderQual.deepCopy(), args = typeArgs?.deepCopy()),
                            name = fieldName.deepCopy(),
                            parameters = listOf(
                                J.FormalParameter(param.pos, type = fieldType.deepCopy(), name = fieldName.deepCopy()),
                            ),
                            body = J.BlockStatement(
                                param.pos,
                                body = buildList {
                                    // Optional set tracking.
                                    if (setName != null) {
                                        Assign.assign(
                                            setName.asNameExpr(),
                                            J.BooleanLiteral(param.pos, true),
                                        ).also { add(it.exprStatement()) }
                                    }
                                    // Value assignment.
                                    Assign.assign(
                                        J.ThisExpr(param.pos).field(fieldName.deepCopy()),
                                        fieldName.asNameExpr(),
                                    ).also { add(it.exprStatement()) }
                                    J.ReturnStatement(param.pos, J.ThisExpr(param.pos)).also { add(it) }
                                },
                            ),
                        ).also { add(it) }
                    }
                    // Build method.
                    J.MethodDeclaration(
                        pos,
                        mods = publicMods,
                        result = J.ClassType(
                            pos,
                            type = J.QualIdentifier(pos, listOf(className.deepCopy())),
                            args = typeArgs,
                        ),
                        name = J.Identifier(pos, "build"), // TODO Ensure unique!!!
                        parameters = listOf(),
                        body = J.BlockStatement(
                            pos,
                            body = buildList {
                                if (requireds.isNotEmpty()) {
                                    // if (!a__set || b == null || ...)) { throw IllegalStateException("...") }
                                    val unsets = buildList {
                                        for (required in requireds) {
                                            add(JavaOperator.BoolComplement.prefix(required.asNameExpr()))
                                        }
                                        for (required in requiredNonNulls) {
                                            add(JavaOperator.Equals.infix(required.asNameExpr(), J.NullLiteral(pos)))
                                        }
                                    }
                                    val anyUnset = unsets.reduce { anyUnset, unset ->
                                        JavaOperator.ConditionalOr.infix(anyUnset, unset)
                                    }
                                    J.IfStatement(
                                        pos,
                                        test = anyUnset,
                                        consequent = J.BlockStatement(
                                            pos,
                                            body = buildList {
                                                // Message builder for missing field names.
                                                val messageName = J.Identifier(pos, "_message")
                                                val start = J.StringLiteral(pos, "Missing required fields:")
                                                J.LocalVariableDeclaration(
                                                    pos,
                                                    type = javaLangStringBuilder.toClassType(pos),
                                                    name = messageName,
                                                    expr = J.InstanceCreationExpr(
                                                        pos,
                                                        type = javaLangStringBuilder.toClassType(pos),
                                                        args = listOf(start.asArgument()),
                                                    ),
                                                ).also { add(it) }
                                                // Add names of missing fields.
                                                val unsetPairs = (requiredFields + requiredNonNulls).zip(unsets)
                                                for ((required, unset) in unsetPairs) {
                                                    val requiredText = required.outName.outputNameText
                                                    J.IfStatement(
                                                        pos,
                                                        test = unset.deepCopy(),
                                                        consequent = J.BlockStatement(
                                                            pos,
                                                            body = listOf(
                                                                messageName.asNameExpr().method(
                                                                    "append",
                                                                    // Comma would be nicer, but this is easier for now.
                                                                    J.StringLiteral(pos, " $requiredText"),
                                                                ).exprStatement(),
                                                            ),
                                                        ),
                                                    ).also { add(it) }
                                                }
                                                // Throw exception with message.
                                                J.ThrowStatement(
                                                    pos,
                                                    J.InstanceCreationExpr(
                                                        pos,
                                                        type = javaLangIllegalStateException.toClassType(pos),
                                                        args = listOf(
                                                            messageName.asNameExpr().method("toString").asArgument(),
                                                        ),
                                                    ),
                                                ).also { add(it) }
                                            },
                                        ),
                                    ).also { add(it) }
                                }
                                // Return built object.
                                val classQual = J.QualIdentifier(pos, listOf(className.deepCopy()))
                                J.ReturnStatement(
                                    pos,
                                    expr = J.InstanceCreationExpr(
                                        pos,
                                        type = J.ClassType(pos, type = classQual),
                                        typeArgs = when {
                                            typeParams.params.isEmpty() -> null
                                            else -> J.TypeArguments(pos) // <> diamond
                                        },
                                        args = fields.map { it.asNameExpr().asArgument() },
                                    ),
                                ).also { add(it) }
                            },
                        ),
                    ).also { add(it) }
                },
            )
        }

        private fun classDeclare(t: TmpL.TypeDeclaration) {
            require(t.kind == TmpL.TypeDeclarationKind.Class) { "kind=${t.kind} but should be Class" }
            val name = names.typeDeclName(t.name)
            val classTypeParams = typeFormals(t.typeParameters)
            val body: List<J.ClassBodyDeclaration> = buildList {
                if (RENDER_META_COMMENTS) {
                    for (meta in t.metadata) {
                        add(metaComment(t.pos, meta))
                    }
                }
                t.members.forEach { m ->
                    when (m) {
                        is TmpL.GarbageStatement -> add(garbageComment(m))
                        is TmpL.Constructor -> {
                            if (m.visibility.visibility == TmpL.Visibility.Public) {
                                classBuilder(name, classTypeParams, m)?.also { add(it) }
                            }
                            val mods = J.ConstructorModifiers(m.pos, modAccess = access(m))
                            val overs = overloads(null, m.parameters, stmt(m.body).asBlock()) { args ->
                                J.AlternateConstructorInvocation(t.pos, args = args).asBlock()
                            }
                            for (over in overs) {
                                add(
                                    J.ConstructorDeclaration(
                                        pos = m.pos,
                                        mods = mods.deepCopy(),
                                        name = name.deepCopy(),
                                        parameters = over.parameters.parameters,
                                        body = over.body.preface(over.parameters.preamble),
                                    ),
                                )
                            }
                        }
                        is TmpL.Getter -> {
                            val result = resultType(names, m.returnType, m.pos)
                            val name = names.getterName(m.dotName, JavaType.toFrontend(m.returnType.ot))
                            val parameters = parameters(m.parameters)
                            val prop = t.members.firstNotNullOfOrNull {
                                (it as? TmpL.InstanceProperty)?.let { p ->
                                    if (p.dotName == m.dotName) {
                                        p
                                    } else {
                                        null
                                    }
                                }
                            }
                            add(
                                J.MethodDeclaration(
                                    pos = m.pos,
                                    javadoc = javadoc(autodocFor(m), names = parameters.tmpLIdToParamName)
                                        ?: prop?.run { javadoc(autodocFor(pos, metadata)) },
                                    mods = J.MethodModifiers(m.pos, modAccess = access(m), modAbstract = abstract(m)),
                                    result = result,
                                    name = name,
                                    parameters = parameters.parametersNoPreamble,
                                    body = m.body?.let { block(it) },
                                ),
                            )
                        }
                        is TmpL.Setter -> {
                            val result = resultType(names, m.returnType, m.pos)
                            val name = names.setterName(m.dotName)
                            val parameters = parameters(m.parameters)
                            add(
                                J.MethodDeclaration(
                                    pos = m.pos,
                                    javadoc = javadoc(autodocFor(m), parameters.tmpLIdToParamName),
                                    mods = J.MethodModifiers(m.pos, modAccess = access(m), modAbstract = abstract(m)),
                                    result = result,
                                    name = name,
                                    parameters = parameters.parametersNoPreamble,
                                    body = m.body?.let { block(it) },
                                ),
                            )
                        }
                        is TmpL.NormalMethod,
                        is TmpL.StaticMethod,
                        -> {
                            @Suppress("USELESS_IS_CHECK")
                            check(m is TmpL.DotAccessibleMethod)
                            val isStatic = m is TmpL.StaticMember
                            val formals = parameters(m.parameters)
                            val tentativeBody = block(m.body).preface(formals.preamble)
                            val tentativeMethodName = names.method(m.dotName).toIdentifier(m.dotName.pos)
                            val mods = J.MethodModifiers(
                                // methodifiers, not even once
                                m.pos.leftEdge,
                                modAccess = access(m),
                                modStatic = if (isStatic) {
                                    J.ModStatic.Static
                                } else {
                                    J.ModStatic.Dynamic
                                },
                            )
                            val tentativeResult = resultType(names, m.returnType, m.pos)

                            val boxedTypeAdjustments = (m as? TmpL.NormalMethod)?.let {
                                findJavaParametersThatNeedAdjustmentToBoxedType(
                                    names,
                                    formals.parameters,
                                    returnType = tentativeResult,
                                    it.overridden,
                                )
                            }

                            val typeParams = typeFormals(m.typeParameters)
                            var result = tentativeResult
                            var body = tentativeBody
                            if (boxedTypeAdjustments != null) {
                                val privateMethodName = names.privateHelperMethod(m.name)
                                add(
                                    J.MethodDeclaration(
                                        t.pos,
                                        typeParams = typeParams.deepCopy(),
                                        result = result,
                                        mods = J.MethodModifiers(
                                            mods.pos,
                                            J.ModAccess.Private,
                                        ),
                                        name = privateMethodName,
                                        parameters = formals.parameters,
                                        body = body,
                                    ),
                                )

                                val callToPrivateHelper = J.InstanceMethodInvocationExpr(
                                    pos = body.pos,
                                    expr = null,
                                    method = privateMethodName.deepCopy(),
                                    args = formals.parameters.mapIndexed { index, param ->
                                        val read = J.NameExpr(param.name.deepCopy())
                                        J.Argument(
                                            param.pos,
                                            when (param) {
                                                is J.FormalParameter -> {
                                                    val adjust = boxedTypeAdjustments.parametersToAdjust
                                                        .getOrNull(index)
                                                    if (adjust == true) {
                                                        unboxToPrimitive(read, (param.type as J.PrimitiveType).type)
                                                    } else {
                                                        read
                                                    }
                                                }
                                                is J.VariableArityParameter -> read
                                            },
                                        )
                                    },
                                )
                                body = J.BlockStatement(
                                    body.pos,
                                    listOf(
                                        if (result is J.VoidType) {
                                            J.ExpressionStatement(callToPrivateHelper)
                                        } else {
                                            J.ReturnStatement(callToPrivateHelper.pos, callToPrivateHelper)
                                        },
                                    ),
                                )

                                if (boxedTypeAdjustments.adjustReturn) {
                                    result = (result as J.PrimitiveType).type.asReferenceType()
                                        .toResultTypeAst(result.pos)
                                }
                            }

                            val overloads = overloads(
                                boxedTypeAdjustments, m.parameters, body,
                            ) { args ->
                                // Abusively pretend static calls, whether static or not.
                                J.StaticMethodInvocationExpr(
                                    t.pos,
                                    type = null, // And don't actually include a method call target.
                                    method = tentativeMethodName.deepCopy(),
                                    args = args,
                                ).exprOrReturnStatement(shouldReturn = result !is J.VoidType).asBlock()
                            }
                            for (over in overloads) {
                                val parameters = over.parameters
                                add(
                                    J.MethodDeclaration(
                                        pos = t.pos,
                                        javadoc = javadoc(autodocFor(m), parameters.tmpLIdToParamName),
                                        typeParams = typeParams,
                                        result = result,
                                        mods = mods,
                                        name = tentativeMethodName,
                                        parameters = parameters.parameters,
                                        body = over.body.preface(parameters.preamble),
                                    ),
                                )
                            }
                        }
                        is TmpL.InstanceProperty -> if (m.memberShape.abstractness == Abstractness.Concrete) {
                            add(
                                J.FieldDeclaration(
                                    pos = m.pos,
                                    javadoc = javadoc(autodocFor(m.pos, m.metadata)),
                                    mods = J.FieldModifiers(
                                        m.pos,
                                        modAccess = access(m),
                                        modFinal = final(m.assignOnce),
                                    ),
                                    type = JavaType.fromTmpL(m.type, names).toTypeAst(m.pos),
                                    variable = names.field(m.name),
                                    initializer = null,
                                ),
                            )
                        }
                        is TmpL.StaticProperty -> add(
                            J.FieldDeclaration(
                                pos = m.pos,
                                javadoc = javadoc(autodocFor(m.pos, m.metadata)),
                                mods = J.FieldModifiers(
                                    pos = m.pos,
                                    modAccess = access(m),
                                    modFinal = final(m.assignOnce),
                                    modStatic = J.ModStatic.Static,
                                ),
                                type = JavaType.fromTmpL(m.type, names).toTypeAst(m.pos),
                                variable = names.staticField(m.dotName),
                                initializer = expr(m.expression),
                            ),
                        )
                    }
                }
            }

            this.programs.add(
                J.TopLevelClassDeclaration(
                    t.pos,
                    packageStatement = modulePackageStatement(t.pos),
                    programMeta = programMeta(t.pos, isTestClass = processingTestCode),
                    classDef = J.ClassDeclaration(
                        pos = t.pos,
                        javadoc = javadoc(autodocFor(t)),
                        mods = J.ClassModifiers(
                            t.pos,
                            modAccess = access(t.name),
                            modFinal = J.ModSealedFinal.Final,
                        ),
                        params = classTypeParams,
                        name = name.deepCopy(),
                        classImplements = t.superTypes.map { nominalType -> classTypeRef(nominalType) },
                        body = body,
                    ),
                ),
            )
        }

        private fun interfaceDeclare(t: TmpL.TypeDeclaration) {
            require(t.kind == TmpL.TypeDeclarationKind.Interface) { "kind=${t.kind} but should be Interface" }
            val body: List<J.InterfaceBodyDeclaration> = buildList {
                if (RENDER_META_COMMENTS) {
                    for (meta in t.metadata) {
                        add(metaComment(t.pos, meta))
                    }
                }

                // Detects if a method is virtual and, if so, makes it abstract and drops the body.
                fun interfaceMethod(
                    pos: Position,
                    autodoc: FnAutodoc?,
                    body: TmpL.BlockStatement?,
                    result: J.ResultType,
                    name: J.Identifier,
                    params: ParamsPreamble,
                    typeParams: J.TypeParameters = J.TypeParameters(pos),
                    isStatic: Boolean = false,
                ): J.InterfaceMethodDeclaration {
                    val isVirtual = abstract(body) == J.ModAbstract.Abstract
                    check(!isStatic || (body != null && !isVirtual))
                    val mods = when {
                        isStatic -> J.ModInterfaceMethod.Static
                        isVirtual -> J.ModInterfaceMethod.Abstract
                        else -> J.ModInterfaceMethod.Default
                    }
                    val realBody = if (isVirtual) null else block(body).preface(params.preamble)
                    return J.InterfaceMethodDeclaration(
                        pos = pos,
                        javadoc = javadoc(autodoc, params.tmpLIdToParamName),
                        mods = mods,
                        typeParams = typeParams,
                        result = result,
                        name = name,
                        parameters = params.parameters,
                        body = realBody,
                    )
                }

                t.members.forEach { m ->
                    when (m) {
                        is TmpL.GarbageStatement -> add(garbageComment(m))
                        is TmpL.Constructor -> add(
                            garbageComment(m.pos, "$m", "constructor invalid in an interface"),
                        )
                        is TmpL.Getter -> add(
                            interfaceMethod(
                                pos = m.pos,
                                autodoc = autodocFor(m),
                                body = m.body,
                                result = resultType(names, m.returnType, m.pos),
                                name = names.getterName(m.dotName, JavaType.toFrontend(m.returnType.ot)),
                                params = parameters(m.parameters),
                            ),
                        )
                        is TmpL.Setter -> add(
                            interfaceMethod(
                                pos = m.pos,
                                autodoc = autodocFor(m),
                                body = m.body,
                                result = resultType(names, m.returnType, m.pos),
                                name = names.setterName(m.dotName),
                                params = parameters(m.parameters),
                            ),
                        )
                        is TmpL.NormalMethod -> add(
                            interfaceMethod(
                                pos = m.pos,
                                autodoc = autodocFor(m),
                                body = m.body,
                                result = resultType(names, m.returnType, m.pos),
                                name = names.method(m.dotName).toIdentifier(m.dotName.pos),
                                params = parameters(m.parameters),
                                typeParams = typeFormals(m.typeParameters),
                            ),
                        )
                        is TmpL.StaticMethod -> add(
                            interfaceMethod(
                                pos = m.pos,
                                autodoc = autodocFor(m),
                                body = m.body,
                                name = names.method(m.dotName).toIdentifier(m.dotName.pos),
                                result = resultType(names, m.returnType, m.pos),
                                params = parameters(m.parameters),
                                typeParams = typeFormals(m.typeParameters),
                                isStatic = true,
                            ),
                        )

                        is TmpL.InstanceProperty -> Unit // getters and setters translated
                        is TmpL.StaticProperty -> add(
                            // At least since 8 they’re automatically public, static, final;
                            // and we don't need to worry about whether the initializer is a
                            // Java constant expression.
                            J.InterfaceFieldDeclaration(
                                pos = m.pos,
                                javadoc = javadoc(autodocFor(m.pos, m.metadata)),
                                type = JavaType.fromTmpL(m.type, names).toTypeAst(m.type.pos),
                                variables = listOf(
                                    J.VariableDeclarator(
                                        m.pos,
                                        names.staticField(m.dotName),
                                        initializer = expr(m.expression),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }

            programs.add(
                J.TopLevelClassDeclaration(
                    pos = t.pos,
                    packageStatement = modulePackageStatement(t.pos),
                    programMeta = programMeta(t.pos, isTestClass = processingTestCode),
                    classDef = J.InterfaceDeclaration(
                        pos = t.pos,
                        javadoc = javadoc(autodocFor(t)),
                        mods = J.InterfaceModifiers(t.pos, modAccess = access(t.name)),
                        name = names.typeDeclName(t.name),
                        params = typeFormals(t.typeParameters),
                        classExtends = t.superTypes.map { nominalType -> classTypeRef(nominalType) },
                        body = body,
                    ),
                ),
            )
        }

        /** Create an object containing the method parameters and necessary preamble statements. */
        private fun parameters(px: TmpL.Parameters): ParamsPreamble {
            val rest = px.restParameter
            val params = mutableListOf<J.MethodParameter>()
            val preamble = mutableListOf<J.BlockLevelStatement>()
            val thisName = px.thisName?.name
            val tmpLIdToParamName = mutableMapOf<TmpL.Id, OutName>()

            for (p in px.parameters) {
                val pname = p.name.name
                if (pname != thisName) {
                    var paramType = JavaType.fromTmpL(p.type, names)
                    if (p.optional) {
                        paramType = paramType.makeNullable()
                    }
                    val type = paramType.toTypeAst(p.type.pos)
                    val name = names.formal(p.name)
                    tmpLIdToParamName[p.name] = names.formal(p.name).outName
                    params.add(J.FormalParameter(p.pos, type = type, name = name))
                }
            }
            if (rest != null) {
                // rest.type should be the element type
                val paramType = JavaType.fromTmpL(rest.type, names)
                val paramName = names.restFormal(rest.name)
                val localName = names.formal(rest.name)
                val localType = javaUtilList.toClassType(
                    rest.pos,
                    J.TypeArguments(rest.pos, listOf(paramType.asReferenceType().toTypeArgAst(rest.pos))),
                )
                tmpLIdToParamName[rest.name] = paramName.outName
                params.add(
                    J.VariableArityParameter(
                        rest.pos,
                        type = paramType.toTypeAst(rest.pos),
                        name = paramName,
                    ),
                )
                preamble.add(
                    J.LocalVariableDeclaration(
                        rest.pos,
                        type = localType,
                        name = localName,
                        expr = javaUtilArraysAsList.staticMethod(
                            listOf(paramName.asNameExpr().asArgument()),
                            pos = rest.pos,
                        ),
                    ),
                )
            }
            return ParamsPreamble(params, preamble, tmpLIdToParamName.toMap())
        }

        private fun withAdjustments(params: ParamsPreamble, adj: BoxedTypeAdjustments?): ParamsPreamble {
            if (adj == null) { return params }
            val parametersToAdjust = adj.parametersToAdjust
            return params.copy(
                parameters = params.parameters.mapIndexed { index, formal ->
                    if (
                        formal is J.FormalParameter &&
                        parametersToAdjust.getOrNull(index) == true
                    ) {
                        val adjustedType = (formal.type as J.PrimitiveType)
                            .type.asReferenceType().toTypeAst(formal.type.pos)
                        J.FormalParameter(
                            formal.pos,
                            formal.mods.deepCopy(),
                            adjustedType,
                            formal.name.deepCopy(),
                        )
                    } else {
                        formal.deepCopy()
                    }
                },
                preamble = emptyList(),
            )
        }

        private fun typeFormals(px: TmpL.ATypeParameters) = typeFormals(px.ot)

        private fun typeFormals(px: TmpL.TypeParameters): J.TypeParameters =
            J.TypeParameters(
                px.pos,
                px.typeParameters.map {
                    JavaTypeParam.fromTmpL(it, names).toTypeParamAst(it.pos)
                },
            )

        private fun stubStmt(t: TmpL.Tree) = J.CommentLine(t.pos, t.toLispy().replace('\n', ' '))
        private fun stubExpr(t: TmpL.Tree) = J.StringLiteral(t.pos, t.toLispy())

        /**
         * The flow of `block()` is mostly "convert a [TmpL.Statement] into a [J.BlockLevelStatement]".
         * However, we need to lift some local variables and local functions into local classes; these are the
         * synthesized "scopes" mentioned below.
         *
         * For instance, Java lambda can only capture "effectively final" local variables, so we get around this
         * by creating a final instance of a custom class that boxes the mutable variable.
         */
        fun block(t: TmpL.BlockStatement?): J.BlockStatement {
            if (t == null) {
                return J.BlockStatement(unknownPos, listOf())
            }
            // The current scope statements, if present.
            val allScopes = mutableListOf<ScopeStmts>()
            var inScope = false
            // The translated statements for the current block
            val stmts = mutableListOf<J.BlockLevelStatement>()

            // Track the remaining recursive functions.
            val recursiveFuncs = (
                t.statements.mapNotNull {
                    (it as? TmpL.LocalFunctionDeclaration)?.name ?. let { name ->
                        if (names.lookupLocalNameObj(name)?.isRecursiveFunc == true) {
                            name.name
                        } else {
                            null
                        }
                    }
                }
                ).toMutableSet()
            // Track functions that have been called locally to determine if a function needs to be
            // forward declared.
            val calledFuncs = mutableSetOf<ResolvedName>()

            /**
             * Constructs a new [ScopeStmts] object, and add its local class declarations to the list of statements.
             * The ScopeStmts object lets us declare a class and add class body declarations to it after the
             * fact.
             */
            fun newScope(pos: Position, insertAtFront: Boolean): ScopeStmts {
                val newScope = ScopeStmts(names.newScopeDecl(), pos, calledFuncs.toSet())
                allScopes.add(if (insertAtFront) 0 else allScopes.size, newScope)
                stmts.addAll(if (insertAtFront) 0 else stmts.size, newScope.statements)
                return newScope
            }

            fun chooseScope(pos: Position): ScopeStmts = if (inScope) {
                allScopes.last()
            } else {
                inScope = true
                newScope(pos, insertAtFront = false)
            }

            // See what params need captured as mutable if we're a function block.
            (t.parent as? TmpL.FunctionLike)?.let { fn ->
                // Declare temporaries that should never be referenced except for lift capture purposes.
                val newNames = mutableMapOf<OutName, OutName>()
                for (param in fn.parameters.parameters) {
                    makeTemporaryIfMutablyCapturedParam(param, newNames, stmts)
                }
                // Then lift any params.
                for (param in fn.parameters.parameters) {
                    val varId = param.name
                    val pos = param.pos
                    val oldName = names.lookupLocalNameObj(varId)
                    if (!param.assignOnce && oldName?.isMutablyCaptured == true) {
                        val scope = chooseScope(pos)
                        val localName = names.liftLocal(varId, scope.scopeName, NameLift.CapturedMutableVar)
                        scope.addDecl(
                            J.FieldDeclaration(
                                pos,
                                type = JavaType.fromTmpL(param.type, names)
                                    .toTypeAst(pos),
                                variable = localName.outName.toIdentifier(varId.pos),
                                initializer = newNames[oldName.outName]!!.toNameExpr(varId.pos),
                            ),
                        )
                    }
                }
            }

            for (stmt in t.statements) {
                when (stmt) {
                    is TmpL.LocalFunctionDeclaration -> {
                        val funcId = stmt.name
                        val funcName = funcId.name
                        val func = LocalFunctionDecl(stmt)
                        if (recursiveFuncs.remove(funcName)) {
                            if (funcName in calledFuncs) {
                                // Find a scope for a forward declaration or put a new one in the very beginning.
                                val fwdScope: ScopeStmts = allScopes.lastOrNull {
                                    funcName !in it.callsBefore
                                } ?: newScope(stmt.pos, insertAtFront = true)
                                names.liftLocal(funcId, fwdScope.scopeName, NameLift.FwdDeclFunction)

                                stmts.add(func.toScopeFieldDef(fwdScope.scopeName))
                                fwdScope.addDecl(func.toScopeFieldDecl())
                            } else {
                                // Standard recursive declaration
                                val scope = chooseScope(stmt.pos)
                                names.liftLocal(funcId, scope.scopeName, NameLift.RecursiveFunction)
                                scope.addDecl(func.toScopeMethodDecl())
                            }
                        } else {
                            // Normal lambda declared function
                            inScope = false
                            stmts.add(func.toLocalVarDecl())
                        }
                    }
                    is TmpL.LocalDeclaration -> {
                        val varId = stmt.name
                        if (names.lookupLocalNameObj(varId)?.isMutablyCaptured == true) {
                            // Mutable captured local
                            val scope = chooseScope(stmt.pos)
                            val localName = names.liftLocal(varId, scope.scopeName, NameLift.CapturedMutableVar)
                            scope.addDecl(
                                J.FieldDeclaration(
                                    stmt.pos,
                                    type = JavaType.fromTmpL(stmt.type, names).toTypeAst(stmt.pos),
                                    variable = localName.outName.toIdentifier(varId.pos),
                                    initializer = stmt.init?.let(::expr),
                                ),
                            )
                        } else {
                            // Regular local
                            inScope = false
                            stmts.add(stmt(stmt))
                        }
                    }
                    else -> {
                        // Everything else.
                        inScope = false
                        stmts.add(stmt(stmt))
                    }
                }
                // Scan the current statement to see if it's calling a known recursive function.
                findReferencedFunctions(stmt, within = recursiveFuncs, copyTo = calledFuncs)
            }
            // For each ScopeStmts object to update the AST graph for its local class declaration.
            allScopes.forEach { it.finish() }
            return J.BlockStatement(t.pos, body = stmts)
        }

        private fun makeTemporaryIfMutablyCapturedParam(
            param: TmpL.Formal,
            newNames: MutableMap<OutName, OutName>,
            stmts: MutableList<J.BlockLevelStatement>,
        ) {
            val varId = param.name
            val pos = param.pos
            val localName = names.lookupLocalNameObj(varId)
            if (!param.assignOnce && localName?.isMutablyCaptured == true) {
                // This gets the non-lifted version of the name, which is what we want.
                val oldName = localName.outName
                val newName = OutName("${oldName.outputNameText}$CAPTURE_SUFFIX", oldName.sourceName)
                newNames[oldName] = newName
                stmts.add(
                    J.LocalVariableDeclaration(
                        pos,
                        type = JavaType.fromTmpL(param.type, names).toTypeAst(pos),
                        name = newName.toIdentifier(varId.pos),
                        expr = oldName.toIdentifier(varId.pos).asNameExpr(),
                    ),
                )
            }
        }

        private fun findReferencedFunctions(
            s: TmpL.Statement,
            within: Set<ResolvedName>,
            copyTo: MutableSet<ResolvedName>,
        ) {
            if (within.isNotEmpty()) { // common case is this is empty
                for (t in s.walk()) {
                    if (t is TmpL.AnyReference) {
                        val name = t.id.name
                        if (name in within) {
                            copyTo.add(name)
                        }
                    }
                }
            }
        }

        /** Various helpers to declare local functions. */
        private inner class LocalFunctionDecl(
            /** the source function to translate */
            private val tmplFunc: TmpL.LocalFunctionDeclaration,
        ) {
            /** the full function body */
            val body: J.BlockStatement get() = block(tmplFunc.body)
                .preface(paramsPreamble.preamble)

            /** when possible, provide an expression body for a lambda */
            val exprBody: J.Expression? get() {
                val stmts = tmplFunc.body.statements
                if (stmts.size == 1 && paramsPreamble.preamble.isEmpty()) {
                    return (stmts[0] as? TmpL.ReturnStatement)?.expression?.let(::expr)
                }
                return null
            }

            /**
             * The standard Java parameters and optional preamble for e.g. rest arguments.
             * Eagerly evaluated as this is used more than once.
             */
            val paramsPreamble: ParamsPreamble = parameters(tmplFunc.parameters)
            val typeParams: J.TypeParameters get() = typeFormals(tmplFunc.typeParameters)

            val pos: Position get() = tmplFunc.pos

            /** looks up the function name as a simple identifier */
            val funcName: J.Identifier get() =
                names.lookupLocalOrExternalNameObj(tmplFunc.name).outName.toIdentifier(pos)

            /** the simple parameters for a lambda form */
            val simpleParams: List<J.Identifier> get() = buildList {
                val px = tmplFunc.parameters
                px.parameters.forEach { p ->
                    if (p.name.name != px.thisName?.name) {
                        add(names.formal(p.name))
                    }
                }
                px.restParameter?.let { p ->
                    add(names.formal(p.name))
                }
            }

            /** the overall function type as a static Temper type */
            val funcType: Signature2 get() = tmplFunc.sig

            /** the overall function type as a Java SAM type */
            val javaType: J.Type get() = JavaType.fromSig(funcType, names).toTypeAst(pos)

            /** the result type of the function */
            val javaResultType: J.ResultType get() = resultType(names, tmplFunc.returnType, tmplFunc.returnType.pos)

            /** a lambda expression can be used in a local variable declaration, or also in a forward declared form. */
            fun toLambdaExpr() =
                J.LambdaExpr(pos, params = J.LambdaSimpleParams(pos, simpleParams), body = exprBody ?: body)

            /** express an unlifted local function declaration */
            fun toLocalVarDecl() = J.LocalVariableDeclaration(
                pos,
                name = funcName,
                type = javaType,
                expr = toLambdaExpr(),
            )

            /** express a local function that's lifted into a scope method */
            fun toScopeMethodDecl() = J.MethodDeclaration(
                pos,
                typeParams = typeParams,
                result = javaResultType,
                name = funcName,
                parameters = paramsPreamble.parameters,
                body = body,
            )

            /** express a local function declaration that's lifted into a scope field that's initially null */
            fun toScopeFieldDecl() = J.FieldDeclaration(
                pos,
                type = javaType,
                variable = funcName,
                initializer = null,
            )

            /** express a local function definition that's assigned to a previously declared scope field */
            fun toScopeFieldDef(scopeName: OutName) =
                Assign.assign(
                    left = J.NameExpr(pos, listOf(J.Identifier(pos, scopeName), funcName)),
                    right = toLambdaExpr(),
                    pos = pos,
                ).exprStatement()
        }

        fun stmt(t: TmpL.Statement): J.BlockLevelStatement =
            convertedCoroutinePromiseHandler(t) ?: when (t) {
                is TmpL.Assignment -> assignment(t)
                is TmpL.BlockStatement -> block(t)
                is TmpL.BoilerplateCodeFoldBoundary -> boundary(t)
                is TmpL.EmbeddedComment -> J.CommentLine(t.pos, t.commentText)
                is TmpL.GarbageStatement -> J.CommentLine(t.pos, t.diagnostic?.text ?: "Garbage")
                is TmpL.BreakStatement -> J.BreakStatement(t.pos, t.label?.let { names.label(it) })
                is TmpL.ContinueStatement -> J.ContinueStatement(t.pos, t.label?.let { names.label(it) })
                is TmpL.ExpressionStatement -> exprStatement(t)
                is TmpL.HandlerScope -> stubStmt(t)
                is TmpL.IfStatement -> ifStmt(t)
                is TmpL.LabeledStatement -> J.LabeledStatement(
                    t.pos,
                    names.label(t.label),
                    when (val stmt = stmt(t.statement)) {
                        is J.Statement -> stmt
                        // If it's a local variable declaration, we'd have to wrap it in a block, which would hide it.
                        is J.LocalVariableDeclaration -> error("Can't put a label directly on variable declaration")
                        // Wrap other statements in a block.
                        else -> J.BlockStatement(stmt.pos, listOf(stmt))
                    },
                )

                is TmpL.LocalDeclaration -> localVar(t)
                is TmpL.LocalFunctionDeclaration -> LocalFunctionDecl(t).toLocalVarDecl()
                is TmpL.ModuleInitFailed -> moduleInitFailed(t)
                is TmpL.YieldStatement -> TODO() // JavaSupportNetwork opts into TranslateToRegularFunction
                is TmpL.ReturnStatement -> J.ReturnStatement(t.pos, t.expression?.let(::expr))
                is TmpL.SetProperty -> setProperty(t)
                is TmpL.ThrowStatement -> throwStmt(t)
                is TmpL.TryStatement -> tryStmt(t)
                is TmpL.WhileStatement -> whileStmt(t)
                is TmpL.ComputedJumpStatement -> switchStmt(t)
            }

        private fun moduleInitFailed(t: TmpL.ModuleInitFailed): J.BlockLevelStatement =
            J.ThrowStatement(
                t.pos,
                J.InstanceCreationExpr(
                    t.pos,
                    type = javaLangRuntimeException.toClassType(t.pos),
                    args = listOf(J.Argument(t.pos, J.StringLiteral(t.pos, "ModuleInitFailed"))),
                ),
            )

        private fun localVar(t: TmpL.LocalDeclaration) =
            J.LocalVariableDeclaration(
                t.pos,
                type = JavaType.fromTmpL(t.type, names).toTypeAst(t.pos),
                name = names.lookupRegularLocalNameObj(t.name).outName.toIdentifier(t.name.pos),
                expr = t.init?.let(::expr),
            )

        private fun ifStmt(t: TmpL.IfStatement) = J.IfStatement(
            t.pos,
            test = expr(t.test),
            consequent = stmt(t.consequent).asBlock(t.pos),
            alternate = elseBlock(t.alternate),
        )

        private fun elseBlock(t: TmpL.Statement?): J.ElseBlockStatement? = when (t) {
            null -> null
            is TmpL.IfStatement -> ifStmt(t)
            else -> stmt(t).asBlockNullable()
        }

        private fun whileStmt(t: TmpL.WhileStatement) = J.WhileStatement(
            t.pos,
            test = expr(t.test),
            body = stmt(t.body).asBlock(),
        )

        private fun tryStmt(t: TmpL.TryStatement) = J.TryStatement(
            t.pos,
            bodyBlock = stmt(t.tried).asBlock(),
            catchBlocks = listOf(
                J.CatchBlock(
                    t.recover.pos,
                    types = listOf(temperBubble.toClassType(t.recover.pos)),
                    name = names.ignoredIdentifier(t.recover.pos),
                    body = stmt(t.recover).asBlock(),
                ),
            ),
        )

        private fun throwStmt(t: TmpL.ThrowStatement) = J.ThrowStatement(
            t.pos,
            temperBubbleMethod.staticMethod(args = listOf(), pos = t.pos),
        )

        private fun switchStmt(t: TmpL.ComputedJumpStatement) =
            // TmpL translator does not produce `when`s based on user code, so
            // they are not nested in the target of `break` statements.
            // If that changes, we would need to rewrite any unlabeled `break`s
            // that would get caught by this `switch`.
            J.SwitchStatement(
                t.pos,
                expr(t.caseExpr),
                run {
                    fun translateCaseBody(caseBody: TmpL.BlockStatement): List<J.BlockLevelStatement> {
                        val jBlock = block(caseBody)
                        // In Java, `break` is necessary to prevent fall-through to the next case.
                        val needsBreak = when (jBlock.body.lastOrNull()) {
                            is J.ReturnStatement,
                            is J.ThrowStatement,
                            is J.BreakStatement,
                            is J.ContinueStatement,
                            -> false
                            else -> true // Conservative
                        }
                        if (needsBreak) {
                            jBlock.body += J.BreakStatement(jBlock.pos.rightEdge, null)
                        }

                        // In Java, block statements can introduce variables, but it is an error
                        // to have a variable with a similar name in different cases because
                        // cases can fall-through.
                        //
                        //     switch (n) {
                        //       case 0:
                        //         int i = 0;
                        //         f(i);
                        //         break;
                        //       case 1:
                        //         int i = 1; // <=== ERROR: name conflict
                        //         f(i);
                        //         break;
                        //     }
                        //
                        // Wrapping in a block fixes this.
                        //
                        //     switch (n) {
                        //       case 0: {
                        //         int i = 0;
                        //         f(i);
                        //         break;
                        //       }
                        //       case 1: {
                        //         int i = 1; // <=== ERROR: name conflict
                        //         f(i);
                        //         break;
                        //       }
                        //     }
                        return listOf(jBlock)
                    }

                    val cases = buildList {
                        t.cases.mapTo(this) { case ->
                            J.CaseStatement(
                                case.pos,
                                J.SwitchCaseLabel(
                                    case.pos.leftEdge,
                                    case.values.map { J.IntegerLiteral(it.pos.leftEdge, it.index.toLong()) },
                                ),
                                translateCaseBody(case.body),
                            )
                        }
                        add(
                            J.CaseStatement(
                                t.elseCase.pos,
                                J.SwitchDefaultLabel(t.elseCase.pos.leftEdge),
                                translateCaseBody(t.elseCase.body),
                            ),
                        )
                    }

                    J.SwitchCaseBlock(cases.spanningPosition(t.pos), cases)
                },
            )

        private fun leftHandSide(left: TmpL.Id) =
            names.lookupLocalOrExternalNameObj(left).asLhs(left.pos, names)

        private fun assignment(t: TmpL.Assignment): J.BlockLevelStatement = when (val rhs = t.right) {
            is TmpL.Expression ->
                Assign.assign(
                    leftHandSide(t.left),
                    expr(rhs),
                    pos = t.pos,
                ).exprStatement(t.pos)
            // TmpL
            //     assignedTo = hs(fail, handled)
            // becomes Java
            //     fail = (assignedTo = handled) == null;
            is TmpL.HandlerScope ->
                Assign.assign(
                    leftHandSide(rhs.failed),
                    Assign.assign(
                        leftHandSide(t.left),
                        handled(rhs.handled),
                    ).testNull(),
                ).exprStatement(t.pos)
        }

        private fun setProperty(t: TmpL.SetProperty): J.BlockLevelStatement {
            val left = t.left
            // Static properties are not assignable.
            val leftSubject = left.subject as TmpL.Expression
            return when (val prop = left.property) {
                is TmpL.ExternalPropertyId ->
                    J.InstanceMethodInvocationExpr(
                        t.pos,
                        expr(leftSubject),
                        method = names.setterName(prop.name),
                        args = listOf(expr(t.right).asArgument(t.pos)),
                    ).exprStatement()
                is TmpL.InternalPropertyId ->
                    Assign.assign(expr(leftSubject).field(names.field(prop)), expr(t.right)).exprStatement(t.pos)
            }
        }

        private fun handled(handled: TmpL.Handled): J.Expression = when (handled) {
            is TmpL.Expression -> expr(handled)
            is TmpL.SetAbstractProperty -> TODO()
        }

        fun expr(x: TmpL.Expression): J.Expression = when (x) {
            is TmpL.AwaitExpression -> awaitExpr(x)
            is TmpL.CallExpression -> callExpr(x)
            is TmpL.CastExpression -> castExpr(x.pos, x.checkedType, x.expr)
            is TmpL.UncheckedNotNullExpression -> notNullExpr(x.expression)
            is TmpL.GarbageExpression -> stubExpr(x)
            is TmpL.FunInterfaceExpression -> TODO("${x.callable}")
            is TmpL.GetAbstractProperty -> getProperty(x)
            is TmpL.GetBackedProperty -> getProperty(x)
            is TmpL.InstanceOfExpression -> instanceOf(x)
            is TmpL.BubbleSentinel -> J.NullLiteral(x.pos)
            is TmpL.InfixOperation -> infixOp(x)
            is TmpL.PrefixOperation -> prefixOp(x)
            is TmpL.Reference -> reference(x)
            is TmpL.RestParameterCountExpression -> TODO()
            is TmpL.RestParameterExpression -> TODO()
            is TmpL.This -> J.ThisExpr(x.pos)
            is TmpL.ValueReference -> value(x.pos, x.value)
        }

        /** Some special handling for expression statements. */
        private fun exprStatement(s: TmpL.ExpressionStatement): J.BlockLevelStatement =
            when (val x = s.expression) {
                is TmpL.BubbleSentinel -> garbageComment(s.pos, "$s", "Literal expression statement")
                is TmpL.ValueReference -> when (x.value.typeTag) {
                    TVoid, TBoolean, TFloat64, TInt, TString, TNull ->
                        garbageComment(s.pos, "$x", "Literal expression statement")
                    else -> expr(x).asExprStmtExpr().exprStatement(s.pos)
                }
                is TmpL.GarbageExpression -> garbageComment(x)
                else -> expr(x).asExprStmtExpr().exprStatement(s.pos)
            }

        private fun getProperty(gp: TmpL.GetAbstractProperty): J.Expression =
            J.InstanceMethodInvocationExpr(
                gp.pos,
                expr = expr(gp.subject),
                method = names.getterName(gp.property, gp.type),
                args = listOf(),
            )

        private fun getProperty(gp: TmpL.GetBackedProperty): J.Expression =
            when (val subject = gp.subject) {
                is TmpL.Expression -> J.FieldAccessExpr(
                    gp.pos,
                    expr = expr(subject),
                    field = names.field(gp.property),
                )
                is TmpL.TypeName ->
                    names.classTypeName(subject.sourceDefinition)
                        .qualify(names.staticField(gp.property))
                        .toNameExpr(gp.pos)
            }

        private fun instanceOf(io: TmpL.InstanceOfExpression): J.Expression {
            return J.InstanceofExpr(
                io.pos,
                left = expr(io.expr),
                right = JavaType.fromTmpL(io.checkedType, names)
                    .asReferenceType()
                    .toTypeAst(io.checkedType.pos),
            )
        }

        private fun castExpr(pos: Position, rt: TmpL.AType, x: TmpL.Expression): J.StaticMethodInvocationExpr {
            val type = rt.ot
            val typeWithoutNull = type.withoutNull

            return when {
                // We can't cast to `or` types in tmpl, so the target is non-nullable.
                typeWithoutNull != type && typeWithoutNull !is TmpL.NeverType -> temperCastToNonNull
                else -> temperCast
            }.staticMethod(
                listOf(
                    JavaType.fromTmpL(type, names).toClassLiteral(pos).asArgument(),
                    expr(x).asArgument(),
                ),
                pos = pos,
            )
        }

        private fun notNullExpr(x: TmpL.Expression): J.Expression {
            // TODO: Should this be calling an inlinable method like the below if
            // nonNullType corresponds to a Java reference type.
            //     static <@Nonnull T> @Nonnull T notNull(@Nullable T x) {
            //       return (@Nonnull T) x;
            //     }
            // Or use Object.requireNotNull?
            return expr(x)
        }

        private fun awaitExpr(e: TmpL.AwaitExpression): J.Expression {
            // Call java.concurrent.Future.get()
            return J.InstanceMethodInvocationExpr(
                e.pos,
                expr(e.promise),
                null,
                J.Identifier(e.pos.rightEdge, "get"),
                emptyList(),
            )
        }

        private fun callExpr(call: TmpL.CallExpression): J.Expression = when (val fn = call.fn) {
            is TmpL.MethodReference -> callMethod(call, fn)

            is TmpL.InlineSupportCodeWrapper ->
                when (val sc = fn.supportCode) {
                    is JavaInlineSupportCode ->
                        sc.inlineToTree(
                            call.pos,
                            call.parameters.mapGeneric { TypedArg(actualExpr(it), it.typeOrInvalid) },
                            call.type,
                            this,
                        ) as J.Expression

                    else -> garbageExpr(
                        call.pos,
                        "$call",
                        "inline should be JavaInlineSupportCode got $sc",
                    )
                }

            is TmpL.ConstructorReference -> J.InstanceCreationExpr(
                call.pos,
                type = J.ClassType(fn.pos, type = names.classTypeName(fn.typeName).toQualIdent(fn.pos)),
                typeArgs = if (fn.typeShape.typeParameters.isNotEmpty()) J.TypeArguments(fn.pos) else null,
                args = callActuals(call.parameters, toSigBestEffort(fn.method?.descriptor)),
            )

            is TmpL.FunInterfaceCallable -> callFunctionValue(call.pos, call.parameters, fn)
            is TmpL.FnReference -> callReference(call.pos, call.parameters, fn)
            is TmpL.GarbageCallable -> garbageExpr(call.pos, "$call", fn.diagnostic?.text)
        }

        private fun actualExpr(p: TmpL.Actual): J.Expression =
            when (p) {
                is TmpL.Expression -> expr(p)
                is TmpL.RestSpread -> names.lookupRegularLocalNameObj(p.parameterName).asExpr(names, p)
            }

        private fun callActuals(
            actuals: List<TmpL.Actual>,
            calleeType: Signature2?,
            treatAsStatic: Boolean = false,
        ): List<J.Argument> {
            val calleeFormals = when {
                treatAsStatic -> calleeType?.allValueFormals
                else -> calleeType?.valueFormalsExceptThis
            }
            return actuals.mapGenericIndexed { idx, actual ->
                var expr = actualExpr(actual)
                if (calleeFormals != null && validInstanceMethodReferenceSubject(expr)) {
                    val actualSig = (actual as? TmpL.Expression)?.type?.let {
                        withType(
                            it,
                            fn = { _, sig, _ -> sig },
                            fallback = { null },
                        )
                    }
                    if (actualSig != null) {
                        val formalSig = withType(
                            calleeFormals[idx].type.simplify(),
                            fn = { _, sig, _ -> sig },
                            fallback = { null },
                        )
                        if (formalSig != null) {
                            val actualSig = signature(actualSig)
                            if (signature(formalSig) != actualSig) {
                                expr = J.InstanceMethodReferenceExpr(
                                    expr.pos,
                                    expr,
                                    J.Identifier(expr.pos, actualSig.returnType.samMethodName),
                                )
                            }
                        }
                    }
                }
                expr.asArgument(actual.pos)
            }
        }

        private fun callMethod(call: TmpL.CallExpression, fn: TmpL.MethodReference): J.Expression {
            val pos = call.pos
            val actuals = call.parameters
            val subject = fn.subject
            val methodSignature = toSigBestEffort(fn.method?.descriptor)
            when (subject) {
                is TmpL.TypeName -> return J.StaticMethodInvocationExpr(
                    pos,
                    type = names.classTypeName(subject.sourceDefinition).toQualIdent(subject.pos),
                    // TODO: typeArgs?
                    method = names.method(fn.methodName).toIdentifier(fn.methodName.pos),
                    args = callActuals(actuals, methodSignature),
                )
                is TmpL.Expression -> {
                    if (subject is TmpL.Reference) {
                        val subjectId = subject.id
                        val declNode = names.findDeclNode(subjectId)
                        when (declNode) {
                            is TmpL.SupportCodeDeclaration -> {
                                val (type, method) = names.moduleField(subjectId)
                                return J.InstanceMethodInvocationExpr(
                                    pos,
                                    expr = type.qualify(method).toNameExpr(subject.pos),
                                    method = names.method(fn.methodName).toIdentifier(fn.methodName.pos),
                                    args = callActuals(actuals, methodSignature),
                                )
                            }
                            is TmpL.ModuleFunctionDeclaration -> {
                                val (type, method) = names.moduleFunction(subjectId)
                                return J.StaticMethodInvocationExpr(
                                    pos,
                                    type = type.toQualIdent(subject.pos),
                                    method = J.Identifier(fn.methodName.pos, method),
                                    args = callActuals(actuals, methodSignature),
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
            return J.InstanceMethodInvocationExpr(
                pos,
                expr(subject),
                method = names.method(fn.methodName).toIdentifier(fn.methodName.pos),
                args = callActuals(actuals, methodSignature),
            )
        }

        private fun callReference(
            pos: Position,
            actuals: List<TmpL.Actual>,
            fn: TmpL.FnReference,
        ): J.Expression =
            when (val declNode = names.findDeclNode(fn.id)) {
                is TmpL.SupportCodeDeclaration ->
                    names.supportName(declNode).staticMethod(
                        callActuals(actuals, fn.type, treatAsStatic = true),
                        pos,
                    )

                is TmpL.TypeDeclaration, is TmpL.Method, is TmpL.Test, is TmpL.TypeConnection -> garbageExpr(
                    pos,
                    "CallExpression",
                    "$fn is declared by $declNode but type indicates a function",
                )

                is TmpL.LocalFunctionDeclaration ->
                    callLiftedFunction(pos, actuals, fn)

                is TmpL.LocalDeclaration,
                is TmpL.Formal,
                is TmpL.RestFormal,
                is TmpL.ModuleLevelDeclaration,
                is TmpL.PooledValueDeclaration,
                -> callFunctionValue(pos, actuals, fn)

                else -> {
                    val (type, methodName) = names.moduleFunction(name = fn.id)
                    J.StaticMethodInvocationExpr(
                        pos,
                        type = type.toQualIdent(pos),
                        method = methodName.toIdentifier(fn.id.pos),
                        args = callActuals(
                            actuals = actuals,
                            calleeType = fn.type,
                        ),
                    )
                }
            }

        private fun reference(ref: TmpL.AnyReference): J.Expression {
            val refId = ref.id
            when (val decl = names.findDeclNode(refId)) {
                is TmpL.TypeDeclaration, is TmpL.Method ->
                    return garbageExpr(ref.pos, "$ref", "declared by $decl but type indicates a function")
                is TmpL.SupportCodeDeclaration ->
                    return names.supportName(decl).toStaticMethodRef(ref.pos)
                is TmpL.ModuleLevelDeclaration -> {
                    val (module, field) = names.moduleField(ref.id)
                    return module.qualify(field).toNameExpr(ref.pos)
                }
                is TmpL.PooledValueDeclaration,
                is TmpL.LocalDeclaration, is TmpL.LocalFunctionDeclaration, is TmpL.Formal, is TmpL.RestFormal,
                ->
                    return names.lookupLocalOrExternalNameObj(refId).asExpr(names, ref)
                is TmpL.FunctionDeclaration -> {
                    val (module, func) = names.moduleFunction(ref.id)
                    return module.qualify(func).toStaticMethodRef(ref.pos)
                }
                is TmpL.Import -> when (decl.sig) {
                    is TmpL.ImportedValue -> {
                        val (module, field) = names.moduleField(ref.id)
                        return module.qualify(field).toNameExpr(ref.pos)
                    }
                    is TmpL.ImportedFunction -> {
                        val (module, func) = names.moduleFunction(ref.id)
                        return module.qualify(func).toStaticMethodRef(ref.pos)
                    }
                    is TmpL.ImportedType -> {
                        val (module, type) = names.moduleFunction(ref.id)
                        return module.qualify(type).toNameExpr(ref.pos)
                    }
                    else -> {}
                }
                else -> {}
            }
            val (type, method) = names.moduleFunctionIds(refId)
                ?: return names.lookupLocalOrExternalNameObj(refId).asExpr(names, ref)
            return J.StaticMethodReferenceExpr(ref.pos, type = type, method = method)
        }

        private fun callFunctionValue(
            pos: Position,
            parameters: List<TmpL.Actual>,
            fn: TmpL.FnReference,
        ): J.Expression = callFunctionValue(pos, parameters, fn.type, reference(fn))

        private fun callFunctionValue(
            pos: Position,
            parameters: List<TmpL.Actual>,
            fn: TmpL.FunInterfaceCallable,
        ): J.Expression = callFunctionValue(pos, parameters, fn.type, expr(fn.expr))

        private fun callFunctionValue(
            pos: Position,
            parameters: List<TmpL.Actual>,
            sig: Signature2,
            callable: J.Expression,
        ): J.Expression {
            val samType = names.samType(sig)
            val methodName = samType.method
            return callable.method(
                methodName,
                callActuals(parameters, sig),
                pos = pos,
            )
        }

        /**
         * Look for special functions created during coroutine conversion of `await`.
         *
         * This returns non-null for these constructs:
         *
         * - `awakeUpon(promise, generator)`
         * - `name = getPromiseResultSync(fail#1, promise)`
         * - `getPromiseResultSync(fail#1, promise)`
         */
        private fun convertedCoroutinePromiseHandler(
            s: TmpL.Statement,
        ): J.BlockLevelStatement? {
            val left: TmpL.Id?
            val call: TmpL.CallExpression
            when (s) {
                is TmpL.Assignment -> {
                    left = s.left
                    call = s.right as? TmpL.CallExpression ?: return null
                }
                is TmpL.ExpressionStatement -> {
                    left = null
                    call = s.expression as? TmpL.CallExpression ?: return null
                }
                else -> return null
            }

            // If this is a call to one of the helper functions, do special handling for it.
            val calleeId = (call.fn as? TmpL.FnReference)?.id
                ?: return null
            val declNode = names.findDeclNode(calleeId) as?
                TmpL.SupportCodeDeclaration
            val coroConversionHelper = (declNode?.init as? TmpL.SimpleSupportCodeWrapper)
                ?.supportCode as? JavaSeparateStatic
                ?: return null
            val parameters = call.parameters
            val pos = s.pos

            return when (coroConversionHelper.qualifiedName) {
                coroAwakeUpon -> {
                    check(left == null)
                    // Converted coroutines install these.
                    //     awakeUpon(promise, generator)
                    // ->
                    //     promise.handle((_, _) -> generator.get())
                    val promise = expr(parameters[0] as TmpL.Expression)
                    val generator = expr(parameters[1] as TmpL.Expression)
                    val leftPos = pos.leftEdge
                    J.ExpressionStatement(
                        J.InstanceMethodInvocationExpr(
                            pos,
                            expr = promise,
                            // CompletableFuture.handle(BiConsumer)
                            method = J.Identifier(leftPos, "handle"),
                            args = listOf(
                                J.Argument(
                                    leftPos,
                                    J.LambdaExpr(
                                        leftPos,
                                        J.LambdaSimpleParams(
                                            leftPos,
                                            // unnamed parameters are a preview feature
                                            // and not in Java 8
                                            listOf(
                                                names.ignoredIdentifier(leftPos), // Resolution
                                                names.ignoredIdentifier(leftPos), // Throwable
                                            ),
                                        ),
                                        J.BlockStatement(
                                            pos,
                                            listOf(
                                                J.ExpressionStatement(
                                                    J.InstanceMethodInvocationExpr(
                                                        pos,
                                                        expr = generator,
                                                        method = J.Identifier(leftPos, "get"),
                                                        args = listOf(),
                                                    ),
                                                ),
                                                J.ReturnStatement(pos.rightEdge, J.NullLiteral(pos.rightEdge)),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                // A promise was awaited but its result is not used
                coroPromiseResultAsync -> {
                    // Converted coroutines use these also.
                    //     left = getPromiseResultSync(fail#1, promise)
                    // ->
                    //     try {
                    //       left = promise.get();
                    //     } catch (InterruptedException) {
                    //       break; // skips to done state in `switch`
                    //     } catch (ExecutionException) {
                    //       fail_1 = true;
                    //     }
                    val failVar = (parameters[0] as? TmpL.Reference)?.id
                    val promise = expr(parameters[1] as TmpL.Expression)
                    var getCall: J.ExpressionStatementExpr = J.InstanceMethodInvocationExpr(
                        pos = pos,
                        expr = promise,
                        // CompletableFuture.get
                        method = J.Identifier(pos.rightEdge, "get"),
                        args = emptyList(),
                    )
                    getCall = if (left != null) {
                        J.AssignmentExpr(
                            pos = pos,
                            left = leftHandSide(left),
                            operator = J.Operator(left.pos.rightEdge, Assign),
                            right = getCall,
                        )
                    } else {
                        getCall
                    }
                    val catchPos = pos.rightEdge
                    val interruptedExceptionName = names.ignoredIdentifier(catchPos)
                    val executionExceptionName = names.ignoredIdentifier(catchPos)
                    return J.TryStatement(
                        pos = pos,
                        bodyBlock = J.BlockStatement(J.ExpressionStatement(getCall)),
                        catchBlocks = listOf(
                            J.CatchBlock(
                                pos = catchPos,
                                types = listOf(
                                    javaLangInterruptedException.toClassType(catchPos),
                                ),
                                name = interruptedExceptionName,
                                body = J.BlockStatement(
                                    // Since the state machine tentatively sets the case index
                                    // to -1 before switching the coroutine will enter a done
                                    // statement if we break from the case here.
                                    J.BreakStatement(catchPos),
                                ),
                            ),
                            J.CatchBlock(
                                pos = catchPos,
                                types = listOf(
                                    javaUtilConcurrentExecutionException.toClassType(catchPos),
                                ),
                                name = executionExceptionName,
                                body = J.BlockStatement(
                                    if (failVar != null) {
                                        J.ExpressionStatement(
                                            J.AssignmentExpr(
                                                catchPos,
                                                leftHandSide(failVar),
                                                J.Operator(catchPos, Assign),
                                                J.BooleanLiteral(catchPos, true),
                                            ),
                                        )
                                    } else {
                                        J.ThrowStatement(catchPos, J.NameExpr(executionExceptionName.deepCopy()))
                                    },
                                ),
                            ),
                        ),
                    )
                }
                else -> null
            }
        }

        private fun callLiftedFunction(
            pos: Position,
            parameters: List<TmpL.Actual>,
            fn: TmpL.FnReference,
        ): J.Expression = when (val localName = names.lookupLocalNameObj(fn.id)) {
            is RecursiveFuncName -> {
                val args = callActuals(parameters, fn.type)
                val method = localName.outName.toIdentifier(fn.pos)
                val receiver = if (names.isInScope(fn, localName.scopeName)) {
                    J.ThisExpr(fn.pos)
                } else {
                    localName.scopeName.toNameExpr(fn.pos)
                }
                J.InstanceMethodInvocationExpr(
                    pos,
                    expr = receiver,
                    method = method,
                    args = args,
                )
            }
            is FwdDeclFuncName -> {
                val fieldId = localName.outName.toIdentifier(fn.pos)
                val scope: J.Expression = localName.scopeName.toNameExpr(fn.pos)
                val field = J.FieldAccessExpr(fn.pos, scope, fieldId)
                val ft = fn.type
                // Look up the SAM interface type for this function type and call the appropriate method.
                field.method(
                    names.samType(ft).method,
                    callActuals(parameters, fn.type),
                    pos = pos,
                )
            }
            else -> callFunctionValue(pos, parameters, fn)
        }

        private fun infixOp(op: TmpL.InfixOperation) = J.InfixExpr(
            op.pos,
            left = expr(op.left),
            operator = J.Operator(
                op.op.pos,
                when (op.op.tmpLOperator) {
                    TmpLOperator.AmpAmp -> JavaOperator.ConditionalAnd
                    TmpLOperator.BarBar -> JavaOperator.ConditionalOr
                    TmpLOperator.EqEqInt -> JavaOperator.Equals
                    TmpLOperator.GeInt -> JavaOperator.GreaterEquals
                    TmpLOperator.GtInt -> JavaOperator.GreaterThan
                    TmpLOperator.LeInt -> JavaOperator.LessEquals
                    TmpLOperator.LtInt -> JavaOperator.LessThan
                    TmpLOperator.PlusInt -> JavaOperator.Addition
                },
            ),
            right = expr(op.right),
        )

        private fun prefixOp(op: TmpL.PrefixOperation) = when (op.op.tmpLOperator) {
            TmpLOperator.Bang -> simplifiedComplement(expr(op.operand), pos = op.pos)
        }

        private fun value(pos: Position, v: Value<*>): J.Expression {
            return when (v.typeTag) {
                TBoolean -> J.BooleanLiteral(pos, TBoolean.unpack(v))
                TFloat64 -> when (val unpacked = TFloat64.unpackParsed(v).value) {
                    Double.NEGATIVE_INFINITY -> javaLangDoubleNegativeInfinity.toNameExpr(pos)
                    Double.POSITIVE_INFINITY -> javaLangDoublePositiveInfinity.toNameExpr(pos)
                    Math.E -> javaMathE.toNameExpr(pos) // TODO Negative E and PI?
                    Math.PI -> javaMathPi.toNameExpr(pos)
                    else -> when (unpacked.isNaN()) {
                        true -> javaLangDoubleNaN.toNameExpr(pos)
                        false -> J.FloatingPointLiteral(pos, unpacked, J.Precision.Double)
                    }
                }
                TInt -> J.IntegerLiteral(pos, TInt.unpack(v).toLong())
                TInt64 -> J.IntegerLiteral(pos, TInt64.unpack(v))
                TString -> potentiallyLongStringToJavaExpression(pos, TString.unpack(v))
                TFunction -> TODO()
                TList -> TODO()
                TListBuilder -> TODO()
                TMap, TMapBuilder -> TODO()
                TNull -> J.NullLiteral(pos)
                TProblem -> TODO()
                TStageRange -> TODO()
                TSymbol -> TODO()
                TType -> {
                    val g = TType.unpack(v)
                    JavaType.fromFrontend(g.type2, names).toClassLiteral(pos)
                }
                TVoid -> J.NullLiteral(pos)
                TClosureRecord -> TODO()
                is TClass -> TODO()
            }
        }
    }
}

private fun access(name: TmpL.Id) = access(name.name is ExportedName)

private fun access(member: TmpL.Member) =
    // TODO: private means not inherited, unlike package private below
    access(member.memberShape.visibility >= Visibility.Public)

private fun access(pub: Boolean?) = when (pub) {
    true -> J.ModAccess.Public
    else -> J.ModAccess.PackagePrivate
}

private fun final(assignOnce: Boolean) = when (assignOnce) {
    true -> J.ModFinal.Final
    false -> J.ModFinal.Open
}

private fun abstract(member: TmpL.Method): J.ModAbstract = abstract(member.body)

private fun abstract(body: TmpL.BlockStatement?): J.ModAbstract {
    return if (body == null || body.isPureVirtual()) {
        J.ModAbstract.Abstract
    } else {
        J.ModAbstract.Concrete
    }
}

internal fun resultType(names: JavaNames, type: TmpL.AType?, pos: Position) =
    resultType(names, type?.ot, pos)

internal fun resultType(names: JavaNames, type: TmpL.Type?, pos: Position): J.ResultType = when (type) {
    null -> Invalid
    else -> JavaType.fromTmpL(type, names)
}.toResultTypeAst(type?.pos ?: pos)

internal fun validInstanceMethodReferenceSubject(e: J.Expression) = when (e) {
    is J.ConstructorReferenceExpr,
    is J.InstanceMethodReferenceExpr,
    is J.StaticMethodReferenceExpr,
    is J.LambdaExpr,
    -> false
    else -> true
}

private const val RENDER_META_COMMENTS = false

/** stackoverflow.com/questions/77417411/why-is-the-maximum-string-literal-length-in-java-65534 */
internal const val JAVAC_MODIFIED_UTF8_STRING_LITERAL_LIMIT = 65534

internal fun potentiallyLongStringToJavaExpression(pos: Position, s: String): J.Expression {
    // Per stackoverflow.com/questions/77417411/why-is-the-maximum-string-literal-length-in-java-65534
    // there are limits on the lengths of string literals.
    // Check that the Modified UTF-8 length of the string is within the limit.
    // If not, just output the string literal.
    // If so, split into chunks smaller than that at codepoint boundaries and
    // turn it into a builder expression like the below:
    //     new StringBuilder(utf16Len).append("...").append("...").toString()
    // That works because Temper string literals are not required to have any
    // particular semantics re java.lang.String#intern.
    // TODO: pull these out into GlobalClass constants?
    if (!modifiedUtf8LenExceeds(s, JAVAC_MODIFIED_UTF8_STRING_LITERAL_LIMIT)) {
        return J.StringLiteral(pos, s)
    }

    val lPos = pos.leftEdge
    val utf16Size = s.length
    var e: J.Expression = J.InstanceCreationExpr( // new StringBuilder(70000)
        lPos,
        javaLangStringBuilder.toClassType(lPos),
        args = listOf(
            J.Argument(
                lPos,
                J.IntegerLiteral(lPos, utf16Size.toLong()),
            ),
        ),
    )
    var offset = 0
    var chunkStart = 0 // UTF-16 offset of a substring to pass to append
    var modUtf16ChunkLen = 0
    fun emitChunk(chunkEnd: Int) {
        if (chunkEnd == chunkStart) { return }
        val substring = s.substring(chunkStart, chunkEnd)
        e = J.InstanceMethodInvocationExpr(
            pos,
            e,
            method = J.Identifier(lPos, OutName("append", null)),
            args = listOf(
                J.Argument(pos, J.StringLiteral(pos, substring)),
            ),
        )
        chunkStart = chunkEnd
        modUtf16ChunkLen = 0
    }
    while (offset < utf16Size) {
        val cp = decodeUtf16(s, offset)
        val nMUtf8 = modifiedUtf8ByteCount(cp)
        if (modUtf16ChunkLen + nMUtf8 >= JAVAC_MODIFIED_UTF8_STRING_LITERAL_LIMIT) {
            emitChunk(chunkEnd = offset)
        }
        modUtf16ChunkLen += nMUtf8
        offset += charCount(cp)
    }
    emitChunk(chunkEnd = utf16Size)
    return J.InstanceMethodInvocationExpr(
        pos,
        e,
        method = J.Identifier(lPos, OutName("toString", null)),
        args = emptyList(),
    )
}
