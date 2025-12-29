package lang.temper.value

/**
 * That which may be [Helpful] but may not.
 */
interface OccasionallyHelpful {
    /** Ask for help.  Null if help is not forthcoming. */
    fun prettyPleaseHelp(): Helpful?
}

/**
 * Can be introspected over for documentation for example by the REPL help
 * function.
 */
interface Helpful : OccasionallyHelpful {
    override fun prettyPleaseHelp() = this

    /** Should be just a few words describing this thing */
    fun briefHelp(): String

    /** Should be a block of text that goes into usage detail */
    fun longHelp(): String

    companion object {
        fun wrap(value: Value<*>): Helpful = when (val sv = value.stateVector) {
            is Helpful -> sv
            is OccasionallyHelpful -> sv.prettyPleaseHelp()
            else -> null
        } ?: generic(value)

        fun generic(value: Value<*>): Helpful = object : Helpful {
            override fun briefHelp(): String = "<<${value.typeTag.name} not documented yet>>"

            override fun longHelp(): String = buildString {
                when (val sv = value.stateVector) {
                    is NamedBuiltinFun -> {
                        appendLine("Builtin function ${sv.name} has not been documented yet.")
                        for (sig in sv.sigs.orEmpty()) {
                            appendLine("Signature: $sig")
                        }
                    }
                    else -> appendLine(briefHelp())
                }
            }
        }

        fun of(briefHelp: String, longHelp: String): Helpful = object : Helpful {
            override fun briefHelp(): String = briefHelp
            override fun longHelp(): String = longHelp

            override fun toString(): String = "Helpful($briefHelp)"
        }
    }
}

interface HelpfullyNamed : Helpful {
    /** A suggested topic name for the REPL `help` function to retrieve this help topic. */
    fun helpfulTopicName(): String
}
