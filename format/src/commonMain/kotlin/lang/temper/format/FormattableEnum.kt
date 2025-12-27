package lang.temper.format

import lang.temper.common.asciiUnTitleCase

/** Allows using an enum value, e.g. an out-grammar definition, in a code formatting template. */
interface FormattableEnum : FormattableTree {
    override val formatElementCount: Int get() = 0
    override fun formatElement(index: Int): IndexableFormattableTreeElement =
        throw IndexOutOfBoundsException(index)
    override fun isCurlyBracketBlock(): Boolean = false

    override val codeFormattingTemplate: CodeFormattingTemplate?
        get() = null
    override val operatorDefinition: OperatorDefinition?
        get() = null

    override fun renderTo(tokenSink: TokenSink) {
        val name = (this as Enum<*>).name.asciiUnTitleCase()
        tokenSink.emit(OutputToken(name, OutputTokenType.Word))
    }
}
