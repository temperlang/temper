package lang.temper.be.cpp03

import lang.temper.be.Backend
import lang.temper.be.cpp.Cpp
import lang.temper.be.cpp.CppBuilder
import lang.temper.be.cpp.CppName
import lang.temper.be.cpp.CppNames
import lang.temper.be.cpp.cppKeywords
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.mapParameters
import lang.temper.common.subListToEnd
import lang.temper.log.resolveFile
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.type.TypeDefinition
import lang.temper.type.WellKnownTypes
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TString
import lang.temper.value.failSymbol

class CppTranslator(
    private val module: TmpL.Module,
    cppNames: CppNames,
) {
    internal val cpp = CppBuilder(cppNames)

//    private val headerGlobals = mutableListOf<Cpp.Global>()
    private val decls = mutableMapOf<ResolvedName, DeclInfo>()
    private val fails = mutableListOf<Cpp.Name>()
    private val implGlobals = mutableListOf<Cpp.Global>()
    var initCount = 0

    fun translate() = cpp.pos(module) {
        // Build and translate content.
//        addAccessFunction()
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        // Plan names and paths.
        val libraryName = module.libraryName?.text ?: "library"
        val libraryNamespace = libraryName.dashToSnake()
        val modulePath = module.codeLocation.codeLocation.relativePath()
        val segments = buildList {
            add(cpp.singleName(libraryNamespace))
            for (segment in modulePath.segments) {
                add(cpp.singleName(segment.fullName.dashToSnake()))
            }
        }
        val fullPath = modulePath.resolveFile(modulePath.lastOrNull()?.fullName ?: libraryName)
//        val headerPath = fullPath.withExtension(".hpp")!!
        val implPath = fullPath.withExtension(".cpp")!!
        // Build files.
        buildList {
            // Impl file.
            Backend.TranslatedFileSpecification(
                path = implPath,
                mimeType = CppBackend.mimeType,
                content = cpp.program(
                    buildList {
                        // TODO Instead include temper-core from the module's header.
                        add(cpp.include("temper-core/core.hpp"))
                        addAll(namespaced(segments) { implGlobals })
                    },
                ),
            ).also { add(it) }
        }
    }

//    /** Make a function that can ensure the module stays linked in. TODO But maybe best to avoid this? */
//    private fun addAccessFunction() {
//        val func = cpp.func(
//            retType = cpp.singleName(CppName("void", allowKey = true)),
//            name = cpp.singleName("access"),
//            argTypes = listOf(),
//            block = cpp.blockStmt(listOf()),
//        )
//        headerGlobals.add(func.decl)
//        implGlobals.add(func.def)
//    }

    private fun namespaced(segments: List<Cpp.SingleName>, build: () -> List<Cpp.Global>): List<Cpp.Global> = run {
        when {
            segments.isEmpty() -> build()
            else -> cpp.namespace(
                segments.first(),
                namespaced(segments.subListToEnd(1), build),
            ).let { listOf(it) }
        }
    }

    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        when (topLevel) {
            is TmpL.ModuleFunctionDeclaration -> processModuleFunctionDeclaration(topLevel)
            is TmpL.ModuleInitBlock -> processModuleInitBlock(topLevel)
            is TmpL.ModuleLevelDeclaration -> processModuleLevelDeclaration(topLevel)
//            is TmpL.Test -> processTest(topLevel)
//            is TmpL.TypeDeclaration -> processTypeDeclaration(topLevel)
            else -> {}
        }
    }

    private fun processModuleFunctionDeclaration(fn: TmpL.ModuleFunctionDeclaration) = cpp.pos(fn) {
        val func = cpp.func(
            cpp.name(fn.name),
            translateType(fn.returnType),
            fn.parameters.parameters.map { translateType(it.type) },
            fn.parameters.parameters.map { translateId(it.name) as Cpp.SingleName },
            translateBlockStatement(fn.body),
        )
        implGlobals.add(func.def)
    }

    private fun processModuleInitBlock(block: TmpL.ModuleInitBlock) = cpp.pos(block) {
        val number = initCount++
        val initStructName = cpp.singleName("_Init$number")
        cpp.namespace(
            listOf(
                cpp.structDef(
                    name = initStructName,
                    fields = listOf(
                        cpp.funcDef(
                            ret = null,
                            name = initStructName,
                            args = listOf(),
                            body = buildList {
                                translateStatements(block.body.statements)
                            }.let { cpp.blockStmt(it) },
                        ),
                    ),
                ),
                cpp.varDef(type = initStructName, name = cpp.singleName("_init$number")),
            ),
        ).also { implGlobals.add(it) }
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration): Unit = cpp.pos(decl) {
        // Skip console declaration for now. TODO Support logging consoles.
        decl.isConsole() && return
        decl.metadata.any { it.key.symbol == failSymbol } && return
        // TODO If not exported, put inside a namespace { ... } block?
        translateModuleOrLocalDeclaration(decl).also { implGlobals.addAll(it) }
    }

    private fun translateActual(actual: TmpL.Actual) = translateExpression(actual as TmpL.Expression)

    private fun translateAssignment(stmt: TmpL.Assignment): Cpp.Stmt = cpp.pos(stmt) {
        cpp.binaryExpr(
            translateId(stmt.left),
            cpp.binaryOp("="),
            when (val value = stmt.right) {
                is TmpL.Expression -> translateExpression(value)
                is TmpL.HandlerScope -> error("$stmt") // handled elsewhere
            },
        ).let { cpp.exprStmt(it) }
    }

    private fun translateBlockStatement(block: TmpL.BlockStatement): Cpp.BlockStmt = cpp.pos(block) {
        buildList {
            translateStatements(block.statements)
        }.let { cpp.blockStmt(it) }
    }

    private fun translateBreakStatement(stmt: TmpL.BreakStatement): Cpp.Stmt = cpp.pos(stmt) {
        when (val label = stmt.label) {
            // TODO Just invent labels for every loop? Could help avoid switch issues.
            null -> cpp.exprStmt(cpp.literal("TODO $label"))
            else -> cpp.gotoStmt(translateId(label.id) as Cpp.SingleName)
        }
    }

    private fun translateBubbleSentinel(expr: TmpL.BubbleSentinel): Cpp.Expr = cpp.pos(expr) {
        cpp.callExpr(cpp.name("temper", "core", "Unexpected"), currentError())
    }

    private fun translateCallExpression(expr: TmpL.CallExpression): Cpp.Expr = cpp.pos(expr) {
        when (val callable = expr.fn) {
            is TmpL.InlineSupportCodeWrapper -> {
                (callable.supportCode as CppInlineSupportCode).inlineToTree(
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
            }
            else -> cpp.callExpr(
                translateCallable(callable),
                expr.parameters.map { translateActual(it) },
            )
        }
    }

    private fun translateCallable(callable: TmpL.Callable): Cpp.Expr = cpp.pos(callable) {
        when (callable) {
            is TmpL.FnReference -> cpp.name(callable.id)
            is TmpL.MethodReference -> when (val subject = callable.subject) {
                is TmpL.Expression -> cpp.memberExpr(
                    translateExpression(subject),
                    translateDotName(callable.methodName),
                )
                else -> null
            }
            else -> null
        } ?: cpp.literal("TODO: $callable")
    }

    private fun translateDotName(name: TmpL.DotName): Cpp.SingleName = cpp.pos(name) {
        cpp.singleName(name.dotNameText)
    }

    private fun translateExpression(expr: TmpL.Expression): Cpp.Expr = cpp.pos(expr) {
        when (expr) {
            is TmpL.BubbleSentinel -> translateBubbleSentinel(expr)
            is TmpL.CallExpression -> translateCallExpression(expr)
            is TmpL.Reference -> translateReference(expr)
            is TmpL.ValueReference -> translateValueReference(expr)
            else -> cpp.literal("TODO: $expr")
        }
    }

    private fun translateExpressionStatement(stmt: TmpL.ExpressionStatement): Cpp.Stmt = cpp.pos(stmt) {
        val expr = translateExpression(stmt.expression)
        cpp.exprStmt(expr)
    }

    private fun MutableList<Cpp.Stmt>.translateHandlerScopeAssignment(
        assignment: TmpL.Assignment,
        check: TmpL.Statement,
    ) {
        val handlerScope = assignment.right as TmpL.HandlerScope
        val failed = translateId(handlerScope.failed)
        // Get the Expected instance.
        cpp.pos(assignment) {
            cpp.varDef(
                cpp.template(
                    cpp.name("temper", "core", "Expected"),
                    decls.getValue(assignment.left.name).type,
                ),
                failed as Cpp.SingleName,
                translateExpression(handlerScope.handled as TmpL.Expression),
            ).also { add(it) }
        }
        // Check if it's an error.
        cpp.pos(check) check@{
            val checkIf = (check as? TmpL.IfStatement) ?: return@check
            cpp.ifStmt(
                cpp.op("!", cpp.callExpr(cpp.memberExpr(failed, cpp.singleName("has_value")))),
                run {
                    // One of various options for providing failure information to failure propagation statements.
                    fails.add(failed)
                    try {
                        cpp.stmt(translateStatement(checkIf.consequent))
                    } finally {
                        fails.removeLast()
                    }
                },
            ).also { add(it) }
        }
        // Get the value from the Expected.
        cpp.pos(assignment) {
            // Use dereference op because we already checked if it has a value.
            cpp.exprStmt(
                cpp.op("=", translateId(assignment.left), cpp.op("*", failed)),
            ).also { add(it) }
        }
    }

    private fun translateId(id: TmpL.Id): Cpp.Name = cpp.pos(id) {
        // TODO Always use pretty names where possible?
        // TODO Namespacing.
        when (val name = id.name) {
            is Temporary -> "${name.nameHint}_${name.uid}" // double underscore forbidden
            else -> {
                when (val pretty = name.displayName) {
                    in cppKeywords -> "${pretty}_"
                    else -> pretty
                }
            }
            // We don't actually allow key below, but we've checked it above.
        }.let { cpp.singleName(CppName(it, allowKey = true)) }
    }

    private fun translateLabeledStatement(stmt: TmpL.LabeledStatement): List<Cpp.Stmt> = cpp.pos(stmt) {
        buildList {
            // TODO Also some inner label for loop blocks in case labeled continue is needed.
            translateStatement(stmt.statement).also { addAll(it) }
            cpp.labelStmt(
                translateId(stmt.label.id) as Cpp.SingleName,
                // We really just need the label in our case, but for formality and block ends, provide something.
                cpp.blockStmt(listOf()),
            ).also { add(it) }
        }
    }

    private fun translateLocalDeclaration(decl: TmpL.LocalDeclaration): List<Cpp.Stmt> =
        translateModuleOrLocalDeclaration(decl)

    private fun translateModuleInitFailed(stmt: TmpL.ModuleInitFailed): Cpp.Stmt = cpp.pos(stmt) {
        cpp.throwStmt(cpp.callExpr(cpp.name("std", "logic_error"), currentError()))
    }

    private fun translateModuleOrLocalDeclaration(
        decl: TmpL.ModuleOrLocalDeclaration,
    ): List<Cpp.VarDef> = cpp.pos(decl) {
        val type = translateType(decl.type)
        decls[decl.name.name] = DeclInfo(type = type)
        decl.metadata.any { it.key.symbol == failSymbol } && return listOf()
        cpp.varDef(
            type = type,
            name = translateId(decl.name) as Cpp.SingleName,
            init = decl.init?.let { translateExpression(it) },
        ).let { listOf(it) }
    }

    private fun translateReference(ref: TmpL.Reference): Cpp.Expr = cpp.pos(ref) {
        translateId(ref.id)
    }

    private fun translateReturnStatement(stmt: TmpL.ReturnStatement): Cpp.Stmt = cpp.pos(stmt) {
        cpp.returnStmt(stmt.expression?.let { translateExpression(it) })
    }

    private fun translateStatement(stmt: TmpL.Statement): List<Cpp.Stmt> = cpp.pos(stmt) {
        when (stmt) {
            is TmpL.Assignment -> translateAssignment(stmt)
            is TmpL.BlockStatement -> translateBlockStatement(stmt)
            is TmpL.BreakStatement -> translateBreakStatement(stmt)
            is TmpL.ExpressionStatement -> translateExpressionStatement(stmt)
            is TmpL.LabeledStatement -> return translateLabeledStatement(stmt)
            is TmpL.LocalDeclaration -> return translateLocalDeclaration(stmt)
            is TmpL.ModuleInitFailed -> translateModuleInitFailed(stmt)
            is TmpL.ReturnStatement -> translateReturnStatement(stmt)
            is TmpL.WhileStatement -> translateWhileStatement(stmt)
            else -> cpp.exprStmt(cpp.literal("TODO ${stmt.javaClass.simpleName} $stmt"))
        }.let { listOf(it) }
    }

    private fun MutableList<Cpp.Stmt>.translateStatements(statements: List<TmpL.Statement>) {
        var i = 0
        statements@ while (i < statements.size) {
            val statement = statements[i]
            when (statement) {
                // Combine handler scope statements that come awkwardly from frontend and tmpl.
                // TODO Non-assignment handler scope.
//                is TmpL.HandlerScope -> {
//                    translateHandlerScope(statement, statements[i + 1])
//                    i += 2
//                    continue@statements
//                }
                is TmpL.Assignment -> when (statement.right) {
                    is TmpL.HandlerScope -> {
                        translateHandlerScopeAssignment(statement, statements[i + 1])
                        i += 2
                        continue@statements
                    }
                    else -> {}
                }
                else -> {}
            }
            addAll(translateStatement(statement))
            i += 1
        }
    }

    private fun translateType(type: TmpL.AType): Cpp.Type = cpp.pos(type) {
        translateType(type.ot)
    }

    private fun translateType(type: TmpL.Type): Cpp.Type = run {
        when (type) {
            is TmpL.NominalType -> translateTypeDefinition(type.typeName.sourceDefinition)
            is TmpL.TypeUnion if type.types.size == 2 && type.types.any { it is TmpL.BubbleType } -> {
                val mainType = type.types.first { it !is TmpL.BubbleType }
                cpp.template(cpp.name("temper", "core", "Expected"), translateType(mainType))
            }
            else -> cpp.singleName("TODO")
        }
    }

    private fun translateTypeDefinition(def: TypeDefinition): Cpp.Type = run {
        when (def) {
            WellKnownTypes.booleanTypeDefinition -> return cpp.singleName(CppName("bool", allowKey = true))
            WellKnownTypes.intTypeDefinition -> return cpp.singleName("int32_t")
            WellKnownTypes.int64TypeDefinition -> return cpp.singleName("int64_t")
            WellKnownTypes.stringTypeDefinition -> cpp.name("std", "string")
            WellKnownTypes.voidTypeDefinition -> return cpp.singleName(CppName("void", allowKey = true))
            else -> cpp.singleName("TODO")
        }.let { cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Shared"), it) }
    }

    private fun translateValueReference(expr: TmpL.ValueReference): Cpp.Expr = cpp.pos(expr) {
        val value = expr.value
        when (value.typeTag) {
            TInt -> TInt.unpack(value).let { i ->
                when (i) {
                    // Sneak into min value because -x sees x first, which is overflow for signed.
                    Int.MIN_VALUE -> cpp.op("-", cpp.literal(Int.MIN_VALUE + 1), cpp.literal(1))
                    else -> cpp.literal(i)
                }
            }
            TInt64 -> TInt64.unpack(value).let { i ->
                when (i) {
                    // Sneak into min value because -x sees x first, which is overflow for signed.
                    Long.MIN_VALUE -> cpp.op("-", cpp.literal(Long.MIN_VALUE + 1), cpp.literal(1))
                    else -> cpp.literal(i)
                }.let { cpp.callExpr(cpp.singleName("int64_t"), it) }
            }
            TString -> TString.unpack(value).let { string ->
                string.codePoints().count()
                cpp.callExpr(
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "shared"), cpp.name("std", "string")),
                    // TODO Ensure we escape out string literals to utf8.
                    cpp.literal(string),
                    cpp.literal(string.utf8Length()),
                )
            }
            else -> cpp.literal("TODO $value")
        }
    }

    private fun translateWhileStatement(stmt: TmpL.WhileStatement): Cpp.Stmt = cpp.pos(stmt) {
        cpp.whileStmt(
            cond = translateExpression(stmt.test),
            body = cpp.stmt(translateStatement(stmt.body)),
        )
    }

    private fun currentError(): Cpp.Expr = run {
        when (val fail = fails.lastOrNull()) {
            null -> cpp.literal("fail")
            else -> cpp.callExpr(cpp.memberExpr(fail, cpp.singleName("error")))
        }
    }
}

data class DeclInfo(
    val type: Cpp.Type,
)
