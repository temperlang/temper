package lang.temper.common

/**
 * Allows manipulating logical expressions.
 *
 * @param X a value stored with each node that has no specific semantics.
 *   This can be used to keep position metadata, for example, so that we
 *   can turn an AST into a logical expression, simplify it, and then
 *   turn it back into a tree.
 * @param TERM the content stored at leaves
 */
sealed class LogicalAlgebra<X, TERM> {
    abstract val x: X

    abstract fun toStructureString(): String

    override fun toString() = toStringViaBuilder {
        this.buildIntuitive(IntuitiveToStringBuilder(it))
    }

    companion object {
        /** aka intersection */
        fun <X, TERM> and(x: X, vararg elements: LogicalAlgebra<X, TERM>): LogicalAlgebra<X, TERM> {
            return and(x, elements.toList())
        }

        fun <X, TERM> and(x: X, elements: Iterable<LogicalAlgebra<X, TERM>>): LogicalAlgebra<X, TERM> {
            return not(Nand(x, elements))
        }

        /** aka union */
        fun <X, TERM> or(x: X, vararg types: LogicalAlgebra<X, TERM>): LogicalAlgebra<X, TERM> {
            return or(x, types.toList())
        }

        fun <X, TERM> or(x: X, types: Iterable<LogicalAlgebra<X, TERM>>): LogicalAlgebra<X, TERM> {
            return Nand(x, types.map(::not))
        }

        /**
         * aka negation.
         */
        fun <X, TERM> not(la: LogicalAlgebra<X, TERM>): LogicalAlgebra<X, TERM> {
            return Nand(la.x, listOf(la))
        }

        /**
         * a - b - c   ==   a & -(b | c)
         *
         * Since this can represent for union/intersection as well as
         * logical disjunction/conjunction we define subtraction.
         */
        fun <X, TERM> minus(
            x: X,
            pos: LogicalAlgebra<X, TERM>,
            vararg subtracted: LogicalAlgebra<X, TERM>,
        ): LogicalAlgebra<X, TERM> {
            return and(x, pos, not(or(x, subtracted.toList())))
        }

        // The identity of n-ary AND is true, so the identity of n-ary NAND is false.
        fun <X, TERM> valueTrue(x: X) = Nand.invoke<X, TERM>(x, listOf(valueFalse(x)))
        fun <X, TERM> valueFalse(x: X) = Nand.invoke<X, TERM>(x, emptyList())
    }
}

data class Term<X, TERM>(
    override val x: X,
    val term: TERM,
) : LogicalAlgebra<X, TERM>() {
    override fun toStructureString() = "(Term $term)"
}

class Nand<X, TERM> private constructor(
    override val x: X,
    val elements: List<LogicalAlgebra<X, TERM>>,
) : LogicalAlgebra<X, TERM>() {

    override fun equals(other: Any?): Boolean {
        return other is Nand<*, *> && this.elements == other.elements
    }

    override fun hashCode() = elements.hashCode()

    override fun toStructureString() =
        "(Nand ${elements.joinToString(", ") { it.toStructureString() }})"

    companion object {
        operator fun <X, TERM> invoke(
            x: X,
            elements: Iterable<LogicalAlgebra<X, TERM>>,
        ): LogicalAlgebra<X, TERM> {
            val els = mutableSetOf<LogicalAlgebra<X, TERM>>()
            for (el in elements) {
                if (el is Nand) {
                    val subs = el.elements
                    if (subs.size == 1) {
                        val subEl = subs[0]
                        if (subEl is Nand) {
                            // Flattening doubly-nested NANDs produces prettier textual output.
                            els.addAll(subEl.elements)
                            continue
                        }
                    }
                }
                els.add(el)
            }
            return Nand(x, els.toList())
        }
    }
}

private enum class OpPrec {
    Lowest,
    Bar,
    Amp,
    InfixMinus,
}

private class IntuitiveToStringBuilder<X, TERM>(
    val out: StringBuilder,
) : IntuitiveLogicalExpressionBuilder<X, TERM, Unit> {
    override fun constant(x: X, b: Boolean) {
        out.append(b)
    }

    override fun term(x: X, t: TERM) {
        out.append(t)
    }

    override fun parenthesize(betweenTwoParentheses: () -> Unit) {
        out.append('(')
        betweenTwoParentheses()
        out.append(')')
    }

    override fun difference(x: X, left: Unit, rights: List<Unit>) {
        // Nothing to do
    }

    override fun and(x: X, operands: List<Unit>) {
        // Nothing to do
    }

    override fun or(x: X, operands: List<Unit>) {
        // Nothing to do
    }

    override fun not(x: X, operand: Unit) {
        // Nothing to do
    }

    override fun minusSymbol() {
        out.append(" - ")
    }

    override fun andSymbol() {
        out.append(" & ")
    }

    override fun notSymbol() {
        out.append("-")
    }

    override fun orSymbol() {
        out.append(" | ")
    }

    override val useDifference: Boolean get() = true
}

/**
 * Re-sugars the logical expression form.
 * The [NAND form] is simple, but sometimes what we want is a logical form with AND, OR, and NOT.
 * This allows translating to such a form.
 */
fun <X, TERM, OUT> (LogicalAlgebra<X, TERM>).buildIntuitive(
    builder: IntuitiveLogicalExpressionBuilder<X, TERM, OUT>,
) = buildIntuitive(this, builder, false, OpPrec.Lowest)

private fun <X, TERM, OUT> buildIntuitive(
    la: LogicalAlgebra<X, TERM>,
    builder: IntuitiveLogicalExpressionBuilder<X, TERM, OUT>,
    negated: Boolean,
    prec: OpPrec,
): OUT {
    when (la) {
        is Nand -> {
            val elements = la.elements
            // See if we can resugar by testing for a series of patterns:
            // Simple negation
            //   -a                                          from   nand(a)
            if (elements.size == 1) {
                return buildIntuitive(elements[0], builder, !negated, prec)
            }
            // Simple boolean value                          from nand(/*intentionally blank*/)
            if (elements.isEmpty()) {
                return builder.constant(la.x, negated)
            }
            // Infix negation
            //   (a-b0-...-bn)   from   (a & !(b0|...|bn))   from   nand(nand(a, nand(b0),...,nand(bn)))
            if (negated && builder.useDifference) {
                val subtractee = elements[0]
                val subtracted = elements.subList(1, elements.size)
                val countNegated = subtracted.count {
                    it is Nand && it.elements.size == 1
                }
                if (countNegated >= subtracted.size / 2) {
                    return buildIntuitiveOperation(prec, OpPrec.InfixMinus, builder) { p ->
                        builder.difference(
                            la.x,
                            buildIntuitive(subtractee, builder, false, p),
                            subtracted.map { s ->
                                builder.minusSymbol()
                                buildIntuitive(s, builder, true, p)
                            },
                        )
                    }
                }
            }
            // And
            //   (a0&...&an)                                 from   nand(nand(a0,...,bn))
            if (negated) {
                return buildIntuitiveOperation(
                    prec,
                    OpPrec.Amp,
                    builder,
                ) { p ->
                    builder.and(
                        la.x,
                        elements.mapIndexed { i, la ->
                            if (i != 0) { builder.andSymbol() }
                            buildIntuitive(la, builder, false, p)
                        },
                    )
                }
            }
            // Or
            //   (a0|...|an)     from   !(!a0&...&!an)       from   nand(nand(a0),...,nand(an))
            return buildIntuitiveOperation(
                prec,
                OpPrec.Bar,
                builder,
            ) { p ->
                builder.or(
                    la.x,
                    elements.mapIndexed { i, la ->
                        if (i != 0) {
                            builder.orSymbol()
                        }
                        buildIntuitive(la, builder, true, p)
                    },
                )
            }
        }
        is Term -> {
            return if (negated) {
                builder.notSymbol()
                builder.not(la.x, builder.term(la.x, la.term))
            } else {
                builder.term(la.x, la.term)
            }
        }
    }
}

private fun <X, TERM, OUT> buildIntuitiveOperation(
    outerPrec: OpPrec,
    operationPrec: OpPrec,
    builder: IntuitiveLogicalExpressionBuilder<X, TERM, OUT>,
    betweenTwoParentheses: (OpPrec) -> OUT,
): OUT = if (outerPrec >= operationPrec) {
    builder.parenthesize {
        betweenTwoParentheses(OpPrec.Lowest)
    }
} else {
    betweenTwoParentheses(operationPrec)
}

/**
 * See [buildIntuitive]
 */
interface IntuitiveLogicalExpressionBuilder<X, TERM, OUT> {
    fun constant(x: X, b: Boolean): OUT

    fun term(x: X, t: TERM): OUT

    /**
     * Called for operations where [and], [or], and [not] operations would be required according
     * to C-style precedence rules.
     */
    fun parenthesize(betweenTwoParentheses: () -> OUT): OUT

    fun difference(x: X, left: OUT, rights: List<OUT>): OUT

    fun and(x: X, operands: List<OUT>): OUT

    fun or(x: X, operands: List<OUT>): OUT

    fun not(x: X, operand: OUT): OUT

    fun minusSymbol()
    fun andSymbol()
    fun notSymbol()
    fun orSymbol()

    /** True to try to turn logical expressions into logical subtraction via [difference]. */
    val useDifference: Boolean
}
