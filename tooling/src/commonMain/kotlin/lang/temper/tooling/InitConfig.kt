package lang.temper.tooling

import lang.temper.name.DashedIdentifier
import lang.temper.name.identifiers.IdentStyle

/** Either [dashName] or [title] is required. Both can also be supplied. */
fun initConfigContent(title: String? = null, dashName: String? = null): String {
    val dashNameSure = dashName ?: IdentStyle.Human.convertTo(IdentStyle.Dash, title!!)
    val titleSure = title ?: dashName!!.dashToTitle()
    return """
        |# $titleSure
        |
        |This file is used for library configuration.
        |
        |The `name` export defines the library name:
        |
        |    export let name = "$dashNameSure";
        |
        |By default, the current directory is imported as a module. It can import
        |other module directories. See Temper documentation for additional config
        |options.
        |
    """.trimMargin()
}

/** Converts to a loose dashed identifier, loose in the sense that more than one dash is allowed. */
fun String.chimericToDash(): String {
    // Convert to dashed identifier, but also handle camels and underscores.
    val dashed = IdentStyle.Camel.convertTo(IdentStyle.Dash, this).replace('_', '-')
    return DashedIdentifier.from(dashed)?.text ?: "untitled"
}

/** Convert dash-case to Title Case. */
fun String.dashToTitle() =
    IdentStyle.Pascal.convertTo(IdentStyle.Human, IdentStyle.Dash.convertTo(IdentStyle.Pascal, this))
