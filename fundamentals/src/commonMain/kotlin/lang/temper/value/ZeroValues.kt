package lang.temper.value

import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNewOrNull
import lang.temper.type2.withNullity

/**
 * For a type, lets us pick a value that can be used to initialize a declaration of that type.
 *
 * Not all types have zero values, most do not, but
 */
object ZeroValues {
    operator fun get(type: Type2): ZeroValueRecord {
        if (type.nullity == Nullity.OrNull) {
            return ZeroValueRecord(
                TNull.value,
                needsNullAdjustment = false,
                unadjustedType = type,
                adjustedType = type,
            )
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
                WellKnownTypes.int64TypeDefinition -> Value(0L, TInt64)
                WellKnownTypes.float64TypeDefinition -> Value(0.0, TFloat64)
                WellKnownTypes.emptyTypeDefinition -> emptyValue
                WellKnownTypes.booleanTypeDefinition -> TBoolean.valueFalse
                WellKnownTypes.voidTypeDefinition -> void
                WellKnownTypes.stringTypeDefinition -> Value("", TString)
                // No List entry: a constant `List` value carries no element
                // type, and the java, rust, cpp, csharp and lua translators
                // cannot write one. A `List` takes the null path below.
                else -> null
            }
            if (v != null) {
                return ZeroValueRecord(
                    v,
                    needsNullAdjustment = false,
                    unadjustedType = type,
                    adjustedType = type,
                )
            }
        }

        return ZeroValueRecord(
            TNull.value,
            needsNullAdjustment = true,
            unadjustedType = type,
            adjustedType = type.withNullity(Nullity.OrNull),
        )
    }
}

data class ZeroValueRecord(
    val value: Value<*>,
    val needsNullAdjustment: Boolean,
    val unadjustedType: Type2,
    val adjustedType: Type2,
)

/** The value of the type [WellKnownTypes.emptyType] */
val emptyValue = Value(
    InstancePropertyRecord(mutableMapOf()),
    TClass(WellKnownTypes.emptyTypeDefinition),
)
