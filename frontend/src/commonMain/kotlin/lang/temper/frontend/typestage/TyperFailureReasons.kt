package lang.temper.frontend.typestage

import lang.temper.common.Log
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.type.StaticType
import lang.temper.type.TypeShape
import lang.temper.type2.TypeReason
import lang.temper.value.AbstractTypeReasonElement
import lang.temper.value.CallTree
import lang.temper.value.TypeReasonElement
import lang.temper.value.toLispy

internal class BecauseNameUndeclared(
    override val pos: Position,
    val undeclaredName: ResolvedName,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseNameUndeclared"
    override val formatString get() = "No declaration for %s"
    override val templateFillers: List<TokenSerializable>
        get() = listOf(undeclaredName.toToken(inOperatorPosition = false))
    override val level: Log.Level get() = Log.Error
}

internal class BecauseMalformedFunctionDefinition(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseMalformedFunctionDefinition"
    override val formatString get() = "Malformed function definition"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Info
}

internal class BecauseMalformedTypeReference(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseMalformedTypeReference"
    override val formatString get() = "Malformed type reference"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Info
}

internal class BecauseMalformedTypeFormal(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseMalformedTypeFormal"
    override val formatString get() = "Malformed type formal"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Info
}

internal class BecauseMalformedParameterDeclaration(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseMalformedParameterDeclaration"
    override val formatString get() = "Function parameter declaration is malformed"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Info
}

internal class BecauseMalformedSpecialCall(
    override val pos: Position,
    val call: CallTree,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseMalformedSpecialCall"
    override val formatString get() = "Special function call to %s is malformed"
    override val templateFillers: List<TokenSerializable> get() = listOf(
        OutputToken(call.toLispy(), OutputTokenType.OtherValue),
    )
    override val level: Log.Level get() = Log.Error
}

internal class BecauseUnresolvedFunctionSignaturePart(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseUnresolvedFunctionSignaturePart"
    override val formatString get() = "Could not resolve part of function signature to a type"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Info
}

internal class BecauseReturnTypeRequired(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseReturnTypeRequired"
    override val formatString get() = "Explicit return type required"
    override val templateFillers: List<TokenSerializable> get() = emptyList()
    override val level: Log.Level get() = Log.Error
}

internal class BecauseIllegalAssignment(
    override val pos: Position,
    val leftType: StaticType,
    val rightType: StaticType,
) : TypeReasonElement {
    override fun logTo(logSink: LogSink) {
        logSink.log(
            level = Log.Error,
            template = MessageTemplate.IllegalAssignment,
            pos = pos,
            values = listOf(leftType, rightType),
        )
    }
}

internal class BecauseTypeInfoMissingForName(
    override val pos: Position,
    val nameMissingInfo: ResolvedName,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseTypeInfoMissingForName"
    override val level: Log.Level get() = Log.Error
    override val formatString get() = "Missing type info for %s"
    override val templateFillers: List<TokenSerializable>
        get() = listOf(nameMissingInfo.toToken(inOperatorPosition = false))
}

internal abstract class BecauseNoMemberAccessible(
    override val pos: Position,
    private val memberSymbol: Symbol,
    private val definingTypes: Set<TypeShape>,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseCannotAccessMembers"
    override val level: Log.Level get() = Log.Error
    override val templateFillers: List<TokenSerializable>
        get() = listOf(
            ParsedName(memberSymbol.text).toToken(inOperatorPosition = false),
            if (definingTypes.isNotEmpty()) {
                definingTypes.joinToTokenSerializable(OutToks.bar) {
                    it.name.toToken(inOperatorPosition = false)
                }
            } else {
                OutputToken("MissingType", OutputTokenType.Word)
            },
        )
}

internal class BecauseCannotAccessMembers(
    pos: Position,
    memberSymbol: Symbol,
    definingTypes: Set<TypeShape>,
) : BecauseNoMemberAccessible(pos, memberSymbol, definingTypes) {
    override val formatString get() = "Member %s defined in %s not publicly accessible"
}

internal class BecauseNoMemberCompatible(
    pos: Position,
    memberSymbol: Symbol,
    definingTypes: Set<TypeShape>,
) : BecauseNoMemberAccessible(pos, memberSymbol, definingTypes) {
    override val formatString get() = MessageTemplate.IncompatibleUsage.formatString
}

internal class BecauseNoSuchMember(
    pos: Position,
    memberSymbol: Symbol,
    definingTypes: Set<TypeShape>,
) : BecauseNoMemberAccessible(pos, memberSymbol, definingTypes) {
    override val formatString get() = "No member %s in %s"
}

internal class BecauseUnresolvedTypeReference(
    override val pos: Position,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseUnresolvedTypeReference"
    override val level: Log.Level get() = Log.Error
    override val formatString get() = MessageTemplate.ExpectedValueOfType.formatString
    override val templateFillers: List<TokenSerializable>
        get() = listOf(
            OutputToken("Type", OutputTokenType.Word),
        )
}

internal class BecauseExpectedNamedType(
    override val pos: Position,
    val type: StaticType,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseExpectedNamedType"
    override val level: Log.Level get() = Log.Error
    override val formatString get() = MessageTemplate.ExpectedNominalType.formatString
    override val templateFillers: List<TokenSerializable> get() = listOf(type)
}

internal class BecauseExpectedTypeShape(
    override val pos: Position,
    val type: StaticType,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseExpectedTypeShape"
    override val level: Log.Level get() = Log.Error
    override val formatString get() = MessageTemplate.ExpectedTypeShape.formatString
    override val templateFillers: List<TokenSerializable> get() = listOf(type)
}

internal class BecauseArityMismatch(
    override val pos: Position,
    val wantedArity: Int,
) : AbstractTypeReasonElement() {
    override val name get() = "BecauseArityMismatch"
    override val formatString get() = MessageTemplate.ArityMismatch.formatString
    override val templateFillers: List<TokenSerializable> get() = listOf(
        OutputToken("$wantedArity", OutputTokenType.NumericValue),
    )
    override val level: Log.Level get() = Log.Error
}

internal fun <T> (Iterable<T>).joinToTokenSerializable(
    sep: TokenSerializable = OutToks.comma,
    f: (T) -> TokenSerializable,
): TokenSerializable {
    val iterable = this
    return TokenSerializable { tokenSink ->
        iterable.forEachIndexed { index, el ->
            if (index != 0) {
                sep.renderTo(tokenSink)
            }
            f(el).renderTo(tokenSink)
        }
    }
}

internal fun becauseRedundantArgument(p: Positioned) = TypeReason(
    LogEntry(
        Log.Error,
        MessageTemplate.RedundantArgument,
        p.pos,
        listOf(),
    ),
)
