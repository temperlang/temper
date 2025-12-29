package lang.temper.be.tmpl

import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Type2
import lang.temper.value.TBoolean
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.TypeTag

val wellKnownTypeDefinitionToTypeTag = mapOf<TypeShape, TypeTag<*>?>(
    WellKnownTypes.anyValueTypeDefinition to null,
    WellKnownTypes.booleanTypeDefinition to TBoolean,
    WellKnownTypes.closureRecordTypeDefinition to TClosureRecord,
    WellKnownTypes.float64TypeDefinition to TFloat64,
    WellKnownTypes.functionTypeDefinition to TFunction,
    WellKnownTypes.intTypeDefinition to TInt,
    WellKnownTypes.listTypeDefinition to TList,
    WellKnownTypes.listBuilderTypeDefinition to TListBuilder,
    WellKnownTypes.nullTypeDefinition to TNull,
    WellKnownTypes.problemTypeDefinition to TProblem,
    WellKnownTypes.stageRangeTypeDefinition to TStageRange,
    WellKnownTypes.stringTypeDefinition to TString,
    WellKnownTypes.symbolTypeDefinition to TSymbol,
    WellKnownTypes.typeTypeDefinition to TType,
    WellKnownTypes.voidTypeDefinition to TVoid,
)

val typeTagToStaticType: Map<TypeTag<*>, Type2> = mapOf(
    TBoolean to WellKnownTypes.booleanType2,
    TFloat64 to WellKnownTypes.float64Type2,
    TInt to WellKnownTypes.intType2,
    TProblem to MkType2(WellKnownTypes.problemTypeDefinition).get(),
    TStageRange to MkType2(WellKnownTypes.stageRangeTypeDefinition).get(),
    TString to WellKnownTypes.stringType2,
    TSymbol to MkType2(WellKnownTypes.symbolTypeDefinition).get(),
    TType to MkType2(WellKnownTypes.typeTypeDefinition).get(),
    TVoid to WellKnownTypes.voidType2,
)
