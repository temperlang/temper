package lang.temper.be.csharp

import lang.temper.common.mutableIdentityMapOf
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.OutName

/**
 * [13.15 the yield statement] says
 *
 * > It is a compile-time error for a `yield return` statement to appear
 * > anywhere in a `try` statement that contains any *catch_clauses*.
 *
 * That means, if a Temper `yield` or `await` appears inside an `orelse`,
 * I need to repair the code that's produces.
 *
 * "[Labels and the goto statement]" suggests that `goto` is considered
 * helpful in this situation.
 *
 * ```cs
 * try {
 *   One();
 *   yield return value;  // NOT OK
 *   Two();
 * } catch (Exception ignored) {
 *   Recover();
 * }
 *
 * // is equivalent to
 *
 * try {
 *   One();
 * } catch (Exception ignored) {
 *   goto CAUGHT;
 * }
 * yield return value; // COOL COOL
 * try {
 *   Two();
 * } catch (Exception ignored) {
 *   goto CAUGHT;
 * }
 * goto AFTER_CAUGHT:
 * CAUGHT:
 *   Recover();
 *
 * AFTER_CAUGHT:
 *   ;
 * ```
 *
 * [13.15 the yield statement]: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/statements#1315-the-yield-statement
 * [Labels and the goto statement]: https://learn.microsoft.com/en-us/cpp/cpp/labeled-statements?view=msvc-170#labels-and-the-goto-statement
 */
internal class YieldOrDoNotYieldThereIsNoTry(
    private val unusedIdMaker: (Position, String) -> CSharp.Identifier,
) {
    fun convertFnBody(fnBody: CSharp.BlockStatement): CSharp.BlockStatement =
        convertBlock(fnBody, null)

    private fun convertStatement(
        statement: CSharp.Statement,
        currentCatchLabel: OutName?,
    ): CSharp.Statement = when (statement) {
        is CSharp.BreakStatement,
        is CSharp.ContinueStatement,
        is CSharp.ExpressionStatement,
        is CSharp.GotoStatement,
        is CSharp.MethodDecl,
        is CSharp.LocalVariableDecl,
        is CSharp.ReturnStatement,
        is CSharp.YieldReturn,
        -> statement.deepCopy()

        is CSharp.BlockStatement -> convertBlock(statement, currentCatchLabel)
        is CSharp.IfStatement -> maybeExtractTestIntoTry(
            currentCatchLabel = currentCatchLabel,
            stmt = statement,
            test = statement.test,
            reincorporateTest = { ifStmt, decl, testId ->
                ifStmt.test = testId
                CSharp.BlockStatement(ifStmt.pos, listOf(decl, ifStmt))
            },
            convert = {
                convertIf(statement, currentCatchLabel)
            },
        )
        is CSharp.LabeledStatement -> convertLabeled(statement, currentCatchLabel)
        is CSharp.TryStatement -> convertTry(statement, currentCatchLabel) // non-yielding
        is CSharp.WhileStatement -> maybeExtractTestIntoTry(
            currentCatchLabel = currentCatchLabel,
            stmt = statement,
            test = statement.test,
            reincorporateTest = { whileStmt, decl, testId ->
                // while (test) { ... }
                // ->
                // while (true) { let test = ...; if (!test) { break } ... }
                whileStmt.test = CSharp.Identifier(whileStmt.test.pos.leftEdge, OutName("true", null))
                whileStmt.body.statements = listOf(
                    decl,
                    CSharp.IfStatement(
                        testId.pos,
                        CSharp.Operation(
                            testId.pos,
                            null,
                            CSharp.Operator(testId.pos.leftEdge, CSharpOperator.BoolComplement),
                            testId,
                        ),
                        CSharp.BreakStatement(testId.pos.rightEdge),
                        null,
                    ),
                ) + whileStmt.body.statements
                whileStmt
            },
            convert = {
                convertWhile(statement, currentCatchLabel)
            },
        )
    }

    private fun convertBlock(
        block: CSharp.BlockStatement,
        currentCatchLabel: OutName?,
    ): CSharp.BlockStatement {
        val converted = mutableListOf<CSharp.Statement>()
        var startOfTry = 0
        val checkYielding = currentCatchLabel != null && yields(block)

        // take statements after startOfTry into a try{...}catch{goto currentCatchLabel}
        fun foldTryBefore() {
            val endOfTry = converted.size
            val stmts = converted.subList(startOfTry, endOfTry)
            // Split stmts into:
            // - variable declarations that might need to be visible to block statements
            //   that follow
            // - other statements and initializers from declarations that need to be run
            //   inside the try.
            // So for example,
            //   int i = f();
            //   g();
            // is split into
            //   int i;
            // and
            //   i = f();
            //   g();
            val beforeTry = mutableListOf<CSharp.Statement>()
            val inTry = mutableListOf<CSharp.Statement>()
            for (stmt in stmts) {
                when (stmt) {
                    is CSharp.MethodDecl -> beforeTry.add(stmt)
                    is CSharp.LocalVariableDecl -> {
                        beforeTry.add(stmt)
                        for (declarator in stmt.variables) {
                            val initializer = declarator.initializer
                            if (initializer != null) {
                                declarator.initializer = null
                                inTry.add(
                                    CSharp.ExpressionStatement(
                                        CSharp.Operation(
                                            initializer.pos,
                                            CSharp.Identifier(initializer.pos.leftEdge, declarator.variable.outName),
                                            CSharp.Operator(initializer.pos.leftEdge, CSharpOperator.Assign),
                                            initializer,
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    else -> inTry.add(stmt)
                }
            }
            stmts.clear()

            converted.addAll(beforeTry)
            if (inTry.isNotEmpty()) {
                val newTryBody = CSharp.BlockStatement(
                    inTry.spanningPosition(inTry.first().pos),
                    inTry.toList(),
                )
                converted.add(
                    CSharp.TryStatement(
                        newTryBody.pos,
                        newTryBody,
                        catchBlock = CSharp.BlockStatement(
                            newTryBody.pos.rightEdge,
                            listOf(
                                CSharp.GotoStatement(
                                    newTryBody.pos.rightEdge,
                                    CSharp.Identifier(
                                        newTryBody.pos.rightEdge,
                                        currentCatchLabel!!,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
            startOfTry = converted.size
        }

        for (s in block.statements) {
            if (s is CSharp.TryStatement) {
                if (currentCatchLabel != null) {
                    foldTryBefore()
                }
                val tryBlock: CSharp.BlockStatement = s.tryBlock
                val catchBlock: CSharp.BlockStatement? = s.catchBlock
                val finallyBlock: CSharp.BlockStatement? = s.finallyBlock
                if (catchBlock != null && yields(tryBlock)) {
                    val convertedTryStmts = mutableListOf<CSharp.Statement>()
                    val catchLabel = unusedIdMaker(catchBlock.pos.leftEdge, "CATCH")
                    val okLabel = unusedIdMaker(s.pos.rightEdge, "OK")
                    adoptOnto(convertBlock(s.tryBlock, catchLabel.outName), convertedTryStmts)
                    // If we exit normally, skip over the catch.
                    convertedTryStmts.add(
                        CSharp.GotoStatement(
                            tryBlock.pos.rightEdge,
                            CSharp.Identifier(tryBlock.pos.rightEdge, okLabel.outName),
                        ),
                    )
                    // `CATCH_LABEL: { convertedCatchStmts() }`
                    convertedTryStmts.add(
                        CSharp.LabeledStatement(
                            catchBlock.pos,
                            catchLabel,
                            convertBlock(catchBlock, currentCatchLabel),
                        ),
                    )
                    // `OK: {}` allows the leapfrog above
                    convertedTryStmts.add(
                        CSharp.LabeledStatement(
                            s.pos.rightEdge,
                            okLabel,
                            CSharp.BlockStatement(s.pos.rightEdge, emptyList()),
                        ),
                    )
                    if (finallyBlock != null) {
                        // After the catch, run any finally clause.
                        converted.add(
                            CSharp.TryStatement(
                                pos = s.pos,
                                tryBlock = CSharp.BlockStatement(
                                    convertedTryStmts.spanningPosition(
                                        convertedTryStmts.firstOrNull()?.pos
                                            ?: s.pos.leftEdge,
                                    ),
                                    convertedTryStmts.toList(),
                                ),
                                catchBlock = null,
                                finallyBlock = convertBlock(finallyBlock, currentCatchLabel),
                            ),
                        )
                    } else {
                        converted.addAll(convertedTryStmts)
                    }
                    startOfTry = converted.size
                    continue
                }
            }
            if (checkYielding && yields(s)) {
                foldTryBefore()
                val convertedStmt = convertStatement(s, currentCatchLabel)
                if (convertedStmt is CSharp.BlockStatement && s !is CSharp.BlockStatement) {
                    // We pull conditions out so that they can be evaluated inside
                    // a `try`. don't introduce extra synthetic blocks around those.
                    adoptOnto(convertedStmt, converted)
                } else {
                    converted.add(convertedStmt)
                }
                startOfTry = converted.size
            } else {
                converted.add(convertStatement(s, null))
            }
        }
        if (checkYielding) {
            foldTryBefore()
        }
        return CSharp.BlockStatement(block.pos, converted)
    }

    // Add block boundaries around clauses so that we can assume that
    // `try` clauses and `yield return` are children of blocks.
    //
    // If every `try` is in a block, then we have a place to add any
    // labeled statements for `goto`s.
    private fun clauseToBlock(
        s: CSharp.Statement,
        replace: (CSharp.BlockStatement) -> Unit,
    ): CSharp.BlockStatement {
        if (s is CSharp.BlockStatement) {
            return s
        }
        val block = CSharp.BlockStatement(s.pos, emptyList())
        replace(block)
        block.statements = listOf(s)
        return block
    }

    private fun clauseOrNullToBlock(
        s: CSharp.Statement?,
        replace: (CSharp.BlockStatement) -> Unit,
    ): CSharp.BlockStatement? = s?.let {
        clauseToBlock(it, replace)
    }

    private fun convertIf(
        statement: CSharp.IfStatement,
        currentCatchLabel: OutName?,
    ): CSharp.IfStatement {
        val consequent = convertBlock(
            clauseToBlock(statement.consequent) {
                statement.consequent = it
            },
            currentCatchLabel,
        )
        val alternate = clauseOrNullToBlock(statement.alternate) {
            statement.alternate = it
        }?.let { convertBlock(it, currentCatchLabel) }
        return CSharp.IfStatement(statement.pos, statement.test.deepCopy(), consequent, alternate)
    }

    private fun convertLabeled(
        statement: CSharp.LabeledStatement,
        currentCatchLabel: OutName?,
    ): CSharp.LabeledStatement {
        val body = convertBlock(
            clauseToBlock(statement.statement) {
                statement.statement = it
            },
            currentCatchLabel,
        )
        return CSharp.LabeledStatement(statement.pos, statement.label.deepCopy(), body)
    }

    private fun convertTry(
        statement: CSharp.TryStatement,
        outerCatchLabel: OutName?,
    ): CSharp.TryStatement {
        // Rewriting a try clause that yields is done in convertBlock

        val bodyUnconverted =
            clauseToBlock(statement.tryBlock) {
                statement.tryBlock = it
            }
        val catchUnconverted =
            clauseOrNullToBlock(statement.catchBlock) {
                statement.catchBlock = it
            }
        val finallyUnconverted =
            clauseOrNullToBlock(statement.finallyBlock) {
                statement.finallyBlock = it
            }

        val bodyBlock = convertBlock(
            bodyUnconverted,
            if (catchUnconverted == null) {
                outerCatchLabel
            } else {
                null
            },
        )
        val catchBlock =
            catchUnconverted?.let { convertBlock(it, outerCatchLabel) }
        val finallyBLock =
            finallyUnconverted?.let { convertBlock(it, outerCatchLabel) }

        return CSharp.TryStatement(
            statement.pos,
            bodyBlock,
            catchBlock,
            finallyBLock,
        )
    }

    private fun convertWhile(
        statement: CSharp.WhileStatement,
        currentCatchLabel: OutName?,
    ): CSharp.WhileStatement {
        val body = convertBlock(
            clauseToBlock(statement.body) { statement.body = it },
            currentCatchLabel,
        )
        return CSharp.WhileStatement(
            statement.pos,
            statement.test.deepCopy(),
            body,
        )
    }

    // When factoring try/catch into control flow statements we still need to
    // catch exceptions thrown during condition evaluation.
    // `if (complexCondition()) { ... }`
    // ->
    // `{ bool cond = complexCondition(); if (cond) { ... } }`
    private fun <T : CSharp.Statement> maybeExtractTestIntoTry(
        currentCatchLabel: OutName?,
        stmt: T,
        test: CSharp.Expression,
        reincorporateTest: (T, CSharp.LocalVariableDecl, CSharp.Identifier) -> CSharp.Statement,
        convert: (T) -> T,
    ): CSharp.Statement {
        if (currentCatchLabel == null || test is CSharp.Identifier) {
            return convert(stmt)
        }
        val testName = unusedIdMaker(test.pos, "cond")

        @Suppress("UNCHECKED_CAST")
        val copy = stmt.deepCopy() as T
        val leftPos = test.pos.leftEdge
        return convertStatement(
            reincorporateTest(
                copy,
                CSharp.LocalVariableDecl(
                    leftPos,
                    StandardNames.keyBool.toType(leftPos),
                    listOf(
                        CSharp.VariableDeclarator(
                            leftPos,
                            CSharp.Identifier(leftPos, testName.outName),
                            test.deepCopy(),
                        ),
                    ),
                ),
                testName,
            ),
            currentCatchLabel,
        )
    }

    private val yieldingBlocks = mutableIdentityMapOf<CSharp.BlockStatement, Boolean>()

    private fun yields(s: CSharp.Statement?): Boolean = when (s) {
        null,
        is CSharp.BreakStatement,
        is CSharp.ContinueStatement,
        is CSharp.ExpressionStatement,
        is CSharp.GotoStatement,
        is CSharp.MethodDecl,
        is CSharp.LocalVariableDecl,
        is CSharp.ReturnStatement,
        -> false

        is CSharp.YieldReturn -> true

        is CSharp.BlockStatement -> yieldingBlocks.getOrPut(s) {
            s.statements.any { yields(it) }
        }
        is CSharp.IfStatement -> yields(s.consequent) || yields(s.alternate)
        is CSharp.LabeledStatement -> yields(s.statement)
        is CSharp.TryStatement -> yields(s.tryBlock) || yields(s.catchBlock) || yields(s.finallyBlock)
        is CSharp.WhileStatement -> yields(s.body)
    }
}

private fun adoptOnto(syntheticBlock: CSharp.BlockStatement, out: MutableList<CSharp.Statement>) {
    val stmtList = syntheticBlock.statements.toList()
    syntheticBlock.statements = emptyList()
    out.addAll(stmtList)
}
