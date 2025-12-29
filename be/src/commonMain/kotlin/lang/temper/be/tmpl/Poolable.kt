package lang.temper.be.tmpl

import lang.temper.type2.Descriptor
import lang.temper.value.Value

/** That which can be associated with a name in a [ConstantPool]. */
sealed interface Poolable

internal data class PooledSupportCode(
    val supportCode: SupportCode,
    /**
     * A differentiator which allows each TmpL.Module in a module set to have its own local name
     * for the same external reference.  This avoids unnecessary auto-importing and any ensuing
     * dependency cycles.
     */
    val scopeIndex: Int,
) : Poolable

internal data class PooledValue(
    val value: Value<*>,
    val type: Descriptor,
) : Poolable
