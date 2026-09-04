package lang.temper.be.tmpl

import lang.temper.interp.docgenalts.DocGenAltFn
import lang.temper.interp.docgenalts.DocGenAltIfFn
import lang.temper.interp.docgenalts.DocGenAltImpliedResultFn
import lang.temper.interp.docgenalts.DocGenAltReturnFn
import lang.temper.interp.docgenalts.DocGenAltWhileFn
import lang.temper.log.spanningPosition
import lang.temper.name.NameMaker
import lang.temper.name.TemperName
import lang.temper.value.BlockTree
import lang.temper.value.CallTree

// Many of the alt doc gen fns were meant to work around
// control flow mangling, but our new representation of ControlFlow
// is better than the old one in that respect.
// TOOD: Do we need alt docgen functions for control flow?
internal fun translateAltDocGenFn(
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
    is DocGenAltReturnFn -> PreTranslated.Return(
        t.pos,
        PreTranslated.TreeWrapper(t.child(1)),
        goalTranslator.bodyFor as BodyForFun,
    )
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
