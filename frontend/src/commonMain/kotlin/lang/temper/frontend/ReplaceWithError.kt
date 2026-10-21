package lang.temper.frontend

import lang.temper.builtin.Types
import lang.temper.log.LogEntry
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.value.CallTypeInferences
import lang.temper.value.ErrorFn
import lang.temper.value.TEdge
import lang.temper.value.TProblem
import lang.temper.value.Value
import lang.temper.value.errorFn
import lang.temper.value.typeFromSignature

/**
 * Replaces the edge's target with a call to [ErrorFn] that carries [problem],
 * typed like the replaced tree, so that the interpreter fails with [problem]
 * and backends see a garbage expression instead of the rejected tree.
 */
internal fun TEdge.replaceWithError(problem: LogEntry) {
    val type = target.typeInferences?.type
    val errorTypeInferences = type?.let {
        val sig = if (type.isVoidLike) {
            ErrorFn.voidSig
        } else {
            ErrorFn.genericSig
        } // Extend nary type with arg
            .copy(requiredInputTypes = listOf(WellKnownTypes.anyValueOrNullType2))
        CallTypeInferences(
            type,
            typeFromSignature(sig),
            buildMap {
                val tf = sig.typeFormals.firstOrNull()
                if (tf != null) {
                    this[tf] = type
                }
            },
            listOf(),
        )
    }
    replace {
        Call(type = errorTypeInferences) {
            V(errorFn, type = errorTypeInferences?.variant)
            V(Value(problem, TProblem), type = Types.problem.type)
        }
    }
}
