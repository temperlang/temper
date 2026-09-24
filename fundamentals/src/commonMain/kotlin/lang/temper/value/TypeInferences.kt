package lang.temper.value

import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.type.TypeFormal
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.Type2

sealed class TypeInferences : Structured {
    abstract val type: Type2
    abstract val explanations: List<TypeReasonElement>

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type") {
            value(type)
        }
        key("explanation", isDefault = explanations.isEmpty()) {
            arr {
                explanations.forEach {
                    value(it)
                }
            }
        }
        destructureUncommonProperties(this)
    }

    abstract fun destructureUncommonProperties(propertySink: PropertySink)
}

data class BasicTypeInferences(
    override val type: Type2,
    override val explanations: List<TypeReasonElement>,
) : TypeInferences() {
    override fun destructureUncommonProperties(propertySink: PropertySink) = Unit

    override fun toString() = toStringViaBuilder {
        it.append("TypeInferences(")
        it.append(type)
        if (explanations.isNotEmpty()) {
            it.append(", ")
            it.append(explanations)
        }
        it.append(')')
    }
}

data class CallTypeInferences(
    override val type: Type2,
    /** The callee type filtered by applicable cover-function variants */
    val variant: Signature2,
    val bindings2: Map<TypeFormal, Type2>,
    override val explanations: List<TypeReasonElement>,
) : TypeInferences() {
    override fun destructureUncommonProperties(propertySink: PropertySink) = propertySink.run {
        key("variant") { value(variant) }
        key("bindings", isDefault = bindings2.isNotEmpty()) { value(bindings2) }
    }

    override fun toString() = toStringViaBuilder { sb ->
        sb.append("CallTypeInferences(")
        sb.append(type)
        sb.append(", variant=")
        sb.append(variant)
        if (bindings2.isNotEmpty()) {
            sb.append(", bindings={")
            for ((i, e) in bindings2.entries.withIndex()) {
                if (i != 0) { sb.append(", ") }
                sb.append(e.key).append(": ").append(e.value)
            }
            sb.append("}")
        }
        if (explanations.isNotEmpty()) {
            sb.append(", explanations=")
            sb.append(explanations)
        }
        sb.append(')')
    }
}

// TODO: define DeclarationTypeInferences that stores information about whether
// they're multiply assigned, and whether the type is necessary.

val CallTypeInferences?.returnsVoid: Boolean
    get() {
        var returnType = this?.variant?.returnType2
        if (returnType?.definition == WellKnownTypes.resultTypeDefinition) {
            returnType = returnType.bindings.getOrNull(0)
        }
        if (returnType?.definition == WellKnownTypes.neverTypeDefinition) {
            returnType = returnType.bindings.getOrNull(0)
        }
        return returnType?.definition == WellKnownTypes.voidTypeDefinition
    }

val CallTree.returnsVoidClearly: Boolean
    get() {
        // TODO: Take this nullaryNeverCall case away once we've got Never<Void> as a distinct return type.
        return !isNullaryNeverCall(this) && typeInferences.returnsVoid
    }
