package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.type2.AnySignature
import kotlin.reflect.full.findAnnotations

/**
 * A value that may be called with a set of AST node arguments.
 *
 * Values that are called with actual values as arguments are a subset of these; functions are
 * a subset of macros that don't need to access trees to do their jobs.
 */
interface MacroValue : StayReferrer, OccasionallyHelpful {
    /**
     * Used to check whether this can be applied to the call arguments in their present form.
     */
    val sigs: List<AnySignature>?

    operator fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult

    /**
     * True if a call may [Fail], not due to the failure of any argument, but because of some
     * internal operation of the function's body.
     */
    val callMayFailPerSe get() = true

    /** True if the argument at index 0 should be a [LeftNameLeaf] */
    val assignsArgumentOne get() = false

    /** Describes the time at which this should be invoked. */
    val functionSpecies: FunctionSpecies get() = FunctionSpecies.Macro

    /** This default implementation looks for help information in a [HelpInfo] annotation. */
    override fun prettyPleaseHelp(): Helpful? {
        val clazz = this::class
        val context = (this as? NamedBuiltinFun)?.name
            ?: clazz.simpleName
            ?: "anonymous"

        val helpDefined = helpDefined(this)
        if (helpDefined != null) {
            return helpDefined.toHelpful(context)
        }

        val helpInfo = clazz.findAnnotations<HelpInfo>()
        if (helpInfo.isNotEmpty()) {
            return helpInfo.first().toHelpful(context)
        }

        val helpSnippet = clazz.findAnnotations<HelpSnippet>()
        if (helpSnippet.isNotEmpty()) {
            return helpSnippet.first().toHelpful(context)
        }

        return null
    }
}

interface StaylessMacroValue : MacroValue, Stayless

interface BuiltinStatelessMacroValue : StaylessMacroValue
