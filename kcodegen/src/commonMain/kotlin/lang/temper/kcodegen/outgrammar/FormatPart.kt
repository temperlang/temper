package lang.temper.kcodegen.outgrammar

internal sealed class FormatPart
internal data class FormatText(val text: String) : FormatPart()
internal data class FormatExpr(val code: KotlinCode) : FormatPart()
internal data class FormatPlaceholder(
    /** If null, the placeholder is not repeated. */
    val joiner: String?,
) : FormatPart()
