package lang.temper.builtin

import lang.temper.value.BlockTree
import lang.temper.value.Tree
import lang.temper.value.complexArgSymbol
import lang.temper.value.symbolContained

/**
 * The ast-build grammar parses a cover-grammar of actual and formal
 * args until [lang.temper.stage.Stage.DisAmbiguate] can rewrite
 * complex args to one or the other.
 *
 *     // `x = 1` is a formal argument with a default expression
 *     let f(x = 1): Int { x + 1 }
 *     // An actual argument x = 1 passed to f.
 *     f(x = 1)
 *
 * That `x = 1` is represented as a block which contains:
 *
 * - the complex arg symbol
 * - the name `x`
 * - the \init symbol
 * - the initial expression: `1`
 *
 * Those are regrouped into a declaration if a formal, or
 * reverse engineered into an actual expression by the DisAmbiguate
 * stage machinery.
 */
fun isComplexArg(tree: Tree): Boolean =
    tree is BlockTree &&
        tree.size >= 1 &&
        tree.child(0).symbolContained == complexArgSymbol
