package lang.temper.interp

import lang.temper.name.ModularName
import lang.temper.type.Abstractness
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TListed
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TMapped
import lang.temper.value.TString
import lang.temper.value.Value
import java.beans.Introspector

/**
 * Takes a source Kotlin/Java object and a reference Temper interpreter object, and converts
 * the Java object to an interpreter object based on the reference template.
 *
 * Additional requirements:
 * - All class instances use BlockEnvironment.
 * - All values from the source are compatible and supported.
 *
 * Use this only in situations that are known to be compatible.
 */
fun packValue(source: Any, ref: Value<*>, cb: InterpreterCallback): Value<*> {
    val content = when (source) {
        is List<*>, is Map<*, *> -> extractContent(ref)
        is Value<*> -> return source
        else -> ref
    }
    return when (val type = content.typeTag) {
        is TClass -> {
            // We're in `commonMain` but still have access to Java information here for some reason.
            // TODO(tjp, java): Provide instead a mapping from property names to expected KClasses?
            val sourceInfo = Introspector.getBeanInfo(source.javaClass)
            val klass = content.typeTag as TClass
            val refInstance = klass.unpack(content)
            val refProperties = refInstance.properties
            val result = InstancePropertyRecord(refProperties.toMutableMap())
            for (property in klass.typeShape.properties) {
                if (property.abstractness != Abstractness.Concrete) {
                    continue
                }
                val propertyName = property.name as ModularName
                val propertyNameText = propertyName.propertyNameText()
                val sourceProperty = sourceInfo.propertyDescriptors.first { sourceProperty ->
                    sourceProperty.name == propertyNameText
                }
                val sourceValue = sourceProperty.readMethod.invoke(source)
                val refValue = refProperties.getValue(propertyName)
                result.properties[propertyName] = packValue(sourceValue, refValue, cb)
            }
            Value(result, type)
        }

        is TListed<*> -> {
            // Lists require one template member to work from.
            val refItem = type.unpack(content)[0]
            val result = (source as List<*>).map { packValue(it!!, refItem, cb) }
            when (type) {
                is TList -> Value(result, type)
                is TListBuilder -> Value(result.toMutableList(), type)
            }
        }

        is TMapped<*> -> {
            // Mapped require one template entry to work from.
            val refEntry = type.unpack(content).entries.first()
            val result = (source as Map<*, *>).map { (key, value) ->
                packValue(key!!, refEntry.key, cb) to packValue(value!!, refEntry.value, cb)
            }.toMap()
            when (type) {
                is TMap -> Value(result, type)
                is TMapBuilder -> Value(LinkedHashMap(result), type)
            }
        }

        is TBoolean -> TBoolean.value(source as Boolean)
        is TFloat64 -> Value((source as Number).toDouble(), type)
        is TInt -> Value((source as Number).toInt(), type)
        is TString -> Value(source as String, type)
        else -> TODO("More auto pack into type tags: $content")
    }
}

/** Pull out buried "content" property if we have that instead of a direct value. */
private fun extractContent(ref: Value<*>): Value<*> {
    val stateVector = (ref.stateVector as? InstancePropertyRecord) ?: return ref
    stateVector.properties.size == 1 || return ref
    val entry = stateVector.properties.entries.first()
    entry.key.propertyNameText() == "content" || return ref
    return entry.value
}

private fun ModularName.propertyNameText() = toSymbol()!!.text
