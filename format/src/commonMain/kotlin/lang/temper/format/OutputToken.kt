package lang.temper.format

/**
 * A token that may be emitted to a [TokenSink].
 * Backend generated code is often serialized to a series of tokens that are formatted and rendered,
 * with position metadata, to produce output files along with source maps relating portions of those
 * files to input files.
 *
 * Intermediate forms and elements like types are often also serialized to pseudocode when debugging
 * the compiler.
 */
sealed interface OutputToken : TokenSerializable {
    val text: String
    val type: OutputTokenType
    val association: TokenAssociation

    fun isSignificant() = type != OutputTokenType.Space && type != OutputTokenType.Comment

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(this)
    }

    operator fun component1() = text
    operator fun component2() = type
    operator fun component3() = association

    companion object {
        fun makeSlashStarComment(body: String) = SimpleOutputToken(
            "/* ${ escapeSlashStars(body) } */",
            OutputTokenType.Comment,
        )

        /** Factory for simple tokens. */
        operator fun invoke(
            text: String,
            type: OutputTokenType,
            association: TokenAssociation = TokenAssociation.Unknown,
        ): OutputToken = SimpleOutputToken(text, type, association)
    }
}

fun escapeSlashStars(body: String) = body.replace("*/", "*\\/")

/** An output token. */
class SimpleOutputToken internal constructor(
    override val text: String,
    override val type: OutputTokenType,
    override val association: TokenAssociation = TokenAssociation.Unknown,
) : OutputToken {
    override fun equals(other: Any?) =
        other is SimpleOutputToken &&
            this.text == other.text &&
            this.type == other.type &&
            this.association == other.association

    override fun hashCode(): Int =
        text.hashCode() + 31 * (type.hashCode() + 31 * association.hashCode())

    override fun toString(): String = "(SimpleOutputToken $text $type $association)"
}

/** OutputTokens produced from names */
interface INameOutputToken : OutputToken {
    val inOperatorPosition: Boolean

    override val association: TokenAssociation get() = TokenAssociation.Unknown

    /**
     * Removes information that clears up ambiguity from names during
     * debugging but which is distracting in error messages.
     */
    fun simplify(): INameOutputToken
}
