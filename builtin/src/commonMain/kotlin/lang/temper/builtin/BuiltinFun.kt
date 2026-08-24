package lang.temper.builtin

import lang.temper.format.OutputToken
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.name.BuiltinName
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.NamedBuiltinFun

abstract class BuiltinFun(
    val builtinName: BuiltinName,
    signatures: List<Signature2>?,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : BuiltinStatelessCallableValue, TokenSerializable, NamedBuiltinFun {
    constructor(
        name: String,
        signature: Signature2?,
        builtinOperatorId: BuiltinOperatorId? = null,
    ) : this(BuiltinName(name), signature?.let { listOf(it) }, builtinOperatorId)
    constructor(
        name: BuiltinName,
        signature: Signature2?,
        builtinOperatorId: BuiltinOperatorId? = null,
    ) : this(name, signature?.let { listOf(it) }, builtinOperatorId)

    override val name: String
        get() = builtinName.builtinKey

    override val sigs = signatures

    protected open val token: OutputToken get() = builtinName.toToken(inOperatorPosition = false)
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(token)
    }

    override fun toString(): String = buildString {
        append(name)
        val sigs = sigs
        if (sigs?.size == 1) {
            val sig = sigs[0]
            append('(')
            for ((i, f) in sig.allValueFormals.withIndex()) {
                if (i != 0) { append(", ") }
                append(f.type)
                when (f.kind) {
                    ValueFormalKind.Required -> {}
                    ValueFormalKind.Optional -> append("=")
                    ValueFormalKind.Rest -> append("...")
                }
            }
            append(')')
        }
    }
}
