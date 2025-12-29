package lang.temper.type2

import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink

/**
 * Whether a type has the postfix `?` nullity modifier.
 *
 * A type can be [NonNull] but still admit the special `null` value.
 * For example, it could be a [reference to a type parameter][TypeParamRef]
 * that has an [OrNull] upper bound.
 */
enum class Nullity(val canBeNull: Boolean) : TokenSerializable {
    NonNull(false),
    OrNull(true),
    ;

    override fun renderTo(tokenSink: TokenSink) = when (this) {
        NonNull -> {} // no suffix
        OrNull -> tokenSink.emit(OutToks.postfixQMark)
    }
}
