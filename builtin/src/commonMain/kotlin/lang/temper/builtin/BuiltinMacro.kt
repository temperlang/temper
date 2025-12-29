package lang.temper.builtin

import lang.temper.type2.AnySignature
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.NamedBuiltinFun

internal abstract class BuiltinMacro(
    final override val name: String,
    signature: AnySignature?,
    override val nameIsKeyword: Boolean = false,
) : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val sigs = signature?.let { listOf(it) }
}
