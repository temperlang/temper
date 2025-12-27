package lang.temper.type2

/** Support for generating non-colliding [SolverVar names][SolverVar.name] */
interface SolverVarNamer {
    fun unusedTypeVar(hint: String) = TypeVar(unusedVarName(hint))
    fun unusedSimpleVar(hint: String) = SimpleVar(unusedVarName(hint))
    fun unusedVarName(hint: String): String

    companion object {
        fun new(): SolverVarNamer = object : AbstractSolverVarNamer() {}
    }
}

abstract class AbstractSolverVarNamer : SolverVarNamer {
    private var tVarCounter = 0

    override fun unusedVarName(hint: String): String = buildString {
        append(VAR_PREFIX_CHAR)
        append(hint)
        if (hint.isNotEmpty() && hint.last() in '0'..'9') {
            // Avoid the counter number colliding with another number,
            // possibly leading to duplicates as in:
            // - unusedVarName("x1") returns "x10" when tVarCounter is 0
            // - unusedVarName("x")  returns "x10" when tVarCounter is 10
            append('#')
            // With this, the former returns "x1#0"
        }
        append(tVarCounter++)
    }
}
