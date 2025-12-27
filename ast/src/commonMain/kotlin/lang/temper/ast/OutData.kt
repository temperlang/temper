package lang.temper.ast

import lang.temper.name.DashedIdentifier

interface OutData<DATA : OutData<DATA>> : OutFormattable<DATA> {
    val sourceLibrary: DashedIdentifier
}
