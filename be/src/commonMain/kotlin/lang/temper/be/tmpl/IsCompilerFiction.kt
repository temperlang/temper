package lang.temper.be.tmpl

import lang.temper.type.WellKnownTypes
import lang.temper.value.DeclTree
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typePlaceholderSymbol

internal fun isCompilerFiction(t: DeclTree): Boolean {
    val parts = t.parts
    if (parts != null) {
        val type = parts.name.typeInferences?.type
        // type aliases, stores of types that do not themselves participate
        // in defining a type, are compiler fictions and should not be translated
        if (type == WellKnownTypes.typeType) {
            val metadataKeys = parts.metadataSymbolMultimap.keys
            if (
                typeDeclSymbol !in metadataKeys &&
                typePlaceholderSymbol !in metadataKeys &&
                typeFormalSymbol !in metadataKeys
            ) {
                return true
            }
        }
    }
    return false
}
