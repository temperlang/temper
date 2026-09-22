package lang.temper.type2

import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.type.FunctionType
import lang.temper.type.MkType
import lang.temper.type.TypeFormal

enum class ValueFormalKind {
    /** A regular value formal */
    Required,

    /**
     * A value formal which may not be supplied.
     *
     * Optional value formals are implicitly nullable,
     * and passing `null` for one is equivalent to not supplying it.
     *
     * If an optional value formal has a default expression, that expression
     * is evaluated when the function receives the actual value `null` for it
     * and if the declared type is non-nullable and the default expression's type
     * is non-nullable, then references to the value formal in the function body
     * or in later default expressions, may be treated as non-nullable.
     */
    Optional,
}

fun Signature2.mapType(m: Map<TypeFormal, Type2>): Signature2 {
    if (m.isEmpty()) {
        return this
    }
    return Signature2(
        returnType2 = returnType2.mapType(m),
        hasThisFormal = hasThisFormal,
        requiredInputTypes = requiredInputTypes.map { it.mapType(m) },
        optionalInputTypes = optionalInputTypes.map { it.mapType(m) },
        typeFormals = typeFormals,
    )
}

data class Callee(
    val sig: Signature2,
    val priority: CalleePriority = CalleePriority.Default,
) : TokenSerializable {
    constructor(t: FunctionType, priority: CalleePriority) : this(hackTryStaticTypeToSig(t) ?: error("$t"), priority)

    val functionType: FunctionType get() = MkType.fnDetails(
        sig.typeFormals,
        valueFormals = buildList {
            sig.requiredInputTypes.mapTo(this) {
                FunctionType.ValueFormal(
                    null,
                    hackMapNewStyleToOld(it),
                    isOptional = false,
                )
            }
            sig.optionalInputTypes.mapTo(this) {
                FunctionType.ValueFormal(
                    null,
                    hackMapNewStyleToOld(it),
                    isOptional = true,
                )
            }
        },
        returnType = hackMapNewStyleToOld(sig.returnType2),
    )

    override fun renderTo(tokenSink: TokenSink) {
        when (priority) {
            CalleePriority.Default -> {}
            CalleePriority.Fallback -> tokenSink.word("fallback")
        }
        sig.renderTo(tokenSink)
    }

    override fun toString(): String = toStringViaTokenSink { renderTo(it) }
}

enum class CalleePriority : Comparable<CalleePriority> {
    /**
     * For callees that can be ignored if there is a higher priority
     * callee that is at least as compatible.
     */
    Fallback,
    Default,
}
