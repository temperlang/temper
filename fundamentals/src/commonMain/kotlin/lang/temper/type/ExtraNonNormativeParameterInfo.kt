package lang.temper.type

import lang.temper.name.Symbol
import lang.temper.value.FnParts
import lang.temper.value.symbolContained
import lang.temper.value.wordSymbol

data class ExtraNonNormativeParameterInfo(
    val names: List<Symbol?>,
) {
    constructor(funParts: FnParts) : this(
        funParts.formals.map {
            it.parts?.metadataSymbolMap?.get(wordSymbol)?.symbolContained
        },
    )
}
