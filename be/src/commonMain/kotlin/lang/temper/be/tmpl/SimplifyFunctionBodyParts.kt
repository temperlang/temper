package lang.temper.be.tmpl

import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.void

/**
 * Rewrite overly verbose function bodies.
 *
 * Specifically, we rewrite formulaic bodies like the below.
 *
 *     let return__123;
 *     ... // Some stuff that doesn't mention return__123
 *     return__123 = ...;
 *     return return__123;
 *
 * The frontend tends to put the return variable assignment second-to-last
 * (as above, right before the return), or right after the declaration.
 *
 * That three-statement pattern can be condensed to
 *
 *     ... // Some stuff
 *     return ...;
 */
internal fun simplifyFunctionBodyParts(
    statements: MutableList<TmpL.Statement>,
    pool: ConstantPool,
) {
    if (statements.size >= STATEMENT_COUNT_FOR_LET_ASSIGN_RETURN) {
        val lastIndex = statements.lastIndex
        val zero = statements[0]
        val last = statements[lastIndex]
        if (zero is TmpL.LocalDeclaration && last is TmpL.ReturnStatement) {
            val (returned, returnLookedThrough) = lookThroughSingleArgFn(
                last.expression, BuiltinOperatorId.PackOkResult, pool,
            )
            if (returned is TmpL.Reference && returned.id.name == zero.name.name) {
                val initIndex = statements.indexOfFirst {
                    it is TmpL.Assignment && it.left.name == zero.name.name
                }
                if (initIndex >= 0) {
                    val init = statements[initIndex] as TmpL.Assignment
                    val (initRight, initLookedThrough) = lookThroughSingleArgFn(
                        init.right, BuiltinOperatorId.UnpackOkResult, pool,
                    )
                    check(initRight != null)

                    var okToSimplify = when {
                        initLookedThrough && !returnLookedThrough -> false
                        // Can reorder because expression is very simple, like `void`.
                        initRight is TmpL.ValueReference ||
                            initRight is TmpL.BubbleSentinel -> true
                        // Not reordering by folding initializer into `return`.
                        initIndex == lastIndex - 1 -> true
                        else -> false
                    }
                    if (okToSimplify) {
                        // Can't eliminate the *return_123* local if other statements need it.
                        for (i in 1..<lastIndex) {
                            if (i != initIndex && statements[i].reads(returned.id.name)) {
                                okToSimplify = false
                                break
                            }
                        }
                    }

                    if (okToSimplify) {
                        val toReturn =
                            if (returnLookedThrough && !initLookedThrough) {
                                // Need to pack the result.
                                val preStrippedReturn = last.expression as TmpL.CallExpression
                                TmpL.CallExpression(
                                    pos = preStrippedReturn.pos,
                                    fn = preStrippedReturn.fn.deepCopy(),
                                    typeActuals = preStrippedReturn.typeActuals.deepCopy(),
                                    parameters = listOf(initRight.deepCopy()),
                                    type = preStrippedReturn.type,
                                )
                            } else if (
                                initRight is TmpL.ValueReference && initRight.value == void &&
                                pool.representationOfVoid == RepresentationOfVoid.DoNotReifyVoid
                            ) {
                                null // return;
                            } else {
                                // Release assigned from its parent
                                init.right =
                                    TmpL.ValueReference(initRight.pos, WellKnownTypes.booleanType2, TBoolean.valueFalse)
                                initRight
                            }

                        // Rewrite the statements
                        statements.removeAt(initIndex) // init
                        statements.compatRemoveFirst() // one
                        statements.compatRemoveLast() // last
                        statements.add(TmpL.ReturnStatement(initRight.pos, toReturn))
                    }
                }
            }
        }
    }
}

private const val STATEMENT_COUNT_FOR_LET_ASSIGN_RETURN = 3

private fun TmpL.Tree.reads(name: TemperName): Boolean {
    return this is TmpL.Id && this.name == name || this.children.any { it.reads(name) }
}

private fun lookThroughSingleArgFn(
    t: TmpL.Expression?,
    builtinOperatorId: BuiltinOperatorId,
    pool: ConstantPool,
): Pair<TmpL.Expression?, Boolean> {
    if (t is TmpL.CallExpression && t.parameters.size == 1) {
        val fn = t.fn
        if (fn is TmpL.FnReference) {
            val match = when (val pooled = pool.getPoolableForName(fn.id.name)) {
                is PooledValue -> (TFunction.unpackOrNull(pooled.value) as? NamedBuiltinFun)?.builtinOperatorId
                is PooledSupportCode? -> pooled?.supportCode?.builtinOperatorId
            }
            if (match == builtinOperatorId) {
                return (t.parameters[0] as? TmpL.Expression) to true
            }
        }
    }
    return t to false
}

private val ConstantPool.representationOfVoid get() =
    translator.supportNetwork.representationOfVoid(translator.genre)
