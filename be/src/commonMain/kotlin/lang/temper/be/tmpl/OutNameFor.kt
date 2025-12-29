package lang.temper.be.tmpl

import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary

internal fun outNameFor(name: ResolvedName): OutName? = when (name) {
    is BuiltinName -> OutName(name.builtinKey, name)
    is SourceName -> OutName(name.rawDiagnostic, name)
    is ExportedName -> OutName(name.baseName.nameText, name)
    is Temporary -> null
}
