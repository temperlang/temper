package lang.temper.be.cpp

import lang.temper.be.tmpl.TmpL
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.PropertyShape
import lang.temper.type.TypeShape
import lang.temper.type2.Type2
import lang.temper.value.connectedSymbol

/**
 * Which fields of a module's classes each of its functions, methods, and local functions
 * may assign while it runs, itself or through what it calls.
 *
 * A caller passing a `var` field to a `const &` parameter copies it unless the callee can't
 * assign that field ([mayAssign]). The analysis is conservative: a call to code it can't
 * see, such as another module's function, a function value, an interface's method, or
 * connected code, may assign any field, and so may a generator or an async function. That
 * is sound because a class's fields are assigned only by its own code, all in this module:
 * other code reaches them only by calling it.
 */
internal class FieldWrites(module: TmpL.Module) {
    private class Facts {
        val assigns = mutableSetOf<ResolvedName>()
        val calls = mutableSetOf<ResolvedName>()

        /** Whether it runs code the analysis can't see. */
        var opaque = false
    }

    /** Facts about each function-like by name, before following calls. */
    private val direct = mutableMapOf<ResolvedName, Facts>()

    /** This module's classes, whose methods a call reaches without dynamic dispatch. */
    private val classes = mutableMapOf<TemperName, TmpL.TypeDeclaration>()

    /** Each callee asked about to the fields it may assign, or null if it may run unseen code. */
    private val closed = mutableMapOf<ResolvedName, Set<ResolvedName>?>()

    init {
        for (top in module.topLevels) {
            if (top is TmpL.TypeDeclaration && top.kind == TmpL.TypeDeclarationKind.Class &&
                top.typeShape.abstractness == Abstractness.Concrete &&
                top.metadata.none { it.key.symbol == connectedSymbol }
            ) {
                classes[top.typeShape.name] = top
            }
        }
        for (top in module.topLevels) {
            when (top) {
                is TmpL.ModuleFunctionDeclaration -> addFunction(top)
                is TmpL.TypeDeclaration -> for (member in top.members) {
                    when (member) {
                        is TmpL.FunctionDeclarationOrMethod -> addFunction(member)
                        else -> walk(member, Facts())
                    }
                }
                // Not callable, but may declare local functions.
                else -> walk(top, Facts())
            }
        }
    }

    /**
     * Whether a call to [callee], from [calleeOf], could assign [field] on any instance
     * before it returns. True for a callee the analysis doesn't know.
     */
    fun mayAssign(callee: ResolvedName?, field: ResolvedName): Boolean {
        callee ?: return true
        val assigns = transitiveAssigns(callee) ?: return true
        return field in assigns
    }

    /**
     * The function a call to [fn] runs, when known without dynamic dispatch: one of this
     * module's functions, local functions, or methods of its classes, or else something
     * [mayAssign] treats as unknown.
     */
    fun calleeOf(fn: TmpL.Callable): ResolvedName? = when (fn) {
        is TmpL.FnReference -> fn.id.name
        is TmpL.MethodReference -> {
            val method = fn.method
            when {
                method == null || fn.subject is TmpL.SuperSubject -> null
                method.enclosingType.name !in classes -> null
                else -> method.name as? ResolvedName
            }
        }
        is TmpL.ConstructorReference -> classes[fn.typeShape.name]?.members
            ?.filterIsInstance<TmpL.Constructor>()
            ?.singleOrNull()?.name?.name
        else -> null
    }

    private fun transitiveAssigns(start: ResolvedName): Set<ResolvedName>? {
        if (start in closed) {
            return closed[start]
        }
        val assigns = mutableSetOf<ResolvedName>()
        val seen = mutableSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val facts = direct[pending.removeFirst()]
            if (facts == null || facts.opaque) {
                closed[start] = null
                return null
            }
            assigns.addAll(facts.assigns)
            for (callee in facts.calls) {
                if (seen.add(callee)) {
                    pending.add(callee)
                }
            }
        }
        closed[start] = assigns
        return assigns
    }

    private fun addFunction(fn: TmpL.FunctionDeclarationOrMethod) {
        val facts = Facts()
        val body = fn.body
        // The accessors of a backed property only read or assign its field.
        val field = ((fn as? TmpL.GetterOrSetter)?.propertyShape)
            ?.takeIf { it.abstractness == Abstractness.Concrete }?.name
        when {
            field != null -> {
                if (fn is TmpL.Setter) {
                    when (field) {
                        is ResolvedName -> facts.assigns.add(field)
                        else -> facts.opaque = true
                    }
                }
                body?.let { walk(it, Facts()) }
            }
            body == null || fn.mayYield || fn.metadata.any { it.key.symbol == connectedSymbol } -> {
                facts.opaque = true
                // Still walk, for its local functions.
                body?.let { walk(it, Facts()) }
            }
            else -> walk(body, facts)
        }
        direct[fn.name.name] = facts
    }

    private fun walk(tree: TmpL.Tree, facts: Facts) {
        when (tree) {
            is TmpL.LocalFunctionDeclaration -> {
                // A closure runs when called, not where it's declared.
                addFunction(tree)
                return
            }
            is TmpL.SetBackedProperty -> when (val field = backedField(tree.left)) {
                null -> facts.opaque = true
                else -> facts.assigns.add(field)
            }
            is TmpL.SetAbstractProperty -> {
                val property = classProperty(tree.left.subject, tree.left.property)
                val setter = property?.setter as? ResolvedName
                val field = property?.name as? ResolvedName
                when {
                    setter != null -> facts.calls.add(setter)
                    field != null && property.abstractness == Abstractness.Concrete -> facts.assigns.add(field)
                    else -> facts.opaque = true
                }
            }
            is TmpL.GetAbstractProperty -> {
                val property = classProperty(tree.subject, tree.property)
                val getter = property?.getter as? ResolvedName
                when {
                    property == null -> facts.opaque = true
                    getter != null -> facts.calls.add(getter)
                    property.abstractness != Abstractness.Concrete -> facts.opaque = true
                    // Else the generated getter only returns the field.
                }
            }
            is TmpL.CallExpression -> when (val fn = tree.fn) {
                is TmpL.InlineSupportCodeWrapper -> {
                    val support = fn.supportCode as? CppInlineSupportCode
                    val handedCode = tree.parameters.any { mayCarryCode(it.passType) }
                    if (support == null || (support.effect == SupportEffect.Acts && handedCode)) {
                        facts.opaque = true
                    }
                }
                is TmpL.FnReference, is TmpL.MethodReference, is TmpL.ConstructorReference ->
                    when (val callee = calleeOf(fn)) {
                        null -> facts.opaque = true
                        else -> facts.calls.add(callee)
                    }
                else -> facts.opaque = true
            }
            is TmpL.AwaitExpression, is TmpL.YieldStatement, is TmpL.Garbage -> facts.opaque = true
            else -> {}
        }
        for (kid in tree.children) {
            walk(kid, facts)
        }
    }

    /** The field a backed property assignment sets. */
    private fun backedField(left: TmpL.PropertyLValue): ResolvedName? = when (val id = left.property) {
        is TmpL.InternalPropertyId -> id.name.name
        is TmpL.ExternalPropertyId -> classProperty(left.subject, id)?.name as? ResolvedName
    }

    /** A property of one of this module's classes, through a subject of that class's type. */
    private fun classProperty(subject: TmpL.Subject, id: TmpL.PropertyId): PropertyShape? {
        val shape = (subject as? TmpL.Expression)?.passType?.definition as? TypeShape ?: return null
        if (shape.name !in classes) {
            return null
        }
        return when (id) {
            is TmpL.ExternalPropertyId -> shape.properties.firstOrNull { it.symbol.text == id.name.dotNameText }
            is TmpL.InternalPropertyId -> shape.properties.firstOrNull { it.name == id.name.name }
        }
    }

    /** Whether a value of [type] could run code when used: a function, or any interface's. */
    private fun mayCarryCode(type: Type2): Boolean {
        val definition = type.definition
        return definition !is TypeShape || definition.abstractness != Abstractness.Concrete
    }
}
