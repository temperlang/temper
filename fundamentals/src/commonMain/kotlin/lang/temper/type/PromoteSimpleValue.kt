package lang.temper.type

import lang.temper.name.ModularName
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.TClass
import lang.temper.value.TypeTag
import lang.temper.value.Value
import java.util.Collections

private val typeTagToContentField =
    Collections.synchronizedMap(mutableMapOf<TypeTag<*>, Pair<PropertyShape, TClass>>())

private val typeNameToShapeMap = lazy {
    buildMap {
        WellKnownTypes.allWellKnown.forEach {
            val builtinKey = it.name.builtinKey
            if (builtinKey != null) {
                this[builtinKey] = it
            }
        }
    }
}

fun promoteSimpleValue(value: Value<*>): Value<InstancePropertyRecord>? {
    val typeTag = value.typeTag
    var (field, tClass) = typeTagToContentField[typeTag]
        ?: run {
            val typeName = typeTag.name.builtinKey
            val typeShape = typeNameToShapeMap.value[typeName]
            val field = typeShape?.properties?.firstOrNull { it.abstractness == Abstractness.Concrete }
                ?: return@promoteSimpleValue null
            (field to TClass(field.enclosingType)).also {
                typeTagToContentField[typeTag] = it
            }
        }
    return Value(
        InstancePropertyRecord(mutableMapOf((field.name as ModularName) to value)),
        tClass,
    )
}

