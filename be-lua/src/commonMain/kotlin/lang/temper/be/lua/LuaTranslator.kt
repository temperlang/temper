package lang.temper.be.lua

import lang.temper.be.Backend
import lang.temper.be.Dependencies
import lang.temper.be.tmpl.ImplicitTypeTag
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.canBeNull
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.implicitTypeTag
import lang.temper.be.tmpl.libraryName
import lang.temper.be.tmpl.withoutBubbleOrNull
import lang.temper.common.MimeType
import lang.temper.common.ParseDouble
import lang.temper.common.TriState
import lang.temper.common.isNotEmpty
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.last
import lang.temper.log.spanningPosition
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.value.DependencyCategory
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

internal class LuaTranslator(
    val luaNames: LuaNames,
    val luaClosureMode: LuaClosureMode = LuaClosureMode.BasicFunction,
    val luaLibraryName: String? = null,
    private val shouldWrapFuncs: Boolean = false,
    private val dependenciesBuilder: Dependencies.Builder<LuaBackend>? = null,
) {
    fun translateTopLevel(mod: TmpL.Module): List<Backend.TranslatedFileSpecification> = luaNames.forModule(
        mod.codeLocation.codeLocation,
    ) {
        // Set up.
        fun makeParts(dependencyCategory: DependencyCategory) =
            ModuleParts(dependencyCategory, luaNames, luaClosureMode, shouldWrapFuncs, dependenciesBuilder)
        val prodModuleParts = makeParts(DependencyCategory.Production)
        val testModuleParts = makeParts(DependencyCategory.Test)
        prodModuleParts.init(mod)
        testModuleParts.init(mod)
        // Translate to Lua.
        val prodMap = mutableMapOf<LuaName, TmpL.Declaration>()
        mod.topLevels.forEach topLevels@{ topLevel ->
            val parts = when (topLevel.dependencyCategory()) {
                DependencyCategory.Production -> {
                    if (topLevel is TmpL.Declaration) {
                        // Also track prod top-levels by Lua name.
                        prodMap[luaNames.name(topLevel.name)] = topLevel
                    }
                    prodModuleParts
                }
                DependencyCategory.Test -> testModuleParts
                null -> return@topLevels
            }
            parts.topLevels.addAll(parts.translateTopLevel(topLevel))
        }
        // Figure out what we need to import from prod in tests.
        var anyUnexported = false
        val neededByTest = buildSet {
            for (testTop in testModuleParts.topLevels) {
                fun dig(tree: Lua.Tree) {
                    kids@ for (kid in tree.children) {
                        when (kid) {
                            is Lua.Name -> {
                                val prodTop = prodMap[kid.id] ?: continue@kids
                                if (prodTop.name.name !is ExportedName) {
                                    anyUnexported = true
                                }
                                add(kid.id)
                            }
                            else -> dig(kid)
                        }
                    }
                }
                dig(testTop)
            }
        }
        // Generate.
        buildList {
            // Prod module.
            val prodSuffix = when {
                anyUnexported -> "-internal"
                else -> ""
            }
            val prodUnit = prodModuleParts.finish(mod, ensuredExported = neededByTest, nameSuffix = prodSuffix)
            add(prodUnit)
            val prodPath = when (luaLibraryName) {
                null -> prodUnit.path
                else -> dirPath(luaLibraryName).resolve(prodUnit.path)
            }
            val prodModuleName = (prodPath.dirName().segments + prodPath.last().baseName).joinToString(LUA_MODULE_SEP)
            // Public prod module if primary was internal.
            if (anyUnexported) {
                add(
                    buildPublicExports(
                        internalName = prodModuleName,
                        luaNames = luaNames,
                        mod = mod,
                        topLevels = mod.topLevels,
                    ),
                )
            }
            // Test module if needed.
            if (testModuleParts.topLevels.isNotEmpty()) {
                if (neededByTest.isNotEmpty()) {
                    for (name in neededByTest) {
                        testModuleParts.addImport(mod.pos, name, prodModuleName)
                    }
                }
                add(testModuleParts.finish(mod, nameSuffix = "-test", pathPrefix = dirPath(LUA_TESTS_DIR)))
            }
        }
    }
}

private class ModuleParts(
    val dependencyCategory: DependencyCategory,
    val luaNames: LuaNames,
    val luaClosureMode: LuaClosureMode = LuaClosureMode.BasicFunction,
    private val shouldWrapFuncs: Boolean = false,
    private val dependenciesBuilder: Dependencies.Builder<LuaBackend>? = null,
) {
    private var breaks = mutableListOf<String?>()
    private var continues = mutableListOf<String?>()
    private val labels = mutableMapOf<String, Int>()
    private var labelUsed = mutableSetOf<String>()
    private var libraryName: DashedIdentifier? = null
    var needsPreDecl = mutableSetOf<LuaName>()
    var importedNames = mutableListOf<LuaName>()
    val exportedNames = mutableSetOf<LuaName>()
    var globalFuncs = mutableListOf<Lua.Stmt>()
    val topLevels = mutableListOf<Lua.Stmt>()
    val imports = mutableListOf<Lua.Stmt>()
    val preDecls = mutableListOf<Lua.Stmt>()
    val exports = mutableListOf<Lua.Stmt>()
    lateinit var temperStmt: Lua.Stmt

    /**
     * Has to start with "Test" for LuaUnit, and easiest just to define whether we're test category or prod.
     * TODO Ensure unique instead of hoping because of awkward naming?
     * TODO Can we just reserve leading, trailing, and 2+ runs of "_"?
     */
    private val testClassName = name("Test_")

    private fun translateExpr(expr: TmpL.AwaitExpression): Lua.Expr {
        val rightPos = expr.pos.rightEdge
        // Can we just put a method on promise? `promise:await()`
        return Lua.MethodCallExpr(
            expr.pos,
            translateExpr(expr.promise),
            Lua.Name(rightPos, LuaName("await")),
            Lua.Args(rightPos, Lua.Exprs(rightPos, listOf())),
        )
    }

    private fun translateCallable(callable: TmpL.Callable): Lua.Expr = when (callable) {
        is TmpL.FnReference -> translateReference(callable)
        is TmpL.FunInterfaceCallable -> translateExpr(callable.expr)
        is TmpL.ConstructorReference -> Lua.Name(
            callable.pos,
            luaNames.name(callable.typeShape.name),
        )
        is TmpL.MethodReference -> {
            val subject = translateSubject(callable.subject)
            val dotName = callable.methodName.dotNameText
            when (callable.subject) {
                is TmpL.TypeName -> (subject as Lua.LiteralExpr).dot(fixName(dotName))
                else -> TODO()
            }
        }

        is TmpL.GarbageCallable -> TODO()
        is TmpL.InlineSupportCodeWrapper -> TODO()
    }

    private fun translateExpr(
        expr: TmpL.CallExpression,
    ): Lua.Expr {
        val args = lazy {
            val pos = expr.parameters.spanningPosition(expr.pos.rightEdge)
            Lua.Args(pos, Lua.Exprs(pos, translateParameters(expr.parameters)))
        }
        return when (val fn = expr.fn) {
            is TmpL.InlineSupportCodeWrapper -> (fn.supportCode as InlineLua).callFactory(
                expr.pos,
                translateParameters(expr.parameters),
            ) as Lua.Expr

            // Use colon for instance methods, but farther down,
            // dot for static, where the subject is always a LiteralExpr
            is TmpL.MethodReference if fn.subject !is TmpL.TypeName -> {
                val subject = translateSubject(fn.subject)
                val dotName = fn.methodName.dotNameText
                Lua.MethodCallExpr(
                    expr.pos,
                    subject,
                    Lua.Name(fn.methodName.pos, safeName(dotName)),
                    args.value,
                )
            }
            is TmpL.GarbageCallable -> translateGarbage(fn)
            is TmpL.FnReference,
            is TmpL.ConstructorReference,
            is TmpL.FunInterfaceCallable,
            is TmpL.MethodReference,
            -> Lua.FunctionCallExpr(expr.pos, translateCallable(fn), args.value)
        }
    }

    private fun translateSubject(
        subject: TmpL.Subject,
    ): Lua.Expr = when (subject) {
        is TmpL.Expression ->
            translateExpr(subject)
        // TODO: we need some way to refer to a holder of statics
        is TmpL.TemperTypeName ->
            Lua.Name(subject.pos, luaNames.name(subject.typeDefinition.name))
        is TmpL.ConnectedToTypeName ->
            TODO("Handle be-lua's flavour of TargetLanguageTypeName when it has one")
    }

    private fun translateExpr(
        expr: TmpL.CastExpression,
    ): Lua.Expr {
        fun castFunc(
            implFunc: String,
        ): Lua.Expr = Lua.FunctionCallExpr(
            expr.pos,
            Lua.DotIndexExpr(
                expr.pos,
                Lua.Name(expr.pos, name("temper")),
                Lua.Name(expr.pos, name(implFunc)),
            ),
            Lua.Args(
                expr.pos,
                Lua.Exprs(
                    expr.pos,
                    listOf(
                        translateExpr(expr.expr),
                    ),
                ),
            ),
        )

        return when (expr.checkedType.implicitTypeTag) {
            ImplicitTypeTag.Boolean -> castFunc("cast_to_boolean")
            ImplicitTypeTag.Float64 -> castFunc("cast_to_float64")
            ImplicitTypeTag.Int -> castFunc("cast_to_int")
            ImplicitTypeTag.String -> castFunc("cast_to_string")
            ImplicitTypeTag.Function -> castFunc("cast_to_function")
            ImplicitTypeTag.List -> castFunc("cast_to_list")
            ImplicitTypeTag.ListBuilder -> castFunc("cast_to_listbuilder")
            ImplicitTypeTag.Map -> castFunc("cast_to_map")
            ImplicitTypeTag.MapBuilder -> castFunc("cast_to_mapbuilder")
            ImplicitTypeTag.Null -> castFunc("cast_to_null")
            ImplicitTypeTag.Other -> {
                val typeName = (expr.checkedType.ot.withoutBubbleOrNull as TmpL.NominalType)
                    .typeName.sourceDefinition.name
                Lua.FunctionCallExpr(
                    expr.pos,
                    Lua.DotIndexExpr(
                        expr.pos,
                        Lua.Name(expr.pos, name("temper")),
                        Lua.Name(expr.pos, name("cast_to")),
                    ),
                    Lua.Args(
                        expr.pos,
                        Lua.Exprs(
                            expr.pos,
                            listOf(
                                translateExpr(expr.expr),
                                Lua.Name(
                                    expr.pos,
                                    luaNames.name(typeName),
                                ),
                            ),
                        ),
                    ),
                )
            }
            ImplicitTypeTag.Void -> TODO("cast to Void")
        }
    }

    private fun translateExpr(
        expr: TmpL.InstanceOfExpression,
    ): Lua.Expr {
        fun typeEq(typeStr: String): Lua.Expr =
            // type(expr) == "..."
            Lua.BinaryExpr(
                expr.pos,
                Lua.FunctionCallExpr(
                    expr.expr.pos,
                    Lua.Name(expr.expr.pos.leftEdge, LuaName("type")),
                    Lua.Args(
                        expr.expr.pos,
                        Lua.Exprs(expr.expr.pos, listOf(translateExpr(expr.expr))),
                    ),
                ),
                Lua.BinaryOp(
                    expr.expr.pos.rightEdge,
                    BinaryOpEnum.Eq,
                    LuaOperatorDefinition.Eq,
                ),
                Lua.Str(expr.checkedType.pos, typeStr),
            )

        fun hasTag(tag: Lua.Name): Lua.Expr =
            // temper.instance_of(expr, tag)
            Lua.FunctionCallExpr(
                expr.pos,
                Lua.DotIndexExpr(
                    expr.pos,
                    Lua.Name(expr.pos, name("temper")),
                    Lua.Name(expr.pos, name("instance_of")),
                ),
                Lua.Args(
                    expr.pos,
                    Lua.Exprs(
                        expr.pos,
                        listOf(
                            translateExpr(expr.expr),
                            tag,
                        ),
                    ),
                ),
            )

        fun hasTag(tagText: String) =
            hasTag(Lua.Name(expr.checkedType.pos, name(tagText)))

        return when (expr.checkedType.implicitTypeTag) {
            ImplicitTypeTag.Boolean -> typeEq("boolean")
            ImplicitTypeTag.Float64 -> typeEq("number")
            ImplicitTypeTag.Int -> typeEq("number")
            ImplicitTypeTag.String -> typeEq("string")
            ImplicitTypeTag.Function -> typeEq("function")
            ImplicitTypeTag.List -> hasTag("List")
            ImplicitTypeTag.ListBuilder -> hasTag("ListBuilder")
            ImplicitTypeTag.Map -> hasTag("Map")
            ImplicitTypeTag.MapBuilder -> hasTag("MapBuilder")
            ImplicitTypeTag.Null -> TODO("should call isNull")
            ImplicitTypeTag.Void -> TODO("cast to Void")
            ImplicitTypeTag.Other -> {
                val typeName = (expr.checkedType.ot.withoutBubbleOrNull as TmpL.NominalType)
                    .typeName.sourceDefinition.name
                hasTag(
                    Lua.Name(
                        expr.pos,
                        luaNames.name(typeName),
                    ),
                )
            }
        }
    }

    private fun translateExpr(
        expr: TmpL.UncheckedNotNullExpression,
    ): Lua.Expr = translateExpr(expr.expression)

    private fun translateExpr(
        expr: TmpL.Expression,
    ): Lua.Expr = when (expr) {
        is TmpL.AwaitExpression -> translateExpr(expr)
        is TmpL.CallExpression -> translateExpr(expr)
        is TmpL.CastExpression -> translateExpr(expr)
        is TmpL.UncheckedNotNullExpression -> translateExpr(expr)
        is TmpL.FunInterfaceExpression -> translateCallable(expr.callable)
        is TmpL.GarbageExpression -> translateGarbage(expr)
        is TmpL.GetAbstractProperty -> Lua.DotIndexExpr(
            expr.pos,
            when (val got = translateExpr(expr.subject)) {
                is Lua.LiteralExpr -> got
                else -> Lua.WrappedExpr(got.pos, got)
            },
            Lua.Name(expr.pos, safeName(expr.property.toString())),
        )
        is TmpL.GetBackedProperty -> Lua.DotIndexExpr(
            expr.pos,
            when (val got = translateSubject(expr.subject)) {
                is Lua.LiteralExpr -> got
                else -> Lua.WrappedExpr(got.pos, got)
            },
            Lua.Name(expr.pos, safeName(expr.property.toString())),
        )
        is TmpL.InstanceOfExpression -> translateExpr(expr)
        is TmpL.BubbleSentinel -> TODO()
        is TmpL.InfixOperation -> {
            val op = when (expr.op.tmpLOperator) {
                TmpLOperator.AmpAmp -> BinaryOpEnum.BoolAnd
                TmpLOperator.BarBar -> BinaryOpEnum.BoolOr
                TmpLOperator.EqEqInt -> BinaryOpEnum.Eq
                TmpLOperator.GeInt -> BinaryOpEnum.GtEq
                TmpLOperator.GtInt -> BinaryOpEnum.Gt
                TmpLOperator.LeInt -> BinaryOpEnum.LtEq
                TmpLOperator.LtInt -> BinaryOpEnum.Lt
                TmpLOperator.PlusInt -> BinaryOpEnum.Add
            }
            Lua.BinaryExpr(
                expr.pos,
                translateExpr(expr.left),
                Lua.BinaryOp(expr.op.pos, op, op.operatorDefinition),
                translateExpr(expr.right),
            )
        }
        is TmpL.PrefixOperation -> when (expr.op.tmpLOperator) {
            TmpLOperator.Bang -> Lua.UnaryExpr(
                expr.pos,
                Lua.UnaryOp(
                    expr.pos,
                    UnaryOpEnum.BoolNot,
                    LuaOperatorDefinition.Not,
                ),
                translateExpr(expr.operand),
            )
        }
        is TmpL.Reference -> translateReference(expr)
        is TmpL.RestParameterCountExpression -> Lua.FunctionCallExpr(
            expr.pos,
            Lua.Name(expr.pos, name("select")),
            Lua.Args(
                expr.pos,
                Lua.Exprs(
                    expr.pos,
                    listOf(
                        Lua.Str(expr.pos, "#"),
                        Lua.RestExpr(expr.pos),
                    ),
                ),
            ),
        )
        is TmpL.RestParameterExpression -> Lua.FunctionCallExpr(
            expr.pos,
            Lua.DotIndexExpr(
                expr.pos,
                Lua.Name(expr.pos, name("temper")),
                Lua.Name(expr.pos, name("listof")),
            ),
            Lua.Args(
                expr.pos,
                Lua.Exprs(
                    expr.pos,
                    listOf(
                        Lua.RestExpr(expr.pos),
                    ),
                ),
            ),
        )
        is TmpL.This -> Lua.Name(expr.pos, luaNames.name(expr.id))
        is TmpL.ValueReference -> expr.value.let { value ->
            when (value.typeTag) {
                TBoolean -> Lua.Name(
                    expr.pos,
                    name(
                        if (TBoolean.unpack(value)) {
                            "true"
                        } else {
                            "false"
                        },
                    ),
                )

                TFloat64 -> {
                    val f64 = TFloat64.unpack(value)
                    when (ParseDouble.invoke(f64)) {
                        ParseDouble.NegativeInfinity -> Lua.DotIndexExpr(
                            expr.pos,
                            Lua.Name(expr.pos, name("temper")),
                            Lua.Name(expr.pos, name("neg_inf")),
                        )
                        ParseDouble.PositiveInfinity -> Lua.DotIndexExpr(
                            expr.pos,
                            Lua.Name(expr.pos, name("temper")),
                            Lua.Name(expr.pos, name("pos_inf")),
                        )
                        ParseDouble.NaN -> Lua.DotIndexExpr(
                            expr.pos,
                            Lua.Name(expr.pos, name("temper")),
                            Lua.Name(expr.pos, name("nan")),
                        )
                        else -> Lua.Num(expr.pos, f64)
                    }
                }
                TInt -> Lua.Num(expr.pos, TInt.unpack(value))
                TInt64 -> Lua.FunctionCallExpr(
                    expr.pos,
                    Lua.DotIndexExpr(
                        expr.pos,
                        Lua.Name(expr.pos, name("temper")),
                        Lua.Name(expr.pos, name("int64_constructor")),
                    ),
                    Lua.Args(
                        expr.pos,
                        Lua.Exprs(
                            expr.pos,
                            @Suppress("MagicNumber")
                            buildList {
                                when (val i = TInt64.unpack(value)) {
                                    in Integer.MIN_VALUE..Integer.MAX_VALUE ->
                                        add(Lua.Num(expr.pos, i))
                                    else -> {
                                        // TODO Actually test this sometime.
                                        add(Lua.Num(expr.pos, i shr 32))
                                        add(Lua.Num(expr.pos, i and 0xffff_ffff))
                                    }
                                }
                            },
                        ),
                    ),
                )
                TString -> Lua.Str(expr.pos, TString.unpack(value))
                is TClass -> TODO("TClass")
                TClosureRecord -> TODO("TClosureRecord")
                TFunction -> TODO("TFunction")
                TList -> TODO("TList")
                TListBuilder -> TODO("TListBuilder")
                TMap -> TODO("TMap")
                TMapBuilder -> TODO("TMapBuilder")
                TNull -> makeNull(expr.pos)
                TProblem -> TODO("TProblem")
                TStageRange -> TODO("TStageRange")
                TSymbol -> Lua.Str(expr.pos, TSymbol.unpack(value).text)
                TType -> Lua.Name(expr.pos, name("nil"))
                TVoid -> Lua.Name(expr.pos, name("nil"))
            }
        }
    }

    fun translateReference(ref: TmpL.AnyReference) = Lua.Name(ref.pos, luaNames.name(ref.id))

    fun translateGarbage(garbage: TmpL.Garbage) = Lua.FunctionCallExpr(
        garbage.pos,
        Lua.DotIndexExpr(
            garbage.pos,
            Lua.Name(garbage.pos, name("temper")),
            Lua.Name(garbage.pos, name("bubble")),
        ),
        Lua.Args(
            garbage.pos,
            Lua.Exprs(
                garbage.pos,
                when (val text = garbage.diagnostic?.text) {
                    null -> listOf()
                    else -> listOf(Lua.Str(garbage.pos, text))
                },
            ),
        ),
    )

    private fun translateParameters(
        parameters: List<TmpL.Actual>,
    ): List<Lua.Expr> {
        val goodParams = parameters.filter {
            it !is TmpL.ValueReference || it.value.typeTag != TSymbol
        }
        return goodParams.map {
            when (it) {
                is TmpL.Expression -> translateExpr(it)
                is TmpL.RestSpread -> Lua.RestExpr(it.pos)
            }
        }
    }

    private fun translateAssignmentBase(
        stmt: TmpL.Assignment,
    ): List<Lua.Stmt> = when {
        stmt.right.isPureVirtual() -> listOf(
            Lua.CallStmt(
                stmt.pos,
                Lua.FunctionCallExpr(
                    stmt.pos,
                    Lua.DotIndexExpr(
                        stmt.pos,
                        Lua.Name(stmt.pos, name("temper")),
                        Lua.Name(stmt.pos, name("bubble")),
                    ),
                    Lua.Args(
                        stmt.pos,
                        Lua.Exprs(stmt.pos, listOf()),
                    ),
                ),
            ),
        )
        else -> listOf(
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.left.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.left.pos,
                            Lua.Name(stmt.left.pos, luaNames.name(stmt.left)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.right.pos,
                    listOf(
                        translateExpr(stmt.right as TmpL.Expression),
                    ),
                ),
            ),
        )
    }

    private fun translateStmt(
        stmt: TmpL.Assignment,
    ): List<Lua.Stmt> = translateAssignmentBase(stmt)

    private fun translateStmt(
        stmt: TmpL.BlockStatement,
    ): List<Lua.Stmt> {
        val list = mutableListOf<Lua.Stmt>()
        stmt.statements.forEach {
            list.addAll(translateStmt(it))
        }
        return list
    }

    private fun translateStmt(
        stmt: TmpL.BoilerplateCodeFoldEnd,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "code-fold-end"))

    private fun translateStmt(
        stmt: TmpL.BoilerplateCodeFoldStart,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "code-fold-start"))

    private fun translateStmt(
        stmt: TmpL.BreakStatement,
    ): List<Lua.Stmt> = when (val label = stmt.label) {
        null -> {
            val index = breaks.size - 1
            if (index < 0) {
                labelUsed.add("break")
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.ReturnStmt(
                                stmt.pos,
                                Lua.Exprs(
                                    stmt.pos,
                                    listOf(
                                        Lua.Str(stmt.pos, "break"),
                                        Lua.Str(stmt.pos, "flow"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            } else {
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.BreakStmt(stmt.pos),
                        ),
                    ),
                )
            }
        }
        else -> {
            if (labels[luaNames.onBreak(label.toString()).text] == 0) {
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.GotoStmt(
                                stmt.pos,
                                Lua.Name(stmt.pos, luaNames.onBreak(label.toString())),
                            ),
                        ),
                    ),
                )
            } else {
                labelUsed.add(luaNames.onBreak(label.toString()).text)
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.ReturnStmt(
                                stmt.pos,
                                Lua.Exprs(
                                    stmt.pos,
                                    listOf(
                                        Lua.Str(stmt.pos, luaNames.onBreak(label.toString()).text),
                                        Lua.Str(stmt.pos, "flow"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun translateStmt(
        stmt: TmpL.ContinueStatement,
    ): List<Lua.Stmt> = when (val label = stmt.label) {
        null -> {
            val index = continues.size - 1
            if (index < 0) {
                labelUsed.add("continue")
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.ReturnStmt(
                                stmt.pos,
                                Lua.Exprs(
                                    stmt.pos,
                                    listOf(
                                        Lua.Str(stmt.pos, "continue"),
                                        Lua.Str(stmt.pos, "flow"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            } else {
                val cont = continues[index] ?: luaNames.gensym().text
                continues[index] = cont
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.GotoStmt(
                                stmt.pos,
                                Lua.Name(stmt.pos, name(cont)),
                            ),
                        ),
                    ),
                )
            }
        }
        else -> {
            if (labels[luaNames.onContinue(label.toString()).text] == 0) {
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.GotoStmt(
                                stmt.pos,
                                Lua.Name(stmt.pos, luaNames.onContinue(label.toString())),
                            ),
                        ),
                    ),
                )
            } else {
                labelUsed.add(luaNames.onContinue(label.toString()).text)
                listOf(
                    Lua.DoStmt(
                        stmt.pos,
                        luaChunk(
                            stmt.pos,
                            listOf(),
                            Lua.ReturnStmt(
                                stmt.pos,
                                Lua.Exprs(
                                    stmt.pos,
                                    listOf(
                                        Lua.Str(stmt.pos, luaNames.onContinue(label.toString()).text),
                                        Lua.Str(stmt.pos, "flow"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun translateStmt(
        stmt: TmpL.EmbeddedComment,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "comment: ${stmt.commentText}"))

    private fun translateStmt(
        stmt: TmpL.ExpressionStatement,
    ): List<Lua.Stmt> = when (val expr = translateExpr(stmt.expression)) {
        is Lua.CallExpr -> listOf(
            Lua.CallStmt(
                stmt.pos,
                expr,
            ),
        )
        else -> listOf()
    }

    private fun translateStmt(
        stmt: TmpL.GarbageStatement,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "garbage"))

    private fun translateStmt(
        stmt: TmpL.HandlerScope,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "handler"))

    private fun translateStmt(
        stmt: TmpL.IfStatement,
    ): List<Lua.Stmt> {
        // ElseIf gathering styled after PyTranslatorImpl. TODO Unify logic in TmpL somewhere?
        val elseIfs = mutableListOf<Lua.ElseIf>()
        var alternate = stmt.alternate
        while (alternate is TmpL.IfStatement) {
            elseIfs.add(
                Lua.ElseIf(
                    alternate.pos,
                    translateExpr(alternate.test),
                    luaChunk(alternate.consequent.pos, translateStmt(alternate.consequent), null),
                ),
            )
            alternate = alternate.alternate
        }
        // Once we know the elseif chain, we can put things together at one go.
        return listOf(
            Lua.IfStmt(
                stmt.pos,
                translateExpr(stmt.test),
                luaChunk(stmt.pos, translateStmt(stmt.consequent), null),
                elseIfs,
                alternate?.let { luaChunk(it.pos, translateStmt(it), null) }?.let { Lua.Else(it.pos, it) },
            ),
        )
    }

    private fun translateStmt(
        stmt: TmpL.LabeledStatement,
    ): List<Lua.Stmt> {
        val onBreak = luaNames.onBreak(stmt.label.toString())
        val onContinue = luaNames.onContinue(stmt.label.toString())
        breaks.add(onBreak.text)
        continues.add(onContinue.text)
        labels[onBreak.text] = 0
        labels[onContinue.text] = 0
        val body = translateStmt(stmt.statement)
        val onBreakOrNull = breaks.removeLastOrNull()
        val onContinueOrNull = continues.removeLastOrNull()
        labels.remove(onBreak.text)
        labels.remove(onContinue.text)
        if (onContinueOrNull == null && onBreakOrNull == null) {
            return body
        }
        return buildList {
            if (onContinueOrNull != null) {
                add(
                    Lua.LabelStmt(
                        stmt.pos,
                        Lua.Name(stmt.pos, onContinue),
                    ),
                )
            }
            add(
                Lua.DoStmt(
                    stmt.pos,
                    luaChunk(
                        stmt.pos,
                        body,
                        null,
                    ),
                ),
            )
            if (onBreakOrNull != null) {
                add(
                    Lua.LabelStmt(
                        stmt.pos,
                        Lua.Name(stmt.pos, onBreak),
                    ),
                )
            }
        }
    }

    private fun translateStmt(
        stmt: TmpL.LocalDeclaration,
    ): List<Lua.Stmt> = when (val init = stmt.init) {
        null -> listOf(
            Lua.LocalDeclStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.pos,
                            Lua.Name(stmt.pos, luaNames.name(stmt.name)),
                        ),
                    ),
                ),
            ),
        )
        else -> listOf(
            Lua.LocalStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.pos,
                            Lua.Name(stmt.pos, luaNames.name(stmt.name)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    init.pos,
                    listOf(
                        translateExpr(init),
                    ),
                ),
            ),
        )
    }

    private fun translateStmt(
        stmt: TmpL.LocalFunctionDeclaration,
    ): List<Lua.Stmt> {
        val pos = stmt.pos
        val name = Lua.Name(stmt.name.pos, luaNames.name(stmt.name))
        val params = Lua.Params(
            stmt.parameters.pos,
            translateParamDecl(stmt.parameters),
        )
        val body = luaChunk(
            stmt.body.pos,
            handleSpecialParams(stmt.parameters) + translateStmt(stmt.body),
            null,
        )
        val func = Lua.LocalFunctionStmt(
            pos = pos,
            name = name,
            params = params,
            body = body,
        )
        return if (stmt.mayYield) {
            listOf(
                func,
                Lua.SetStmt(
                    pos,
                    Lua.SetTargets(
                        name.pos,
                        listOf(
                            Lua.NameSetTarget(
                                name.pos,
                                Lua.Name(name.pos, luaNames.name(stmt.name)),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            Lua.FunctionCallExpr(
                                stmt.pos,
                                Lua.DotIndexExpr(
                                    stmt.pos,
                                    Lua.Name(stmt.pos, name("temper")),
                                    Lua.Name(stmt.pos, name("adapt_generator_fn")),
                                ),
                                Lua.Args(
                                    stmt.pos,
                                    Lua.Exprs(
                                        stmt.pos,
                                        listOf(
                                            Lua.Name(stmt.name.pos, luaNames.name(stmt.name)),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        } else {
            listOf(func)
        }
    }

    private fun translateStmt(
        stmt: TmpL.ModuleInitFailed,
    ): List<Lua.Stmt> = listOf(Lua.Comment(stmt.pos, "init-failed"))

    private fun translateStmt(
        stmt: TmpL.YieldStatement,
    ): List<Lua.Stmt> = listOf(
        Lua.CallStmt(
            stmt.pos,
            Lua.FunctionCallExpr(
                stmt.pos,
                Lua.DotIndexExpr(
                    stmt.pos,
                    Lua.Name(stmt.pos, name("temper")),
                    Lua.Name(stmt.pos, name("yield")),
                ),
                Lua.Args(
                    stmt.pos,
                    Lua.Exprs(
                        stmt.pos,
                        listOf(),
                    ),
                ),
            ),
        ),
    )

    private fun translateStmt(
        stmt: TmpL.ReturnStatement,
    ): List<Lua.Stmt> = listOf(
        Lua.DoStmt(
            stmt.pos,
            luaChunk(
                stmt.pos,
                listOf(),
                Lua.ReturnStmt(
                    stmt.pos,
                    when (val value = stmt.expression) {
                        null -> Lua.Exprs(
                            stmt.pos,
                            if (depth == 0) {
                                listOf(
                                    Lua.Name(stmt.pos, name("nil")),
                                )
                            } else {
                                labelUsed.add("return")
                                listOf(
                                    Lua.Name(stmt.pos, name("nil")),
                                    Lua.Str(stmt.pos, "return"),
                                )
                            },
                        )
                        else -> Lua.Exprs(
                            stmt.pos,
                            if (depth == 0) {
                                listOf(
                                    translateExpr(value),
                                )
                            } else {
                                labelUsed.add("return")
                                listOf(
                                    translateExpr(value),
                                    Lua.Str(stmt.pos, "return"),
                                )
                            },
                        )
                    },
                ),
            ),
        ),
    )

    private fun translateTarget(
        target: TmpL.PropertyLValue,
    ): Lua.SetTarget = when (val prop = target.property) {
        // QUESTION Why do we use subject["prop"] for external rather than subject.prop?
        is TmpL.ExternalPropertyId -> Lua.IndexSetTarget(
            target.pos,
            when (val obj = translateSubject(target.subject)) {
                is Lua.Name -> Lua.NameSetTarget(obj.pos, obj)
                is Lua.SetTargetOrWrapped -> obj
                else -> Lua.WrappedExpr(obj.pos, obj)
            },
            Lua.Str(
                prop.pos,
                // Even if using string indexing here, it references something that needs escaped elsewhere.
                fixName(prop.name.dotNameText),
            ),
        )
        is TmpL.InternalPropertyId -> Lua.DotSetTarget(
            target.pos,
            when (val obj = translateSubject(target.subject)) {
                is Lua.Name -> Lua.NameSetTarget(obj.pos, obj)
                is Lua.SetTargetOrWrapped -> obj
                else -> Lua.WrappedExpr(obj.pos, obj)
            },
            Lua.Name(
                prop.pos,
                luaNames.name(prop.name),
            ),
        )
    }

    private fun translateStmt(
        stmt: TmpL.SetAbstractProperty,
    ): List<Lua.Stmt> = listOf(
        Lua.SetStmt(
            stmt.pos,
            Lua.SetTargets(
                stmt.pos,
                listOf(
                    translateTarget(stmt.left),
                ),
            ),
            Lua.Exprs(
                stmt.pos,
                listOf(
                    translateExpr(stmt.right),
                ),
            ),
        ),
    )

    private fun translateStmt(
        stmt: TmpL.SetBackedProperty,
    ): List<Lua.Stmt> {
        val subject = translateSubject(stmt.left.subject)
        return if (subject is Lua.Name) {
            listOf(
                Lua.SetStmt(
                    stmt.pos,
                    Lua.SetTargets(
                        stmt.pos,
                        listOf(
                            Lua.DotSetTarget(
                                stmt.pos,
                                Lua.NameSetTarget(
                                    stmt.pos,
                                    Lua.Name(stmt.pos, subject.id),
                                ),
                                Lua.Name(stmt.pos, safeName(stmt.left.property.toString())),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            translateExpr(stmt.right),
                        ),
                    ),
                ),
            )
        } else {
            val id = luaNames.gensym()
            listOf(
                Lua.LocalStmt(
                    stmt.pos,
                    Lua.SetTargets(
                        stmt.pos,
                        listOf(
                            Lua.NameSetTarget(
                                stmt.pos,
                                Lua.Name(stmt.pos, id),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            subject,
                        ),
                    ),
                ),
                Lua.SetStmt(
                    stmt.pos,
                    Lua.SetTargets(
                        stmt.pos,
                        listOf(
                            Lua.DotSetTarget(
                                stmt.pos,
                                Lua.NameSetTarget(
                                    stmt.pos,
                                    Lua.Name(stmt.pos, id),
                                ),
                                Lua.Name(stmt.pos, safeName(stmt.left.property.toString())),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            translateExpr(stmt.right),
                        ),
                    ),
                ),
            )
        }
    }

    private fun translateStmt(
        stmt: TmpL.ThrowStatement,
    ): List<Lua.Stmt> = listOf(
        Lua.CallStmt(
            stmt.pos,
            Lua.FunctionCallExpr(
                stmt.pos,
                Lua.DotIndexExpr(
                    stmt.pos,
                    Lua.Name(stmt.pos, name("temper")),
                    Lua.Name(stmt.pos, name("bubble")),
                ),
                Lua.Args(
                    stmt.pos,
                    Lua.Exprs(
                        stmt.pos,
                        listOf(),
                    ),
                ),
            ),
        ),
    )

    private fun translateStmt(
        stmt: TmpL.TryStatement,
    ): List<Lua.Stmt> {
        val ret = mutableListOf<Lua.Stmt>()
        val ok = luaNames.gensym()
        val msg = luaNames.gensym()
        val isbreak = luaNames.gensym()
        val (tried, usedLabels) = scoped(false) {
            translateStmt(stmt.tried)
        }
        val objName = luaNames.gensym()
        val closure = when (luaClosureMode) {
            LuaClosureMode.BasicFunction -> null
            LuaClosureMode.GlobalTableFunction -> LuaClosure(
                objName,
                importedNames,
            )
            LuaClosureMode.InlineTableFunction -> LuaClosure(
                objName,
                listOf(),
            )
        }
        val functionExpr: Lua.Expr = when (luaClosureMode) {
            LuaClosureMode.BasicFunction -> Lua.FunctionExpr(
                stmt.tried.pos,
                Lua.Params(
                    stmt.tried.pos,
                    listOf(),
                ),
                luaChunk(
                    stmt.tried.pos,
                    tried,
                    null,
                ),
            )
            LuaClosureMode.GlobalTableFunction -> {
                val funcName = luaNames.gensym()
                globalFuncs.add(
                    Lua.LocalFunctionStmt(
                        stmt.tried.pos,
                        Lua.Name(stmt.tried.pos, funcName),
                        Lua.Params(
                            stmt.tried.pos,
                            listOf(
                                Lua.Param(
                                    stmt.tried.pos,
                                    Lua.Name(stmt.pos, objName),
                                ),
                            ),
                        ),
                        luaChunk(
                            stmt.tried.pos,
                            tried.map(closure!!::scan),
                            null,
                        ),
                    ),
                )
                Lua.Name(stmt.tried.pos, funcName)
            }
            LuaClosureMode.InlineTableFunction -> Lua.FunctionExpr(
                stmt.tried.pos,
                Lua.Params(
                    stmt.tried.pos,
                    listOf(
                        Lua.Param(
                            stmt.tried.pos,
                            Lua.Name(stmt.pos, objName),
                        ),
                    ),
                ),
                luaChunk(
                    stmt.tried.pos,
                    tried.map(closure!!::scan),
                    null,
                ),
            )
        }
        if (closure != null) {
            ret.add(
                Lua.LocalStmt(
                    stmt.pos,
                    Lua.SetTargets(
                        stmt.pos,
                        listOf(
                            Lua.NameSetTarget(
                                stmt.pos,
                                Lua.Name(stmt.pos, objName),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            Lua.TableExpr(
                                stmt.pos,
                                closure.allExterns.map { name ->
                                    Lua.IndexTableEntry(
                                        stmt.pos,
                                        Lua.Name(stmt.pos, name),
                                    )
                                },
                            ),
                        ),
                    ),
                ),
            )
        }
        ret.add(
            Lua.LocalStmt(
                stmt.tried.pos,
                Lua.SetTargets(
                    stmt.tried.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.tried.pos,
                            Lua.Name(stmt.tried.pos, ok),
                        ),
                        Lua.NameSetTarget(
                            stmt.tried.pos,
                            Lua.Name(stmt.tried.pos, msg),
                        ),
                        Lua.NameSetTarget(
                            stmt.tried.pos,
                            Lua.Name(stmt.tried.pos, isbreak),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.tried.pos,
                    listOf(
                        Lua.FunctionCallExpr(
                            stmt.tried.pos,
                            Lua.DotIndexExpr(
                                stmt.tried.pos,
                                Lua.Name(stmt.tried.pos, name("temper")),
                                Lua.Name(stmt.tried.pos, name("pcall")),
                            ),
                            Lua.Args(
                                stmt.tried.pos,
                                Lua.Exprs(
                                    stmt.tried.pos,
                                    when (luaClosureMode) {
                                        LuaClosureMode.BasicFunction -> listOf(functionExpr)
                                        else -> listOf(
                                            functionExpr,
                                            Lua.Name(stmt.tried.pos, objName),
                                        )
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        closure?.mutExterns?.forEach { name ->
            ret.add(
                Lua.SetStmt(
                    stmt.pos,
                    Lua.SetTargets(
                        stmt.pos,
                        listOf(
                            Lua.NameSetTarget(
                                stmt.pos,
                                Lua.Name(stmt.pos, name),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        stmt.pos,
                        listOf(
                            Lua.IndexExpr(
                                stmt.pos,
                                Lua.Name(stmt.pos, objName),
                                Lua.Num(stmt.pos, (closure.allExterns.indexOf(name) + 1).toDouble()),
                            ),
                        ),
                    ),
                ),
            )
        }
        val recover = translateStmt(stmt.recover)
        var ctrlFlowIf = listOf<Lua.Stmt>()
        if (usedLabels.contains("break")) {
            basicIfStmt(
                stmt.pos,
                Lua.BinaryExpr(
                    stmt.pos,
                    Lua.Name(stmt.pos, msg),
                    Lua.BinaryOp(
                        stmt.pos,
                        BinaryOpEnum.Eq,
                        LuaOperatorDefinition.Eq,
                    ),
                    Lua.Str(stmt.pos, "break"),
                ),
                translateStmt(TmpL.BreakStatement(stmt.pos, null)),
                ctrlFlowIf,
            )
        }
        if (usedLabels.contains("continue")) {
            basicIfStmt(
                stmt.pos,
                Lua.BinaryExpr(
                    stmt.pos,
                    Lua.Name(stmt.pos, msg),
                    Lua.BinaryOp(
                        stmt.pos,
                        BinaryOpEnum.Eq,
                        LuaOperatorDefinition.Eq,
                    ),
                    Lua.Str(stmt.pos, "continue"),
                ),
                translateStmt(TmpL.ContinueStatement(stmt.pos, null)),
                ctrlFlowIf,
            )
        }
        labels.forEach { (key, value) ->
            if (!usedLabels.contains(key)) {
                return@forEach
            }
            ctrlFlowIf = listOf(
                ifStmt(
                    stmt.pos,
                    Lua.BinaryExpr(
                        stmt.pos,
                        Lua.Name(stmt.pos, msg),
                        Lua.BinaryOp(
                            stmt.pos,
                            BinaryOpEnum.Eq,
                            LuaOperatorDefinition.Eq,
                        ),
                        Lua.Str(stmt.pos, key),
                    ),
                    listOf(),
                    if (value != 0) {
                        labelUsed.add(key)
                        Lua.ReturnStmt(
                            stmt.pos,
                            Lua.Exprs(
                                stmt.pos,
                                listOf(
                                    Lua.Str(stmt.pos, key),
                                    Lua.Str(stmt.pos, "flow"),
                                ),
                            ),
                        )
                    } else {
                        Lua.GotoStmt(
                            stmt.pos,
                            Lua.Name(stmt.pos, name(key)),
                        )
                    },
                    ctrlFlowIf,
                    null,
                ),
            )
        }
        if (ctrlFlowIf.isNotEmpty()) {
            ctrlFlowIf = listOf(
                basicIfStmt(
                    stmt.pos,
                    Lua.BinaryExpr(
                        stmt.pos,
                        Lua.Name(stmt.pos, msg),
                        Lua.BinaryOp(
                            stmt.pos,
                            BinaryOpEnum.Eq,
                            LuaOperatorDefinition.Eq,
                        ),
                        Lua.Name(stmt.pos, name("nil")),
                    ),
                    listOf(),
                    ctrlFlowIf,
                ),
            )
        }
        var chunk: List<Lua.Stmt> = listOf()
        if (usedLabels.contains("return")) {
            chunk = listOf(
                ifStmt(
                    stmt.pos,
                    Lua.BinaryExpr(
                        stmt.pos,
                        Lua.Name(stmt.pos, isbreak),
                        Lua.BinaryOp(
                            stmt.pos,
                            BinaryOpEnum.Eq,
                            LuaOperatorDefinition.Eq,
                        ),
                        Lua.Str(stmt.pos, "return"),
                    ),
                    listOf(),
                    Lua.ReturnStmt(
                        stmt.pos,
                        Lua.Exprs(
                            stmt.pos,
                            if (depth == 0) {
                                listOf(
                                    Lua.Name(stmt.pos, msg),
                                )
                            } else {
                                listOf(
                                    Lua.Name(stmt.pos, msg),
                                    Lua.Str(stmt.pos, "return"),
                                )
                            },
                        ),
                    ),
                    chunk,
                    null,
                ),
            )
        }
        ret.add(
            basicIfStmt(
                stmt.pos,
                Lua.Name(stmt.pos, ok),
                if (ctrlFlowIf.isEmpty()) {
                    chunk
                } else {
                    labelUsed.add("return")
                    listOf(
                        basicIfStmt(
                            stmt.pos,
                            Lua.BinaryExpr(
                                stmt.pos,
                                Lua.Name(stmt.pos, isbreak),
                                Lua.BinaryOp(
                                    stmt.pos,
                                    BinaryOpEnum.Eq,
                                    LuaOperatorDefinition.Eq,
                                ),
                                Lua.Str(stmt.pos, "flow"),
                            ),
                            ctrlFlowIf,
                            chunk,
                        ),
                    )
                },
                recover,
            ),
        )
        return ret
    }

    private fun translateStmt(
        stmt: TmpL.WhileStatement,
    ): List<Lua.Stmt> {
        breaks.add(null)
        continues.add(null)
        val first = Lua.WhileStmt(
            stmt.pos,
            translateExpr(stmt.test),
            luaChunk(
                stmt.body.pos,
                translateStmt(stmt.body),
                null,
            ),
        )
        val onContinue = continues.removeLastOrNull()
        val onBreak = breaks.removeLastOrNull()
        var before: Lua.Stmt? = null
        var after: Lua.Stmt? = null
        if (onContinue != null) {
            before = Lua.LabelStmt(stmt.pos, Lua.Name(stmt.pos, name(onContinue)))
        }
        if (onBreak != null) {
            after = Lua.LabelStmt(stmt.pos, Lua.Name(stmt.pos, name(onBreak)))
        }
        if (before == null && after == null) {
            return listOf(first)
        }
        return buildList {
            if (before != null) {
                add(before)
            }
            add(
                Lua.DoStmt(
                    stmt.pos,
                    luaChunk(
                        stmt.pos,
                        listOf(first),
                        null,
                    ),
                ),
            )
            if (after != null) {
                add(after)
            }
        }
    }

    private fun translateStmt(
        statement: TmpL.Statement,
    ): List<Lua.Stmt> = when (statement) {
        is TmpL.Assignment -> translateStmt(statement)
        is TmpL.BlockStatement -> translateStmt(statement)
        is TmpL.BoilerplateCodeFoldEnd -> translateStmt(statement)
        is TmpL.BoilerplateCodeFoldStart -> translateStmt(statement)
        is TmpL.BreakStatement -> translateStmt(statement)
        is TmpL.ContinueStatement -> translateStmt(statement)
        is TmpL.EmbeddedComment -> translateStmt(statement)
        is TmpL.ExpressionStatement -> translateStmt(statement)
        is TmpL.GarbageStatement -> translateStmt(statement)
        is TmpL.HandlerScope -> translateStmt(statement)
        is TmpL.IfStatement -> translateStmt(statement)
        is TmpL.LabeledStatement -> translateStmt(statement)
        is TmpL.LocalDeclaration -> translateStmt(statement)
        is TmpL.LocalFunctionDeclaration -> translateStmt(statement)
        is TmpL.ModuleInitFailed -> translateStmt(statement)
        is TmpL.YieldStatement -> translateStmt(statement)
        is TmpL.ReturnStatement -> translateStmt(statement)
        is TmpL.SetAbstractProperty -> translateStmt(statement)
        is TmpL.SetBackedProperty -> translateStmt(statement)
        is TmpL.ThrowStatement -> translateStmt(statement)
        is TmpL.TryStatement -> translateStmt(statement)
        is TmpL.WhileStatement -> translateStmt(statement)
        // Currently compute jumps are only used with coroutine strategy mode not
        // opted into by the support network.
        is TmpL.ComputedJumpStatement -> TODO()
    }

    private fun translateTopLevel(garbage: TmpL.GarbageTopLevel): List<Lua.Stmt> {
        // Includes the word garbage in the string representation.
        return listOf(Lua.Comment(garbage.pos, garbage.toString()))
    }

    private fun translateTopLevel(
        stmt: TmpL.ModuleInitBlock,
    ): List<Lua.Stmt> {
        val list = mutableListOf<Lua.Stmt>()
        stmt.body.statements.forEach {
            list.addAll(translateStmt(it))
        }
        return list
    }

    private fun handleSpecialParams(parameters: TmpL.Parameters): List<Lua.Stmt> {
        return buildList {
            handleNullDefaults(this, parameters.parameters)
            handleRest(this, parameters.restParameter)
        }
    }

    private fun makeNull(pos: Position): Lua.Expr =
        Lua.DotIndexExpr(pos, Lua.Name(pos, name("temper")), Lua.Name(pos, name("null")))

    private fun handleNullDefaults(stmts: MutableList<Lua.Stmt>, parameters: List<TmpL.Formal>) {
        for (param in parameters) {
            // If there's an explicit non-null default or if non-null type, no need to do anything.
            when (param.optionalState) {
                TriState.TRUE -> continue
                TriState.FALSE -> when {
                    canBeNull(param.type.ot) -> {}
                    else -> continue
                }
                else -> {}
            }
            // Otherwise add support for nil as if temper.null.
            val pos = param.pos
            val name = luaNames.name(param.name)
            // Use explicit `if` to avoid issues with boolean false for nullable booleans.
            Lua.IfStmt(
                pos,
                cond = Lua.BinaryExpr(
                    pos,
                    Lua.Name(pos, name),
                    Lua.BinaryOp(pos, BinaryOpEnum.Eq, LuaOperatorDefinition.Eq),
                    Lua.Name(pos, name("nil")),
                ),
                then = Lua.Chunk(
                    pos,
                    body = listOf(
                        Lua.SetStmt(
                            pos,
                            Lua.SetTargets(pos, listOf(Lua.NameSetTarget(pos, Lua.Name(pos, name)))),
                            Lua.Exprs(pos, listOf(makeNull(pos))),
                        ),
                    ),
                    last = null,
                ),
                elseIfs = listOf(),
                els = null,
            ).also { stmts.add(it) }
        }
    }

    private fun handleRest(stmts: MutableList<Lua.Stmt>, rest: TmpL.RestFormal?) {
        if (rest == null) {
            return
        }
        stmts.add(
            Lua.LocalStmt(
                rest.pos,
                Lua.SetTargets(
                    rest.pos,
                    listOf(
                        Lua.NameSetTarget(
                            rest.pos,
                            Lua.Name(rest.pos, luaNames.name(rest.name)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    rest.pos,
                    listOf(
                        Lua.FunctionCallExpr(
                            rest.pos,
                            Lua.DotIndexExpr(
                                rest.pos,
                                Lua.Name(rest.pos, name("temper")),
                                Lua.Name(rest.pos, name("listof")),
                            ),
                            Lua.Args(
                                rest.pos,
                                Lua.Exprs(
                                    rest.pos,
                                    listOf(
                                        Lua.RestExpr(rest.pos),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun translateTopLevel(
        stmt: TmpL.ModuleFunctionDeclaration,
    ): List<Lua.Stmt> {
        val name = luaNames.name(stmt.name)
        needsPreDecl.add(name)

        val pos = stmt.pos
        val params = Lua.Params(
            stmt.parameters.pos,
            translateParamDecl(stmt.parameters),
        )
        val body = luaChunk(
            stmt.body.pos,
            handleSpecialParams(stmt.parameters) + translateStmt(stmt.body),
            null,
        )
        var funcExpr: Lua.Expr = Lua.FunctionExpr(pos, params, body)
        if (stmt.mayYield) {
            // Make it a coroutine
            funcExpr = Lua.FunctionCallExpr(
                pos,
                Lua.DotIndexExpr(
                    pos,
                    Lua.Name(pos.leftEdge, name("temper")),
                    Lua.Name(pos.leftEdge, name("adapt_generator_fn")),
                ),
                Lua.Args(pos, Lua.Exprs(pos, listOf(funcExpr))),
            )
        }

        return listOf(
            Lua.SetStmt(
                pos,
                Lua.SetTargets(
                    pos.leftEdge,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.name.pos,
                            Lua.Name(stmt.name.pos, name),
                        ),
                    ),
                ),
                Lua.Exprs(
                    funcExpr.pos,
                    listOf(
                        wrapFunc(
                            funcExpr.pos,
                            "::${name.text}",
                            funcExpr,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun translateTopLevel(
        stmt: TmpL.ModuleLevelDeclaration,
    ): List<Lua.Stmt> {
        val name = luaNames.name(stmt.name)
        needsPreDecl.add(name)
        return listOf(
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.pos,
                            Lua.Name(stmt.pos, name),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.pos,
                    when (val expr = stmt.init) {
                        null -> listOf(
                            Lua.Name(stmt.pos, name("nil")),
                        )
                        else -> listOf(
                            translateExpr(expr),
                        )
                    },
                ),
            ),
        )
    }

    private fun translateTopLevel(
        stmt: TmpL.Test,
    ): List<Lua.Stmt> {
        val testName = "test_${stmt.name.name.rawDiagnostic}"
        dependenciesBuilder?.addTest(libraryName, stmt, "${testClassName.text}.$testName")
        return listOf(
            Lua.SetStmt(
                stmt.pos,
                testClassName.at(stmt.name.pos).dotSetTargets(testName),
                Lua.FunctionExpr(
                    stmt.pos,
                    Lua.Params(stmt.pos, listOf()),
                    Lua.Chunk(
                        // no need to simplify this block, so skip `luaChunk`
                        stmt.pos,
                        listOf(
                            "temper".asName(stmt.pos).dot("test").call(
                                listOf(
                                    stmt.rawName.asStr(stmt.name.pos),
                                    Lua.FunctionExpr(
                                        stmt.pos,
                                        Lua.Params(stmt.parameters.pos, translateParamDecl(stmt.parameters)),
                                        luaChunk(
                                            stmt.body.pos,
                                            translateStmt(stmt.body),
                                            null,
                                        ),
                                    ),
                                ),
                            ).asStmt(),
                        ),
                        null,
                    ),
                ).asExprs(),
            ),
        )
    }

    var depth = 0
    fun <Type> scoped(isFunction: Boolean, cb: () -> Type): Pair<Type, Set<String>> {
        val lastBreaks = breaks
        breaks = mutableListOf()
        val lastContinues = continues
        continues = mutableListOf()
        val lastDepth = depth
        if (isFunction) {
            depth = 0
        } else {
            depth += 1
        }
        val last = labelUsed
        val cur = mutableSetOf<String>()
        labelUsed = cur
        for (i in labels.keys) {
            labels[i] = labels[i]!! + 1
        }
        val ret = cb()
        for (i in labels.keys) {
            labels[i] = labels[i]!! - 1
        }
        labelUsed = last
        depth = lastDepth
        continues = lastContinues
        breaks = lastBreaks
        return Pair(ret, cur)
    }

    private fun translateTopLevel(
        stmt: TmpL.TypeConnection,
    ): List<Lua.Stmt> {
        return listOf(
            Lua.Comment(stmt.pos, "Type ${stmt.name.name} connected to ${stmt.to.typeName}"),
        )
    }

    private fun translateParamDecl(
        parameters: TmpL.Parameters,
    ): List<Lua.ParamOrRest> = buildList {
        parameters.parameters.forEach { formal ->
            add(
                Lua.Param(
                    formal.pos,
                    Lua.Name(formal.pos, luaNames.name(formal.name)),
                ),
            )
        }
        val rest = parameters.restParameter
        if (rest != null) {
            add(Lua.RestExpr(rest.pos))
        }
    }

    private fun wrapFunc(
        pos: Position,
        name: String,
        expr: Lua.Expr,
    ): Lua.Expr = if (shouldWrapFuncs) {
        Lua.FunctionCallExpr(
            pos,
            Lua.DotIndexExpr(
                pos,
                Lua.Name(pos, name("temper")),
                Lua.Name(pos, name("wrap_func")),
            ),
            Lua.Args(
                pos,
                Lua.Exprs(
                    pos,
                    listOf(
                        Lua.Str(pos, name),
                        expr,
                    ),
                ),
            ),
        )
    } else {
        expr
    }

    private fun translateMember(
        member: TmpL.MemberOrGarbage,
        typeName: () -> Lua.Name,
        defers: MutableList<Lua.Stmt>,
    ): List<Lua.Stmt> = when (member) {
        is TmpL.GarbageStatement -> TODO()
        is TmpL.Constructor -> listOf(
            Lua.SetStmt(
                member.pos,
                Lua.SetTargets(
                    member.pos,
                    listOf(
                        Lua.DotSetTarget(
                            member.pos,
                            Lua.NameSetTarget(member.pos, typeName()),
                            Lua.Name(member.pos, name("constructor")),
                        ),
                    ),
                ),
                Lua.Exprs(
                    member.pos,
                    listOf(
                        wrapFunc(
                            member.pos,
                            "${typeName().id.text}::constructor",
                            Lua.FunctionExpr(
                                member.pos,
                                Lua.Params(
                                    member.pos,
                                    translateParamDecl(member.parameters),
                                ),
                                luaChunk(
                                    member.body.pos,
                                    scoped(true) {
                                        buildList {
                                            addAll(handleSpecialParams(member.parameters))
                                            addAll(translateStmt(member.body))
                                        }
                                    }.first,
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        is TmpL.Getter -> listOf(
            Lua.SetStmt(
                member.pos,
                Lua.SetTargets(
                    member.pos,
                    listOf(
                        Lua.DotSetTarget(
                            member.pos,
                            Lua.DotSetTarget(
                                member.pos,
                                Lua.NameSetTarget(member.pos, typeName()),
                                Lua.Name(member.pos, name("get")),
                            ),
                            Lua.Name(member.pos, safeName(member.dotName.dotNameText)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    member.pos,
                    listOf(
                        wrapFunc(
                            member.pos,
                            "${typeName().id.text}::get(${member.dotName.dotNameText})",
                            Lua.FunctionExpr(
                                member.pos,
                                Lua.Params(
                                    member.pos,
                                    member.parameters.parameters.map { formal ->
                                        Lua.Param(
                                            formal.pos,
                                            Lua.Name(formal.pos, luaNames.name(formal.name)),
                                        )
                                    },
                                ),
                                luaChunk(
                                    member.pos,
                                    when (val body = member.body) {
                                        null -> listOf()
                                        else -> handleSpecialParams(member.parameters) + translateStmt(body)
                                    },
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        is TmpL.Setter -> listOf(
            Lua.SetStmt(
                member.pos,
                Lua.SetTargets(
                    member.pos,
                    listOf(
                        Lua.DotSetTarget(
                            member.pos,
                            Lua.DotSetTarget(
                                member.pos,
                                Lua.NameSetTarget(member.pos, typeName()),
                                Lua.Name(member.pos, name("set")),
                            ),
                            Lua.Name(member.pos, safeName(member.dotName.dotNameText)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    member.pos,
                    listOf(
                        wrapFunc(
                            member.pos,
                            "${typeName().id.text}::get(${member.dotName.dotNameText})",
                            Lua.FunctionExpr(
                                member.pos,
                                Lua.Params(
                                    member.pos,
                                    translateParamDecl(member.parameters),
                                ),
                                luaChunk(
                                    member.pos,
                                    when (val body = member.body) {
                                        null -> listOf()
                                        else -> handleSpecialParams(member.parameters) + translateStmt(body)
                                    },
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        is TmpL.NormalMethod -> listOf(
            Lua.SetStmt(
                member.pos,
                Lua.SetTargets(
                    member.pos,
                    listOf(
                        Lua.DotSetTarget(
                            member.pos,
                            Lua.DotSetTarget(
                                member.pos,
                                Lua.NameSetTarget(member.pos, typeName()),
                                Lua.Name(member.pos, name("methods")),
                            ),
                            Lua.Name(member.pos, safeName(member.dotName.dotNameText)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    member.pos,
                    listOf(
                        wrapFunc(
                            member.pos,
                            "${typeName().id.text}::${member.dotName.dotNameText}",
                            Lua.FunctionExpr(
                                member.pos,
                                Lua.Params(
                                    member.pos,
                                    translateParamDecl(member.parameters),
                                ),
                                luaChunk(
                                    member.pos,
                                    when (val body = member.body) {
                                        null -> listOf()
                                        else -> handleSpecialParams(member.parameters) + translateStmt(body)
                                    },
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        is TmpL.InstanceProperty -> listOf()
        is TmpL.StaticMember -> {
            val (initial, deferred) = when (member) {
                // In case of reference to class members, defer all static property assignments.
                is TmpL.StaticProperty -> translateExpr(member.expression) to true
                is TmpL.StaticMethod -> {
                    // We don't use member.name here.
                    // See https://github.com/temperlang/temper/issues/1
                    Lua.FunctionExpr(
                        member.pos,
                        Lua.Params(
                            member.parameters.pos,
                            translateParamDecl(member.parameters),
                        ),
                        luaChunk(
                            member.pos,
                            handleSpecialParams(member.parameters) +
                                (member.body?.let { translateStmt(it) } ?: emptyList()),
                            null,
                        ),
                    ) to false
                }
            }
            val assignment = Lua.SetStmt(
                member.pos,
                Lua.SetTargets(
                    member.pos,
                    listOf(
                        Lua.DotSetTarget(
                            member.pos,
                            Lua.NameSetTarget(
                                member.pos,
                                typeName(),
                            ),
                            Lua.Name(member.pos, safeName(member.dotName.dotNameText)),
                        ),
                    ),
                ),
                Lua.Exprs(
                    member.pos,
                    listOf(
                        initial,
                    ),
                ),
            )
            when {
                deferred -> {
                    defers.add(assignment)
                    listOf()
                }
                else -> listOf(assignment)
            }
        }
    }

    private fun translateTopLevel(
        stmt: TmpL.TypeDeclaration,
    ): List<Lua.Stmt> {
        val ret = mutableListOf<Lua.Stmt>()
        val name = luaNames.name(stmt.typeShape.name)
        val typeName = {
            Lua.Name(stmt.name.pos, name)
        }
        needsPreDecl.add(name)
        ret.add(
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.name.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.name.pos,
                            typeName(),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.pos,
                    listOf(
                        Lua.FunctionCallExpr(
                            stmt.pos,
                            Lua.DotIndexExpr(
                                stmt.pos,
                                Lua.Name(stmt.pos, name("temper")),
                                Lua.Name(stmt.pos, name("type")),
                            ),
                            Lua.Args(
                                stmt.pos,
                                Lua.Exprs(
                                    stmt.pos,
                                    buildList {
                                        add(Lua.Str(stmt.pos, luaNames.name(stmt.typeShape.name).text))
                                        stmt.superTypes.forEach { superType ->
                                            add(
                                                Lua.Name(
                                                    superType.pos,
                                                    luaNames.name(
                                                        when (val superTypeName = superType.typeName) {
                                                            is TmpL.ConnectedToTypeName -> TODO()
                                                            is TmpL.TemperTypeName ->
                                                                superTypeName.typeDefinition.name
                                                        },
                                                    ),
                                                ),
                                            )
                                        }
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val defers = mutableListOf<Lua.Stmt>()
        stmt.members.forEach { member ->
            val stmts = translateMember(member, typeName, defers)
            ret.addAll(stmts)
        }
        ret.addAll(defers)
        return ret
    }

    fun translateTopLevel(
        topLevel: TmpL.TopLevel,
    ): List<Lua.Stmt> = when (topLevel) {
        is TmpL.BoilerplateCodeFoldEnd -> TODO("BoilerplateCodeFoldEnd")
        is TmpL.BoilerplateCodeFoldStart -> TODO("BoilerplateCodeFoldStart")
        is TmpL.EmbeddedComment -> TODO("EmbeddedComment")
        is TmpL.GarbageTopLevel -> translateTopLevel(topLevel)
        is TmpL.ModuleInitBlock -> translateTopLevel(topLevel)
        is TmpL.ModuleFunctionDeclaration -> translateTopLevel(topLevel)
        is TmpL.ModuleLevelDeclaration -> translateTopLevel(topLevel)
        is TmpL.PooledValueDeclaration -> TODO("PooledValueDeclaration")
        is TmpL.SupportCodeDeclaration -> TODO("SupportCodeDeclaration")
        is TmpL.Test -> translateTopLevel(topLevel)
        is TmpL.TypeConnection -> translateTopLevel(topLevel)
        is TmpL.TypeDeclaration -> translateTopLevel(topLevel)
    }

    private fun translateLibraryName(path: TmpL.ModulePath): String {
        val superPath = FilePathSegment(path.libraryName.text)
        val subPath = path.to.relativePath().withTemperAwareExtension("").segments
        return when (val ret = FilePath(listOf(superPath) + subPath, isDir = false).join()) {
            "work/regex" -> "std/regex"
            else -> ret
        }
    }

    fun init(mod: TmpL.Module) {
        libraryName = mod.libraryName
        importedNames.add(name("temper"))
        val pos = mod.pos
        temperStmt = buildLocalRequire(pos, name("temper"), "temper-core")
        exports.add(
            Lua.LocalStmt(
                pos,
                Lua.SetTargets(
                    pos,
                    listOf(
                        Lua.NameSetTarget(
                            pos,
                            Lua.Name(pos, name("exports")),
                        ),
                    ),
                ),
                Lua.Exprs(
                    pos,
                    listOf(
                        Lua.TableExpr(
                            pos,
                            listOf(),
                        ),
                    ),
                ),
            ),
        )
        mod.imports.forEach { tmplImport ->
            val path = tmplImport.path ?: return@forEach
            val moduleName = translateLibraryName(path)
            if (moduleName == "std/testing") {
                return@forEach
            }
            val localName = tmplImport.localName
            val externalLuaName by lazy { luaNames.name(tmplImport.externalName) }
            if (localName != null) {
                luaNames.alias(localName.name, externalLuaName)
                val localLuaName = luaNames.name(localName)
                addImport(pos, localLuaName, moduleName, externalLuaName)
            } else {
                luaNames.importAsNeeded(tmplImport.externalName.name) { localLuaName ->
                    addImport(pos, localLuaName, moduleName, externalLuaName)
                }
            }
        }
        needsPreDecl = mutableSetOf()
    }

    fun addImport(
        pos: Position,
        localLuaName: LuaName,
        moduleName: String,
        externalLuaName: LuaName = localLuaName,
    ) {
        importedNames.add(localLuaName)
        imports.add(
            Lua.LocalStmt(
                pos,
                Lua.SetTargets(
                    pos,
                    listOf(
                        Lua.NameSetTarget(
                            pos,
                            Lua.Name(pos, localLuaName),
                        ),
                    ),
                ),
                Lua.Exprs(
                    pos,
                    listOf(
                        Lua.FunctionCallExpr(
                            pos,
                            Lua.DotIndexExpr(
                                pos,
                                Lua.Name(pos, name("temper")),
                                Lua.Name(pos, name("import")),
                            ),
                            Lua.Args(
                                pos,
                                Lua.Exprs(
                                    pos,
                                    listOf(
                                        Lua.Str(pos, moduleName),
                                        Lua.Str(pos, externalLuaName.text),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    fun finish(
        mod: TmpL.Module,
        ensuredExported: Set<LuaName> = setOf(),
        nameSuffix: String = "",
        pathPrefix: FilePath = dirPath(),
    ): Backend.TranslatedFileSpecification {
        // TODO Filter out unused imports?
        val pos = mod.pos
        if (dependencyCategory == DependencyCategory.Test) {
            addLuaunitTestingStructure(pos)
        }
        if (needsPreDecl.isNotEmpty()) {
            preDecls.add(
                Lua.LocalDeclStmt(
                    pos,
                    Lua.SetTargets(
                        pos,
                        needsPreDecl.map { name ->
                            Lua.NameSetTarget(
                                pos,
                                Lua.Name(pos, name),
                            )
                        },
                    ),
                ),
            )
        }
        mod.topLevels.forEach topLevels@{ topLevel ->
            topLevel.dependencyCategory() == dependencyCategory || return@topLevels
            val varName = (topLevel as? TmpL.Declaration)?.name ?: return@topLevels
            if (varName.name is ExportedName) {
                val luaName = luaNames.name(varName)
                addExport(varName.pos, luaName)
            }
        }
        for (name in ensuredExported) {
            if (name !in exportedNames) {
                addExport(pos, name)
            }
        }
        val withLocals = luaChunk(
            pos,
            buildList {
                addAll(imports)
                addAll(preDecls)
                addAll(globalFuncs)
                addAll(topLevels)
                addAll(exports)
            },
            Lua.ReturnStmt(
                pos,
                Lua.Exprs(
                    pos,
                    listOf(
                        Lua.Name(pos, name("exports")),
                    ),
                ),
            ),
        )
        val withLocalTables = LuaLocalRemover().scan(
            Lua.DoStmt(
                pos,
                withLocals,
            ),
        )
        return Backend.TranslatedFileSpecification(
            pathPrefix.resolve(pathForModule(mod.codeLocation.codeLocation, nameSuffix = nameSuffix)),
            MimeType.luaSource,
            luaChunk(
                pos,
                listOf(
                    temperStmt,
                    withLocalTables!!,
                ),
                null,
            ),
        )
    }

    private fun addExport(
        pos: Position,
        luaName: LuaName,
    ) {
        exportedNames.add(luaName)
        exports.add(Lua.SetStmt(pos, "exports".asName(pos).dotSet(luaName).asSetTargets(), luaName.at(pos).asExprs()))
    }

    private fun addLuaunitTestingStructure(pos: Position) {
        val unpack = luaNames.gensym()
        // For luajit: local unpack_ = unpack or table.unpack
        imports.add(
            Lua.LocalStmt(
                pos,
                unpack.asSetTargets(pos),
                Lua.BinaryExpr(
                    pos,
                    name("unpack").at(pos),
                    Lua.BinaryOp(pos, BinaryOpEnum.BoolOr, LuaOperatorDefinition.Or),
                    name("table").at(pos).dot("unpack"),
                ).asExprs(),
            ),
        )
        val luaunit = luaNames.gensym()
        imports.add(buildLocalRequire(pos, luaunit, "luaunit"))
        // Not really imports, but this gets them before topLevels.
        // lu.FAILURE_PREFIX = temper.test_failure_prefix
        imports.add(
            Lua.SetStmt(
                pos,
                luaunit.at(pos).dotSetTargets("FAILURE_PREFIX"),
                name("temper").at(pos).dot("test_failure_prefix").asExprs(),
            ),
        )
        // Test_ = {} -- global on purpose
        imports.add(Lua.SetStmt(pos, testClassName.asSetTargets(pos), Lua.TableExpr(pos, listOf()).asExprs()))
        // And not really exports, but this goes after topLevels.
        // lu.LuaUnit.run(unpack_({"--pattern", "^Test_%.", unpack_(arg)}))
        exports.add(
            luaunit.at(pos).dot("LuaUnit").dot("run").call(
                unpack.at(pos).call(
                    buildTableList(
                        pos,
                        listOf(
                            "--pattern".asStr(pos),
                            "^Test_%.".asStr(pos),
                            unpack.at(pos).call(listOf("arg".asName(pos))),
                        ),
                    ),
                ),
            ).asStmt(),
        )
    }
}

private fun buildPublicExports(
    internalName: String,
    luaNames: LuaNames,
    mod: TmpL.Module,
    topLevels: List<TmpL.TopLevel>,
): Backend.TranslatedFileSpecification {
    val pos = mod.pos
    val importsName = name("imports")
    val importStmt = buildLocalRequire(pos, importsName, internalName)
    val exportStmt = Lua.ReturnStmt(
        pos,
        Lua.Exprs(
            pos,
            listOf(
                Lua.TableExpr(
                    pos,
                    topLevels.mapNotNull tops@{ top ->
                        val name = (top as? TmpL.Declaration)?.name ?: return@tops null
                        name.name is ExportedName || return@tops null
                        val luaName = luaNames.name(top.name)
                        Lua.NamedTableEntry(
                            pos,
                            Lua.Name(pos, luaName),
                            Lua.DotIndexExpr(pos, Lua.Name(pos, importsName), Lua.Name(pos, luaName)),
                        )
                    },
                ),
            ),
        ),
    )
    return Backend.TranslatedFileSpecification(
        pathForModule(mod.codeLocation.codeLocation),
        MimeType.luaSource,
        luaChunk(pos, listOf(importStmt), exportStmt),
    )
}
