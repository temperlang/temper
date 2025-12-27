package lang.temper.be.tmpl

import lang.temper.common.withCapturingConsole
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.value.Helpful
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.docStringSymbol

data class Autodoc(
    override val pos: Position,
    val short: String,
    val full: String,
) : Positioned

private class TmpLFunctionHelpful(
    val functionDoc: Autodoc,
    val parameters: List<Pair<TmpL.Formal, Autodoc?>>,
) : Helpful {
    override fun briefHelp(): String = functionDoc.short

    override fun longHelp(): String = withCapturingConsole { docConsole ->
        docConsole.log(functionDoc.full)
        for ((formal, autodoc) in parameters) {
            docConsole.log("")
            // Indenting docs for params
            docConsole.group("${formal.name}: ${formal.type}") {
                if (autodoc != null) {
                    docConsole.log(autodoc.full)
                }
            }
        }
    }.second
}

data class FnAutodoc(
    val pos: Position,
    val functionDoc: Autodoc?,
    val parameters: List<Pair<TmpL.Formal, Autodoc?>>,
) : OccasionallyHelpful {
    override fun prettyPleaseHelp(): Helpful? = if (functionDoc != null) {
        TmpLFunctionHelpful(functionDoc, parameters)
    } else {
        null
    }
}

fun autodocFor(pos: Position, metadata: List<TmpL.DeclarationMetadata>): Autodoc? {
    val metadatum = metadata
        .firstOrNull { it.key.symbol == docStringSymbol }
        ?.value
        ?: return null
    val ls = when (metadatum) {
        is TmpL.NameData -> return null
        is TmpL.ValueData -> TList.unpackOrNull(metadatum.value)
    } ?: return null
    val (short, full) = ls.map { TString.unpackOrNull(it) }
    return if (short != null && full != null) {
        Autodoc(pos, short, full)
    } else {
        null
    }
}

fun autodocFor(f: TmpL.FunctionLike): FnAutodoc {
    return FnAutodoc(
        f.pos,
        autodocFor(f.pos, f.metadata),
        f.parameters.parameters.map {
            it to autodocFor(it.pos, it.metadata)
        },
    )
}

fun autodocFor(t: TmpL.TypeDeclaration): Autodoc? =
    autodocFor(t.pos.leftEdge, t.metadata)
