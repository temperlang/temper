package lang.temper.value

import lang.temper.name.Symbol
import lang.temper.type.MethodShape

val MethodShape.parameterNameSymbols: ParameterNameSymbols? get() {
    val ls = TList.unpackOrNull(this.metadata[parameterNameSymbolsListSymbol]?.firstOrNull())
        ?: return null
    val split = ls.indexOf(TNull.value)
    if (split < 0) {
        return null
    }
    val required = ls.subList(0, split)
    val optional = ls.subList(split + 1, ls.size)
    return if (required.all { it.typeTag == TSymbol } && optional.all { it.typeTag == TSymbol }) {
        ParameterNameSymbols(
            requiredSymbols = required.map { TSymbol.unpack(it) },
            optionalSymbols = optional.map { TSymbol.unpack(it) },
        )
    } else {
        null
    }
}

data class ParameterNameSymbols(
    val requiredSymbols: List<Symbol>,
    val optionalSymbols: List<Symbol>,
)
