package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.type.WellKnownTypes
import lang.temper.value.BreakOrContinue
import lang.temper.value.TInt
import lang.temper.value.Value

/** Provide a way to intercept jumps up the stack temporarily for common code execution. */
internal class EscapeHatch private constructor(
    pos: Position,
    private val translator: TmpLTranslator,
    private val parent: PreTranslated?,
    private val escapes: Set<JumpInfo>,
) {
    private val noFurtherEscapeNeeded = escapes.size == 1 && (
        parent is PreTranslated.LabeledStmt && escapes.first().let {
            // Breaking out of parent labeled statement.
            it.label != null && it.label == parent.label.jumpLabel && it.kind == BreakOrContinue.Break
        } || parent is PreTranslated.WhileLoop && escapes.first().let {
            // Continuing unlabeled parent loop.
            it.label == null && it.kind == BreakOrContinue.Continue
        }
        )
    private val escapeValues = escapes.withIndex().associate { it.value to it.index + 1 }
    private val escapeId = TmpL.Id(pos, translator.unusedName(ParsedName("escape")))
    private val escapeLabel = TmpL.JumpLabel(TmpL.Id(pos, translator.unusedName(ParsedName("escapeLabel"))))

    fun apply(statement: TmpL.Statement): TmpL.Statement {
        return TmpL.LabeledStatement(pos = statement.pos, label = escapeLabel.deepCopy(), statement = statement)
    }

    fun declareEscapeValue(pos: Position, statements: MutableList<TmpL.Statement>) {
        if (!noFurtherEscapeNeeded) {
            TmpL.LocalDeclaration(
                pos = pos,
                metadata = listOf(),
                name = escapeId,
                type = translator.translateType(pos, WellKnownTypes.intType2).aType,
                init = TmpL.ValueReference(pos, Value(0, TInt)),
                assignOnce = false,
                descriptor = WellKnownTypes.intType2,
            ).also { statements.add(it) }
        }
    }

    fun rewriteEscape(statement: TmpL.Statement, loopDepth: Int): TmpL.Statement? {
        val jump = (statement as? TmpL.JumpUpStatement) ?: return null
        if (jump.label == null && loopDepth > 0) {
            return statement
        }
        val escapeValue = escapeValues[JumpInfo(jump)] ?: return statement
        val pos = statement.pos
        val valueReference = TmpL.ValueReference(pos, Value(escapeValue, TInt))
        val breakStatement = TmpL.BreakStatement(pos, escapeLabel.deepCopy())
        return when {
            noFurtherEscapeNeeded -> breakStatement
            else -> TmpL.BlockStatement(
                pos,
                listOf(
                    TmpL.Assignment(pos, escapeId.deepCopy(), valueReference, type = WellKnownTypes.intType2),
                    breakStatement,
                ),
            )
        }
    }

    fun addEscapes(pos: Position, statements: MutableList<TmpL.Statement>) {
        when (escapes.size) {
            // Presume single escape is a common case, so make it prettier.
            1 -> when {
                noFurtherEscapeNeeded -> return
                else -> TmpL.IfStatement(
                    pos,
                    test = TmpL.InfixOperation(
                        pos,
                        TmpL.Reference(pos, escapeId.deepCopy(), WellKnownTypes.intType2),
                        TmpL.InfixOperator(pos, TmpLOperator.EqEqInt),
                        TmpL.ValueReference(pos, Value(1, TInt)),
                    ),
                    consequent = escapes.first().buildStatement(pos),
                    alternate = null,
                )
            }
            // Use computed jump for more than one escape.
            else -> TmpL.ComputedJumpStatement(
                pos,
                caseExpr = TmpL.Reference(pos, escapeId.deepCopy(), WellKnownTypes.intType2),
                cases = escapeValues.map { (escapeInfo, escapeValue) ->
                    TmpL.ComputedJumpCase(
                        pos,
                        values = listOf(TmpL.ConstIndex(pos, escapeValue)),
                        body = escapeInfo.buildStatement(pos).let { TmpL.BlockStatement(pos, listOf(it)) },
                    )
                },
                elseCase = TmpL.ComputedJumpElse(pos, TmpL.BlockStatement(pos, listOf())),
            )
        }.also { statements.add(it) }
    }

    companion object {
        fun buildIfNeeded(
            pos: Position,
            translator: TmpLTranslator,
            parent: PreTranslated?,
            statements: List<TmpL.Statement>,
        ): EscapeHatch? {
            val escapes = findEscapes(statements)
            return when {
                escapes.isEmpty() -> null
                else -> EscapeHatch(pos, translator, parent, escapes)
            }
        }

        private fun findEscapes(statements: Iterable<TmpL.Statement>): Set<JumpInfo> = buildSet {
            val containedLabels = mutableSetOf<ResolvedName>()
            var loopDepth = 0
            for (statement in statements) {
                fun inspectLabels(sub: TmpL.Statement) {
                    run before@{
                        val label = when (sub) {
                            is TmpL.JumpUpStatement -> sub.label
                            is TmpL.LabeledStatement -> {
                                containedLabels.add(sub.label.id.name)
                                return@before
                            }
                            is TmpL.WhileStatement -> {
                                loopDepth += 1
                                return@before
                            }
                            else -> return@before
                        }?.id?.name
                        val escaping = when {
                            label == null -> loopDepth == 0
                            else -> label !in containedLabels
                        }
                        if (escaping) {
                            add(JumpInfo(sub))
                        }
                    }
                    if (sub is TmpL.NestingStatement) {
                        sub.nestedStatements.forEach { inspectLabels(it) }
                        if (sub is TmpL.WhileStatement) {
                            loopDepth -= 1
                        }
                    }
                }
                inspectLabels(statement)
            }
        }
    }
}

internal open class EscapeHatchRewriter(private val hatch: EscapeHatch?) : TmpLTreeRewriter {
    private var loopDepth = 0

    override fun rewriteStatement(x: TmpL.Statement): TmpL.Statement {
        return hatch?.rewriteEscape(x, loopDepth) ?: super.rewriteStatement(x)
    }

    override fun rewriteWhileStatement(x: TmpL.WhileStatement): TmpL.Statement {
        loopDepth += 1
        return try {
            super.rewriteWhileStatement(x)
        } finally {
            loopDepth -= 1
        }
    }
}

/** Extract core jump info for use as a summary or key. */
private data class JumpInfo(
    val kind: BreakOrContinue,
    val label: ResolvedName?,
) {
    constructor(stmt: TmpL.JumpUpStatement) : this(
        kind = when (stmt) {
            is TmpL.BreakStatement -> BreakOrContinue.Break
            is TmpL.ContinueStatement -> BreakOrContinue.Continue
        },
        label = stmt.label?.id?.name,
    )

    fun buildStatement(pos: Position): TmpL.Statement {
        val label = label?.let { TmpL.JumpLabel(TmpL.Id(pos, it)) }
        return when (kind) {
            BreakOrContinue.Break -> TmpL.BreakStatement(pos, label)
            BreakOrContinue.Continue -> TmpL.ContinueStatement(pos, label)
        }
    }
}
