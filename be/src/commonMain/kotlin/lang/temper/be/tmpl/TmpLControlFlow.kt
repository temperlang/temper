package lang.temper.be.tmpl

import lang.temper.ast.TreeVisit
import lang.temper.be.BaseOutTree
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.builtin.isSetPropertyCall
import lang.temper.common.LeftOrRight
import lang.temper.common.allIndexed
import lang.temper.common.temperEscaper
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.interp.LongLivedUserFunction
import lang.temper.interp.docgenalts.DocGenAltFn
import lang.temper.interp.docgenalts.DocGenAltIfFn
import lang.temper.interp.docgenalts.DocGenAltImpliedResultFn
import lang.temper.interp.docgenalts.DocGenAltReturnFn
import lang.temper.interp.docgenalts.DocGenAltWhileFn
import lang.temper.interp.emptyValue
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import lang.temper.name.name
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.BreakOrContinue
import lang.temper.value.BubbleFn
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.DefaultJumpSpecifier
import lang.temper.value.ErrorFn
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.JumpLabel
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.NamedJumpSpecifier
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TFunction
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.UnresolvedJumpSpecifier
import lang.temper.value.ValueLeaf
import lang.temper.value.failSymbol
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.initSymbol
import lang.temper.value.isBubbleCall
import lang.temper.value.isEmptyBlock
import lang.temper.value.isPanicCall
import lang.temper.value.jumpKind
import lang.temper.value.ssaSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.varSymbol

internal fun translateFlow(
    tree: BlockTree,
    goalTranslator: GoalTranslator,
    nameMaker: NameMaker,
    options: CfOptions,
    outputName: TemperName?,
    /**
     * If null, do not do a state machine conversion.
     * If non-null, the type of the generator produced
     */
    stateMachineConversionType: Type2? = null,
): PreTranslated {
    fun translateBlockChild(t: Tree): PreTranslated {
        val altFn = (t as? CallTree)?.childOrNull(0)?.functionContained as? DocGenAltFn
        return if (altFn != null) {
            translateAltDocGenFn(altFn, t, goalTranslator, nameMaker, options, outputName = outputName)
        } else {
            PreTranslated.TreeWrapper(t)
        }
    }

    // First we recover structure.  This allows us to have all the adjacent "statements" in blocks
    // so that we can merge adjacent declarations and initializers.
    // Once all our ducks are in a row, we produce something that the translator can interpret in
    // an expression/statement/top-level context as needed.
    val flow = tree.flow
    val flowTranslator = ControlFlowTranslator(goalTranslator, nameMaker, options)
    var preTranslated = when {
        stateMachineConversionType != null -> convertCoroutineToControlFlow(
            tree,
            nameMaker,
            outputName,
            goalTranslator.supportNetwork,
            goalTranslator.translator.typeContext2,
            generatorType = stateMachineConversionType,
            ::translateBlockChild,
        )
        flow is StructuredFlow -> {
            flowTranslator.translate(flow.controlFlow, tree)
        }
        else -> {
            check(flow is LinearFlow)
            PreTranslated.Block(
                tree.pos,
                tree.children.map { translateBlockChild(it) },
            )
        }
    }

    if (options.representationOfVoid == RepresentationOfVoid.DoNotReifyVoid) {
        preTranslated = removeReferencesAndAssignmentsToVoid(preTranslated)
    }

    preTranslated = migrateDeclarationsOutOfSyntheticBlocks(preTranslated, outputName = outputName)

    check(preTranslated is PreTranslated.ConvertedCoroutine == (stateMachineConversionType != null))

    return preTranslated
}

private fun translateAltDocGenFn(
    f: DocGenAltFn,
    t: CallTree,
    goalTranslator: GoalTranslator,
    nameMaker: NameMaker,
    options: CfOptions,
    outputName: TemperName?,
): PreTranslated = when (f) {
    is DocGenAltIfFn -> {
        val lastIndex = t.size - 1

        // We walk backwards over the condition, block pairs building up a thunked translation.
        // We use a thunk so that translation happens in-order.

        // If there's not an `else`, then we need to fill in PreTranslated.If.alternate with
        // an empty block.  That's the case when the last index is odd as seen below.

        // if (b) { x } else if (c) { y } else { z }   Conditions 1 3   Bodies 2 4 5  Last 5
        // if (b) { x } else if (c) { y }              Conditions 1 3   Bodies 2 4    Last 4
        // if (b) { x } else { y }                     Conditions 1     Bodies 2 3    Last 3
        // if (b) { x }                                Conditions 1     Bodies 2      Last 2
        val hasElse = (lastIndex and 1) != 0

        var (index, translation) = if (hasElse) {
            lastIndex - 1 to {
                translateFlow(t.child(lastIndex) as BlockTree, goalTranslator, nameMaker, options, outputName)
            }
        } else {
            lastIndex to { PreTranslated.Block(t.pos.rightEdge, emptyList()) }
        }
        while (index > 1) {
            val testTree = t.child(index - 1)
            val bodyTree = t.child(index) as BlockTree
            index -= 2

            val priorTranslation = translation
            translation = {
                val test = PreTranslated.TreeWrapper(testTree)
                val consequent = translateFlow(bodyTree, goalTranslator, nameMaker, options, outputName)
                val alternate = priorTranslation()
                PreTranslated.If(
                    pos = if (index == 1) {
                        t.pos
                    } else {
                        listOf(test, consequent, alternate).spanningPosition(t.pos)
                    },
                    test = test,
                    consequent = consequent,
                    alternate = alternate,
                )
            }
        }
        // Translations are thunked so they happen in order.
        translation()
    }
    is DocGenAltReturnFn -> PreTranslated.Return(t.pos, PreTranslated.TreeWrapper(t.child(1)))
    is DocGenAltWhileFn -> PreTranslated.WhileLoop(
        t.pos,
        test = PreTranslated.TreeWrapper(t.child(1)),
        body = translateFlow(t.child(2) as BlockTree, goalTranslator, nameMaker, options, outputName),
    )
    is DocGenAltImpliedResultFn -> PreTranslated.Block(
        t.pos,
        listOf(
            PreTranslated.DocFoldBoundary(TmpL.BoilerplateCodeFoldStart(t.pos.leftEdge)),
            PreTranslated.TreeWrapper(t.child(1)),
            PreTranslated.DocFoldBoundary(TmpL.BoilerplateCodeFoldEnd(t.pos.rightEdge)),
        ),
    )
}

/**
 * We need to delayed translation of subtrees until after we've grouped elements of a block
 * together so that we can group declarations and their initializers.  If we don't suspend them,
 * then we need to treat declarations of functions as assignments of a function value to a local
 * variable instead of as a local function declaration for some period of time.
 */
internal sealed class PreTranslated : Positioned {
    /**
     * Root trees used to specify content in the statement and expression forms.
     */
    open val roots: Sequence<Tree> get() = children.flatMap { it.roots }
    abstract val children: Sequence<PreTranslated>

    open fun toStatement(translator: TmpLTranslator, parent: PreTranslated? = null): Stmt =
        OneStmt(TmpL.ExpressionStatement(toExpression(translator)))

    open fun toExpression(translator: TmpLTranslator): TmpL.Expression =
        translator.untranslatableExpr(pos, "${this::class.simpleName} cannot convert to expression")

    /**
     * Dump tokens that describe this structure.  Used by test harness to match derived structures
     * against expected structures expressed in a string.
     */
    abstract fun diagnosticToTokenSink(tokenSink: TokenSink)

    data class Block(
        override val pos: Position,
        private val elements: List<PreTranslated>,
    ) : PreTranslated() {
        val fixedElements get() = fixupBlockContent(elements)
        val unfixedElements get() = elements

        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt = OneStmt(
            TmpL.BlockStatement(
                pos,
                fixedElements.flatMap { it.toStatement(translator).stmtList },
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.emit(OutToks.leftCurly)
            fixedElements.forEach {
                it.diagnosticToTokenSink(tokenSink)
                tokenSink.emit(OutToks.semi)
            }
            tokenSink.emit(OutToks.rightCurly)
        }

        override val children: Sequence<PreTranslated> get() = elements.asSequence()
    }

    data class CombinedDeclaration(
        override val pos: Position,
        val declaration: DeclTree,
        /** An assignment expression that assigns to [declaration]'s name. */
        val initializer: Tree,
        /** The right-hand operand from [initializer] used to initialize [declaration]'s name. */
        val initial: Tree,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) =
            translator.translateDeclarationToStmt(
                pos,
                declaration,
                allowFunctionDecl = true,
                initial = initial,
            )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(tokenSink, "CombinedDeclaration") {
                declaration.toPseudoCode(tokenSink)
                tokenSink.emit(OutToks.semi)
                tokenSink.emit(OutToks.eq)
                initial.toPseudoCode(tokenSink)
            }
        }

        override val roots: Sequence<Tree> get() = sequenceOf(declaration, initial)

        override val children: Sequence<PreTranslated> get() = sequenceOf()

        override fun toString() =
            "CombinedDeclaration(${declaration.toLispy()}, ${initial.toLispy()})"
    }

    data class CombinedTypeDefinition(
        override val pos: Position,
        val typeShape: TypeShape,
        val typeIdDecl: CombinedDeclaration?,
        val members: List<PreTranslated>,
    ) : PreTranslated() {
        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(
                tokenSink,
                "CombinedTypeDefinition",
                inParens = { tokenSink.emit(typeShape.name.toToken(inOperatorPosition = false)) },
            ) {
                typeIdDecl?.diagnosticToTokenSink(tokenSink)
                members.forEach {
                    tokenSink.emit(OutToks.semi)
                    it.diagnosticToTokenSink(tokenSink)
                }
            }
        }

        override val children: Sequence<PreTranslated>
            get() = if (typeIdDecl != null) {
                sequenceOf(
                    sequenceOf(typeIdDecl),
                    members.asSequence(),
                ).flatten()
            } else {
                members.asSequence()
            }
    }

    /**
     * A coroutine that has been converted to a regular function.
     * See [CoroutineStrategy.TranslateToRegularFunction]
     *
     * This is its own node type because some parts of the translation
     * need to be recombined into a larger translation, like
     * declarations that are initialized before a yield but also
     * need to be available after a resume.
     */
    data class ConvertedCoroutine(
        override val pos: Position,
        val persistentDeclarations: List<PreTranslated>,
        val generatorName: ResolvedName,
        val body: PreTranslated,
        /** Maps names that need to adjust from a possibly null state to the value, the non-null type. */
        val variablesToNullAdjust: Map<TemperName, Type2>,
    ) : PreTranslated() {
        override val children: Sequence<PreTranslated>
            get() = run {
                var i = 0
                generateSequence {
                    when (val k = i++) {
                        in persistentDeclarations.indices ->
                            persistentDeclarations[k]
                        persistentDeclarations.size -> body
                        else -> null
                    }
                }
            }

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.word("wasCoroutine")
            tokenSink.emit(OutToks.leftCurly)
            persistentDeclarations.forEach {
                tokenSink.word("@extracted")
                it.diagnosticToTokenSink(tokenSink)
            }
            tokenSink.emit(OutToks.semiSemi)
            body.diagnosticToTokenSink(tokenSink)
            if (variablesToNullAdjust.isNotEmpty()) {
                tokenSink.emit(
                    OutputToken.makeSlashStarComment("Need to adjust: $variablesToNullAdjust"),
                )
            }
            tokenSink.emit(OutToks.rightCurly)
        }
    }

    data class TreeWrapper(
        val tree: Tree,
    ) : PreTranslated() {
        override val pos: Position get() = tree.pos

        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt =
            translator.translateStatement(tree)

        override fun toExpression(translator: TmpLTranslator): TmpL.Expression =
            translator.translateExpression(tree)

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tree.toPseudoCode(tokenSink)
        }

        override val roots: Sequence<Tree> get() = sequenceOf(tree)
        override val children: Sequence<PreTranslated> get() = sequenceOf()

        override fun toString() = "TreeWrapper(${tree.toLispy()})"
    }

    data class DocFoldBoundary(
        val tmpL: TmpL.BoilerplateCodeFoldBoundary,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt = OneStmt(tmpL.deepCopy())

        override val children: Sequence<PreTranslated> get() = emptySequence()

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            (tmpL as BaseOutTree<*>).formatTo(tokenSink)
        }

        override val pos: Position get() = tmpL.pos
    }

    data class If(
        override val pos: Position,
        val test: PreTranslated,
        val consequent: PreTranslated,
        val alternate: PreTranslated,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): OneStmt {
            val testExpr = test.toExpression(translator)
            val consequentStmt = consequent.toStatement(translator).asStmt()
            var alternateStmt: TmpL.Statement? = alternate.toStatement(translator).asStmt()
            if (alternateStmt is TmpL.BlockStatement) {
                if (alternateStmt.statements.isEmpty()) {
                    alternateStmt = null
                } else if (
                    alternateStmt.statements.size == 1 &&
                    alternateStmt.statements[0] is TmpL.IfStatement
                ) {
                    // Unwrapping length-1 blocks, makes it easier for backends
                    // to build their version of `else if`/`elsif`/`elif`
                    // instead of `if (...) {...} else { if (...) {...} }`.
                    val body = alternateStmt.takeBody()
                    alternateStmt = body.first()
                }
            }

            return OneStmt(
                TmpL.IfStatement(
                    pos,
                    test = testExpr,
                    consequent = consequentStmt,
                    alternate = alternateStmt,
                ),
            )
        }

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(
                tokenSink,
                "if",
                inParens = { test.diagnosticToTokenSink(tokenSink) },
            ) {
                consequent.diagnosticToTokenSink(tokenSink)
            }
            inDiagnosticBlock(tokenSink, "else") {
                alternate.diagnosticToTokenSink(tokenSink)
            }
        }

        override val children: Sequence<PreTranslated>
            get() = sequenceOf(test, consequent, alternate)
    }

    data class Try(
        override val pos: Position,
        val tried: PreTranslated,
        val recover: PreTranslated,
    ) : PreTranslated() {
        val justRethrows: Boolean
            get() = recover is Goal && recover.goal is FreeFailure

        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt {
            val triedStmt = tried.toStatement(translator).asStmt()
            val recoverStmt = recover.toStatement(translator).asStmt()
            return if (recoverStmt is TmpL.ThrowStatement) {
                OneStmt(triedStmt)
            } else {
                val migratedTry = migrateConstAssignmentsOutOfTryCatch(
                    pos,
                    tried = triedStmt,
                    recover = recoverStmt,
                    translator = translator,
                    parent = parent,
                )
                val coreTry = TmpL.TryStatement(
                    pos = pos,
                    tried = migratedTry.tried,
                    recover = migratedTry.recover,
                )

                Stmts(
                    pos,
                    buildList {
                        addAll(migratedTry.before)
                        add(migratedTry.escapeHatch?.apply(coreTry) ?: coreTry)
                        addAll(migratedTry.after)
                    },
                )
            }
        }

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(tokenSink, "try") {
                tried.diagnosticToTokenSink(tokenSink)
            }
            inDiagnosticBlock(tokenSink, "catch") {
                recover.diagnosticToTokenSink(tokenSink)
            }
        }

        override val children: Sequence<PreTranslated>
            get() = sequenceOf(tried, recover)
    }

    data class WhileLoop(
        override val pos: Position,
        val test: PreTranslated,
        val body: PreTranslated,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) = OneStmt(
            TmpL.WhileStatement(
                pos,
                test = test.toExpression(translator),
                body = body.toStatement(translator, parent = this).asStmt(),
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(
                tokenSink,
                "while",
                inParens = { test.diagnosticToTokenSink(tokenSink) },
            ) {
                body.diagnosticToTokenSink(tokenSink)
            }
        }

        override val children: Sequence<PreTranslated>
            get() = sequenceOf(test, body)
    }

    data class WhenInt(
        override val pos: Position,
        val caseExpr: PreTranslated,
        val cases: List<Pair<Set<Int>, PreTranslated>>,
        val elseCase: PreTranslated,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt {
            return OneStmt(
                TmpL.ComputedJumpStatement(
                    pos = pos,
                    caseExpr = caseExpr.toExpression(translator),
                    cases = cases.map { (ints, body) ->
                        val leftPos = body.pos.leftEdge
                        TmpL.ComputedJumpCase(
                            body.pos,
                            values = ints.map { TmpL.ConstIndex(leftPos, it) },
                            body = body.toStatement(translator).asBlock(),
                        )
                    },
                    elseCase = elseCase.toStatement(translator).asBlock().let {
                        TmpL.ComputedJumpElse(it.pos, it)
                    },
                ),
            )
        }

        @Suppress("SpreadOperator") // Avoid Kotlin suspend for Graal interop
        override val children: Sequence<PreTranslated>
            get() = sequenceOf(
                *buildList {
                    add(caseExpr)
                    cases.forEach { add(it.second) }
                    add(elseCase)
                }.toTypedArray(),
            )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.emit(OutToks.whenWord)
            tokenSink.emit(OutToks.leftParen)
            caseExpr.diagnosticToTokenSink(tokenSink)
            tokenSink.emit(OutToks.rightParen)

            tokenSink.emit(OutToks.leftCurly)

            cases.forEach { (nums, body) ->
                nums.forEachIndexed { index, i ->
                    if (index != 0) { tokenSink.emit(OutToks.comma) }
                    tokenSink.number("$i")
                }
                tokenSink.emit(OutToks.rArrow)
                tokenSink.emit(OutToks.doWord)
                body.diagnosticToTokenSink(tokenSink)
            }

            tokenSink.emit(OutToks.elseWord)
            tokenSink.emit(OutToks.rArrow)
            tokenSink.emit(OutToks.doWord)
            elseCase.diagnosticToTokenSink(tokenSink)

            tokenSink.emit(OutToks.rightCurly)
        }
    }

    data class Break(
        override val pos: Position,
        val label: PreTranslatedLabel,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) = OneStmt(
            TmpL.BreakStatement(
                pos,
                if (label.isDefault) {
                    null
                } else {
                    TmpL.JumpLabel(TmpL.Id(pos, label.jumpLabel!!))
                },
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.emit(OutToks.breakWord)
            label.diagnosticToTokenSink(tokenSink)
        }

        override val children: Sequence<PreTranslated> get() = emptySequence()
    }

    data class Continue(
        override val pos: Position,
        val label: PreTranslatedLabel,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) = OneStmt(
            TmpL.ContinueStatement(
                pos,
                if (label.isDefault) {
                    null
                } else {
                    TmpL.JumpLabel(TmpL.Id(pos, label.jumpLabel!!))
                },
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.emit(OutToks.continueWord)
            label.diagnosticToTokenSink(tokenSink)
        }

        override val children: Sequence<PreTranslated> get() = emptySequence()
    }

    data class Return(
        override val pos: Position,
        val expr: PreTranslated?,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) = OneStmt(
            TmpL.ReturnStatement(
                pos,
                expr?.toExpression(translator),
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.emit(OutToks.returnWord)
            expr?.diagnosticToTokenSink(tokenSink)
        }

        override val children: Sequence<PreTranslated> get() = if (expr != null) {
            sequenceOf(expr)
        } else {
            emptySequence()
        }
    }

    data class Goal(
        override val pos: Position,
        val goal: GoalSpecifier,
        val goalTranslator: GoalTranslator,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) =
            goalTranslator.translateGoal(pos, goal)

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(tokenSink, "Goal") {
                when (goal) {
                    ExitGoalSpecifier -> tokenSink.word("EXIT")
                    FreeFailure -> tokenSink.word("FAIL")
                    is JumpGoalSpecifier -> {
                        tokenSink.word(goal.kind.name)
                        when (val target = goal.target) {
                            DefaultJumpSpecifier -> {}
                            is NamedJumpSpecifier ->
                                tokenSink.name(target.label, inOperatorPosition = false)
                            is UnresolvedJumpSpecifier ->
                                tokenSink.emit(OutputToken("${target.symbol}", OutputTokenType.OtherValue))
                        }
                    }
                }
            }
        }

        override val children: Sequence<PreTranslated> get() = emptySequence()

        override fun toString() = "Goal($goal)"
    }

    data class LabeledStmt(
        override val pos: Position,
        val label: PreTranslatedLabel,
        val stmt: PreTranslated,
    ) : PreTranslated() {
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?) = OneStmt(
            TmpL.LabeledStatement(
                pos,
                TmpL.JumpLabel(TmpL.Id(pos, label.jumpLabel!!)),
                stmt.toStatement(translator, parent = this).asStmt(),
            ),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            inDiagnosticBlock(
                tokenSink,
                "LabeledStmt",
                inParens = { label.diagnosticToTokenSink(tokenSink) },
            ) {
                stmt.diagnosticToTokenSink(tokenSink)
            }
        }

        override val children: Sequence<PreTranslated> get() = sequenceOf(stmt)
    }

    data class Garbage(
        override val pos: Position,
        val diagnosticString: String,
    ) : PreTranslated() {
        override fun toExpression(translator: TmpLTranslator) =
            translator.untranslatableExpr(pos, diagnosticString)
        override fun toStatement(translator: TmpLTranslator, parent: PreTranslated?): Stmt = OneStmt(
            translator.untranslatableStmt(pos, diagnosticString),
        )

        override fun diagnosticToTokenSink(tokenSink: TokenSink) {
            tokenSink.word("Garbage")
            tokenSink.emit(OutToks.leftParen)
            tokenSink.emit(
                OutputToken(temperEscaper.escape(diagnosticString), OutputTokenType.QuotedValue),
            )
            tokenSink.emit(OutToks.rightParen)
        }

        override val children: Sequence<PreTranslated> get() = emptySequence()
    }

    companion object {
        fun inDiagnosticBlock(
            tokenSink: TokenSink,
            label: String,
            inParens: (() -> Unit)? = null,
            inBlock: () -> Unit,
        ) {
            tokenSink.word(label)
            if (inParens != null) {
                tokenSink.emit(OutToks.leftParen)
                inParens()
                tokenSink.emit(OutToks.rightParen)
            }
            tokenSink.emit(OutToks.leftCurly)
            inBlock()
            tokenSink.emit(OutToks.rightCurly)
        }
    }
}

internal class PreTranslatedLabel(
    val pos: Position?,
    val jumpLabel: JumpLabel?,
) {
    val isDefault get() = jumpLabel == null

    internal fun diagnosticToTokenSink(tokenSink: TokenSink) {
        if (jumpLabel == null) {
            tokenSink.word("default")
        } else {
            tokenSink.name(jumpLabel, inOperatorPosition = false)
        }
    }

    override fun toString(): String = "(PreTranslatedLabel $jumpLabel)"
}

private class ControlFlowTranslator(
    val goalTranslator: GoalTranslator,
    val nameMaker: NameMaker,
    val options: CfOptions,
) {
    /**
     * Variables that have [failSymbol] metadata so which track whether a
     * [handler scope][isHandlerScopeCall] call succeeded.
     */
    val failVars = mutableSetOf<ResolvedName>()

    /**
     * Labels defined on [ControlFlow.OrElse] that are currently in the scope of the
     * or-clause being processed.
     * Breaking to these simulates a local transfer to bubble-handling code
     */
    val orElseJumpLabels = mutableSetOf<ResolvedName>()

    private fun untranslatable(p: Position): PreTranslated = PreTranslated.TreeWrapper(
        goalTranslator.translator.document.treeFarm.grow {
            Call(p, ErrorFn) {}
        },
    )

    fun translateRef(ref: BlockChildReference, root: BlockTree): PreTranslated {
        val tree = root.dereference(ref)?.target
            ?: return untranslatable(ref.pos)
        // Anticipate removing void/never assignments for failure purposes.
        val effectiveTree = when {
            options.representationOfVoid == RepresentationOfVoid.DoNotReifyVoid && isVoidLikeAssignment(tree) ->
                tree.child(2)
            else -> tree
        }
        if (isBubbleCall(effectiveTree) || isPanicCall(effectiveTree)) {
            return PreTranslated.Goal(ref.pos, FreeFailure, goalTranslator)
        }
        // But go back to original if not, in case someone has a more elaborate plan later.
        return PreTranslated.TreeWrapper(tree)
    }

    fun translateCondition(ref: BlockChildReference, root: BlockTree): PreTranslated =
        translateRef(ref, root)

    fun translate(cf: ControlFlow, root: BlockTree): PreTranslated = when (cf) {
        is ControlFlow.If -> PreTranslated.If(
            cf.pos,
            translateCondition(cf.condition, root),
            translate(cf.thenClause, root),
            translate(cf.elseClause, root),
        )

        is ControlFlow.Loop -> {
            check(cf.checkPosition == LeftOrRight.Left)
            check(cf.increment.isEmptyBlock())
            var pt: PreTranslated = PreTranslated.WhileLoop(
                cf.pos,
                translateCondition(cf.condition, root),
                translate(cf.body, root),
            )
            val label = cf.label
            if (label != null) {
                pt = PreTranslated.LabeledStmt(
                    cf.pos,
                    PreTranslatedLabel(cf.pos.leftEdge, label),
                    pt,
                )
            }
            pt
        }

        is ControlFlow.Jump -> {
            val label = when (val target = cf.target) {
                DefaultJumpSpecifier -> PreTranslatedLabel(cf.pos, null)
                is NamedJumpSpecifier -> PreTranslatedLabel(cf.pos, target.label)
                is UnresolvedJumpSpecifier -> null
            }
            if (label != null) {
                when (cf.jumpKind) {
                    BreakOrContinue.Break -> {
                        var isEffectivelyThrow = false
                        val jumpLabel = label.jumpLabel
                        if (
                            options.nrbStrategy == BubbleBranchStrategy.CatchBubble &&
                            jumpLabel != null && jumpLabel in orElseJumpLabels
                        ) {
                            val parent = cf.parent as ControlFlow.StmtBlock?
                            // Look for if (fail#123) { break orelse#234 }
                            val grandParent = parent?.parent
                            isEffectivelyThrow = !checksFailVarAndBubbles(grandParent, root)
                        }
                        if (isEffectivelyThrow) {
                            PreTranslated.TreeWrapper(
                                root.document.treeFarm.grow(cf.pos) {
                                    Call(BubbleFn) {}
                                },
                            )
                        } else {
                            PreTranslated.Break(cf.pos, label)
                        }
                    }
                    BreakOrContinue.Continue -> PreTranslated.Continue(cf.pos, label)
                }
            } else {
                untranslatable(cf.pos)
            }
        }
        is ControlFlow.Labeled -> {
            check(cf.continueLabel == null) // erased by simplifyControlFlow
            PreTranslated.LabeledStmt(
                cf.pos,
                PreTranslatedLabel(cf.pos.leftEdge, cf.breakLabel),
                translate(cf.stmts, root),
            )
        }
        is ControlFlow.OrElse -> {
            val orElseJumpLabel = cf.orClause.breakLabel

            when (options.nrbStrategy) {
                BubbleBranchStrategy.IfHandlerScopeVar -> {
                    // O orelse E
                    //
                    // ->
                    //
                    // ok_label: do {
                    //   orelse_label: do {
                    //     O
                    //     break ok_label;
                    //   }
                    //   // `break orelse_label` jumps here
                    //   E
                    // }
                    // // `break ok_label` jumps here
                    //
                    // If O exits normally, control flows to `break ok_label`
                    // which jumps to the end of the outermost block above.
                    // Any `break orelse_label` in O jumps over `break ok_label` to E.
                    val okLabel = nameMaker.unusedTemporaryName("ok")
                    PreTranslated.LabeledStmt(
                        cf.pos,
                        PreTranslatedLabel(cf.pos.leftEdge, okLabel),
                        buildPTBlock(cf.pos) {
                            add(
                                PreTranslated.LabeledStmt(
                                    cf.orClause.pos,
                                    PreTranslatedLabel(
                                        cf.orClause.pos.leftEdge,
                                        orElseJumpLabel,
                                    ),
                                    buildPTBlock(cf.orClause.pos) {
                                        add(translate(cf.orClause.stmts, root))
                                        add(
                                            PreTranslated.Break(
                                                cf.orClause.pos.rightEdge,
                                                PreTranslatedLabel(
                                                    cf.orClause.pos.rightEdge,
                                                    okLabel,
                                                ),
                                            ),
                                        )
                                    },
                                ),
                            )
                            add(translate(cf.elseClause, root))
                        },
                    )
                }

                BubbleBranchStrategy.CatchBubble -> {
                    orElseJumpLabels.add(orElseJumpLabel)
                    val tried = translate(cf.orClause.stmts, root)
                    orElseJumpLabels.remove(orElseJumpLabel)
                    val recover = translate(cf.elseClause, root)
                    PreTranslated.Try(cf.pos, tried, recover)
                }
            }
        }

        is ControlFlow.Stmt -> translateRef(cf.ref, root)
        is ControlFlow.StmtBlock -> buildPTBlock(cf.pos) {
            for (s in cf.stmts) {
                add(translate(s, root))
            }
        }
    }

    private fun buildPTBlock(p: Position, buildIt: PTBlockMaker.() -> Unit): PreTranslated {
        val maker = PTBlockMaker()
        maker.buildIt()
        return PreTranslated.Block(p, maker.toList())
    }

    private inner class PTBlockMaker {
        private val elements = mutableListOf<PreTranslated>()

        fun add(pt: PreTranslated) {
            if (pt is PreTranslated.Block) {
                pt.unfixedElements.forEach { add(it) }
                return
            }
            var toAdd: PreTranslated? = pt
            if (options.nrbStrategy == BubbleBranchStrategy.CatchBubble) {
                // hs(..., expr) -> expr
                toAdd = toAdd?.let { unpackHandlerScopeCall(it) } ?: toAdd
                if (toAdd is PreTranslated.TreeWrapper) {
                    val t = toAdd.tree
                    val declParts = (t as? DeclTree)?.parts
                    if (declParts != null) {
                        if (failSymbol in declParts.metadataSymbolMultimap) {
                            // var fail#123 -> nothing
                            failVars.add(declParts.name.content as ResolvedName)
                            toAdd = null
                        }
                    }
                }
                if (toAdd is PreTranslated.If) {
                    // if (fail#123) { bubble() } -> nothing
                    if (checksFailVarAndBubbles(toAdd)) {
                        toAdd = null
                    }
                }
            }

            if (toAdd != null) {
                elements.add(toAdd)
            }
        }

        fun toList() = elements.toList()
    }

    fun unpackHandlerScopeCall(expr: PreTranslated): PreTranslated? {
        val t = (expr as? PreTranslated.TreeWrapper)?.tree
        if (t is CallTree) {
            if (isHandlerScopeCall(t)) {
                val failVar = (t.child(1) as? LeftNameLeaf)?.content
                if (failVar is ResolvedName) {
                    failVars.add(failVar)
                    return PreTranslated.TreeWrapper(t.child(2))
                }
            } else if (isAssignmentCall(t)) {
                val left = t.child(1)
                val right = t.child(2)
                if (left is LeftNameLeaf && right is CallTree && isHandlerScopeCall(right)) {
                    val failVar = (right.child(1) as? LeftNameLeaf)?.content
                    if (failVar is ResolvedName) {
                        failVars.add(failVar)
                    }
                    // We can't split it down further without mutating the tree which
                    // backends are not allowed to do, so TmpLTranslator specially handles
                    // assignments of `hs` calls.
                    return expr
                }
            }
        }
        return null
    }

    // Like `if (fail#123) { bubble() }`
    fun checksFailVarAndBubbles(cf: ControlFlow?, blockTree: BlockTree): Boolean {
        if (cf is ControlFlow.If && cf.elseClause.isEmptyBlock()) {
            val test = blockTree.dereference(cf.condition)?.target
            if (test is RightNameLeaf && test.content in failVars) {
                val body = cf.thenClause
                if (body.stmts.size == 1) {
                    val stmt = body.stmts[0]
                    if (
                        stmt is ControlFlow.Break &&
                        (stmt.target as? NamedJumpSpecifier)?.label?.let {
                            it in orElseJumpLabels
                        } == true
                    ) {
                        return true
                    }
                    if (stmt is ControlFlow.Stmt) {
                        val tree = blockTree.dereference(stmt.ref)?.target
                        if (tree != null && isBubbleCall(tree)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
    fun checksFailVarAndBubbles(pt: PreTranslated): Boolean {
        if (pt is PreTranslated.If && pt.alternate.isEmptyBlock) {
            val test = (pt.test as? PreTranslated.TreeWrapper)?.tree
            if (test is RightNameLeaf && test.content in failVars) {
                var body = pt.consequent
                if (body is PreTranslated.Block && body.unfixedElements.size == 1) {
                    body = body.unfixedElements[0]
                }
                when (val goal = (body as? PreTranslated.Goal)?.goal) {
                    is FreeFailure -> {
                        // A transfer out of the current function
                        return true
                    }
                    is JumpGoalSpecifier -> {
                        if (goal.kind == BreakOrContinue.Break) {
                            val label = (goal.target as? NamedJumpSpecifier)?.label
                            if (label != null && label in orElseJumpLabels) {
                                // A local failure transfer
                                return true
                            }
                        }
                    }
                    null, ExitGoalSpecifier -> {}
                }
                if (body is PreTranslated.Break) {
                    val jumpLabel = body.label.jumpLabel
                    if (jumpLabel != null && jumpLabel in orElseJumpLabels) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

private fun moveDeclarationsForwardToMeetInitializers(
    stmts: List<PreTranslated>,
): List<PreTranslated> {
    val declarationToPosition = mutableMapOf<TemperName, Pair<Int, DeclParts>>()
    val assignmentToPosition = mutableMapOf<TemperName, Int>()
    for ((index, stmt) in stmts.withIndex()) {
        if (stmt is PreTranslated.TreeWrapper) {
            val tree = stmt.tree
            if (tree is DeclTree) {
                val parts = tree.parts
                val name = parts?.name?.content ?: continue
                declarationToPosition[name] = index to parts
            } else if (isAssignmentCall(tree)) {
                val assigned = (tree.child(1) as? LeftNameLeaf)?.content ?: continue
                if (assigned !in assignmentToPosition) {
                    assignmentToPosition[assigned] = index
                }
            }
        }
    }

    val mentionsCache = MentionsCache()

    val toMoveToFrom = mutableMapOf<Int, Int>()
    for ((declaredName, indexAndParts) in declarationToPosition) {
        val (declarationIndex, parts) = indexAndParts
        val initializerIndex = assignmentToPosition[declaredName] ?: continue
        if (
            // Can we move it forward?
            initializerIndex > declarationIndex &&
            // Safety check that it doesn't have an initializer already
            initSymbol !in parts.metadataSymbolMap &&
            // We can't move the declaration forward over a use of its name.
            ((declarationIndex + 1) until initializerIndex).none { interveningIndex ->
                declaredName in mentionsCache.reads(stmts[interveningIndex])
            }
        ) {
            toMoveToFrom[initializerIndex] = declarationIndex
        }
    }

    if (toMoveToFrom.isEmpty()) {
        return stmts
    }

    val moving = toMoveToFrom.values.toSet()

    val reordered = mutableListOf<PreTranslated>()
    for (i in stmts.indices) {
        val before = toMoveToFrom[i]
        if (before != null) {
            reordered.add(stmts[before])
        }
        if (i !in moving) {
            reordered.add(stmts[i])
        }
    }
    return reordered.toList()
}

/** Allows efficiently looking up names mentioned in a [PreTranslated] subtree. */
internal class MentionsCache {
    internal data class Mentions(
        val declared: Set<TemperName>,
        val leftNames: Set<TemperName>,
        val rightNames: Set<TemperName>,
    )

    private val cache = mutableMapOf<PreTranslated, Mentions>()

    /**
     * An expression or statement *mentions* a name when that name is used in it.
     * The "mentions" of an expression or statement are the names used therein.
     *
     * @return the set of contents of [name leaves][lang.temper.value.NameLeaf] that appear under
     * the input's [tree roots][PreTranslated.roots].
     */
    fun mentions(preTranslated: PreTranslated) = cache.getOrPut(preTranslated) {
        val declared = mutableSetOf<TemperName>()
        val lefts = mutableSetOf<TemperName>()
        val rights = mutableSetOf<TemperName>()
        preTranslated.roots.forEach { root ->
            TreeVisit.startingAt(root)
                .forEachContinuing {
                    if (it is NameLeaf) {
                        val s = when (it) {
                            is LeftNameLeaf -> {
                                val parent = it.incoming?.source
                                if (parent is DeclTree && parent.parts?.name == it) {
                                    declared
                                } else {
                                    lefts
                                }
                            }
                            is RightNameLeaf -> rights
                        }
                        s.add(it.content)
                    }
                }
                .visitPreOrder()
        }
        Mentions(
            declared = declared.toSet(),
            leftNames = lefts.toSet(),
            rightNames = rights.toSet(),
        )
    }

    fun reads(preTranslated: PreTranslated) = mentions(preTranslated).rightNames
}

/**
 * Group declarations and assignments to the declared together into a declaration with an
 * initializer: [PreTranslated.CombinedDeclaration].
 */
internal fun combineDeclarationsOnto(stmts: List<PreTranslated>, out: MutableList<PreTranslated>) {
    val movedDeclarations = moveDeclarationsForwardToMeetInitializers(stmts)
    for (stmt in movedDeclarations) {
        if (
            stmt is PreTranslated.TreeWrapper &&
            isAssignmentCall(stmt.tree) &&
            translatesToExpression(stmt.tree.child(2))
        ) {
            // Combine an assignment to a variable into a declaration of that variable
            //      let x;
            //      x = init;
            // becomes
            //      let x = init;
            val assignedName = stmt.tree.child(1) as LeftNameLeaf
            val target = stmt.tree.lastChild
            var matching: PreTranslated.TreeWrapper? = null
            var matchingIndex: Int = -1
            for (outIndex in out.indices.reversed()) {
                when (val pt = out[outIndex]) {
                    is PreTranslated.TreeWrapper -> {
                        val potentialMatchingTree = pt.tree
                        if (potentialMatchingTree is DeclTree) {
                            val declParts = potentialMatchingTree.parts
                            if (declParts?.name?.content == assignedName.content &&
                                initSymbol !in declParts.metadataSymbolMap
                            ) {
                                matching = pt
                                matchingIndex = outIndex
                                break
                            }
                        }
                    }
                    is PreTranslated.CombinedDeclaration -> {
                        if (pt.declaration.children.first().content == target.content) {
                            // The thing being assigned is used here; it can't move further back
                            break
                        }
                    }
                    else -> {
                        // Any other content might interfere with combining.
                        // TODO Other init expressions could also have side effects. Does this init expression?
                        break
                    }
                }
            }
            if (matching != null) {
                (matching.tree as? DeclTree)?.let {
                    out[matchingIndex] = PreTranslated.CombinedDeclaration(
                        pos = listOf(matching, stmt).spanningPosition(matching.pos),
                        declaration = it,
                        initializer = stmt.tree,
                        initial = stmt.tree.child(2),
                    )
                }
                continue
            }
        }
        out.add(stmt)
    }
    for (i in out.indices.reversed()) {
        val pt = out[i]
        if (pt is PreTranslated.CombinedDeclaration && isCompilerFiction(pt.declaration)) {
            out.removeAt(i)
        }
    }
}

/** Expected to be called in the context of [fixupBlockContent]. */
private fun flattenRethrows(stmts: List<PreTranslated>): List<PreTranslated> {
    return buildList {
        for (stmt in stmts) {
            when (stmt) {
                is PreTranslated.Try -> when {
                    stmt.justRethrows -> when (val tried = stmt.tried) {
                        // Maybe unfixedElements is fine here since we intend to fix shortly?
                        is PreTranslated.Block -> addAll(flattenRethrows(tried.unfixedElements))
                        else -> add(tried)
                    }
                    else -> add(stmt)
                }
                else -> add(stmt)
            }
        }
    }
}

private fun fixupBlockContent(stmts: List<PreTranslated>): List<PreTranslated> {
    val out = mutableListOf<PreTranslated>()
    // Flatten rethrow blocks before combining so that we can combine better.
    val flat = flattenRethrows(stmts)
    combineDeclarationsOnto(flat, out)

    // Group member declarations with
    //     @fromType(MyClass)
    // into coherent type definitions, and, for non-nested types, group them with their definition
    //     @typeDecl(...) let MyClass = ...;
    val typesDeclared = mutableSetOf<TypeShape>()
    for (pt in out) {
        val declaration = when (pt) {
            is PreTranslated.CombinedDeclaration -> pt.declaration
            is PreTranslated.TreeWrapper -> pt.tree as? DeclTree
            else -> null
        } ?: continue
        val parts = declaration.parts ?: continue
        val metadata = parts.metadataSymbolMap
        if (ssaSymbol !in metadata) { continue }
        val typeMetadata = metadata[fromTypeSymbol]
            ?: metadata[typePlaceholderSymbol]
            ?: continue
        val type = typeMetadata.target.staticTypeContained?.let {
            hackMapOldStyleToNew(it)
        }
        if (type is DefinedNonNullType) {
            typesDeclared.add(type.definition)
        }
    }

    for (typeDeclared in typesDeclared) {
        val memberIndices = mutableListOf<Int>()
        var declIndex: Int = -1

        out.indices.forEach { index ->
            val metadata = when (val pt = out[index]) {
                is PreTranslated.CombinedDeclaration ->
                    pt.declaration.parts?.metadataSymbolMap
                is PreTranslated.TreeWrapper ->
                    (pt.tree as? DeclTree)?.parts?.metadataSymbolMap
                else -> null
            }
            if (metadata != null) {
                val fromType = metadata[fromTypeSymbol]
                if (fromType?.target?.typeShapeAtLeafOrNull == typeDeclared) {
                    memberIndices.add(index)
                } else if (
                    declIndex < 0 &&
                    metadata[typeDeclSymbol]?.target?.typeShapeAtLeafOrNull == typeDeclared
                ) {
                    declIndex = index
                } else {
                    val typePlaceholder = metadata[typePlaceholderSymbol]
                    if (typePlaceholder?.target?.typeShapeAtLeafOrNull == typeDeclared) {
                        memberIndices.add(index)
                    }
                }
            }
        }

        val decl = out.getOrNull(declIndex)
        val positions = listOfNotNull(decl?.pos) +
            memberIndices.map { out[it].pos }
        val pos = positions.spanningPosition(positions.first())

        val combinedTypeDefinition = PreTranslated.CombinedTypeDefinition(
            pos,
            typeDeclared,
            decl as? PreTranslated.CombinedDeclaration,
            memberIndices.map { out[it] },
        )
        // Insert type definition after name declaration
        if (declIndex >= 0) {
            out[declIndex] = combinedTypeDefinition
        } else {
            val insertionPoint = memberIndices.first()
            out.add(insertionPoint, combinedTypeDefinition)
            memberIndices.indices.forEach { i ->
                memberIndices[i] += 1
            }
        }
        memberIndices.asReversed().forEach { out.removeAt(it) }
    }
    return out.toList()
}

private open class PreTranslatedRewrite<I, O>(
    private val combine: (List<O>) -> O,
    private val zero: O,
) {
    operator fun invoke(pt: PreTranslated, x: I): Pair<PreTranslated, O> = when (pt) {
        is PreTranslated.Break -> rewriteBreak(pt, x)
        is PreTranslated.CombinedDeclaration -> rewriteCombinedDeclaration(pt, x)
        is PreTranslated.CombinedTypeDefinition -> rewriteCombinedTypeDefinition(pt, x)
        is PreTranslated.ConvertedCoroutine -> rewriteConvertedCoroutine(pt, x)
        is PreTranslated.Continue -> rewriteContinue(pt, x)
        is PreTranslated.Goal -> rewriteGoal(pt, x)
        is PreTranslated.Garbage -> rewriteGarbage(pt, x)
        is PreTranslated.Return -> rewriteReturn(pt, x)
        is PreTranslated.TreeWrapper -> rewriteTreeWrapper(pt, x)
        is PreTranslated.DocFoldBoundary -> rewriteDocFoldBoundary(pt, x)
        is PreTranslated.LabeledStmt -> rewriteLabeledStmt(pt, x)
        is PreTranslated.If -> rewriteIf(pt, x)
        is PreTranslated.Try -> rewriteTry(pt, x)
        is PreTranslated.WhenInt -> rewriteWhenInt(pt, x)
        is PreTranslated.WhileLoop -> rewriteWhileLoop(pt, x)
        is PreTranslated.Block -> rewriteBlock(pt, x)
    }

    protected fun rewriteByParts(
        original: PreTranslated,
        x: I,
        parts: List<PreTranslated>,
        reconstitute: (List<PreTranslated>) -> PreTranslated,
    ): Pair<PreTranslated, O> {
        val rewrittenParts = parts.map { invoke(it, x) }
        val withRewrittenChildren =
            if (parts.allIndexed { i, part -> rewrittenParts[i].first === part }) {
                original
            } else {
                reconstitute(rewrittenParts.map { it.first })
            }
        return withRewrittenChildren to combine(rewrittenParts.map { it.second })
    }

    open fun rewriteBreak(pt: PreTranslated.Break, x: I) = pt to zero
    open fun rewriteCombinedDeclaration(pt: PreTranslated.CombinedDeclaration, x: I) =
        pt to zero
    open fun rewriteCombinedTypeDefinition(pt: PreTranslated.CombinedTypeDefinition, x: I) =
        pt to zero
    open fun rewriteContinue(pt: PreTranslated.Continue, x: I) = pt to zero
    open fun rewriteGarbage(pt: PreTranslated.Garbage, x: I) = pt to zero
    open fun rewriteGoal(pt: PreTranslated.Goal, x: I) = pt to zero
    open fun rewriteReturn(pt: PreTranslated.Return, x: I) = pt to zero
    open fun rewriteTreeWrapper(pt: PreTranslated.TreeWrapper, x: I) = pt to zero
    open fun rewriteDocFoldBoundary(pt: PreTranslated.DocFoldBoundary, x: I) = pt to zero
    open fun rewriteBlock(pt: PreTranslated.Block, x: I) =
        rewriteByParts(pt, x, pt.unfixedElements) { es ->
            pt.copy(elements = es)
        }
    open fun rewriteConvertedCoroutine(pt: PreTranslated.ConvertedCoroutine, x: I) =
        rewriteByParts(pt, x, pt.children.toList()) { parts ->
            pt.copy(
                persistentDeclarations = parts.subList(0, parts.size - 1),
                body = parts.last(),
            )
        }
    open fun rewriteLabeledStmt(pt: PreTranslated.LabeledStmt, x: I) =
        rewriteByParts(pt, x, listOf(pt.stmt)) { (s) ->
            pt.copy(stmt = s)
        }
    open fun rewriteIf(pt: PreTranslated.If, x: I) =
        rewriteByParts(pt, x, listOf(pt.consequent, pt.alternate)) { (c, a) ->
            pt.copy(consequent = c, alternate = a)
        }
    open fun rewriteTry(pt: PreTranslated.Try, x: I) =
        rewriteByParts(pt, x, listOf(pt.tried, pt.recover)) { (t, r) ->
            pt.copy(tried = t, recover = r)
        }
    open fun rewriteWhenInt(pt: PreTranslated.WhenInt, x: I) =
        rewriteByParts(
            pt,
            x,
            buildList {
                pt.cases.forEach { add(it.second) }
                add(pt.elseCase)
            },
        ) { ls ->
            val newCases = pt.cases.mapIndexed { i, (nums) -> nums to ls[i] }
            val newElseCase = ls.last()
            pt.copy(cases = newCases, elseCase = newElseCase)
        }
    open fun rewriteWhileLoop(pt: PreTranslated.WhileLoop, x: I) =
        rewriteByParts(pt, x, listOf(pt.body)) { (b) ->
            pt.copy(body = b)
        }
}

/**
 * If a declaration gets wrapped in a labeled block, but is used later, we need to migrate it so
 * that the uses are in its scope.
 *
 * So the declaration for `x` has been wrapped in the block with the synthesized label, `s_123`.
 *
 *     s_123: {
 *       let x;
 *       ...
 *     }
 *     f(x);     // Use of `x` outside the scope of `let x`.
 *
 * This needs to be changed to
 *
 *     let x;    // Migrated out
 *     s_123: {
 *       ...
 *     }
 *     f(x);
 */
private fun migrateDeclarationsOutOfSyntheticBlocks(
    root: PreTranslated,
    outputName: TemperName?,
): PreTranslated {
    val mentionsCache = MentionsCache()

    class Migrator : PreTranslatedRewrite<(TemperName) -> Boolean, List<PreTranslated.TreeWrapper>>(
        combine = { it.flatten() },
        zero = emptyList(),
    ) {
        override fun rewriteBlock(
            pt: PreTranslated.Block,
            /** True if the given name is used after [pt]. */
            x: (TemperName) -> Boolean,
        ): Pair<PreTranslated, List<PreTranslated.TreeWrapper>> {
            val usedAfter = x
            val children = pt.unfixedElements
            val migratedChildren = mutableListOf<PreTranslated>()
            for (childIndex in children.indices) {
                val child = children[childIndex]
                val (childMigrated, extractedDecls) = invoke(child) usedAfterTest@{ name ->
                    for (followerIndex in (childIndex + 1)..children.lastIndex) {
                        if (name in mentionsCache.reads(children[followerIndex])) {
                            return@usedAfterTest true
                        }
                    }
                    usedAfter(name)
                }
                migratedChildren.addAll(extractedDecls)
                migratedChildren.add(childMigrated)
            }
            val extracted = mutableListOf<PreTranslated.TreeWrapper>()
            run {
                var i = 0
                while (i < migratedChildren.size) {
                    val migratedChild = migratedChildren[i]
                    if (migratedChild is PreTranslated.CombinedDeclaration) {
                        val decl = migratedChild.declaration
                        val name = decl.parts?.name?.content
                        if (name != null && usedAfter(name)) {
                            extracted.add(PreTranslated.TreeWrapper(decl))
                            migratedChildren[i] =
                                PreTranslated.TreeWrapper(migratedChild.initializer)
                        }
                    } else if (migratedChild is PreTranslated.TreeWrapper) {
                        val tree = migratedChild.tree
                        if (tree is DeclTree) {
                            val name = tree.parts?.name?.content
                            if (name != null && usedAfter(name)) {
                                extracted.add(migratedChild)
                                migratedChildren.removeAt(i)
                                i -= 1 // Step back over removed item
                            }
                        }
                    }
                    i += 1
                }
            }

            val migratedBlock = if (
                migratedChildren.size == children.size &&
                migratedChildren.indices.all { i ->
                    migratedChildren[i] === children[i]
                }
            ) {
                pt
            } else {
                pt.copy(elements = migratedChildren.toList())
            }
            return migratedBlock to extracted.toList()
        }
    }

    val (migratedPt, decls) = Migrator()(root) { it == outputName }
    return if (decls.isNotEmpty()) {
        val allParts = if (migratedPt is PreTranslated.Block) {
            decls + migratedPt.unfixedElements
        } else {
            decls + listOf(migratedPt)
        }
        PreTranslated.Block(root.pos, allParts)
    } else {
        migratedPt
    }
}

private fun translatesToExpression(t: Tree) = when (t) {
    is BlockTree -> false
    is CallTree -> when {
        isHandlerScopeCall(t) -> false
        isAssignmentCall(t) -> false
        isSetPropertyCall(t) -> false
        else -> true
    }
    is DeclTree -> false
    is EscTree -> false
    is FunTree -> true
    is LeftNameLeaf -> false
    is RightNameLeaf -> true
    is StayLeaf -> false
    is ValueLeaf -> true
}

private fun removeReferencesAndAssignmentsToVoid(
    root: PreTranslated,
): PreTranslated {
    class DeVoider : PreTranslatedRewrite<Unit, Unit>({}, Unit) {
        override fun rewriteReturn(pt: PreTranslated.Return, x: Unit): Pair<PreTranslated.Return, Unit> {
            // TODO: Do we need to remove the expression if its type is Void-like
            // or can that be left to TmpLTranslator?
            //
            // Maybe
            //    return callToVoidFunction()
            // needs to generate two statements
            //    callToVoidFunction()
            //    return
            return pt to Unit
        }

        override fun rewriteTreeWrapper(pt: PreTranslated.TreeWrapper, x: Unit): Pair<PreTranslated.TreeWrapper, Unit> =
            when (val tree = pt.tree) {
                is DeclTree -> if (hasVoidLikeType(tree.parts?.name)) {
                    makeEmptyValueWrapper(tree)
                } else {
                    pt
                }
                is CallTree -> {
                    if (isVoidLikeAssignment(tree)) {
                        // If it's a void value reference, drop it entirely.
                        // Otherwise, just substitute the right hand side.
                        val right = tree.child(2)
                        val rightWrapper = PreTranslated.TreeWrapper(right)
                        rewriteTreeWrapper(rightWrapper, Unit).first
                    } else {
                        pt
                    }
                }
                is NameLeaf -> {
                    if (hasVoidLikeType(tree)) {
                        makeEmptyValueWrapper(tree)
                    } else {
                        pt
                    }
                }
                is ValueLeaf -> {
                    if (tree.content == TVoid.value) {
                        makeEmptyValueWrapper(tree)
                    } else {
                        pt
                    }
                }
                is BlockTree, is EscTree, is FunTree,
                is StayLeaf,
                -> pt
            } to Unit

        override fun rewriteBlock(pt: PreTranslated.Block, x: Unit): Pair<PreTranslated, Unit> {
            val (rewritten) = super.rewriteBlock(pt, x)
            return if (rewritten is PreTranslated.Block) {
                val parts = rewritten.unfixedElements.filter {
                    !isEmptyValueWrapper(it)
                }
                if (parts.size == 1) {
                    parts[0]
                } else {
                    PreTranslated.Block(rewritten.pos, parts)
                }
            } else {
                rewritten
            } to Unit
        }

        private fun isEmptyValueWrapper(pt: PreTranslated) =
            pt is PreTranslated.TreeWrapper && pt.tree is ValueLeaf &&
                pt.tree.content == emptyValue

        private fun makeEmptyValueWrapper(tree: Tree): PreTranslated.TreeWrapper {
            val valueLeaf = ValueLeaf(tree.document, tree.pos, emptyValue)
            valueLeaf.typeInferences = BasicTypeInferences(
                WellKnownTypes.emptyType,
                emptyList(),
            )
            return PreTranslated.TreeWrapper(valueLeaf)
        }
    }

    return DeVoider()(root, Unit).first
    // Ironic               ↑↑↑↑
}

/**
 * Many languages that use `try`/`catch` make worst-case assumptions about
 * whether local variables are assigned.
 *
 * This Java fails to compile with the errors reported below indicating that it
 * can't assume that the first is assigned when used later, and can't assume
 * that the second is not assigned when the catch block tries to assign a
 * fallback value.
 *
 *     public class TryPathology {
 *       final int field; // `final` means assignable at most once
 *
 *       TryPathology(int n, int d) {
 *         int local;   // locals must be assigned before use
 *         try {
 *             local = 0;
 *             field = n / d;
 *         } catch (ArithmeticException ex) {
 *             field = 0;
 *         }
 *         System.out.println(local);
 *       }
 *     }
 *
 * That fails with compiler errors.
 *
 *     $ javac TryPathology.java
 *     TryPathology.java:10: error: variable field might already have been assigned
 *             field = 0;
 *             ^
 *     TryPathology.java:12: error: variable local might not have been initialized
 *         System.out.println(local);
 *                            ^
 *     2 errors
 *
 * This pass finds names of [TmpL.ModuleOrLocalDeclaration] marked as
 * [assignOnce][TmpL.ModuleOrLocalDeclaration.assignOnce].
 *
 * For each of those names, like *x*, we look for [TmpL.TryStatement] where the
 * name is assigned in both the `try` and `catch` branches.
 *
 *     try {
 *       x = expressionThatMightFail;
 *     } catch {
 *       x = fallback;
 *     }
 *
 * and we turn that into
 *
 *     let x__0: SameTypeAsX; // Not marked assignOnce
 *     try {
 *       x__0 = expressionThatMightFail;
 *     } catch {
 *       x__0 = fallback;
 *     }
 *     x = x__0;
 */
private fun migrateConstAssignmentsOutOfTryCatch(
    pos: Position,
    tried: TmpL.Statement,
    recover: TmpL.Statement,
    translator: TmpLTranslator,
    parent: PreTranslated?,
): MigratedTryCatch {
    if (translator.supportNetwork.mayAssignInBothTryAndRecover) {
        return MigratedTryCatch(tried = tried, recover = recover)
    }

    val sharedNameTables = translator.pool.sharedNameTables

    // Find names in tried that we can extract
    val nonVarAssignedInTried = buildSet {
        forEachLeafStmt(tried) { stmt ->
            if (stmt is TmpL.Assignment) {
                val name = stmt.left.name
                val metadata = sharedNameTables.declarationMetadataForName[name]
                if (metadata != null && varSymbol !in metadata && name in sharedNameTables.typeInferencesForName) {
                    add(name)
                }
            }
        }
    }
    // Relate names that also appear in recover to a temporary name and type
    val nameToExtractToTemporary = buildMap {
        forEachLeafStmt(recover) { stmt ->
            if (stmt is TmpL.Assignment) {
                val name = stmt.left.name
                if (name in nonVarAssignedInTried && name !in this) {
                    this[name] = translator.unusedName(
                        when (name) {
                            is ResolvedParsedName -> name.baseName
                            else -> ParsedName("t")
                        },
                    ) to hackMapOldStyleToNew(sharedNameTables.typeInferencesForName.getValue(name).type)
                }
            }
        }
    }
    if (nameToExtractToTemporary.isEmpty()) {
        return MigratedTryCatch(tried = tried, recover = recover)
    }

    // Declare all the new names.  These are `var` so can be assigned across both branches.
    val leftEdge = tried.pos.leftEdge
    val hatch = EscapeHatch.buildIfNeeded(pos = leftEdge, translator, parent, statements = listOf(tried, recover))
    val declarations = buildList {
        hatch?.declareEscapeValue(pos = leftEdge, statements = this)
        for (entry in nameToExtractToTemporary) {
            val (name, type) = entry.value
            TmpL.LocalDeclaration(
                pos = leftEdge,
                metadata = listOf(),
                name = TmpL.Id(leftEdge, name, null),
                type = translator.translateType(leftEdge, type).aType,
                init = null,
                assignOnce = false,
                descriptor = type,
            ).also { add(it) }
        }
    }

    // After the try/catch we write the new names back to the original names.
    val rightEdge = pos.rightEdge
    val writeBack = buildList {
        // Assign to values.
        for (entry in nameToExtractToTemporary) {
            val originalName = entry.key
            val (name, type) = entry.value
            TmpL.Assignment(
                rightEdge,
                TmpL.Id(rightEdge, originalName),
                TmpL.Reference(
                    rightEdge,
                    type = type,
                    id = TmpL.Id(rightEdge, name),
                ),
                type,
            ).also { add(it) }
        }
        hatch?.addEscapes(pos = rightEdge, statements = this)
    }

    // Replace old names with new in the try and recover clauses
    val rewriter = object : EscapeHatchRewriter(hatch) {
        override fun rewriteId(x: TmpL.Id): TmpL.Id {
            val name = x.name
            val (newName) = nameToExtractToTemporary[name]
                ?: return super.rewriteId(x)
            return TmpL.Id(
                pos = x.pos,
                name = newName,
                outName = null,
            )
        }
    }

    return MigratedTryCatch(
        before = declarations,
        tried = rewriter.rewriteStatement(tried),
        recover = rewriter.rewriteStatement(recover),
        escapeHatch = hatch,
        after = writeBack,
    )
}

private data class MigratedTryCatch(
    val before: List<TmpL.Statement> = emptyList(),
    val tried: TmpL.Statement,
    val recover: TmpL.Statement,
    val escapeHatch: EscapeHatch? = null,
    val after: List<TmpL.Statement> = emptyList(),
)

/**
 * Temper GeneratorFn blocks implicitly return *DoneResult*.
 * This allows them to advertise a return value of *GeneratorResult* and be
 * accurate.
 *
 * discord.com/channels/1108828567439163503/1195475479222816799/1234918469678399612
 * discusses how generator functions are typed in various languages and none of them
 * want a result object as the explicit return value.
 *
 * This function recognizes `return doneResult()` and eliminates it (if in terminal)
 * position, or rewrites it to a simple `return`.
 */
internal fun simplifyGeneratorFnReturns(body: PreTranslated, returnName: ResolvedName): PreTranslated {
    class Rewrite : PreTranslatedRewrite<Unit, Unit>({}, Unit) {
        override fun rewriteBlock(pt: PreTranslated.Block, x: Unit): Pair<PreTranslated, Unit> {
            val (b) = super.rewriteBlock(pt, x)
            check(b is PreTranslated.Block)
            return b.copy(
                elements = b.unfixedElements.filter {
                    if (it is PreTranslated.TreeWrapper) {
                        val tree = it.tree
                        !(
                            // Filter out `return__123 = implicits.doneResult()`
                            isAssignmentCall(tree) &&
                                (tree.child(1) as? LeftNameLeaf)?.content == returnName &&
                                isDoneResultCall(tree.child(2))
                            )
                    } else {
                        true
                    }
                },
            ) to Unit
        }

        private fun isDoneResultCall(expr: Tree): Boolean {
            if (!(expr is CallTree && expr.size == 1)) {
                return false
            }
            var callee = expr.child(0)
            if (
                callee is CallTree &&
                callee.childOrNull(0)?.functionContained == BuiltinFuns.angleFn &&
                callee.size >= 2
            ) {
                callee = callee.child(1)
            }
            return if (callee is RightNameLeaf) {
                val calleeName = callee.content as? ResolvedName
                // Is it the name of `@export let doneResult = new DoneResult()` from Implicits.temper
                calleeName is ExportedName && calleeName.origin.loc is ImplicitsCodeLocation &&
                    calleeName.baseName == doneResultParsedName
            } else {
                val fn = callee.functionContained
                fn is LongLivedUserFunction && fn.stayLeaf == doneResultExportStay.value
            }
        }
    }
    val (simpler) = Rewrite()(body, Unit)
    return simpler
}

private val doneResultParsedName = ParsedName("doneResult") // defined in Implicits.temper

private val PreTranslated.isEmptyBlock get() =
    this is PreTranslated.Block && this.unfixedElements.isEmpty()

// Apply an operation to each simple statement, recursing through loops and conditions.
private fun forEachLeafStmt(
    stmt: TmpL.Statement?,
    body: (TmpL.Statement) -> Unit,
): Unit = when (stmt) {
    null -> {}
    is TmpL.NestingStatement -> stmt.nestedStatements.forEach { forEachLeafStmt(it, body) }
    else -> body(stmt)
}

private val doneResultExportStay = lazy {
    (
        TFunction.unpackOrNull(
            ImplicitsModule.module.exports!!.first { it.name.toSymbol().text == doneResultParsedName.nameText }
                .value,
        ) as? LongLivedUserFunction
        )?.stayLeaf
}
