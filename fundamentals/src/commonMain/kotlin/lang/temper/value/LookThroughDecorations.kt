package lang.temper.value

import lang.temper.common.subListToEnd

/**
 * Look through decorations for members so that we can find the eventually decorated.
 *
 * When processing parts of a type declaration body, we might see a decorated property declaration:
 *     @foo x;
 * This method makes it easier to find the `x` and promote it to a declaration so that the above
 * becomes:
 *     @foo let x;
 * which is important when the implementation of `@foo` requires a declaration.
 */
fun lookThroughDecorations(edge: TEdge): TEdge {
    var candidateEdge = edge
    while (true) {
        val (_, decoratedIndex) = unpackUnappliedDecoration(candidateEdge) ?: break
        candidateEdge = candidateEdge.target.edge(decoratedIndex)
    }
    return candidateEdge
}

/**
 * Information about a decorator that has yet to be folded into its target.
 *
 * Unapplied decorators come in three forms:
 * - Calls to `@` with a decorator name as the first argument and the decorated as the
 *   second argument.
 * - Calls to `@` with a call like `decoratorName(param1, param2)` as the first argument and
 *   the decorated as the second argument
 * - Calls to a name like `@foo` with the decorated as the first argument, and any decorator
 *   parameters as subsequent arguments.
 */
data class UnappliedDecoration(
    val decoratorName: String,
    val decoratedIndex: Int,
    val parameterEdges: List<TEdge>,
)

/**
 * If [edge]'s target is an application of a decorator, the name of the decorator and the index of
 * the decorated child.
 */
fun unpackUnappliedDecoration(edge: TEdge): UnappliedDecoration? {
    val target = edge.target
    val lexicalDecoratorNameText = lexicalDecoratorNameOf(target) ?: return null
    return if (lexicalDecoratorNameText == "@") {
        val unprefixedDecorator = target.childOrNull(1)
        val unprefixedDecoratorName = when (unprefixedDecorator) {
            is RightNameLeaf -> unprefixedDecorator
            is CallTree -> unprefixedDecorator.childOrNull(0)
            else -> null
        }
        val unprefixedNameText = (unprefixedDecoratorName as? RightNameLeaf)?.content?.builtinKey
            ?: return null
        // Before `@` runs, the decorated is at the end, after any parenthesized arguments.
        UnappliedDecoration(
            decoratorName = "@$unprefixedNameText",
            decoratedIndex = target.size - 1,
            parameterEdges = if (unprefixedDecorator is CallTree && unprefixedDecorator.size > 1) {
                unprefixedDecorator.edges.subListToEnd(1)
            } else {
                emptyList()
            },
        )
    } else {
        // The `@` operator moves the decorated to position 1, before parenthesized arguments.
        UnappliedDecoration(
            decoratorName = lexicalDecoratorNameText,
            decoratedIndex = 1,
            parameterEdges = target.edges.subListToEnd(2),
        )
    }
}

private fun lexicalDecoratorNameOf(tree: Tree): String? {
    if (tree !is CallTree || tree.size < 2) { return null }
    val callee = tree.child(0)
    val nameText = if (callee is RightNameLeaf) {
        callee.content.builtinKey
    } else {
        val fn = callee.functionContained
        if (fn is NamedBuiltinFun) {
            fn.name
        } else {
            null
        }
    }
    return if (nameText?.startsWith("@") == true) {
        nameText
    } else {
        null
    }
}
