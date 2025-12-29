package lang.temper.builtin

import lang.temper.value.CallableValue

/**
 * A callable value that does not depend on an environment.
 */
interface PureCallableValue : CallableValue {
    override val isPure: Boolean get() = true
}
