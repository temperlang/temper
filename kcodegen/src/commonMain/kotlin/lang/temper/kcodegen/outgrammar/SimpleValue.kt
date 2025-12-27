package lang.temper.kcodegen.outgrammar

internal sealed class SimpleValue
internal data class SimpleBoolean(val value: Boolean) : SimpleValue() {
    override fun toString() = "`$value`"
}
internal data class EnumReference(var enumName: Id?, val memberName: Id) : SimpleValue() {
    override fun toString() = "$enumName.$memberName"
}

/** Not null or empty */
internal object Truthy : SimpleValue() {
    override fun toString() = "Truthy"
}
