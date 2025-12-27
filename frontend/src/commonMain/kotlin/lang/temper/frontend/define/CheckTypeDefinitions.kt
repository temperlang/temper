package lang.temper.frontend.define

import lang.temper.common.Log
import lang.temper.common.putMultiSet
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.type.Abstractness
import lang.temper.type.MutableTypeShape
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.value.sealedTypeSymbol

internal fun checkTypeDefinitions(
    convertedTypeInfo: ConvertedTypeInfo,
    logSink: LogSink,
) {
    val subTypesGroupedBySealedSuper = mutableMapOf<MutableTypeShape, MutableSet<TypeShape>>()
    // Check type definition rules.
    for ((typeShape) in convertedTypeInfo.typesAndEdgePastLastMember) {
        checkConcreteUnsealed(typeShape, logSink)
        checkSealedExtension(typeShape, logSink, subTypesGroupedBySealedSuper)
    }
    // Track info and check more rules.
    for ((sup, subs) in subTypesGroupedBySealedSuper) {
        // Track sealed subtype options here because that's convenient.
        sup.sealedSubTypes = subs.toList()
        // Validate more now that it's convenient.
        checkSubTypeParams(sup, logSink)
    }
}

/** Sealed types must be abstract since concrete types are not extensible. */
private fun checkConcreteUnsealed(typeShape: TypeShape, logSink: LogSink) {
    if (typeShape.abstractness != Abstractness.Abstract) {
        val sealedMetadata = typeShape.metadata.getEdges(sealedTypeSymbol).firstOrNull()
        if (sealedMetadata != null) {
            logSink.log(
                Log.Error,
                MessageTemplate.CannotSealClass,
                sealedMetadata.target.pos,
                emptyList(),
            )
        }
    }
}

/** Check that sealed types are only extended by same-module declarations. */
private fun checkSealedExtension(
    typeShape: TypeShape,
    logSink: LogSink,
    subTypesGroupedBySealedSuper: MutableMap<MutableTypeShape, MutableSet<TypeShape>>,
) {
    val subTypeStay = typeShape.stayLeaf!!
    val subTypeOrigin = subTypeStay.document
    val illegalSuperTypes = mutableSetOf<NominalType>()
    for (superType in typeShape.superTypes) {
        val superTypeShape = superType.definition as TypeShape
        val sealedMetadata = superTypeShape.metadata.getEdges(sealedTypeSymbol).firstOrNull()
        if (sealedMetadata != null) {
            val superTypeOrigin = superTypeShape.stayLeaf?.document ?: continue
            if (subTypeOrigin != superTypeOrigin) {
                logSink.log(
                    Log.Error,
                    MessageTemplate.CannotExtendSealed,
                    subTypeStay.pos,
                    listOf(
                        superType,
                        sealedMetadata.target.pos,
                        typeShape.name,
                    ),
                )
                illegalSuperTypes.add(superType)
            } else {
                // Only track for current module.
                subTypesGroupedBySealedSuper.putMultiSet(
                    superTypeShape as MutableTypeShape,
                    typeShape,
                )
            }
        }
    }
    if (illegalSuperTypes.isEmpty()) {
        (typeShape as MutableTypeShape).superTypes.removeAll(illegalSuperTypes)
    }
}

/** Call only for types with sealed subtypes. Check that sealed subtypes don't introduce type parameters. */
private fun checkSubTypeParams(sup: MutableTypeShape, logSink: LogSink) {
    for (sub in sup.sealedSubTypes!!) {
        val supBindings = sub.superTypes.find { it.definition == sup }!!.bindings
        params@ for (param in sub.typeParameters) {
            val paramDef = param.definition
            val boundUp = supBindings.any { (it as? NominalType)?.definition == paramDef }
            if (!boundUp) {
                logSink.log(
                    Log.Error,
                    MessageTemplate.CannotIntroduceParamInSealedSubtype,
                    paramDef.pos,
                    listOf(sub.name),
                )
            }
        }
    }
}
