package lang.temper.value

import lang.temper.type.FunctionType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2

fun typeFromSignature(signature: Signature2): FunctionType {
    val valueFormals = signature.requiredAndOptionalValueFormals.map {
        val reifiedType = it.reifiedType
        val type = typeOr(
            reifiedType,
            fallback = WellKnownTypes.anyValueType,
        )
        FunctionType.ValueFormal(symbol = it.symbol, staticType = type, isOptional = it.isOptional)
    }
    val restValuesFormal = signature.restValuesFormal?.let {
        val reifiedType = it.reifiedType
        typeOr(
            reifiedType,
            fallback = WellKnownTypes.anyValueType,
        )
    }
    val returnType = typeOr(
        signature.returnType,
        fallback = TopType,
    )
    return MkType.fnDetails(
        typeFormals = signature.typeFormals,
        valueFormals = valueFormals,
        restValuesFormal = restValuesFormal,
        returnType = returnType,
    )
}

private fun staticTypeFrom(reifiedType: BaseReifiedType?): StaticType? = when (reifiedType) {
    null,
    is SizeStructureExpectation,
    is TreeTypeStructureExpectation,
    is ChildStructureExpectation,
    is TypesStructureExpectation,
    -> null

    is ReifiedType -> reifiedType.type
    is IntersectionStructureExpectation -> {
        val types = reifiedType.structureExpectations.mapNotNull { staticTypeFrom(it) }
        if (types.isEmpty()) {
            null
        } else {
            MkType.and(types)
        }
    }
    TypeStructureExpectation -> WellKnownTypes.typeType
}

private fun typeOr(
    reifiedType: BaseReifiedType?,
    fallback: StaticType,
): StaticType = staticTypeFrom(reifiedType) ?: fallback

fun factorySignatureFromConstructorSignature(constructorSig: Signature2): Signature2? {
    // Constructors are special methods that initialize a value.
    // They do not allocate memory for the value and so do not return the value.
    // Replace the return type with the constructed type.
    //
    // We also need to pop the first valueFormal because it's the `this` value.
    val constructedType = constructorSig.requiredInputTypes.firstOrNull() as? DefinedType
        ?: return null
    val constructorSigReturnType = constructorSig.returnType2
    val returnType = if (
        constructorSigReturnType.definition == WellKnownTypes.resultTypeDefinition &&
        constructorSigReturnType.bindings.isNotEmpty()
    ) {
        MkType2(WellKnownTypes.resultTypeDefinition)
            .actuals(listOf(constructedType) + constructorSigReturnType.bindings.drop(1))
            .get()
    } else {
        constructedType
    }
    val constructedTypeFormals = constructedType.definition.formals
    val valueFormalsSansThis =
        constructorSig.requiredInputTypes.drop(1) // Pop `this` argument

    return Signature2(
        returnType2 = returnType,
        typeFormals = constructedTypeFormals + constructorSig.typeFormals,
        hasThisFormal = false, // We're intentionally leaving `this` off required below.
        requiredInputTypes = valueFormalsSansThis,
        optionalInputTypes = constructorSig.optionalInputTypes,
        restInputsType = constructorSig.restInputsType,
    )
}
