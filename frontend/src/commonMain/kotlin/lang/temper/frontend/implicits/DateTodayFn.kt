package lang.temper.frontend.implicits

import kotlinx.datetime.Clock
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.todayIn
import lang.temper.env.InterpMode
import lang.temper.name.ModularName
import lang.temper.name.ResolvedParsedName
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Stayless
import lang.temper.value.TClass
import lang.temper.value.TInt
import lang.temper.value.Value

private val utc = FixedOffsetTimeZone(UtcOffset.ZERO)

/** Support for `Date.today` from std/temporal */
class DateTodayFn(val sig: Signature2) : CallableValue, Stayless {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        if (cb.stage != Stage.Run) { return NotYet }
        val today = Clock.System.todayIn(utc)
        val returnType = sig.returnType2 as DefinedNonNullType
        val typeShape = returnType.definition
        val typeTag = TClass(typeShape)

        val properties = mutableMapOf<ModularName, Value<*>>()
        for (propertyShape in typeShape.properties) {
            if (propertyShape.abstractness == Abstractness.Concrete) {
                val propertyName = propertyShape.name as ModularName
                val propertyParsedName = (propertyName as ResolvedParsedName).baseName
                val value = when (propertyParsedName.nameText) {
                    "year" -> Value(today.year, TInt)
                    "month" -> Value(today.monthNumber, TInt)
                    "day" -> Value(today.dayOfMonth, TInt)
                    else -> error(propertyName)
                }
                properties[propertyName] = value
            }
        }

        val instance = InstancePropertyRecord(properties)
        return Value(instance, typeTag)
    }

    override val sigs: List<Signature2> = listOf(sig)
}
