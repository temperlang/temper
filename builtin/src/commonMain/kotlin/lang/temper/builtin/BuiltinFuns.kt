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
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
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
import lang.temper.type2.withNullity
import lang.temper.value.ActualValues
import lang.temper.value.BubbleFn
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallableValue
import lang.temper.value.ComparableTypeTag
import lang.temper.value.CoverFunction
import lang.temper.value.Fail
import lang.temper.value.HANDLER_SCOPE_FN_NAME
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InterpreterCallback
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
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
import lang.temper.value.catBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.interpolateSymbol
import lang.temper.value.listBuiltinName
import lang.temper.value.or
import lang.temper.value.postfixApplyName
import lang.temper.value.typeSymbol
import lang.temper.value.unpackOrFail
import lang.temper.value.unpackPositionedOr
import lang.temper.value.unpackValue
import lang.temper.value.valueContained
import lang.temper.value.void
import kotlin.math.pow
import lang.temper.type.WellKnownTypes as WKT

private fun fTypeTypeToBoolean(sides: TypeShape): Signature2 {
    val sidesAll = MkType2(sides).nullity(Nullity.OrNull).get()
    return Signature2(
        returnType2 = WKT.booleanType2,
        requiredInputTypes = listOf(sidesAll, sidesAll),
        hasThisFormal = false,
    )
}

private val fIntIntToBoolean = fTypeTypeToBoolean(WKT.intTypeDefinition)
private val fLongLongToBoolean = fTypeTypeToBoolean(WKT.int64TypeDefinition)
private val fDoubleDoubleToBoolean = fTypeTypeToBoolean(WKT.float64TypeDefinition)
private val fStringStringToBoolean = fTypeTypeToBoolean(WKT.stringTypeDefinition)

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

private val fLongLongToLongOrBubble = fLongLongToLong.copy(
    returnType2 = MkType2.result(WKT.int64Type2, WKT.bubbleType2).get(),
)

private val fLongToLong = Signature2(
    returnType2 = WKT.int64Type2,
    requiredInputTypes = listOf(WKT.int64Type2),
    hasThisFormal = false,
)

private val fBoolToBool = Signature2(
    returnType2 = WKT.booleanType2,
    requiredInputTypes = listOf(WKT.booleanType2),
    hasThisFormal = false,
)

// These need to be lambdas so that we can compare for reference equality below
private val twoIntsToNull = { _: Int, _: Int, _: InterpreterCallback -> null }
private val twoLongsToNull = { _: Long, _: Long, _: InterpreterCallback -> null }

private class IntCompareFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Int) -> Result,
) : BuiltinFun(name, fIntIntToBoolean), PureCallableValue {
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
) : BuiltinFun(name, fLongLongToBoolean), PureCallableValue {
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
) : BuiltinFun(name, fDoubleDoubleToBoolean), PureCallableValue {
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
) : BuiltinFun(name, fStringStringToBoolean), PureCallableValue {
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
    signature = if (fail === twoIntsToNull) { fIntIntToInt } else { fIntIntToIntOrBubble },
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

private class BoolToBoolFun(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    val f: (a: Boolean) -> Boolean,
) : BuiltinFun(name, fBoolToBool), PureCallableValue {
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a) = args.unpackPositioned(1, cb) ?: return Fail
        return Value(
            f(TBoolean.unpack(a)),
            TBoolean,
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

/** Base for `==` and `!=`. */
private class EqualityFunction(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId,
    val invert: Boolean,
) : BuiltinFun(name, eqSig), PureCallableValue {
    // TODO: maybe turn != into a late macro that does a != b     ->    !(a == b)
    // to make life easier for backend implementors

    /** Unlike `<` this cannot fail because the operands are not required to be orderable. */
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a, b) = args.unpackPositioned(2, cb) ?: return Fail
        // TODO: For class instances, maybe do something like
        //    a.equals(b) && b.equals(a)
        // See below.
        return TBoolean.value((a == b) != invert)
    }

    companion object {
        private val eqSig = Signature2(
            returnType2 = WKT.booleanType2,
            requiredInputTypes = listOf(WKT.anyValueOrNullType2, WKT.anyValueOrNullType2),
            hasThisFormal = false,
        )
    }
}

/** Base for `<=>` and `<` and `>` and `<=` and `>=`. */
private class ComparisonFunction(
    name: String,
    override val builtinOperatorId: BuiltinOperatorId,
    returnType: Type2,
    val f: (comparison: Int) -> Result,
) : BuiltinFun(name, cmpSig(returnType)), PureCallableValue {
    override val callMayFailPerSe: Boolean get() = false

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val (a, b) = args.unpackPositioned(2, cb) ?: return Fail
        val aTypeTag = a.typeTag
        val bTypeTag = b.typeTag
        compareHandlingNull(aTypeTag, bTypeTag) { return@invoke Value(it, TInt) }
        if (aTypeTag == bTypeTag) {
            if (aTypeTag is ComparableTypeTag) {
                return f(aTypeTag.compareBoth(a, b))
            } else if ((aTypeTag as? TClass)?.typeShape == WKT.stringIndexTypeDefinition) {
                // Hack to support direct comparison of StringIndex instances.
                // TODO Generalize notion of comparison support for classes.
                return when (val cmp = StringIndexSupport.compare(args, cb, interpMode)) {
                    is Value<*> -> f(TInt.unpack(cmp))
                    else -> cmp
                }
            }
        }
        val logEntry = LogEntry(
            level = Log.Error,
            template = MessageTemplate.Incomparable,
            pos = cb.pos,
            values = listOf(a.typeTag, b.typeTag),
        )
        cb.explain(logEntry)
        return Fail(logEntry)
    }

    companion object {
        private fun cmpSig(returnType: Type2) = run {
            val (typeFormalT) = makeTypeFormal("cmp", "T")
            val typeTOrNull = MkType2(typeFormalT).canBeNull().get()
            Signature2(
                returnType2 = returnType,
                requiredInputTypes = listOf(typeTOrNull, typeTOrNull),
                hasThisFormal = false,
                typeFormals = listOf(typeFormalT),
            )
        }
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
private object StrCatFn :
    BuiltinFun(
        catBuiltinName.builtinKey,
        Signature2(
            returnType2 = WKT.stringType2,
            requiredInputTypes = listOf(),
            hasThisFormal = false,
            restInputsType = WKT.stringType2,
        ),
    ),
    PureCallableValue {
    // TODO: once we have varargs sigs, define a sig.

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

private object InterpolateMacro : BuiltinMacro(interpolateSymbol.text, null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        // Be lenient here because we control all calling of this macro. It's not even in a user environment.
        macroEnv.call?.incoming?.let incoming@{ incoming ->
            val edgeIndex = incoming.edgeIndex
            edgeIndex >= 0 || return@incoming
            incoming.source!!.replace(edgeIndex..edgeIndex) {
                // Gather interpolated values list ...
                val values = buildList {
                    // ... while already building string template content list.
                    Call {
                        V(BuiltinFuns.vListifyFn)
                        val args = macroEnv.args
                        // Init expectations ensure we start with string template content.
                        val stringContent = StringBuilder()
                        fun addStringArg() {
                            V(Value(stringContent.toString(), TString))
                            stringContent.clear()
                        }
                        for (index in args.indices) {
                            val arg = args.valueTree(index)
                            when (args.key(index)) {
                                interpolateSymbol -> {
                                    addStringArg()
                                    add(arg)
                                }
                                else -> {
                                    // We expect only string literal content except for interpolated values as above.
                                    stringContent.append(TString.unpack(arg.valueContained!!))
                                }
                            }
                        }
                        // Technically can be inferred when empty and absent, but include it for consistency.
                        addStringArg()
                    }
                }
                Call {
                    V(BuiltinFuns.vListifyFn)
                    for (value in values) {
                        Replant(freeTree(value))
                    }
                }
            }
        }
        return NotYet
    }
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
        if (i < n && args.key(0) == typeSymbol) {
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
 * Turns failure into success while capturing failure state in a temporary variable.
 *
 *     pr(t#0, x)
 *
 * performs operation *x* and sets t#0 to `true` if it [Fail]ed.
 * Otherwise, it sets *x* to `false` and produces *x*'s result unchanged.
 *
 * The name stands for "Handled Scope" a subset of a function's code for which we need to handle
 * failure.
 * ["Exception Handling in CLU" by Liskov, Snyder
 * ](https://www.cs.tufts.edu/~nr/cs257/archive/barbara-liskov/exceptions.pdf)
 * coins related terms:
 *
 * > The *handler table* ... contains an entry for each handler in the procedure.
 * > An entry contains the following information:
 * > 1. ...
 * > 2. a pair of values defining the *scope of the handler*, that is, the object code
 * >    corresponding to the statement to which the handler is attached,
 * > 3. ...
 *
 * This corresponds to similar concepts in more recent languages' specifications:
 *
 * > Each method in the Java Virtual Machine may be associated with zero or more exception handlers.
 * > An *exception handler specifies the range of offsets into the Java Virtual Machine code*
 * > implementing the method for which the exception handler is active, describes the type of
 * > exception that the exception handler is able to handle, and specifies the location of the code
 * > that is to handle that exception.
 *
 * See also [lang.temper.frontend.MagicSecurityDust].
 */
private object HandlerScopeFn : SpecialFunction, NamedBuiltinFun {
    override val name: String get() = HANDLER_SCOPE_FN_NAME
    override val sigs: List<Signature2> = run {
        val (typeFormalT, typeT) = makeTypeFormal(name, "T")
        listOf(
            Signature2(
                returnType2 = typeT,
                requiredInputTypes = listOf(
                    typeT, // Expects a left name
                    typeT,
                ),
                hasThisFormal = false,
                typeFormals = listOf(typeFormalT),
            ),
        )
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        if (interpMode == InterpMode.Partial) {
            return Fail
        }
        val args = macroEnv.args
        val failureVariableName = args.rawTreeList[0] as? LeftNameLeaf
            ?: return Fail
        val result = args.evaluate(1, interpMode)
        val failed = when (result) {
            is Fail -> true
            NotYet -> return NotYet
            is Value<*> -> false
        }
        when (macroEnv.setLocal(failureVariableName, TBoolean.value(failed))) {
            is Fail -> {
                // Bad things happen if we fail to set a failure bit and then continue.
                macroEnv.explain(
                    MessageTemplate.CouldNotStoreFailureBit,
                    macroEnv.pos,
                    emptyList(),
                )
                throw Panic()
            }
            is Value<*> -> {}
        }
        if (result is Fail) {
            // Stash this away for later memory.
            macroEnv.environment.declarationMetadata(failureVariableName.content)?.fail = result
        }
        return result.or { void }
    }

    override val assignsArgumentOne get() = true

    override val callMayFailPerSe: Boolean get() = false
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
        if (cb.stage != Stage.Run) { return Fail }
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
        // We should only insert this function internally when known not to be null, but assert for safety in interp.
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

    /**
     * <!-- snippet: builtin/+ -->
     * # `+`
     * The builtin `+` operator has four variants:
     * - *Infix* with two [snippet/type/Int32]s: signed addition
     * - *Prefix* with one [snippet/type/Int32]: numeric identity
     * - *Infix* with two [snippet/type/Float64]s: signed addition
     * - *Prefix* with one [snippet/type/Float64]: numeric identity
     *
     * ```temper
     * 1   + 2   == 3   &&
     * 1.0 + 2.0 == 3.0 &&
     * +1        == 1   &&
     * +1.0      == 1.0
     * ```
     *
     * As explained above, you cannot mix [snippet/type/Int32] and
     * [snippet/type/Float64] inputs:
     *
     * ```temper FAIL
     * 1 + 1.0
     * ```
     *
     * `+` does not work on [snippet/type/String]s.  Use [snippet/builtin/cat] instead.
     *
     * ```temper FAIL
     * "foo" + "bar"
     * ```
     */
    val plusFn = CoverFunction(
        listOf(plusIntIntFn, plusIntFn, plusLongLongFn, plusLongFn, plusFloatFloatFn, plusFloatFn),
    )

    /**
     * <!-- snippet: builtin/- -->
     * # `-`
     * The builtin `-` operator has four variants like [snippet/builtin/+].
     *
     * ```temper
     * 3   - 1   == 2   &&
     * 3.0 - 1.0 == 2.0 &&
     * -3        <  0   &&
     * -3.0      <  0.0
     * ```
     *
     * As with `+`, you cannot mix [snippet/type/Int32] and [snippet/type/Float64] inputs:
     *
     * ```temper FAIL
     * 1 + 1.0
     * ```
     *
     * The `-` operator is left-associative:
     *
     * ```temper
     * 1 - 1 - 1 == (1 - 1) - 1 &&
     * 1 - 1 - 1 == -1
     * ```
     *
     * Since there is a [snippet/builtin/--] operator, `--x` is not a negation of a negation.
     *
     * ```temper
     * var x = 1;
     * +x == -(-x) &&  // double negation is identity
     * --x == 0        // but two adjacent `-` means pre-decrement
     * ```
     */
    val minusFn = CoverFunction(
        listOf(
            IntIntToIntFun(
                "-",
                BuiltinOperatorId.MinusIntInt,
            ) { a, b -> a - b },
            IntToIntFun(
                "-",
                BuiltinOperatorId.MinusInt,
            ) { a -> -a },
            LongLongToLongFun(
                "-",
                BuiltinOperatorId.MinusIntInt64,
            ) { a, b -> a - b },
            LongToLongFun(
                "-",
                BuiltinOperatorId.MinusInt64,
            ) { a -> -a },
            FloatFloatToFloatFun(
                "-",
                BuiltinOperatorId.MinusFltFlt,
            ) { a, b -> a - b },
            FloatToFloatFun(
                "-",
                BuiltinOperatorId.MinusFlt,
            ) { a -> -a },
        ),
    )

    /**
     * <!-- snippet: builtin/%2A : operator `*` -->
     * # Multiplication `*`
     * Infix `*` allows multiplying numbers.
     *
     * Given two [snippet/type/Int32]s it produces an *Int* and given two [snippet/type/Float64]s it
     * produces a *Float64*.
     *
     * ```temper
     * 3   * 4   == 12   &&
     * 3.0 * 4.0 == 12.0
     * ```
     */
    val timesFn = CoverFunction(
        listOf(
            IntIntToIntFun("*", BuiltinOperatorId.TimesIntInt) { a, b ->
                a * b
            },
            LongLongToLongFun("*", BuiltinOperatorId.TimesIntInt64) { a, b ->
                a * b
            },
            FloatFloatToFloatFun("*", BuiltinOperatorId.TimesFltFlt) { a, b ->
                a * b
            },
        ),
    )

    /**
     * <!-- snippet: builtin/%2A%2A : operator `**` -->
     * # Exponentiation `**`
     * Infix `**` allows raising one number to the power of another.
     *
     * Given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 3.0 **  2.0 == 9.0 &&
     * 4.0 ** -0.5 == 0.5
     * ```
     */
    val powFn = CoverFunction(
        listOf(
            FloatFloatToFloatFun("**", BuiltinOperatorId.PowFltFlt) { a, b ->
                a.pow(b)
            },
        ),
    )

    /**
     * <!-- snippet: builtin/& : `&` -->
     * # Operator `&`
     * The `&` operator can be applied in two ways:
     *
     * - To [snippet/type/Int32]s it acts as a [bitwise operator][snippet/bitwise-and].
     * - To types it produces an [intersection type][snippet/type/intersection-fn]
     *
     * ⎀ bitwise-and
     *
     * ⎀ type/intersection-fn
     * <!-- /snippet -->
     *
     * <!-- snippet: bitwise-and -->
     * # *Int* `&`
     *
     * Takes two [snippet/type/Int32]s and returns the *Int* that has any bit set
     * that is set in both input.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0010101 &
     *  0b1011011) ==
     *  0b0010001
     * ```
     */
    val ampFn = CoverFunction(
        listOf(
            IntIntToIntFun(
                "&",
                BuiltinOperatorId.BitwiseAnd,
            ) { a, b -> a and b },
            LongLongToLongFun(
                "&",
                BuiltinOperatorId.BitwiseAnd,
            ) { a, b -> a and b },
            TypeIntersectionFun,
        ),
    )

    /**
     * <!-- snippet: builtin/| : `|` -->
     * # Operator `|`
     * The `|` operator performs bitwise union.
     *
     * It takes two [snippet/type/Int32]s and returns the *Int32* that has any bit set
     * that is set in either input.
     *
     * ```temper
     * // Using binary number syntax
     * (0b0010101 |
     *  0b1011011) ==
     *  0b1011111
     * ```
     */
    val barFn = CoverFunction(
        listOf(
            IntIntToIntFun(
                "|",
                BuiltinOperatorId.BitwiseOr,
            ) { a, b -> a or b },
            LongLongToLongFun(
                "|",
                BuiltinOperatorId.BitwiseOr,
            ) { a, b -> a or b },
        ),
    )

    /**
     * <!-- snippet: builtin/! -->
     * # `!`
     * The prefix `!` operator performs [snippet/type/Boolean] inverse.
     *
     * `!`[snippet/builtin/false] is [snippet/builtin/true] and vice versa.
     */
    val notFn = CoverFunction(
        listOf(
            BoolToBoolFun(
                "!",
                BuiltinOperatorId.BooleanNegation,
            ) { a -> !a },
        ),
    )
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

    /**
     * <!-- snippet: builtin/%2F : operator `/` -->
     * # Division `/`
     * Infix `/` allows dividing numbers.
     *
     * Given two [snippet/type/Int32]s it produces an *Int* and given two [snippet/type/Float64]s it
     * produces a *Float64*.
     *
     * ```temper
     * 12   / 3   == 4    &&
     * 12.0 / 3.0 == 4.0
     * ```
     *
     * Integer division [rounds towards zero].
     *
     * ```temper
     *  7   / 2   ==  3   &&
     * -7   / 2   == -3   &&
     *  7.0 / 2.0 ==  3.5 &&
     * -7.0 / 2.0 == -3.5
     * ```
     *
     * Division by zero has [snippet/type/Bubble].
     *
     * ```temper
     * (1 / 0) orelse console.log("div by zero");
     * //!outputs "div by zero"
     * ```
     *
     * Float64 division by zero is a *Bubble* too.
     *
     * ```temper
     * console.log("${ (0.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * console.log("${ (1.0 /  0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * console.log("${ (1.0 / -0.0).toString() orelse "Bubble" }"); //!outputs "Bubble"
     * ```
     *
     * [IEEE-754]: https://en.wikipedia.org/wiki/IEEE_754
     * [rounds towards zero]: https://en.wikipedia.org/wiki/Rounding#Rounding_toward_zero
     */
    val divFn = CoverFunction(
        listOf(
            divIntIntFn,
            divLongLongFn,
            FloatFloatToFloatFun(
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
            },
        ),
    )

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

    /**
     * <!-- snippet: builtin/%25 : operator `%` -->
     * # Remainder `%`
     * Given two [snippet/type/Int32]s it produces an *Int*
     * and given two [snippet/type/Float64]s it produces a *Float64*.
     *
     * ```temper
     * 13   % 3   == 1    &&
     * 13.0 % 3.0 == 1.0
     * ```
     *
     * Division by Zero [bubbles][snippet/type/Bubble]
     * ```temper
     * (1 % 0) orelse console.log("mod by zero");
     * //!outputs "mod by zero"
     * ```
     * ```temper
     * (1.0 % 0.0) orelse console.log("mod by zero");
     * //!outputs "mod by zero"
     * ```
     *
     */
    val modFn = CoverFunction(
        listOf(
            modIntIntFn,
            modLongLongFn,
            FloatFloatToFloatFun(
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
            },
        ),
    )

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

    /**
     * <!-- snippet: builtin/< -->
     * # `<`
     * `a < b` is [snippet/builtin/true] when *a* orders before *b*, and is a compile-time error
     * if the two are not mutually comparable.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    val lessThanFn = CoverFunction(
        listOf(
            IntCompareFun(
                "<",
                BuiltinOperatorId.LtIntInt,
            ) { cmp ->
                TBoolean.value(cmp < 0)
            },
            LongCompareFun(
                "<",
                BuiltinOperatorId.LtIntInt,
            ) { cmp ->
                TBoolean.value(cmp < 0)
            },
            DoubleCompareFun(
                "<",
                BuiltinOperatorId.LtFltFlt,
            ) { cmp ->
                TBoolean.value(cmp < 0)
            },
            StringCompareFun(
                "<",
                BuiltinOperatorId.LtStrStr,
            ) { cmp ->
                TBoolean.value(cmp < 0)
            },
        ),
        otherwise = ComparisonFunction(
            "<",
            BuiltinOperatorId.LtGeneric,
            WKT.booleanType2,
        ) { d -> TBoolean.value(d < 0) },
    )

    /**
     * <!-- snippet: builtin/<= -->
     * # `<=`
     * `a <= b` is [snippet/builtin/true] when *a* orders with or before *b*, and is a compile-time
     * error if the two are not mutually comparable.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    val lessEqualsFn = CoverFunction(
        listOf(
            IntCompareFun(
                "<=",
                BuiltinOperatorId.LeIntInt,
            ) { cmp ->
                TBoolean.value(cmp <= 0)
            },
            LongCompareFun(
                "<=",
                BuiltinOperatorId.LeIntInt,
            ) { cmp ->
                TBoolean.value(cmp <= 0)
            },
            DoubleCompareFun(
                "<=",
                BuiltinOperatorId.LeFltFlt,
            ) { cmp ->
                TBoolean.value(cmp <= 0)
            },
            StringCompareFun(
                "<=",
                BuiltinOperatorId.LeStrStr,
            ) { cmp ->
                TBoolean.value(cmp <= 0)
            },
        ),
        otherwise = ComparisonFunction(
            "<=",
            BuiltinOperatorId.LeGeneric,
            WKT.booleanType2,
        ) { d -> TBoolean.value(d <= 0) },
    )

    /**
     * <!-- snippet: builtin/> -->
     * # `>`
     * `a > b` is [snippet/builtin/true] when *a* orders after *b*, and is a compile-time
     * error if the two are not mutually comparable.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    val greaterThanFn = CoverFunction(
        listOf(
            IntCompareFun(
                ">",
                BuiltinOperatorId.GtIntInt,
            ) { cmp ->
                TBoolean.value(cmp > 0)
            },
            LongCompareFun(
                ">",
                BuiltinOperatorId.GtIntInt,
            ) { cmp ->
                TBoolean.value(cmp > 0)
            },
            DoubleCompareFun(
                ">",
                BuiltinOperatorId.GtFltFlt,
            ) { cmp ->
                TBoolean.value(cmp > 0)
            },
            StringCompareFun(
                ">",
                BuiltinOperatorId.GtStrStr,
            ) { cmp ->
                TBoolean.value(cmp > 0)
            },
        ),
        otherwise = ComparisonFunction(
            ">",
            BuiltinOperatorId.GtGeneric,
            WKT.booleanType2,
        ) { d -> TBoolean.value(d > 0) },
    )

    /**
     * <!-- snippet: builtin/>= -->
     * # `>=`
     * `a >= b` is [snippet/builtin/true] when *a* orders after or with *b*, and is a compile-time
     * error if the two are not mutually comparable.
     *
     * See the [snippet/general-comparison/algo] for details of how they are compiled and
     * especially the [snippet/general-comparison/caveats].
     */
    val greaterEqualsFn = CoverFunction(
        listOf(
            IntCompareFun(
                ">=",
                BuiltinOperatorId.GeIntInt,
            ) { cmp ->
                TBoolean.value(cmp >= 0)
            },
            LongCompareFun(
                ">=",
                BuiltinOperatorId.GeIntInt,
            ) { cmp ->
                TBoolean.value(cmp >= 0)
            },
            DoubleCompareFun(
                ">=",
                BuiltinOperatorId.GeFltFlt,
            ) { cmp ->
                TBoolean.value(cmp >= 0)
            },
            StringCompareFun(
                ">=",
                BuiltinOperatorId.GeStrStr,
            ) { cmp ->
                TBoolean.value(cmp >= 0)
            },
        ),
        otherwise = ComparisonFunction(
            ">=",
            BuiltinOperatorId.GeGeneric,
            WKT.booleanType2,
        ) { d -> TBoolean.value(d >= 0) },
    )

    /**
     * <!-- snippet: builtin/== -->
     * # `==`
     * `a == b` is the default equivalence operation.
     *
     * Two values are equivalent if they have the same type-tag and the same content.
     *
     * [issue#36]: custom equivalence and default equivalence for record classes
     */
    val equalsFn = CoverFunction(
        listOf(
            IntCompareFun(
                "==",
                BuiltinOperatorId.EqIntInt,
            ) { cmp ->
                TBoolean.value(cmp == 0)
            },
            LongCompareFun(
                "==",
                BuiltinOperatorId.EqIntInt,
            ) { cmp ->
                TBoolean.value(cmp == 0)
            },
            DoubleCompareFun(
                "==",
                BuiltinOperatorId.EqFltFlt,
            ) { cmp ->
                TBoolean.value(cmp == 0)
            },
            StringCompareFun(
                "==",
                BuiltinOperatorId.EqStrStr,
            ) { cmp ->
                TBoolean.value(cmp == 0)
            },
        ),
        otherwise = EqualityFunction(
            "==",
            BuiltinOperatorId.EqGeneric,
            invert = false,
        ),
    )

    /**
     * <!-- snippet: builtin/!= -->
     * # `!=`
     * `a != b` is the [snippet/type/Boolean] inverse of [snippet/builtin/==]
     */
    val notEqualsFn = CoverFunction(
        listOf(
            IntCompareFun(
                "!=",
                BuiltinOperatorId.NeIntInt,
            ) { cmp ->
                TBoolean.value(cmp != 0)
            },
            LongCompareFun(
                "!=",
                BuiltinOperatorId.NeIntInt,
            ) { cmp ->
                TBoolean.value(cmp != 0)
            },
            DoubleCompareFun(
                "!=",
                BuiltinOperatorId.NeFltFlt,
            ) { cmp ->
                TBoolean.value(cmp != 0)
            },
            StringCompareFun(
                "!=",
                BuiltinOperatorId.NeStrStr,
            ) { cmp ->
                TBoolean.value(cmp != 0)
            },
        ),
        otherwise = EqualityFunction(
            "!=",
            BuiltinOperatorId.NeGeneric,
            invert = true,
        ),
    )

    /**
     * <!-- snippet: builtin/<=> -->
     * # `<=>`
     * `a <=> b` results in an [Int][snippet/type/Int32] based on whether *a* orders before, after, or
     * with *b*, and is a compile-time error if the two are not mutually comparable.
     *
     * - `a <=> b` is `-1` if *a* orders **before** *b*
     * - `a <=> b` is `0` if *a* orders **with** *b*
     * - `a <=> b` is `1` if *a* orders **after** *b*
     *
     * ```temper
     * (   42 <=>   123) == -1 &&  //    42 orders before   123
     * (  1.0 <=>   1.0) == 0  &&  //   1.0 orders with     1.0
     * ("foo" <=> "bar") == 1      // "foo" orders after  "bar"
     * ```
     *
     * ⎀ general-comparison/algo
     *
     * ⎀ general-comparison/caveats
     *
     * <!-- snippet: general-comparison/algo -->
     * # General comparison algorithm
     * The general comparison algorithm is designed to allow for easy structural comparison of
     * data values that work the same regardless of target language.
     *
     * [snippet/type/Int32]s are compared based on their position on the number line.
     * No surprises here.
     *
     * ```temper
     * -1 < 0 && 0 < 1 && 1 < 2
     * ```
     *
     * [snippet/type/Float64]s are also compared numerically.
     *
     * ```temper
     * -1.0 < 0.0 && 0.0 < 1.0 && 1.0 < 2.0
     * ```
     *
     * But the default comparison operators are meant to support structural comparison of records
     * so see also [caveats][snippet/general-comparison/caveats] for how *Float64* ordering differs
     * from other languages.
     *
     * [snippet/type/String]s are compared lexicographically based on their code-points.
     *
     * ```temper
     * "foo" > "bar"
     * ```
     *
     * See also [caveats][snippet/general-comparison/caveats] for *String* related ordering.
     *
     * ## Custom comparison for classes
     *
     * [issue#37]: custom comparison for classes.
     *
     * <!-- snippet: general-comparison/caveats -->
     * # General Comparison Caveats
     *
     * ## String Ordering Caveats
     * [snippet/type/String] ordering based on code-points means that [supplementary code-points]
     * (code-points greater than U+10000) sort higher than all [basic plane] code-points,
     *
     * ```temper
     * "\u{10000}" > "\u{FFFF}" // Hex code-point escapes
     * ```
     *
     * Developers used to lexicographic [UTF-16] might be surprised since UTF-16 ordering
     * treats each supplementary code-point as two [surrogate]s in the range \[0xD800, 0xDFFF\].
     * The first string above would be "\uD800\uDC00" if Temper string literals supported
     * surrogate pairs. In some languages, that might compare as less than "\u{FFFF}", but
     * Temper views all strings in terms of full code-points, or more precisely, in terms of
     * Unicode [scalar value]s, which exclude surrogate codes.
     *
     * ## Float64 Ordering Caveats
     *
     * ⎀ float64-comparison-details -heading
     *
     * [basic plane]: https://unicode.org/glossary/#basic_multilingual_plane
     * [scalar value]: https://unicode.org/glossary/#unicode_scalar_value
     * [supplementary code-points]: https://unicode.org/glossary/#supplementary_code_point
     * [surrogate]: https://unicode.org/glossary/#surrogate_code_point
     * [UTF-16]: https://unicode.org/glossary/#UTF_16
     */
    @Suppress("KDocUnresolvedReference")
    val cmpFn: NamedBuiltinFun = ComparisonFunction(
        "<=>",
        BuiltinOperatorId.CmpGeneric,
        WKT.intType2,
    ) { d -> Value(d, TInt) }

    val interpolateMacro: NamedBuiltinFun = InterpolateMacro
    val strCatFn: NamedBuiltinFun = StrCatFn
    val strCatMacro: NamedBuiltinFun = StrCatMacro
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
    val desugarPunFn: MacroValue = DesugarPun
    val coalesceMacro: NamedBuiltinFun = CoalesceMacro
    val whenMacro: NamedBuiltinFun = WhenMacro
    val regexLiteralMacro: NamedBuiltinFun = RegexLiteralMacro
    val assertMacro: NamedBuiltinFun = AssertMacro
    val testMacro: NamedBuiltinFun = TestMacro

    val handlerScope: MacroValue = HandlerScopeFn
    val bubble: NamedBuiltinFun = BubbleFn
    val panic: NamedBuiltinFun = PanicFn

    val makeClosRec: MacroValue = MakeClosRec
    val getCR: CallableValue = GetCR
    val setCR: CallableValue = SetCR

    val print: NamedBuiltinFun = PrintFn

    val enumMacro: MacroValue = DesugarEnumMacro

    val preserveFn: MacroValue = PreserveFn
    val identityFn: SpecialFunction = IdentityFn
    val embeddedCommentFn: MacroValue = EmbeddedCommentFn

    val vStrCatFn = Value(strCatFn)
    val vStrCatMacro = Value(strCatMacro)
    val vStrRawMacro = Value(strRawMacro)
    val vCharTagFn = Value(charTagFn)
    val vBubble = Value(bubble)
    val vPanic = Value(panic)
    val vCmp = Value(cmpFn)
    val vCommaFn = Value(commaFn)
    val vMinusFn = Value(minusFn)
    val vPlusFn = Value(plusFn)
    val vAmpFn = Value(ampFn)
    val vBarFn = Value(barFn)
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

    val vPostfixApply = Value(DesugarPostfixOperatorMacro(postfixApplyName.builtinKey))
    val vHandlerScope = Value(handlerScope)
    val vInterpolateMacro = Value(interpolateMacro)
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
    val vDesugarPun = Value(desugarPunFn)

    val vMakeClosRec = Value(makeClosRec)
    val vGetCR = Value(getCR)
    val vSetCR = Value(setCR)

    val vPreserveFn = Value(preserveFn)
    val vIdentityFn = Value(identityFn)
    val vEmbeddedCommentFn = Value(embeddedCommentFn)
}

internal fun makeTypeFormal(
    fnName: String,
    nameSuffix: String,
    vararg upperBounds: NominalType,
): Pair<TypeFormal, Type2> {
    val nameKey = "$fnName$nameSuffix"
    val upperBoundsList = if (upperBounds.isEmpty()) {
        listOf(MkType.nominal(WKT.anyValueTypeDefinition))
    } else {
        upperBounds.toList()
    }
    val typeFormal = TypeFormal(
        Position(ImplicitsCodeLocation, 0, 0),
        BuiltinName(nameKey),
        Symbol(nameKey),
        Variance.Invariant,
        AtomicCounter(),
        upperBounds = upperBoundsList,
    )
    return typeFormal to MkType2(typeFormal).get()
}
