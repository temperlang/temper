package lang.temper.be.java

import lang.temper.be.java.Java as J

/**
 * True if code after this would be unreachable by
 * [14.21. Unreachable Statements](https://docs.oracle.com/javase/specs/jls/se8/html/jls-14.html#jls-14.21)
 */
internal fun codeAfterWouldBeDeadByJavaRules(
    s: J.BlockLevelStatement?,
    onToC: ((J.TransferOfControl) -> Unit)?,
): Boolean = when (s) {
    is J.TransferOfControl -> {
        onToC?.invoke(s)
        true
    }

    is J.BlockStatement -> codeAfterWouldBeDeadByJavaRules(s.body, onToC)
    is J.IfStatement -> if (onToC == null) {
        codeAfterWouldBeDeadByJavaRules(s.consequent, onToC) &&
            codeAfterWouldBeDeadByJavaRulesElsy(s.alternate, onToC)
    } else {
        // Using `and` so that we detect nested transfers of control consistently.
        codeAfterWouldBeDeadByJavaRules(s.consequent, onToC) and
            codeAfterWouldBeDeadByJavaRulesElsy(s.alternate, onToC)
    }
    is J.LabeledStatement -> {
        val label = s.label
        var brokenFrom = false
        val labelOnToC = { toc: J.TransferOfControl ->
            if (toc is J.BreakStatement && label == toc.target) {
                brokenFrom = true
            } else if (onToC != null) {
                onToC(toc)
            }
        }
        codeAfterWouldBeDeadByJavaRules(s.stmt, labelOnToC) && !brokenFrom
    }
    is J.SwitchStatement -> {
        var switchExited = false
        val switchOnToC = { toc: J.TransferOfControl ->
            if ((toc is J.BreakStatement && toc.target == null) || toc is J.YieldStatement) {
                switchExited = true
            } else if (onToC != null) {
                onToC(toc)
            }
        }
        // There is a default and all the cases and the default have this property.
        when (val switchBlock = s.block) {
            is J.SwitchCaseBlock -> {
                val hasDefault =
                    switchBlock.cases.any { caseStmt ->
                        when (caseStmt.label) {
                            is J.SwitchCaseLabel -> false
                            is J.SwitchDefaultLabel -> true
                        }
                    }
                // TODO: If we generate `enum` switches we might
                // need to test coverage more directly since a `default`
                // is not required in that case???

                var codeAfterCaseIsDead = false
                // The way `case` fall-through works, the `switch`
                // exits normally if the last one exits normally.
                // But we need to examine each so that we know whether
                // any `break`s go to the end of the `switch`.
                for (caseStmt in switchBlock.cases) {
                    val body = caseStmt.body
                    codeAfterCaseIsDead =
                        codeAfterWouldBeDeadByJavaRules(body.lastOrNull(), switchOnToC)
                }
                // If everything `return`ed, for example, instead of breaking
                // to the end of the `switch`, then this is true.

                hasDefault && codeAfterCaseIsDead && !switchExited
            }
            is J.SwitchRuleBlock -> {
                // Rule blocks must be exhaustive so no need to check
                // for default.
                val allExit = switchBlock.rules.all { rule ->
                    when (rule) {
                        is J.BlockRuleStatement -> codeAfterWouldBeDeadByJavaRules(rule.block, switchOnToC)
                        is J.ExpressionRuleStatement -> false
                        is J.ThrowRuleStatement -> true
                    }
                }
                allExit && !switchExited
            }
        }
    }
    is J.TryStatement -> {
        var dead = true // Look for counter-evidence
        // The JLS does not assume exception tracking is sound, so no need to track
        // exception types.
        // There's a normal exit when the try exits normally or any of the catch blocks do.
        if (!codeAfterWouldBeDeadByJavaRules(s.bodyBlock, onToC)) {
            dead = false
        }
        if (dead || onToC != null) {
            for (catchBlock in s.catchBlocks) {
                if (!codeAfterWouldBeDeadByJavaRules(catchBlock.body, onToC)) {
                    dead = false
                    if (onToC == null) { break }
                }
            }
        }
        // But if there's a `finally` block, and it doesn't exit normally, then the whole doesn't.
        val finallyBody = s.finallyBlock?.body
        if (finallyBody != null) {
            if (codeAfterWouldBeDeadByJavaRules(finallyBody, onToC)) {
                dead = true
            }
        }

        dead
    }

    // For loops, if the condition is a Java "constant expression",
    // and it is `true`, then there has to be a free break.
    // Unfortunately, we're not in a position here to evaluate constant
    // expressions like:
    //
    //      while (DAY_ENDS_IN_Y) { ... }
    //
    // TODO: if this becomes a problem, maybe we can fixup loops that look
    // constant with a wrapper:
    //
    //      while (DAY_ENDS_IN_Y) {
    //        ...
    //      }
    //      Temper.panic();    // ERROR: UNREACHABLE BY JAVA RULES
    //
    // That is equivalent to the below, but the below does not trigger Java's
    // checker allowing Temper-inserted instructions to lexically follow it.
    //
    //      while (Temper.booleanIdentity(DAY_ENDS_IN_Y)) {
    //        ...
    //      }
    //      Temper.panic();    // ERROR: UNREACHABLE BY JAVA RULES
    is J.DoStatement, is J.WhileStatement -> {
        val (test, body, isDoStatement) = when (s) {
            is J.DoStatement -> Triple(s.test, s.body, true)
            is J.WhileStatement -> Triple(s.test, s.body, false)
        }

        // We always have to look at the body of a do/while because of cases like
        //
        // do {
        //  return 123;
        // } while(false);
        //
        // The condition doesn't matter because the body never exits normally.

        val conditionIsTrue = isDefinitelyTrue(test)

        if (!isDoStatement && onToC == null && !conditionIsTrue) {
            false
        } else {
            val label = (s.parent as? J.LabeledStatement)?.label
            var brokenFrom = false
            val loopOnToC = { toc: J.TransferOfControl ->
                if (toc is J.BreakStatement && (toc.target == null || toc.target == label)) {
                    brokenFrom = true
                } else if (onToC != null) {
                    onToC(toc)
                }
            }

            val bodyIsDead = codeAfterWouldBeDeadByJavaRules(body, loopOnToC)

            if (isDoStatement) {
                (conditionIsTrue && !brokenFrom) || bodyIsDead
            } else {
                conditionIsTrue && (!brokenFrom || bodyIsDead)
            }
        }
    }

    is J.CommentLine,
    is J.LocalClassDeclaration,
    is J.LocalInterfaceDeclaration,
    is J.LocalVariableDeclaration,
    is J.AlternateConstructorInvocation,
    is J.AssertStatement,
    is J.EmptyStatement,
    is J.ExpressionStatement,
    null,
    ->
        false
}

internal fun codeAfterWouldBeDeadByJavaRulesElsy(
    s: J.ElseBlockStatement?,
    onToC: ((J.TransferOfControl) -> Unit)?,
): Boolean = when (s) {
    is J.BlockStatement -> codeAfterWouldBeDeadByJavaRules(s, onToC)
    is J.IfStatement -> codeAfterWouldBeDeadByJavaRules(s, onToC)
    null -> false
}

internal fun codeAfterWouldBeDeadByJavaRules(
    stmts: List<J.BlockLevelStatement>,
    onToC: ((J.TransferOfControl) -> Unit)?,
): Boolean =
    if (onToC == null) {
        // No need to check statements other than the last
        codeAfterWouldBeDeadByJavaRules(stmts.lastOrNull(), onToC)
    } else {
        var dead = false
        for (s in stmts) {
            if (codeAfterWouldBeDeadByJavaRules(s, onToC)) {
                dead = true
            }
        }
        dead
    }

private fun isDefinitelyTrue(test: J.Expression): Boolean {
    return test is J.BooleanLiteral && test.value
}
