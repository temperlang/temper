package lang.temper.type

import lang.temper.common.asciiUnTitleCase
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.lexer.OperatorType
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.value.TString
import lang.temper.value.extensionSymbol
import lang.temper.value.operatorSymbol
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.unpackPairValue

/**
 * A member of a type.
 *
 * There are two kinds of members:
 *
 * - Members identified by a [dot name][DotMember], like `.foo`.
 *   The dot name text can directly match a member's declared name,
 *   or it can match via [lang.temper.value.overloadSymbol] metadata.
 * - Members associated with [operators][OperatorMember], like infix `+` might correspond
 *   to `.plus` when applied to `MyFancyNumber` type.
 *   This is based on operator specs as defined by [lang.temper.value.operatorSymbol]
 *   metadata.
 */
sealed interface Member : TokenSerializable

/**
 * Matches members declared with the given name text or
 * which have overload metadata referring to that dot name.
 */
data class DotMember(val dotName: Symbol) : Member {
    override fun toString(): String = ".${dotName.text}"

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(BuiltinName(dotName.text).toToken(false))
    }
}

/**
 * Matches members with [lang.temper.value.operatorSymbol] metadata
 * matching the given operator spec.
 */
data class OperatorMember(
    /**
     * <!-- snippet: operator-specifier -->
     * # Operator specifier
     *
     * An operator specifier specifies a kind of operator.
     *
     * The text of the operator surrounded by underscores where operands are allowed.
     * This allows for describing these different kinds of operators, as shown below
     * where `+` is the sample operator.
     *
     * - Infix: `_+_`, the operator punctuation (or word) appears between two operands.
     * - Prefix: `+_`, the operator appears before its operand, like `-` for negation.
     * - Postfix: `_+`, the operator appears after its operand, like `++` for post-increment
     *   or superscript `T` for matrix transpose.
     */
    val operatorSpecifier: String,
) : Member {
    override fun toString(): String {
        val kind = this.kind
        val operator = this.operator
        // Like "infix nym`*`" or "prefix nym`-`"
        return "${kind.name.asciiUnTitleCase()} ${BuiltinName(operator)}"
    }

    override fun renderTo(tokenSink: TokenSink) {
        // Similar to the toString form.
        val kind = this.kind
        val operator = this.operator
        tokenSink.emit(OutputToken(kind.name.asciiUnTitleCase(), OutputTokenType.Word))
        tokenSink.emit(BuiltinName(operator).toToken(false))
    }

    val operator get() = when (kind) {
        OperatorType.Separator,
        OperatorType.Infix,
        -> operatorSpecifier.substring(1, operatorSpecifier.lastIndex)
        OperatorType.Postfix -> operatorSpecifier.drop(1)
        OperatorType.Prefix -> operatorSpecifier.dropLast(1)
        OperatorType.Nullary -> operatorSpecifier // Should not occur
    }

    val kind: OperatorType =
        if (operatorSpecifier.startsWith("_")) {
            if (operatorSpecifier.endsWith("_")) {
                OperatorType.Infix // _+_
            } else {
                OperatorType.Postfix // _+
            }
        } else {
            OperatorType.Prefix // +_
        }

    companion object {
        fun from(operator: String, type: OperatorType): OperatorMember = OperatorMember(
            when (type) {
                OperatorType.Separator,
                OperatorType.Infix,
                -> "_${operator}_"
                OperatorType.Postfix -> "_${operator}"
                OperatorType.Prefix -> "${operator}_"
                OperatorType.Nullary -> operator
            },
        )
    }
}

fun Member.matches(memberShape: VisibleMemberShape, includeOverloads: Boolean): Boolean {
    when (this) {
        is DotMember -> {
            val dotName = this.dotName
            if (memberShape.symbol == dotName) { return true }
            if (includeOverloads) {
                if (memberShape is StaticPropertyShape) {
                    val extensions = memberShape.metadata[staticExtensionSymbol]
                    if (extensions != null) {
                        return extensions.any { v ->
                            val extendedDotName = TString.unpackOrNull(
                                v?.let { unpackPairValue(it) }?.second,
                            )
                            extendedDotName == dotName.text
                        }
                    }
                } else {
                    val extensions = memberShape.metadata[extensionSymbol]
                    if (extensions != null) {
                        return extensions.any { v ->
                            val extendedDotName = TString.unpackOrNull(v)
                            extendedDotName == dotName.text
                        }
                    }
                }
            }
        }
        is OperatorMember -> if (memberShape is MethodShape) {
            val operatorSpecifier = this.operatorSpecifier
            val ops = memberShape.metadata[operatorSymbol]
            if (ops != null) {
                return ops.any { v ->
                    TString.unpackOrNull(v) == operatorSpecifier
                }
            }
        }
    }
    return false
}
