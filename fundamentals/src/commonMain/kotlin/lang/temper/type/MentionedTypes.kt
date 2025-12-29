package lang.temper.type

import lang.temper.name.ResolvedName
import lang.temper.type2.Type2

fun addTypeNamesMentionedTo(typeActual: TypeActual, out: MutableSet<ResolvedName>) {
    when (typeActual) {
        Wildcard -> Unit
        TopType -> Unit
        BubbleType -> Unit
        is NominalType -> {
            out.add(typeActual.definition.name)
            typeActual.bindings.forEach { addTypeNamesMentionedTo(it, out) }
        }
        is FunctionType -> {
            typeActual.valueFormals.forEach { addTypeNamesMentionedTo(it.type, out) }
            typeActual.restValuesFormal?.let { addTypeNamesMentionedTo(it, out) }
            addTypeNamesMentionedTo(typeActual.returnType, out)
        }
        InvalidType -> Unit
        is OrType -> typeActual.members.forEach { addTypeNamesMentionedTo(it, out) }
        is AndType -> typeActual.members.forEach { addTypeNamesMentionedTo(it, out) }
        is InfiniBinding -> Unit
    }
}

fun addTypeNamesMentionedTo(typeActual: Type2, out: MutableSet<ResolvedName>) {
    out.add(typeActual.definition.name)
    typeActual.bindings.forEach { addTypeNamesMentionedTo(it, out) }
}
