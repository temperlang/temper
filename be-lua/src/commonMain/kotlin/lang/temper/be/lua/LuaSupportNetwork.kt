package lang.temper.be.lua

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.Name
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.getStaticBuiltinName
import lang.temper.value.internalGetStaticBuiltinName

internal fun operatorToName(
    op: BuiltinOperatorId?,
): String = when (op) {
    BuiltinOperatorId.BooleanNegation -> "bool_not"
    BuiltinOperatorId.BitwiseAnd32, BuiltinOperatorId.BitwiseAnd64 -> "band"
    BuiltinOperatorId.BitwiseOr32, BuiltinOperatorId.BitwiseOr64 -> "bor"
    BuiltinOperatorId.BitwiseNegation32, BuiltinOperatorId.BitwiseNegation64 -> "bnot"
    BuiltinOperatorId.BitwiseXor32, BuiltinOperatorId.BitwiseXor64 -> "bxor"
    BuiltinOperatorId.BitwiseShl32 -> "shl32"
    BuiltinOperatorId.BitwiseShl64 -> "shl64"
    BuiltinOperatorId.BitwiseShr32 -> "shr32"
    BuiltinOperatorId.BitwiseShr64 -> "shr64"
    BuiltinOperatorId.BitwiseShrUnsigned32 -> "ushr32"
    BuiltinOperatorId.BitwiseShrUnsigned64 -> "ushr64"
    BuiltinOperatorId.IsNull -> "is_null"
    BuiltinOperatorId.NotNull -> "not_null"
    BuiltinOperatorId.DivFltFlt -> "fdiv"
    BuiltinOperatorId.DivIntInt -> "int32_div"
    BuiltinOperatorId.DivIntInt64 -> "int64_div"
    BuiltinOperatorId.DivIntIntSafe -> "int32_div"
    BuiltinOperatorId.DivIntInt64Safe -> "int64_div" // TODO Just use `x/y` because either standard int64 or metatabled?
    BuiltinOperatorId.ModFltFlt -> "fmod"
    BuiltinOperatorId.ModIntInt -> "int32_mod"
    BuiltinOperatorId.ModIntInt64 -> "int64_mod"
    BuiltinOperatorId.ModIntIntSafe -> "int32_mod"
    BuiltinOperatorId.ModIntInt64Safe -> "int64_mod"
    BuiltinOperatorId.MinusFlt -> "unm"
    BuiltinOperatorId.MinusFltFlt -> "sub"
    BuiltinOperatorId.MinusInt -> "int32_unm"
    BuiltinOperatorId.MinusInt64 -> "int64_unm" // TODO Just use `-x` because either standard int64 or metatabled?
    BuiltinOperatorId.MinusIntInt -> "int32_sub"
    BuiltinOperatorId.MinusIntInt64 -> "int64_sub"
    BuiltinOperatorId.PlusFltFlt -> "add"
    BuiltinOperatorId.PlusIntInt -> "int32_add"
    BuiltinOperatorId.PlusIntInt64 -> "int64_add"
    BuiltinOperatorId.PowFltFlt -> "pow"
    BuiltinOperatorId.TimesIntInt -> "int32_mul"
    BuiltinOperatorId.TimesIntInt64 -> "int64_mul"
    BuiltinOperatorId.TimesFltFlt -> "mul"
    BuiltinOperatorId.LtFltFlt -> "float_lt"
    BuiltinOperatorId.LtIntInt -> "generic_lt"
    BuiltinOperatorId.LtStrStr -> "str_lt"
    BuiltinOperatorId.LtGeneric -> "generic_lt"
    BuiltinOperatorId.LeFltFlt -> "float_le"
    BuiltinOperatorId.LeIntInt -> "generic_le"
    BuiltinOperatorId.LeStrStr -> "str_le"
    BuiltinOperatorId.LeGeneric -> "generic_le"
    BuiltinOperatorId.GtFltFlt -> "float_gt"
    BuiltinOperatorId.GtIntInt -> "generic_gt"
    BuiltinOperatorId.GtStrStr -> "str_gt"
    BuiltinOperatorId.GtGeneric -> "generic_gt"
    BuiltinOperatorId.GeFltFlt -> "float_ge"
    BuiltinOperatorId.GeIntInt -> "generic_ge"
    BuiltinOperatorId.GeStrStr -> "str_ge"
    BuiltinOperatorId.GeGeneric -> "generic_ge"
    BuiltinOperatorId.EqFltFlt -> "float_eq"
    BuiltinOperatorId.EqIntInt -> "generic_eq"
    BuiltinOperatorId.EqStrStr -> "str_eq"
    BuiltinOperatorId.EqGeneric -> "generic_eq"
    BuiltinOperatorId.NeFltFlt -> "float_ne"
    BuiltinOperatorId.NeIntInt -> "generic_ne"
    BuiltinOperatorId.NeStrStr -> "str_ne"
    BuiltinOperatorId.NeGeneric -> "generic_ne"
    BuiltinOperatorId.CmpFltFlt -> "float_cmp"
    BuiltinOperatorId.CmpIntInt -> "int_cmp"
    BuiltinOperatorId.CmpStrStr -> "str_cmp"
    BuiltinOperatorId.CmpGeneric -> "generic_cmp"
    BuiltinOperatorId.Bubble, BuiltinOperatorId.Panic -> "bubble"
    BuiltinOperatorId.Print -> "print"
    BuiltinOperatorId.StrCat -> "concat"
    BuiltinOperatorId.Listify -> "listof"
    BuiltinOperatorId.Async -> "async"
    // should not be used with CoroutineStrategy.TranslateToGenerator
    BuiltinOperatorId.AdaptGeneratorFn,
    BuiltinOperatorId.SafeAdaptGeneratorFn,
    -> "null_op"
    null -> "null_op"
}

internal object LuaSupportNetwork : SupportNetwork {
    override val backendDescription: String
        get() = "Lua Backend"
    override val bubbleStrategy: BubbleBranchStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy: CoroutineStrategy = CoroutineStrategy.TranslateToGenerator
    override val functionTypeStrategy: FunctionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override fun representationOfVoid(
        genre: Genre,
    ): RepresentationOfVoid = RepresentationOfVoid.ReifyVoid

    @Suppress("NAME_SHADOWING") // pos
    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode? {
        return when {
            (
                builtin.name == getStaticBuiltinName.builtinKey ||
                    builtin.name == internalGetStaticBuiltinName.builtinKey
                ) -> InlineLua("get-static") { _, args ->
                args[0]
            }
            (builtin == BuiltinFuns.pureVirtualFn) -> InlineLua("pure-virtual") { pos, args ->
                Lua.FunctionCallExpr(
                    pos,
                    Lua.DotIndexExpr(
                        pos,
                        Lua.Name(pos, name("temper")),
                        Lua.Name(pos, name("virtual")),
                    ),
                    Lua.Args(
                        pos,
                        Lua.Exprs(pos, args),
                    ),
                )
            }
            (builtin.builtinOperatorId == null) -> null
            else -> when (builtin.builtinOperatorId) {
                BuiltinOperatorId.NotNull -> null
                BuiltinOperatorId.BooleanNegation -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.UnaryExpr(
                        pos,
                        Lua.UnaryOp(
                            pos.leftEdge,
                            UnaryOpEnum.BoolNot,
                            LuaOperatorDefinition.Not,
                        ),
                        args[0],
                    )
                }
                BuiltinOperatorId.PlusIntInt64 -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Add,
                            LuaOperatorDefinition.Add,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.MinusIntInt64 -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Sub,
                            LuaOperatorDefinition.Sub,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.TimesIntInt64 -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Mul,
                            LuaOperatorDefinition.Mul,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.EqIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Eq,
                            LuaOperatorDefinition.Eq,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.NeIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.NotEq,
                            LuaOperatorDefinition.Ne,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.LtIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Lt,
                            LuaOperatorDefinition.Lt,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.LeIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.LtEq,
                            LuaOperatorDefinition.Le,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.GtIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.Gt,
                            LuaOperatorDefinition.Gt,
                        ),
                        args[1],
                    )
                }
                BuiltinOperatorId.GeIntInt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos,
                        args[0],
                        Lua.BinaryOp(
                            pos,
                            BinaryOpEnum.GtEq,
                            LuaOperatorDefinition.Ge,
                        ),
                        args[1],
                    )
                }
                // Inline float arithmetic as native Lua operators (Lua numbers are doubles).
                BuiltinOperatorId.PlusFltFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos, args[0],
                        Lua.BinaryOp(pos, BinaryOpEnum.Add, LuaOperatorDefinition.Add),
                        args[1],
                    )
                }
                BuiltinOperatorId.MinusFltFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos, args[0],
                        Lua.BinaryOp(pos, BinaryOpEnum.Sub, LuaOperatorDefinition.Sub),
                        args[1],
                    )
                }
                BuiltinOperatorId.TimesFltFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos, args[0],
                        Lua.BinaryOp(pos, BinaryOpEnum.Mul, LuaOperatorDefinition.Mul),
                        args[1],
                    )
                }
                BuiltinOperatorId.DivFltFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos, args[0],
                        Lua.BinaryOp(pos, BinaryOpEnum.Div, LuaOperatorDefinition.Div),
                        args[1],
                    )
                }
                BuiltinOperatorId.PowFltFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.BinaryExpr(
                        pos, args[0],
                        Lua.BinaryOp(pos, BinaryOpEnum.Pow, LuaOperatorDefinition.Pow),
                        args[1],
                    )
                }
                BuiltinOperatorId.MinusFlt -> InlineLua(
                    builtin.builtinOperatorId.toString(),
                    builtin.builtinOperatorId,
                ) { pos, args ->
                    Lua.UnaryExpr(
                        pos,
                        Lua.UnaryOp(pos.leftEdge, UnaryOpEnum.UnaryAdd, LuaOperatorDefinition.Unm),
                        args[0],
                    )
                }
                // Inline string comparisons as native Lua operators (lexicographic, same as Temper).
                BuiltinOperatorId.EqStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.Eq, LuaOperatorDefinition.Eq,
                    builtin.builtinOperatorId,
                )
                BuiltinOperatorId.NeStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.NotEq, LuaOperatorDefinition.Ne,
                    builtin.builtinOperatorId,
                )
                BuiltinOperatorId.LtStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.Lt, LuaOperatorDefinition.Lt,
                    builtin.builtinOperatorId,
                )
                BuiltinOperatorId.LeStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.LtEq, LuaOperatorDefinition.Le,
                    builtin.builtinOperatorId,
                )
                BuiltinOperatorId.GtStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.Gt, LuaOperatorDefinition.Gt,
                    builtin.builtinOperatorId,
                )
                BuiltinOperatorId.GeStrStr -> inlineBinaryOp(
                    builtin.builtinOperatorId.toString(), BinaryOpEnum.GtEq, LuaOperatorDefinition.Ge,
                    builtin.builtinOperatorId,
                )
                else -> InlineLua(builtin.builtinOperatorId.toString(), builtin.builtinOperatorId) { pos, args ->
                    Lua.FunctionCallExpr(
                        pos,
                        Lua.DotIndexExpr(
                            pos,
                            Lua.Name(pos, name("temper")),
                            Lua.Name(pos, name(operatorToName(builtin.builtinOperatorId))),
                        ),
                        Lua.Args(
                            pos,
                            Lua.Exprs(pos, args),
                        ),
                    )
                }
            }
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    @Suppress("NAME_SHADOWING") // pos
    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? = when (connectedKey) {
        "core.getConsole()" -> InlineLua(connectedKey) { pos, _ ->
            Lua.Num(pos, 0.0)
        }
        "core.type Console.log()" -> InlineLua(connectedKey) { pos, args ->
            Lua.FunctionCallExpr(
                pos,
                Lua.DotIndexExpr(
                    pos,
                    Lua.Name(pos, name("temper")),
                    Lua.Name(pos, name("log")),
                ),
                Lua.Args(
                    pos,
                    Lua.Exprs(pos, args.takeLast(1)),
                ),
            )
        }
        "core.type String.begin" -> InlineLua(connectedKey) { pos, _ ->
            // Strings in Lua are one-indexed
            Lua.Num(pos, 1.0)
        }
        "core.type StringIndex.none" -> InlineLua(connectedKey) { pos, _ ->
            // -1 is out of band
            Lua.Num(pos, -1.0)
        }
        "core.type Generator.next()",
        "core.type SafeGenerator.next()",
        -> InlineLua(connectedKey) { pos, args ->
            require(args.size == 1)
            Lua.FunctionCallExpr(
                pos,
                Lua.DotIndexExpr(
                    pos.leftEdge,
                    Lua.Name(pos.leftEdge, name("temper")),
                    Lua.Name(pos.leftEdge, name("generator_next")),
                ),
                Lua.Args(pos, Lua.Exprs(pos, args)),
            )
        }

        // core.type Int32.toFloat64() is identity in Lua (all numbers are doubles).
        "core.type Int32.toFloat64()" -> inlineIdentity(connectedKey)

        // Math functions → Lua's math.* standard library (available in 5.1+).
        "core.type Float64.abs()" -> inlineGlobalCall(connectedKey, "math", "abs")
        "core.type Float64.ceil()" -> inlineGlobalCall(connectedKey, "math", "ceil")
        "core.type Float64.floor()" -> inlineGlobalCall(connectedKey, "math", "floor")
        "core.type Float64.sqrt()" -> inlineGlobalCall(connectedKey, "math", "sqrt")
        "core.type Float64.sin()" -> inlineGlobalCall(connectedKey, "math", "sin")
        "core.type Float64.cos()" -> inlineGlobalCall(connectedKey, "math", "cos")
        "core.type Float64.tan()" -> inlineGlobalCall(connectedKey, "math", "tan")
        "core.type Float64.asin()" -> inlineGlobalCall(connectedKey, "math", "asin")
        "core.type Float64.acos()" -> inlineGlobalCall(connectedKey, "math", "acos")
        "core.type Float64.atan()" -> inlineGlobalCall(connectedKey, "math", "atan")
        "core.type Float64.exp()" -> inlineGlobalCall(connectedKey, "math", "exp")
        "core.type Float64.log()" -> inlineGlobalCall(connectedKey, "math", "log")
        // core.type Float64.max()/min() have NaN propagation semantics; cannot use math.max/min directly.
        "core.type Int32.max()" -> inlineGlobalCall(connectedKey, "math", "max")
        "core.type Int32.min()" -> inlineGlobalCall(connectedKey, "math", "min")

        // Math constants.
        "core.type Float64.pi" -> inlineGlobalProp(connectedKey, "math", "pi")
        "core.type Float64.infinity()" -> inlineGlobalProp(connectedKey, "math", "huge")

        // core.type String.get isEmpty() → #str == 0 (byte-length check, correct for empty strings).
        "core.type String.get isEmpty()" -> InlineLua(connectedKey) { pos, args ->
            Lua.BinaryExpr(
                pos,
                Lua.UnaryExpr(
                    pos,
                    Lua.UnaryOp(pos, UnaryOpEnum.UnarySub, LuaOperatorDefinition.Unm),
                    args[0],
                ),
                Lua.BinaryOp(pos, BinaryOpEnum.Eq, LuaOperatorDefinition.Eq),
                Lua.Num(pos, 0.0),
            )
        }

        // core.type List.isEmpty() → #list == 0.
        "core.type List.isEmpty()",
        "core.type Listed.isEmpty()",
        -> InlineLua(connectedKey) { pos, args ->
            Lua.BinaryExpr(
                pos,
                Lua.UnaryExpr(
                    pos,
                    Lua.UnaryOp(pos, UnaryOpEnum.UnarySub, LuaOperatorDefinition.Unm),
                    args[0],
                ),
                Lua.BinaryOp(pos, BinaryOpEnum.Eq, LuaOperatorDefinition.Eq),
                Lua.Num(pos, 0.0),
            )
        }

        // core.type Boolean.toString() → tostring(). (core.type Int32.toString() has a radix param, can't inline.)
        "core.type Boolean.toString()" -> inlineBuiltinCall(connectedKey, "tostring")

        // StringIndex comparisons → native Lua operators (they're just integers).
        "core.type StringIndexOption.compareTo()" -> InlineLua(connectedKey) { pos, args ->
            Lua.BinaryExpr(pos, args[0], Lua.BinaryOp(pos, BinaryOpEnum.Sub, LuaOperatorDefinition.Sub), args[1])
        }
        "core.type StringIndexOption.compareTo()::eq" -> inlineBinaryOp(connectedKey, BinaryOpEnum.Eq, LuaOperatorDefinition.Eq)
        "core.type StringIndexOption.compareTo()::ne" -> inlineBinaryOp(connectedKey, BinaryOpEnum.NotEq, LuaOperatorDefinition.Ne)
        "core.type StringIndexOption.compareTo()::lt" -> inlineBinaryOp(connectedKey, BinaryOpEnum.Lt, LuaOperatorDefinition.Lt)
        "core.type StringIndexOption.compareTo()::le" -> inlineBinaryOp(connectedKey, BinaryOpEnum.LtEq, LuaOperatorDefinition.Le)
        "core.type StringIndexOption.compareTo()::gt" -> inlineBinaryOp(connectedKey, BinaryOpEnum.Gt, LuaOperatorDefinition.Gt)
        "core.type StringIndexOption.compareTo()::ge" -> inlineBinaryOp(connectedKey, BinaryOpEnum.GtEq, LuaOperatorDefinition.Ge)

        else -> temperMethod(
            connectedKey,
            connectedKey
                .replace("::", "_")
                .lowercase(),
        )
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? {
        return null
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        val (name, methodSuffix) = when (targetType.typeName.sourceDefinition) {
            WellKnownTypes.stringIndexTypeDefinition -> "StringIndex" to "string_index"
            WellKnownTypes.noStringIndexTypeDefinition -> "NoStringIndex" to "no_string_index"
            else ->
                return super.translateRuntimeTypeOperation(pos, rto, sourceType, targetType)
        }
        val prefix = when (rto) {
            RuntimeTypeOperation.As, RuntimeTypeOperation.AssertAs -> "require_"
            RuntimeTypeOperation.Is -> "is_"
        }
        return temperMethod(
            "${rto.name}$name",
            "$prefix$methodSuffix",
        )
    }
}

internal data class InlineLua(
    val name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val factory: (pos: Position, arguments: List<Lua.Expr>) -> Lua.Tree,
) : InlineSupportCode<Lua.Tree, LuaTranslator>, NamedSupportCode {

    override val baseName: ParsedName
        get() = ParsedName(name)

    override val needsThisEquivalent: Boolean = false

    override fun equals(other: Any?): Boolean =
        this === other || (other is InlineLua && name == other.name)
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = "LuaSupportCode($baseName)"

    override fun renderTo(tokenSink: TokenSink) =
        tokenSink.name(baseName as Name, inOperatorPosition = false)

    fun callFactory(
        pos: Position,
        args: List<Lua.Expr>,
    ): Lua.Tree = factory(pos, args)

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Lua.Tree>>,
        returnType: Type2,
        translator: LuaTranslator,
    ): Lua.Tree = factory(pos, arguments.map { it.expr as Lua.Expr })
}

internal fun temperMethod(
    name: String,
    temperMethodName: String,
) = InlineLua(
    name,
    factory = { pos, args ->
        Lua.FunctionCallExpr(
            pos,
            Lua.DotIndexExpr(
                pos,
                Lua.Name(pos.leftEdge, name("temper")),
                Lua.Name(
                    pos,
                    name(
                        temperMethodName,
                    ),
                ),
            ),
            Lua.Args(
                pos.rightEdge,
                Lua.Exprs(pos.rightEdge, args),
            ),
        )
    },
)

// --- Inline helper factories for systematic operator/method inlining ---

/** Inline a binary operator: `args[0] op args[1]` */
private fun inlineBinaryOp(
    key: String,
    op: BinaryOpEnum,
    opDef: LuaOperatorDefinition,
    builtinOp: BuiltinOperatorId? = null,
) = InlineLua(key, builtinOp) { pos, args ->
    Lua.BinaryExpr(pos, args[0], Lua.BinaryOp(pos, op, opDef), args[1])
}

/** Inline a call to a Lua standard library function: `module.fn(args)` */
private fun inlineGlobalCall(
    key: String,
    module: String,
    fn: String,
) = InlineLua(key) { pos, args ->
    Lua.FunctionCallExpr(
        pos,
        Lua.DotIndexExpr(pos, Lua.Name(pos, name(module)), Lua.Name(pos, name(fn))),
        Lua.Args(pos, Lua.Exprs(pos, args)),
    )
}

/** Inline a call to a Lua global function: `fn(args)` */
private fun inlineBuiltinCall(
    key: String,
    fn: String,
) = InlineLua(key) { pos, args ->
    Lua.FunctionCallExpr(
        pos,
        Lua.Name(pos, name(fn)),
        Lua.Args(pos, Lua.Exprs(pos, args)),
    )
}

/** Inline identity: returns `args[0]` unchanged. */
private fun inlineIdentity(key: String) = InlineLua(key) { _, args -> args[0] }

/** Inline a global property access: `module.prop` */
private fun inlineGlobalProp(
    key: String,
    module: String,
    prop: String,
) = InlineLua(key) { pos, _ ->
    Lua.DotIndexExpr(pos, Lua.Name(pos, name(module)), Lua.Name(pos, name(prop)))
}
