package lang.temper.be.java

import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.name.identifiers.IdentStyle

internal const val CAPTURE_SUFFIX = $$"$capture"
internal const val REST_SUFFIX = $$"$varargs"
internal const val IGNORED_PREFIX = "ignored$"
internal const val LOCAL_VAR_PREFIX = "local$"
internal const val LOCAL_CLASS_PREFIX = "Local_"

/** Convert a dashed identifier into a list of snake case identifiers for use in package identifiers. */
internal fun DashedIdentifier.unpack(): List<String> =
    this.text.split('.').map { IdentStyle.Dash.convertTo(IdentStyle.Snake, it).safeIdentifier() }

/** To track names in a map, unwrap exported names. */
internal fun ResolvedName.baseName(): TemperName = when (this) {
    is ExportedName -> baseName
    is SourceName -> baseName
    else -> this
}

/** Apply a simple set of rules to extract a name's text; uniqued by Temper. */
internal fun ResolvedName.distinctText() = when (this) {
    is BuiltinName -> builtinKey
    is SourceName -> rawDiagnostic
    is ExportedName -> baseName.nameText
    is Temporary -> "${nameHint}_$uid"
}

/** Apply a simple set of rules to extract a name's text; but not necessarily unique. */
internal fun ResolvedName.simpleText(): String = when (this) {
    is BuiltinName -> builtinKey
    is SourceName -> baseName.nameText
    is ExportedName -> baseName.nameText
    is Temporary -> nameHint
}

/** Apply a simple set of rules to extract a name's text and ensure the identifier is safe for Java. */
internal fun ResolvedName.distinctSafeText() = distinctText().safeIdentifier()

/** Apply a simple set of rules to extract a name's text and ensure the identifier is safe for Java. */
internal fun ResolvedName.simpleSafeText() = simpleText().safeIdentifier()
