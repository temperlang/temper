package lang.temper.be.js

import lang.temper.be.tmpl.wellKnownTypeDefinitionToTypeTag
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.TypeTag

@Suppress(
    "EnumEntryName",
    "EnumNaming", // Mirroring JS identifier names
    "unused", // These are facts about JS.  We don't need to use them all.
)
internal enum class JsTypeOf(val stringValue: String) {
    bigint("bigint"),
    boolean("boolean"),
    function("function"),
    number("number"),
    string("string"),
    symbol("symbol"),
    `object`("object"),
    undefined("undefined"),
    ;

    companion object {

        fun forTypeTag(rt: TypeTag<*>): Pair<JsTypeOf, Boolean>? {
            var t = rt
            if (rt is TClass) {
                val simpler = wellKnownTypeDefinitionToTypeTag[rt.typeShape]
                if (simpler != null) {
                    t = simpler
                }
            }
            return when (t) {
                TBoolean -> boolean to true
                TFloat64, TInt, TInt64 -> number to false // Cannot distinguish based on number
                TString -> string to true
                TFunction -> function to true
                TList, TListBuilder -> null
                TMap, TMapBuilder -> null
                TNull -> null // Cannot distinguish from non-null object
                TStageRange -> null
                TSymbol -> null // or symbol?
                TType -> null
                TVoid -> undefined to true
                TClosureRecord -> null
                TProblem -> null
                is TClass -> null
            }
        }
    }
}
