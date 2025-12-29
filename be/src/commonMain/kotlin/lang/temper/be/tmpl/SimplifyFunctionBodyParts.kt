package lang.temper.be.tmpl

import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.value.TBoolean
import lang.temper.value.void

/**
 * Rewrite overly verbose function bodies.
 *
 * Specifically we rewrite formulaic bodies like the below.
 *
 *     let return__123;
 *     ... // Some stuff that doesn't mention return__123
 *     return__123 = ...;
 *     return return__123;
 *
 * That three-statement pattern can be condensed to
 *
 *     ... // Some stuff
 *     return ...;
 */
internal fun simplifyFunctionBodyParts(
    statements: MutableList<TmpL.Statement>,
) {
    if (statements.size >= STATEMENT_COUNT_FOR_LET_ASSIGN_RETURN) {
        val one = statements.first()
        val two = statements[statements.lastIndex - 1]
        val three = statements[statements.lastIndex]
        val others = statements.subList(1, statements.lastIndex - 1)
        if (
            one is TmpL.LocalDeclaration &&
            two is TmpL.Assignment &&
            three is TmpL.ReturnStatement
        ) {
            val returned = three.expression
            if (
                returned is TmpL.Reference &&
                returned.id.name == one.name.name &&
                two.left.name == returned.id.name &&
                others.none { it.reads(returned.id.name) }
            ) {
                val assigned = two.right
                if (assigned is TmpL.Expression) {
                    val toReturn =
                        if (assigned is TmpL.ValueReference && assigned.value == void) {
                            null // return;
                        } else {
                            // Release assigned from its parent
                            two.right =
                                TmpL.ValueReference(assigned.pos, WellKnownTypes.booleanType2, TBoolean.valueFalse)
                            assigned
                        }

                    // Rewrite the statements
                    statements.compatRemoveFirst() // one
                    statements.compatRemoveLast() // two
                    statements.compatRemoveLast() // three
                    statements.add(TmpL.ReturnStatement(assigned.pos, toReturn))
                }
            }
        }
    }
}

private const val STATEMENT_COUNT_FOR_LET_ASSIGN_RETURN = 3

private fun TmpL.Tree.reads(name: TemperName): Boolean {
    if (this is TmpL.Id && this.name == name) { return true }
    return this.children.any { it.reads(name) }
}
