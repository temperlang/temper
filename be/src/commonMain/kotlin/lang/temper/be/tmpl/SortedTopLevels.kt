package lang.temper.be.tmpl

import lang.temper.common.buildListMultimap
import lang.temper.common.partiallyOrder
import lang.temper.common.putMultiList

internal fun sortedTopLevels(topLevels: List<TmpL.TopLevel>): List<TmpL.TopLevel> {
    // Sort top-levels to ensure some invariants:
    //
    // - type declarations follow any locally declared type they extend
    // - (TODO: implement this)
    //   top level functions follow declarations of types they reference in their signatures
    // - (TODO: implement TmpL for non-in-situ static initializers)
    //   static initializers are initialized *after*
    //   locally declared functions and types that they reference
    //
    // This differs from the top-level sorting we do in the frontend because the frontend
    // has some tricks:
    // - it hoists the declaration of the TypeShape so there's no order conflict with `extends` calls
    // - it teases apart type definitions into a bunch of statements that can be ordered independently
    //
    // The TmpL translator then reassembles those separate statements into CombinedTypeDeclarations
    // which must be atomically ordered.

    val typeDeclarationIndexByTypeName = buildMap {
        for ((index, topLevel) in topLevels.withIndex()) {
            if (topLevel is TmpL.TypeDeclaration) {
                this[topLevel.typeShape.name] = index
            }
        }
    }

    val afterMap = buildListMultimap {
        // Make sure that each type appears after super-types declared in the same module
        for (typeDeclarationIndex in typeDeclarationIndexByTypeName.values) {
            val decl = topLevels[typeDeclarationIndex] as TmpL.TypeDeclaration
            for (superType in decl.superTypes) {
                when (val superTypeName = superType.typeName) {
                    is TmpL.ConnectedToTypeName -> {} // Not locally declared
                    is TmpL.TemperTypeName -> {
                        val superTypeIndex = typeDeclarationIndexByTypeName[superTypeName.typeDefinition.name]
                        if (superTypeIndex != null) {
                            putMultiList(typeDeclarationIndex, superTypeIndex)
                        }
                    }
                }
            }
        }
    }
    return partiallyOrder(topLevels.indices, afterMap) { it }
        .map { topLevels[it] }
}
