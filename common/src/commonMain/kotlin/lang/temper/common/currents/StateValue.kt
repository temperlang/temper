package lang.temper.common.currents

/**
 * The value of a [CurrentStateMachine]'s state variable.
 *
 * These are typically `enum class`es but state machine implementations may
 * craft values that carry state.
 */
interface StateValue {
    /**
     * True iff this value is a terminal one meaning that [Currents] do not need to
     * count state machines in this state preventing resolution of [Currents.done].
     * This must be stable for a value.
     */
    val isTerminal: Boolean

    /** A diagnostic string */
    val name: String
}
