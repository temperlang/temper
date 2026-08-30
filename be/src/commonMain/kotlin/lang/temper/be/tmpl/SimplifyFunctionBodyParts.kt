package lang.temper.be.tmpl

import lang.temper.common.Cons
import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
import lang.temper.common.contains
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.JumpLabel
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
                } else {
                    val returnedId = returned.id
                    val penultIndex = statements.lastIndex - 1
                    val simplifiedSome =
                        simplifyReturns(statements.getOrNull(penultIndex), returnedId)
                    if (simplifiedSome) {
                        if (statements.subList(1, penultIndex + 1).none { mentions(it, returnedId) }) {
                            statements.compatRemoveFirst()
                            statements.compatRemoveLast()
                        }
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

private fun simplifyReturns(
    terminal: TmpL.Statement?,
    returnedId: TmpL.Id,
    loopDepth: Int = 0,
    labelsThatBreak: Cons<TmpL.JumpLabel> = Cons.Empty,
): Boolean {
    var simplified = false
    when (terminal) {
        is TmpL.Assignment,
        is TmpL.BoilerplateCodeFoldEnd,
        is TmpL.BoilerplateCodeFoldStart,
        is TmpL.BreakStatement,
        is TmpL.ContinueStatement,
        is TmpL.EmbeddedComment,
        is TmpL.ExpressionStatement,
        is TmpL.GarbageStatement,
        is TmpL.LocalDeclaration,
        is TmpL.LocalFunctionDeclaration,
        is TmpL.ModuleInitFailed,
        is TmpL.ReturnStatement,
        is TmpL.SetAbstractProperty,
        is TmpL.SetBackedProperty,
        is TmpL.ThrowStatement,
        is TmpL.YieldStatement,
        null,
        -> {}

        is TmpL.BlockStatement -> {
            val blockStatements = terminal.statements
            var assignmentToReturn: TmpL.Assignment? = null
            var nToDrop = 0
            if (loopDepth == 0) {
                val last = blockStatements.lastOrNull()
                if (last is TmpL.Assignment && last.left == returnedId) {
                    nToDrop = 1
                    assignmentToReturn = last
                }
            }
            if (assignmentToReturn == null && blockStatements.size >= 2) {
                val last = blockStatements[blockStatements.lastIndex]
                val penult = blockStatements[blockStatements.lastIndex - 1]
                if (penult is TmpL.Assignment && penult.left == returnedId) {
                    val lastBreaksToEnd = when {
                        last !is TmpL.BreakStatement -> false
                        last.label == null -> loopDepth <= 1
                        else -> last.label in labelsThatBreak
                    }
                    if (lastBreaksToEnd) {
                        nToDrop = 2
                        assignmentToReturn = penult
                    }
                }
            }
            if (assignmentToReturn != null) {
                terminal.statements = buildList {
                    addAll(blockStatements.subList(0, blockStatements.size - nToDrop))
                    add(
                        TmpL.ReturnStatement(
                            assignmentToReturn.pos,
                            assignmentToReturn.right.deepCopy(),
                        ),
                    )
                }
                simplified = true
            } else {
                simplified = simplifyReturns(
                    blockStatements.lastOrNull(),
                    returnedId,
                    loopDepth = loopDepth,
                    labelsThatBreak = labelsThatBreak,
                )
            }
        }
        is TmpL.ComputedJumpStatement -> {
            for (case in terminal.cases) {
                if (simplifyReturns(case.body, returnedId, loopDepth, labelsThatBreak)) {
                    simplified = true
                }
            }
            if (simplifyReturns(terminal.elseCase.body, returnedId, loopDepth, labelsThatBreak)) {
                simplified = true
            }
        }
        is TmpL.IfStatement -> {
            if (simplifyReturns(terminal.consequent, returnedId, loopDepth, labelsThatBreak)) {
                simplified = true
            }
            if (simplifyReturns(terminal.alternate, returnedId, loopDepth, labelsThatBreak)) {
                simplified = true
            }
        }
        is TmpL.LabeledStatement -> {
            val label = terminal.label
            simplified = simplifyReturns(terminal.statement, returnedId, loopDepth, Cons(label, labelsThatBreak))
        }
        is TmpL.TryStatement -> {
            if (simplifyReturns(terminal.tried, returnedId, loopDepth, labelsThatBreak)) {
                simplified = true
            }
            if (simplifyReturns(terminal.recover, returnedId, loopDepth, labelsThatBreak)) {
                simplified = true
            }
        }
        is TmpL.WhileStatement -> {
            simplified = simplifyReturns(terminal.body, returnedId, loopDepth + 1, labelsThatBreak)
        }
    }
    return simplified
}

private fun mentions(t: TmpL.Tree, id: TmpL.Id): Boolean {
    if (t is TmpL.Id && t == id) { return true }
    for (child in t.children) {
        if (mentions(child, id)) { return true }
    }
    return false
}
