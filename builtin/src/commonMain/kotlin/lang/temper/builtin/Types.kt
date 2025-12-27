package lang.temper.builtin

import lang.temper.name.ResolvedName
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.withNullity
import lang.temper.value.ReifiedType
import lang.temper.value.TBoolean
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.listedTypeBuiltinName

object Types {
    val anyValue: ReifiedType
    val anyValueOrNull: ReifiedType
    val boolean: ReifiedType
    val closureRecord: ReifiedType
    val empty: ReifiedType
    val float64: ReifiedType
    val function: ReifiedType
    val int: ReifiedType
    val int64: ReifiedType
    val list: ReifiedType
    val listed: ReifiedType
    val problem: ReifiedType
    val stageRange: ReifiedType
    val string: ReifiedType
    val symbol: ReifiedType
    val type: ReifiedType
    val void: ReifiedType

    val bubble: ReifiedType

    val vAnyValue: Value<ReifiedType>
    val vBoolean: Value<ReifiedType>
    val vClosureRecord: Value<ReifiedType>
    val vEmpty: Value<ReifiedType>
    val vFloat64: Value<ReifiedType>
    val vFunction: Value<ReifiedType>
    val vInt: Value<ReifiedType>
    val vInt64: Value<ReifiedType>
    val vList: Value<ReifiedType>
    val vListed: Value<ReifiedType>
    val vProblem: Value<ReifiedType>
    val vStageRange: Value<ReifiedType>
    val vString: Value<ReifiedType>
    val vSymbol: Value<ReifiedType>
    val vType: Value<ReifiedType>
    val vVoid: Value<ReifiedType>

    val vBubble: Value<ReifiedType>

    val typesAsValues: List<Value<ReifiedType>>

    init {
        fun referenceToTypeTag(name: ResolvedName): ReifiedType {
            val typeShape: TypeShape = WellKnownTypes.withName(name)!!
            return ReifiedType(
                MkType2(typeShape).get(),
                hasExplicitActuals = false,
            )
        }
        fun referenceToTypeTag(rt: TypeTag<*>) = referenceToTypeTag(rt.name)

        // Simple type tag backed reified types
        anyValue = ReifiedType(WellKnownTypes.anyValueType2)
        anyValueOrNull = ReifiedType(WellKnownTypes.anyValueOrNullType2)
        boolean = referenceToTypeTag(TBoolean)
        closureRecord = referenceToTypeTag(TClosureRecord)
        empty = ReifiedType(WellKnownTypes.emptyType2)
        float64 = referenceToTypeTag(TFloat64)
        function = referenceToTypeTag(TFunction)
        int = referenceToTypeTag(TInt)
        int64 = referenceToTypeTag(TInt64)
        list = referenceToTypeTag(TList)
        listed = referenceToTypeTag(listedTypeBuiltinName)
        problem = referenceToTypeTag(TProblem)
        stageRange = referenceToTypeTag(TStageRange)
        string = referenceToTypeTag(TString)
        symbol = referenceToTypeTag(TSymbol)
        type = referenceToTypeTag(TType)
        void = referenceToTypeTag(TVoid)

        // Non-tag backed reified types
        bubble = ReifiedType(WellKnownTypes.bubbleType2)

        // Values wrapping reified types
        vAnyValue = Value(anyValue)
        vBoolean = Value(boolean)
        vClosureRecord = Value(closureRecord)
        vEmpty = Value(empty)
        vFloat64 = Value(float64)
        vFunction = Value(function)
        vInt = Value(int)
        vInt64 = Value(int64)
        vList = Value(list)
        vListed = Value(listed)
        vProblem = Value(problem)
        vStageRange = Value(stageRange)
        vString = Value(string)
        vSymbol = Value(symbol)
        vType = Value(type)
        vVoid = Value(void)

        vBubble = Value(bubble)

        typesAsValues = listOf(
            vBoolean,
            vFloat64,
            vFunction,
            vInt,
            vInt64,
            vList,
            vListed,
            vProblem,
            vStageRange,
            vString,
            vSymbol,
            vType,
            vVoid,

            vBubble,
        )
    }

    fun nullableOf(t: ReifiedType): ReifiedType = ReifiedType(t.type2.withNullity(Nullity.OrNull))
}
