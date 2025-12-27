package lang.temper.value

/**
 * An enum of [InternalFeatureKey] wrappers.
 *
 * TODO: write tests that both
 * *BuiltinEnvironment* and *InterpreterFeatureImplementations* have entries for each key in their
 * own ways.
 */
enum class InternalFeatureKeys(
    val featureKey: InternalFeatureKey,
    val functionSpecies: FunctionSpecies,
    /** If its [featureKey] is also a builtin key that needs a binding in the builtin environment */
    val isBuiltin: Boolean,
) {
    Arrow("=>", FunctionSpecies.Macro, isBuiltin = true), // Formalizes arguments to arrow functions.
    Class("class", FunctionSpecies.Macro, isBuiltin = true),
    Fn("fn", FunctionSpecies.Macro, isBuiltin = true),
    Interface("interface", FunctionSpecies.Macro, isBuiltin = true),
    Let("let", FunctionSpecies.Macro, isBuiltin = true),
    MakeValueResult("makeValueResult", FunctionSpecies.Pure, isBuiltin = false),
    AdaptGeneratorFn("adaptGeneratorFn", FunctionSpecies.Pure, isBuiltin = false),
    SafeAdaptGeneratorFn("safeAdaptGeneratorFn", FunctionSpecies.Pure, isBuiltin = false),
    GeneratorStepperFn("generatorStepperFn", FunctionSpecies.Normal, isBuiltin = false),
    PromoteSimpleValueToClassInstance("promoteSimpleValueToClassInstance", FunctionSpecies.Normal, isBuiltin = false),
}
