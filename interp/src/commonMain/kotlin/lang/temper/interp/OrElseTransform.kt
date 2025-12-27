package lang.temper.interp

import lang.temper.lexer.Operator
import lang.temper.value.ControlFlow

/**
 * <!-- snippet: builtin/orelse -->
 * # `orelse`
 * The `orelse` infix operator allows recovering from
 * [failure to produce a result][snippet/builtin/bubble].
 *
 * The left is executed and if it [does not produce a result][snippet/type/Bubble],
 * then the right is executed and its result is used instead.
 *
 * ```temper
 * let f(n: Int, d: Int): Int { n / d orelse -1 }
 * console.log("f(3, 0)=${f(3, 0).toString()}"); //!outputs "f(3, 0)=-1"
 * console.log("f(6, 2)=${f(6, 2).toString()}"); //!outputs "f(6, 2)=3"
 * ```
 *
 * When you find that you're on a track that cannot produce a usable result, use
 * `orelse` to switch to a track that may yet succeed or allow a client to
 * try something else, or recover gracefully.
 */
internal object OrElseTransform : ControlFlowTransform(Operator.OrElse.text!!) {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val pos = macroCursor.macroEnvironment.pos
        val orClause = macroCursor.nextTEdge() ?: return null
        val elseClause = macroCursor.nextTEdge() ?: return null
        if (!macroCursor.isEmpty()) {
            return null
        }
        val orStmts = ControlFlow.StmtBlock.wrap(
            ControlFlow.Stmt(macroCursor.referenceTo(orClause)),
        )
        val elseStmts = ControlFlow.StmtBlock.wrap(
            ControlFlow.Stmt(macroCursor.referenceTo(elseClause)),
        )

        // Fails in orStmts that are not nested in a narrower orelse will weave to jumps to label which
        // continues onto elseStmts.
        // Using a temporary label avoids any confusion with inlining of block lambda bodies as in:
        //
        //     do {
        //       f { if (g()) { break orelse } }
        //     } orelse h();
        //
        //     // on weaving ->
        //
        //     var fail#0;
        //     //orelse#1: {
        //         var t#2;
        //         hs(fail#0, t#2 = f(fn { if (g()) { break orelse } }));
        //         if (fail#0) {
        //           break orelse#1; // jump to else
        //         }
        //         t#2 // Then do not proceed to else.
        //     //} else {
        //         h();
        //     //}

        val label = macroCursor.macroEnvironment.nameMaker.unusedTemporaryName("orelse")
        return ControlFlowSubflow(
            ControlFlow.OrElse(
                pos = pos,
                orClause = ControlFlow.Labeled(
                    pos = orClause.target.pos,
                    breakLabel = label,
                    continueLabel = null,
                    stmts = orStmts,
                ),
                elseClause = elseStmts,
            ),
        )
    }
}
