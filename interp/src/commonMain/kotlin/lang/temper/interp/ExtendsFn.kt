package lang.temper.interp

import lang.temper.builtin.asReifiedType
import lang.temper.builtin.asReifiedTypeListOr
import lang.temper.builtin.asReifiedTypeOr
import lang.temper.common.Log
import lang.temper.common.PassFail
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.AndType
import lang.temper.type.MutableTypeFormal
import lang.temper.type.MutableTypeShape
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.NonNullType
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Fail
import lang.temper.value.IntersectionStructureExpectation
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.TypesStructureExpectation
import lang.temper.value.Value
import lang.temper.value.extendsBuiltinName
import lang.temper.value.void

/**
 * As part of generating [MutableTypeShape]s, records a super-type relationship.
 * Calls to this function are generated as part of desugaring `class` and `interface` macro calls.
 *
 * <!-- snippet: builtin/extends : # `extends` keyword -->
 * # *SubType* `extends` *SuperType*
 *
 * The *extends* keyword expresses that the thing to the left is a subtype of
 * the thing to the right.
 *
 * It's used in two different contexts.
 *
 * First, when defining types.
 *
 * ```temper null
 * interface SuperType {}
 * class SubType extends SuperType {}
 *
 * // An instance of a subtype may be assigned to a variable
 * // whose type is the supertype.
 * let x: SuperType = new SubType();
 * ```
 *
 * Second, when declaring a type variable.
 * The below defines an upper bound: the type variable may only bind to types
 * that are subtypes of the upper bound.
 *
 * ```temper null
 * let f<T extends Listed<String>>(x: T): T { x }
 * ```
 *
 * When more than one super-type is extended, use
 * [type intersection][snippet/type/intersection-fn] syntax (`&`).
 *
 * ```temper null
 * interface I {}
 * interface J {}
 *
 * class C extends I & J {}
 *
 * let f<T extends I & J>(x: T): T { x }
 * // T extends I and T extends J
 * ```
 */
object ExtendsFn : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val name: String = extendsBuiltinName.builtinKey
    override val sigs: List<MacroSignature> = run {
        val typeType = IntersectionStructureExpectation(
            listOf(
                TreeTypeStructureExpectation(setOf(LeafTreeType.Value)),
                TypesStructureExpectation,
            ),
        )
        listOf(
            MacroSignature(
                returnType = null,
                requiredValueFormals = listOf(
                    MacroValueFormal(null, typeType, ValueFormalKind.Required),
                    MacroValueFormal(null, typeType, ValueFormalKind.Required),
                ),
                restValuesFormal = null,
            ),
        )
    }
    override val callMayFailPerSe: Boolean get() = false
    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val stage = macroEnv.stage
        if (stage < Stage.SyntaxMacro) {
            // Quiescent until later
            return NotYet
        }
        return if (updateTypeDefinition(macroEnv, interpMode).passed) {
            // After definition, time to evaporate
            if (stage > Stage.Define && macroEnv.call != null) {
                macroEnv.replaceMacroCallWith { V(void) }
            }
            NotYet
        } else {
            when {
                stage > Stage.Define -> { // Probably not going to recover.
                    val fail = macroEnv.fail(MessageTemplate.MalformedTypeDeclaration)
                    if (macroEnv.call != null) {
                        macroEnv.replaceMacroCallWithErrorNode()
                    }
                    fail
                }
                else -> Fail
            }
        }
    }
}

val vExtendsFn = Value(ExtendsFn)

private fun updateTypeDefinition(env: MacroEnvironment, interpMode: InterpMode): PassFail {
    val args = env.args
    if (args.size != 2) {
        env.explain(MessageTemplate.ArityMismatch, values = listOf(2))
        return PassFail.Fail
    }
    if (args.key(0) != null || args.key(1) != null) {
        env.explain(MessageTemplate.NoSignatureMatches)
        return PassFail.Fail
    }
    val subTypeValue = args.result(0, interpMode)
    val superTypeValue = args.result(1, interpMode)
    val subType = asReifiedTypeOr(subTypeValue, env) { return PassFail.Fail }.type2
    val superTypeList = asReifiedType(superTypeValue)?.let { listOf(it) }
        ?: asReifiedTypeListOr(superTypeValue, env) { return PassFail.Fail }
    val superTypes = mutableListOf<NominalType>()
    var superTypesOk = true
    fun addSuperType(type: StaticType) {
        when (type) {
            is NominalType -> superTypes.add(type)
            is AndType -> type.members.forEach { addSuperType(it) }
            else -> {
                superTypesOk = false
                env.explain(MessageTemplate.CannotExtend, values = listOf(type))
            }
        }
    }
    for (superType in superTypeList) {
        addSuperType(superType.type)
    }
    if (!superTypesOk) {
        return PassFail.Fail
    }
    if (subType !is NonNullType) { // Treating as bare reference to type shape ID
        env.explain(
            MessageTemplate.ExpectedValueOfType,
            values = listOf("named Type", subType),
        )
        return PassFail.Fail
    }

    val subTypeDefinition = subType.definition
    val superTypesList = when (subTypeDefinition) {
        is MutableTypeShape -> subTypeDefinition.superTypes
        is MutableTypeFormal -> subTypeDefinition.upperBounds
    }

    // This may run during multiple stages; don't accumulate garbage super types.
    val present = superTypesList.toMutableSet()
    val concreteSupers = mutableListOf<NominalType>()
    for (oneSuperType in superTypes) {
        if (oneSuperType !in present) {
            superTypesList.add(oneSuperType)
            present.add(oneSuperType)
            // Handle here when we know it's a newly added supertype.
            if ((oneSuperType.definition as? TypeShape)?.abstractness == Abstractness.Concrete) {
                concreteSupers.add(oneSuperType)
            }
        }
    }

    // Validate against concrete supers.
    if (concreteSupers.isNotEmpty() && subTypeDefinition !is TypeFormal) {
        val concreteSupersText = concreteSupers.joinToString { it.definition.name.displayName }
        // This pos is less than ideal for `&` types, but doing better isn't easy at the moment.
        env.logSink.log(
            Log.Error,
            MessageTemplate.CannotExtendConcrete,
            args.pos(1),
            listOf(concreteSupersText),
        )
    }

    return PassFail.Pass
}
