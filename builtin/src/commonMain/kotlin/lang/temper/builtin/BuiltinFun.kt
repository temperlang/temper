package lang.temper.builtin

import lang.temper.format.OutputToken
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.name.BuiltinName
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.NamedBuiltinFun

abstract class BuiltinFun(
    val builtinName: BuiltinName,
    signatures: List<Signature2>?,
) : BuiltinStatelessCallableValue, TokenSerializable, NamedBuiltinFun {
    constructor(name: String, signature: Signature2?) : this(BuiltinName(name), signature?.let { listOf(it) })
    constructor(name: BuiltinName, signature: Signature2?) : this(name, signature?.let { listOf(it) })

    override val name: String
        get() = builtinName.builtinKey

    override val sigs = signatures

    protected open val token: OutputToken get() = builtinName.toToken(inOperatorPosition = false)
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(token)
    }
}
