package lang.temper.type

import lang.temper.name.ModularName
import lang.temper.name.ResolvedParsedName
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.TClass
import lang.temper.value.TypeTag
import lang.temper.value.Value
import java.util.Collections

private val typeTagToContentField =
    Collections.synchronizedMap(mutableMapOf<TypeTag<*>, Pair<PropertyShape, TClass>>())

private val typeNameToShapeMap by lazy {
    buildMap {
        for (typeShape in WellKnownTypes.allWellKnown) {
            val builtinKey = (typeShape.name as? ResolvedParsedName)?.baseName?.builtinKey
                ?: continue
            this[builtinKey] = typeShape
        }
    }
}

fun promoteSimpleValue(value: Value<*>): Value<InstancePropertyRecord>? {
    val typeTag = value.typeTag
    var (field, tClass) = typeTagToContentField[typeTag]
        ?: run {
            val typeName = typeTag.name.builtinKey
            val typeShape = typeNameToShapeMap[typeName] ?: return@promoteSimpleValue null
            val field = typeShape.properties.firstOrNull { it.abstractness == Abstractness.Concrete }
            if (field?.name !is ModularName) {
                // Field with ParsedName has not been finalized
                return@promoteSimpleValue null
            }
            val fieldAndClassTypeTag = field to TClass(field.enclosingType)
            typeTagToContentField[typeTag] = fieldAndClassTypeTag
            fieldAndClassTypeTag
        }
    return Value(
        InstancePropertyRecord(mutableMapOf((field.name as ModularName) to value)),
        tClass,
    )
}
