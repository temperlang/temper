package lang.temper.value

import lang.temper.name.ModularName
import lang.temper.type.Abstractness
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2

fun makePairValue(a: Value<*>, b: Value<*>): Value<*> {
    val pairTypeDefinition = WellKnownTypes.pairTypeDefinition
    val tClass = TClass(pairTypeDefinition)
    val (aProperty, bProperty) = pairTypeDefinition.properties
    val instancePropertyMap = mutableMapOf(
        (aProperty.name as ModularName) to a,
        (bProperty.name as ModularName) to b,
    )
    val instanceRecord = InstancePropertyRecord(instancePropertyMap)
    return Value(instanceRecord, tClass)
}

private val pairTClass = lazy {
    val pairTypeDefinition = WellKnownTypes.pairTypeDefinition
    TClass(pairTypeDefinition)
}

fun unpackPairValue(v: Value<*>): Pair<Value<*>, Value<*>>? {
    val p = pairTClass.value.unpackOrNull(v) ?: return null

    val propertyIterator = p.properties.values.iterator()
    if (propertyIterator.hasNext()) {
        val first = propertyIterator.next()
        if (propertyIterator.hasNext()) {
            return first to propertyIterator.next()
        }
    }
    return null
}

/**
 * Best effort to deduce a type for a value.
 */
fun typeForValue(value: Value<*>): StaticType? {
    val type = when (val rt = value.typeTag) {
        TBoolean -> WellKnownTypes.booleanType
        TFloat64 -> WellKnownTypes.float64Type
        TInt -> WellKnownTypes.intType
        TInt64 -> WellKnownTypes.int64Type
        TProblem -> MkType.nominal(WellKnownTypes.problemTypeDefinition)
        TString -> WellKnownTypes.stringType
        TType -> WellKnownTypes.typeType
        TVoid -> WellKnownTypes.voidType
        TFunction -> typeForFunctionValue(TFunction.unpack(value))
        TList -> null // TODO
        TListBuilder -> null // TODO
        TMap -> null // TODO
        TMapBuilder -> null // TODO
        TNull -> null
        TStageRange -> null // TODO
        TSymbol -> WellKnownTypes.symbolType
        TClosureRecord -> null // TODO
        is TClass -> {
            // If the type has no generic parameters, we're good.
            val typeDef = rt.typeShape
            if (
                typeDef.abstractness == Abstractness.Concrete &&
                typeDef.typeParameters.isEmpty()
            ) {
                MkType.nominal(typeDef)
            } else if (typeDef == WellKnownTypes.pairTypeDefinition) {
                var firstT: StaticType? = null
                var secondT: StaticType? = null
                unpackPairValue(value)?.let {
                    firstT = typeForValue(it.first)
                    secondT = typeForValue(it.second)
                }
                if (firstT != null && secondT != null) {
                    MkType.nominal(typeDef, listOf(firstT, secondT))
                } else {
                    null
                }
            } else {
                null
            }
            // TODO: Make sure that
            //     let x = new C<A, B>()
            // gets the type from the constructor stored somewhere even when constructor
            // collapses to a value early.
            // Is this a problem in practice?
        }
    }
    return type
}

fun typeForFunctionValue(f: MacroValue): StaticType {
    val signatures = f.sigs
    return if (signatures.isNullOrEmpty()) {
        WellKnownTypes.functionType
    } else {
        MkType.and(
            signatures.map { signature ->
                (signature as? Signature2)?.let {
                    typeFromSignature(it)
                } ?: InvalidType
            },
        )
    }
}
