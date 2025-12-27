package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.name.NameMaker
import lang.temper.type2.Signature2
import lang.temper.type2.Type2

interface TranslationAssistant {
    val nameMaker: NameMaker

    fun supportCodeReference(
        pos: Position,
        supportCode: SupportCode,
        type: Signature2,
        connectedKey: String? = null,
    ): TmpL.FnReference

    fun translateType(pos: Position, type: Type2): TmpL.Type
}

internal class TranslationAssistantImpl(
    val translator: TmpLTranslator,
) : TranslationAssistant {
    override val nameMaker: NameMaker get() = translator.mergedNameMaker

    override fun supportCodeReference(
        pos: Position,
        supportCode: SupportCode,
        type: Signature2,
        connectedKey: String?,
    ): TmpL.FnReference =
        translator.supportCodeReference(supportCode, connectedKey, pos, type, emptyMap())

    override fun translateType(pos: Position, type: Type2): TmpL.Type =
        translator.translateType(pos, type)
}
