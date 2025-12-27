package lang.temper.value

import lang.temper.common.Either
import lang.temper.common.Either.Left
import lang.temper.common.Either.Right
import lang.temper.common.KBitSet
import lang.temper.common.KBitSetHelpers.contains
import lang.temper.common.allIndexed
import lang.temper.common.bitIndices
import lang.temper.common.clearBitIndices
import lang.temper.name.Symbol
import lang.temper.type.FunctionType
import lang.temper.type2.AnySignature
import lang.temper.type2.IValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormal2
import lang.temper.type2.ValueFormalKind

/**
 * The list [0, 1, ..., n] indicating that the actual expressions are already in proper positional order.
 *
 * Callers to [applicationOrderForActuals] may check if the result
 * `is` [lang.temper.value.IdentityActualOrder] to avoid unnecessary work.
 */
data class IdentityActualOrder(override val size: Int) : AbstractList<Int>() {
    override fun get(index: Int): Int {
        require(index in (0 until size))
        return index
    }

    override fun contains(element: Int): Boolean = element in (0 until size)

    override fun isEmpty(): Boolean = size == 0
}

/**
 * The order for a list of actual expressions being passed to a function to match the
 * function's signature requirements.
 *
 * For a function call with three [valueActuals], a result of `[0, 1, null, null, 2]`
 * would indicate that the first two actuals correspond to the first two formals,
 * then two `null`s need to be inserted to fill optional parameters, and finally,
 * [valueActuals]\[2\] corresponds to the final optional parameter (or rest parameter).
 *
 * @return The reordering of arguments: a list containing every index from
 *   0 to [valueActuals]`.lastIndex` exactly once that reflects the positional
 *   ordering of arguments.
 *   Left([IdentityActualOrder]) if appropriate.
 */
fun applicationOrderForActuals(
    /**
     * For each actual expression:
     *
     * - A [Symbol] if the actual is passed with a name.
     * - [TFunction] if the actual is a trailing lambda.
     * - Null otherwise.
     */
    valueActuals: List<Either<Symbol, TFunction>?>,
    sig: FunctionType,
): Either<List<Int?>, ResolutionProblem> = applicationOrderForActuals(
    valueActuals = valueActuals,
    valueFormals = sig.valueFormals,
    hasRest = sig.restValuesFormal != null,
)

/**
 * Reorders a list of actual expressions being passed to a function to match the
 * function's signature requirements.
 *
 * @return The reordering of arguments: a list containing every index from
 *   0 to [valueActuals]`.lastIndex` exactly once that reflects the positional
 *   ordering of arguments.
 *   Left([IdentityActualOrder]) if appropriate.
 */
fun applicationOrderForActuals(
    /**
     * For each actual expression:
     *
     * - A [Symbol] if the actual is passed with a name.
     * - [TFunction] if the actual is a trailing lambda.
     * - Null otherwise.
     */
    valueActuals: List<Either<Symbol, TFunction>?>,
    sig: AnySignature,
): Either<List<Int?>, ResolutionProblem> = applicationOrderForActuals(
    valueActuals = valueActuals,
    valueFormals = sig.requiredAndOptionalValueFormals,
    hasRest = sig.restValuesFormal != null,
)

/**
 * Reorders a list of actual expressions being passed to a function to match the
 * function's signature requirements.
 *
 * @return The reordering of arguments: a list containing every index from
 *   0 to [valueActuals]`.lastIndex` exactly once that reflects the positional
 *   ordering of arguments.
 *   Left([IdentityActualOrder]) if appropriate.
 */
fun applicationOrderForActuals(
    /**
     * For each actual expression:
     *
     * - A [Symbol] if the actual is passed with a name.
     * - [TFunction] if the actual is a trailing lambda.
     * - Null otherwise.
     */
    valueActuals: List<Either<Symbol, TFunction>?>,
    sig: Signature2,
): Either<List<Int?>, ResolutionProblem> = applicationOrderForActuals(
    valueActuals = valueActuals,
    valueFormals = buildList(capacity = sig.optionalInputTypes.size + sig.requiredInputTypes.size) {
        sig.requiredInputTypes.mapTo(this) {
            ValueFormal2(it, ValueFormalKind.Required)
        }
        sig.optionalInputTypes.mapTo(this) {
            ValueFormal2(it, ValueFormalKind.Optional)
        }
    },
    hasRest = sig.restInputsType != null,
)

/**
 * Reorders a list of actual expressions being passed to a function to match the
 * function's signature requirements.
 *
 * @return The reordering of arguments: a list containing every index from
 *   0 to [valueActuals]`.lastIndex` exactly once that reflects the positional
 *   ordering of arguments.
 *   Left([IdentityActualOrder]) if appropriate.
 */
fun applicationOrderForActuals(
    /**
     * For each actual expression:
     *
     * - A [Symbol] if the actual is passed with a name.
     * - [TFunction] if the actual is a trailing lambda.
     * - Null otherwise.
     */
    valueActuals: List<Either<Symbol, TFunction>?>,
    valueFormals: List<IValueFormal>,
    hasRest: Boolean,
): Either<List<Int?>, ResolutionProblem> {
    val nActuals = valueActuals.size
    val nFormals = valueFormals.size

    val hasTrailingLambda = valueActuals.lastOrNull() is Right
    val nActualsMinusTrailingLambda = nActuals - (if (hasTrailingLambda) 1 else 0)
    val noNamed = (0 until nActualsMinusTrailingLambda).all { valueActuals[it] == null }

    var nRequired = 0
    var nOptional = 0
    // True if there are no optionals or all required precede the first optional
    var optionalsAtEnd = true
    for (vf in valueFormals) {
        if (vf.isOptional) {
            nOptional += 1
        } else {
            if (nOptional != 0) {
                optionalsAtEnd = false
                break
            }
            nRequired += 1
        }
    }

    val arityOk = when {
        nActuals < nRequired -> false
        hasRest -> true
        else -> nActuals <= nFormals
    }
    if (!arityOk && noNamed) {
        return Right(
            ResolutionProblem.ArgumentListSizeMismatch(
                nPositionalActuals = nActuals,
                nFormals = nFormals,
            ),
        )
    }

    if (noNamed && optionalsAtEnd) {
        // Try a fast path.
        // If all required arguments precede all optional arguments, as is the case
        // for any function that passes static checks, and none of the actuals are
        // named, then we can do some quick checks.
        var trailingLambdaDestIndex = -1
        if (hasTrailingLambda) {
            if (nActuals - 1 >= nRequired) {
                // There are enough actuals to fill the required, even without the trailing lambda.
                // Look for the trailing lambda receiver among the optional params
                trailingLambdaDestIndex = (nRequired until nFormals).lastOrNull { i ->
                    mentionsFunctionType(valueFormals[i].type)
                } ?: -1
            }
            if (trailingLambdaDestIndex < 0) {
                // Look for the trailing lambda receiver among the required params
                trailingLambdaDestIndex = (0 until nRequired).lastOrNull { i ->
                    mentionsFunctionType(valueFormals[i].type)
                } ?: -1
            }
        }
        // If we don't need to reposition the trailing lambda, the identity transform works
        if (trailingLambdaDestIndex < 0 || trailingLambdaDestIndex == nActualsMinusTrailingLambda) {
            val nOptionalUnsatisfied = (nRequired + nOptional) - nActuals
            return Left(
                if (nOptionalUnsatisfied <= 0) {
                    IdentityActualOrder(nActuals)
                } else {
                    buildList {
                        addAll(0 until nActuals)
                        repeat(nFormals - nActuals) {
                            add(null)
                        }
                    }
                },
            )
        }

        // Found a location for the trailing lambda
        return Left(
            buildList {
                if (nActualsMinusTrailingLambda >= trailingLambdaDestIndex) {
                    // There is no null optional before the trailing lambda.

                    // Output the arguments before the trailing lambda
                    for (i in 0 until trailingLambdaDestIndex) {
                        add(i)
                    }
                    add(nActualsMinusTrailingLambda)
                    // The actuals after
                    for (i in trailingLambdaDestIndex + 1 until nActuals) {
                        add(i - 1)
                    }
                } else {
                    for (i in 0 until nActualsMinusTrailingLambda) {
                        add(i)
                    }
                    repeat(trailingLambdaDestIndex - nActualsMinusTrailingLambda) {
                        add(null)
                    }
                    add(nActualsMinusTrailingLambda)
                }
                // Pad with null
                repeat(nFormals - this.size) { add(null) }
            },
        )
    }

    return applicationOrderForActualsSlow(valueActuals, valueFormals, hasRest = hasRest)
}

internal fun applicationOrderForActualsSlow(
    valueActuals: List<Either<Symbol, TFunction>?>,
    valueFormals: List<IValueFormal>,
    hasRest: Boolean,
): Either<List<Int?>, ResolutionProblem> {
    // See if we can assign the actual input values we've got to the input parameters.
    // Step 1. Pair actuals with names to formals.
    // Step 2. Pair the actuals without names, the positional actuals, with the remaining formals
    //         via the following process.
    //    a. If there is a trailing block actual that is unassigned,
    //       i. Let trailingBlockFormal be null to indicate that we don't yet know where to put it.
    //       ii. If the number of unused actuals minus 1 (for the trailing block) is not sufficient
    //           to fill all unused, required formal parameters, then
    //           let trailingBlockFormal be the last unused required formal that is not a
    //           rest formal and that has a declared type that, ignoring nullity, is a sub-type
    //           (non-strict) of any functional interface; or null if none matches.
    //       ii. Else, the other actuals are sufficient, so calculate trailingBlockFormal as above,
    //           but ignoring whether the formal is required or optional
    //           (though still excluding rest formals).
    //
    //       NOTE: This makes it non-compatibility breaking to add optional parameters
    //       after a required trailing block parameter that are not declared with a functional
    //       interface type.
    //
    //       NOTE: By binding to the last functional interface parameter, we make this reordering
    //       idempotent.  Once we have reordered named parameters to positional, and inserted `null`
    //       actuals for missing optional parameters, any function that binds to the last actual is
    //       already in its preferred position, and the last-rule above won't reorder.
    //
    //       iii. If trailingBlockFormal is null, skip to b.
    //       iv. Else, bind the trailing block to trailingBlockFormal.
    //    b. Let nRequired = the count of formals that are unused AND not optional.
    //    c. Bind the first nRequired positional actuals to the first nRequired unused formals
    //       in order.
    //    d. Pair unused (optional) formals to unused positional actuals in order as long as
    //       either (there is no rest values parameter) or
    //       (the next optional formal's type is consistent).
    //    e. Pair remaining unbound positional actuals to the rest values formal.
    // Step 3. See if everything that was not paired off has an initializer or is marked optional.
    // If not, return an appropriate *ResolutionProblem*.

    val symbolToFormalIndex = valueFormals.mapIndexedNotNull { index, formal ->
        (formal.symbol ?: return@mapIndexedNotNull null) to index
    }.toMap()
    // Keep track of which formals have been matched to actuals by index
    val usedFormals = KBitSet()
    val usedActuals = KBitSet()

    val formalActualPairs = mutableListOf<Pair<Int, Int>>()
    // Step 1
    var nPositionalActuals = 0
    for (valueActualIndex in valueActuals.indices) {
        val key = (valueActuals[valueActualIndex] as? Left)?.item
        if (key != null) {
            val formalIndex = symbolToFormalIndex[key]
            if (formalIndex == null) {
                return Right(
                    ResolutionProblem.NamedArgumentMismatch(
                        actualIndex = valueActualIndex,
                        formalIndex = formalIndex,
                        key = key,
                    ),
                )
            } else if (usedFormals[formalIndex]) {
                return Right(
                    ResolutionProblem.DuplicateName(
                        actualIndex = valueActualIndex,
                        formalIndex = formalIndex,
                        key = key,
                    ),
                )
            }
            formalActualPairs.add(formalIndex to valueActualIndex)
            usedFormals.set(formalIndex)
            usedActuals.set(valueActualIndex)
        } else {
            nPositionalActuals += 1
        }
    }

    if (nPositionalActuals > (valueFormals.size - usedFormals.cardinality()) && !hasRest) {
        return Right(
            ResolutionProblem.ArgumentListSizeMismatch(
                nPositionalActuals = nPositionalActuals,
                nFormals = valueFormals.size,
            ),
        )
    }

    val lastFormalIndex = valueFormals.lastIndex
    val lastActualIndex = valueActuals.lastIndex
    // Step 2.a Trailing blocks
    if (
        lastActualIndex >= 0 && lastActualIndex !in usedActuals &&
        valueActuals[lastActualIndex] is Right
    ) {
        var trailingBlockFormalIndex = -1

        val nActuals = valueActuals.size
        val actualsAvailable = nActuals - usedActuals.cardinality()

        val nFormals = valueFormals.size
        val unusedFormals = usedFormals.clearBitIndices(0 until nFormals)
        val unusedRequiredFormals = unusedFormals.count { i -> !valueFormals[i].isOptional }

        val includeOptionalFormals = actualsAvailable - 1 >= unusedRequiredFormals
        for (formalIndex in usedFormals.clearBitIndices(0..lastFormalIndex).reverse()) {
            val formal = valueFormals[formalIndex]
            if ((includeOptionalFormals || !formal.isOptional) && mentionsFunctionType(formal.type)) {
                trailingBlockFormalIndex = formalIndex
                break
            }
        }
        if (trailingBlockFormalIndex >= 0) {
            formalActualPairs.add(trailingBlockFormalIndex to lastActualIndex)
            usedFormals.set(trailingBlockFormalIndex)
            usedActuals.set(lastActualIndex)
        }
    }
    // Step 2.b Find n required
    var nRequired = 0 // Actually the count of actuals required to reach the last unused, required formal.
    run {
        var nUnusedSeen = 0
        valueFormals.forEachIndexed { valueFormalIndex, valueFormal ->
            if (valueFormalIndex !in usedFormals) {
                nUnusedSeen += 1
                if (!valueFormal.isOptional) {
                    nRequired = nUnusedSeen
                }
            }
        }
    }
    // Step 2.c Required positional parameters
    run {
        var formalIndex = 0
        var actualIndex = 0
        bindLoop@
        while (nRequired > 0 && formalIndex <= lastFormalIndex && actualIndex <= lastActualIndex) {
            while (formalIndex in usedFormals) {
                if (++formalIndex > lastFormalIndex) { break@bindLoop }
            }
            while (actualIndex in usedActuals) {
                if (++actualIndex > lastActualIndex) { break@bindLoop }
            }
            formalActualPairs.add(formalIndex to actualIndex)
            usedFormals.set(formalIndex)
            usedActuals.set(actualIndex)
            nRequired -= 1
            formalIndex += 1
            actualIndex += 1
        }
    }
    // Step 2.d Optional positional parameters
    run {
        var formalIndex = 0
        var actualIndex = 0
        bindLoop@
        while (formalIndex <= lastFormalIndex && actualIndex <= lastActualIndex) {
            while (formalIndex in usedFormals) {
                if (++formalIndex > lastFormalIndex) { break@bindLoop }
            }
            while (actualIndex in usedActuals) {
                if (++actualIndex > lastActualIndex) { break@bindLoop }
            }
            val valueFormal = valueFormals[formalIndex]
            check(valueFormal.isOptional)
            formalActualPairs.add(formalIndex to actualIndex)
            usedFormals.set(formalIndex)
            usedActuals.set(actualIndex)
        }
    }
    // Step 2.e Required group to rest values parameter
    val restActualIndices = KBitSet()
    if (hasRest && usedActuals.cardinality() < valueActuals.size) {
        var actualIndex = 0
        while (actualIndex <= lastActualIndex) {
            if (actualIndex !in usedActuals) {
                restActualIndices.set(actualIndex)
                usedActuals.set(actualIndex)
            }
            actualIndex += 1
        }
    }
    // Step 3
    if (
        usedFormals.clearBitIndices(valueFormals.indices).any { formalIndex ->
            !valueFormals[formalIndex].isOptional
        }
    ) {
        return Right(
            ResolutionProblem.ArgumentListSizeMismatch(nPositionalActuals, valueFormals.size),
        )
    }
    // TODO is this needed with ArgumentListSizeMismatch check above?
    for (actualIndex in valueActuals.indices) {
        if (actualIndex !in usedActuals && actualIndex !in restActualIndices) {
            return Right(ResolutionProblem.NoFormalForActual(actualIndex))
        }
    }

    // Step 4: Convert formal/rest/actual relationships to an actual order.
    val actualOrder = mutableListOf<Int?>()
    for ((formalIndex, actualIndex) in formalActualPairs) {
        while (actualOrder.size <= formalIndex) {
            actualOrder.add(null)
        }
        actualOrder[formalIndex] = actualIndex
    }
    repeat(valueFormals.size - actualOrder.size) {
        actualOrder.add(null)
    }
    for (actualIndex in restActualIndices.bitIndices) {
        actualOrder.add(actualIndex)
    }

    return Left(
        if (actualOrder.allIndexed { i, j -> i == j }) {
            IdentityActualOrder(actualOrder.size)
        } else {
            actualOrder.toList()
        },
    )
}

private fun mentionsFunctionType(type: Type2?): Boolean =
    type != null && functionalInterfaceSymbol in type.definition.metadata

/** Returns an actuals list of the form needed by [applicationOrderForActuals] */
fun actualsListFromTree(callTree: CallTree): List<Either<Symbol, TFunction>?> = buildList {
    var i = callTree.firstArgumentIndex
    val n = callTree.size
    while (i < n) {
        val child = callTree.child(i)
        i += 1
        if (i < n) {
            val symbol = child.symbolContained
            if (symbol != null) {
                add(Left(symbol))
                i += 1
                continue
            }
        }
        if (child is FunTree) {
            add(Right(TFunction))
        } else {
            add(null)
        }
    }
}
