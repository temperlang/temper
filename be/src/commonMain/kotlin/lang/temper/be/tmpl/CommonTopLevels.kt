package lang.temper.be.tmpl

import lang.temper.name.ResolvedName

/**
 * Scans the AST to build a map of optional [support code][SupportCode]
 * to names for the same.
 */
fun TmpL.Module.findCommonTopLevels(): CommonTopLevels {
    val scMap = mutableMapOf<SupportCode, ResolvedName>()
    for (topLevel in topLevels) {
        when (topLevel) {
            is TmpL.SupportCodeDeclaration -> {
                val sc = topLevel.init.supportCode
                check(sc !in scMap) // ConstantPool should preserve uniqueness per module set
                scMap[sc] = topLevel.name.name
            }
            else -> Unit
        }
    }
    return CommonTopLevels(scMap.toMap())
}

data class CommonTopLevels(
    val supportCodes: Map<SupportCode, ResolvedName>,
)
