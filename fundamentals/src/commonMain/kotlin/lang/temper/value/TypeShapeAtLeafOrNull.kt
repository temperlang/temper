package lang.temper.value

import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type2.NonNullType

/**
 * Get a type shape from an expression that has resolved into a value.
 */
val PartialResult.typeShapeAtLeafOrNull: TypeShape? get() =
    typeDefinitionAtLeafOrNull as? TypeShape

/**
 * Get a type shape from an expression that has resolved into a value stored in the AST.
 */
val Tree.typeShapeAtLeafOrNull: TypeShape? get() = typeDefinitionAtLeafOrNull as? TypeShape

/**
 * Get a type definition from an expression that has resolved into a value.
 */
val PartialResult.typeDefinitionAtLeafOrNull: TypeDefinition? get() =
    if (this is Value<*>) {
        val reifiedType = TType.unpackOrNull(this)
        (reifiedType?.type2 as? NonNullType)?.definition
    } else {
        null
    }

/**
 * Get a type definition from an expression that has resolved into a value stored in the AST.
 */
val Tree.typeDefinitionAtLeafOrNull: TypeDefinition? get() =
    (this.reifiedTypeContained?.type2 as? NonNullType)?.definition
