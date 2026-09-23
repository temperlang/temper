@file:Suppress("MagicNumber") // in hashCode implementations

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
    Fn,
    Postfixed,
    SelfContained, // Binds tightest
}

sealed class TypeActual : StayReferrer, Structured, TokenSerializable {
    final override fun toString(): String = toStringViaTokenSink {
        this.renderTo(it)
    }

    internal abstract fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean
    internal abstract fun hashCode(bnr: ButNotRecursively): Int
    internal abstract fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively)
    internal abstract fun destructure(structureSink: StructureSink, bnr: ButNotRecursively)

    internal data object TypeActualEquals : ButNotRecursively.TaskKey<Pair<TypeActual, TypeActual>, Boolean>
    internal data object TypeActualHashCode : ButNotRecursively.TaskKey<TypeActual, Int>
    internal data object TypeActualsRenderTo : ButNotRecursively.TaskKey<Any, Boolean>
    internal data object TypeActualsDestructure : ButNotRecursively.TaskKey<Any, Boolean>

    final override fun equals(other: Any?) = this === other ||
        other is TypeActual && equals(other, ButNotRecursively())

    final override fun hashCode(): Int = hashCode(ButNotRecursively())

    final override fun renderTo(tokenSink: TokenSink) {
        renderTo(tokenSink, ButNotRecursively())
    }

    final override fun destructure(structureSink: StructureSink) {
        destructure(structureSink, ButNotRecursively())
    }
}

object Wildcard : TypeActual(), Stayless {
    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        structureSink.value("*")
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        tokenSink.emit(OutToks.prefixStar)
    }

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean = other is Wildcard

    override fun hashCode(bnr: ButNotRecursively): Int = -0x1b5d360c
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
 * At the bottom is *Never* which is the bottom type, a subtype of
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
 * - producing an actual value (it produces a subtype of *AnyValue*),
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

private fun renderParenthesized(
    outer: TypeOpPrecedence,
    inner: StaticType,
    tokenSink: TokenSink,
    bnr: ButNotRecursively,
) {
    if (inner.precedence <= outer) {
        tokenSink.emit(OutToks.leftParen)
        inner.renderTo(tokenSink, bnr)
        tokenSink.emit(OutToks.rightParen)
    } else {
        inner.renderTo(tokenSink, bnr)
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
    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        structureSink.value(OutToks.topWord.text)
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        tokenSink.emit(OutToks.topWord)
    }

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean =
        other is TopType

    override fun hashCode(bnr: ButNotRecursively): Int = 0x2daad58c
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
    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        structureSink.value(OutToks.bubbleWord.text)
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        tokenSink.emit(OutToks.bubbleWord)
    }

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean =
        other is BubbleType

    override fun hashCode(bnr: ButNotRecursively): Int = -0x55cb44bd
}

/**
 * <!-- snippet: builtin/Invalid -->
 * # *Invalid*
 * A name that may be used to reference the special [snippet/type/Invalid] type.
 */
object InvalidType : StaticType(), Stayless {
    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        structureSink.value(OutToks.invalidWord.text)
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        tokenSink.emit(OutToks.invalidWord)
    }

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean =
        other is InvalidType

    override fun hashCode(bnr: ButNotRecursively): Int = 0x79690832
}

/** A named type with any actual bindings for type parameters. */
class NominalType private constructor(
    val definition: TypeDefinition,
    val bindings: List<TypeActual>,
) : SimpleType() {
    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean {
        if (this === other) { return true }
        if (other !is NominalType) { return false }
        if (this.definition !== other.definition) { return false }

        val aBindings = bindings
        val bBindings = other.bindings
        if (aBindings.size != bBindings.size) { return false }

        return bnr.compute(
            TypeActualEquals,
            this to other,
            true,
        ) {
            for (i in aBindings.indices) {
                val a = aBindings[i]
                val b = bBindings[i]
                if (!a.equals(b, bnr)) {
                    return@compute false
                }
            }
            true
        }
    }

    override fun hashCode(bnr: ButNotRecursively): Int =
        bnr.compute(TypeActualHashCode, this, 0) {
            var hc = definition.hashCode()
            for (b in bindings) {
                hc = hc * 31 + b.hashCode(bnr)
            }
            hc
        }

    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        if (bindings.isEmpty()) {
            structureSink.value(definition.name)
        } else {
            val done = bnr.compute(TypeActualsDestructure, this, false) {
                structureSink.arr {
                    value("Nominal")
                    value(definition.name)
                    bindings.forEach {
                        it.destructure(this, bnr)
                    }
                }
                true
            }
            if (!done) {
                structureSink.value("...")
            }
        }
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        val done = bnr.compute(TypeActualsRenderTo, this, false) {
            definition.renderName(tokenSink)
            if (bindings.isNotEmpty()) {
                tokenSink.emit(OutToks.leftAngle)
                for (i in bindings.indices) {
                    if (i != 0) {
                        tokenSink.emit(OutToks.comma)
                    }
                    bindings[i].renderTo(tokenSink, bnr)
                }
                tokenSink.emit(OutToks.rightAngle)
            }
            true
        }
        if (!done) { tokenSink.emit(OutToks.ellipses) }
    }

    override fun addStays(s: StaySink) {
        definition.addStays(s)
        for (binding in bindings) {
            binding.addStays(s)
        }
    }

    /** If no bindings exist for formals. Having empty formals also means not unbound. */
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

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean {
        if (this === other) { return true }
        if (other !is FunctionType) { return false }

        val aTypeFormals = this.typeFormals
        val bTypeFormals = other.typeFormals
        if (aTypeFormals.size != bTypeFormals.size) { return false }

        val aValueFormals = this.valueFormals
        val bValueFormals = other.valueFormals
        if (aValueFormals.size != bValueFormals.size) { return false }

        return bnr.compute(TypeActualEquals, this to other, true) {
            val aReturnType = returnType
            val bReturnType = other.returnType
            if (!aReturnType.equals(bReturnType, bnr)) {
                return@compute false
            }
            for (i in aTypeFormals.indices) {
                val a = aTypeFormals[i].internal
                val b = bTypeFormals[i].internal
                if (!a.equals(b, bnr)) { return@compute false }
            }
            for (i in aValueFormals.indices) {
                val a = aValueFormals[i]
                val b = bValueFormals[i]
                if (!a.equals(b, bnr)) { return@compute false }
            }
            true
        }
    }

    override fun hashCode(bnr: ButNotRecursively): Int = bnr.compute(
        TypeActualHashCode,
        this,
        0,
    ) {
        var hc = -0x68630b7a
        for (tf in typeFormals) {
            hc = hc * 31 + tf.hashCode()
        }
        for (vf in valueFormals) {
            hc = hc * 31 + vf.hashCode(bnr)
        }
        hc = hc * 31 + returnType.hashCode(bnr)
        hc
    }

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            typeFormals.forEach { it.addStays(s) }
            valueFormals.forEach { it.staticType.addStays(s) }
            returnType.addStays(s)
        }
    }

    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        val done = bnr.compute(TypeActualsDestructure, this, false) {
            structureSink.obj {
                key("typeFormals", isDefault = typeFormals.isEmpty()) {
                    arr {
                        typeFormals.forEach { it.internal.destructure(this, bnr) }
                    }
                }
                key("valueFormals") {
                    arr {
                        valueFormals.forEach { it.destructure(this, bnr) }
                    }
                }
                key("returnType") {
                    returnType.destructure(this)
                }
            }
            true
        }
        if (!done) {
            structureSink.value("...")
        }
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        val done = bnr.compute(TypeActualsRenderTo, this, false) {
            // As long as we render this way, TypeOpPrecedence.SelfContained works.
            // If this changes to do arrow style rendering, then we'll need a TypeOpPrecedence.Arrow
            tokenSink.emit(OutToks.fnWord)

            // Render type formals inside <...>
            if (typeFormals.isNotEmpty()) {
                tokenSink.emit(OutToks.leftAngle)
                typeFormals.forEachIndexed { i, el ->
                    if (i != 0) {
                        tokenSink.emit(OutToks.comma)
                    }
                    el.internal.renderTo(tokenSink, bnr)
                }
                tokenSink.emit(OutToks.rightAngle)
            }

            // Render value formals inside (...)
            if (valueFormals.isNotEmpty()) {
                tokenSink.emit(OutToks.leftParen)
                valueFormals.forEachIndexed { i, el ->
                    if (i != 0) {
                        tokenSink.emit(OutToks.comma)
                    }
                    el.renderTo(tokenSink, bnr)
                }
                tokenSink.emit(OutToks.rightParen)
            }

            // TODO: Is the associativity of `:` right so that
            //     let f: fn: Int = fn { 0 }
            // works?
            tokenSink.emit(OutToks.colon)

            val returnTypeOpPrecedence = returnType.precedence
            if (returnTypeOpPrecedence >= TypeOpPrecedence.Postfixed) {
                returnType.renderTo(tokenSink, bnr)
            } else {
                tokenSink.emit(OutToks.leftParen)
                returnType.renderTo(tokenSink, bnr)
                tokenSink.emit(OutToks.rightParen)
            }
            true
        }
        if (!done) {
            tokenSink.emit(OutToks.ellipses)
        }
    }

    override val precedence: TypeOpPrecedence
        // So `?` suffix doesn't spuriously attach to return type.
        get() = TypeOpPrecedence.Fn

    companion object {
        internal fun makeInternalOnly(
            typeFormals: List<TypeFormal>,
            valueFormals: List<ValueFormal>,
            returnType: StaticType,
        ): FunctionType = FunctionType(
            typeFormals = typeFormals,
            valueFormals = valueFormals,
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

        override fun destructure(structureSink: StructureSink) {
            destructure(structureSink, ButNotRecursively())
        }

        internal fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
            structureSink.obj {
                key("symbol") { value(symbol) }
                key("type") { staticType.destructure(this, bnr) }
                key("isOptional") { value(isOptional) }
            }
        }

        override fun renderTo(tokenSink: TokenSink) = renderTo(tokenSink, ButNotRecursively())

        internal fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
            if (isOptional) {
                // For now, render as named just those that are optional.
                tokenSink.emit(OutputToken(symbol?.text ?: "_", OutputTokenType.Name))
                tokenSink.postfixOp("?")
                tokenSink.emit(OutToks.colon)
            }
            staticType.renderTo(tokenSink, bnr)
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is ValueFormal && equals(other, ButNotRecursively())

        override fun hashCode(): Int = hashCode(ButNotRecursively())

        internal fun equals(other: ValueFormal, bnr: ButNotRecursively): Boolean {
            if (this.symbol != other.symbol) { return false }
            if (this.isOptional != other.isOptional) { return false }
            return staticType.equals(other.staticType, bnr)
        }

        internal fun hashCode(bnr: ButNotRecursively): Int =
            symbol.hashCode() + 31 * (isOptional.hashCode() + 31 * staticType.hashCode(bnr))
    }
}

/** Union type */
class OrType private constructor(val members: Set<StaticType>) : StaticType() {
    override val precedence get() = TypeOpPrecedence.Or

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean {
        if (this === other) { return true }
        if (other !is OrType) { return false }
        val aMembers = this.members
        val bMembers = other.members
        if (aMembers.size != bMembers.size) { return false }
        return bnr.compute(TypeActualEquals, this to other, true) {
            for ((a, b) in aMembers zip bMembers) {
                if (!a.equals(b, bnr)) { return@compute false }
            }
            true
        }
    }

    override fun hashCode(bnr: ButNotRecursively): Int = bnr.compute(
        TypeActualHashCode, this, 0,
    ) {
        var hc = -0x71153C33
        for (m in members) {
            hc = hc * 31 + m.hashCode(bnr)
        }
        hc
    }

    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        val done = bnr.compute(TypeActualsDestructure, this, false) {
            structureSink.arr {
                value("Or")
                members.forEach {
                    it.destructure(this, bnr)
                }
            }
            true
        }
        if (!done) {
            structureSink.value("...")
        }
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
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
            renderParenthesized(TypeOpPrecedence.Postfixed, t, tokenSink, bnr)
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
            renderParenthesized(precedence, member, tokenSink, bnr)
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

    override fun equals(other: TypeActual, bnr: ButNotRecursively): Boolean {
        if (this === other) { return true }
        if (other !is AndType) { return false }
        val aMembers = this.members
        val bMembers = other.members
        if (aMembers.size != bMembers.size) { return false }
        return bnr.compute(TypeActualEquals, this to other, true) {
            for ((a, b) in aMembers zip bMembers) {
                if (!a.equals(b, bnr)) { return@compute false }
            }
            true
        }
    }

    override fun hashCode(bnr: ButNotRecursively): Int = bnr.compute(
        TypeActualHashCode, this, 0,
    ) {
        var hc = 0x3922FF90
        for (m in members) {
            hc = hc * 31 + m.hashCode(bnr)
        }
        hc
    }

    override fun destructure(structureSink: StructureSink, bnr: ButNotRecursively) {
        val done = bnr.compute(TypeActualsDestructure, this, false) {
            structureSink.arr {
                value("And")
                members.forEach {
                    value(it)
                }
            }
            true
        }
        if (!done) {
            structureSink.value("...")
        }
    }

    override fun renderTo(tokenSink: TokenSink, bnr: ButNotRecursively) {
        members.forEachIndexed { i, member ->
            if (i != 0) {
                tokenSink.emit(OutToks.amp)
            }
            renderParenthesized(precedence, member, tokenSink, bnr)
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
        returnType: StaticType,
    ): FunctionType = FunctionType.makeInternalOnly(typeFormals, valueFormals, returnType)
    fun fn(
        typeFormals: List<TypeFormal>,
        valueFormals: List<StaticType>,
        returnType: StaticType,
    ) = fnDetails(
        typeFormals = typeFormals,
        valueFormals = valueFormals.map {
            FunctionType.ValueFormal(symbol = null, staticType = it, isOptional = false)
        },
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
    ): StaticType {
        val subMapped = when (t) {
            InvalidType, BubbleType, TopType -> t
            is NominalType ->
                nominal(m.mapDefinition(t.definition), t.bindings.map { map(it, m) })
            is FunctionType -> fnDetails(
                typeFormals = t.typeFormals.map { typeFormal ->
                    m.mapDefinition(typeFormal) as TypeFormal
                },
                valueFormals = t.valueFormals.map { valueFormal ->
                    valueFormal.copy(staticType = map(valueFormal.staticType, m))
                },
                returnType = map(t.returnType, m),
            )
            is OrType -> or(t.members.map { map(it, m) })
            is AndType -> and(t.members.map { map(it, m) })
        }
        return m.mapType(subMapped)
    }

    fun map(
        b: TypeActual,
        m: TypePartMapper,
    ): TypeActual {
        val subMapped = when (b) {
            is StaticType -> map(b, m)
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
        returnType.mentionsInvalid || valueFormals.any { it.staticType.mentionsInvalid }
    InvalidType -> true
    is OrType -> members.any { it.mentionsInvalid }
    is AndType -> members.any { it.mentionsInvalid }
}

/** Determine if any component is invalid. */
val Type2.mentionsInvalid: Boolean get() = this.mentions { it == WellKnownTypes.invalidTypeDefinition }
fun Type2.mentions(definitionPredicate: (TypeDefinition) -> Boolean): Boolean =
    definitionPredicate(this.definition) || this.bindings.any { it.mentions(definitionPredicate) }

val StaticType.isBooleanLike: Boolean get() = isTypeOrNever(WellKnownTypes.booleanTypeDefinition)
val StaticType.isVoidLike: Boolean get() = excludeBubble(this).isTypeOrNever(WellKnownTypes.voidTypeDefinition)

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

val StaticType.nullity: Nullity get() = when (this) {
    is FunctionType,
    is BubbleType,
    is InvalidType,
    -> Nullity.NonNull
    is NominalType -> if (definition == WellKnownTypes.nullTypeDefinition) {
        Nullity.OrNull
    } else {
        Nullity.NonNull
    }
    is AndType,
    is OrType,
    -> {
        val members = if (this is AndType) { members } else { (this as OrType).members }
        if (members.any { it.nullity == Nullity.OrNull }) { Nullity.OrNull } else {
            Nullity.NonNull
        }
    }
    is TopType -> Nullity.OrNull
}

/**
 * Simplifies a type by removing annotation types like `Null` or `Bubble`, if present.
 * See [SimplifyStaticType] for details.
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
 * are simply ignored. For example, Java has no explicit "null" type. Rather, it's indicated through `@Nullable`
 * annotations.
 *
 * This class assumes that types can be decomposed into a single principal type, usually a nominal type, and some
 * number of annotations including Null, Bubble, Never, etc.
 *
 * Not all annotation types are bottom-like, especially the Void type is a principal type. These distinctions are
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
