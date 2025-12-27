package lang.temper.value

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.name.BuiltinName

/** A function with a well-known name which may be used to render this in diagnostics. */
interface NamedBuiltinFun : StaylessMacroValue, TokenSerializable {
    val name: String

    val builtinOperatorId: BuiltinOperatorId? get() = null

    val nameIsKeyword: Boolean get() = false

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(
            if (nameIsKeyword) {
                OutputToken(name, OutputTokenType.Word)
            } else {
                BuiltinName(name).toToken(inOperatorPosition = false)
            },
        )
    }
}
