package lang.temper.be.tmpl

import lang.temper.builtin.Assign
import lang.temper.builtin.BuiltinFuns
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.value.BasicTypeInferences
import lang.temper.value.CallTree
import lang.temper.value.NotFn
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.typeSymbol

/**
 * Given a call, check whether it bubbles, so needs to be expanded it into statements
 * that might deal with it as a non-first-class result value.
 */
internal fun adjustForBubbles(
    pt: PreTranslated,
    goalTranslator: GoalTranslator,
    orElseJumpLabels: OrElseJumpLabels,
): List<PreTranslated>? {
    when (goalTranslator.supportNetwork.bubbleStrategy) {
        BubbleBranchStrategy.Exceptions -> return null
        BubbleBranchStrategy.Results -> Unit
    }
    val tree = (pt as? PreTranslated.TreeWrapper)?.tree ?: return null
    val bubblyCall = unpackBubblyCall(tree) ?: return null

    var nameHint = ParsedName("result")
    (bubblyCall.assigned?.content as? ResolvedParsedName)?.let { nameHint = it.baseName }
    val resultVarName = goalTranslator.translator.unusedName(nameHint)
    val resultType = bubblyCall.resultType
    val resultTypeOld = MkType.nominal(
        WellKnownTypes.resultTypeDefinition,
        resultType.bindings.map { hackMapNewStyleToOld(it) },
    )

    val resultDeclPos = bubblyCall.pos.leftEdge
    val afterCallPos = bubblyCall.pos.rightEdge

    val doc = tree.document
    return buildList {
        // let result#123;
        add(
            PreTranslated.TreeWrapper(
                doc.treeFarm.grow(resultDeclPos) {
                    Decl {
                        Ln(resultVarName, resultTypeOld)
                        V(typeSymbol)
                        V(Value(ReifiedType(resultType), TType), WellKnownTypes.typeType)
                    }
                },
            ),
        )

        // result#123 = mightBubble();
        add(
            PreTranslated.TreeWrapper(
                doc.treeFarm.grow {
                    Assign(bubblyCall.bubbles.pos, resultVarName, resultTypeOld) {
                        Replant(bubblyCall.bubbles.copy(copyInferences = true))
                    }
                },
            ),
        )

        // condition for the `if` that checks whether the result needs to jump to
        // recovery code.
        val isOkResultCall = synthesizeCall(
            doc,
            afterCallPos,
            ResultHelperFnPlaceholders.IsOkResult,
            listOf(
                RightNameLeaf(doc, afterCallPos, resultVarName).also {
                    it.typeInferences = BasicTypeInferences(resultTypeOld, listOf())
                },
            ),
            resultTypeOld,
            listOf(bubblyCall.passType, bubblyCall.failType),
        )

        // If we're in an orelse, break to the end of the or-block so that it flows
        // right into the recovery instructions.
        // If not, return the result.
        val exit = if (orElseJumpLabels.isEmpty()) {
            when (val bodyFor = goalTranslator.bodyFor) {
                is BodyForFun -> {
                    // TODO: maybe have FreeFailure take the result variable name and
                    // adjust goal translators to do this where applicable.
                    PreTranslated.Return(
                        afterCallPos,
                        PreTranslated.TreeWrapper(
                            RightNameLeaf(doc, afterCallPos, resultVarName).also {
                                it.typeInferences = BasicTypeInferences(resultTypeOld, listOf())
                            },
                        ),
                        bodyFor,
                    )
                }
                is BodyForModule ->
                    PreTranslated.Goal(afterCallPos, FreeFailure, goalTranslator)
            }
        } else {
            PreTranslated.Break(
                afterCallPos,
                PreTranslatedLabel(afterCallPos, orElseJumpLabels.last()),
            )
        }

        // if (!isOkResult(r#123)) { break orElse#123 }
        add(
            PreTranslated.If(
                bubblyCall.pos,
                test = PreTranslated.TreeWrapper(
                    synthesizeCall(doc, afterCallPos, NotFn, listOf(isOkResultCall)),
                ),
                consequent = exit,
                alternate = PreTranslated.Block(afterCallPos, listOf()),
            ),
        )

        val assigned = bubblyCall.assigned
        if (assigned != null) {
            val assignment = tree as CallTree
            val assignCallee = tree.child(0)

            // assigned = unpackOkResult(r#123);
            add(
                PreTranslated.TreeWrapper(
                    doc.treeFarm.grow(assigned.pos) {
                        Call(assignment.pos, type = assignment.typeInferences) {
                            V(assignCallee.pos, BuiltinFuns.vSetLocalFn, assignCallee.typeInferences?.type)
                            Replant(assigned.copy(copyInferences = true))
                            Replant(
                                synthesizeCall(
                                    doc, afterCallPos, ResultHelperFnPlaceholders.UnpackOkResult,
                                    listOf(
                                        doc.treeFarm.grow { Rn(afterCallPos, resultVarName, resultTypeOld) },
                                    ),
                                    returnType = hackMapNewStyleToOld(bubblyCall.passType),
                                    typeActuals = listOf(bubblyCall.passType, bubblyCall.failType),
                                ),
                            )
                        }
                    },
                ),
            )
        }
    }
}
