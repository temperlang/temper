package lang.temper.type

import lang.temper.name.ResolvedName
import lang.temper.type2.Type2

fun addTypeNamesMentionedTo(type: StaticType, out: MutableSet<ResolvedName>) {
    when (type) {
        TopType -> Unit
        BubbleType -> Unit
        is NominalType -> {
            out.add(type.definition.name)
            type.bindings.forEach { addTypeNamesMentionedTo(it, out) }
        }
        is FunctionType -> {
            type.valueFormals.forEach { addTypeNamesMentionedTo(it.type, out) }
            addTypeNamesMentionedTo(type.returnType, out)
        }
        InvalidType -> Unit
        is OrType -> type.members.forEach { addTypeNamesMentionedTo(it, out) }
        is AndType -> type.members.forEach { addTypeNamesMentionedTo(it, out) }
    }
}

fun addTypeNamesMentionedTo(type: Type2, out: MutableSet<ResolvedName>) {
    out.add(type.definition.name)
    type.bindings.forEach { addTypeNamesMentionedTo(it, out) }
}
