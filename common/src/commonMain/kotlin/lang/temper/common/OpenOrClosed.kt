package lang.temper.common

import lang.temper.common.currents.StateValue

enum class OpenOrClosed(override val isTerminal: Boolean) : StateValue {
    Open(false),
    Closed(true),
}
