package lang.temper.be.elixir

import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.TypedArg
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId

/**
 * Support code that becomes Elixir syntax at the call site, shaped like
 * be-blimp's and be-lua's inline support code.
 */
internal abstract class ElixirInlineSupportCode(
    /** The `@connected` key, e.g. `core.type Console.log()`. Doubles as the name hint. */
    val connectedKey: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : InlineSupportCode<Elixir.Tree, Any>, NamedSupportCode {

    override val baseName: ParsedName get() = ParsedName(connectedKey)

    override val needsThisEquivalent: Boolean get() = false

    override fun renderTo(tokenSink: TokenSink) = tokenSink.name(baseName, inOperatorPosition = false)

    /**
     * Builds the Elixir expression for one call. For a `@connected` method,
     * `args[0]` is the receiver and the declared parameters follow.
     */
    abstract fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr

    final override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Elixir.Tree>>,
        returnType: Type2,
        translator: Any,
    ): Elixir.Tree = callFactory(pos, arguments.map { it.expr as Elixir.Expr })

    final override fun equals(other: Any?): Boolean =
        this === other || (other is ElixirInlineSupportCode && connectedKey == other.connectedKey)

    final override fun hashCode(): Int = connectedKey.hashCode()

    final override fun toString(): String = "ElixirSupportCode($connectedKey)"
}

/**
 * `core.getConsole()`.
 *
 * Elixir has no console object, and the only thing Temper does with this
 * value is call `log` on it, which [ConsoleLog] inlines while dropping the
 * receiver. So it is `nil`, as be-blimp's is.
 */
internal object GetConsole : ElixirInlineSupportCode("core.getConsole()") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr = Elixir.NilLit(pos)
}

/**
 * `core.type Console.log()` becomes `IO.puts(message)`.
 *
 * `args` is `[receiver, message]`, and the receiver is [GetConsole]'s `nil`.
 * `IO.puts` writes the binary as it is and a newline, which is what
 * `console.log` of a string means; `IO.inspect` would quote it.
 */
internal object ConsoleLog : ElixirInlineSupportCode("core.type Console.log()") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr =
        Elixir.RemoteCall(
            pos,
            module = Elixir.ModuleName(pos, listOf(Elixir.Id(pos, OutName("IO", null)))),
            fn = Elixir.Id(pos, OutName("puts", null)),
            args = listOf(args.last()),
        )
}

// ── Builtin operators ────────────────────────────────────────────────────

private fun eid(pos: Position, text: String) = Elixir.Id(pos, OutName(text, null))

internal fun elixirModule(pos: Position, vararg segments: String) =
    Elixir.ModuleName(pos, segments.map { eid(pos, it) })

internal fun remoteCall(pos: Position, module: Elixir.Expr, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
    Elixir.RemoteCall(pos, module = module, fn = eid(pos, fn), args = args)

/** `TemperCore.fn(args)` */
internal fun coreCall(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
    remoteCall(pos, elixirModule(pos, "TemperCore"), fn, args)

internal fun localCall(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
    Elixir.Call(pos, callee = eid(pos, fn), args = args)

internal fun infixOp(pos: Position, left: Elixir.Expr, op: ElixirOperator, right: Elixir.Expr): Elixir.Expr =
    Elixir.Operation(pos, left = left, operator = Elixir.Operator(pos, op), right = right)

internal fun prefixOp(pos: Position, op: ElixirOperator, operand: Elixir.Expr): Elixir.Expr =
    Elixir.Operation(pos, left = null, operator = Elixir.Operator(pos, op), right = operand)

/** A builtin operator, built at the call site by [factory]. */
internal class ElixirOperatorCode(
    id: BuiltinOperatorId,
    private val factory: (Position, List<Elixir.Expr>) -> Elixir.Expr,
) : ElixirInlineSupportCode("builtin ${id.name}", id) {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr = factory(pos, args)
}

private fun infix(id: BuiltinOperatorId, op: ElixirOperator) =
    ElixirOperatorCode(id) { pos, a -> infixOp(pos, a[0], op, a[1]) }

private fun prefix(id: BuiltinOperatorId, op: ElixirOperator) =
    ElixirOperatorCode(id) { pos, a -> prefixOp(pos, op, a[0]) }

/** `TemperCore.int32(a op b)`: Elixir integers have no width, Temper's Int wraps at 32 bits. */
private fun wrap32(id: BuiltinOperatorId, op: ElixirOperator) =
    ElixirOperatorCode(id) { pos, a ->
        coreCall(pos, "int32", listOf(if (a.size == 1) prefixOp(pos, op, a[0]) else infixOp(pos, a[0], op, a[1])))
    }

private fun wrap64(id: BuiltinOperatorId, op: ElixirOperator) =
    ElixirOperatorCode(id) { pos, a ->
        coreCall(pos, "int64", listOf(if (a.size == 1) prefixOp(pos, op, a[0]) else infixOp(pos, a[0], op, a[1])))
    }

private fun core(id: BuiltinOperatorId, fn: String) =
    ElixirOperatorCode(id) { pos, a -> coreCall(pos, fn, a) }

/**
 * `TemperCore.Float.fn(args)`: Temper calls `-0.0` and `0.0` unequal and
 * orders `-0.0` first, which `==` and `<` on the BEAM do not.
 */
private fun floatCore(id: BuiltinOperatorId, fn: String) =
    ElixirOperatorCode(id) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", "Float"), fn, a) }

private fun kernel(id: BuiltinOperatorId, fn: String) =
    ElixirOperatorCode(id) { pos, a -> localCall(pos, fn, a) }

/** `TemperCore.intN(Bitwise.fn(args))` */
private fun bitwise(id: BuiltinOperatorId, fn: String, wrapper: String) =
    ElixirOperatorCode(id) { pos, a ->
        coreCall(pos, wrapper, listOf(remoteCall(pos, elixirModule(pos, "Bitwise"), fn, a)))
    }

/**
 * A shift whose count is masked to the width, as Java and JavaScript do:
 * `TemperCore.intN(Bitwise.fn(a, Bitwise.band(n, width - 1)))`.
 */
private fun shift(id: BuiltinOperatorId, fn: String, wrapper: String, mask: Int) =
    ElixirOperatorCode(id) { pos, a ->
        val count = remoteCall(pos, elixirModule(pos, "Bitwise"), "band", listOf(a[1], Elixir.NumberLit(pos, mask)))
        coreCall(pos, wrapper, listOf(remoteCall(pos, elixirModule(pos, "Bitwise"), fn, listOf(a[0], count))))
    }

/** `>>>`: shift the unsigned reading of the bits, then read them signed again. */
private fun unsignedShift(id: BuiltinOperatorId, wrapper: String, bits: Long, mask: Int) =
    ElixirOperatorCode(id) { pos, a ->
        val bitwise = elixirModule(pos, "Bitwise")
        val unsigned = remoteCall(pos, bitwise, "band", listOf(a[0], Elixir.NumberLit(pos, bits)))
        val count = remoteCall(pos, bitwise, "band", listOf(a[1], Elixir.NumberLit(pos, mask)))
        coreCall(pos, wrapper, listOf(remoteCall(pos, bitwise, "bsr", listOf(unsigned, count))))
    }

/**
 * `"a" <> b <> "c"`. StrCat is variadic: an interpolation arrives as one
 * call with every piece. One piece is already the string; none is "".
 */
private val strCat = ElixirOperatorCode(BuiltinOperatorId.StrCat) { pos, a ->
    when (a.size) {
        0 -> Elixir.StringLit(pos, "")
        else -> a.reduceRight { piece, acc -> infixOp(pos, piece, ElixirOperator.StringConcat, acc) }
    }
}

private fun raiseOf(pos: Position, exception: String): Elixir.Expr =
    localCall(pos, "raise", listOf(elixirModule(pos, "TemperCore", exception)))

internal val elixirOperators: Map<BuiltinOperatorId, ElixirOperatorCode> = listOf(
    wrap32(BuiltinOperatorId.PlusIntInt, ElixirOperator.Addition),
    wrap32(BuiltinOperatorId.MinusIntInt, ElixirOperator.Subtraction),
    wrap32(BuiltinOperatorId.TimesIntInt, ElixirOperator.Multiplication),
    wrap32(BuiltinOperatorId.MinusInt, ElixirOperator.Negate),
    wrap64(BuiltinOperatorId.PlusIntInt64, ElixirOperator.Addition),
    wrap64(BuiltinOperatorId.MinusIntInt64, ElixirOperator.Subtraction),
    wrap64(BuiltinOperatorId.TimesIntInt64, ElixirOperator.Multiplication),
    wrap64(BuiltinOperatorId.MinusInt64, ElixirOperator.Negate),
    // `div` and `rem` truncate toward zero as Temper does. The Safe forms have
    // a divisor the frontend proved non-zero; the others bubble on zero.
    ElixirOperatorCode(BuiltinOperatorId.DivIntIntSafe) { pos, a ->
        coreCall(pos, "int32", listOf(localCall(pos, "div", a)))
    },
    ElixirOperatorCode(BuiltinOperatorId.DivIntInt64Safe) { pos, a ->
        coreCall(pos, "int64", listOf(localCall(pos, "div", a)))
    },
    core(BuiltinOperatorId.DivIntInt, "int32_div"),
    core(BuiltinOperatorId.DivIntInt64, "int64_div"),
    kernel(BuiltinOperatorId.ModIntIntSafe, "rem"),
    kernel(BuiltinOperatorId.ModIntInt64Safe, "rem"),
    core(BuiltinOperatorId.ModIntInt, "int32_rem"),
    core(BuiltinOperatorId.ModIntInt64, "int64_rem"),
    infix(BuiltinOperatorId.PlusFltFlt, ElixirOperator.Addition),
    infix(BuiltinOperatorId.MinusFltFlt, ElixirOperator.Subtraction),
    infix(BuiltinOperatorId.TimesFltFlt, ElixirOperator.Multiplication),
    // Raises ArithmeticError on a zero divisor, where IEEE gives infinity.
    // The BEAM has no infinity to give: a known gap, not a translation.
    infix(BuiltinOperatorId.DivFltFlt, ElixirOperator.FloatDivision),
    prefix(BuiltinOperatorId.MinusFlt, ElixirOperator.Negate),
    ElixirOperatorCode(BuiltinOperatorId.ModFltFlt) { pos, a -> remoteCall(pos, Elixir.Atom(pos, "math"), "fmod", a) },
    ElixirOperatorCode(BuiltinOperatorId.PowFltFlt) { pos, a -> remoteCall(pos, Elixir.Atom(pos, "math"), "pow", a) },
    infix(BuiltinOperatorId.LtIntInt, ElixirOperator.LessThan),
    infix(BuiltinOperatorId.LeIntInt, ElixirOperator.LessEquals),
    infix(BuiltinOperatorId.GtIntInt, ElixirOperator.GreaterThan),
    infix(BuiltinOperatorId.GeIntInt, ElixirOperator.GreaterEquals),
    infix(BuiltinOperatorId.EqIntInt, ElixirOperator.Equals),
    infix(BuiltinOperatorId.NeIntInt, ElixirOperator.NotEquals),
    floatCore(BuiltinOperatorId.LtFltFlt, "lt"),
    floatCore(BuiltinOperatorId.LeFltFlt, "le"),
    floatCore(BuiltinOperatorId.GtFltFlt, "gt"),
    floatCore(BuiltinOperatorId.GeFltFlt, "ge"),
    floatCore(BuiltinOperatorId.EqFltFlt, "eq"),
    floatCore(BuiltinOperatorId.NeFltFlt, "ne"),
    // Elixir compares binaries byte by byte, which for UTF-8 is code point order.
    infix(BuiltinOperatorId.LtStrStr, ElixirOperator.LessThan),
    infix(BuiltinOperatorId.LeStrStr, ElixirOperator.LessEquals),
    infix(BuiltinOperatorId.GtStrStr, ElixirOperator.GreaterThan),
    infix(BuiltinOperatorId.GeStrStr, ElixirOperator.GreaterEquals),
    infix(BuiltinOperatorId.EqStrStr, ElixirOperator.Equals),
    infix(BuiltinOperatorId.NeStrStr, ElixirOperator.NotEquals),
    infix(BuiltinOperatorId.LtGeneric, ElixirOperator.LessThan),
    infix(BuiltinOperatorId.LeGeneric, ElixirOperator.LessEquals),
    infix(BuiltinOperatorId.GtGeneric, ElixirOperator.GreaterThan),
    infix(BuiltinOperatorId.GeGeneric, ElixirOperator.GreaterEquals),
    infix(BuiltinOperatorId.EqGeneric, ElixirOperator.Equals),
    infix(BuiltinOperatorId.NeGeneric, ElixirOperator.NotEquals),
    core(BuiltinOperatorId.CmpIntInt, "cmp"),
    floatCore(BuiltinOperatorId.CmpFltFlt, "cmp"),
    core(BuiltinOperatorId.CmpStrStr, "cmp"),
    core(BuiltinOperatorId.CmpGeneric, "cmp"),
    prefix(BuiltinOperatorId.BooleanNegation, ElixirOperator.Not),
    ElixirOperatorCode(BuiltinOperatorId.IsNull) { pos, a ->
        infixOp(pos, a[0], ElixirOperator.StrictEquals, Elixir.NilLit(pos))
    },
    ElixirOperatorCode(BuiltinOperatorId.NotNull) { pos, a ->
        infixOp(pos, a[0], ElixirOperator.StrictNotEquals, Elixir.NilLit(pos))
    },
    bitwise(BuiltinOperatorId.BitwiseAnd32, "band", "int32"),
    bitwise(BuiltinOperatorId.BitwiseOr32, "bor", "int32"),
    bitwise(BuiltinOperatorId.BitwiseXor32, "bxor", "int32"),
    bitwise(BuiltinOperatorId.BitwiseNegation32, "bnot", "int32"),
    bitwise(BuiltinOperatorId.BitwiseAnd64, "band", "int64"),
    bitwise(BuiltinOperatorId.BitwiseOr64, "bor", "int64"),
    bitwise(BuiltinOperatorId.BitwiseXor64, "bxor", "int64"),
    bitwise(BuiltinOperatorId.BitwiseNegation64, "bnot", "int64"),
    shift(BuiltinOperatorId.BitwiseShl32, "bsl", "int32", 31),
    shift(BuiltinOperatorId.BitwiseShr32, "bsr", "int32", 31),
    shift(BuiltinOperatorId.BitwiseShl64, "bsl", "int64", 63),
    shift(BuiltinOperatorId.BitwiseShr64, "bsr", "int64", 63),
    unsignedShift(BuiltinOperatorId.BitwiseShrUnsigned32, "int32", 0xFFFF_FFFFL, 31),
    // 2^64 - 1 does not fit a Long, so this one is spelled out in the literal's text
    ElixirOperatorCode(BuiltinOperatorId.BitwiseShrUnsigned64) { pos, a ->
        val bitwise = elixirModule(pos, "Bitwise")
        val mask = Elixir.NumberLit(pos, java.math.BigInteger("18446744073709551615"))
        val count = remoteCall(pos, bitwise, "band", listOf(a[1], Elixir.NumberLit(pos, 63)))
        coreCall(
            pos,
            "int64",
            listOf(
                remoteCall(pos, bitwise, "bsr", listOf(remoteCall(pos, bitwise, "band", listOf(a[0], mask)), count)),
            ),
        )
    },
    strCat,
    ElixirOperatorCode(BuiltinOperatorId.Listify) { pos, a -> Elixir.ListLit(pos, a) },
    ElixirOperatorCode(BuiltinOperatorId.Bubble) { pos, _ -> raiseOf(pos, "Bubble") },
    ElixirOperatorCode(BuiltinOperatorId.Panic) { pos, _ -> raiseOf(pos, "Panic") },
    ElixirOperatorCode(BuiltinOperatorId.Print) { pos, a -> remoteCall(pos, elixirModule(pos, "IO"), "puts", a) },
).associateBy { it.builtinOperatorId!! }

// ── Connected functions and methods ──────────────────────────────────────

/** A `@connected` function or method, built at the call site. For a method `args[0]` is the receiver. */
internal class ElixirConnected(
    key: String,
    private val factory: (Position, List<Elixir.Expr>) -> Elixir.Expr,
) : ElixirInlineSupportCode(key) {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr = factory(pos, args)
}

private fun connectedCore(key: String, fn: String) = ElixirConnected(key) { pos, a -> coreCall(pos, fn, a) }

private fun connectedFloat(key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", "Float"), fn, a) }

private fun connectedKernel(key: String, fn: String) = ElixirConnected(key) { pos, a -> localCall(pos, fn, a) }

private fun erlangMath(key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, Elixir.Atom(pos, "math"), fn, a) }

private fun identity(key: String) = ElixirConnected(key) { _, a -> a[0] }

/** Every `@connected` key be-elixir understands. Anything absent uses Temper's own implementation. */
internal val elixirConnected: Map<String, ElixirInlineSupportCode> = (
    listOf(ConsoleLog, GetConsole) + listOf(
        connectedCore("core.type Int32.toString()", "int_to_string"),
        connectedCore("core.type Int64.toString()", "int_to_string"),
        connectedFloat("core.type Float64.toString()", "to_string"),
        ElixirConnected("core.type Boolean.toString()") { pos, a ->
            remoteCall(pos, elixirModule(pos, "Atom"), "to_string", a)
        },
        identity("core.type String.toString()"),
        connectedCore("core.ignore()", "ignore"),
        connectedKernel("core.type Int32.min()", "min"),
        connectedKernel("core.type Int32.max()", "max"),
        connectedKernel("core.type Int64.min()", "min"),
        connectedKernel("core.type Int64.max()", "max"),
        connectedFloat("core.type Float64.min()", "min"),
        connectedFloat("core.type Float64.max()", "max"),
        connectedFloat("core.type Float64.sign()", "sign"),
        connectedKernel("core.type Float64.abs()", "abs"),
        erlangMath("core.type Float64.sqrt()", "sqrt"),
        erlangMath("core.type Float64.exp()", "exp"),
        erlangMath("core.type Float64.log()", "log"),
        erlangMath("core.type Float64.log10()", "log10"),
        erlangMath("core.type Float64.log2()", "log2"),
        erlangMath("core.type Float64.sin()", "sin"),
        erlangMath("core.type Float64.cos()", "cos"),
        erlangMath("core.type Float64.tan()", "tan"),
        erlangMath("core.type Float64.asin()", "asin"),
        erlangMath("core.type Float64.acos()", "acos"),
        erlangMath("core.type Float64.atan()", "atan"),
        erlangMath("core.type Float64.atan2()", "atan2"),
        erlangMath("core.type Float64.sinh()", "sinh"),
        erlangMath("core.type Float64.cosh()", "cosh"),
        erlangMath("core.type Float64.tanh()", "tanh"),
        erlangMath("core.type Float64.ceil()", "ceil"),
        erlangMath("core.type Float64.floor()", "floor"),
        connectedCore("core.type Int32.toFloat64()", "int_to_float"),
        connectedCore("core.type Int32.toFloat64Unsafe()", "int_to_float"),
        connectedCore("core.type Int64.toFloat64()", "int64_to_float"),
        connectedCore("core.type Int64.toFloat64Unsafe()", "int_to_float"),
        connectedCore("core.type Float64.toInt32()", "float_to_int32"),
        connectedCore("core.type Float64.toInt64()", "float_to_int64"),
        ElixirConnected("core.type Float64.toInt32Unsafe()") { pos, a ->
            coreCall(pos, "int32", listOf(localCall(pos, "trunc", a)))
        },
        ElixirConnected("core.type Float64.toInt64Unsafe()") { pos, a ->
            coreCall(pos, "int64", listOf(localCall(pos, "trunc", a)))
        },
        identity("core.type Int32.toInt64()"),
        connectedCore("core.type Int64.toInt32()", "int64_to_int32"),
        connectedCore("core.type Int64.toInt32Unsafe()", "int32"),
        ElixirConnected("core.type String.get isEmpty()") { pos, a ->
            infixOp(pos, a[0], ElixirOperator.Equals, Elixir.StringLit(pos, ""))
        },
        connectedKernel("core.type Listed.get length()", "length"),
        connectedKernel("core.type List.get length()", "length"),
        ElixirConnected("core.type Listed.get isEmpty()") { pos, a ->
            infixOp(pos, a[0], ElixirOperator.Equals, Elixir.ListLit(pos, listOf()))
        },
        connectedCore("core.type Listed.get()", "list_get"),
        connectedCore("core.type List.get()", "list_get"),
        connectedCore("core.type Listed.getOr()", "list_get_or"),
        identity("core.type Listed.toList()"),
        identity("core.type List.toList()"),
    )
    ).associateBy { it.connectedKey }
