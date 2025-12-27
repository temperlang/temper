package lang.temper.type2

import lang.temper.common.joinedIterable
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.join
import lang.temper.format.joinAngleBrackets
import lang.temper.format.joinParens
import lang.temper.format.joinSquareBrackets
import lang.temper.format.toStringViaTokenSink
import lang.temper.type.WellKnownTypes

/**
 * Constraints drive type inferencing in the TypeSolver by moving type bound information among
 * "nodes" which correspond to type variables, or other "type boundaries."
 * They're effectively edges in the type solver graph.
 */
internal sealed class Constraint : TokenSerializable {
    abstract fun bounds(): Iterable<Solvable>

    final override fun toString() = toStringViaTokenSink { renderTo(it) }
}

/**
 * A constraint that establishes a type relationship between two [TypeLike] nodes.
 */
internal sealed class TypeConstraint : Constraint() {
    abstract operator fun component1(): TypeBoundary
    abstract operator fun component2(): TypeBoundary
}

/**
 * A type constraint that requires that [sub] is a subtype of [sup].
 * This means that all sub's bounds can become lower bounds of sup
 * and all sups bounds can become upper bounds of sub.
 */
internal data class SubTypeConstraint(
    val sub: TypeBoundary,
    val sup: TypeBoundary,
) : TypeConstraint() {
    override fun renderTo(tokenSink: TokenSink) {
        sub.renderTo(tokenSink)
        tokenSink.emit(subTypeOrEqualTok)
        sup.renderTo(tokenSink)
    }

    override fun bounds(): Iterable<Solvable> = listOf(sub, sup)
}

/** Shortcut for two [SubTypeConstraint]s such that [b] <: [a] <: [b]. */
internal data class SameTypeConstraint(
    val a: TypeBoundary,
    val b: TypeBoundary,
) : TypeConstraint() {
    override fun renderTo(tokenSink: TokenSink) {
        a.renderTo(tokenSink)
        tokenSink.emit(OutToks.eqEq)
        b.renderTo(tokenSink)
    }

    override fun bounds(): Iterable<Solvable> = listOf(a, b)
}

/**
 * A constraint that holds when [a] <: [b] or [b] <: [a].
 * This doesn't occur often, but is useful for `as` function calls
 * since these can be invoked either as narrowing or widening casts.
 */
internal data class BivariantConstraint(
    val a: TypeBoundary,
    val b: TypeBoundary,
) : TypeConstraint() {
    override fun renderTo(tokenSink: TokenSink) {
        a.renderTo(tokenSink)
        tokenSink.emit(bivariantMehTok)
        b.renderTo(tokenSink)
    }

    override fun bounds(): Iterable<Solvable> = listOf(a, b)
}

/**
 * A constraint that connects a partial type to the type variables it connects.
 *
 * For example, `Map<a, b> uses [a, b]`.
 *
 * These constraints allow the solver to choose solutions for [PartialType]s.
 *
 * For example, when *a* solves to `String`, that `Map<a, b>`
 * is equivalent to `Map<String, b>`.
 */
@ConsistentCopyVisibility
internal data class UsesConstraint private constructor(
    val typeLike: TypeLike,
    val typeVars: List<TypeVar>,
) : Constraint() {
    constructor(typeLike: TypeLike) : this(
        typeLike,
        when (typeLike) {
            is Type2 -> listOf()
            is TypeVarRef -> listOf(typeLike.typeVar)
            is PartialType -> typeLike.typeVarsUsed().toList()
        },
    )

    override fun bounds(): Iterable<Solvable> = buildList {
        add(typeLike)
        addAll(typeVars)
    }

    override fun renderTo(tokenSink: TokenSink) {
        typeLike.renderTo(tokenSink)
        tokenSink.word("uses")
        typeVars.join(tokenSink)
    }
}

/**
 * Bundles up information about a function or method call.
 *
 * This allows the type solver to, possibly over multiple solver steps,
 * to rule out some callees, and then create simpler constraints relating
 * arguments, explicit type parameters, and outputs to [PartialType]s
 * derived from the chosen [signature][Signature2].
 *
 * <!-- snippet: type-compatibility -->
 * # Type Compatibility
 *
 * When calling a function method, overloaded methods are filtered based on type and argument list compatibility.
 *
 * For example, [snippet/builtin/%2B] has variants that operate on [snippet/type/Int32] and
 * [snippet/type/Float64].
 *
 * During type checking, the Temper compiler filters out unusable variants.
 * So if two arguments whose static types are *Int32* are passed in, we can filter out the *Float64*
 * variants because we know that no *Float64* values will ever be passed in.
 *
 * An input with a static type, *I* is compatible with a function argument with static type, *A*
 * when *A* is not nullable or *I* is nullable, and any of the following are true:
 *
 * 1. *I* is a functional interface type and *A* is a block lambda or is a reference to a named function
 *    whose signature matches *I*'s apply method signature after substituting *I*'s type parameters.
 * 2. *I* is a type parameter and its bounds are compatible with *A*.
 * 3. *A* is a type parameter and *I* is compatible* with *A*'s bound
 * 4. *I* and *A* are defined types and *A*'s type shape is or inherits from *A*'s type shape.
 */
internal data class CallConstraint(
    /**
     * The possible callees.
     * The solver must whittle this down to 1 by looking for
     * incompatibilities between callees and known type bounds.
     */
    val callees: List<Callee>,
    /**
     * When the callee is chosen, this variable resolves to
     * an [IntSolution] for the index in [callees].
     * If all callees are ruled out, it is [Unsolvable].
     */
    val calleeChoice: SimpleVar,
    /**
     * Any explicit type arguments supplied for the call.
     * `Foo<String>("")` has explicit type arguments, but
     * `Foo("")` does not.
     */
    val explicitTypeArgs: List<Type2>?,
    /**
     * Vars that should solve to individual type parameter actuals.
     * This allows relating reified type bounds to type parameters.
     */
    val typeArgVars: List<TypeVar>?,
    /** Type variables representing the actual type of inputs. */
    val args: List<TypeVar>,
    /** True when the last [arg][args] is a trailing lambda that might need reordering. */
    val hasTrailingBlock: Boolean,
    /**
     * A type variable that resolves to a [TypeListSolution] for the call's actual type parameters.
     * This list will have one type for each [type formal][Signature2.typeFormals] that the
     * chosen callee's signature has.
     */
    val typeActuals: SimpleVar?,
    /**
     * A type variable that resolves to the contextualized return type **after** unpacking any *Result*.
     * This is the chosen callees return type but with any type formals resolved in the context
     * of the [typeActuals].
     *
     * Since this is the post-Result-unpack return type, it is the return type that could be assignable
     * to a variable.  In `myVar = callee(...)`, this type should be a subtype of `myVar`'s
     * declared or inferred type.
     *
     * It could resolve to *Void*.
     */
    val callPass: TypeVar,
    /**
     * Resolves to a [TypeListSolution] with the failure types of the call.
     * Empty if the chosen callee's signature does not have a *Result* return type.
     * If it does, then the contextualized types of any "thrown" types.
     */
    val callFail: SimpleVar?,
) : Constraint() {
    init {
        check(
            callFail != null ||
                callees.none { it.sig.returnType2.definition == WellKnownTypes.resultTypeDefinition },
        ) {
            "callFail=$callFail, callees=$callees"
        }
    }

    override fun bounds(): Iterable<Solvable> = joinedIterable(
        listOfNotNull(calleeChoice),
        explicitTypeArgs ?: emptyList(),
        typeArgVars ?: emptyList(),
        listOfNotNull(typeActuals),
        args,
        listOf(callPass),
        listOfNotNull(callFail),
    )

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("call")
        if (explicitTypeArgs?.isNotEmpty() == true) {
            explicitTypeArgs.joinAngleBrackets(tokenSink)
        }
        callees.joinParens(tokenSink, sep = OutToks.barBar)
        args.joinParens(tokenSink)
        tokenSink.emit(OutToks.rArrow)
        if (typeActuals != null || typeArgVars != null) {
            tokenSink.emit(OutToks.leftAngle)
            if (typeArgVars != null && typeArgVars.isNotEmpty()) {
                typeArgVars.join(tokenSink)
            }
            if (typeActuals != null) {
                if (typeArgVars != null && typeArgVars.isNotEmpty()) {
                    tokenSink.emit(OutToks.asWord)
                }
                tokenSink.emit(OutToks.prefixEllipses)
                typeActuals.renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightAngle)
        }
        calleeChoice.renderTo(tokenSink)
        tokenSink.emit(OutToks.colon)
        callPass.renderTo(tokenSink)
        if (callFail != null) {
            tokenSink.emit(OutToks.throwsWord)
            callFail.renderTo(tokenSink)
        }
    }
}

/**
 * A constraint used to build [TypeListSolution]s from multiple types.
 *
 * [receiver] resolves to that [TypeListSolution].
 *
 * Type lists are used to represent aspects of complex function calls:
 *
 * - The type actuals, the bindings for `<T, U, V>`, are built by a put constraint.
 * - The failure modes, `throws` clauses, of a function call with a *Result* type,
 *   and the failure modes that escape a block lambda's body are built by a put constraint.
 */
internal data class PutConstraint(
    val receiver: SimpleVar,
    val parts: List<TypeBoundary>,
) : Constraint() {
    override fun bounds(): Iterable<Solvable> = listOf(receiver) + parts

    override fun renderTo(tokenSink: TokenSink) {
        receiver.renderTo(tokenSink)
        tokenSink.emit(leftArrow)
        parts.joinSquareBrackets(tokenSink)
    }
}

internal val subTypeOrEqualTok =
    OutputToken("<:", OutputTokenType.Punctuation, TokenAssociation.Infix)

private val leftArrow =
    OutputToken("←", OutputTokenType.Punctuation, TokenAssociation.Infix)

internal val bivariantMehTok =
    OutputToken("~:", OutputTokenType.Punctuation, TokenAssociation.Infix)
