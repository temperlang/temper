package lang.temper.value

import lang.temper.common.Cons
import lang.temper.common.Freq3
import lang.temper.common.LeftOrRight
import lang.temper.common.ignore
import lang.temper.name.NameMaker
import lang.temper.type.WellKnownTypes

private data class SimplifyResult(
    val simpler: ControlFlow,
    val flowsToNext: Freq3,
    /** Jumps that might propagate out of simpler to ancestor nodes */
    val freeJumps: Set<JumpTarget>,
    val bubbles: Freq3,
)

/**
 * Simplifies and removes unnecessary constructs.
 *
 * This is important to translation because it reduces the complexity of
 * [ControlFlow.Loop]s so that they may be translated easily to PLs that
 * only have a `while (condition) { body }` loop construct.
 *
 * It does so progressively, so that late running macros can still introduce
 * new jumps.  See step 3 below for how this is important to the `for...of`
 * loop construct.
 *
 * Here's the lifecycle of a loop:
 *
 * 1. Loops may be left (`while`) or right (`do...while`) oriented.
 *    They may have an increment clause as in `for (...;...;increment){...}`.
 *    Any of their clauses may contain nested blocks and flow control
 *    as may their condition, because Temper is an expression language.
 *    See [ControlFlow.Loop.checkPosition].
 * 2. The *Weaver* pulls blocks, so the condition may no longer contain
 *    flow control.  But this means that there may be instructions that
 *    need to run before the condition.  For example, if the loop looks like
 *    `do { ... } while (if (a) { b } else { c });`, then the *Weaver*
 *    moves the `if` to before the condition and has each branch assign
 *    a temporary:
 *    `do { ... var t#0; if (a) {t#0 = b} else {t#0 = c}} while (t#0);`
 *    This means that `continue`s in the `...` part need to jump before
 *    those instructions, not right to `while (...)`.
 * 3. Block lambdas might be inlined allowing better weaving.
 *    For example, if a block lambda contains a `break` or `continue`
 *    that are lexically within a loop in its parent function, we can now
 *    link that `break` to the loop.
 *    This is important because our `for ... of` loops are syntactic sugar
 *    for inlineable calls to `foreach` methods which allows us to
 *    generate efficient index bumping loops for low-level array iteration.
 * 4. The module stager commits to all jumps in a function/module body being
 *    known and incorporated into one *BlockTree*'s *ControlFlow*.
 *    Now we can start eliminating unnecessary jumps and labels, and
 *    we can convert `continue`s that need rewriting because of inserted
 *    condition instructions.
 *    At this stage we can also convert (right oriented) `do...while` loops
 *    into (left oriented) `while` loops, and eliminate the increment/body
 *    distinction.
 *
 * Here's some examples of this progressive loop simplification.
 *
 * First, a left oriented loop with an increment clause and a complex condition.
 *
 * ```js
 * // Source code
 * for (init; a < (f() ? b : c); ++b, --c) {
 *   ...continue;...
 * }
 *
 * // Step 1: after initial processing
 * do {
 *   init;
 *   for (; do { a < (if (f()) { b } else { c } } ; do { ++b; --c }) {
 *     ...;continue;...
 *   }
 * }
 *
 * // Step 2: weaving
 * do {
 *   init;
 *   for (; true; do { ++b; --c }) {
 *     let t#0;
 *     if (f()) {
 *       t#0 = b;
 *     } else {
 *       t#0 = c;
 *     }
 *     if (! t#0) { break } // Condition check here
 *     // real body starts here
 *     bodyWrapperThatTrapsContinue: do {
 *       ...;continue;...
 *     }
 *     // continue goes here
 *   }
 * }
 *
 * // Step 3 just a while loop
 * do {
 *   init;
 *   while (true) {
 *     let t#0;
 *     if (f()) {
 *       t#0 = b;
 *     } else {
 *       t#0 = c;
 *     }
 *     if (! t#0) { break } // Condition check here
 *     // real body starts here
 *     bodyWrapperThatTrapsContinue: do {
 *       ...;break bodyWrapperThatTrapsContinue;...
 *     }
 *     // continue goes here so increment runs even on continue
 *     ++b;
 *     --c
 *   }
 * }
 * ```
 *
 * Second, a right oriented loop with a complex condition.
 *
 * ```js
 * // Source code
 * do {
 *   ...;continue;...
 * } while (f() ? g() : h());
 *
 * // Step 1
 * do {
 *   ...;continue;...
 * } while (do { if (f()) { g() } else { h() } });
 *
 * // Step 2
 * do {
 *   var t#0; // var outside is in scope of condition and body
 *   do {
 *     bodyWrapperThatTrapsContinue: do {
 *       ...;continue;...
 *     }
 *     if (f()) {
 *       t#0 = f();
 *     } else {
 *       t#0 = g();
 *     }
 *   } while (t#0);
 * }
 *
 * // Step 3
 * var t#0;
 * while (true) {
 *   bodyWrapperThatTrapsContinue: do {
 *     ...;break bodyWrapperThatTrapsContinue;...
 *   }
 *   if (f()) {
 *     t#0 = f();
 *   } else {
 *     t#0 = g();
 *   }
 *   if (! t#0) { break } // Condition check moved inside
 * }
 * ```
 */
fun simplifyControlFlow(
    /** used to resolve [BlockChildReference]s */
    block: BlockTree,
    /** The control flow to simplify */
    controlFlow: ControlFlow.StmtBlock,
    /**
     * True if compilation has reached a stage where all [ControlFlow.Jump]s
     * to labeled statements are present and have fully resolved specifiers,
     * or any unresolved specifiers should be treated as errors.
     */
    assumeAllJumpsResolved: Boolean,
    /**
     * True if compilation has reached a stage where terminal expression
     * results are stored in output variables.
     */
    assumeResultsCaptured: Boolean,
): StructuredFlow {
    val nameMaker = block.document.nameMaker

    fun truthiness(ref: BlockChildReference) =
        block.dereference(ref)?.valueContained(TBoolean)

    val loopDepth = DepthCounter()

    fun trim(
        cf: ControlFlow,
        /** Used to rewrite jump specifiers. If non-null, a replacement jump. */
        jumpSimplifier: JumpSimplifier,
        /**
         * Which jumps, if appearing at the end of a block would be no-ops because
         * control would naturally flow to where they go.
         */
        noopJumps: Cons<JumpTarget>,
    ): SimplifyResult = when (cf) {
        is ControlFlow.Jump -> {
            val copy = cf.deepCopy()
            val jump = if (JumpTarget(cf) in noopJumps) {
                null // Eliminate jump as it's a noop before rewriting.
            } else {
                val rewritten = jumpSimplifier.simplify(copy, loopDepth.get()) ?: copy
                if (JumpTarget(rewritten) in noopJumps) {
                    null // We rewrote it and now realized it's a noop.
                } else {
                    rewritten
                }
            }
            if (jump != null) {
                SimplifyResult(
                    simpler = jump,
                    flowsToNext = Freq3.Never,
                    freeJumps = setOf(JumpTarget(jump)),
                    bubbles = Freq3.Never,
                )
            } else { // Empty blocks represent no-ops
                SimplifyResult(
                    simpler = ControlFlow.StmtBlock(cf.pos, emptyList()),
                    flowsToNext = Freq3.Always,
                    freeJumps = emptySet(),
                    bubbles = Freq3.Never,
                )
            }
        }
        is ControlFlow.Stmt -> {
            val tree = block.dereference(cf.ref)?.target
            val isBubble = tree != null && isBubbleCall(tree)
            val flowsToNext = if (isBubble) {
                Freq3.Never
            } else {
                Freq3.Always
            }
            val bubbles = if (isBubble) {
                Freq3.Always
            } else {
                Freq3.Never
            }
            SimplifyResult(
                simpler = cf.deepCopy(), // Will probably have a different parent
                flowsToNext = flowsToNext,
                freeJumps = emptySet(),
                bubbles = bubbles,
            )
        }
        // Complex control flow
        is ControlFlow.If -> {
            when (truthiness(cf.condition)) {
                null -> {
                    val tTrimmed = trim(cf.thenClause, jumpSimplifier, noopJumps)
                    val eTrimmed = trim(cf.elseClause, jumpSimplifier, noopJumps)
                    var newThenClause = tTrimmed.simpler as ControlFlow.StmtBlock
                    var newElseClause = eTrimmed.simpler as ControlFlow.StmtBlock
                    if (newThenClause.isEmptyBlock() && !newElseClause.isEmptyBlock()) {
                        cf.condition.invertLogicalExpr(block)
                        val swap = newThenClause
                        newThenClause = newElseClause
                        newElseClause = swap
                    }
                    SimplifyResult(
                        simpler = ControlFlow.If(
                            pos = cf.pos,
                            condition = cf.condition,
                            thenClause = newThenClause,
                            elseClause = newElseClause,
                        ),
                        flowsToNext = Freq3.and(tTrimmed.flowsToNext, eTrimmed.flowsToNext),
                        freeJumps = tTrimmed.freeJumps + eTrimmed.freeJumps,
                        bubbles = Freq3.and(tTrimmed.bubbles, eTrimmed.bubbles),
                    )
                }

                true -> trim(cf.thenClause, jumpSimplifier, noopJumps)
                false -> trim(cf.elseClause, jumpSimplifier, noopJumps)
            }
        }
        is ControlFlow.Labeled -> {
            // If we have all the jumps we're going to get, then rewrite all continues
            // that target this to breaks.
            // See the handling below that reduces the Loop surface area by delegating
            // to this code.
            val rewriteJumps = if (assumeAllJumpsResolved && cf.continueLabel != null) {
                JumpSimplifier.compose(
                    wider = jumpSimplifier,
                    narrower = JumpSimplifier.ContinueToBreak(
                        breakLabel = cf.breakLabel,
                        continueLabel = cf.continueLabel,
                        nameMaker = nameMaker,
                        depthOfDefaultLoop = loopDepth.get(),
                    ),
                )
            } else {
                jumpSimplifier
            }
            var noopJumpsForBody = noopJumps
            if (cf.parent !is ControlFlow.OrElse) {
                // breaks in or-clauses go to the else-clause, which is not what the
                // no-op transition does.
                noopJumpsForBody = Cons(
                    JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(cf.breakLabel)),
                    noopJumpsForBody,
                )
            }
            if (cf.continueLabel != null) {
                noopJumpsForBody = Cons(
                    JumpTarget(BreakOrContinue.Continue, NamedJumpSpecifier(cf.continueLabel)),
                    noopJumpsForBody,
                )
            }

            val sr = trim(cf.stmts, rewriteJumps, noopJumpsForBody)
            val freeJumpsFromStmts = sr.freeJumps

            // The labels we'll keep
            var continueLabel = cf.continueLabel
            var breakLabel: JumpLabel? = cf.breakLabel

            val continueTargets = if (continueLabel != null) {
                setOf(
                    JumpTarget(BreakOrContinue.Continue, NamedJumpSpecifier(continueLabel)),
                    JumpTarget(BreakOrContinue.Continue, DefaultJumpSpecifier),
                )
            } else {
                emptySet()
            }
            val breakTarget = JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(cf.breakLabel))
            if (assumeAllJumpsResolved) {
                if (continueLabel != null && continueTargets.none { it in freeJumpsFromStmts }) {
                    continueLabel = null
                }
                if (breakTarget !in freeJumpsFromStmts) {
                    breakLabel = null
                }
            }
            val freeJumps = freeJumpsFromStmts - (continueTargets + setOf(breakTarget))
            val jumpsToEnd = freeJumpsFromStmts.size > freeJumps.size
            val flowsToNext = when {
                sr.flowsToNext == Freq3.Always -> Freq3.Always
                // TODO: if it always either flows next or jumps to one of this's target, then it always flows
                sr.flowsToNext == Freq3.Sometimes -> Freq3.Sometimes
                jumpsToEnd -> Freq3.Sometimes
                else -> Freq3.Never
            }
            SimplifyResult(
                simpler =
                if (breakLabel == null && continueLabel == null) {
                    sr.simpler
                } else {
                    ControlFlow.Labeled(
                        pos = cf.pos,
                        breakLabel = cf.breakLabel,
                        continueLabel = continueLabel,
                        stmts = sr.simpler as ControlFlow.StmtBlock,
                    )
                },
                flowsToNext = flowsToNext,
                freeJumps = freeJumps,
                bubbles = sr.bubbles,
            )
        }
        is ControlFlow.Loop -> {
            val condTruthiness = truthiness(cf.condition)
            if (condTruthiness == false) { // Condition is falsey, so not really a loop anymore
                when (cf.checkPosition) {
                    LeftOrRight.Left -> SimplifyResult(
                        simpler = ControlFlow.StmtBlock(cf.pos, listOf()),
                        flowsToNext = Freq3.Always,
                        freeJumps = emptySet(),
                        bubbles = Freq3.Never,
                    )
                    LeftOrRight.Right -> {
                        //     label: do (;;increment) { body } while(false);
                        // is equivalent to the below where the inner block grabs `continue`s
                        // to run the increment even if the body breaks.
                        //     label: do {
                        //       fake_break_label&label: do {
                        //         body
                        //       }
                        //       increment
                        //     }
                        // Simplify the body followed by the increment.
                        val fakeBreakLabel = nameMaker.unusedTemporaryName("fake_break")
                        val label = cf.label ?: nameMaker.unusedTemporaryName("loop")
                        val equivalent = ControlFlow.Labeled(
                            pos = cf.pos,
                            breakLabel = label,
                            continueLabel = null,
                            stmts = ControlFlow.StmtBlock(
                                pos = cf.pos,
                                stmts = listOf(
                                    ControlFlow.Labeled(
                                        pos = cf.pos,
                                        breakLabel = fakeBreakLabel,
                                        continueLabel = label,
                                        stmts = cf.body.deepCopy(),
                                    ),
                                    cf.increment.deepCopy(),
                                ),
                            ),
                        )
                        trim(equivalent, jumpSimplifier, noopJumps)
                    }
                }
            } else {
                var checkPosition = cf.checkPosition
                val convertingBody = assumeAllJumpsResolved &&
                    (checkPosition == LeftOrRight.Right || !cf.increment.isEmptyBlock())

                val continueRewriter = if (convertingBody) {
                    // If we're converting the body, make sure there is a jump target
                    // for any `continue`s we need to convert.
                    //
                    // For example, the unwrapped body is:
                    //
                    //     if (x) { continue }
                    //     ...
                    //
                    // Then, the body to convert is:
                    //
                    //     continue#123: do {
                    //       if (x) { continue }
                    //       ...
                    //     }
                    //
                    // And simplifying a labeled statement with an explicit `continue` label
                    // becomes a labeled statement with just a `break` label:
                    //
                    //     continue#123: do {
                    //       if (x) { break#123 }
                    //       ...
                    //     }
                    //
                    // That allows the entire do...while loop to convert to a `while` loop.
                    //
                    //     do {
                    //       if (x) { continue }
                    //       ...
                    //     } while (cond);
                    //
                    //     ->
                    //
                    //     while (true) {
                    //       break#123: do {
                    //         if (x) { break#123 } // does not skip condition check below
                    //         ...
                    //       }
                    //       if (!cond) { break }
                    //     }
                    //
                    // That reduces the surface area of loops that we need to support and
                    // lets us translate better to languages, like Python, that do not support
                    // the full-suite of C-like looping constructs.
                    JumpSimplifier.ContinueToBreak(
                        breakLabel = null, // Allocated on demand
                        continueLabel = cf.label,
                        nameMaker = nameMaker,
                        depthOfDefaultLoop = loopDepth.get() + 1, // + 1 since we will increment
                    )
                } else {
                    null
                }

                val specs = listOfNotNull(
                    DefaultJumpSpecifier,
                    cf.label?.let { NamedJumpSpecifier(it) },
                )
                var noopJumpsForBody: Cons<JumpTarget> = Cons.Empty
                for (spec in specs) {
                    noopJumpsForBody = Cons(JumpTarget(BreakOrContinue.Continue, spec), noopJumpsForBody)
                }
                val (bodyUnwrapped, bodyFlows, jBody, bodyBubbles) =
                    loopDepth.withDepthIncremented {
                        trim(
                            cf.body,
                            JumpSimplifier.compose(jumpSimplifier, continueRewriter),
                            noopJumpsForBody,
                        )
                    }
                val breakLabelForContinues = continueRewriter?.breakLabel
                val bodyContinues = breakLabelForContinues != null || specs.any {
                    JumpTarget(BreakOrContinue.Continue, it) in jBody
                }
                val (increment, incrFlows, jIncr, incrementBubbles) =
                    if (bodyFlows != Freq3.Never || bodyContinues) {
                        // If we sometimes exit the body, then we might enter the increment
                        loopDepth.withDepthIncremented {
                            trim(cf.increment, jumpSimplifier, Cons.Empty)
                        }
                    } else {
                        // Otherwise, the increment doesn't matter
                        SimplifyResult(
                            ControlFlow.StmtBlock(cf.increment.pos, emptyList()),
                            Freq3.Never,
                            emptySet(),
                            bodyBubbles,
                        )
                    }
                ignore(incrFlows) // We could replace condition with `true` if control never flows into it
                var condition = cf.condition
                var body = ControlFlow.StmtBlock.wrap(
                    if (breakLabelForContinues != null) {
                        ControlFlow.Labeled(
                            pos = bodyUnwrapped.pos,
                            breakLabel = breakLabelForContinues,
                            continueLabel = null, // They were erased.
                            stmts = ControlFlow.StmtBlock.wrap(bodyUnwrapped),
                        )
                    } else {
                        bodyUnwrapped
                    },
                )

                val freeJumps = mutableSetOf<JumpTarget>()
                freeJumps.addAll(jBody)
                // Remove jumps trapped by the body wrapper, but not the loop.
                breakLabelForContinues?.let {
                    freeJumps.remove(JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(it)))
                }
                // The increment is effectively part of the body as far as jumps are concerned.
                //    for (initialize; condition; increment orelse break) {
                //      body
                //    }
                // There, that `orelse break` breaks from the loop shown there, not some unseen
                // outer loop.
                freeJumps.addAll(jIncr)
                var loopBreaks = false // Is there a way out of the loop besides a false condition?
                for (specifier in specs) {
                    val nBefore = freeJumps.size
                    freeJumps.remove(JumpTarget(BreakOrContinue.Break, specifier))
                    if (nBefore != freeJumps.size) {
                        loopBreaks = true
                    }
                    freeJumps.remove(JumpTarget(BreakOrContinue.Continue, specifier))
                }

                // The loop flows to next sometimes if the body breaks, or if the condition is not truthy.
                val flowsToNext = when {
                    loopBreaks -> Freq3.Sometimes
                    condTruthiness != true -> Freq3.Sometimes
                    // Otherwise the only way out is via a free jump
                    else -> Freq3.Never
                    // TODO: if the body always breaks, then we always flow to next
                }

                val label = if (
                    cf.label != null && assumeAllJumpsResolved &&
                    BreakOrContinue.entries.toTypedArray().all {
                        JumpTarget(it, NamedJumpSpecifier(cf.label)) !in jBody
                    }
                ) {
                    null
                } else {
                    cf.label
                }

                if (convertingBody) { // Convert do...while and for(;;increment) to while
                    val conditionCheckedAtEnd = cf.checkPosition == LeftOrRight.Right
                    var conditionCheckAtEnd: ControlFlow? = null
                    if (conditionCheckedAtEnd) {
                        checkPosition = LeftOrRight.Left
                        // `break` if the condition is false
                        // `if (!condition) { break }`
                        condition.invertLogicalExpr(block)
                        if (bodyContinues || bodyFlows != Freq3.Never) {
                            conditionCheckAtEnd = ControlFlow.If(
                                condition.pos,
                                condition,
                                ControlFlow.StmtBlock.wrap(
                                    ControlFlow.Break(condition.pos.rightEdge, DefaultJumpSpecifier),
                                ),
                                ControlFlow.StmtBlock(condition.pos.rightEdge, emptyList()),
                            )
                        }
                    }

                    body = ControlFlow.StmtBlock(
                        body.pos,
                        buildList {
                            // Inline the body and increment's content.
                            for (blockToAdopt in listOf(body, increment)) {
                                ControlFlow.StmtBlock.wrap(blockToAdopt).withMutableStmtList {
                                    addAll(it)
                                    it.clear()
                                }
                            }
                            if (conditionCheckAtEnd != null) {
                                add(conditionCheckAtEnd)
                            }
                        },
                    )
                    // Set the condition to `true` now that we're checking it at the end.
                    if (conditionCheckedAtEnd) {
                        val trueConditionIndex = block.size
                        val trueConditionPos = condition.pos.leftEdge
                        block.insert(at = trueConditionIndex) {
                            V(trueConditionPos, TBoolean.valueTrue, WellKnownTypes.booleanType)
                        }
                        condition = BlockChildReference(trueConditionIndex, trueConditionPos)
                    }
                }

                // If the increment is empty, and the condition is truthy, and
                // the body exits without continuing, we're just running the body once.
                val simpler: ControlFlow
                if (
                    increment.isEmptyBlock() && condTruthiness == true &&
                    bodyFlows == Freq3.Never && !bodyContinues
                ) {
                    val blockLabel = label ?: nameMaker.unusedTemporaryName("body")
                    // Rewrite any default `break`s to `break label`.
                    val labeledBlock = ControlFlow.Labeled(
                        pos = cf.pos,
                        breakLabel = blockLabel,
                        continueLabel = null,
                        stmts = body,
                    )
                    val simplifiedBodyResult = loopDepth.withDepthIncremented {
                        trim(
                            labeledBlock,
                            jumpSimplifier = JumpSimplifier.RewriteLoopDefaults(
                                NamedJumpSpecifier(blockLabel),
                                depthOfDefaultLoop = loopDepth.get(),
                            ),
                            noopJumps = Cons(
                                JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(blockLabel)),
                                Cons(
                                    JumpTarget(BreakOrContinue.Break, DefaultJumpSpecifier),
                                ),
                            ),
                        )
                    }
                    simpler = simplifiedBodyResult.simpler
                } else {
                    simpler = ControlFlow.Loop(
                        pos = cf.pos,
                        label = label,
                        checkPosition = checkPosition,
                        condition = condition,
                        body = body,
                        increment = increment as ControlFlow.StmtBlock,
                    )
                }

                if (!convertingBody) {
                    SimplifyResult(
                        simpler = simpler,
                        flowsToNext = flowsToNext,
                        freeJumps = freeJumps,
                        bubbles = Freq3.and(bodyBubbles, incrementBubbles),
                    )
                } else {
                    // Make sure that if we've converted from a do...while to a while, that
                    // we get opportunities to fully simplify things.
                    // This lets us get away with fairly simple logic for body exits, but
                    // still avoid problems where a body doesn't exit which would otherwise
                    // lead us to miss an opportunity to eliminate a loop entirely.
                    trim(simpler, jumpSimplifier, noopJumps)
                }
            }
        }
        is ControlFlow.OrElse -> {
            val orResult = trim(cf.orClause, jumpSimplifier, noopJumps)

            var orGoesStraightToElse = false
            if (assumeAllJumpsResolved) {
                // If orElse just bubbles, then we can replace it with the else clause
                var simpler = orResult.simpler
                if (simpler is ControlFlow.StmtBlock && simpler.stmts.size == 1) {
                    simpler = simpler.stmts.first()
                }
                if (simpler is ControlFlow.Stmt) {
                    val tree = block.dereference(simpler.ref)?.target
                    orGoesStraightToElse = tree != null && isBubbleCall(tree)
                } else if (simpler is ControlFlow.Break) {
                    orGoesStraightToElse = simpler.target == NamedJumpSpecifier(cf.orClause.breakLabel)
                }
            }
            // If we got rid of the label, add it back if there's a bubble call that might
            // be rewritten to a break to it.
            val simplerOr = when (val simpler = orResult.simpler) {
                is ControlFlow.Labeled -> simpler
                else -> if (orResult.bubbles == Freq3.Never && assumeAllJumpsResolved) {
                    // Transfer to else clause might happen via a bubble() call that hasn't
                    // yet been rewritten
                    simpler
                } else {
                    ControlFlow.Labeled(
                        pos = cf.orClause.pos,
                        breakLabel = cf.orClause.breakLabel,
                        continueLabel = cf.orClause.continueLabel,
                        stmts = ControlFlow.StmtBlock.wrap(simpler),
                    )
                }
            }

            if (simplerOr !is ControlFlow.Labeled) {
                // The label was eliminated because nothing needed it
                orResult
            } else if (orGoesStraightToElse) {
                trim(cf.elseClause, jumpSimplifier, noopJumps)
            } else {
                val (_, orFlowsNext, orFreeJumps) = orResult
                val (simplerElse, elseFlowsNext, elseFreeJumps, elseBubbles) =
                    trim(cf.elseClause, jumpSimplifier, noopJumps)
                val freeJumps: Set<JumpTarget> = orFreeJumps + elseFreeJumps
                // TODO: if the or-clause always jumps to the else-clause, then it never flows next,
                // but if the else-clause always flows next, then the orelse as a whole always flows next
                val flowsToNext = Freq3.and(orFlowsNext, elseFlowsNext)
                val simpler = ControlFlow.OrElse(
                    pos = cf.pos,
                    orClause = simplerOr,
                    elseClause = simplerElse as ControlFlow.StmtBlock,
                )
                val bubbles = when {
                    orResult.bubbles == Freq3.Always -> elseBubbles
                    elseBubbles == Freq3.Never -> Freq3.Never
                    else -> Freq3.Sometimes
                }
                SimplifyResult(
                    simpler = simpler,
                    flowsToNext = flowsToNext,
                    freeJumps = freeJumps,
                    bubbles = bubbles,
                )
            }
        }
        is ControlFlow.StmtBlock -> {
            val stmts = cf.stmts
            val simplerStmts = ControlFlow.StmtBlock(cf.pos, emptyList())
            val blockFreeJumps = mutableSetOf<JumpTarget>()
            var blockFlowsToNext: Freq3 = Freq3.Always
            var blockBubbles: Freq3 = Freq3.Never
            simplerStmts.withMutableStmtList { trimmedStmtList ->
                for (i in stmts.indices) {
                    val stmt = stmts[i]
                    val noopJumpsForStmt = if (i == stmts.lastIndex) {
                        noopJumps
                    } else {
                        // Otherwise, the noop transition is to stmts[i + 1]
                        Cons.Empty
                    }
                    val (simpler, flowsNext, freeJumps, bubbles) =
                        trim(stmt, jumpSimplifier, noopJumpsForStmt)
                    if (simpler is ControlFlow.StmtBlock) {
                        simpler.withMutableStmtList { toAdopt ->
                            trimmedStmtList.addAll(toAdopt)
                            toAdopt.clear()
                        }
                    } else {
                        trimmedStmtList.add(simpler)
                    }
                    when (bubbles) {
                        Freq3.Never -> {}
                        Freq3.Always -> {
                            blockBubbles = if (blockFlowsToNext == Freq3.Always) {
                                Freq3.Always // Always flows to the bubble()
                            } else {
                                Freq3.Sometimes
                            }
                            blockFlowsToNext = Freq3.Never
                        }
                        Freq3.Sometimes -> {
                            blockBubbles = Freq3.Sometimes
                            if (blockFlowsToNext == Freq3.Always) {
                                blockFlowsToNext = Freq3.Sometimes
                            }
                        }
                    }
                    blockFlowsToNext = Freq3.min(blockFlowsToNext, flowsNext)
                    blockFreeJumps.addAll(freeJumps)
                    // If we always exit, skip any later statements as dead code.
                    if (blockFlowsToNext == Freq3.Never) { break }
                }

                // Now go back over and remove useless value references.
                // These clutter up debugging output.
                // If the result capturing assertion bit is set, then all
                // bare value references are useless.
                // Otherwise, a useless `void` is one that is not the last Stmt.
                // The last Stmt might contribute void as the termination value.
                var sawStmtAfter = false
                for (i in trimmedStmtList.indices.reversed()) {
                    val stmt =
                        trimmedStmtList[i] as? ControlFlow.Stmt ?: continue
                    if (sawStmtAfter || assumeResultsCaptured) {
                        val t = block.dereference(stmt.ref)?.target
                        if (t is ValueLeaf && (assumeResultsCaptured || t.content == void)) {
                            trimmedStmtList.removeAt(i)
                        }
                    } else {
                        sawStmtAfter = true
                    }
                }
            }
            SimplifyResult(
                simpler = simplerStmts,
                flowsToNext = blockFlowsToNext,
                freeJumps = blockFreeJumps,
                bubbles = blockBubbles,
            )
        }
    }
    val (trimmed) = trim(controlFlow, JumpSimplifier.NoChanges, Cons.Empty)
    return StructuredFlow(trimmed as ControlFlow.StmtBlock)
}

data class JumpTarget(val kind: BreakOrContinue, val target: JumpSpecifier) {
    constructor(jump: ControlFlow.Jump) : this(jump.jumpKind, jump.target)
}

private interface JumpSimplifier {
    fun simplify(jump: ControlFlow.Jump, loopDepth: Int): ControlFlow.Jump?

    object NoChanges : JumpSimplifier {
        override fun simplify(jump: ControlFlow.Jump, loopDepth: Int): ControlFlow.Jump? = null
    }

    class ContinueToBreak(
        /**
         * The label to `break` to for any matching `continue`s if known,
         * or that was allocated lazily via [nameMaker] as needed.
         */
        var breakLabel: JumpLabel?,
        /** The optional label for labeled `continue`s to rewrite */
        val continueLabel: JumpLabel?,
        /** Used to allocates a `break` label if none is available. */
        val nameMaker: NameMaker,
        /** The loop depth of the loop we're rewriting for. */
        val depthOfDefaultLoop: Int,
    ) : JumpSimplifier {
        override fun simplify(jump: ControlFlow.Jump, loopDepth: Int): ControlFlow.Jump? {
            if (jump.target == DefaultJumpSpecifier && loopDepth != depthOfDefaultLoop) {
                return null
            }
            if (jump is ControlFlow.Continue) {
                val pos = jump.pos
                val pseudoLoop = ControlFlow.Loop(
                    pos = pos,
                    label = continueLabel,
                    checkPosition = LeftOrRight.Left,
                    condition = BlockChildReference(null, pos),
                    body = ControlFlow.StmtBlock(pos, emptyList()),
                    increment = ControlFlow.StmtBlock(pos, emptyList()),
                )
                if (pseudoLoop.matches(BreakOrContinue.Continue, jump.target)) {
                    val breakLabel = this.breakLabel
                        ?: run {
                            this.breakLabel = nameMaker.unusedTemporaryName("continue")
                            this.breakLabel!!
                        }
                    return ControlFlow.Break(
                        jump.pos,
                        NamedJumpSpecifier(breakLabel),
                    )
                }
            }
            return null
        }
    }

    class RewriteLoopDefaults(
        val spec: JumpSpecifier,
        val depthOfDefaultLoop: Int,
    ) : JumpSimplifier {
        override fun simplify(jump: ControlFlow.Jump, loopDepth: Int): ControlFlow.Jump? {
            if (jump.target == DefaultJumpSpecifier && loopDepth == depthOfDefaultLoop) {
                return ControlFlow.Break(jump.pos, spec)
            }
            return null
        }
    }

    private class Compose(
        val wider: JumpSimplifier,
        val narrower: JumpSimplifier,
    ) : JumpSimplifier {
        override fun simplify(jump: ControlFlow.Jump, loopDepth: Int): ControlFlow.Jump? =
            wider.simplify(jump, loopDepth) ?: narrower.simplify(jump, loopDepth)
    }

    companion object {
        fun compose(
            wider: JumpSimplifier?,
            narrower: JumpSimplifier?,
        ): JumpSimplifier = when {
            wider == null || wider == NoChanges -> narrower ?: NoChanges
            narrower == null || narrower == NoChanges -> wider
            else -> Compose(wider = wider, narrower = narrower)
        }
    }
}

fun simplifyStructuredBlock(
    block: BlockTree,
    flow: StructuredFlow,
    assumeAllJumpsResolved: Boolean,
    assumeResultsCaptured: Boolean,
) {
    val newFlow = simplifyControlFlow(
        block,
        flow.controlFlow,
        assumeAllJumpsResolved = assumeAllJumpsResolved,
        assumeResultsCaptured = assumeResultsCaptured,
    )
    block.replaceFlow(newFlow)

    // Garbage collect unreferenced children
    val used = mutableMapOf<Int, BlockChildReference>()

    // First we mark.
    fun mark(cf: ControlFlow) {
        val ref = cf.ref
        val index = ref?.index
        if (index != null) {
            check(index !in used) // Single ownership
            used[index] = ref
        }
        for (sub in cf.clauses) {
            mark(sub)
        }
    }
    mark(newFlow.controlFlow)

    // Compact the child list onto newChildren.
    val newChildren = mutableListOf<Tree>()
    for ((index, ref) in used) {
        val child = block.child(index)
        newChildren.add(child)
        val newIndex = newChildren.lastIndex
        ref.overrideIndex(newIndex)
    }

    block.replace(block.children.indices) {
        for (child in newChildren) {
            Replant(child)
        }
    }
}

/**
 * Used to track the count of default break/continue destinations before the control flow currently
 * being simplified so that jump simplifiers can figure out whether a default jump specifier refers
 * to their owner
 */
private class DepthCounter {
    private var depth = 0
    fun get() = depth

    fun <T> withDepthIncremented(f: () -> T): T {
        depth += 1
        try {
            return f()
        } finally {
            depth -= 1
        }
    }
}
