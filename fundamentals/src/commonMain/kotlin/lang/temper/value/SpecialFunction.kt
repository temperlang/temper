package lang.temper.value

/** A non-macro that needs access to its arguments as trees and which may survive to runtime. */
interface SpecialFunction : BuiltinStatelessMacroValue {
    override val functionSpecies: FunctionSpecies get() = FunctionSpecies.Special
}
