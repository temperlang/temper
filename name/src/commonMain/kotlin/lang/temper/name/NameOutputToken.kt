package lang.temper.name

import lang.temper.format.INameOutputToken
import lang.temper.format.OutputTokenType
import lang.temper.log.SimplifiesInLogMessage

class NameOutputToken(
    val name: Name,
    override val inOperatorPosition: Boolean,
    override val text: String,
    override val type: OutputTokenType,
) : INameOutputToken {
    override fun equals(other: Any?) =
        other is NameOutputToken &&
            this.name == other.name &&
            this.inOperatorPosition == other.inOperatorPosition &&
            this.text == other.text &&
            this.type == other.type

    override fun hashCode(): Int = name.hashCode() + 31 * inOperatorPosition.hashCode()

    override fun toString(): String =
        "(NameOutputToken $name inOperatorPosition=$inOperatorPosition)"

    /**
     * Finish simulating a data class. Hard to make an actual data class with
     * operator componentN overrides in supertype. And we're already manually
     * doing other data class things here already, anyway.
     */
    fun copy(
        name: Name? = null,
        inOperatorPosition: Boolean? = null,
        text: String? = null,
        type: OutputTokenType? = null,
    ) = NameOutputToken(
        name = name ?: this.name,
        inOperatorPosition = inOperatorPosition ?: this.inOperatorPosition,
        text = text ?: this.text,
        type = type ?: this.type,
    )

    override fun simplify() =
        when (val simplerName = (name as? SimplifiesInLogMessage)?.simplerLoggable) {
            is Name -> simplerName.toToken(inOperatorPosition = inOperatorPosition)
            else -> this
        }
}
