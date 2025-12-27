package lang.temper.be.tmpl

import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withType
import lang.temper.value.TString
import lang.temper.value.connectedSymbol

internal class TypeTranslator(
    private val supportNetwork: SupportNetwork,
    private val genre: Genre,
    private val untranslatableType: (Position, String) -> TmpL.GarbageType,
) {
    internal fun translateType(
        pos: Position,
        type: Type2,
    ): TmpL.Type {
        val tt = translateTypeIgnoreNullity(pos, type)
        return when (type.nullity) {
            Nullity.NonNull -> tt
            Nullity.OrNull -> typeUnion(
                pos,
                buildList {
                    add(tt)
                    add(translateRegularType(pos, WellKnownTypes.nullTypeDefinition, emptyList()))
                },
            )
        }
    }

    private fun translateTypeIgnoreNullity(pos: Position, type: Type2): TmpL.Type = withType(
        type,
        fallback = { translateRegularTypeIgnoringNullity(pos, it) },
        result = { passType, _, _ ->
            typeUnion(
                pos,
                listOf(
                    translateType(pos, passType),
                    TmpL.BubbleType(pos),
                ),
            )
        },
        never = { _, _, _ -> TmpL.NeverType(pos) },
        fn = { _, sig, t ->
            when (supportNetwork.functionTypeStrategy) {
                FunctionTypeStrategy.ToFunctionType -> translateFunctionType(pos, sig)
                FunctionTypeStrategy.ToFunctionalInterface -> translateRegularTypeIgnoringNullity(pos, t)
            }
        },
        malformed = { _, t -> untranslatableType(pos, "$t") },
    )

    private fun translateFunctionType(pos: Position, sig: Signature2): TmpL.FunctionType {
        val typeParameters = TmpL.TypeParameters(
            pos = pos,
            typeParameters = sig.typeFormals.map { typeFormal ->
                TmpL.TypeFormal(
                    pos = pos,
                    name = TmpL.Id(pos, typeFormal.name),
                    upperBounds = typeFormal.superTypes.map {
                        translateRegularTypeIgnoringNullity(pos, hackMapOldStyleToNew(it))
                    },
                    definition = typeFormal,
                )
            },
        )
        val valueFormals = TmpL.ValueFormalList(
            pos = pos,
            formals = buildList {
                for ((vfs, isOptional) in listOf(
                    sig.requiredInputTypes to false,
                    sig.optionalInputTypes to true,
                )) {
                    vfs.mapTo(this) { valueFormal ->
                        TmpL.ValueFormal(
                            pos = pos,
                            name = null,
                            type = translateType(pos, valueFormal).aType,
                            isOptional = isOptional,
                        )
                    }
                }
            },
            rest = sig.restInputsType?.let { translateType(pos, it).aType },
        )
        val returnType = translateType(pos, sig.returnType2)

        return TmpL.FunctionType(
            pos = pos,
            typeParameters = TmpL.ATypeParameters(typeParameters),
            valueFormals = valueFormals,
            returnType = returnType.aType,
        )
    }

    private fun translateRegularTypeIgnoringNullity(pos: Position, type: Type2): TmpL.NominalType {
        val (definition, bindings) = type
        return translateRegularType(pos, definition, bindings)
    }

    private fun translateRegularType(
        pos: Position,
        definition: TypeDefinition,
        bindings: List<Type2>,
    ): TmpL.NominalType {
        val connectedKey = when (definition) {
            is TypeFormal -> null
            is TypeShape -> TString.unpackOrNull(definition.metadata[connectedSymbol]?.firstOrNull())
        }
        if (connectedKey != null) {
            check(definition is TypeShape) // connectedKey only non-null when definition is a TypeShape
            val type = MkType2(definition).actuals(bindings).get()
            val translation = supportNetwork.translatedConnectedType(
                pos = pos,
                connectedKey = connectedKey,
                genre = genre,
                temperType = type,
            )
            if (translation != null) {
                val (name, actuals) = translation
                return TmpL.NominalType(
                    pos,
                    TmpL.ConnectedToTypeName(pos, definition, name),
                    actuals.map { translateTypeActual(pos, it) },
                    connectsFrom = type,
                )
            }
        }

        return TmpL.NominalType(
            pos,
            TmpL.TemperTypeName(pos, definition),
            bindings.map { translateTypeActual(pos, it) },
            connectsFrom = null,
        )
    }

    internal fun translateTypeName(pos: Position, type: Type2, followConnected: Boolean): TmpL.TypeName {
        val definition = type.definition
        val connectedKey = if (followConnected) {
            when (definition) {
                is TypeFormal -> null
                is TypeShape -> TString.unpackOrNull(definition.metadata[connectedSymbol]?.firstOrNull())
            }
        } else {
            null
        }
        if (connectedKey != null) {
            val translation = supportNetwork.translatedConnectedType(
                pos = pos,
                connectedKey = connectedKey,
                genre = genre,
                temperType = type,
            )
            if (translation != null) {
                check(definition is TypeShape) // connectedKey only non-null when definition is a TypeShape
                val (name) = translation
                return TmpL.ConnectedToTypeName(pos, definition, name)
            }
        }

        return TmpL.TemperTypeName(pos, type.definition)
    }

    fun translateTypeActual(pos: Position, typeActual: Type2): TmpL.AType =
        translateType(pos, typeActual).aType
}

private fun typeUnion(pos: Position, ts: List<TmpL.Type>): TmpL.TypeUnion {
    val flatTs = if (ts.any { it is TmpL.TypeUnion }) {
        buildList {
            for (t in ts) {
                if (t is TmpL.TypeUnion) {
                    val types = t.types.toList()
                    t.types = emptyList()
                    addAll(types)
                } else {
                    add(t)
                }
            }
        }
    } else {
        ts
    }
    return TmpL.TypeUnion(pos, flatTs)
}
