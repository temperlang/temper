package lang.temper.common.structure

actual data class SampleDate actual constructor(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
) : Structured {
    private val isoString: String get() =
        "${pad(year, 4)}-${pad(month, 2)}-${pad(dayOfMonth, 2)}"

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("year", Hints.n) { value(year) }
        key("month", Hints.n) { value(month) }
        key("dayOfMonth", Hints.n) { value(dayOfMonth) }
        key("isoString", Hints.su) { value(isoString) }
    }
}
