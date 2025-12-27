package lang.temper.be.csharp

import lang.temper.be.tmpl.SignatureAdjustments
import lang.temper.type2.NonNullType

internal sealed interface CSharpSignatureAdjustment : SignatureAdjustments.SignatureAdjustment

internal data class WrappedInOptional(
    val nonNullableType: NonNullType,
) : CSharpSignatureAdjustment
