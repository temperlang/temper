package lang.temper.be.cpp03

import lang.temper.be.tmpl.TmpL
import lang.temper.common.utf8ByteLength
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ResolvedParsedName
import lang.temper.name.identifiers.IdentStyle

fun String.dashToSnake() = IdentStyle.Dash.convertTo(IdentStyle.Snake, this)

fun String.utf8Length(): Int = run {
    var sum = 0
    for (code in codePoints()) {
        sum += utf8ByteLength(code)
    }
    sum
}

internal fun TmpL.ModuleLevelDeclaration.isConsole(): Boolean {
    (type.ot as? TmpL.NominalType)?.typeName?.sourceDefinition?.let { typeDefinition ->
        if (typeDefinition.sourceLocation === ImplicitsCodeLocation) {
            when ((typeDefinition.name as? ResolvedParsedName)?.baseName?.nameText) {
                "Console", "GlobalConsole" -> return true
                else -> {}
            }
        }
    }
    return false
}

internal const val TEMPER_CORE_NAMESPACE = "temper::core"
