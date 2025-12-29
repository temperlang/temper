package lang.temper.value

import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.type.FunctionType
import lang.temper.type.StaticType
import lang.temper.type.TypeActual
import lang.temper.type.TypeFormal
import lang.temper.type.isVoidLike

sealed class TypeInferences : Structured {
    abstract val type: StaticType
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
    override val type: StaticType,
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
    override val type: StaticType,
    /** The callee type filtered by applicable cover-function variants */
    val variant: StaticType,
    val bindings2: Map<TypeFormal, TypeActual>,
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
        return (this?.variant as? FunctionType)?.returnType?.isVoidLike ?: false
    }

val CallTree.returnsVoidClearly: Boolean
    get() {
        // TODO: Make this first hack away once we've got Never<Void> as a distinct return type
        if (isNullaryNeverCall(this)) { return false }
        return typeInferences.returnsVoid
    }
