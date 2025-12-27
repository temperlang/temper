package lang.temper.common.currents

/**
 * That which may prevent a [Currents] from completing and which
 * may be described as part of [Currents.debug].
 */
interface DebuggableCurrent {
    /** Short diagnostic text that may be dumped to the console */
    val description: String
}
