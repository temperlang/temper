package lang.temper.common.structure

@NaturalOrder(["year", "month", "dayOfMonth"])
actual data class SampleDate actual constructor(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
) : Structured, StructuredViaReflection {
    @Suppress("unused") // Used reflectively
    @StructuredProperty([StructureHint.Sufficient, StructureHint.Unnecessary])
    val isoString: String get() =
        "${pad(year, 4)}-${pad(month, 2)}-${pad(dayOfMonth, 2)}"
}
