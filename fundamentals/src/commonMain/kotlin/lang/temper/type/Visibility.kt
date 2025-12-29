package lang.temper.type

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.name.Symbol
import lang.temper.value.privateSymbol
import lang.temper.value.protectedSymbol
import lang.temper.value.publicSymbol

/**
 * How widely part of a declaration is exposed.
 *
 * When *a* > *b*, *a* is visible in more contexts than *b*.
 */
enum class Visibility(val keyword: String) : Structured {
    /** Not visible externally */
    Private(privateSymbol.text),

    /** Visible to subtypes, but not externally otherwise. */
    Protected(protectedSymbol.text),

    /** Visible externally */
    Public(publicSymbol.text),

    ;

    companion object {
        fun fromSymbol(symbol: Symbol?): Visibility? = when (symbol) {
            publicSymbol -> Public
            privateSymbol -> Private
            protectedSymbol -> Protected
            else -> null
        }
    }

    override fun destructure(structureSink: StructureSink) {
        structureSink.value(keyword)
    }

    fun toSymbol() = Symbol(keyword)
}
