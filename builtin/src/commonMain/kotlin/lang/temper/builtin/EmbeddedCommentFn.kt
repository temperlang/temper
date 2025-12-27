package lang.temper.builtin

import lang.temper.common.LeftOrRight
import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.withNullity
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.CallTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TBoolean
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.functionContained
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * This function provides a mechanism similar to
 * [*REM*](https://www.c64-wiki.com/wiki/REM) statements.
 *
 * Statement level comments parse to calls to this function.
 * During normal execution, they would inline out, but in
 * [documentation][lang.temper.lexer.Genre.Documentation] code,
 * we want to preserve comments and embed them in the translated
 * documentation.
 *
 * It simply returns `void`.
 */
internal object EmbeddedCommentFn : BuiltinStatelessCallableValue, NamedBuiltinFun {
    override val name: String = "REM" // Because comments are all part of life's rich pageant
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            requiredInputTypes = listOf(
                // The comment text with delimiters and line prefixes removed.
                WellKnownTypes.stringType2,
                // null -> not documentation for a declaration or function
                // false -> documentation for the preceding declaration or function as in
                //        let x = ...; /// Documentation
                // true -> documentation for the following declaration or function as in
                //        /** Documentation */ let x = ...;
                WellKnownTypes.intType2.withNullity(Nullity.OrNull),
                // true for semilit comments which need to be filtered by prefix in the
                // context of a specific declaration.
                WellKnownTypes.booleanType2,
            ),
            hasThisFormal = false,
        ),
    )

    override val callMayFailPerSe: Boolean = false

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        if (cb.stage <= Stage.SyntaxMacro) {
            return NotYet
        }
        return void
    }

    override val isPure: Boolean = true
}

private const val REM_CALL_TREE_SIZE = 4

/** Is a call to [EmbeddedCommentFn] with the correct arity. */
fun isRemCall(tree: Tree): Boolean =
    tree is CallTree && tree.size == REM_CALL_TREE_SIZE &&
        tree.child(0).functionContained == EmbeddedCommentFn

data class RemUnpacked(
    override val pos: Position,
    val association: LeftOrRight,
    val text: String,
    val isSemilit: Boolean,
) : Positioned

fun unpackAsRemCall(tree: Tree): RemUnpacked? {
    if (isRemCall(tree)) {
        val text = tree.child(1).valueContained(TString)
        val associatesRight = tree.child(2).valueContained(TBoolean)

        @SuppressWarnings("MagicNumber") // THREE!
        val isSemilit = tree.child(3).valueContained(TBoolean)
        if (text != null && associatesRight != null && isSemilit != null) {
            return RemUnpacked(
                tree.pos,
                association = if (associatesRight) { LeftOrRight.Right } else { LeftOrRight.Left },
                text = text,
                isSemilit = isSemilit,
            )
        }
    }
    return null
}
