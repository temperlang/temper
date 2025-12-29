package lang.temper.value

import lang.temper.common.AppendingTextOutput
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutToks
import lang.temper.format.TextOutputTokenSink
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.LogEntry
import lang.temper.name.Symbol

/** The result of partially evaluating an expression. */
sealed class PartialResult : StayReferrer, Structured, TokenSerializable

/** The expression does not yet have a well-defined result. */
object NotYet : PartialResult(), Stayless {
    override fun destructure(structureSink: StructureSink) =
        structureSink.value(OutToks.notYetWord.text)

    override fun toString() = OutToks.notYetWord.text

    override fun renderTo(tokenSink: TokenSink) = tokenSink.emit(OutToks.notYetWord)
}

/**
 * The result of evaluating an expression in the interpreter.
 */
sealed class Result : PartialResult(), Stayless

/**
 * The result of an expression that succeeded.
 *
 * In Temper's minimal runtime, a value is a pair of:
 * - A state vector consisting of zero or more pointer bits and zero or more non-pointer bits.
 * - A type tag.
 *
 * It's a goal of the type checker to identify most cases where the type tag is statically
 * knowable and so can be dropped.
 */
data class Value<T : Any>(
    val stateVector: T,
    val typeTag: TypeTag<T>,
) : Result() {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("stateVector", Hints.n) {
            typeTag.destructureValue(stateVector, this)
        }
        key("typeTag", Hints.n) { value(typeTag) }
        key("abbrev", Hints.su) {
            value(this@Value.toString())
        }
    }

    override fun renderTo(tokenSink: TokenSink) = renderTo(tokenSink, false)

    fun renderTo(tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        if (stateVector is TokenSerializable) {
            stateVector.renderTo(tokenSink)
        } else {
            typeTag.renderValue(stateVector, tokenSink, typeInfoIsRedundant)
        }
    }

    override fun toString() = toStringViaBuilder { sb ->
        stringify(sb, true)
        sb.append(": ")
        sb.append(typeTag)
    }

    fun stringify(out: Appendable, typeInfoIsRedundant: Boolean = false) {
        TemperFormattingHints.makeFormattingTokenSink(
            TextOutputTokenSink(AppendingTextOutput(out)),
            singleLine = true,
        ).use {
            renderTo(it, typeInfoIsRedundant)
        }
    }

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            typeTag.addStays(stateVector, s)
        }
    }

    /** Checks both [typeTag] and [stateVector] equality. See also [ComparableTypeTag] for ordering. */
    override fun equals(other: Any?) = when (other) {
        // Just go with same instance on type tag since we know those, but allow state vector value equality.
        is Value<*> -> typeTag === other.typeTag && stateVector == other.stateVector
        else -> false
    }

    /** No attempt to avoid collisions across type tags. Best to avoid mixing those as keys anyway. */
    override fun hashCode() = stateVector.hashCode()

    companion object {
        operator fun invoke(symbol: Symbol) = Value(symbol, TSymbol)
        operator fun invoke(reifiedType: ReifiedType) = Value(reifiedType, TType)
        operator fun invoke(macroValue: MacroValue) = Value(macroValue, TFunction)
    }
}

/**
 * Indicates a failure to produce a usable result.
 *
 * <!-- snippet: failure -->
 * # Failure
 * Expressions either evaluate to a value or fail to produce a usable result.
 * See also:
 *
 * - [snippet/builtin/Bubble] which provides type system support for tracking failure.
 * - [snippet/builtin/orelse] which allows recovering from failure.
 */
sealed class Fail(val info: LogEntry? = null) : Result() {
    override fun destructure(structureSink: StructureSink) = structureSink.nil()

    override fun toString() = OutToks.failWord.text

    override fun renderTo(tokenSink: TokenSink) = tokenSink.emit(OutToks.failWord)

    companion object : Fail() {
        operator fun invoke(info: LogEntry? = null): Fail = FailData(info)
    }
}

class FailData(info: LogEntry? = null) : Fail(info)

/** [Fail] if `this` is [Fail] else the result of [f]\(`this`\) */
inline fun (Result).and(f: (x: Value<*>) -> Result): Result = when (this) {
    is Fail -> this
    is Value<*> -> f(this)
}

/** [Fail] if `this` is [Fail] else the result of [f]\(`this`\) */
inline fun (PartialResult).and(f: (x: Value<*>) -> PartialResult): PartialResult = when (this) {
    is Fail, NotYet -> this
    is Value<*> -> f(this)
}

/** `this` if it is a [Value] else the result of [f]\(\). */
inline fun (Result).or(f: () -> Result): Result = when (this) {
    is Fail -> f()
    is Value<*> -> this
}

/** `this` if it is a [Value] else the result of [f]\(\). */
inline fun (PartialResult).or(f: () -> Result): Result = when (this) {
    NotYet, is Fail -> f()
    is Value<*> -> this
}

/** `this` if it is a [Value] or a [Fail] with info else the result of [f]\(\). */
inline fun (Result).infoOr(f: () -> Result): Result = when (this) {
    is Fail -> when (this.info) {
        null -> f()
        else -> this
    }
    is Value<*> -> this
}

/** `this` if it is a [Value] or a [Fail] with info else the result of [f]\(\). */
inline fun (PartialResult).infoOr(f: () -> Result): Result = when (this) {
    NotYet -> f()
    is Result -> this.infoOr(f)
}

/**
 * Fails if `this` is not a [Value] with the given type tag or else the result of applying
 * [action] to `this`'s state vector.
 */
inline fun <T : Any> (PartialResult).passedWithType(
    typeTag: TypeTag<T>,
    action: (stateVector: T) -> PartialResult,
): PartialResult {
    /*
    contract {
        callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
     */

    return when (this) {
        is Fail, NotYet -> this
        is Value<*> -> {
            if (this.typeTag == typeTag) {
                action(typeTag.unpack(this))
            } else {
                Fail
            }
        }
    }
}

val <T : Any> (Value<T>).stability get() = typeTag.stabilityOf(stateVector)
