package lang.temper.be.py

import lang.temper.log.Position

enum class PyConstant {
    Ellipsis,
    None,
    False,
    True,
    NotImplemented,
    ;

    val text: String
        get() = when (this) {
            Ellipsis -> "..."
            else -> this.name
        }

    fun at(pos: Position) = Py.Constant(pos, this)

    companion object {
        fun bool(v: Boolean) = if (v) {
            True
        } else {
            False
        }

        /**
         * An abstraction for the value indicating that an argument hasn't been set.
         * Easier to track down usage this way.
         */
        val Unset = None
    }
}
