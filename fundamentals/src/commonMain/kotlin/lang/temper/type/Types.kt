package lang.temper.type

import lang.temper.common.defensiveListCopy
import lang.temper.common.dequeIterable
import lang.temper.common.soleElementOrNull
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.name.Symbol
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.IValueFormal
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.ReifiedType
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.Stayless

enum class TypeOpPrecedence {
    Or, // Binds loosest
    And,
    Postfixed,
    SelfContained, // Binds tightest
}

sealed class TypeActual : StayReferrer, Structured, TokenSerializable {
    override fun toString(): String = toStringViaTokenSink {
        this.renderTo(it)
    }
}

object Wildcard : TypeActual(), Stayless {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value("*")
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.prefixStar)
    }
}

/**
 * <!-- snippet: type/relationships -->
 * !!! note
 *
 *     These type relationships are deprecated.
 *     Especially arbitrary type unions and intersections, and unqualified never types.
 *
 * <!-- TODO: rewrite after type2 transition -->
 *
 * <figure markdown="1">
 *
 * ``` mermaid
 * graph TD
 * Top --> AnyValue
 * Top --> Void
 * Top --> Bubble
 * AnyValue --> Union["A | B"]
 * subgraph nominals [Nominals]
 *   direction LR
 *   A
 *   B
 * end
 * Union --> A
 * Union --> B
 * A --> Intersection["A & B"]
 * B --> Intersection
 * Intersection --> Never
 * Void --> Never
 * Bubble --> Never
 * Invalid
 * ```
 *
 * <figcaption markdown="1">
 *
 * Arrows point from supertypes to subtypes.
 *
 * </figcaption>
 * </figure>
 *
 * There is a *Top* type at the top which is the super-type of all types.
 *
 * At the bottom is *Never* which is the bottom type, a sub-type of
 * all types, and an appropriate type for computations that never complete like
 *
 * ```temper inert
 * while (true) {}
 * ```
 *
 * The *Invalid* type, off to the right, is a type marker for constructs
 * that the compiler cannot make sense of.  It is outside the type hierarchy; not
 * a subtype of any other nor vice versa.
 *
 * *Top* branches into *AnyValue*, *Void*, and *Bubble*.  This represents the
 * three ways a computation can complete, either by
 *
 * - producing an actual value (it produces a sub-type of *AnyValue*),
 * - finishing normally but without a usable value, or
 * - bubbling up the call stack until replaced with a value by `orelse`.
 *
 * Below any value we have `A | B`, a union type.
 * Values of type `A | B` can be of type `A` or[^1] of type `B`.
 * A union type is a super-type of each of its members.
 *
 * [^1]: "or" is non-exclusive.  A value could be of both types.
 *
 * Below the *Nominal Type* box we have `A & B`, an
 * [intersection type][snippet/type/intersection-fn].
 * An intersection is a sub-type of each of its elements.
 *
 * ```temper inert
 * class C extends A, B {}
 * ```
 *
 * In that, `C` is a declared sub-type of both `A` and `B`, so it's a
 * sub-type of `A & B`.
 *
 * (Union and intersection types are actually more general.
 * `A | Bubble` is an expressible type as is `(A?) & D`, so
 * this diagram does not imply that all union/intersection types fit
 * neatly in a region on one side of nominal types. `Void` has
 * constraints here, however. `Void | Bubble` makes sense, but
 * `A | Void` doesn't.)
 *
 * In the middle are *NominalType*s.  These are types declared with a name
 * and parameters.  *NominalTypes* include all these:
 *
 * ```temper inert
 * Boolean         // A builtin type
 * List<T?>        // A parameterized type
 * C               // A nominal type, assuming a definition like `class C` is in scope.
 * fn (Int): Int   // A type for functions that take an Int and return an Int
 * ```
 */
sealed class StaticType : TypeActual() {
    open val precedence: TypeOpPrecedence get() = TypeOpPrecedence.SelfContained
}

sealed class SimpleType : StaticType()

private fun renderParenthesized(outer: TypeOpPrecedence, inner: StaticType, tokenSink: TokenSink) {
    if (inner.precedence <= outer) {
        tokenSink.emit(OutToks.leftParen)
        inner.renderTo(tokenSink)
        tokenSink.emit(OutToks.rightParen)
    } else {
        inner.renderTo(tokenSink)
    }
}

/**
 * A top type that is a super type of all types.
 *
 * <!-- snippet: type/Top -->
 * # *Top*
 * *Top* is a super-type of every type, including [snippet/type/AnyValue] and
 * [snippet/type/Bubble].
 *
 * <!-- snippet: builtin/Top -->
 * # *Top*
 * A name that may be used to reference the special [snippet/type/Top] type.
 */
object TopType : SimpleType(), Stayless {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value(OutToks.topWord.text)
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.topWord)
    }
}

/**
 * A type that is disjoint from all [NominalType]s.
 * It is the type for expressions that complete with [failure][lang.temper.value.Fail].
 *
 * <!-- snippet: builtin/Bubble -->
 * # *Bubble*
 * A name that may be used to reference the special [snippet/type/Bubble] type.
 */
object BubbleType : SimpleType(), Stayless {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value(OutToks.bubbleWord.text)
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.bubbleWord)
    }
}

/**
 * <!-- snippet: builtin/Invalid -->
 * # *Invalid*
 * A name that may be used to reference the special [snippet/type/Invalid] type.
 */
object InvalidType : StaticType(), Stayless {
    override fun destructure(structureSink: StructureSink) {
        structureSink.value(OutToks.invalidWord.text)
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.invalidWord)
    }
}

/** A named type with any actual bindings for type parameters. */
class NominalType private constructor(
    val definition: TypeDefinition,
    val bindings: List<TypeActual>,
) : SimpleType() {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NominalType &&
            definition === other.definition &&
            bindings == other.bindings

    override fun hashCode(): Int = definition.hashCode() + 31 * bindings.hashCode()

    override fun destructure(structureSink: StructureSink) =
        if (bindings.isEmpty()) {
            structureSink.value(definition.name)
        } else {
            structureSink.arr {
                value("Nominal")
                value(definition.name)
                bindings.forEach {
                    value(it)
                }
            }
        }

    override fun renderTo(tokenSink: TokenSink) {
        definition.renderName(tokenSink)
        if (bindings.isNotEmpty()) {
            tokenSink.emit(OutToks.leftAngle)
            for (i in bindings.indices) {
                if (i != 0) {
                    tokenSink.emit(OutToks.comma)
                }
                bindings[i].renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightAngle)
        }
    }
    override fun addStays(s: StaySink) {
        definition.addStays(s)
        for (binding in bindings) {
            binding.addStays(s)
        }
    }

    /** If no bindings exist for formals. Empty formals also means not unbound. */
    fun isUnbound() = bindings.isEmpty() && definition.hasFormals

    companion object {
        fun makeInternalOnly(
            definition: TypeDefinition,
            bindings: Iterable<TypeActual> = emptyList(),
        ): NominalType =
            NominalType(definition, defensiveListCopy(bindings))
    }
}

/**
 * A type for a function including parameters, input types, and result type.
 *
 * <!-- snippet: type/FunctionTypes : Function Types -->
 * ## Function Types
 *
 * The keyword `fn` is used to denote function values and types.
 *
 * | Example | Means |
 * | ------- | ----- |
 * | `:::js fn (): Void` | Type for a function that takes no arguments and returns the `void` value |
 * | `:::js fn (Int): Int` | Type for a function that takes one integer and returns an integer |
 * | `:::js fn<T> (T): List<T>` | Type for a generic function with a type parameter `<T>` |
 * | `:::js fn (...Int): Boolean` | Type for a function that takes any number of integers |
 *
 * Source: [temper/fundamentals/**/Types.kt]
 */
class FunctionType private constructor(
    val typeFormals: List<TypeFormal>,
    /** Input types.  Never types and [BubbleType] make little sense here. */
    val valueFormals: List<ValueFormal>,
    /**
     * If the function typed accepts value arguments besides those in [valueFormals] the type of
     * those additional arguments.
     */
    val restValuesFormal: StaticType?,
    /**
     * The type of result returned.
     * Never types make sense here for functions whose calls cannot complete.
     * [BubbleType] makes sense here by itself or in a union for functions whose calls
     * may complete without a result.
     */
    val returnType: StaticType,
) : SimpleType() {
    // FunctionType is simple in the sense that we don't distribute union and intersection over it.
    //
    //     (T => R) | (U => R)
    //
    // is not
    //
    //     (T | U) => R
    //
    // because statically typed backends, especially those that reify generics, probably will need
    // to distinguish between the two.
    // We will need to box enough information to allow casting to the right variant.

    override fun equals(other: Any?): Boolean = other is FunctionType &&
        this.typeFormals == other.typeFormals &&
        this.valueFormals == other.valueFormals &&
        this.restValuesFormal == other.restValuesFormal &&
        this.returnType == other.returnType

    override fun hashCode(): Int =
        typeFormals.hashCode() +
            31 * (
                valueFormals.hashCode() + 31 * (
                    (restValuesFormal?.hashCode() ?: 0) +
                        31 * returnType.hashCode()
                    )
                )

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            typeFormals.forEach { it.addStays(s) }
            valueFormals.forEach { it.staticType.addStays(s) }
            restValuesFormal?.addStays(s)
            returnType.addStays(s)
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("typeFormals", isDefault = typeFormals.isEmpty()) {
            arr {
                typeFormals.forEach { value(it) }
            }
        }
        key("valueFormals") {
            arr {
                valueFormals.forEach { value(it) }
            }
        }
        key("restValuesFormal", isDefault = restValuesFormal == null) {
            value(restValuesFormal)
        }
        key("returnType") {
            returnType.destructure(this)
        }
    }

    override fun renderTo(tokenSink: TokenSink) = renderFunctionType(
        tokenSink,
        typeFormals = typeFormals,
        valueFormals = valueFormals,
        restValuesFormal = restValuesFormal,
        returnType = returnType,
        returnTypeOpPrecedence = returnType.precedence,
    )

    companion object {
        internal fun renderFunctionType(
            tokenSink: TokenSink,
            typeFormals: List<TokenSerializable>,
            valueFormals: List<TokenSerializable>,
            restValuesFormal: TokenSerializable?,
            returnType: TokenSerializable,
            returnTypeOpPrecedence: TypeOpPrecedence,
        ) {
            // As long as we render this way TypeOpPrecedence.SelfContained works.
            // If this changes to do arrow style rendering, then we'll need a TypeOpPrecedence.Arrow
            tokenSink.emit(OutToks.fnWord)

            // Render type formals inside <...>
            if (typeFormals.isNotEmpty()) {
                tokenSink.emit(OutToks.leftAngle)
                typeFormals.forEachIndexed { i, el ->
                    if (i != 0) {
                        tokenSink.emit(OutToks.comma)
                    }
                    el.renderTo(tokenSink)
                }
                tokenSink.emit(OutToks.rightAngle)
            }

            // Render value formals inside (...)
            if (valueFormals.isNotEmpty() || restValuesFormal != null) {
                tokenSink.emit(OutToks.leftParen)
                valueFormals.forEachIndexed { i, el ->
                    if (i != 0) {
                        tokenSink.emit(OutToks.comma)
                    }
                    el.renderTo(tokenSink)
                }
                if (restValuesFormal != null) {
                    if (valueFormals.isNotEmpty()) {
                        tokenSink.emit(OutToks.comma)
                    }
                    tokenSink.emit(OutToks.ellipses)
                    restValuesFormal.renderTo(tokenSink)
                }
                tokenSink.emit(OutToks.rightParen)
            }

            // TODO: Is the associativity of `:` right so that
            //     let f: fn: Int = fn { 0 }
            // works?
            tokenSink.emit(OutToks.colon)
            if (returnTypeOpPrecedence >= TypeOpPrecedence.Postfixed) {
                returnType.renderTo(tokenSink)
            } else {
                tokenSink.emit(OutToks.leftParen)
                returnType.renderTo(tokenSink)
                tokenSink.emit(OutToks.rightParen)
            }
        }

        internal fun makeInternalOnly(
            typeFormals: List<TypeFormal>,
            valueFormals: List<ValueFormal>,
            restValuesFormal: StaticType?,
            returnType: StaticType,
        ): FunctionType = FunctionType(
            typeFormals = typeFormals,
            valueFormals = valueFormals,
            restValuesFormal = restValuesFormal,
            returnType = returnType,
        )
    }

    data class ValueFormal(
        override val symbol: Symbol?,
        override val staticType: StaticType,
        override val isOptional: Boolean = false,
    ) : Structured, TokenSerializable, IValueFormal {
        override val type: Type2
            get() = hackMapOldStyleToNew(staticType)
        override val kind: ValueFormalKind
            get() = if (isOptional) ValueFormalKind.Optional else ValueFormalKind.Required
        override val reifiedType get() = ReifiedType(type)

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("symbol") { value(symbol) }
            key("type") { value(staticType) }
            key("isOptional") { value(isOptional) }
        }

        override fun renderTo(tokenSink: TokenSink) {
            if (isOptional) {
                // For now, render as named just those that are optional.
                tokenSink.emit(OutputToken(symbol?.text ?: "_", OutputTokenType.Name))
                tokenSink.postfixOp("?")
                tokenSink.emit(OutToks.colon)
            }
            staticType.renderTo(tokenSink)
        }
    }
}

/** Union type */
class OrType private constructor(val members: Set<StaticType>) : StaticType() {
    override val precedence get() = TypeOpPrecedence.Or

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is OrType && members == other.members

    override fun hashCode(): Int = members.hashCode() xor -0x71153C33

    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        value("Or")
        members.forEach {
            value(it)
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        var hasNull = false
        val membersToRender = buildSet {
            members.filterTo(this) { member ->
                (!member.isNullType).also { keep ->
                    if (!keep) { hasNull = true }
                }
            }
        }

        if (hasNull) {
            val t = when (membersToRender.size) {
                0 -> {
                    tokenSink.emit(OutToks.nullTypeWord)
                    return
                }
                1 -> membersToRender.first()
                else -> OrType(membersToRender)
            }
            renderParenthesized(TypeOpPrecedence.Postfixed, t, tokenSink)
            tokenSink.emit(OutToks.postfixQMark)
            return
        }

        if (membersToRender.isEmpty()) {
            tokenSink.emit(OutToks.neverWord)
            return
        }

        membersToRender.forEachIndexed { i, member ->
            if (i != 0) {
                tokenSink.emit(OutToks.bar)
            }
            renderParenthesized(precedence, member, tokenSink)
        }
    }

    override fun addStays(s: StaySink) {
        members.forEach { it.addStays(s) }
    }

    private object OrTypeSimplifier : AbstractOrTypeSimplifier<StaticType>(
        neverType = emptyOrType,
        topType = TopType,
        bubbleType = BubbleType,
    ) {
        override fun isAnyValueType(t: StaticType): Boolean = t == WellKnownTypes.anyValueType
        override fun alternativesOf(t: StaticType): Set<StaticType>? = (t as? OrType)?.members
    }

    companion object {
        internal fun makeInternalOnly(members: Iterable<StaticType>): StaticType {
            val flat = OrTypeSimplifier.simplify(members)
            return when (flat.size) {
                1 -> flat.first()
                else -> OrType(flat.toSet())
            }
        }

        val emptyOrType = OrType(setOf())
    }
}

/** An intersection of types. */
class AndType private constructor(val members: Set<StaticType>) : StaticType() {
    override val precedence get() = TypeOpPrecedence.And

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is AndType && members == other.members

    override fun hashCode(): Int = members.hashCode() xor 0x3922FF90

    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        value("And")
        members.forEach {
            value(it)
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        members.forEachIndexed { i, member ->
            if (i != 0) {
                tokenSink.emit(OutToks.amp)
            }
            renderParenthesized(precedence, member, tokenSink)
        }
    }

    override fun addStays(s: StaySink) {
        members.forEach { it.addStays(s) }
    }

    private object AndTypeSimplifier : AbstractAndTypeSimplifier<StaticType>(
        neverType = OrType.emptyOrType,
        topType = TopType,
        bubbleType = BubbleType,
    ) {
        override fun isAnyValueType(t: StaticType): Boolean = t == WellKnownTypes.anyValueType
        override fun alternativesOf(t: StaticType): Set<StaticType>? = (t as? OrType)?.members
        override fun requirementsOf(t: StaticType): Set<StaticType>? = (t as? AndType)?.members
        override fun isNominalType(t: StaticType): Boolean = t is NominalType
        override fun isFunctionType(t: StaticType): Boolean = t is FunctionType
        override fun makeOr(alternatives: Iterable<StaticType>) =
            MkType.or(alternatives)
    }

    companion object {
        internal fun makeInternalOnly(
            members: Iterable<StaticType>,
        ): StaticType {
            val flat = AndTypeSimplifier.simplify(members)
            return when (flat.size) {
                0 -> TopType
                1 -> flat.first()
                else -> AndType(flat.toSet())
            }
        }
    }
}

/**
 * A mutable type actual subclass that may be assigned a backing actual once.
 * This class enables construction of infinite types.
 */
class InfiniBinding : TypeActual() {
    private var binding: TypeActual? = null
    val isInitialized get() = binding != null

    /**
     * @return a type actual that is not an InfiniBinding.
     *     If this is initialized to an InfiniBinding, returns that's binding if possible.
     *     If unbound, returns alternative, unless it is an Infinibinding in which case it
     *     returns alternative's binding.
     *     If both this and alternative are ultimately unbound, returns [InvalidType].
     */
    operator fun get(alternative: TypeActual = InvalidType): TypeActual {
        var ib = this
        var fallback = alternative
        while (true) {
            val bOrNull = ib.binding
            val b: TypeActual
            if (bOrNull == null) {
                b = fallback
                // If alternative is an Infinibinding that is ultimately uninitialized,
                // do not start back at the beginning
                fallback = InvalidType
            } else {
                b = bOrNull
            }
            if (b is InfiniBinding) {
                ib = b
            } else {
                return b
            }
        }
    }
    fun set(newBound: TypeActual) {
        check(binding == null)
        if (newBound is InfiniBinding) {
            // Clients are responsible for avoiding cycles.
            val seen = mutableSetOf(this)
            var ib: InfiniBinding = newBound
            while (true) {
                require(ib !in seen) { "Set would create an InfiniBound only cycle" }
                val next = ib.get()
                if (next is InfiniBinding) {
                    ib = next
                } else {
                    break
                }
            }
        }
        binding = newBound
    }

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            binding?.addStays(s)
        }
    }

    override fun destructure(structureSink: StructureSink) {
        val infinibound = this
        structureSink.arr {
            value("Infinibound")
            if (!isInitialized) {
                value("?")
            } else if (infinibound !in rendering) {
                rendering.add(infinibound)
                try {
                    value(get())
                } finally {
                    rendering.remove(infinibound)
                }
            } else {
                value("...")
            }
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        val infinibound = this
        if (!isInitialized) {
            tokenSink.emit(OutToks.uninitializedInfiniBindingCommentToken)
        } else if (infinibound !in rendering) {
            rendering.add(infinibound)
            try {
                get().renderTo(tokenSink)
            } finally {
                rendering.remove(infinibound)
            }
        } else {
            tokenSink.emit(OutToks.ellipses)
        }
    }

    companion object {
        private val rendering = mutableSetOf<InfiniBinding>()
    }
}

object MkType {
    fun or(vararg ts: StaticType) = or(ts.asList())
    fun and(vararg ts: StaticType) = and(ts.asList())
    fun or(ts: Iterable<StaticType>): StaticType = OrType.makeInternalOnly(ts)
    fun and(ts: Iterable<StaticType>): StaticType = AndType.makeInternalOnly(ts)
    fun nominal(definition: TypeDefinition, bindings: List<TypeActual> = emptyList()): NominalType =
        NominalType.makeInternalOnly(definition, bindings)
    fun fnDetails(
        typeFormals: List<TypeFormal>,
        valueFormals: List<FunctionType.ValueFormal>,
        restValuesFormal: StaticType?,
        returnType: StaticType,
    ): FunctionType = FunctionType.makeInternalOnly(typeFormals, valueFormals, restValuesFormal, returnType)
    fun fn(
        typeFormals: List<TypeFormal>,
        valueFormals: List<StaticType>,
        restValuesFormal: StaticType?,
        returnType: StaticType,
    ) = fnDetails(
        typeFormals = typeFormals,
        valueFormals = valueFormals.map {
            FunctionType.ValueFormal(symbol = null, staticType = it, isOptional = false)
        },
        restValuesFormal = restValuesFormal,
        returnType = returnType,
    )
    private val nominalTypeNull = lazy { nominal(WellKnownTypes.nullTypeDefinition) }
    fun nullable(t: StaticType) = or(t, nominalTypeNull.value)

    fun bindFormals(t: StaticType, bindings: Map<TypeFormal, StaticType>): StaticType {
        // See also ReplaceWithBinding that includes extra indirection for InferenceVariable mapping.
        val m = object : TypePartMapper {
            override fun mapBinding(b: TypeActual) = b
            override fun mapDefinition(d: TypeDefinition) = d
            override fun mapType(t: StaticType): StaticType {
                return (t as? NominalType)?.definition?.let { definition ->
                    // Follow example of ReplaceWithBinding and avoid when formal has bindings of its own.
                    // TODO If formal has bindings that actual doesn't, use those?
                    when {
                        t.bindings.isEmpty() -> bindings[definition]
                        else -> null
                    }
                } ?: t
            }
        }
        return when (val result = map(t, m)) {
            is FunctionType -> when {
                result.typeFormals.isNotEmpty() -> fnDetails(
                    // Remove replaced function type formals to avoid confusion.
                    typeFormals = result.typeFormals - bindings.keys,
                    valueFormals = result.valueFormals,
                    restValuesFormal = result.restValuesFormal,
                    returnType = result.returnType,
                )
                else -> result
            }
            else -> result
        }
    }

    fun map(
        t: StaticType,
        m: TypePartMapper,
        ibs: MutableMap<InfiniBinding, InfiniBinding> = mutableMapOf(),
    ): StaticType {
        val subMapped = when (t) {
            InvalidType, BubbleType, TopType -> t
            is NominalType ->
                nominal(m.mapDefinition(t.definition), t.bindings.map { map(it, m, ibs) })
            is FunctionType -> fnDetails(
                typeFormals = t.typeFormals.map { typeFormal ->
                    m.mapDefinition(typeFormal) as TypeFormal
                },
                valueFormals = t.valueFormals.map { valueFormal ->
                    valueFormal.copy(staticType = map(valueFormal.staticType, m, ibs))
                },
                restValuesFormal = t.restValuesFormal?.let { map(it, m, ibs) },
                returnType = map(t.returnType, m, ibs),
            )
            is OrType -> or(t.members.map { map(it, m, ibs) })
            is AndType -> and(t.members.map { map(it, m, ibs) })
        }
        return m.mapType(subMapped)
    }

    fun map(
        b: TypeActual,
        m: TypePartMapper,
        ibs: MutableMap<InfiniBinding, InfiniBinding> = mutableMapOf(),
    ): TypeActual {
        val subMapped = when (b) {
            is StaticType -> map(b, m, ibs)
            is InfiniBinding -> {
                val ib = ibs[b]
                if (ib == null) {
                    val newIb = InfiniBinding()
                    ibs[b] = newIb
                    if (b.isInitialized) {
                        newIb.set(map(b.get(), m, ibs))
                    }
                    newIb
                } else {
                    ib
                }
            }
            Wildcard -> b
        }
        return m.mapBinding(subMapped)
    }
}

/** Determine if any component is invalid. */
val StaticType.mentionsInvalid: Boolean get() = when (this) {
    TopType -> false
    BubbleType -> false
    is NominalType -> bindings.any { it is StaticType && it.mentionsInvalid }
    is FunctionType ->
        returnType.mentionsInvalid || valueFormals.any { it.staticType.mentionsInvalid } ||
            restValuesFormal?.mentionsInvalid == true
    InvalidType -> true
    is OrType -> members.any { it.mentionsInvalid }
    is AndType -> members.any { it.mentionsInvalid }
}

/** Determine if any component is invalid. */
val Type2.mentionsInvalid: Boolean get() =
    this.definition == WellKnownTypes ||
        this.bindings.any { it.mentionsInvalid }

val StaticType.isBooleanLike: Boolean get() = isTypeOrNever(WellKnownTypes.booleanTypeDefinition)
val StaticType.isVoidLike: Boolean get() = isTypeOrNever(WellKnownTypes.voidTypeDefinition)

/** Should eventually become [Type2.isVoidLike] but old style *Never* is too ambiguous */
val StaticType.isVoidLikeButNotOldStyleNever: Boolean get() = this == WellKnownTypes.voidType
val StaticType.isEmptyLike: Boolean get() = isTypeOrNever(WellKnownTypes.emptyTypeDefinition)
val StaticType.isNullType: Boolean get() =
    this is NominalType && this.definition == WellKnownTypes.nullTypeDefinition
val StaticType.isNeverType: Boolean get() =
    this == OrType.emptyOrType ||
        (this is NominalType && this.definition == WellKnownTypes.neverTypeDefinition)

val Type2.isBooleanLike: Boolean get() = isTypeOrNever(WellKnownTypes.booleanType2)
val Type2.isVoidLike: Boolean get() = isTypeOrNever(WellKnownTypes.voidType2)
val Type2.isEmptyLike: Boolean get() = isTypeOrNever(WellKnownTypes.emptyType2)
val Type2.canOnlyBeNull: Boolean get() = this.nullity == Nullity.NonNull &&
    this.definition == WellKnownTypes.neverTypeDefinition
val Type2.isNeverType: Boolean get() =
    this.nullity == Nullity.NonNull && this.definition == WellKnownTypes.neverTypeDefinition

private fun StaticType.isTypeOrNever(definition: TypeDefinition) = when (this) {
    is OrType -> this.members.isEmpty()
    is NominalType -> this.definition == definition
    else -> false
}

private fun Type2.isTypeOrNever(t: Type2) = this == t ||
    this.nullity == Nullity.NonNull && this.definition == WellKnownTypes.neverTypeDefinition &&
    this.bindings.soleElementOrNull == t

/** Checks for precisely void type. */
val StaticType.isVoid: Boolean get() = (this as? NominalType)?.definition == WellKnownTypes.voidTypeDefinition

/** To be used for return types only. */
val StaticType.isVoidAllowing: Boolean get() = when (this) {
    is NominalType -> definition == WellKnownTypes.voidTypeDefinition
    is AndType -> members.all { it.isVoidAllowing }
    is OrType -> members.any { it.isVoidAllowing }
    is TopType -> true
    else -> false
}

val StaticType.isBubbly: Boolean get() = when (this) {
    is BubbleType -> true
    is AndType -> members.all { it.isBubbly }
    is OrType -> members.any { it.isBubbly }
    is TopType -> true
    else -> false
}

/**
 * Simplifies a type by removing annotation types like `Null` or `Bubble`, if present. See [SimplifyStaticType] for
 * details.
 */
fun Type2.simplify() = SimplifyStaticType(this).principal

/** Walks ancestors of the type. */
fun Type2.ancestors(): Iterable<Type2> = this.dequeIterable { deque ->
    val type = deque.removeFirst()
    if (type is DefinedNonNullType) {
        deque.addAll(type.definition.superTypes.map { hackMapOldStyleToNew(it) })
    }
    type
}

enum class TAnnotation {
    Nullable,
    Bubbly,
    Never,
}

/**
 * Some languages make a distinction between "real" types and types that exist as annotations, or that
 * are simply ignored. For example, Java has no explicit "null" type, rather, it's indicated through `@Nullable`
 * annotations.
 *
 * This class assumes that types can be decomposed into a single principal type, usually a nominal type, and some
 * number of annotations including Null, Bubble, Never, etc.
 *
 * Not all annotation types are bottom-like, especially, the Void type is a principal type. These distinctions are
 * fairly arbitrary and based on specification or even mere convention in the backend.
 */
data class SimplifyStaticType(
    /** This is generally some nominal type, but, importantly, it could be Void. */
    val principal: NonNullType,
    /** Generally, these are various kinds of bottom-like types. */
    val annotations: Set<TAnnotation>,
) {
    val hasNullAnnotation: Boolean get() = TAnnotation.Nullable in annotations

    companion object {
        operator fun invoke(
            type: Type2,
        ): SimplifyStaticType {
            val annotations = mutableSetOf<TAnnotation>()

            var nonNull: NonNullType = when (type.nullity) {
                Nullity.OrNull -> {
                    annotations.add(TAnnotation.Nullable)
                    type.withNullity(Nullity.NonNull)
                }
                Nullity.NonNull -> type
            } as NonNullType

            while (true) {
                val simpler = withType(
                    nonNull,
                    result = { pass, _, _ ->
                        annotations.add(TAnnotation.Bubbly)
                        pass
                    },
                    never = { inner, _, _ ->
                        annotations.add(TAnnotation.Never)
                        inner
                    },
                    malformed = { _, _ ->
                        nonNull = WellKnownTypes.invalidType2
                        null
                    },
                    fallback = {
                        null
                    },
                ) ?: break
                nonNull = when (simpler.nullity) {
                    Nullity.OrNull -> {
                        annotations.add(TAnnotation.Nullable)
                        simpler.withNullity(Nullity.NonNull)
                    }
                    Nullity.NonNull -> simpler
                } as NonNullType
            }

            return SimplifyStaticType(nonNull, annotations.toSet())
        }
    }
}
