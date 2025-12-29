package lang.temper.value

import lang.temper.common.IntuitiveLogicalExpressionBuilder
import lang.temper.common.LogicalAlgebra
import lang.temper.common.Term
import lang.temper.common.buildIntuitive
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes

/**
 * Replaces a block child with an expression that is the logical inverse of it.
 */
fun BlockChildReference.invertLogicalExpr(parentBlock: BlockTree) {
    val doc = parentBlock.document
    val edge = parentBlock.dereference(this) ?: return
    val negation = LogicalAlgebra.not(logicalAlgebraFrom(edge.target))

    val booleanType = WellKnownTypes.booleanType
    val oneBoolToBoolType = MkType.fn(
        emptyList(),
        listOf(booleanType),
        null,
        booleanType,
    )
    val twoBoolsToBoolType = MkType.fn(
        emptyList(),
        listOf(booleanType, booleanType),
        null,
        booleanType,
    )

    val b = object : IntuitiveLogicalExpressionBuilder<Position, Tree, Tree> {
        override fun constant(x: Position, b: Boolean): Tree =
            ValueLeaf(doc, x, TBoolean.value(b))

        override fun term(x: Position, t: Tree): Tree = freeTree(t)

        override fun parenthesize(betweenTwoParentheses: () -> Tree): Tree =
            betweenTwoParentheses()

        override val useDifference: Boolean = false
        override fun difference(x: Position, left: Tree, rights: List<Tree>): Tree {
            error("use difference is false")
        }

        override fun and(x: Position, operands: List<Tree>): Tree =
            binaryChain(x, operands, andBuiltinName)

        override fun or(x: Position, operands: List<Tree>): Tree =
            binaryChain(x, operands, orBuiltinName)

        private fun binaryChain(pos: Position, operands: List<Tree>, operator: BuiltinName): Tree {
            check(operands.isNotEmpty())
            // Logical expressions are right associative, so the below are equivalent
            //
            //    a && b && c
            //    a && (b && c)
            //
            // We iterate right to left building the n-ary operation.
            var i = operands.lastIndex
            var t = freeTree(operands[i])
            while (i > 0) {
                i -= 1
                val next = freeTree(operands[i])
                val operatorTree = RightNameLeaf(doc, t.pos.leftEdge, operator)
                operatorTree.typeInferences = BasicTypeInferences(twoBoolsToBoolType, emptyList())
                val children = listOf(
                    operatorTree,
                    next,
                    t,
                )
                val p = if (i == 0) {
                    pos
                } else {
                    children.spanningPosition(t.pos)
                }
                t = CallTree(doc, p, children)
                t.typeInferences = CallTypeInferences(
                    booleanType,
                    twoBoolsToBoolType,
                    emptyMap(),
                    emptyList(),
                )
            }
            return t
        }

        override fun not(x: Position, operand: Tree): Tree {
            val notFn = RightNameLeaf(doc, x.leftEdge, notBuiltinName)
            notFn.typeInferences = BasicTypeInferences(oneBoolToBoolType, emptyList())
            val call = CallTree(
                doc,
                x,
                listOf(notFn, freeTree(operand)),
            )
            call.typeInferences = CallTypeInferences(
                booleanType, oneBoolToBoolType, emptyMap(), emptyList(),
            )
            return call
        }

        override fun minusSymbol() {
            // Nothing to do
        }
        override fun andSymbol() {
            // Nothing to do
        }
        override fun notSymbol() {
            // Nothing to do
        }
        override fun orSymbol() {
            // Nothing to do
        }
    }
    edge.replace(negation.buildIntuitive(b))
}

private fun logicalAlgebraFrom(
    tree: Tree,
): LogicalAlgebra<Position, Tree> {
    if (tree is ValueLeaf) {
        when (tree.valueContained(TBoolean)) {
            null -> {}
            false -> return LogicalAlgebra.valueFalse(tree.pos)
            true -> return LogicalAlgebra.valueTrue(tree.pos)
        }
    }
    if (tree is CallTree) {
        val calleeName = when (val callee = tree.childOrNull(0)) {
            is RightNameLeaf -> (callee.content as? BuiltinName)?.builtinKey
            else -> (tree.functionContained as? NamedBuiltinFun)?.name
        }
        when (calleeName) {
            "!" -> if (tree.size == UNARY_OP_CALL_ARG_COUNT) {
                return LogicalAlgebra.not(
                    logicalAlgebraFrom(tree.child(1)),
                )
            }
            "||" -> if (tree.size == BINARY_OP_CALL_ARG_COUNT) {
                return LogicalAlgebra.or(
                    tree.pos,
                    listOf(
                        logicalAlgebraFrom(tree.child(1)),
                        logicalAlgebraFrom(tree.child(2)),
                    ),
                )
            }
            "&&" -> if (tree.size == BINARY_OP_CALL_ARG_COUNT) {
                return LogicalAlgebra.and(
                    tree.pos,
                    listOf(
                        logicalAlgebraFrom(tree.child(1)),
                        logicalAlgebraFrom(tree.child(2)),
                    ),
                )
            }
        }
    }
    return Term(tree.pos, tree)
}

private val notBuiltinName = BuiltinName("!")
private val andBuiltinName = BuiltinName("&&")
private val orBuiltinName = BuiltinName("||")
