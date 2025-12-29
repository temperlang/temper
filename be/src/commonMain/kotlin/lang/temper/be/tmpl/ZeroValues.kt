package lang.temper.be.tmpl

import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNewOrNull
import lang.temper.type2.withNullity
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.void

/**
 * For a type, lets us pick a value that can be used to initialize a declaration of that type.
 */
internal object ZeroValues {
    operator fun get(type: Type2): ZeroValueRecord {
        if (type.nullity == Nullity.OrNull) {
            return ZeroValueRecord(TNull.value, needsNullAdjustment = false, type)
        }

        if (type is DefinedNonNullType) {
            val def = type.definition
            if (def is TypeFormal) {
                val ub = hackMapOldStyleToNewOrNull(def.superTypes.firstOrNull())
                if (ub != null) {
                    return get(ub)
                }
            }
            val v = when (def) {
                WellKnownTypes.intTypeDefinition -> Value(0, TInt)
                WellKnownTypes.booleanTypeDefinition -> TBoolean.valueFalse
                WellKnownTypes.voidTypeDefinition -> void
                WellKnownTypes.stringTypeDefinition -> Value("", TString)
                else -> null
            }
            if (v != null) {
                return ZeroValueRecord(v, needsNullAdjustment = false, adjustedType = type)
            }
        }

        return ZeroValueRecord(TNull.value, needsNullAdjustment = true, type.withNullity(Nullity.OrNull))
    }
}

internal data class ZeroValueRecord(
    val value: Value<*>,
    val needsNullAdjustment: Boolean,
    val adjustedType: Type2,
)
