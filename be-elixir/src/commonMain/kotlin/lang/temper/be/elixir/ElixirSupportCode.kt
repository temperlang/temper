package lang.temper.be.elixir

import lang.temper.be.tmpl.ComparisonKind
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

internal fun elixirModule(pos: Position, vararg segments: String) = elixirModule(pos, segments.asList())

internal fun elixirModule(pos: Position, segments: List<String>) = Elixir.ModuleName(pos, segments.map { eid(pos, it) })

/** A shift count is taken modulo the width, as Temper's are: `n &&& 31` for an Int32. */
private const val INT32_SHIFT_MASK = 31
private const val INT64_SHIFT_MASK = 63
private const val UINT32_MAX = 0xFFFF_FFFFL

/** After `this`, `find` and `replace` take the compiled regex, the string and a start or replacement. */
private const val FIND_ARGS = 3

/** After `this`, `split` takes the compiled regex and the string. */
private const val SPLIT_ARGS = 2

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
 * orders `-0.0` first, which `==` and `<` on the BEAM do not, and a Float64
 * may be `:infinity`, `:neg_infinity` or `:nan`, which the BEAM's floats
 * cannot be.
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

/** An operator that is a call to `TemperCore.<module>.<fn>` with the operator's arguments. */
private fun coreIn(id: BuiltinOperatorId, module: String, fn: String) =
    ElixirOperatorCode(id) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", module), fn, a) }

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
    // The BEAM raises on overflow and a zero divisor, where IEEE gives
    // infinity or NaN, so float arithmetic is never a bare operator.
    floatCore(BuiltinOperatorId.PlusFltFlt, "add"),
    floatCore(BuiltinOperatorId.MinusFltFlt, "sub"),
    floatCore(BuiltinOperatorId.TimesFltFlt, "mul"),
    floatCore(BuiltinOperatorId.DivFltFlt, "divide"),
    floatCore(BuiltinOperatorId.MinusFlt, "neg"),
    floatCore(BuiltinOperatorId.ModFltFlt, "rem"),
    floatCore(BuiltinOperatorId.PowFltFlt, "pow"),
    // Only Int32 has the whole relational suite. For every other type the
    // frontend writes `a < b` as `(a <=> b) < 0` and `a != b` as `!(a == b)`.
    infix(BuiltinOperatorId.LtIntInt, ElixirOperator.LessThan),
    infix(BuiltinOperatorId.LeIntInt, ElixirOperator.LessEquals),
    infix(BuiltinOperatorId.GtIntInt, ElixirOperator.GreaterThan),
    infix(BuiltinOperatorId.GeIntInt, ElixirOperator.GreaterEquals),
    infix(BuiltinOperatorId.EqIntInt, ElixirOperator.Equals),
    infix(BuiltinOperatorId.EqLongLong, ElixirOperator.Equals),
    infix(BuiltinOperatorId.EqBoolBool, ElixirOperator.Equals),
    floatCore(BuiltinOperatorId.EqFltFlt, "eq"),
    // Elixir compares binaries byte by byte, which for UTF-8 is code point order.
    infix(BuiltinOperatorId.EqStrStr, ElixirOperator.Equals),
    core(BuiltinOperatorId.CmpIntInt, "cmp"),
    core(BuiltinOperatorId.CmpLongLong, "cmp"),
    // false < true in the BEAM's term order, as in Temper.
    core(BuiltinOperatorId.CmpBoolBool, "cmp"),
    floatCore(BuiltinOperatorId.CmpFltFlt, "cmp"),
    core(BuiltinOperatorId.CmpStrStr, "cmp"),
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
    shift(BuiltinOperatorId.BitwiseShl32, "bsl", "int32", INT32_SHIFT_MASK),
    shift(BuiltinOperatorId.BitwiseShr32, "bsr", "int32", INT32_SHIFT_MASK),
    shift(BuiltinOperatorId.BitwiseShl64, "bsl", "int64", INT64_SHIFT_MASK),
    shift(BuiltinOperatorId.BitwiseShr64, "bsr", "int64", INT64_SHIFT_MASK),
    unsignedShift(BuiltinOperatorId.BitwiseShrUnsigned32, "int32", UINT32_MAX, INT32_SHIFT_MASK),
    // 2^64 - 1 does not fit a Long, so this one is spelled out in the literal's text
    ElixirOperatorCode(BuiltinOperatorId.BitwiseShrUnsigned64) { pos, a ->
        val bitwise = elixirModule(pos, "Bitwise")
        val mask = Elixir.NumberLit(pos, java.math.BigInteger("18446744073709551615"))
        val count = remoteCall(pos, bitwise, "band", listOf(a[1], Elixir.NumberLit(pos, INT64_SHIFT_MASK)))
        coreCall(
            pos,
            "int64",
            listOf(
                remoteCall(pos, bitwise, "bsr", listOf(remoteCall(pos, bitwise, "band", listOf(a[0], mask)), count)),
            ),
        )
    },
    strCat,
    ElixirOperatorCode(BuiltinOperatorId.Listify) { pos, a -> vecLiteral(pos, a) },
    ElixirOperatorCode(BuiltinOperatorId.Bubble) { pos, _ -> raiseOf(pos, "Bubble") },
    ElixirOperatorCode(BuiltinOperatorId.Panic) { pos, _ -> raiseOf(pos, "Panic") },
    ElixirOperatorCode(BuiltinOperatorId.Print) { pos, a -> remoteCall(pos, elixirModule(pos, "IO"), "puts", a) },
    coreIn(BuiltinOperatorId.Async, "Async", "run"),
    coreIn(BuiltinOperatorId.AdaptGeneratorFn, "Generator", "adapt"),
    coreIn(BuiltinOperatorId.SafeAdaptGeneratorFn, "Generator", "adapt"),
).associateBy { it.builtinOperatorId!! }

/** `a < b` for a type whose order is the BEAM's term order; see [ElixirSupportNetwork.simplifyPossibleComparison]. */
internal class ElixirComparison(kind: ComparisonKind) : ElixirInlineSupportCode("comparison ${kind.name}") {
    private val op = when (kind) {
        ComparisonKind.LessThan -> ElixirOperator.LessThan
        ComparisonKind.LessThanOrEqual -> ElixirOperator.LessEquals
        ComparisonKind.GreaterThanOrEqual -> ElixirOperator.GreaterEquals
        ComparisonKind.GreaterThan -> ElixirOperator.GreaterThan
    }
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr = infixOp(pos, args[0], op, args[1])
}

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

/** `TemperCore.Float.math(:fn, x)`: `:math.fn`, with IEEE answers where it raises. */
private fun floatMath(key: String, fn: String) =
    ElixirConnected(key) { pos, a ->
        remoteCall(pos, elixirModule(pos, "TemperCore", "Float"), "math", listOf(Elixir.Atom(pos, fn)) + a)
    }

/** A Temper List literal: `%TemperCore.Vec{t: {1, 2, 3}}`, its tuple built in place. */
internal fun vecLiteral(pos: Position, items: List<Elixir.Expr>): Elixir.Expr =
    Elixir.StructLit(
        pos,
        name = elixirModule(pos, "TemperCore", "Vec"),
        fields = listOf(Elixir.KeywordEntry(pos, Elixir.Id(pos, OutName("t", null)), Elixir.TupleLit(pos, items))),
    )

private fun regex(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
    remoteCall(pos, elixirModule(pos, "TemperCore", "Regex"), fn, args)

private fun matchModules(pos: Position): List<Elixir.Expr> =
    listOf(elixirModule(pos, "Temper", "Std", "Match"), elixirModule(pos, "Temper", "Std", "Group"))

/** `TemperCore.Module.fn(args)` */
private fun connectedIn(module: String, key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", module), fn, a) }

/** `TemperCore.String.fn(args)` */
private fun connectedString(key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", "String"), fn, a) }

/** `TemperCore.StringBuilder.fn(args)` */
private fun connectedBuilder(key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", "StringBuilder"), fn, a) }

/** `TemperCore.List.fn(args)` */
private fun connectedList(key: String, fn: String) =
    ElixirConnected(key) { pos, a -> remoteCall(pos, elixirModule(pos, "TemperCore", "List"), fn, a) }

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
        connectedFloat("core.type Float64.abs()", "abs"),
        floatMath("core.type Float64.sqrt()", "sqrt"),
        floatMath("core.type Float64.exp()", "exp"),
        floatMath("core.type Float64.log()", "log"),
        floatMath("core.type Float64.log10()", "log10"),
        floatMath("core.type Float64.log2()", "log2"),
        floatMath("core.type Float64.sin()", "sin"),
        floatMath("core.type Float64.cos()", "cos"),
        floatMath("core.type Float64.tan()", "tan"),
        floatMath("core.type Float64.asin()", "asin"),
        floatMath("core.type Float64.acos()", "acos"),
        floatMath("core.type Float64.atan()", "atan"),
        connectedFloat("core.type Float64.atan2()", "atan2"),
        floatMath("core.type Float64.sinh()", "sinh"),
        floatMath("core.type Float64.cosh()", "cosh"),
        floatMath("core.type Float64.tanh()", "tanh"),
        floatMath("core.type Float64.ceil()", "ceil"),
        floatMath("core.type Float64.floor()", "floor"),
        floatMath("core.type Float64.expm1()", "expm1"),
        floatMath("core.type Float64.log1p()", "log1p"),
        connectedFloat("core.type Float64.round()", "round"),
        connectedFloat("core.type Float64.near()", "near"),
        connectedCore("core.type Int32.toFloat64()", "int_to_float"),
        connectedCore("core.type Int32.toFloat64Unsafe()", "int_to_float"),
        connectedCore("core.type Int64.toFloat64()", "int64_to_float"),
        connectedCore("core.type Int64.toFloat64Unsafe()", "int_to_float"),
        connectedCore("core.type Float64.toInt32()", "float_to_int32"),
        connectedCore("core.type Float64.toInt64()", "float_to_int64"),
        ElixirConnected("core.type Float64.toInt32Unsafe()") { pos, a ->
            coreCall(pos, "int32", listOf(coreCall(pos, "float_trunc", a)))
        },
        ElixirConnected("core.type Float64.toInt64Unsafe()") { pos, a ->
            coreCall(pos, "int64", listOf(coreCall(pos, "float_trunc", a)))
        },
        identity("core.type Int32.toInt64()"),
        connectedCore("core.type Int64.toInt32()", "int64_to_int32"),
        connectedCore("core.type Int64.toInt32Unsafe()", "int32"),
        connectedString("core.type String.get isEmpty()", "is_empty"),
        connectedString("core.type String.get end()", "end_of"),
        connectedString("core.type String.get()", "get"),
        connectedString("core.type String.hasIndex()", "has_index"),
        connectedString("core.type String.next()", "next"),
        connectedString("core.type String.prev()", "prev"),
        connectedString("core.type String.step()", "step"),
        connectedString("core.type String.countBetween()", "count_between"),
        connectedString("core.type String.hasAtLeast()", "has_at_least"),
        connectedString("core.type String.slice()", "slice"),
        connectedString("core.type String.split()", "split"),
        connectedString("core.type String.forEach()", "for_each"),
        connectedString("core.type String.indexOf()", "index_of"),
        connectedString("core.type String.fromCodePoint()", "from_code_point"),
        connectedString("core.type String.fromCodePoints()", "from_code_points"),
        connectedString("core.type String.toInt32()", "to_int32"),
        connectedString("core.type String.toInt64()", "to_int64"),
        connectedString("core.type String.toFloat64()", "to_float64"),
        connectedString("core.type String.begin", "begin"),
        connectedString("core.type StringIndex.none", "none"),
        connectedCore("core.type StringIndexOption.compareTo()", "cmp"),
        // a string index is an integer, -1 for none
        ElixirConnected("core.type StringIndexOption.eq()") { pos, a ->
            infixOp(pos, a[0], ElixirOperator.Equals, a[1])
        },
        connectedBuilder("core.type StringBuilder.constructor()", "new"),
        connectedBuilder("core.type StringBuilder.append()", "append"),
        connectedBuilder("core.type StringBuilder.appendCodePoint()", "append_code_point"),
        connectedBuilder("core.type StringBuilder.appendBetween()", "append_between"),
        connectedBuilder("core.type StringBuilder.clear()", "clear"),
        connectedBuilder("core.type StringBuilder.toString()", "to_string"),
        connectedBuilder("core.type StringBuilder.get end()", "end_of"),
        connectedIn("Pair", "core.type Pair.constructor()", "new"),
        connectedIn("Map", "core.type Map.constructor()", "new"),
        connectedIn("Map", "core.type MapBuilder.constructor()", "builder"),
        connectedIn("Map", "core.type Mapped.get length()", "length"),
        connectedIn("Map", "core.type Mapped.get()", "get"),
        connectedIn("Map", "core.type Mapped.getOr()", "get_or"),
        connectedIn("Map", "core.type Mapped.has()", "has"),
        connectedIn("Map", "core.type Mapped.keys()", "keys"),
        connectedIn("Map", "core.type Mapped.values()", "values"),
        connectedIn("Map", "core.type Mapped.toMap()", "to_map"),
        connectedIn("Map", "core.type Mapped.toMapBuilder()", "to_builder"),
        connectedIn("Map", "core.type Mapped.toList()", "to_list"),
        connectedIn("Map", "core.type Mapped.toListBuilder()", "to_list_builder"),
        connectedIn("Map", "core.type Mapped.toListWith()", "to_list_with"),
        connectedIn("Map", "core.type Mapped.toListBuilderWith()", "to_list_builder_with"),
        connectedIn("Map", "core.type Mapped.forEach()", "for_each"),
        connectedIn("Map", "core.type MapBuilder.set()", "set"),
        connectedIn("Map", "core.type MapBuilder.remove()", "remove"),
        connectedIn("Map", "core.type MapBuilder.clear()", "clear"),
        connectedIn("Deque", "core.type Deque.constructor()", "new"),
        connectedIn("Deque", "core.type Deque.add()", "add"),
        connectedIn("Deque", "core.type Deque.get isEmpty()", "is_empty"),
        connectedIn("Deque", "core.type Deque.removeFirst()", "remove_first"),
        connectedIn("DenseBitVector", "core.type DenseBitVector.constructor()", "new"),
        connectedIn("DenseBitVector", "core.type DenseBitVector.get()", "get"),
        connectedIn("DenseBitVector", "core.type DenseBitVector.set()", "set"),
        ElixirConnected("core.type Int32.succ()") { pos, a ->
            coreCall(pos, "int32", listOf(infixOp(pos, a[0], ElixirOperator.Addition, Elixir.NumberLit(pos, 1))))
        },
        ElixirConnected("core.type Int32.pred()") { pos, a ->
            coreCall(pos, "int32", listOf(infixOp(pos, a[0], ElixirOperator.Subtraction, Elixir.NumberLit(pos, 1))))
        },
        connectedIn("Test", "std/testing.type Test.assert()", "assert"),
        connectedIn("Test", "std/testing.type Test.assertHard()", "assert_hard"),
        connectedIn("Test", "std/testing.type Test.bail()", "bail"),
        connectedIn("Test", "std/testing.type Test.get passing()", "passing"),
        connectedIn("Test", "std/testing.type Test.messages()", "messages"),
        connectedIn("Test", "std/testing.type Test.get failedOnAssert()", "failed_on_assert"),
        connectedIn("Test", "std/testing.runTestCases()", "run_cases"),
        connectedIn("Test", "std/testing.processTestCases()", "process_cases"),
        // std/regex formats a pattern itself; the host compiles and runs it.
        // A member's first argument is `this`, unused here. Match and Group
        // are std's own classes, passed in so temper-core never names std.
        ElixirConnected("std/regex.type RegexFormatter.regexCompileFormatted()") { pos, a ->
            regex(pos, "compile", listOf(a[1]))
        },
        ElixirConnected("std/regex.type Regex.compiledFound()") { pos, a -> regex(pos, "found", a.drop(1)) },
        ElixirConnected("std/regex.type Regex.compiledFind()") { pos, a ->
            regex(pos, "find", a.subList(1, 1 + FIND_ARGS) + matchModules(pos))
        },
        ElixirConnected("std/regex.type Regex.compiledReplace()") { pos, a ->
            regex(pos, "replace", a.subList(1, 1 + FIND_ARGS) + matchModules(pos))
        },
        ElixirConnected("std/regex.type Regex.compiledSplit()") { pos, a ->
            regex(pos, "split", a.subList(1, 1 + SPLIT_ARGS))
        },
        ElixirConnected("std/regex.type RegexFormatter.pushCodeTo()") { pos, a ->
            remoteCall(
                pos,
                elixirModule(pos, "TemperCore", "StringBuilder"),
                "append",
                listOf(a[1], regex(pos, "code_escape", listOf(a[2]))),
            )
        },
        // std's Date is a translated class, in std's own root module; only
        // reading the clock needs the host
        ElixirConnected("std/temporal.type Date.today()") { pos, _ ->
            remoteCall(
                pos,
                elixirModule(pos, "TemperCore", "Temporal"),
                "today",
                listOf(elixirModule(pos, "Temper", "Std", "Date")),
            )
        },
        ElixirConnected("core.type Float64.pi") { pos, _ -> Elixir.NumberLit(pos, kotlin.math.PI) },
        ElixirConnected("core.type Float64.e") { pos, _ -> Elixir.NumberLit(pos, kotlin.math.E) },
        // a ListBuilder is a Listed too, so every read goes through
        // TemperCore.List, which takes a plain list or a builder
        connectedList("core.type Listed.get length()", "length"),
        connectedList("core.type List.get length()", "length"),
        connectedList("core.type ListBuilder.get length()", "length"),
        connectedList("core.type Listed.get isEmpty()", "is_empty"),
        connectedList("core.type Listed.get()", "get"),
        connectedList("core.type List.get()", "get"),
        connectedList("core.type ListBuilder.get()", "get"),
        connectedList("core.type Listed.getOr()", "get_or"),
        connectedList("core.type Listed.slice()", "slice"),
        connectedList("core.type Listed.toList()", "to_list"),
        connectedList("core.type List.toList()", "to_list"),
        connectedList("core.type ListBuilder.toList()", "to_list"),
        connectedList("core.type Listed.toListBuilder()", "to_builder"),
        connectedList("core.type List.toListBuilder()", "to_builder"),
        connectedList("core.type ListBuilder.toListBuilder()", "to_builder"),
        connectedList("core.type Listed.map()", "map"),
        connectedList("core.type Listed.filter()", "filter"),
        connectedList("core.type Listed.reduce()", "reduce"),
        connectedList("core.type Listed.reduceFrom()", "reduce_from"),
        connectedList("core.type Listed.join()", "join"),
        connectedList("core.type Listed.sorted()", "sorted"),
        connectedList("core.type Listed.forEach()", "for_each"),
        connectedList("core.type List.forEach()", "for_each"),
        connectedList("core.type ListBuilder.constructor()", "builder"),
        connectedList("core.type ListBuilder.add()", "add"),
        connectedList("core.type ListBuilder.addAll()", "add_all"),
        connectedList("core.type ListBuilder.clear()", "clear"),
        connectedList("core.type ListBuilder.removeLast()", "remove_last"),
        connectedList("core.type ListBuilder.reverse()", "reverse"),
        connectedList("core.type ListBuilder.set()", "set"),
        connectedList("core.type ListBuilder.sort()", "sort"),
        connectedList("core.type ListBuilder.splice()", "splice"),
        // Empty must differ from nil, and survive interpolation, which `{}` does not
        ElixirConnected("core.empty()") { pos, _ -> Elixir.Atom(pos, "empty") },
        ElixirConnected("core.doneResult()") { pos, _ -> Elixir.Atom(pos, "done") },
        ElixirConnected("core.type ValueResult.constructor()") { pos, a ->
            Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, "value"), a[0]))
        },
        connectedIn("Generator", "core.type Generator.next()", "next"),
        connectedIn("Generator", "core.type SafeGenerator.next()", "next"),
        connectedIn("Generator", "core.type SafeGenerator.nextSafe()", "next"),
        connectedIn("Generator", "core.type Generator.close()", "close"),
        connectedIn("Promise", "core.type PromiseBuilder.constructor()", "new"),
        identity("core.type PromiseBuilder.get promise()"),
        connectedIn("Promise", "core.type PromiseBuilder.complete()", "complete"),
        connectedIn("Promise", "core.type PromiseBuilder.breakPromise()", "break_promise"),
        connectedIn("Net", "std/net.sendRequest()", "send_request"),
        connectedIn("Net", "std/net.type NetResponse.get status()", "status"),
        connectedIn("Net", "std/net.type NetResponse.get contentType()", "content_type"),
        connectedIn("Net", "std/net.type NetResponse.get bodyContent()", "body_content"),
    )
    ).associateBy { it.connectedKey }

/** `pureVirtual()`, the body of an abstract method. */
internal object PureVirtual : ElixirInlineSupportCode("pureVirtual") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr =
        localCall(pos, "raise", listOf(elixirModule(pos, "TemperCore", "Panic")))
}

internal object AwakeUpon : ElixirInlineSupportCode("awakeUpon") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr =
        remoteCall(pos, elixirModule(pos, "TemperCore", "Promise"), "awake_upon", args)
}

internal object GetPromiseResultSync : ElixirInlineSupportCode("getPromiseResultSync") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr =
        remoteCall(pos, elixirModule(pos, "TemperCore", "Promise"), "result", args)
}
