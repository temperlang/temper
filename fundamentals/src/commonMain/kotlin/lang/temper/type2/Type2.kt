package lang.temper.type2

import lang.temper.common.ConcatenatedListView
import lang.temper.common.LeftOrRight
import lang.temper.common.MappingListView
import lang.temper.env.Constness
import lang.temper.env.ReferentBitSet
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.join
import lang.temper.format.joinAngleBrackets
import lang.temper.format.joinParens
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.StaticType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Nullity.OrNull
import lang.temper.value.BaseReifiedType
import lang.temper.value.MacroValue
import lang.temper.value.ReifiedType
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.Value

// This file defines Type2 and a bunch of sealed supertypes so that
// Type2 can be used within the type reasoning and inference machinery
// without wrapper types.
//
// TypeSolver needs to deal with partial types as intermediate results
// while doing type inference.  TypeContext2 needs to enable reasoning
// about partial types.
// Being able to accept/return a TypeOrPartialType as input simplifies
// a lot of that code.

/** That which can be solved by a [TypeSolver] */
sealed interface Solvable : TokenSerializable

/** A type or a type solver variable or abstraction */
sealed interface TypeBoundary : Solvable

/** That which a [Solvable] resolves to. */
sealed interface Solution : TokenSerializable

/** A [Solution] for a [TypeVar] or other [TypeBoundary] */
sealed interface TypeSolution : Solution

/**
 * A placeholder solution for solver graph nodes which cannot be solved, since
 * `null` means not solved **yet** internally.
 *
 * There are a few reasons why a node might be unsolvable:
 * - no constraints.  For example:
 *
 *       let x;  // x is never assigned nor read
 *
 * - insufficient constraints.
 *   There are subtype constraints between x and y which don't
 *   help pick a type for either:
 *
 *       let x;
 *       let y;
 *       if (b) {
 *         y = x;
 *       } else {
 *         x = y;
 *       }
 */
data object Unsolvable : TypeSolution {
    // TODO: figure out a good way to store position and detail
    // info for unsolvable nodes.
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("unsolvable")
    }
}

/**
 * A solution for a list of type actuals: the actual types bound to
 * a called function's type formals in the context of the call.
 */
data class TypeListSolution(
    val types: List<TypeSolution>,
) : Solution {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutToks.leftSquare)
        types.forEachIndexed { i, t ->
            if (i != 0) {
                tokenSink.emit(OutToks.comma)
            }
            t.renderTo(tokenSink)
        }
        tokenSink.emit(OutToks.rightSquare)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }

    companion object {
        val empty = TypeListSolution(emptyList())
    }
}

/** A solution for a [SimpleVar], for example: [CallConstraint.calleeChoice] */
data class IntSolution(val n: Int) : Solution {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("$n", OutputTokenType.NumericValue))
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * By convention, type solver variable names start with U+2BC,
 * which looks like the apostrophe used as a prefix in OCaml type variables.
 */
internal const val VAR_PREFIX_CHAR = 'ʼ'

/**
 * A named solver variable.  Either solves to a type solution, [TypeVar], or some
 * other kind of solution, [SimpleVar].
 */
sealed interface SolverVar : Solvable {
    val name: String

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken(name, OutputTokenType.Name))
    }
}

/** A solver variable that should resolve to a [TypeSolution] */
data class TypeVar(override val name: String) : TypeBoundary, SolverVar {
    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * A solver variable whose [Solution] will not be a [TypeSolution] so solving it is not
 * aided by tracking and reconciling lower and upper bounds.
 */
data class SimpleVar(override val name: String) : SolverVar {
    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * A bound based on a value.
 * This is a useful bound when a value is assigned to a variable, e.g. `i = 0`
 * The type variable for `i`'s declared type can have a lower bound of `Int`
 * derived from the value.
 *
 * TODO: should we collapse complex values to partial types with variables for
 * their subvalues instead of doing this?
 */
class ValueBound(val value: Value<*>) : TypeBoundary {
    // equals and hashCode are identity based so that if there are multiple
    // of the same value appearing at different lexical locations, they are
    // not forced to the same type solution based on hashing to the same thing.

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("ValueBound")
        tokenSink.emit(OutToks.leftParen)
        value.renderTo(tokenSink)
        tokenSink.emit(OutToks.rightParen)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * A representation of a type that may help establish bounds on an eventual [TypeSolution]s.
 *
 * A [Type2] is type-like.
 * A [PartialType] is type-like
 * A [TypeVarRef] is type-like.
 */
sealed interface TypeLike : TypeBoundary {
    val definition: TokenSerializable
    val bindings: List<TypeLike>
    val nullity: Nullity
}

/**
 * A reference to a [TypeVar]'s eventual solution combined with a nullity.
 * This may be used as a type actual.
 *
 * If there's a [TypeVar] with name `x`, then these are valid partial types
 * that use a [TypeVarRef] as an actual: `List<(x)>` and `List<(x?)>`.
 *
 * [reconcilePartialTypes] allows combining information about partial type
 * bounds, which may conclude bounds for a [TypeVarRef]'s [TypeVar] based on
 * corresponding bindings in other partial type bounds.
 * For example, if the same type boundary's common bound set included
 * `MapBuilder<x, String>` and `MapBuilder<Int, y>` then reconciliation might
 * conclude `x == Int` and `y == String` which [UsesConstraint] would pick
 * up to eventually compute `MapBuilder<Int, String>` as the solution for
 * that type boundary.
 */
data class TypeVarRef(
    val typeVar: TypeVar,
    override val nullity: Nullity,
) : TypeLike {
    override val definition: TypeVar get() = typeVar
    override val bindings: List<Nothing> get() = emptyList()

    override fun renderTo(tokenSink: TokenSink) {
        if (nullity == NonNull) {
            // Make TypeVarRef with no nullity suffix visually distinct from its TypeVar.
            // This aids debugging.
            tokenSink.emit(OutToks.leftParen)
            definition.renderTo(tokenSink)
            tokenSink.emit(OutToks.rightParen)
        } else {
            definition.renderTo(tokenSink)
            nullity.renderTo(tokenSink)
        }
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * Either a [Type2] or a [PartialType].
 *
 * It helps in the type solver and related inference machinery to know, at runtime,
 * whether something that could be a partial type is, in fact, complete.
 * [PartialType.from] switches to constructing a [Type2] when the inputs
 * allow for constructing a complete type, so it and code paths that combine or map
 * partial types return [TypeOrPartialType] instead of returning [PartialType].
 */
sealed interface TypeOrPartialType : TypeLike {
    override val definition: TypeDefinition

    override fun renderTo(tokenSink: TokenSink) {
        val pos = (this as? Positioned)?.pos
        if (pos != null) {
            tokenSink.position(pos.leftEdge, LeftOrRight.Left)
        }
        var name: TemperName = definition.name
        val definition = this.definition
        if (name is ResolvedParsedName && definition is TypeShape && WellKnownTypes.isWellKnown(definition)) {
            name = name.baseName
        }
        name.renderTo(tokenSink)
        if (bindings.isNotEmpty()) {
            tokenSink.emit(OutToks.leftAngle)
            var first = true
            for (p in bindings) {
                if (first) {
                    first = false
                } else {
                    tokenSink.emit(OutToks.comma)
                }
                p.renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightAngle)
        }
        nullity.renderTo(tokenSink)
        if (pos != null) {
            tokenSink.position(pos.rightEdge, LeftOrRight.Right)
        }
    }
}

/**
 * Describes a declaration's shape.
 *
 * - A [Type2] is for any declaration of a name that refers to a value.
 * - A [Signature2] is for any declaration of a named function describing its inputs and outputs,
 *   and calling convention.
 */
sealed interface Descriptor : TokenSerializable, StayReferrer

/** A type consists of a name/definition, zero or more type parameter bindings, and a [Nullity]. */
sealed interface Type2 : TypeOrPartialType, TypeSolution, Descriptor {
    override val bindings: List<Type2>
    override val nullity: Nullity

    operator fun component1(): TypeDefinition
    operator fun component2(): List<Type2>
    operator fun component3(): Nullity

    override fun addStays(s: StaySink) {
        definition.addStays(s)
        for (binding in bindings) {
            binding.addStays(s)
        }
    }
}

/**
 * A partial type is like a [Type2] but is useful in the type
 * solver for representing types for which we lack complete info.
 *
 * For example, `List<String>` is a full-fledged type, but
 * we might know something is a *List* without knowing of what.
 *
 * If *x* is a [TypeVar], then we can represent `List<(x)>` as a partial
 * type.
 *
 * Once a type has been fully parameterized, it's a full-fledged type.
 * [from] given type bindings that are [Type2] will return a [Type2],
 * not a [PartialType].
 */
sealed interface PartialType : TypeOrPartialType {
    override val definition: TypeShape

    companion object {
        fun from(
            typeShape: TypeShape,
            bindings: List<TypeLike>,
            nullity: Nullity,
            pos: Position? = null,
        ): TypeOrPartialType = fromInternal(typeShape, bindings, nullity, pos)

        fun from(
            typeDef: TypeFormal,
            nullity: Nullity,
            pos: Position? = null,
        ): TypeOrPartialType = fromInternal(typeDef, listOf(), nullity, pos)

        private fun fromInternal(
            definition: TypeDefinition,
            bindings: List<TypeLike>,
            nullity: Nullity,
            pos: Position?,
        ): TypeOrPartialType {
            if (bindings.all { it is Type2 }) {
                // Fully specified
                return when (definition) {
                    is TypeShape -> MkType2(definition)
                        .actuals(bindings.map { it as Type2 }) // checked above
                    is TypeFormal -> MkType2(definition)
                }
                    .nullity(nullity)
                    .position(pos)
                    .get()
            }
            // Only the from(TypeShape) variant above could have forwarded a
            // non-empty binding list and only a non-empty binding list could
            // have failed the bindings.all check above.
            check(definition is TypeShape)

            // Normalize Never representations. This mirrors code in MkType2
            if (definition == neverTypeDefinition && bindings.size == 1) {
                val b = bindings[0]
                // Never<Never<T>> -> Never<T>
                // Never<Never<T>>? -> Never<T>?
                if ((b as? TypeOrPartialType)?.definition == neverTypeDefinition) {
                    val adjustedNullity = when {
                        nullity == b.nullity -> nullity
                        nullity == OrNull || b.nullity == OrNull -> OrNull
                        else -> NonNull
                    }
                    return b.withNullity(adjustedNullity)
                }
                // Never<Foo?> -> Never<Foo>?
                if (b.nullity != NonNull) {
                    val adjustedNullity = when {
                        nullity == b.nullity -> nullity
                        nullity == OrNull || b.nullity == OrNull -> OrNull
                        else -> NonNull
                    }
                    val adjustedBindings = listOf(b.withNullity(NonNull))
                    return fromInternal(definition, adjustedBindings, adjustedNullity, pos)
                }
            }

            return if (pos != null) {
                PositionedPartialTypeImpl(definition, bindings, nullity, pos)
            } else {
                PartialTypeImpl(definition, bindings, nullity)
            }
        }

        private open class PartialTypeImpl(
            override val definition: TypeShape,
            override val bindings: List<TypeLike>,
            override val nullity: Nullity,
        ) : PartialType {
            override fun equals(other: Any?): Boolean =
                other is PartialTypeImpl && definition == other.definition &&
                    bindings == other.bindings && nullity == other.nullity

            override fun hashCode(): Int =
                (definition.hashCode() + 31 * bindings.hashCode()) xor nullity.ordinal

            override fun toString() = toStringViaTokenSink { renderTo(it) }
        }

        private class PositionedPartialTypeImpl(
            definition: TypeShape,
            bindings: List<TypeLike>,
            nullity: Nullity,
            override val pos: Position,
        ) : PartialTypeImpl(definition, bindings, nullity), Positioned
    }
}

/**
 * A type parsed from source code can be positioned.
 *
 * The position does not affect *equals|hashCode*.
 */
sealed interface PositionedType : Type2, Positioned

sealed interface NonNullType : Type2 {
    override val nullity: Nullity get() = NonNull
}

/**
 * A type that accepts the special `null` value.
 */
sealed interface NullableType : Type2 {
    override val nullity: Nullity get() = OrNull
}

/**
 * A type whose name is that of a class or interface.
 * "Defined" refers to the existence of the type's definition, not
 * just a declaration the way `<T extends U>` has.
 */
sealed class DefinedType(
    override val definition: TypeShape,
    override val bindings: List<Type2>,
) : Type2 {
    override operator fun component1() = definition
    override operator fun component2() = bindings
    override operator fun component3() = nullity

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DefinedType &&
                nullity == other.nullity &&
                definition == other.definition &&
                bindings == other.bindings
            )

    override fun hashCode(): Int =
        (definition.hashCode() + 31 * bindings.hashCode()) xor nullity.ordinal

    final override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * Defined, non-null types are interesting as upper bounds in `extends` lists
 * and as the basis for a static property.
 */
sealed class DefinedNonNullType(
    override val definition: TypeShape,
    override val bindings: List<Type2>,
) : DefinedType(definition, bindings), NonNullType

/**
 * A reference to a type parameter like `<T>`.
 */
sealed class TypeParamRef(
    override val definition: TypeFormal,
) : Type2 {
    override val bindings: List<Nothing> get() = emptyList()

    override operator fun component1() = definition
    override operator fun component2() = bindings
    override operator fun component3() = nullity

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is TypeParamRef &&
                this.nullity == other.nullity &&
                definition == other.definition
            )

    override fun hashCode(): Int =
        definition.hashCode() xor nullity.ordinal

    final override fun toString() = toStringViaTokenSink { renderTo(it) }
}

fun TypeLike.withNullity(nullity: Nullity): TypeLike = when (this) {
    is TypeVarRef -> this.copy(nullity = nullity)
    is TypeOrPartialType -> this.withNullity(nullity)
}

fun TypeOrPartialType.withNullity(nullity: Nullity): TypeOrPartialType =
    if (this.nullity == nullity) {
        this
    } else {
        val pos = (this as? Positioned)?.pos
        when (val d = definition) {
            is TypeShape -> PartialType.from(d, bindings, nullity, pos)
            is TypeFormal -> PartialType.from(d, nullity, pos)
        }
    }

fun Type2.withNullity(nullity: Nullity): Type2 = if (this.nullity == nullity) {
    this
} else {
    when (this) {
        is DefinedType -> MkType2.from(this).nullity(nullity).get()
        is TypeParamRef -> MkType2.from(this).nullity(nullity).get()
    }
}

/**
 * Builder for [Type2] instances that avoid some representational hazards:
 *
 * - you can't add type bindings to a type parameter reference
 * - never types don't nest and carry nullity outside
 */
class MkType2<KIND : Type2, ACTUAL : Type2> private constructor(
    private val definition: TypeDefinition,
    private val castOut: (Type2) -> KIND,
) {
    private var pos: Position? = null
    private var args: MutableList<ACTUAL>? = null
    private var nullity: Nullity = NonNull

    fun position(pos: Position?) = this.also {
        this.pos = pos
    }

    fun actuals(moreActuals: Iterable<ACTUAL>) = this.also {
        val argList = this.args
            ?: mutableListOf<ACTUAL>().also { args = it }
        argList.addAll(moreActuals)
    }

    fun canBeNull(can: Boolean = true) =
        nullity(if (can) OrNull else NonNull)

    fun nullity(nullity: Nullity) = this.also {
        this.nullity = nullity
    }

    fun get(): KIND {
        val definition = this.definition
        val pos = this.pos
        var args = this.args?.toList() ?: listOf()
        var nullity = this.nullity

        // Normalize Never.  This mirrors code in PartialType.from
        if (definition == neverTypeDefinition && args.size == 1) {
            val (arg) = args
            if (arg.definition == neverTypeDefinition) {
                return castOut(
                    when (nullity) {
                        OrNull -> arg.withNullity(NonNull)
                        NonNull -> arg
                    },
                )
            }
            // Never<Foo?> -> Never<Foo>?
            when (arg.nullity) {
                NonNull -> {}
                OrNull -> {
                    nullity = OrNull
                    @Suppress("UNCHECKED_CAST") // Actual is either Type2 or Nothing and it's not Nothing
                    args = listOf(arg.withNullity(NonNull) as ACTUAL)
                }
            }
        }

        val result = when (nullity) {
            NonNull -> when (definition) {
                is TypeShape ->
                    if (pos != null) {
                        PositionedDefinedType(definition, args, pos)
                    } else {
                        UnpositionedDefinedType(definition, args)
                    }
                is TypeFormal ->
                    if (pos != null) {
                        PositionedTypeParamRef(definition, pos)
                    } else {
                        UnpositionedTypeParamRef(definition)
                    }
            }
            OrNull -> when (definition) {
                is TypeShape ->
                    if (pos != null) {
                        PositionedDefinedTypeOrNull(definition, args, pos)
                    } else {
                        UnpositionedDefinedTypeOrNull(definition, args)
                    }
                is TypeFormal ->
                    if (pos != null) {
                        PositionedTypeParamRefOrNull(definition, pos)
                    } else {
                        UnpositionedTypeParamRefOrNull(definition)
                    }
            }
        }
        return castOut(result)
    }

    companion object {
        operator fun invoke(typeShape: TypeShape) =
            MkType2<DefinedType, Type2>(typeShape) { it as DefinedType }
        operator fun invoke(typeFormal: TypeFormal) =
            MkType2<TypeParamRef, Nothing>(typeFormal) { it as TypeParamRef }
        fun from(t: Type2): MkType2<*, *> = when (t) {
            is DefinedType -> from(t)
            is TypeParamRef -> from(t)
        }
        fun from(t: DefinedType) = invoke(t.definition)
            .actuals(t.bindings)
            .nullity(t.nullity)
            .position((t as? Positioned)?.pos)
        fun from(t: TypeParamRef) = invoke(t.definition)
            .nullity(t.nullity)
            .position((t as? Positioned)?.pos)
        fun result(actuals: Iterable<Type2>) =
            invoke(WellKnownTypes.resultTypeDefinition).actuals(actuals)
        fun result(vararg actuals: Type2) = result(actuals.toList())
    }
}

// Multiple subtypes that allow using types like NonNullType, PositionedType, and DefinedType
// reliably in when clauses.
private class PositionedDefinedType(
    definition: TypeShape,
    bindings: List<Type2>,
    override val pos: Position,
) : DefinedNonNullType(definition, bindings), PositionedType, NonNullType

private class PositionedDefinedTypeOrNull(
    definition: TypeShape,
    bindings: List<Type2>,
    override val pos: Position,
) : DefinedType(definition, bindings), PositionedType, NullableType

private class UnpositionedDefinedType(
    definition: TypeShape,
    bindings: List<Type2>,
) : DefinedNonNullType(definition, bindings), NonNullType

private class UnpositionedDefinedTypeOrNull(
    definition: TypeShape,
    bindings: List<Type2>,
) : DefinedType(definition, bindings), NullableType

private class PositionedTypeParamRef(
    definition: TypeFormal,
    override val pos: Position,
) : TypeParamRef(definition), PositionedType, NonNullType

private class PositionedTypeParamRefOrNull(
    definition: TypeFormal,
    override val pos: Position,
) : TypeParamRef(definition), PositionedType, NullableType

private class UnpositionedTypeParamRef(
    definition: TypeFormal,
) : TypeParamRef(definition), NonNullType

private class UnpositionedTypeParamRefOrNull(
    definition: TypeFormal,
) : TypeParamRef(definition), NullableType

/**
 * Information about type parameters and return values useful in deciding which variant of group
 * of overloads to apply and how to reorder a mix of named and positional parameters to line up
 * with formal parameters.
 */
sealed class AnySignature : StayReferrer, TokenSerializable {
    abstract val requiredValueFormals: List<AnyValueFormal>
    abstract val optionalValueFormals: List<AnyValueFormal>
    abstract val requiredAndOptionalValueFormals: List<AnyValueFormal>
    abstract val allValueFormals: List<AnyValueFormal>
    abstract val restValuesFormal: AnyValueFormal?
    abstract val returnType: BaseReifiedType?
    abstract val typeFormals: List<TypeFormal>

    protected fun checkSymbolsDistinct() {
        // Symbols are distinct
        val argNames = requiredAndOptionalValueFormals.mapNotNull { it.symbol }
        require(argNames.size == argNames.toSet().size)
    }

    override fun addStays(s: StaySink) {
        for (vf in allValueFormals) {
            vf.addStays(s)
        }
        returnType?.addStays(s)
        for (tf in typeFormals) {
            tf.addStays(s)
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        // This toString is used in error messages like MessageTemplate.NotApplicableTo
        tokenSink.emit(OutToks.fnWord)
        if (typeFormals.isNotEmpty()) {
            tokenSink.emit(OutToks.leftAngle)
            typeFormals.forEachIndexed { index, typeFormal ->
                if (index != 0) { tokenSink.emit(OutToks.comma) }
                val formalName = typeFormal.word?.text?.let { ParsedName(it) }
                    ?: typeFormal.name
                tokenSink.emit(formalName.toToken(inOperatorPosition = false))
            }
            tokenSink.emit(OutToks.rightAngle)
        }
        tokenSink.emit(OutToks.leftParen)

        fun renderType(reifiedType: BaseReifiedType?) = when (reifiedType) {
            null -> tokenSink.emit(OutToks.prefixStar)
            else -> reifiedType.renderTo(tokenSink)
        }

        allValueFormals.forEachIndexed { index, valueFormal ->
            if (index != 0) { tokenSink.emit(OutToks.comma) }
            when (valueFormal.kind) {
                ValueFormalKind.Required -> {}
                ValueFormalKind.Optional -> {
                    tokenSink.emit(OutputToken("optional", OutputTokenType.Word))
                }
                ValueFormalKind.Rest -> {
                    tokenSink.emit(OutToks.prefixEllipses)
                }
            }

            val symbol = valueFormal.symbol
            val reifiedType = valueFormal.reifiedType
            if (symbol != null) {
                tokenSink.emit(ParsedName(symbol.text).toToken(inOperatorPosition = false))
                tokenSink.emit(OutToks.colon)
            }
            renderType(reifiedType)
        }
        tokenSink.emit(OutToks.rightParen)
        tokenSink.emit(OutToks.colon)

        val returnType = returnType
        if (returnType is ReifiedType) {
            withType(
                returnType.type2,
                result = { pass, fails, _ ->
                    pass.renderTo(tokenSink)
                    tokenSink.emit(OutToks.throwsWord)
                    fails.join(tokenSink, sep = OutToks.bar)
                },
                fallback = {
                    renderType(returnType)
                },
            )
        } else {
            renderType(returnType)
        }
    }

    final override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * Type information about a function/method definition.
 *
 * The types of inputs and outputs, and any type parameters
 * that are scoped to the function/method.
 *
 * Unlike the more general [AnySignature], parameters must have simple, translatable types.
 */
data class Signature2(
    val returnType2: Type2,
    /** True when there is an implicit, required value formal representing `this`. */
    val hasThisFormal: Boolean,
    val requiredInputTypes: List<Type2>,
    val optionalInputTypes: List<Type2> = emptyList(),
    val restInputsType: Type2? = null,
    override val typeFormals: List<TypeFormal> = emptyList(),
) : AnySignature(), Descriptor {
    override val returnType get() = ReifiedType(returnType2)

    override val requiredValueFormals get() =
        MappingListView(requiredInputTypes) { ValueFormal2(it, ValueFormalKind.Required) }
    override val optionalValueFormals get() =
        MappingListView(optionalInputTypes) { ValueFormal2(it, ValueFormalKind.Optional) }
    override val restValuesFormal get() = restInputsType?.let { ValueFormal2(it, ValueFormalKind.Rest) }

    override val requiredAndOptionalValueFormals: List<ValueFormal2>
        get() {
            val req = requiredValueFormals
            return if (optionalInputTypes.isNotEmpty()) {
                ConcatenatedListView(req, optionalValueFormals)
            } else {
                req
            }
        }

    override val allValueFormals: List<ValueFormal2> get() {
        val reqAndOpt = requiredAndOptionalValueFormals
        return if (restInputsType != null) {
            ConcatenatedListView(reqAndOpt, listOfNotNull(restValuesFormal))
        } else {
            reqAndOpt
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        if (typeFormals.isNotEmpty()) {
            typeFormals.joinAngleBrackets(tokenSink)
        }

        var allFormalsToRender: List<TokenSerializable> = allValueFormals
        if (hasThisFormal) {
            allFormalsToRender = buildList {
                addAll(allFormalsToRender)
                val thisFormal = this[0]
                this[0] = TokenSerializable { tokenSink ->
                    tokenSink.word("this")
                    tokenSink.emit(OutToks.infixColon)
                    thisFormal.renderTo(tokenSink)
                }
            }
        }
        allFormalsToRender.joinParens(tokenSink)

        tokenSink.emit(OutToks.rArrow)
        returnType2.renderTo(tokenSink)
    }

    val arityRange: IntRange get() {
        val min = requiredInputTypes.size
        val max = if (restInputsType != null) {
            Integer.MAX_VALUE
        } else {
            min + optionalInputTypes.size
        }
        return min..max
    }

    fun valueFormalForActual(i: Int): ValueFormal2? {
        check(i >= 0)
        if (i in requiredInputTypes.indices) {
            return ValueFormal2(requiredInputTypes[i], ValueFormalKind.Required)
        }
        val indexInOptional = i - requiredInputTypes.size
        if (indexInOptional in optionalInputTypes.indices) {
            return ValueFormal2(optionalInputTypes[indexInOptional], ValueFormalKind.Optional)
        }
        return restInputsType?.let { ValueFormal2(it, ValueFormalKind.Rest) }
    }
}

/**
 * A signature for a macro whose calls must be ironed out before code generation
 * and so whose signatures need not be translatable.
 */
data class MacroSignature(
    override val returnType: BaseReifiedType?,
    override val requiredValueFormals: List<AnyValueFormal>,
    override val optionalValueFormals: List<AnyValueFormal> = listOf(),
    override val restValuesFormal: AnyValueFormal? = null,
    override val typeFormals: List<TypeFormal> = emptyList(),
) : AnySignature() {
    init { checkSymbolsDistinct() }

    override val requiredAndOptionalValueFormals: List<AnyValueFormal> get() =
        ConcatenatedListView(requiredValueFormals, optionalValueFormals)

    override val allValueFormals: List<AnyValueFormal> get() =
        ConcatenatedListView(requiredAndOptionalValueFormals, listOfNotNull(restValuesFormal))
}

/**
 * A signature that aids in interpretation of functions that may not be fully defined,
 * and so do not have a complete [Signature2] yet.
 */
data class InterpSignature(
    override val returnType: BaseReifiedType?,
    override val requiredAndOptionalValueFormals: List<InterpValueFormal>,
    val restInputsType: Type2?,
    override val typeFormals: List<TypeFormal>,
) : AnySignature() {
    override val requiredValueFormals: List<AnyValueFormal> =
        requiredAndOptionalValueFormals.filter { it.kind == ValueFormalKind.Required }
    override val optionalValueFormals: List<AnyValueFormal> =
        requiredAndOptionalValueFormals.filter { it.kind == ValueFormalKind.Optional }
    override val restValuesFormal get() = restInputsType?.let {
        ValueFormal2(it, ValueFormalKind.Rest)
    }
    override val allValueFormals: List<AnyValueFormal>
        get() = restValuesFormal?.let {
            ConcatenatedListView(requiredAndOptionalValueFormals, listOf(it))
        } ?: requiredAndOptionalValueFormals
}

/** A minimal description of a function argument. */
interface IValueFormal {
    val symbol: Symbol?
    val reifiedType: BaseReifiedType?
    val staticType: StaticType?
    val type: Type2?
    val kind: ValueFormalKind
    val isOptional: Boolean get() = when (kind) {
        ValueFormalKind.Required -> false
        ValueFormalKind.Optional,
        ValueFormalKind.Rest,
        -> true
    }
}

/**
 * A super-type for [value formal parameters][AnyValueFormal] and the optional
 * rest parameter which collects any actual values not bound to other formals.
 */
sealed interface AbstractValueFormal : IValueFormal, StayReferrer {
    override val staticType get() = (reifiedType as? ReifiedType)?.type
    override val type get() = (reifiedType as? ReifiedType)?.type2
    val constness: Constness
    val missing: ReferentBitSet
    val defaultExpr: Value<MacroValue>?

    override fun addStays(s: StaySink) {
        reifiedType?.addStays(s)
        defaultExpr?.addStays(s)
    }
}

sealed interface AnyValueFormal : AbstractValueFormal

/**
 * Information about a parameter declared in the parenthesized argument list
 * of a function definition which binds to a single value, which bundles up
 * extra information beyond [ValueFormal2] useful during interpretation.
 */
data class InterpValueFormal(
    override val symbol: Symbol?,
    override val reifiedType: ReifiedType?,
    /** True if the formal must have a value assigned. */
    override val kind: ValueFormalKind,
    override val constness: Constness = Constness.NotConst,
    override val missing: ReferentBitSet = ReferentBitSet.empty,
    /**
     * Optionally used to compute the value.
     * During early stages, formals are declarations, possibly that include initializers.
     * During later stages, specifically after *SimplifyDeclarations*, the body uses the *isSet*
     * macro to check and supply a value.
     * So during those later stages [isOptional] may be true and initializer null.
     */
    override val defaultExpr: Value<MacroValue>? = null,
) : AnyValueFormal

/** Encapsulates information about an input in a [Signature2] */
data class ValueFormal2(
    override val type: Type2,
    override val kind: ValueFormalKind,
) : TokenSerializable, AnyValueFormal {
    override fun renderTo(tokenSink: TokenSink) {
        if (kind == ValueFormalKind.Rest) {
            tokenSink.emit(OutToks.prefixEllipses)
        }
        type.renderTo(tokenSink)
        if (kind == ValueFormalKind.Optional) {
            tokenSink.emit(OutToks.eq)
            tokenSink.emit(OutToks.ellipses)
        }
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
    override val reifiedType: ReifiedType get() = ReifiedType(type)
    override val constness: Constness get() = Constness.Const
    override val missing: ReferentBitSet get() = ReferentBitSet.empty
    override val defaultExpr: Nothing? get() = null
    override val symbol: Nothing? get() = null
}

/**
 * Information about a parameter declared in the parenthesized argument list of a function
 * definition which binds to a single value.
 */
data class MacroValueFormal(
    override val symbol: Symbol?,
    override val reifiedType: BaseReifiedType?,
    /** True if the formal must have a value assigned. */
    override val kind: ValueFormalKind,
    override val constness: Constness = Constness.NotConst,
    override val missing: ReferentBitSet = ReferentBitSet.empty,
    /**
     * Optionally used to compute the value.
     * During early stages, formals are declarations, possibly that include initializers.
     * During later stages, specifically after *SimplifyDeclarations*, the body uses the *isSet*
     * macro to check and supply a value.
     * So during those later stages [isOptional] may be true and initializer null.
     */
    override val defaultExpr: Value<MacroValue>? = null,
) : AnyValueFormal
