package lang.temper.format

import lang.temper.common.Formattable

fun interface TokenSerializable : Formattable {
    fun renderTo(tokenSink: TokenSink)

    override fun preformat(): CharSequence =
        toStringViaTokenSink { renderTo(it) }
}
