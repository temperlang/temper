package lang.temper.value

import lang.temper.common.EnumRange
import lang.temper.common.ParseDouble
import lang.temper.common.SAFE_DOUBLE_FORMAT_STRING
import lang.temper.common.asciiTitleCase
import lang.temper.common.sprintf
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.temperEscaper
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.LogEntry
import lang.temper.name.BuiltinName
import lang.temper.name.ModularName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.stage.Stage
import lang.temper.type.FUNCTION_TYPE_NAME_TEXT
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes

/**
 * A coarse-grained type that may exist at runtime.
 * In contrast to fine-grained [static types][lang.temper.type.StaticType].
 *
 * <!-- snippet: type-tag -->
 * # Type Tags
 * Type tags are what determine whether a narrowing cast[^1] would be safe at runtime.
 *
 * Static types have more nuance than type tags.
 *
 * | Static Type  | Type Tag     |
 * | ------------ | ------------ |
 * | `C`          | `C`          |
 * | `Boolean`    | `Boolean`    |
 * | `List<Int>`  | `List`       |
 * | `fn(): Void` | `Function`   |
 *
 * As you can see, all function values have the same type tag, *Function*, and generic class's
 * corresponding type tag has the type parameters erased.
 *
 * [^1]: A narrowing cast is from a super-type to a sub-type.  For example, from [snippet/type/AnyValue] to `class C`.
 */
sealed class TypeTag<V : Any>(val name: ResolvedName) : Structured {
    constructor(nameText: String) : this(BuiltinName(nameText))

    open val names = listOf(name)

    /**
     * Assuming this type matches [Value.typeTag], returns the [Value.stateVector] cast to [V].
     * [Panic]s otherwise.
     */
    fun unpack(x: Value<*>) = if (x.typeTag.canUnpackAs(this)) {
        // Type-safe as long as concrete TypeTags are not parameterized
        @Suppress("UNCHECKED_CAST")
        (x as Value<V>).stateVector
    } else {
        throw Panic()
    }

    /**
     * Like [TypeTag.unpack] but returns `null` when the [Value.typeTag] does not match.
     */
    fun unpackOrNull(x: Value<*>?) = if (x != null && x.typeTag == this) {
        // Type-safe as long as concrete TypeTags are not parameterized
        @Suppress("UNCHECKED_CAST")
        (x as Value<V>).stateVector
    } else {
        null
    }

    open fun renderValue(value: V, tokenSink: TokenSink, typeInfoIsRedundant: Boolean = false) {
        if (value is TokenSerializable) {
            value.renderTo(tokenSink)
        } else {
            tokenSink.emit(OutputToken("$value", OutputTokenType.OtherValue))
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.value(toString())

    open fun destructureValue(
        value: V,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean = false,
    ) = if (value is Structured) {
        structureSink.value(value)
    } else {
        structureSink.value(
            toStringViaTokenSink {
                renderValue(value, it, typeInfoIsRedundant)
            },
        )
    }

    open fun stabilityOf(value: V): ValueStability = ValueStability.Unstable

    abstract fun addStays(value: V, s: StaySink)

    override fun toString() = "$name"

    open fun canUnpackAs(other: TypeTag<*>) = this == other

    open fun isCompatibleType(wellKnownTypeName: ResolvedName): Boolean {
        val defn = WellKnownTypes.withName(name) ?: return false
        return wellKnownTypeName in defn.rawSuperTypeNames
    }
}

sealed class ComparableTypeTag<V : Any>(
    nameText: String,
    val comparator: Comparator<V>,
) : TypeTag<V>(nameText)

/**
 * <!-- snippet: builtin/Boolean -->
 * # *Boolean*
 * The name `Boolean` refers to the builtin type, [snippet/type/Boolean].
 *
 * It has only two values:
 *
 * - [snippet/builtin/false]
 * - [snippet/builtin/true]
 *
 * ⎀ syntax/BooleanLiteral -heading
 */
object TBoolean : ComparableTypeTag<Boolean>("Boolean", naturalOrder()) {
    /**
     * <!-- snippet: builtin/false -->
     * # *false*
     * The value `false` of type [snippet/type/Boolean].
     */
    val valueFalse = Value(false, TBoolean)

    /**
     * <!-- snippet: builtin/true -->
     * # *true*
     * The value `true` of type [snippet/type/Boolean].
     */
    val valueTrue = Value(true, TBoolean)
    fun value(b: Boolean) = if (b) valueTrue else valueFalse

    override fun renderValue(value: Boolean, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(if (value) OutToks.trueWord else OutToks.falseWord)
    }
    override fun destructureValue(
        value: Boolean,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) =
        structureSink.value(value)

    override fun stabilityOf(value: Boolean): ValueStability = ValueStability.Stable

    override fun addStays(value: Boolean, s: StaySink) {
        // No stays to add
    }
}

/**
 * <!-- snippet: builtin/Float64 -->
 * # *Float64*
 * The name `Float64` refers to the builtin type, [snippet/type/Float64].
 */
object TFloat64 : ComparableTypeTag<Double>("Float64", ConsistentDoubleComparator) {
    override fun stabilityOf(value: Double): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: Double,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) =
        structureSink.value(value)

    override fun addStays(value: Double, s: StaySink) {
        // No stays to add
    }

    override fun renderValue(value: Double, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(
            OutputToken(
                when {
                    value.isInfinite() || value.isNaN() -> "$value"
                    else -> {
                        // On Kotlin/JVM Double.toString consistently uses a decimal point,
                        // but on Kotlin/JS that isn't the case because it bottoms out on
                        // Number.prototype.toString.
                        //
                        // Do custom formatting so that tests get consistent stringification of double
                        // values on both.
                        sprintf(SAFE_DOUBLE_FORMAT_STRING, listOf(value))
                    }
                },
                OutputTokenType.NumericValue,
            ),
        )
    }

    /** As a convenience to backends, unpack and use a wrapper to expose various special float values. */
    fun unpackParsed(v: Value<*>): ParseDouble = ParseDouble(unpack(v))

    /** As a convenience to backends, unpack and use a wrapper to expose various special float values. */
    fun unpackParsedOrNull(v: Value<*>): ParseDouble? = unpackOrNull(v) ?. let { ParseDouble(it) }
}

/**
 * <!-- snippet: builtin/Function -->
 * # *Function*
 * The name `Function` refers to the builtin type, [snippet/type/Function], which is the super-type
 * for all [snippet/type/FunctionTypes].
 */
object TFunction : TypeTag<MacroValue>(FUNCTION_TYPE_NAME_TEXT) {
    override fun stabilityOf(value: MacroValue): ValueStability = when (value) {
        is BuiltinStatelessMacroValue -> ValueStability.Stable
        is CoverFunction ->
            if (
                value.covered.all {
                    stabilityOf(it) == ValueStability.Stable
                }
            ) {
                ValueStability.Stable
            } else {
                ValueStability.Unstable
            }
        else -> if (value.functionSpecies == FunctionSpecies.Pure) {
            ValueStability.Stable
        } else {
            ValueStability.Unstable
        }
    }

    override fun renderValue(
        value: MacroValue,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        when (value) {
            is TokenSerializable -> value.renderTo(tokenSink)
            else -> tokenSink.emit(
                when (value.functionSpecies) {
                    FunctionSpecies.Normal, FunctionSpecies.Pure -> OutToks.functionDisplayName
                    FunctionSpecies.Macro, FunctionSpecies.Special -> OutToks.macroDisplayName
                },
            )
        }
    }

    override fun addStays(value: MacroValue, s: StaySink) {
        value.addStays(s)
    }
}

/**
 * <!-- snippet: builtin/Int32 -->
 * # *Int32*
 * The name `Int32` refers to the builtin type, [snippet/type/Int32].
 *
 * <!-- snippet: builtin/Int -->
 * # *Int*
 * The name `Int` is an alias for `Int32`, for convenience in indicating the
 * default int type.
 */
object TInt : ComparableTypeTag<Int>("Int32", naturalOrder()) {
    val alias = BuiltinName("Int")
    override val names = listOf(name, alias)

    override fun stabilityOf(value: Int): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: Int,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) = structureSink.value(value)

    override fun addStays(value: Int, s: StaySink) {
        // No stays here
    }

    override fun renderValue(value: Int, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutputToken("$value", OutputTokenType.NumericValue))
    }
}

/**
 * <!-- snippet: builtin/Int64 -->
 * # *Int64*
 * The name `Int64` refers to the builtin type, [snippet/type/Int64].
 */
object TInt64 : ComparableTypeTag<Long>("Int64", naturalOrder()) {
    override fun stabilityOf(value: Long): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: Long,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) = structureSink.value(value)

    override fun addStays(value: Long, s: StaySink) {
        // No stays here
    }

    override fun renderValue(value: Long, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutputToken("$value", OutputTokenType.NumericValue))
    }
}

/**
 * <!-- snippet: builtin/Listed -->
 * # `Listed`
 * The name `Listed` refers to the builtin type, [snippet/type/Listed].
 */
sealed class TListed<V : List<Value<*>>>(nameText: String) : TypeTag<V>(nameText) {
    override fun renderValue(
        value: V,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        tokenSink.emit(OutToks.leftSquare)
        for (i in value.indices) {
            if (i != 0) {
                tokenSink.emit(OutToks.comma)
            }
            value[i].renderTo(tokenSink, typeInfoIsRedundant)
        }
        tokenSink.emit(OutToks.rightSquare)
    }

    override fun addStays(value: V, s: StaySink) {
        for (v in value) {
            v.addStays(s)
        }
    }
}

/**
 * <!-- snippet: builtin/List -->
 * # `List`
 * The name `List` refers to the builtin type, [snippet/type/List].
 */
object TList : TListed<List<Value<*>>>("List") {
    override fun stabilityOf(value: List<Value<*>>): ValueStability {
        if (value.isNotEmpty() && value.all { it.typeTag == TType }) {
            // Allow lists of types from type bound operator (`A & B`) to
            // make it to extends clauses.
            // do not commit, is this necessary
            return ValueStability.Stable
        }
        return super.stabilityOf(value)
    }
}

/**
 * <!-- snippet: builtin/ListBuilder -->
 * # `ListBuilder`
 * The name `ListBuilder` refers to the builtin type, [snippet/type/ListBuilder].
 */
object TListBuilder : TListed<MutableList<Value<*>>>("ListBuilder") {
    /** Allow TListBuilder to unpack as TList, too. */
    override fun canUnpackAs(other: TypeTag<*>) = other is TListed
}

/**
 * <!-- snippet: builtin/Mapped -->
 * # `Mapped`
 * The name `Mapped` refers to the builtin type, [snippet/type/Mapped].
 */
sealed class TMapped<V : Map<Value<*>, Value<*>>>(nameText: String) : TypeTag<V>(nameText) {
    override fun renderValue(
        value: V,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        // Represent for now as a list of pairs. Maybe ok as we tend to be in a class instance in the interpreter.
        tokenSink.emit(OutToks.leftSquare)
        var pastFirst = false
        for (entry in value.entries) {
            if (pastFirst) {
                tokenSink.emit(OutToks.comma)
            }
            pastFirst = true
            tokenSink.emit(OutToks.leftParen)
            entry.key.renderTo(tokenSink, typeInfoIsRedundant)
            tokenSink.emit(OutToks.comma)
            entry.value.renderTo(tokenSink, typeInfoIsRedundant)
            tokenSink.emit(OutToks.rightParen)
        }
        tokenSink.emit(OutToks.rightSquare)
    }

    override fun addStays(value: V, s: StaySink) {
        for (entry in value.entries) {
            entry.key.addStays(s)
            entry.value.addStays(s)
        }
    }
}

/**
 * <!-- snippet: builtin/Map -->
 * # `Map`
 * The name `Map` refers to the builtin type, [snippet/type/Map].
 */
object TMap : TMapped<Map<Value<*>, Value<*>>>("Map")

/**
 * <!-- snippet: builtin/MapBuilder -->
 * # `MapBuilder`
 * The name `MapBuilder` refers to the builtin type, [snippet/type/MapBuilder].
 */
object TMapBuilder : TMapped<LinkedHashMap<Value<*>, Value<*>>>("MapBuilder") {
    /** Allow TMapBuilder to unpack as TMap, too. */
    override fun canUnpackAs(other: TypeTag<*>) = other is TMapped
}

/**
 * Type tag for the singleton `null` value.
 */
object TNull : TypeTag<TNull.Null>("Null") {
    /**
     * <!-- snippet: builtin/null -->
     * # *null*
     * The singleton value `null` which is admitted by any [snippet/builtin/%3F] type.
     *
     * `null` may be used to represent an absent value, for example:
     *
     * - an unspecified, optional input
     * - an output that is not usable but not due to an error, for example, when mapping a list of values,
     *   but excluding some corresponding elements from the output list
     * - a temporary, not computed *yet*, intermediate value
     *
     * `null` is distinguishable, in translated Temper code, from any other value.
     * For example, in the context of the *Int32?* type, *0* is not equal to `null`.
     *
     * Backends may translate `null` and the concept of nullable types in various ways:
     *
     * - Target languages that have an equivalent (`null`, `nil`, `undefined`, or `None`)
     *   type/value pair may use that where it does not (as in C#) complicate translation of generics.
     * - Target languages that prefer *Option* types may translate nullable types to those
     *   and translate the `null` singleton to the *None* variant.
     *
     * In either case, for dynamic language users and for users of typed languages that do not have
     * null-safety checking, please document when and why `null` is allowed in exported interfaces.
     * Temper has null-safety checking, so can mark APIs that accept/produce `null` equivalent values,
     * but the more clarity around `null` the better.
     */
    val value = Value(Null, TNull)

    override fun renderValue(value: Null, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutToks.nullWord)
    }

    override fun stabilityOf(value: Null): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: Null,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) = structureSink.nil()

    override fun addStays(value: Null, s: StaySink) {
        // no stays here
    }

    object Null : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.nil()
    }
}

/**
 * <!-- snippet: builtin/StageRange -->
 * TODO: StageRange
 */
object TStageRange : TypeTag<EnumRange<Stage>>("StageRange") {
    override fun renderValue(
        value: EnumRange<Stage>,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        val min = value.start
        val max = value.endInclusive
        tokenSink.emit(OutputToken("@${min.abbrev}", OutputTokenType.Word))
        if (min != max) {
            tokenSink.emit(OutToks.dotDot)
            tokenSink.emit(OutputToken("${max.abbrev}", OutputTokenType.Word))
        }
    }

    override fun stabilityOf(value: EnumRange<Stage>): ValueStability = ValueStability.Stable

    override fun addStays(value: EnumRange<Stage>, s: StaySink) {
        // no stays here
    }
}

/**
 * <!-- snippet: builtin/String -->
 * # *String*
 * The name `String` refers to the builtin type, [snippet/type/String].
 */
object TString : ComparableTypeTag<String>("String", Utf8EquivalentStringComparator) {
    override fun toString() = "String"

    override fun renderValue(value: String, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutputToken(temperEscaper.escape(value), OutputTokenType.QuotedValue))
    }

    override fun stabilityOf(value: String): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: String,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) =
        structureSink.value(value)

    override fun addStays(value: String, s: StaySink) {
        // no stays here
    }
}

/**
 * <!-- snippet: builtin/Symbol -->
 * # *Symbol*
 * Symbol is the type for values that are used, during compilation,
 * to represent parameter names, member names, and metadata keys.
 *
 * They're relevant to the Temper language, and symbol values are
 * not meant to translate to values in other languages.
 */
object TSymbol : TypeTag<Symbol>("Symbol") {
    override fun renderValue(
        value: Symbol,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        tokenSink.emit(OutputToken("\\${value.text}", OutputTokenType.Name))
    }

    override fun stabilityOf(value: Symbol): ValueStability = ValueStability.Stable

    override fun addStays(value: Symbol, s: StaySink) {
        // no stays here
    }
}

/**
 * <!-- snippet: builtin/Type -->
 * # *Type*
 * Type values represent a Temper type, allowing them to be inputs
 * to macros.
 */
object TType : TypeTag<ReifiedType>(typeBuiltinName) {
    override fun addStays(value: ReifiedType, s: StaySink) {
        s.whenUnvisited(value) {
            value.addStays(s)
        }
    }

    override fun stabilityOf(value: ReifiedType): ValueStability = ValueStability.Stable
}

object TClosureRecord : TypeTag<ClosureRecord>(OutToks.closRecWord.text.asciiTitleCase()) {
    override fun addStays(value: ClosureRecord, s: StaySink) = value.addStays(s)

    override fun stabilityOf(value: ClosureRecord): ValueStability =
        ValueStability.Unstable // Closes over an environment
}

/**
 * <!-- snippet: builtin/Problem -->
 * # *Problem*
 * The name `Problem` refers to the builtin type, [snippet/type/Problem],
 * an abstraction that allows running some tests despite failures during
 * compilation.
 */
object TProblem : TypeTag<LogEntry>("Problem") {
    override fun addStays(value: LogEntry, s: StaySink) {
        // log entries are opaque diagnostics.  We shouldn't need to reach into them to call values.
    }

    override fun stabilityOf(value: LogEntry): ValueStability = ValueStability.Stable

    override fun renderValue(value: LogEntry, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutputToken(value.template.name, OutputTokenType.Word))
    }
}

typealias InstancePropertyMap = Map<ModularName, Value<*>>

data class InstancePropertyRecord(
    val properties: MutableMap<ModularName, Value<*>>,
)

/** Type tag for an instance of a class. */
class TClass(
    val typeShape: TypeShape,
) : TypeTag<InstancePropertyRecord>(
    name = typeShape.name,
) {
    override fun renderValue(
        value: InstancePropertyRecord,
        tokenSink: TokenSink,
        typeInfoIsRedundant: Boolean,
    ) {
        var needComma = false
        tokenSink.emit(OutToks.leftCurly)
        if (!typeInfoIsRedundant) {
            tokenSink.emit(OutToks.classWord)
            tokenSink.emit(OutToks.colon)
            tokenSink.emit(OutToks.oneSpace)
            tokenSink.emit(typeShape.name.toToken(inOperatorPosition = false))
            needComma = true
        }
        for ((name, binding) in value.properties) {
            if (needComma) {
                tokenSink.emit(OutToks.comma)
                tokenSink.emit(OutToks.oneSpace)
            }
            val nameToken =
                when (name) {
                    is ResolvedParsedName -> name.baseName
                    is Temporary -> name
                }
                    .toToken(inOperatorPosition = false)
            tokenSink.emit(nameToken)
            tokenSink.emit(OutToks.colon)
            tokenSink.emit(OutToks.oneSpace)
            binding.renderTo(tokenSink, typeInfoIsRedundant)
            needComma = true
        }
        tokenSink.emit(OutToks.rightCurly)
    }

    override fun addStays(value: InstancePropertyRecord, s: StaySink) {
        value.properties.forEach { (_, binding) ->
            binding.addStays(s)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is TClass && this.typeShape == other.typeShape

    override fun hashCode(): Int = typeShape.hashCode()
}

/**
 * <!-- snippet: builtin/Void -->
 * # *Void*
 * The name `Void` refers to the builtin type [snippet/type/Void].
 *
 * *Void* is an appropriate return type for functions that are called for their effect, not
 * to produce a value.
 */
object TVoid : TypeTag<Unit>("Void") {
    val value = Value(Unit, TVoid)

    override fun renderValue(value: Unit, tokenSink: TokenSink, typeInfoIsRedundant: Boolean) {
        tokenSink.emit(OutToks.voidWord)
    }

    override fun stabilityOf(value: Unit): ValueStability = ValueStability.Stable

    override fun destructureValue(
        value: Unit,
        structureSink: StructureSink,
        typeInfoIsRedundant: Boolean,
    ) = structureSink.nil()

    override fun addStays(value: Unit, s: StaySink) {
        // no stays here
    }
}
