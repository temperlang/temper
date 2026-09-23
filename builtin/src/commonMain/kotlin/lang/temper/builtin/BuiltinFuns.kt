package lang.temper.builtin

import lang.temper.common.AtomicCounter
import lang.temper.common.Log
import lang.temper.common.MIN_SUPPLEMENTAL_CP
import lang.temper.common.decodeUtf16
import lang.temper.common.toStringViaBuilder
import lang.temper.env.InterpMode
import lang.temper.lexer.TokenType
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.CoreCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.withNullity
import lang.temper.value.AbstractPanic
import lang.temper.value.ActualValues
import lang.temper.value.BubbleFn
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallableValue
import lang.temper.value.ComparableTypeTag
import lang.temper.value.Fail
import lang.temper.value.HelpSnippet
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotFn
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PanicFn
import lang.temper.value.PartialResult
import lang.temper.value.PreserveFn
import lang.temper.value.PureVirtual
import lang.temper.value.Result
import lang.temper.value.SpecialFunction
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.VoidishPanicFn
import lang.temper.value.listBuiltinName
import lang.temper.value.typeSymbol
import lang.temper.value.unpackOrFail
import lang.temper.value.unpackPositionedOr
import lang.temper.value.unpackValue
import lang.temper.value.void
import kotlin.math.pow
import lang.temper.type.WellKnownTypes as WKT

private fun fTypeTypeToBoolean(sides: TypeShape, nullity: Nullity): Signature2 {
    val sidesAll = MkType2(sides).nullity(nullity).get()
    return Signature2(
        returnType2 = WKT.booleanType2,
        requiredInputTypes = listOf(sidesAll, sidesAll),
        hasThisFormal = false,
    )
}

private val fIntIntNonNullToBoolean = fTypeTypeToBoolean(WKT.intTypeDefinition, Nullity.NonNull)
private val fLongLongNonNullToBoolean = fTypeTypeToBoolean(WKT.int64TypeDefinition, Nullity.NonNull)
private val fDoubleDoubleNonNullToBoolean = fTypeTypeToBoolean(WKT.float64TypeDefinition, Nullity.NonNull)
private val fStringStringNonNullToBoolean = fTypeTypeToBoolean(WKT.stringTypeDefinition, Nullity.NonNull)
private val fBooleanBooleanNonNullToBoolean = fTypeTypeToBoolean(WKT.booleanTypeDefinition, Nullity.NonNull)

private val fDoubleDoubleToDouble = Signature2(
    returnType2 = WKT.float64Type2,
    requiredInputTypes = listOf(WKT.float64Type2, WKT.float64Type2),
    hasThisFormal = false,
)

private val fDoubleDoubleToDoubleOrBubble = fDoubleDoubleToDouble.copy(
    returnType2 = MkType2.result(WKT.float64Type2, WKT.bubbleType2).get(),
)

private val fDoubleToDouble = Signature2(
    returnType2 = WKT.float64Type2,
    requiredInputTypes = listOf(WKT.float64Type2),
    hasThisFormal = false,
)

private val fIntIntToInt = Signature2(
    returnType2 = WKT.intType2,
    requiredInputTypes = listOf(WKT.intType2, WKT.intType2),
    hasThisFormal = false,
)

private val fIntIntToIntOrBubble = fIntIntToInt.copy(
    returnType2 = MkType2.result(WKT.intType2, WKT.bubbleType2).get(),
)

private val fIntToInt = Signature2(
    returnType2 = WKT.intType2,
    requiredInputTypes = listOf(WKT.intType2),
    hasThisFormal = false,
)

private val fLongLongToLong = Signature2(
    returnType2 = WKT.int64Type2,
    requiredInputTypes = listOf(WKT.int64Type2, WKT.int64Type2),
    hasThisFormal = false,
)

private val fLongIntToLong = Signature2(
    returnType2 = WKT.int64Type2,
    requiredInputTypes = listOf(WKT.int64Type2, WKT.intType2),
    hasThisFormal = false,
)

private val fLongLongToLongOrBubble = fLongLongToLong.copy(
    returnType2 = MkType2.result(WKT.int64Type2, WKT.bubbleType2).get(),
)

private val fLongIntToLongOrBubble = fLongIntToLong.copy(
    returnType2 = MkType2.result(WKT.int64Type2, WKT.bubbleType2).get(),
)

private val fLongToLong = Signature2(
    returnType2 = WKT.int64Type2,
    requiredInputTypes = listOf(WKT.int64Type2),
    hasThisFormal = false,
)

// These need to be lambdas so that we can compare for reference equality below
private val twoIntsToNull = { _: Int, _: Int, _: InterpreterCallback -> null }
private val twoLongsToNull = { _: Long, _: Long, _: InterpreterCallback -> null }
private val longIntToNull = { _: Long, _: Int, _: InterpreterCallback -> null }

private class Comparator<T : Any>(
    builtinOperatorId: BuiltinOperatorId?,
    inputType: Type2,
    private val tag: ComparableTypeTag<T>,
) : BuiltinFun(
    "<=>",
    Signature2(WKT.intType2, false, listOf(inputType, inputType)),
    builtinOperatorId,
) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (a, b) = args.unpackPositionedOr(2, cb) {
            return@invoke it
        }
        if (a.typeTag != tag || b.typeTag != tag) {
            val (index, arg) = if (a.typeTag != tag) {
                0 to a
            } else {
                1 to b
            }

            return Fail(
                LogEntry(
                    MessageTemplate.ExpectedValueOfType,
                    args.pos(index) ?: cb.pos,
                    listOf(tag, arg),
                ),
            )
        }

        return Value(
            tag.comparator.compare(tag.unpack(a), tag.unpack(b)),
            TInt,
        )
    }
}

private class IntCompareFun(
    name: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fIntIntNonNullToBoolean, builtinOperatorId), PureCallableValue {
    override val callMayFailPerSe get() = false
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        return f(TInt.compareBoth(left, right))
    }
}

private class LongCompareFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fLongLongNonNullToBoolean), PureCallableValue {
    override val callMayFailPerSe get() = false
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        return f(TInt64.compareBoth(left, right))
    }
}

private class DoubleCompareFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fDoubleDoubleNonNullToBoolean), PureCallableValue {
    override val callMayFailPerSe get() = false
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        return f(TFloat64.compareBoth(left, right))
    }
}

private class StringCompareFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fStringStringNonNullToBoolean), PureCallableValue {
    override val callMayFailPerSe get() = false
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        return f(TString.compareBoth(left, right))
    }
}

private class BoolCompareFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fBooleanBooleanNonNullToBoolean), PureCallableValue {
    override val callMayFailPerSe get() = false
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        return f(TBoolean.compareBoth(left, right))
    }
}

private class IntIntToIntFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    /**
     * [Fail] iff the inputs are invalid for [f], else null.
     */
    val fail: (a: Int, b: Int, cb: InterpreterCallback) -> Fail? = twoIntsToNull,
    val f: (a: Int, b: Int) -> Int,
) : BuiltinFun(
    name,
    signature = if (fail === twoIntsToNull) {
        fIntIntToInt
    } else {
        fIntIntToIntOrBubble
    },
),
    PureCallableValue {
    override val callMayFailPerSe get() = fail !== twoIntsToNull

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        val a = TInt.unpack(left)
        val b = TInt.unpack(right)
        return fail(a, b, cb) ?: Value(
            f(a, b),
            TInt,
        )
    }
}

private class LongLongToLongFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    /**
     * [Fail] iff the inputs are invalid for [f], else null.
     */
    val fail: (a: Long, b: Long, cb: InterpreterCallback) -> Fail? = twoLongsToNull,
    val f: (a: Long, b: Long) -> Long,
) : BuiltinFun(
    name,
    signature = if (fail === twoLongsToNull) { fLongLongToLong } else { fLongLongToLongOrBubble },
),
    PureCallableValue {
    override val callMayFailPerSe get() = fail !== twoLongsToNull

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        val a = TInt64.unpack(left)
        val b = TInt64.unpack(right)
        return fail(a, b, cb) ?: Value(
            f(a, b),
            TInt64,
        )
    }
}

private class LongIntToLongFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    /**
     * [Fail] iff the inputs are invalid for [f], else null.
     */
    val fail: (a: Long, b: Int, cb: InterpreterCallback) -> Fail? = longIntToNull,
    val f: (a: Long, b: Int) -> Long,
) : BuiltinFun(
    name,
    signature = if (fail === longIntToNull) { fLongIntToLong } else { fLongIntToLongOrBubble },
),
    PureCallableValue {
    override val callMayFailPerSe get() = fail !== twoLongsToNull

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        val a = TInt64.unpack(left)
        val b = TInt.unpack(right)
        return fail(a, b, cb) ?: Value(
            f(a, b),
            TInt64,
        )
    }
}

private val twoDoublesToNull = { _: Double, _: Double, _: InterpreterCallback -> null }

private class FloatFloatToFloatFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    /**
     * [Fail] iff the inputs are valid for [f], else null.
     */
    val fail: (a: Double, b: Double, cb: InterpreterCallback) -> Fail? = twoDoublesToNull,
    val f: (a: Double, b: Double) -> Double,
) : BuiltinFun(
    name,
    signature = if (fail === twoDoublesToNull) {
        fDoubleDoubleToDouble
    } else {
        fDoubleDoubleToDoubleOrBubble
    },
),
    PureCallableValue {
    override val callMayFailPerSe get() = fail !== twoDoublesToNull

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (left, right) = args.unpackPositioned(2, cb) ?: return Fail
        val a = TFloat64.unpack(left)
        val b = TFloat64.unpack(right)
        return fail(a, b, cb) ?: Value(
            f(a, b),
            TFloat64,
        )
    }
}

private class IntToIntFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Int,
) : BuiltinFun(name, fIntToInt), PureCallableValue {
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a) = args.unpackPositioned(1, cb) ?: return Fail
        return Value(
            f(TInt.unpack(a)),
            TInt,
        )
    }
}

private class LongToLongFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Long) -> Long,
) : BuiltinFun(name, fLongToLong), PureCallableValue {
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a) = args.unpackPositioned(1, cb) ?: return Fail
        return Value(
            f(TInt64.unpack(a)),
            TInt64,
        )
    }
}

private class FloatToFloatFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Double) -> Double,
) : BuiltinFun(name, fDoubleToDouble), PureCallableValue {
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a) = args.unpackPositioned(1, cb) ?: return Fail
        return Value(
            f(TFloat64.unpack(a)),
            TFloat64,
        )
    }
}

object StringIndexSupport {
    val stringIndexOffsetProperty by lazy {
        WKT.stringIndexTypeDefinition.properties.first {
            it.abstractness == Abstractness.Concrete
        }.name as ModularName
    }

    val stringIndexTClass = TClass(WKT.stringIndexTypeDefinition)
    val noStringIndexTClass = TClass(WKT.noStringIndexTypeDefinition)

    fun unpackStringIndex(r: InstancePropertyRecord): Int = TInt.unpack(
        r.properties.getValue(stringIndexOffsetProperty),
    )

    fun isNoStringIndex(r: PartialResult) =
        (r as? Value<*>)?.typeTag == noStringIndexTClass

    fun compare(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (a: Int, b: Int) = listOf(0, 1).map { argIndex ->
            if (isNoStringIndex(args.result(argIndex, interpMode))) {
                -1
            } else {
                stringIndexTClass.unpackOrFail(args, argIndex, cb, interpMode) {
                    return@compare it
                }.let { unpackStringIndex(it) }
            }
        }
        return Value(a.compareTo(b), TInt)
    }

    fun equals(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult =
        when (val comparisonResult = compare(args, cb, interpMode)) {
            NotYet, is Fail -> comparisonResult
            is Value<*> if comparisonResult.typeTag == TInt -> TBoolean.value(
                TInt.unpack(comparisonResult) == 0,
            )
            is Value<*> -> Fail
        }
}

/**
 * <!-- snippet: builtin/cat -->
 * # `cat`
 * Short for "con**cat**enate", combines multiple strings into one string.
 *
 * ```temper
 * ""       == cat()             &&
 * "foo"    == cat("foo")        &&
 * "foobar" == cat("foo", "bar")
 * ```
 *
 * [snippet/builtin/+] does not concatenate strings; it's reserved for math.
 *
 * `cat` is an implementation detail.  Prefer [snippet/syntax/string/interpolation]
 * to compose strings.
 *
 * ```temper
 * let a = "foo";
 * let b = "bar";
 *
 * "foo-bar" == "${ a }-${ b }"
 * ```
 */
@HelpSnippet("concatenates strings", "builtin/cat")
private object StrCatFn :
    BuiltinFun(
        "cat",
        Signature2(
            returnType2 = WKT.stringType2,
            requiredInputTypes = listOf(),
            hasThisFormal = false,
            restInputsType = WKT.stringType2,
        ),
    ),
    PureCallableValue {

    override val builtinOperatorId get() = BuiltinOperatorId.StrCat

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val str = toStringViaBuilder { sb ->
            for (i in args.indices) {
                val value = args[i] // TODO: what do we do if we get keys
                // And just let this panic if it's not a string.
                sb.append(TString.unpack(value))
            }
        }
        return Value(str, TString)
    }

    override val callMayFailPerSe: Boolean get() = false
}

/**
 * <!-- snippet: builtin/char -->
 * # `char`
 * A string tag that requires a single code-point string and returns that
 * code-point as an [snippet/type/Int32].
 *
 * ```temper
 * char'a' == 97
 * ```
 */
@HelpSnippet("Literal syntax for a Unicode code-point number", "builtin/char")
private object CharTagFn : NamedBuiltinFun, PureCallableValue {
    override val name: String = "char"

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        args.unpackPositionedOr(2, cb) { return@invoke it }
        val strs = TList.unpackOrFail(args, 0, cb, interpMode) {
            return@invoke it
        }
        if (strs.size != 1) {
            return Fail
        }
        val rawString = strs.first()
        val decoded = TString.unpackOrNull(rawString)?.let {
            unpackValue(it, TokenType.QuotedString)
        }
        when (decoded) {
            is Fail -> return decoded
            null, is Value<*> -> {}
        }

        val str = TString.unpackOrNull(decoded)
            ?: return Fail(
                LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedValueOfType,
                    args.pos(1) ?: cb.pos,
                    listOf(TString, strs.first()),
                ),
            )
        val codePoint = if (str.isEmpty()) {
            0
        } else {
            decodeUtf16(str, 0)
        }
        val expectedLength = if (codePoint < MIN_SUPPLEMENTAL_CP) 1 else 2
        if (str.length != expectedLength) {
            return Fail
        }
        return Value(codePoint, TInt)
    }

    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = MkType2.result(WKT.intType2, WKT.bubbleType2).get(),
            hasThisFormal = false,
            requiredInputTypes = listOf(
                MkType2(WKT.listTypeDefinition).actuals(listOf(WKT.stringType2)).get(),
                // List<Never<Empty>>
                MkType2(WKT.listTypeDefinition).actuals(
                    listOf(
                        MkType2(WKT.neverTypeDefinition).actuals(listOf(WKT.emptyType2)).get(),
                    ),
                ).get(),
            ),
        ),
    )
}

private object ListifyFn :
    BuiltinFun(
        listBuiltinName.builtinKey,
        run {
            // fn <T>(...: T): List<T>
            val (defT, typeT) = makeTypeFormal(
                listBuiltinName.builtinKey,
                "T",
            )
            Signature2(
                returnType2 = MkType2(WKT.listTypeDefinition)
                    .actuals(listOf(typeT))
                    .get(),
                requiredInputTypes = listOf(),
                hasThisFormal = false,
                restInputsType = typeT,
                typeFormals = listOf(defT),
            )
        },
    ),
    PureCallableValue {

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val elements = mutableListOf<Value<*>>()
        var i = 0
        val n = args.size
        if (i < n && args.key(i) == typeSymbol) {
            // type=... specifies the element type, not an element.
            i += 1
        }
        while (i < n) {
            if (args.key(i) != null) { return Fail } // TODO: explain
            elements.add(args[i])
            i += 1
        }
        return Value(elements, TList)
    }

    override val callMayFailPerSe: Boolean get() = false

    override val builtinOperatorId: BuiltinOperatorId get() = BuiltinOperatorId.Listify
}

/**
 * A placeholder that may be substituted by backends for functional tests.
 * TODO: replace with `console.log` once we have member access.
 *
 * <!-- snippet: builtin/print -->
 * # `print`
 * Logs a message for debugging purposes.
 *
 * Libraries do not own STDOUT, so should not write their primary output using this mechanism.
 * Instead take an output channel as a parameter if you're writing code whose job is to emit
 * textual or binary output to a file descriptor.
 */
private object PrintFn : BuiltinFun(
    "print",
    Signature2(
        returnType2 = WKT.voidType2,
        requiredInputTypes = listOf(WKT.stringType2),
        hasThisFormal = false,
    ),
) {
    override val isPure: Boolean = false // print has a side effect

    override val builtinOperatorId get() = BuiltinOperatorId.Print

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        if (interpMode != InterpMode.Full) { return Fail }
        val (message) = args.unpackPositioned(1, cb) ?: return Fail
        val text = TString.unpackOrNull(message) ?: run {
            return@invoke cb.fail(MessageTemplate.ExpectedValueOfType, values = listOf(TString, message))
        }

        when (val impl = cb.getFeatureImplementation(OPTIONAL_PRINT_FEATURE_KEY)) {
            is Fail -> { // This default behavior works well for many cases but not for the repl
                cb.logSink.log(
                    level = Log.Info,
                    template = MessageTemplate.StandardOut,
                    pos = cb.pos,
                    values = listOf(text),
                )
            }
            is Value<*> -> {
                // Even if the delegate call fails, this wrapper succeeds per the signature above.
                (TFunction.unpack(impl) as CallableValue).invoke(args, cb, interpMode)
            }
        }
        return void
    }
}

const val OPTIONAL_PRINT_FEATURE_KEY: InternalFeatureKey = "print"

/**
 * Express that an expression is known to be not null. This should only be inserted by the compiler, and it primarily
 * exists for helping the typer.
 */
object NotNullFn : NamedBuiltinFun, CallableValue {
    override val builtinOperatorId get() = BuiltinOperatorId.NotNull
    override val name: String get() = "notNull"
    val sig = run {
        val (defT, typeT) = makeTypeFormal(name, "T")
        val typeTOrNull = typeT.withNullity(Nullity.OrNull)
        Signature2(
            returnType2 = typeT,
            requiredInputTypes = listOf(typeTOrNull),
            hasThisFormal = false,
            typeFormals = listOf(defT),
        )
    }
    override val sigs = listOf(sig)

    // We might throw/panic because of assertion, but semantics are officially just an identity function.
    override val callMayFailPerSe get() = false

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (arg) = args.unpackPositionedOr(1, cb) {
            return@invoke it
        }
        // We should only insert this function internally when known not to be `null`, but assert for safety in interp.
        // Backends typically can skip this assertion, depending on their semantics.
        check(arg.typeTag != TNull)
        return arg
    }
}

/**
 * Namespace well-known functions.
 */
object BuiltinFuns {
    val plusIntIntFn: CallableValue = IntIntToIntFun(
        "+",
        BuiltinOperatorId.PlusIntInt,
    ) { a, b -> a + b }

    val plusLongLongFn: CallableValue = LongLongToLongFun(
        "+",
        BuiltinOperatorId.PlusIntInt64,
    ) { a, b -> a + b }

    val plusIntFn: CallableValue = IntToIntFun(
        "+",
        null,
    ) { a -> a }

    val plusLongFn: CallableValue = LongToLongFun(
        "+",
        null,
    ) { a -> a }

    val plusFloatFloatFn: CallableValue = FloatFloatToFloatFun(
        "+",
        BuiltinOperatorId.PlusFltFlt,
    ) { a, b -> a + b }

    val plusFloatFn: CallableValue = FloatToFloatFun(
        "+",
        null,
    ) { a -> a }

    val minusIntIntFn: CallableValue = IntIntToIntFun(
        "-",
        BuiltinOperatorId.MinusIntInt,
    ) { a, b -> a - b }

    val minusIntFn: CallableValue = IntToIntFun(
        "-",
        BuiltinOperatorId.MinusInt,
    ) { a -> -a }

    val minusLongLongFn: CallableValue = LongLongToLongFun(
        "-",
        BuiltinOperatorId.MinusIntInt64,
    ) { a, b -> a - b }

    val minusLongFn: CallableValue = LongToLongFun(
        "-",
        BuiltinOperatorId.MinusInt64,
    ) { a -> -a }

    val minusFloatFloatFn: CallableValue = FloatFloatToFloatFun(
        "-",
        BuiltinOperatorId.MinusFltFlt,
    ) { a, b -> a - b }

    val minusFloatFn: CallableValue = FloatToFloatFun(
        "-",
        BuiltinOperatorId.MinusFlt,
    ) { a -> -a }

    val timesIntIntFn: CallableValue =
        IntIntToIntFun("*", BuiltinOperatorId.TimesIntInt) { a, b ->
            a * b
        }

    val timesLongLongFn: CallableValue =
        LongLongToLongFun("*", BuiltinOperatorId.TimesIntInt64) { a, b ->
            a * b
        }

    val timesFloatFloatFn: CallableValue =
        FloatFloatToFloatFun("*", BuiltinOperatorId.TimesFltFlt) { a, b ->
            a * b
        }

    val powFloatFloatFn: CallableValue =
        FloatFloatToFloatFun("**", BuiltinOperatorId.PowFltFlt) { a, b ->
            a.pow(b)
        }

    val ampIntIntFn: CallableValue = IntIntToIntFun(
        "&",
        BuiltinOperatorId.BitwiseAnd32,
    ) { a, b -> a and b }

    val ampLongLongFn: CallableValue = LongLongToLongFun(
        "&",
        BuiltinOperatorId.BitwiseAnd64,
    ) { a, b -> a and b }

    val barIntIntFn: CallableValue = IntIntToIntFun(
        "|",
        BuiltinOperatorId.BitwiseOr32,
    ) { a, b -> a or b }

    val barLongLongFn: CallableValue = LongLongToLongFun(
        "|",
        BuiltinOperatorId.BitwiseOr64,
    ) { a, b -> a or b }

    val bitInverseIntFn: CallableValue = IntToIntFun(
        "~",
        BuiltinOperatorId.BitwiseNegation32,
    ) { a -> a.inv() }

    val bitInverseLongFn: CallableValue = LongToLongFun(
        "~",
        BuiltinOperatorId.BitwiseNegation64,
    ) { a -> a.inv() }

    val bitXorIntIntFn: CallableValue = IntIntToIntFun(
        "^",
        BuiltinOperatorId.BitwiseXor32,
    ) { a, b -> a.xor(b) }

    val bitXorLongLongFn: CallableValue = LongLongToLongFun(
        "^",
        BuiltinOperatorId.BitwiseXor64,
    ) { a, b -> a.xor(b) }

    val shlIntIntFn: CallableValue = IntIntToIntFun(
        "<<",
        BuiltinOperatorId.BitwiseShl32,
    ) { a, b -> a.shl(b.and(I32_SHIFT_AMOUNT_MASK)) }

    val shlLongLongFn: CallableValue = LongIntToLongFun(
        "<<",
        BuiltinOperatorId.BitwiseShl64,
    ) { a, b -> a.shl(b.and(I64_SHIFT_AMOUNT_MASK)) }

    val shrIntIntFn: CallableValue = IntIntToIntFun(
        ">>",
        BuiltinOperatorId.BitwiseShr32,
    ) { a, b -> a.shr(b.and(I32_SHIFT_AMOUNT_MASK)) }

    val shrLongLongFn: CallableValue = LongIntToLongFun(
        ">>",
        BuiltinOperatorId.BitwiseShr64,
    ) { a, b -> a.shr(b.and(I64_SHIFT_AMOUNT_MASK)) }

    val uShrIntIntFn: CallableValue = IntIntToIntFun(
        ">>>",
        BuiltinOperatorId.BitwiseShrUnsigned32,
    ) { a, b -> a.ushr(b.and(I32_SHIFT_AMOUNT_MASK)) }

    val uShrLongIntFn: CallableValue = LongIntToLongFun(
        ">>>",
        BuiltinOperatorId.BitwiseShrUnsigned64,
    ) { a, b -> a.ushr(b.and(I64_SHIFT_AMOUNT_MASK)) }

    val notFn: CallableValue = NotFn

    val desugarLogicalAndFn: MacroValue = DesugarLogicalAnd
    val desugarLogicalOrFn: MacroValue = DesugarLogicalOr

    val angleFn: CallableValue = TypeAngleFn
    val fnTypeFn: CallableValue = FnTypeFn
    val orNullFn: CallableValue = OrNullFn
    val throwsFn: CallableValue = ThrowsFn
    val asFn: CallableValue = AsFunction
    val assertAsFn: CallableValue = AssertAsFunction
    val isFn: CallableValue = IsFunction
    val notNullFn: MacroValue = NotNullFn

    val squareBracketFn: MacroValue = SquareBracketFn

    val divIntIntFn: CallableValue = IntIntToIntFun(
        "/",
        BuiltinOperatorId.DivIntInt,
        fail = { _, divisor, cb ->
            when (divisor) {
                0 -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b -> a / b }

    val divLongLongFn: CallableValue = LongLongToLongFun(
        "/",
        BuiltinOperatorId.DivIntInt64,
        fail = { _, divisor, cb ->
            when (divisor) {
                0L -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b -> a / b }

    val divFloatFloatFn: CallableValue = FloatFloatToFloatFun(
        "/",
        BuiltinOperatorId.DivFltFlt,
        fail = { _, divisor, cb ->
            // Floating 0/0 is normally silently via NaN in most languages.
            // We can provide a NaN producing division operator, but it seems that, where a
            // language provides non-return-value failure affordances, default operators
            // should use them consistently.
            when (divisor) {
                0.0 -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b ->
        a / b
    }

    /** A specialization of the integer division where we know that the divisor is non-zero. */
    val divIntIntSafeFn: CallableValue = IntIntToIntFun(
        "/",
        BuiltinOperatorId.DivIntIntSafe,
    ) { a, b ->
        if (b == 0) { throw Panic() }
        a / b
    }

    /** A specialization of the integer division where we know that the divisor is non-zero. */
    val divLongLongSafeFn: CallableValue = LongLongToLongFun(
        "/",
        BuiltinOperatorId.DivIntInt64Safe,
    ) { a, b ->
        if (b == 0L) { throw Panic() }
        a / b
    }

    val modIntIntFn: CallableValue = IntIntToIntFun(
        "%",
        BuiltinOperatorId.ModIntInt,
        fail = { _, divisor, cb ->
            when (divisor) {
                0 -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b -> a % b }

    val modLongLongFn: CallableValue = LongLongToLongFun(
        "%",
        BuiltinOperatorId.ModIntInt64,
        fail = { _, divisor, cb ->
            when (divisor) {
                0L -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b -> a % b }

    val modFloatFloatFn: CallableValue = FloatFloatToFloatFun(
        "%",
        BuiltinOperatorId.ModFltFlt,
        fail = { _, divisor, cb ->
            // Floating 0/0 is normally silently via NaN in most languages.
            // We can provide a NaN producing division operator, but it seems that, where a
            // language provides non-return-value failure affordances, default operators
            // should use them consistently.
            when (divisor) {
                0.0 -> cb.fail(MessageTemplate.DivByZero)
                else -> null
            }
        },
    ) { a, b ->
        a % b
    }

    /**
     * A specialization of the integer division where we know that the divisor
     * is positive so does not lead to confusion between division and remainder.
     */
    val modIntIntSafeFn: CallableValue = IntIntToIntFun(
        "%",
        BuiltinOperatorId.ModIntIntSafe,
    ) { a, b ->
        if (b <= 0) { throw Panic() }
        a % b
    }

    /**
     * A specialization of the integer division where we know that the divisor
     * is positive so does not lead to confusion between division and remainder.
     */
    val modLongLongSafeFn: CallableValue = LongLongToLongFun(
        "%",
        BuiltinOperatorId.ModIntInt64Safe,
    ) { a, b ->
        if (b <= 0L) { throw Panic() }
        a % b
    }

    val ltIntFn: CallableValue = IntCompareFun("<", BuiltinOperatorId.LtIntInt) { cmp ->
        TBoolean.value(cmp < 0)
    }
    val vLtIntFn = Value(ltIntFn)

    val leIntFn: CallableValue = IntCompareFun("<=", BuiltinOperatorId.LeIntInt) { cmp ->
        TBoolean.value(cmp <= 0)
    }
    val vLeIntFn = Value(leIntFn)

    val gtIntFn: CallableValue = IntCompareFun(">", BuiltinOperatorId.GtIntInt) { cmp ->
        TBoolean.value(cmp > 0)
    }
    val vGtIntFn = Value(gtIntFn)

    val geIntFn: CallableValue = IntCompareFun(">=", BuiltinOperatorId.GeIntInt) { cmp ->
        TBoolean.value(cmp >= 0)
    }
    val vGeIntFn = Value(geIntFn)

    val cmpInt32Fn: CallableValue = Comparator(
        BuiltinOperatorId.CmpIntInt,
        WKT.intType2,
        TInt,
    )

    val cmpInt64Fn: CallableValue = Comparator(
        BuiltinOperatorId.CmpLongLong,
        WKT.int64Type2,
        TInt64,
    )

    val cmpFloat64Fn: CallableValue = Comparator(
        BuiltinOperatorId.CmpFltFlt,
        WKT.float64Type2,
        TFloat64,
    )

    val cmpStringFn: CallableValue = Comparator(
        BuiltinOperatorId.CmpStrStr,
        WKT.stringType2,
        TString,
    )

    val cmpBooleanFn: CallableValue = Comparator(
        BuiltinOperatorId.CmpBoolBool,
        WKT.booleanType2,
        TBoolean,
    )

    val eqIntFn: CallableValue = IntCompareFun(
        "==",
        BuiltinOperatorId.EqIntInt,
    ) { cmp ->
        TBoolean.value(cmp == 0)
    }
    val vEqIntFn = Value(eqIntFn)

    val eqInt64Fn: CallableValue = LongCompareFun(
        "==",
        BuiltinOperatorId.EqIntInt,
    ) { cmp ->
        TBoolean.value(cmp == 0)
    }

    val eqFloat64Fn: CallableValue = DoubleCompareFun(
        "==",
        BuiltinOperatorId.EqFltFlt,
    ) { cmp ->
        TBoolean.value(cmp == 0)
    }

    val eqStringFn: CallableValue = StringCompareFun(
        "==",
        BuiltinOperatorId.EqStrStr,
    ) { cmp ->
        TBoolean.value(cmp == 0)
    }

    val eqBooleanFn: CallableValue = BoolCompareFun(
        "==",
        BuiltinOperatorId.EqBoolBool,
    ) { cmp ->
        TBoolean.value(cmp == 0)
    }

    val strCatFn: NamedBuiltinFun = StrCatFn
    val strRawMacro: NamedBuiltinFun = StrRawMacro
    val charTagFn: NamedBuiltinFun = CharTagFn

    val listifyFn: NamedBuiltinFun = ListifyFn

    val setLocalFn: MacroValue = SetLocalFn

    val commaFn: CallableValue = CommaFn

    val await: MacroValue = AwaitFn
    val yield: MacroValue = YieldFn
    val async: MacroValue = AsyncFn

    val thisPlaceholder: MacroValue = This
    val getpFn: MacroValue = Getp
    val setpFn: MacroValue = Setp
    val getsFn: GetStaticOp = GetStatic
    val igetsFn: GetStaticOp = InternalGetStatic
    val pureVirtualFn: NamedBuiltinFun = PureVirtual
    val abstractPanicFn: NamedBuiltinFun = AbstractPanic
    val desugarPunFn: MacroValue = DesugarPun
    val coalesceMacro: NamedBuiltinFun = CoalesceMacro
    val whenMacro: NamedBuiltinFun = WhenMacro
    val regexLiteralMacro: NamedBuiltinFun = RegexLiteralMacro
    val assertMacro: NamedBuiltinFun = AssertMacro
    val testMacro: NamedBuiltinFun = TestMacro
    val dataFileMacro: NamedBuiltinFun = DataFileMacro

    val bubble: NamedBuiltinFun = BubbleFn
    val panic: NamedBuiltinFun = PanicFn
    val voidishPanic: NamedBuiltinFun = VoidishPanicFn

    val makeClosRec: MacroValue = MakeClosRec
    val getCR: CallableValue = GetCR
    val setCR: CallableValue = SetCR

    val print: NamedBuiltinFun = PrintFn

    val enumMacro: MacroValue = DesugarEnumMacro

    val preserveFn: MacroValue = PreserveFn
    val identityFn: SpecialFunction = IdentityFn
    val embeddedCommentFn: MacroValue = EmbeddedCommentFn
    val doPure: SpecialFunction = DoPureFn

    val vStrCatFn = Value(strCatFn)
    val vStrRawMacro = Value(strRawMacro)
    val vCharTagFn = Value(charTagFn)
    val vBubble = Value(bubble)
    val vPanic = Value(panic)
    val vVoidishPanic = Value(voidishPanic)
    val vCommaFn = Value(commaFn)
    val vNotFn = Value(notFn)
    val vDesugarLogicalAndFn = Value(desugarLogicalAndFn)
    val vDesugarLogicalOrFn = Value(desugarLogicalOrFn)
    val vAngleFn = Value(angleFn)
    val vFnTypeFn = Value(fnTypeFn)
    val vOrNullFn = Value(orNullFn)
    val vThrowsFn = Value(throwsFn)
    val vIsFn = Value(isFn)
    val vAsFn = Value(asFn)
    val vAssertAsFn = Value(assertAsFn)
    val vNotNullFn = Value(notNullFn)
    val vPrint = Value(print)
    val vEnumMacro = Value(enumMacro)
    val vSquareBracketFn = Value(squareBracketFn)
    val vCoalesceMacro = Value(coalesceMacro)
    val vWhenMacro = Value(whenMacro)
    val vRegexLiteralMacro = Value(regexLiteralMacro)
    val vAssertMacro = Value(assertMacro)
    val vTestMacro = Value(testMacro)
    val vDataFileMacro = Value(dataFileMacro)

    val vAwait = Value(await)
    val vYield = Value(yield)
    val vAsync = Value(async)
    val vListifyFn = Value(listifyFn)
    val vSetLocalFn = Value(setLocalFn)
    val vThis = Value(thisPlaceholder)
    val vGetp = Value(getpFn)
    val vSetp = Value(setpFn)
    val vGets = Value(getsFn)
    val vIGets = Value(igetsFn)
    val vPureVirtual = Value(pureVirtualFn)
    val vAbstractPanic = Value(abstractPanicFn)
    val vDesugarPun = Value(desugarPunFn)

    val vMakeClosRec = Value(makeClosRec)
    val vGetCR = Value(getCR)
    val vSetCR = Value(setCR)

    val vPreserveFn = Value(preserveFn)
    val vIdentityFn = Value(identityFn)
    val vEmbeddedCommentFn = Value(embeddedCommentFn)
    val vDoPure = Value(doPure)
}

fun makeTypeFormal2(
    fnName: String,
    nameSuffix: String,
    upperBounds: List<Type2>,
): Pair<TypeFormal, Type2> =
    makeTypeFormal(
        fnName, nameSuffix,
        upperBounds.map {
            hackMapNewStyleToOld(it.withNullity(Nullity.NonNull)) as NominalType
        },
    )

fun makeTypeFormal(
    fnName: String,
    nameSuffix: String,
    vararg upperBounds: NominalType,
): Pair<TypeFormal, Type2> = makeTypeFormal(
    fnName = fnName,
    nameSuffix = nameSuffix,
    upperBounds = upperBounds.toList(),
)

fun makeTypeFormal(
    fnName: String,
    nameSuffix: String,
    upperBounds: List<NominalType>,
): Pair<TypeFormal, Type2> {
    val nameKey = "$fnName$nameSuffix"
    val upperBoundsList = if (upperBounds.isEmpty()) {
        listOf(MkType.nominal(WKT.anyValueTypeDefinition))
    } else {
        upperBounds.toList()
    }
    val typeFormal = TypeFormal(
        Position(CoreCodeLocation, 0, 0),
        BuiltinName(nameKey),
        Symbol(nameKey),
        Variance.Invariant,
        AtomicCounter(),
        upperBounds = upperBoundsList,
    )
    return typeFormal to MkType2(typeFormal).get()
}

internal const val I32_SHIFT_AMOUNT_MASK = 0x1F
internal const val I64_SHIFT_AMOUNT_MASK = 0x3F
