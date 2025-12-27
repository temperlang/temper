package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.Dependencies
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.mapParameters
import lang.temper.common.MimeType
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.resolveFile
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import lang.temper.type.WellKnownTypes
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TString

class CppTranslator(
    cppNames: CppNames,
    private val cppLibraryName: String? = null,
    @Suppress("unused")
    private val dependenciesBuilder: Dependencies.Builder<CppBackend>? = null,
) {
    val cpp = CppBuilder(cppNames)
    val includes = mutableSetOf<String>()
    var currentModuleLocation: ModuleName? = null

    private fun translateImplicitsType(builtinKey: String): Cpp.Type = when (builtinKey) {
        "AnyValue" -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
        else -> cpp.name(TEMPER_CORE_NAMESPACE, builtinKey)
    }

    private fun translateTypeName(name: TmpL.TypeName): Cpp.Type = when (name) {
        is TmpL.ConnectedToTypeName -> TODO()
        is TmpL.TemperTypeName -> when (val def = name.typeDefinition) {
            WellKnownTypes.anyValueTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "AnyValue")
            WellKnownTypes.booleanTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Boolean")
            WellKnownTypes.float64TypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Float64")
            WellKnownTypes.functionTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Function")
            WellKnownTypes.intTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Int")
            WellKnownTypes.nullTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Null")
            WellKnownTypes.promiseTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Promise")
            WellKnownTypes.promiseBuilderTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "PromiseBuilder")
            WellKnownTypes.stringTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "String")
            WellKnownTypes.symbolTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Symbol")
            WellKnownTypes.typeTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Type")
            WellKnownTypes.voidTypeDefinition -> cpp.name(TEMPER_CORE_NAMESPACE, "Void")
            else -> {
                when (val loc = def.sourceLocation) {
                    ImplicitsCodeLocation -> when (val defName = def.name) {
                        is ExportedName -> translateImplicitsType(defName.baseName.builtinKey)
                        is SourceName -> translateImplicitsType(defName.baseName.builtinKey)
                        is Temporary -> TODO()
                        is BuiltinName -> TODO()
                    }
                    is ModuleName -> {
                        val rest = cpp.name(def.name)
                        if (loc == currentModuleLocation) {
                            rest
                        } else {
                            cpp.name(cpp.nameForModule(loc), rest)
                        }
                    }
                }
            }
        }
    }

    private val inTranslateType = mutableListOf<TmpL.Type>()
    private fun translateType(type: TmpL.AType) = translateType(type.ot)

    private fun translateType(type: TmpL.Type): Cpp.Type = cpp.pos(type) {
        if (inTranslateType.contains(type)) {
            TODO("recursive type: $type")
        }
        inTranslateType.add(type)
        val ret = when (type) {
            is TmpL.FunctionType -> {
                val ret = translateType(type.returnType)
                val params = type.valueFormals.formals.map { formal ->
                    translateType(formal.type)
                }
                cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Function"),
                    buildList {
                        add(ret)
                        addAll(params)
                    },
                )
            }
            is TmpL.TypeIntersection -> TODO()
            is TmpL.TypeUnion -> {
                var isBubble = false
                var isNull = false
                val other = mutableListOf<TmpL.Type>()

                fun addType(type: TmpL.Type) {
                    when (type) {
                        is TmpL.FunctionType -> other.add(type)
                        is TmpL.TypeIntersection -> other.add(type)
                        is TmpL.TypeUnion -> {
                            type.types.forEach(::addType)
                        }
                        is TmpL.NominalType -> when (type.typeName.sourceDefinition) {
                            WellKnownTypes.nullTypeDefinition -> {
                                isNull = true
                            }
                            else -> other.add(type)
                        }
                        is TmpL.BubbleType -> {
                            isBubble = true
                        }
                        else -> other.add(type)
                    }
                }

                type.types.forEach(::addType)

                val base = if (other.size == 1) {
                    val first = translateType(other[0])
                    if (isNull) {
                        cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Nullable"), first)
                    } else {
                        first
                    }
                } else if (isNull) {
                    cpp.name(TEMPER_CORE_NAMESPACE, "Null")
                } else {
                    return@pos cpp.name(TEMPER_CORE_NAMESPACE, "Void")
                }

                if (isBubble) {
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Bubble"), base)
                } else {
                    base
                }
            }
            is TmpL.GarbageType -> TODO()
            is TmpL.NominalType -> {
                cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "Object"),
                    if (type.params.isEmpty()) {
                        translateTypeName(type.typeName)
                    } else {
                        cpp.template(
                            translateTypeName(type.typeName),
                            type.params.map { param -> translateType(param) },
                        )
                    },
                )
            }
            is TmpL.BubbleType -> TODO()
            is TmpL.NeverType -> cpp.type("void")
            is TmpL.TopType -> cpp.type("void *")
        }
        inTranslateType.removeLast()
        return@pos ret
    }

    private fun todoCommentOf(value: TmpL.ExpressionOrCallable): Cpp.Expr = cpp.callExpr(
        cpp.name("exit"),
        cpp.literal(0),
    ).withComment("${value.javaClass}")

    private fun todoCommentOf(value: TmpL.Statement, stmts: Iterable<Cpp.Stmt>): List<Cpp.Stmt> = buildList {
        cpp.comment("${value.javaClass}")
        addAll(stmts)
    }

    private fun todoCommentOf(
        value: TmpL.Statement,
        vararg stmts: Cpp.Stmt,
    ): List<Cpp.Stmt> = todoCommentOf(value, stmts.toList())

    private fun translateCallable(fn: TmpL.Callable): Cpp.Expr = cpp.pos(fn) {
        when (fn) {
            is TmpL.InlineSupportCodeWrapper -> todoCommentOf(fn)
            is TmpL.FnReference -> cpp.name(fn.id)
            is TmpL.FunInterfaceCallable -> translateExpression(fn.expr)
            is TmpL.ConstructorReference -> cpp.scopedName(
                translateTypeName(fn.typeName),
                cpp.singleName(CppName("make")),
            )

            is TmpL.GarbageCallable -> todoCommentOf(fn)
            is TmpL.MethodReference ->
                when (val subject = fn.subject) {
                    is TmpL.Expression -> cpp.memberExpr(
                        translateExpression(subject),
                        cpp.singleName(CppName(fn.methodName.dotNameText)),
                    )

                    is TmpL.ConnectedToTypeName -> TODO()
                    is TmpL.TemperTypeName -> TODO()
                }
        }
    }

    private fun translateExpression(expr: TmpL.Expression): Cpp.Expr = cpp.pos(expr) {
        when (expr) {
            is TmpL.AwaitExpression -> TODO()
            is TmpL.BubbleSentinel -> TODO()
            is TmpL.CallExpression -> when (val fn = expr.fn) {
                is TmpL.GarbageCallable -> {
                    todoCommentOf(fn)
                }

                is TmpL.InlineSupportCodeWrapper -> {
                    when (val supportCode = fn.supportCode) {
                        is CppInlineSupportCode -> supportCode.inlineToTree(
                            expr.pos,
                            expr.mapParameters { actual, staticType, _ ->
                                TypedArg(
                                    translateExpression(actual as TmpL.Expression),
                                    staticType ?: WellKnownTypes.anyValueType2,
                                )
                            },
                            expr.type,
                            this,
                        ) as Cpp.Expr

                        else -> todoCommentOf(expr)
                    }
                }

                else -> {
                    cpp.callExpr(
                        translateCallable(fn),
                        expr.parameters.map { actual ->
                            translateExpression(actual as TmpL.Expression)
                        },
                    )
                }
            }
            is TmpL.CastExpression -> todoCommentOf(expr)
            is TmpL.FunInterfaceExpression -> translateCallable(expr.callable)
            is TmpL.GarbageExpression -> todoCommentOf(expr)
            is TmpL.GetAbstractProperty -> cpp.op(
                "->",
                translateExpression(expr.subject),
                when (val prop = expr.property) {
                    is TmpL.ExternalPropertyId -> cpp.name(prop.name.dotNameText)
                    is TmpL.InternalPropertyId -> cpp.name(prop.name.name)
                },
            )
            is TmpL.GetBackedProperty -> {
                when (val subject = expr.subject) {
                    is TmpL.Expression -> cpp.op(
                        "->",
                        translateExpression(subject),
                        when (val prop = expr.property) {
                            is TmpL.ExternalPropertyId -> cpp.name(prop.name.dotNameText)
                            is TmpL.InternalPropertyId -> cpp.name(prop.name.name)
                        },
                    )
                    is TmpL.ConnectedToTypeName -> TODO()
                    is TmpL.TemperTypeName -> TODO()
                }
            }
            is TmpL.InstanceOfExpression -> todoCommentOf(expr)
            is TmpL.InfixOperation -> todoCommentOf(expr)
            is TmpL.PrefixOperation -> todoCommentOf(expr)
            is TmpL.Reference -> cpp.name(expr.id)
            is TmpL.RestParameterCountExpression -> todoCommentOf(expr)
            is TmpL.RestParameterExpression -> todoCommentOf(expr)
            is TmpL.This -> cpp.name(expr.id)
            is TmpL.UncheckedNotNullExpression -> todoCommentOf(expr)
            is TmpL.ValueReference -> {
                val type = expr.type
                when (type.definition) {
                    WellKnownTypes.booleanType.definition -> cpp.literal(TBoolean.unpack(expr.value))
                    WellKnownTypes.intType.definition -> cpp.literal(TInt.unpack(expr.value))
                    WellKnownTypes.float64Type.definition -> cpp.literal(TFloat64.unpack(expr.value))
                    WellKnownTypes.stringType.definition -> cpp.literal(TString.unpack(expr.value))
                    WellKnownTypes.voidType.definition -> cpp.literal(cpp.raw("TEMPER_VOID"))
                    WellKnownTypes.typeType.definition -> cpp.literal(
                        cpp.raw("TEMPER_TYPE(${type.definition.name.displayName})"),
                    )
                    WellKnownTypes.symbolType.definition -> cpp.literal(expr.value.typeTag.toString())
                    else -> TODO("literal $type: ${type.definition}")
                }
            }
        }
    }

    private fun translateExpressionOrNull(expr: TmpL.Expression?): Cpp.Expr? = when (expr) {
        null -> null
        else -> translateExpression(expr)
    }

    private fun translateStatement(stmt: TmpL.Statement): Iterable<Cpp.Stmt> = cpp.pos(stmt) {
        when (stmt) {
            is TmpL.Assignment -> listOf(
                cpp.exprStmt(
                    cpp.op(
                        "=",
                        cpp.name(stmt.left),
                        when (val right = stmt.right) {
                            is TmpL.Expression -> translateExpression(right)
                            is TmpL.HandlerScope -> TODO()
                        },
                    ),
                ),
            )
            is TmpL.BoilerplateCodeFoldEnd -> todoCommentOf(stmt)
            is TmpL.BoilerplateCodeFoldStart -> todoCommentOf(stmt)
            is TmpL.BreakStatement -> todoCommentOf(stmt)
            is TmpL.ContinueStatement -> todoCommentOf(stmt)
            is TmpL.EmbeddedComment -> todoCommentOf(stmt)
            is TmpL.ExpressionStatement -> listOf(
                cpp.exprStmt(translateExpression(stmt.expression)),
            )

            is TmpL.GarbageStatement -> todoCommentOf(stmt)
            is TmpL.HandlerScope -> todoCommentOf(stmt)
            is TmpL.LocalDeclaration -> listOf(
                cpp.varDef(
                    translateType(stmt.type),
                    cpp.name(stmt.name),
                    translateExpressionOrNull(stmt.init),
                ),
            )

            is TmpL.LocalFunctionDeclaration -> todoCommentOf(stmt)
            is TmpL.ModuleInitFailed -> todoCommentOf(stmt)
            is TmpL.BlockStatement -> listOf(
                cpp.blockStmt(
                    stmt.statements.flatMap(::translateStatement).toList(),
                ),
            )

            is TmpL.ComputedJumpStatement -> todoCommentOf(stmt)
            is TmpL.IfStatement -> listOf(
                cpp.ifStmt(
                    translateExpression(stmt.test),
                    cpp.blockStmt(
                        translateStatement(stmt.consequent),
                    ),
                    stmt.alternate?.let {
                        cpp.blockStmt(translateStatement(it))
                    },
                ),
            )

            is TmpL.LabeledStatement -> todoCommentOf(stmt)
            is TmpL.TryStatement -> todoCommentOf(stmt)
            is TmpL.WhileStatement -> listOf(
                cpp.whileStmt(
                    translateExpression(stmt.test),
                    cpp.blockStmt(
                        translateStatement(stmt.body),
                    ),
                ),
            )
            is TmpL.ReturnStatement -> listOf(
                cpp.returnStmt(
                    translateExpressionOrNull(stmt.expression),
                ),
            )

            is TmpL.SetAbstractProperty -> todoCommentOf(stmt)
            is TmpL.SetBackedProperty -> todoCommentOf(stmt)
            is TmpL.ThrowStatement -> todoCommentOf(stmt)
            is TmpL.YieldStatement -> todoCommentOf(stmt)
        }
    }

    private fun translateBlock(block: TmpL.BlockStatement): Cpp.BlockStmt = cpp.pos(block) {
        cpp.blockStmt(
            block.statements.flatMap(::translateStatement),
        )
    }

    private fun translateBlockWithThis(
        thisType: Cpp.Type,
        thisName: Cpp.SingleName,
        block: TmpL.BlockStatement,
    ): Cpp.BlockStmt = cpp.pos(block) {
        cpp.blockStmt(
            buildList<Cpp.Stmt> {
                add(
                    cpp.varDef(cpp.ptr(thisType), thisName, cpp.thisExpr()),
                )
                block.statements.forEach { stmt ->
                    addAll(translateStatement(stmt))
                }
            },
        )
    }

    fun translateModule(mod: TmpL.Module): List<Backend.TranslatedFileSpecification> {
        includes.clear()
        currentModuleLocation = mod.codeLocation.codeLocation

        fun namespaced(body: Iterable<Cpp.Global>): Iterable<Cpp.Global> = cpp.pos(mod) {
            val innerNamespace = when (cppLibraryName) {
                null -> body
                else -> listOf(cpp.namespace(cpp.libraryName(cppLibraryName), body))
            }
            val finalNamespace = cpp.namespace(cpp.singleName(CppName("temper")), innerNamespace)
            return@pos listOf(finalNamespace)
        }

        val relPath = mod.codeLocation.codeLocation.relativePath()

        val path = when {
            relPath.isFile -> relPath
            relPath.segments.isEmpty() -> filePath(INIT_NAME)
            else -> relPath.dirName().resolveFile(relPath.last().fullName)
        }

        return cpp.pos(mod) {
            val headerTypeDecl = mutableListOf<Cpp.Global>()
            val headerTypeDefs = mutableListOf<Cpp.Global>()
            val headerFunctions = mutableListOf<Cpp.Global>()
            val headerDecl = mutableListOf<Cpp.Global>()
            val headerInit = mutableListOf<Cpp.Global>()

            fun header(): List<Cpp.Global> = buildList {
                addAll(headerTypeDecl)
                addAll(headerTypeDefs)
                addAll(headerFunctions)
                addAll(headerDecl)
                addAll(headerInit)
            }

            val impl = mutableListOf<Cpp.Global>()

            mod.topLevels.forEach { topLevel ->
                cpp.pos(topLevel) {
                    when (topLevel) {
                        is TmpL.EmbeddedComment -> {}
                        is TmpL.ModuleInitBlock -> {
                            val func = cpp.func(
                                cpp.tmp(),
                                cpp.name(TEMPER_CORE_NAMESPACE, "Void"),
                                listOf(),
                                listOf(),
                                translateBlock(topLevel.body),
                            )
                            headerInit.add(func.decl)
                            impl.add(func.def)
                        }
                        is TmpL.ModuleFunctionDeclaration -> {
                            val func = cpp.func(
                                cpp.name(topLevel.name),
                                translateType(topLevel.returnType),
                                topLevel.parameters.parameters.map { translateType(it.type) },
                                topLevel.parameters.parameters.map { cpp.name(it.name) },
                                translateBlock(topLevel.body),
                            )
                            headerFunctions.add(func.decl)
                            impl.add(func.def)
                        }
                        is TmpL.ModuleLevelDeclaration -> {
                            val isReal = when (val type = topLevel.type.ot) {
                                is TmpL.FunctionType -> true
                                is TmpL.TypeIntersection -> false
                                is TmpL.TypeUnion -> true
                                is TmpL.GarbageType -> false
                                is TmpL.NominalType -> when (type.typeName.sourceDefinition) {
                                    WellKnownTypes.voidType.definition -> false
                                    else -> true
                                }
                                is TmpL.BubbleType -> false
                                is TmpL.NeverType -> false
                                is TmpL.TopType -> false
                            }
                            if (isReal) {
                                val type = translateType(topLevel.type)
                                val name = cpp.name(topLevel.name)
                                headerDecl.add(cpp.varDecl(type, name))
                                impl.add(
                                    cpp.varDef(
                                        type,
                                        name,
                                        translateExpressionOrNull(topLevel.init),
                                    ),
                                )
                            }
                        }
                        is TmpL.TypeDeclaration -> {
                            val struct = cpp.struct(
                                cpp.name(topLevel.name),
                                buildList {
                                    for (member in topLevel.members) {
                                        cpp.pos(member) {
                                            when (member) {
                                                is TmpL.Property -> {
                                                    add(
                                                        cpp.structField(
                                                            translateType(member.type),
                                                            cpp.name(member.name),
                                                        ),
                                                    )
                                                }
                                                is TmpL.Getter -> {
                                                    when (val body = member.body) {
                                                        null -> {
                                                            add(cpp.comment("TODO: TmpL.Getter with null body"))
                                                        }
                                                        else -> {
                                                            val func = cpp.func(
                                                                cpp.scopedName(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.name),
                                                                ),
                                                                translateType(member.returnType),
                                                                member.parameters.parameters.drop(1).map { param ->
                                                                    cpp.pos(param) {
                                                                        val type = translateType(param.type)
                                                                        val name = cpp.name(param.name)
                                                                        type to name
                                                                    }
                                                                },
                                                                translateBlockWithThis(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.parameters.parameters.first().name),
                                                                    body,
                                                                ),
                                                            )
                                                            impl.add(func.def)
                                                            add(func.decl)
                                                        }
                                                    }
                                                }
                                                is TmpL.Setter -> {
                                                    when (val body = member.body) {
                                                        null -> {
                                                            add(cpp.comment("TODO: TmpL.Setter with null body"))
                                                        }
                                                        else -> {
                                                            val func = cpp.func(
                                                                cpp.scopedName(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.name),
                                                                ),
                                                                translateType(member.returnType),
                                                                member.parameters.parameters.drop(1).map { param ->
                                                                    cpp.pos(param) {
                                                                        val type = translateType(param.type)
                                                                        val name = cpp.name(param.name)
                                                                        type to name
                                                                    }
                                                                },
                                                                translateBlockWithThis(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.parameters.parameters.first().name),
                                                                    body,
                                                                ),
                                                            )
                                                            impl.add(func.def)
                                                            add(func.decl)
                                                        }
                                                    }
                                                }
                                                is TmpL.NormalMethod -> {
                                                    when (val body = member.body) {
                                                        null -> {
                                                            add(cpp.comment("TODO: TmpL.NormalMethod with null body"))
                                                        }
                                                        else -> {
                                                            val func = cpp.func(
                                                                cpp.scopedName(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.name),
                                                                ),
                                                                translateType(member.returnType),
                                                                member.parameters.parameters.drop(1).map { param ->
                                                                    cpp.pos(param) {
                                                                        val type = translateType(param.type)
                                                                        val name = cpp.name(param.name)
                                                                        type to name
                                                                    }
                                                                },
                                                                translateBlockWithThis(
                                                                    cpp.name(topLevel.name),
                                                                    cpp.name(member.parameters.parameters.first().name),
                                                                    body,
                                                                ),
                                                            )
                                                            impl.add(func.def)
                                                            add(func.decl)
                                                        }
                                                    }
                                                }
                                                is TmpL.StaticMethod -> {
                                                    add(cpp.comment("TODO: TmpL.StaticMethod"))
                                                }
                                                is TmpL.Constructor -> {
                                                    val func = cpp.func(
                                                        cpp.name(
                                                            cpp.name(topLevel.name),
                                                            cpp.singleName(CppName("make")),
                                                        ),
                                                        cpp.name(topLevel.name),
                                                        member.parameters.parameters.drop(1).map {
                                                            cpp.pos(it) {
                                                                translateType(it.type) to cpp.name(it.name)
                                                            }
                                                        },
                                                        cpp.pos(member.body) {
                                                            cpp.blockStmt(
                                                                buildList {
                                                                    member.body.statements.forEach { stmt ->
                                                                        if (stmt is TmpL.ReturnStatement) {
                                                                            return@forEach
                                                                        }
                                                                        addAll(translateStatement(stmt))
                                                                    }
                                                                    val fields = topLevel.members.mapNotNull { field ->
                                                                        if (field is TmpL.Property) {
                                                                            cpp.name(field.name)
                                                                        } else {
                                                                            null
                                                                        }
                                                                    }
                                                                    val obj = cpp.name(TEMPER_CORE_NAMESPACE, "object")
                                                                    add(cpp.returnStmt(cpp.callExpr(obj, fields)))
                                                                },
                                                            )
                                                        },
                                                    )
                                                    impl.add(func.def)
                                                    add(func.decl)
                                                }
                                                is TmpL.GarbageStatement -> {
                                                    add(cpp.comment("TODO: TmpL.GarbageStatement"))
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                            headerTypeDecl.add(struct.decl)
                            headerTypeDefs.add(struct.def)
                        }
                        else -> TODO()
                    }
                }
            }

            val modPath = mod.codeLocation.outputPath
            val hppName = path.withTemperAwareExtension(HPP_EXT)

            listOf(
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(HPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.includeGuard(
                        cpp.tmp("TEMPER_HEADER_GUARD"),
                        cpp.program(
                            buildList {
                                add(cpp.include("temper-core/core.hpp"))
                                addAll(namespaced(header()))
                            },
                        ),
                    ),
                ),
                Backend.TranslatedFileSpecification(
                    path = path.withTemperAwareExtension(CPP_EXT),
                    mimeType = MimeType.cppSource,
                    content = cpp.program(
                        buildList {
                            add(cpp.include("$modPath$hppName"))
                            addAll(namespaced(impl))
                        },
                    ),
                ),
            )
        }
    }
}
